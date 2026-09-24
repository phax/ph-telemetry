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

import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.ContextPropagators;

/**
 * An {@link OpenTelemetry} instance for tests that records the instrumentation scope names it was
 * asked for. Everything it hands out is the official no-op implementation - the point is not what a
 * span or an instrument does, but whether this instance was consulted at all.
 *
 * @author Philip Helger
 */
final class MockOpenTelemetry implements OpenTelemetry
{
  private final ICommonsList <String> m_aTracerScopeNames = new CommonsArrayList <> ();
  private final ICommonsList <String> m_aMeterScopeNames = new CommonsArrayList <> ();

  @NonNull
  public TracerProvider getTracerProvider ()
  {
    return new TracerProvider ()
    {
      public Tracer get (final String sScopeName)
      {
        return TracerProvider.noop ().get (sScopeName);
      }

      public Tracer get (final String sScopeName, final String sScopeVersion)
      {
        return TracerProvider.noop ().get (sScopeName, sScopeVersion);
      }

      @Override
      public TracerBuilder tracerBuilder (final String sScopeName)
      {
        m_aTracerScopeNames.add (sScopeName);
        return TracerProvider.noop ().tracerBuilder (sScopeName);
      }
    };
  }

  @NonNull
  public MeterProvider getMeterProvider ()
  {
    return sScopeName -> {
      m_aMeterScopeNames.add (sScopeName);
      return MeterProvider.noop ().meterBuilder (sScopeName);
    };
  }

  @NonNull
  public ContextPropagators getPropagators ()
  {
    return ContextPropagators.noop ();
  }

  /**
   * @return All instrumentation scope names a tracer was requested for, in order. Never
   *         <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllTracerScopeNames ()
  {
    return m_aTracerScopeNames.getClone ();
  }

  /**
   * @return All instrumentation scope names a meter was requested for, in order. Never
   *         <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public ICommonsList <String> getAllMeterScopeNames ()
  {
    return m_aMeterScopeNames.getClone ();
  }
}
