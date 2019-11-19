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

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import walkingkooka.collect.list.Lists;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

/**
 * A {@link J2clRequest} that accompanies a test. The entry points and initial-script-filename are NOT taken from the pom,
 * but rather generated by {@link J2clMojoTest} and this request is reused over multiple tests.
 */
final class J2clMojoTestRequest extends J2clRequest {

    static J2clMojoTestRequest with(final J2clPath base,
                                    final J2clPath target,
                                    final J2clClasspathScope scope,
                                    final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies,
                                    final List<J2clArtifactCoords> classpathRequired,
                                    final Predicate<J2clArtifactCoords> excluded,
                                    final List<J2clArtifactCoords> javascriptSourceRequired,
                                    final List<J2clArtifactCoords> processingSkipped,
                                    final Map<J2clArtifactCoords, J2clArtifactCoords> replaced,
                                    final CompilationLevel level,
                                    final Map<String, String> defines,
                                    final Set<String> externs,
                                    final Set<ClosureFormattingOption> formatting,
                                    final LanguageMode languageOut,
                                    final J2clMavenMiddleware middleware,
                                    final ExecutorService executor,
                                    final J2clLogger logger) {
        return new J2clMojoTestRequest(base,
                target,
                scope,
                addedDependencies,
                classpathRequired,
                excluded,
                javascriptSourceRequired,
                processingSkipped,
                replaced,
                level,
                defines,
                externs,
                formatting,
                languageOut,
                middleware,
                executor,
                logger);
    }

    private J2clMojoTestRequest(final J2clPath base,
                                final J2clPath target,
                                final J2clClasspathScope scope,
                                final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies,
                                final List<J2clArtifactCoords> classpathRequired,
                                final Predicate<J2clArtifactCoords> excluded,
                                final List<J2clArtifactCoords> javascriptSourceRequired,
                                final List<J2clArtifactCoords> processingSkipped,
                                final Map<J2clArtifactCoords, J2clArtifactCoords> replaced,
                                final CompilationLevel level,
                                final Map<String, String> defines,
                                final Set<String> externs,
                                final Set<ClosureFormattingOption> formatting,
                                final LanguageMode languageOut,
                                final J2clMavenMiddleware middleware,
                                final ExecutorService executor,
                                final J2clLogger logger) {
        super(base,
                target,
                scope,
                addedDependencies,
                classpathRequired,
                excluded,
                javascriptSourceRequired,
                processingSkipped,
                replaced,
                level,
                defines,
                externs,
                formatting,
                languageOut,
                middleware,
                executor,
                logger);
    }

    @Override
    J2clSourcesKind sourcesKind() {
        return J2clSourcesKind.TEST;
    }

    @Override
    List<String> entryPoints() {
        // this is the mangled name of a javascript file produced by the junit annotation-processor.
        return Lists.of("javatests." + this.test + "_AdapterSuite");
    }

    @Override
    J2clPath initialScriptFilename() {
        return this.project.directory().append(this.test + ".js");
    }

    @Override
    String hash() {
        if (null == this.hash) {
            final HashBuilder hash = this.computeHash();
            hash.append(this.test);
            this.hash = hash.toString();
        }
        return this.hash;
    }

    /**
     * Lazily computed and cached hash. A new hash needs to be computed when the test is changed.
     */
    private String hash;

    J2clMojoTestRequest setTest(final J2clDependency project,
                                final String test) {
        this.project = project;
        this.test = test;
        this.hash = null; // force recompute.

        return this;
    }

    private J2clDependency project;
    private String test;
}
