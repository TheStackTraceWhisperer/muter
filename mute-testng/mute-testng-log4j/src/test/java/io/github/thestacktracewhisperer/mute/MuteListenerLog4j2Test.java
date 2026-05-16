package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-testng-log4j
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
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.TestNG;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Log4j 2 implementation of the TestNG mute listener.
 */
class MuteListenerLog4j2Test {

  private static final org.apache.logging.log4j.core.Logger ROOT =
    (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
  private static final org.apache.logging.log4j.core.Logger SERVICE_A =
    (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ServiceA.class);
  private static final org.apache.logging.log4j.core.Logger SERVICE_B =
    (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ServiceB.class);

  private Level savedRoot;
  private Level savedServiceA;
  private Level savedServiceB;

  @BeforeEach
  void saveLevels() {
    savedRoot = ROOT.getLevel();
    savedServiceA = SERVICE_A.getLevel();
    savedServiceB = SERVICE_B.getLevel();
  }

  @AfterEach
  void restoreLevels() {
    Configurator.setRootLevel(savedRoot);
    restoreLevel(SERVICE_A, savedServiceA);
    restoreLevel(SERVICE_B, savedServiceB);
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

  @Test
  @DisplayName("Root logger is muted during @Mute test and restored afterward")
  void rootLoggerMutedAndRestored() {
    Configurator.setRootLevel(Level.INFO);
    runTestNG(RootMuteFixture.class);
    assertEquals(Level.INFO, ROOT.getLevel());
  }

  @Test
  @DisplayName("Root logger is restored even after a @Mute test that throws")
  void rootLoggerRestoredAfterFailingTest() {
    Configurator.setRootLevel(Level.WARN);
    runTestNG(RootMuteThrowsFixture.class, 0, 1);
    assertEquals(Level.WARN, ROOT.getLevel());
  }

  @Test
  @DisplayName("Class-level @Mute mutes the root logger for every test in the class")
  void classLevelMuteAnnotationMutesAndRestores() {
    Configurator.setRootLevel(Level.INFO);
    runTestNG(ClassLevelRootMuteFixture.class);
    assertEquals(Level.INFO, ROOT.getLevel());
  }

  @Test
  @DisplayName("Specific class logger is muted; root logger is unaffected")
  void specificClassLoggerMutedAndRestored() {
    Configurator.setRootLevel(Level.INFO);
    Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
    runTestNG(SingleClassMuteFixture.class);
    assertEquals(Level.DEBUG, SERVICE_A.getLevel(), "ServiceA logger should be restored");
    assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should be unaffected");
  }

  @Test
  @DisplayName("Multiple class loggers are all muted and restored independently")
  void multipleClassLoggersMutedAndRestored() {
    Configurator.setLevel(SERVICE_A.getName(), Level.TRACE);
    Configurator.setLevel(SERVICE_B.getName(), Level.ERROR);
    runTestNG(MultipleClassMuteFixture.class);
    assertEquals(Level.TRACE, SERVICE_A.getLevel());
    assertEquals(Level.ERROR, SERVICE_B.getLevel());
  }

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
    Configurator.setLevel(SERVICE_B.getName(), Level.ERROR);
    LogRestorer restorer = new Log4j2Mute().mute(new Class<?>[]{ServiceA.class, ServiceB.class});

    assertEquals(Level.INFO, ROOT.getLevel());
    assertEquals(Level.OFF, SERVICE_A.getLevel());
    assertEquals(Level.OFF, SERVICE_B.getLevel());
    restorer.restore();
    assertEquals(Level.INFO, ROOT.getLevel());
    assertEquals(Level.DEBUG, SERVICE_A.getLevel());
    assertEquals(Level.ERROR, SERVICE_B.getLevel());
  }

  @Test
  @DisplayName("Public no-arg constructor can be instantiated without error")
  void publicConstructorInstantiates() {
    assertDoesNotThrow((org.junit.jupiter.api.function.Executable) Log4j2Mute::new);
  }

  @Test
  @DisplayName("Non-Log4j 2 context supplier throws IllegalStateException with helpful message")
  void nonLog4j2ContextFailsFast() {
    Log4j2Mute mute = new Log4j2Mute(() -> "not-a-log4j-context");
    IllegalStateException ex = assertThrows(IllegalStateException.class,
      () -> mute.mute(new Class<?>[0]));
    assertTrue(ex.getMessage().contains("mute-testng-log4j"),
      "error message should mention the module name");
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
  @DisplayName("Muting a class with no explicit config removes the config entry on restore")
  void muteClassWithNoExplicitConfigRestoresInheritance() {
    LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
    // Ensure SERVICE_A has NO explicit logger config
    ctx.getConfiguration().removeLogger(SERVICE_A.getName());
    ctx.updateLoggers();

    LogRestorer restorer = new Log4j2Mute().mute(new Class<?>[]{ServiceA.class});
    assertEquals(Level.OFF, SERVICE_A.getLevel());

    restorer.restore();
    // After restore the explicit entry should be gone; level inherits from root
    assertTrue(!ctx.getConfiguration().getLoggers().containsKey(SERVICE_A.getName()),
      "explicit logger config entry should be removed on restore");
  }

  // ---------- TestNG fixture classes (run programmatically, not by Surefire) ----------

  public static class RootMuteFixture {
    @org.testng.annotations.Test
    @Mute
    public void run() {
      assertEquals(Level.OFF, ROOT.getLevel());
    }
  }

  public static class RootMuteThrowsFixture {
    @org.testng.annotations.Test
    @Mute
    public void run() {
      assertEquals(Level.OFF, ROOT.getLevel());
      throw new RuntimeException("expected");
    }
  }

  @Mute
  public static class ClassLevelRootMuteFixture {
    @org.testng.annotations.Test
    public void run() {
      assertEquals(Level.OFF, ROOT.getLevel());
    }
  }

  public static class SingleClassMuteFixture {
    @org.testng.annotations.Test
    @Mute(classes = ServiceA.class)
    public void run() {
      assertEquals(Level.OFF, SERVICE_A.getLevel());
      assertEquals(Level.INFO, ROOT.getLevel());
    }
  }

  public static class MultipleClassMuteFixture {
    @org.testng.annotations.Test
    @Mute(classes = {ServiceA.class, ServiceB.class})
    public void run() {
      assertEquals(Level.OFF, SERVICE_A.getLevel());
      assertEquals(Level.OFF, SERVICE_B.getLevel());
    }
  }

  // ---------- Dummy service marker classes ----------

  private static class ServiceA {
  }

  private static class ServiceB {
  }

  // ---------- Helper ----------

  private static void runTestNG(Class<?> fixtureClass) {
    runTestNG(fixtureClass, 1, 0);
  }

  private static void runTestNG(Class<?> fixtureClass, int expectedPassed, int expectedFailed) {
    int[] counts = {0, 0}; // [passed, failed]
    TestNG testng = new TestNG(false);
    testng.setTestClasses(new Class<?>[]{fixtureClass});
    testng.addListener(new ITestListener() {
      @Override
      public void onTestSuccess(ITestResult r) {
        counts[0]++;
      }

      @Override
      public void onTestFailure(ITestResult r) {
        counts[1]++;
      }
    });
    testng.run();
    assertEquals(expectedPassed, counts[0],
      fixtureClass.getSimpleName() + ": expected " + expectedPassed + " passed test(s)");
    assertEquals(expectedFailed, counts[1],
      fixtureClass.getSimpleName() + ": expected " + expectedFailed + " failed test(s)");
  }
}
