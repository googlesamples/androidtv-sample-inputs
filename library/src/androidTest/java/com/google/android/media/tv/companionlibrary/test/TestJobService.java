/*
 * Copyright 2016 The Android Open Source Project.
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
package com.google.android.media.tv.companionlibrary.test;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.VisibleForTesting;
import com.google.android.media.tv.companionlibrary.ads.EpgSyncWithAdsJobService;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import com.google.android.media.tv.companionlibrary.xmltv.XmlTvParser;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;

/** Generic concrete class that returns non-null values for test purposes */
public class TestJobService extends EpgSyncWithAdsJobService {
    // For testing purposes we use the test activity context
    public static Context mContext;
    public static final String GMAIL_BLUE_VIDEO_URL = "assets://introducing_gmail_blue.mp4";

    @Override
    public List<Channel> getChannels() {
        Assert.assertNotNull("Please set the static mContext before running", mContext);
        // Wrap our list in an ArrayList so we will be able to make modifications if necessary
        Assert.assertNotNull("Set TestJobService.mContext.", mContext);
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setRepeatable(true);
        ArrayList<Channel> testChannels = new ArrayList<>();
        testChannels.add(
                new Channel.Builder()
                        .setOriginalNetworkId(0)
                        .setDisplayName("Test Channel")
                        .setDisplayNumber("1")
                        .setInternalProviderData(internalProviderData)
                        .build());

        // Add an XML parsed channel
        Uri xmlUri =
                Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.xmltv)
                        .normalizeScheme();
        try {
            InputStream inputStream = mContext.getContentResolver().openInputStream(xmlUri);
            Assert.assertNotNull(inputStream);
            testChannels.addAll(XmlTvParser.parse(inputStream).getChannels());
        } catch (FileNotFoundException | XmlTvParser.XmlTvParseException e) {
            throw new RuntimeException(
                    "Exception found of type "
                            + e.getClass().getCanonicalName()
                            + ": "
                            + e.getMessage());
        }

        return testChannels;
    }

    @Override
    public List<Program> getOriginalProgramsForChannel(
            Uri channelUri, Channel channel, long startMs, long endMs) {
        ArrayList<Program> testPrograms = new ArrayList<>();

        if (channel.getOriginalNetworkId() == 0) {
            // Generate a program
            InternalProviderData internalProviderData = new InternalProviderData();
            internalProviderData.setVideoType(TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE);
            internalProviderData.setVideoUrl(GMAIL_BLUE_VIDEO_URL);
            testPrograms =
                    new ArrayList<>(
                            Collections.singletonList(
                                    new Program.Builder()
                                            .setTitle("Test Program")
                                            .setChannelId(channel.getId())
                                            .setInternalProviderData(internalProviderData)
                                            .setStartTimeUtcMillis(0)
                                            .setEndTimeUtcMillis(1000 * 60 * 15) // 15 Minutes long
                                            .build()));
        } else if (channel.getOriginalNetworkId()
                == "com.example.android.sampletvinput.2-1".hashCode()) {
            // Obtain programs from Xml
            Uri xmlUri =
                    Uri.parse("android.resource://" + mContext.getPackageName() + "/" + R.raw.xmltv)
                            .normalizeScheme();
            try {
                InputStream inputStream = mContext.getContentResolver().openInputStream(xmlUri);
                Assert.assertNotNull(inputStream);
                XmlTvParser.TvListing listing = XmlTvParser.parse(inputStream);
                List<Program> programList = listing.getPrograms(channel);
                testPrograms.addAll(programList);
            } catch (FileNotFoundException | XmlTvParser.XmlTvParseException e) {
                throw new RuntimeException(
                        "Exception found of type "
                                + e.getClass().getCanonicalName()
                                + ": "
                                + e.getMessage());
            }
        }
        // Create some delay to test longer syncing
        try {
            CountDownLatch delayLatch = new CountDownLatch(1);
            Assert.assertFalse(delayLatch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
        return testPrograms;
    }

    /** Generates an EpgSyncTask for testing. */
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
