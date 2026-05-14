package io.github.thestacktracewhisperer.muter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.slf4j.LoggerFactory;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link MuteExtension} end-to-end.
 *
 * <p>Each test is fully self-contained: it sets a known logger level, executes a
 * small fixture class via the JUnit Platform Launcher, then asserts the level is
 * restored — regardless of how the fixture test itself ends.  No shared mutable
 * state and no {@code @Order} dependencies between tests.
 */
class MuteExtensionTest {
    private static final AnnotatedElement NO_ELEMENT = null;

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

    @Test
    @DisplayName("Missing Logback binding fails fast with helpful error")
    void missingLogbackBindingFailsFast() throws Exception {
        MuteExtension extension = new MuteExtension(() -> new Object());
        Method method = RootMuteFixture.class.getDeclaredMethod("run");
        ExtensionContext context = contextFor(method, RootMuteFixture.class);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> extension.beforeTestExecution(context));
        assertEquals(
                "muter-logback requires Logback Classic on the classpath; found: java.lang.Object",
                exception.getMessage());
    }

    @Test
    @DisplayName("Null logger factory fails fast with null type in error")
    void nullLoggerFactoryFailsFast() throws Exception {
        MuteExtension extension = new MuteExtension(() -> null);
        Method method = RootMuteFixture.class.getDeclaredMethod("run");
        ExtensionContext context = contextFor(method, RootMuteFixture.class);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> extension.beforeTestExecution(context));
        assertEquals(
                "muter-logback requires Logback Classic on the classpath; found: null",
                exception.getMessage());
    }

    @Test
    @DisplayName("Context without @Mute annotation is ignored")
    void contextWithoutMuteAnnotationIsIgnored() throws Exception {
        MuteExtension extension = new MuteExtension();
        Method method = NoMuteFixture.class.getDeclaredMethod("run");
        ExtensionContext context = contextFor(method, NoMuteFixture.class);

        assertDoesNotThrow(() -> extension.beforeTestExecution(context));
        assertDoesNotThrow(() -> extension.afterTestExecution(context));
    }

    @Test
    @DisplayName("Class-level @Mute is used when method lacks @Mute")
    void classLevelMuteIsUsedWhenMethodLacksMute() throws Exception {
        MuteExtension extension = new MuteExtension();
        ROOT.setLevel(Level.INFO);
        Method method = NoMuteFixture.class.getDeclaredMethod("run");
        ExtensionContext context = contextFor(method, ClassLevelOnlyFixture.class);

        extension.beforeTestExecution(context);
        assertEquals(Level.OFF, ROOT.getLevel());
        extension.afterTestExecution(context);
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("State stack restores once and no-ops when empty")
    void stateStackRestoresOnceAndNoopsWhenEmpty() {
        JUnitMuteStateStack stateStack = new JUnitMuteStateStack();
        ExtensionContext context = contextFor(NO_ELEMENT, NoMuteFixture.class);
        AtomicInteger restores = new AtomicInteger();

        stateStack.push(context, restores::incrementAndGet);
        stateStack.popAndRestore(context);
        stateStack.popAndRestore(context);

        assertEquals(1, restores.get());
    }

    @Test
    @DisplayName("Direct mute with empty classes mutes root and restores level")
    void directMuteWithEmptyClassesMutesRootAndRestoresLevel() {
        ROOT.setLevel(Level.INFO);
        MuteRestorer restorer = new MuteExtension().mute(muteAnnotation());

        assertEquals(Level.OFF, ROOT.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
    }

    @Test
    @DisplayName("Direct mute with classes mutes only selected loggers and restores")
    void directMuteWithClassesMutesOnlySelectedLoggersAndRestores() {
        ROOT.setLevel(Level.INFO);
        SERVICE_A.setLevel(Level.DEBUG);
        SERVICE_B.setLevel(Level.ERROR);
        MuteRestorer restorer = new MuteExtension().mute(muteAnnotation(ServiceA.class, ServiceB.class));

        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.OFF, SERVICE_A.getLevel());
        assertEquals(Level.OFF, SERVICE_B.getLevel());
        restorer.restore();
        assertEquals(Level.INFO, ROOT.getLevel());
        assertEquals(Level.DEBUG, SERVICE_A.getLevel());
        assertEquals(Level.ERROR, SERVICE_B.getLevel());
    }

    // ---------- Fixture classes ----------

    /** Method-level {@code @Mute} — mutes root logger. */
    static class RootMuteFixture {
        @Test @Mute void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
        }
    }

    /** Method-level {@code @Mute} where the test body throws — restoration still expected. */
    static class RootMuteThrowsFixture {
        @Test @Mute void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
            throw new RuntimeException("expected");
        }
    }

    /**
     * Class-level {@code @Mute} — the extension must discover the annotation on the
     * class when no method-level annotation is present.
     */
    @Mute
    static class ClassLevelRootMuteFixture {
        @Test void run() {
            assertEquals(Level.OFF, ROOT.getLevel());
        }
    }

    /** Method-level {@code @Mute} targeting a single specific logger. */
    static class SingleClassMuteFixture {
        @Test @Mute(classes = ServiceA.class) void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.INFO, ROOT.getLevel());
        }
    }

    /** Method-level {@code @Mute} targeting two class loggers simultaneously. */
    static class MultipleClassMuteFixture {
        @Test @Mute(classes = {ServiceA.class, ServiceB.class}) void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
            assertEquals(Level.OFF, SERVICE_B.getLevel());
        }
    }

    /**
     * ServiceA logger intentionally has a {@code null} (inherited) level before the test runs;
     * the extension must restore it to {@code null} rather than some concrete level.
     */
    static class NullLevelMuteFixture {
        @Test @Mute(classes = ServiceA.class) void run() {
            assertEquals(Level.OFF, SERVICE_A.getLevel());
        }
    }

    /**
     * Outer test uses method-level {@code @Mute} (root); the {@code @Nested} inner test
     * uses method-level {@code @Mute(classes = ServiceA.class)}.
     * Both loggers must be independently restored after their respective tests.
     */
    static class NestedMuteFixture {
        @Test @Mute void outer() {
            assertEquals(Level.OFF, ROOT.getLevel());
        }

        @Nested
        class Inner {
            @Test @Mute(classes = ServiceA.class) void inner() {
                assertEquals(Level.OFF, SERVICE_A.getLevel());
                assertEquals(Level.INFO, ROOT.getLevel());
            }
        }
    }

    static class NoMuteFixture {
        @Test void run() {}
    }

    @Mute
    static class ClassLevelOnlyFixture {
        @Test void run() {}
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

    private static Mute muteAnnotation(Class<?>... classes) {
        return (Mute) Proxy.newProxyInstance(
                Mute.class.getClassLoader(),
                new Class<?>[] {Mute.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "classes" -> classes;
                    case "annotationType" -> Mute.class;
                    case "toString" -> "@Mute(classes=" + Arrays.toString(classes) + ")";
                    case "hashCode" -> 31 * Mute.class.hashCode() + Arrays.hashCode(classes);
                    case "equals" -> args[0] instanceof Mute other && Arrays.equals(classes, other.classes());
                    default -> method.getDefaultValue();
                });
    }

    private static ExtensionContext contextFor(AnnotatedElement element, Class<?> testClass) {
        Map<ExtensionContext.Namespace, ExtensionContext.Store> stores = new HashMap<>();

        return (ExtensionContext) Proxy.newProxyInstance(
                ExtensionContext.class.getClassLoader(),
                new Class<?>[] {ExtensionContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getElement" -> Optional.ofNullable(element);
                    case "getRequiredTestClass" -> testClass;
                    case "getStore" -> stores.computeIfAbsent(
                            (ExtensionContext.Namespace) args[0],
                            ignored -> createStore());
                    case "toString" -> "ExtensionContextProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Not needed in this test: " + method.getName());
                });
    }

    private static ExtensionContext.Store createStore() {
        Map<Object, Object> values = new HashMap<>();

        return (ExtensionContext.Store) Proxy.newProxyInstance(
                ExtensionContext.Store.class.getClassLoader(),
                new Class<?>[] {ExtensionContext.Store.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "put" -> {
                        values.put(args[0], args[1]);
                        yield null;
                    }
                    case "get" -> args.length == 1
                            ? values.get(args[0])
                            : ((Class<?>) args[1]).cast(values.get(args[0]));
                    case "remove" -> args.length == 1
                            ? values.remove(args[0])
                            : ((Class<?>) args[1]).cast(values.remove(args[0]));
                    case "toString" -> "StoreProxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException("Not needed in this test: " + method.getName());
                });
    }
}
