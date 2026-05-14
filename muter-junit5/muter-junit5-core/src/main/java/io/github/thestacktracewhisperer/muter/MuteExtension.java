package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * JUnit 5 extension registered by {@link Mute}. Delegates the actual logger
 * manipulation to all {@link LogMuter} implementations found on the classpath
 * via {@link ServiceLoader}.
 *
 * <p>{@link Mute} may be placed on a test method <em>or</em> on a test class.
 * When placed on a class the annotation is inherited by every test method in
 * that class.
 *
 * <p>At least one {@code LogMuter} implementation must be present on the test
 * classpath (e.g., muter-junit5-logback, muter-junit5-log4j, or
 * muter-junit5-jul); otherwise an {@link IllegalStateException} is thrown
 * when the first {@link Mute}-annotated test runs.
 */
public class MuteExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final JUnitMuteStateStack stateStack = new JUnitMuteStateStack();
    private final List<LogMuter> logMuters;

    public MuteExtension() {
        List<LogMuter> discovered = new ArrayList<>();
        ServiceLoader.load(LogMuter.class).forEach(discovered::add);
        this.logMuters = Collections.unmodifiableList(discovered);
    }

    /**
     * Testing seam that allows controlled {@link LogMuter} injection in unit tests.
     * Production use should rely on {@link #MuteExtension()}.
     */
    MuteExtension(List<LogMuter> logMuters) {
        this.logMuters = Collections.unmodifiableList(new ArrayList<>(logMuters));
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        findMuteAnnotation(context).ifPresent(annotation -> {
            if (logMuters.isEmpty()) {
                throw new IllegalStateException(
                        "No LogMuter found on the classpath. "
                        + "Add muter-junit5-logback, muter-junit5-log4j, or muter-junit5-jul "
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
            stateStack.push(context, () -> {
                for (int i = restorers.size() - 1; i >= 0; i--) {
                    restorers.get(i).restore();
                }
            });
        });
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        findMuteAnnotation(context)
                .ifPresent(annotation -> stateStack.popAndRestore(context));
    }

    /**
     * Looks for {@link Mute} on the test method first; falls back to the test class
     * to support class-level {@code @Mute}.
     */
    private Optional<Mute> findMuteAnnotation(ExtensionContext context) {
        return context.getElement()
                .map(element -> element.getAnnotation(Mute.class))
                .or(() -> Optional.ofNullable(context.getRequiredTestClass().getAnnotation(Mute.class)));
    }
}
