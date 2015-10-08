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

package com.example.android.sampletvinput.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.Build;
import android.text.TextUtils;

/**
 * A convenience class to create and insert channel entries into the database.
 */
public final class Channel {
    public static final long INVALID_CHANNEL_ID = -1;

    private long mId;
    private String mPackageName;
    private String mInputId;
    private String mType;
    private String mDisplayNumber;
    private String mDisplayName;
    private String mDescription;
    private String mVideoFormat;
    private int mOriginalNetworkId;
    private int mTransportStreamId;
    private int mServiceId;
    private String mAppLinkText;
    private int mAppLinkColor;
    private String mAppLinkIconUri;
    private String mAppLinkPosterArtUri;
    private String mAppLinkIntentUri;

    private Channel() {
        mId = INVALID_CHANNEL_ID;
    }

    public long getId() {
        return mId;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getInputId() {
        return mInputId;
    }

    public String getType() {
        return mType;
    }

    public String getDisplayNumber() {
        return mDisplayNumber;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getDescription() {
        return mDescription;
    }

    public String getVideoFormat() {
        return mVideoFormat;
    }

    public int getOriginalNetworkId() {
        return mOriginalNetworkId;
    }

    public int getTransportStreamId() {
        return mTransportStreamId;
    }

    public int getServiceId() {
        return mServiceId;
    }

    public String getAppLinkText() {
        return mAppLinkText;
    }

    public int getAppLinkColor() {
        return mAppLinkColor;
    }

    public String getAppLinkIconUri() {
        return mAppLinkIconUri;
    }

    public String getAppLinkPosterArtUri() {
        return mAppLinkPosterArtUri;
    }

    public String getAppLinkIntentUri() {
        return mAppLinkIntentUri;
    }

    public void setAppLinkIntentUri(String intentUri) {
        mAppLinkIntentUri = intentUri;
    }

    @Override
    public String toString() {
        return "Channel{"
                + "id=" + mId
                + ", packageName=" + mPackageName
                + ", inputId=" + mInputId
                + ", type=" + mType
                + ", displayNumber=" + mDisplayNumber
                + ", displayName=" + mDisplayName
                + ", description=" + mDescription
                + ", videoFormat=" + mVideoFormat
                + ", appLinkText=" + mAppLinkText + "}";
    }

    public ContentValues toContentValues() {
        ContentValues values = new ContentValues();
        if (mId != INVALID_CHANNEL_ID) {
            values.put(TvContract.Channels._ID, mId);
        }
        if (!TextUtils.isEmpty(mPackageName)) {
            values.put(TvContract.Channels.COLUMN_PACKAGE_NAME, mPackageName);
        } else {
            values.putNull(TvContract.Channels.COLUMN_PACKAGE_NAME);
        }
        if (!TextUtils.isEmpty(mInputId)) {
            values.put(TvContract.Channels.COLUMN_INPUT_ID, mInputId);
        } else {
            values.putNull(TvContract.Channels.COLUMN_INPUT_ID);
        }
        if (!TextUtils.isEmpty(mType)) {
            values.put(TvContract.Channels.COLUMN_TYPE, mType);
        } else {
            values.putNull(TvContract.Channels.COLUMN_TYPE);
        }
        if (!TextUtils.isEmpty(mDisplayNumber)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NUMBER, mDisplayNumber);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        }
        if (!TextUtils.isEmpty(mDisplayName)) {
            values.put(TvContract.Channels.COLUMN_DISPLAY_NAME, mDisplayName);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DISPLAY_NAME);
        }
        if (!TextUtils.isEmpty(mDescription)) {
            values.put(TvContract.Channels.COLUMN_DESCRIPTION, mDescription);
        } else {
            values.putNull(TvContract.Channels.COLUMN_DESCRIPTION);
        }
        if (!TextUtils.isEmpty(mVideoFormat)) {
            values.put(TvContract.Channels.COLUMN_VIDEO_FORMAT, mVideoFormat);
        } else {
            values.putNull(TvContract.Channels.COLUMN_VIDEO_FORMAT);
        }
        values.put(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID, mOriginalNetworkId);
        values.put(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID, mTransportStreamId);
        values.put(TvContract.Channels.COLUMN_SERVICE_ID, mServiceId);

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            values.put(TvContract.Channels.COLUMN_APP_LINK_COLOR, mAppLinkColor);
            if (!TextUtils.isEmpty(mAppLinkText)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_TEXT, mAppLinkText);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_TEXT);
            }
            if (!TextUtils.isEmpty(mAppLinkIconUri)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_ICON_URI, mAppLinkIconUri);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_ICON_URI);
            }
            if (!TextUtils.isEmpty(mAppLinkPosterArtUri)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI, mAppLinkPosterArtUri);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI);
            }
            if (!TextUtils.isEmpty(mAppLinkIntentUri)) {
                values.put(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI, mAppLinkIntentUri);
            } else {
                values.putNull(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI);
            }
        }
        return values;
    }

    public void copyFrom(Channel other) {
        if (this == other) {
            return;
        }
        mId = other.mId;
        mPackageName = other.mPackageName;
        mInputId = other.mInputId;
        mType = other.mType;
        mDisplayNumber = other.mDisplayNumber;
        mDisplayName = other.mDisplayName;
        mDescription = other.mDescription;
        mVideoFormat = other.mVideoFormat;
        mOriginalNetworkId = other.mOriginalNetworkId;
        mTransportStreamId = other.mTransportStreamId;
        mServiceId = other.mServiceId;
        mAppLinkText = other.mAppLinkText;
        mAppLinkColor = other.mAppLinkColor;
        mAppLinkIconUri = other.mAppLinkIconUri;
        mAppLinkPosterArtUri = other.mAppLinkPosterArtUri;
        mAppLinkIntentUri = other.mAppLinkIntentUri;
    }

    public static Channel fromCursor(Cursor cursor) {
        Builder builder = new Builder();
        int index = cursor.getColumnIndex(TvContract.Channels._ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setId(cursor.getLong(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_PACKAGE_NAME);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setPackageName(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_INPUT_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setInputId(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TYPE);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setType(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDisplayNumber(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDisplayName(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_DESCRIPTION);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setDescription(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_VIDEO_FORMAT);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setVideoFormat(cursor.getString(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setOriginalNetworkId(cursor.getInt(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setTransportStreamId(cursor.getInt(index));
        }
        index = cursor.getColumnIndex(TvContract.Channels.COLUMN_SERVICE_ID);
        if (index >= 0 && !cursor.isNull(index)) {
            builder.setServiceId(cursor.getInt(index));
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_TEXT);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkText(cursor.getString(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_COLOR);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkColor(cursor.getInt(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_ICON_URI);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkIconUri(cursor.getString(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkPosterArtUri(cursor.getString(index));
            }
            index = cursor.getColumnIndex(TvContract.Channels.COLUMN_APP_LINK_INTENT_URI);
            if (index >= 0 && !cursor.isNull(index)) {
                builder.setAppLinkIntentUri(cursor.getString(index));
            }
        }
        return builder.build();
    }

    public static final class Builder {
        private final Channel mChannel;

        public Builder() {
            mChannel = new Channel();
        }

        public Builder(Channel other) {
            mChannel = new Channel();
            mChannel.copyFrom(other);
        }

        public Builder setId(long id) {
            mChannel.mId = id;
            return this;
        }

        public Builder setPackageName(String packageName) {
            mChannel.mPackageName = packageName;
            return this;
        }

        public Builder setInputId(String inputId) {
            mChannel.mInputId = inputId;
            return this;
        }

        public Builder setType(String type) {
            mChannel.mType = type;
            return this;
        }

        public Builder setDisplayNumber(String displayNumber) {
            mChannel.mDisplayNumber = displayNumber;
            return this;
        }

        public Builder setDisplayName(String displayName) {
            mChannel.mDisplayName = displayName;
            return this;
        }

        public Builder setDescription(String description) {
            mChannel.mDescription = description;
            return this;
        }

        public Builder setVideoFormat(String videoFormat) {
            mChannel.mVideoFormat = videoFormat;
            return this;
        }

        public Builder setOriginalNetworkId(int originalNetworkId) {
            mChannel.mOriginalNetworkId = originalNetworkId;
            return this;
        }

        public Builder setTransportStreamId(int transportStreamId) {
            mChannel.mTransportStreamId = transportStreamId;
            return this;
        }

        public Builder setServiceId(int serviceId) {
            mChannel.mServiceId = serviceId;
            return this;
        }

        public Builder setAppLinkText(String appLinkText) {
            mChannel.mAppLinkText = appLinkText;
            return this;
        }

        public Builder setAppLinkColor(int appLinkColor) {
            mChannel.mAppLinkColor = appLinkColor;
            return this;
        }

        public Builder setAppLinkIconUri(String appLinkIconUri) {
            mChannel.mAppLinkIconUri = appLinkIconUri;
            return this;
        }

        public Builder setAppLinkPosterArtUri(String appLinkPosterArtUri) {
            mChannel.mAppLinkPosterArtUri = appLinkPosterArtUri;
            return this;
        }

        public Builder setAppLinkIntentUri(String appLinkIntentUri) {
            mChannel.mAppLinkIntentUri = appLinkIntentUri;
            return this;
        }

        public Channel build() {
            Channel channel = new Channel();
            channel.copyFrom(mChannel);
            return channel;
        }
    }
}
