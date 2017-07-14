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
package com.example.android.sampletvinput;

import android.media.tv.TvContract;
import android.net.Uri;
import com.example.android.sampletvinput.rich.RichFeedUtil;
import com.google.android.exoplayer.util.Util;
import com.google.android.media.tv.companionlibrary.ads.EpgSyncWithAdsJobService;
import com.google.android.media.tv.companionlibrary.model.Advertisement;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.xmltv.XmlTvParser;
import java.util.ArrayList;
import java.util.List;

/**
 * EpgSyncJobService that periodically runs to update channels and programs.
 */
public class SampleJobService extends EpgSyncWithAdsJobService {
    private String MPEG_DASH_CHANNEL_NAME = "MPEG_DASH";
    private String MPEG_DASH_CHANNEL_NUMBER = "3";
    private String MPEG_DASH_CHANNEL_LOGO
            = "https://storage.googleapis.com/android-tv/images/mpeg_dash.png";
    private int MPEG_DASH_ORIGINAL_NETWORK_ID = 101;
    private String TEARS_OF_STEEL_TITLE = "Tears of Steel";
    private String TEARS_OF_STEEL_DESCRIPTION = "Monsters invade a small town in this sci-fi flick";
    private String TEARS_OF_STEEL_ART
            = "https://storage.googleapis.com/gtv-videos-bucket/sample/images/tears.jpg";
    private String TEARS_OF_STEEL_SOURCE
            = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd";
    private static final long TEARS_OF_STEEL_START_TIME_MS = 0;
    private static final long TEARS_OF_STEEL_DURATION_MS = 734 * 1000;
    private static final long TEST_AD_1_START_TIME_MS = 15 * 1000;
    private static final long TEST_AD_2_START_TIME_MS = 40 * 1000;
    private static final long TEST_AD_DURATION_MS = 10 * 1000;
    /**
     * Test <a href="http://www.iab.com/guidelines/digital-video-ad-serving-template-vast-3-0/">
     * VAST</a> URL from <a href="https://www.google.com/dfp">DoubleClick for Publishers (DFP)</a>.
     * More sample VAST tags can be found on
     * <a href="https://developers.google.com/interactive-media-ads/docs/sdks/android/tags">DFP
     * website</a>. You should replace it with the vast tag that you applied from your
     * advertisement provider. To verify whether your video ad response is VAST compliant, try<a
     * href="https://developers.google.com/interactive-media-ads/docs/sdks/android/vastinspector">
     * Google Ads Mobile Video Suite Inspector</a>
     */
    private static String TEST_AD_REQUEST_URL =
            "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480&iu=/124319096/external/" +
            "single_ad_samples&ciu_szs=300x250&impl=s&gdfp_req=1&env=vp&output=vast" +
            "&unviewed_position_start=1&cust_params=deployment%3Ddevsite%26sample_ct" +
            "%3Dlinear&correlator=";

    @Override
    public List<Channel> getChannels() {
        // Add channels through an XMLTV file
        XmlTvParser.TvListing listings = RichFeedUtil.getRichTvListings(this);
        List<Channel> channelList = new ArrayList<>(listings.getChannels());

        // Build advertisement list for the channel.
        Advertisement channelAd = new Advertisement.Builder()
                .setType(Advertisement.TYPE_VAST)
                .setRequestUrl(TEST_AD_REQUEST_URL)
                .build();
        List<Advertisement> channelAdList = new ArrayList<>();
        channelAdList.add(channelAd);

        // Add a channel programmatically
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setRepeatable(true);
        internalProviderData.setAds(channelAdList);
        Channel channelTears = new Channel.Builder()
                .setDisplayName(MPEG_DASH_CHANNEL_NAME)
                .setDisplayNumber(MPEG_DASH_CHANNEL_NUMBER)
                .setChannelLogo(MPEG_DASH_CHANNEL_LOGO)
                .setOriginalNetworkId(MPEG_DASH_ORIGINAL_NETWORK_ID)
                .setInternalProviderData(internalProviderData)
                .build();
        channelList.add(channelTears);
        return channelList;
    }

    @Override
    public List<Program> getOriginalProgramsForChannel(Uri channelUri, Channel channel,
            long startMs, long endMs) {
        if (!channel.getDisplayName().equals(MPEG_DASH_CHANNEL_NAME)) {
            // Is an XMLTV Channel
            XmlTvParser.TvListing listings = RichFeedUtil.getRichTvListings(getApplicationContext());
            return listings.getPrograms(channel);
        } else {
            // Build Advertisement list for the program.
            Advertisement programAd1 = new Advertisement.Builder()
                    .setStartTimeUtcMillis(TEST_AD_1_START_TIME_MS)
                    .setStopTimeUtcMillis(TEST_AD_1_START_TIME_MS + TEST_AD_DURATION_MS)
                    .setType(Advertisement.TYPE_VAST)
                    .setRequestUrl(TEST_AD_REQUEST_URL)
                    .build();
            Advertisement programAd2 = new Advertisement.Builder(programAd1)
                    .setStartTimeUtcMillis(TEST_AD_2_START_TIME_MS)
                    .setStopTimeUtcMillis(TEST_AD_2_START_TIME_MS + TEST_AD_DURATION_MS)
                    .build();
            List<Advertisement> programAdList = new ArrayList<>();
            programAdList.add(programAd1);
            programAdList.add(programAd2);

            // Programatically add channel
            List<Program> programsTears = new ArrayList<>();
            InternalProviderData internalProviderData = new InternalProviderData();
            internalProviderData.setVideoType(Util.TYPE_DASH);
            internalProviderData.setVideoUrl(TEARS_OF_STEEL_SOURCE);
            internalProviderData.setAds(programAdList);
            programsTears.add(new Program.Builder()
                    .setTitle(TEARS_OF_STEEL_TITLE)
                    .setStartTimeUtcMillis(TEARS_OF_STEEL_START_TIME_MS)
                    .setEndTimeUtcMillis(TEARS_OF_STEEL_START_TIME_MS + TEARS_OF_STEEL_DURATION_MS)
                    .setDescription(TEARS_OF_STEEL_DESCRIPTION)
                    .setCanonicalGenres(new String[] {TvContract.Programs.Genres.TECH_SCIENCE,
                            TvContract.Programs.Genres.MOVIES})
                    .setPosterArtUri(TEARS_OF_STEEL_ART)
                    .setThumbnailUri(TEARS_OF_STEEL_ART)
                    .setInternalProviderData(internalProviderData)
                    .build());
            return programsTears;
        }
    }
}
