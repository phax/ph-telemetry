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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjLongConsumer;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.ThreadSafe;
import com.helger.telemetry.TelemetryAttributes;

/**
 * Per-instrument accumulation of counter values, split by the flattened attribute set. JFR has no
 * metric instruments of its own — a counter is therefore summed in process and only the running
 * total is emitted, by a periodic hook.
 * <p>
 * The number of distinct attribute sets is capped at {@link #MAX_ATTRIBUTE_SETS}. Everything beyond
 * that is folded into a single bucket named {@value #OVERFLOW_KEY}, and a warning is logged once
 * per instrument — an unbounded map keyed by caller-provided attribute values would otherwise be a
 * memory leak for high cardinality dimensions.
 *
 * @author Philip Helger
 */
@ThreadSafe
final class JfrValueAccumulator
{
  /** Maximum number of distinct attribute sets tracked per instrument */
  public static final int MAX_ATTRIBUTE_SETS = 1_000;
  /** Bucket name all recordings beyond {@link #MAX_ATTRIBUTE_SETS} are folded into */
  public static final String OVERFLOW_KEY = "<overflow>";

  private static final Logger LOGGER = LoggerFactory.getLogger (JfrValueAccumulator.class);

  private final String m_sInstrumentName;
  private final ConcurrentHashMap <String, LongAdder> m_aValues = new ConcurrentHashMap <> ();
  private final AtomicBoolean m_aOverflowLogged = new AtomicBoolean (false);

  JfrValueAccumulator (@NonNull final String sInstrumentName)
  {
    m_sInstrumentName = sInstrumentName;
  }

  void add (final long nValue, @NonNull final TelemetryAttributes aAttributes)
  {
    String sKey = JfrAttributesConverter.toFlatString (aAttributes);
    LongAdder aAdder = m_aValues.get (sKey);
    if (aAdder == null)
    {
      if (m_aValues.size () >= MAX_ATTRIBUTE_SETS)
      {
        if (!m_aOverflowLogged.getAndSet (true))
          LOGGER.warn ("The JFR telemetry instrument '" +
                       m_sInstrumentName +
                       "' exceeded " +
                       MAX_ATTRIBUTE_SETS +
                       " distinct attribute sets - all further combinations are folded into '" +
                       OVERFLOW_KEY +
                       "'");
        sKey = OVERFLOW_KEY;
      }
      aAdder = m_aValues.computeIfAbsent (sKey, k -> new LongAdder ());
    }
    aAdder.add (nValue);
  }

  /**
   * Visit the current total of every tracked attribute set.
   *
   * @param aConsumer
   *        Receives the flattened attribute set and its current total. Never <code>null</code>.
   */
  void forEachValue (@NonNull final ObjLongConsumer <String> aConsumer)
  {
    for (final Map.Entry <String, LongAdder> aEntry : m_aValues.entrySet ())
      aConsumer.accept (aEntry.getKey (), aEntry.getValue ().sum ());
  }
}
