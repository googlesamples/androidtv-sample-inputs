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

package com.example.android.sampletvinput.ads;

import android.view.Surface;

import com.example.android.sampletvinput.player.DemoPlayer;
import com.google.android.exoplayer.ExoPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of {@link AdController.VideoPlayer} with {@link DemoPlayer}.
 */
public class AdVideoPlayerProxy implements AdController.VideoPlayer {
    private final DemoPlayer mDemoPlayer;
    private final List<PlayerCallback> mVideoPlayerCallbacks;

    public AdVideoPlayerProxy(DemoPlayer demoPlayer) {
        mDemoPlayer = demoPlayer;
        mDemoPlayer.addListener(new DemoPlayer.Listener() {
            @Override
            public void onStateChanged(boolean playWhenReady, int playbackState) {
                if (playWhenReady && playbackState == ExoPlayer.STATE_ENDED) {
                    for (PlayerCallback callback : mVideoPlayerCallbacks) {
                        callback.onCompleted();
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                // Do nothing.
            }

            @Override
            public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                    float pixelWidthHeightRatio) {
                // Do nothing.
            }
        });
        mVideoPlayerCallbacks = new ArrayList<>();
    }

    @Override
    public void play() {
        mDemoPlayer.setPlayWhenReady(true);
        for (PlayerCallback callback : mVideoPlayerCallbacks) {
            callback.onPlay();
        }
    }

    @Override
    public void pause() {
        mDemoPlayer.setPlayWhenReady(false);
        for (PlayerCallback callback : mVideoPlayerCallbacks) {
            callback.onPause();
        }
    }

    @Override
    public void stop() {
        mDemoPlayer.setPlayWhenReady(false);
    }

    @Override
    public void seekTo(long videoPositionMs) {
        mDemoPlayer.seekTo(videoPositionMs);
    }

    @Override
    public long getCurrentPosition() {
        return mDemoPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return mDemoPlayer.getDuration();
    }

    @Override
    public void addPlayerCallback(PlayerCallback callback) {
        mVideoPlayerCallbacks.add(callback);
    }

    @Override
    public void removePlayerCallback(PlayerCallback callback) {
        mVideoPlayerCallbacks.remove(callback);
    }

    @Override
    public void setSurface(Surface surface) {
        mDemoPlayer.setSurface(surface);
    }

    @Override
    public void setVolume(float volume) {
        mDemoPlayer.setVolume(volume);
    }
}
