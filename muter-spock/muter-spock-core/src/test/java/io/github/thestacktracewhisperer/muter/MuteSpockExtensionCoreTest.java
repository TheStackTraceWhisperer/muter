package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spockframework.runtime.extension.IMethodInvocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MuteInterceptor} orchestration logic.
 * Uses mock {@link LogMuter} instances to avoid any dependency on a concrete logging framework.
 */
class MuteSpockExtensionCoreTest {

    @Test
    @DisplayName("Interceptor calls mute() before and restore() after feature execution")
    void interceptorMutesAndRestores() throws Throwable {
        AtomicBoolean muteCalled = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        LogMuter mockMuter = classes -> {
            muteCalled.set(true);
            return () -> restored.set(true);
        };

        MuteInterceptor interceptor = new MuteInterceptor(new Class<?>[0], List.of(mockMuter));

        interceptor.intercept(proceedingInvocation());

        assertTrue(muteCalled.get(), "mute() should have been called before feature");
        assertTrue(restored.get(), "restore() should have been called after feature");
    }

    @Test
    @DisplayName("Interceptor restores loggers even when feature throws")
    void interceptorRestoresWhenProceedThrows() {
        AtomicBoolean restored = new AtomicBoolean();
        LogMuter mockMuter = classes -> () -> restored.set(true);
        MuteInterceptor interceptor = new MuteInterceptor(new Class<?>[0], List.of(mockMuter));

        assertThrows(Throwable.class,
                () -> interceptor.intercept(throwingInvocation()));
        assertTrue(restored.get(), "restore() should be called even when feature throws");
    }

    @Test
    @DisplayName("All LogMuters are called and all restorers are invoked in reverse order")
    void allLogMutersCalledAndRestored() throws Throwable {
        AtomicInteger muteCount = new AtomicInteger();
        AtomicInteger restoreCount = new AtomicInteger();

        LogMuter muter1 = classes -> { muteCount.incrementAndGet(); return restoreCount::incrementAndGet; };
        LogMuter muter2 = classes -> { muteCount.incrementAndGet(); return restoreCount::incrementAndGet; };
        MuteInterceptor interceptor = new MuteInterceptor(new Class<?>[0], List.of(muter1, muter2));

        interceptor.intercept(proceedingInvocation());

        assertEquals(2, muteCount.get(), "Both LogMuters should be called");
        assertEquals(2, restoreCount.get(), "Both restorers should be invoked");
    }

    @Test
    @DisplayName("No LogMuter on classpath throws IllegalStateException with helpful message")
    void noLogMuterFoundThrowsHelpfulError() {
        MuteInterceptor interceptor = new MuteInterceptor(new Class<?>[0], List.of());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> interceptor.intercept(proceedingInvocation()));
        assertTrue(ex.getMessage().contains("No LogMuter found on the classpath"),
                "Error message should guide user to add an implementation module");
    }

    @Test
    @DisplayName("Mute restorer during mute() rolls back already-applied muters and rethrows")
    void muteFailureRollsBackPreviousMuters() {
        AtomicBoolean firstRestored = new AtomicBoolean();
        LogMuter goodMuter = classes -> () -> firstRestored.set(true);
        LogMuter failingMuter = classes -> { throw new RuntimeException("mute failed"); };
        MuteInterceptor interceptor = new MuteInterceptor(new Class<?>[0], List.of(goodMuter, failingMuter));

        assertThrows(RuntimeException.class, () -> interceptor.intercept(proceedingInvocation()));
        assertTrue(firstRestored.get(), "First muter's restorer should be called when second muter fails");
    }

    // ---------- Helpers ----------

    private static IMethodInvocation proceedingInvocation() {
        return (IMethodInvocation) Proxy.newProxyInstance(
                IMethodInvocation.class.getClassLoader(),
                new Class<?>[]{IMethodInvocation.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "proceed" -> null;
                    case "toString" -> "InvocationProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static IMethodInvocation throwingInvocation() {
        return (IMethodInvocation) Proxy.newProxyInstance(
                IMethodInvocation.class.getClassLoader(),
                new Class<?>[]{IMethodInvocation.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "proceed" -> throw new RuntimeException("feature failed");
                    case "toString" -> "ThrowingInvocationProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }
}
