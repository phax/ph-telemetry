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
 * JFR event for a single trace span. Allocated and {@link Event#begin()}-ed when the span starts
 * and {@link Event#end()}-ed plus {@link Event#commit()}-ed when it is closed, so the JFR duration
 * of this event is the real duration of the span.
 * <p>
 * Stack traces are disabled by default: JFR captures the stack at <em>commit</em> time, which for a
 * span is the closing site and rarely the interesting frame. Re-enable them per recording via
 * {@code enable (CJfrTelemetry.EVENT_SPAN).withStackTrace ()} if needed.
 *
 * @author Philip Helger
 */
@Name (CJfrTelemetry.EVENT_SPAN)
@Label ("Telemetry Span")
@Description ("A ph-telemetry trace span")
@Category ({ CJfrTelemetry.CATEGORY, CJfrTelemetry.CATEGORY_TRACING })
@StackTrace (false)
final class JfrSpanEvent extends Event
{
  @Label ("Name")
  @Name ("name")
  String m_sName;

  @Label ("Kind")
  @Name ("kind")
  String m_sKind;

  @Label ("Trace ID")
  @Name ("traceID")
  String m_sTraceID;

  @Label ("Span ID")
  @Name ("spanID")
  String m_sSpanID;

  @Label ("Parent Span ID")
  @Name ("parentSpanID")
  String m_sParentSpanID;

  @Label ("Status")
  @Name ("status")
  String m_sStatus;

  @Label ("Status Message")
  @Name ("statusMessage")
  String m_sStatusMessage;

  @Label ("Attributes")
  @Name ("attributes")
  String m_sAttributes;
}
