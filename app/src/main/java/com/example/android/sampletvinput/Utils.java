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

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;

import com.example.android.sampletvinput.BaseTvInputService.ChannelInfo;

public class Utils {
    private static final String TAG = "Utils";
    private static final boolean DEBUG = true;

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

    public static boolean hasProgramInfo(ContentResolver resolver, Uri channelUri, long startTimeMs,
            long endTimeMs) {
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri, startTimeMs,
                endTimeMs);
        String[] projection = {TvContract.Programs._ID};
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, projection, null, null, null);
            if (cursor.getCount() > 0) {
                return true;
            }
        } catch (Exception e) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
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

    public static String getInputIdFromComponentName(Context context, ComponentName name) {
        TvInputManager tim = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
        for (TvInputInfo info : tim.getTvInputList()) {
            ServiceInfo si = info.getServiceInfo();
            if (new ComponentName(si.packageName, si.name).equals(name)) {
                return info.getId();
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
            if (info.mNumber.equals(channelNumber)) {
                return info;
            }
        }
        throw new IllegalArgumentException("Unknown channel: " + channelNumber);
    }

    private Utils() {}
}
