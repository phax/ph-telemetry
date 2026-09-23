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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.After;
import org.junit.Test;

import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.ITelemetrySpan;
import com.helger.telemetry.Telemetry;
import com.helger.telemetry.TelemetryAttributes;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;

/**
 * Test class for class {@link JfrTelemetryTracerSPI}. Runs a real Flight Recorder recording and
 * reads the emitted events back.
 *
 * @author Philip Helger
 */
public final class JfrTelemetryTracerSPITest
{
  @After
  public void afterTest ()
  {
    Telemetry.install (null);
  }

  @Test
  public void testSpanHierarchy () throws Exception
  {
    // Make sure the event types exist before the recording configures them by name
    FlightRecorder.register (JfrSpanEvent.class);
    FlightRecorder.register (JfrSpanMarkerEvent.class);
    FlightRecorder.register (JfrSpanExceptionEvent.class);

    final List <RecordedEvent> aEvents;
    try (final Recording aRecording = new Recording ())
    {
      aRecording.enable (CJfrTelemetry.EVENT_SPAN);
      aRecording.enable (CJfrTelemetry.EVENT_SPAN_MARKER);
      aRecording.enable (CJfrTelemetry.EVENT_SPAN_EXCEPTION);
      aRecording.start ();

      Telemetry.install (new JfrTelemetryTracerSPI ());
      try (final ITelemetrySpan aOuter = Telemetry.startSpan ("outer", ETelemetrySpanKind.SERVER))
      {
        aOuter.setAttribute ("s", "v").setAttribute ("n", 42L);
        try (final ITelemetrySpan aInner = Telemetry.startSpan ("inner", ETelemetrySpanKind.CLIENT))
        {
          aInner.addEvent ("marker", TelemetryAttributes.builder ().put ("x", true).build ());
          aInner.recordException (new IllegalStateException ("boom"));
          aInner.setStatusError ("bad");
        }
        aOuter.setStatusOk ();
      }

      aEvents = JfrTestHelper.stopAndReadEvents (aRecording);
    }

    assertEquals (2, JfrTestHelper.count (aEvents, CJfrTelemetry.EVENT_SPAN));

    final RecordedEvent aOuterEvent = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_SPAN, "outer");
    assertNotNull (aOuterEvent);
    assertEquals ("SERVER", aOuterEvent.getString ("kind"));
    assertEquals (CJfrTelemetry.STATUS_OK, aOuterEvent.getString ("status"));
    assertNull (aOuterEvent.getString ("statusMessage"));
    assertNull (aOuterEvent.getString ("parentSpanID"));
    assertEquals ("s=v;n=42", aOuterEvent.getString ("attributes"));
    assertEquals (32, aOuterEvent.getString ("traceID").length ());
    assertEquals (16, aOuterEvent.getString ("spanID").length ());
    assertTrue (aOuterEvent.getDuration ().toNanos () > 0);

    final RecordedEvent aInnerEvent = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_SPAN, "inner");
    assertNotNull (aInnerEvent);
    assertEquals ("CLIENT", aInnerEvent.getString ("kind"));
    assertEquals (CJfrTelemetry.STATUS_ERROR, aInnerEvent.getString ("status"));
    assertEquals ("bad", aInnerEvent.getString ("statusMessage"));
    assertEquals ("", aInnerEvent.getString ("attributes"));
    // Nesting on the same thread is linked up
    assertEquals (aOuterEvent.getString ("traceID"), aInnerEvent.getString ("traceID"));
    assertEquals (aOuterEvent.getString ("spanID"), aInnerEvent.getString ("parentSpanID"));

    final RecordedEvent aMarker = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_SPAN_MARKER, "marker");
    assertNotNull (aMarker);
    assertEquals (aInnerEvent.getString ("spanID"), aMarker.getString ("spanID"));
    assertEquals (aInnerEvent.getString ("traceID"), aMarker.getString ("traceID"));
    assertEquals ("x=true", aMarker.getString ("attributes"));

    final RecordedEvent aException = JfrTestHelper.findFirst (aEvents, CJfrTelemetry.EVENT_SPAN_EXCEPTION);
    assertNotNull (aException);
    assertEquals (aInnerEvent.getString ("spanID"), aException.getString ("spanID"));
    assertEquals (IllegalStateException.class.getName (), aException.getString ("exceptionClass"));
    assertEquals ("boom", aException.getString ("message"));
  }

  @Test
  public void testSiblingSpansShareNoParent () throws Exception
  {
    FlightRecorder.register (JfrSpanEvent.class);

    final List <RecordedEvent> aEvents;
    try (final Recording aRecording = new Recording ())
    {
      aRecording.enable (CJfrTelemetry.EVENT_SPAN);
      aRecording.start ();

      final JfrTelemetryTracerSPI aTracer = new JfrTelemetryTracerSPI ();
      aTracer.startSpan ("first", ETelemetrySpanKind.INTERNAL).close ();
      aTracer.startSpan ("second", ETelemetrySpanKind.INTERNAL).close ();

      aEvents = JfrTestHelper.stopAndReadEvents (aRecording);
    }

    final RecordedEvent aFirst = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_SPAN, "first");
    final RecordedEvent aSecond = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_SPAN, "second");
    assertNotNull (aFirst);
    assertNotNull (aSecond);
    assertNull (aFirst.getString ("parentSpanID"));
    assertNull (aSecond.getString ("parentSpanID"));
    // Both are root spans, so they get their own trace
    assertTrue (!aFirst.getString ("traceID").equals (aSecond.getString ("traceID")));
  }

  @Test
  public void testExternalIDsAreAdopted () throws Exception
  {
    FlightRecorder.register (JfrSpanEvent.class);

    final String sTraceID = "0123456789abcdef0123456789abcdef";
    final String sSpanID = "fedcba9876543210";

    final List <RecordedEvent> aEvents;
    try (final Recording aRecording = new Recording ())
    {
      aRecording.enable (CJfrTelemetry.EVENT_SPAN);
      aRecording.start ();

      final JfrTelemetryTracerSPI aTracer = new JfrTelemetryTracerSPI ()
      {
        @Override
        protected String getExternalTraceID ()
        {
          return sTraceID;
        }

        @Override
        protected String getExternalSpanID ()
        {
          return sSpanID;
        }
      };
      aTracer.startSpan ("external", ETelemetrySpanKind.PRODUCER).close ();

      aEvents = JfrTestHelper.stopAndReadEvents (aRecording);
    }

    final RecordedEvent aEvent = JfrTestHelper.findNamed (aEvents, CJfrTelemetry.EVENT_SPAN, "external");
    assertNotNull (aEvent);
    assertEquals (sTraceID, aEvent.getString ("traceID"));
    assertEquals (sSpanID, aEvent.getString ("spanID"));
  }

  @Test
  public void testThresholdDropsShortSpans () throws Exception
  {
    FlightRecorder.register (JfrSpanEvent.class);

    final List <RecordedEvent> aEvents;
    try (final Recording aRecording = new Recording ())
    {
      // A threshold applies to the whole event type - no span will ever be that long
      aRecording.enable (CJfrTelemetry.EVENT_SPAN).withThreshold (Duration.ofHours (1));
      aRecording.start ();

      new JfrTelemetryTracerSPI ().startSpan ("short", ETelemetrySpanKind.INTERNAL).close ();

      aEvents = JfrTestHelper.stopAndReadEvents (aRecording);
    }

    assertEquals (0, JfrTestHelper.count (aEvents, CJfrTelemetry.EVENT_SPAN));
  }

  @Test
  public void testDisabledEventYieldsNoOpSpan ()
  {
    // No recording is active here, so the span event type is not enabled
    try (final ITelemetrySpan aSpan = new JfrTelemetryTracerSPI ().startSpan ("nope", ETelemetrySpanKind.INTERNAL))
    {
      assertSame (Telemetry.NoOpTelemetrySpan.INSTANCE, aSpan);
    }
  }
}
