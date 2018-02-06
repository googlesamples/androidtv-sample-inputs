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

package com.google.android.media.tv.companionlibrary;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import java.util.ArrayList;
import java.util.List;

/**
 * A controller class integrated with <a
 * href="https://developers.google.com/interactive-media-ads/docs/sdks/android/">Google IMA SDK</a>,
 * which can be used to request <a
 * href="http://www.iab.com/guidelines/digital-video-ad-serving-template-vast-3-0/">VAST</a> video
 * ads, handle ad error and track the progress of ad playback.
 *
 * @hide
 */
public class AdController
        implements AdErrorEvent.AdErrorListener,
                AdsLoader.AdsLoadedListener,
                AdEvent.AdEventListener {
    private static final String TAG = "AdController";
    private static final boolean DEBUG = false;

    /** Callback interface used to delegate major ad request events. */
    public interface AdControllerCallback {
        /**
         * This is called when the advertisement request finished and advertisement video is about
         * to be played.
         *
         * @param adVideoUrl URL of advertisement video.
         * @return A {@link TvPlayer} instance constructed by {@link
         *     android.media.tv.TvInputService}
         */
        TvPlayer onAdReadyToPlay(String adVideoUrl);

        /**
         * This is called when advertisement has successfully finished its video playback. This
         * method should be implemented to resume channel content after ad playback.
         */
        void onAdCompleted();

        /**
         * This is called when advertisement error occurs. Implementing this method ensures that
         * {@link android.media.tv.TvInputService} will not get stuck in ad request forever.
         */
        void onAdError();
    }

    // Container with references to video player and ad UI ViewGroup.
    private AdDisplayContainer mAdDisplayContainer;

    // The AdsLoader instance exposes the requestAds method.
    private AdsLoader mAdsLoader;

    // AdsManager exposes methods to control ad playback and listen to ad events.
    private AdsManager mAdsManager;

    // Factory class for creating SDK objects.
    private ImaSdkFactory mSdkFactory;

    // IMA SDK requires a UI to be passed in, although TV input service does not need a UI
    // component.
    private ViewGroup mStubViewGroup;

    // Callback used to define behavior of the ad.
    private AdControllerCallback mAdControllerCallback;

    public AdController(Context context) {
        // Create an AdsLoader.
        mSdkFactory = ImaSdkFactory.getInstance();
        mAdsLoader = mSdkFactory.createAdsLoader(context);
        mAdsLoader.addAdErrorListener(this);
        mAdsLoader.addAdsLoadedListener(this);

        mStubViewGroup = new FrameLayout(context);
    }

    /**
     * Requests video ads from the given VAST ad tag.
     *
     * @param adRequestUrl URL of the ad's VAST XML.
     */
    public void requestAds(
            @NonNull String adRequestUrl, @NonNull AdControllerCallback adControllerCallback) {
        mAdControllerCallback = adControllerCallback;

        mAdDisplayContainer = mSdkFactory.createAdDisplayContainer();
        mAdDisplayContainer.setPlayer(new VideoAdPlayerImpl());
        mAdDisplayContainer.setAdContainer(mStubViewGroup);

        // Create the ads request.
        AdsRequest request = mSdkFactory.createAdsRequest();
        request.setAdTagUrl(adRequestUrl);
        request.setAdDisplayContainer(mAdDisplayContainer);

        // Request the ad. After the ad is loaded, onAdsManagerLoaded() will be called.
        mAdsLoader.requestAds(request);
    }

    /** Releases related resources. */
    public void release() {
        if (mAdsManager != null) {
            mAdsManager.destroy();
            mAdsManager = null;
        }
        if (mAdsLoader != null) {
            mAdsLoader.removeAdsLoadedListener(this);
            mAdsLoader.removeAdErrorListener(this);
        }
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        Log.e(TAG, adErrorEvent.getError().getMessage());
        mAdControllerCallback.onAdError();
        release();
    }

    /** An event raised when ads are successfully loaded from the ad server via an AdsLoader. */
    @Override
    public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
        // Ads were successfully loaded, so get the AdsManager instance. AdsManager has
        // events for ad playback and errors.
        mAdsManager = adsManagerLoadedEvent.getAdsManager();

        // Attach event and error event listeners.
        mAdsManager.addAdErrorListener(this);
        mAdsManager.addAdEventListener(this);
        mAdsManager.init();
    }

    /** Responds to AdEvents. */
    @Override
    public void onAdEvent(AdEvent adEvent) {
        if (DEBUG) {
            Log.d(TAG, "Event: " + adEvent.getType());
        }
        // These are the suggested event types to handle. For full list of all ad event types,
        // see the documentation for AdEvent.AdEventType.
        switch (adEvent.getType()) {
            case LOADED:
                // AdEventType.LOADED will be fired when ads are ready to be played.
                // AdsManager.start() begins ad playback. This method is ignored for VMAP or ad
                // rules playlists, as the SDK will automatically start executing the playlist.
                mAdsManager.start();
                break;
            case COMPLETED:
                // AdEventType.COMPLETED is fire when ad is completed, but before the fire of
                // AdEventType.CONTENT_RESUME_REQUESTED.
                mAdControllerCallback.onAdCompleted();
                release();
                break;
            default:
                break;
        }
    }

    private class VideoAdPlayerImpl extends TvPlayer.Callback implements VideoAdPlayer {
        String mAdVideoUrl;
        TvPlayer mTvPlayer;
        List<VideoAdPlayerCallback> mAdCallbacks;

        VideoAdPlayerImpl() {
            mAdCallbacks = new ArrayList<>(1);
        }

        @Override
        public void loadAd(String adVideoUrl) {
            mAdVideoUrl = adVideoUrl;
        }

        @Override
        public void playAd() {
            mTvPlayer = mAdControllerCallback.onAdReadyToPlay(mAdVideoUrl);
            mTvPlayer.registerCallback(this);
            mTvPlayer.play();
        }

        @Override
        public void stopAd() {
            // Do nothing.
        }

        @Override
        public void pauseAd() {
            // Do nothing.
        }

        @Override
        public void resumeAd() {
            // Do nothing.
        }

        @Override
        public void addCallback(VideoAdPlayer.VideoAdPlayerCallback videoAdPlayerCallback) {
            mAdCallbacks.add(videoAdPlayerCallback);
        }

        @Override
        public void removeCallback(VideoAdPlayer.VideoAdPlayerCallback videoAdPlayerCallback) {
            mAdCallbacks.remove(videoAdPlayerCallback);
        }

        @Override
        public VideoProgressUpdate getAdProgress() {
            if (mTvPlayer == null) {
                return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
            }
            return new VideoProgressUpdate(mTvPlayer.getCurrentPosition(), mTvPlayer.getDuration());
        }

        @Override
        public void onStarted() {
            for (VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onPlay();
            }
        }

        @Override
        public void onCompleted() {
            for (VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onEnded();
            }
        }

        @Override
        public void onError(Exception error) {
            for (VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onError();
            }
        }

        @Override
        public void onPaused() {
            for (VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onPause();
            }
        }

        @Override
        public void onResumed() {
            for (VideoAdPlayerCallback callback : mAdCallbacks) {
                callback.onResume();
            }
        }
    }
}
