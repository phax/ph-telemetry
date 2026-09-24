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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.helger.telemetry.ITelemetryCounter;

import io.opentelemetry.api.GlobalOpenTelemetry;

/**
 * Test class for class {@link OtelTelemetryMeterSPI}, specifically for how it resolves the meter
 * from {@link GlobalOpenTelemetry}. Manipulates the JVM global {@code OpenTelemetry} instance, so
 * every test starts and ends with it reset.
 *
 * @author Philip Helger
 */
public final class OtelTelemetryMeterSPITest
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
  public void testCreatingAnInstrumentDoesNotClaimTheGlobalInstance ()
  {
    assertFalse (GlobalOpenTelemetry.isSet ());

    final OtelTelemetryMeterSPI aSPI = new OtelTelemetryMeterSPI (SCOPE_NAME, "1.0");
    final ITelemetryCounter aCounter = aSPI.createCounter ("early", "Created before the bootstrap", null);
    assertNotNull (aCounter);
    aCounter.add (1);

    // The point of "getOrNoop ()": an instrument created before the SDK bootstrap must not register
    // the no-op instance globally, because that makes every later GlobalOpenTelemetry.set (...)
    // throw
    assertFalse (GlobalOpenTelemetry.isSet ());
  }

  @Test
  public void testMeterIsReResolvedOnceTheGlobalInstanceIsSet ()
  {
    final OtelTelemetryMeterSPI aSPI = new OtelTelemetryMeterSPI (SCOPE_NAME, "1.0");

    // Resolves the no-op meter, because nothing is registered yet
    assertNotNull (aSPI.createCounter ("early", null, null));

    final MockOpenTelemetry aOtel = new MockOpenTelemetry ();
    assertTrue (aOtel.getAllMeterScopeNames ().isEmpty ());
    GlobalOpenTelemetry.set (aOtel);

    // The no-op meter of the first instrument must not be cached - otherwise every instrument of
    // this JVM would stay a no-op, even though an instance was registered in the meantime
    assertNotNull (aSPI.createCounter ("late", null, null));
    assertEquals (1, aOtel.getAllMeterScopeNames ().size ());
    assertEquals (SCOPE_NAME, aOtel.getAllMeterScopeNames ().get (0));

    // From now on the meter is stable and must not be resolved again
    assertNotNull (aSPI.createHistogram ("later", null, "ms"));
    assertEquals (1, aOtel.getAllMeterScopeNames ().size ());
  }
}
