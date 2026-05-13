package io.github.thestacktracewhisperer.muter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link LogMuter} implementation that manipulates Logback {@link Logger} levels directly.
 */
public class LogbackMuter implements LogMuter {

    @Override
    public MuteRestorer mute(Mute annotation) {
        Class<?>[] classes = annotation.classes();
        Map<Logger, Level> originalLevels = classes.length == 0
                ? new HashMap<>(2)
                : new HashMap<>(classes.length * 2);

        if (classes.length == 0) {
            muteRoot(originalLevels);
        } else {
            muteClasses(classes, originalLevels);
        }

        return () -> originalLevels.forEach(Logger::setLevel);
    }

    private void muteRoot(Map<Logger, Level> state) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        state.put(rootLogger, rootLogger.getLevel());
        rootLogger.setLevel(Level.OFF);
    }

    private void muteClasses(Class<?>[] classes, Map<Logger, Level> state) {
        for (Class<?> clazz : classes) {
            Logger logger = (Logger) LoggerFactory.getLogger(clazz);
            state.put(logger, logger.getLevel());
            logger.setLevel(Level.OFF);
        }
    }
}
