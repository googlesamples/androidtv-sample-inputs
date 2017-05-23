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

/** A class to store advertisement information. */
public class Advertisement implements Comparable<Advertisement> {
    /** The advertisement type for VAST. */
    public static final int TYPE_VAST = 0;

    private long mStartTimeUtcMillis;
    private long mStopTimeUtcMillis;
    private int mType;
    private String mRequestUrl;

    private Advertisement() {
        mType = TYPE_VAST;
    }

    /** @return Epoch start time of ad playback in milliseconds. */
    public long getStartTimeUtcMillis() {
        return mStartTimeUtcMillis;
    }

    /** @return Epoch stop time of ad playback in milliseconds. */
    public long getStopTimeUtcMillis() {
        return mStopTimeUtcMillis;
    }

    /** @return The type of advertisement. */
    public int getType() {
        return mType;
    }

    /** @return URL for requesting advertisement from providers. */
    public String getRequestUrl() {
        return mRequestUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Advertisement)) return false;

        Advertisement that = (Advertisement) o;
        if (mStartTimeUtcMillis != that.getStartTimeUtcMillis()) return false;
        if (mStopTimeUtcMillis != that.getStopTimeUtcMillis()) return false;
        if (mType != that.getType()) return false;
        return mRequestUrl.equals(that.getRequestUrl());
    }

    @Override
    public int hashCode() {
        int result = (int) (mStartTimeUtcMillis ^ (mStartTimeUtcMillis >>> 32));
        result = 31 * result + (int) (mStopTimeUtcMillis ^ (mStopTimeUtcMillis >>> 32));
        result = 31 * result + mType;
        result = 31 * result + (mRequestUrl != null ? mRequestUrl.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Advertisement{"
                + "start="
                + mStartTimeUtcMillis
                + ", stop="
                + mStopTimeUtcMillis
                + ", type="
                + mType
                + ", request-url="
                + mRequestUrl
                + "}";
    }

    @Override
    public int compareTo(@NonNull Advertisement other) {
        int startTimeCompare =
                Long.compare(this.getStartTimeUtcMillis(), other.getStartTimeUtcMillis());
        if (startTimeCompare != 0) {
            return startTimeCompare;
        } else {
            return Long.compare(this.getStopTimeUtcMillis(), other.getStopTimeUtcMillis());
        }
    }

    private void copyFrom(Advertisement other) {
        if (this == other) {
            return;
        }

        mStartTimeUtcMillis = other.getStartTimeUtcMillis();
        mStopTimeUtcMillis = other.getStopTimeUtcMillis();
        mType = other.getType();
        mRequestUrl = other.getRequestUrl();
    }

    /** This Builder class simplifies the creation of a {@link Advertisement} object. */
    public static final class Builder {
        private final Advertisement mAdvertisement;

        /** Creates a new Builder object. */
        public Builder() {
            mAdvertisement = new Advertisement();
        }

        /**
         * Creates a new Builder object with values copied from another {@link Advertisement}
         * object.
         *
         * @param other The Program you're copying from.
         */
        public Builder(Advertisement other) {
            mAdvertisement = new Advertisement();
            mAdvertisement.copyFrom(other);
        }

        /**
         * Sets the start time of advertisement.
         *
         * @param startTimeUtcMillis Epoch start time of advertisement in milliseconds.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setStartTimeUtcMillis(long startTimeUtcMillis) {
            mAdvertisement.mStartTimeUtcMillis = startTimeUtcMillis;
            return this;
        }

        /**
         * Sets the stop time of advertisement.
         *
         * @param stopTimeUtcMillis Epoch stop time of advertisement in milliseconds.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setStopTimeUtcMillis(long stopTimeUtcMillis) {
            mAdvertisement.mStopTimeUtcMillis = stopTimeUtcMillis;
            return this;
        }

        /**
         * Sets the type of advertisement. The default is {@link #TYPE_VAST}.
         *
         * @param type Type of advertisement.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setType(int type) {
            switch (type) {
                case TYPE_VAST:
                    mAdvertisement.mType = TYPE_VAST;
                    break;
                default:
                    throw new IllegalStateException("Unsupported type: " + type);
            }
            return this;
        }

        /**
         * Sets the URL for requesting ads.
         *
         * @param requestUrl URL for requesting advertisement.
         * @return This Builder object to allow for chaining of calls to builder methods.
         */
        public Builder setRequestUrl(String requestUrl) {
            mAdvertisement.mRequestUrl = requestUrl;
            return this;
        }

        /** @return An {@link Advertisement} object with values specified by the Builder. */
        public Advertisement build() {
            return mAdvertisement;
        }
    }
}
