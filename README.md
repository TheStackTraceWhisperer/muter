# muter-logback

A JUnit 5 extension that declaratively silences logging noise during test execution.

## Purpose

`@Mute` temporarily disables logging output for tests that intentionally trigger exceptions or other log-noisy paths, keeping CI console output clean without XML configuration changes.

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

```xml
<dependency>
    <groupId>io.github.thestacktracewhisperer</groupId>
    <artifactId>muter-logback</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

## Requirements

- Java 17+
- JUnit Jupiter 5.x
- SLF4J 2.x + Logback Classic 1.5.x
