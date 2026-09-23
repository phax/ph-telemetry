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
import com.helger.telemetry.ITelemetryCounter;
import com.helger.telemetry.TelemetryAttributes;

/**
 * {@link ITelemetryCounter} that sums up in process and is sampled into
 * {@code com.helger.telemetry.Counter} JFR events by the periodic hook of
 * {@link JfrTelemetryMeterSPI}.
 *
 * @author Philip Helger
 */
@ThreadSafe
final class JfrTelemetryCounter implements ITelemetryCounter
{
  private final String m_sName;
  private final String m_sUnit;
  private final JfrValueAccumulator m_aAccumulator;

  JfrTelemetryCounter (@NonNull final String sName, @Nullable final String sUnit)
  {
    m_sName = sName;
    m_sUnit = sUnit;
    m_aAccumulator = new JfrValueAccumulator (sName);
  }

  public void add (final long nValue, @NonNull final TelemetryAttributes aAttributes)
  {
    m_aAccumulator.add (nValue, aAttributes);
  }

  void commitSnapshot ()
  {
    m_aAccumulator.forEachValue ((sAttributes, nValue) -> {
      final JfrCounterEvent aEvent = new JfrCounterEvent ();
      aEvent.m_sName = m_sName;
      aEvent.m_sUnit = m_sUnit;
      aEvent.m_sAttributes = sAttributes;
      aEvent.m_nValue = nValue;
      aEvent.commit ();
    });
  }
}
