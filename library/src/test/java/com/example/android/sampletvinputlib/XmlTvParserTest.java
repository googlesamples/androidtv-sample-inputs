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

package com.example.android.sampletvinputlib;

import com.example.android.sampletvinput.xmltv.XmlTvParser;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.io.FileNotFoundException;
import java.io.InputStream;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21, manifest = "src/main/AndroidManifest.xml")
public class XmlTvParserTest extends TestCase {
    @Test
    public void testValidXmlParsing()
            throws XmlTvParser.XmlTvParseException, FileNotFoundException {
        String testXmlFile = "xmltv.xml";
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(testXmlFile);
        XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
        // The parsing did not encounter any errors
        assertNotNull(listings);
        assertEquals(listings.channels.size(), 4);
        assertEquals(listings.programs.size(), 9);
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
        assert inputStream == null;
        try {
            XmlTvParser.TvListing listings = XmlTvParser.parse(inputStream);
            // The parsing succeeded though it was not supposed to
            fail();
        } catch (IllegalArgumentException e) {
            // The parser encountered an error and exposed it to the developer as expected
        }
    }
}
