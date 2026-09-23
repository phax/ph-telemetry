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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.telemetry.ITelemetryCounter;
import com.helger.telemetry.ITelemetryGauge;
import com.helger.telemetry.ITelemetryHistogram;
import com.helger.telemetry.ITelemetryMeterSPI;
import com.helger.telemetry.ITelemetryUpDownCounter;

import jdk.jfr.FlightRecorder;

/**
 * Java Flight Recorder implementation of {@link ITelemetryMeterSPI}. JFR is an event store, not a
 * metrics system, so the four instrument types are mapped as follows:
 * <ul>
 * <li><b>Counter</b> and <b>up-down counter</b> — accumulated in process per distinct attribute set
 * and sampled into one {@code com.helger.telemetry.Counter} respectively
 * {@code com.helger.telemetry.UpDownCounter} event per attribute set by a periodic hook. Emitting
 * an event per {@code add (...)} call would be ruinous on a hot path.</li>
 * <li><b>Histogram</b> — one {@code com.helger.telemetry.Histogram} event per recorded value. In
 * JFR the event is the sample; JDK Mission Control computes the distribution over the recorded
 * population. This is the most volume-intensive event type of the binding.</li>
 * <li><b>Gauge</b> — the natural fit: the supplier is polled by a periodic hook and emitted as a
 * {@code com.helger.telemetry.Gauge} event. {@code close ()} unregisters it.</li>
 * </ul>
 * The three periodic hooks are registered once per instance, in the constructor. Their default
 * period is {@value CJfrTelemetry#DEFAULT_PERIOD} and can be changed per recording, e.g. via
 * {@code enable (CJfrTelemetry.EVENT_GAUGE).withPeriod (Duration.ofSeconds (5))}. Call
 * {@link #shutdown()} to remove them again — the instance is unusable afterwards.
 * <p>
 * If JFR is not available in the running JVM, construction logs a warning and every instrument
 * becomes an in-memory no-op instead of failing.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@ThreadSafe
public class JfrTelemetryMeterSPI implements ITelemetryMeterSPI
{
  private static final Logger LOGGER = LoggerFactory.getLogger (JfrTelemetryMeterSPI.class);

  private final ConcurrentHashMap <String, JfrTelemetryCounter> m_aCounters = new ConcurrentHashMap <> ();
  private final ConcurrentHashMap <String, JfrTelemetryUpDownCounter> m_aUpDownCounters = new ConcurrentHashMap <> ();
  private final ConcurrentHashMap <String, JfrTelemetryHistogram> m_aHistograms = new ConcurrentHashMap <> ();
  private final Set <JfrTelemetryGauge> m_aGauges = ConcurrentHashMap.newKeySet ();
  private final Runnable m_aCounterHook = this::_commitCounterSnapshots;
  private final Runnable m_aUpDownCounterHook = this::_commitUpDownCounterSnapshots;
  private final Runnable m_aGaugeHook = this::_commitGaugeSnapshots;
  private volatile boolean m_bPeriodicRegistered;

  public JfrTelemetryMeterSPI ()
  {
    if (FlightRecorder.isAvailable ())
    {
      try
      {
        FlightRecorder.register (JfrCounterEvent.class);
        FlightRecorder.register (JfrUpDownCounterEvent.class);
        FlightRecorder.register (JfrGaugeEvent.class);
        FlightRecorder.addPeriodicEvent (JfrCounterEvent.class, m_aCounterHook);
        FlightRecorder.addPeriodicEvent (JfrUpDownCounterEvent.class, m_aUpDownCounterHook);
        FlightRecorder.addPeriodicEvent (JfrGaugeEvent.class, m_aGaugeHook);
        m_bPeriodicRegistered = true;
      }
      catch (final RuntimeException ex)
      {
        // Instruments are commonly resolved in a static initializer - throwing from here would
        // turn a telemetry problem into an ExceptionInInitializerError
        LOGGER.error ("Failed to register the periodic ph-telemetry JFR events - counters, up-down counters and gauges will not be recorded",
                      ex);
      }
    }
    else
      LOGGER.warn ("Java Flight Recorder is not available in this JVM - the ph-telemetry JFR meter records nothing");
  }

  /**
   * @return <code>true</code> if the periodic hooks for counters, up-down counters and gauges are
   *         currently registered with the Flight Recorder.
   */
  public final boolean isPeriodicRegistered ()
  {
    return m_bPeriodicRegistered;
  }

  /**
   * Remove the periodic event hooks from the Flight Recorder. After this call no further counter,
   * up-down counter or gauge samples are emitted by this instance. Histogram samples, which are
   * committed inline, are unaffected. Idempotent.
   */
  public final void shutdown ()
  {
    if (m_bPeriodicRegistered)
    {
      m_bPeriodicRegistered = false;
      FlightRecorder.removePeriodicEvent (m_aCounterHook);
      FlightRecorder.removePeriodicEvent (m_aUpDownCounterHook);
      FlightRecorder.removePeriodicEvent (m_aGaugeHook);
    }
  }

  @NonNull
  private static String _cacheKey (@NonNull final String sName,
                                   @Nullable final String sDescription,
                                   @Nullable final String sUnit)
  {
    return sName + '\n' + sDescription + '\n' + sUnit;
  }

  private void _commitCounterSnapshots ()
  {
    for (final JfrTelemetryCounter aCounter : m_aCounters.values ())
      aCounter.commitSnapshot ();
  }

  private void _commitUpDownCounterSnapshots ()
  {
    for (final JfrTelemetryUpDownCounter aCounter : m_aUpDownCounters.values ())
      aCounter.commitSnapshot ();
  }

  private void _commitGaugeSnapshots ()
  {
    for (final JfrTelemetryGauge aGauge : m_aGauges)
      aGauge.commitSnapshot ();
  }

  /**
   * Callback from {@link JfrTelemetryGauge#close()}.
   *
   * @param aGauge
   *        The gauge to stop observing. Never <code>null</code>.
   */
  void removeGauge (@NonNull final JfrTelemetryGauge aGauge)
  {
    m_aGauges.remove (aGauge);
  }

  @NonNull
  public ITelemetryCounter createCounter (@NonNull final String sName,
                                          @Nullable final String sDescription,
                                          @Nullable final String sUnit)
  {
    return m_aCounters.computeIfAbsent (_cacheKey (sName, sDescription, sUnit),
                                        k -> new JfrTelemetryCounter (sName, sUnit));
  }

  @NonNull
  public ITelemetryUpDownCounter createUpDownCounter (@NonNull final String sName,
                                                      @Nullable final String sDescription,
                                                      @Nullable final String sUnit)
  {
    return m_aUpDownCounters.computeIfAbsent (_cacheKey (sName, sDescription, sUnit),
                                              k -> new JfrTelemetryUpDownCounter (sName, sUnit));
  }

  @NonNull
  public ITelemetryHistogram createHistogram (@NonNull final String sName,
                                              @Nullable final String sDescription,
                                              @Nullable final String sUnit)
  {
    return m_aHistograms.computeIfAbsent (_cacheKey (sName, sDescription, sUnit),
                                          k -> new JfrTelemetryHistogram (sName, sUnit));
  }

  @NonNull
  public ITelemetryGauge createGauge (@NonNull final String sName,
                                      @Nullable final String sDescription,
                                      @Nullable final String sUnit,
                                      @NonNull final LongSupplier aSupplier)
  {
    final JfrTelemetryGauge ret = new JfrTelemetryGauge (this, sName, sUnit, aSupplier);
    m_aGauges.add (ret);
    return ret;
  }
}
