package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-core
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

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declaratively mutes logging output during a test execution.
 *
 * <p>Supported test frameworks:
 * <ul>
 *   <li><strong>JUnit 5</strong> — place on a test method or class; the
 *       {@link MuteExtension} is activated automatically via {@code @ExtendWith}.</li>
 *   <li><strong>Spock 2</strong> — place on a feature method or specification class;
 *       {@code MuteSpockExtension} is auto-registered as a global extension via SPI.</li>
 *   <li><strong>TestNG</strong> — place on a test method or class;
 *       {@code MuteListener} is auto-registered via
 *       {@code META-INF/services/org.testng.ITestNGListener}.</li>
 *   <li><strong>Kotest</strong> — place on a {@code Spec} class;
 *       {@code MuteKotestListener} is auto-registered via {@code @AutoScan}.</li>
 * </ul>
 *
 * <p>When {@link #classes()} is empty the ROOT logger is muted; otherwise only the
 * loggers for the specified classes are muted.
 *
 * <p>Original log levels are restored after every test regardless of outcome.
 *
 * <p>At least one logging-backend module must be present on the test classpath
 * (e.g., {@code mute-logback}, {@code mute-log4j}, or {@code mute-jul}).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MuteExtension.class)
public @interface Mute {
  /**
   * Specify which classes' loggers should be muted.
   * If left empty, the ROOT logger is muted.
   *
   * @return the classes whose loggers should be muted, or an empty array to mute the root logger
   */
  Class<?>[] classes() default {};
}

