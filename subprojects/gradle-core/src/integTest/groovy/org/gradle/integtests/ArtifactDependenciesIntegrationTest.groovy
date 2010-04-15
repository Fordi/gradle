/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests

import org.junit.Test
import static org.hamcrest.Matchers.*
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Ignore

class ArtifactDependenciesIntegrationTest extends AbstractIntegrationTest {
    @Rule
    public final TestResources testResources = new TestResources()

    @Test
    public void dependencyReportWithConflicts() {
        File buildFile = testFile("projectWithConflicts.gradle");
        usingBuildFile(buildFile).withDependencyList().run();
    }

    @Test
    public void canNestModules() throws IOException {
        File buildFile = testFile("projectWithNestedModules.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test @Ignore
    public void canHaveCycleInDependencyGraph() throws IOException {
        File buildFile = testFile("projectWithCyclesInDependencyGraph.gradle");
        usingBuildFile(buildFile).run();
    }

    @Test
    public void reportsUnknownDependencyError() {
        File buildFile = testFile("projectWithUnknownDependency.gradle");
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration ':compile'"));
        failure.assertThatCause(containsString("unresolved dependency: test#unknownProjectA;1.2: not found"));
        failure.assertThatCause(containsString("unresolved dependency: test#unknownProjectB;2.1.5: not found"));
    }

    @Test
    public void reportsProjectDependsOnSelfError() {
        TestFile buildFile = testFile("build.gradle");
        buildFile << '''
            configurations { compile }
            dependencies { compile project(':') }
            defaultTasks 'listJars'
            task listJars << { configurations.compile.each { println it } }
'''
        ExecutionFailure failure = usingBuildFile(buildFile).runWithFailure();
        failure.assertHasFileName("Build file '" + buildFile.getPath() + "'");
        failure.assertHasDescription("Execution failed for task ':listJars'");
        failure.assertThatCause(startsWith("Could not resolve all dependencies for configuration ':compile'"));
        failure.assertThatCause(containsString("a module is not authorized to depend on itself"));
    }

    @Test
    public void canSpecifyProducerTasksForFileDependency() {
        testFile("settings.gradle").write("include 'sub'");
        testFile("build.gradle") << '''
            configurations { compile }
            dependencies { compile project(path: ':sub', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << { assertTrue(file('sub/sub.jar').isFile()) }
'''
        testFile("sub/build.gradle") << '''
            configurations { compile }
            dependencies { compile files('sub.jar') { builtBy 'jar' } }
            task jar << { file('sub.jar').text = 'content' }
'''

        inTestDirectory().withTasks("test").run().assertTasksExecuted(":sub:jar", ":test");
    }

    @Test
    public void resolvedProjectArtifactsContainProjectVersionInTheirNames() {
        testFile('settings.gradle').write("include 'a', 'b'");
        testFile('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { compile }
            task aJar(type: Jar) { }
            artifacts { compile aJar }
'''
        testFile('b/build.gradle') << '''
            apply plugin: 'base'
            version = 'early'
            configurations { compile }
            task bJar(type: Jar) { }
            gradle.taskGraph.whenReady { project.version = 'late' }
            artifacts { compile bJar }
'''
        testFile('build.gradle') << '''
            configurations { compile }
            dependencies { compile project(path: ':a', configuration: 'compile'), project(path: ':b', configuration: 'compile') }
            task test(dependsOn: configurations.compile) << {
                assertEquals(configurations.compile.collect { it.name }, ['a.jar', 'b-late.jar'])
            }
'''
        inTestDirectory().withTasks('test').run()
    }

    @Test
    public void canUseArtifactSelectorForProjectDependencies() {
        testFile('settings.gradle').write("include 'a', 'b'");
        testFile('a/build.gradle') << '''
            apply plugin: 'base'
            configurations { 'default' {} }
            task aJar(type: Jar) { }
            artifacts { 'default' aJar }
'''
        testFile('b/build.gradle') << '''
            configurations { compile }
            dependencies { compile project(':a') { artifact { name = 'a'; type = 'jar' } } }
            task test {
                inputs.files configurations.compile
                doFirst {
                    assertEquals([project(':a').tasks.aJar.archivePath] as Set, configurations.compile.files)
                }
            }
'''
        inTestDirectory().withTasks('test').run()
    }
}

