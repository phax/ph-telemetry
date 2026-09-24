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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.ITelemetrySpan;

import io.opentelemetry.api.GlobalOpenTelemetry;

/**
 * Test class for class {@link OtelTelemetryTracerSPI}, specifically for how it resolves the tracer
 * from {@link GlobalOpenTelemetry}. Manipulates the JVM global {@code OpenTelemetry} instance, so
 * every test starts and ends with it reset.
 *
 * @author Philip Helger
 */
public final class OtelTelemetryTracerSPITest
{
  private static final String SCOPE_NAME = "com.helger.telemetry.otel.test";

  @Before
  public void beforeTest ()
  {
    GlobalOpenTelemetry.resetForTest ();
  }

  @After
  public void afterTest ()
  {
    GlobalOpenTelemetry.resetForTest ();
  }

  @Test
  public void testTakingASpanDoesNotClaimTheGlobalInstance ()
  {
    assertFalse (GlobalOpenTelemetry.isSet ());

    final OtelTelemetryTracerSPI aSPI = new OtelTelemetryTracerSPI (SCOPE_NAME, "1.0");
    try (final ITelemetrySpan aSpan = aSPI.startSpan ("early", ETelemetrySpanKind.INTERNAL))
    {
      aSpan.setStatusOk ();
    }

    // The point of "getOrNoop ()": a span taken before the SDK bootstrap must not register the
    // no-op instance globally, because that makes every later GlobalOpenTelemetry.set (...) throw
    assertFalse (GlobalOpenTelemetry.isSet ());
  }

  @Test
  public void testTracerIsReResolvedOnceTheGlobalInstanceIsSet ()
  {
    final OtelTelemetryTracerSPI aSPI = new OtelTelemetryTracerSPI (SCOPE_NAME, "1.0");

    // Resolves the no-op tracer, because nothing is registered yet
    aSPI.startSpan ("early", ETelemetrySpanKind.INTERNAL).close ();

    final MockOpenTelemetry aOtel = new MockOpenTelemetry ();
    assertTrue (aOtel.getAllTracerScopeNames ().isEmpty ());
    GlobalOpenTelemetry.set (aOtel);

    // The no-op tracer of the first span must not be cached - otherwise every span of this JVM
    // would stay a no-op, even though an instance was registered in the meantime
    aSPI.startSpan ("late", ETelemetrySpanKind.INTERNAL).close ();
    assertEquals (1, aOtel.getAllTracerScopeNames ().size ());
    assertEquals (SCOPE_NAME, aOtel.getAllTracerScopeNames ().get (0));

    // From now on the tracer is stable and must not be resolved again
    aSPI.startSpan ("later", ETelemetrySpanKind.INTERNAL).close ();
    assertEquals (1, aOtel.getAllTracerScopeNames ().size ());
  }
}
