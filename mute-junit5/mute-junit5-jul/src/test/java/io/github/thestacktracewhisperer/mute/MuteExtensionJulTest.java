package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-junit5-jul
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

import org.junit.jupiter.api.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for the JUL implementation of the mute extension.
 */
class MuteExtensionJulTest {

    private static final Logger ROOT = Logger.getLogger("");
    private static final Logger SERVICE_A = Logger.getLogger(ServiceA.class.getName());
    private static final Logger SERVICE_B = Logger.getLogger(ServiceB.class.getName());

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
        ROOT.setLevel(savedRoot);
        SERVICE_A.setLevel(savedServiceA);
        SERVICE_B.setLevel(savedServiceB);
    }

    @Test
    @DisplayName("Root logger is muted during @Mute test and restored afterward")
    void rootLoggerMutedAndRestored() {
        ROOT.setLevel(Level.INFO);
        runFixture(RootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Root logger is restored even after a @Mute test that throws")
    void rootLoggerRestoredAfterFailingTest() {
        ROOT.setLevel(Level.WARNING);
        runFixture(RootMuteThrowsFixture.class);
        assertEquals(Level.WARNING, ROOT.getLevel());
    }

    @Test
    @DisplayName("Class-level @Mute mutes the root logger for every test in the class")
    void classLevelMuteAnnotationMutesAndRestores() {
        ROOT.setLevel(Level.INFO);
        runFixture(ClassLevelRootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Specific class logger is muted; root logger is unaffected")
    void specificClassLoggerMutedAndRestored() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.FINE);
        runFixture(SingleClassMuteFixture.class);
        assertEquals(Level.FINE, SERVICE_A.getLevel(), "ServiceA logger should be restored");
        assertEquals(Level.INFO, ROOT.getLevel(),      "Root logger should be unaffected");
    }

    @Test
    @DisplayName("Multiple class loggers are all muted and restored independently")
    void multipleClassLoggersMutedAndRestored() {
        SERVICE_A.setLevel(Level.FINEST);
        SERVICE_B.setLevel(Level.SEVERE);
        runFixture(MultipleClassMuteFixture.class);
        assertEquals(Level.FINEST, SERVICE_A.getLevel());
        assertEquals(Level.SEVERE, SERVICE_B.getLevel());
    }

    @Test
    @DisplayName("A null (inherited) logger level is correctly restored to null after muting")
    void nullInheritedLevelIsRestored() {
        SERVICE_A.setLevel(null);
        assertNull(SERVICE_A.getLevel());
        runFixture(NullLevelMuteFixture.class);
        assertNull(SERVICE_A.getLevel(), "Null (inherited) level should be restored after muting");
    }

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        ROOT.setLevel(Level.INFO);
        LogRestorer restorer = new JulMute().mute(new Class<?>[0]);

        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.FINE);
        SERVICE_B.setLevel(Level.SEVERE);
        LogRestorer restorer = new JulMute().mute(new Class<?>[] {ServiceA.class, ServiceB.class});

        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        assertEquals(Level.OFF, SERVICE_B.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.FINE, SERVICE_A.getLevel());
        assertEquals(Level.SEVERE, SERVICE_B.getLevel());
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

    static class NullLevelMuteFixture {
        @Test @Mute(classes = ServiceA.class) void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
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
}
