package io.github.thestacktracewhisperer.muter;

/**
 * Strategy interface for muting loggers before a test and producing a
 * {@link MuteRestorer} that undoes those mutations afterward.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader}.
 * Each implementation module (muter-logback, muter-log4j, muter-jul,
 * muter-commons-logging) registers its implementation in
 * {@code META-INF/services/io.github.thestacktracewhisperer.muter.LogMuter}.
 */
public interface LogMuter {
    /**
     * Mutes the loggers described by {@code annotation}.
     *
     * @param annotation the {@link Mute} annotation carrying the target class list
     * @return a command that restores all loggers to their pre-mute state
     */
    MuteRestorer mute(Mute annotation);
}
