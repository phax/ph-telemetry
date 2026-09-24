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
package com.helger.telemetry.otel;

import java.util.function.LongSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.telemetry.ITelemetryCounter;
import com.helger.telemetry.ITelemetryGauge;
import com.helger.telemetry.ITelemetryHistogram;
import com.helger.telemetry.ITelemetryMeterSPI;
import com.helger.telemetry.ITelemetryUpDownCounter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * Generic OpenTelemetry implementation of {@link ITelemetryMeterSPI}. Resolves the {@link Meter}
 * from {@link GlobalOpenTelemetry} on first use and translates instrument creation one-to-one to
 * the underlying OpenTelemetry meter API.
 * <p>
 * Subclass with a no-arg constructor that supplies the instrumentation scope name and version, then
 * register the subclass via {@code META-INF/services/com.helger.telemetry.ITelemetryMeterSPI}:
 *
 * <pre>
 * public final class MyAppMeterSPI extends OtelTelemetryMeterSPI
 * {
 *   public MyAppMeterSPI ()
 *   {
 *     super ("com.example.myapp", MyAppVersion.BUILD_VERSION);
 *   }
 * }
 * </pre>
 * <p>
 * If no {@code OpenTelemetry} instance is registered yet, every instrument operation through this
 * adapter becomes a cheap no-op at the SDK level — but the adapter neither claims the global slot
 * nor remembers the no-op meter, so an SDK that is installed later takes effect. Note that an
 * instrument that was created before is <b>not</b> re-created: it keeps pointing at the meter it
 * was built from, so instruments should be created after the SDK bootstrap.
 *
 * @author Philip Helger
 */
public class OtelTelemetryMeterSPI implements ITelemetryMeterSPI
{
  private final String m_sScopeName;
  private final String m_sScopeVersion;
  // Resolved from a registered OpenTelemetry instance - final, because the global one can only be
  // set once
  private volatile Meter m_aMeter;
  // Resolved while no OpenTelemetry instance was registered yet - must be dropped as soon as one is
  private volatile Meter m_aMeterWithoutGlobal;

  /**
   * @param sScopeName
   *        The OpenTelemetry instrumentation scope name. Never <code>null</code>.
   * @param sScopeVersion
   *        The OpenTelemetry instrumentation scope version. May be <code>null</code>.
   */
  protected OtelTelemetryMeterSPI (@NonNull final String sScopeName, @Nullable final String sScopeVersion)
  {
    m_sScopeName = sScopeName;
    m_sScopeVersion = sScopeVersion;
  }

  @NonNull
  private Meter _buildMeter ()
  {
    // Deliberately "getOrNoop ()" and not "get ()": the latter registers the no-op instance as the
    // global one if none is set yet, and every later "GlobalOpenTelemetry.set (...)" then fails
    // with an IllegalStateException - so merely creating an instrument before the SDK bootstrap of
    // the application would break that bootstrap. "getOrNoop ()" is what the OpenTelemetry API
    // documents for instrumentation and has no such side effect.
    final MeterBuilder aBuilder = GlobalOpenTelemetry.getOrNoop ().getMeterProvider ().meterBuilder (m_sScopeName);
    if (m_sScopeVersion != null)
      aBuilder.setInstrumentationVersion (m_sScopeVersion);
    return aBuilder.build ();
  }

  @NonNull
  private Meter _meter ()
  {
    // Fast path: a meter of a registered OpenTelemetry instance can never become stale
    final Meter aMeter = m_aMeter;
    if (aMeter != null)
      return aMeter;

    if (!GlobalOpenTelemetry.isSet ())
    {
      // Nothing registered yet, e.g. because the SDK bootstrap of the application did not run yet.
      // The meter built here is the no-op one and is deliberately NOT remembered as the final one:
      // otherwise a single instrument created too early would leave every instrument of this JVM a
      // no-op, even after the SDK is installed a moment later
      Meter aRet = m_aMeterWithoutGlobal;
      if (aRet == null)
      {
        aRet = _buildMeter ();
        m_aMeterWithoutGlobal = aRet;
      }
      return aRet;
    }

    final Meter aRet = _buildMeter ();
    m_aMeter = aRet;
    return aRet;
  }

  @NonNull
  public ITelemetryCounter createCounter (@NonNull final String sName,
                                          @Nullable final String sDescription,
                                          @Nullable final String sUnit)
  {
    final LongCounterBuilder aBuilder = _meter ().counterBuilder (sName);
    if (sDescription != null)
      aBuilder.setDescription (sDescription);
    if (sUnit != null)
      aBuilder.setUnit (sUnit);
    final LongCounter aCounter = aBuilder.build ();
    return new OtelTelemetryCounter (aCounter);
  }

  @NonNull
  public ITelemetryUpDownCounter createUpDownCounter (@NonNull final String sName,
                                                      @Nullable final String sDescription,
                                                      @Nullable final String sUnit)
  {
    final LongUpDownCounterBuilder aBuilder = _meter ().upDownCounterBuilder (sName);
    if (sDescription != null)
      aBuilder.setDescription (sDescription);
    if (sUnit != null)
      aBuilder.setUnit (sUnit);
    final LongUpDownCounter aCounter = aBuilder.build ();
    return new OtelTelemetryUpDownCounter (aCounter);
  }

  @NonNull
  public ITelemetryHistogram createHistogram (@NonNull final String sName,
                                              @Nullable final String sDescription,
                                              @Nullable final String sUnit)
  {
    final DoubleHistogramBuilder aBuilder = _meter ().histogramBuilder (sName);
    if (sDescription != null)
      aBuilder.setDescription (sDescription);
    if (sUnit != null)
      aBuilder.setUnit (sUnit);
    final DoubleHistogram aHistogram = aBuilder.build ();
    return new OtelTelemetryHistogram (aHistogram);
  }

  @NonNull
  public ITelemetryGauge createGauge (@NonNull final String sName,
                                      @Nullable final String sDescription,
                                      @Nullable final String sUnit,
                                      @NonNull final LongSupplier aSupplier)
  {
    var aBuilder = _meter ().gaugeBuilder (sName).ofLongs ();
    if (sDescription != null)
      aBuilder = aBuilder.setDescription (sDescription);
    if (sUnit != null)
      aBuilder = aBuilder.setUnit (sUnit);
    final ObservableLongGauge aGauge = aBuilder.buildWithCallback (m -> m.record (aSupplier.getAsLong ()));
    return new OtelTelemetryGauge (aGauge);
  }
}
