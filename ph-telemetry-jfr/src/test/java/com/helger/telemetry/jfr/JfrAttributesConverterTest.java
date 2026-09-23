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

import org.junit.Test;

import com.helger.telemetry.TelemetryAttributes;

/**
 * Test class for class {@link JfrAttributesConverter}.
 *
 * @author Philip Helger
 */
public final class JfrAttributesConverterTest
{
  @Test
  public void testEmpty ()
  {
    assertEquals ("", JfrAttributesConverter.toFlatString (TelemetryAttributes.EMPTY));
  }

  @Test
  public void testAllTypesInInsertionOrder ()
  {
    final TelemetryAttributes aAttrs = TelemetryAttributes.builder ()
                                                          .put ("s", "text")
                                                          .put ("n", 42L)
                                                          .put ("d", 2.5)
                                                          .put ("b", true)
                                                          .build ();
    assertEquals ("s=text;n=42;d=2.5;b=true", JfrAttributesConverter.toFlatString (aAttrs));
  }

  @Test
  public void testNullStringValueIsDropped ()
  {
    final TelemetryAttributes aAttrs = TelemetryAttributes.builder ()
                                                          .put ("a", (String) null)
                                                          .put ("b", "x")
                                                          .build ();
    assertEquals ("b=x", JfrAttributesConverter.toFlatString (aAttrs));
  }

  @Test
  public void testEscaping ()
  {
    final TelemetryAttributes aAttrs = TelemetryAttributes.builder ()
                                                          .put ("a;b", "c=d")
                                                          .put ("back\\slash", "v")
                                                          .build ();
    assertEquals ("a\\;b=c\\=d;back\\\\slash=v", JfrAttributesConverter.toFlatString (aAttrs));
  }
}
