/*
 * Copyright 2015 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.media.tv.companionlibrary.xmltv;

import android.graphics.Color;
import android.media.tv.TvContentRating;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.util.Xml;
import com.google.android.media.tv.companionlibrary.model.Advertisement;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * XMLTV document parser which conforms to http://wiki.xmltv.org/index.php/Main_Page
 *
 * <p>Please note that xmltv.dtd are extended to be align with Android TV Input Framework and
 * contain static video contents:
 *
 * <p><!ELEMENT channel ([elements in xmltv.dtd], display-number, app-link) > <!ATTLIST channel
 * [attributes in xmltv.dtd] repeat-programs CDATA #IMPLIED > <!ATTLIST programme [attributes in
 * xmltv.dtd] video-src CDATA #IMPLIED video-type CDATA #IMPLIED > <!ELEMENT app-link (icon) >
 * <!ATTLIST app-link text CDATA #IMPLIED color CDATA #IMPLIED poster-uri CDATA #IMPLIED intent-uri
 * CDATA #IMPLIED > <!ELEMENT advertisement > <!ATTLIST start stop type >
 *
 * <p>display-number : The channel number that is displayed to the user.
 *
 * <p>repeat-programs : If "true", the programs in the xml document are scheduled sequentially in a
 * loop. Program and advertisement start and end times will be shifted as necessary for looping
 * content. This is introduced to simulate a live channel in this sample.
 *
 * <p>video-src : The video URL for the given program. This can be omitted if the xml will be used
 * only for the program guide update.
 *
 * <p>video-type : The video type. Should be one of "HTTP_PROGRESSIVE", "HLS", or "MPEG-DASH". This
 * can be omitted if the xml will be used only for the program guide update.
 *
 * <p>app-link : The app-link allows channel input sources to provide activity links from their live
 * channel programming to another activity. This enables content providers to increase user
 * engagement by offering the viewer other content or actions.
 *
 * <p>&emsp;text : The text of the app link template for this channel.
 *
 * <p>&emsp;color : The accent color of the app link template for this channel. This is primarily
 * used for the background color of the text box in the template.
 *
 * <p>&emsp;poster-uri : The URI for the poster art used as the background of the app link template
 * for this channel.
 *
 * <p>&emsp;intent-uri : The intent URI of the app link for this channel. It should be created using
 * Intent.toUri(int) with Intent.URI_INTENT_SCHEME. (see
 * https://developer.android.com/reference/android/media/tv/TvContract.Channels.html#COLUMN_APP_LINK_INTENT_URI)
 * The intent is launched when the user clicks the corresponding app link for the current channel.
 *
 * <p>advertisement : Representing an advertisement that can play on a channel or during a program.
 *
 * <p>&emsp;type : The type of advertisement. Requires "VAST".
 *
 * <p>&emsp;start : The start time of the advertisement.
 *
 * <p>&emsp;stop : The stop time of the advertisement.
 *
 * <p>&emsp;request-url : This element should contain the URL for the advertisement.
 *
 * <p>
 */
public class XmlTvParser {
    private static final String TAG_TV = "tv";
    private static final String TAG_CHANNEL = "channel";
    private static final String TAG_DISPLAY_NAME = "display-name";
    private static final String TAG_ICON = "icon";
    private static final String TAG_APP_LINK = "app-link";
    private static final String TAG_PROGRAM = "programme";
    private static final String TAG_TITLE = "title";
    private static final String TAG_DESC = "desc";
    private static final String TAG_CATEGORY = "category";
    private static final String TAG_RATING = "rating";
    private static final String TAG_VALUE = "value";
    private static final String TAG_DISPLAY_NUMBER = "display-number";
    private static final String TAG_AD = "advertisement";
    private static final String TAG_REQUEST_URL = "request-url";

    private static final String ATTR_ID = "id";
    private static final String ATTR_START = "start";
    private static final String ATTR_STOP = "stop";
    private static final String ATTR_CHANNEL = "channel";
    private static final String ATTR_SYSTEM = "system";
    private static final String ATTR_SRC = "src";
    private static final String ATTR_REPEAT_PROGRAMS = "repeat-programs";
    private static final String ATTR_VIDEO_SRC = "video-src";
    private static final String ATTR_VIDEO_TYPE = "video-type";
    private static final String ATTR_APP_LINK_TEXT = "text";
    private static final String ATTR_APP_LINK_COLOR = "color";
    private static final String ATTR_APP_LINK_POSTER_URI = "poster-uri";
    private static final String ATTR_APP_LINK_INTENT_URI = "intent-uri";
    private static final String ATTR_AD_START = "start";
    private static final String ATTR_AD_STOP = "stop";
    private static final String ATTR_AD_TYPE = "type";

    private static final String VALUE_VIDEO_TYPE_HTTP_PROGRESSIVE = "HTTP_PROGRESSIVE";
    private static final String VALUE_VIDEO_TYPE_HLS = "HLS";
    private static final String VALUE_VIDEO_TYPE_MPEG_DASH = "MPEG_DASH";
    private static final String VALUE_ADVERTISEMENT_TYPE_VAST = "VAST";

    private static final String ANDROID_TV_RATING = "com.android.tv";

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
    private static final String TAG = "XmlTvParser";

    private XmlTvParser() {}

    /**
     * Converts a TV ratings from an XML file to {@link TvContentRating}.
     *
     * @param rating An XmlTvRating.
     * @return A TvContentRating.
     */
    private static TvContentRating xmlTvRatingToTvContentRating(XmlTvParser.XmlTvRating rating) {
        if (ANDROID_TV_RATING.equals(rating.system)) {
            return TvContentRating.unflattenFromString(rating.value);
        }
        return null;
    }

    /**
     * Reads an InputStream and parses the data to identify channels and programs
     *
     * @param inputStream The InputStream of your data
     * @return A TvListing containing your channels and programs
     */
    public static TvListing parse(@NonNull InputStream inputStream) throws XmlTvParseException {
        return parse(inputStream, Xml.newPullParser());
    }

    /**
     * Reads an InputStream and parses the data to identify channels and programs
     *
     * @param inputStream The InputStream of your data
     * @param parser The XmlPullParser the developer selects to parse this data
     * @return A TvListing containing your channels and programs
     */
    private static TvListing parse(@NonNull InputStream inputStream, @NonNull XmlPullParser parser)
            throws XmlTvParseException {
        try {
            parser.setInput(inputStream, null);
            int eventType = parser.next();
            if (eventType != XmlPullParser.START_TAG || !TAG_TV.equals(parser.getName())) {
                throw new XmlTvParseException("Input stream does not contain an XMLTV description");
            }
            return parseTvListings(parser);
        } catch (XmlPullParserException | IOException | ParseException e) {
            Log.w(TAG, e.getMessage());
        }
        return null;
    }

    private static TvListing parseTvListings(XmlPullParser parser)
            throws IOException, XmlPullParserException, ParseException {
        List<Channel> channels = new ArrayList<>();
        List<Program> programs = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && TAG_CHANNEL.equalsIgnoreCase(parser.getName())) {
                channels.add(parseChannel(parser));
            }
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && TAG_PROGRAM.equalsIgnoreCase(parser.getName())) {
                programs.add(parseProgram(parser));
            }
        }
        return new TvListing(channels, programs);
    }

    private static Channel parseChannel(XmlPullParser parser)
            throws IOException, XmlPullParserException, ParseException {
        String id = null;
        boolean repeatPrograms = false;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_ID.equalsIgnoreCase(attr)) {
                id = value;
            } else if (ATTR_REPEAT_PROGRAMS.equalsIgnoreCase(attr)) {
                repeatPrograms = "TRUE".equalsIgnoreCase(value);
            }
        }
        String displayName = null;
        String displayNumber = null;
        XmlTvIcon icon = null;
        XmlTvAppLink appLink = null;
        Advertisement advertisement = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if (TAG_DISPLAY_NAME.equalsIgnoreCase(parser.getName()) && displayName == null) {
                    displayName = parser.nextText();
                } else if (TAG_DISPLAY_NUMBER.equalsIgnoreCase(parser.getName())
                        && displayNumber == null) {
                    displayNumber = parser.nextText();
                } else if (TAG_ICON.equalsIgnoreCase(parser.getName()) && icon == null) {
                    icon = parseIcon(parser);
                } else if (TAG_APP_LINK.equalsIgnoreCase(parser.getName()) && appLink == null) {
                    appLink = parseAppLink(parser);
                } else if (TAG_AD.equalsIgnoreCase(parser.getName()) && advertisement == null) {
                    advertisement = parseAd(parser, TAG_CHANNEL);
                }
            } else if (TAG_CHANNEL.equalsIgnoreCase(parser.getName())
                    && parser.getEventType() == XmlPullParser.END_TAG) {
                break;
            }
        }
        if (TextUtils.isEmpty(id) || TextUtils.isEmpty(displayName)) {
            throw new IllegalArgumentException("id and display-name can not be null.");
        }

        // Developers should assign original network ID in the right way not using the fake ID.
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setRepeatable(repeatPrograms);
        Channel.Builder builder =
                new Channel.Builder()
                        .setDisplayName(displayName)
                        .setDisplayNumber(displayNumber)
                        .setOriginalNetworkId(id.hashCode())
                        .setInternalProviderData(internalProviderData)
                        .setTransportStreamId(0)
                        .setServiceId(0);
        if (icon != null) {
            builder.setChannelLogo(icon.src);
        }
        if (appLink != null) {
            builder.setAppLinkColor(appLink.color)
                    .setAppLinkIconUri(appLink.icon.src)
                    .setAppLinkIntentUri(appLink.intentUri)
                    .setAppLinkPosterArtUri(appLink.posterUri)
                    .setAppLinkText(appLink.text);
        }
        if (advertisement != null) {
            List<Advertisement> advertisements = new ArrayList<>(1);
            advertisements.add(advertisement);
            internalProviderData.setAds(advertisements);
            builder.setInternalProviderData(internalProviderData);
        }
        return builder.build();
    }

    private static Program parseProgram(XmlPullParser parser)
            throws IOException, XmlPullParserException, ParseException {
        String channelId = null;
        Long startTimeUtcMillis = null;
        Long endTimeUtcMillis = null;
        String videoSrc = null;
        int videoType = TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_CHANNEL.equalsIgnoreCase(attr)) {
                channelId = value;
            } else if (ATTR_START.equalsIgnoreCase(attr)) {
                startTimeUtcMillis = DATE_FORMAT.parse(value).getTime();
            } else if (ATTR_STOP.equalsIgnoreCase(attr)) {
                endTimeUtcMillis = DATE_FORMAT.parse(value).getTime();
            } else if (ATTR_VIDEO_SRC.equalsIgnoreCase(attr)) {
                videoSrc = value;
            } else if (ATTR_VIDEO_TYPE.equalsIgnoreCase(attr)) {
                if (VALUE_VIDEO_TYPE_HTTP_PROGRESSIVE.equals(value)) {
                    videoType = TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE;
                } else if (VALUE_VIDEO_TYPE_HLS.equals(value)) {
                    videoType = TvContractUtils.SOURCE_TYPE_HLS;
                } else if (VALUE_VIDEO_TYPE_MPEG_DASH.equals(value)) {
                    videoType = TvContractUtils.SOURCE_TYPE_MPEG_DASH;
                }
            }
        }
        String title = null;
        String description = null;
        XmlTvIcon icon = null;
        List<String> category = new ArrayList<>();
        List<TvContentRating> rating = new ArrayList<>();
        List<Advertisement> ads = new ArrayList<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if (TAG_TITLE.equalsIgnoreCase(parser.getName())) {
                    title = parser.nextText();
                } else if (TAG_DESC.equalsIgnoreCase(tagName)) {
                    description = parser.nextText();
                } else if (TAG_ICON.equalsIgnoreCase(tagName)) {
                    icon = parseIcon(parser);
                } else if (TAG_CATEGORY.equalsIgnoreCase(tagName)) {
                    category.add(parser.nextText());
                } else if (TAG_RATING.equalsIgnoreCase(tagName)) {
                    TvContentRating xmlTvRating = xmlTvRatingToTvContentRating(parseRating(parser));
                    if (xmlTvRating != null) {
                        rating.add(xmlTvRating);
                    }
                } else if (TAG_AD.equalsIgnoreCase(tagName)) {
                    ads.add(parseAd(parser, TAG_PROGRAM));
                }
            } else if (TAG_PROGRAM.equalsIgnoreCase(tagName)
                    && parser.getEventType() == XmlPullParser.END_TAG) {
                break;
            }
        }
        if (TextUtils.isEmpty(channelId)
                || startTimeUtcMillis == null
                || endTimeUtcMillis == null) {
            throw new IllegalArgumentException("channel, start, and end can not be null.");
        }
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setVideoType(videoType);
        internalProviderData.setVideoUrl(videoSrc);
        internalProviderData.setAds(ads);
        return new Program.Builder()
                .setChannelId(channelId.hashCode())
                .setTitle(title)
                .setDescription(description)
                .setPosterArtUri(icon.src)
                .setCanonicalGenres(category.toArray(new String[category.size()]))
                .setStartTimeUtcMillis(startTimeUtcMillis)
                .setEndTimeUtcMillis(endTimeUtcMillis)
                .setContentRatings(rating.toArray(new TvContentRating[rating.size()]))
                // NOTE: {@code COLUMN_INTERNAL_PROVIDER_DATA} is a private field
                // where TvInputService can store anything it wants. Here, we store
                // video type and video URL so that TvInputService can play the
                // video later with this field.
                .setInternalProviderData(internalProviderData)
                .build();
    }

    private static XmlTvIcon parseIcon(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        String src = null;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_SRC.equalsIgnoreCase(attr)) {
                src = value;
            }
        }
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (TAG_ICON.equalsIgnoreCase(parser.getName())
                    && parser.getEventType() == XmlPullParser.END_TAG) {
                break;
            }
        }
        if (TextUtils.isEmpty(src)) {
            throw new IllegalArgumentException("Icon src cannot be null.");
        }
        return new XmlTvIcon(src);
    }

    private static XmlTvAppLink parseAppLink(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        String text = null;
        Integer color = null;
        String posterUri = null;
        String intentUri = null;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_APP_LINK_TEXT.equalsIgnoreCase(attr)) {
                text = value;
            } else if (ATTR_APP_LINK_COLOR.equalsIgnoreCase(attr)) {
                color = Color.parseColor(value);
            } else if (ATTR_APP_LINK_POSTER_URI.equalsIgnoreCase(attr)) {
                posterUri = value;
            } else if (ATTR_APP_LINK_INTENT_URI.equalsIgnoreCase(attr)) {
                intentUri = value;
            }
        }

        XmlTvIcon icon = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG
                    && TAG_ICON.equalsIgnoreCase(parser.getName())
                    && icon == null) {
                icon = parseIcon(parser);
            } else if (TAG_APP_LINK.equalsIgnoreCase(parser.getName())
                    && parser.getEventType() == XmlPullParser.END_TAG) {
                break;
            }
        }

        return new XmlTvAppLink(text, color, posterUri, intentUri, icon);
    }

    private static XmlTvRating parseRating(XmlPullParser parser)
            throws IOException, XmlPullParserException {
        String system = null;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_SYSTEM.equalsIgnoreCase(attr)) {
                system = value;
            }
        }
        String value = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if (TAG_VALUE.equalsIgnoreCase(parser.getName())) {
                    value = parser.nextText();
                }
            } else if (TAG_RATING.equalsIgnoreCase(parser.getName())
                    && parser.getEventType() == XmlPullParser.END_TAG) {
                break;
            }
        }
        if (TextUtils.isEmpty(system) || TextUtils.isEmpty(value)) {
            throw new IllegalArgumentException("system and value cannot be null.");
        }
        return new XmlTvRating(system, value);
    }

    private static Advertisement parseAd(XmlPullParser parser, String adType)
            throws IOException, XmlPullParserException, ParseException {
        Long startTimeUtcMillis = null;
        Long stopTimeUtcMillis = null;
        int type = Advertisement.TYPE_VAST;
        for (int i = 0; i < parser.getAttributeCount(); ++i) {
            String attr = parser.getAttributeName(i);
            String value = parser.getAttributeValue(i);
            if (ATTR_AD_START.equalsIgnoreCase(attr)) {
                startTimeUtcMillis = DATE_FORMAT.parse(value).getTime();
            } else if (ATTR_AD_STOP.equalsIgnoreCase(attr)) {
                stopTimeUtcMillis = DATE_FORMAT.parse(value).getTime();
            } else if (ATTR_AD_TYPE.equalsIgnoreCase(attr)) {
                if (VALUE_ADVERTISEMENT_TYPE_VAST.equalsIgnoreCase(attr)) {
                    type = Advertisement.TYPE_VAST;
                }
            }
        }
        String requestUrl = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if (TAG_REQUEST_URL.equalsIgnoreCase(parser.getName())) {
                    requestUrl = parser.nextText();
                }
            } else if (TAG_AD.equalsIgnoreCase(parser.getName())
                    && parser.getEventType() == XmlPullParser.END_TAG) {
                break;
            }
        }
        Advertisement.Builder builder = new Advertisement.Builder();
        if (adType.equals(TAG_PROGRAM)) {
            if (startTimeUtcMillis == null || stopTimeUtcMillis == null) {
                throw new IllegalArgumentException(
                        "start, stop time of program ads cannot be null");
            }
            builder.setStartTimeUtcMillis(startTimeUtcMillis);
            builder.setStopTimeUtcMillis(stopTimeUtcMillis);
        }
        return builder.setType(type).setRequestUrl(requestUrl).build();
    }

    /**
     * Contains a list of channels and corresponding programs that have been generated from parsing
     * an XML TV file.
     */
    public static class TvListing {
        private List<Channel> mChannels;
        private List<Program> mPrograms;
        private HashMap<Long, List<Program>> mProgramMap;

        private TvListing(List<Channel> channels, List<Program> programs) {
            this.mChannels = channels;
            this.mPrograms = new ArrayList<>(programs);
            // Place programs into the epg map
            mProgramMap = new HashMap<>();
            for (Channel channel : channels) {
                List<Program> programsForChannel = new ArrayList<>();
                Iterator<Program> programIterator = programs.iterator();
                while (programIterator.hasNext()) {
                    Program program = programIterator.next();
                    if (program.getChannelId() == channel.getOriginalNetworkId()) {
                        programsForChannel.add(
                                new Program.Builder(program).setChannelId(channel.getId()).build());
                        programIterator.remove();
                    }
                }
                mProgramMap.put(channel.getOriginalNetworkId(), programsForChannel);
            }
        }

        /** @return All channels found by the XmlTvParser. */
        public List<Channel> getChannels() {
            return mChannels;
        }

        /** @return All programs found by the XmlTvParser. */
        public List<Program> getAllPrograms() {
            return mPrograms;
        }

        /**
         * Returns a list of programs found by the XmlTvParser for a given channel.
         *
         * @param channel The channel to obtain programs for.
         * @return A list of programs that belong to that channel.
         */
        public List<Program> getPrograms(Channel channel) {
            return mProgramMap.get(channel.getOriginalNetworkId());
        }
    }

    private static class XmlTvIcon {
        public final String src;

        private XmlTvIcon(String src) {
            this.src = src;
        }
    }

    private static class XmlTvRating {
        public final String system;
        public final String value;

        public XmlTvRating(String system, String value) {
            this.system = system;
            this.value = value;
        }
    }

    private static class XmlTvAppLink {
        public final String text;
        public final Integer color;
        public final String posterUri;
        public final String intentUri;
        public final XmlTvIcon icon;

        public XmlTvAppLink(
                String text, Integer color, String posterUri, String intentUri, XmlTvIcon icon) {
            this.text = text;
            this.color = color;
            this.posterUri = posterUri;
            this.intentUri = intentUri;
            this.icon = icon;
        }
    }

    /** An exception that indicates the provided XMLTV file is invalid or improperly formatted. */
    public static class XmlTvParseException extends Exception {
        public XmlTvParseException(String msg) {
            super(msg);
        }
    }
}
