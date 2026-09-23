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
 * JFR event carrying the current value of an up-down counter, one event per distinct attribute set.
 * Emitted by a periodic hook. Unlike {@link JfrCounterEvent} the value may decrease again, so a
 * single sample is meaningful on its own.
 *
 * @author Philip Helger
 */
@Name (CJfrTelemetry.EVENT_UP_DOWN_COUNTER)
@Label ("Telemetry Up-Down Counter")
@Description ("Current value of a ph-telemetry up-down counter")
@Category ({ CJfrTelemetry.CATEGORY, CJfrTelemetry.CATEGORY_METRICS })
@Period (CJfrTelemetry.DEFAULT_PERIOD)
@StackTrace (false)
final class JfrUpDownCounterEvent extends Event
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
  long m_nValue;
}
