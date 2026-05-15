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

import org.spockframework.runtime.extension.IAnnotationDrivenExtension;
import org.spockframework.runtime.model.FeatureInfo;
import org.spockframework.runtime.model.SpecInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Spock 2 extension registered by {@link Mute} via {@code @ExtensionAnnotation}.
 * Delegates the actual logger manipulation to all {@link LogMuter} implementations
 * found on the classpath via {@link ServiceLoader}.
 *
 * <p>{@link Mute} may be placed on a feature method <em>or</em> on a specification class.
 * When placed on a class the annotation applies to every feature in that specification.
 *
 * <p>At least one {@code LogMuter} implementation must be present on the test classpath
 * (e.g., muter-spock-logback, muter-spock-log4j, or muter-spock-jul); otherwise an
 * {@link IllegalStateException} is thrown when the first {@link Mute}-annotated feature runs.
 */
public class MuteSpockExtension implements IAnnotationDrivenExtension<Mute> {

    /** Public constructor required by Spock's annotation-driven extension mechanism. */
    public MuteSpockExtension() {}

    @Override
    public void visitSpecAnnotation(Mute annotation, SpecInfo spec) {
        List<LogMuter> logMuters = loadLogMuters();
        for (FeatureInfo feature : spec.getFeatures()) {
            if (feature.getFeatureMethod().getReflection().getAnnotation(Mute.class) == null) {
                feature.getFeatureMethod().addInterceptor(
                        new MuteInterceptor(annotation.classes(), logMuters));
            }
        }
    }

    @Override
    public void visitFeatureAnnotation(Mute annotation, FeatureInfo feature) {
        feature.getFeatureMethod().addInterceptor(
                new MuteInterceptor(annotation.classes(), loadLogMuters()));
    }

    private static List<LogMuter> loadLogMuters() {
        List<LogMuter> discovered = new ArrayList<>();
        ServiceLoader.load(LogMuter.class).forEach(discovered::add);
        return Collections.unmodifiableList(discovered);
    }
}
