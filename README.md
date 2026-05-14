# muter

A JUnit 5 and TestNG extension that declaratively silences logging noise during test execution.

## Purpose

`@Mute` temporarily disables logging output for tests that intentionally trigger exceptions or other log-noisy paths, keeping CI console output clean without XML configuration changes.

## Project Structure

This is a multimodule Maven project. Choose the module that matches your **test framework** and **logging framework**:

### JUnit 5

| Module | Logging framework |
|---|---|
| `muter-junit5-logback` | SLF4J + Logback Classic |
| `muter-junit5-log4j` | Apache Log4j 2 |
| `muter-junit5-jul` | `java.util.logging` (JUL, built-in JDK) |

### TestNG

| Module | Logging framework |
|---|---|
| `muter-testng-logback` | SLF4J + Logback Classic |
| `muter-testng-log4j` | Apache Log4j 2 |
| `muter-testng-jul` | `java.util.logging` (JUL, built-in JDK) |

The `muter-core`, `muter-junit5-core`, and `muter-testng-core` modules are shared dependencies pulled in automatically; you do not need to declare them explicitly.

There is **no classpath pollution** between JUnit 5 and TestNG modules — each family depends only on its own test framework.

## Usage

| Scenario | Usage | Behaviour |
|---|---|---|
| **Mute all output** | `@Mute` | Sets the ROOT logger to `OFF`. No logs from the application or third-party libraries will print during the test. |
| **Mute a specific class** | `@Mute(classes = { DatabaseRepository.class })` | Sets only the logger for `DatabaseRepository` to `OFF`. Other logs continue normally. |

Annotate a test method or a test class:

```java
@Test
@Mute
void throwsExpectedException() {
    assertThrows(IllegalArgumentException.class, () -> service.doSomething(null));
}

@Test
@Mute(classes = { PaymentService.class })
void paymentFailsGracefully() {
    assertThrows(PaymentException.class, () -> paymentService.charge(-1));
}
```

Original log levels are restored automatically after every test, regardless of pass/fail/error.

### TestNG listener auto-registration

For TestNG modules, the `MuteListener` is automatically discovered via
`META-INF/services/org.testng.ITestNGListener` (TestNG 7.5+). No explicit
`@Listeners` declaration or `testng.xml` configuration is required.

## Dependency

Pick **one** module that matches your test framework and logging framework:

### JUnit 5 + Logback Classic (SLF4J)

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-junit5-logback</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### JUnit 5 + Apache Log4j 2

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-junit5-log4j</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### JUnit 5 + java.util.logging (JUL)

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-junit5-jul</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### TestNG + Logback Classic (SLF4J)

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-testng-logback</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### TestNG + Apache Log4j 2

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-testng-log4j</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### TestNG + java.util.logging (JUL)

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-testng-jul</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

> **Note:** Projects using Apache Commons Logging with the default JUL backend are also covered by the `jul` modules, since Commons Logging routes through `java.util.logging` in that configuration.

## Requirements

- Java 21+ (Java 21 is the minimum supported runtime and release level)
- JUnit Jupiter 5.x **or** TestNG 7.5+

## Provided Dependencies

The following dependencies must be present on the classpath at runtime but are **not** bundled with this library — your project is expected to supply them:

| Module family | Required dependencies |
|---|---|
| `muter-*-logback` | SLF4J 2.x, Logback Classic 1.5.x |
| `muter-*-log4j` | Log4j 2 API + Core 2.x |
| `muter-*-jul` | _(none — JUL is part of the JDK)_ |
| `muter-junit5-*` | JUnit Jupiter 5.x |
| `muter-testng-*` | TestNG 7.5+ |

