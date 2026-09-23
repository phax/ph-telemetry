/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.telemetry.jfr;

import com.helger.annotation.style.PresentForCodeCoverage;

/**
 * Constants of the JFR binding — most importantly the names of the event types it emits. Use them
 * to enable, disable or tune the events from a {@code .jfc} settings file, from {@code jcmd}, or
 * programmatically:
 *
 * <pre>
 * aRecording.enable (CJfrTelemetry.EVENT_SPAN).withStackTrace ();
 * aRecording.enable (CJfrTelemetry.EVENT_GAUGE).withPeriod (Duration.ofSeconds (5));
 * aRecording.disable (CJfrTelemetry.EVENT_HISTOGRAM);
 * </pre>
 *
 * All event types live in the JFR categories <code>ph-telemetry / Tracing</code> and
 * <code>ph-telemetry / Metrics</code>, so they can also be addressed as a group in JDK Mission
 * Control.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
public final class CJfrTelemetry
{
  /** JFR top level category of all events emitted by this binding */
  public static final String CATEGORY = "ph-telemetry";
  /** JFR sub category of the tracing events */
  public static final String CATEGORY_TRACING = "Tracing";
  /** JFR sub category of the metric events */
  public static final String CATEGORY_METRICS = "Metrics";

  /** Event type name of a single trace span - has a duration */
  public static final String EVENT_SPAN = "com.helger.telemetry.Span";
  /** Event type name of a named marker inside a span - instantaneous */
  public static final String EVENT_SPAN_MARKER = "com.helger.telemetry.SpanMarker";
  /** Event type name of an exception recorded on a span - instantaneous */
  public static final String EVENT_SPAN_EXCEPTION = "com.helger.telemetry.SpanException";

  /** Event type name of a monotonic counter snapshot - periodic */
  public static final String EVENT_COUNTER = "com.helger.telemetry.Counter";
  /** Event type name of an up-down counter snapshot - periodic */
  public static final String EVENT_UP_DOWN_COUNTER = "com.helger.telemetry.UpDownCounter";
  /** Event type name of an observable gauge sample - periodic */
  public static final String EVENT_GAUGE = "com.helger.telemetry.Gauge";
  /** Event type name of a single histogram sample - instantaneous */
  public static final String EVENT_HISTOGRAM = "com.helger.telemetry.Histogram";

  /** Default period of all periodic events, overridable via the recording settings */
  public static final String DEFAULT_PERIOD = "1 s";

  /** Span status value if neither OK nor ERROR was set */
  public static final String STATUS_UNSET = "UNSET";
  /** Span status value set by {@code setStatusOk ()} */
  public static final String STATUS_OK = "OK";
  /** Span status value set by {@code setStatusError (String)} */
  public static final String STATUS_ERROR = "ERROR";

  @PresentForCodeCoverage
  private static final CJfrTelemetry INSTANCE = new CJfrTelemetry ();

  private CJfrTelemetry ()
  {}
}
