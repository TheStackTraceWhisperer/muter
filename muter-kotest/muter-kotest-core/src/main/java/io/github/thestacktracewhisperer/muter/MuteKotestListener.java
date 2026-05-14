package io.github.thestacktracewhisperer.muter;

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
 * <p>Delegates the actual logger manipulation to all {@link LogMuter} implementations found
 * via {@link ServiceLoader}.
 *
 * <p>At least one {@code LogMuter} implementation must be present on the test classpath
 * (e.g., muter-kotest-logback, muter-kotest-log4j, or muter-kotest-jul); otherwise an
 * {@link IllegalStateException} is thrown when the first {@link Mute}-annotated test runs.
 */
@AutoScan
public class MuteKotestListener implements BeforeEachListener, AfterEachListener {

    private final List<LogMuter> logMuters;
    private final Map<Object, MuteRestorer> restorerHolder = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "MuteKotestListener";
    }

    public MuteKotestListener() {
        List<LogMuter> discovered = new ArrayList<>();
        ServiceLoader.load(LogMuter.class).forEach(discovered::add);
        this.logMuters = Collections.unmodifiableList(discovered);
    }

    /**
     * Testing seam that allows controlled {@link LogMuter} injection in unit tests.
     * Production use should rely on {@link #MuteKotestListener()}.
     */
    MuteKotestListener(List<LogMuter> logMuters) {
        this.logMuters = Collections.unmodifiableList(new ArrayList<>(logMuters));
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
     */
    @Deprecated(forRemoval = false)
    void muteBefore(Class<?> specClass) {
        muteBefore(Thread.currentThread(), specClass);
    }

    void muteBefore(Object executionKey, Class<?> specClass) {
        Mute mute = specClass.getAnnotation(Mute.class);
        if (mute == null) {
            return;
        }
        if (logMuters.isEmpty()) {
            throw new IllegalStateException(
                    "No LogMuter found on the classpath. "
                    + "Add muter-kotest-logback, muter-kotest-log4j, or muter-kotest-jul "
                    + "to your test dependencies.");
        }
        List<MuteRestorer> restorers = new ArrayList<>(logMuters.size());
        try {
            for (LogMuter muter : logMuters) {
                restorers.add(muter.mute(mute.classes()));
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
     */
    @Deprecated(forRemoval = false)
    void restoreAfter() {
        restoreAfter(Thread.currentThread());
    }

    void restoreAfter(Object executionKey) {
        MuteRestorer restorer = restorerHolder.remove(executionKey);
        if (restorer != null) {
            restorer.restore();
        }
    }
}
