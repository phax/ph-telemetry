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

import org.jspecify.annotations.NonNull;

/**
 * A {@link java.util.function.Function} variant that takes an active {@link ITelemetrySpan} and may
 * throw a declared checked exception. Used by
 * {@link Telemetry#withSpanThrowing(String, ETelemetrySpanKind, IThrowingSpanFunction)}.
 *
 * @param <T>
 *        Return type of the body.
 * @param <E>
 *        Throwable type the body may raise.
 * @author Philip Helger
 * @since 1.0.1
 */
@FunctionalInterface
public interface IThrowingSpanFunction <T, E extends Throwable>
{
  /**
   * Execute the body with the active span.
   *
   * @param aSpan
   *        The active span. Never <code>null</code>.
   * @return The result of the body.
   * @throws E
   *         If the body throws.
   */
  T apply (@NonNull ITelemetrySpan aSpan) throws E;
}
