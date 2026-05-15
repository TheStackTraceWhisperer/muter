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

import org.spockframework.runtime.extension.IMethodInterceptor;
import org.spockframework.runtime.extension.IMethodInvocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spock method interceptor that mutes loggers before a feature executes and restores
 * them afterward, regardless of whether the feature passes or fails.
 */
class MuteInterceptor implements IMethodInterceptor {

    private final Class<?>[] targetClasses;
    private final List<LogMuter> logMuters;

    MuteInterceptor(Class<?>[] targetClasses, List<LogMuter> logMuters) {
        this.targetClasses = targetClasses;
        this.logMuters = Collections.unmodifiableList(new ArrayList<>(logMuters));
    }

    @Override
    public void intercept(IMethodInvocation invocation) throws Throwable {
        if (logMuters.isEmpty()) {
            throw new IllegalStateException(
                    "No LogMuter found on the classpath. "
                    + "Add muter-spock-logback, muter-spock-log4j, or muter-spock-jul "
                    + "to your test dependencies.");
        }
        List<MuteRestorer> restorers = new ArrayList<>(logMuters.size());
        try {
            for (LogMuter muter : logMuters) {
                restorers.add(muter.mute(targetClasses));
            }
            invocation.proceed();
        } finally {
            for (int i = restorers.size() - 1; i >= 0; i--) {
                restorers.get(i).restore();
            }
        }
    }
}
