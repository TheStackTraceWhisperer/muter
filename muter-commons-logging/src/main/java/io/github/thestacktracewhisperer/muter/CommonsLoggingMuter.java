package io.github.thestacktracewhisperer.muter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Jdk14Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link LogMuter} implementation for Apache Commons Logging (JCL).
 *
 * <p>Commons Logging is a thin facade that delegates to an underlying logging
 * framework. This implementation supports the JUL backend, which is the default
 * when no other framework (e.g., Log4j) is present on the classpath.
 *
 * <p>The muter inspects the Commons Logging {@link Log} instance returned for
 * the target class. If it is a {@link Jdk14Logger} (the JUL bridge), it mutes
 * the underlying {@link java.util.logging.Logger} directly.
 *
 * <p>If Commons Logging is configured to use a different backend (e.g.,
 * Log4j 2 or Logback), use {@code muter-log4j} or {@code muter-logback}
 * instead and throw an {@link IllegalStateException} with a helpful message.
 */
public class CommonsLoggingMuter implements LogMuter {

    @Override
    public MuteRestorer mute(Mute annotation) {
        validateBackend();

        Class<?>[] classes = annotation.classes();
        Map<Logger, Level> originalLevels = classes.length == 0
                ? new HashMap<>(2)
                : new HashMap<>(classes.length * 2);

        if (classes.length == 0) {
            Logger rootLogger = Logger.getLogger("");
            originalLevels.put(rootLogger, rootLogger.getLevel());
            rootLogger.setLevel(Level.OFF);
        } else {
            for (Class<?> clazz : classes) {
                Log log = LogFactory.getLog(clazz);
                Logger julLogger = ((Jdk14Logger) log).getLogger();
                originalLevels.put(julLogger, julLogger.getLevel());
                julLogger.setLevel(Level.OFF);
            }
        }

        return () -> originalLevels.forEach(Logger::setLevel);
    }

    /**
     * Verifies that Commons Logging is backed by JUL. Throws with a helpful message
     * if a different backend is detected.
     */
    private static void validateBackend() {
        Log probe = LogFactory.getLog(CommonsLoggingMuter.class);
        if (!(probe instanceof Jdk14Logger)) {
            throw new IllegalStateException(
                    "muter-commons-logging supports the JUL backend for Commons Logging; "
                    + "detected: " + probe.getClass().getName()
                    + ". Use muter-logback or muter-log4j for other backends.");
        }
    }
}
