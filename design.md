# @Mute: Technical Design

## 1. Overview

### 1.1 Purpose

`@Mute` is a multi-framework test extension that declaratively silences logging noise during test
execution. It is designed to suppress the "expected exception" log spam that pollutes CI console
output.

### 1.2 Supported Frameworks

| Test framework | Integration mechanism                                        | Auto-registration                              |
|----------------|--------------------------------------------------------------|------------------------------------------------|
| JUnit 5        | `BeforeTestExecutionCallback` / `AfterTestExecutionCallback` | `@ExtendWith` on `@Mute` itself                |
| TestNG         | `IInvokedMethodListener`                                     | `META-INF/services/org.testng.ITestNGListener` |
| Spock 2        | `IAnnotationDrivenExtension` + `IMethodInterceptor`          | `@ExtensionAnnotation` on `@Mute` itself       |
| Kotest         | `BeforeEachListener` / `AfterEachListener`                   | `@AutoScan` on listener class                  |

### 1.3 Supported Logging Backends

`LogMute` implementations are provided for:

| Backend             | Module suffix | Implementation class |
|---------------------|---------------|----------------------|
| Logback             | `-logback`    | `LogbackMute`        |
| Apache Log4j 2      | `-log4j`      | `Log4j2Mute`         |
| `java.util.logging` | `-jul`        | `JulMute`            |

### 1.4 Architectural Goals

- **Locality of Behaviour:** Muting configuration lives directly on the test method or class.
- **Performance:** All three implementations set the targeted logger(s) to `OFF` at the logger object itself, so the
  framework's enabled check (`isEnabledFor` / `isEnabled` / `isLoggable`) rejects every call before parameter
  substitution or appender/handler I/O occurs. Note: eager Java string concatenation in caller code (e.g.
  `logger.warn("x=" + x)`) is evaluated by the JVM before the logger method is invoked and is unaffected — use
  parameterized logging (e.g. `logger.warn("x={}", x)`) to avoid that cost.
- **State Safety:** Original log levels are restored after every test regardless of outcome.
- **SOLID / DIP:** Test-framework mechanics are fully decoupled from logging-framework mechanics
  via the `LogMute` strategy interface and `ServiceLoader` discovery.
- **Zero Configuration:** No XML, no `testng.xml`, no `@Listeners`. Drop in the dependency and
  annotate.

---

## 2. Module Structure

```
mute-core                          LogMute + LogRestorer interfaces (shared by all)
│
├── mute-junit5/
│   ├── mute-junit5-core           @Mute annotation, MuteExtension, JUnitMuteStateStack
│   ├── mute-junit5-logback        LogbackMute  (SPI registration)
│   ├── mute-junit5-log4j          Log4j2Mute   (SPI registration)
│   └── mute-junit5-jul            JulMute      (SPI registration)
│
├── mute-testng/
│   ├── mute-testng-core           @Mute annotation, MuteListener
│   ├── mute-testng-logback        LogbackMute  (SPI registration)
│   ├── mute-testng-log4j          Log4j2Mute   (SPI registration)
│   └── mute-testng-jul            JulMute      (SPI registration)
│
├── mute-spock/
│   ├── mute-spock-core            @Mute annotation, MuteSpockExtension, MuteInterceptor
│   ├── mute-spock-logback         LogbackMute  (SPI registration)
│   ├── mute-spock-log4j           Log4j2Mute   (SPI registration)
│   └── mute-spock-jul             JulMute      (SPI registration)
│
└── mute-kotest/
    ├── mute-kotest-core           @Mute annotation, MuteKotestListener
    ├── mute-kotest-logback        LogbackMute  (SPI registration)
    ├── mute-kotest-log4j          Log4j2Mute   (SPI registration)
    └── mute-kotest-jul            JulMute      (SPI registration)
```

Each leaf module (e.g. `mute-junit5-logback`) registers its `LogMute` implementation via
`META-INF/services/io.github.thestacktracewhisperer.mute.LogMute`. The test-framework core
modules discover implementations at runtime using `ServiceLoader`, so adding a single
dependency is sufficient to wire everything together.

---

## 3. Core Abstractions (`mute-core`)

### 3.1 `LogMute` — Strategy Interface

```java
public interface LogMute {
  /**
   * Mutes the loggers for the specified target classes.
   *
   * @param targetClasses classes whose loggers should be muted;
   *                      empty array means mute the ROOT logger
   * @return a command that restores all loggers to their pre-mute state
   */
  LogRestorer mute(Class<?>[] targetClasses);
}
```

### 3.2 `LogRestorer` — Command Interface

```java
@FunctionalInterface
public interface LogRestorer {
  void restore();
}
```

The Command pattern keeps restoration logic encapsulated inside the implementation that created
it; callers never need to know anything about the underlying logging API.

---

## 4. The `@Mute` Annotation

Each framework family has its own copy of `@Mute` in its `*-core` module because each requires a
different meta-annotation to hook into its framework's extension mechanism.

| Framework | Meta-annotation on `@Mute`                         |
|-----------|----------------------------------------------------|
| JUnit 5   | `@ExtendWith(MuteExtension.class)`                 |
| TestNG    | _(none — listener self-registers via SPI)_         |
| Spock 2   | `@ExtensionAnnotation(MuteSpockExtension.class)`   |
| Kotest    | _(none — listener self-registers via `@AutoScan`)_ |

All variants share the same user-facing contract:

```java

@Target({ElementType.METHOD, ElementType.TYPE})   // TYPE only for Kotest
@Retention(RetentionPolicy.RUNTIME)
public @interface Mute {
  /**
   * Classes whose loggers should be muted.
   * Leave empty to mute the ROOT logger.
   */
  Class<?>[] classes() default {};
}
```

> **Kotest note:** `@Mute` targets `ElementType.TYPE` only. Kotest tests are lambdas inside a
> spec class, not discrete methods, so the spec class is the natural annotation target.

---

## 5. Framework Integrations

### 5.1 JUnit 5 — `MuteExtension`

**Lifecycle hooks:** `BeforeTestExecutionCallback`, `AfterTestExecutionCallback`

**Annotation lookup:** method first, then declaring class (supports class-level `@Mute`).

**State management:** `JUnitMuteStateStack` stores the `LogRestorer` in JUnit's
`ExtensionContext.Store` scoped to the current test method. Each test has its own store entry,
so nested tests are safe. Note: the restorer bookkeeping is thread-isolated, but the
underlying logger level mutation is JVM-global state — see §10 for the parallel execution caveat.

```
@Mute on method / class
        │
        ▼
MuteExtension.beforeTestExecution()
  ├── findMuteAnnotation()    ← method-level, then class-level fallback
  ├── ServiceLoader<LogMute>  ← discovers all LogMute implementations
  ├── LogMute.mute()          ← sets loggers to OFF, returns LogRestorer
  └── JUnitMuteStateStack.push(context, restorer)

        ... test runs ...

MuteExtension.afterTestExecution()
  └── JUnitMuteStateStack.popAndRestore(context)
        └── LogRestorer.restore()  ← guaranteed, even on failure
```

### 5.2 TestNG — `MuteListener`

**Lifecycle hooks:** `IInvokedMethodListener.beforeInvocation` / `afterInvocation`

**Registration:** SPI file `META-INF/services/org.testng.ITestNGListener` — no user
configuration required.

**Annotation lookup:** method first, then declaring class.

**State management:** `ThreadLocal<LogRestorer>` — one slot per thread, set in `before` and
cleared in `after`. The per-thread bookkeeping is correct for parallel TestNG runs; however, the
underlying logger level mutation is JVM-global state — see §10 for the parallel execution caveat.

```
@Mute on method / class
        │
        ▼
MuteListener.beforeInvocation()
  ├── Guards: isTestMethod() only
  ├── findMuteAnnotation()    ← method-level, then class-level fallback
  ├── LogMute.mute()
  └── ThreadLocal.set(restorer)

        ... test runs ...

MuteListener.afterInvocation()
  └── ThreadLocal.get() → restorer.restore() → ThreadLocal.remove()
```

### 5.3 Spock 2 — `MuteSpockExtension` + `MuteInterceptor`

**Integration point:** `IAnnotationDrivenExtension<Mute>` — Spock calls `visitSpecAnnotation`
and `visitFeatureAnnotation` at spec initialisation time (not at test execution time).

**Registration:** `@ExtensionAnnotation(MuteSpockExtension.class)` on `@Mute` — Spock discovers
this automatically.

**Class-level behaviour:** `visitSpecAnnotation` iterates all features in the spec and attaches
a `MuteInterceptor` to each feature method that does not already carry its own `@Mute`
(preventing double-muting).

**State management:** `MuteInterceptor` holds the mute/restore logic in a standard
`try/finally` block around `invocation.proceed()`. No external state store is needed —
each interceptor invocation is self-contained.

```
@Mute on spec / feature
        │
        ▼ (at initialisation time)
MuteSpockExtension.visitSpecAnnotation() or visitFeatureAnnotation()
  └── feature.addInterceptor(new MuteInterceptor(classes, logMutes))

        ... feature runs ...

MuteInterceptor.intercept(invocation)
  ├── LogMute.mute()
  ├── invocation.proceed()
  └── finally: LogRestorer.restore()   ← guaranteed
```

### 5.4 Kotest — `MuteKotestListener`

**Lifecycle hooks:** `BeforeEachListener`, `AfterEachListener`

**Registration:** `@AutoScan` on `MuteKotestListener` — Kotest scans the classpath at startup
and registers all `@AutoScan`-annotated listeners globally. No user configuration required.

**When to mute:** The listener checks whether the spec class (not the individual test) carries
`@Mute`. Because Kotest tests are lambdas, the spec class is the only annotation target.

**State management:** `ConcurrentHashMap<Object, LogRestorer>` keyed by the `TestCase` instance,
which is stable and unique per test execution. The bookkeeping is coroutine-safe, but the
underlying logger level mutation is JVM-global state — see §10 for the parallel execution caveat.

```
@Mute on spec class
        │
        ▼
MuteKotestListener.beforeEach(testCase)
  ├── spec class → getAnnotation(Mute.class)
  ├── LogMute.mute()
  └── restorerHolder.put(testCase, restorer)

        ... test runs (possibly on a different thread/coroutine) ...

MuteKotestListener.afterEach(testCase)
  └── restorerHolder.remove(testCase) → restorer.restore()
```

---

## 6. Logging Backend Implementations

All implementations follow the same pattern regardless of framework or backend:

1. Resolve the active logger context / manager.
2. Collect current levels for the target loggers (or the root logger if `targetClasses` is empty).
3. Set each level to `OFF`.
4. Return a `LogRestorer` lambda that restores all collected levels.

### 6.1 Logback — `LogbackMute`

Resolves `org.slf4j.LoggerFactory.getILoggerFactory()` and casts to `LoggerContext`. Throws
`IllegalStateException` if Logback is not the bound SLF4J provider.

```java
LogRestorer mute(Class<?>[] targetClasses) {
  LoggerContext ctx = (LoggerContext) loggerFactorySupplier.get();
  Map<Logger, Level> saved = new HashMap<>();
  // ... collect and set OFF ...
  return () -> saved.forEach(Logger::setLevel);
}
```

### 6.2 Log4j 2 — `Log4j2Mute`

Resolves loggers via `LogManager.getLogger()` and manipulates levels through Log4j 2's
`Configurator.setLevel()` API.

### 6.3 JUL — `JulMute`

Resolves loggers via `java.util.logging.Logger.getLogger()`. The root logger name is
`""` (empty string). Levels are saved and restored via `Logger.getLevel()` /
`Logger.setLevel()`.

---

## 7. Class-Level `@Mute`

All four frameworks support `@Mute` at the class/spec level. The annotation applies to every
test in that class — useful when an entire test class exercises noisy paths.

| Framework | Class-level mechanism                                                                  |
|-----------|----------------------------------------------------------------------------------------|
| JUnit 5   | `findMuteAnnotation()` falls back from method to `context.getRequiredTestClass()`      |
| TestNG    | `findMuteAnnotation()` falls back from method reflection to `testClass.getRealClass()` |
| Spock 2   | `visitSpecAnnotation()` adds interceptors to all features at spec initialisation time  |
| Kotest    | Only class-level is supported (`@Target(ElementType.TYPE)` only)                       |

Method-level `@Mute` always takes precedence over class-level `@Mute` where both are present
(Spock explicitly skips class-level interceptor attachment for features that already carry
their own `@Mute`).

---

## 8. State Management Summary

Each framework uses the state mechanism best suited to its threading and lifecycle model:

| Framework | Mechanism                             | Why                                                   |
|-----------|---------------------------------------|-------------------------------------------------------|
| JUnit 5   | `ExtensionContext.Store` (per-test)   | JUnit provides a first-class, scoped key/value store  |
| TestNG    | `ThreadLocal`                         | TestNG tests run on discrete threads; simple and fast |
| Spock 2   | `try/finally` in interceptor          | Interceptors own the full invocation scope            |
| Kotest    | `ConcurrentHashMap` keyed by TestCase | Kotest uses coroutines; `TestCase` is the stable key  |

---

## 9. Testing Strategy

Each `*-core` module has a unit-test suite that exercises the extension/listener in isolation
using injected `LogMute` test doubles (via the package-private constructor testing seam), with
no real logging framework on the classpath.

Each `*-logback` / `*-log4j` / `*-jul` module has a full integration test suite that:

1. Runs fixture test classes programmatically through the real framework runner.
2. Asserts that loggers are `OFF` during annotated tests.
3. Asserts that loggers are restored to their original levels after each test.
4. Asserts that state does not leak between tests (no cross-test contamination).
5. Asserts that test failures still trigger restoration (state safety).

---

## 10. Known Limitations

### 10.1 Parallel Test Execution

Logger levels are **JVM-global mutable state**. Muting a logger on Thread-1 suppresses output
for every thread in the same JVM — including concurrent non-muted tests on Thread-2.

Each framework's *bookkeeping* (restorer storage and cleanup) is correctly scoped per-test or
per-thread:

| Framework | Restorer storage         | Thread-safe bookkeeping? | Logger mutation thread-safe? |
|-----------|--------------------------|--------------------------|------------------------------|
| JUnit 5   | `ExtensionContext.Store`  | ✅ per-test context       | ❌ global state               |
| TestNG    | `ThreadLocal`             | ✅ per-thread             | ❌ global state               |
| Spock 2   | `try/finally` local var   | ✅ per-invocation         | ❌ global state               |
| Kotest    | `ConcurrentHashMap`       | ✅ per `TestCase`         | ❌ global state               |

**Practical consequence:** if tests run in parallel and any of them is annotated `@Mute`, a
concurrent non-muted test may lose log output for the duration of the muted test.

**Workaround:** restrict `@Mute`-annotated tests to sequential execution. In JUnit 5 this can
be done with `@Execution(ExecutionMode.SAME_THREAD)` on the annotated class.

