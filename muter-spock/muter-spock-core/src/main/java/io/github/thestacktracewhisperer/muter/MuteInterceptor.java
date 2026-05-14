package io.github.thestacktracewhisperer.muter;

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
