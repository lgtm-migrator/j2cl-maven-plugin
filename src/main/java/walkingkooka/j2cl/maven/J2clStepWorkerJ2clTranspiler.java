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

import java.util.List;
import java.util.stream.Collectors;

/**
 * Transpiles the stripped source into javascript equivalents.
 */
final class J2clStepWorkerJ2clTranspiler extends J2clStepWorker2 {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerJ2clTranspiler();
    }

    private J2clStepWorkerJ2clTranspiler() {
        super();
    }

    @Override
    final J2clStepResult execute1(final J2clDependency artifact,
                                  final J2clStepDirectory directory,
                                  final J2clLinePrinter logger) throws Exception {
        final J2clPath sourceRoot;

        if (artifact.isIgnored()) {
            sourceRoot = artifact.step(J2clStep.UNPACK).output();
        } else {
            sourceRoot = shadeOrCompileGwtIncompatibleStripped(artifact);
        }

        logger.printLine("Preparing...");
        logger.printIndented("Source path(s)", sourceRoot);

        final List<J2clPath> classpath = artifact.dependencies()
                .stream()
                .map(d -> d.isIgnored() ?
                        d.artifactFileOrFail() :
                        shadeOrCompileGwtIncompatibleStripped(d))
                        .flatMap(d -> d.exists().stream())
                .collect(Collectors.toList());

        return J2clTranspiler.execute(classpath,
                sourceRoot,
                directory.output().absentOrFail(),
                logger) ?
                J2clStepResult.SUCCESS :
                J2clStepResult.FAILED;
    }

    /**
     * Try the shaded/output if that exists or fallback to compile-gwt-incompatible-stripped/output
     */
    private static J2clPath shadeOrCompileGwtIncompatibleStripped(final J2clDependency dependency) {
        return dependency.step(J2clStep.SHADE_JAVA_SOURCE)
                .output()
                .exists()
                .orElseGet(() -> dependency.step(J2clStep.COMPILE_GWT_INCOMPATIBLE_STRIPPED).output());
    }
}
