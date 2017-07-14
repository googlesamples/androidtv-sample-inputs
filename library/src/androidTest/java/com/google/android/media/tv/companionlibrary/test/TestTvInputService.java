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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Log;
import com.google.android.media.tv.companionlibrary.BaseTvInputService;
import com.google.android.media.tv.companionlibrary.TvPlayer;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.model.RecordedProgram;
import java.io.IOException;
import junit.framework.Assert;

/**
 * Dummy TvInputService that may be called for testing purposes
 */
public class TestTvInputService extends BaseTvInputService {
    private static final String TAG = TestTvInputService.class.getSimpleName();
    public static final String INPUT_ID = TestTvInputService.class.getPackage().getName() + "/." +
            TestTvInputService.class.getSimpleName();

    public static boolean mIsRecording;
    public static TestSession mSession;

    @Nullable
    @Override
    public Session onCreateSession(String inputId) {
        // Set this to the BaseUiTest for UI tests if applicable
        mSession = new TestSession(this, "");
        return super.sessionCreated(mSession);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Nullable
    @Override
    public TvInputService.RecordingSession onCreateRecordingSession(String inputId) {
        return new TestRecordingSession(this, inputId);
    }

    private class TestSession extends BaseTvInputService.Session {
        private MockTvPlayer mMockTvPlayer;
        private Program mProgram;

        public TestSession(Context context, String inputId) {
            super(context, inputId);
        }

        @Override
        public void onRelease() {
            super.onRelease();
            releasePlayer();
        }

        @Override
        public TvPlayer getTvPlayer() {
            if (mMockTvPlayer == null) {
                mMockTvPlayer = new MockTvPlayer();
            }
            Assert.assertNotNull("MediaPlayer is null.", mMockTvPlayer);
            return mMockTvPlayer;
        }

        public void releasePlayer() {
            if (mMockTvPlayer != null) {
                mMockTvPlayer.release();
            }
        }

        @Override
        public long onTimeShiftGetStartPosition() {
            Assert.assertNotNull("There is no currently playing program", mProgram);
            return super.onTimeShiftGetStartPosition();
        }

        private boolean playMediaUrl(String mediaUrl) {
            getTvPlayer();
            Log.d(TAG, "Play " + mediaUrl);
            try {
                if (mediaUrl.startsWith("assets://")) {
                    AssetFileDescriptor fileDescriptor = getAssets().openFd(mediaUrl.substring(9));
                    mMockTvPlayer.playMediaFromAssets(fileDescriptor);
                } else {
                    mMockTvPlayer.playMedia(mediaUrl);
                }
            } catch (IOException e) {
                e.printStackTrace();
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notifyTimeShiftStatusChanged(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
            }
            notifyVideoAvailable();
            return true;
        }

        @Override
        public boolean onPlayProgram(Program program, long startMs) {
            if (program == null) {
                return false;
            } else {
                mProgram = program;
                Assert.assertNotNull("There is no internal provider data",
                        mProgram.getInternalProviderData());
                String videoUrl = program.getInternalProviderData().getVideoUrl();
                return playMediaUrl(videoUrl);
            }
        }

        @Override
        public boolean onPlayRecordedProgram(RecordedProgram recordedProgram) {
            if (recordedProgram == null) {
                return false;
            } else {
                return playMediaUrl(recordedProgram.getRecordingDataUri());
            }
        }

        @Override
        public void onTimeShiftResume() {
            super.onTimeShiftResume();
            // Make sure that the MediaPlayer is still playing without error.
            Assert.assertTrue(mMockTvPlayer.isPlaying());
        }

        @Override
        public void onTimeShiftPause() {
            super.onTimeShiftPause();
            Assert.assertTrue(!mMockTvPlayer.isPlaying());
        }

        @Override
        public void onSetCaptionEnabled(boolean b) {

        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private class TestRecordingSession extends BaseTvInputService.RecordingSession {

        private String mInputId;

        public TestRecordingSession(Context context, String inputId) {
            super(context, inputId);
            mInputId = inputId;
        }

        @Override
        public void onTune(Uri uri) {
            super.onTune(uri);
            notifyTuned(uri);
        }

        @Override
        public void onStartRecording(Uri uri) {
            super.onStartRecording(uri);
            mIsRecording = true;
        }

        @Override
        public void onStopRecording(Program programToRecord) {
            mIsRecording = false;
            // Add a sample program into our DVR
            notifyRecordingStopped(new RecordedProgram.Builder()
                    .setInputId(mInputId)
                    .setTitle("That Gmail Blue Video")
                    .setRecordingDataUri(TestJobService.GMAIL_BLUE_VIDEO_URL)
                    .setStartTimeUtcMillis(System.currentTimeMillis())
                    .setEndTimeUtcMillis(System.currentTimeMillis() + 1000 * 60 * 60)
                    .build());
        }

        @Override
        public void onStopRecordingChannel(Channel channelToRecord) {
            mIsRecording = false;
            // Add a sample program into our DVR
            notifyRecordingStopped(new RecordedProgram.Builder()
                    .setInputId(mInputId)
                    .setTitle("That Gmail Blue Video")
                    .setRecordingDataUri(TestJobService.GMAIL_BLUE_VIDEO_URL)
                    .setStartTimeUtcMillis(System.currentTimeMillis())
                    .setEndTimeUtcMillis(System.currentTimeMillis() + 1000 * 60 * 60)
                    .build());
        }

        @Override
        public void onRelease() {

        }
    }
}