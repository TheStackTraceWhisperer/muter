package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MuteKotestListener} orchestration logic.
 * Uses mock {@link LogMuter} instances to avoid any dependency on a concrete logging framework.
 */
class MuteKotestListenerCoreTest {

    @Test
    @DisplayName("Spec without @Mute annotation: LogMuter.mute() is never called")
    void specWithoutMuteAnnotationIsIgnored() {
        AtomicBoolean muteCalled = new AtomicBoolean();
        LogMuter mockMuter = classes -> { muteCalled.set(true); return () -> {}; };
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMuter));
        Object executionKey = new Object();

        listener.muteBefore(executionKey, UnmutedSpec.class);
        listener.restoreAfter(executionKey);

        assertFalse(muteCalled.get(), "mute() should not be called for un-annotated spec");
    }

    @Test
    @DisplayName("Spec with @Mute annotation: LogMuter.mute() is called before and restored after")
    void specWithMuteAnnotationCallsLogMuter() {
        AtomicBoolean muteCalled = new AtomicBoolean();
        AtomicBoolean restored = new AtomicBoolean();
        LogMuter mockMuter = classes -> {
            muteCalled.set(true);
            return () -> restored.set(true);
        };
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMuter));
        Object executionKey = new Object();

        listener.muteBefore(executionKey, MutedSpec.class);
        assertTrue(muteCalled.get(), "mute() should be called");

        listener.restoreAfter(executionKey);
        assertTrue(restored.get(), "restore() should be called");
    }

    @Test
    @DisplayName("All LogMuters are called and all restorers are invoked")
    void allLogMutersCalledAndRestored() {
        AtomicInteger muteCount = new AtomicInteger();
        AtomicInteger restoreCount = new AtomicInteger();

        LogMuter muter1 = classes -> { muteCount.incrementAndGet(); return restoreCount::incrementAndGet; };
        LogMuter muter2 = classes -> { muteCount.incrementAndGet(); return restoreCount::incrementAndGet; };
        MuteKotestListener listener = new MuteKotestListener(List.of(muter1, muter2));
        Object executionKey = new Object();

        listener.muteBefore(executionKey, MutedSpec.class);
        assertEquals(2, muteCount.get());

        listener.restoreAfter(executionKey);
        assertEquals(2, restoreCount.get());
    }

    @Test
    @DisplayName("No LogMuter on classpath throws IllegalStateException with helpful message")
    void noLogMuterFoundThrowsHelpfulError() {
        MuteKotestListener listener = new MuteKotestListener(List.of());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> listener.muteBefore(new Object(), MutedSpec.class));
        assertTrue(ex.getMessage().contains("No LogMuter found on the classpath"),
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
        LogMuter mockMuter = classes -> () -> restored.set(true);
        MuteKotestListener listener = new MuteKotestListener(List.of(mockMuter));
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
    @DisplayName("Mute failure rolls back already-applied muters and rethrows")
    void muteFailureRollsBackPreviousMuters() {
        AtomicBoolean firstRestored = new AtomicBoolean();
        LogMuter goodMuter = classes -> () -> firstRestored.set(true);
        LogMuter failingMuter = classes -> { throw new RuntimeException("mute failed"); };
        MuteKotestListener listener = new MuteKotestListener(List.of(goodMuter, failingMuter));
        Object executionKey = new Object();

        assertThrows(RuntimeException.class, () -> listener.muteBefore(executionKey, MutedSpec.class));
        assertTrue(firstRestored.get(), "First muter's restorer should be called when second muter fails");
    }

    // ---------- Fixture classes ----------

    static class UnmutedSpec {}

    @Mute
    static class MutedSpec {}
}
