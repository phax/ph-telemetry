# ph-telemetry

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger.telemetry/ph-telemetry-parent-pom/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger.telemetry/ph-telemetry-parent-pom/)
[![javadoc](https://javadoc.io/badge2/com.helger.telemetry/ph-telemetry/javadoc.svg)](https://javadoc.io/doc/com.helger.telemetry/ph-telemetry)

> If this project saved you some time or made your day a little easier, a star would mean a lot — it helps others find it too.
<!-- ph-badge-end -->

Java 17+ vendor-neutral telemetry abstraction (tracing + metrics) with a pluggable OpenTelemetry binding. Lets libraries emit spans and instruments without pulling the OpenTelemetry API into their dependency graph, and lets applications swap in a real backend (OpenTelemetry out of the box; any `ServiceLoader`-registered SPI implementation — Jaeger, Zipkin, a custom recorder, etc. — works the same way) or a no-op fallback.

Licensed under the Apache 2.0 license.

# Modules

* **`ph-telemetry`** — the abstraction itself. Static facades `Telemetry` (tracing) and `TelemetryMetrics` (counters / up-down counters / histograms / observable gauges), backed by SPIs (`ITelemetryTracerSPI`, `ITelemetryMeterSPI`). If no SPI is registered, both facades transparently degrade to cheap no-ops, so libraries can emit telemetry unconditionally without forcing the cost or the dependency on downstream consumers.
* **`ph-telemetry-otel`** — the OpenTelemetry binding. Provides `OtelTelemetryTracerSPI` and `OtelTelemetryMeterSPI` as subclassable base classes that resolve the SDK via `GlobalOpenTelemetry`. Project applications subclass them with a no-arg constructor supplying an instrumentation scope name + version, register the subclass via `META-INF/services`, and let `ServiceLoader` wire it all up at runtime.

# Maven usage

Add the following to your `pom.xml`, where `x.y.z` is the latest released version:

```xml
<dependency>
  <groupId>com.helger.telemetry</groupId>
  <artifactId>ph-telemetry</artifactId>
  <version>x.y.z</version>
</dependency>
```

```xml
<dependency>
  <groupId>com.helger.telemetry</groupId>
  <artifactId>ph-telemetry-otel</artifactId>
  <version>x.y.z</version>
</dependency>
```

Or import the BOM and skip per-module versions:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.helger.telemetry</groupId>
      <artifactId>ph-telemetry-parent-pom</artifactId>
      <version>x.y.z</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Note: prior to v1.0.0 the abstraction shipped from `ph-commons` as `com.helger.commons:ph-telemetry`. That module is now `@Deprecated(forRemoval = true)`; switch the dependency over.

# Usage

## Emitting a span

```java
import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.Telemetry;

Telemetry.withSpanVoid ("outbound.send", ETelemetrySpanKind.PRODUCER, aSpan -> {
  aSpan.setAttribute ("transaction.id", sTxID);
  ... business work ...
  aSpan.setStatusOk ();
});
```

Exceptions thrown inside the body are automatically recorded on the span and the status is set to `ERROR`. If no tracer SPI is registered, the body still runs and `aSpan` is a no-op.

`ETelemetrySpanKind` mirrors OpenTelemetry's `SpanKind`: `INTERNAL`, `CLIENT`, `SERVER`, `PRODUCER`, `CONSUMER`. Use `Telemetry.withSpan (name, kind, body)` when the body needs to return a value; `withSpanVoid (...)` for the void-returning case. Both start the span, record exceptions, set OK/ERROR status, and close the span in a `finally` block. `Telemetry.startSpan (...)` is also available for callers that want to manage the lifecycle manually — `ITelemetrySpan` exposes typed attribute setters (`setAttribute (String, String|long|double|boolean)`), `recordException (Throwable)`, and `setStatusOk ()` / `setStatusError (String)`.

### Bodies that throw checked exceptions

`withSpan (...)` and `withSpanVoid (...)` take a `Function` / `Consumer` and therefore cannot accept a body that declares a checked exception. Use the `*Throwing` variants when the body needs to throw — they take `IThrowingSpanFunction <T, E>` / `IThrowingSpanConsumer <E>`, propagate `E` from the call, and still record the exception on the span before re-throwing:

```java
// returns a value, may throw IOException
final byte[] aPayload = Telemetry.<byte[], IOException> withSpanThrowing (
    "payload.read", ETelemetrySpanKind.INTERNAL, aSpan -> {
      final byte[] aBytes = readRequestBody ();
      aSpan.setAttribute ("payload.size_bytes", aBytes.length);
      return aBytes;
    });

// void, may throw IOException
Telemetry.<IOException> withSpanVoidThrowing (
    "outbound.send", ETelemetrySpanKind.PRODUCER, aSpan -> {
      aSpan.setAttribute ("transaction.id", sTxID);
      sendOverHttp (...);   // throws IOException
    });
```

The throwing variants catch `Throwable` (not just `RuntimeException`), so they also handle `Error` correctly: the exception is recorded on the span and the original is always re-thrown — a defective backend that itself throws from `recordException` cannot mask the user's exception.

## Recording metrics

```java
import com.helger.telemetry.ITelemetryCounter;
import com.helger.telemetry.TelemetryAttributes;
import com.helger.telemetry.TelemetryMetrics;

public final class MyMetrics
{
  public static final ITelemetryCounter REQUESTS_RECEIVED = TelemetryMetrics.counter (
      "myapp.requests.received",
      "Inbound requests accepted by the service",
      "{request}");

  private MyMetrics () {}
}

// at the call site:
MyMetrics.REQUESTS_RECEIVED.add (1,
    TelemetryAttributes.builder ().put ("route", sRoute).build ());
```

## Wiring OpenTelemetry

In your application module, subclass each binding with a no-arg constructor that supplies your instrumentation scope:

```java
public final class MyAppTracerSPI extends OtelTelemetryTracerSPI
{
  public MyAppTracerSPI ()
  {
    super ("com.example.myapp", MyAppVersion.BUILD_VERSION);
  }
}

public final class MyAppMeterSPI extends OtelTelemetryMeterSPI
{
  public MyAppMeterSPI ()
  {
    super ("com.example.myapp", MyAppVersion.BUILD_VERSION);
  }
}
```

Register them via two `META-INF/services` files:

```
META-INF/services/com.helger.telemetry.ITelemetryTracerSPI
  -> com.example.myapp.MyAppTracerSPI

META-INF/services/com.helger.telemetry.ITelemetryMeterSPI
  -> com.example.myapp.MyAppMeterSPI
```

Initialise the OpenTelemetry SDK once at application startup (e.g. via `AutoConfiguredOpenTelemetrySdk.builder().setResultAsGlobal().build()`). The SPI bindings resolve the SDK from `GlobalOpenTelemetry` on first use; until the SDK is installed, the OTel no-op returned by `GlobalOpenTelemetry.get()` keeps the whole pipeline cheap.

## Tests

Tests can install a custom recording SPI without needing an SDK:

```java
@After public void tearDown () { Telemetry.install (null); }

@Test public void example ()
{
  Telemetry.install ((sName, eKind) -> myRecordingSpan);
  ... exercise code that calls Telemetry.startSpan ...
}
```

`TelemetryMetrics.install (...)` works the same way for the metrics side.

# News and noteworthy

v1.0.3 - work in progress
* Added `CapturingTelemetry.getMeasurementCount ()` and `getMeasurementCount (String)` to count the captured recordings — overall or per instrument — mirroring the existing `getSpanCount (...)` methods.

v1.0.2 - 2026-09-05
* New package `com.helger.telemetry.mock` with `CapturingTelemetry` — an in-memory `ITelemetryTracerSPI` + `ITelemetryMeterSPI` implementation for unit tests.
  It captures span names, kinds, attributes, events, recorded exceptions and status, plus every single counter/up-down-counter/histogram recording including its attributes, and it retains gauge suppliers.
  Install it with `install ()` and restore the no-op defaults with `CapturingTelemetry.uninstall ()`; `reset ()` clears the captured data in place so instruments cached in a static initializer stay wired.
  This replaces the per-project copies of the same test double.
* Added an optional dependency to `ph-collection`, needed only by the new `com.helger.telemetry.mock` package.

v1.0.1 - 2026-06-16
* New `Telemetry.withSpanThrowing (...)` and `Telemetry.withSpanVoidThrowing (...)` variants that accept a body declaring a checked exception (`IThrowingSpanFunction <T, E>` / `IThrowingSpanConsumer <E>`).
  The throwable is recorded on the span and re-thrown without wrapping — callers no longer need to smuggle a checked exception through a `RuntimeException`.
  Both variants catch `Throwable` and defensively guard the `recordException` call so a defective backend cannot mask the user's exception.

v1.0.0 - 2026-06-12
* Initial release as a standalone repository.
  The abstraction (`Telemetry`, `TelemetryMetrics`, `ITelemetryTracerSPI`, `ITelemetryMeterSPI`, `TelemetryAttributes`, instrument interfaces, no-op fallbacks) is unchanged from its previous home in `ph-commons:ph-telemetry` v12.3.0 — only the Maven coordinates moved from `com.helger.commons:ph-telemetry` to `com.helger.telemetry:ph-telemetry`.
* New module `ph-telemetry-otel` extracted from per-project OpenTelemetry bindings.
  Provides `OtelTelemetryTracerSPI` and `OtelTelemetryMeterSPI` as subclassable base classes that wrap the OpenTelemetry API; project subclasses supply only the instrumentation scope name and version.
* `ph-telemetry-otel` depends on `opentelemetry-api` only — applications that also need the SDK (autoconfigure, OTLP exporter, etc.) pull those dependencies themselves at the deployment boundary.

---

My personal [Coding Styleguide](https://github.com/phax/meta/blob/master/CodingStyleguide.md) |
It is appreciated if you star the GitHub project if you like it.
