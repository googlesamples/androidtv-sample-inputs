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

package com.example.android.sampletvinput.player;

import android.content.Context;
import android.net.Uri;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.util.Util;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;

/**
 * Factory used by {@link DemoPlayer} to create a new {@link DemoPlayer.RendererBuilder}.
 */
public class RendererBuilderFactory {
    /**
     * Create new instance of {@link DemoPlayer.RendererBuilder}.
     *
     * @param context The {@link Context} to use.
     * @param contentType The type of the video content: {@link Util#TYPE_SS},
     * {@link Util#TYPE_DASH}, {@link Util#TYPE_HLS} or {@link Util#TYPE_OTHER}.
     * @param contentUri The URI of the video content.
     * @return A {@link DemoPlayer.RendererBuilder} instance.
     */
    public static DemoPlayer.RendererBuilder createRendererBuilder(
            Context context, int contentType, Uri contentUri) {
        String userAgent = Util.getUserAgent(context, "ExoVideoPlayer");

        switch (contentType) {
            case TvContractUtils.SOURCE_TYPE_MPEG_DASH: {
                // Implement your own DRM callback here.
                MediaDrmCallback drmCallback = new WidevineTestMediaDrmCallback(null, null);
                return new DashRendererBuilder(context, userAgent, contentUri.toString(),
                        drmCallback);
            }
            case TvContractUtils.SOURCE_TYPE_SS: {
                // Implement your own DRM callback here.
                MediaDrmCallback drmCallback = new SmoothStreamingTestMediaDrmCallback();
                return new SmoothStreamingRendererBuilder(context, userAgent,
                        contentUri.toString(), drmCallback);
            }
            case TvContractUtils.SOURCE_TYPE_HLS: {
                return new HlsRendererBuilder(context, userAgent, contentUri.toString());
            }
            case TvContractUtils.SOURCE_TYPE_HTTP_PROGRESSIVE: {
                return new ExtractorRendererBuilder(context, userAgent, contentUri);
            }
            default: {
                throw new IllegalStateException("Unsupported type: " + contentType);
            }
        }
    }
}
