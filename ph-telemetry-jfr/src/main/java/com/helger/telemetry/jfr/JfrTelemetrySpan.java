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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.ITelemetrySpan;
import com.helger.telemetry.TelemetryAttributes;

/**
 * {@link ITelemetrySpan} backed by a {@code JfrSpanEvent} that has already been
 * {@code begin ()}-ed. Attributes are accumulated into the flat String representation described in
 * {@code JfrAttributesConverter} and only written to the event when it is actually committed.
 * <p>
 * Not thread-safe: a JFR event is bound to the thread that commits it, so a span is meant to be
 * used - and closed - on the thread that started it.
 *
 * @author Philip Helger
 */
@NotThreadSafe
final class JfrTelemetrySpan implements ITelemetrySpan
{
  private final JfrTelemetryTracerSPI m_aTracer;
  private final JfrSpanEvent m_aEvent;
  private final String m_sName;
  private final ETelemetrySpanKind m_eKind;
  private final String m_sTraceID;
  private final String m_sSpanID;
  private final String m_sParentSpanID;
  private final StringBuilder m_aAttributes = new StringBuilder ();
  private String m_sStatus = CJfrTelemetry.STATUS_UNSET;
  private String m_sStatusMessage;
  private boolean m_bClosed;

  JfrTelemetrySpan (@NonNull final JfrTelemetryTracerSPI aTracer,
                    @NonNull final JfrSpanEvent aEvent,
                    @NonNull final String sName,
                    @NonNull final ETelemetrySpanKind eKind,
                    @NonNull final String sTraceID,
                    @NonNull final String sSpanID,
                    @Nullable final String sParentSpanID)
  {
    m_aTracer = aTracer;
    m_aEvent = aEvent;
    m_sName = sName;
    m_eKind = eKind;
    m_sTraceID = sTraceID;
    m_sSpanID = sSpanID;
    m_sParentSpanID = sParentSpanID;
  }

  @NonNull
  String getTraceID ()
  {
    return m_sTraceID;
  }

  @NonNull
  String getSpanID ()
  {
    return m_sSpanID;
  }

  @NonNull
  private ITelemetrySpan _addAttribute (@NonNull final String sKey, @NonNull final String sValue)
  {
    if (!m_bClosed)
      JfrAttributesConverter.appendEntry (m_aAttributes, sKey, sValue);
    return this;
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, @Nullable final String sValue)
  {
    if (sValue == null)
      return this;
    return _addAttribute (sKey, sValue);
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, final boolean bValue)
  {
    return _addAttribute (sKey, Boolean.toString (bValue));
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, final long nValue)
  {
    return _addAttribute (sKey, Long.toString (nValue));
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, final double dValue)
  {
    return _addAttribute (sKey, Double.toString (dValue));
  }

  @NonNull
  public ITelemetrySpan recordException (@NonNull final Throwable aException)
  {
    if (!m_bClosed)
    {
      final JfrSpanExceptionEvent aEvent = new JfrSpanExceptionEvent ();
      if (aEvent.isEnabled ())
      {
        aEvent.m_sTraceID = m_sTraceID;
        aEvent.m_sSpanID = m_sSpanID;
        aEvent.m_sExceptionClass = aException.getClass ().getName ();
        aEvent.m_sMessage = aException.getMessage ();
        aEvent.commit ();
      }
    }
    return this;
  }

  @NonNull
  public ITelemetrySpan addEvent (@NonNull final String sName, @NonNull final TelemetryAttributes aAttributes)
  {
    if (!m_bClosed)
    {
      final JfrSpanMarkerEvent aEvent = new JfrSpanMarkerEvent ();
      if (aEvent.isEnabled ())
      {
        aEvent.m_sName = sName;
        aEvent.m_sTraceID = m_sTraceID;
        aEvent.m_sSpanID = m_sSpanID;
        aEvent.m_sAttributes = JfrAttributesConverter.toFlatString (aAttributes);
        aEvent.commit ();
      }
    }
    return this;
  }

  @NonNull
  public ITelemetrySpan setStatusOk ()
  {
    if (!m_bClosed)
    {
      m_sStatus = CJfrTelemetry.STATUS_OK;
      m_sStatusMessage = null;
    }
    return this;
  }

  @NonNull
  public ITelemetrySpan setStatusError (@Nullable final String sMessage)
  {
    if (!m_bClosed)
    {
      m_sStatus = CJfrTelemetry.STATUS_ERROR;
      m_sStatusMessage = sMessage;
    }
    return this;
  }

  public void close ()
  {
    if (!m_bClosed)
    {
      m_bClosed = true;
      m_aTracer.onSpanClosed (this);
      m_aEvent.end ();
      // Only now is the duration known, so only now can the threshold be evaluated
      if (m_aEvent.shouldCommit ())
      {
        m_aEvent.m_sName = m_sName;
        m_aEvent.m_sKind = m_eKind.name ();
        m_aEvent.m_sTraceID = m_sTraceID;
        m_aEvent.m_sSpanID = m_sSpanID;
        m_aEvent.m_sParentSpanID = m_sParentSpanID;
        m_aEvent.m_sStatus = m_sStatus;
        m_aEvent.m_sStatusMessage = m_sStatusMessage;
        m_aEvent.m_sAttributes = m_aAttributes.toString ();
        m_aEvent.commit ();
      }
    }
  }
}
