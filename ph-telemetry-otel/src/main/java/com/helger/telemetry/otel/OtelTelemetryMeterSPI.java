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
 * from {@link GlobalOpenTelemetry} on first use, caches it, and translates instrument creation
 * one-to-one to the underlying OpenTelemetry meter API.
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
 * If the OTel SDK has not been initialised, {@link GlobalOpenTelemetry#get()} returns the official
 * no-op {@code OpenTelemetry} — every instrument operation through this adapter then becomes a
 * cheap no-op at the SDK level.
 *
 * @author Philip Helger
 */
public class OtelTelemetryMeterSPI implements ITelemetryMeterSPI
{
  private final String m_sScopeName;
  private final String m_sScopeVersion;
  private volatile Meter m_aMeter;

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
  private Meter _meter ()
  {
    Meter aRet = m_aMeter;
    if (aRet == null)
    {
      final MeterBuilder aBuilder = GlobalOpenTelemetry.get ().getMeterProvider ().meterBuilder (m_sScopeName);
      if (m_sScopeVersion != null)
        aBuilder.setInstrumentationVersion (m_sScopeVersion);
      aRet = aBuilder.build ();
      m_aMeter = aRet;
    }
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
