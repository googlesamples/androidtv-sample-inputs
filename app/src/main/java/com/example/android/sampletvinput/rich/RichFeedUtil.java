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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Xml;

import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.rich.RichTvInputService.ChannelInfo;
import com.example.android.sampletvinput.rich.RichTvInputService.TvInput;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * Static helper methods for fetching the channel feed.
 */
public class RichFeedUtil {
    private static final String TAG = "RichFeedUtil";
    private static List<ChannelInfo> sSampleChannels;
    private static TvInput sTvInput;

    private static final boolean USE_LOCAL_XML_FEED = false;

    private RichFeedUtil() {
    }

    /**
     * Returns the channel metadata for {@link RichTvInputService}. Note that this will block until
     * the channel feed has been retrieved.
     */
    public static List<ChannelInfo> getRichChannels(Context context) {
        Uri catalogUri =
                USE_LOCAL_XML_FEED ?
                        Uri.parse("android.resource://" + context.getPackageName() + "/"
                                + R.raw.rich_tv_inputs_tif)
                        : Uri.parse(context.getResources().getString(R.string.rich_input_feed_url));
        if (sSampleChannels != null) {
            return sSampleChannels;
        }

        InputStream inputStream = null;
        try {
            if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(catalogUri.getScheme())) {
                inputStream = context.getContentResolver().openInputStream(catalogUri);
            } else {
                URLConnection urlConnection = new URL(catalogUri.toString()).openConnection();
                inputStream = urlConnection.getInputStream();
            }

            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(inputStream, null);
            sTvInput = ChannelXMLParser.parseTvInput(parser);
            sSampleChannels = ChannelXMLParser.parseChannelXML(parser);
        } catch (IOException e) {
            Log.e(TAG, "Error in fetching " + catalogUri, e);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Error in parsing " + catalogUri, e);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // Do nothing.
                }
            }
        }
        return sSampleChannels;
    }

    public static TvInput getTvInput(Context context) {
        if (sTvInput == null) {
            getRichChannels(context);
        }
        return sTvInput;
    }
}
