package io.github.thestacktracewhisperer.muter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.*;
import org.spockframework.runtime.extension.IMethodInvocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Log4j 2 implementation of the Spock muter extension.
 */
class MuteSpockLog4j2Test {

    private static final org.apache.logging.log4j.core.Logger ROOT =
            (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
    private static final org.apache.logging.log4j.core.Logger SERVICE_A =
            (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ServiceA.class);

    private Level savedRoot;
    private Level savedServiceA;

    @BeforeEach
    void saveLevels() {
        savedRoot     = ROOT.getLevel();
        savedServiceA = SERVICE_A.getLevel();
    }

    @AfterEach
    void restoreLevels() {
        Configurator.setRootLevel(savedRoot);
        restoreLevel(SERVICE_A, savedServiceA);
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

    // ---------- Log4j2Muter unit tests ----------

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
        MuteRestorer restorer = new Log4j2Muter().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
    }

    @Test
    @DisplayName("Muting a class with no explicit config restores inheritance")
    void muteClassWithNoExplicitConfigRestoresInheritance() {
        Configurator.setRootLevel(Level.INFO);
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(SERVICE_A.getName());
        ctx.updateLoggers();

        Level effectiveBefore = SERVICE_A.getLevel();

        MuteRestorer restorer = new Log4j2Muter().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.OFF, SERVICE_A.getLevel());

        restorer.restore();
        assertEquals(effectiveBefore, SERVICE_A.getLevel(), "Level should be restored to inherited value");
    }

    // ---------- MuteInterceptor + Log4j2Muter integration ----------

    @Test
    @DisplayName("MuteInterceptor with Log4j2Muter mutes root and restores after proceed")
    void interceptorMutesRootLoggerAndRestores() throws Throwable {
        Configurator.setRootLevel(Level.INFO);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new Log4j2Muter()));
        AtomicBoolean proceedCalled = new AtomicBoolean();

        interceptor.intercept(invocationCapturing(() -> {
            proceedCalled.set(true);
            assertEquals(Level.OFF, ROOT.getLevel(), "Root logger should be muted during feature");
        }));

        assertTrue(proceedCalled.get());
        assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should be restored after feature");
    }

    @Test
    @DisplayName("MuteInterceptor with Log4j2Muter restores logger even when feature throws")
    void interceptorRestoresWhenFeatureThrows() {
        Configurator.setRootLevel(Level.WARN);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new Log4j2Muter()));

        assertThrows(RuntimeException.class, () ->
                interceptor.intercept(invocationCapturing(() -> {
                    throw new RuntimeException("expected failure");
                })));

        assertEquals(Level.WARN, ROOT.getLevel(), "Root logger should be restored after failure");
    }

    @Test
    @DisplayName("MuteInterceptor with Log4j2Muter mutes specific class logger")
    void interceptorMutesSpecificClassLogger() throws Throwable {
        Configurator.setRootLevel(Level.INFO);
        Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[]{ServiceA.class}, List.of(new Log4j2Muter()));

        interceptor.intercept(invocationCapturing(() -> {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
        }));

        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    // ---------- Dummy service classes ----------

    private static class ServiceA {}

    // ---------- Helper ----------

    private static IMethodInvocation invocationCapturing(ProceedAction action) {
        return (IMethodInvocation) Proxy.newProxyInstance(
                IMethodInvocation.class.getClassLoader(),
                new Class<?>[]{IMethodInvocation.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "proceed" -> { action.run(); yield null; }
                    case "toString" -> "InvocationProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @FunctionalInterface
    interface ProceedAction {
        void run() throws Throwable;
    }
}
