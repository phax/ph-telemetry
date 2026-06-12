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

import com.helger.telemetry.ITelemetryGauge;

import io.opentelemetry.api.metrics.ObservableLongGauge;

/**
 * {@link ITelemetryGauge} backed by an OpenTelemetry {@link ObservableLongGauge}.
 *
 * @author Philip Helger
 */
final class OtelTelemetryGauge implements ITelemetryGauge
{
  private final ObservableLongGauge m_aGauge;

  OtelTelemetryGauge (@NonNull final ObservableLongGauge aGauge)
  {
    m_aGauge = aGauge;
  }

  public void close ()
  {
    m_aGauge.close ();
  }
}
