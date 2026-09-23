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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Test;

import com.helger.telemetry.ITelemetryCounter;
import com.helger.telemetry.ITelemetryGauge;
import com.helger.telemetry.TelemetryAttributes;
import com.helger.telemetry.TelemetryMetrics;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

/**
 * Test class for class {@link JfrTelemetryMeterSPI}. Runs a real Flight Recorder recording and
 * reads the emitted events back.
 *
 * @author Philip Helger
 */
public final class JfrTelemetryMeterSPITest
{
  private JfrTelemetryMeterSPI m_aMeter;

  @After
  public void afterTest ()
  {
    TelemetryMetrics.install (null);
    if (m_aMeter != null)
    {
      m_aMeter.shutdown ();
      m_aMeter = null;
    }
  }

  @SuppressWarnings ("resource")
  @Test
  public void testAllInstruments () throws Exception
  {
    m_aMeter = new JfrTelemetryMeterSPI ();
    assertTrue (m_aMeter.isPeriodicRegistered ());
    TelemetryMetrics.install (m_aMeter);

    final AtomicLong aGaugeSource = new AtomicLong (7);
    final List <RecordedEvent> aEvents;
    final ITelemetryGauge aGauge;
    try (final Recording aRecording = new Recording ())
    {
      // "endChunk" makes the periodic hooks fire exactly once, when the recording is stopped
      aRecording.enable (CJfrTelemetry.EVENT_COUNTER).with ("period", "endChunk");
      aRecording.enable (CJfrTelemetry.EVENT_UP_DOWN_COUNTER).with ("period", "endChunk");
      aRecording.enable (CJfrTelemetry.EVENT_GAUGE).with ("period", "endChunk");
      aRecording.enable (CJfrTelemetry.EVENT_HISTOGRAM);
      aRecording.start ();

      final TelemetryAttributes aAttrs = TelemetryAttributes.builder ().put ("route", "/a").build ();
      TelemetryMetrics.counter ("test.counter", "A counter", "{x}").add (5, aAttrs);
      TelemetryMetrics.counter ("test.counter", "A counter", "{x}").add (4, aAttrs);
      TelemetryMetrics.upDownCounter ("test.updown", "An up-down counter", "{x}").add (3);
      TelemetryMetrics.upDownCounter ("test.updown", "An up-down counter", "{x}").add (-10);
      TelemetryMetrics.histogram ("test.histogram", "A histogram", "ms").record (12.5, aAttrs);
      aGauge = TelemetryMetrics.gauge ("test.gauge", "A gauge", "1", aGaugeSource::get);

      aEvents = JfrTestHelper.stopAndReadEvents (aRecording);
    }

    final RecordedEvent aCounter = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_COUNTER, "test.counter");
    assertNotNull (aCounter);
    assertEquals (9, aCounter.getLong ("value"));
    assertEquals ("{x}", aCounter.getString ("unit"));
    assertEquals ("route=/a", aCounter.getString ("attributes"));

    final RecordedEvent aUpDown = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_UP_DOWN_COUNTER, "test.updown");
    assertNotNull (aUpDown);
    assertEquals (-7, aUpDown.getLong ("value"));
    assertEquals ("", aUpDown.getString ("attributes"));

    final RecordedEvent aHistogram = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_HISTOGRAM, "test.histogram");
    assertNotNull (aHistogram);
    assertEquals (12.5, aHistogram.getDouble ("value"), 0.0);
    assertEquals ("ms", aHistogram.getString ("unit"));
    assertEquals ("route=/a", aHistogram.getString ("attributes"));

    final RecordedEvent aGaugeEvent = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_GAUGE, "test.gauge");
    assertNotNull (aGaugeEvent);
    assertEquals (7, aGaugeEvent.getLong ("value"));

    aGauge.close ();
  }

  @Test
  public void testInstrumentsAreCached ()
  {
    m_aMeter = new JfrTelemetryMeterSPI ();
    final ITelemetryCounter aFirst = m_aMeter.createCounter ("cached", "Desc", "{x}");
    assertSame (aFirst, m_aMeter.createCounter ("cached", "Desc", "{x}"));
    // A different description is a different instrument
    assertTrue (aFirst != m_aMeter.createCounter ("cached", "Other", "{x}"));
  }

  @Test
  public void testClosedGaugeIsNoLongerPolled () throws Exception
  {
    m_aMeter = new JfrTelemetryMeterSPI ();

    final AtomicLong aPollCount = new AtomicLong (0);
    final List <RecordedEvent> aEvents;
    try (final Recording aRecording = new Recording ())
    {
      aRecording.enable (CJfrTelemetry.EVENT_GAUGE).with ("period", "endChunk");
      aRecording.start ();

      @SuppressWarnings ("resource")
      final ITelemetryGauge aGauge = m_aMeter.createGauge ("closed.gauge", null, null, aPollCount::incrementAndGet);
      aGauge.close ();
      // Idempotent
      aGauge.close ();

      aEvents = JfrTestHelper.stopAndReadEvents (aRecording);
    }

    assertEquals (0, aPollCount.get ());
    assertFalse (JfrTestHelper.count (aEvents, CJfrTelemetry.EVENT_GAUGE) > 0);
  }

  @Test
  public void testShutdownRemovesPeriodicHooks ()
  {
    m_aMeter = new JfrTelemetryMeterSPI ();
    assertTrue (m_aMeter.isPeriodicRegistered ());
    m_aMeter.shutdown ();
    assertFalse (m_aMeter.isPeriodicRegistered ());
    // Idempotent
    m_aMeter.shutdown ();
    assertFalse (m_aMeter.isPeriodicRegistered ());
  }
}
