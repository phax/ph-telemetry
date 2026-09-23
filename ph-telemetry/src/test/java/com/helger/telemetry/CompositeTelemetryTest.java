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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Test;

import com.helger.telemetry.mock.CapturingTelemetry;

/**
 * Test class for class {@link CompositeTelemetryTracerSPI} and {@link CompositeTelemetryMeterSPI}.
 *
 * @author Philip Helger
 */
public final class CompositeTelemetryTest
{
  @After
  public void afterTest ()
  {
    Telemetry.install (null);
    TelemetryMetrics.install (null);
  }

  @Test
  public void testSpanFanOut ()
  {
    final CapturingTelemetry aFirst = new CapturingTelemetry ();
    final CapturingTelemetry aSecond = new CapturingTelemetry ();
    Telemetry.install (new CompositeTelemetryTracerSPI (aFirst, aSecond));

    Telemetry.withSpanVoid ("composite.span", ETelemetrySpanKind.SERVER, aSpan -> {
      aSpan.setAttribute ("s", "v");
      aSpan.setAttribute ("n", 42);
      aSpan.setAttribute ("d", 2.5);
      aSpan.setAttribute ("b", true);
      aSpan.addEvent ("marker");
      aSpan.setStatusOk ();
    });

    for (final CapturingTelemetry aTelemetry : new CapturingTelemetry [] { aFirst, aSecond })
    {
      assertEquals (1, aTelemetry.getSpanCount ());
      final CapturingTelemetry.CapturedSpan aSpan = aTelemetry.getFirstSpan ("composite.span");
      assertNotNull (aSpan);
      assertEquals (ETelemetrySpanKind.SERVER, aSpan.getKind ());
      assertEquals ("v", aSpan.getAttribute ("s"));
      assertEquals (Long.valueOf (42), aSpan.getAttribute ("n"));
      assertEquals (Double.valueOf (2.5), aSpan.getAttribute ("d"));
      assertEquals (Boolean.TRUE, aSpan.getAttribute ("b"));
      assertNotNull (aSpan.getFirstEvent ("marker"));
      assertTrue (aSpan.isStatusOk ());
      assertTrue (aSpan.isClosed ());
    }
  }

  @Test
  public void testSpanExceptionFanOut ()
  {
    final CapturingTelemetry aFirst = new CapturingTelemetry ();
    final CapturingTelemetry aSecond = new CapturingTelemetry ();
    Telemetry.install (new CompositeTelemetryTracerSPI (List.of (aFirst, aSecond)));

    final IllegalStateException aEx = new IllegalStateException ("boom");
    try
    {
      Telemetry.withSpanVoid ("composite.fail", ETelemetrySpanKind.INTERNAL, aSpan -> { throw aEx; });
      fail ();
    }
    catch (final IllegalStateException ex)
    {
      assertSame (aEx, ex);
    }

    for (final CapturingTelemetry aTelemetry : new CapturingTelemetry [] { aFirst, aSecond })
    {
      final CapturingTelemetry.CapturedSpan aSpan = aTelemetry.getFirstSpan ("composite.fail");
      assertNotNull (aSpan);
      assertSame (aEx, aSpan.getRecordedException ());
      assertTrue (aSpan.isStatusError ());
      assertEquals ("boom", aSpan.getStatusMessage ());
      assertTrue (aSpan.isClosed ());
    }
  }

  @Test
  public void testMetricsFanOut ()
  {
    final CapturingTelemetry aFirst = new CapturingTelemetry ();
    final CapturingTelemetry aSecond = new CapturingTelemetry ();
    TelemetryMetrics.install (new CompositeTelemetryMeterSPI (aFirst, aSecond));

    final TelemetryAttributes aAttrs = TelemetryAttributes.builder ().put ("route", "/a").build ();
    TelemetryMetrics.counter ("c", "Counter", "{x}").add (3, aAttrs);
    TelemetryMetrics.upDownCounter ("u", "UpDown", "{x}").add (-2, aAttrs);
    TelemetryMetrics.histogram ("h", "Histogram", "ms").record (12.5, aAttrs);

    final AtomicLong aGaugeCalls = new AtomicLong (0);
    try (final ITelemetryGauge aGauge = TelemetryMetrics.gauge ("g", "Gauge", "1", aGaugeCalls::incrementAndGet))
    {
      for (final CapturingTelemetry aTelemetry : new CapturingTelemetry [] { aFirst, aSecond })
      {
        assertEquals (3, aTelemetry.getCounterValue ("c"));
        assertEquals (-2, aTelemetry.getCounterValue ("u"));
        assertEquals (1, aTelemetry.getHistogramValues ("h").size ());
        assertEquals (Double.valueOf (12.5), aTelemetry.getHistogramValues ("h").getFirstOrNull ());
        assertEquals ("/a", aTelemetry.getFirstMeasurement ("c").getAttribute ("route"));
        assertNotNull (aTelemetry.getGauge ("g"));
        assertFalse (aTelemetry.getGauge ("g").isClosed ());
      }

      // One gauge per delegate - so the shared supplier is polled once per delegate
      aFirst.getGaugeValue ("g");
      aSecond.getGaugeValue ("g");
      assertEquals (2, aGaugeCalls.get ());
    }
    assertTrue (aFirst.getGauge ("g").isClosed ());
    assertTrue (aSecond.getGauge ("g").isClosed ());
  }

  @Test
  public void testDelegateOrderIsPreserved ()
  {
    final CapturingTelemetry aFirst = new CapturingTelemetry ();
    final CapturingTelemetry aSecond = new CapturingTelemetry ();
    final CompositeTelemetryTracerSPI aTracer = new CompositeTelemetryTracerSPI (aFirst, aSecond);
    assertEquals (List.of (aFirst, aSecond), aTracer.getAllDelegates ());

    final CompositeTelemetryMeterSPI aMeter = new CompositeTelemetryMeterSPI (aSecond, aFirst);
    assertEquals (List.of (aSecond, aFirst), aMeter.getAllDelegates ());
  }

  @Test
  public void testInvalidDelegates ()
  {
    try
    {
      new CompositeTelemetryTracerSPI (new ITelemetryTracerSPI [0]);
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }

    try
    {
      new CompositeTelemetryMeterSPI (new ITelemetryMeterSPI [] { null });
      fail ();
    }
    catch (final IllegalArgumentException ex)
    {
      // expected
    }
  }
}
