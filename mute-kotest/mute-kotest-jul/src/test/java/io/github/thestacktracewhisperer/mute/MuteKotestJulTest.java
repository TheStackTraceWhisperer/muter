package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-kotest-jul
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the JUL implementation of the Kotest mute listener.
 */
class MuteKotestJulTest {

  private static final Logger ROOT = Logger.getLogger("");
  private static final Logger SERVICE_A = Logger.getLogger(ServiceA.class.getName());

  private Level savedRoot;
  private Level savedServiceA;

  @BeforeEach
  void saveLevels() {
    savedRoot = ROOT.getLevel();
    savedServiceA = SERVICE_A.getLevel();
  }

  @AfterEach
  void restoreLevels() {
    ROOT.setLevel(savedRoot);
    SERVICE_A.setLevel(savedServiceA);
  }

  // ---------- JulMute unit tests ----------

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
    LogRestorer restorer = new JulMute().mute(new Class<?>[]{ServiceA.class});
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
    LogRestorer restorer = new JulMute().mute(new Class<?>[]{ServiceA.class});
    assertEquals(Level.OFF, SERVICE_A.getLevel());
    restorer.restore();
    assertNull(SERVICE_A.getLevel());
  }

  // ---------- MuteKotestListener + JulMute integration ----------

  @Test
  @DisplayName("MuteKotestListener with JulMute mutes root and restores for @Mute spec")
  void listenerMutesRootLoggerAndRestores() {
    ROOT.setLevel(Level.INFO);
    MuteKotestListener listener = new MuteKotestListener(List.of(new JulMute()));
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
    MuteKotestListener listener = new MuteKotestListener(List.of(new JulMute()));

    listener.muteBefore(UnmutedSpec.class);
    assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should not be muted for un-annotated spec");
    listener.restoreAfter();
    assertEquals(Level.INFO, ROOT.getLevel());
  }

  @Test
  @DisplayName("MuteKotestListener with JulMute mutes specific class logger")
  void listenerMutesSpecificClassLogger() {
    ROOT.setLevel(Level.INFO);
    SERVICE_A.setLevel(Level.FINE);
    MuteKotestListener listener = new MuteKotestListener(List.of(new JulMute()));

    listener.muteBefore(MutedWithClassSpec.class);
    assertEquals(Level.OFF, SERVICE_A.getLevel());
    assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
    listener.restoreAfter();

    assertEquals(Level.FINE, SERVICE_A.getLevel());
    assertEquals(Level.INFO, ROOT.getLevel());
  }

  // ---------- Fixture spec classes ----------

  @Mute
  static class MutedSpec {
  }

  @Mute(classes = ServiceA.class)
  static class MutedWithClassSpec {
  }

  static class UnmutedSpec {
  }

  private static class ServiceA {
  }
}
