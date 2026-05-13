package io.github.thestacktracewhisperer.muter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link LogMuter} implementation that manipulates Logback {@link Logger} levels directly.
 */
public class LogbackMuter implements LogMuter {

    @Override
    public MuteRestorer mute(Mute annotation) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) {
            throw new IllegalStateException(
                    "muter-logback requires Logback Classic on the classpath; found: "
                            + LoggerFactory.getILoggerFactory().getClass().getName());
        }

        Class<?>[] classes = annotation.classes();
        Map<Logger, Level> originalLevels = classes.length == 0
                ? new HashMap<>(2)
                : new HashMap<>(classes.length * 2);

        if (classes.length == 0) {
            muteRoot(ctx, originalLevels);
        } else {
            muteClasses(ctx, classes, originalLevels);
        }

        return () -> originalLevels.forEach(Logger::setLevel);
    }

    private void muteRoot(LoggerContext ctx, Map<Logger, Level> state) {
        Logger rootLogger = ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        state.put(rootLogger, rootLogger.getLevel());
        rootLogger.setLevel(Level.OFF);
    }

    private void muteClasses(LoggerContext ctx, Class<?>[] classes, Map<Logger, Level> state) {
        for (Class<?> clazz : classes) {
            Logger logger = ctx.getLogger(clazz.getName());
            state.put(logger, logger.getLevel());
            logger.setLevel(Level.OFF);
        }
    }
}
