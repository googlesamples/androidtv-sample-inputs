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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.player.DemoPlayer;
import com.example.android.sampletvinput.player.RendererBuilderFactory;
import com.example.android.sampletvinput.service.BaseTvInputService;
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
import java.util.concurrent.TimeUnit;

/**
 * TvInputService which provides a full implementation of EPG, subtitles, multi-audio, parental
 * controls, and overlay view.
 */
public class RichTvInputService extends BaseTvInputService {
    private static final String TAG = "RichTvInputService";
    private static final boolean DEBUG = false;
    private static final long SYNC_REQUESTED_PERIOD_MS = 1000 * 60 * 60; // 1 Hour
    private static final long EPG_SYNC_DELAYED_PERIOD_MS = 1000 * 2; // 2 Seconds

    private CaptioningManager mCaptioningManager;

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
        setTheme(android.R.style.Theme_Holo_Light_NoActionBar);
        mCaptioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
    }

    @Override
    public final Session onCreateSession(String inputId) {
        RichTvInputSessionImpl session = new RichTvInputSessionImpl(this, inputId);
        session.setOverlayViewEnabled(true);
        return super.sessionCreated(session);
    }

    class RichTvInputSessionImpl extends BaseTvInputService.Session implements
            DemoPlayer.Listener, DemoPlayer.CaptionListener, Handler.Callback {
        private static final int MSG_PLAY_AD = 2000;
        private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;
        private static final int TEXT_UNIT_PIXELS = 0;
        private final long MIN_AD_INTERVAL_ON_TUNE_MS = TimeUnit.MINUTES.toMillis(5);

        private int mSelectedSubtitleTrackIndex;
        private SubtitleLayout mSubtitleView;
        private AdController mAdController;
        private DemoPlayer mPlayer;
        private long mLastNewChannelAdWatchedTimeMs;
        private boolean mCaptionEnabled;
        private int mContentType;
        private Uri mContentUri;
        private Surface mSurface;
        private float mVolume;
        private String mInputId;
        private Context mContext;
        private Handler mHandler;
        private boolean mPlayingAd;
        private Channel mCurrentChannel;
        private Program mCurrentProgram;

        RichTvInputSessionImpl(Context context, String inputId) {
            super(context, inputId);
            mCaptionEnabled = mCaptioningManager.isEnabled();
            mContext = context;
            mInputId = inputId;
            mHandler = new Handler(this);
        }

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_PLAY_AD:
                    return insertAd((Advertisement) msg.obj);
            }
            return super.handleMessage(msg);
        }

        @Override
        public void onRelease() {
            releaseAdController();
            onReleasePlayer();
            super.onRelease();
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
            onReleasePlayer();
            if (onPlayProgram(info)) {
                if (startPosMs > 0) {
                    mPlayer.seekTo(startPosMs);
                }
            }
            return true;
        }

        @Override
        public boolean onPlayProgram(Program info) {
            mCurrentProgram = info;
            if (mPlayingAd) {
                // Don't play program on top of advertisement.
                return false;
            }
            if (info == null) {
                requestEpgSync(mContentUri);
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
                return false;
            }
            mContentType = info.getInternalProviderData().getSourceType();
            mContentUri = Uri.parse(info.getInternalProviderData().getSourceUrl());
            onReleasePlayer();
            createPlayer();
            mPlayer.setPlayWhenReady(true);
            return true;
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
            onReleasePlayer();
            if (mAdController != null) {
                mAdController.release();
            }
            return super.onTune(channelUri);
        }

        @Override
        public void onPlayChannel(Channel channel) {
            List<Advertisement> ads = InternalProviderDataUtil.parseAds(
                    channel.getInternalProviderData());
            if (! ads.isEmpty() && System.currentTimeMillis() - mLastNewChannelAdWatchedTimeMs >
                    MIN_AD_INTERVAL_ON_TUNE_MS) {
                mHandler.removeMessages(MSG_PLAY_AD);
                // There is at most one advertisement in the channel.
                mHandler.obtainMessage(MSG_PLAY_AD, ads.get(0)).sendToTarget();
                mPlayingAd = true;
            } else {
                mPlayingAd = false;
            }
        }

        private boolean insertAd(Advertisement ad) {
            releaseAdController();
            if (DEBUG) {
                Log.d(TAG, "Insert an ad");
            }
            if (mAdController != null) {
                mAdController.release();
            }
            mAdController = new AdController(mContext);
            mAdController.requestAds(ad.getRequestUrl(), new AdControllerCallbackImpl());
            notifyVideoAvailable();
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
                    if (! mCaptionEnabled) {
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

        private void releaseAdController() {
            if (mAdController != null) {
                mAdController.release();
            }
        }

        public void onReleasePlayer() {
            if (mPlayer != null) {
                mPlayer.removeListener(this);
                mPlayer.setSurface(null);
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
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
        public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                float pixelWidthHeightRatio) {
            // Do nothing.
        }

        @Override
        public void onError(Exception e) {
            Log.e(TAG, e.getMessage());
        }

        @Override
        public void onCues(List<Cue> cues) {
            mSubtitleView.setCues(cues);
        }

        public void requestEpgSync(final Uri channelUri) {
            EpgSyncJobService.requestSync(RichTvInputService.this, mInputId,
                    SYNC_REQUESTED_PERIOD_MS,
                    new ComponentName(RichTvInputService.this, SampleJobService.class));
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    onTune(channelUri);
                }
            }, EPG_SYNC_DELAYED_PERIOD_MS);
        }

        private final class AdControllerCallbackImpl implements AdController.AdControllerCallback {
            private static final long INVALID_POSITION = - 1;

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
                onReleasePlayer();
                mContentType = TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE;
                mContentUri = Uri.parse(adVideoUrl);
                createPlayer();
                return new AdVideoPlayerProxy(mPlayer);
            }

            @Override
            public void onAdCompleted() {
                mPlayingAd = false;
                if (mContentPosMs != INVALID_POSITION) {
                    // Resume channel content playback.
                    playProgram(mCurrentProgram, mContentPosMs);
                } else {
                    // No video content was played before ad insertion. Start querying database to
                    // get channel program information.
                    playProgram(mCurrentProgram);
                    mLastNewChannelAdWatchedTimeMs = System.currentTimeMillis();
                }
            }

            @Override
            public void onAdError() {
                Log.e(TAG, "An error occurred playing ads");
                playProgram(mCurrentProgram);
            }
        }
    }
}