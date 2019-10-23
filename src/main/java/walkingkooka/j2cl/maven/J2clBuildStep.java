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

import walkingkooka.collect.list.Lists;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The individual steps that are executed in series to complete the process or building.
 */
enum J2clBuildStep {
    /**
     * Computes the hash for the given {@link J2clDependency} including its dependencies.
     */
    HASH {
        @Override
        String directoryName() {
            return "0-hash";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.HASH;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return false;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.of(UNPACK);
        }
    },

    /**
     * For archives (dependencies) unpack the accompanying sources.
     */
    UNPACK {
        @Override
        String directoryName() {
            return "1-unpack";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.UNPACK;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return true;
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.of(COMPILE);
        }
    },

    /**
     * Calls javac on the unpack directory along with its dependencies on the classpath into /compile
     */
    COMPILE {
        @Override
        String directoryName() {
            return "2-javac-compiled-source";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.COMPILE_SOURCE;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return true; // dont try and compile JRE
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.of(GWT_INCOMPATIBLE_STRIP);
        }
    },

    /**
     * Calls the @GwtIncompatible stripper on /compile saving into /gwt-incompatible-strip
     */
    GWT_INCOMPATIBLE_STRIP {
        @Override
        String directoryName() {
            return "3-gwt-incompatible-stripped-source";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.STRIP_GWT_INCOMPAT;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return true; // assumes JRE artifact has already been stripped
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.of(COMPILE_GWT_INCOMPATIBLE_STRIPPED);
        }
    },

    /**
     * Compiles /gwt-incompatible-strip along with dependencies on the classpath into /gwt-incompatible-strip-compiled
     */
    COMPILE_GWT_INCOMPATIBLE_STRIPPED {
        @Override
        String directoryName() {
            return "4-javac-compiled-gwt-incompatible-stripped-source";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.COMPILE_STRIP_GWT_INCOMPAT;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return true; // no striped JRE to compile
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.of(TRANSPILE);
        }
    },

    /**
     * Calls the transpiler on /gwt-incompatible-strip and dependencies as native source/javascript into /transpile
     */
    TRANSPILE {
        @Override
        String directoryName() {
            return "5-transpiled-java-to-javascript";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.TRANSPILER;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return true; // transpile JRE/UNPACK
        }

        @Override
        boolean skipIfDependency() {
            return false;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.of(CLOSURE_COMPILER);
        }
    },
    /**
     * Calls the closure compiler on the /transpiler along with other "files" into /closure-compiled.
     */
    CLOSURE_COMPILER {
        @Override
        String directoryName() {
            return "6-closure-compiler-output";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.CLOSURE;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return true;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.of(OUTPUT_ASSEMBLER);
        }
    },
    /**
     * Assembles the output and copies files to the place.
     */
    OUTPUT_ASSEMBLER {
        @Override
        String directoryName() {
            return "7-output-assembler";
        }

        @Override
        J2clBuildStepWorker execute1() {
            return J2clBuildStepWorker.OUTPUT_ASSEMBLER;
        }

        @Override
        boolean skipIJavacBootstrapOrJre() {
            return true;
        }

        @Override
        boolean skipIfDependency() {
            return true;
        }

        @Override
        Optional<J2clBuildStep> next() {
            return Optional.empty();
        }
    };

    public final static J2clBuildStep FIRST = HASH;

    // step directory naming............................................................................................

    /**
     * Returns the actual sub directory name on disk. The directory will hold all the output files created by this step.
     */
    abstract String directoryName();

    // work methods.....................................................................................................

    /**
     * A {@link Callable} that creates a logger that is saved to a log file when the task is successful (completes without
     * throwing) or logs as errors to the output anything printed during execution.
     */
    final Optional<J2clBuildStep> execute(final J2clDependency artifact) throws Exception {
        final J2clLogger j2clLogger = artifact.request()
                .logger();
        final List<CharSequence> lines = Lists.array(); // these lines will be written to a log file.
        final String prefix = artifact.coords() + "-" + this;

        final J2clLinePrinter logger = J2clLinePrinter.with(j2clLogger.printer(j2clLogger::debug)
                .printedLine((line, eol, p) -> {
                    p.print(line);
                    p.flush();
                    lines.add(line);
                }));
        try {
            final J2clBuildStepResult result;

            if ((artifact.isJavacBootstrap() || artifact.isJreBinary()) && this.skipIJavacBootstrapOrJre()) {
                result = J2clBuildStepResult.SUCCESS;
            } else {
                if (artifact.isDependency() && this.skipIfDependency()) {
                    result = J2clBuildStepResult.SUCCESS;
                } else {
                    logger.printLine(prefix);
                    logger.indent();

                    result = this.execute1()
                            .execute(artifact,
                                    this,
                                    logger);
                    final J2clStepDirectory directory = artifact.step(this);
                    directory.writeLog(lines, logger);

                    result.path(directory).createIfNecessary();

                    result.reportIfFailure(artifact, this);
                }
            }
            return result.next(this.next());
        } catch (final Exception cause) {
            logger.flush();

            j2clLogger.error("Failed to execute " + prefix + " message: " + cause.getMessage(), cause);
            lines.forEach(l -> j2clLogger.error(prefix + " " + l));

            // capture stack trace into $lines
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final String charset = Charset.defaultCharset().name();
            cause.printStackTrace(new PrintStream(bytes, true, charset));
            logger.emptyLine();
            logger.print(new String(bytes.toByteArray(), charset));

            final J2clStepDirectory directory = artifact.step(this);
            if (directory.path().exists().isPresent()) {
                directory.writeLog(lines, logger);
            } else {
                // HASH step probably failed so create a unique file and write it to the base directory.
                final Path base = Paths.get(artifact.request().base.path().toString(), artifact.coords() + "-" + DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));

                j2clLogger.error("Log file");
                j2clLogger.error(J2clLogger.INDENTATION + base.toString());

                Files.write(base, lines);
            }
            artifact.request().cancel(cause);

            throw cause;
        }
    }

    /**
     * Returns the sub class of {@link J2clBuildStepWorker} and then calls {@link J2clBuildStepWorker#execute(J2clDependency, J2clBuildStep, J2clLinePrinter)
     */
    abstract J2clBuildStepWorker execute1();

    // skipIfJre........................................................................................................

    /**
     * Some steps can be skipped if the artifact is the java bootstrap or JRE. These steps include {@link #COMPILE}, {@link #GWT_INCOMPATIBLE_STRIP}
     * or {@link #COMPILE_GWT_INCOMPATIBLE_STRIPPED}.
     */
    abstract boolean skipIJavacBootstrapOrJre();

    // skipIfJre........................................................................................................

    /**
     * Some steps should not be attemped by dependencies.
     */
    abstract boolean skipIfDependency();

    // next.............................................................................................................

    /**
     * Returns the next step if one is present.
     */
    abstract Optional<J2clBuildStep> next();
}
