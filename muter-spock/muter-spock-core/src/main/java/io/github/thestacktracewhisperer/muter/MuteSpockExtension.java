package io.github.thestacktracewhisperer.muter;

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

    @Override
    public void visitSpecAnnotation(Mute annotation, SpecInfo spec) {
        List<LogMuter> logMuters = loadLogMuters();
        for (FeatureInfo feature : spec.getFeatures()) {
            feature.getFeatureMethod().addInterceptor(
                    new MuteInterceptor(annotation.classes(), logMuters));
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
