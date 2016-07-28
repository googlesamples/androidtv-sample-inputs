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
import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.player.DemoPlayer;
import com.example.android.sampletvinput.player.RendererBuilderFactory;
import com.example.android.sampletvinput.service.BaseTvInputService;
import com.example.android.sampletvinput.sync.EpgSyncJobService;
import com.example.android.sampletvinput.sync.SampleJobService;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleLayout;

import java.util.ArrayList;
import java.util.List;

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
            DemoPlayer.Listener, DemoPlayer.CaptionListener {
        private static final float CAPTION_LINE_HEIGHT_RATIO = 0.0533f;
        private static final int TEXT_UNIT_PIXELS = 0;
        private static final long INVALID_VIDEO_POSITION = -1;

        private int mSelectedSubtitleTrackIndex;
        private SubtitleLayout mSubtitleView;
        private DemoPlayer mPlayer;
        private boolean mCaptionEnabled;
        private Surface mSurface;
        private float mVolume;
        private String mInputId;
        private Context mContext;

        RichTvInputSessionImpl(Context context, String inputId) {
            super(context, inputId);
            mCaptionEnabled = mCaptioningManager.isEnabled();
            mContext = context;
            mInputId = inputId;
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

        @Override
        public boolean onPlayProgram(Program program, long startPosMs) {
            if (program == null) {
                requestEpgSync(getCurrentChannelUri());
                notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
                return false;
            }
            createPlayer(program.getInternalProviderData().getVideoType(),
                    Uri.parse(program.getInternalProviderData().getVideoUrl()));
            if (startPosMs > 0) {
                mPlayer.seekTo(startPosMs);
            }
            mPlayer.setPlayWhenReady(true);
            return true;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            if (DEBUG) {
                Log.d(TAG, "Tune to " + channelUri.toString());
            }
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);
            onReleasePlayer();
            return super.onTune(channelUri);
        }

        @Override
        public long getCurrentPos() {
            if (mPlayer != null) {
                return mPlayer.getCurrentPosition();
            }
            return INVALID_VIDEO_POSITION;
        }

        @Override
        public AdController.VideoPlayer onCreateAdPlayer(int videoType, Uri videoUrl) {
            createPlayer(videoType, videoUrl);
            return new AdVideoPlayerProxy(mPlayer);
        }

        private void createPlayer(int videoType, Uri videoUrl) {
            mPlayer = new DemoPlayer(RendererBuilderFactory.createRendererBuilder(
                    mContext, videoType, videoUrl));
            mPlayer.addListener(this);
            mPlayer.setCaptionListener(this);
            mPlayer.prepare();
            mPlayer.setSurface(mSurface);
            mPlayer.setVolume(mVolume);
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
    }
}