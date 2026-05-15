package io.github.thestacktracewhisperer.muter;

/*-
 * #%L
 * muter
 * %%
 * Copyright (C) 2026 TheStackTraceWhisperer
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@link LogMuter} implementation for Logback Classic.
 *
 * <p>Mutes Logback loggers by setting their level to {@link Level#OFF} and
 * restores the original levels afterward via the returned {@link MuteRestorer}.
 *
 * <p>Requires {@code ch.qos.logback:logback-classic} on the classpath;
 * throws {@link IllegalStateException} if the bound logging framework is not Logback.
 */
public class LogbackMuter implements LogMuter {

    private final Supplier<Object> loggerFactorySupplier;

    /** Production constructor: resolves the logger factory from {@link org.slf4j.LoggerFactory}. */
    public LogbackMuter() {
        this(org.slf4j.LoggerFactory::getILoggerFactory);
    }

    /**
     * Testing seam that allows controlled logger-factory injection in unit tests.
     * Production use should rely on {@link #LogbackMuter()}.
     */
    LogbackMuter(Supplier<Object> loggerFactorySupplier) {
        this.loggerFactorySupplier = loggerFactorySupplier;
    }

    @Override
    public MuteRestorer mute(Class<?>[] targetClasses) {
        Object loggerFactory = loggerFactorySupplier.get();
        if (!(loggerFactory instanceof LoggerContext ctx)) {
            throw new IllegalStateException(
                    "muter-spock-logback requires Logback Classic on the classpath; found: "
                            + (loggerFactory == null ? "null" : loggerFactory.getClass().getName()));
        }

        Map<Logger, Level> originalLevels = targetClasses.length == 0
                ? new HashMap<>(2)
                : new HashMap<>(targetClasses.length * 2);

        if (targetClasses.length == 0) {
            Logger rootLogger = ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            originalLevels.put(rootLogger, rootLogger.getLevel());
            rootLogger.setLevel(Level.OFF);
        } else {
            for (Class<?> clazz : targetClasses) {
                Logger logger = ctx.getLogger(clazz.getName());
                originalLevels.putIfAbsent(logger, logger.getLevel());
                logger.setLevel(Level.OFF);
            }
        }

        return () -> originalLevels.forEach(Logger::setLevel);
    }
}
