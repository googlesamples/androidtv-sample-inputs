package com.example.android.sampletvinput.player;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;

import com.google.android.exoplayer.DummyTrackRenderer;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.ExoPlayerLibraryInfo;
import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.text.TextRenderer;
import com.google.android.exoplayer.text.eia608.Eia608TrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.UriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A wrapper around {@link ExoPlayer} that provides a higher level interface. Designed for
 * integration with {@link android.media.tv.TvInputService}.
 */
public class TvInputPlayer implements TextRenderer {
    private static final String TAG = "TvInputPlayer";

    public static final int SOURCE_TYPE_HTTP_PROGRESSIVE = 0;
    public static final int SOURCE_TYPE_HLS = 1;
    public static final int SOURCE_TYPE_MPEG_DASH = 2;
    public static final int SOURCE_TYPE_SMOOTH_STREAMING = 3;

    public static final int STATE_IDLE = ExoPlayer.STATE_IDLE;
    public static final int STATE_PREPARING = ExoPlayer.STATE_PREPARING;
    public static final int STATE_BUFFERING = ExoPlayer.STATE_BUFFERING;
    public static final int STATE_READY = ExoPlayer.STATE_READY;
    public static final int STATE_ENDED = ExoPlayer.STATE_ENDED;

    private static final int RENDERER_COUNT = 3;
    private static final int MIN_BUFFER_MS = 1000;
    private static final int MIN_REBUFFER_MS = 5000;

    private final Handler mHandler;
    private final ExoPlayer mPlayer;
    private MediaCodecVideoTrackRenderer mVideoRenderer;
    private MediaCodecAudioTrackRenderer mAudioRenderer;
    private TrackRenderer mTextRenderer;
    private final CopyOnWriteArrayList<Callback> mCallbacks;
    private float mVolume;
    private Surface mSurface;
    private TvTrackInfo[][] mTvTracks = new TvTrackInfo[3][];

    public TvInputPlayer() {
        mHandler = new Handler();
        mTvTracks[TvTrackInfo.TYPE_AUDIO] = new TvTrackInfo[0];
        mTvTracks[TvTrackInfo.TYPE_VIDEO] = new TvTrackInfo[0];
        mTvTracks[TvTrackInfo.TYPE_SUBTITLE] = new TvTrackInfo[0];
        mCallbacks = new CopyOnWriteArrayList<>();
        mPlayer = ExoPlayer.Factory.newInstance(RENDERER_COUNT, MIN_BUFFER_MS, MIN_REBUFFER_MS);
        mPlayer.addListener(new ExoPlayer.Listener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                for(Callback callback : mCallbacks) {
                    callback.onPlayerStateChanged(playWhenReady, playbackState);
                }
            }

            @Override
            public void onPlayWhenReadyCommitted() {
                for(Callback callback : mCallbacks) {
                    callback.onPlayWhenReadyCommitted();
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException e) {
                for(Callback callback : mCallbacks) {
                    callback.onPlayerError(e);
                }
            }
        });
    }

    @Override
    public void onText(String text) {
        for (Callback callback : mCallbacks) {
            callback.onText(text);
        }
    }

    public void prepare(Context context, final Uri uri, int sourceType) {
        if (sourceType == SOURCE_TYPE_HTTP_PROGRESSIVE) {
            FrameworkSampleSource sampleSource = new FrameworkSampleSource(context, uri, null, 2);
            mAudioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);
            mVideoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
                    MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, null, null, 50);
            mTextRenderer = new DummyTrackRenderer();
            prepareInternal();
        } else if (sourceType == SOURCE_TYPE_HLS) {
            final String userAgent = getUserAgent(context);
            HlsPlaylistParser parser = new HlsPlaylistParser();
            ManifestFetcher<HlsPlaylist> playlistFetcher =
                    new ManifestFetcher<>(parser, uri.toString(), uri.toString(), userAgent);
            playlistFetcher.singleLoad(mHandler.getLooper(),
                    new ManifestFetcher.ManifestCallback<HlsPlaylist>() {
                        @Override
                        public void onManifest(String contentId, HlsPlaylist manifest) {
                            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
                            DataSource dataSource = new UriDataSource(userAgent, bandwidthMeter);
                            HlsChunkSource chunkSource = new HlsChunkSource(dataSource,
                                    uri.toString(), manifest, bandwidthMeter, null,
                                    HlsChunkSource.ADAPTIVE_MODE_SPLICE);
                            HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, true,
                                    2);
                            mAudioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);
                            mVideoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
                                    MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 0, mHandler, null,
                                    50);
                            mTextRenderer = new Eia608TrackRenderer(sampleSource,
                                    TvInputPlayer.this, mHandler.getLooper());
                            // TODO: Implement custom HLS source to get the internal track metadata.
                            mTvTracks[TvTrackInfo.TYPE_SUBTITLE] = new TvTrackInfo[1];
                            mTvTracks[TvTrackInfo.TYPE_SUBTITLE][0] =
                                    new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "text1")
                                        .setLanguage("eng")
                                        .build();
                            prepareInternal();
                        }

                        @Override
                        public void onManifestError(String contentId, IOException e) {
                            for (Callback callback : mCallbacks) {
                                callback.onPlayerError(new ExoPlaybackException(e));
                            }
                        }
                    });
        } else {
            // TODO: handle DASH and SS.
        }
    }

    public TvTrackInfo[] getTracks(int trackType) {
        if (trackType < 0 || trackType >= mTvTracks.length) {
            throw new IllegalArgumentException("Illegal track type: " + trackType);
        }
        return mTvTracks[trackType];
    }

    public boolean selectTrack(int trackType, String trackId) {
        if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            // TODO: Handle the case for multiple tracks.
            mPlayer.setRendererEnabled(trackType, trackId != null);
            return true;
        }
        return false;
    }

    public void setPlayWhenReady(boolean playWhenReady) {
        mPlayer.setPlayWhenReady(playWhenReady);
    }

    public void setVolume(float volume) {
        mVolume = volume;
        if (mPlayer != null && mAudioRenderer != null) {
            mPlayer.sendMessage(mAudioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME,
                    volume);
        }
    }

    public void setSurface(Surface surface) {
        mSurface = surface;
        if (mPlayer != null && mVideoRenderer != null) {
            mPlayer.sendMessage(mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                    surface);
        }
    }

    public void seekTo(long position) {
        mPlayer.seekTo(position);
    }

    public void stop() {
        mPlayer.stop();
    }

    public void release() {
        mPlayer.release();
    }

    public void addCallback(Callback callback) {
        mCallbacks.add(callback);
    }

    public void removeCallback(Callback callback) {
        mCallbacks.remove(callback);
    }

    private void prepareInternal() {
        mPlayer.prepare(mAudioRenderer, mVideoRenderer, mTextRenderer);
        mPlayer.sendMessage(mAudioRenderer, MediaCodecAudioTrackRenderer.MSG_SET_VOLUME,
                mVolume);
        mPlayer.sendMessage(mVideoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE,
                mSurface);
        // Disable text track by default.
        mPlayer.setRendererEnabled(TvTrackInfo.TYPE_SUBTITLE, false);
        for (Callback callback : mCallbacks) {
            callback.onPrepared();
        }
    }

    public static String getUserAgent(Context context) {
        String versionName;
        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }
        return "SampleTvInput/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE +
                ") " + "ExoPlayerLib/" + ExoPlayerLibraryInfo.VERSION;
    }

    public interface Callback {
        void onPrepared();
        void onPlayerStateChanged(boolean playWhenReady, int state);
        void onPlayWhenReadyCommitted();
        void onPlayerError(ExoPlaybackException e);
        void onText(String text);
    }
}