package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages a per-root-context stack of {@link MuteRestorer} commands so that
 * nested and sequential tests each restore only their own mutations.
 */
public class JUnitMuteStateStack {

    private static final ExtensionContext.Namespace NAMESPACE =
            ExtensionContext.Namespace.create(MuteExtension.class);
    private static final String STACK_KEY = "muteRestorerStack";

    @SuppressWarnings("unchecked")
    public void push(ExtensionContext context, MuteRestorer restorer) {
        Deque<MuteRestorer> stack = context.getRoot()
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(STACK_KEY, k -> new ArrayDeque<>(), Deque.class);
        stack.push(restorer);
    }

    @SuppressWarnings("unchecked")
    public void popAndRestore(ExtensionContext context) {
        Deque<MuteRestorer> stack = context.getRoot()
                .getStore(NAMESPACE)
                .get(STACK_KEY, Deque.class);

        if (stack != null && !stack.isEmpty()) {
            stack.pop().restore();
        }
    }
}
