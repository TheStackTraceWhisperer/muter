# muter-logback

A JUnit 5 extension that declaratively silences logging noise during test execution.

## Purpose

`@Mute` temporarily disables logging output for tests that intentionally trigger exceptions or other log-noisy paths, keeping CI console output clean without XML configuration changes.

## Project Structure

This is a multimodule Maven project. Choose the module that matches your logging framework:

| Module | Logging framework |
|---|---|
| `muter-logback` | SLF4J + Logback Classic |
| `muter-log4j` | Apache Log4j 2 |
| `muter-jul` | `java.util.logging` (JUL, built-in JDK) |

The `muter-core` module is a shared dependency pulled in automatically; you do not need to declare it explicitly.

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

## Dependency

Pick **one** module that matches your logging framework:

### Logback Classic (SLF4J)

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-logback</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### Apache Log4j 2

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-log4j</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

### java.util.logging (JUL)

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-jul</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

> **Note:** Projects using Apache Commons Logging with the default JUL backend are also covered by `muter-jul`, since Commons Logging routes through `java.util.logging` in that configuration.

## Requirements

- Java 17+ (Java 17 is the minimum supported runtime and release level)
- JUnit Jupiter 5.x

## Provided Dependencies

The following dependencies must be present on the classpath at runtime but are **not** bundled with this library — your project is expected to supply them:

| Module | Required dependencies |
|---|---|
| `muter-logback` | SLF4J 2.x, Logback Classic 1.5.x |
| `muter-log4j` | Log4j 2 API + Core 2.x |
| `muter-jul` | _(none — JUL is part of the JDK)_ |
