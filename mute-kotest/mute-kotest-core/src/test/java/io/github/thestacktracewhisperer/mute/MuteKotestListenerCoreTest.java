package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-kotest-core
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

import io.kotest.core.annotation.AutoScan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MuteKotestListener} orchestration logic.
 * Uses mock {@link LogMute} instances to avoid any dependency on a concrete logging framework.
 */
class MuteKotestListenerCoreTest {

    @Test
    @DisplayName("MuteKotestListener is properly configured with @AutoScan")
    void listenerHasAutoScanAnnotation() {
        AutoScan autoScan = MuteKotestListener.class.getAnnotation(AutoScan.class);
        assertNotNull(autoScan, "MuteKotestListener should be annotated with @AutoScan for auto-discovery");
    }

    @Test
    @DisplayName("Spec without @Mute annotation: LogMute.mute() is never called")
    void specWithoutMuteAnnotationIsIgnored() {
        AtomicBoolean muteCalled = new AtomicBoolean();
        LogMute mockMute = classes -> { muteCalled.set(true); return () -> {}; };
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMute));
        Object executionKey = new Object();

        listener.muteBefore(executionKey, UnmutedSpec.class);
        listener.restoreAfter(executionKey);

        assertFalse(muteCalled.get(), "mute() should not be called for un-annotated spec");
    }

    @Test
    @DisplayName("Spec with @Mute annotation: LogMute.mute() is called before and restored after")
    void specWithMuteAnnotationCallsLogMute() {
        AtomicBoolean muteCalled = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        LogMute mockMute = classes -> {
            muteCalled.set(true);
            return () -> restored.set(true);
        };
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMute));
        Object executionKey = new Object();

        listener.muteBefore(executionKey, MutedSpec.class);
        assertTrue(muteCalled.get(), "mute() should be called");

        listener.restoreAfter(executionKey);
        assertTrue(restored.get(), "restore() should be called");
    }

    @Test
    @DisplayName("All LogMutes are called and all restorers are invoked")
    void allLogMutesCalledAndRestored() {
        AtomicInteger muteCount = new AtomicInteger();
        AtomicInteger restoreCount = new AtomicInteger();

        LogMute mute1 = classes -> { muteCount.incrementAndGet(); return restoreCount::incrementAndGet; };
        LogMute mute2 = classes -> { muteCount.incrementAndGet(); return restoreCount::incrementAndGet; };
        MuteKotestListener listener = new MuteKotestListener(List.of(mute1, mute2));
        Object executionKey = new Object();

        listener.muteBefore(executionKey, MutedSpec.class);
        assertEquals(2, muteCount.get());

        listener.restoreAfter(executionKey);
        assertEquals(2, restoreCount.get());
    }

    @Test
    @DisplayName("No LogMute on classpath throws IllegalStateException with helpful message")
    void noLogMuteFoundThrowsHelpfulError() {
        MuteKotestListener listener = new MuteKotestListener(List.of());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> listener.muteBefore(new Object(), MutedSpec.class));
        assertTrue(ex.getMessage().contains("No LogMute found on the classpath"),
                "Error message should guide user to add an implementation module");
    }

    @Test
    @DisplayName("restoreAfter() is a no-op when no mute was performed")
    void restoreAfterIsNoOpWhenNoMute() {
        MuteKotestListener listener = new MuteKotestListener(List.of());
        assertDoesNotThrow(() -> listener.restoreAfter(new Object()), "restoreAfter() should not throw when nothing was muted");
    }

    @Test
    @DisplayName("Restorer is resolved by execution key, not current thread")
    void restorerIsBoundToExecutionKey() throws InterruptedException {
        AtomicBoolean restored = new AtomicBoolean();
        LogMute mockMute = classes -> () -> restored.set(true);
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMute));
        Object executionKey = new Object();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread beforeThread = new Thread(() -> {
            try {
                listener.muteBefore(executionKey, MutedSpec.class);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        beforeThread.start();
        beforeThread.join();

        Thread afterThread = new Thread(() -> {
            try {
                listener.restoreAfter(executionKey);
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        afterThread.start();
        afterThread.join();

        assertNull(failure.get(), "cross-thread listener calls should not fail");
        assertTrue(restored.get(), "restore() should still run when afterEach executes on a different thread");
    }

    @Test
    @DisplayName("Mute failure rolls back already-applied mutes and rethrows")
    void muteFailureRollsBackPreviousMutes() {
        AtomicBoolean firstRestored = new AtomicBoolean();
        LogMute goodMute = classes -> () -> firstRestored.set(true);
        LogMute failingMute = classes -> { throw new RuntimeException("mute failed"); };
        MuteKotestListener listener = new MuteKotestListener(List.of(goodMute, failingMute));
        Object executionKey = new Object();

        assertThrows(RuntimeException.class, () -> listener.muteBefore(executionKey, MutedSpec.class));
        assertTrue(firstRestored.get(), "First mute's restorer should be called when second mute fails");
    }

    // ---------- Fixture classes ----------

    static class UnmutedSpec {}

    @Mute
    static class MutedSpec {}

    // ---------- Additional coverage tests ----------

    @Test
    @DisplayName("Public no-arg constructor can be instantiated without error")
    void publicConstructorInstantiates() {
        assertDoesNotThrow((org.junit.jupiter.api.function.Executable) MuteKotestListener::new);
    }

    @Test
    @DisplayName("getName() returns the listener name")
    void getNameReturnsListenerName() {
        MuteKotestListener listener = new MuteKotestListener(List.of());
        assertNotNull(listener.getName());
    }

    @Test
    @DisplayName("Deprecated muteBefore(Class) delegates to keyed variant using current thread")
    @SuppressWarnings("deprecation")
    void deprecatedMuteBeforeDelegates() {
        AtomicBoolean muteCalled = new AtomicBoolean();
        LogMute mockMute = classes -> { muteCalled.set(true); return () -> {}; };
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMute));

        listener.muteBefore(MutedSpec.class);
        assertTrue(muteCalled.get(), "deprecated muteBefore should trigger mute");
    }

    @Test
    @DisplayName("Deprecated restoreAfter() delegates to keyed variant using current thread")
    @SuppressWarnings("deprecation")
    void deprecatedRestoreAfterDelegates() {
        AtomicBoolean restored = new AtomicBoolean();
        LogMute mockMute = classes -> () -> restored.set(true);
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMute));

        listener.muteBefore(MutedSpec.class);
        listener.restoreAfter();
        assertTrue(restored.get(), "deprecated restoreAfter() should trigger restore via thread key");
    }

    @Test
    @DisplayName("Calling muteBefore twice with the same key restores the previous restorer")
    void secondMuteBeforeRestoresPreviousRestorer() {
        AtomicBoolean firstRestored = new AtomicBoolean();
        // One listener with one mute; calling muteBefore twice on the same key
        // triggers the "previous != null" branch inside restorerHolder.compute().
        MuteKotestListener listener = new MuteKotestListener(
                List.of(classes -> () -> firstRestored.set(true)));
        Object sameKey = new Object();

        listener.muteBefore(sameKey, MutedSpec.class);
        listener.muteBefore(sameKey, MutedSpec.class); // should restore previous restorer

        assertTrue(firstRestored.get(), "previous restorer should be invoked on second muteBefore with same key");
    }
}
