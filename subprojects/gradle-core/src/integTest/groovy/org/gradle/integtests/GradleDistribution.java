/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.integtests;

import org.gradle.process.launcher.BootstrapClassLoaderWorker;
import org.gradle.process.launcher.GradleWorkerMain;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.util.Arrays;

/**
 * Provides access to a Gradle distribution for integration testing.
 */
public class GradleDistribution implements MethodRule {
    private static final String NOFORK_SYS_PROP = "org.gradle.integtest.nofork";
    private static final TestFile USER_HOME_DIR;
    private static final TestFile GRADLE_HOME_DIR;
    private static final TestFile SAMPLES_DIR;
    private static final TestFile USER_GUIDE_OUTPUT_DIR;
    private static final TestFile USER_GUIDE_INFO_DIR;
    private static final TestFile DISTS_DIR;
    private static final boolean FORK;
    private final TemporaryFolder temporaryFolder = new TemporaryFolder();
    private GradleExecuter executer;

    static {
        USER_HOME_DIR = file("integTest.gradleUserHomeDir", "intTestHomeDir");

        TestFile workerJar = USER_HOME_DIR.file("worker-main-jar-exploded");
        for (Class<?> aClass : Arrays.asList(GradleWorkerMain.class, BootstrapClassLoaderWorker.class)) {
            String fileName = aClass.getName().replace('.', '/') + ".class";
            workerJar.file(fileName).copyFrom(GradleDistribution.class.getClassLoader().getResource(fileName));
        }

        System.setProperty("gradle.core.worker.jar", workerJar.getAbsolutePath());

        GRADLE_HOME_DIR = file("integTest.gradleHomeDir", null);
        SAMPLES_DIR = file("integTest.samplesdir", new File(GRADLE_HOME_DIR, "samples").getAbsolutePath());
        USER_GUIDE_OUTPUT_DIR
                = file("integTest.userGuideOutputDir", "subprojects/gradle-docs/src/samples/userguideOutput");
        USER_GUIDE_INFO_DIR = file("integTest.userGuideInfoDir", "subprojects/gradle-docs/build/src/docbook");
        DISTS_DIR = file("integTest.distsDir", "build/distributions");

        FORK = System.getProperty(NOFORK_SYS_PROP, "false").equalsIgnoreCase("false");
    }

    public Statement apply(Statement base, FrameworkMethod method, Object target) {
        return temporaryFolder.apply(base, method, target);
    }

    private static TestFile file(String propertyName, String defaultFile) {
        String path = System.getProperty(propertyName, defaultFile);
        if (path == null) {
            throw new RuntimeException(String.format("You must set the '%s' property to run the integration tests.",
                    propertyName));
        }
        return new TestFile(new File(path));
    }

    /**
     * An executer to use to execute this gradle distribution.
     */
    public GradleExecuter getExecuter() {
        if (executer == null) {
            executer = new QuickGradleExecuter(this, FORK);
        }
        return executer;
    }

    /**
     * The user home dir used for the current test. This is usually shared with other tests.
     */
    public TestFile getUserHomeDir() {
        return USER_HOME_DIR;
    }

    /**
     * The distribution for the current test. This is usually shared with other tests.
     */
    public TestFile getGradleHomeDir() {
        return GRADLE_HOME_DIR;
    }

    /**
     * The samples from the distribution.
     */
    public TestFile getSamplesDir() {
        return SAMPLES_DIR;
    }

    public TestFile getUserGuideInfoDir() {
        return USER_GUIDE_INFO_DIR;
    }

    public TestFile getUserGuideOutputDir() {
        return USER_GUIDE_OUTPUT_DIR;
    }

    /**
     * The directory containing the distribution Zips
     */
    public TestFile getDistributionsDir() {
        return DISTS_DIR;
    }

    /**
     * Returns true if the given file is either part of the distributions, samples, or test files.
     */
    public boolean isFileUnderTest(File file) {
        return GRADLE_HOME_DIR.isSelfOrDescendent(file)
                || SAMPLES_DIR.isSelfOrDescendent(file)
                || getTestDir().isSelfOrDescendent(file)
                || getUserHomeDir().isSelfOrDescendent(file);
    }

    /**
     * Returns a scratch-pad directory for the current test.
     */
    public TestFile getTestDir() {
        return temporaryFolder.getDir();
    }

    /**
     * Returns a scratch-pad file for the current test. Equivalent to getTestDir().file(path)
     */
    public TestFile testFile(Object... path) {
        return getTestDir().file(path);
    }
}
