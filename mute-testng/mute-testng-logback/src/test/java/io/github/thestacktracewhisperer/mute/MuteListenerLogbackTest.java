package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-testng-logback
 * %%
 * Copyright (C) 2026 TheStackTraceWhisperer
 * %%
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
 * #L%
 */

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.TestNG;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the Logback implementation of the TestNG mute listener.
 *
 * <p>Each test is fully self-contained: it sets a known logger level, executes a
 * small TestNG fixture class programmatically (with the MuteListener auto-discovered
 * via ServiceLoader), then asserts the level is restored — regardless of how the
 * fixture test itself ends.
 */
class MuteListenerLogbackTest {

    private static final Logger ROOT =
            (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private static final Logger SERVICE_A =
            (Logger) LoggerFactory.getLogger(ServiceA.class);
    private static final Logger SERVICE_B =
            (Logger) LoggerFactory.getLogger(ServiceB.class);

    private Level savedRoot;
    private Level savedServiceA;
    private Level savedServiceB;

    @BeforeEach
    void saveLevels() {
        savedRoot     = ROOT.getLevel();
        savedServiceA = SERVICE_A.getLevel();
        savedServiceB = SERVICE_B.getLevel();
    }

    @AfterEach
    void restoreLevels() {
        ROOT.setLevel(savedRoot);
        SERVICE_A.setLevel(savedServiceA);
        SERVICE_B.setLevel(savedServiceB);
    }

    @Test
    @DisplayName("Root logger is muted during @Mute test and restored afterward")
    void rootLoggerMutedAndRestored() {
        ROOT.setLevel(Level.INFO);
        runTestNG(RootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Root logger is restored even after a @Mute test that throws")
    void rootLoggerRestoredAfterFailingTest() {
        ROOT.setLevel(Level.WARN);
        runTestNG(RootMuteThrowsFixture.class, 0, 1);
        assertEquals(Level.WARN, ROOT.getLevel());
    }

    @Test
    @DisplayName("Class-level @Mute mutes the root logger for every test in the class")
    void classLevelMuteAnnotationMutesAndRestores() {
        ROOT.setLevel(Level.INFO);
        runTestNG(ClassLevelRootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Specific class logger is muted; root logger is unaffected")
    void specificClassLoggerMutedAndRestored() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        runTestNG(SingleClassMuteFixture.class);
        assertEquals(Level.DEBUG, SERVICE_A.getLevel(), "ServiceA logger should be restored");
        assertEquals(Level.INFO,  ROOT.getLevel(),      "Root logger should be unaffected");
    }

    @Test
    @DisplayName("Multiple class loggers are all muted and restored independently")
    void multipleClassLoggersMutedAndRestored() {
        SERVICE_A.setLevel(Level.TRACE);
        SERVICE_B.setLevel(Level.ERROR);
        runTestNG(MultipleClassMuteFixture.class);
        assertEquals(Level.TRACE, SERVICE_A.getLevel());
        assertEquals(Level.ERROR, SERVICE_B.getLevel());
    }

    @Test
    @DisplayName("A null (inherited) logger level is correctly restored to null after muting")
    void nullInheritedLevelIsRestored() {
        SERVICE_A.setLevel(null);
        assertNull(SERVICE_A.getLevel());
        runTestNG(NullLevelMuteFixture.class);
        assertNull(SERVICE_A.getLevel(), "Null (inherited) level should be restored after muting");
    }

    // ---------- LogbackMute unit tests ----------

    @Test
    @DisplayName("Missing Logback binding fails fast with helpful error")
    void missingLogbackBindingFailsFast() {
        LogbackMute mute = new LogbackMute(() -> new Object());

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> mute.mute(new Class<?>[0]));
        assertEquals(
                "mute-testng-logback requires Logback Classic on the classpath; found: java.lang.Object",
                exception.getMessage());
    }

    @Test
    @DisplayName("Null logger factory fails fast with null type in error")
    void nullLoggerFactoryFailsFast() {
        LogbackMute mute = new LogbackMute(() -> null);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> mute.mute(new Class<?>[0]));
        assertEquals(
                "mute-testng-logback requires Logback Classic on the classpath; found: null",
                exception.getMessage());
    }

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        ROOT.setLevel(Level.INFO);
        MuteRestorer restorer = new LogbackMute().mute(new Class<?>[0]);

        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        SERVICE_B.setLevel(Level.ERROR);
        MuteRestorer restorer = new LogbackMute().mute(new Class<?>[] {ServiceA.class, ServiceB.class});

        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        assertEquals(Level.OFF, SERVICE_B.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
        assertEquals(Level.ERROR, SERVICE_B.getLevel());
    }

    // ---------- TestNG fixture classes (run programmatically, not by Surefire) ----------

    public static class RootMuteFixture {
        @org.testng.annotations.Test
        @Mute
        public void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
        }
    }

    public static class RootMuteThrowsFixture {
        @org.testng.annotations.Test
        @Mute
        public void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
            throw new RuntimeException("expected");
        }
    }

    @Mute
    public static class ClassLevelRootMuteFixture {
        @org.testng.annotations.Test
        public void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
        }
    }

    public static class SingleClassMuteFixture {
        @org.testng.annotations.Test
        @Mute(classes = ServiceA.class)
        public void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.INFO, ROOT.getLevel());
        }
    }

    public static class MultipleClassMuteFixture {
        @org.testng.annotations.Test
        @Mute(classes = {ServiceA.class, ServiceB.class})
        public void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.OFF, SERVICE_B.getLevel());
        }
    }

    public static class NullLevelMuteFixture {
        @org.testng.annotations.Test
        @Mute(classes = ServiceA.class)
        public void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
        }
    }

    // ---------- Dummy service marker classes ----------

    private static class ServiceA {}
    private static class ServiceB {}

    // ---------- Helper ----------

    private static void runTestNG(Class<?> fixtureClass) {
        runTestNG(fixtureClass, 1, 0);
    }

    private static void runTestNG(Class<?> fixtureClass, int expectedPassed, int expectedFailed) {
        int[] counts = {0, 0}; // [passed, failed]
        TestNG testng = new TestNG(false);
        testng.setTestClasses(new Class<?>[] {fixtureClass});
        testng.addListener(new ITestListener() {
            @Override public void onTestSuccess(ITestResult r) { counts[0]++; }
            @Override public void onTestFailure(ITestResult r) { counts[1]++; }
        });
        testng.run();
        assertEquals(expectedPassed, counts[0],
                fixtureClass.getSimpleName() + ": expected " + expectedPassed + " passed test(s)");
        assertEquals(expectedFailed, counts[1],
                fixtureClass.getSimpleName() + ": expected " + expectedFailed + " failed test(s)");
    }
}
