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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.TrackInfo;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.Pair;
import android.view.Surface;
import android.view.accessibility.CaptioningManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

abstract public class BaseTvInputService extends TvInputService {
    private static final String TAG = "BaseTvInputService";
    private static final boolean DEBUG = true;

    private final LongSparseArray<ChannelInfo> mChannelMap = new LongSparseArray<ChannelInfo>();
    private HandlerThread mHandlerThread;
    private Handler mDbHandler;
    private Handler mHandler;

    protected List<ChannelInfo> mChannels;
    private List<BaseTvInputSessionImpl> mSessions;

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

        buildChannelMap();
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
        mSessions.add(session);
        return session;
    }

    /**
     * Child classes should extend this to change the result of onCreateSession.
     */
    public BaseTvInputSessionImpl onCreateSessionInternal(String inputId) {
        return new BaseTvInputSessionImpl(this);
    }

    abstract public List<ChannelInfo> createSampleChannels();

    private synchronized void buildChannelMap() {
        Uri uri = TvContract.buildChannelsUriForInput(Utils.getInputIdFromComponentName(this,
                new ComponentName(this, this.getClass())));
        String[] projection = {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER
        };
        mChannels = createSampleChannels();
        if (mChannels == null || mChannels.isEmpty()) {
            return;
        }

        try {
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            if (cursor == null || cursor.getCount() == 0) {
                return;
            }

            while (cursor.moveToNext()) {
                long channelId = cursor.getLong(0);
                String channelNumber = cursor.getString(1);
                mChannelMap.put(channelId, getChannelByNumber(channelNumber, false));
            }
        } catch (Exception e) {
            Log.d(TAG, "Content provider query: " + e.getStackTrace());
        }
    }

    private ChannelInfo getChannelByNumber(String channelNumber, boolean isRetry) {
        for (ChannelInfo info : mChannels) {
            if (info.mNumber.equals(channelNumber)) {
                return info;
            }
        }
        if (!isRetry) {
            buildChannelMap();
            return getChannelByNumber(channelNumber, true);
        }
        throw new IllegalArgumentException("Unknown channel: " + channelNumber);
    }

    private ChannelInfo getChannelByUri(Uri channelUri, boolean isRetry) {
        ChannelInfo info = mChannelMap.get(ContentUris.parseId(channelUri));
        if (info == null) {
            if (!isRetry) {
                buildChannelMap();
                return getChannelByUri(channelUri, true);
            }
            throw new IllegalArgumentException("Unknown channel: " + channelUri);
        }
        return info;
    }

    class BaseTvInputSessionImpl extends TvInputService.Session {
        private TvInputManager mTvInputManager;
        protected MediaPlayer mPlayer;
        private boolean mNotifiedVideoAvailable;
        private Surface mSurface;
        private float mVolume;
        private Map<String, TvTrackInfo> mTracks;
        private boolean mCaptionEnabled;
        private TvContentRating mLastBlockedRating;
        private String mSelectedAudioTrack;
        private String mSelectedVideoTrack;
        private String mSelectedSubtitleTrack;
        private ChannelInfo mChannelInfo;
        private ProgramInfo mCurrentProgramInfo;
        private TvContentRating mCurrentContentRating;
        private final Set<TvContentRating> mUnblockedRatingSet = new HashSet<>();

        private final Runnable mPlayCurrentProgramRunnable = new Runnable() {
            @Override
            public void run() {
                playCurrentProgram();
            }
        };

        protected BaseTvInputSessionImpl(Context context) {
            super(context);

            mTvInputManager = (TvInputManager) context.getSystemService(Context.TV_INPUT_SERVICE);
            mVolume = 1.0f;
            mLastBlockedRating = null;

            CaptioningManager captionManager = (CaptioningManager) getSystemService(
                    CAPTIONING_SERVICE);
            mCaptionEnabled = captionManager.isEnabled();
        }

        @Override
        public void onRelease() {
            releasePlayerInBackground();
            mSessions.remove(this);
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
        public void notifyVideoUnavailable(int reason) {
            super.notifyVideoUnavailable(reason);
            mNotifiedVideoAvailable = false;
        }

        @Override
        public void notifyVideoAvailable() {
            super.notifyVideoAvailable();
            mNotifiedVideoAvailable = true;
        }

        private boolean setDataSource(MediaPlayer player, ProgramInfo program) {
            try {
                if (program.mUrl != null) {
                    player.setDataSource(program.mUrl);
                } else {
                    AssetFileDescriptor afd = getResources().openRawResourceFd(program.mResourceId);
                    if (afd == null) {
                        return false;
                    }
                    player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                            afd.getDeclaredLength());
                    afd.close();
                }
            } catch (IllegalArgumentException e) {
                // Do nothing.
            } catch (IllegalStateException e) {
                // Do nothing.
            } catch (IOException e) {
                // Do nothing.
            }
            return true;
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
            notifyVideoUnavailable(TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING);

            mUnblockedRatingSet.clear();
            mChannelInfo = getChannelByUri(channelUri, false);
            if (!playCurrentProgram()) {
                return false;
            }
            mDbHandler.post(new AddProgramRunnable(channelUri, mChannelInfo));
            return true;
        }

        private boolean playCurrentProgram() {
            Pair<ProgramInfo, Long> status = getCurrentProgramStatus();
            mCurrentProgramInfo = status.first;
            mCurrentContentRating = mCurrentProgramInfo.mContentRatings.length > 0 ?
                    mCurrentProgramInfo.mContentRatings[0] : null;
            if (!startPlayback(true)) {
                return false;
            }
            checkContentBlockNeeded();
            mHandler.removeCallbacks(mPlayCurrentProgramRunnable);
            mHandler.postDelayed(mPlayCurrentProgramRunnable, (status.second + 1) * 1000);
            return true;
        }

        private boolean startPlayback(final boolean fromChannelChange) {
            final MediaPlayer oldPlayer = mPlayer;
            if (oldPlayer != null) {
                oldPlayer.setOnInfoListener(null);
                oldPlayer.setOnPreparedListener(null);
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        // It sometimes takes around few seconds.
                        oldPlayer.setSurface(null);
                        // After setSurface(null), we can do setSurface(mSurface) for the new
                        // media player.
                        publishProgress();
                        oldPlayer.release();
                        return null;
                    }

                    @Override
                    protected void onProgressUpdate(Void... values) {
                        if (mPlayer != null) {
                            mPlayer.setSurface(mSurface);
                        }
                    }
                }.execute();
            }
            mPlayer = new MediaPlayer();
            mPlayer.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer player, int what, int arg) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        notifyVideoUnavailable(
                                TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING);
                        return true;
                    } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        notifyVideoAvailable();
                        return true;
                    } else if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START
                            && (fromChannelChange || !mNotifiedVideoAvailable)) {
                        notifyVideoAvailable();
                        return true;
                    }
                    return false;
                }
            });
            if (!setDataSource(mPlayer, mCurrentProgramInfo)) {
                return false;
            }
            if (oldPlayer == null) {
                mPlayer.setSurface(mSurface);
            }
            mPlayer.setVolume(mVolume, mVolume);
            mPlayer.setLooping(true);
            try {
                mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer player) {
                        if (mPlayer != null && !mPlayer.isPlaying() && mLastBlockedRating == null) {
                            int duration = mPlayer.getDuration();
                            if (duration > 0) {
                                int seekPosition = (int) (System.currentTimeMillis() % duration);
                                mPlayer.seekTo(seekPosition);
                            }
                            try {
                                MediaPlayer.TrackInfo[] tracks = mPlayer.getTrackInfo();
                                setupTrackInfo(tracks, mChannelInfo);
                            } catch (RuntimeException e) {
                                restartPlayer();
                                return;
                            }
                            try {
                                mPlayer.start();
                            } catch (IllegalStateException e) {
                            }
                        }
                    }
                });
                mPlayer.prepareAsync();
            } catch (IllegalStateException e1) {
                return false;
            }
            return true;
        }

        @Override
        public boolean onTune(Uri channelUri) {
            return changeChannel(channelUri);
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
            mCaptionEnabled = enabled;
            if (mSelectedSubtitleTrack != null) {
                if (enabled) {
                    try {
                        mPlayer.selectTrack(Integer.parseInt(mSelectedSubtitleTrack));
                    } catch (RuntimeException e) {
                        // Invalid track for test
                    }
                } else {
                    try {
                        mPlayer.deselectTrack(Integer.parseInt(mSelectedSubtitleTrack));
                    } catch (RuntimeException e) {
                        // Invalid track for test
                    }
                }
            }
        }

        @Override
        public boolean onSelectTrack(int type, String trackId) {
            if (mPlayer != null) {
                if (type == TvTrackInfo.TYPE_SUBTITLE) {
                    // SelectTrack only works on subtitle tracks.
                    if (mCaptionEnabled) {
                        if (trackId == null) {
                            if (mSelectedSubtitleTrack != null) {
                                try {
                                    mPlayer.deselectTrack(Integer.parseInt(mSelectedSubtitleTrack));
                                } catch (RuntimeException e) {
                                    // Invalid track for test
                                }
                            }
                        } else {
                            TvTrackInfo track = mTracks.get(trackId);
                            if (track == null) {
                                return false;
                            }
                            try {
                                mPlayer.selectTrack(Integer.parseInt(trackId));
                            } catch (RuntimeException e) {
                                // Invalid track for test
                            }
                        }
                    }
                    mSelectedSubtitleTrack = trackId;
                    notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, trackId);
                    return true;
                } else if (type == TvTrackInfo.TYPE_AUDIO) {
                    mSelectedAudioTrack = trackId;
                    notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, trackId);
                } else if (type == TvTrackInfo.TYPE_VIDEO) {
                    mSelectedVideoTrack = trackId;
                    notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, trackId);
                }
            }
            return false;
        }

        private void setupTrackInfo(MediaPlayer.TrackInfo[] infos, ChannelInfo channel) {
            if (channel.mVideoHeight == 0 && channel.mVideoWidth == 0
                    && channel.mAudioChannel == 0) {
                // This case represents a TV input which does not provide track metadata.
                return;
            }
            Map<String, TvTrackInfo> tracks = new HashMap<String, TvTrackInfo>();
            // Add subtitle tracks from the real media.
            int i;
            for (i = 0; i < infos.length; ++i) {
                if (infos[i].getTrackType() == TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
                        || infos[i].getTrackType() == TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                    tracks.put(Integer.toString(i), new TvTrackInfo.Builder(
                            TvTrackInfo.TYPE_SUBTITLE, Integer.toString(i))
                            .setLanguage("und".equals(infos[i].getLanguage()) ? null
                                    : infos[i].getLanguage())
                            .build());
                }
            }
            // Add predefine video and audio track.
            mSelectedVideoTrack = Integer.toString(i++);
            tracks.put(mSelectedVideoTrack, new TvTrackInfo.Builder(
                    TvTrackInfo.TYPE_VIDEO, mSelectedVideoTrack)
                    .setVideoWidth(channel.mVideoWidth)
                    .setVideoHeight(channel.mVideoHeight)
                    .build());
            mSelectedAudioTrack = Integer.toString(i++);
            tracks.put(mSelectedAudioTrack, new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO,
                    mSelectedAudioTrack)
                    .setLanguage(Locale.ENGLISH.getLanguage())
                    .setAudioChannelCount(channel.mAudioChannel)
                    .build());
            String trackId = Integer.toString(i++);
            tracks.put(trackId, new TvTrackInfo.Builder(TvTrackInfo.TYPE_AUDIO, trackId)
                    .setLanguage(Locale.KOREAN.getLanguage())
                    .setAudioChannelCount(channel.mAudioChannel)
                    .build());
            trackId = Integer.toString(i++);
            tracks.put(trackId, new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE,
                    trackId).setLanguage(Locale.ENGLISH.getLanguage()).build());
            mSelectedSubtitleTrack = Integer.toString(i++);
            tracks.put(mSelectedSubtitleTrack, new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE,
                    mSelectedSubtitleTrack).setLanguage(Locale.KOREAN.getLanguage()).build());
            trackId = Integer.toString(i++);
            tracks.put(trackId, new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE,
                    trackId).setLanguage(Locale.JAPANESE.getLanguage()).build());

            mTracks = tracks;
            notifyTracksChanged(new ArrayList<TvTrackInfo>(mTracks.values()));
            notifyTrackSelected(TvTrackInfo.TYPE_VIDEO, mSelectedVideoTrack);
            notifyTrackSelected(TvTrackInfo.TYPE_AUDIO, mSelectedAudioTrack);
            notifyTrackSelected(TvTrackInfo.TYPE_SUBTITLE, mSelectedSubtitleTrack);
        }

        protected TvTrackInfo getTrack(int type, String trackId) {
            if (mTracks == null || trackId == null) {
                return null;
            }
            TvTrackInfo track = mTracks.get(trackId);
            if (track != null && track.getType() == type) {
                return track;
            }
            return null;
        }

        protected String getSelectedVideoTrackId() {
            return mSelectedVideoTrack;
        }

        protected String getSelectedAudioTrackId() {
            return mSelectedAudioTrack;
        }

        protected String getSelectedSubtitleTrackId() {
            return mSelectedSubtitleTrack;
        }

        @Override
        public void onUnblockContent(TvContentRating rating) {
            if (rating != null) {
                unblockContent(rating);
            }
        }

        protected void onContentRatingChanged(TvContentRating rating) {
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
                mPlayer.reset();
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
                if (mPlayer != null && !mPlayer.isPlaying()) {
                    startPlayback(false);
                }
                notifyContentAllowed();
            }
        }

        private void restartPlayer() {
            releasePlayerInBackground();
            startPlayback(false);
        }

        private void releasePlayerInBackground() {
            if (mPlayer != null) {
                new AsyncTask<MediaPlayer, Void, Void>() {
                    @Override
                    protected Void doInBackground(MediaPlayer... player) {
                        // It sometimes takes around few seconds.
                        player[0].release();
                        return null;
                    }
                }.execute(mPlayer);
                mPlayer = null;
            }
        }

        private class AddProgramRunnable implements Runnable {
            private static final int PROGRAM_REPEAT_COUNT = 24;
            private final Uri mChannelUri;
            private final ChannelInfo mChannelInfo;

            public AddProgramRunnable(Uri channelUri, ChannelInfo channel) {
                mChannelUri = channelUri;
                mChannelInfo = channel;
            }

            @Override
            public void run() {
                long durationSumSec = 0;
                List<ContentValues> programs = new ArrayList<>();
                for (ProgramInfo program : mChannelInfo.mPrograms) {
                    durationSumSec += program.mDurationSec;

                    ContentValues values = new ContentValues();
                    values.put(Programs.COLUMN_CHANNEL_ID, ContentUris.parseId(mChannelUri));
                    values.put(Programs.COLUMN_TITLE, program.mTitle);
                    values.put(Programs.COLUMN_SHORT_DESCRIPTION, program.mDescription);
                    values.put(Programs.COLUMN_CONTENT_RATING,
                            Utils.contentRatingsToString(program.mContentRatings));
                    if (!TextUtils.isEmpty(program.mPosterArtUri)) {
                        values.put(Programs.COLUMN_POSTER_ART_URI, program.mPosterArtUri);
                    }
                    programs.add(values);
                }

                long nowSec = System.currentTimeMillis() / 1000;
                long epgStartTimeSec = nowSec - nowSec % durationSumSec;
                for (int i = 0; i < PROGRAM_REPEAT_COUNT; ++i) {
                    long startSec = epgStartTimeSec + i * durationSumSec;
                    if (!hasProgramInfo(startSec * 1000 + 1, (startSec + durationSumSec) * 1000 )) {
                        long programStartSec = startSec;
                        for (int j = 0; j < mChannelInfo.mPrograms.size(); ++j) {
                            ProgramInfo program = mChannelInfo.mPrograms.get(j);
                            ContentValues values = programs.get(j);
                            values.put(Programs.COLUMN_START_TIME_UTC_MILLIS,
                                    programStartSec * 1000);
                            values.put(Programs.COLUMN_END_TIME_UTC_MILLIS,
                                    (programStartSec + program.mDurationSec) * 1000);
                            getContentResolver().insert(TvContract.Programs.CONTENT_URI, values);
                            programStartSec = programStartSec + program.mDurationSec;
                        }
                    }
                }
            }

            private boolean hasProgramInfo(long startTimeMs, long endTimeMs) {
                Uri uri = TvContract.buildProgramsUriForChannel(mChannelUri, startTimeMs,
                        endTimeMs);
                String[] projection = {TvContract.Programs._ID};
                try {
                    Cursor cursor =
                            getContentResolver().query(uri, projection, null, null, null);
                    if (cursor.getCount() > 0) {
                        return true;
                    }
                } catch (Exception e) {

                }
                return false;
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
        public final String mUrl;
        public final int mResourceId;
        public final TvContentRating[] mContentRatings;

        public ProgramInfo(String title, String posterArtUri, String description, long durationSec,
                TvContentRating[] contentRatings, String url, int resourceId) {
            mTitle = title;
            mPosterArtUri = posterArtUri;
            mDescription = description;
            mDurationSec = durationSec;
            mContentRatings = contentRatings;
            mUrl = url;
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
