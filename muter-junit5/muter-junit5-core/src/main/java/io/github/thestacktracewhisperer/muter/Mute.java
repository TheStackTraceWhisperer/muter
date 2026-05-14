package io.github.thestacktracewhisperer.muter;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.*;

/**
 * Declaratively mutes logging output during a JUnit 5 test execution.
 *
 * <p>Place on a test method or test class to suppress log noise caused by expected exceptions.
 * When {@link #classes()} is empty the ROOT logger is muted; otherwise only the loggers
 * for the specified classes are muted.
 *
 * <p>Original log levels are restored after every test regardless of outcome.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MuteExtension.class)
public @interface Mute {
    /**
     * Specify which classes' loggers should be muted.
     * If left empty, the ROOT logger is muted.
     */
    Class<?>[] classes() default {};
}
