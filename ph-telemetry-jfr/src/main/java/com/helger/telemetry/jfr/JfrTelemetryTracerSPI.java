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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.OverrideOnDemand;
import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.ITelemetrySpan;
import com.helger.telemetry.ITelemetryTracerSPI;
import com.helger.telemetry.Telemetry;

import jdk.jfr.Event;

/**
 * Java Flight Recorder implementation of {@link ITelemetryTracerSPI}. Every span becomes one
 * {@code com.helger.telemetry.Span} JFR event whose JFR duration is the real span duration: the
 * event is allocated and {@link Event#begin()}-ed in {@link #startSpan(String, ETelemetrySpanKind)}
 * and {@link Event#end()}-ed plus {@link Event#commit()}-ed on {@code close ()}. That ordering is
 * not optional — {@link Event} offers no way to backdate a start time.
 * <p>
 * Spans nested on the same thread are linked through the <code>parentSpanID</code> field, using a
 * per-thread span stack maintained by this class; JFR itself has no notion of parent and child
 * events. Trace and span IDs are generated in the OpenTelemetry format (32 and 16 hex characters)
 * so a recording can be joined against a distributed trace.
 * <p>
 * If the span event type is disabled in the active recording configuration, the no-op span of
 * {@link Telemetry} is returned and nothing is tracked at all.
 * <p>
 * <b>Running alongside OpenTelemetry.</b> Register a {@code CompositeTelemetryTracerSPI} that lists
 * the OpenTelemetry tracer <em>before</em> this one, and override {@link #getExternalTraceID()} /
 * {@link #getExternalSpanID()} to adopt the IDs the OTel span has just established — the JFR
 * recording and the exported trace then share the same identifiers:
 *
 * <pre>
 * public final class MyAppJfrTracerSPI extends JfrTelemetryTracerSPI
 * {
 *   &#64;Override
 *   protected String getExternalTraceID ()
 *   {
 *     final SpanContext aCtx = Span.current ().getSpanContext ();
 *     return aCtx.isValid () ? aCtx.getTraceId () : null;
 *   }
 *
 *   &#64;Override
 *   protected String getExternalSpanID ()
 *   {
 *     final SpanContext aCtx = Span.current ().getSpanContext ();
 *     return aCtx.isValid () ? aCtx.getSpanId () : null;
 *   }
 * }
 * </pre>
 * <p>
 * <b>Thread affinity.</b> JFR attributes an event to the thread that commits it, so a span opened
 * on one thread and closed on another is recorded against the closing thread. The
 * try-with-resources usage the abstraction is built around keeps this correct; explicit hand-offs
 * between threads do not.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@ThreadSafe
public class JfrTelemetryTracerSPI implements ITelemetryTracerSPI
{
  private final ThreadLocal <Deque <JfrTelemetrySpan>> m_aSpanStack = ThreadLocal.withInitial (ArrayDeque::new);

  public JfrTelemetryTracerSPI ()
  {}

  @NonNull
  private static String _toHex16 (final long nValue)
  {
    final String sHex = Long.toHexString (nValue);
    return "0000000000000000".substring (sHex.length ()) + sHex;
  }

  private static long _nextNonZeroLong ()
  {
    long nRet;
    do
    {
      nRet = ThreadLocalRandom.current ().nextLong ();
    } while (nRet == 0);
    return nRet;
  }

  /**
   * Create a new trace ID. 32 hex characters, matching the OpenTelemetry trace ID format.
   *
   * @return The new trace ID. Never <code>null</code>.
   */
  @NonNull
  private static String _createTraceID ()
  {
    return _toHex16 (_nextNonZeroLong ()) + _toHex16 (ThreadLocalRandom.current ().nextLong ());
  }

  /**
   * Create a new span ID. 16 hex characters, matching the OpenTelemetry span ID format.
   *
   * @return The new span ID. Never <code>null</code>.
   */
  @NonNull
  private static String _createSpanID ()
  {
    return _toHex16 (_nextNonZeroLong ());
  }

  /**
   * Overridable hook to adopt the trace ID of another telemetry backend instead of generating one.
   * Called once per span, before the parent lookup.
   *
   * @return The trace ID to use, or <code>null</code> to inherit it from the enclosing span
   *         respectively to generate a new one. The default implementation returns
   *         <code>null</code>.
   */
  @Nullable
  @OverrideOnDemand
  protected String getExternalTraceID ()
  {
    return null;
  }

  /**
   * Overridable hook to adopt the span ID of another telemetry backend instead of generating one.
   * Called once per span.
   *
   * @return The span ID to use, or <code>null</code> to generate a new one. The default
   *         implementation returns <code>null</code>.
   */
  @Nullable
  @OverrideOnDemand
  protected String getExternalSpanID ()
  {
    return null;
  }

  @NonNull
  public ITelemetrySpan startSpan (@NonNull final String sName, @NonNull final ETelemetrySpanKind eKind)
  {
    final JfrSpanEvent aEvent = new JfrSpanEvent ();
    if (!aEvent.isEnabled ())
    {
      // Span events are switched off - do not even maintain the per-thread stack
      return Telemetry.NoOpTelemetrySpan.INSTANCE;
    }

    final Deque <JfrTelemetrySpan> aStack = m_aSpanStack.get ();
    final JfrTelemetrySpan aParent = aStack.peek ();

    String sTraceID = getExternalTraceID ();
    if (sTraceID == null)
      sTraceID = aParent != null ? aParent.getTraceID () : _createTraceID ();

    String sSpanID = getExternalSpanID ();
    if (sSpanID == null)
      sSpanID = _createSpanID ();

    final JfrTelemetrySpan ret = new JfrTelemetrySpan (this,
                                                       aEvent,
                                                       sName,
                                                       eKind,
                                                       sTraceID,
                                                       sSpanID,
                                                       aParent != null ? aParent.getSpanID () : null);
    aStack.push (ret);
    aEvent.begin ();
    return ret;
  }

  /**
   * Callback from {@link JfrTelemetrySpan#close()} to unwind the per-thread span stack.
   *
   * @param aSpan
   *        The span being closed. Never <code>null</code>.
   */
  void onSpanClosed (@NonNull final JfrTelemetrySpan aSpan)
  {
    final Deque <JfrTelemetrySpan> aStack = m_aSpanStack.get ();
    if (aStack.peek () == aSpan)
      aStack.pop ();
    else
    {
      // Out of order close, or closed on a different thread than it was started on
      aStack.remove (aSpan);
    }
    if (aStack.isEmpty ())
    {
      // Do not retain an empty deque on pooled threads
      m_aSpanStack.remove ();
    }
  }
}
