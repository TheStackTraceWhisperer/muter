/*-
 * #%L
 * mute-examples-spock-logback
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
package io.github.thestacktracewhisperer.mute.examples

import io.github.thestacktracewhisperer.mute.Mute
import spock.lang.Specification

/**
 * Demonstrates @Mute with Spock 2: the logger of {@link NoiseProducer} is silenced
 * for the duration of each annotated feature and fully restored afterward.
 *
 * <p>@Mute can be placed on the spec class (silences all features) or on individual
 * feature methods.  Remove it and re-run to see the suppressed log line appear.
 */
class NoiseProducerSpec extends Specification {
    /**
     * The WARN/WARNING produced by {@link NoiseProducer#doSomethingExpectedToFail()}
     * is suppressed because @Mute targets the {@link NoiseProducer} logger.
     */
    @Mute(classes = [NoiseProducer])
    def "suppresses the specific logger during an expected failure"() {
        when:
        NoiseProducer.doSomethingExpectedToFail()
        then:
        thrown RuntimeException
    }
    /**
     * Root-logger variant: all loggers are silenced for this feature.
     */
    @Mute
    def "suppresses the root logger during an expected failure"() {
        when:
        NoiseProducer.doSomethingExpectedToFail()
        then:
        thrown RuntimeException
    }
}
