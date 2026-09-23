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

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * JFR event for a named marker recorded inside a span via
 * {@code ITelemetrySpan.addEvent (String, TelemetryAttributes)}. Instantaneous — it is committed
 * right away and carries no duration. Correlate it with its {@link JfrSpanEvent} via the
 * <code>spanID</code> field.
 *
 * @author Philip Helger
 */
@Name (CJfrTelemetry.EVENT_SPAN_MARKER)
@Label ("Telemetry Span Marker")
@Description ("A named marker recorded inside a ph-telemetry trace span")
@Category ({ CJfrTelemetry.CATEGORY, CJfrTelemetry.CATEGORY_TRACING })
@StackTrace (false)
final class JfrSpanMarkerEvent extends Event
{
  @Label ("Name")
  @Name ("name")
  String m_sName;

  @Label ("Trace ID")
  @Name ("traceID")
  String m_sTraceID;

  @Label ("Span ID")
  @Name ("spanID")
  String m_sSpanID;

  @Label ("Attributes")
  @Name ("attributes")
  String m_sAttributes;
}
