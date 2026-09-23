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
package com.helger.telemetry;

import java.util.List;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.Nonempty;
import com.helger.annotation.concurrent.Immutable;
import com.helger.annotation.style.ReturnsImmutableObject;
import com.helger.base.enforce.ValueEnforcer;

/**
 * An {@link ITelemetryTracerSPI} that fans every span out to a fixed list of delegate tracers. Use
 * it when more than one backend should see the same spans — e.g. an OpenTelemetry exporter for the
 * distributed trace <em>and</em> a Java Flight Recorder binding for the local recording.
 * <p>
 * {@link Telemetry} resolves the <em>first</em> registered {@link ITelemetryTracerSPI} only, so the
 * way to run several backends side by side is to register a single subclass of this class instead
 * of registering each backend on its own:
 *
 * <pre>
 * public final class MyAppTracerSPI extends CompositeTelemetryTracerSPI
 * {
 *   public MyAppTracerSPI ()
 *   {
 *     super (new MyAppOtelTracerSPI (), new JfrTelemetryTracerSPI ());
 *   }
 * }
 * </pre>
 * <p>
 * Delegates are invoked in the order they were passed in — on
 * {@link #startSpan(String, ETelemetrySpanKind)} and on every mutator, and in reverse order on
 * {@link ITelemetrySpan#close()}, so that the nesting each delegate maintains on the calling thread
 * unwinds symmetrically. The order is therefore meaningful: a delegate that wants to observe the
 * span context established by another delegate must be listed after it.
 * <p>
 * No exception handling is performed: a delegate that throws aborts the fan-out and propagates to
 * the caller. Delegates are expected to be well-behaved, exactly as a single directly registered
 * SPI would have to be.
 *
 * @author Philip Helger
 * @since 1.1.0
 */
@Immutable
public class CompositeTelemetryTracerSPI implements ITelemetryTracerSPI
{
  private final ITelemetryTracerSPI [] m_aDelegates;

  /**
   * @param aDelegates
   *        The tracers to delegate to, in invocation order. May neither be <code>null</code> nor
   *        empty and may not contain <code>null</code> elements.
   */
  public CompositeTelemetryTracerSPI (@NonNull @Nonempty final ITelemetryTracerSPI... aDelegates)
  {
    ValueEnforcer.notEmptyNoNullValue (aDelegates, "Delegates");
    m_aDelegates = aDelegates.clone ();
  }

  /**
   * @param aDelegates
   *        The tracers to delegate to, in invocation order. May neither be <code>null</code> nor
   *        empty and may not contain <code>null</code> elements.
   */
  public CompositeTelemetryTracerSPI (@NonNull @Nonempty final List <? extends ITelemetryTracerSPI> aDelegates)
  {
    ValueEnforcer.notEmptyNoNullValue (aDelegates, "Delegates");
    m_aDelegates = aDelegates.toArray (new ITelemetryTracerSPI [0]);
  }

  /**
   * @return All delegate tracers in invocation order. Never <code>null</code> nor empty.
   */
  @NonNull
  @Nonempty
  @ReturnsImmutableObject
  public final List <ITelemetryTracerSPI> getAllDelegates ()
  {
    return List.of (m_aDelegates);
  }

  @NonNull
  public ITelemetrySpan startSpan (@NonNull final String sName, @NonNull final ETelemetrySpanKind eKind)
  {
    final int nCount = m_aDelegates.length;
    final ITelemetrySpan [] aSpans = new ITelemetrySpan [nCount];
    for (int i = 0; i < nCount; ++i)
      aSpans[i] = m_aDelegates[i].startSpan (sName, eKind);
    return new CompositeTelemetrySpan (aSpans);
  }
}
