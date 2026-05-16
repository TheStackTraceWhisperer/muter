package io.github.thestacktracewhisperer.mute.examples;

/*-
 * #%L
 * mute-examples-junit5-logback
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Example production class whose loggers should be silenced during tests.
 *
 * <p>Without {@code @Mute}, calling {@link #doSomethingExpectedToFail()} would
 * produce a WARN log line that clutters the test output.  With {@code @Mute}
 * the log level is set to OFF for the duration of the test and restored afterward.
 */
public class NoiseProducer {
  private static final Logger log = LoggerFactory.getLogger(NoiseProducer.class);

  private NoiseProducer() {
  }

  /**
   * Performs an operation that intentionally logs a warning then fails.
   *
   * @throws RuntimeException always, to simulate an expected failure
   */
  public static void doSomethingExpectedToFail() {
    log.warn("Expected failure – this noise should be suppressed by @Mute");
    throw new RuntimeException("expected");
  }
}
