package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Manages a per-test-context {@link MuteRestorer} command so that each test
 * restores only its own mutations.
 */
public class JUnitMuteStateStack {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(MuteExtension.class);
    private static final String RESTORER_KEY = "muteRestorer";

    public void push(ExtensionContext context, MuteRestorer restorer) {
        context.getStore(NAMESPACE).put(RESTORER_KEY, restorer);
    }

    public void popAndRestore(ExtensionContext context) {
        MuteRestorer restorer = context.getStore(NAMESPACE).remove(RESTORER_KEY, MuteRestorer.class);

        if (restorer != null) {
            restorer.restore();
        }
    }
}
