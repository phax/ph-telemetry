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

import org.jspecify.annotations.NonNull;

import com.helger.telemetry.TelemetryAttributes;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

/**
 * Internal helper that converts a {@link TelemetryAttributes} snapshot into the equivalent
 * OpenTelemetry {@link Attributes} via the visitor protocol.
 *
 * @author Philip Helger
 */
final class OtelAttributesConverter
{
  private OtelAttributesConverter ()
  {}

  @NonNull
  static Attributes toOtelAttributes (@NonNull final TelemetryAttributes aAttrs)
  {
    if (aAttrs.isEmpty ())
      return Attributes.empty ();

    final AttributesBuilder aBuilder = Attributes.builder ();
    aAttrs.forEach (new TelemetryAttributes.IVisitor ()
    {
      public void onString (@NonNull final String sKey, @NonNull final String sValue)
      {
        aBuilder.put (sKey, sValue);
      }

      public void onLong (@NonNull final String sKey, final long nValue)
      {
        aBuilder.put (sKey, nValue);
      }

      public void onDouble (@NonNull final String sKey, final double dValue)
      {
        aBuilder.put (sKey, dValue);
      }

      public void onBoolean (@NonNull final String sKey, final boolean bValue)
      {
        aBuilder.put (sKey, bValue);
      }
    });
    return aBuilder.build ();
  }
}
