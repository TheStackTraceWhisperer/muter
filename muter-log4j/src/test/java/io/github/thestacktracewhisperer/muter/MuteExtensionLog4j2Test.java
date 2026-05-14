package io.github.thestacktracewhisperer.muter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for the Log4j 2 implementation of the muter extension.
 */
class MuteExtensionLog4j2Test {

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
        // Use the logger's own name (Log4j 2 normalises '$' → '.') for consistency
        restoreLevel(SERVICE_A, savedServiceA);
        restoreLevel(SERVICE_B, savedServiceB);
    }

    private static void restoreLevel(org.apache.logging.log4j.core.Logger logger, Level savedLevel) {
        if (savedLevel != null) {
            Configurator.setLevel(logger.getName(), savedLevel);
        } else {
            // Remove any explicit config so the logger inherits from its parent
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getConfiguration().removeLogger(logger.getName());
            ctx.updateLoggers();
        }
    }

    @Test
    @DisplayName("Root logger is muted during @Mute test and restored afterward")
    void rootLoggerMutedAndRestored() {
        Configurator.setRootLevel(Level.INFO);
        runFixture(RootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Root logger is restored even after a @Mute test that throws")
    void rootLoggerRestoredAfterFailingTest() {
        Configurator.setRootLevel(Level.WARN);
        runFixture(RootMuteThrowsFixture.class);
        assertEquals(Level.WARN, ROOT.getLevel());
    }

    @Test
    @DisplayName("Class-level @Mute mutes the root logger for every test in the class")
    void classLevelMuteAnnotationMutesAndRestores() {
        Configurator.setRootLevel(Level.INFO);
        runFixture(ClassLevelRootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Specific class logger is muted; root logger is unaffected")
    void specificClassLoggerMutedAndRestored() {
        Configurator.setRootLevel(Level.INFO);
        Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
        runFixture(SingleClassMuteFixture.class);
        assertEquals(Level.DEBUG, SERVICE_A.getLevel(), "ServiceA logger should be restored");
        assertEquals(Level.INFO,  ROOT.getLevel(),      "Root logger should be unaffected");
    }

    @Test
    @DisplayName("Multiple class loggers are all muted and restored independently")
    void multipleClassLoggersMutedAndRestored() {
        Configurator.setLevel(SERVICE_A.getName(), Level.TRACE);
        Configurator.setLevel(SERVICE_B.getName(), Level.ERROR);
        runFixture(MultipleClassMuteFixture.class);
        assertEquals(Level.TRACE, SERVICE_A.getLevel());
        assertEquals(Level.ERROR, SERVICE_B.getLevel());
    }

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        Configurator.setRootLevel(Level.INFO);
        MuteRestorer restorer = new Log4j2Muter().mute(muteAnnotation());

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
        MuteRestorer restorer = new Log4j2Muter().mute(muteAnnotation(ServiceA.class, ServiceB.class));

        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        assertEquals(Level.OFF, SERVICE_B.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
        assertEquals(Level.ERROR, SERVICE_B.getLevel());
    }

    @Test
    @DisplayName("Muting a class with no explicit config and restoring removes the added config")
    void muteClassWithNoExplicitConfigRestoresInheritance() {
        // Remove any existing explicit config for ServiceA so it inherits from root
        Configurator.setRootLevel(Level.INFO);
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(SERVICE_A.getName());
        ctx.updateLoggers();

        Level effectiveBefore = SERVICE_A.getLevel(); // inherited from root

        MuteRestorer restorer = new Log4j2Muter().mute(muteAnnotation(ServiceA.class));
        assertEquals(Level.OFF, SERVICE_A.getLevel());

        restorer.restore();
        assertEquals(effectiveBefore, SERVICE_A.getLevel(), "Level should be restored to inherited value");
    }

    // ---------- Fixture classes ----------

    static class RootMuteFixture {
        @Test @Mute void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
        }
    }

    static class RootMuteThrowsFixture {
        @Test @Mute void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
            throw new RuntimeException("expected");
        }
    }

    @Mute
    static class ClassLevelRootMuteFixture {
        @Test void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
        }
    }

    static class SingleClassMuteFixture {
        @Test @Mute(classes = ServiceA.class) void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.INFO, ROOT.getLevel());
        }
    }

    static class MultipleClassMuteFixture {
        @Test @Mute(classes = {ServiceA.class, ServiceB.class}) void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.OFF, SERVICE_B.getLevel());
        }
    }

    // ---------- Dummy service marker classes ----------

    private static class ServiceA {}
    private static class ServiceB {}

    // ---------- Helper ----------

    private static void runFixture(Class<?> fixtureClass) {
        LauncherFactory.create().execute(
                LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(fixtureClass))
                        .build());
    }

    private static Mute muteAnnotation(Class<?>... classes) {
        return (Mute) java.lang.reflect.Proxy.newProxyInstance(
                Mute.class.getClassLoader(),
                new Class<?>[] {Mute.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "classes" -> classes;
                    case "annotationType" -> Mute.class;
                    case "toString" -> "@Mute(classes=" + java.util.Arrays.toString(classes) + ")";
                    case "hashCode" -> 31 * Mute.class.hashCode() + java.util.Arrays.hashCode(classes);
                    case "equals" -> args[0] instanceof Mute other
                            && java.util.Arrays.equals(classes, other.classes());
                    default -> method.getDefaultValue();
                });
    }
}
