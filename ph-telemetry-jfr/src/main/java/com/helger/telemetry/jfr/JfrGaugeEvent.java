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

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;
import jdk.jfr.StackTrace;

/**
 * JFR event carrying one sample of an observable gauge. Emitted by a periodic hook that polls the
 * registered {@link java.util.function.LongSupplier}. Gauges carry no attributes — the abstraction
 * does not offer them for this instrument type.
 *
 * @author Philip Helger
 */
@Name (CJfrTelemetry.EVENT_GAUGE)
@Label ("Telemetry Gauge")
@Description ("One sample of a ph-telemetry observable gauge")
@Category ({ CJfrTelemetry.CATEGORY, CJfrTelemetry.CATEGORY_METRICS })
@Period (CJfrTelemetry.DEFAULT_PERIOD)
@StackTrace (false)
final class JfrGaugeEvent extends Event
{
  @Label ("Name")
  @Name ("name")
  String m_sName;

  @Label ("Unit")
  @Name ("unit")
  String m_sUnit;

  @Label ("Value")
  @Name ("value")
  long m_nValue;
}
