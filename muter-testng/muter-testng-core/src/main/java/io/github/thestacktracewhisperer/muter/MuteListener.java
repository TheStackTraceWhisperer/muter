package io.github.thestacktracewhisperer.muter;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * TestNG listener registered via {@code META-INF/services/org.testng.ITestNGListener}.
 *
 * <p>Detects {@link Mute} annotations on test methods or their declaring class and delegates
 * the actual logger manipulation to all {@link LogMuter} implementations found on the
 * classpath via {@link ServiceLoader}.
 *
 * <p>State is stored per-thread using a {@link ThreadLocal}, ensuring correctness for
 * single-threaded and parallel test runs alike.
 *
 * <p>At least one {@code LogMuter} implementation must be present on the test
 * classpath (e.g., muter-testng-logback, muter-testng-log4j, or
 * muter-testng-jul); otherwise an {@link IllegalStateException} is thrown
 * when the first {@link Mute}-annotated test runs.
 */
public class MuteListener implements IInvokedMethodListener {

    private final List<LogMuter> logMuters;
    private final ThreadLocal<MuteRestorer> restorerHolder = new ThreadLocal<>();

    public MuteListener() {
        List<LogMuter> discovered = new ArrayList<>();
        ServiceLoader.load(LogMuter.class).forEach(discovered::add);
        this.logMuters = Collections.unmodifiableList(discovered);
    }

    /**
     * Testing seam that allows controlled {@link LogMuter} injection in unit tests.
     * Production use should rely on {@link #MuteListener()}.
     */
    MuteListener(List<LogMuter> logMuters) {
        this.logMuters = Collections.unmodifiableList(new ArrayList<>(logMuters));
    }

    @Override
    public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        findMuteAnnotation(method).ifPresent(annotation -> {
            if (logMuters.isEmpty()) {
                throw new IllegalStateException(
                        "No LogMuter found on the classpath. "
                        + "Add muter-testng-logback, muter-testng-log4j, or muter-testng-jul "
                        + "to your test dependencies.");
            }
            List<MuteRestorer> restorers = new ArrayList<>(logMuters.size());
            try {
                for (LogMuter muter : logMuters) {
                    restorers.add(muter.mute(annotation.classes()));
                }
            } catch (RuntimeException | Error e) {
                for (int i = restorers.size() - 1; i >= 0; i--) {
                    restorers.get(i).restore();
                }
                throw e;
            }
            restorerHolder.set(() -> {
                for (int i = restorers.size() - 1; i >= 0; i--) {
                    restorers.get(i).restore();
                }
            });
        });
    }

    @Override
    public void afterInvocation(IInvokedMethod method, ITestResult testResult) {
        if (!method.isTestMethod()) {
            return;
        }
        MuteRestorer restorer = restorerHolder.get();
        if (restorer != null) {
            restorerHolder.remove();
            restorer.restore();
        }
    }

    /**
     * Looks for {@link Mute} on the test method first; falls back to the test class
     * to support class-level {@code @Mute}.
     */
    private Optional<Mute> findMuteAnnotation(IInvokedMethod method) {
        Method reflectMethod = method.getTestMethod().getConstructorOrMethod().getMethod();
        if (reflectMethod != null) {
            Mute mute = reflectMethod.getAnnotation(Mute.class);
            if (mute != null) {
                return Optional.of(mute);
            }
        }
        return Optional.ofNullable(
                method.getTestMethod().getTestClass().getRealClass().getAnnotation(Mute.class));
    }
}
