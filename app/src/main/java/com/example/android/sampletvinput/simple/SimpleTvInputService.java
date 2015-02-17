/*
 * Copyright 2015 The Android Open Source Project
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

package com.example.android.sampletvinput.simple;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.view.Surface;

import com.example.android.sampletvinput.R;

import java.io.IOException;

/**
 * Simple TV input service which provides two sample channels.
 * <p>
 * NOTE: The purpose of this sample is to provide a really simple TV input sample to the developers
 * so that they can understand the core APIs and when/how/where they should use them with ease.
 * This means lots of features including EPG, subtitles, multi-audio, parental controls, and overlay
 * view are missing here. So, to check the example codes for them, see {@link RichTvInputService}.
 * </p>
 */
public class SimpleTvInputService extends TvInputService {
    @Override
    public Session onCreateSession(String inputId) {
        return new SimpleSessionImpl(this);
    }

    /**
     * Simple session implementation which plays local videos on the application's tune request.
     */
    private class SimpleSessionImpl extends TvInputService.Session {
        private static final int RESOURCE_1 =
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_22050hz;
        private static final int RESOURCE_2 =
                R.raw.video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz;

        private MediaPlayer mPlayer;
        private float mVolume;
        private Surface mSurface;

        SimpleSessionImpl(Context context) {
            super(context);
        }

        @Override
        public void onRelease() {
            mPlayer.release();
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            if (mPlayer != null) {
                mPlayer.setSurface(surface);
            }
            mSurface = surface;
            return true;
        }

        @Override
        public void onSetStreamVolume(float volume) {
            if (mPlayer != null) {
                mPlayer.setVolume(volume, volume);
            }
            mVolume = volume;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            String[] projection = {TvContract.Channels.COLUMN_SERVICE_ID};
            int resource = RESOURCE_1;
            Cursor cursor = null;
            try {
                cursor = getContentResolver().query(channelUri, projection, null, null, null);
                if (cursor == null || cursor.getCount() == 0) {
                    return false;
                }
                cursor.moveToNext();
                resource = (cursor.getInt(0) == SimpleTvInputSetupActivity.CHANNEL_1_SERVICE_ID ?
                        RESOURCE_1 : RESOURCE_2);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return startPlayback(resource);
            // NOTE: To display the program information (e.g. title) properly in the channel banner,
            // The implementation needs to register the program metadata on TvProvider.
            // For the example implementation, please see {@link RichTvInputService}.
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            // The sample content does not have caption. Nothing to do in this sample input.
            // NOTE: If the channel has caption, the implementation should turn on/off the caption
            // based on {@code enabled}.
            // For the example implementation for the case, please see {@link RichTvInputService}.
        }

        private boolean startPlayback(int resource) {
            if (mPlayer == null) {
                mPlayer = new MediaPlayer();
                mPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                    @Override
                    public boolean onInfo(MediaPlayer player, int what, int arg) {
                        // NOTE: TV input should notify the video playback state by using
                        // {@code notifyVideoAvailable()} and {@code notifyVideoUnavailable() so
                        // that the application can display back screen or spinner properly.
                        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                            notifyVideoUnavailable(
                                    TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                            return true;
                        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END
                                || what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            notifyVideoAvailable();
                            return true;
                        }
                        return false;
                    }
                });
                mPlayer.setSurface(mSurface);
                mPlayer.setVolume(mVolume, mVolume);
            } else {
                mPlayer.reset();
            }
            mPlayer.setLooping(true);
            AssetFileDescriptor afd = getResources().openRawResourceFd(resource);
            if (afd == null) {
                return false;
            }
            try {
                mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                        afd.getDeclaredLength());
                mPlayer.prepare();
                mPlayer.start();
            } catch (IOException e) {
                return false;
            } finally {
                try {
                    afd.close();
                } catch (IOException e) {
                    // Do nothing.
                }
            }
            // The sample content does not have rating information. Just allow the content here.
            // NOTE: If the content might include problematic scenes, it should not be allowed.
            // Also, if the content has rating information, the implementation should allow the
            // content based on the current rating settings by using
            // {@link android.media.tv.TvInputManager#isRatingBlocked()}.
            // For the example implementation for the case, please see {@link RichTvInputService}.
            notifyContentAllowed();
            return true;
        }
    }
}
