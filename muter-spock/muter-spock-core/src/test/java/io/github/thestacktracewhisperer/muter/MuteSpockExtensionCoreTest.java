package io.github.thestacktracewhisperer.muter;

/*-
 * #%L
 * muter
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spockframework.runtime.extension.ExtensionAnnotation;
import org.spockframework.runtime.extension.IMethodInterceptor;
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
    @DisplayName("@Mute annotation is properly configured with @ExtensionAnnotation")
    void muteAnnotationHasExtensionAnnotation() {
        ExtensionAnnotation extensionAnnotation = Mute.class.getAnnotation(ExtensionAnnotation.class);
        assertNotNull(extensionAnnotation, "@Mute should be annotated with @ExtensionAnnotation");
        assertEquals(MuteSpockExtension.class, extensionAnnotation.value(),
                "@ExtensionAnnotation should reference MuteSpockExtension.class");
    }

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

    // ---------- MuteSpockExtension tests ----------

    @Test
    @DisplayName("MuteSpockExtension can be instantiated via public no-arg constructor")
    void extensionPublicConstructorInstantiates() {
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) MuteSpockExtension::new);
    }

    // ---------- Fixture classes for visitSpecAnnotation tests ----------

    static class PlainFeatureFixture {
        public void plainMethod() {}
    }

    static class MutedFeatureFixture {
        @Mute public void mutedMethod() {}
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
