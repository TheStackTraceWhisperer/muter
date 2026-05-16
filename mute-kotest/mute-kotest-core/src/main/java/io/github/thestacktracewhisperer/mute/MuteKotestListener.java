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
import io.kotest.core.listeners.BeforeEachListener;
import io.kotest.core.listeners.AfterEachListener;
import io.kotest.core.test.TestCase;
import io.kotest.core.test.TestResult;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kotest listener that mutes loggers before each test in a spec annotated with {@link Mute}
 * and restores them afterward, regardless of test outcome.
 *
 * <p>This listener is auto-registered globally via {@code @AutoScan}. When a Kotest spec is
 * not annotated with {@link Mute}, this listener is a no-op for that spec.
 *
 * <p>Delegates the actual logger manipulation to all {@link LogMute} implementations found
 * via {@link ServiceLoader}.
 *
 * <p>At least one {@code LogMute} implementation must be present on the test classpath
 * (e.g., mute-kotest-logback, mute-kotest-log4j, or mute-kotest-jul); otherwise an
 * {@link IllegalStateException} is thrown when the first {@link Mute}-annotated test runs.
 */
@AutoScan
public class MuteKotestListener implements BeforeEachListener, AfterEachListener {

    private final List<LogMute> logMutes;
    private final Map<Object, MuteRestorer> restorerHolder = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "MuteKotestListener";
    }

    /** Production constructor: discovers {@link LogMute} implementations via {@link ServiceLoader}. */
    public MuteKotestListener() {
        List<LogMute> discovered = new ArrayList<>();
        ServiceLoader.load(LogMute.class).forEach(discovered::add);
        this.logMutes = Collections.unmodifiableList(discovered);
    }

    /**
     * Testing seam that allows controlled {@link LogMute} injection in unit tests.
     * Production use should rely on {@link #MuteKotestListener()}.
     */
    MuteKotestListener(List<LogMute> logMutes) {
        this.logMutes = Collections.unmodifiableList(new ArrayList<>(logMutes));
    }

    @Override
    public Object beforeEach(TestCase testCase, Continuation<? super Unit> $completion) {
        muteBefore(testCase, testCase.getSpec().getClass());
        return Unit.INSTANCE;
    }

    @Override
    public Object afterEach(TestCase testCase, TestResult result, Continuation<? super Unit> $completion) {
        restoreAfter(testCase);
        return Unit.INSTANCE;
    }

    /**
     * Mutes loggers if the given spec class is annotated with {@link Mute}.
     * Package-private for testing.
     *
     * @deprecated Use {@link #muteBefore(Object, Class)} with an execution-context key.
     */
    @Deprecated(forRemoval = false)
    void muteBefore(Class<?> specClass) {
        muteBefore(Thread.currentThread(), specClass);
    }

    void muteBefore(Object executionKey, Class<?> specClass) {
        Mute annotation = specClass.getAnnotation(Mute.class);
        if (annotation == null) {
            return;
        }
        if (logMutes.isEmpty()) {
            throw new IllegalStateException(
                    "No LogMute found on the classpath. "
                    + "Add mute-kotest-logback, mute-kotest-log4j, or mute-kotest-jul "
                    + "to your test dependencies.");
        }
        List<MuteRestorer> restorers = new ArrayList<>(logMutes.size());
        try {
            for (LogMute mute : logMutes) {
                restorers.add(mute.mute(annotation.classes()));
            }
        } catch (RuntimeException | Error e) {
            for (int i = restorers.size() - 1; i >= 0; i--) {
                restorers.get(i).restore();
            }
            throw e;
        }
        MuteRestorer restorer = () -> {
            for (int i = restorers.size() - 1; i >= 0; i--) {
                restorers.get(i).restore();
            }
        };
        restorerHolder.compute(executionKey, (key, previous) -> {
            if (previous != null) {
                previous.restore();
            }
            return restorer;
        });
    }

    /**
     * Restores loggers if a restorer is held for the given execution key.
     * Package-private for testing.
     *
     * @deprecated Use {@link #restoreAfter(Object)} with an execution-context key.
     */
    @Deprecated(forRemoval = false)
    void restoreAfter() {
        restoreAfter(Thread.currentThread());
    }

    void restoreAfter(Object executionKey) {
        MuteRestorer restorer = restorerHolder.get(executionKey);
        if (restorer != null) {
            restorer.restore();
            restorerHolder.remove(executionKey, restorer);
        }
    }
}
