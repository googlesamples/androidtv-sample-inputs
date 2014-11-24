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

import android.content.Context;
import android.os.AsyncTask;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class OnlineTvInputService extends BaseTvInputService {
    private static final String TAG = "OnlineTvInputService";

    private static String mCatalogUrl = null;

    private static List<ChannelInfo> sSampleChannels = null;
    private static TvInput sTvInput = null;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public List<ChannelInfo> createSampleChannels() {
        return createOnlineChannelsStatic(this);
    }

    public static List<ChannelInfo> createOnlineChannelsStatic(Context context) {
        mCatalogUrl = context.getResources().getString(R.string.catalog_url);
        synchronized (OnlineTvInputService.class) {
            if (sSampleChannels != null) {
                return sSampleChannels;
            }
            LoadTvInputTask inputTask = new LoadTvInputTask();
            inputTask.execute(mCatalogUrl);

            try {
                inputTask.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            return sSampleChannels;
        }
    }

    public static TvInput getTvInput() {
        return sTvInput;
    }

    // AsyncTask for loading online channels
    private static class LoadTvInputTask extends AsyncTask<String, Void, Void> {

        private static InputStream mInputStream = null;

        @Override
        protected Void doInBackground(String... urls) {
            try {
                downloadUrl(urls[0]);
            } catch (IOException e) {
            }
            return null;
        }

        private void downloadUrl(String video_url) throws IOException {

            try {
                URL url = new java.net.URL(video_url);
                URLConnection urlConnection = url.openConnection();
                mInputStream = new BufferedInputStream(urlConnection.getInputStream());

                XmlPullParser parser = Xml.newPullParser();
                try {
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                    parser.setInput(mInputStream, null);
                    sTvInput = ChannelXMLParser.parseTvInput(parser);
                    sSampleChannels = ChannelXMLParser.parseChannelXML(parser);
                } catch (XmlPullParserException e) {
                    e.printStackTrace();
                }
            } finally {
                if (mInputStream != null) {
                    mInputStream.close();
                }
            }
        }

    }
}
