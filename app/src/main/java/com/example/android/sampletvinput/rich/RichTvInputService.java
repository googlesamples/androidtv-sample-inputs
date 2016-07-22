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

package com.example.android.sampletvinput.rich;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Point;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;

import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.ads.AdController;
import com.example.android.sampletvinput.ads.AdVideoPlayerProxy;
import com.example.android.sampletvinput.model.Advertisement;
import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.InternalProviderData;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.player.DemoPlayer;
import com.example.android.sampletvinput.player.RendererBuilderFactory;
import com.example.android.sampletvinput.sync.EpgSyncJobService;
import com.example.android.sampletvinput.sync.SampleJobService;
import com.example.android.sampletvinput.utils.InternalProviderDataUtil;
import com.example.android.sampletvinput.utils.TvContractUtils;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * TvInputService which provides a full implementation of EPG, subtitles, multi-audio, parental
 * controls, and overlay view.
 */
public class RichTvInputService extends TvInputService {
    private static final String TAG = "RichTvInputService";
    private static final boolean DEBUG = false;
    private static final long SYNC_REQUESTED_PERIOD_MS = 1000 * 60 * 60; // 1 Hour

    private HandlerThread mHandlerThread;
    private Handler mDbHandler;

    private List<RichTvInputSessionImpl> mSessions;
    private CaptioningManager mCaptioningManager;

    private final BroadcastReceiver mParentalControlsBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mSessions != null) {
                for (RichTvInputSessionImpl session : mSessions) {
                    session.checkContentBlockNeeded();
                }
            }
        }
    };

    /**
     * Gets the track id of the track type and track index.
     *
     * @param trackType  the type of the track e.g. TvTrackInfo.TYPE_AUDIO
     * @param trackIndex the index of that track within the media. e.g. 0, 1, 2...
     * @return the track id for the type & index combination.
     */
    private static String getTrackId(int trackType, int trackIndex) {
        return trackType + "-" + trackIndex;
    }

    /**
     * Gets the index of the track for a given track id.
     *
     * @param trackId the track id.
     * @return the track index for the given id, as an integer.
     */
    private static int getIndexFromTrackId(String trackId) {
        return Integer.parseInt(trackId.split("-")[1]);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mDbHandler = new Handler(mHandlerThread.getLooper());
        mCaptioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);

        setTheme(android.R.style.Theme_Holo_Light_NoActionBar);

        mSessions = new ArrayList<>();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
        intentFilter.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        registerReceiver(mParentalControlsBroadcastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mParentalControlsBroadcastReceiver);
        mHandlerThread.quit();
        mHandlerThread = null;
        mDbHandler = null;
    }

    @Override
    public final Session onCreateSession(String inputId) {
        RichTvInputSessionImpl session = new RichTvInputSessionImpl(this, inputId);
        session.setOverlayViewEnabled(true);
        mSessions.add(session);
        return session;
    }

    class RichTvInputSessionImpl extends TvInputService.Session implements Handler.Callback,
            DemoPlayer.Listener, DemoPlayer.CaptionListener {
        private static final int MSG_PLAY_PROGRAM = 1000;
        private static final int MSG_PLAY_AD = 1001;
        private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;
        private static final int TEXT_UNIT_PIXELS = 0;
        private final long MIN_AD_INTERVAL_ON_TUNE_MS = TimeUnit.MINUTES.toMillis(5);

        private final Context mContext;
        private final String mInputId;
        private final TvInputManager mTvInputManager;
        private Surface mSurface;
        private float mVolume;
        private boolean mCaptionEnabled;
        private Program mCurrentProgram;
        private TvContentRating mLastBlockedRating;
        private TvContentRating mCurrentContentRating;
        private int mSelectedSubtitleTrackIndex;
        private SubtitleLayout mSubtitleView;
        private boolean mEpgSyncRequested;
        private final Set<TvContentRating> mUnblockedRatingSet = new HashSet<>();
        private final Handler mHandler;
        private PlayCurrentChannelRunnable mPlayCurrentChannelRunnable;
        private PlayCurrentProgramRunnable mPlayCurrentProgramRunnable;
        private int mContentType;
        private Uri mContentUri;
        private AdController mAdController;
        private DemoPlayer mPlayer;
        private long mLastNewChannelAdWatchedTimeMs;

        RichTvInputSessionImpl(Context context, String inputId) {
            super(context);

            mContext = context;
            mInputId = inputId;
            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            mLastBlockedRating = null;
            mCaptionEnabled = mCaptioningManager.isEnabled();
            mHandler = new Handler(this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PLAY_PROGRAM:
                    return playProgram((Program) msg.obj);
                case MSG_PLAY_AD:
                    return insertAd((Advertisement) msg.obj);
            }
            return false;
        }

        @Override
        public void onRelease() {
            if (mDbHandler != null) {
                mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
                mDbHandler.removeCallbacks(mPlayCurrentChannelRunnable);
            }
            releaseAdController();
            releasePlayer();
            mSessions.remove(this);
        }

        @Override
        public View onCreateOverlayView() {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            mSubtitleView = (SubtitleLayout) inflater.inflate(R.layout.subtitleview, null);

            // Configure the subtitle view.
            CaptionStyleCompat captionStyle;
            float captionTextSize = getCaptionFontSize();
            captionStyle = CaptionStyleCompat
                    .createFromCaptionStyle(mCaptioningManager.getUserStyle());
            captionTextSize *= mCaptioningManager.getFontScale();
            mSubtitleView.setStyle(captionStyle);
            mSubtitleView.setFixedTextSize(TEXT_UNIT_PIXELS, captionTextSize);
            mSubtitleView.setVisibility(View.VISIBLE);

            return mSubtitleView;
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
                mPlayer.setVolume(volume);
            }
            mVolume = volume;
        }

        private List<TvTrackInfo> getAllTracks() {
            String trackId;
            List<TvTrackInfo> tracks = new ArrayList<>();

            int[] trackTypes = {
                    DemoPlayer.TYPE_AUDIO,
                    DemoPlayer.TYPE_VIDEO,
                    DemoPlayer.TYPE_TEXT
            };

            for (int trackType : trackTypes) {
                int count = mPlayer.getTrackCount(trackType);
                for (int i = 0; i < count; i++) {
                    MediaFormat format = mPlayer.getTrackFormat(trackType, i);
                    trackId = getTrackId(trackType, i);
                    TvTrackInfo.Builder builder = new TvTrackInfo.Builder(trackType, trackId);

                    if (trackType == DemoPlayer.TYPE_VIDEO) {
                        builder.setVideoWidth(format.width);
                        builder.setVideoHeight(format.height);
                    } else if (trackType == DemoPlayer.TYPE_AUDIO) {
                        builder.setAudioChannelCount(format.channelCount);
                        builder.setAudioSampleRate(format.sampleRate);
                        if (format.language != null) {
                            builder.setLanguage(format.language);
                        }
                    } else if (trackType == DemoPlayer.TYPE_TEXT) {
                        if (format.language != null) {
                            builder.setLanguage(format.language);
                        }
                    }

                    tracks.add(builder.build());
                }
            }
            return tracks;
        }

        private boolean playProgram(Program info) {
            long nowMs = System.currentTimeMillis();
            long seekPosMs = nowMs - info.getStartTimeUtcMillis();
            List<Advertisement> ads = InternalProviderDataUtil.parseAds(
                    info.getInternalProviderData());
            // Minus past ad playback time to seek to the correct content playback position.
            for (Advertisement ad : ads) {
                if (ad.getStopTimeUtcMillis() < nowMs) {
                    seekPosMs -= (ad.getStopTimeUtcMillis() - ad.getStartTimeUtcMillis());
                }
            }
            return playProgram(info, seekPosMs);
        }

        private boolean playProgram(Program info, long startPosMs) {
            releaseAdController();
            releasePlayer();

            mCurrentProgram = info;
            mCurrentContentRating = (info.getContentRatings() == null
                    || info.getContentRatings().length == 0) ? null : info.getContentRatings()[0];
            InternalProviderData programInternalProviderData = info.getInternalProviderData();
            mContentType = programInternalProviderData.getSourceType();
            mContentUri = Uri.parse(programInternalProviderData.getSourceUrl());

            createPlayer();
            if (startPosMs > 0) {
                mPlayer.seekTo(startPosMs);
            }
            mPlayer.setPlayWhenReady(true);

            checkContentBlockNeeded();
            if (mDbHandler != null) {
                mDbHandler.postDelayed(mPlayCurrentProgramRunnable,
                        info.getEndTimeUtcMillis() - System.currentTimeMillis() + 1000);
                return true;
            }
            return false;
        }

        private void createPlayer() {
            mPlayer = new DemoPlayer(RendererBuilderFactory.createRendererBuilder(
                    mContext, mContentType, mContentUri));
            mPlayer.addListener(this);
            mPlayer.setCaptionListener(this);
            mPlayer.prepare();
            mPlayer.setSurface(mSurface);
            mPlayer.setVolume(mVolume);
        }

        @Override
        public boolean onTune(Uri channelUri) {
            if (DEBUG) {
                Log.d(TAG, "tune to " + channelUri.toString());
            }
            // Release unfinished AdController.
            releaseAdController();
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            releasePlayer();
            mUnblockedRatingSet.clear();

            mDbHandler.removeCallbacks(mPlayCurrentChannelRunnable);
            mPlayCurrentChannelRunnable = new PlayCurrentChannelRunnable(channelUri);
            mDbHandler.post(mPlayCurrentChannelRunnable);
            return true;
        }

        private boolean insertAd(Advertisement ad) {
            releaseAdController();
            mAdController = new AdController(mContext);
            mAdController.requestAds(ad.getRequestUrl(), new AdControllerCallbackImpl());
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            mCaptionEnabled = enabled;
            if (mPlayer != null) {
                if (mCaptionEnabled) {
                    mPlayer.setSelectedTrack(TvTrackInfo.TYPE_SUBTITLE,
                            mSelectedSubtitleTrackIndex);
                } else {
                    mPlayer.setSelectedTrack(TvTrackInfo.TYPE_SUBTITLE, DemoPlayer.TRACK_DISABLED);
                }
            }
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            if (trackId == null) {
                return true;
            }

            int trackIndex = getIndexFromTrackId(trackId);
            if (mPlayer != null) {
                if (type == TvTrackInfo.TYPE_SUBTITLE) {
                    if (!mCaptionEnabled) {
                        return false;
                    }
                    mSelectedSubtitleTrackIndex = trackIndex;
                }

                mPlayer.setSelectedTrack(type, trackIndex);
                notifyTrackSelected(type, trackId);
                return true;
            }
            return false;
        }

        @Override
        public void onUnblockContent(TvContentRating rating) {
            if (rating != null) {
                unblockContent(rating);
            }
        }

        private void releaseAdController() {
            if (mAdController != null) {
                mAdController.release();
            }
        }

        private void releasePlayer() {
            if (mPlayer != null) {
                mPlayer.removeListener(this);
                mPlayer.setSurface(null);
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
        }

        private void checkContentBlockNeeded() {
            if (mCurrentContentRating == null || !mTvInputManager.isParentalControlsEnabled()
                    || !mTvInputManager.isRatingBlocked(mCurrentContentRating)
                    || mUnblockedRatingSet.contains(mCurrentContentRating)) {
                // Content rating is changed so we don't need to block anymore.
                // Unblock content here explicitly to resume playback.
                unblockContent(null);
                return;
            }

            mLastBlockedRating = mCurrentContentRating;
            if (mPlayer != null) {
                // Children restricted content might be blocked by TV app as well,
                // but TIS should do its best not to show any single frame of blocked content.
                releasePlayer();
            }

            notifyContentBlocked(mCurrentContentRating);
        }

        private void unblockContent(TvContentRating rating) {
            // TIS should unblock content only if unblock request is legitimate.
            if (rating == null || mLastBlockedRating == null || rating.equals(mLastBlockedRating)) {
                mLastBlockedRating = null;
                if (rating != null) {
                    mUnblockedRatingSet.add(rating);
                }
                if (mPlayer == null && mCurrentProgram != null) {
                    playProgram(mCurrentProgram);
                }
                notifyContentAllowed();
            }
        }

        private float getCaptionFontSize() {
            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE))
                    .getDefaultDisplay();
            Point displaySize = new Point();
            display.getSize(displaySize);
            return Math.max(getResources().getDimension(R.dimen.subtitle_minimum_font_size),
                    CAPTION_LINE_HEIGHT_RATIO * Math.min(displaySize.x, displaySize.y));
        }

        @Override
        public void onStateChanged(boolean playWhenReady, int playbackState) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_BUFFERING) {
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
            } else if (playWhenReady && playbackState == ExoPlayer.STATE_READY) {
                notifyTracksChanged(getAllTracks());
                String audioId = getTrackId(TvTrackInfo.TYPE_AUDIO,
                        mPlayer.getSelectedTrack(TvTrackInfo.TYPE_AUDIO));
                String videoId = getTrackId(TvTrackInfo.TYPE_VIDEO,
                        mPlayer.getSelectedTrack(TvTrackInfo.TYPE_VIDEO));
                String textId = getTrackId(TvTrackInfo.TYPE_SUBTITLE,
                        mPlayer.getSelectedTrack(TvTrackInfo.TYPE_SUBTITLE));

                notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, audioId);
                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, videoId);
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, textId);
                notifyVideoAvailable();
            }
        }

        @Override
        public void onError(Exception e) {
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                float pixelWidthHeightRatio) {
            // Do nothing.
        }

        @Override
        public void onCues(List<Cue> cues) {
            mSubtitleView.setCues(cues);
        }

        private final class AdControllerCallbackImpl implements AdController.AdControllerCallback {
            private static final long INVALID_POSITION = -1;

            // Content video position before ad insertion. If no video was played before, it will be
            // set to INVALID_POSITION.
            private long mContentPosMs;

            @Override
            public AdController.VideoPlayer onAdReadyToPlay(String adVideoUrl) {
                if (mPlayer != null) {
                    // Records current content position in order to resume playback.
                    mContentPosMs = mPlayer.getCurrentPosition();
                } else {
                    // No content is being played in current channel.
                    mContentPosMs = INVALID_POSITION;
                }
                releasePlayer();
                mContentType = TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE;
                mContentUri = Uri.parse(adVideoUrl);
                createPlayer();
                return new AdVideoPlayerProxy(mPlayer);
            }

            @Override
            public void onAdCompleted() {
                if (mContentPosMs != INVALID_POSITION) {
                    // Resume channel content playback.
                    playProgram(mCurrentProgram, mContentPosMs);
                } else {
                    // No video content was played before ad insertion. Start querying database to
                    // get channel program information.
                    mDbHandler.post(mPlayCurrentProgramRunnable);
                    mLastNewChannelAdWatchedTimeMs = System.currentTimeMillis();
                }
            }

            @Override
            public void onAdError() {
                mDbHandler.post(mPlayCurrentProgramRunnable);
            }
        }

        private class PlayCurrentProgramRunnable implements Runnable {
            private static final int RETRY_DELAY_MS = 2000;
            private final Uri mChannelUri;

            public PlayCurrentProgramRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                mDbHandler.removeCallbacks(this);
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
                    mHandler.removeMessages(MSG_PLAY_PROGRAM);
                    mHandler.obtainMessage(MSG_PLAY_PROGRAM, program).sendToTarget();
                } else {
                    Log.w(TAG, "Failed to get program info for " + mChannelUri + ". Retry in "
                            + RETRY_DELAY_MS + "ms.");
                    mDbHandler.postDelayed(mPlayCurrentProgramRunnable, RETRY_DELAY_MS);
                    if (!mEpgSyncRequested) {
                        EpgSyncJobService.requestSync(RichTvInputService.this, mInputId,
                                SYNC_REQUESTED_PERIOD_MS,
                                new ComponentName(RichTvInputService.this, SampleJobService.class));
                        mEpgSyncRequested = true;
                    }
                }
            }
        }

        private class PlayCurrentChannelRunnable implements Runnable {
            private final Uri mChannelUri;

            PlayCurrentChannelRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                mHandler.removeMessages(MSG_PLAY_AD);
                mDbHandler.removeCallbacks(this);
                mDbHandler.removeCallbacks(mPlayCurrentProgramRunnable);
                ContentResolver resolver = mContext.getContentResolver();
                mPlayCurrentProgramRunnable = new PlayCurrentProgramRunnable(mChannelUri);
                Channel channel = TvContractUtils.getChannel(resolver, mChannelUri);
                List<Advertisement> ads = InternalProviderDataUtil.parseAds(
                        channel.getInternalProviderData());
                if (!ads.isEmpty() && System.currentTimeMillis() - mLastNewChannelAdWatchedTimeMs >
                        MIN_AD_INTERVAL_ON_TUNE_MS) {
                    // There is at most one advertisement in the channel.
                    mHandler.obtainMessage(MSG_PLAY_AD, ads.get(0)).sendToTarget();
                } else {
                    mDbHandler.post(mPlayCurrentProgramRunnable);
                }
            }
        }
    }
}
