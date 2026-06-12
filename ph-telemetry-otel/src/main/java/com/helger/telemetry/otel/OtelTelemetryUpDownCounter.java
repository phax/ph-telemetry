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

import com.helger.telemetry.ITelemetryUpDownCounter;
import com.helger.telemetry.TelemetryAttributes;

import io.opentelemetry.api.metrics.LongUpDownCounter;

/**
 * {@link ITelemetryUpDownCounter} backed by an OpenTelemetry {@link LongUpDownCounter}.
 *
 * @author Philip Helger
 */
final class OtelTelemetryUpDownCounter implements ITelemetryUpDownCounter
{
  private final LongUpDownCounter m_aCounter;

  OtelTelemetryUpDownCounter (@NonNull final LongUpDownCounter aCounter)
  {
    m_aCounter = aCounter;
  }

  public void add (final long nValue, @NonNull final TelemetryAttributes aAttributes)
  {
    if (aAttributes.isEmpty ())
      m_aCounter.add (nValue);
    else
      m_aCounter.add (nValue, OtelAttributesConverter.toOtelAttributes (aAttributes));
  }
}
