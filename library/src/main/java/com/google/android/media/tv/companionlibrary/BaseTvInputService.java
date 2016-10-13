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

package com.google.android.media.tv.companionlibrary;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.LongSparseArray;
import android.view.Surface;

import com.google.android.media.tv.companionlibrary.model.Advertisement;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.model.RecordedProgram;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /**
     * Used for interacting with {@link SharedPreferences}.
     * @hide
     */
    public static final String PREFERENCES_FILE_KEY =
            "com.google.android.media.tv.companionlibrary";
    /**
     * Base key string used to identifying last played ad times for a channel
     * TODO This key will be shared by multiple Sessions (e.g. PIP)
     * @hide
     */
    public static final String SHARED_PREFERENCES_KEY_LAST_CHANNEL_AD_PLAY =
            "last_program_ad_time_ms";

    // For database calls
    private static HandlerThread mDbHandlerThread;

    // Map of channel {@link TvContract.Channels#_ID} to Channel objects
    private static LongSparseArray<Channel> mChannelMap;
    private static ContentResolver mContentResolver;
    private static ContentObserver mChannelObserver;

    // For content ratings
    private static final List<Session> mSessions = new ArrayList<>();
    private final BroadcastReceiver mParentalControlsBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            for (Session session : mSessions) {
                TvInputManager manager =
                        (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);

                if (!manager.isParentalControlsEnabled()) {
                    session.onUnblockContent(null);
                } else {
                    session.checkCurrentProgramContent();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // Create background thread
        mDbHandlerThread = new HandlerThread(getClass().getSimpleName());
        mDbHandlerThread.start();

        // Initialize the channel map and set observer for changes
        mContentResolver = BaseTvInputService.this.getContentResolver();
        updateChannelMap();
        mChannelObserver = new ContentObserver(new Handler(mDbHandlerThread.getLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                updateChannelMap();
            }
        };
        mContentResolver.registerContentObserver(TvContract.Channels.CONTENT_URI, true,
                mChannelObserver);

        // Setup our BroadcastReceiver
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
        intentFilter.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        registerReceiver(mParentalControlsBroadcastReceiver, intentFilter);
    }

    private void updateChannelMap() {
        ComponentName component = new ComponentName(BaseTvInputService.this.getPackageName(),
                BaseTvInputService.this.getClass().getName());
        String inputId = TvContract.buildInputId(component);
        mChannelMap = TvContractUtils.buildChannelMap(mContentResolver, inputId);
    }

    /**
     * Adds the Session to the list of currently available sessions.
     * @param session The newly created session.
     * @return The session that was created.
     */
    public Session sessionCreated(Session session) {
        mSessions.add(session);
        return session;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mParentalControlsBroadcastReceiver);
        mContentResolver.unregisterContentObserver(mChannelObserver);
        mDbHandlerThread.quit();
        mDbHandlerThread = null;
    }

    /**
     * A {@link BaseTvInputService.Session} is called when a user tunes to channel provided by
     * this {@link BaseTvInputService}.
     */
    public static abstract class Session extends TvInputService.Session implements Handler.Callback {
        private static final int MSG_PLAY_CONTENT = 1000;
        private static final int MSG_PLAY_AD = 1001;
        private static final int MSG_PLAY_RECORDED_CONTENT = 1002;

        private final Context mContext;
        private final TvInputManager mTvInputManager;
        private Channel mCurrentChannel;
        private boolean mNeedToCheckChannelAd;
        private boolean mPlayingRecordedProgram;
        private RecordedProgram mRecordedProgram;
        private Program mCurrentProgram;
        private long mPlaybackStartTime = TvInputManager.TIME_SHIFT_INVALID_TIME;

        private TvContentRating mLastBlockedRating;
        private TvContentRating[] mCurrentContentRatingSet;

        private final Set<TvContentRating> mUnblockedRatingSet = new HashSet<>();
        private final Handler mDbHandler;
        private final Handler mHandler;
        private GetCurrentProgramRunnable mGetCurrentProgramRunnable;

        private long mMinimumOnTuneAdInterval = TimeUnit.MINUTES.toMillis(5);
        private AdController mAdController;
        private Uri mChannelUri;
        private Surface mSurface;
        private float mVolume;

        public Session(Context context, String inputId) {
            super(context);
            this.mContext = context;
            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            mLastBlockedRating = null;
            mDbHandler = new Handler(mDbHandlerThread.getLooper());
            mHandler = new Handler(this);
        }

        @Override
        public void onRelease() {
            mDbHandler.removeCallbacksAndMessages(null);
            mHandler.removeCallbacksAndMessages(null);
            releaseAdController();
            mSessions.remove(this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PLAY_CONTENT:
                    mCurrentProgram = (Program) msg.obj;
                    playCurrentContent();
                    return true;
                case MSG_PLAY_AD:
                    return insertAd((Advertisement) msg.obj);
                case MSG_PLAY_RECORDED_CONTENT:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        mPlayingRecordedProgram = true;
                        mRecordedProgram = (RecordedProgram) msg.obj;
                        playRecordedContent();
                    }
                    return true;
            }
            return false;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            setTvPlayerSurface(surface);
            mSurface = surface;
            return true;
        }

        private void setTvPlayerSurface(Surface surface) {
            if (getTvPlayer() != null) {
                getTvPlayer().setSurface(surface);
            }
        }

        @Override
        public void onSetStreamVolume(float volume) {
            setTvPlayerVolume(volume);
            mVolume = volume;
        }

        private void setTvPlayerVolume(float volume) {
            if (getTvPlayer() != null) {
                getTvPlayer().setVolume(volume);
            }
        }

        @Override
        public boolean onTune(Uri channelUri) {
            mNeedToCheckChannelAd = true;

            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

            mChannelUri = channelUri;
            long channelId = ContentUris.parseId(channelUri);
            mCurrentChannel = mChannelMap.get(channelId);

            // Release Ads assets
            releaseAdController();
            mHandler.removeMessages(MSG_PLAY_AD);

            if (mDbHandler != null) {
                mUnblockedRatingSet.clear();
                mDbHandler.removeCallbacks(mGetCurrentProgramRunnable);
                mGetCurrentProgramRunnable = new GetCurrentProgramRunnable(mChannelUri);
                mDbHandler.post(mGetCurrentProgramRunnable);
            }
            return true;
        }

        @Override
        public void onTimeShiftPause() {
            mDbHandler.removeCallbacks(mGetCurrentProgramRunnable);
            if (getTvPlayer() != null) {
                getTvPlayer().pause();
            }
        }

        @Override
        public void onTimeShiftResume() {
            if (DEBUG) Log.d(TAG, "Resume playback of program");
            if (mCurrentProgram == null) {
                return;
            }

            if (!mPlayingRecordedProgram) {
                mDbHandler.postDelayed(mGetCurrentProgramRunnable,
                        mCurrentProgram.getEndTimeUtcMillis() - System.currentTimeMillis() + 1000);
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
            if (DEBUG) Log.d(TAG, "Seeking to the position: " + timeMs);
            if (mCurrentProgram == null) {
                return;
            }
            // Update our handler because we have changed the playback time.
            if (getTvPlayer() != null) {
                if (mPlayingRecordedProgram) {
                    getTvPlayer().seekTo(timeMs - mPlaybackStartTime);
                } else {
                    getTvPlayer().seekTo(timeMs - mCurrentProgram.getStartTimeUtcMillis());
                }
            }
            mDbHandler.removeCallbacks(mGetCurrentProgramRunnable);

            if (!mPlayingRecordedProgram) {
                mDbHandler.postDelayed(mGetCurrentProgramRunnable,
                        mCurrentProgram.getEndTimeUtcMillis() - timeMs + 1000);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public long onTimeShiftGetStartPosition() {
            if (mCurrentProgram != null) {
                if (mPlayingRecordedProgram) {
                    return mPlaybackStartTime;
                } else {
                    return mCurrentProgram.getStartTimeUtcMillis();
                }
            }
            return TvInputManager.TIME_SHIFT_INVALID_TIME;
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public long onTimeShiftGetCurrentPosition() {
            if (getTvPlayer() != null && mCurrentProgram != null) {
                if (mPlayingRecordedProgram) {
                    long recordingStartTime = mCurrentProgram.getInternalProviderData()
                            .getRecordedProgramStartTime();
                    return getTvPlayer().getCurrentPosition() - (recordingStartTime -
                            mCurrentProgram.getStartTimeUtcMillis()) + mPlaybackStartTime;
                } else {
                    return getTvPlayer().getCurrentPosition() + mCurrentProgram.getStartTimeUtcMillis();
                }
            }
            return TvInputManager.TIME_SHIFT_INVALID_TIME;
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
            if (params.getSpeed() != 1) {
                mDbHandler.removeCallbacks(mGetCurrentProgramRunnable);
            }
            if (DEBUG) {
                Log.d(TAG, "Set playback speed to " + params.getSpeed());
            }
            if (getTvPlayer() != null) {
                getTvPlayer().setPlaybackParams(params);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onTimeShiftPlay(Uri recordedProgramUri) {
            if (DEBUG) {
                Log.d(TAG, "onTimeShiftPlay " + recordedProgramUri);
            }
            GetRecordedProgramRunnable getRecordedProgramRunnable =
                    new GetRecordedProgramRunnable(recordedProgramUri);
            mDbHandler.post(getRecordedProgramRunnable);
        }

        /**
         * This method is called when the currently playing program has been blocked by parental
         * controls. Developers should release their {@link TvPlayer} immediately so unwanted
         * content is not displayed.
         *
         * @param rating The rating for the program that was blocked.
         */
        public void onBlockContent(TvContentRating rating) {
        }

        @Override
        public void onUnblockContent(TvContentRating rating) {
            // If called with null, parental controls are off.
            if (rating == null) {
                mUnblockedRatingSet.clear();
            }

            unblockContent(rating);
            if (mPlayingRecordedProgram) {
                playRecordedContent();
            } else {
                playCurrentContent();
            }
        }

        private boolean checkCurrentProgramContent() {
            mCurrentContentRatingSet = (mCurrentProgram == null
                    || mCurrentProgram.getContentRatings() == null
                    || mCurrentProgram.getContentRatings().length == 0) ? null :
                    mCurrentProgram.getContentRatings();
            return blockContentIfNeeded();
        }

        private void playRecordedContent() {
            mCurrentProgram = mRecordedProgram.toProgram();
            if (mTvInputManager.isParentalControlsEnabled() && !checkCurrentProgramContent()) {
                return;
            }

            mPlaybackStartTime = System.currentTimeMillis();
            if (onPlayRecordedProgram(mRecordedProgram)) {
                setTvPlayerSurface(mSurface);
                setTvPlayerVolume(mVolume);
            }
        }

        private void playCurrentContent() {
            if (mTvInputManager.isParentalControlsEnabled() && !checkCurrentProgramContent()) {
                mDbHandler.removeCallbacks(mGetCurrentProgramRunnable);
                mDbHandler.postDelayed(mGetCurrentProgramRunnable,
                        mCurrentProgram.getEndTimeUtcMillis()
                                - System.currentTimeMillis() + 1000);
                return;
            }

            if (mNeedToCheckChannelAd) {
                playCurrentChannel();
                return;
            }

            if (playCurrentProgram()) {
                setTvPlayerSurface(mSurface);
                setTvPlayerVolume(mVolume);
                if (mCurrentProgram != null) {
                    // Prepare to play the upcoming program
                    mDbHandler.removeCallbacks(mGetCurrentProgramRunnable);
                    mDbHandler.postDelayed(mGetCurrentProgramRunnable,
                            mCurrentProgram.getEndTimeUtcMillis()
                                    - System.currentTimeMillis() + 1000);
                }
            }
        }

        private boolean playCurrentProgram() {
            if (mCurrentProgram == null) {
                Log.w(TAG, "Failed to get program info for " + mChannelUri + ". Try to do an " +
                            "EPG sync.");
                return onPlayProgram(null, 0);
            }
            long currentTimeMs = System.currentTimeMillis();
            long seekPosMs = currentTimeMs - mCurrentProgram.getStartTimeUtcMillis();
            if (mCurrentProgram.getInternalProviderData() != null) {
                List<Advertisement> ads = mCurrentProgram.getInternalProviderData().getAds();
                Advertisement adToPlay = null;
                long timeTilAdToPlay = 0;
                for (Advertisement ad : ads) {
                    if (ad.getStopTimeUtcMillis() < currentTimeMs) {
                        // Subtract past ad playback time to seek to
                        // the correct content playback position.
                        seekPosMs -= (ad.getStopTimeUtcMillis() - ad.getStartTimeUtcMillis());
                    } else {
                        long timeTilAd = ad.getStartTimeUtcMillis() - currentTimeMs;
                        if (timeTilAd < 0) {
                            // If tuning to the middle of a scheduled ad, the played portion
                            // of the ad will be skipped by the AdControllerCallback.
                            mHandler.sendMessage(mHandler.obtainMessage(MSG_PLAY_AD, ad));
                            return false;
                        } else if (adToPlay == null || timeTilAd < timeTilAdToPlay) {
                            adToPlay = ad;
                            timeTilAdToPlay = timeTilAd;
                        }
                    }
                }

                if (adToPlay != null) {
                    Message pauseContentPlayAdMsg = mHandler.obtainMessage(MSG_PLAY_AD, adToPlay);
                    mHandler.sendMessageDelayed(pauseContentPlayAdMsg, timeTilAdToPlay);
                }
            } else {
                Log.w(TAG, "Failed to get program provider data for " +
                        mCurrentProgram.getTitle() + ". Try to do an EPG sync.");
            }
            return onPlayProgram(mCurrentProgram, seekPosMs);
        }

        private void playCurrentChannel() {
            Message playAd = null;
            if (mCurrentChannel.getInternalProviderData() != null) {
                // Get the last played ad time for this channel
                long mostRecentOnTuneAdWatchedTime =
                        mContext.getSharedPreferences(PREFERENCES_FILE_KEY,
                                Context.MODE_PRIVATE)
                                .getLong(SHARED_PREFERENCES_KEY_LAST_CHANNEL_AD_PLAY +
                                        mCurrentChannel.getId(), 0);
                List<Advertisement> ads = mCurrentChannel.getInternalProviderData().getAds();
                if (!ads.isEmpty() && System.currentTimeMillis() - mostRecentOnTuneAdWatchedTime
                        > mMinimumOnTuneAdInterval) {
                    // There is at most one advertisement in the channel.
                    playAd = mHandler.obtainMessage(MSG_PLAY_AD, ads.get(0));
                }
            }
            onPlayChannel(mCurrentChannel);

            if (playAd != null) {
                playAd.sendToTarget();
            } else {
                mNeedToCheckChannelAd = false;
                playCurrentContent();
            }
        }
        private boolean insertAd(Advertisement ad) {
            if (DEBUG) {
                Log.d(TAG, "Insert an ad");
            }
            releaseAdController();
            mAdController = new AdController(mContext);
            mAdController.requestAds(ad.getRequestUrl(), new AdControllerCallbackImpl(ad));
            return true;
        }

        private void releaseAdController() {
            if (mAdController != null) {
                mAdController.release();
                mAdController = null;
            }
        }

        /**
         * Return the current {@link TvPlayer}.
         */
        public abstract TvPlayer getTvPlayer();

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
        public abstract boolean onPlayProgram(Program program, long startPosMs);

        /**
         * This method is called when a particular recorded program is to begin playing. If the
         * program does not exist, the parameter will be {@code null}.
         *
         * @param recordedProgram The program that is set to be playing for a the currently tuned
         * channel.
         * @return Whether playing this program was successful
         */
        public abstract boolean onPlayRecordedProgram(RecordedProgram recordedProgram);

        /**
         * This method is called when the user tunes to a given channel. Developers can override
         * this if they want specific behavior to occur after the user tunes but before the program
         * or channel ad begins playing.
         *
         * @param channel The channel that the user wants to watch.
         */
        public void onPlayChannel(Channel channel) {
            // Do nothing.
        }

        /**
         * Called when ads player is about to be created. Developers should override this if they
         * want to enable ads insertion.
         *
         * @param advertisement The advertisement that should be played.
         */
        public void onPlayAdvertisement(Advertisement advertisement) {
            throw new UnsupportedOperationException(
                    "Override BaseTvInputService.Session.onPlayAdvertisement(int, Uri) to enable " +
                            "ads insertion.");
        }

        /**
         * Set minimum interval between two ads shown on tuning to new channels. If another
         * channel ad played within the past minimum interval, tuning to a new channel will not
         * trigger the new channel's ads to be shown. This provides a better user experience.
         * The default value of the minimum interval is 5 minutes.
         *
         * @param minimumOnTuneAdInterval The minimum interval between playing channel ads
         */
        public void setMinimumOnTuneAdInterval(long minimumOnTuneAdInterval) {
            mMinimumOnTuneAdInterval = minimumOnTuneAdInterval;
        }

        public Uri getCurrentChannelUri() {
            return mChannelUri;
        }

        private boolean blockContentIfNeeded() {
            if (mCurrentContentRatingSet == null || !mTvInputManager.isParentalControlsEnabled()) {
                // Content rating is invalid so we don't need to block anymore.
                // Unblock content here explicitly to resume playback.
                unblockContent(null);
                return true;
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
                return true;
            }
            mLastBlockedRating = blockedRating;
            // Children restricted content might be blocked by TV app as well,
            // but TIS should do its best not to show any single frame of blocked content.
            onBlockContent(blockedRating);
            notifyContentBlocked(blockedRating);
            return false;
        }

        private void unblockContent(TvContentRating rating) {
            // TIS should unblock content only if unblock request is legitimate.
            if (rating == null || mLastBlockedRating == null || rating.equals(mLastBlockedRating)) {
                mLastBlockedRating = null;
                if (rating != null) {
                    mUnblockedRatingSet.add(rating);
                }
                notifyContentAllowed();
            }
        }

        private final class AdControllerCallbackImpl implements AdController.AdControllerCallback {
            private Advertisement mAdvertisement;

            public AdControllerCallbackImpl(Advertisement advertisement) {
                mAdvertisement = advertisement;
            }

            @Override
            public TvPlayer onAdReadyToPlay(String adVideoUrl) {
                onPlayAdvertisement(new Advertisement.Builder(mAdvertisement)
                        .setRequestUrl(adVideoUrl)
                        .build());
                setTvPlayerSurface(mSurface);
                setTvPlayerVolume(mVolume);

                long currentTimeMs = System.currentTimeMillis();
                long adStartTime = mAdvertisement.getStartTimeUtcMillis();
                if (adStartTime > 0 && adStartTime < currentTimeMs) {
                    getTvPlayer().seekTo(currentTimeMs - adStartTime);
                }
                return getTvPlayer();
            }

            @Override
            public void onAdCompleted() {
                if (DEBUG) {
                    Log.i(TAG, "Ad completed");
                }
                // Check if the ad played was an on-tune Channel ad
                if (mNeedToCheckChannelAd) {
                    // In some TV apps, opening the guide will cause the session to restart, so this
                    // value is stored in SharedPreferences to persist between sessions.
                    SharedPreferences.Editor editor = mContext.getSharedPreferences(
                            PREFERENCES_FILE_KEY, Context.MODE_PRIVATE).edit();
                    editor.putLong(SHARED_PREFERENCES_KEY_LAST_CHANNEL_AD_PLAY +
                            mCurrentChannel.getId(), System.currentTimeMillis());
                    editor.apply();
                    mNeedToCheckChannelAd = false;
                }
                playCurrentContent();
            }

            @Override
            public void onAdError() {
                Log.e(TAG, "An error occurred playing ads");
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                onAdCompleted();
            }
        }

        private class GetCurrentProgramRunnable implements Runnable {
            private final Uri mChannelUri;

             GetCurrentProgramRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                ContentResolver resolver = mContext.getContentResolver();
                Program program = TvContractUtils.getCurrentProgram(resolver, mChannelUri);
                mHandler.removeMessages(MSG_PLAY_CONTENT);
                mHandler.obtainMessage(MSG_PLAY_CONTENT, program).sendToTarget();
            }
        }

        private class GetRecordedProgramRunnable implements Runnable {
            private final Uri mRecordedProgramUri;

            GetRecordedProgramRunnable(Uri recordedProgramUri) {
                mRecordedProgramUri = recordedProgramUri;
            }

            @Override
            public void run() {
                ContentResolver contentResolver = mContext.getContentResolver();
                Cursor cursor = contentResolver.query(mRecordedProgramUri,
                        RecordedProgram.PROJECTION, null, null, null);
                if (cursor == null) {
                    // The recorded program does not exist.
                    notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                } else {
                    if (cursor.moveToNext()) {
                        RecordedProgram recordedProgram = RecordedProgram.fromCursor(cursor);
                        if (DEBUG) {
                            Log.d(TAG, "Play program " + recordedProgram.getTitle());
                            Log.d(TAG, recordedProgram.getRecordingDataUri());
                        }
                        if (recordedProgram == null) {
                            Log.e(TAG, "RecordedProgram at " + mRecordedProgramUri + " does not " +
                                    "exist");
                            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                        }
                        mHandler.removeMessages(MSG_PLAY_RECORDED_CONTENT);
                        mHandler.obtainMessage(MSG_PLAY_RECORDED_CONTENT, recordedProgram)
                                .sendToTarget();
                    }
                }
            }
        }
    }

    /**
     * A {@link BaseTvInputService.RecordingSession} is created when a user wants to begin recording
     * a particular channel or program.
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public static abstract class RecordingSession extends TvInputService.RecordingSession {
        private Context mContext;
        private String mInputId;
        private Uri mChannelUri;
        private Uri mProgramUri;
        private Handler mDbHandler;

        public RecordingSession(Context context, String inputId) {
            super(context);
            mContext = context;
            mInputId = inputId;
            mDbHandler = new Handler(mDbHandlerThread.getLooper());
        }

        @Override
        public void onTune(Uri uri) {
            mChannelUri = uri;
        }

        @Override
        public void onStartRecording(final Uri uri) {
            mProgramUri = uri;
        }

        @Override
        public void onStopRecording() {
            // Run in the database thread
            mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Check if user wanted to record a specific program
                    if (mProgramUri != null) {
                        Cursor programCursor =
                                mContext.getContentResolver().query(mProgramUri, Program.PROJECTION,
                                        null, null, null);
                        if (programCursor != null && programCursor.moveToNext()) {
                            Program programToRecord = Program.fromCursor(programCursor);
                            onStopRecording(programToRecord);
                        } else {
                            Channel recordedChannel =
                                    TvContractUtils.getChannel(mContext.getContentResolver(),
                                            mChannelUri);
                            onStopRecordingChannel(recordedChannel);
                        }
                    } else {
                        // User is recording a channel
                        Channel recordedChannel =
                                TvContractUtils.getChannel(mContext.getContentResolver(),
                                        mChannelUri);
                        onStopRecordingChannel(recordedChannel);
                    }
                }
            });
        }

        /**
         * Called when the application requests to stop TV program recording. Recording must stop
         * immediately when this method is called.
         * </p>
         * The session must create a new data entry using
         * {@link #notifyRecordingStopped(RecordedProgram)} that describes the new
         * {@link RecordedProgram} and call {@link #notifyRecordingStopped(Uri)} with the URI to
         * that entry. If the stop request cannot be fulfilled, the session must call
         * {@link #notifyError(int)}.
         *
         * @param programToRecord The program set by the user to be recorded.
         */
        public abstract void onStopRecording(Program programToRecord);

        /**
         * Called when the application requests to stop TV channel recording. Recording must stop
         * immediately when this method is called.
         * </p>
         * The session must create a new data entry using
         * {@link #notifyRecordingStopped(RecordedProgram)} that describes the new
         * {@link RecordedProgram} and call {@link #notifyRecordingStopped(Uri)} with the URI to
         * that entry. If the stop request cannot be fulfilled, the session must call
         * {@link #notifyError(int)}.
         *
         * @param channelToRecord The channel set by the user to be recorded.
         */
        public abstract void onStopRecordingChannel(Channel channelToRecord);

        /**
         * Notify the TV app that the recording has ended.
         *
         * @param recordedProgram The program that was recorded and should be saved.
         */
        public void notifyRecordingStopped(final RecordedProgram recordedProgram) {
            mDbHandler.post(new Runnable() {
                @Override
                public void run() {
                    Uri recordedProgramUri = mContext.getContentResolver().insert(
                            TvContract.RecordedPrograms.CONTENT_URI,
                            recordedProgram.toContentValues());
                    notifyRecordingStopped(recordedProgramUri);
                }
            });
        }

    }
}
