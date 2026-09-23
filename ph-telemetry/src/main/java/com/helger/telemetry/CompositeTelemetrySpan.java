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
package com.helger.telemetry;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * {@link ITelemetrySpan} that fans every call out to a fixed set of delegate spans. Created by
 * {@link CompositeTelemetryTracerSPI#startSpan(String, ETelemetrySpanKind)}.
 *
 * @author Philip Helger
 */
final class CompositeTelemetrySpan implements ITelemetrySpan
{
  private final ITelemetrySpan [] m_aSpans;
  private boolean m_bClosed;

  CompositeTelemetrySpan (@NonNull final ITelemetrySpan [] aSpans)
  {
    m_aSpans = aSpans;
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, @Nullable final String sValue)
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.setAttribute (sKey, sValue);
    return this;
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, final boolean bValue)
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.setAttribute (sKey, bValue);
    return this;
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, final long nValue)
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.setAttribute (sKey, nValue);
    return this;
  }

  @NonNull
  public ITelemetrySpan setAttribute (@NonNull final String sKey, final double dValue)
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.setAttribute (sKey, dValue);
    return this;
  }

  @NonNull
  public ITelemetrySpan recordException (@NonNull final Throwable aException)
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.recordException (aException);
    return this;
  }

  @NonNull
  public ITelemetrySpan addEvent (@NonNull final String sName, @NonNull final TelemetryAttributes aAttributes)
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.addEvent (sName, aAttributes);
    return this;
  }

  @NonNull
  public ITelemetrySpan setStatusOk ()
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.setStatusOk ();
    return this;
  }

  @NonNull
  public ITelemetrySpan setStatusError (@Nullable final String sMessage)
  {
    for (final ITelemetrySpan aSpan : m_aSpans)
      aSpan.setStatusError (sMessage);
    return this;
  }

  public void close ()
  {
    if (!m_bClosed)
    {
      m_bClosed = true;
      // Reverse order, so that per-thread nesting maintained by a delegate unwinds symmetrically
      for (int i = m_aSpans.length - 1; i >= 0; --i)
        m_aSpans[i].close ();
    }
  }
}
