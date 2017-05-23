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

import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.google.android.media.tv.companionlibrary.utils.CollectionUtils;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;
import java.util.Arrays;
import java.util.Objects;

/** A convenience class to create and insert program information into the database. */
public final class Program implements Comparable<Program> {
    /** @hide */
    public static final String[] PROJECTION = getProjection();

    private static final long INVALID_LONG_VALUE = -1;
    private static final int INVALID_INT_VALUE = -1;
    private static final int IS_RECORDING_PROHIBITED = 1;
    private static final int IS_SEARCHABLE = 1;

    private long mId;
    private long mChannelId;
    private String mTitle;
    private String mEpisodeTitle;
    private String mSeasonNumber;
    private String mEpisodeNumber;
    private long mStartTimeUtcMillis;
    private long mEndTimeUtcMillis;
    private String mDescription;
    private String mLongDescription;
    private int mVideoWidth;
    private int mVideoHeight;
    private String mPosterArtUri;
    private String mThumbnailUri;
    private String[] mBroadcastGenres;
    private String[] mCanonicalGenres;
    private TvContentRating[] mContentRatings;
    private byte[] mInternalProviderData;
    private String mAudioLanguages;
    private int mRecordingProhibited;
    private int mSearchable;
    private String mSeasonTitle;

    private Program() {
        mChannelId = INVALID_LONG_VALUE;
        mId = INVALID_LONG_VALUE;
        mStartTimeUtcMillis = INVALID_LONG_VALUE;
        mEndTimeUtcMillis = INVALID_LONG_VALUE;
        mVideoWidth = INVALID_INT_VALUE;
        mVideoHeight = INVALID_INT_VALUE;
        mSearchable = IS_SEARCHABLE;
    }

    /** @return The value of {@link TvContract.Programs#_ID} for the channel. */
    public long getId() {
        return mId;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_CHANNEL_ID} for the channel. */
    public long getChannelId() {
        return mChannelId;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_TITLE} for the channel. */
    public String getTitle() {
        return mTitle;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_EPISODE_TITLE} for the channel. */
    public String getEpisodeTitle() {
        return mEpisodeTitle;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_SEASON_DISPLAY_NUMBER} for the
     *     channel.
     */
    public String getSeasonNumber() {
        return mSeasonNumber;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_EPISODE_DISPLAY_NUMBER} for the
     *     channel.
     */
    public String getEpisodeNumber() {
        return mEpisodeNumber;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_START_TIME_UTC_MILLIS} for the
     *     channel.
     */
    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_END_TIME_UTC_MILLIS} for the channel.
     */
    public long getEndTimeUtcMillis() {
        return mEndTimeUtcMillis;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_SHORT_DESCRIPTION} for the channel.
     */
    public String getDescription() {
        return mDescription;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_LONG_DESCRIPTION} for the channel. */
    public String getLongDescription() {
        return mLongDescription;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_VIDEO_WIDTH} for the channel. */
    public int getVideoWidth() {
        return mVideoWidth;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_VIDEO_HEIGHT} for the channel. */
    public int getVideoHeight() {
        return mVideoHeight;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_BROADCAST_GENRE} for the channel. */
    public String[] getBroadcastGenres() {
        return mBroadcastGenres;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_CANONICAL_GENRE} for the channel. */
    public String[] getCanonicalGenres() {
        return mCanonicalGenres;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_CONTENT_RATING} for the channel. */
    public TvContentRating[] getContentRatings() {
        return mContentRatings;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_POSTER_ART_URI} for the channel. */
    public String getPosterArtUri() {
        return mPosterArtUri;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_THUMBNAIL_URI} for the channel. */
    public String getThumbnailUri() {
        return mThumbnailUri;
    }

    /**
     * @return The value of {@link TvContract.Channels#COLUMN_INTERNAL_PROVIDER_DATA} for the
     *     channel.
     */
    public byte[] getInternalProviderDataByteArray() {
        return mInternalProviderData;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_INTERNAL_PROVIDER_DATA} for the
     *     channel.
     */
    public InternalProviderData getInternalProviderData() {
        if (mInternalProviderData != null) {
            try {
                return new InternalProviderData(mInternalProviderData);
            } catch (InternalProviderData.ParseException e) {
                return null;
            }
        }
        return null;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_AUDIO_LANGUAGE} for the channel. */
    public String getAudioLanguages() {
        return mAudioLanguages;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_RECORDING_PROHIBITED} for the channel.
     */
    public boolean isRecordingProhibited() {
        return mRecordingProhibited == IS_RECORDING_PROHIBITED;
    }

    /**
     * @return The value of {@link TvContract.Programs#COLUMN_RECORDING_PROHIBITED} for the channel.
     */
    public boolean isSearchable() {
        return mSearchable == IS_SEARCHABLE;
    }

    /** @return The value of {@link TvContract.Programs#COLUMN_SEASON_TITLE} for the channel. */
    public String getSeasonTitle() {
        return mSeasonTitle;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mChannelId,
                mStartTimeUtcMillis,
                mEndTimeUtcMillis,
                mTitle,
                mEpisodeTitle,
                mDescription,
                mLongDescription,
                mVideoWidth,
                mVideoHeight,
                mPosterArtUri,
                mThumbnailUri,
                Arrays.hashCode(mContentRatings),
                Arrays.hashCode(mCanonicalGenres),
                mSeasonNumber,
                mEpisodeNumber);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Program)) {
            return false;
        }
        Program program = (Program) other;
        return mChannelId == program.mChannelId
                && mStartTimeUtcMillis == program.mStartTimeUtcMillis
                && mEndTimeUtcMillis == program.mEndTimeUtcMillis
                && Objects.equals(mTitle, program.mTitle)
                && Objects.equals(mEpisodeTitle, program.mEpisodeTitle)
                && Objects.equals(mDescription, program.mDescription)
                && Objects.equals(mLongDescription, program.mLongDescription)
                && mVideoWidth == program.mVideoWidth
                && mVideoHeight == program.mVideoHeight
                && Objects.equals(mPosterArtUri, program.mPosterArtUri)
                && Objects.equals(mThumbnailUri, program.mThumbnailUri)
                && Arrays.equals(mInternalProviderData, program.mInternalProviderData)
                && Arrays.equals(mContentRatings, program.mContentRatings)
                && Arrays.equals(mCanonicalGenres, program.mCanonicalGenres)
                && Objects.equals(mSeasonNumber, program.mSeasonNumber)
                && Objects.equals(mEpisodeNumber, program.mEpisodeNumber);
    }

    /**
     * @param other The program you're comparing to.
     * @return The chronological order of the programs.
     */
    @Override
    public int compareTo(@NonNull Program other) {
        return Long.compare(mStartTimeUtcMillis, other.mStartTimeUtcMillis);
    }

    @Override
    public String toString() {
        return "Program{"
                + "id="
                + mId
                + ", channelId="
                + mChannelId
                + ", title="
                + mTitle
                + ", episodeTitle="
                + mEpisodeTitle
                + ", seasonNumber="
                + mSeasonNumber
                + ", episodeNumber="
                + mEpisodeNumber
                + ", startTimeUtcSec="
                + mStartTimeUtcMillis
                + ", endTimeUtcSec="
                + mEndTimeUtcMillis
                + ", videoWidth="
                + mVideoWidth
                + ", videoHeight="
                + mVideoHeight
                + ", contentRatings="
                + Arrays.toString(mContentRatings)
                + ", posterArtUri="
                + mPosterArtUri
                + ", thumbnailUri="
                + mThumbnailUri
                + ", contentRatings="
                + Arrays.toString(mContentRatings)
                + ", genres="
                + Arrays.toString(mCanonicalGenres)
                + "}";
    }

    private void copyFrom(Program other) {
        if (this == other) {
            return;
        }

        mId = other.mId;
        mChannelId = other.mChannelId;
        mTitle = other.mTitle;
        mEpisodeTitle = other.mEpisodeTitle;
        mSeasonNumber = other.mSeasonNumber;
        mEpisodeNumber = other.mEpisodeNumber;
        mStartTimeUtcMillis = other.mStartTimeUtcMillis;
        mEndTimeUtcMillis = other.mEndTimeUtcMillis;
        mDescription = other.mDescription;
        mLongDescription = other.mLongDescription;
        mVideoWidth = other.mVideoWidth;
        mVideoHeight = other.mVideoHeight;
        mPosterArtUri = other.mPosterArtUri;
        mThumbnailUri = other.mThumbnailUri;
        mBroadcastGenres = other.mBroadcastGenres;
        mCanonicalGenres = other.mCanonicalGenres;
        mContentRatings = other.mContentRatings;
        mAudioLanguages = other.mAudioLanguages;
        mRecordingProhibited = other.mRecordingProhibited;
        mSearchable = other.mSearchable;
        mSeasonTitle = other.mSeasonTitle;
        mInternalProviderData = other.mInternalProviderData;
    }

    /**
     * @return The fields of the Program in the ContentValues format to be easily inserted into the
     *     TV Input Framework database.
     * @hide
     */
    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        if (mId != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs._ID, mId);
        }
        if (mChannelId != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_CHANNEL_ID, mChannelId);
        } else {
            values.putNull(TvContract.Programs.COLUMN_CHANNEL_ID);
        }
        if (!TextUtils.isEmpty(mTitle)) {
            values.put(TvContract.Programs.COLUMN_TITLE, mTitle);
        } else {
            values.putNull(TvContract.Programs.COLUMN_TITLE);
        }
        if (!TextUtils.isEmpty(mEpisodeTitle)) {
            values.put(TvContract.Programs.COLUMN_EPISODE_TITLE, mEpisodeTitle);
        } else {
            values.putNull(TvContract.Programs.COLUMN_EPISODE_TITLE);
        }
        if (!TextUtils.isEmpty(mSeasonNumber) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            values.put(TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER, mSeasonNumber);
        } else if (!TextUtils.isEmpty(mSeasonNumber)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            values.put(TvContract.Programs.COLUMN_SEASON_NUMBER, Integer.parseInt(mSeasonNumber));
        } else {
            values.putNull(TvContract.Programs.COLUMN_SEASON_NUMBER);
        }
        if (!TextUtils.isEmpty(mEpisodeNumber) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            values.put(TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER, mEpisodeNumber);
        } else if (!TextUtils.isEmpty(mEpisodeNumber)
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            values.put(TvContract.Programs.COLUMN_EPISODE_NUMBER, Integer.parseInt(mEpisodeNumber));
        } else {
            values.putNull(TvContract.Programs.COLUMN_EPISODE_NUMBER);
        }
        if (!TextUtils.isEmpty(mDescription)) {
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, mDescription);
        } else {
            values.putNull(TvContract.Programs.COLUMN_SHORT_DESCRIPTION);
        }
        if (!TextUtils.isEmpty(mDescription)) {
            values.put(TvContract.Programs.COLUMN_LONG_DESCRIPTION, mLongDescription);
        } else {
            values.putNull(TvContract.Programs.COLUMN_LONG_DESCRIPTION);
        }
        if (!TextUtils.isEmpty(mPosterArtUri)) {
            values.put(TvContract.Programs.COLUMN_POSTER_ART_URI, mPosterArtUri);
        } else {
            values.putNull(TvContract.Programs.COLUMN_POSTER_ART_URI);
        }
        if (!TextUtils.isEmpty(mThumbnailUri)) {
            values.put(TvContract.Programs.COLUMN_THUMBNAIL_URI, mThumbnailUri);
        } else {
            values.putNull(TvContract.Programs.COLUMN_THUMBNAIL_URI);
        }
        if (!TextUtils.isEmpty(mAudioLanguages)) {
            values.put(TvContract.Programs.COLUMN_AUDIO_LANGUAGE, mAudioLanguages);
        } else {
            values.putNull(TvContract.Programs.COLUMN_AUDIO_LANGUAGE);
        }
        if (mBroadcastGenres != null && mBroadcastGenres.length > 0) {
            values.put(
                    TvContract.Programs.COLUMN_BROADCAST_GENRE,
                    TvContract.Programs.Genres.encode(mBroadcastGenres));
        } else {
            values.putNull(TvContract.Programs.COLUMN_BROADCAST_GENRE);
        }
        if (mCanonicalGenres != null && mCanonicalGenres.length > 0) {
            values.put(
                    TvContract.Programs.COLUMN_CANONICAL_GENRE,
                    TvContract.Programs.Genres.encode(mCanonicalGenres));
        } else {
            values.putNull(TvContract.Programs.COLUMN_CANONICAL_GENRE);
        }
        if (mContentRatings != null && mContentRatings.length > 0) {
            values.put(
                    TvContract.Programs.COLUMN_CONTENT_RATING,
                    TvContractUtils.contentRatingsToString(mContentRatings));
        } else {
            values.putNull(TvContract.Programs.COLUMN_CONTENT_RATING);
        }
        if (mStartTimeUtcMillis != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, mStartTimeUtcMillis);
        } else {
            values.putNull(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS);
        }
        if (mEndTimeUtcMillis != INVALID_LONG_VALUE) {
            values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, mEndTimeUtcMillis);
        } else {
            values.putNull(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS);
        }
        if (mVideoWidth != INVALID_INT_VALUE) {
            values.put(TvContract.Programs.COLUMN_VIDEO_WIDTH, mVideoWidth);
        } else {
            values.putNull(TvContract.Programs.COLUMN_VIDEO_WIDTH);
        }
        if (mVideoHeight != INVALID_INT_VALUE) {
            values.put(TvContract.Programs.COLUMN_VIDEO_HEIGHT, mVideoHeight);
        } else {
            values.putNull(TvContract.Programs.COLUMN_VIDEO_HEIGHT);
        }
        if (mInternalProviderData != null && mInternalProviderData.length > 0) {
            values.put(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA, mInternalProviderData);
        } else {
            values.putNull(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            values.put(TvContract.Programs.COLUMN_SEARCHABLE, mSearchable);
        }
        if (!TextUtils.isEmpty(mSeasonTitle) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            values.put(TvContract.Programs.COLUMN_SEASON_TITLE, mSeasonTitle);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            values.putNull(TvContract.Programs.COLUMN_SEASON_TITLE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            values.put(TvContract.Programs.COLUMN_RECORDING_PROHIBITED, mRecordingProhibited);
        }
        return values;
    }

    /**
     * Creates a Program object from a cursor including the fields defined in {@link
     * TvContract.Programs}.
     *
     * @param cursor A row from the TV Input Framework database.
     * @return A Program with the values taken from the cursor.
     * @hide
     */
    public static Program fromCursor(Cursor cursor) {
        Builder builder = new Builder();
        int index = 0;
        if (!cursor.isNull(index)) {
            builder.setId(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setChannelId(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setTitle(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setEpisodeTitle(cursor.getString(index));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!cursor.isNull(++index)) {
                builder.setSeasonNumber(cursor.getString(index), INVALID_INT_VALUE);
            }
        } else {
            if (!cursor.isNull(++index)) {
                builder.setSeasonNumber(cursor.getInt(index));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!cursor.isNull(++index)) {
                builder.setEpisodeNumber(cursor.getString(index), INVALID_INT_VALUE);
            }
        } else {
            if (!cursor.isNull(++index)) {
                builder.setEpisodeNumber(cursor.getInt(index));
            }
        }
        if (!cursor.isNull(++index)) {
            builder.setDescription(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setLongDescription(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setPosterArtUri(cursor.getString(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setThumbnailUri(cursor.getString(index));
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
            builder.setContentRatings(
                    TvContractUtils.stringToContentRatings(cursor.getString(index)));
        }
        if (!cursor.isNull(++index)) {
            builder.setStartTimeUtcMillis(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setEndTimeUtcMillis(cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setVideoWidth((int) cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setVideoHeight((int) cursor.getLong(index));
        }
        if (!cursor.isNull(++index)) {
            builder.setInternalProviderData(cursor.getBlob(index));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!cursor.isNull(++index)) {
                builder.setSearchable(cursor.getInt(index) == IS_SEARCHABLE);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (!cursor.isNull(++index)) {
                builder.setSeasonTitle(cursor.getString(index));
            }
            if (!cursor.isNull(++index)) {
                builder.setRecordingProhibited(cursor.getInt(index) == IS_RECORDING_PROHIBITED);
            }
        }
        return builder.build();
    }

    private static String[] getProjection() {
        String[] baseColumns =
                new String[] {
                    TvContract.Programs._ID,
                    TvContract.Programs.COLUMN_CHANNEL_ID,
                    TvContract.Programs.COLUMN_TITLE,
                    TvContract.Programs.COLUMN_EPISODE_TITLE,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            ? TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER
                            : TvContract.Programs.COLUMN_SEASON_NUMBER,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            ? TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER
                            : TvContract.Programs.COLUMN_EPISODE_NUMBER,
                    TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
                    TvContract.Programs.COLUMN_LONG_DESCRIPTION,
                    TvContract.Programs.COLUMN_POSTER_ART_URI,
                    TvContract.Programs.COLUMN_THUMBNAIL_URI,
                    TvContract.Programs.COLUMN_AUDIO_LANGUAGE,
                    TvContract.Programs.COLUMN_BROADCAST_GENRE,
                    TvContract.Programs.COLUMN_CANONICAL_GENRE,
                    TvContract.Programs.COLUMN_CONTENT_RATING,
                    TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                    TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
                    TvContract.Programs.COLUMN_VIDEO_WIDTH,
                    TvContract.Programs.COLUMN_VIDEO_HEIGHT,
                    TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA
                };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] marshmallowColumns = new String[] {TvContract.Programs.COLUMN_SEARCHABLE};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                String[] nougatColumns =
                        new String[] {
                            TvContract.Programs.COLUMN_SEASON_TITLE,
                            TvContract.Programs.COLUMN_RECORDING_PROHIBITED
                        };
                return CollectionUtils.concatAll(baseColumns, marshmallowColumns, nougatColumns);
            } else {
                return CollectionUtils.concatAll(baseColumns, marshmallowColumns);
            }
        }
        return baseColumns;
    }

    /** This Builder class simplifies the creation of a {@link Program} object. */
    public static final class Builder {
        private final Program mProgram;

        /** Creates a new Builder object. */
        public Builder() {
            mProgram = new Program();
        }

        /**
         * Creates a new Builder object with values copied from another Program.
         *
         * @param other The Program you're copying from.
         */
        public Builder(Program other) {
            mProgram = new Program();
            mProgram.copyFrom(other);
        }

        /**
         * Creates a new Builder object with values from the Channel this program is playing on.
         *
         * @param channel The Channel that contains this Program
         */
        public Builder(Channel channel) {
            mProgram = new Program();
            mProgram.mChannelId = channel.getId();
            mProgram.mDescription = channel.getDescription();
            mProgram.mInternalProviderData = channel.getInternalProviderDataByteArray();
            mProgram.mThumbnailUri = channel.getChannelLogo();
            mProgram.mTitle = channel.getDisplayName();
        }

        /**
         * Sets a unique id for this program.
         *
         * @param programId The value of {@link TvContract.Programs#_ID} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        private Builder setId(long programId) {
            mProgram.mId = programId;
            return this;
        }

        /**
         * Sets the ID of the {@link Channel} that contains this program.
         *
         * @param channelId The value of {@link TvContract.Programs#COLUMN_CHANNEL_ID for the
         * program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setChannelId(long channelId) {
            mProgram.mChannelId = channelId;
            return this;
        }

        /**
         * Sets the title of this program. For a series, this is the series title.
         *
         * @param title The value of {@link TvContract.Programs#COLUMN_TITLE} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setTitle(String title) {
            mProgram.mTitle = title;
            return this;
        }

        /**
         * Sets the title of this particular episode for a series.
         *
         * @param episodeTitle The value of {@link TvContract.Programs#COLUMN_EPISODE_TITLE} for the
         *     program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setEpisodeTitle(String episodeTitle) {
            mProgram.mEpisodeTitle = episodeTitle;
            return this;
        }

        /**
         * Sets the season number for this episode for a series.
         *
         * @param seasonNumber The value of {@link TvContract.Programs#COLUMN_SEASON_DISPLAY_NUMBER}
         *     for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setSeasonNumber(int seasonNumber) {
            mProgram.mSeasonNumber = String.valueOf(seasonNumber);
            return this;
        }

        /**
         * Sets the season number for this episode for a series.
         *
         * @param seasonNumber The value of {@link TvContract.Programs#COLUMN_SEASON_NUMBER} for the
         *     program.
         * @param numericalSeasonNumber An integer value for {@link
         *     TvContract.Programs#COLUMN_SEASON_NUMBER} which will be used for API Level 23 and
         *     below.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setSeasonNumber(String seasonNumber, int numericalSeasonNumber) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mProgram.mSeasonNumber = seasonNumber;
            } else {
                mProgram.mSeasonNumber = String.valueOf(numericalSeasonNumber);
            }
            return this;
        }

        /**
         * Sets the episode number in a season for this episode for a series.
         *
         * @param episodeNumber The value of {@link
         *     TvContract.Programs#COLUMN_EPISODE_DISPLAY_NUMBER} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setEpisodeNumber(int episodeNumber) {
            mProgram.mEpisodeNumber = String.valueOf(episodeNumber);
            return this;
        }

        /**
         * Sets the episode number in a season for this episode for a series.
         *
         * @param episodeNumber The value of {@link
         *     TvContract.Programs#COLUMN_EPISODE_DISPLAY_NUMBER} for the program.
         * @param numericalEpisodeNumber An integer value for {@link
         *     TvContract.Programs#COLUMN_SEASON_NUMBER} which will be used for API Level 23 and
         *     below.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setEpisodeNumber(String episodeNumber, int numericalEpisodeNumber) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mProgram.mEpisodeNumber = episodeNumber;
            } else {
                mProgram.mEpisodeNumber = String.valueOf(numericalEpisodeNumber);
            }
            return this;
        }

        /**
         * Sets the time when the program is going to begin in milliseconds since the epoch.
         *
         * @param startTimeUtcMillis The value of {@link
         *     TvContract.Programs#COLUMN_START_TIME_UTC_MILLIS} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mProgram.mStartTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        /**
         * Sets the time when this program is going to end in milliseconds since the epoch.
         *
         * @param endTimeUtcMillis The value of {@link
         *     TvContract.Programs#COLUMN_END_TIME_UTC_MILLIS} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setEndTimeUtcMillis(long endTimeUtcMillis) {
            mProgram.mEndTimeUtcMillis = endTimeUtcMillis;
            return this;
        }

        /**
         * Sets a brief description of the program. For a series, this would be a brief description
         * of the episode.
         *
         * @param description The value of {@link TvContract.Programs#COLUMN_SHORT_DESCRIPTION} for
         *     the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setDescription(String description) {
            mProgram.mDescription = description;
            return this;
        }

        /**
         * Sets a longer description of a program if one exists.
         *
         * @param longDescription The value of {@link TvContract.Programs#COLUMN_LONG_DESCRIPTION}
         *     for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setLongDescription(String longDescription) {
            mProgram.mLongDescription = longDescription;
            return this;
        }

        /**
         * Sets the video width of the program.
         *
         * @param width The value of {@link TvContract.Programs#COLUMN_VIDEO_WIDTH} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setVideoWidth(int width) {
            mProgram.mVideoWidth = width;
            return this;
        }

        /**
         * Sets the video height of the program.
         *
         * @param height The value of {@link TvContract.Programs#COLUMN_VIDEO_HEIGHT} for the
         *     program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setVideoHeight(int height) {
            mProgram.mVideoHeight = height;
            return this;
        }

        /**
         * Sets the content ratings for this program.
         *
         * @param contentRatings An array of {@link TvContentRating} that apply to this program
         *     which will be flattened to a String to store in a database.
         * @return This Builder object to allow for chaining of calls to builder methods.
         * @see TvContract.Programs#COLUMN_CONTENT_RATING
         */
        public Builder setContentRatings(TvContentRating[] contentRatings) {
            mProgram.mContentRatings = contentRatings;
            return this;
        }

        /**
         * Sets the large poster art of the program.
         *
         * @param posterArtUri The value of {@link TvContract.Programs#COLUMN_POSTER_ART_URI} for
         *     the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setPosterArtUri(String posterArtUri) {
            mProgram.mPosterArtUri = posterArtUri;
            return this;
        }

        /**
         * Sets a small thumbnail of the program.
         *
         * @param thumbnailUri The value of {@link TvContract.Programs#COLUMN_THUMBNAIL_URI} for the
         *     program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setThumbnailUri(String thumbnailUri) {
            mProgram.mThumbnailUri = thumbnailUri;
            return this;
        }

        /**
         * Sets the broadcast-specified genres of the program.
         *
         * @param genres Array of genres that apply to the program based on the broadcast standard
         *     which will be flattened to a String to store in a database.
         * @return This Builder object to allow for chaining of calls to builder methods.
         * @see TvContract.Programs#COLUMN_BROADCAST_GENRE
         */
        public Builder setBroadcastGenres(String[] genres) {
            mProgram.mBroadcastGenres = genres;
            return this;
        }

        /**
         * Sets the genres of the program.
         *
         * @param genres An array of {@link TvContract.Programs.Genres} that apply to the program
         *     which will be flattened to a String to store in a database.
         * @return This Builder object to allow for chaining of calls to builder methods.
         * @see TvContract.Programs#COLUMN_CANONICAL_GENRE
         */
        public Builder setCanonicalGenres(String[] genres) {
            mProgram.mCanonicalGenres = genres;
            return this;
        }

        /**
         * Sets the internal provider data for the program as raw bytes.
         *
         * @param data The value of {@link TvContract.Programs#COLUMN_INTERNAL_PROVIDER_DATA} for
         *     the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setInternalProviderData(byte[] data) {
            mProgram.mInternalProviderData = data;
            return this;
        }

        /**
         * Sets the internal provider data for the program.
         *
         * @param internalProviderData The value of {@link
         *     TvContract.Programs#COLUMN_INTERNAL_PROVIDER_DATA} for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setInternalProviderData(InternalProviderData internalProviderData) {
            if (internalProviderData != null) {
                mProgram.mInternalProviderData = internalProviderData.toString().getBytes();
            }
            return this;
        }

        /**
         * Sets the available audio languages for this program as a comma-separated String.
         *
         * @param audioLanguages The value of {@link TvContract.Programs#COLUMN_AUDIO_LANGUAGE} for
         *     the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setAudioLanguages(String audioLanguages) {
            mProgram.mAudioLanguages = audioLanguages;
            return this;
        }

        /**
         * Sets whether this program cannot be recorded.
         *
         * @param prohibited The value of {@link TvContract.Programs#COLUMN_RECORDING_PROHIBITED}
         *     for the program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setRecordingProhibited(boolean prohibited) {
            mProgram.mRecordingProhibited = prohibited ? IS_RECORDING_PROHIBITED : 0;
            return this;
        }

        /**
         * Sets whether this channel can be searched for in other applications.
         *
         * @param searchable The value of {@link TvContract.Programs#COLUMN_SEARCHABLE} for the
         *     program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setSearchable(boolean searchable) {
            mProgram.mSearchable = searchable ? IS_SEARCHABLE : 0;
            return this;
        }

        /**
         * Sets a custom name for the season, if applicable.
         *
         * @param seasonTitle The value of {@link TvContract.Programs#COLUMN_SEASON_TITLE} for the
         *     program.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setSeasonTitle(String seasonTitle) {
            mProgram.mSeasonTitle = seasonTitle;
            return this;
        }

        /** @return A new Program with values supplied by the Builder. */
        public Program build() {
            Program program = new Program();
            program.copyFrom(mProgram);
            if (mProgram.getStartTimeUtcMillis() >= mProgram.getEndTimeUtcMillis()) {
                throw new IllegalArgumentException(
                        "This program must have defined start and end " + "times");
            }
            return program;
        }
    }
}
