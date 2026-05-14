package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.*;
import org.spockframework.runtime.extension.IMethodInvocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the JUL implementation of the Spock muter extension.
 */
class MuteSpockJulTest {

    private static final Logger ROOT = Logger.getLogger("");
    private static final Logger SERVICE_A = Logger.getLogger(ServiceA.class.getName());

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

    // ---------- JulMuter unit tests ----------

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
        MuteRestorer restorer = new JulMuter().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.FINE, SERVICE_A.getLevel());
    }

    @Test
    @DisplayName("Null (inherited) logger level is correctly restored to null after muting")
    void nullInheritedLevelIsRestored() {
        SERVICE_A.setLevel(null);
        assertNull(SERVICE_A.getLevel());
        MuteRestorer restorer = new JulMuter().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        restorer.restore();
        assertNull(SERVICE_A.getLevel());
    }

    // ---------- MuteInterceptor + JulMuter integration ----------

    @Test
    @DisplayName("MuteInterceptor with JulMuter mutes root and restores after proceed")
    void interceptorMutesRootLoggerAndRestores() throws Throwable {
        ROOT.setLevel(Level.INFO);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new JulMuter()));
        AtomicBoolean proceedCalled = new AtomicBoolean();

        interceptor.intercept(invocationCapturing(() -> {
            proceedCalled.set(true);
            assertEquals(Level.OFF, ROOT.getLevel(), "Root logger should be muted during feature");
        }));

        assertTrue(proceedCalled.get());
        assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should be restored after feature");
    }

    @Test
    @DisplayName("MuteInterceptor with JulMuter restores logger even when feature throws")
    void interceptorRestoresWhenFeatureThrows() {
        ROOT.setLevel(Level.WARNING);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new JulMuter()));

        assertThrows(RuntimeException.class, () ->
                interceptor.intercept(invocationCapturing(() -> {
                    throw new RuntimeException("expected failure");
                })));

        assertEquals(Level.WARNING, ROOT.getLevel(), "Root logger should be restored after failure");
    }

    @Test
    @DisplayName("MuteInterceptor with JulMuter mutes specific class logger")
    void interceptorMutesSpecificClassLogger() throws Throwable {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.FINE);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[]{ServiceA.class}, List.of(new JulMuter()));

        interceptor.intercept(invocationCapturing(() -> {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
        }));

        assertEquals(Level.FINE, SERVICE_A.getLevel());
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
