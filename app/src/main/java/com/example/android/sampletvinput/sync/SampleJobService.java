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
package com.example.android.sampletvinput.sync;

import android.content.Context;
import android.net.Uri;

import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.rich.RichFeedUtil;
import com.example.android.sampletvinput.xmltv.XmlTvParser;

import java.util.ArrayList;
import java.util.List;

/**
 * EpgSyncJobService that periodically runs to update channels and programs.
 */
public class SampleJobService extends EpgSyncJobService {
    @Override
    public List<Channel> getChannels() {
        // Add channels through an XMLTV file
        XmlTvParser.TvListing listings = RichFeedUtil.getRichTvListings(this);
        List<Channel> channelList = new ArrayList<>(listings.channels);
        return channelList;
    }

    @Override
    public List<Program> getProgramsForChannel(Uri channelUri, Channel channel, long startMs,
            long endMs) {
        XmlTvParser.TvListing listings = RichFeedUtil.getRichTvListings(this);
        return listings.programs;
    }
}