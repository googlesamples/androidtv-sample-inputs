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

package com.example.android.sampletvinput.player;

import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;
import android.util.Log;
import com.google.android.media.tv.companionlibrary.TvPlayer;

@RequiresApi(api = Build.VERSION_CODES.M)
public class FakeTrickplayRunnable implements Runnable {
    private final static String TAG = FakeTrickplayRunnable.class.getSimpleName();
    private final static boolean DEBUG = false;
    private final static int DELAY_MILLIS = 1000; // 1 FPS

    private float mSpeed;
    private boolean mActive;
    private TvPlayer mTvPlayer;
    private Handler mFakeTrickplayHandler;

    public FakeTrickplayRunnable(TvPlayer tvPlayer) {
        mTvPlayer = tvPlayer;
    }

    @Override
    public void run() {
        if (DEBUG) {
            Log.d(TAG, "Seek from " + mTvPlayer.getCurrentPosition());
            Log.d(TAG, "by " + mSpeed * DELAY_MILLIS);
        }
        mTvPlayer.seekTo((long) (mTvPlayer.getCurrentPosition() + mSpeed * DELAY_MILLIS));
        if (mActive) {
            mFakeTrickplayHandler.postDelayed(this, DELAY_MILLIS);
        }
    }

    public void setPlaybackParams(PlaybackParams playbackParams) {
        if (Math.abs(playbackParams.getSpeed() - 1f) < 0.1) {
            // Handle normal playback, stopping the background thread
            if (DEBUG) {
                Log.d(TAG, "Returning to normal speed, so we will stop running thread");
            }
            stop();
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Start trickplay thread with a speed of " + playbackParams.getSpeed());
        }
        mSpeed = playbackParams.getSpeed();
        if (mFakeTrickplayHandler == null) {
            mFakeTrickplayHandler = new Handler(Looper.getMainLooper());
        }
        if (!mActive) {
            mFakeTrickplayHandler.post(this);
            mActive = true;
        }
    }

    public void stop() {
        if (DEBUG) {
            Log.d(TAG, "Stop trickplay handler thread");
        }
        mActive = false;
        if (mFakeTrickplayHandler != null) {
            mFakeTrickplayHandler.removeCallbacksAndMessages(null);
        }
    }
}