package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-kotest-logback
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Logback implementation of the Kotest mute listener.
 */
class MuteKotestLogbackTest {

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

    // ---------- LogbackMute unit tests ----------

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        ROOT.setLevel(Level.INFO);
        MuteRestorer restorer = new LogbackMute().mute(new Class<?>[0]);
        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        MuteRestorer restorer = new LogbackMute().mute(new Class<?>[]{ServiceA.class});
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
        MuteRestorer restorer = new LogbackMute().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        restorer.restore();
        assertNull(SERVICE_A.getLevel());
    }

    @Test
    @DisplayName("Missing Logback binding fails fast with helpful error")
    void missingLogbackBindingFailsFast() {
        LogbackMute mute = new LogbackMute(() -> new Object());
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> mute.mute(new Class<?>[0]));
        assertEquals(
                "mute-kotest-logback requires Logback Classic on the classpath; found: java.lang.Object",
                ex.getMessage());
    }

    @Test
    @DisplayName("Null logger factory fails fast with null type in error")
    void nullLoggerFactoryFailsFast() {
        LogbackMute mute = new LogbackMute(() -> null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> mute.mute(new Class<?>[0]));
        assertEquals(
                "mute-kotest-logback requires Logback Classic on the classpath; found: null",
                ex.getMessage());
    }

    // ---------- MuteKotestListener + LogbackMute integration ----------

    @Test
    @DisplayName("MuteKotestListener with LogbackMute mutes root and restores for @Mute spec")
    void listenerMutesRootLoggerAndRestores() {
        ROOT.setLevel(Level.INFO);
        MuteKotestListener listener = new MuteKotestListener(List.of(new LogbackMute()));
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
        MuteKotestListener listener = new MuteKotestListener(List.of(new LogbackMute()));

        listener.muteBefore(UnmutedSpec.class);
        assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should not be muted for un-annotated spec");
        listener.restoreAfter();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("MuteKotestListener with LogbackMute mutes specific class logger")
    void listenerMutesSpecificClassLogger() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        MuteKotestListener listener = new MuteKotestListener(List.of(new LogbackMute()));

        listener.muteBefore(MutedWithClassSpec.class);
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
        listener.restoreAfter();

        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
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
