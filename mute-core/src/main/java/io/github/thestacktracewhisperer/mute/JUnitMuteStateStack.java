package io.github.thestacktracewhisperer.mute;

/*-
 * #%L
 * mute-core
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

import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Manages a per-test-context {@link LogRestorer} command so that each test
 * restores only its own mutations.
 */
public class JUnitMuteStateStack {

  private static final ExtensionContext.Namespace NAMESPACE =
    ExtensionContext.Namespace.create(MuteExtension.class);
  private static final String RESTORER_KEY = "muteRestorer";

  /**
   * Creates a new {@code JUnitMuteStateStack}.
   */
  public JUnitMuteStateStack() {
  }

  /**
   * Stores {@code restorer} in the given extension context's store so it can be
   * retrieved and invoked by {@link #popAndRestore(ExtensionContext)}.
   *
   * @param context  the JUnit extension context identifying the current test
   * @param restorer the restorer to store
   */
  public void push(ExtensionContext context, LogRestorer restorer) {
    context.getStore(NAMESPACE).put(RESTORER_KEY, restorer);
  }

  /**
   * Removes the stored {@link LogRestorer} for the given context and invokes it.
   * Does nothing if no restorer is present (idempotent).
   *
   * @param context the JUnit extension context identifying the current test
   */
  public void popAndRestore(ExtensionContext context) {
    LogRestorer restorer = context.getStore(NAMESPACE)
      .remove(RESTORER_KEY, LogRestorer.class);

    if (restorer != null) {
      restorer.restore();
    }
  }
}

