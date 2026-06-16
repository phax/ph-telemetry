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

ph-telemetry is a small two-module Java library (Java 17+, built with Java 21). Parent POM: `com.helger:parent-pom`. Provides a vendor-neutral telemetry abstraction (tracing + metrics) with a pluggable OpenTelemetry binding. Was previously shipped as `com.helger.commons:ph-telemetry` inside `ph-commons`; that module is now `@Deprecated(forRemoval = true)`.

### Module Dependency Graph

```
ph-telemetry          ← abstraction: facades, SPIs, no-op fallbacks (depends only on ph-annotations + ph-base)
  └── ph-telemetry-otel ← OpenTelemetry binding (depends on opentelemetry-api only — NOT the SDK)
```

The split is deliberate: libraries that just want to emit telemetry depend on `ph-telemetry` and stay free of any OpenTelemetry dependency. Applications that have chosen OpenTelemetry pull `ph-telemetry-otel` at the deployment boundary.

### Key Classes

- **`Telemetry`** (`ph-telemetry`) — static facade for tracing. Resolves `ITelemetryTracerSPI` lazily via `ServiceLoader`; falls back to a no-op tracer if none is registered. Use `Telemetry.withSpan(...)` / `Telemetry.withSpanVoid(...)` to automatically record exceptions and ensure the span is closed. For bodies that declare a checked exception, use `withSpanThrowing(...)` / `withSpanVoidThrowing(...)` — they take `IThrowingSpanFunction<T, E>` / `IThrowingSpanConsumer<E>`, catch `Throwable` (defensively guarding `recordException`), and re-throw `E` without wrapping.
- **`TelemetryMetrics`** (`ph-telemetry`) — static facade for metrics (counters, up-down counters, histograms, observable gauges). Same SPI pattern as `Telemetry`.
- **`ITelemetrySpan`**, **`ETelemetrySpanKind`**, **`TelemetryAttributes`** — the per-call abstractions. `TelemetryAttributes` is immutable and exposed via a typed visitor (`IVisitor`) so SPI implementations dispatch values without `instanceof` checks.
- **`ITelemetryTracerSPI`** / **`ITelemetryMeterSPI`** — the SPIs that backends implement; ServiceLoader-discovered, one wins.
- **`OtelTelemetryTracerSPI`** / **`OtelTelemetryMeterSPI`** (`ph-telemetry-otel`) — subclassable base classes for the OpenTelemetry binding. Concrete subclasses pass `(scopeName, scopeVersion)` to `super(...)` and are registered via `META-INF/services` in the consuming project.

### Patterns

- **Static facade + ServiceLoader-discovered SPI** — same model as SLF4J. The facade is `final` and stateless apart from the lazy SPI cache; `install(...)` exists for tests.
- **No-op fallback** — when no SPI is registered, every operation through the facade is a cheap no-op, so libraries can emit telemetry unconditionally.
- **Visitor for typed attributes** — `TelemetryAttributes.forEach(IVisitor)` dispatches to `onString` / `onLong` / `onDouble` / `onBoolean` instead of forcing callers to inspect runtime types.
- **Hungarian notation** — `m_` for instance fields, `s_` for static fields, type prefixes (`s`, `n`, `b`, `e`, `a`, ...), `_` prefix for private methods, `I` prefix for interfaces, `E` prefix for enums. Parameters are `final`. Spaces before parentheses in method calls and control flow.

## Testing

- JUnit 4 throughout.
- All tests in `ph-telemetry` use `install (null)` in `@After` to reset SPI state between tests — no global leakage.
- The OpenTelemetry binding has no unit tests in `ph-telemetry-otel`: it's a thin pass-through to the OTel API, exercised in downstream consumers. If you add tests here, prefer `opentelemetry-sdk-testing` over mocking.

## Things to know before changing the API surface

- The SPI is small on purpose. Adding methods to `ITelemetrySpan` / `ITelemetrySpan` / `ITelemetry*` instruments breaks every implementer. Prefer `default` methods or new interfaces.
- The histogram SPI is `record(double)` only — `OtelTelemetryMeterSPI` uses `DoubleHistogram` for everything. Integer-typed histograms (`ofLongs()`) are intentionally not exposed; callers pass `(double) nValue` for count-like values.
- `OtelTelemetryTracerSPI` and `OtelTelemetryMeterSPI` cache the `Tracer` / `Meter` lazily on first use. Once cached, swapping `GlobalOpenTelemetry` at runtime has no effect on already-resolved instances. In production this is fine (the SDK is installed once at startup); in tests, prefer `Telemetry.install(...)` / `TelemetryMetrics.install(...)` over fiddling with `GlobalOpenTelemetry`.
- `ph-telemetry-otel` deliberately only depends on `opentelemetry-api`. Do not pull in `opentelemetry-sdk`, `opentelemetry-sdk-extension-autoconfigure`, exporters, or `okhttp` — those belong in the application module that initialises the SDK.
