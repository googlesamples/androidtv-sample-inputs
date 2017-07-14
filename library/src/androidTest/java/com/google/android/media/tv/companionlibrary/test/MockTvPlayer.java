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
package com.google.android.media.tv.companionlibrary.test;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.Surface;
import com.google.android.media.tv.companionlibrary.TvPlayer;
import java.io.IOException;
import junit.framework.Assert;

/**
 * The MockTvPlayer is an implementation of TvPlayer which plays media with Android's MediaPlayer.
 * It is used for instrumentation tests.
 */
public class MockTvPlayer implements TvPlayer {
    private MediaPlayer mMediaPlayer;

    public MockTvPlayer() {
        mMediaPlayer = new MediaPlayer();
    }

    @Override
    public void seekTo(long position) {
        mMediaPlayer.seekTo((int) position);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void setPlaybackParams(PlaybackParams params) {
        mMediaPlayer.setPlaybackParams(params);
    }

    @Override
    public long getCurrentPosition() {
        return mMediaPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return mMediaPlayer.getDuration();
    }

    @Override
    public void setSurface(Surface surface) {
        mMediaPlayer.setSurface(surface);
    }

    @Override
    public void setVolume(float volume) {
        Assert.assertNotNull("MediaPlayer is null.", mMediaPlayer);
        mMediaPlayer.setVolume(volume, volume);
    }

    @Override
    public void pause() {
        mMediaPlayer.pause();
    }

    @Override
    public void play() {
        mMediaPlayer.start();
    }

    @Override
    public void registerCallback(Callback callback) {
        // Don't do anything
    }

    @Override
    public void unregisterCallback(Callback callback) {
        // Don't do anything
    }

    public void release() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
        }
        mMediaPlayer = null;
    }

    public void playMediaFromAssets(AssetFileDescriptor descriptor) throws IOException {
        mMediaPlayer.setDataSource(descriptor.getFileDescriptor(),
                descriptor.getStartOffset(), descriptor.getLength());
        descriptor.close();
    }

    public void playMedia(String mediaUrl) throws IOException {
        mMediaPlayer.setDataSource(mediaUrl);
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                mediaPlayer.start();
                Assert.assertTrue("Video is not playing.", mediaPlayer.isPlaying());
            }
        });
        mMediaPlayer.prepareAsync();
    }

    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }
}
