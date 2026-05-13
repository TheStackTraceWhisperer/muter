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
        Map<Logger, Level> originalLevels = new HashMap<>();

        if (annotation.classes().length == 0) {
            muteRoot(originalLevels);
        } else {
            muteClasses(annotation.classes(), originalLevels);
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
