/*
 * Copyright 2014 Google Inc. All rights reserved.
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

package com.example.android.sampletvinput;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Point;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;

import com.example.android.sampletvinput.player.TvInputPlayer;
import com.example.android.sampletvinput.syncadapter.SyncUtils;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.SubtitleView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

abstract public class BaseTvInputService extends TvInputService {
    private static final String TAG = "BaseTvInputService";
    private static final boolean DEBUG = true;

    private LongSparseArray<ChannelInfo> mChannelMap;
    private HandlerThread mHandlerThread;
    private Handler mDbHandler;
    private Handler mHandler;

    private List<BaseTvInputSessionImpl> mSessions;
    private CaptioningManager mCaptioningManager;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mSessions != null) {
                for (BaseTvInputSessionImpl session : mSessions) {
                    session.checkContentBlockNeeded();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mDbHandler = new Handler(mHandlerThread.getLooper());
        mHandler = new Handler();
        mCaptioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);

        mChannelMap = Utils.buildChannelMap(getContentResolver(),
                Utils.getInputIdFromComponentName(this, new ComponentName(this, this.getClass())),
                createSampleChannels());
        setTheme(android.R.style.Theme_Holo_Light_NoActionBar);

        mSessions = new ArrayList<BaseTvInputSessionImpl>();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TvInputManager.ACTION_BLOCKED_RATINGS_CHANGED);
        intentFilter.addAction(TvInputManager.ACTION_PARENTAL_CONTROLS_ENABLED_CHANGED);
        registerReceiver(mBroadcastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        mHandlerThread.quit();
        mHandlerThread = null;
        mDbHandler = null;
    }

    @Override
    public final Session onCreateSession(String inputId) {
        BaseTvInputSessionImpl session = onCreateSessionInternal(inputId);
        session.setOverlayViewEnabled(true);
        mSessions.add(session);
        return session;
    }

    /**
     * Child classes should extend this to change the result of onCreateSession.
     */
    public BaseTvInputSessionImpl onCreateSessionInternal(String inputId) {
        return new BaseTvInputSessionImpl(this, inputId);
    }

    abstract public List<ChannelInfo> createSampleChannels();

    private ChannelInfo getChannelByUri(Uri channelUri) {
        ChannelInfo info = mChannelMap.get(ContentUris.parseId(channelUri));
        if (info == null) {
            throw new IllegalArgumentException("Unknown channel: " + channelUri);
        }
        return info;
    }

    class BaseTvInputSessionImpl extends TvInputService.Session {
        private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;

        private final Context mContext;
        private final String mInputId;
        private TvInputManager mTvInputManager;
        protected TvInputPlayer mPlayer;
        private Surface mSurface;
        private float mVolume;
        private boolean mCaptionEnabled;
        private TvContentRating mLastBlockedRating;
        private ChannelInfo mChannelInfo;
        private ProgramInfo mCurrentProgramInfo;
        private TvContentRating mCurrentContentRating;
        private String mSelectedSubtitleTrackId;
        private SubtitleView  mSubtitleView;
        private final Set<TvContentRating> mUnblockedRatingSet = new HashSet<>();

        private final TvInputPlayer.Callback mPlayerCallback = new TvInputPlayer.Callback() {
            private boolean mFirstFrameDrawn;
            @Override
            public void onPrepared() {
                mFirstFrameDrawn = false;
                List<TvTrackInfo> tracks = new ArrayList<>();
                Collections.addAll(tracks, mPlayer.getTracks(TvTrackInfo.TYPE_AUDIO));
                Collections.addAll(tracks, mPlayer.getTracks(TvTrackInfo.TYPE_VIDEO));
                Collections.addAll(tracks, mPlayer.getTracks(TvTrackInfo.TYPE_SUBTITLE));

                notifyTracksChanged(tracks);
                notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, mPlayer.getSelectedTrack(
                        TvTrackInfo.TYPE_AUDIO));
                notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, mPlayer.getSelectedTrack(
                        TvTrackInfo.TYPE_VIDEO));
                notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, mPlayer.getSelectedTrack(
                        TvTrackInfo.TYPE_SUBTITLE));
            }

            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playWhenReady == true && playbackState == ExoPlayer.STATE_BUFFERING) {
                    if (mFirstFrameDrawn) {
                        notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                    }
                } else if (playWhenReady == true && playbackState == ExoPlayer.STATE_READY) {
                    notifyVideoAvailable();
                }
            }

            @Override
            public void onPlayWhenReadyCommitted() {
                // Do nothing.
            }

            @Override
            public void onPlayerError(ExoPlaybackException e) {
                // Do nothing.
            }

            @Override
            public void onDrawnToSurface(Surface surface) {
                mFirstFrameDrawn = true;
                notifyVideoAvailable();
            }

            @Override
            public void onText(String text) {
                if (mSubtitleView != null) {
                    if (TextUtils.isEmpty(text)) {
                        mSubtitleView.setVisibility(View.INVISIBLE);
                    } else {
                        mSubtitleView.setVisibility(View.VISIBLE);
                        mSubtitleView.setText(text);
                    }
                }
            }
        };

        private final Runnable mPlayCurrentProgramRunnable = new Runnable() {
            @Override
            public void run() {
                playCurrentProgram();
            }
        };

        protected BaseTvInputSessionImpl(Context context, String inputId) {
            super(context);

            mContext = context;
            mInputId = inputId;
            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            mLastBlockedRating = null;
            mCaptionEnabled = mCaptioningManager.isEnabled();
        }

        @Override
        public void onRelease() {
            mHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            releasePlayer();
            mSessions.remove(this);
        }

        @Override
        public View onCreateOverlayView() {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.overlayview, null);
            mSubtitleView = (SubtitleView) view.findViewById(R.id.subtitles);

            // Configure the subtitle view.
            CaptionStyleCompat captionStyle;
            float captionTextSize = getCaptionFontSize();
            captionStyle = CaptionStyleCompat.createFromCaptionStyle(
                    mCaptioningManager.getUserStyle());
            captionTextSize *= mCaptioningManager.getFontScale();
            mSubtitleView.setStyle(captionStyle);
            mSubtitleView.setTextSize(captionTextSize);
            return view;
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

        private Pair<ProgramInfo, Long> getCurrentProgramStatus() {
            long durationSumSec = 0;
            for (ProgramInfo program : mChannelInfo.mPrograms) {
                durationSumSec += program.mDurationSec;
            }
            long nowSec = System.currentTimeMillis() / 1000;
            long startTimeSec = nowSec - nowSec % durationSumSec;
            for (ProgramInfo program : mChannelInfo.mPrograms) {
                if (nowSec < startTimeSec + program.mDurationSec) {
                    return new Pair(program, startTimeSec + program.mDurationSec - nowSec);
                }
                startTimeSec += program.mDurationSec;
            }
            ProgramInfo first = mChannelInfo.mPrograms.get(0);
            return new Pair(first, first.mDurationSec);
        }

        private boolean changeChannel(Uri channelUri) {
            if (mSubtitleView != null) {
                mSubtitleView.setVisibility(View.INVISIBLE);
            }
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

            mUnblockedRatingSet.clear();
            mChannelInfo = getChannelByUri(channelUri);
            if (!playCurrentProgram()) {
                return false;
            }
            mDbHandler.post(new AddProgramRunnable(channelUri));
            return true;
        }

        private boolean playCurrentProgram() {
            releasePlayer();

            Pair<ProgramInfo, Long> status = getCurrentProgramStatus();
            mCurrentProgramInfo = status.first;
            long remainingTimeSec = status.second;
            mCurrentContentRating = mCurrentProgramInfo.mContentRatings.length > 0 ?
                    mCurrentProgramInfo.mContentRatings[0] : null;

            mPlayer = new TvInputPlayer();
            mPlayer.addCallback(mPlayerCallback);
            mPlayer.prepare(BaseTvInputService.this, Uri.parse(mCurrentProgramInfo.mVideoUrl),
                    mCurrentProgramInfo.mVideoType);
            mPlayer.setSurface(mSurface);
            mPlayer.setVolume(mVolume);

            if (mCurrentProgramInfo.mVideoType != TvInputPlayer.SOURCE_TYPE_HTTP_PROGRESSIVE) {
                // TODO: Seeking on http progressive source is not stable.
                //       Fix ExoPlayer/MediaExtractor and remove the condition above.
                int seekPosSec = (int) (mCurrentProgramInfo.mDurationSec - remainingTimeSec);
                mPlayer.seekTo(seekPosSec * 1000);
            }
            mPlayer.setPlayWhenReady(true);

            checkContentBlockNeeded();
            mHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            mHandler.postDelayed(mPlayCurrentProgramRunnable, (remainingTimeSec + 1) * 1000);
            return true;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            return changeChannel(channelUri);
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            mCaptionEnabled = enabled;
            if (mPlayer != null) {
                if (enabled) {
                    if (mSelectedSubtitleTrackId != null && mPlayer != null) {
                        mPlayer.selectTrack(TvTrackInfo.TYPE_SUBTITLE, mSelectedSubtitleTrackId);
                    }
                } else {
                    mPlayer.selectTrack(TvTrackInfo.TYPE_SUBTITLE, null);
                }
            }
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            if (mPlayer != null) {
                if (type == TvTrackInfo.TYPE_SUBTITLE) {
                    if (!mCaptionEnabled && trackId != null) {
                        return false;
                    }
                    mSelectedSubtitleTrackId = trackId;
                    if (trackId == null) {
                        mSubtitleView.setVisibility(View.INVISIBLE);
                    }
                }
                if (mPlayer.selectTrack(type, trackId)) {
                    notifyTrackSelected(type, trackId);
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onUnblockContent(TvContentRating rating) {
            if (rating != null) {
                unblockContent(rating);
            }
        }

        private void releasePlayer() {
            if (mPlayer != null) {
                mPlayer.removeCallback(mPlayerCallback);
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
            if (rating == null || mLastBlockedRating == null
                    || (mLastBlockedRating != null && rating.equals(mLastBlockedRating))) {
                mLastBlockedRating = null;
                if (rating != null) {
                    mUnblockedRatingSet.add(rating);
                }
                if (mPlayer == null) {
                    playCurrentProgram();
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

        private class AddProgramRunnable implements Runnable {
            private final Uri mChannelUri;

            public AddProgramRunnable(Uri channelUri) {
                mChannelUri = channelUri;
            }

            @Override
            public void run() {
                long nowSec = System.currentTimeMillis() / 1000;
                if (!Utils.hasProgramInfo(mContext.getContentResolver(), mChannelUri, nowSec,
                        nowSec + 1)) {
                    SyncUtils.requestSync(mInputId);
                }
            }
        }
    }

    public static final class ChannelInfo {
        public final String mNumber;
        public final String mName;
        public final String mLogoUrl;
        public final int mOriginalNetworkId;
        public final int mTransportStreamId;
        public final int mServiceId;
        public final int mVideoWidth;
        public final int mVideoHeight;
        public final int mAudioChannel;
        public final boolean mHasClosedCaption;
        public final List<ProgramInfo> mPrograms;

        public ChannelInfo(String number, String name, String logoUrl, int originalNetworkId,
                           int transportStreamId, int serviceId, int videoWidth, int videoHeight,
                           int audioChannel, boolean hasClosedCaption, List<ProgramInfo> programs) {
            mNumber = number;
            mName = name;
            mLogoUrl = logoUrl;
            mOriginalNetworkId = originalNetworkId;
            mTransportStreamId = transportStreamId;
            mServiceId = serviceId;
            mVideoWidth = videoWidth;
            mVideoHeight = videoHeight;
            mAudioChannel = audioChannel;
            mHasClosedCaption = hasClosedCaption;
            mPrograms = programs;
        }
    }

    public static final class ProgramInfo {
        public final String mTitle;
        public final String mPosterArtUri;
        public final String mDescription;
        public final long mDurationSec;
        public final String mVideoUrl;
        public final int mVideoType;
        public final int mResourceId;
        public final TvContentRating[] mContentRatings;

        public ProgramInfo(String title, String posterArtUri, String description, long durationSec,
                TvContentRating[] contentRatings, String url, int videoType, int resourceId) {
            mTitle = title;
            mPosterArtUri = posterArtUri;
            mDescription = description;
            mDurationSec = durationSec;
            mContentRatings = contentRatings;
            mVideoUrl = url;
            mVideoType = videoType;
            mResourceId = resourceId;
        }
    }

    public static final class TvInput {
        public final String mDisplayName;
        public final String mName;
        public final String mDescription;
        public final String mLogoThumbUrl;
        public final String mLogoBackgroundUrl;

        public TvInput(String displayName,
                       String name,
                       String description,
                       String logoThumbUrl,
                       String logoBackgroundUrl) {
            mDisplayName = displayName;
            mName = name;
            mDescription = description;
            mLogoThumbUrl = logoThumbUrl;
            mLogoBackgroundUrl = logoBackgroundUrl;
        }

        public String getLogoBackgroundUrl() {
            return mLogoBackgroundUrl;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public String getName() {
            return mName;
        }

        public String getDescription() {
            return mDescription;
        }
    }
}
