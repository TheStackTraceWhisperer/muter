package io.github.thestacktracewhisperer.muter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configurator;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link LogMuter} implementation for Apache Log4j 2.
 *
 * <p>Mutes Log4j 2 loggers by setting their level to {@link Level#OFF} and
 * restores the original configuration afterward via the returned {@link MuteRestorer}.
 *
 * <p>Requires {@code org.apache.logging.log4j:log4j-core} on the classpath;
 * throws {@link IllegalStateException} if Log4j 2 Core is not available.
 *
 * <p><strong>Note on inner class names:</strong> Log4j 2 normalises class names by
 * replacing {@code $} with {@code .}, so logger names may differ from
 * {@link Class#getName()}. This implementation always uses the name as reported by
 * {@link org.apache.logging.log4j.core.Logger#getName()} to stay consistent with
 * how Log4j 2 resolves logger configurations.
 */
public class Log4j2Muter implements LogMuter {

    @Override
    public MuteRestorer mute(Mute annotation) {
        LoggerContext ctx = getLoggerContext();
        List<Runnable> restoreActions = new ArrayList<>();

        if (annotation.classes().length == 0) {
            org.apache.logging.log4j.core.Logger rootLogger =
                    (org.apache.logging.log4j.core.Logger) LogManager.getRootLogger();
            Level original = rootLogger.getLevel();
            Configurator.setRootLevel(Level.OFF);
            restoreActions.add(() -> Configurator.setRootLevel(original));
        } else {
            for (Class<?> clazz : annotation.classes()) {
                // Use the logger's own name: Log4j 2 replaces '$' with '.' for inner classes
                org.apache.logging.log4j.core.Logger coreLogger =
                        (org.apache.logging.log4j.core.Logger) LogManager.getLogger(clazz);
                String name = coreLogger.getName();
                boolean hadExplicitConfig = ctx.getConfiguration().getLoggers().containsKey(name);
                Level original = coreLogger.getLevel();
                Configurator.setLevel(name, Level.OFF);
                if (hadExplicitConfig) {
                    restoreActions.add(() -> Configurator.setLevel(name, original));
                } else {
                    restoreActions.add(() -> {
                        ctx.getConfiguration().removeLogger(name);
                        ctx.updateLoggers();
                    });
                }
            }
        }

        return () -> restoreActions.forEach(Runnable::run);
    }

    private static LoggerContext getLoggerContext() {
        Object ctx = LogManager.getContext(false);
        if (!(ctx instanceof LoggerContext loggerContext)) {
            throw new IllegalStateException(
                    "muter-log4j requires Log4j 2 Core on the classpath; found: "
                            + (ctx == null ? "null" : ctx.getClass().getName()));
        }
        return loggerContext;
    }
}
