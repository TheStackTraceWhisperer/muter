package io.github.thestacktracewhisperer.muter;

/**
 * Command interface that restores loggers to their pre-mute state.
 */
@FunctionalInterface
public interface MuteRestorer {
    void restore();
}
