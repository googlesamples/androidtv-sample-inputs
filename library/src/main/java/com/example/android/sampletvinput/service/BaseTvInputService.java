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

package com.example.android.sampletvinput.service;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.utils.TvContractUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The BaseTvInputService provides helper methods to make it easier to create a
 * {@link TvInputService} with built-in methods for content blocking and pulling the current program
 * from the Electronic Programming Guide.
 */
public abstract class BaseTvInputService extends TvInputService {
    private static final String TAG = BaseTvInputService.class.getSimpleName();
    private static final boolean DEBUG = false;

    // For database calls
    private HandlerThread mDbHandlerThread;
    private Handler mDbHandler;
    // For content ratings
    private final List<Session> mSessions = new ArrayList<>();
    private final BroadcastReceiver mParentalControlsBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            for (Session session : mSessions) {
                session.checkContentBlockNeeded();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Create background thread
        mDbHandlerThread = new HandlerThread(getClass().getSimpleName());
        mDbHandlerThread.start();
        mDbHandler = new Handler(mDbHandlerThread.getLooper());

        // Setup our BroadcastReceiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
        intentFilter.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        registerReceiver(mParentalControlsBroadcastReceiver, intentFilter);
    }

    /**
     * Adds the Session to the list of currently available sessions.
     * @param session The newly created session.
     * @return The session that was created.
     */
    protected Session sessionCreated(Session session) {
        mSessions.add(session);
        return session;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mParentalControlsBroadcastReceiver);
        mDbHandlerThread.quit();
        mDbHandlerThread = null;
        mDbHandler = null;
    }

    /**
     * A {@link BaseTvInputService.Session} is called when a user tunes to channel provided by
     * this {@link BaseTvInputService}.
     */
    public abstract class Session extends TvInputService.Session implements Handler.Callback {
        private static final int MSG_PLAY_PROGRAM = 1000;
        private static final int MSG_TUNE_CHANNEL = 1001;

        private final Context mContext;
        private final TvInputManager mTvInputManager;
        private Program mCurrentProgram;
        private TvContentRating mLastBlockedRating;
        private TvContentRating[] mCurrentContentRatingSet;

        private final Set<TvContentRating> mUnblockedRatingSet = new HashSet<>();
        private final Handler mHandler;
        private PlayCurrentChannelRunnable mPlayCurrentChannelRunnable;
        private PlayCurrentProgramRunnable mPlayCurrentProgramRunnable;

        public Session(Context context, String inputId) {
            super(context);
            this.mContext = context;
            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            mLastBlockedRating = null;
            mHandler = new Handler(this);
        }

        @Override
        public void onRelease() {
            if (mDbHandler != null) {
                mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            }
            mSessions.remove(this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PLAY_PROGRAM:
                    Program currentProgram = (Program) msg.obj;
                    if (onPlayProgram(currentProgram)) {
                        checkProgramContent(currentProgram);
                    }
                    return true;
                case MSG_TUNE_CHANNEL:
                    Channel currentChannel = (Channel) msg.obj;
                    onPlayChannel(currentChannel);
                    return true;
            }
            return false;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

            mDbHandler.removeCallbacks(mPlayCurrentChannelRunnable);
            mPlayCurrentChannelRunnable = new PlayCurrentChannelRunnable(channelUri);
            mDbHandler.post(mPlayCurrentChannelRunnable);

            mUnblockedRatingSet.clear();
            mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            mPlayCurrentProgramRunnable = new PlayCurrentProgramRunnable(channelUri);
            mDbHandler.post(mPlayCurrentProgramRunnable);
            return true;
        }

        @Override
        public void onUnblockContent(TvContentRating rating) {
            if (rating != null) {
                unblockContent(rating);
            }
        }

        private boolean checkProgramContent(Program currentProgram) {
            if (!Objects.equals(currentProgram, mCurrentProgram)) {
                mCurrentProgram = currentProgram;
                mCurrentContentRatingSet = (currentProgram.getContentRatings() == null
                        || currentProgram.getContentRatings().length == 0) ? null :
                        currentProgram.getContentRatings();

                checkContentBlockNeeded();
                if (mDbHandler != null) {
                    mDbHandler.postDelayed(mPlayCurrentProgramRunnable,
                            currentProgram.getEndTimeUtcMillis() - System.currentTimeMillis()
                                    + 1000);
                    return true;
                }
                return false;
            }
            return true;
        }

        /**
         * Called when the media player should stop playing content and be released
         */
        public abstract void onReleasePlayer();

        /**
         * This method is called when a particular program is to begin playing. If there is not
         * a program scheduled in the EPG, the parameter will be {@code null}. Developers should
         * check the null condition and handle that case, possibly by manually resyncing the EPG.
         *
         * @param program The program that is set to be playing for a the currently tuned channel.
         * @return Whether playing this program was successful
         */
        public abstract boolean onPlayProgram(Program program);

        /**
         * This method is called when the user tunes to a given channel. Developers can override
         * this if they want specific behavior to occur after the user tunes but before the program
         * begins playing.
         *
         * @param channel The channel that the user wants to watch.
         */
        public void onPlayChannel(Channel channel) {

        }

        private void checkContentBlockNeeded() {
            if (mCurrentContentRatingSet == null || !mTvInputManager.isParentalControlsEnabled()) {
                // Content rating is invalid so we don't need to block anymore.
                // Unblock content here explicitly to resume playback.
                unblockContent(null);
                return;
            }
            // Check each content rating that the program has
            TvContentRating blockedRating = null;
            for (TvContentRating contentRating : mCurrentContentRatingSet) {
                if (mTvInputManager.isRatingBlocked(contentRating)
                        && !mUnblockedRatingSet.contains(contentRating)) {
                    // This should be blocked
                    blockedRating = contentRating;
                }
            }
            if (blockedRating == null) {
                // Content rating is null so we don't need to block anymore.
                // Unblock content here explicitly to resume playback.
                unblockContent(null);
                return;
            }
            mLastBlockedRating = blockedRating;
            // Children restricted content might be blocked by TV app as well,
            // but TIS should do its best not to show any single frame of blocked content.
            onReleasePlayer();
            notifyContentBlocked(blockedRating);
        }

        private void unblockContent(TvContentRating rating) {
            // TIS should unblock content only if unblock request is legitimate.
            if (rating == null || mLastBlockedRating == null || rating.equals(mLastBlockedRating)) {
                mLastBlockedRating = null;
                if (rating != null) {
                    mUnblockedRatingSet.add(rating);
                }
                if (mCurrentProgram != null && !mUnblockedRatingSet.isEmpty()) {
                    // If the program was previously blocked and has been unblocked, playback is
                    // restarted. If the program was not originally blocked, no action is taken.
                    if (onPlayProgram(mCurrentProgram)) {
                        checkProgramContent(mCurrentProgram);
                    }
                }
                notifyContentAllowed();
            }
        }

        private class PlayCurrentProgramRunnable implements Runnable {
            private final Uri mChannelUri;

            PlayCurrentProgramRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                ContentResolver resolver = mContext.getContentResolver();
                Program program = TvContractUtils.getCurrentProgram(resolver, mChannelUri);
                if (program == null) {
                    Log.w(TAG, "Failed to get program info for " + mChannelUri + ". Try to do an " +
                            "EPG sync.");
                }
                mHandler.removeMessages(MSG_PLAY_PROGRAM);
                mHandler.obtainMessage(MSG_PLAY_PROGRAM, program).sendToTarget();
            }
        }

        private class PlayCurrentChannelRunnable implements Runnable {
            private final Uri mChannelUri;

            PlayCurrentChannelRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                mDbHandler.removeCallbacks(this);
                ContentResolver resolver = mContext.getContentResolver();
                mPlayCurrentProgramRunnable = new PlayCurrentProgramRunnable(mChannelUri);
                Channel channel = TvContractUtils.getChannel(resolver, mChannelUri);
                mHandler.obtainMessage(MSG_TUNE_CHANNEL, channel).sendToTarget();
            }
        }
    }
}