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
package com.helger.telemetry.mock;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.CommonsLinkedHashMap;
import com.helger.collection.commons.ICommonsList;
import com.helger.collection.commons.ICommonsOrderedMap;
import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.ITelemetryCounter;
import com.helger.telemetry.ITelemetryGauge;
import com.helger.telemetry.ITelemetryHistogram;
import com.helger.telemetry.ITelemetryMeterSPI;
import com.helger.telemetry.ITelemetrySpan;
import com.helger.telemetry.ITelemetryTracerSPI;
import com.helger.telemetry.ITelemetryUpDownCounter;
import com.helger.telemetry.Telemetry;
import com.helger.telemetry.TelemetryAttributes;
import com.helger.telemetry.TelemetryMetrics;

/**
 * In-memory capturing tracer and meter for unit tests. Implements both {@link ITelemetryTracerSPI}
 * and {@link ITelemetryMeterSPI} and records everything that is emitted, so a test can assert on
 * span names, span kinds, span attributes, recorded exceptions, metric values and metric
 * attributes.
 * <p>
 * Install it programmatically via {@link #install()} — it is deliberately <em>not</em> registered
 * via {@link java.util.ServiceLoader}, so it never leaks into a production classpath scan. Restore
 * the default no-op SPIs afterwards via {@link #uninstall()}.
 *
 * <pre>
 * private static final CapturingTelemetry TELEMETRY = new CapturingTelemetry ();
 *
 * &#64;BeforeClass
 * public static void beforeClass ()
 * {
 *   TELEMETRY.install ();
 * }
 *
 * &#64;AfterClass
 * public static void afterClass ()
 * {
 *   CapturingTelemetry.uninstall ();
 * }
 *
 * &#64;Test
 * public void testSomething ()
 * {
 *   ... run the unit under test ...
 *   final CapturedSpan aSpan = TELEMETRY.getFirstSpan ("db.query");
 *   assertNotNull (aSpan);
 *   assertEquals (ETelemetrySpanKind.CLIENT, aSpan.getKind ());
 *   assertEquals (Boolean.TRUE, aSpan.getAttribute ("success"));
 *   assertEquals (1, TELEMETRY.getCounterValue ("db.query.count"));
 * }
 * </pre>
 *
 * Note on timing: metric instruments are commonly resolved once in the static initializer of a
 * {@code *Metrics} holder class. Installing must therefore happen <em>before</em> that class is
 * loaded — use {@code @BeforeClass} (or a {@code @ClassRule}), not {@code @Before}.
 *
 * @author Philip Helger
 * @since 1.0.2
 */
@ThreadSafe
public final class CapturingTelemetry implements ITelemetryTracerSPI, ITelemetryMeterSPI
{
  /**
   * Convert an attribute set to an ordered map, preserving the declared value type of every entry
   * ({@link String}, {@link Long}, {@link Double} or {@link Boolean}). Declared first because all
   * nested types below use it.
   *
   * @param aAttributes
   *        The attributes to convert. Never <code>null</code>.
   * @return A new mutable map in insertion order. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  private static ICommonsOrderedMap <String, Object> _toMap (@NonNull final TelemetryAttributes aAttributes)
  {
    final ICommonsOrderedMap <String, Object> ret = new CommonsLinkedHashMap <> ();
    aAttributes.forEach (new TelemetryAttributes.IVisitor ()
    {
      public void onString (@NonNull final String sKey, @NonNull final String sValue)
      {
        ret.put (sKey, sValue);
      }

      public void onLong (@NonNull final String sKey, final long nValue)
      {
        ret.put (sKey, Long.valueOf (nValue));
      }

      public void onDouble (@NonNull final String sKey, final double dValue)
      {
        ret.put (sKey, Double.valueOf (dValue));
      }

      public void onBoolean (@NonNull final String sKey, final boolean bValue)
      {
        ret.put (sKey, Boolean.valueOf (bValue));
      }
    });
    return ret;
  }

  /**
   * Snapshot of a single event recorded via
   * {@link ITelemetrySpan#addEvent(String, TelemetryAttributes)}.
   *
   * @author Philip Helger
   * @since 1.0.2
   */
  @Immutable
  public static final class CapturedEvent
  {
    private final String m_sName;
    private final ICommonsOrderedMap <String, Object> m_aAttributes;

    CapturedEvent (@NonNull final String sName, @NonNull final TelemetryAttributes aAttributes)
    {
      m_sName = sName;
      m_aAttributes = _toMap (aAttributes);
    }

    /**
     * @return The event name. Never <code>null</code>.
     */
    @NonNull
    public String getName ()
    {
      return m_sName;
    }

    /**
     * @return A copy of the attributes attached to this event, in insertion order. Never
     *         <code>null</code>.
     */
    @NonNull
    @ReturnsMutableCopy
    public ICommonsOrderedMap <String, Object> getAttributes ()
    {
      return m_aAttributes.getClone ();
    }

    /**
     * @param sKey
     *        The attribute key to look up. Never <code>null</code>.
     * @return The attribute value, or <code>null</code> if the event has no such attribute.
     */
    @Nullable
    public Object getAttribute (@NonNull final String sKey)
    {
      return m_aAttributes.get (sKey);
    }

    @Override
    public String toString ()
    {
      return new ToStringGenerator (this).append ("Name", m_sName).append ("Attributes", m_aAttributes).getToString ();
    }
  }

  /**
   * A span that records everything set on it. Implements {@link ITelemetrySpan} so that the
   * {@link ITelemetryTracerSPI#startSpan(String, ETelemetrySpanKind)} contract is satisfied; tests
   * inspect the recorded data through the accessors.
   * <p>
   * Unlike the no-op span, recording is unconditional — data set after {@link #close()} is still
   * captured, and {@link #isClosed()} tells whether the span was closed at all.
   *
   * @author Philip Helger
   * @since 1.0.2
   */
  @ThreadSafe
  public static final class CapturedSpan implements ITelemetrySpan
  {
    private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
    private final String m_sName;
    private final ETelemetrySpanKind m_eKind;
    private final ICommonsOrderedMap <String, Object> m_aAttributes = new CommonsLinkedHashMap <> ();
    private final ICommonsList <CapturedEvent> m_aEvents = new CommonsArrayList <> ();
    private Throwable m_aRecordedException;
    private boolean m_bStatusOk;
    private boolean m_bStatusError;
    private String m_sStatusMessage;
    private boolean m_bClosed;

    CapturedSpan (@NonNull final String sName, @NonNull final ETelemetrySpanKind eKind)
    {
      m_sName = sName;
      m_eKind = eKind;
    }

    private void _putAttribute (@NonNull final String sKey, @NonNull final Object aValue)
    {
      m_aRWLock.writeLocked (() -> m_aAttributes.put (sKey, aValue));
    }

    @NonNull
    public ITelemetrySpan setAttribute (@NonNull final String sKey, @Nullable final String sValue)
    {
      // Same contract as the real SPI — a null value means "not set"
      if (sValue != null)
        _putAttribute (sKey, sValue);
      return this;
    }

    @NonNull
    public ITelemetrySpan setAttribute (@NonNull final String sKey, final boolean bValue)
    {
      _putAttribute (sKey, Boolean.valueOf (bValue));
      return this;
    }

    @NonNull
    public ITelemetrySpan setAttribute (@NonNull final String sKey, final long nValue)
    {
      _putAttribute (sKey, Long.valueOf (nValue));
      return this;
    }

    @NonNull
    public ITelemetrySpan setAttribute (@NonNull final String sKey, final double dValue)
    {
      _putAttribute (sKey, Double.valueOf (dValue));
      return this;
    }

    @NonNull
    public ITelemetrySpan recordException (@NonNull final Throwable aException)
    {
      m_aRWLock.writeLocked (() -> m_aRecordedException = aException);
      return this;
    }

    @NonNull
    public ITelemetrySpan addEvent (@NonNull final String sName, @NonNull final TelemetryAttributes aAttributes)
    {
      final CapturedEvent aEvent = new CapturedEvent (sName, aAttributes);
      m_aRWLock.writeLocked (() -> m_aEvents.add (aEvent));
      return this;
    }

    @NonNull
    public ITelemetrySpan setStatusOk ()
    {
      m_aRWLock.writeLocked (() -> m_bStatusOk = true);
      return this;
    }

    @NonNull
    public ITelemetrySpan setStatusError (@Nullable final String sMessage)
    {
      m_aRWLock.writeLocked (() -> {
        m_bStatusError = true;
        m_sStatusMessage = sMessage;
      });
      return this;
    }

    public void close ()
    {
      m_aRWLock.writeLocked (() -> m_bClosed = true);
    }

    /**
     * @return The span name as passed to
     *         {@link ITelemetryTracerSPI#startSpan(String, ETelemetrySpanKind)}. Never
     *         <code>null</code>.
     */
    @NonNull
    public String getName ()
    {
      return m_sName;
    }

    /**
     * @return The span kind as passed to
     *         {@link ITelemetryTracerSPI#startSpan(String, ETelemetrySpanKind)}. Never
     *         <code>null</code>.
     */
    @NonNull
    public ETelemetrySpanKind getKind ()
    {
      return m_eKind;
    }

    /**
     * @return A copy of all attributes set via the {@code setAttribute(...)} overloads, in
     *         insertion order. Never <code>null</code>.
     */
    @NonNull
    @ReturnsMutableCopy
    public ICommonsOrderedMap <String, Object> getAttributes ()
    {
      return m_aRWLock.readLockedGet (m_aAttributes::getClone);
    }

    /**
     * @param sKey
     *        The attribute key to look up. Never <code>null</code>.
     * @return The attribute value, or <code>null</code> if the span has no such attribute. The
     *         runtime type is {@link String}, {@link Long}, {@link Double} or {@link Boolean},
     *         matching the {@code setAttribute(...)} overload that was used.
     */
    @Nullable
    public Object getAttribute (@NonNull final String sKey)
    {
      return m_aRWLock.readLockedGet (() -> m_aAttributes.get (sKey));
    }

    /**
     * @return A copy of all events recorded via {@link #addEvent(String, TelemetryAttributes)}, in
     *         record order. Never <code>null</code>; may be empty.
     */
    @NonNull
    @ReturnsMutableCopy
    public ICommonsList <CapturedEvent> getEvents ()
    {
      return m_aRWLock.readLockedGet (m_aEvents::getClone);
    }

    /**
     * @param sName
     *        The event name to match. Never <code>null</code>.
     * @return The first recorded event with that name, or <code>null</code> if there is none.
     */
    @Nullable
    public CapturedEvent getFirstEvent (@NonNull final String sName)
    {
      return m_aRWLock.readLockedGet (() -> m_aEvents.findFirst (x -> sName.equals (x.getName ())));
    }

    /**
     * @return The exception passed to {@link #recordException(Throwable)}, or <code>null</code> if
     *         none was recorded. If called more than once, the last one wins.
     */
    @Nullable
    public Throwable getRecordedException ()
    {
      return m_aRWLock.readLockedGet (() -> m_aRecordedException);
    }

    /**
     * @return <code>true</code> if {@link #setStatusOk()} was called at least once.
     */
    public boolean isStatusOk ()
    {
      return m_aRWLock.readLockedBoolean (() -> m_bStatusOk);
    }

    /**
     * @return <code>true</code> if {@link #setStatusError(String)} was called at least once.
     */
    public boolean isStatusError ()
    {
      return m_aRWLock.readLockedBoolean (() -> m_bStatusError);
    }

    /**
     * @return The message passed to {@link #setStatusError(String)}. May be <code>null</code>.
     */
    @Nullable
    public String getStatusMessage ()
    {
      return m_aRWLock.readLockedGet (() -> m_sStatusMessage);
    }

    /**
     * @return <code>true</code> if {@link #close()} was called — the usual assertion that the unit
     *         under test uses try-with-resources correctly.
     */
    public boolean isClosed ()
    {
      return m_aRWLock.readLockedBoolean (() -> m_bClosed);
    }

    @Override
    public String toString ()
    {
      return new ToStringGenerator (this).append ("Name", m_sName)
                                         .append ("Kind", m_eKind)
                                         .append ("Attributes", m_aAttributes)
                                         .append ("Events", m_aEvents)
                                         .appendIfNotNull ("RecordedException", m_aRecordedException)
                                         .append ("StatusOk", m_bStatusOk)
                                         .append ("StatusError", m_bStatusError)
                                         .appendIfNotNull ("StatusMessage", m_sStatusMessage)
                                         .append ("Closed", m_bClosed)
                                         .getToString ();
    }
  }

  /**
   * A single value recorded against a counter, up-down counter or histogram, together with the
   * attributes that were attached to that specific recording.
   *
   * @author Philip Helger
   * @since 1.0.2
   */
  @Immutable
  public static final class CapturedMeasurement
  {
    private final String m_sInstrumentName;
    private final double m_dValue;
    private final ICommonsOrderedMap <String, Object> m_aAttributes;

    CapturedMeasurement (@NonNull final String sInstrumentName,
                         final double dValue,
                         @NonNull final TelemetryAttributes aAttributes)
    {
      m_sInstrumentName = sInstrumentName;
      m_dValue = dValue;
      m_aAttributes = _toMap (aAttributes);
    }

    /**
     * @return The name of the instrument this value was recorded against. Never <code>null</code>.
     */
    @NonNull
    public String getInstrumentName ()
    {
      return m_sInstrumentName;
    }

    /**
     * @return The recorded value. For counters and up-down counters this is the added delta, not
     *         the aggregate — see {@link CapturingTelemetry#getCounterValue(String)} for the
     *         latter.
     */
    public double getValue ()
    {
      return m_dValue;
    }

    /**
     * @return The recorded value cast to <code>long</code>. Convenience for counter assertions.
     */
    public long getValueAsLong ()
    {
      return (long) m_dValue;
    }

    /**
     * @return A copy of the attributes attached to this recording, in insertion order. Never
     *         <code>null</code>.
     */
    @NonNull
    @ReturnsMutableCopy
    public ICommonsOrderedMap <String, Object> getAttributes ()
    {
      return m_aAttributes.getClone ();
    }

    /**
     * @param sKey
     *        The attribute key to look up. Never <code>null</code>.
     * @return The attribute value, or <code>null</code> if this recording has no such attribute.
     */
    @Nullable
    public Object getAttribute (@NonNull final String sKey)
    {
      return m_aAttributes.get (sKey);
    }

    @Override
    public String toString ()
    {
      return new ToStringGenerator (this).append ("InstrumentName", m_sInstrumentName)
                                         .append ("Value", m_dValue)
                                         .append ("Attributes", m_aAttributes)
                                         .getToString ();
    }
  }

  /**
   * A gauge registration. No backend polls in a unit test, so the supplier is simply retained and
   * can be evaluated on demand via {@link #getValue()}.
   *
   * @author Philip Helger
   * @since 1.0.2
   */
  @ThreadSafe
  public static final class CapturedGauge implements ITelemetryGauge
  {
    private final String m_sName;
    private final LongSupplier m_aSupplier;
    private volatile boolean m_bClosed;

    CapturedGauge (@NonNull final String sName, @NonNull final LongSupplier aSupplier)
    {
      m_sName = sName;
      m_aSupplier = aSupplier;
    }

    public void close ()
    {
      m_bClosed = true;
    }

    /**
     * @return The instrument name. Never <code>null</code>.
     */
    @NonNull
    public String getName ()
    {
      return m_sName;
    }

    /**
     * @return The supplier passed to
     *         {@link ITelemetryMeterSPI#createGauge(String, String, String, LongSupplier)}. Never
     *         <code>null</code>.
     */
    @NonNull
    public LongSupplier getSupplier ()
    {
      return m_aSupplier;
    }

    /**
     * Evaluate the underlying supplier now. Works irrespective of {@link #isClosed()}, so a test
     * can still inspect the wiring of a closed gauge.
     *
     * @return The current value of the gauge.
     */
    public long getValue ()
    {
      return m_aSupplier.getAsLong ();
    }

    /**
     * @return <code>true</code> if {@link #close()} was called.
     */
    public boolean isClosed ()
    {
      return m_bClosed;
    }

    @Override
    public String toString ()
    {
      return new ToStringGenerator (this).append ("Name", m_sName)
                                         .append ("Supplier", m_aSupplier)
                                         .append ("Closed", m_bClosed)
                                         .getToString ();
    }
  }

  private final SimpleReadWriteLock m_aRWLock = new SimpleReadWriteLock ();
  private final ICommonsList <CapturedSpan> m_aSpans = new CommonsArrayList <> ();
  private final ICommonsList <CapturedMeasurement> m_aMeasurements = new CommonsArrayList <> ();
  private final ICommonsOrderedMap <String, AtomicLong> m_aCounters = new CommonsLinkedHashMap <> ();
  private final ICommonsOrderedMap <String, ICommonsList <Double>> m_aHistograms = new CommonsLinkedHashMap <> ();
  private final ICommonsOrderedMap <String, CapturedGauge> m_aGauges = new CommonsLinkedHashMap <> ();

  private void _recordMeasurement (@NonNull final String sInstrumentName,
                                   final double dValue,
                                   @NonNull final TelemetryAttributes aAttributes)
  {
    final CapturedMeasurement aMeasurement = new CapturedMeasurement (sInstrumentName, dValue, aAttributes);
    m_aRWLock.writeLocked (() -> m_aMeasurements.add (aMeasurement));
  }

  @NonNull
  private AtomicLong _getOrCreateCounterAggregate (@NonNull final String sName)
  {
    return m_aRWLock.writeLockedGet (() -> m_aCounters.computeIfAbsent (sName, x -> new AtomicLong ()));
  }

  @NonNull
  private ICommonsList <Double> _getOrCreateHistogramValues (@NonNull final String sName)
  {
    return m_aRWLock.writeLockedGet (() -> m_aHistograms.computeIfAbsent (sName, x -> new CommonsArrayList <> ()));
  }

  // --- ITelemetryTracerSPI ---

  @NonNull
  public ITelemetrySpan startSpan (@NonNull final String sName, @NonNull final ETelemetrySpanKind eKind)
  {
    final CapturedSpan aSpan = new CapturedSpan (sName, eKind);
    m_aRWLock.writeLocked (() -> m_aSpans.add (aSpan));
    return aSpan;
  }

  // --- ITelemetryMeterSPI ---

  @NonNull
  public ITelemetryCounter createCounter (@NonNull final String sName,
                                          @Nullable final String sDescription,
                                          @Nullable final String sUnit)
  {
    // AtomicLong is thread-safe on its own, so the per-add path needs no lock for the aggregate
    final AtomicLong aAggregate = _getOrCreateCounterAggregate (sName);
    return (nValue, aAttributes) -> {
      aAggregate.addAndGet (nValue);
      _recordMeasurement (sName, nValue, aAttributes);
    };
  }

  @NonNull
  public ITelemetryUpDownCounter createUpDownCounter (@NonNull final String sName,
                                                      @Nullable final String sDescription,
                                                      @Nullable final String sUnit)
  {
    final AtomicLong aAggregate = _getOrCreateCounterAggregate (sName);
    return (nValue, aAttributes) -> {
      aAggregate.addAndGet (nValue);
      _recordMeasurement (sName, nValue, aAttributes);
    };
  }

  @NonNull
  public ITelemetryHistogram createHistogram (@NonNull final String sName,
                                              @Nullable final String sDescription,
                                              @Nullable final String sUnit)
  {
    final ICommonsList <Double> aValues = _getOrCreateHistogramValues (sName);
    return (dValue, aAttributes) -> {
      m_aRWLock.writeLocked (() -> aValues.add (Double.valueOf (dValue)));
      _recordMeasurement (sName, dValue, aAttributes);
    };
  }

  @NonNull
  public ITelemetryGauge createGauge (@NonNull final String sName,
                                      @Nullable final String sDescription,
                                      @Nullable final String sUnit,
                                      @NonNull final LongSupplier aSupplier)
  {
    final CapturedGauge aGauge = new CapturedGauge (sName, aSupplier);
    m_aRWLock.writeLocked (() -> m_aGauges.put (sName, aGauge));
    return aGauge;
  }

  // --- Installation ---

  /**
   * Install this instance as both the tracer and the meter SPI. Call this before the class holding
   * the metric instruments is loaded — see the class comment.
   */
  public void install ()
  {
    Telemetry.install (this);
    TelemetryMetrics.install (this);
  }

  /**
   * Restore the default no-op tracer and meter. Static because it does not matter which instance
   * was installed.
   */
  public static void uninstall ()
  {
    Telemetry.install (null);
    TelemetryMetrics.install (null);
  }

  // --- Inspection ---

  /**
   * @return A copy of all captured spans, in start order. Never <code>null</code>; may be empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <CapturedSpan> getSpans ()
  {
    return m_aRWLock.readLockedGet (m_aSpans::getClone);
  }

  /**
   * @param sName
   *        The span name to match. Never <code>null</code>.
   * @return A copy of all captured spans with that name, in start order. Never <code>null</code>;
   *         may be empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <CapturedSpan> getSpans (@NonNull final String sName)
  {
    return m_aRWLock.readLockedGet (() -> m_aSpans.getAll (x -> sName.equals (x.getName ())));
  }

  /**
   * @param sName
   *        The span name to match. Never <code>null</code>.
   * @return The first captured span with that name, or <code>null</code> if there is none.
   */
  @Nullable
  public CapturedSpan getFirstSpan (@NonNull final String sName)
  {
    return m_aRWLock.readLockedGet (() -> m_aSpans.findFirst (x -> sName.equals (x.getName ())));
  }

  /**
   * @return The total number of captured spans.
   */
  public int getSpanCount ()
  {
    return m_aRWLock.readLockedInt (m_aSpans::size);
  }

  /**
   * @param sName
   *        The span name to match. Never <code>null</code>.
   * @return The number of captured spans with that name.
   */
  public int getSpanCount (@NonNull final String sName)
  {
    return m_aRWLock.readLockedInt (() -> m_aSpans.getCount (x -> sName.equals (x.getName ())));
  }

  /**
   * @return A copy of every single value recorded against a counter, up-down counter or histogram,
   *         in record order. Never <code>null</code>; may be empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <CapturedMeasurement> getMeasurements ()
  {
    return m_aRWLock.readLockedGet (m_aMeasurements::getClone);
  }

  /**
   * @param sInstrumentName
   *        The instrument name to match. Never <code>null</code>.
   * @return A copy of all values recorded against that instrument, in record order. Never
   *         <code>null</code>; may be empty.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <CapturedMeasurement> getMeasurements (@NonNull final String sInstrumentName)
  {
    return m_aRWLock.readLockedGet (() -> m_aMeasurements.getAll (x -> sInstrumentName.equals (x.getInstrumentName ())));
  }

  /**
   * @param sInstrumentName
   *        The instrument name to match. Never <code>null</code>.
   * @return The first value recorded against that instrument, or <code>null</code> if there is
   *         none.
   */
  @Nullable
  public CapturedMeasurement getFirstMeasurement (@NonNull final String sInstrumentName)
  {
    return m_aRWLock.readLockedGet (() -> m_aMeasurements.findFirst (x -> sInstrumentName.equals (x.getInstrumentName ())));
  }

  /**
   * @return The total number of captured values, across all counters, up-down counters and
   *         histograms.
   */
  public int getMeasurementCount ()
  {
    return m_aRWLock.readLockedInt (m_aMeasurements::size);
  }

  /**
   * @param sInstrumentName
   *        The instrument name to match. Never <code>null</code>.
   * @return The number of values recorded against that instrument. This counts the recordings, not
   *         their sum — see {@link #getCounterValue(String)} for the aggregate.
   */
  public int getMeasurementCount (@NonNull final String sInstrumentName)
  {
    return m_aRWLock.readLockedInt (() -> m_aMeasurements.getCount (x -> sInstrumentName.equals (x.getInstrumentName ())));
  }

  /**
   * @param sName
   *        The instrument name. Never <code>null</code>.
   * @return The aggregated value of the counter or up-down counter with that name, or {@code 0} if
   *         no such instrument was ever created.
   */
  public long getCounterValue (@NonNull final String sName)
  {
    final AtomicLong aAggregate = m_aRWLock.readLockedGet (() -> m_aCounters.get (sName));
    return aAggregate == null ? 0L : aAggregate.get ();
  }

  /**
   * @param sName
   *        The instrument name. Never <code>null</code>.
   * @return A copy of all values recorded against the histogram with that name, in record order.
   *         Never <code>null</code>; empty if no such instrument was ever created.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <Double> getHistogramValues (@NonNull final String sName)
  {
    return m_aRWLock.readLockedGet (() -> {
      final ICommonsList <Double> aValues = m_aHistograms.get (sName);
      return aValues == null ? new CommonsArrayList <> () : aValues.getClone ();
    });
  }

  /**
   * @param sName
   *        The instrument name. Never <code>null</code>.
   * @return The gauge registered under that name, or <code>null</code> if no such gauge was ever
   *         created.
   */
  @Nullable
  public CapturedGauge getGauge (@NonNull final String sName)
  {
    return m_aRWLock.readLockedGet (() -> m_aGauges.get (sName));
  }

  /**
   * @param sName
   *        The instrument name. Never <code>null</code>.
   * @return The current value of the gauge with that name, or {@code 0} if no such gauge was ever
   *         created.
   */
  public long getGaugeValue (@NonNull final String sName)
  {
    final CapturedGauge aGauge = getGauge (sName);
    return aGauge == null ? 0L : aGauge.getValue ();
  }

  /**
   * Drop all captured spans and measurements, and reset all counters to zero and all histograms to
   * empty. Convenient between the test methods of a single JUnit class.
   * <p>
   * Instrument <em>registrations</em> are deliberately kept, and the existing aggregate objects are
   * reset in place rather than discarded: production code typically resolves its instruments once
   * in a static initializer, so an instrument handed out before the reset keeps writing to the very
   * object this method clears. Gauge registrations are kept for the same reason.
   */
  public void reset ()
  {
    m_aRWLock.writeLocked (() -> {
      m_aSpans.clear ();
      m_aMeasurements.clear ();
      for (final AtomicLong aAggregate : m_aCounters.values ())
        aAggregate.set (0);
      for (final ICommonsList <Double> aValues : m_aHistograms.values ())
        aValues.clear ();
    });
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("Spans", m_aSpans)
                                       .append ("Measurements", m_aMeasurements)
                                       .append ("Counters", m_aCounters)
                                       .append ("Histograms", m_aHistograms)
                                       .append ("Gauges", m_aGauges)
                                       .getToString ();
  }
}
