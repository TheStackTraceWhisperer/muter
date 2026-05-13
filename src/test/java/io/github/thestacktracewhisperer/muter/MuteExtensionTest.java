package io.github.thestacktracewhisperer.muter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MuteExtensionTest {

    private static class SpecificDummyService {}

    // Cast SLF4J loggers to Logback loggers to inspect their raw Level
    private static final Logger rootLogger =
            (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private static final Logger specificLogger =
            (Logger) LoggerFactory.getLogger(SpecificDummyService.class);

    private static Level originalRootLevel;
    private static Level originalSpecificLevel;

    @BeforeAll
    static void setupBaseline() {
        originalRootLevel = rootLogger.getLevel();
        originalSpecificLevel = specificLogger.getLevel();

        // Establish an arbitrary baseline to prove restoration works
        rootLogger.setLevel(Level.INFO);
        specificLogger.setLevel(Level.DEBUG);
    }

    @AfterAll
    static void restoreBaseline() {
        rootLogger.setLevel(originalRootLevel);
        specificLogger.setLevel(originalSpecificLevel);
    }

    @Test
    @Order(1)
    @DisplayName("Pre-condition: Baseline log levels are respected")
    void verifyBaselineIsCorrect() {
        assertEquals(Level.INFO, rootLogger.getLevel());
        assertEquals(Level.DEBUG, specificLogger.getLevel());
    }

    @Test
    @Order(2)
    @Mute
    @DisplayName("Execution: @Mute cleanly turns OFF the root logger")
    void rootLoggerIsMuted() {
        assertEquals(Level.OFF, rootLogger.getLevel());
        // Specific logger was independently set to DEBUG; it should not be affected
        assertEquals(Level.DEBUG, specificLogger.getLevel());
    }

    @Test
    @Order(3)
    @DisplayName("Restoration: State Stack correctly restores Root logger")
    void rootLoggerIsRestored() {
        assertEquals(Level.INFO, rootLogger.getLevel(), "Failed to restore Root logger after Step 2");
    }

    @Test
    @Order(4)
    @Mute(classes = SpecificDummyService.class)
    @DisplayName("Execution: @Mute cleanly turns OFF specific targeted loggers")
    void specificLoggerIsMuted() {
        assertEquals(Level.OFF, specificLogger.getLevel());
        assertEquals(Level.INFO, rootLogger.getLevel(), "Root logger should be unaffected");
    }

    @Test
    @Order(5)
    @DisplayName("Restoration: State Stack correctly restores specific logger")
    void specificLoggerIsRestored() {
        assertEquals(Level.DEBUG, specificLogger.getLevel(), "Failed to restore Specific logger after Step 4");
    }
}
