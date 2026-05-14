package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the JUL implementation of the Kotest muter listener.
 */
class MuteKotestJulTest {

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

    // ---------- MuteKotestListener + JulMuter integration ----------

    @Test
    @DisplayName("MuteKotestListener with JulMuter mutes root and restores for @Mute spec")
    void listenerMutesRootLoggerAndRestores() {
        ROOT.setLevel(Level.INFO);
        MuteKotestListener listener = new MuteKotestListener(List.of(new JulMuter()));
        AtomicBoolean mutedDuringTest = new AtomicBoolean();

        listener.muteBefore(MutedSpec.class);
        mutedDuringTest.set(ROOT.getLevel() == Level.OFF);
        listener.restoreAfter();

        assertTrue(mutedDuringTest.get(), "Root logger should be muted during test");
        assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should be restored after test");
    }

    @Test
    @DisplayName("MuteKotestListener is no-op for spec without @Mute")
    void listenerIsNoOpForUnmutedSpec() {
        ROOT.setLevel(Level.INFO);
        MuteKotestListener listener = new MuteKotestListener(List.of(new JulMuter()));

        listener.muteBefore(UnmutedSpec.class);
        assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should not be muted for un-annotated spec");
        listener.restoreAfter();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("MuteKotestListener with JulMuter mutes specific class logger")
    void listenerMutesSpecificClassLogger() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.FINE);
        MuteKotestListener listener = new MuteKotestListener(List.of(new JulMuter()));

        listener.muteBefore(MutedWithClassSpec.class);
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
        listener.restoreAfter();

        assertEquals(Level.FINE, SERVICE_A.getLevel());
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    // ---------- Fixture spec classes ----------

    @Mute
    static class MutedSpec {}

    @Mute(classes = ServiceA.class)
    static class MutedWithClassSpec {}

    static class UnmutedSpec {}

    private static class ServiceA {}
}
