/*
 * Copyright 2016 Google Inc. All rights reserved.
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

import android.support.annotation.NonNull;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is a serialized class used for storing and retrieving serialized data from {@link
 * android.media.tv.TvContract.Channels#COLUMN_INTERNAL_PROVIDER_DATA}, {@link
 * android.media.tv.TvContract.Programs#COLUMN_INTERNAL_PROVIDER_DATA}, and {@link
 * android.media.tv.TvContract.RecordedPrograms#COLUMN_INTERNAL_PROVIDER_DATA}.
 *
 * <p>In addition to developers being able to add custom attributes to this data type, there are
 * pre-defined values.
 */
public class InternalProviderData {
    private static final String TAG = "InternalProviderData";
    private static final boolean DEBUG = true;

    private static final String KEY_VIDEO_TYPE = "type";
    private static final String KEY_VIDEO_URL = "url";
    private static final String KEY_REPEATABLE = "repeatable";
    private static final String KEY_CUSTOM_DATA = "custom";
    private static final String KEY_ADVERTISEMENTS = "advertisements";
    private static final String KEY_ADVERTISEMENT_START = "start";
    private static final String KEY_ADVERTISEMENT_STOP = "stop";
    private static final String KEY_ADVERTISEMENT_TYPE = "type";
    private static final String KEY_ADVERTISEMENT_REQUEST_URL = "requestUrl";
    private static final String KEY_RECORDING_START_TIME = "recordingStartTime";

    private JSONObject mJsonObject;

    /** Creates a new empty object */
    public InternalProviderData() {
        mJsonObject = new JSONObject();
    }

    /**
     * Creates a new object and attempts to populate from the provided String
     *
     * @param data Correctly formatted InternalProviderData
     * @throws ParseException If data is not formatted correctly
     */
    public InternalProviderData(@NonNull String data) throws ParseException {
        try {
            mJsonObject = new JSONObject(data);
        } catch (JSONException e) {
            throw new ParseException(e.getMessage());
        }
    }

    /**
     * Creates a new object and attempts to populate by obtaining the String representation of the
     * provided byte array
     *
     * @param bytes Byte array corresponding to a correctly formatted String representation of
     *     InternalProviderData
     * @throws ParseException If data is not formatted correctly
     */
    public InternalProviderData(@NonNull byte[] bytes) throws ParseException {
        try {
            mJsonObject = new JSONObject(new String(bytes));
        } catch (JSONException e) {
            throw new ParseException(e.getMessage());
        }
    }

    private int jsonHash(JSONObject jsonObject) {
        int hashSum = 0;
        Iterator<String> keys = jsonObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                if (jsonObject.get(key) instanceof JSONObject) {
                    // This is a branch, get hash of this object recursively
                    JSONObject branch = jsonObject.getJSONObject(key);
                    hashSum += jsonHash(branch);
                } else {
                    // If this key does not link to a JSONObject, get hash of leaf
                    hashSum += key.hashCode() + jsonObject.get(key).hashCode();
                }
            } catch (JSONException ignored) {
            }
        }
        return hashSum;
    }

    @Override
    public int hashCode() {
        // Recursively get the hashcode from all internal JSON keys and values
        return jsonHash(mJsonObject);
    }

    private boolean jsonEquals(JSONObject json1, JSONObject json2) {
        Iterator<String> keys = json1.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                if (json1.get(key) instanceof JSONObject) {
                    // This is a branch, check equality of this object recursively
                    JSONObject thisBranch = json1.getJSONObject(key);
                    JSONObject otherBranch = json2.getJSONObject(key);
                    return jsonEquals(thisBranch, otherBranch);
                } else {
                    // If this key does not link to a JSONObject, check equality of leaf
                    if (!json1.get(key).equals(json2.get(key))) {
                        // The VALUE of the KEY does not match
                        return false;
                    }
                }
            } catch (JSONException e) {
                return false;
            }
        }
        // Confirm that no key has been missed in the check
        return json1.length() == json2.length();
    }

    /**
     * Tests that the value of each key is equal. Order does not matter.
     *
     * @param obj The object you are comparing to.
     * @return Whether the value of each key between both objects is equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof InternalProviderData)) {
            return false;
        }
        JSONObject otherJsonObject = ((InternalProviderData) obj).mJsonObject;
        return jsonEquals(mJsonObject, otherJsonObject);
    }

    @Override
    public String toString() {
        return mJsonObject.toString();
    }

    /**
     * Gets the video type of the program.
     *
     * @return The video type of the program, -1 if no value has been given.
     */
    public int getVideoType() {
        if (mJsonObject.has(KEY_VIDEO_TYPE)) {
            try {
                return mJsonObject.getInt(KEY_VIDEO_TYPE);
            } catch (JSONException ignored) {
            }
        }
        return TvContractUtils.SOURCE_TYPE_INVALID;
    }

    /**
     * Sets the video type of the program.
     *
     * @param videoType The video source type. Could be {@link TvContractUtils#SOURCE_TYPE_HLS},
     *     {@link TvContractUtils#SOURCE_TYPE_HTTP_PROGRESSIVE}, or {@link
     *     TvContractUtils#SOURCE_TYPE_MPEG_DASH}.
     */
    public void setVideoType(int videoType) {
        try {
            mJsonObject.put(KEY_VIDEO_TYPE, videoType);
        } catch (JSONException ignored) {
        }
    }

    /**
     * Gets the video url of the program if valid.
     *
     * @return The video url of the program if valid, null if no value has been given.
     */
    public String getVideoUrl() {
        if (mJsonObject.has(KEY_VIDEO_URL)) {
            try {
                return mJsonObject.getString(KEY_VIDEO_URL);
            } catch (JSONException ignored) {
            }
        }
        return null;
    }

    /**
     * Gets a list of all advertisements. If no ads have been assigned, the list will be empty.
     *
     * @return A list of all advertisements for this channel or program.
     */
    public List<Advertisement> getAds() {
        List<Advertisement> ads = new ArrayList<>();
        try {
            if (mJsonObject.has(KEY_ADVERTISEMENTS)) {
                JSONArray adsJsonArray =
                        new JSONArray(mJsonObject.get(KEY_ADVERTISEMENTS).toString());
                for (int i = 0; i < adsJsonArray.length(); i++) {
                    JSONObject ad = adsJsonArray.getJSONObject(i);
                    long start = ad.getLong(KEY_ADVERTISEMENT_START);
                    long stop = ad.getLong(KEY_ADVERTISEMENT_STOP);
                    int type = ad.getInt(KEY_ADVERTISEMENT_TYPE);
                    String requestUrl = ad.getString(KEY_ADVERTISEMENT_REQUEST_URL);
                    ads.add(
                            new Advertisement.Builder()
                                    .setStartTimeUtcMillis(start)
                                    .setStopTimeUtcMillis(stop)
                                    .setType(type)
                                    .setRequestUrl(requestUrl)
                                    .build());
                }
            }
        } catch (JSONException ignored) {
        }
        return ads;
    }

    /**
     * Gets recording start time of program for recorded program. For a non-recorded program, this
     * value will not be set.
     *
     * @return Recording start of program in UTC milliseconds, 0 if no value is given.
     */
    public long getRecordedProgramStartTime() {
        try {
            return mJsonObject.getLong(KEY_RECORDING_START_TIME);
        } catch (JSONException ignored) {
        }
        return 0;
    }

    /**
     * Sets the video url of the program.
     *
     * @param videoUrl A valid url pointing to the video to be played.
     */
    public void setVideoUrl(String videoUrl) {
        try {
            mJsonObject.put(KEY_VIDEO_URL, videoUrl);
        } catch (JSONException ignored) {
        }
    }

    /**
     * Checks whether the programs on this channel should be repeated periodically in order.
     *
     * @return Whether to repeat programs. Returns false if no value has been set.
     */
    public boolean isRepeatable() {
        if (mJsonObject.has(KEY_REPEATABLE)) {
            try {
                return mJsonObject.getBoolean(KEY_REPEATABLE);
            } catch (JSONException ignored) {
            }
        }
        return false;
    }

    /**
     * Sets whether programs assigned to this channel should be repeated periodically. This field is
     * relevant to channels.
     *
     * @param repeatable Whether to repeat programs.
     */
    public void setRepeatable(boolean repeatable) {
        try {
            mJsonObject.put(KEY_REPEATABLE, repeatable);
        } catch (JSONException ignored) {
        }
    }

    /**
     * Sets a list of advertisements for this channel or program. If setting for a channel, list
     * size should be <= 1. Channels cannot have more than one advertisement.
     *
     * @param ads A list of advertisements that should be shown.
     */
    public void setAds(List<Advertisement> ads) {
        try {
            if (ads != null && !ads.isEmpty()) {
                JSONArray adsJsonArray = new JSONArray();
                for (Advertisement ad : ads) {
                    JSONObject adJson = new JSONObject();
                    adJson.put(KEY_ADVERTISEMENT_START, ad.getStartTimeUtcMillis());
                    adJson.put(KEY_ADVERTISEMENT_STOP, ad.getStopTimeUtcMillis());
                    adJson.put(KEY_ADVERTISEMENT_TYPE, ad.getType());
                    adJson.put(KEY_ADVERTISEMENT_REQUEST_URL, ad.getRequestUrl());
                    adsJsonArray.put(adJson);
                }
                mJsonObject.put(KEY_ADVERTISEMENTS, adsJsonArray);
            }
        } catch (JSONException ignored) {
        }
    }

    /**
     * Sets the recording program start time for a recorded program.
     *
     * @param startTime Recording start time in UTC milliseconds of recorded program.
     */
    public void setRecordingStartTime(long startTime) {
        try {
            mJsonObject.put(KEY_RECORDING_START_TIME, startTime);
        } catch (JSONException ignored) {
        }
    }

    /**
     * Adds some custom data to the InternalProviderData.
     *
     * @param key The key for this data
     * @param value The value this data should take
     * @return This InternalProviderData object to allow for chaining of calls
     * @throws ParseException If there is a problem adding custom data
     */
    public InternalProviderData put(String key, Object value) throws ParseException {
        try {
            if (!mJsonObject.has(KEY_CUSTOM_DATA)) {
                mJsonObject.put(KEY_CUSTOM_DATA, new JSONObject());
            }
            mJsonObject.getJSONObject(KEY_CUSTOM_DATA).put(key, String.valueOf(value));
        } catch (JSONException e) {
            throw new ParseException(e.getMessage());
        }
        return this;
    }

    /**
     * Gets some previously added custom data stored in InternalProviderData.
     *
     * @param key The key assigned to this data
     * @return The value of this key if it has been defined. Returns null if the key is not found.
     * @throws ParseException If there is a problem getting custom data
     */
    public Object get(String key) throws ParseException {
        if (!mJsonObject.has(KEY_CUSTOM_DATA)) {
            return null;
        }
        try {
            return mJsonObject.getJSONObject(KEY_CUSTOM_DATA).opt(key);
        } catch (JSONException e) {
            throw new ParseException(e.getMessage());
        }
    }

    /**
     * Checks whether a custom key is found in InternalProviderData.
     *
     * @param key The key assigned to this data
     * @return Whether this key is found.
     * @throws ParseException If there is a problem checking custom data
     */
    public boolean has(String key) throws ParseException {
        if (!mJsonObject.has(KEY_CUSTOM_DATA)) {
            return false;
        }
        try {
            return mJsonObject.getJSONObject(KEY_CUSTOM_DATA).has(key);
        } catch (JSONException e) {
            throw new ParseException(e.getMessage());
        }
    }

    /**
     * This exception is thrown when an error occurs in getting or setting data for the
     * InternalProviderData.
     */
    public class ParseException extends JSONException {
        public ParseException(String s) {
            super(s);
        }
    }
}
