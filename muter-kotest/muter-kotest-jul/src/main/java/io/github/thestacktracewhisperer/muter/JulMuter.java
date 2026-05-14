package io.github.thestacktracewhisperer.muter;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link LogMuter} implementation for {@code java.util.logging} (JUL).
 *
 * <p>Mutes JUL loggers by setting their level to {@link Level#OFF} and
 * restores the original levels afterward via the returned {@link MuteRestorer}.
 *
 * <p>No additional dependencies are required — JUL is part of the JDK.
 */
public class JulMuter implements LogMuter {

    @Override
    public MuteRestorer mute(Class<?>[] targetClasses) {
        Map<Logger, Level> originalLevels = targetClasses.length == 0
                ? new HashMap<>(2)
                : new HashMap<>(targetClasses.length * 2);

        if (targetClasses.length == 0) {
            Logger rootLogger = Logger.getLogger("");
            originalLevels.put(rootLogger, rootLogger.getLevel());
            rootLogger.setLevel(Level.OFF);
        } else {
            for (Class<?> clazz : targetClasses) {
                Logger logger = Logger.getLogger(clazz.getName());
                originalLevels.putIfAbsent(logger, logger.getLevel());
                logger.setLevel(Level.OFF);
            }
        }

        return () -> originalLevels.forEach(Logger::setLevel);
    }
}
