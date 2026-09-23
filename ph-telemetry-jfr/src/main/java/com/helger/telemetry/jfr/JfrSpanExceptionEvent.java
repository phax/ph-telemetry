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
 * JFR event for an exception recorded on a span via
 * {@code ITelemetrySpan.recordException (Throwable)}. Instantaneous, and unlike the other events of
 * this binding it keeps the JFR stack trace: it is rare enough to afford one, and the capture site
 * is the point at which the failure was observed.
 * <p>
 * Only the exception class name and message are stored — the throwable's own stack trace is not
 * serialized into the recording, because stringifying it on every recorded exception is too
 * expensive for an always-on profiler.
 *
 * @author Philip Helger
 */
@Name (CJfrTelemetry.EVENT_SPAN_EXCEPTION)
@Label ("Telemetry Span Exception")
@Description ("An exception recorded on a ph-telemetry trace span")
@Category ({ CJfrTelemetry.CATEGORY, CJfrTelemetry.CATEGORY_TRACING })
@StackTrace (true)
final class JfrSpanExceptionEvent extends Event
{
  @Label ("Trace ID")
  @Name ("traceID")
  String m_sTraceID;

  @Label ("Span ID")
  @Name ("spanID")
  String m_sSpanID;

  @Label ("Exception Class")
  @Name ("exceptionClass")
  String m_sExceptionClass;

  @Label ("Message")
  @Name ("message")
  String m_sMessage;
}
