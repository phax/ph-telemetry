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

import org.jspecify.annotations.NonNull;

import com.helger.telemetry.TelemetryAttributes;

/**
 * Converts a {@link TelemetryAttributes} set into the single flat <code>String</code> that the JFR
 * events of this binding carry in their <code>attributes</code> field.
 * <p>
 * A JFR event has a fixed schema and its fields are limited to the primitive types plus
 * {@link String}, {@link Thread} and {@link Class} — there is no map-valued field type. Dynamic
 * attributes are therefore flattened into <code>key=value</code> pairs separated by <code>;</code>,
 * in the insertion order of the attribute set. The characters <code>\</code>, <code>=</code> and
 * <code>;</code> are escaped with a leading backslash inside both keys and values, so the result
 * can be parsed back unambiguously.
 * <p>
 * The declared value type is lost in the process: a long <code>1</code>, a double <code>1.0</code>
 * and the string <code>"1"</code> are all rendered as their {@link String} form. Backends that need
 * typed dimensions (an OpenTelemetry exporter, for example) should run alongside this binding via
 * {@code CompositeTelemetryMeterSPI} rather than replace it.
 *
 * @author Philip Helger
 */
final class JfrAttributesConverter
{
  private JfrAttributesConverter ()
  {}

  private static void _appendEscaped (@NonNull final StringBuilder aSB, @NonNull final String sValue)
  {
    final int nLen = sValue.length ();
    for (int i = 0; i < nLen; ++i)
    {
      final char c = sValue.charAt (i);
      if (c == '\\' || c == '=' || c == ';')
        aSB.append ('\\');
      aSB.append (c);
    }
  }

  /**
   * Append a single escaped <code>key=value</code> pair to the provided buffer, prefixing it with
   * the entry separator if the buffer is not empty yet.
   *
   * @param aSB
   *        The target buffer. Never <code>null</code>.
   * @param sKey
   *        The attribute key. Never <code>null</code>.
   * @param sValue
   *        The already stringified attribute value. Never <code>null</code>.
   */
  static void appendEntry (@NonNull final StringBuilder aSB, @NonNull final String sKey, @NonNull final String sValue)
  {
    if (aSB.length () > 0)
      aSB.append (';');
    _appendEscaped (aSB, sKey);
    aSB.append ('=');
    _appendEscaped (aSB, sValue);
  }

  /**
   * Flatten the provided attributes to a single String.
   *
   * @param aAttributes
   *        The attributes to flatten. Never <code>null</code>.
   * @return The flattened representation, or the empty String if no attributes are present. Never
   *         <code>null</code>.
   */
  @NonNull
  static String toFlatString (@NonNull final TelemetryAttributes aAttributes)
  {
    if (aAttributes.isEmpty ())
      return "";

    final StringBuilder aSB = new StringBuilder ();
    aAttributes.forEach (new TelemetryAttributes.IVisitor ()
    {
      public void onString (@NonNull final String sKey, @NonNull final String sValue)
      {
        appendEntry (aSB, sKey, sValue);
      }

      public void onLong (@NonNull final String sKey, final long nValue)
      {
        appendEntry (aSB, sKey, Long.toString (nValue));
      }

      public void onDouble (@NonNull final String sKey, final double dValue)
      {
        appendEntry (aSB, sKey, Double.toString (dValue));
      }

      public void onBoolean (@NonNull final String sKey, final boolean bValue)
      {
        appendEntry (aSB, sKey, Boolean.toString (bValue));
      }
    });
    return aSB.toString ();
  }
}
