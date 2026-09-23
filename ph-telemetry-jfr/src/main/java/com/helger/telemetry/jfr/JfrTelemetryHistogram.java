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

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.telemetry.ITelemetryHistogram;
import com.helger.telemetry.TelemetryAttributes;

/**
 * {@link ITelemetryHistogram} that commits one {@code com.helger.telemetry.Histogram} JFR event per
 * recorded value. No aggregation happens in process — in JFR the event is the sample and JDK
 * Mission Control derives the distribution from the recorded population.
 *
 * @author Philip Helger
 */
@ThreadSafe
final class JfrTelemetryHistogram implements ITelemetryHistogram
{
  private final String m_sName;
  private final String m_sUnit;

  JfrTelemetryHistogram (@NonNull final String sName, @Nullable final String sUnit)
  {
    m_sName = sName;
    m_sUnit = sUnit;
  }

  public void record (final double dValue, @NonNull final TelemetryAttributes aAttributes)
  {
    final JfrHistogramEvent aEvent = new JfrHistogramEvent ();
    if (aEvent.isEnabled ())
    {
      aEvent.m_sName = m_sName;
      aEvent.m_sUnit = m_sUnit;
      aEvent.m_sAttributes = JfrAttributesConverter.toFlatString (aAttributes);
      aEvent.m_dValue = dValue;
      aEvent.commit ();
    }
  }
}
