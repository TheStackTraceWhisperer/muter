package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-spock-jul
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
import org.spockframework.runtime.extension.IMethodInvocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the JUL implementation of the Spock mute extension.
 */
class MuteSpockJulTest {

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

  // ---------- MuteInterceptor + JulMute integration ----------

  @Test
  @DisplayName("MuteInterceptor with JulMute mutes root and restores after proceed")
  void interceptorMutesRootLoggerAndRestores() throws Throwable {
    ROOT.setLevel(Level.INFO);
    MuteInterceptor interceptor = new MuteInterceptor(
      new Class<?>[0], List.of(new JulMute()));
    AtomicBoolean proceedCalled = new AtomicBoolean();

    interceptor.intercept(invocationCapturing(() -> {
      proceedCalled.set(true);
      assertEquals(Level.OFF, ROOT.getLevel(), "Root logger should be muted during feature");
    }));

    assertTrue(proceedCalled.get());
    assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should be restored after feature");
  }

  @Test
  @DisplayName("MuteInterceptor with JulMute restores logger even when feature throws")
  void interceptorRestoresWhenFeatureThrows() {
    ROOT.setLevel(Level.WARNING);
    MuteInterceptor interceptor = new MuteInterceptor(
      new Class<?>[0], List.of(new JulMute()));

    assertThrows(RuntimeException.class, () ->
      interceptor.intercept(invocationCapturing(() -> {
        throw new RuntimeException("expected failure");
      })));

    assertEquals(Level.WARNING, ROOT.getLevel(), "Root logger should be restored after failure");
  }

  @Test
  @DisplayName("MuteInterceptor with JulMute mutes specific class logger")
  void interceptorMutesSpecificClassLogger() throws Throwable {
    ROOT.setLevel(Level.INFO);
    SERVICE_A.setLevel(Level.FINE);
    MuteInterceptor interceptor = new MuteInterceptor(
      new Class<?>[]{ServiceA.class}, List.of(new JulMute()));

    interceptor.intercept(invocationCapturing(() -> {
      assertEquals(Level.OFF, SERVICE_A.getLevel());
      assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
    }));

    assertEquals(Level.FINE, SERVICE_A.getLevel());
    assertEquals(Level.INFO, ROOT.getLevel());
  }

  // ---------- Dummy service classes ----------

  private static class ServiceA {
  }

  // ---------- Helper ----------

  private static IMethodInvocation invocationCapturing(ProceedAction action) {
    return (IMethodInvocation) Proxy.newProxyInstance(
      IMethodInvocation.class.getClassLoader(),
      new Class<?>[]{IMethodInvocation.class},
      (proxy, method, args) -> switch (method.getName()) {
        case "proceed" -> {
          action.run();
          yield null;
        }
        case "toString" -> "InvocationProxy";
        case "hashCode" -> System.identityHashCode(proxy);
        case "equals" -> proxy == args[0];
        default -> null;
      });
  }

  @FunctionalInterface
  interface ProceedAction {
    void run() throws Throwable;
  }
}
