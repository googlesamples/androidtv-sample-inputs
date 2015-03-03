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

package com.example.android.sampletvinput;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.util.SparseArray;

import com.example.android.sampletvinput.rich.RichTvInputService.ChannelInfo;
import com.example.android.sampletvinput.rich.RichTvInputService.PlaybackInfo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static helper methods for working with {@link android.media.tv.TvContract}.
 */
public class TvContractUtils {
    private static final String TAG = "TvContractUtils";
    private static final boolean DEBUG = true;

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

    public static void updateChannels(
            Context context, String inputId, List<ChannelInfo> channels) {
        // Create a map from original network ID to channel row ID for existing channels.
        SparseArray<Long> mExistingChannelsMap = new SparseArray<Long>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {Channels._ID, Channels.COLUMN_ORIGINAL_NETWORK_ID};
        Cursor cursor = null;
        ContentResolver resolver = context.getContentResolver();
        try {
            cursor = resolver.query(channelsUri, projection, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                int originalNetworkId = cursor.getInt(1);
                mExistingChannelsMap.put(originalNetworkId, rowId);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // If a channel exists, update it. If not, insert a new one.
        ContentValues values = new ContentValues();
        values.put(Channels.COLUMN_INPUT_ID, inputId);
        Map<Uri, String> logos = new HashMap<Uri, String>();
        for (ChannelInfo channel : channels) {
            values.put(Channels.COLUMN_DISPLAY_NUMBER, channel.number);
            values.put(Channels.COLUMN_DISPLAY_NAME, channel.name);
            values.put(Channels.COLUMN_ORIGINAL_NETWORK_ID, channel.originalNetworkId);
            values.put(Channels.COLUMN_TRANSPORT_STREAM_ID, channel.transportStreamId);
            values.put(Channels.COLUMN_SERVICE_ID, channel.serviceId);
            String videoFormat = getVideoFormat(channel.videoHeight);
            if (videoFormat != null) {
                values.put(Channels.COLUMN_VIDEO_FORMAT, videoFormat);
            } else {
                values.putNull(Channels.COLUMN_VIDEO_FORMAT);
            }
            Long rowId = mExistingChannelsMap.get(channel.originalNetworkId);
            Uri uri;
            if (rowId == null) {
                uri = resolver.insert(TvContract.Channels.CONTENT_URI, values);
            } else {
                uri = TvContract.buildChannelUri(rowId);
                resolver.update(uri, values, null, null);
                mExistingChannelsMap.remove(channel.originalNetworkId);
            }
            if (!TextUtils.isEmpty(channel.logoUrl)) {
                logos.put(TvContract.buildChannelLogoUri(uri), channel.logoUrl);
            }
        }
        if (!logos.isEmpty()) {
            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
        }

        // Deletes channels which don't exist in the new feed.
        int size = mExistingChannelsMap.size();
        for(int i = 0; i < size; ++i) {
            Long rowId = mExistingChannelsMap.valueAt(i);
            resolver.delete(TvContract.buildChannelUri(rowId), null, null);
        }
    }

    public static int getChannelCount(ContentResolver resolver, String inputId) {
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = { TvContract.Channels._ID };

        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, projection, null, null, null);
            if (cursor != null) {
                return cursor.getCount();
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    private static String getVideoFormat(int videoHeight) {
        return VIDEO_HEIGHT_TO_FORMAT_MAP.get(videoHeight);
    }

    public static LongSparseArray<ChannelInfo> buildChannelMap(ContentResolver resolver,
            String inputId, List<ChannelInfo> channels) {
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER
        };

        LongSparseArray<ChannelInfo> channelMap = new LongSparseArray<>();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, projection, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }

            while (cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                String channelNumber = cursor.getString(1);
                channelMap.put(channelId, getChannelByNumber(channelNumber, channels));
            }
        } catch (Exception e) {
            Log.d(TAG, "Content provider query: " + e.getStackTrace());
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return channelMap;
    }

    public static long getLastProgramEndTimeMillis(ContentResolver resolver, Uri channelUri) {
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri);
        String[] projection = {Programs.COLUMN_END_TIME_UTC_MILLIS};
        Cursor cursor = null;
        try {
            // TvProvider returns programs chronological order by default.
            cursor = resolver.query(uri, projection, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return 0;
            }
            cursor.moveToLast();
            return cursor.getLong(0);
        } catch (Exception e) {
            Log.w(TAG, "Unable to get last program end time for " + channelUri, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return 0;
    }

    public static List<PlaybackInfo> getProgramPlaybackInfo(
            ContentResolver resolver, Uri channelUri, long startTimeMs, long endTimeMs,
            int maxProgramInReturn) {
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri, startTimeMs,
                endTimeMs);
        String[] projection = { Programs.COLUMN_START_TIME_UTC_MILLIS,
                Programs.COLUMN_END_TIME_UTC_MILLIS,
                Programs.COLUMN_CONTENT_RATING,
                Programs.COLUMN_INTERNAL_PROVIDER_DATA };
        Cursor cursor = null;
        List<PlaybackInfo> list = new ArrayList<>();
        try {
            cursor = resolver.query(uri, projection, null, null, null);
            while (cursor.moveToNext()) {
                long startMs = cursor.getLong(0);
                long endMs = cursor.getLong(1);
                TvContentRating[] ratings = stringToContentRatings(cursor.getString(2));
                Pair<Integer, String> values = parseInternalProviderData(cursor.getString(3));
                int videoType = values.first;
                String videoUrl = values.second;
                list.add(new PlaybackInfo(startMs, endMs, videoUrl, videoType,
                        ratings));
                if (list.size() > maxProgramInReturn) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get program playback info from TvProvider.", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    public static String convertVideoInfoToInternalProviderData(int videotype, String videoUrl) {
        return videotype + "," + videoUrl;
    }

    public static Pair<Integer, String> parseInternalProviderData(String internalData) {
        String[] values = internalData.split(",", 2);
        if (values.length != 2) {
            throw new IllegalArgumentException(internalData);
        }
        return new Pair<>(Integer.parseInt(values[0]), values[1]);
    }

    public static void insertUrl(Context context, Uri contentUri, URL sourceUrl) {
        if (DEBUG) {
            Log.d(TAG, "Inserting " + sourceUrl + " to " + contentUri);
        }
        InputStream is = null;
        OutputStream os = null;
        try {
            is = sourceUrl.openStream();
            os = context.getContentResolver().openOutputStream(contentUri);
            copy(is, os);
        } catch (IOException ioe) {
            Log.e(TAG, "Failed to write " + sourceUrl + "  to " + contentUri, ioe);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore exception.
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // Ignore exception.
                }
            }
        }
    }

    public static void copy(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            os.write(buffer, 0, len);
        }
    }

    public static String getServiceNameFromInputId(Context context, String inputId) {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : tim.getTvInputList()) {
            if (info.getId().equals(inputId)) {
                return info.getServiceInfo().name;
            }
        }
        return null;
    }

    public static TvContentRating[] stringToContentRatings(String commaSeparatedRatings) {
        if (TextUtils.isEmpty(commaSeparatedRatings)) {
            return null;
        }
        String[] ratings = commaSeparatedRatings.split("\\s*,\\s*");
        TvContentRating[] contentRatings = new TvContentRating[ratings.length];
        for (int i = 0; i < contentRatings.length; ++i) {
            contentRatings[i] = TvContentRating.unflattenFromString(ratings[i]);
        }
        return contentRatings;
    }

    public static String contentRatingsToString(TvContentRating[] contentRatings) {
        if (contentRatings == null || contentRatings.length == 0) {
            return null;
        }
        final String DELIMITER = ",";
        StringBuilder ratings = new StringBuilder(contentRatings[0].flattenToString());
        for (int i = 1; i < contentRatings.length; ++i) {
            ratings.append(DELIMITER);
            ratings.append(contentRatings[i].flattenToString());
        }
        return ratings.toString();
    }

    private static ChannelInfo getChannelByNumber(String channelNumber,
            List<ChannelInfo> channels) {
        for (ChannelInfo info : channels) {
            if (info.number.equals(channelNumber)) {
                return info;
            }
        }
        throw new IllegalArgumentException("Unknown channel: " + channelNumber);
    }

    private TvContractUtils() {}

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
                        insertUrl(mContext, uri, new URL(logos.get(uri)));
                    } catch (MalformedURLException e) {
                        Log.e(TAG, "Can't load " + logos.get(uri), e);
                    }
                }
            }
            return null;
        }
    }
}
