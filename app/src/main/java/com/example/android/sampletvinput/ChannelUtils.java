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

import android.content.ContentValues;
import android.content.Context;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import com.example.android.sampletvinput.BaseTvInputService.ChannelInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ChannelUtils {
    private static final String TAG = "ChannelUtils";

    private static final SparseArray<String> VIDEO_HEIGHT_TO_FORMAT_MAP =
            new SparseArray<String>();

    static {
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(480, TvContract.Channels.VIDEO_FORMAT_480P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(576, TvContract.Channels.VIDEO_FORMAT_576P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(720, TvContract.Channels.VIDEO_FORMAT_720P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(1080, TvContract.Channels.VIDEO_FORMAT_1080P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(2160, TvContract.Channels.VIDEO_FORMAT_2160P);
        VIDEO_HEIGHT_TO_FORMAT_MAP.put(4320, TvContract.Channels.VIDEO_FORMAT_4320P);
    }

    public static void populateChannels(
            Context context, String inputId, List<ChannelInfo> channels) {
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, inputId);
        Map<Uri, String> logos = new HashMap<Uri, String>();
        for (ChannelInfo channel : channels) {
            values.put(Channels.COLUMN_DISPLAY_NUMBER, channel.mNumber);
            values.put(Channels.COLUMN_DISPLAY_NAME, channel.mName);
            values.put(Channels.COLUMN_ORIGINAL_NETWORK_ID, channel.mOriginalNetworkId);
            values.put(Channels.COLUMN_TRANSPORT_STREAM_ID, channel.mTransportStreamId);
            values.put(Channels.COLUMN_SERVICE_ID, channel.mServiceId);
            String videoFormat = getVideoFormat(channel.mVideoHeight);
            if (videoFormat != null) {
                values.put(Channels.COLUMN_VIDEO_FORMAT, videoFormat);
            }
            Uri uri = context.getContentResolver().insert(TvContract.Channels.CONTENT_URI, values);
            if (!TextUtils.isEmpty(channel.mLogoUrl)) {
                logos.put(TvContract.buildChannelLogoUri(uri), channel.mLogoUrl);
            }
        }

        if (!logos.isEmpty()) {
            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
        }
    }

    private static String getVideoFormat(int videoHeight) {
        return VIDEO_HEIGHT_TO_FORMAT_MAP.get(videoHeight);
    }

    public static class InsertLogosTask extends AsyncTask<Map<Uri, String>, Void, Void> {
        private final Context mContext;

        InsertLogosTask(Context context) {
            mContext = context;
        }

        @Override
        public Void doInBackground(Map<Uri, String>... logosList) {
            for (Map<Uri, String> logos : logosList) {
                for (Uri uri : logos.keySet()) {
                    try {
                        Utils.insertUrl(mContext, uri, new URL(logos.get(uri)));
                    } catch (MalformedURLException e) {
                        Log.e(TAG, "Can't load " + logos.get(uri), e);
                    }
                }
            }
            return null;
        }
    }

    private ChannelUtils() {}
}
