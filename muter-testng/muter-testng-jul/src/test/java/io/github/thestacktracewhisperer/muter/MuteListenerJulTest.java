package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.*;
import org.testng.TestNG;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for the JUL implementation of the TestNG muter listener.
 */
class MuteListenerJulTest {

    private static final Logger ROOT = Logger.getLogger("");
    private static final Logger SERVICE_A = Logger.getLogger(ServiceA.class.getName());
    private static final Logger SERVICE_B = Logger.getLogger(ServiceB.class.getName());

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
        ROOT.setLevel(Level.WARNING);
        runTestNG(RootMuteThrowsFixture.class);
        assertEquals(Level.WARNING, ROOT.getLevel());
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
        SERVICE_A.setLevel(Level.FINE);
        runTestNG(SingleClassMuteFixture.class);
        assertEquals(Level.FINE, SERVICE_A.getLevel(), "ServiceA logger should be restored");
        assertEquals(Level.INFO, ROOT.getLevel(),      "Root logger should be unaffected");
    }

    @Test
    @DisplayName("Multiple class loggers are all muted and restored independently")
    void multipleClassLoggersMutedAndRestored() {
        SERVICE_A.setLevel(Level.FINEST);
        SERVICE_B.setLevel(Level.SEVERE);
        runTestNG(MultipleClassMuteFixture.class);
        assertEquals(Level.FINEST, SERVICE_A.getLevel());
        assertEquals(Level.SEVERE, SERVICE_B.getLevel());
    }

    @Test
    @DisplayName("A null (inherited) logger level is correctly restored to null after muting")
    void nullInheritedLevelIsRestored() {
        SERVICE_A.setLevel(null);
        assertNull(SERVICE_A.getLevel());
        runTestNG(NullLevelMuteFixture.class);
        assertNull(SERVICE_A.getLevel(), "Null (inherited) level should be restored after muting");
    }

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        ROOT.setLevel(Level.INFO);
        MuteRestorer restorer = new JulMuter().mute(new Class<?>[0]);

        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.FINE);
        SERVICE_B.setLevel(Level.SEVERE);
        MuteRestorer restorer = new JulMuter().mute(new Class<?>[] {ServiceA.class, ServiceB.class});

        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        assertEquals(Level.OFF, SERVICE_B.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.FINE, SERVICE_A.getLevel());
        assertEquals(Level.SEVERE, SERVICE_B.getLevel());
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
        TestNG testng = new TestNG(false);
        testng.setTestClasses(new Class<?>[] {fixtureClass});
        testng.run();
    }
}
