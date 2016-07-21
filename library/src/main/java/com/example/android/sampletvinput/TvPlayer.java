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

package com.example.android.sampletvinput;

import android.media.PlaybackParams;
import android.view.Surface;

/**
 * The TvPlayer contains common media controller methods that a player can use to interface with
 * the system TV app to handle user input events.
 */
public interface TvPlayer {
    /**
     * Sets the current position for the current media.
     *
     * @param position The current time in milliseconds to play the media.
     */
    void seekTo(long position);

    /**
     * Sets the playback params for the current media.
     *
     * @param params The new playback params.
     */
    void setPlaybackParams(PlaybackParams params);

    /**
     * @return The current time in milliseconds of the media.
     */
    long getCurrentPosition();

    /**
     * Sets the surface for the current media.
     *
     * @param surface The surface to play media on
     */
    void setSurface(Surface surface);

    /**
     * Sets the volume for the current media.
     *
     * @param volume The volume between 0 and 1 to play the media at.
     */
    void setVolume(float volume);

    /**
     * Pause the current media.
     */
    void pause();

    /**
     * Start playing or resume the current media.
     */
    void play();
}