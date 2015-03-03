/*
 * Copyright 2015 The Android Open Source Project
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

package com.example.android.sampletvinput.rich;

import com.example.android.sampletvinput.rich.RichTvInputService.ChannelInfo;
import com.example.android.sampletvinput.rich.RichTvInputService.ProgramInfo;
import com.example.android.sampletvinput.rich.RichTvInputService.TvInput;
import com.example.android.sampletvinput.TvContractUtils;
import com.example.android.sampletvinput.player.TvInputPlayer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * A parser for the channels and programs feed used in {@link RichTvInputService}.
 * <p>
 * NOTE: The feed format here is just an example. Developers may invent any new formats and parse
 * them in their own way.
 * </p>
 */
public class ChannelXMLParser {
    private static String TAG = "ChannelXmlParser";

    private static final String TAG_TVINPUTS = "TvInputs";
    private static final String TAG_CHANNELS = "Channels";
    private static final String TAG_CHANNEL = "Channel";
    private static final String TAG_PROGRAM = "Program";

    private static final String ATTR_TVINPUT_DISPLAY_NAME = "display_name";
    private static final String ATTR_TVINPUT_NAME = "name";
    private static final String ATTR_TVINPUT_DESCRIPTION = "description";
    private static final String ATTR_LOGO_THUMB_URL = "logo_thumb_url";
    private static final String ATTR_LOGO_BACKGROUND_URL = "logo_background_url";

    private static final String ATTR_DISPLAY_NUMBER = "display_number";
    private static final String ATTR_DISPLAY_NAME = "display_name";
    private static final String ATTR_VIDEO_WIDTH = "video_width";
    private static final String ATTR_VIDEO_HEIGHT = "video_height";
    private static final String ATTR_LOGO_URL = "logo_url";

    private static final String ATTR_TITLE = "title";
    private static final String ATTR_POSTER_ART_URL = "poster_art_url";
    private static final String ATTR_DURATION_SEC = "duration_sec";
    private static final String ATTR_VIDEO_URL = "video_url";
    private static final String ATTR_VIDEO_TYPE = "video_type";
    private static final String ATTR_DESCRIPTION = "description";
    private static final String ATTR_CONTENT_RATING = "content_rating";

    private static final String VALUE_VIDEO_TYPE_HTTP_PROGRESSIVE = "HTTP_PROGRESSIVE";
    private static final String VALUE_VIDEO_TYPE_HLS = "HLS";
    private static final String VALUE_VIDEO_TYPE_MPEG_DASH = "MPEG_DASH";

    public static TvInput parseTvInput(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        parser.nextTag();
        parser.require(XmlPullParser.START_TAG, null, TAG_TVINPUTS);
        return parseTvInputDetail(parser);
    }

    private static TvInput parseTvInputDetail(XmlPullParser parser) {
        String displayName = null;
        String name = null;
        String description = null;
        String logoThumbUrl = null;
        String logoBackgroundUrl = null;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_TVINPUT_DISPLAY_NAME.equals(attr)) {
                displayName = value;
            } else if (ATTR_TVINPUT_NAME.equals(attr)) {
                name = value;
            } else if (ATTR_TVINPUT_DESCRIPTION.equals(attr)) {
                description = value;
            } else if (ATTR_LOGO_THUMB_URL.equals(attr)) {
                logoThumbUrl = value;
            } else if (ATTR_LOGO_BACKGROUND_URL.equals(attr)) {
                logoBackgroundUrl = value;
            }
        }
        return new TvInput(displayName, name, description, logoThumbUrl, logoBackgroundUrl);
    }

    public static List<ChannelInfo> parseChannelXML(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<ChannelInfo> list = new ArrayList<ChannelInfo>();
        parser.nextTag();

        parser.require(XmlPullParser.START_TAG, null, TAG_CHANNELS);
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && TAG_CHANNEL.equals(parser.getName())) {
                list.add(parseChannel(parser));
            }
        }
        return list;
    }

    private static ChannelInfo parseChannel(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        String displayNumber = null;
        String displayName = null;
        int videoWidth = 0;
        int videoHeight = 0;
        String logoUrl = null;
        StringBuilder hashString = new StringBuilder();
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            // Here we assume that other metadata except number and name may change when the feed
            // is updated.
            if (ATTR_DISPLAY_NUMBER.equals(attr)) {
                displayNumber = value;
                hashString.append(value).append(";");
            } else if (ATTR_DISPLAY_NAME.equals(attr)) {
                displayName = value;
                hashString.append(value).append(";");
            } else if (ATTR_VIDEO_WIDTH.equals(attr)) {
                videoWidth = Integer.parseInt(value);
            } else if (ATTR_VIDEO_HEIGHT.equals(attr)) {
                videoHeight = Integer.parseInt(value);
            } else if (ATTR_LOGO_URL.equals(attr)) {
                logoUrl = value;
            }
        }
        List<ProgramInfo> programs = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if (TAG_PROGRAM.equals(parser.getName())) {
                    programs.add(parseProgram(parser));
                }
            } else if (TAG_CHANNEL.equals(parser.getName())
                    && parser.getEventType() == XmlPullParser.END_TAG) {
                break;
            }
        }
        // Developers should assign original network ID in the right way not using the fake ID.
        int fakeOriginalNetworkId = hashString.toString().hashCode();
        return new ChannelInfo(displayNumber, displayName, logoUrl, fakeOriginalNetworkId, 0, 0,
                videoWidth, videoHeight, programs);
    }

    private static ProgramInfo parseProgram(XmlPullParser parser) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String title = null;
        long durationSec = 0;
        String videoUrl = null;
        int videoType = TvInputPlayer.SOURCE_TYPE_HTTP_PROGRESSIVE;
        String description = null;
        String posterArtUri = null;
        String contentRatings = null;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_TITLE.equals(attr)) {
                title = value;
            } else if (ATTR_POSTER_ART_URL.equals(attr)) {
                posterArtUri = value;
            } else if (ATTR_DURATION_SEC.equals(attr)) {
                durationSec = Integer.parseInt(value);
            } else if (ATTR_VIDEO_URL.equals(attr)) {
                videoUrl = value;
            } else if (ATTR_VIDEO_TYPE.equals(attr)) {
                if (VALUE_VIDEO_TYPE_HTTP_PROGRESSIVE.equals(value)) {
                    videoType = TvInputPlayer.SOURCE_TYPE_HTTP_PROGRESSIVE;
                } else if (VALUE_VIDEO_TYPE_HLS.equals(value)) {
                    videoType = TvInputPlayer.SOURCE_TYPE_HLS;
                } else if (VALUE_VIDEO_TYPE_MPEG_DASH.equals(value)) {
                    videoType = TvInputPlayer.SOURCE_TYPE_MPEG_DASH;
                }
            } else if (ATTR_DESCRIPTION.equals(attr)) {
                description = value;
            } else if (ATTR_CONTENT_RATING.equals(attr)) {
                contentRatings = value;
            }
        }
        return new ProgramInfo(title, posterArtUri, description, durationSec,
                TvContractUtils.stringToContentRatings(contentRatings), videoUrl, videoType, 0);
    }
}
