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

import java.lang.annotation.*;

/**
 * Declaratively mutes logging output during a TestNG test execution.
 *
 * <p>Place on a test method or test class to suppress log noise caused by expected exceptions.
 * When {@link #classes()} is empty the ROOT logger is muted; otherwise only the loggers
 * for the specified classes are muted.
 *
 * <p>Original log levels are restored after every test regardless of outcome.
 *
 * <p>The {@link MuteListener} is automatically discovered via
 * {@code META-INF/services/org.testng.ITestNGListener} (TestNG 7.5+), so no explicit
 * {@code @Listeners} declaration or {@code testng.xml} configuration is required.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
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
