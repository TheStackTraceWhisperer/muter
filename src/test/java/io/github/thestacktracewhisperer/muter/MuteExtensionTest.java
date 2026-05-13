package io.github.thestacktracewhisperer.muter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies {@link MuteExtension} end-to-end.
 *
 * <p>Each test is fully self-contained: it sets a known logger level, executes a
 * small fixture class via the JUnit Platform Launcher, then asserts the level is
 * restored — regardless of how the fixture test itself ends.  No shared mutable
 * state and no {@code @Order} dependencies between tests.
 */
class MuteExtensionTest {

    // ---------- Logger handles ----------

    private static final Logger ROOT =
            (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private static final Logger SERVICE_A =
            (Logger) LoggerFactory.getLogger(ServiceA.class);
    private static final Logger SERVICE_B =
            (Logger) LoggerFactory.getLogger(ServiceB.class);

    // ---------- Save / restore real levels around every test ----------

    private Level savedRoot;
    private Level savedServiceA;
    private Level savedServiceB;

    @BeforeEach
    void saveLevels() {
        savedRoot     = ROOT.getLevel();
        savedServiceA = SERVICE_A.getLevel();
        savedServiceB = SERVICE_B.getLevel();
    }

    @AfterEach
    void restoreLevels() {
        ROOT.setLevel(savedRoot);
        SERVICE_A.setLevel(savedServiceA);
        SERVICE_B.setLevel(savedServiceB);
    }

    // ---------- Tests ----------

    @Test
    @DisplayName("Root logger is muted during @Mute test and restored afterward")
    void rootLoggerMutedAndRestored() {
        ROOT.setLevel(Level.INFO);
        runFixture(RootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Root logger is restored even after a @Mute test that throws")
    void rootLoggerRestoredAfterFailingTest() {
        ROOT.setLevel(Level.WARN);
        runFixture(RootMuteThrowsFixture.class);   // fixture test fails; restoration must still occur
        assertEquals(Level.WARN, ROOT.getLevel());
    }

    @Test
    @DisplayName("Class-level @Mute mutes the root logger for every test in the class")
    void classLevelMuteAnnotationMutesAndRestores() {
        ROOT.setLevel(Level.INFO);
        runFixture(ClassLevelRootMuteFixture.class);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Specific class logger is muted; root logger is unaffected")
    void specificClassLoggerMutedAndRestored() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        runFixture(SingleClassMuteFixture.class);
        assertEquals(Level.DEBUG, SERVICE_A.getLevel(), "ServiceA logger should be restored");
        assertEquals(Level.INFO,  ROOT.getLevel(),      "Root logger should be unaffected");
    }

    @Test
    @DisplayName("Multiple class loggers are all muted and restored independently")
    void multipleClassLoggersMutedAndRestored() {
        SERVICE_A.setLevel(Level.TRACE);
        SERVICE_B.setLevel(Level.ERROR);
        runFixture(MultipleClassMuteFixture.class);
        assertEquals(Level.TRACE, SERVICE_A.getLevel());
        assertEquals(Level.ERROR, SERVICE_B.getLevel());
    }

    @Test
    @DisplayName("A null (inherited) logger level is correctly restored to null after muting")
    void nullInheritedLevelIsRestored() {
        SERVICE_A.setLevel(null);   // inherit from parent — getLevel() returns null
        assertNull(SERVICE_A.getLevel());
        runFixture(NullLevelMuteFixture.class);
        assertNull(SERVICE_A.getLevel(), "Null (inherited) level should be restored after muting");
    }

    @Test
    @DisplayName("Nested @Mute tests each restore only their own logger")
    void nestedMuteRestoresBothLoggers() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        runFixture(NestedMuteFixture.class);
        assertEquals(Level.INFO,  ROOT.getLevel(),      "Root logger restored after outer @Mute");
        assertEquals(Level.DEBUG, SERVICE_A.getLevel(), "ServiceA logger restored after nested @Mute");
    }

    // ---------- Fixture classes ----------

    /** Method-level {@code @Mute} — mutes root logger. */
    static class RootMuteFixture {
        @Test @Mute void run() {}
    }

    /** Method-level {@code @Mute} where the test body throws — restoration still expected. */
    static class RootMuteThrowsFixture {
        @Test @Mute void run() { throw new RuntimeException("expected"); }
    }

    /**
     * Class-level {@code @Mute} — the extension must discover the annotation on the
     * class when no method-level annotation is present.
     */
    @Mute
    static class ClassLevelRootMuteFixture {
        @Test void run() {}
    }

    /** Method-level {@code @Mute} targeting a single specific logger. */
    static class SingleClassMuteFixture {
        @Test @Mute(classes = ServiceA.class) void run() {}
    }

    /** Method-level {@code @Mute} targeting two class loggers simultaneously. */
    static class MultipleClassMuteFixture {
        @Test @Mute(classes = {ServiceA.class, ServiceB.class}) void run() {}
    }

    /**
     * ServiceA logger intentionally has a {@code null} (inherited) level before the test runs;
     * the extension must restore it to {@code null} rather than some concrete level.
     */
    static class NullLevelMuteFixture {
        @Test @Mute(classes = ServiceA.class) void run() {}
    }

    /**
     * Outer test uses method-level {@code @Mute} (root); the {@code @Nested} inner test
     * uses method-level {@code @Mute(classes = ServiceA.class)}.
     * Both loggers must be independently restored after their respective tests.
     */
    static class NestedMuteFixture {
        @Test @Mute void outer() {}

        @Nested
        class Inner {
            @Test @Mute(classes = ServiceA.class) void inner() {}
        }
    }

    // ---------- Dummy service marker classes ----------

    private static class ServiceA {}
    private static class ServiceB {}

    // ---------- Helper ----------

    private static void runFixture(Class<?> fixtureClass) {
        LauncherFactory.create().execute(
                LauncherDiscoveryRequestBuilder.request()
                        .selectors(DiscoverySelectors.selectClass(fixtureClass))
                        .build());
    }
}

