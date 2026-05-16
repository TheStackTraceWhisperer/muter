package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-spock-log4j
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
import org.junit.jupiter.api.*;
import org.spockframework.runtime.extension.IMethodInvocation;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Log4j 2 implementation of the Spock mute extension.
 */
class MuteSpockLog4j2Test {

    private static final org.apache.logging.log4j.core.Logger ROOT =
            (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
    private static final org.apache.logging.log4j.core.Logger SERVICE_A =
            (org.apache.logging.log4j.core.Logger) LogManager.getLogger(ServiceA.class);

    private Level savedRoot;
    private Level savedServiceA;

    @BeforeEach
    void saveLevels() {
        savedRoot     = ROOT.getLevel();
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
        MuteRestorer restorer = new Log4j2Mute().mute(new Class<?>[0]);
        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        Configurator.setRootLevel(Level.INFO);
        Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
        MuteRestorer restorer = new Log4j2Mute().mute(new Class<?>[]{ServiceA.class});
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

        MuteRestorer restorer = new Log4j2Mute().mute(new Class<?>[]{ServiceA.class});
        assertEquals(Level.OFF, SERVICE_A.getLevel());

        restorer.restore();
        assertEquals(effectiveBefore, SERVICE_A.getLevel(), "Level should be restored to inherited value");
    }

    // ---------- MuteInterceptor + Log4j2Mute integration ----------

    @Test
    @DisplayName("MuteInterceptor with Log4j2Mute mutes root and restores after proceed")
    void interceptorMutesRootLoggerAndRestores() throws Throwable {
        Configurator.setRootLevel(Level.INFO);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new Log4j2Mute()));
        AtomicBoolean proceedCalled = new AtomicBoolean();

        interceptor.intercept(invocationCapturing(() -> {
            proceedCalled.set(true);
            assertEquals(Level.OFF, ROOT.getLevel(), "Root logger should be muted during feature");
        }));

        assertTrue(proceedCalled.get());
        assertEquals(Level.INFO, ROOT.getLevel(), "Root logger should be restored after feature");
    }

    @Test
    @DisplayName("MuteInterceptor with Log4j2Mute restores logger even when feature throws")
    void interceptorRestoresWhenFeatureThrows() {
        Configurator.setRootLevel(Level.WARN);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[0], List.of(new Log4j2Mute()));

        assertThrows(RuntimeException.class, () ->
                interceptor.intercept(invocationCapturing(() -> {
                    throw new RuntimeException("expected failure");
                })));

        assertEquals(Level.WARN, ROOT.getLevel(), "Root logger should be restored after failure");
    }

    @Test
    @DisplayName("MuteInterceptor with Log4j2Mute mutes specific class logger")
    void interceptorMutesSpecificClassLogger() throws Throwable {
        Configurator.setRootLevel(Level.INFO);
        Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);
        MuteInterceptor interceptor = new MuteInterceptor(
                new Class<?>[]{ServiceA.class}, List.of(new Log4j2Mute()));

        interceptor.intercept(invocationCapturing(() -> {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.INFO, ROOT.getLevel(), "Root should be unaffected");
        }));

        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Duplicate class in target list is processed only once (dedup via processedLoggers set)")
    void duplicateClassIsDeduplicatedAndRestored() {
        Configurator.setLevel(SERVICE_A.getName(), Level.DEBUG);

        // Pass ServiceA twice — should be processed once
        MuteRestorer restorer = new Log4j2Mute()
                .mute(new Class<?>[]{ServiceA.class, ServiceA.class});

        assertEquals(Level.OFF, SERVICE_A.getLevel());
        restorer.restore();
        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
    }

    @Test
    @DisplayName("Non-Log4j 2 context supplier throws IllegalStateException with helpful message")
    void nonLog4j2ContextFailsFast() {
        Log4j2Mute mute = new Log4j2Mute(() -> "not-a-log4j-context");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> mute.mute(new Class<?>[0]));
        assertTrue(ex.getMessage().contains("mute-spock-log4j"));
    }

    @Test
    @DisplayName("Null context supplier throws IllegalStateException mentioning null")
    void nullContextFailsFast() {
        Log4j2Mute mute = new Log4j2Mute(() -> null);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> mute.mute(new Class<?>[0]));
        assertTrue(ex.getMessage().contains("null"));
    }

    // ---------- Dummy service classes ----------

    private static class ServiceA {}
    private static class ServiceB {}

    // ---------- Helper ----------

    private static IMethodInvocation invocationCapturing(ProceedAction action) {
        return (IMethodInvocation) Proxy.newProxyInstance(
                IMethodInvocation.class.getClassLoader(),
                new Class<?>[]{IMethodInvocation.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "proceed" -> { action.run(); yield null; }
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
