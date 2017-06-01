/*
 * Copyright 2017 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.media.tv.companionlibrary.ads;

import android.net.Uri;
import android.support.annotation.VisibleForTesting;
import com.google.android.media.tv.companionlibrary.model.Advertisement;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import java.util.ArrayList;
import java.util.List;

/** EpgSyncJobService that inserts ads and repeats programs */
public abstract class EpgSyncWithAdsJobService extends EpgSyncJobService {

    /**
     * Calls {@link #getOriginalProgramsForChannel(Uri, Channel, long, long)} and then repeats and
     * inserts ads as needed.
     */
    @Override
    public final List<Program> getProgramsForChannel(
            Uri channelUri, Channel channel, long startMs, long endMs) throws EpgSyncException {
        return repeatAndInsertAds(
                channel,
                getOriginalProgramsForChannel(channelUri, channel, startMs, endMs),
                startMs,
                endMs);
    }

    public abstract List<Program> getOriginalProgramsForChannel(
            Uri channelUri, Channel channel, long startMs, long endMs);

    /**
     * Repeats and ads ads to programs as needed.
     *
     * @param channel The {@link Channel} for the programs to return.
     * @param programs The original fetched from cloud.
     * @param startTimeMs The start time of the range requested.
     * @param endTimeMs The end time of the range requested.
     * @return A list of programs for the channel within the specifed range. They may be repeated.
     * @hide
     */
    @VisibleForTesting
    public static List<Program> repeatAndInsertAds(
            Channel channel, List<Program> programs, long startTimeMs, long endTimeMs) {
        if (startTimeMs > endTimeMs) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        if (programs.isEmpty()) {
            return programs;
        }
        List<Program> programForGivenTime = new ArrayList<>();
        if (channel.getInternalProviderData() != null
                && !channel.getInternalProviderData().isRepeatable()) {
            for (Program program : programs) {
                if (program.getStartTimeUtcMillis() <= endTimeMs
                        && program.getEndTimeUtcMillis() >= startTimeMs) {
                    programForGivenTime.add(
                            new Program.Builder(program).setChannelId(channel.getId()).build());
                }
            }
            return programForGivenTime;
        }

        // If repeat-programs is on, schedule the programs sequentially in a loop. To make every
        // device play the same program in a given channel and time, we assumes the loop started
        // from the epoch time.
        long totalDurationMs = 0;
        for (Program program : programs) {
            totalDurationMs += (program.getEndTimeUtcMillis() - program.getStartTimeUtcMillis());
        }
        if (totalDurationMs <= 0) {
            throw new IllegalArgumentException(
                    "The duration of all programs must be greater " + "than 0ms.");
        }

        long programStartTimeMs = startTimeMs - startTimeMs % totalDurationMs;
        int i = 0;
        final int programCount = programs.size();
        while (programStartTimeMs < endTimeMs) {
            Program programInfo = programs.get(i++ % programCount);
            long programEndTimeMs = programStartTimeMs + totalDurationMs;
            if (programInfo.getEndTimeUtcMillis() > -1
                    && programInfo.getStartTimeUtcMillis() > -1) {
                programEndTimeMs =
                        programStartTimeMs
                                + (programInfo.getEndTimeUtcMillis()
                                        - programInfo.getStartTimeUtcMillis());
            }
            if (programEndTimeMs < startTimeMs) {
                programStartTimeMs = programEndTimeMs;
                continue;
            }
            // Shift advertisement time to match current program time.
            InternalProviderData updateInternalProviderData = programInfo.getInternalProviderData();
            shiftAdsTimeWithProgram(
                    updateInternalProviderData,
                    programInfo.getStartTimeUtcMillis(),
                    programStartTimeMs);
            programForGivenTime.add(
                    new Program.Builder(programInfo)
                            .setChannelId(channel.getId())
                            .setStartTimeUtcMillis(programStartTimeMs)
                            .setEndTimeUtcMillis(programEndTimeMs)
                            .setInternalProviderData(updateInternalProviderData)
                            .build());
            programStartTimeMs = programEndTimeMs;
        }
        return programForGivenTime;
    }

    /**
     * Shift advertisement time to match program playback time. For channels with repeated program,
     * the time for current program may vary from what it was defined previously.
     *
     * @param oldProgramStartTimeMs Outdated program start time.
     * @param newProgramStartTimeMs Updated program start time.
     */
    private static void shiftAdsTimeWithProgram(
            InternalProviderData internalProviderData,
            long oldProgramStartTimeMs,
            long newProgramStartTimeMs) {
        if (internalProviderData == null) {
            return;
        }
        long timeShift = newProgramStartTimeMs - oldProgramStartTimeMs;
        List<Advertisement> oldAds = internalProviderData.getAds();
        List<Advertisement> newAds = new ArrayList<>();
        for (Advertisement oldAd : oldAds) {
            newAds.add(
                    new Advertisement.Builder(oldAd)
                            .setStartTimeUtcMillis(oldAd.getStartTimeUtcMillis() + timeShift)
                            .setStopTimeUtcMillis(oldAd.getStopTimeUtcMillis() + timeShift)
                            .build());
        }
        internalProviderData.setAds(newAds);
    }
}
