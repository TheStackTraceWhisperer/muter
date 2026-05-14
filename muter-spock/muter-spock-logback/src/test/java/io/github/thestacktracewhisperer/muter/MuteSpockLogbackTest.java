package io.github.thestacktracewhisperer.muter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.spockframework.runtime.extension.IMethodInvocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Logback implementation of the Spock muter extension.
 */
class MuteSpockLogbackTest {

    private static final Logger ROOT =
            (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private static final Logger SERVICE_A =
            (Logger) LoggerFactory.getLogger(ServiceA.class);

    private Level savedRoot;
    private Level savedServiceA;

    @BeforeEach
    void saveLevels() {
        savedRoot     = ROOT.getLevel();
        savedServiceA = SERVICE_A.getLevel();
    }

    @AfterEach
    void restoreLevels() {
        ROOT.setLevel(savedRoot);
        SERVICE_A.setLevel(savedServiceA);
    }

    // ---------- LogbackMuter unit tests ----------

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        ROOT.setLevel(Level.INFO);
        MuteRestorer restorer = new LogbackMuter().mute(new Class<?>[0]);
        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        MuteRestorer restorer = new LogbackMuter().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
    }

    @Test
    @DisplayName("Null (inherited) logger level is correctly restored to null after muting")
    void nullInheritedLevelIsRestored() {
        SERVICE_A.setLevel(null);
        assertNull(SERVICE_A.getLevel());
        MuteRestorer restorer = new LogbackMuter().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        restorer.restore();
        assertNull(SERVICE_A.getLevel());
    }

    @Test
    @DisplayName("Missing Logback binding fails fast with helpful error")
    void missingLogbackBindingFailsFast() {
        LogbackMuter muter = new LogbackMuter(() -> new Object());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> muter.mute(new Class<?>[0]));
        assertEquals(
                "muter-spock-logback requires Logback Classic on the classpath; found: java.lang.Object",
                ex.getMessage());
    }

    @Test
    @DisplayName("Null logger factory fails fast with null type in error")
    void nullLoggerFactoryFailsFast() {
        LogbackMuter muter = new LogbackMuter(() -> null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> muter.mute(new Class<?>[0]));
        assertEquals(
                "muter-spock-logback requires Logback Classic on the classpath; found: null",
                ex.getMessage());
    }

    // ---------- MuteInterceptor + LogbackMuter integration ----------

    @Test
    @DisplayName("MuteInterceptor with LogbackMuter mutes root and restores after proceed")
    void interceptorMutesRootLoggerAndRestores() throws Throwable {
        ROOT.setLevel(Level.INFO);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new LogbackMuter()));
        AtomicBoolean proceedCalled = new AtomicBoolean();

        interceptor.intercept(invocationCapturing(() -> {
            proceedCalled.set(true);
            assertEquals(Level.OFF, ROOT.getLevel(), "Root logger should be muted during feature");
        }));

        assertTrue(proceedCalled.get());
        assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should be restored after feature");
    }

    @Test
    @DisplayName("MuteInterceptor with LogbackMuter restores logger even when feature throws")
    void interceptorRestoresWhenFeatureThrows() {
        ROOT.setLevel(Level.WARN);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new LogbackMuter()));

        assertThrows(RuntimeException.class, () ->
                interceptor.intercept(invocationCapturing(() -> {
                    throw new RuntimeException("expected failure");
                })));

        assertEquals(Level.WARN, ROOT.getLevel(), "Root logger should be restored after failure");
    }

    @Test
    @DisplayName("MuteInterceptor with LogbackMuter mutes specific class logger")
    void interceptorMutesSpecificClassLogger() throws Throwable {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[]{ServiceA.class}, List.of(new LogbackMuter()));

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
