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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.telemetry.ITelemetryGauge;

/**
 * {@link ITelemetryGauge} whose supplier is polled by the periodic hook of
 * {@link JfrTelemetryMeterSPI} and emitted as a {@code com.helger.telemetry.Gauge} JFR event.
 * {@link #close()} unregisters the gauge from the meter, after which the supplier is no longer
 * invoked.
 *
 * @author Philip Helger
 */
@ThreadSafe
final class JfrTelemetryGauge implements ITelemetryGauge
{
  private final JfrTelemetryMeterSPI m_aMeter;
  private final String m_sName;
  private final String m_sUnit;
  private final LongSupplier m_aSupplier;
  private final AtomicBoolean m_aClosed = new AtomicBoolean (false);

  JfrTelemetryGauge (@NonNull final JfrTelemetryMeterSPI aMeter,
                     @NonNull final String sName,
                     @Nullable final String sUnit,
                     @NonNull final LongSupplier aSupplier)
  {
    m_aMeter = aMeter;
    m_sName = sName;
    m_sUnit = sUnit;
    m_aSupplier = aSupplier;
  }

  void commitSnapshot ()
  {
    if (!m_aClosed.get ())
    {
      final JfrGaugeEvent aEvent = new JfrGaugeEvent ();
      aEvent.m_sName = m_sName;
      aEvent.m_sUnit = m_sUnit;
      aEvent.m_nValue = m_aSupplier.getAsLong ();
      aEvent.commit ();
    }
  }

  public void close ()
  {
    if (m_aClosed.compareAndSet (false, true))
      m_aMeter.removeGauge (this);
  }
}
