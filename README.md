# ph-telemetry

<!-- ph-badge-start -->
[![Sonatype Central](https://maven-badges.sml.io/sonatype-central/com.helger.telemetry/ph-telemetry-parent-pom/badge.svg)](https://maven-badges.sml.io/sonatype-central/com.helger.telemetry/ph-telemetry-parent-pom/)
[![javadoc](https://javadoc.io/badge2/com.helger.telemetry/ph-telemetry/javadoc.svg)](https://javadoc.io/doc/com.helger.telemetry/ph-telemetry)

> If this project saved you some time or made your day a little easier, a star would mean a lot — it helps others find it too.
<!-- ph-badge-end -->

Java 17+ vendor-neutral telemetry abstraction (tracing + metrics) with pluggable OpenTelemetry and Java Flight Recorder bindings. Lets libraries emit spans and instruments without pulling the OpenTelemetry API into their dependency graph, and lets applications swap in a real backend (OpenTelemetry and JFR out of the box; any `ServiceLoader`-registered SPI implementation — Jaeger, Zipkin, a custom recorder, etc. — works the same way), several backends at once, or a no-op fallback.

Licensed under the Apache 2.0 license.

# Modules

* **`ph-telemetry`** — the abstraction itself. Static facades `Telemetry` (tracing) and `TelemetryMetrics` (counters / up-down counters / histograms / observable gauges), backed by SPIs (`ITelemetryTracerSPI`, `ITelemetryMeterSPI`). If no SPI is registered, both facades transparently degrade to cheap no-ops, so libraries can emit telemetry unconditionally without forcing the cost or the dependency on downstream consumers.
* **`ph-telemetry-otel`** — the OpenTelemetry binding. Provides `OtelTelemetryTracerSPI` and `OtelTelemetryMeterSPI` as subclassable base classes that resolve the SDK via `GlobalOpenTelemetry`. Project applications subclass them with a no-arg constructor supplying an instrumentation scope name + version, register the subclass via `META-INF/services`, and let `ServiceLoader` wire it all up at runtime.
* **`ph-telemetry-jfr`** — the Java Flight Recorder binding. Provides `JfrTelemetryTracerSPI` and `JfrTelemetryMeterSPI`, which turn spans and instruments into JFR events in the `ph-telemetry` event category, readable in JDK Mission Control or via `jdk.jfr.consumer`. It depends on nothing but the JDK's own `jdk.jfr` module — no third-party dependency at all. Unlike OpenTelemetry this is a *local* sink, so it complements an exporter rather than replacing it.

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

```xml
<dependency>
  <groupId>com.helger.telemetry</groupId>
  <artifactId>ph-telemetry-jfr</artifactId>
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

## Wiring Java Flight Recorder

`JfrTelemetryTracerSPI` and `JfrTelemetryMeterSPI` have no instrumentation scope to configure, so they are registered directly — no subclass needed:

```
META-INF/services/com.helger.telemetry.ITelemetryTracerSPI
  -> com.helger.telemetry.jfr.JfrTelemetryTracerSPI

META-INF/services/com.helger.telemetry.ITelemetryMeterSPI
  -> com.helger.telemetry.jfr.JfrTelemetryMeterSPI
```

Nothing else has to be started: the binding emits JFR events, and whether they are recorded is decided by the JVM's recording configuration (`-XX:StartFlightRecording`, `jcmd JFR.start`, JDK Mission Control or a programmatic `jdk.jfr.Recording`). When no recording has the event type enabled, `startSpan (...)` returns the no-op span and the instruments do nothing.

These event types are emitted, all of them in the JFR categories `ph-telemetry / Tracing` and `ph-telemetry / Metrics`. `CJfrTelemetry` exposes every name as a constant:

| Event type | Kind | Content |
|---|---|---|
| `com.helger.telemetry.Span` | duration | one event per span: name, kind, `traceID`, `spanID`, `parentSpanID`, status, attributes |
| `com.helger.telemetry.SpanMarker` | instant | `addEvent (...)` inside a span, correlated via `spanID` |
| `com.helger.telemetry.SpanException` | instant | `recordException (...)`: exception class + message, with a JFR stack trace |
| `com.helger.telemetry.Counter` | periodic | running total per counter and attribute set |
| `com.helger.telemetry.UpDownCounter` | periodic | current value per up-down counter and attribute set |
| `com.helger.telemetry.Gauge` | periodic | one sample per gauge |
| `com.helger.telemetry.Histogram` | instant | one event per recorded value — the most volume-intensive type |

The three periodic types default to a period of one second. Everything — period, threshold, stack traces, enablement — is configurable per recording:

```java
try (final Recording aRecording = new Recording ())
{
  aRecording.enable (CJfrTelemetry.EVENT_SPAN).withThreshold (Duration.ofMillis (10));
  aRecording.enable (CJfrTelemetry.EVENT_GAUGE).withPeriod (Duration.ofSeconds (5));
  aRecording.disable (CJfrTelemetry.EVENT_HISTOGRAM);
  aRecording.start ();
  ...
}
```

Three properties of JFR are worth knowing before relying on this binding:

* **Attributes are flattened.** A JFR event has a fixed schema and no map-valued field type, so attributes are rendered into a single `attributes` string of escaped `key=value` pairs separated by `;`. The declared value type is lost in the process — run an OpenTelemetry backend alongside if typed dimensions matter.
* **Spans are thread-affine.** JFR attributes an event to the thread that commits it, and parent/child nesting is tracked per thread. The try-with-resources usage the abstraction is built around is correct; handing a span to another thread is not.
* **Event-type granularity.** Enablement and thresholds apply to a whole event type, not to an individual span name. A `withThreshold (...)` on `com.helger.telemetry.Span` silently drops *every* span shorter than that.

## Running several backends side by side

`Telemetry` and `TelemetryMetrics` resolve only the *first* `ServiceLoader`-registered SPI. To feed more than one backend — an OpenTelemetry exporter for the distributed trace and JFR for the local recording, say — register a single `CompositeTelemetryTracerSPI` / `CompositeTelemetryMeterSPI` that fans out to all of them:

```java
public final class MyAppTracerSPI extends CompositeTelemetryTracerSPI
{
  public MyAppTracerSPI ()
  {
    super (new MyAppOtelTracerSPI (), new MyAppJfrTracerSPI ());
  }
}

public final class MyAppMeterSPI extends CompositeTelemetryMeterSPI
{
  public MyAppMeterSPI ()
  {
    super (new MyAppOtelMeterSPI (), new JfrTelemetryMeterSPI ());
  }
}
```

Delegates are invoked in the order given, and in reverse order on `close ()`. That order matters: a delegate that wants to observe what another one established must come after it. This is what makes the two recordings joinable — list the OpenTelemetry tracer first and let the JFR binding adopt the IDs the OTel span has just created, so the `.jfr` file and the exported trace carry the same identifiers:

```java
public final class MyAppJfrTracerSPI extends JfrTelemetryTracerSPI
{
  @Override
  protected String getExternalTraceID ()
  {
    final SpanContext aCtx = Span.current ().getSpanContext ();
    return aCtx.isValid () ? aCtx.getTraceId () : null;
  }

  @Override
  protected String getExternalSpanID ()
  {
    final SpanContext aCtx = Span.current ().getSpanContext ();
    return aCtx.isValid () ? aCtx.getSpanId () : null;
  }
}
```

The composites perform no exception handling: a delegate that throws aborts the fan-out and propagates to the caller, exactly as a single directly registered SPI would.

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

v1.1.1 - 2026-09-24
* **`ph-telemetry-otel` no longer claims the global `OpenTelemetry` slot, and no longer keeps a no-op tracer or meter forever.**
  `OtelTelemetryTracerSPI` and `OtelTelemetryMeterSPI` resolved their tracer / meter via `GlobalOpenTelemetry.get ()`, which is not a read: on an unset global it *registers* the official no-op instance itself, and every later `GlobalOpenTelemetry.set (...)` then fails with `IllegalStateException: GlobalOpenTelemetry.set has already been called`. So a single span taken before the application had bootstrapped its SDK broke that bootstrap - in one reported case a Flyway migration wrapped in a span by `ph-db-flyway` took down the whole application startup ([phoss-ap#102](https://github.com/phax/phoss-ap/issues/102)).
  Both adapters now use **`GlobalOpenTelemetry.getOrNoop ()`**, which is what the OpenTelemetry API documents for instrumentation and has no such side effect, so telemetry taken too early is a harmless no-op instead of a time bomb.
  In addition, the resolved tracer / meter is only **cached once an instance is really registered** (`GlobalOpenTelemetry.isSet ()`). Before that the no-op is handed out but not remembered, so an SDK installed a moment later takes effect - previously the first early span pinned the no-op for the lifetime of the JVM and all telemetry stayed silently dead. The hot path after the SDK is registered is unchanged: a single volatile read.
  Note that instruments (counters, histograms, gauges) created before the SDK was registered keep pointing at the meter they were built from - create them after the bootstrap.
  **Behaviour change to be aware of:** `GlobalOpenTelemetry.get ()` is also the only thing that honours `-Dotel.java.global-autoconfigure.enabled=true`, the OpenTelemetry option that builds and registers an SDK on the first access. `getOrNoop ()` deliberately does not, so an application that relied on the first span of `ph-telemetry-otel` to trigger that autoconfiguration must now install the SDK itself - which is the supported way anyway. As a side effect the one-time JUL INFO message "AutoConfiguredOpenTelemetrySdk found on classpath but automatic configuration is disabled" is gone, because it is emitted from that same code path.

v1.1.0 - 2026-09-23
* New module `ph-telemetry-jfr` with the Java Flight Recorder binding, in the new package `com.helger.telemetry.jfr`.
  `JfrTelemetryTracerSPI` emits one `com.helger.telemetry.Span` duration event per span plus `SpanMarker` and `SpanException` events, links spans nested on the same thread through a `parentSpanID` field, and generates trace and span IDs in the OpenTelemetry format — overridable via `getExternalTraceID ()` / `getExternalSpanID ()` so a recording can adopt the IDs of an OpenTelemetry span and be joined against the exported trace.
  `JfrTelemetryMeterSPI` accumulates counters and up-down counters in process and samples them plus the observable gauges via periodic JFR events, while every histogram value becomes its own event.
  `CJfrTelemetry` exposes all emitted event type names as constants, for use in `.jfc` settings or `Recording.enable (String)`.
  The module depends on nothing but `ph-telemetry` and the JDK's own `jdk.jfr` module.
* Added `CompositeTelemetryTracerSPI` and `CompositeTelemetryMeterSPI` to `ph-telemetry`, fanning every span and every instrument out to a fixed list of delegate backends.
  `Telemetry` and `TelemetryMetrics` still resolve only the first `ServiceLoader`-registered SPI, so registering a single composite is the supported way to run more than one backend at the same time — e.g. an OpenTelemetry exporter next to the new JFR binding.
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
