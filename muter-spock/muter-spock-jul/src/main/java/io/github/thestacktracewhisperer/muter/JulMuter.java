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

    /** Creates a new {@code JulMuter}. */
    public JulMuter() {}

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
