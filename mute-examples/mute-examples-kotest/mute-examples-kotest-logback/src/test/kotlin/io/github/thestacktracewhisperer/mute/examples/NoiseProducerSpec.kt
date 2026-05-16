/*-
 * #%L
 * mute-examples-kotest-logback
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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
/**
 * Demonstrates @Mute with Kotest: the logger of [NoiseProducer] is silenced
 * for every test in this spec and fully restored afterward.
 *
 * [MuteKotestListener] is auto-registered globally via @AutoScan — no explicit
 * wiring is needed.  @Mute is placed on the spec class to mute all its tests.
 *
 * Remove @Mute and re-run to see the suppressed log line appear.
 */
@Mute(classes = [NoiseProducer::class])
class NoiseProducerSpec : FunSpec({
    test("suppresses the specific logger during an expected failure") {
        shouldThrow<RuntimeException> {
            NoiseProducer.doSomethingExpectedToFail()
        }
    }
})
