package io.github.thestacktracewhisperer.mute.examples;

/*-
 * #%L
 * mute-examples-junit5-log4j
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
import io.github.thestacktracewhisperer.mute.Mute;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
/**
 * Demonstrates @Mute with JUnit 5: the logger of {@link NoiseProducer} is silenced
 * for the duration of each annotated test and fully restored afterward.
 *
 * <p>Remove {@code @Mute} and re-run the test to see the suppressed log line appear.
 */
class NoiseProducerTest {
    /**
     * The WARN/WARNING produced by {@link NoiseProducer#doSomethingExpectedToFail()}
     * is suppressed because {@code @Mute} targets the {@link NoiseProducer} logger.
     */
    @Test
    @Mute(classes = NoiseProducer.class)
    void suppressesLogsDuringExpectedFailure() {
        assertThrows(RuntimeException.class, NoiseProducer::doSomethingExpectedToFail);
    }
    /**
     * Root-logger variant: all loggers are silenced for this test.
     */
    @Test
    @Mute
    void suppressesRootLoggerDuringExpectedFailure() {
        assertThrows(RuntimeException.class, NoiseProducer::doSomethingExpectedToFail);
    }
}
