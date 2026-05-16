package io.github.thestacktracewhisperer.mute.examples;

/*-
 * #%L
 * mute-examples-testng-logback
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
import io.github.thestacktracewhisperer.mute.MuteListener;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
/**
 * Demonstrates @Mute with TestNG: the logger of {@link NoiseProducer} is silenced
 * for the duration of each annotated test and fully restored afterward.
 *
 * <p>{@link MuteListener} is registered via the {@code @Listeners} annotation.
 * Alternatively, it can be registered in a {@code testng.xml} suite file.
 *
 * <p>Remove {@code @Mute} from a test and re-run to see the suppressed log line appear.
 */
@Listeners(MuteListener.class)
public class NoiseProducerTest {
    /**
     * The WARN/WARNING produced by {@link NoiseProducer#doSomethingExpectedToFail()}
     * is suppressed because {@code @Mute} targets the {@link NoiseProducer} logger.
     */
    @Test(expectedExceptions = RuntimeException.class)
    @Mute(classes = NoiseProducer.class)
    public void suppressesLogsDuringExpectedFailure() {
        NoiseProducer.doSomethingExpectedToFail();
    }
    /**
     * Root-logger variant: all loggers are silenced for this test.
     */
    @Test(expectedExceptions = RuntimeException.class)
    @Mute
    public void suppressesRootLoggerDuringExpectedFailure() {
        NoiseProducer.doSomethingExpectedToFail();
    }
}
