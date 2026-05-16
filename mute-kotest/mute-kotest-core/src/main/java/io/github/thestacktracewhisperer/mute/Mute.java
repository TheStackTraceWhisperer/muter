package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-kotest-core
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declaratively mutes logging output during all tests in a Kotest specification.
 *
 * <p>Place on a Kotest {@code Spec} class to suppress log noise caused by expected exceptions.
 * When {@link #classes()} is empty the ROOT logger is muted; otherwise only the loggers for
 * the specified classes are muted.
 *
 * <p>The muting is applied via {@link MuteKotestListener}, which is auto-registered via
 * {@code @AutoScan}. Original log levels are restored after every test regardless of outcome.
 *
 * <p>At least one {@code LogMute} implementation must be present on the test classpath
 * (e.g., mute-kotest-logback, mute-kotest-log4j, or mute-kotest-jul).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mute {
  /**
   * Specify which classes' loggers should be muted.
   * If left empty, the ROOT logger is muted.
   *
   * @return the classes whose loggers should be muted, or an empty array to mute the root logger
   */
  Class<?>[] classes() default {};
}
