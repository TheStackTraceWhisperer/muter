package io.github.thestacktracewhisperer.muter;

import java.lang.annotation.*;

/**
 * Declaratively mutes logging output during a TestNG test execution.
 *
 * <p>Place on a test method or test class to suppress log noise caused by expected exceptions.
 * When {@link #classes()} is empty the ROOT logger is muted; otherwise only the loggers
 * for the specified classes are muted.
 *
 * <p>Original log levels are restored after every test regardless of outcome.
 *
 * <p>The {@link MuteListener} is automatically discovered via
 * {@code META-INF/services/org.testng.ITestNGListener} (TestNG 7.5+), so no explicit
 * {@code @Listeners} declaration or {@code testng.xml} configuration is required.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Mute {
    /**
     * Specify which classes' loggers should be muted.
     * If left empty, the ROOT logger is muted.
     */
    Class<?>[] classes() default {};
}
