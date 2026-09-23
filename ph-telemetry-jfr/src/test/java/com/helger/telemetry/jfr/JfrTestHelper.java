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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/**
 * Small helper to run a JFR recording to a temporary file and read the events back.
 *
 * @author Philip Helger
 */
final class JfrTestHelper
{
  private JfrTestHelper ()
  {}

  /**
   * Stop the provided recording, dump it to a temporary file and read all contained events.
   *
   * @param aRecording
   *        The recording to evaluate. Never <code>null</code>.
   * @return All recorded events. Never <code>null</code>.
   * @throws IOException
   *         On IO error
   */
  @NonNull
  static List <RecordedEvent> stopAndReadEvents (@NonNull final Recording aRecording) throws IOException
  {
    aRecording.stop ();
    final Path aPath = Files.createTempFile ("ph-telemetry-jfr-test", ".jfr");
    try
    {
      aRecording.dump (aPath);
      return RecordingFile.readAllEvents (aPath);
    }
    finally
    {
      Files.deleteIfExists (aPath);
    }
  }

  /**
   * Find the first event of the provided type whose <code>name</code> field matches.
   *
   * @param aEvents
   *        All events to search. Never <code>null</code>.
   * @param sEventType
   *        The JFR event type name. Never <code>null</code>.
   * @param sName
   *        The expected value of the <code>name</code> field. Never <code>null</code>.
   * @return <code>null</code> if no such event was recorded.
   */
  @Nullable
  static RecordedEvent findNamed (@NonNull final List <RecordedEvent> aEvents,
                                  @NonNull final String sEventType,
                                  @NonNull final String sName)
  {
    for (final RecordedEvent aEvent : aEvents)
      if (aEvent.getEventType ().getName ().equals (sEventType) && sName.equals (aEvent.getString ("name")))
        return aEvent;
    return null;
  }

  /**
   * Find the first event of the provided type.
   *
   * @param aEvents
   *        All events to search. Never <code>null</code>.
   * @param sEventType
   *        The JFR event type name. Never <code>null</code>.
   * @return <code>null</code> if no such event was recorded.
   */
  @Nullable
  static RecordedEvent findFirst (@NonNull final List <RecordedEvent> aEvents, @NonNull final String sEventType)
  {
    for (final RecordedEvent aEvent : aEvents)
      if (aEvent.getEventType ().getName ().equals (sEventType))
        return aEvent;
    return null;
  }

  /**
   * Count the events of the provided type.
   *
   * @param aEvents
   *        All events to search. Never <code>null</code>.
   * @param sEventType
   *        The JFR event type name. Never <code>null</code>.
   * @return The number of matching events. Always &ge; 0.
   */
  static int count (@NonNull final List <RecordedEvent> aEvents, @NonNull final String sEventType)
  {
    int ret = 0;
    for (final RecordedEvent aEvent : aEvents)
      if (aEvent.getEventType ().getName ().equals (sEventType))
        ret++;
    return ret;
  }
}
