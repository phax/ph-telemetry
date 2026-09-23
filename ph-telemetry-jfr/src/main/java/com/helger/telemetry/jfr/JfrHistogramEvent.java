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
import jdk.jfr.StackTrace;

/**
 * JFR event carrying a single histogram sample. Unlike the counter and gauge events this one is not
 * periodic: every {@code record (double)} call commits its own event, because in JFR the event
 * <em>is</em> the sample and JDK Mission Control derives the distribution from the recorded
 * population.
 * <p>
 * That makes this the most volume-intensive event type of the binding. Disable it per recording
 * ({@code disable (CJfrTelemetry.EVENT_HISTOGRAM)}) when a hot path records at a high rate.
 *
 * @author Philip Helger
 */
@Name (CJfrTelemetry.EVENT_HISTOGRAM)
@Label ("Telemetry Histogram Sample")
@Description ("A single value recorded on a ph-telemetry histogram")
@Category ({ CJfrTelemetry.CATEGORY, CJfrTelemetry.CATEGORY_METRICS })
@StackTrace (false)
final class JfrHistogramEvent extends Event
{
  @Label ("Name")
  @Name ("name")
  String m_sName;

  @Label ("Unit")
  @Name ("unit")
  String m_sUnit;

  @Label ("Attributes")
  @Name ("attributes")
  String m_sAttributes;

  @Label ("Value")
  @Name ("value")
  double m_dValue;
}
