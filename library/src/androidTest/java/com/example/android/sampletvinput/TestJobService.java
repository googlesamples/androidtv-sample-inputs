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

import android.content.Context;
import android.net.Uri;
import android.support.annotation.VisibleForTesting;

import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.InternalProviderData;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.sync.EpgSyncJobService;
import com.example.android.sampletvinput.xmltv.XmlTvParser;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic concrete class that returns non-null values for test purposes
 */
public class TestJobService extends EpgSyncJobService {
    // For testing purposes we use the test activity context
    public static Context mContext;

    @Override
    public List<Channel> getChannels() {
        // Wrap our list in an ArrayList so we will be able to make modifications if necessary
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setRepeatable(true);
        ArrayList<Channel> testChannels = new ArrayList<>();
        testChannels.add(new Channel.Builder()
                .setOriginalNetworkId(0)
                .setDisplayName("Test Channel")
                .setDisplayNumber("1")
                .setInternalProviderData(internalProviderData)
                .build());

        // Add an XML parsed channel
        Uri xmlUri = Uri.parse("android.resource://" + mContext.getPackageName()
                + "/" + com.example.android.sampletvinputlib.test.R.raw.xmltv)
                .normalizeScheme();
        InputStream inputStream = null;
        try {
            inputStream = mContext.getContentResolver()
                    .openInputStream(xmlUri);
            testChannels.addAll(XmlTvParser.parse(inputStream).getChannels());
        } catch (FileNotFoundException | XmlTvParser.XmlTvParseException e) {
            throw new RuntimeException("Exception found of type " + e.getClass().getCanonicalName()
                    + ": " + e.getMessage());
        }

        return testChannels;
    }

    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel, long startMs,
            long endMs) {
        ArrayList<Program> testPrograms = new ArrayList<>();

        if (channel.getOriginalNetworkId() == 0) {
            // Generate a program
            testPrograms = new ArrayList<>(Collections.singletonList(new Program.Builder()
                    .setTitle("Test Program")
                    .setChannelId(channel.getId())
                    .setStartTimeUtcMillis(0)
                    .setEndTimeUtcMillis(1000 * 60 * 60) // One hour long
                    .build()));
        } else if (channel.getOriginalNetworkId() ==
                "com.example.android.sampletvinput.2-1".hashCode()) {
            // Obtain programs from Xml
            Uri xmlUri = Uri.parse("android.resource://" + mContext.getPackageName()
                    + "/" + com.example.android.sampletvinputlib.test.R.raw.xmltv)
                    .normalizeScheme();
            InputStream inputStream = null;
            try {
                inputStream = mContext.getContentResolver()
                        .openInputStream(xmlUri);
                XmlTvParser.TvListing listing = XmlTvParser.parse(inputStream);
                List<Program> programList = listing.getPrograms(channel);
                testPrograms.addAll(programList);
            } catch (FileNotFoundException | XmlTvParser.XmlTvParseException e) {
                throw new RuntimeException("Exception found of type " +
                        e.getClass().getCanonicalName() + ": " + e.getMessage());
            }
        }
        return testPrograms;
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