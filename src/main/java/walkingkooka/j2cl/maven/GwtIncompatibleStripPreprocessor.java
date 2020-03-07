/*
 * Copyright 2019 Miroslav Pokorny (github.com/mP1)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package walkingkooka.j2cl.maven;

import com.google.j2cl.common.FrontendUtils.FileInfo;
import com.google.j2cl.common.Problems;
import com.google.j2cl.tools.gwtincompatible.JavaPreprocessor;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Accepts a directory and removes any files marked with @GwtIncompatible.
 * Support is included to load an ignore file and skip copying those files only for that directory.
 */
final class GwtIncompatibleStripPreprocessor {

    static J2clStepResult execute(final List<J2clPath> sourceRoots,
                                  final J2clPath output,
                                  final J2clLinePrinter logger) throws IOException {
        output.exists()
                .orElseThrow(() -> new IllegalArgumentException("Output not a directory or does not exist: " + CharSequences.quote(output.toString())));

        J2clStepResult result;

        // FileInfo must have the source path otherwise stripper will write files to the wrong place.
        final List<FileInfo> javaFiles = prepareJavaFiles(sourceRoots, output, logger);

        final int javaFileCount = javaFiles.size();

        if (javaFileCount > 0) {
            result = processStripAnnotationsFiles(javaFiles, output, logger);

            copyJavascriptFiles(sourceRoots, output, logger);
            logger.printIndented("Output file(s)", output.gatherFiles(J2clPath.ALL_FILES));

        } else {
            logger.printIndentedLine("No files found");

            output.removeAll(); // dont want to leave empty output directory when its empty.
            result = J2clStepResult.ABORTED;
        }

        return result;
    }

    private static List<FileInfo> prepareJavaFiles(final List<J2clPath> sourceRoots,
                                                   final J2clPath output,
                                                   final J2clLinePrinter logger) throws IOException {
        final List<FileInfo> javaFiles = Lists.array();

        logger.printLine("Preparing java files");
        logger.indent();
        {
            for(final J2clPath sourceRoot : sourceRoots) {
                if(sourceRoot.exists().isPresent()) {
                    logger.indent();

                    // find then copy from unpack to $output
                    final Collection<J2clPath> files = output.copyFiles(sourceRoot,
                            gatherFiles(sourceRoot, J2clPath.JAVA_FILES),
                            logger);

                    // necessary to prepare FileInfo with correct sourceRoot otherwise stripped files will be written back to the wrong place.
                    javaFiles.addAll(J2clPath.toFileInfo(files, output));
                }
            }
            logger.printLine(javaFiles.size() + " file(s) count");
        }
        logger.outdent();

        return javaFiles;
    }

    /**
     * Finds all files under the root that match the given {@link BiPredicate} collecting their paths into a {@link SortedSet}.
     * and honours any ignore files if any found.
     */
    private static SortedSet<J2clPath> gatherFiles(final J2clPath root,
                                                   final BiPredicate<Path, BasicFileAttributes> include) throws IOException {
        final SortedSet<J2clPath> files = Sets.sorted();

        final Map<Path, List<PathMatcher>> pathToMatchers = Maps.hash();
        final List<PathMatcher> exclude = Lists.array();

        Files.walkFileTree(root.path(), new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(final Path dir,
                                                     final BasicFileAttributes attrs) throws IOException {
                final J2clPath ignoreFile = J2clPath.with(dir).ignoreFile();
                if (ignoreFile.exists().isPresent()) {
                    final List<PathMatcher> matchers = Files.readAllLines(ignoreFile.path())
                            .stream()
                            .filter(l -> false == l.startsWith("#") | l.trim().length() > 0)
                            .map(l -> FileSystems.getDefault().getPathMatcher("glob:" + dir + File.separator + l))
                            .collect(Collectors.toList());
                    pathToMatchers.put(dir, matchers);
                    exclude.addAll(matchers);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir,
                                                      final IOException cause) {
                final List<PathMatcher> matchers = pathToMatchers.remove(dir);
                if (null != matchers) {
                    matchers.forEach(exclude::remove);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file,
                                             final BasicFileAttributes attributes) {
                if (exclude.stream().noneMatch(m -> m.matches(file))) {
                    if(include.test(file, attributes)) {
                        files.add(J2clPath.with(file));
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return files;
    }

    /**
     * Invokes the java preprocesor which use annotations to discover classes, methods and fields to remove from the actual source files.
     * Because the source files are modified a previous step will have taken copied and place them in this output ready for modification if necessary.
     * Errors will also be logged.
     */
    private static J2clStepResult processStripAnnotationsFiles(final List<FileInfo> javaFilesInput,
                                                               final J2clPath output,
                                                               final J2clLinePrinter logger) {
        J2clStepResult result;

        logger.printLine("JavaPreprocessor");
        {
            logger.indent();
            {
                logger.printIndentedFileInfo("Source(s)", javaFilesInput);
                logger.printIndented("Output", output);

                final Problems problems = new Problems();
                JavaPreprocessor.preprocessFiles(javaFilesInput,
                        output.path(),
                        problems);
                final List<String> errors = problems.getErrors();
                final int errorCount = errors.size();

                logger.printLine(errorCount + " Error(s)");
                logger.indent();

                {
                    if (errorCount > 0) {
                        logger.indent();
                        errors.forEach(logger::printLine);
                        logger.outdent();

                        logger.printEndOfList();
                        result = J2clStepResult.FAILED;
                    } else {
                        result = J2clStepResult.SUCCESS;
                    }
                    logger.printEndOfList();
                }
                logger.outdent();
            }
            logger.outdent();
        }

        return result;
    }

    private static void copyJavascriptFiles(final List<J2clPath> sourceRoots,
                                            final J2clPath output,
                                            final J2clLinePrinter logger) throws IOException {
        logger.printLine("Copy *.js from source root(s) to output");
        logger.indent();
        {
            for (final J2clPath sourceRoot : sourceRoots) {
                final SortedSet<J2clPath> copy = gatherFiles(sourceRoot, J2clPath.JAVASCRIPT_FILES);
                output.copyFiles(sourceRoot, copy, logger);
            }
        }
        logger.outdent();
    }
}
