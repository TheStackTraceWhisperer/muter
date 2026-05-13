package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension registered by {@link Mute}. Delegates muting to {@link LogMuter}
 * and state management to {@link JUnitMuteStateStack}.
 */
public class MuteExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final LogMuter logMuter = new LogbackMuter();
    private final JUnitMuteStateStack stateStack = new JUnitMuteStateStack();

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        context.getElement()
               .map(element -> element.getAnnotation(Mute.class))
               .ifPresent(annotation -> {
                   MuteRestorer restorer = logMuter.mute(annotation);
                   stateStack.push(context, restorer);
               });
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        context.getElement()
               .map(element -> element.getAnnotation(Mute.class))
               .ifPresent(annotation -> stateStack.popAndRestore(context));
    }
}
