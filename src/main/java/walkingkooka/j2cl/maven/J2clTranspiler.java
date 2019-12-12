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

import com.google.j2cl.common.Problems;
import com.google.j2cl.frontend.Frontend;
import com.google.j2cl.transpiler.J2clTranspilerOptions;
import walkingkooka.collect.list.Lists;
import walkingkooka.text.CharSequences;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

final class J2clTranspiler {

    static boolean execute(final List<J2clPath> classpath,
                           final J2clPath sourcePath,
                           final J2clPath output,
                           final J2clLinePrinter logger) throws IOException {
        logger.printLine("J2clTranspiler");
        logger.indent();

        boolean success;
        {
            final List<J2clPath> javaInput = Lists.array();
            final List<J2clPath> nativeJsInput = Lists.array();
            final List<J2clPath> jsInput = Lists.array();// probably js
            final List<J2clPath> nativeJsAndJsInput = Lists.array();

            if (sourcePath.exists().isPresent()) {
                sourcePath.gatherFiles((p, a) -> J2clPath.JAVA_FILES.test(p, a) || J2clPath.JAVASCRIPT_FILES.test(p, a) || J2clPath.NATIVE_JAVASCRIPT_FILES.test(p, a))
                        .forEach(f -> {
                            final String filename = f.filename();
                            if (CharSequences.endsWith(filename, ".java")) {
                                javaInput.add(f);
                            } else {
                                if (CharSequences.endsWith(filename, ".native.js")) {
                                    nativeJsInput.add(f);
                                } else {
                                    if (CharSequences.endsWith(filename, ".js")) {
                                        jsInput.add(f);
                                    }
                                }
                                nativeJsAndJsInput.add(f);
                            }
                        });
            }

            logger.printLine("Parameters");
            logger.indent();
            {
                logger.printIndented("Classpath(s)", classpath);
                logger.printIndented("*.java Source(s)", javaInput);
                logger.printIndented("*.native.js source(s)", nativeJsInput);
                logger.printIndented("*.js source(s)", jsInput);
                logger.printIndented("Output", output);
            }
            logger.outdent();

            logger.printLine("J2clTranspiler");
            logger.indent();
            {
                final J2clTranspilerOptions options = J2clTranspilerOptions.newBuilder()
                        .setClasspaths(classpath.stream()
                                .map(J2clPath::toString)
                                .collect(Collectors.toList())
                        )
                        .setOutput(output.path())
                        .setDeclareLegacyNamespace(false)//TODO parameterize these? copied straight from vertispan/j2clmavenplugin
                        .setEmitReadableLibraryInfo(false)
                        .setEmitReadableSourceMap(false)
                        .setFrontend(Frontend.JDT)
                        .setGenerateKytheIndexingMetadata(false)
                        .setSources(J2clPath.toFileInfo(javaInput, sourcePath))
                        .setNativeSources(J2clPath.toFileInfo(nativeJsInput, sourcePath))
                        .build();

                final Problems problems = com.google.j2cl.transpiler.J2clTranspiler.transpile(options);
                success = !problems.hasErrors();

                final List<String> messages = problems.getMessages();
                final int count = messages.size();

                logger.printLine(count + " problem(s)");
                {
                    logger.indent();
                    {
                        logger.indent();
                        messages.forEach(logger::printLine);

                        logger.outdent();
                    }
                    logger.printEndOfList();
                    logger.outdent();
                }

                if (success) {
                    logger.printLine("Copy js to output");
                    logger.indent();
                    {
                        output.copyFiles(sourcePath,
                                jsInput,
                                logger);
                    }
                    logger.outdent();

                    logger.printIndented("Output file(s) after copy", output.gatherFiles(J2clPath.ALL_FILES));
                }
            }
        }
        logger.outdent();

        return success;
    }
}
