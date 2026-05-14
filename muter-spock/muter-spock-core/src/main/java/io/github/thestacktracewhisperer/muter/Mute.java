package io.github.thestacktracewhisperer.muter;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.*;

/**
 * Declaratively mutes logging output during a Spock 2 feature or spec execution.
 *
 * <p>Place on a feature method or specification class to suppress log noise caused by expected
 * exceptions. When {@link #classes()} is empty the ROOT logger is muted; otherwise only the
 * loggers for the specified classes are muted.
 *
 * <p>Original log levels are restored after every feature regardless of outcome.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtensionAnnotation(MuteSpockExtension.class)
public @interface Mute {
    /**
     * Specify which classes' loggers should be muted.
     * If left empty, the ROOT logger is muted.
     */
    Class<?>[] classes() default {};
}
