/*
 * Copyright 2016 The Android Open Source Project
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
package com.example.android.sampletvinput;

import android.net.Uri;
import android.support.annotation.VisibleForTesting;

import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.sync.EpgSyncJobService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic concrete class that returns non-null values for test purposes
 */
public class TestJobService extends EpgSyncJobService {
    @Override
    public List<Channel> getChannels() {
        // Wrap our list in an ArrayList so we will be able to make modifications if necessary
        return new ArrayList<>(Collections.singletonList(new Channel.Builder()
                .setOriginalNetworkId(0)
                .setDisplayName("Test Channel")
                .setDisplayNumber("1")
                .setIsRepeatable(true)
                .build()));
    }

    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel,
            long startMs, long endMs) {
        return new ArrayList<>(Collections.singletonList(new Program.Builder()
                .setTitle("Test Program")
                .setChannelId(channel.getId())
                .setStartTimeUtcMillis(0)
                .setEndTimeUtcMillis(1000 * 60 * 60) // One hour long
                .build()));
    }

    /**
     * Generates an EpgSyncTask for testing.
     */
    @VisibleForTesting
    public TestEpgSyncTask getDefaultEpgSyncTask() {
        return new TestEpgSyncTask();
    }

    public class TestEpgSyncTask extends EpgSyncTask {

        protected TestEpgSyncTask() {
            super(null);
        }
    }
}