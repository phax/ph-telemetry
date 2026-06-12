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

import com.helger.telemetry.ITelemetryHistogram;
import com.helger.telemetry.TelemetryAttributes;

import io.opentelemetry.api.metrics.DoubleHistogram;

/**
 * {@link ITelemetryHistogram} backed by an OpenTelemetry {@link DoubleHistogram}.
 *
 * @author Philip Helger
 */
final class OtelTelemetryHistogram implements ITelemetryHistogram
{
  private final DoubleHistogram m_aHistogram;

  OtelTelemetryHistogram (@NonNull final DoubleHistogram aHistogram)
  {
    m_aHistogram = aHistogram;
  }

  public void record (final double dValue, @NonNull final TelemetryAttributes aAttributes)
  {
    if (aAttributes.isEmpty ())
      m_aHistogram.record (dValue);
    else
      m_aHistogram.record (dValue, OtelAttributesConverter.toOtelAttributes (aAttributes));
  }
}
