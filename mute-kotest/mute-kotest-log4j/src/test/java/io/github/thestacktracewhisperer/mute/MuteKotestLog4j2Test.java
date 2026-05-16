package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-kotest-log4j
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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Log4j 2 implementation of the Kotest mute listener.
 */
class MuteKotestLog4j2Test {

  private static final org.apache.logging.log4j.core.Logger ROOT =
    (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
  private static final org.apache.logging.log4j.core.Logger SERVICE_A =
    (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ServiceA.class);

  private Level savedRoot;
  private Level savedServiceA;

  @BeforeEach
  void saveLevels() {
    savedRoot = ROOT.getLevel();
    savedServiceA = SERVICE_A.getLevel();
  }

  @AfterEach
  void restoreLevels() {
    Configurator.setRootLevel(savedRoot);
    restoreLevel(SERVICE_A, savedServiceA);
  }

  private static void restoreLevel(org.apache.logging.log4j.core.Logger logger, Level savedLevel) {
    if (savedLevel != null) {
      Configurator.setLevel(logger.getName(), savedLevel);
    } else {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      ctx.getConfiguration().removeLogger(logger.getName());
      ctx.updateLoggers();
    }
  }

  // ---------- Log4j2Mute unit tests ----------

  @Test
  @DisplayName("Direct mute with empty classes mutes root and restores level")
  void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
    Configurator.setRootLevel(Level.INFO);
    LogRestorer restorer = new Log4j2Mute().mute(new Class<?>[0]);
    assertEquals(Level.OFF, ROOT.getLevel());
    restorer.restore();
    assertEquals(Level.INFO, ROOT.getLevel());
  }

  @Test
  @DisplayName("Direct mute with classes mutes only selected loggers and restores")
  void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
    Configurator.setRootLevel(Level.INFO);
    Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
    LogRestorer restorer = new Log4j2Mute().mute(new Class<?>[]{ServiceA.class});
    assertEquals(Level.INFO, ROOT.getLevel());
    assertEquals(Level.OFF, SERVICE_A.getLevel());
    restorer.restore();
    assertEquals(Level.INFO, ROOT.getLevel());
    assertEquals(Level.DEBUG, SERVICE_A.getLevel());
  }

  @Test
  @DisplayName("Muting a class with no explicit config restores inheritance")
  void muteClassWithNoExplicitConfigRestoresInheritance() {
    Configurator.setRootLevel(Level.INFO);
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    ctx.getConfiguration().removeLogger(SERVICE_A.getName());
    ctx.updateLoggers();

    Level effectiveBefore = SERVICE_A.getLevel();

    LogRestorer restorer = new Log4j2Mute().mute(new Class<?>[]{ServiceA.class});
    assertEquals(Level.OFF, SERVICE_A.getLevel());

    restorer.restore();
    assertEquals(effectiveBefore, SERVICE_A.getLevel(), "Level should be restored to inherited value");
  }

  // ---------- MuteKotestListener + Log4j2Mute integration ----------

  @Test
  @DisplayName("MuteKotestListener with Log4j2Mute mutes root and restores for @Mute spec")
  void listenerMutesRootLoggerAndRestores() {
    Configurator.setRootLevel(Level.INFO);
    MuteKotestListener listener = new MuteKotestListener(List.of(new Log4j2Mute()));
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
    Configurator.setRootLevel(Level.INFO);
    MuteKotestListener listener = new MuteKotestListener(List.of(new Log4j2Mute()));

    listener.muteBefore(UnmutedSpec.class);
    assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should not be muted for un-annotated spec");
    listener.restoreAfter();
    assertEquals(Level.INFO, ROOT.getLevel());
  }

  @Test
  @DisplayName("MuteKotestListener with Log4j2Mute mutes specific class logger")
  void listenerMutesSpecificClassLogger() {
    Configurator.setRootLevel(Level.INFO);
    Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
    MuteKotestListener listener = new MuteKotestListener(List.of(new Log4j2Mute()));

    listener.muteBefore(MutedWithClassSpec.class);
    assertEquals(Level.OFF, SERVICE_A.getLevel());
    assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
    listener.restoreAfter();

    assertEquals(Level.DEBUG, SERVICE_A.getLevel());
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

  private static class ServiceB {
  }

  // ---------- Additional coverage tests ----------

  @Test
  @DisplayName("Non-Log4j 2 context supplier throws IllegalStateException with helpful message")
  void nonLog4j2ContextFailsFast() {
    Log4j2Mute mute = new Log4j2Mute(() -> "not-a-log4j-context");
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> mute.mute(new Class<?>[0]));
    assertTrue(ex.getMessage().contains("mute-log4j"));
  }

  @Test
  @DisplayName("Null context supplier throws IllegalStateException mentioning null")
  void nullContextFailsFast() {
    Log4j2Mute mute = new Log4j2Mute(() -> null);
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> mute.mute(new Class<?>[0]));
    assertTrue(ex.getMessage().contains("null"));
  }

  @Test
  @DisplayName("Duplicate class in target list is processed only once")
  void duplicateClassIsDeduplicatedAndRestored() {
    Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
    LogRestorer restorer = new Log4j2Mute()
      .mute(new Class<?>[]{ServiceA.class, ServiceA.class});
    assertEquals(Level.OFF, SERVICE_A.getLevel());
    restorer.restore();
    assertEquals(Level.DEBUG, SERVICE_A.getLevel());
  }
}
