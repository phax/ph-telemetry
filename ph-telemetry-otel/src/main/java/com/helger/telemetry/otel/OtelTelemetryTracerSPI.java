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
 * from {@link GlobalOpenTelemetry} on first use and translates {@link ETelemetrySpanKind} to OTel
 * {@link SpanKind}.
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
 * If no {@code OpenTelemetry} instance is registered yet, every span operation through this adapter
 * becomes a cheap no-op at the SDK level — but the adapter neither claims the global slot nor
 * remembers the no-op tracer, so an SDK that is installed later takes effect. See
 * {@link #startSpan(String, ETelemetrySpanKind)} for why that matters.
 *
 * @author Philip Helger
 */
public class OtelTelemetryTracerSPI implements ITelemetryTracerSPI
{
  private final String m_sScopeName;
  private final String m_sScopeVersion;
  // Resolved from a registered OpenTelemetry instance - final, because the global one can only be
  // set once
  private volatile Tracer m_aTracer;
  // Resolved while no OpenTelemetry instance was registered yet - must be dropped as soon as one
  // is
  private volatile Tracer m_aTracerWithoutGlobal;

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
  private Tracer _buildTracer ()
  {
    // Deliberately "getOrNoop ()" and not "get ()": the latter registers the no-op instance as the
    // global one if none is set yet, and every later "GlobalOpenTelemetry.set (...)" then fails
    // with an IllegalStateException - so merely taking a span before the SDK bootstrap of the
    // application would break that bootstrap. "getOrNoop ()" is what the OpenTelemetry API
    // documents for instrumentation and has no such side effect.
    final var aBuilder = GlobalOpenTelemetry.getOrNoop ().getTracerProvider ().tracerBuilder (m_sScopeName);
    if (m_sScopeVersion != null)
      aBuilder.setInstrumentationVersion (m_sScopeVersion);
    return aBuilder.build ();
  }

  @NonNull
  private Tracer _tracer ()
  {
    // Fast path: a tracer of a registered OpenTelemetry instance can never become stale
    final Tracer aTracer = m_aTracer;
    if (aTracer != null)
      return aTracer;

    if (!GlobalOpenTelemetry.isSet ())
    {
      // Nothing registered yet, e.g. because the SDK bootstrap of the application did not run yet.
      // The tracer built here is the no-op one and is deliberately NOT remembered as the final
      // one: otherwise a single span taken too early would leave every span of this JVM a no-op,
      // even after the SDK is installed a moment later
      Tracer aRet = m_aTracerWithoutGlobal;
      if (aRet == null)
      {
        aRet = _buildTracer ();
        m_aTracerWithoutGlobal = aRet;
      }
      return aRet;
    }

    final Tracer aRet = _buildTracer ();
    m_aTracer = aRet;
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
