package io.github.thestacktracewhisperer.muter;

/**
 * Strategy interface for muting loggers before a test and producing a
 * {@link MuteRestorer} that undoes those mutations afterward.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * Each implementation module (muter-junit5-logback, muter-junit5-log4j,
 * muter-junit5-jul, muter-testng-logback, muter-testng-log4j, muter-testng-jul)
 * registers its implementation in
 * {@code META-INF/services/io.github.thestacktracewhisperer.muter.LogMuter}.
 */
public interface LogMuter {
    /**
     * Mutes the loggers for the specified target classes.
     *
     * @param targetClasses the classes whose loggers should be muted; an empty array
     *                      means mute the ROOT logger
     * @return a command that restores all loggers to their pre-mute state
     */
    MuteRestorer mute(Class<?>[] targetClasses);
}
