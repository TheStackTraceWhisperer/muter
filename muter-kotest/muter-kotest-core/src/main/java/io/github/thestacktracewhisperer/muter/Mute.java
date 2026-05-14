package io.github.thestacktracewhisperer.muter;

import java.lang.annotation.*;

/**
 * Declaratively mutes logging output during all tests in a Kotest specification.
 *
 * <p>Place on a Kotest {@code Spec} class to suppress log noise caused by expected exceptions.
 * When {@link #classes()} is empty the ROOT logger is muted; otherwise only the loggers for
 * the specified classes are muted.
 *
 * <p>The muting is applied via {@link MuteKotestListener}, which is auto-registered via
 * {@code @AutoScan}. Original log levels are restored after every test regardless of outcome.
 *
 * <p>At least one {@code LogMuter} implementation must be present on the test classpath
 * (e.g., muter-kotest-logback, muter-kotest-log4j, or muter-kotest-jul).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Mute {
    /**
     * Specify which classes' loggers should be muted.
     * If left empty, the ROOT logger is muted.
     */
    Class<?>[] classes() default {};
}
