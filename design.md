# @Mute JUnit 5 Extension: Technical Specification
## 1. Overview
### 1.1 Purpose
The @Mute extension provides a declarative, programmatic mechanism to temporarily disable logging output during specific JUnit 5 test executions. It is designed to silence the "expected exception" noise that pollutes Continuous Integration (CI) console logs, without requiring complex XML configurations or heavy framework-specific application contexts.
### 1.2 Architectural Goals
 * **Locality of Behavior:** Muting configuration must live directly on the test method.
 * **Performance:** Muting must short-circuit log generation before string formatting or event allocation occurs.
 * **State Safety:** Original logging levels must be guaranteed to restore regardless of test outcome (Pass/Fail/Error).
 * **SOLID Principles:** The JUnit framework mechanics must be decoupled from the specific underlying logging implementation (e.g., Logback).
## 2. Technical Specification
### 2.1 Framework Dependencies
 * **Testing:** JUnit Jupiter API (JUnit 5) org.junit.jupiter.api.extension.*
 * **Logging API:** SLF4J org.slf4j.*
 * **Logging Implementation:** Logback Classic ch.qos.logback.classic.*
### 2.2 Lifecycle Integration
The extension hooks into the JUnit 5 test lifecycle using two specific interfaces:
 1. BeforeTestExecutionCallback: Executes immediately before the test method is invoked. Used to capture current log levels, push them to a state stack, and apply Level.OFF.
 2. AfterTestExecutionCallback: Executes immediately after the test method completes. Used to pop the state stack and restore the original Level.
### 2.3 State Management
Because JUnit 5 test instances share static logger singletons, state management must be precise to prevent permanent log suppression.
 * **Storage:** State is stored in JUnit's ExtensionContext.Store.
 * **Scope:** Using context.getRoot().getStore() ensures the stack survives throughout nested test lifecycles.
 * **Namespace:** Isolated via Namespace.create(MuteExtension.class) to prevent key collisions with other extensions.
 * **Data Structure:** A Deque (Stack) is utilized to manage state, allowing nested tests to push and pop their specific environmental mutations sequentially.
## 3. Implementation Code
### 3.1 The Annotation (@Mute)
Acts as a meta-annotation by automatically registering the extension via @ExtendWith.
```java
import org.junit.jupiter.api.extension.ExtendWith;
import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(MuteExtension.class)
public @interface Mute {
    /**
     * Specify which classes' loggers should be muted.
     * If left empty, it will mute the ROOT logger.
     */
    Class<?>[] classes() default {};
}

```
### 3.2 The Command Interface (MuteRestorer)
Provides a decoupled execution trigger for the JUnit extension to reverse the mute operation.
```java
@FunctionalInterface
public interface MuteRestorer {
    void restore();
}

```
### 3.3 The Logging Abstraction (LogMute)
Inverts the dependency so the extension does not rely directly on Logback.
```java
public interface LogMute {
    /**
     * Mutes the loggers specified in the annotation.
     * @return A command to restore the loggers to their original state.
     */
    MuteRestorer mute(Mute annotation);
}

```
### 3.4 The Logback Implementation (LogbackMute)
Handles the direct manipulation of the ch.qos.logback API.
```java
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;

public class LogbackMute implements LogMute {

    @Override
    public MuteRestorer mute(Mute annotation) {
        Map<Logger, Level> originalLevels = new HashMap<>();

        if (annotation.classes().length == 0) {
            muteRoot(originalLevels);
        } else {
            muteClasses(annotation.classes(), originalLevels);
        }

        // Return the Command to undo these specific mutations
        return () -> originalLevels.forEach(Logger::setLevel);
    }

    private void muteRoot(Map<Logger, Level> state) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        state.put(rootLogger, rootLogger.getLevel());
        rootLogger.setLevel(Level.OFF);
    }

    private void muteClasses(Class<?>[] classes, Map<Logger, Level> state) {
        for (Class<?> clazz : classes) {
            Logger logger = (Logger) LoggerFactory.getLogger(clazz);
            state.put(logger, logger.getLevel());
            logger.setLevel(Level.OFF);
        }
    }
}

```
### 3.5 The State Manager (JUnitMuteStateStack)
Isolates the boilerplate required to interact with JUnit 5's internal ExtensionContext.Store.
```java
import org.junit.jupiter.api.extension.ExtensionContext;
import java.util.ArrayDeque;
import java.util.Deque;

public class JUnitMuteStateStack {
    
    private static final ExtensionContext.Namespace NAMESPACE = 
            ExtensionContext.Namespace.create(MuteExtension.class);
    private static final String STACK_KEY = "muteRestorerStack";

    @SuppressWarnings("unchecked")
    public void push(ExtensionContext context, MuteRestorer restorer) {
        Deque<MuteRestorer> stack = context.getRoot()
                .getStore(NAMESPACE)
                .getOrComputeIfAbsent(STACK_KEY, k -> new ArrayDeque<>(), Deque.class);
        stack.push(restorer);
    }

    @SuppressWarnings("unchecked")
    public void popAndRestore(ExtensionContext context) {
        Deque<MuteRestorer> stack = context.getRoot()
                .getStore(NAMESPACE)
                .get(STACK_KEY, Deque.class);

        if (stack != null && !stack.isEmpty()) {
            stack.pop().restore(); 
        }
    }
}

```
### 3.6 The Orchestrator (MuteExtension)
The main entry point registered by JUnit. It delegates work between the LogMute and the JUnitMuteStateStack.
```java
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class MuteExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private final LogMute logMute = new LogbackMute();
    private final JUnitMuteStateStack stateStack = new JUnitMuteStateStack();

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        context.getElement()
               .map(element -> element.getAnnotation(Mute.class))
               .ifPresent(annotation -> {
                   MuteRestorer restorer = logMute.mute(annotation);
                   stateStack.push(context, restorer);
               });
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        context.getElement()
               .map(element -> element.getAnnotation(Mute.class))
               .ifPresent(annotation -> stateStack.popAndRestore(context));
    }
}

```
## 4. Usage Specification
To use the extension, developers place the @Mute annotation either at the class level or directly on the individual test method expected to generate noise.
| Scenario | Usage | Behavior |
|---|---|---|
| **Mute All Output** | @Mute | Modifies the ROOT logger to OFF. No logs will print from the application or third-party dependencies during the test. |
| **Mute Specific Target** | @Mute(classes = { DatabaseRepository.class }) | Modifies only the logger tied to DatabaseRepository.class. Other application logs will continue to print normally. |
## 5. Testing Plan
### 5.1 Testing Objectives
To prove the structural integrity of the MuteExtension, the testing strategy must guarantee that:
 1. Loggers are correctly transitioned to OFF prior to test execution.
 2. Loggers are flawlessly restored to their pre-execution state regardless of where they started.
 3. Tests running in sequence do not experience "state leakage" (i.e., a muted test does not permanently break logging for subsequent tests).
### 5.2 Test Strategy
We will use an explicitly ordered JUnit 5 test class (@TestMethodOrder) to simulate a sequential lifecycle and assert directly against the underlying Logback Logger.getLevel() state.
### 5.3 Automated Test Suite
```java
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MuteExtensionTest {

    private static class SpecificDummyService {}

    // Cast SLF4J loggers to Logback loggers to inspect their raw Level
    private static final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private static final Logger specificLogger = (Logger) LoggerFactory.getLogger(SpecificDummyService.class);

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
    @Mute // Targets ROOT
    @DisplayName("Execution: @Mute cleanly turns OFF the root logger")
    void rootLoggerIsMuted() {
        assertEquals(Level.OFF, rootLogger.getLevel());
        // Specific logger was independently set to DEBUG; it should not inherit OFF from Root in this context
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

```
*I searched for "JUnit 5" extension technical specification template OR structure and JUnit 5 ExtensionContext State management best practices. The resulting document structure and architectural validation of the ExtensionContext.Store usage, namespaces, and lifecycle callbacks are grounded in the official JUnit 5 User Guide and community best practices for authoring robust extensions.*
