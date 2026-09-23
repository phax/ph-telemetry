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

import java.util.List;
import java.util.function.LongSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsImmutableObject;
import com.helger.base.enforce.ValueEnforcer;

/**
 * An {@link ITelemetryMeterSPI} that fans every instrument out to a fixed list of delegate meters.
 * The metrics counterpart of {@link CompositeTelemetryTracerSPI} — see there for the rationale and
 * the registration pattern.
 * <p>
 * Every factory method creates one instrument per delegate and returns a fan-out instrument that
 * forwards each recording to all of them, in the order the delegates were passed in.
 * <p>
 * Note on {@link #createGauge(String, String, String, LongSupplier)}: the same
 * {@link LongSupplier} is handed to every delegate, so it is polled once per delegate and at each
 * delegate's own cadence. The supplier must be cheap and thread-safe anyway (that is the general
 * SPI contract), but with N delegates it is invoked N times as often.
 * <p>
 * No exception handling is performed: a delegate that throws aborts the fan-out and propagates to
 * the caller.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@Immutable
public class CompositeTelemetryMeterSPI implements ITelemetryMeterSPI
{
  private final ITelemetryMeterSPI [] m_aDelegates;

  /**
   * @param aDelegates
   *        The meters to delegate to, in invocation order. May neither be <code>null</code> nor
   *        empty and may not contain <code>null</code> elements.
   */
  public CompositeTelemetryMeterSPI (@NonNull @Nonempty final ITelemetryMeterSPI... aDelegates)
  {
    ValueEnforcer.notEmptyNoNullValue (aDelegates, "Delegates");
    m_aDelegates = aDelegates.clone ();
  }

  /**
   * @param aDelegates
   *        The meters to delegate to, in invocation order. May neither be <code>null</code> nor
   *        empty and may not contain <code>null</code> elements.
   */
  public CompositeTelemetryMeterSPI (@NonNull @Nonempty final List <? extends ITelemetryMeterSPI> aDelegates)
  {
    ValueEnforcer.notEmptyNoNullValue (aDelegates, "Delegates");
    m_aDelegates = aDelegates.toArray (new ITelemetryMeterSPI [0]);
  }

  /**
   * @return All delegate meters in invocation order. Never <code>null</code> nor empty.
   */
  @NonNull
  @Nonempty
  @ReturnsImmutableObject
  public final List <ITelemetryMeterSPI> getAllDelegates ()
  {
    return List.of (m_aDelegates);
  }

  @NonNull
  public ITelemetryCounter createCounter (@NonNull final String sName,
                                          @Nullable final String sDescription,
                                          @Nullable final String sUnit)
  {
    final int nCount = m_aDelegates.length;
    final ITelemetryCounter [] aInstruments = new ITelemetryCounter [nCount];
    for (int i = 0; i < nCount; ++i)
      aInstruments[i] = m_aDelegates[i].createCounter (sName, sDescription, sUnit);
    return new CompositeCounter (aInstruments);
  }

  @NonNull
  public ITelemetryUpDownCounter createUpDownCounter (@NonNull final String sName,
                                                      @Nullable final String sDescription,
                                                      @Nullable final String sUnit)
  {
    final int nCount = m_aDelegates.length;
    final ITelemetryUpDownCounter [] aInstruments = new ITelemetryUpDownCounter [nCount];
    for (int i = 0; i < nCount; ++i)
      aInstruments[i] = m_aDelegates[i].createUpDownCounter (sName, sDescription, sUnit);
    return new CompositeUpDownCounter (aInstruments);
  }

  @NonNull
  public ITelemetryHistogram createHistogram (@NonNull final String sName,
                                              @Nullable final String sDescription,
                                              @Nullable final String sUnit)
  {
    final int nCount = m_aDelegates.length;
    final ITelemetryHistogram [] aInstruments = new ITelemetryHistogram [nCount];
    for (int i = 0; i < nCount; ++i)
      aInstruments[i] = m_aDelegates[i].createHistogram (sName, sDescription, sUnit);
    return new CompositeHistogram (aInstruments);
  }

  @NonNull
  public ITelemetryGauge createGauge (@NonNull final String sName,
                                      @Nullable final String sDescription,
                                      @Nullable final String sUnit,
                                      @NonNull final LongSupplier aSupplier)
  {
    final int nCount = m_aDelegates.length;
    final ITelemetryGauge [] aInstruments = new ITelemetryGauge [nCount];
    for (int i = 0; i < nCount; ++i)
      aInstruments[i] = m_aDelegates[i].createGauge (sName, sDescription, sUnit, aSupplier);
    return new CompositeGauge (aInstruments);
  }

  // === Fan-out instruments ===

  private static final class CompositeCounter implements ITelemetryCounter
  {
    private final ITelemetryCounter [] m_aInstruments;

    CompositeCounter (@NonNull final ITelemetryCounter [] aInstruments)
    {
      m_aInstruments = aInstruments;
    }

    public void add (final long nValue, @NonNull final TelemetryAttributes aAttributes)
    {
      for (final ITelemetryCounter aInstrument : m_aInstruments)
        aInstrument.add (nValue, aAttributes);
    }
  }

  private static final class CompositeUpDownCounter implements ITelemetryUpDownCounter
  {
    private final ITelemetryUpDownCounter [] m_aInstruments;

    CompositeUpDownCounter (@NonNull final ITelemetryUpDownCounter [] aInstruments)
    {
      m_aInstruments = aInstruments;
    }

    public void add (final long nValue, @NonNull final TelemetryAttributes aAttributes)
    {
      for (final ITelemetryUpDownCounter aInstrument : m_aInstruments)
        aInstrument.add (nValue, aAttributes);
    }
  }

  private static final class CompositeHistogram implements ITelemetryHistogram
  {
    private final ITelemetryHistogram [] m_aInstruments;

    CompositeHistogram (@NonNull final ITelemetryHistogram [] aInstruments)
    {
      m_aInstruments = aInstruments;
    }

    public void record (final double dValue, @NonNull final TelemetryAttributes aAttributes)
    {
      for (final ITelemetryHistogram aInstrument : m_aInstruments)
        aInstrument.record (dValue, aAttributes);
    }
  }

  private static final class CompositeGauge implements ITelemetryGauge
  {
    private final ITelemetryGauge [] m_aInstruments;

    CompositeGauge (@NonNull final ITelemetryGauge [] aInstruments)
    {
      m_aInstruments = aInstruments;
    }

    public void close ()
    {
      for (int i = m_aInstruments.length - 1; i >= 0; --i)
        m_aInstruments[i].close ();
    }
  }
}
