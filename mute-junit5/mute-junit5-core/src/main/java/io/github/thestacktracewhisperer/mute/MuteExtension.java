package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-junit5-core
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

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * JUnit 5 extension registered by {@link Mute}. Delegates the actual logger
 * manipulation to all {@link LogMute} implementations found on the classpath
 * via {@link ServiceLoader}.
 *
 * <p>{@link Mute} may be placed on a test method <em>or</em> on a test class.
 * When placed on a class the annotation is inherited by every test method in
 * that class.
 *
 * <p>At least one {@code LogMute} implementation must be present on the test
 * classpath (e.g., mute-junit5-logback, mute-junit5-log4j, or
 * mute-junit5-jul); otherwise an {@link IllegalStateException} is thrown
 * when the first {@link Mute}-annotated test runs.
 */
public class MuteExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final JUnitMuteStateStack stateStack = new JUnitMuteStateStack();
    private final List<LogMute> logMutes;

    /** Production constructor: discovers {@link LogMute} implementations via {@link ServiceLoader}. */
    public MuteExtension() {
        List<LogMute> discovered = new ArrayList<>();
        ServiceLoader.load(LogMute.class).forEach(discovered::add);
        this.logMutes = Collections.unmodifiableList(discovered);
    }

    /**
     * Testing seam that allows controlled {@link LogMute} injection in unit tests.
     * Production use should rely on {@link #MuteExtension()}.
     */
    MuteExtension(List<LogMute> logMutes) {
        this.logMutes = Collections.unmodifiableList(new ArrayList<>(logMutes));
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        findMuteAnnotation(context).ifPresent(annotation -> {
            if (logMutes.isEmpty()) {
                throw new IllegalStateException(
                        "No LogMute found on the classpath. "
                        + "Add mute-junit5-logback, mute-junit5-log4j, or mute-junit5-jul "
                        + "to your test dependencies.");
            }
            List<MuteRestorer> restorers = new ArrayList<>(logMutes.size());
            try {
                for (LogMute mute : logMutes) {
                    restorers.add(mute.mute(annotation.classes()));
                }
            } catch (RuntimeException | Error e) {
                for (int i = restorers.size() - 1; i >= 0; i--) {
                    restorers.get(i).restore();
                }
                throw e;
            }
            stateStack.push(context, () -> {
                for (int i = restorers.size() - 1; i >= 0; i--) {
                    restorers.get(i).restore();
                }
            });
        });
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        findMuteAnnotation(context)
                .ifPresent(annotation -> stateStack.popAndRestore(context));
    }

    /**
     * Looks for {@link Mute} on the test method first; falls back to the test class
     * to support class-level {@code @Mute}.
     */
    private Optional<Mute> findMuteAnnotation(ExtensionContext context) {
        return context.getElement()
                .map(element -> element.getAnnotation(Mute.class))
                .or(() -> Optional.ofNullable(context.getRequiredTestClass().getAnnotation(Mute.class)));
    }
}
