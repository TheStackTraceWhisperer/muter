package io.github.thestacktracewhisperer.muter;

/**
 * Abstraction over the underlying logging framework so that {@link MuteExtension}
 * does not depend directly on Logback.
 */
public interface LogMuter {
    /**
     * Mutes the loggers specified in the annotation.
     *
     * @param annotation the {@link Mute} annotation carrying the target classes
     * @return a {@link MuteRestorer} command that, when invoked, restores the
     *         loggers to their original state
     */
    MuteRestorer mute(Mute annotation);
}
