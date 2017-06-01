/*
 * Copyright 2016 The Android Open Source Project.
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

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputService;
import android.os.Build;
import android.text.TextUtils;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import java.util.Objects;

/**
 * A convenience class to create and insert information about previously recorded programs into the
 * database.
 */
@TargetApi(Build.VERSION_CODES.N)
public class RecordedProgram {
    /** @hide */
    public static final String[] PROJECTION =
            new String[] {
                TvContract.RecordedPrograms._ID,
                TvContract.RecordedPrograms.COLUMN_AUDIO_LANGUAGE,
                TvContract.RecordedPrograms.COLUMN_BROADCAST_GENRE,
                TvContract.RecordedPrograms.COLUMN_CANONICAL_GENRE,
                TvContract.RecordedPrograms.COLUMN_CHANNEL_ID,
                TvContract.RecordedPrograms.COLUMN_CONTENT_RATING,
                TvContract.RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS,
                TvContract.RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.RecordedPrograms.COLUMN_EPISODE_TITLE,
                TvContract.RecordedPrograms.COLUMN_INPUT_ID,
                TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA,
                TvContract.RecordedPrograms.COLUMN_LONG_DESCRIPTION,
                TvContract.RecordedPrograms.COLUMN_POSTER_ART_URI,
                TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_BYTES,
                TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI,
                TvContract.RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
                TvContract.RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS,
                TvContract.RecordedPrograms.COLUMN_SEARCHABLE,
                TvContract.RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.RecordedPrograms.COLUMN_SEASON_TITLE,
                TvContract.RecordedPrograms.COLUMN_SHORT_DESCRIPTION,
                TvContract.RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.RecordedPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.RecordedPrograms.COLUMN_TITLE,
                TvContract.RecordedPrograms.COLUMN_VERSION_NUMBER,
                TvContract.RecordedPrograms.COLUMN_VIDEO_HEIGHT,
                TvContract.RecordedPrograms.COLUMN_VIDEO_WIDTH
            };

    private static final String TAG = "RecordedProgram";
    private static final boolean DEBUG = true;
    private static final int INVALID_INT_VALUE = -1;
    private static final int IS_SEARCHABLE = 1;

    private String mAudioLanguages;
    private String[] mBroadcastGenres;
    private String[] mCanonicalGenres;
    private long mChannelId;
    private TvContentRating[] mContentRatings;
    private String mEpisodeDisplayNumber;
    private String mEpisodeTitle;
    private int mId;
    private String mInputId;
    private byte[] mInternalProviderData;
    private String mLongDescription;
    private String mPosterArtUri;
    private long mRecordingDataBytes;
    private String mRecordingDataUri;
    private long mRecordingDurationMillis;
    private long mRecordingExpireTimeUtcMillis;
    private int mSearchable;
    private String mSeasonDisplayNumber;
    private String mSeasonTitle;
    private String mShortDescription;
    private long mStartTimeUtcMillis;
    private long mEndTimeUtcMillis;
    private String mThumbnailUri;
    private String mTitle;
    private int mVersionNumber;
    private int mVideoHeight;
    private int mVideoWidth;

    private RecordedProgram() {
        this.mChannelId = INVALID_INT_VALUE;
        this.mId = INVALID_INT_VALUE;
        this.mSearchable = IS_SEARCHABLE;
        this.mStartTimeUtcMillis = INVALID_INT_VALUE;
        this.mEndTimeUtcMillis = INVALID_INT_VALUE;
        this.mRecordingDataBytes = INVALID_INT_VALUE;
        this.mRecordingDurationMillis = INVALID_INT_VALUE;
        this.mRecordingExpireTimeUtcMillis = INVALID_INT_VALUE;
        this.mVideoHeight = INVALID_INT_VALUE;
        this.mVideoWidth = INVALID_INT_VALUE;
    }

    private void copyFrom(RecordedProgram other) {
        if (this == other) {
            return;
        }

        mAudioLanguages = other.mAudioLanguages;
        mBroadcastGenres = other.mBroadcastGenres;
        mCanonicalGenres = other.mCanonicalGenres;
        mChannelId = other.mChannelId;
        mContentRatings = other.mContentRatings;
        mEpisodeDisplayNumber = other.mEpisodeDisplayNumber;
        mEpisodeTitle = other.mEpisodeTitle;
        mId = other.mId;
        mInputId = other.mInputId;
        mInternalProviderData = other.mInternalProviderData;
        mLongDescription = other.mLongDescription;
        mPosterArtUri = other.mPosterArtUri;
        mRecordingDataBytes = other.mRecordingDataBytes;
        mRecordingDataUri = other.mRecordingDataUri;
        mRecordingDurationMillis = other.mRecordingDurationMillis;
        mRecordingExpireTimeUtcMillis = other.mRecordingExpireTimeUtcMillis;
        mSearchable = other.mSearchable;
        mSeasonDisplayNumber = other.mSeasonDisplayNumber;
        mSeasonTitle = other.mSeasonTitle;
        mShortDescription = other.mShortDescription;
        mStartTimeUtcMillis = other.mStartTimeUtcMillis;
        mEndTimeUtcMillis = other.mEndTimeUtcMillis;
        mThumbnailUri = other.mThumbnailUri;
        mTitle = other.mTitle;
        mVersionNumber = other.mVersionNumber;
        mVideoHeight = other.mVideoHeight;
        mVideoWidth = other.mVideoWidth;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_AUDIO_LANGUAGE} for the
     *     RecordedProgram.
     */
    public String getAudioLanguages() {
        return mAudioLanguages;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_BROADCAST_GENRE} for the
     *     RecordedProgram.
     */
    public String[] getBroadcastGenres() {
        return mBroadcastGenres;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_CANONICAL_GENRE} for the
     *     RecordedProgram.
     */
    public String[] getCanonicalGenres() {
        return mCanonicalGenres;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_CHANNEL_ID} for the
     *     RecordedProgram.
     */
    public long getChannelId() {
        return mChannelId;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_CONTENT_RATING} for the
     *     RecordedProgram.
     */
    public TvContentRating[] getContentRatings() {
        return mContentRatings;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_EPISODE_DISPLAY_NUMBER} for
     *     the RecordedProgram.
     */
    public String getEpisodeDisplayNumber() {
        return mEpisodeDisplayNumber;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_EPISODE_TITLE} for the
     *     RecordedProgram.
     */
    public String getEpisodeTitle() {
        return mEpisodeTitle;
    }

    /** @return The value of {@link TvContract.RecordedPrograms#_ID} for the RecordedProgram. */
    public int getId() {
        return mId;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_INPUT_ID} for the
     *     RecordedProgram.
     */
    public String getInputId() {
        return mInputId;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_INTERNAL_PROVIDER_DATA} for
     *     the RecordedProgram.
     */
    public InternalProviderData getInternalProviderData() {
        if (mInternalProviderData != null) {
            try {
                return new InternalProviderData(mInternalProviderData);
            } catch (InternalProviderData.ParseException e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }
        return null;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_LONG_DESCRIPTION} for the
     *     RecordedProgram.
     */
    public String getLongDescription() {
        return mLongDescription;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_POSTER_ART_URI} for the
     *     RecordedProgram.
     */
    public String getPosterArtUri() {
        return mPosterArtUri;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_SEARCHABLE} for the
     *     RecordedProgram.
     */
    public boolean isSearchable() {
        return mSearchable == IS_SEARCHABLE;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_RECORDING_DATA_BYTES} for the
     *     RecordedProgram.
     */
    public long getRecordingDataBytes() {
        return mRecordingDataBytes;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_RECORDING_DATA_URI} for the
     *     RecordedProgram.
     */
    public String getRecordingDataUri() {
        return mRecordingDataUri;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_RECORDING_DURATION_MILLIS} for
     *     the RecordedProgram.
     */
    public long getRecordingDurationMillis() {
        return mRecordingDurationMillis;
    }

    /**
     * @return The value of {@link
     *     TvContract.RecordedPrograms#COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS} for the
     *     RecordedProgram.
     */
    public long getRecordingExpireTimeUtcMillis() {
        return mRecordingExpireTimeUtcMillis;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_SEASON_DISPLAY_NUMBER} for the
     *     RecordedProgram.
     */
    public String getSeasonDisplayNumber() {
        return mSeasonDisplayNumber;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_SEASON_TITLE} for the
     *     RecordedProgram.
     */
    public String getSeasonTitle() {
        return mSeasonTitle;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_SHORT_DESCRIPTION} for the
     *     RecordedProgram.
     */
    public String getShortDescription() {
        return mShortDescription;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_START_TIME_UTC_MILLIS} for the
     *     RecordedProgram.
     */
    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_END_TIME_UTC_MILLIS} for the
     *     RecordedProgram.
     */
    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_THUMBNAIL_URI} for the
     *     RecordedProgram.
     */
    public String getThumbnailUri() {
        return mThumbnailUri;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_TITLE} for the
     *     RecordedProgram.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_VERSION_NUMBER} for the
     *     RecordedProgram.
     */
    public int getVersionNumber() {
        return mVersionNumber;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_VIDEO_HEIGHT} for the
     *     RecordedProgram.
     */
    public int getVideoHeight() {
        return mVideoHeight;
    }

    /**
     * @return The value of {@link TvContract.RecordedPrograms#COLUMN_VIDEO_WIDTH} for the
     *     RecordedProgram.
     */
    public int getVideoWidth() {
        return mVideoWidth;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof RecordedProgram) {
            return Objects.equals(getAudioLanguages(), ((RecordedProgram) obj).getAudioLanguages());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAudioLanguages());
    }

    /**
     * Converts a RecordedProgram to a Program by linking equivalent fields
     *
     * @return Program with the same properties as this object
     */
    public Program toProgram() {
        Program.Builder builder =
                new Program.Builder()
                        .setAudioLanguages(getAudioLanguages())
                        .setBroadcastGenres(getBroadcastGenres())
                        .setCanonicalGenres(getCanonicalGenres())
                        .setChannelId(getChannelId())
                        .setContentRatings(getContentRatings())
                        .setDescription(getShortDescription())
                        .setEpisodeTitle(getEpisodeTitle())
                        .setLongDescription(getLongDescription())
                        .setPosterArtUri(getPosterArtUri())
                        .setVideoHeight(getVideoHeight())
                        .setVideoWidth(getVideoWidth())
                        .setSearchable(isSearchable())
                        .setSeasonTitle(getSeasonTitle())
                        .setStartTimeUtcMillis(getStartTimeUtcMillis())
                        .setEndTimeUtcMillis(getStartTimeUtcMillis() + getRecordingDurationMillis())
                        .setThumbnailUri(getThumbnailUri())
                        .setTitle(getTitle())
                        .setInternalProviderData(getInternalProviderData());
        if (getEpisodeDisplayNumber() != null) {
            builder.setEpisodeNumber(
                    getEpisodeDisplayNumber(), Integer.parseInt(getEpisodeDisplayNumber()));
        }
        if (getSeasonDisplayNumber() != null) {
            builder.setSeasonNumber(
                    getSeasonDisplayNumber(), Integer.parseInt(getSeasonDisplayNumber()));
        }
        return builder.build();
    }

    /**
     * @return The fields of the RecordedProgram in the ContentValues format to be easily inserted
     *     into the TV Input Framework database.
     * @hide
     */
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        if (mId != INVALID_INT_VALUE) {
            values.put(TvContract.RecordedPrograms._ID, mId);
        } else {
            values.putNull(TvContract.RecordedPrograms._ID);
        }
        if (!TextUtils.isEmpty(mAudioLanguages)) {
            values.put(TvContract.RecordedPrograms.COLUMN_AUDIO_LANGUAGE, mAudioLanguages);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_AUDIO_LANGUAGE);
        }
        if (mBroadcastGenres != null && mBroadcastGenres.length > 0) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_BROADCAST_GENRE,
                    TvContract.Programs.Genres.encode(mBroadcastGenres));
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_BROADCAST_GENRE);
        }
        if (mCanonicalGenres != null && mCanonicalGenres.length > 0) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_CANONICAL_GENRE,
                    TvContract.Programs.Genres.encode(mCanonicalGenres));
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_CANONICAL_GENRE);
        }
        if (mContentRatings != null && mContentRatings.length > 0) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_CONTENT_RATING,
                    TvContractUtils.contentRatingsToString(mContentRatings));
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_BROADCAST_GENRE);
        }
        if (mChannelId != INVALID_INT_VALUE) {
            values.put(TvContract.RecordedPrograms.COLUMN_CHANNEL_ID, mChannelId);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_CHANNEL_ID);
        }
        if (mEndTimeUtcMillis > -1) {
            values.put(TvContract.RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, mEndTimeUtcMillis);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS);
        }
        if (!TextUtils.isEmpty(mEpisodeDisplayNumber)) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER,
                    mEpisodeDisplayNumber);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_EPISODE_DISPLAY_NUMBER);
        }
        if (!TextUtils.isEmpty(mEpisodeTitle)) {
            values.put(TvContract.RecordedPrograms.COLUMN_EPISODE_TITLE, mEpisodeTitle);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_EPISODE_TITLE);
        }
        if (!TextUtils.isEmpty(mInputId)) {
            values.put(TvContract.RecordedPrograms.COLUMN_INPUT_ID, mInputId);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_INPUT_ID);
        }
        if (mInternalProviderData != null && mInternalProviderData.length > 0) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA,
                    mInternalProviderData);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_DATA);
        }
        values.putNull(TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG1);
        values.putNull(TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG2);
        values.putNull(TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG3);
        values.putNull(TvContract.RecordedPrograms.COLUMN_INTERNAL_PROVIDER_FLAG4);
        if (!TextUtils.isEmpty(mLongDescription)) {
            values.put(TvContract.RecordedPrograms.COLUMN_LONG_DESCRIPTION, mLongDescription);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_LONG_DESCRIPTION);
        }
        if (!TextUtils.isEmpty(mPosterArtUri)) {
            values.put(TvContract.RecordedPrograms.COLUMN_POSTER_ART_URI, mPosterArtUri);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_POSTER_ART_URI);
        }
        if (mRecordingDataBytes > -1) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_BYTES, mRecordingDataBytes);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_BYTES);
        }
        if (!TextUtils.isEmpty(mRecordingDataUri)) {
            values.put(TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI, mRecordingDataUri);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_RECORDING_DATA_URI);
        }
        if (mRecordingDurationMillis > -1) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS,
                    mRecordingDurationMillis);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS);
        }
        if (mRecordingExpireTimeUtcMillis > -1) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS,
                    mRecordingExpireTimeUtcMillis);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS);
        }
        values.put(TvContract.RecordedPrograms.COLUMN_SEARCHABLE, mSearchable);
        if (!TextUtils.isEmpty(mSeasonDisplayNumber)) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER, mSeasonDisplayNumber);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_SEASON_DISPLAY_NUMBER);
        }
        if (!TextUtils.isEmpty(mSeasonTitle)) {
            values.put(TvContract.RecordedPrograms.COLUMN_SEASON_TITLE, mSeasonTitle);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_SEASON_TITLE);
        }
        if (!TextUtils.isEmpty(mShortDescription)) {
            values.put(TvContract.RecordedPrograms.COLUMN_SHORT_DESCRIPTION, mShortDescription);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_SHORT_DESCRIPTION);
        }
        if (mStartTimeUtcMillis > -1) {
            values.put(
                    TvContract.RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS, mStartTimeUtcMillis);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS);
        }
        if (!TextUtils.isEmpty(mThumbnailUri)) {
            values.put(TvContract.RecordedPrograms.COLUMN_THUMBNAIL_URI, mThumbnailUri);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_THUMBNAIL_URI);
        }
        if (!TextUtils.isEmpty(mTitle)) {
            values.put(TvContract.RecordedPrograms.COLUMN_TITLE, mTitle);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_TITLE);
        }
        values.put(TvContract.RecordedPrograms.COLUMN_VERSION_NUMBER, mVersionNumber);
        if (mVideoHeight != INVALID_INT_VALUE) {
            values.put(TvContract.RecordedPrograms.COLUMN_VIDEO_HEIGHT, mVideoHeight);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_VIDEO_HEIGHT);
        }
        if (mVideoWidth != INVALID_INT_VALUE) {
            values.put(TvContract.RecordedPrograms.COLUMN_VIDEO_WIDTH, mVideoWidth);
        } else {
            values.putNull(TvContract.RecordedPrograms.COLUMN_VIDEO_WIDTH);
        }
        return values;
    }

    /**
     * Creates a RecordedProgram object from a cursor including the fields defined in {@link
     * TvContract.RecordedPrograms}.
     *
     * @param cursor A row from the TV Input Framework database.
     * @return A RecordedProgram with the values taken from the cursor.
     * @hide
     */
    public static RecordedProgram fromCursor(Cursor cursor) {
        Builder builder = new Builder();
        int index = 0;
        if (!cursor.isNull(index)) {
            builder.setId(cursor.getInt(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setAudioLanguages(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setBroadcastGenres(TvContract.Programs.Genres.decode(cursor.getString(index)));
        }
        if (!cursor.isNull(++index)) {
            builder.setCanonicalGenres(TvContract.Programs.Genres.decode(cursor.getString(index)));
        }
        if (!cursor.isNull(++index)) {
            builder.setChannelId(cursor.getInt(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setContentRatings(
                    TvContractUtils.stringToContentRatings(cursor.getString(index)));
        }
        if (!cursor.isNull(++index)) {
            builder.setEndTimeUtcMillis(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setEpisodeDisplayNumber(
                    cursor.getString(index), Integer.parseInt(cursor.getString(index)));
        }
        if (!cursor.isNull(++index)) {
            builder.setEpisodeTitle(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setInputId(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setInternalProviderData(cursor.getBlob(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setLongDescription(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setPosterArtUri(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setRecordingDataBytes(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setRecordingDataUri(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setRecordingDurationMillis(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setRecordingExpireTimeUtcMillis(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setSearchable(cursor.getInt(index) == IS_SEARCHABLE);
        }
        if (!cursor.isNull(++index)) {
            builder.setSeasonDisplayNumber(
                    cursor.getString(index), Integer.parseInt(cursor.getString(index)));
        }
        if (!cursor.isNull(++index)) {
            builder.setSeasonTitle(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setShortDescription(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setStartTimeUtcMillis(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setThumbnailUri(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setTitle(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setVersionNumber(cursor.getInt(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setVideoHeight(cursor.getInt(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setVideoWidth(cursor.getInt(index));
        }
        return builder.build();
    }

    /** This Builder class simplifies the creation of a {@link RecordedProgram} object. */
    public static class Builder {
        private RecordedProgram mRecordedProgram;

        /** Creates a new Builder object. */
        public Builder() {
            mRecordedProgram = new RecordedProgram();
        }

        /**
         * Creates a new Builder object with values copied from another RecordedProgram.
         *
         * @param other The RecordedProgram you're copying from.
         */
        public Builder(RecordedProgram other) {
            mRecordedProgram = new RecordedProgram();
            mRecordedProgram.copyFrom(other);
        }

        /**
         * Creates a new Builder object with values originally from a Program.
         *
         * @param playingProgram The Program you're copying values from.
         */
        public Builder(Program playingProgram) {
            mRecordedProgram = new RecordedProgram();
            setAudioLanguages(playingProgram.getAudioLanguages());
            setBroadcastGenres(playingProgram.getBroadcastGenres());
            setCanonicalGenres(playingProgram.getCanonicalGenres());
            setChannelId(playingProgram.getChannelId());
            setContentRatings(playingProgram.getContentRatings());
            if (playingProgram.getEpisodeNumber() != null) {
                setEpisodeDisplayNumber(
                        playingProgram.getEpisodeNumber(),
                        Integer.parseInt(playingProgram.getEpisodeNumber()));
            }
            setEpisodeTitle(playingProgram.getEpisodeTitle());
            setInternalProviderData(playingProgram.getInternalProviderData());
            setLongDescription(playingProgram.getLongDescription());
            setPosterArtUri(playingProgram.getPosterArtUri());
            setSearchable(playingProgram.isSearchable());
            if (playingProgram.getSeasonNumber() != null) {
                setSeasonDisplayNumber(
                        playingProgram.getSeasonNumber(),
                        Integer.parseInt(playingProgram.getSeasonNumber()));
            }
            setSeasonTitle(playingProgram.getSeasonTitle());
            setShortDescription(playingProgram.getDescription());
            setStartTimeUtcMillis(playingProgram.getStartTimeUtcMillis());
            setEndTimeUtcMillis(playingProgram.getEndTimeUtcMillis());
            setThumbnailUri(playingProgram.getThumbnailUri());
            setTitle(playingProgram.getTitle());
            setVideoHeight(playingProgram.getVideoHeight());
            setVideoWidth(playingProgram.getVideoWidth());
        }

        /**
         * Sets the available audio languages for this program as a comma-separated String.
         *
         * @param audioLanguages The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_AUDIO_LANGUAGE} for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setAudioLanguages(String audioLanguages) {
            mRecordedProgram.mAudioLanguages = audioLanguages;
            return this;
        }

        /**
         * Sets the broadcast-specified genres of the RecordedProgram.
         *
         * @param genres Array of genres that apply to the RecordedProgram based on the broadcast
         *     standard which will be flattened to a String to store in a database.
         * @return This Builder object to allow for chaining of calls to builder methods.
         * @see TvContract.RecordedPrograms#COLUMN_BROADCAST_GENRE
         */
        public Builder setBroadcastGenres(String[] genres) {
            mRecordedProgram.mBroadcastGenres = genres;
            return this;
        }

        /**
         * Sets the genres of the RecordedProgram.
         *
         * @param genres An array of {@link TvContract.Programs.Genres} that apply to the
         *     RecordedProgram which will be flattened to a String to store in a database.
         * @return This Builder object to allow for chaining of calls to builder methods.
         * @see TvContract.RecordedPrograms#COLUMN_CANONICAL_GENRE
         */
        public Builder setCanonicalGenres(String[] genres) {
            mRecordedProgram.mCanonicalGenres = genres;
            return this;
        }

        /**
         * Sets the ID of the {@link Channel} that contains this RecordedProgram.
         *
         * @param channelId The value of {@link TvContract.RecordedPrograms#COLUMN_CHANNEL_ID for
         * the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        private Builder setChannelId(long channelId) {
            mRecordedProgram.mChannelId = channelId;
            return this;
        }

        /**
         * Sets the content ratings for this RecordedProgram.
         *
         * @param contentRatings An array of {@link TvContentRating} that apply to this
         *     RecordedProgram which will be flattened to a String to store in a database.
         * @return This Builder object to allow for chaining of calls to builder methods.
         * @see TvContract.RecordedPrograms#COLUMN_CONTENT_RATING
         */
        public Builder setContentRatings(TvContentRating[] contentRatings) {
            mRecordedProgram.mContentRatings = contentRatings;
            return this;
        }

        /**
         * Sets the time when this program is going to end in milliseconds since the epoch.
         *
         * @param endTimeUtcMillis The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_END_TIME_UTC_MILLIS} for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setEndTimeUtcMillis(long endTimeUtcMillis) {
            mRecordedProgram.mEndTimeUtcMillis = endTimeUtcMillis;
            return this;
        }

        /**
         * Sets the episode number in a season for this episode for a series.
         *
         * @param episodeDisplayNumber The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_EPISODE_DISPLAY_NUMBER} for the program.
         * @param episodeNumber An integer value which will be used for API Level 23 and below.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setEpisodeDisplayNumber(String episodeDisplayNumber, int episodeNumber) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mRecordedProgram.mEpisodeDisplayNumber = episodeDisplayNumber;
            } else {
                mRecordedProgram.mEpisodeDisplayNumber = String.valueOf(episodeNumber);
            }
            return this;
        }

        /**
         * Sets the title of this particular episode for a series.
         *
         * @param episodeTitle The value of {@link TvContract.Programs#COLUMN_EPISODE_TITLE} for the
         *     RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setEpisodeTitle(String episodeTitle) {
            mRecordedProgram.mEpisodeTitle = episodeTitle;
            return this;
        }

        private Builder setId(int id) {
            mRecordedProgram.mId = id;
            return this;
        }

        /**
         * Sets the input id for the {@link TvInputService} that recorded this RecordedProgram.
         *
         * @param inputId The value of {@link TvContract.RecordedPrograms#COLUMN_INPUT_ID} for the
         *     RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setInputId(String inputId) {
            mRecordedProgram.mInputId = inputId;
            return this;
        }

        /**
         * Sets the internal provider data for the RecordedProgram as raw bytes.
         *
         * @param data The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_INTERNAL_PROVIDER_DATA} for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setInternalProviderData(byte[] data) {
            mRecordedProgram.mInternalProviderData = data;
            return this;
        }

        /**
         * Sets the internal provider data for the RecordedProgram.
         *
         * @param internalProviderData The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_INTERNAL_PROVIDER_DATA} for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setInternalProviderData(InternalProviderData internalProviderData) {
            if (internalProviderData != null) {
                mRecordedProgram.mInternalProviderData = internalProviderData.toString().getBytes();
            }
            return this;
        }

        /**
         * Sets a longer description of a RecordedProgram if one exists.
         *
         * @param longDescription The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_LONG_DESCRIPTION} for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setLongDescription(String longDescription) {
            mRecordedProgram.mLongDescription = longDescription;
            return this;
        }

        /**
         * Sets the large poster art of the RecordedProgram.
         *
         * @param posterArtUri The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_POSTER_ART_URI} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setPosterArtUri(String posterArtUri) {
            mRecordedProgram.mPosterArtUri = posterArtUri;
            return this;
        }

        /**
         * Sets the storage size of this RecordedProgram.
         *
         * @param bytes The value of {@link TvContract.RecordedPrograms#COLUMN_RECORDING_DATA_BYTES}
         *     for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setRecordingDataBytes(long bytes) {
            mRecordedProgram.mRecordingDataBytes = bytes;
            return this;
        }

        /**
         * Sets the source location of this RecordedProgram.
         *
         * @param uri The value of {@link TvContract.RecordedPrograms#COLUMN_RECORDING_DATA_URI} for
         *     the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setRecordingDataUri(String uri) {
            mRecordedProgram.mRecordingDataUri = uri;
            return this;
        }

        /**
         * Sets the video duration of this RecordedProgram, which may not be equal to the difference
         * in the start and end times in the case that the user manually stops recording early.
         *
         * @param duration The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_RECORDING_DURATION_MILLIS} for the
         *     RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setRecordingDurationMillis(long duration) {
            mRecordedProgram.mRecordingDurationMillis = duration;
            return this;
        }

        /**
         * Sets a time after which the RecordedProgram will not longer be available. By default the
         * RecordedProgram has no expiration time.
         *
         * @param expireTimeUtcMillis The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS} for the
         *     RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setRecordingExpireTimeUtcMillis(long expireTimeUtcMillis) {
            mRecordedProgram.mRecordingExpireTimeUtcMillis = expireTimeUtcMillis;
            return this;
        }

        /**
         * Sets whether this RecordedProgram can be searched for in other applications.
         *
         * @param searchable The value of {@link TvContract.RecordedPrograms#COLUMN_SEARCHABLE} for
         *     the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setSearchable(boolean searchable) {
            mRecordedProgram.mSearchable = searchable ? IS_SEARCHABLE : 0;
            return this;
        }

        /**
         * Sets the season number for this episode for a series.
         *
         * @param seasonDisplayNumber The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_SEASON_DISPLAY_NUMBER} for the RecordedProgram.
         * @param seasonNumber An integer value which will be used for API Level 23 and below.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setSeasonDisplayNumber(String seasonDisplayNumber, int seasonNumber) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mRecordedProgram.mSeasonDisplayNumber = seasonDisplayNumber;
            } else {
                mRecordedProgram.mSeasonDisplayNumber = String.valueOf(seasonNumber);
            }
            return this;
        }

        /**
         * Sets a custom name for the season, if applicable.
         *
         * @param seasonTitle The value of {@link TvContract.RecordedPrograms#COLUMN_SEASON_TITLE}
         *     for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setSeasonTitle(String seasonTitle) {
            mRecordedProgram.mSeasonTitle = seasonTitle;
            return this;
        }

        /**
         * Sets a brief description of the RecordedProgram.
         *
         * @param shortDescription The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_SHORT_DESCRIPTION} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setShortDescription(String shortDescription) {
            mRecordedProgram.mShortDescription = shortDescription;
            return this;
        }

        /**
         * Sets the time when the RecordedProgram is going to begin in milliseconds since the epoch.
         *
         * @param startTimeUtcMillis The value of {@link
         *     TvContract.RecordedPrograms#COLUMN_START_TIME_UTC_MILLIS} for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mRecordedProgram.mStartTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        /**
         * Sets a small thumbnail of the RecordedProgram.
         *
         * @param thumbnailUri The value of {@link TvContract.RecordedPrograms#COLUMN_THUMBNAIL_URI}
         *     for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setThumbnailUri(String thumbnailUri) {
            mRecordedProgram.mThumbnailUri = thumbnailUri;
            return this;
        }

        /**
         * Sets the title of this RecordedProgram. For a series, this is the series title.
         *
         * @param title The value of {@link TvContract.RecordedPrograms#COLUMN_TITLE} for the
         *     RecordedPrograms.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setTitle(String title) {
            mRecordedProgram.mTitle = title;
            return this;
        }

        private Builder setVersionNumber(int versionNumber) {
            mRecordedProgram.mVersionNumber = versionNumber;
            return this;
        }

        /**
         * Sets the video height of the RecordedProgram.
         *
         * @param videoHeight The value of {@link TvContract.RecordedPrograms#COLUMN_VIDEO_HEIGHT}
         *     for the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setVideoHeight(int videoHeight) {
            mRecordedProgram.mVideoHeight = videoHeight;
            return this;
        }

        /**
         * Sets the video width of the RecordedProgram.
         *
         * @param videoWidth The value of {@link TvContract.RecordedPrograms#COLUMN_VIDEO_WIDTH} for
         *     the RecordedProgram.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setVideoWidth(int videoWidth) {
            mRecordedProgram.mVideoWidth = videoWidth;
            return this;
        }

        /** @return A new Program with values supplied by the Builder. */
        public RecordedProgram build() {
            RecordedProgram recordedProgram = new RecordedProgram();
            recordedProgram.copyFrom(mRecordedProgram);
            if (recordedProgram.getInputId() == null) {
                throw new IllegalArgumentException(
                        "This recorded program does not have an Input Id");
            }
            if (recordedProgram.getRecordingDurationMillis() == INVALID_INT_VALUE
                    && recordedProgram.getEndTimeUtcMillis() > 0) {
                // Set recording duration based on default properties
                recordedProgram.mRecordingDurationMillis =
                        recordedProgram.getEndTimeUtcMillis()
                                - recordedProgram.getStartTimeUtcMillis();
            }
            return recordedProgram;
        }
    }
}
