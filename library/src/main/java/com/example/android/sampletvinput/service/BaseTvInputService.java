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
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import com.example.android.sampletvinput.TvPlayer;
import com.example.android.sampletvinput.ads.AdController;
import com.example.android.sampletvinput.model.Advertisement;
import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.utils.InternalProviderDataUtil;
import com.example.android.sampletvinput.utils.TvContractUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
        private static final int MSG_PLAY_AD = 1002;

        private final Context mContext;
        private final TvInputManager mTvInputManager;
        private Program mCurrentProgram;
        private Channel mCurrentChannel;

        private TvContentRating mLastBlockedRating;
        private TvContentRating[] mCurrentContentRatingSet;

        private final Set<TvContentRating> mUnblockedRatingSet = new HashSet<>();
        private final Handler mHandler;
        private PlayCurrentChannelRunnable mPlayCurrentChannelRunnable;
        private PlayCurrentProgramRunnable mPlayCurrentProgramRunnable;

        private long mMinimalAdIntervalOnTune = TimeUnit.MINUTES.toMillis(5);
        private long mLastNewChannelAdWatchedTimeMs;
        private AdController mAdController;
        private Uri mChannelUri;
        private Surface mSurface;
        private float mVolume;
        /** The timestamp when we began playing */
        private long mTuneMillis;

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
                mDbHandler.removeCallbacks(mPlayCurrentChannelRunnable);
            }
            releaseAdController();
            onReleasePlayer();
            mSessions.remove(this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PLAY_PROGRAM:
                    onReleasePlayer();
                    mCurrentProgram = (Program) msg.obj;
                    if (playProgram(mCurrentProgram)) {
                        getTvPlayer().setSurface(getSurface());
                        getTvPlayer().setVolume(getVolume());
                        checkProgramContent(mCurrentProgram);
                        // Prepare to play the upcoming program
                        mDbHandler.postDelayed(mPlayCurrentProgramRunnable,
                                mCurrentProgram.getEndTimeUtcMillis() - System.currentTimeMillis() +
                                        1000);
                    }
                    return true;
                case MSG_TUNE_CHANNEL:
                    playChannel((Channel) msg.obj);
                    return true;
                case MSG_PLAY_AD:
                    return insertAd((Advertisement) msg.obj);
            }
            return false;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            if (getTvPlayer() != null) {
                getTvPlayer().setSurface(surface);
            }
            mSurface = surface;
            return true;
        }

        @Override
        public void onSetStreamVolume(float volume) {
            if (getTvPlayer() != null) {
                getTvPlayer().setVolume(volume);
            }
            mVolume = volume;
        }

        public Surface getSurface() {
            return mSurface;
        }

        public float getVolume() {
            return mVolume;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            onReleasePlayer();
            mChannelUri = channelUri;

            // Release Ads assets
            releaseAdController();
            mHandler.removeMessages(MSG_PLAY_AD);
            mTuneMillis = System.currentTimeMillis();

            mDbHandler.removeCallbacks(mPlayCurrentChannelRunnable);
            mPlayCurrentChannelRunnable = new PlayCurrentChannelRunnable(channelUri);
            mDbHandler.post(mPlayCurrentChannelRunnable);

            mUnblockedRatingSet.clear();
            mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            mPlayCurrentProgramRunnable = new PlayCurrentProgramRunnable(channelUri);
            return true;
        }

        @Override
        public void onTimeShiftPause() {
            mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            if (getTvPlayer() != null) {
                getTvPlayer().pause();
            }
        }

        @Override
        public void onTimeShiftResume() {
            mDbHandler.postDelayed(mPlayCurrentProgramRunnable,
                    mCurrentProgram.getEndTimeUtcMillis() - System.currentTimeMillis() + 1000);
            if (DEBUG) {
                Log.d(TAG, "Resume playback of program");
            }
            if (getTvPlayer() != null) {
                getTvPlayer().play();
            }
            // Resume and make sure media is playing at regular speed
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PlaybackParams normalParams = new PlaybackParams();
                normalParams.setSpeed(1);
                onTimeShiftSetPlaybackParams(normalParams);
            }
        }

        @Override
        public void onTimeShiftSeekTo(long timeMs) {
            // Update our handler because we have changed the playback time.
            if (getTvPlayer() != null) {
                getTvPlayer().seekTo(timeMs - mCurrentProgram.getStartTimeUtcMillis());
            }
            mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            mDbHandler.postDelayed(mPlayCurrentProgramRunnable,
                    mCurrentProgram.getEndTimeUtcMillis() - timeMs + 1000);
        }

        @Override
        public long onTimeShiftGetStartPosition() {
            return mCurrentProgram.getStartTimeUtcMillis();
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public long onTimeShiftGetCurrentPosition() {
            if (getTvPlayer() != null) {
                return getTvPlayer().getCurrentPosition() + mCurrentProgram.getStartTimeUtcMillis();
            }
            return TvInputManager.TIME_SHIFT_INVALID_TIME;
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
            mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            if (DEBUG) {
                Log.d(TAG, "Set playback speed to " + params.getSpeed());
            }
            if (getTvPlayer() != null) {
                getTvPlayer().setPlaybackParams(params);
            }
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

        private boolean playProgram(Program program) {
            if (program == null) {
                return onPlayProgram(program, 0);
            }
            long nowMs = System.currentTimeMillis();
            long seekPosMs = nowMs - program.getStartTimeUtcMillis();
            List<Advertisement> ads = InternalProviderDataUtil.parseAds(
                    program.getInternalProviderData());
            // Minus past ad playback time to seek to the correct content playback position.
            for (Advertisement ad : ads) {
                if (ad.getStopTimeUtcMillis() < nowMs) {
                    seekPosMs -= (ad.getStopTimeUtcMillis() - ad.getStartTimeUtcMillis());
                }
            }
            return onPlayProgram(program, seekPosMs);
        }

        private void playChannel(Channel channel) {
            mCurrentChannel = channel;
            List<Advertisement> ads = InternalProviderDataUtil.parseAds(
                    channel.getInternalProviderData());
            if (!ads.isEmpty() && System.currentTimeMillis() - mLastNewChannelAdWatchedTimeMs
                    > mMinimalAdIntervalOnTune) {
                // There is at most one advertisement in the channel.
                mHandler.obtainMessage(MSG_PLAY_AD, ads.get(0)).sendToTarget();
            } else {
                mDbHandler.post(mPlayCurrentProgramRunnable);
            }
            onPlayChannel(channel);
        }

        private boolean insertAd(Advertisement ad) {
            if (DEBUG) {
                Log.d(TAG, "Insert an ad");
            }
            releaseAdController();
            mAdController = new AdController(mContext);
            mAdController.requestAds(ad.getRequestUrl(), new AdControllerCallbackImpl());
            return true;
        }

        private void releaseAdController() {
            if (mAdController != null) {
                mAdController.release();
            }
        }

        /**
         * Return the current {@link TvPlayer}.
         */
        public abstract TvPlayer getTvPlayer();

        /**
         * Called when the media player should stop playing content and be released
         */
        public abstract void onReleasePlayer();

        /**
         * This method is called when a particular program is to begin playing at the particular
         * position. If there is not a program scheduled in the EPG, the parameter will be
         * {@code null}. Developers should check the null condition and handle that case, possibly
         * by manually resyncing the EPG.
         *
         * @param program The program that is set to be playing for a the currently tuned channel.
         * @param startPosMs Start position of content video.
         * @return Whether playing this program was successful.
         */
        public boolean onPlayProgram(Program program, long startPosMs) {
            return true;
        }

        /**
         * This method is called when the user tunes to a given channel. Developers can override
         * this if they want specific behavior to occur after the user tunes but before the program
         * begins playing.
         *
         * @param channel The channel that the user wants to watch.
         */
        public void onPlayChannel(Channel channel) {
            // Do nothing.
        }

        /**
         * Called when ads player is about to be created.
         *
         * @param videoType The media source type. Could be {@link TvContractUtils#SOURCE_TYPE_HLS},
         * {@link TvContractUtils#SOURCE_TYPE_HTTP_PROGRESSIVE},
         * or {@link TvContractUtils#SOURCE_TYPE_MPEG_DASH}.
         * @param videoUri The URI of video source.
         * @return A {@link AdController.VideoPlayer} created in subclass for ads playback.
         */
        public abstract AdController.VideoPlayer onCreateAdPlayer(int videoType, Uri videoUri);

        /**
         * Set minimal interval between two ads at the start of new channels. If there was ads
         * played in the past minimal interval, the current ads on new channels will be skipped for
         * a better user experience. The default value of minimal interval is 5 minutes.
         *
         * @param minimalAdIntervalOnTune The minimal interval for ads at the start of new channels.
         */
        public void setMinimalAdIntervalOnTune(long minimalAdIntervalOnTune) {
            mMinimalAdIntervalOnTune = minimalAdIntervalOnTune;
        }

        public Uri getCurrentChannelUri() {
            return mChannelUri;
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
                    if (playProgram(mCurrentProgram)) {
                        checkProgramContent(mCurrentProgram);
                    }
                }
                notifyContentAllowed();
            }
        }

        private final class AdControllerCallbackImpl implements AdController.AdControllerCallback {
            private static final long INVALID_POSITION = -1;

            // Content video position before ad insertion. If no video was played before, it will be
            // set to INVALID_POSITION.
            private long mContentPosMs = INVALID_POSITION;

            @Override
            public AdController.VideoPlayer onAdReadyToPlay(String adVideoUrl) {
                if (getTvPlayer() != null) {
                    mContentPosMs = getTvPlayer().getCurrentPosition();
                }
                onReleasePlayer();
                AdController.VideoPlayer adPlayer =
                        onCreateAdPlayer(TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE,
                            Uri.parse(adVideoUrl));
                adPlayer.setSurface(getSurface());
                adPlayer.setVolume(getVolume());
                return adPlayer;
            }

            @Override
            public void onAdCompleted() {
                if (mContentPosMs != INVALID_POSITION) {
                    // Resume channel content playback.
                    onPlayProgram(mCurrentProgram, mContentPosMs);
                } else {
                    // No video content was played before ad insertion. Start querying database to
                    // get channel program information.
                    mHandler.post(mPlayCurrentProgramRunnable);
                    mLastNewChannelAdWatchedTimeMs = System.currentTimeMillis();
                }
            }

            @Override
            public void onAdError() {
                Log.e(TAG, "An error occurred playing ads");
                mDbHandler.post(mPlayCurrentProgramRunnable);
            }
        }

        private class PlayCurrentProgramRunnable implements Runnable {
            private final Uri mChannelUri;

            PlayCurrentProgramRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                // Release Ads assets
                releaseAdController();
                mHandler.removeMessages(MSG_PLAY_AD);

                ContentResolver resolver = mContext.getContentResolver();
                Program program = TvContractUtils.getCurrentProgram(resolver, mChannelUri);
                if (program != null) {
                    List<Advertisement> ads = InternalProviderDataUtil
                            .parseAds(program.getInternalProviderData());
                    Collections.sort(ads);
                    long currentTimeMs = System.currentTimeMillis();
                    for (Advertisement ad : ads) {
                        // Skips all past ads. If the program happened to be tuned when one ad is
                        // being scheduled to play, this ad will be played from beginning.
                        // {@link #playProgram(Program)} will calculate the correct start position
                        // of program content.
                        if (ad.getStopTimeUtcMillis() > currentTimeMs) {
                            Message pauseContentPlayAdMsg = mHandler.obtainMessage(MSG_PLAY_AD, ad);
                            long adPosMs = ad.getStartTimeUtcMillis() - currentTimeMs;
                            if (adPosMs < 0) {
                                // If tuning to the middle of a scheduled ad, the ad will be treated
                                // in the same way as ads on new channel. By the completion of this
                                // ad, another PlayCurrentProgramRunnable will be posted to schedule
                                // content playing and the following ads.
                                mHandler.sendMessage(pauseContentPlayAdMsg);
                                return;
                            }
                            mHandler.sendMessageDelayed(pauseContentPlayAdMsg, adPosMs);
                        }
                    }
                } else {
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
                Channel channel = TvContractUtils.getChannel(resolver, mChannelUri);
                mHandler.obtainMessage(MSG_TUNE_CHANNEL, channel).sendToTarget();
            }
        }
    }
}