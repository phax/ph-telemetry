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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.ITelemetryCounter;
import com.helger.telemetry.ITelemetryGauge;
import com.helger.telemetry.ITelemetryHistogram;
import com.helger.telemetry.ITelemetrySpan;
import com.helger.telemetry.ITelemetryUpDownCounter;
import com.helger.telemetry.Telemetry;
import com.helger.telemetry.TelemetryAttributes;
import com.helger.telemetry.TelemetryMetrics;
import com.helger.telemetry.mock.CapturingTelemetry.CapturedEvent;
import com.helger.telemetry.mock.CapturingTelemetry.CapturedGauge;
import com.helger.telemetry.mock.CapturingTelemetry.CapturedMeasurement;
import com.helger.telemetry.mock.CapturingTelemetry.CapturedSpan;

/**
 * Test class for class {@link CapturingTelemetry}.
 *
 * @author Philip Helger
 */
public final class CapturingTelemetryTest
{
  private final CapturingTelemetry m_aTelemetry = new CapturingTelemetry ();

  @Before
  public void before ()
  {
    m_aTelemetry.install ();
  }

  @After
  public void after ()
  {
    CapturingTelemetry.uninstall ();
  }

  @Test
  public void testSpanIsCaptured ()
  {
    try (final ITelemetrySpan aSpan = Telemetry.startSpan ("test.span", ETelemetrySpanKind.CLIENT))
    {
      aSpan.setAttribute ("s", "v")
           .setAttribute ("b", true)
           .setAttribute ("n", 42L)
           .setAttribute ("d", 3.5)
           .setStatusOk ();
    }

    assertEquals (1, m_aTelemetry.getSpanCount ());
    assertEquals (1, m_aTelemetry.getSpanCount ("test.span"));
    assertEquals (0, m_aTelemetry.getSpanCount ("other.span"));

    final CapturedSpan aCaptured = m_aTelemetry.getFirstSpan ("test.span");
    assertNotNull (aCaptured);
    assertEquals ("test.span", aCaptured.getName ());
    assertEquals (ETelemetrySpanKind.CLIENT, aCaptured.getKind ());
    assertEquals ("v", aCaptured.getAttribute ("s"));
    assertEquals (Boolean.TRUE, aCaptured.getAttribute ("b"));
    assertEquals (Long.valueOf (42), aCaptured.getAttribute ("n"));
    assertEquals (Double.valueOf (3.5), aCaptured.getAttribute ("d"));
    assertEquals (4, aCaptured.getAttributes ().size ());
    assertTrue (aCaptured.isStatusOk ());
    assertFalse (aCaptured.isStatusError ());
    assertNull (aCaptured.getStatusMessage ());
    assertNull (aCaptured.getRecordedException ());
    assertTrue (aCaptured.isClosed ());
    assertNotNull (aCaptured.toString ());
  }

  @Test
  public void testNullStringAttributeIsDropped ()
  {
    try (final ITelemetrySpan aSpan = Telemetry.startSpan ("test.span", ETelemetrySpanKind.INTERNAL))
    {
      aSpan.setAttribute ("s", (String) null);
    }

    final CapturedSpan aCaptured = m_aTelemetry.getFirstSpan ("test.span");
    assertNotNull (aCaptured);
    assertTrue (aCaptured.getAttributes ().isEmpty ());
    assertNull (aCaptured.getAttribute ("s"));
  }

  @Test
  public void testSpanErrorAndEvents ()
  {
    final IllegalStateException aEx = new IllegalStateException ("boom");
    try (final ITelemetrySpan aSpan = Telemetry.startSpan ("test.span", ETelemetrySpanKind.SERVER))
    {
      aSpan.addEvent ("plain");
      aSpan.addEvent ("with_attrs", TelemetryAttributes.builder ().put ("k", "v").put ("n", 7L).build ());
      aSpan.recordException (aEx).setStatusError ("failed");
    }

    final CapturedSpan aCaptured = m_aTelemetry.getFirstSpan ("test.span");
    assertNotNull (aCaptured);
    assertSame (aEx, aCaptured.getRecordedException ());
    assertTrue (aCaptured.isStatusError ());
    assertEquals ("failed", aCaptured.getStatusMessage ());
    assertEquals (2, aCaptured.getEvents ().size ());

    final CapturedEvent aPlain = aCaptured.getFirstEvent ("plain");
    assertNotNull (aPlain);
    assertTrue (aPlain.getAttributes ().isEmpty ());

    final CapturedEvent aWithAttrs = aCaptured.getFirstEvent ("with_attrs");
    assertNotNull (aWithAttrs);
    assertEquals ("v", aWithAttrs.getAttribute ("k"));
    assertEquals (Long.valueOf (7), aWithAttrs.getAttribute ("n"));
    assertNull (aCaptured.getFirstEvent ("not_there"));
  }

  @Test
  public void testCounterAggregateAndMeasurements ()
  {
    final ITelemetryCounter aCounter = TelemetryMetrics.counter ("test.counter", "desc", "{op}");
    aCounter.add (3, TelemetryAttributes.builder ().put ("db", "h2").build ());
    aCounter.add (1);

    assertEquals (4, m_aTelemetry.getCounterValue ("test.counter"));
    assertEquals (0, m_aTelemetry.getCounterValue ("unknown"));

    assertEquals (2, m_aTelemetry.getMeasurements ().size ());
    assertEquals (2, m_aTelemetry.getMeasurements ("test.counter").size ());

    final CapturedMeasurement aFirst = m_aTelemetry.getFirstMeasurement ("test.counter");
    assertNotNull (aFirst);
    assertEquals ("test.counter", aFirst.getInstrumentName ());
    assertEquals (3, aFirst.getValueAsLong ());
    assertEquals ("h2", aFirst.getAttribute ("db"));
    assertNull (m_aTelemetry.getFirstMeasurement ("unknown"));
    assertNotNull (aFirst.toString ());
  }

  @Test
  public void testUpDownCounter ()
  {
    final ITelemetryUpDownCounter aUDC = TelemetryMetrics.upDownCounter ("test.udc", null, null);
    aUDC.add (5);
    aUDC.add (-2);

    assertEquals (3, m_aTelemetry.getCounterValue ("test.udc"));
    assertEquals (2, m_aTelemetry.getMeasurements ("test.udc").size ());
  }

  @Test
  public void testHistogram ()
  {
    final ITelemetryHistogram aHist = TelemetryMetrics.histogram ("test.hist", null, "s");
    aHist.record (1.5);
    aHist.record (2.5, TelemetryAttributes.builder ().put ("success", true).build ());

    assertEquals (2, m_aTelemetry.getHistogramValues ("test.hist").size ());
    assertEquals (Double.valueOf (1.5), m_aTelemetry.getHistogramValues ("test.hist").getFirstOrNull ());
    assertTrue (m_aTelemetry.getHistogramValues ("unknown").isEmpty ());

    final CapturedMeasurement aSecond = m_aTelemetry.getMeasurements ("test.hist").get (1);
    assertEquals (2.5, aSecond.getValue (), 0.001);
    assertEquals (Boolean.TRUE, aSecond.getAttribute ("success"));
  }

  @Test
  public void testGauge ()
  {
    final AtomicLong aValue = new AtomicLong (12);
    try (final ITelemetryGauge aGauge = TelemetryMetrics.gauge ("test.gauge", null, null, aValue::get))
    {
      assertNotNull (aGauge);
      assertEquals (12, m_aTelemetry.getGaugeValue ("test.gauge"));
      aValue.set (34);
      assertEquals (34, m_aTelemetry.getGaugeValue ("test.gauge"));
    }

    final CapturedGauge aCaptured = m_aTelemetry.getGauge ("test.gauge");
    assertNotNull (aCaptured);
    assertEquals ("test.gauge", aCaptured.getName ());
    assertTrue (aCaptured.isClosed ());
    assertNotNull (aCaptured.getSupplier ());
    assertEquals (0, m_aTelemetry.getGaugeValue ("unknown"));
    assertNull (m_aTelemetry.getGauge ("unknown"));
  }

  @Test
  public void testResetKeepsInstrumentsWired ()
  {
    // Simulates the usual pattern of resolving an instrument once in a static initializer
    final ITelemetryCounter aCounter = TelemetryMetrics.counter ("test.counter", null, null);
    final ITelemetryHistogram aHist = TelemetryMetrics.histogram ("test.hist", null, null);
    aCounter.add (5);
    aHist.record (1.0);
    Telemetry.startSpan ("test.span", ETelemetrySpanKind.INTERNAL).close ();

    m_aTelemetry.reset ();

    assertEquals (0, m_aTelemetry.getSpanCount ());
    assertEquals (0, m_aTelemetry.getCounterValue ("test.counter"));
    assertTrue (m_aTelemetry.getHistogramValues ("test.hist").isEmpty ());
    assertTrue (m_aTelemetry.getMeasurements ().isEmpty ());

    // The instruments handed out before the reset must still feed this instance
    aCounter.add (2);
    aHist.record (9.0);
    assertEquals (2, m_aTelemetry.getCounterValue ("test.counter"));
    assertEquals (1, m_aTelemetry.getHistogramValues ("test.hist").size ());
    assertEquals (2, m_aTelemetry.getMeasurements ().size ());
  }

  @Test
  public void testUninstallRestoresNoOp ()
  {
    CapturingTelemetry.uninstall ();

    Telemetry.startSpan ("test.span", ETelemetrySpanKind.INTERNAL).close ();
    TelemetryMetrics.counter ("test.counter", null, null).add (1);

    assertEquals (0, m_aTelemetry.getSpanCount ());
    assertEquals (0, m_aTelemetry.getCounterValue ("test.counter"));
  }
}
