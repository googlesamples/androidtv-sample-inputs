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

package com.google.android.media.tv.companionlibrary.xmltv;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.fail;

import com.google.android.media.tv.companionlibrary.BuildConfig;
import com.google.android.media.tv.companionlibrary.model.Advertisement;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import java.io.InputStream;
import java.text.ParseException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21, manifest = "src/main/AndroidManifest.xml")
public class XmlTvAdvertisementTest {
    @Test
    public void testAdvertisementParsing() throws XmlTvParser.XmlTvParseException, ParseException {
        long epochStartTime = 0;
        String requestUrl1 = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480" +
                "&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s" +
                "&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1" +
                "&cust_params=deployment%3Ddevsite%26sample_ct%3Dlinear&correlator=";
        String requestUrl2 = "https://pubads.g.doubleclick.net/gampad/ads?sz=640x480" +
                "&iu=/124319096/external/single_ad_samples&ciu_szs=300x250&impl=s" +
                "&gdfp_req=1&env=vp&output=vast&unviewed_position_start=1" +
                "&cust_params=deployment%3Ddevsite%26sample_ct%3Dredirectlinear&correlator=";
        String testXmlFile = "xmltv.xml";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
        // Channel 1 should have one VAST advertisement.
        Channel adChannel = listings.getChannels().get(1);
        assertNotNull(adChannel.getInternalProviderData());
        List<Advertisement> adChannelAds = adChannel.getInternalProviderData().getAds();
        assertEquals(1, adChannelAds.size());
        assertEquals(epochStartTime, adChannelAds.get(0).getStartTimeUtcMillis());
        assertEquals(epochStartTime, adChannelAds.get(0).getStopTimeUtcMillis());
        assertEquals(Advertisement.TYPE_VAST, adChannelAds.get(0).getType());
        // Channel 0 should not have any advertisement.
        Channel noAdChannel = listings.getChannels().get(0);
        assertNotNull(noAdChannel.getInternalProviderData());
        List<Advertisement> noAdChannelAds = noAdChannel.getInternalProviderData().getAds();
        assertEquals(0, noAdChannelAds.size());
        // Program 7 should have 2 advertisements with different request tags.
        Program adProgram = listings.getAllPrograms().get(7);
        assertNotNull(adProgram.getInternalProviderData());
        InternalProviderData adProgramData = adProgram.getInternalProviderData();
        List<Advertisement> adProgramAds = adProgramData.getAds();
        assertEquals(2, adProgramAds.size());
        assertEquals(requestUrl1, adProgramAds.get(0).getRequestUrl());
        assertEquals(requestUrl2, adProgramAds.get(1).getRequestUrl());
    }

    @Test
    public void testInvalidAdvertisement() throws XmlTvParser.XmlTvParseException {
        String testXmlFile = "invalid_xmltv_ad.xml";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        try {
            XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
            fail("IllegalArgumentException was not thrown");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "start, stop time of program ads cannot be null");
        }
    }
}
