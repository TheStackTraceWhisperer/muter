package io.github.thestacktracewhisperer.muter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JUnit 5 extension registered by {@link Mute}. Mutes Logback loggers before
 * test execution and restores their levels afterward.
 *
 * <p>{@link Mute} may be placed on a test method <em>or</em> on a test class.
 * When placed on a class the annotation is inherited by every test method in that class.
 */
public class MuteExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final JUnitMuteStateStack stateStack = new JUnitMuteStateStack();

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        findMuteAnnotation(context)
                .ifPresent(annotation -> stateStack.push(context, mute(annotation)));
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

    private MuteRestorer mute(Mute annotation) {
        Object loggerFactory = getLoggerFactory();
        if (!(loggerFactory instanceof LoggerContext ctx)) {
            throw new IllegalStateException(
                    "muter-logback requires Logback Classic on the classpath; found: "
                            + (loggerFactory == null ? "null" : loggerFactory.getClass().getName()));
        }

        Class<?>[] classes = annotation.classes();
        Map<Logger, Level> originalLevels = classes.length == 0
                ? new HashMap<>(2)
                : new HashMap<>(classes.length * 2);

        if (classes.length == 0) {
            Logger rootLogger = ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            originalLevels.put(rootLogger, rootLogger.getLevel());
            rootLogger.setLevel(Level.OFF);
        } else {
            for (Class<?> clazz : classes) {
                Logger logger = ctx.getLogger(clazz.getName());
                originalLevels.put(logger, logger.getLevel());
                logger.setLevel(Level.OFF);
            }
        }

        return () -> originalLevels.forEach(Logger::setLevel);
    }

    Object getLoggerFactory() {
        return LoggerFactory.getILoggerFactory();
    }
}
