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

package com.example.android.sampletvinput.utils;

import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sampletvinput.model.Advertisement;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Static helper methods for working with
 * {@link android.media.tv.TvContract.Programs#COLUMN_INTERNAL_PROVIDER_DATA}.
 */
public class InternalProviderDataUtil {
    private static final String TAG = "InternalProviderData";

    private static final String VIDEO_TYPE = "videoType";
    private static final String VIDEO_URL = "videoUrl";
    private static final String ADVERTISEMENTS = "advertisements";
    private static final String ADVERTISEMENT_START = "start";
    private static final String ADVERTISEMENT_STOP = "stop";
    private static final String ADVERTISEMENT_TYPE = "type";
    private static final String ADVERTISEMENT_REQUEST_URL = "requestUrl";

    /**
     * Converts video information to a string in order to add them into a database.
     *
     * @param videoType The video format: {@link TvContractUtils#SOURCE_TYPE_HLS},
     * {@link TvContractUtils#SOURCE_TYPE_HTTP_PROGRESSIVE},
     * or {@link TvContractUtils#SOURCE_TYPE_MPEG_DASH}.
     * @param videoUrl The source location for this program's video.
     * @param ads A list of advertisements in this video.
     * @return A string contains video type, source URL and advertisements information.
     */
    public static String convertVideoInfo(int videoType, String videoUrl, List<Advertisement> ads) {
        JSONObject videoInfoJson = new JSONObject();
        try {
            videoInfoJson.put(VIDEO_TYPE, videoType);
            videoInfoJson.put(VIDEO_URL, videoUrl);
            if (ads != null && !ads.isEmpty()) {
                JSONArray adsJsonArray = new JSONArray();
                for (Advertisement ad : ads) {
                    JSONObject adJson = new JSONObject();
                    adJson.put(ADVERTISEMENT_START, ad.getStartTimeUtcMillis());
                    adJson.put(ADVERTISEMENT_STOP, ad.getStopTimeUtcMillis());
                    adJson.put(ADVERTISEMENT_TYPE, ad.getType());
                    adJson.put(ADVERTISEMENT_REQUEST_URL, ad.getRequestUrl());
                    adsJsonArray.put(adJson);
                }
                videoInfoJson.put(ADVERTISEMENTS, adsJsonArray);
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return videoInfoJson.toString();
    }

    /**
     * Parses video information string from {@link #convertVideoInfo} to get the video format.
     *
     * @param internalData A string contains video type, source URL and advertisements information.
     * @return The video format: {@link TvContractUtils#SOURCE_TYPE_HLS},
     * {@link TvContractUtils#SOURCE_TYPE_HTTP_PROGRESSIVE},
     * or {@link TvContractUtils#SOURCE_TYPE_MPEG_DASH}.
     */
    public static int parseVideoType(@NonNull String internalData) {
        try {
            JSONObject videoInfoJson = new JSONObject(internalData);
            return videoInfoJson.getInt(VIDEO_TYPE);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Parses video information string from {@link #convertVideoInfo} to get the video source URL.
     *
     * @param internalData A string contains video type, source URL and advertisements information.
     * @return The source location for this program's video.
     */
    public static String parseVideoUrl(@NonNull String internalData) {
        try {
            JSONObject videoInfoJson = new JSONObject(internalData);
            return videoInfoJson.getString(VIDEO_URL);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Parses video information string from {@link #convertVideoInfo} to get a list of all
     * advertisements contained in this video.
     *
     * @param internalData A string contains video type, source URL and advertisements information.
     * @return A list of all advertisements contained in this video.
     */
    public static List<Advertisement> parseAds(String internalData) {
        List<Advertisement> ads = new ArrayList<>();
        if (internalData == null) {
            return ads;
        }
        try {
            JSONObject videoInfoJson = new JSONObject(internalData);
            if (!videoInfoJson.has(ADVERTISEMENTS)) {
                return ads;
            }
            JSONArray adsJsonArray = videoInfoJson.getJSONArray(ADVERTISEMENTS);
            for (int i = 0; i < adsJsonArray.length(); i++) {
                JSONObject ad = adsJsonArray.getJSONObject(i);
                long start = ad.getLong(ADVERTISEMENT_START);
                long stop = ad.getLong(ADVERTISEMENT_STOP);
                int type = ad.getInt(ADVERTISEMENT_TYPE);
                String requestUrl = ad.getString(ADVERTISEMENT_REQUEST_URL);
                ads.add(new Advertisement.Builder()
                        .setStartTimeUtcMillis(start)
                        .setStopTimeUtcMillis(stop)
                        .setType(type)
                        .setRequestUrl(requestUrl)
                        .build());
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return ads;
    }

    /**
     * Shift advertisement time to match program playback time. For channels with repeated program,
     * the time for current program may vary from what it was defined previously.
     *
     * @param oldInternalProviderData InternalProviderData to be updated.
     * @param oldProgramStartTimeMs Outdated program start time.
     * @param newProgramStartTimeMs Updated program start time.
     * @return InternalProviderData with updated advertisement time.
     */
    public static String shiftAdsTimeWithProgram(String oldInternalProviderData,
                                                        long oldProgramStartTimeMs, long newProgramStartTimeMs) {
        if (oldInternalProviderData == null) {
            Log.w(TAG, "InternalProviderData is null, skipping advertisement time shift.");
            return null;
        }
        long timeShift = newProgramStartTimeMs - oldProgramStartTimeMs;
        List<Advertisement> oldAds = parseAds(oldInternalProviderData);
        List<Advertisement> newAds = new ArrayList<>();
        for (Advertisement oldAd : oldAds) {
            newAds.add(new Advertisement.Builder(oldAd)
                    .setStartTimeUtcMillis(oldAd.getStartTimeUtcMillis() + timeShift)
                    .setStopTimeUtcMillis(oldAd.getStopTimeUtcMillis() + timeShift)
                    .build());
        }
        int videoType = parseVideoType(oldInternalProviderData);
        String videoUrl = parseVideoUrl(oldInternalProviderData);
        return convertVideoInfo(videoType, videoUrl, newAds);
    }
}
