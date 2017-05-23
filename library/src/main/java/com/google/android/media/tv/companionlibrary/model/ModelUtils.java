/*
 * Copyright 2015 Google Inc. All rights reserved.
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

package com.google.android.media.tv.companionlibrary.model;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Channels;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils.InsertLogosTask;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Static utils for data model classes */
public final class ModelUtils {
    private static final String TAG = "ModelUtils";
    private static final boolean DEBUG = false;

    /**
     * Called when {@link #updateChannels(Context, String, List, OnChannelDeletedCallback)} deletes
     * a channel.
     */
    public interface OnChannelDeletedCallback {
        void onChannelDeleted(long rowId);
    }

    /**
     * Updates the list of available channels.
     *
     * @param context The application's context.
     * @param inputId The ID of the TV input service that provides this TV channel.
     * @param channels The updated list of channels.
     * @hide
     */
    public static void updateChannels(
            Context context,
            String inputId,
            List<Channel> channels,
            OnChannelDeletedCallback onChannelDeletedCallback) {
        // Create a map from original network ID to channel row ID for existing channels.
        LongSparseArray<Long> channelMap = new LongSparseArray<>();
        Uri channelsUri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {Channels._ID, Channels.COLUMN_ORIGINAL_NETWORK_ID};
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = null;
        int updateCount = 0;
        int addCount = 0;
        try {
            cursor = resolver.query(channelsUri, projection, null, null, null);
            while (cursor != null && cursor.moveToNext()) {
                long rowId = cursor.getLong(0);
                long originalNetworkId = cursor.getLong(1);
                channelMap.put(originalNetworkId, rowId);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // If a channel exists, update it. If not, insert a new one.
        Map<Uri, String> logos = new HashMap<>();
        for (Channel channel : channels) {
            ContentValues values = new ContentValues();
            values.put(Channels.COLUMN_INPUT_ID, inputId);
            values.putAll(channel.toContentValues());
            // If some required fields are not populated, the app may crash, so defaults are used
            if (channel.getPackageName() == null) {
                // If channel does not include package name, it will be added
                values.put(Channels.COLUMN_PACKAGE_NAME, context.getPackageName());
            }
            if (channel.getInputId() == null) {
                // If channel does not include input id, it will be added
                values.put(Channels.COLUMN_INPUT_ID, inputId);
            }
            if (channel.getType() == null) {
                // If channel does not include type it will be added
                values.put(Channels.COLUMN_TYPE, Channels.TYPE_OTHER);
            }

            Long rowId = channelMap.get(channel.getOriginalNetworkId());
            Uri uri;
            if (rowId == null) {
                uri = resolver.insert(Channels.CONTENT_URI, values);
                addCount++;
                if (DEBUG) {
                    Log.d(TAG, "Adding channel " + channel.getDisplayName() + " at " + uri);
                }
            } else {
                values.put(Channels._ID, rowId);
                uri = TvContract.buildChannelUri(rowId);
                if (DEBUG) {
                    Log.d(TAG, "Updating channel " + channel.getDisplayName() + " at " + uri);
                }
                resolver.update(uri, values, null, null);
                updateCount++;
                channelMap.remove(channel.getOriginalNetworkId());
            }
            if (channel.getChannelLogo() != null && !TextUtils.isEmpty(channel.getChannelLogo())) {
                logos.put(TvContract.buildChannelLogoUri(uri), channel.getChannelLogo());
            }
        }
        if (!logos.isEmpty()) {
            new InsertLogosTask(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, logos);
        }

        // Deletes channels which don't exist in the new feed.
        int size = channelMap.size();
        for (int i = 0; i < size; ++i) {
            Long rowId = channelMap.valueAt(i);
            if (DEBUG) {
                Log.d(TAG, "Deleting channel " + rowId);
            }
            resolver.delete(TvContract.buildChannelUri(rowId), null, null);
            if (onChannelDeletedCallback != null) {
                onChannelDeletedCallback.onChannelDeleted(rowId);
            }
        }
        Log.i(
                TAG,
                inputId
                        + " sync "
                        + channels.size()
                        + " channels. Deleted "
                        + size
                        + " updated "
                        + updateCount
                        + " added "
                        + addCount);
    }

    /**
     * Builds a map of available channels.
     *
     * @param resolver Application's ContentResolver.
     * @param inputId The ID of the TV input service that provides this TV channel.
     * @return LongSparseArray mapping each channel's {@link Channels#_ID} to the Channel object.
     * @hide
     */
    public static LongSparseArray<Channel> buildChannelMap(
            @NonNull ContentResolver resolver, @NonNull String inputId) {
        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        LongSparseArray<Channel> channelMap = new LongSparseArray<>();
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, Channel.PROJECTION, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                if (DEBUG) {
                    Log.d(TAG, "Cursor is null or found no results");
                }
                return null;
            }

            while (cursor.moveToNext()) {
                Channel nextChannel = Channel.fromCursor(cursor);
                channelMap.put(nextChannel.getId(), nextChannel);
            }
        } catch (Exception e) {
            Log.d(TAG, "Content provider query: " + Arrays.toString(e.getStackTrace()));
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return channelMap;
    }

    /**
     * Returns the current list of channels your app provides.
     *
     * @param resolver Application's ContentResolver.
     * @return List of channels.
     */
    public static List<Channel> getChannels(ContentResolver resolver) {
        List<Channel> channels = new ArrayList<>();
        // TvProvider returns programs in chronological order by default.
        Cursor cursor = null;
        try {
            cursor = resolver.query(Channels.CONTENT_URI, Channel.PROJECTION, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return channels;
            }
            while (cursor.moveToNext()) {
                channels.add(Channel.fromCursor(cursor));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to get channels", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return channels;
    }

    /**
     * Returns the {@link Channel} with specified channel URI.
     *
     * @param resolver {@link ContentResolver} used to query database.
     * @param channelUri URI of channel.
     * @return An channel object with specified channel URI.
     * @hide
     */
    public static Channel getChannel(ContentResolver resolver, Uri channelUri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(channelUri, Channel.PROJECTION, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                Log.w(TAG, "No channel matches " + channelUri);
                return null;
            }
            cursor.moveToNext();
            return Channel.fromCursor(cursor);
        } catch (Exception e) {
            Log.w(TAG, "Unable to get the channel with URI " + channelUri, e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Returns the current list of programs on a given channel.
     *
     * @param resolver Application's ContentResolver.
     * @param channelUri Channel's Uri.
     * @return List of programs.
     * @hide
     */
    public static List<Program> getPrograms(ContentResolver resolver, Uri channelUri) {
        if (channelUri == null) {
            return null;
        }
        Uri uri = TvContract.buildProgramsUriForChannel(channelUri);
        List<Program> programs = new ArrayList<>();
        // TvProvider returns programs in chronological order by default.
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, Program.PROJECTION, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return programs;
            }
            while (cursor.moveToNext()) {
                programs.add(Program.fromCursor(cursor));
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to get programs for " + channelUri, e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return programs;
    }

    /**
     * Returns the program that is scheduled to be playing now on a given channel.
     *
     * @param resolver Application's ContentResolver.
     * @param channelUri Channel's Uri.
     * @return The program that is scheduled for now in the EPG.
     */
    public static Program getCurrentProgram(ContentResolver resolver, Uri channelUri) {
        List<Program> programs = getPrograms(resolver, channelUri);
        if (programs == null) {
            return null;
        }
        long nowMs = System.currentTimeMillis();
        for (Program program : programs) {
            if (program.getStartTimeUtcMillis() <= nowMs && program.getEndTimeUtcMillis() > nowMs) {
                return program;
            }
        }
        return null;
    }

    /**
     * Returns the program that is scheduled to be playing after a given program on a given channel.
     *
     * @param resolver Application's ContentResolver.
     * @param channelUri Channel's Uri.
     * @param currentProgram Program which plays before the desired program.If null, returns current
     *     program
     * @return The program that is scheduled after given program in the EPG.
     */
    public static Program getNextProgram(
            ContentResolver resolver, Uri channelUri, Program currentProgram) {
        if (currentProgram == null) {
            return getCurrentProgram(resolver, channelUri);
        }
        List<Program> programs = getPrograms(resolver, channelUri);
        if (programs == null) {
            return null;
        }
        int currentProgramIndex = programs.indexOf(currentProgram);
        if (currentProgramIndex + 1 < programs.size()) {
            return programs.get(currentProgramIndex + 1);
        }
        return null;
    }

    private ModelUtils() {}
}
