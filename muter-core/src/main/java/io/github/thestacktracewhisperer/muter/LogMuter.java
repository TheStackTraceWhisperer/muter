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
