# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build all modules
mvn clean install

# Build single module
mvn clean install -pl ph-telemetry

# Run all tests
mvn test

# Run single test class
mvn test -pl ph-telemetry -Dtest=TelemetryTest

# Run single test method
mvn test -pl ph-telemetry -Dtest=TelemetryTest#testNoOpFallback
```

## Project Overview

ph-telemetry is a small three-module Java library (Java 17+, built with Java 21). Parent POM: `com.helger:parent-pom`. Provides a vendor-neutral telemetry abstraction (tracing + metrics) with pluggable OpenTelemetry and Java Flight Recorder bindings. Was previously shipped as `com.helger.commons:ph-telemetry` inside `ph-commons`; that module is now `@Deprecated(forRemoval = true)`.

### Module Dependency Graph

```
ph-telemetry          ← abstraction: facades, SPIs, composites, no-op fallbacks (depends only on ph-annotations + ph-base)
  ├── ph-telemetry-otel ← OpenTelemetry binding (depends on opentelemetry-api only — NOT the SDK)
  └── ph-telemetry-jfr  ← Java Flight Recorder binding (depends on the JDK's jdk.jfr module only)
```

The split is deliberate: libraries that just want to emit telemetry depend on `ph-telemetry` and stay free of any OpenTelemetry dependency. Applications that have chosen OpenTelemetry pull `ph-telemetry-otel` at the deployment boundary; `ph-telemetry-jfr` adds no third-party dependency at all.

### Key Classes

- **`Telemetry`** (`ph-telemetry`) — static facade for tracing. Resolves `ITelemetryTracerSPI` lazily via `ServiceLoader`; falls back to a no-op tracer if none is registered. Use `Telemetry.withSpan(...)` / `Telemetry.withSpanVoid(...)` to automatically record exceptions and ensure the span is closed. For bodies that declare a checked exception, use `withSpanThrowing(...)` / `withSpanVoidThrowing(...)` — they take `IThrowingSpanFunction<T, E>` / `IThrowingSpanConsumer<E>`, catch `Throwable` (defensively guarding `recordException`), and re-throw `E` without wrapping.
- **`TelemetryMetrics`** (`ph-telemetry`) — static facade for metrics (counters, up-down counters, histograms, observable gauges). Same SPI pattern as `Telemetry`.
- **`ITelemetrySpan`**, **`ETelemetrySpanKind`**, **`TelemetryAttributes`** — the per-call abstractions. `TelemetryAttributes` is immutable and exposed via a typed visitor (`IVisitor`) so SPI implementations dispatch values without `instanceof` checks.
- **`ITelemetryTracerSPI`** / **`ITelemetryMeterSPI`** — the SPIs that backends implement; ServiceLoader-discovered, one wins.
- **`OtelTelemetryTracerSPI`** / **`OtelTelemetryMeterSPI`** (`ph-telemetry-otel`) — subclassable base classes for the OpenTelemetry binding. Concrete subclasses pass `(scopeName, scopeVersion)` to `super(...)` and are registered via `META-INF/services` in the consuming project.
- **`CompositeTelemetryTracerSPI`** / **`CompositeTelemetryMeterSPI`** (`ph-telemetry`) — fan spans and instruments out to a fixed list of delegates. Since both facades resolve only the *first* ServiceLoader result, registering one composite is the supported way to run several backends at once. Delegates run in declaration order and in reverse on `close()`.
- **`JfrTelemetryTracerSPI`** / **`JfrTelemetryMeterSPI`** (`ph-telemetry-jfr`) — directly instantiable, no scope to configure. `CJfrTelemetry` holds every emitted JFR event type name as a constant. The JFR event classes (`JfrSpanEvent`, `JfrCounterEvent`, ...) are package-private on purpose — the public handle on them is the event *name*, which is what `.jfc` files and `Recording.enable(String)` use.

### Patterns

- **Static facade + ServiceLoader-discovered SPI** — same model as SLF4J. The facade is `final` and stateless apart from the lazy SPI cache; `install(...)` exists for tests.
- **No-op fallback** — when no SPI is registered, every operation through the facade is a cheap no-op, so libraries can emit telemetry unconditionally.
- **Visitor for typed attributes** — `TelemetryAttributes.forEach(IVisitor)` dispatches to `onString` / `onLong` / `onDouble` / `onBoolean` instead of forcing callers to inspect runtime types.
- **Hungarian notation** — `m_` for instance fields, `s_` for static fields, type prefixes (`s`, `n`, `b`, `e`, `a`, ...), `_` prefix for private methods, `I` prefix for interfaces, `E` prefix for enums. Parameters are `final`. Spaces before parentheses in method calls and control flow.

## Testing

- JUnit 4 throughout.
- All tests in `ph-telemetry` use `install (null)` in `@After` to reset SPI state between tests — no global leakage.
- The OpenTelemetry binding has no unit tests in `ph-telemetry-otel`: it's a thin pass-through to the OTel API, exercised in downstream consumers. If you add tests here, prefer `opentelemetry-sdk-testing` over mocking.
- The JFR binding *is* tested end-to-end: the tests start a real `jdk.jfr.Recording`, dump it to a temp file and read the events back via `RecordingFile.readAllEvents(...)` (`JfrTestHelper`). Enable the event types by name from `CJfrTelemetry` before starting, and use `.with("period", "endChunk")` on the periodic types so they fire exactly once, at recording stop — no sleeping.

## Things to know before changing the API surface

- The SPI is small on purpose. Adding methods to `ITelemetrySpan` / `ITelemetrySpan` / `ITelemetry*` instruments breaks every implementer. Prefer `default` methods or new interfaces.
- The histogram SPI is `record(double)` only — `OtelTelemetryMeterSPI` uses `DoubleHistogram` for everything. Integer-typed histograms (`ofLongs()`) are intentionally not exposed; callers pass `(double) nValue` for count-like values.
- `OtelTelemetryTracerSPI` and `OtelTelemetryMeterSPI` cache the `Tracer` / `Meter` lazily on first use. Once cached, swapping `GlobalOpenTelemetry` at runtime has no effect on already-resolved instances. In production this is fine (the SDK is installed once at startup); in tests, prefer `Telemetry.install(...)` / `TelemetryMetrics.install(...)` over fiddling with `GlobalOpenTelemetry`.
- `ph-telemetry-otel` deliberately only depends on `opentelemetry-api`. Do not pull in `opentelemetry-sdk`, `opentelemetry-sdk-extension-autoconfigure`, exporters, or `okhttp` — those belong in the application module that initialises the SDK.
- `jdk.jfr.Event` exposes only `begin()` / `end()` / `commit()` / `set(int, Object)` — there is **no** way to backdate a start time. The span event must therefore be allocated and `begin()`-ed inside `startSpan(...)`, which in turn rules out `EventFactory`-generated dynamic event types (the attribute shape is not known until `close()`). That is why the schema is static and attributes are flattened into one escaped `key=value;...` string.
- JFR event fields are limited to the 8 primitives plus `String`, `Thread` and `Class`; there is no map-valued field type, and no native parent/child linkage between events (`JfrTelemetryTracerSPI` maintains its own per-thread span stack for `parentSpanID`).
- JFR attributes an event to the thread that **commits** it. A span opened on one thread and closed on another is recorded against the closing thread.
- `JfrTelemetryMeterSPI` registers three periodic hooks with `FlightRecorder` in its constructor and never removes them unless `shutdown()` is called — create one instance per process, and call `shutdown()` in tests that create their own.
