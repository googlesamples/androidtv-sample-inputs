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

import android.media.tv.TvContract;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.google.android.media.tv.companionlibrary.BuildConfig;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link XmlTvParser}. */
@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 23,
    manifest = "src/main/AndroidManifest.xml")
@RequiresApi(api = Build.VERSION_CODES.M)
public class XmlTvParserTest extends TestCase {
    @Test
    public void testChannelParsing() throws IOException, XmlTvParser.XmlTvParseException {
        String testXmlFile = "xmltv.xml";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
        assertEquals(4, listings.getChannels().size());
        assertEquals("Creative Commons", listings.getChannels().get(1).getDisplayName());
        assertEquals("2-3", listings.getChannels().get(2).getDisplayNumber());
        assertEquals("App Link Text 1", listings.getChannels().get(2).getAppLinkText());
        assertNull(listings.getChannels().get(0).getChannelLogo());
        assertTrue(listings.getChannels().get(3).getChannelLogo()
                .contains("storage.googleapis.com/android-tv/images/mpeg_dash.png"));
    }

    @Test
    public void testProgramParsing() throws XmlTvParser.XmlTvParseException {
        String testXmlFile = "xmltv.xml";
        String APRIL_FOOLS_SOURCE = "https://commondatastorage.googleapis.com/android-tv/Sample%2" +
                "0videos/April%20Fool's%202013/Introducing%20Google%20Fiber%20to%20the%20Pole.mp4";
        String ELEPHANTS_DREAM_POSTER_ART = "https://storage.googleapis.com/gtv-videos-bucket/sam" +
                "ple/images_480x270/ElephantsDream.jpg";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
        assertEquals(9, listings.getAllPrograms().size());
        assertEquals("Introducing Gmail Blue", listings.getAllPrograms().get(0).getTitle());
        assertEquals("Introducing Gmail Blue",
                listings.getPrograms(listings.getChannels().get(0)).get(0).getTitle());
        assertEquals(TvContract.Programs.Genres.TECH_SCIENCE,
                listings.getAllPrograms().get(1).getCanonicalGenres()[1]);
        assertEquals(listings.getAllPrograms().get(2).getChannelId(),
                listings.getChannels().get(0).getOriginalNetworkId());
        assertNotNull(listings.getAllPrograms().get(3).getInternalProviderData());
        assertEquals(APRIL_FOOLS_SOURCE,
                listings.getAllPrograms().get(3).getInternalProviderData().getVideoUrl());
        assertEquals("Introducing Google Nose", listings.getAllPrograms().get(4).getDescription());
        assertEquals(ELEPHANTS_DREAM_POSTER_ART,
                listings.getAllPrograms().get(5).getPosterArtUri());
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setVideoType(TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE);
        internalProviderData.setVideoUrl(APRIL_FOOLS_SOURCE);
        assertEquals(internalProviderData,
                listings.getAllPrograms().get(3).getInternalProviderData());
    }



    @Test
    public void testValidXmlParsing()
            throws XmlTvParser.XmlTvParseException, FileNotFoundException {
        String testXmlFile = "xmltv.xml";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
        // The parsing did not encounter any errors
        assertNotNull(listings);
        assertEquals(4, listings.getChannels().size());
        assertEquals(9, listings.getAllPrograms().size());
    }

    @Test
    public void testInvalidXmlParsing() throws FileNotFoundException {
        String testXmlFile = "invalid_xmltv.xml";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        try {
            XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
            // The parsing succeeded though it was not supposed to
            fail();
        } catch (XmlTvParser.XmlTvParseException e) {
            // The parser encountered an error and exposed it to the developer as expected
        }
    }

    @Test
    public void testInvalidPath() throws XmlTvParser.XmlTvParseException {
        String testXmlFile = "invalid_file.xml";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        assertNull(inputStream);
        try {
            XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
            // The parsing succeeded though it was not supposed to
            fail();
        } catch (IllegalArgumentException e) {
            // The parser encountered an error and exposed it to the developer as expected
        }
    }
}
