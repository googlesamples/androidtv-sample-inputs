/*
 * Copyright 2014 Google Inc. All rights reserved.
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

import android.media.tv.TvContentRating;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

public class LocalTvInputService extends BaseTvInputService {
    private static final String TAG = "LocalTvInputService";
    private static final boolean DEBUG = true;

    public static final String PREF_SUPPORT_MULTI_SESSION = "support_multi_session";

    private static final String PROVIDER_DISPLAY_NAME = "Local TV Inputs with Three Channels";
    private static final String PROVIDER_NAME = "Local Company";
    private static final String PROVIDER_DESCRIPTION = "Local TV inputs with three channels";
    private static final String PROVIDER_LOGO_THUMB_URL =
            "http://commondatastorage.googleapis.com/android-tv/Local.jpg";
    private static final String PROVIDER_LOGO_BACKGROUND_URL =
            "http://commondatastorage.googleapis.com/android-tv/Local.jpg";

    private static final String CHANNEL_1_NUMBER = "1-1";
    private static final String CHANNEL_1_NAME = "BUNNY(SD)";
    private static final String CHANNEL_1_LOGO = null;
    private static final int CHANNEL_1_ORIG_NETWORK_ID = 0;
    private static final int CHANNEL_1_TRANSPORT_STREAM_ID = 0;
    private static final int CHANNEL_1_SERVICE_ID = 1;
    private static final int CHANNEL_1_VIDEO_WIDTH = 640;
    private static final int CHANNEL_1_VIDEO_HEIGHT = 480;
    private static final int CHANNEL_1_AUTO_CHANNEL = 2;
    private static final boolean CHANNEL_1_CLOSED_CAPTION = false;
    private static final String PROGRAM_1_TITLE = "Big Buck Bunny";
    private static final String PROGRAM_1_POSTER_URI = null;
    private static final String PROGRAM_1_DESC = "Big Buck Bunny - Low resolution";
    private static final long PROGRAM_1_START_TIME = 0;
    private static final long PROGRAM_1_DURATION = 3600;
    private static final String PROGRAM_1_URI = null;
    private static final int RESOURCE_1 =
            R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_22050hz;

    private static final String CHANNEL_2_NUMBER = "1-2";
    private static final String CHANNEL_2_NAME = "BUNNY(HD)";
    private static final String CHANNEL_2_LOGO = null;
    private static final int CHANNEL_2_ORIG_NETWORK_ID = 0;
    private static final int CHANNEL_2_TRANSPORT_STREAM_ID = 0;
    private static final int CHANNEL_2_SERVICE_ID = 2;
    private static final int CHANNEL_2_VIDEO_WIDTH = 1280;
    private static final int CHANNEL_2_VIDEO_HEIGHT = 782;
    private static final int CHANNEL_2_AUTO_CHANNEL = 6;
    private static final boolean CHANNEL_2_CLOSED_CAPTION = true;
    private static final String PROGRAM_2_TITLE = "Big Buck Bunny";
    private static final String PROGRAM_2_POSTER_URI = null;
    private static final String PROGRAM_2_DESC = "Big Buck Bunny - High resolution";
    private static final long PROGRAM_2_START_TIME = 0;
    private static final long PROGRAM_2_DURATION = 3600;
    private static final String PROGRAM_2_URI = null;
    private static final int RESOURCE_2 =
            R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;

    private static final String CHANNEL_3_NUMBER = "1-3";
    private static final String CHANNEL_3_NAME = "BUNNY";
    private static final String CHANNEL_3_LOGO = null;
    private static final int CHANNEL_3_ORIG_NETWORK_ID = 0;
    private static final int CHANNEL_3_TRANSPORT_STREAM_ID = 0;
    private static final int CHANNEL_3_SERVICE_ID = 3;
    private static final int CHANNEL_3_VIDEO_WIDTH = 0;
    private static final int CHANNEL_3_VIDEO_HEIGHT = 0;
    private static final int CHANNEL_3_AUTO_CHANNEL = 0;
    private static final boolean CHANNEL_3_CLOSED_CAPTION = false;
    private static final String PROGRAM_3_TITLE = "Big Buck Bunny";
    private static final String PROGRAM_3_POSTER_URI = null;
    private static final String PROGRAM_3_DESC = "Big Buck Bunny - No resolution info";
    private static final long PROGRAM_3_START_TIME = 0;
    private static final long PROGRAM_3_DURATION = 7200;
    private static final String PROGRAM_3_URI = null;
    private static final int RESOURCE_3 =
            R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;

    private static List<ChannelInfo> sSampleChannels = null;
    private static TvInput sTvInput = null;

    private boolean mSupportMultiSession = true;

    @Override
    public void onCreate() {
        super.onCreate();
        mSupportMultiSession = getSupportMultiSession();
    }

    public static TvInput getTvInput() {
        sTvInput = new TvInput(PROVIDER_DISPLAY_NAME,
                PROVIDER_NAME,
                PROVIDER_DESCRIPTION,
                PROVIDER_LOGO_THUMB_URL,
                PROVIDER_LOGO_BACKGROUND_URL);
        return sTvInput;
    }

    public static List<ChannelInfo> createSampleChannelsStatic() {
        synchronized (LocalTvInputService.class) {
            if (sSampleChannels != null) {
                return sSampleChannels;
            }
            TvContentRating[] ratings = {
                    TvContentRating.createRating("com.android.tv", "US_TV", "US_TV_Y7", "US_TV_FV"),
                    TvContentRating.createRating("com.android.tv", "US_TV", "US_TV_MA"),
                    TvContentRating.createRating("com.android.tv", "US_TV", "US_TV_PG", "US_TV_L",
                            "US_TV_S")};
            sSampleChannels = new ArrayList<ChannelInfo>();

            sSampleChannels.add(
                    new ChannelInfo(CHANNEL_1_NUMBER,
                            CHANNEL_1_NAME,
                            CHANNEL_1_LOGO,
                            CHANNEL_1_ORIG_NETWORK_ID,
                            CHANNEL_1_TRANSPORT_STREAM_ID,
                            CHANNEL_1_SERVICE_ID,
                            CHANNEL_1_VIDEO_WIDTH,
                            CHANNEL_1_VIDEO_HEIGHT,
                            CHANNEL_1_AUTO_CHANNEL,
                            CHANNEL_1_CLOSED_CAPTION,
                            new ProgramInfo(PROGRAM_1_TITLE,
                                    PROGRAM_1_POSTER_URI,
                                    PROGRAM_1_DESC,
                                    PROGRAM_1_START_TIME,
                                    PROGRAM_1_DURATION,
                                    null,
                                    PROGRAM_1_URI,
                                    RESOURCE_1)));
            sSampleChannels.add(
                    new ChannelInfo(CHANNEL_2_NUMBER,
                            CHANNEL_2_NAME,
                            CHANNEL_2_LOGO,
                            CHANNEL_2_ORIG_NETWORK_ID,
                            CHANNEL_2_TRANSPORT_STREAM_ID,
                            CHANNEL_2_SERVICE_ID,
                            CHANNEL_2_VIDEO_WIDTH,
                            CHANNEL_2_VIDEO_HEIGHT,
                            CHANNEL_2_AUTO_CHANNEL,
                            CHANNEL_2_CLOSED_CAPTION,
                            new ProgramInfo(PROGRAM_2_TITLE,
                                    PROGRAM_2_POSTER_URI,
                                    PROGRAM_2_DESC,
                                    PROGRAM_2_START_TIME,
                                    PROGRAM_2_DURATION,
                                    ratings,
                                    PROGRAM_2_URI,
                                    RESOURCE_2)));
            sSampleChannels.add(
                    new ChannelInfo(CHANNEL_3_NUMBER,
                            CHANNEL_3_NAME,
                            CHANNEL_3_LOGO,
                            CHANNEL_3_ORIG_NETWORK_ID,
                            CHANNEL_3_TRANSPORT_STREAM_ID,
                            CHANNEL_3_SERVICE_ID,
                            CHANNEL_3_VIDEO_WIDTH,
                            CHANNEL_3_VIDEO_HEIGHT,
                            CHANNEL_3_AUTO_CHANNEL,
                            CHANNEL_3_CLOSED_CAPTION,
                            new ProgramInfo(PROGRAM_3_TITLE,
                                    PROGRAM_3_POSTER_URI,
                                    PROGRAM_3_DESC,
                                    PROGRAM_3_START_TIME,
                                    PROGRAM_3_DURATION,
                                    null,
                                    PROGRAM_3_URI,
                                    RESOURCE_3)));
            return sSampleChannels;
        }
    }

    @Override
    public List<ChannelInfo> createSampleChannels() {
        return createSampleChannelsStatic();
    }

    private boolean getSupportMultiSession() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(PREF_SUPPORT_MULTI_SESSION, true);
    }

    private void putSupportMultiSession(boolean supportMultiSession) {
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean(PREF_SUPPORT_MULTI_SESSION, supportMultiSession).apply();
    }
}
