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
import org.jspecify.annotations.Nullable;

import com.helger.telemetry.ETelemetrySpanKind;
import com.helger.telemetry.ITelemetrySpan;
import com.helger.telemetry.ITelemetryTracerSPI;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;

/**
 * Generic OpenTelemetry implementation of {@link ITelemetryTracerSPI}. Resolves the {@link Tracer}
 * from {@link GlobalOpenTelemetry} on first use, caches it, and translates
 * {@link ETelemetrySpanKind} to OTel {@link SpanKind}.
 * <p>
 * Subclass with a no-arg constructor that supplies the instrumentation scope name and version, then
 * register the subclass via {@code META-INF/services/com.helger.telemetry.ITelemetryTracerSPI}:
 *
 * <pre>
 * public final class MyAppTracerSPI extends OtelTelemetryTracerSPI
 * {
 *   public MyAppTracerSPI ()
 *   {
 *     super ("com.example.myapp", MyAppVersion.BUILD_VERSION);
 *   }
 * }
 * </pre>
 * <p>
 * If the OTel SDK has not been initialised, {@link GlobalOpenTelemetry#get()} returns the official
 * no-op {@code OpenTelemetry} — every span operation through this adapter then becomes a cheap
 * no-op at the SDK level.
 *
 * @author Philip Helger
 */
public class OtelTelemetryTracerSPI implements ITelemetryTracerSPI
{
  private final String m_sScopeName;
  private final String m_sScopeVersion;
  private volatile Tracer m_aTracer;

  /**
   * @param sScopeName
   *        The OpenTelemetry instrumentation scope name. Never <code>null</code>.
   * @param sScopeVersion
   *        The OpenTelemetry instrumentation scope version. May be <code>null</code>.
   */
  protected OtelTelemetryTracerSPI (@NonNull final String sScopeName, @Nullable final String sScopeVersion)
  {
    m_sScopeName = sScopeName;
    m_sScopeVersion = sScopeVersion;
  }

  @NonNull
  private Tracer _tracer ()
  {
    Tracer aRet = m_aTracer;
    if (aRet == null)
    {
      final var aBuilder = GlobalOpenTelemetry.get ().getTracerProvider ().tracerBuilder (m_sScopeName);
      if (m_sScopeVersion != null)
        aBuilder.setInstrumentationVersion (m_sScopeVersion);
      aRet = aBuilder.build ();
      m_aTracer = aRet;
    }
    return aRet;
  }

  @NonNull
  private static SpanKind _toOtelKind (@NonNull final ETelemetrySpanKind eKind)
  {
    return switch (eKind)
    {
      case INTERNAL -> SpanKind.INTERNAL;
      case CLIENT -> SpanKind.CLIENT;
      case SERVER -> SpanKind.SERVER;
      case PRODUCER -> SpanKind.PRODUCER;
      case CONSUMER -> SpanKind.CONSUMER;
    };
  }

  @NonNull
  public ITelemetrySpan startSpan (@NonNull final String sName, @NonNull final ETelemetrySpanKind eKind)
  {
    final SpanBuilder aBuilder = _tracer ().spanBuilder (sName).setSpanKind (_toOtelKind (eKind));
    final Span aSpan = aBuilder.startSpan ();
    return new OtelTelemetrySpan (aSpan);
  }
}
