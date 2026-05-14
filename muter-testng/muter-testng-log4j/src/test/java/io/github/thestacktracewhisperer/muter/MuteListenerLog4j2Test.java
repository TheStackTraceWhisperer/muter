package io.github.thestacktracewhisperer.muter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.*;
import org.testng.TestNG;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the Log4j 2 implementation of the TestNG muter listener.
 */
class MuteListenerLog4j2Test {

    private static final org.apache.logging.log4j.core.Logger ROOT =
            (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
    private static final org.apache.logging.log4j.core.Logger SERVICE_A =
            (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ServiceA.class);
    private static final org.apache.logging.log4j.core.Logger SERVICE_B =
            (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ServiceB.class);

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
        Configurator.setRootLevel(savedRoot);
        restoreLevel(SERVICE_A, savedServiceA);
        restoreLevel(SERVICE_B, savedServiceB);
    }

    private static void restoreLevel(org.apache.logging.log4j.core.Logger logger, Level savedLevel) {
        if (savedLevel != null) {
            Configurator.setLevel(logger.getName(), savedLevel);
        } else {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().removeLogger(logger.getName());
            ctx.updateLoggers();
        }
    }

    @Test
    @DisplayName("Root logger is muted during @Mute test and restored afterward")
    void rootLoggerMutedAndRestored() {
        Configurator.setRootLevel(Level.INFO);
        runTestNG(RootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Root logger is restored even after a @Mute test that throws")
    void rootLoggerRestoredAfterFailingTest() {
        Configurator.setRootLevel(Level.WARN);
        runTestNG(RootMuteThrowsFixture.class);
        assertEquals(Level.WARN, ROOT.getLevel());
    }

    @Test
    @DisplayName("Class-level @Mute mutes the root logger for every test in the class")
    void classLevelMuteAnnotationMutesAndRestores() {
        Configurator.setRootLevel(Level.INFO);
        runTestNG(ClassLevelRootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Specific class logger is muted; root logger is unaffected")
    void specificClassLoggerMutedAndRestored() {
        Configurator.setRootLevel(Level.INFO);
        Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
        runTestNG(SingleClassMuteFixture.class);
        assertEquals(Level.DEBUG, SERVICE_A.getLevel(), "ServiceA logger should be restored");
        assertEquals(Level.INFO,  ROOT.getLevel(),      "Root logger should be unaffected");
    }

    @Test
    @DisplayName("Multiple class loggers are all muted and restored independently")
    void multipleClassLoggersMutedAndRestored() {
        Configurator.setLevel(SERVICE_A.getName(), Level.TRACE);
        Configurator.setLevel(SERVICE_B.getName(), Level.ERROR);
        runTestNG(MultipleClassMuteFixture.class);
        assertEquals(Level.TRACE, SERVICE_A.getLevel());
        assertEquals(Level.ERROR, SERVICE_B.getLevel());
    }

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        Configurator.setRootLevel(Level.INFO);
        MuteRestorer restorer = new Log4j2Muter().mute(new Class<?>[0]);

        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        Configurator.setRootLevel(Level.INFO);
        Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
        Configurator.setLevel(SERVICE_B.getName(), Level.ERROR);
        MuteRestorer restorer = new Log4j2Muter().mute(new Class<?>[] {ServiceA.class, ServiceB.class});

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
