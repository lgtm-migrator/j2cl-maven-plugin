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

import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * If the dependency source has a shade file, create an output directory with selected shaded class files combined
 * with the other class files changed.
 */
abstract class J2clStepWorkerShade extends J2clStepWorker2 {

    /**
     * Package private to limit sub classing.
     */
    J2clStepWorkerShade() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                            final J2clStepDirectory directory,
                            final J2clLinePrinter logger) throws Exception {
        J2clStepResult result = null;

        if (artifact.isProcessingSkipped()) {
            result = J2clStepResult.SKIPPED;
        } else {
            logger.indent();
            {
                final Map<String, String> shadeMappings = artifact.shadeMappings();

                if (!shadeMappings.isEmpty()) {
                    this.copyAndShade(artifact.step(this.step()).output(),
                            shadeMappings,
                            directory.output(),
                            logger);
                    result = J2clStepResult.SUCCESS;
                } else {
                    logger.printLine("Not found");
                    result = J2clStepResult.SKIPPED;
                }
            }

            logger.outdent();
        }

        return result;
    }

    abstract J2clStep step();

    /**
     * Performs two copy passes, the first will shade any files during the copy process, the second will simply
     * copy the files to the destination.
     */
    private void copyAndShade(final J2clPath root,
                                  final Map<String, String> shade,
                                  final J2clPath output,
                                  final J2clLinePrinter logger) throws Exception {
        final Set<J2clPath> files = root.gatherFiles(this.fileFilter());
        final Set<J2clPath> nonShadedFiles = Sets.sorted();
        nonShadedFiles.addAll(files);

        logger.indent();
        {
            for (final Entry<String, String> mapping : shade.entrySet()) {
                final String find = mapping.getKey();
                final String replace = mapping.getValue();

                final J2clPath shadeDirectory = replace.isEmpty() ?
                        output :
                        output.append(replace.replace('.', File.separatorChar));

                logger.printLine("Shading package from " + CharSequences.quote(find) + " to " + CharSequences.quote(replace));
                logger.indent();
                {
                    final Set<J2clPath> shadedFiles = Sets.sorted();
                    final J2clPath shadedRoot = root.append(find.replace('.', File.separatorChar));

                    // filter only files belonging to shade source root
                    files.stream()
                            .filter(f -> f.path().startsWith(shadedRoot.path()))
                            .forEach(shadedFiles::add);

                    nonShadedFiles.removeAll(shadedFiles);

                    // copy and shade java source and copy other files to output.
                    shadeDirectory.copyFiles(shadedRoot,
                            shadedFiles,
                            (content, path) -> {
                                return path.isJava() ?
                                        shade(content, shade) :
                                        content;
                            },
                            logger);
                }
                logger.outdent();
            }

            logger.printLine("Copying other files");
            logger.indent();
            {

                // copy all other files verbatim.
                output.copyFiles(root,
                        nonShadedFiles,
                        logger);

            }
            logger.outdent();
        }
        logger.outdent();
    }

    abstract BiPredicate<Path, BasicFileAttributes> fileFilter();

    /**
     * Reads the file and shades the source text or class file type references.
     */
    abstract byte[] shade(final byte[] content,
                          final Map<String, String> mappings);
}