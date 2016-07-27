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
package com.google.android.media.tv.companionlibrary.model;

import android.content.ContentValues;
import android.database.MatrixCursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.google.android.media.tv.companionlibrary.BuildConfig;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tests that programs can be created using the Builder pattern and correctly obtain
 * values from them
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 23, manifest = "src/main/AndroidManifest.xml")
public class ProgramTest extends TestCase {
    @Test
    public void testEmptyProgram() {
        // Tests creating an empty program and handling the error because it's missing required
        // attributes.
        try {
            Program emptyProgram = new Program.Builder()
                    .build();
            ContentValues contentValues = emptyProgram.toContentValues();
            compareProgram(emptyProgram, Program.fromCursor(getProgramCursor(contentValues)));

            fail("A program should not be allowed to exist with undefined start and end times.");
        } catch (IllegalArgumentException ignored) {
            // Exception correctly handled
        }
    }

    @Test
    public void testSampleProgram() {
        // Tests cloning and database I/O of a program with some defined and some undefined
        // values.
        Program sampleProgram = new Program.Builder()
                .setTitle("Program Title")
                .setDescription("This is a sample program")
                .setChannelId(3)
                .setEpisodeNumber(5)
                .setSeasonNumber("The Final Season", 7)
                .setThumbnailUri("http://www.example.com/programs/poster.png")
                .setStartTimeUtcMillis(0)
                .setEndTimeUtcMillis(1000)
                .build();
        ContentValues contentValues = sampleProgram.toContentValues();
        compareProgram(sampleProgram, Program.fromCursor(getProgramCursor(contentValues)));

        Program clonedSampleProgram = new Program.Builder(sampleProgram).build();
        compareProgram(sampleProgram, clonedSampleProgram);
    }

    @RequiresApi(api = Build.VERSION_CODES.M) @Test
    public void testFullyPopulatedProgram() {
        // Tests cloning and database I/O of a program with every value being defined.
        InternalProviderData internalProviderData = new InternalProviderData();
        internalProviderData.setVideoType(TvContractUtils.SOURCE_TYPE_HLS);
        internalProviderData.setVideoUrl("http://example.com/stream.m3u8");
        Program fullyPopulatedProgram = new Program.Builder()
                .setSearchable(false)
                .setChannelId(3)
                .setThumbnailUri("http://example.com/thumbnail.png")
                .setInternalProviderData(internalProviderData)
                .setAudioLanguages("en-us")
                .setBroadcastGenres(new String[] {"Music", "Family"})
                .setCanonicalGenres(new String[] {TvContract.Programs.Genres.MOVIES})
                .setContentRatings(new TvContentRating[] {TvContentRating.UNRATED})
                .setDescription("This is a sample program")
                .setEndTimeUtcMillis(1000)
                .setEpisodeNumber("Pilot", 0)
                .setEpisodeTitle("Hello World")
                .setLongDescription("This is a longer description than the previous description")
                .setPosterArtUri("http://example.com/poster.png")
                .setRecordingProhibited(false)
                .setSeasonNumber("The Final Season", 7)
                .setSeasonTitle("The Final Season")
                .setStartTimeUtcMillis(0)
                .setTitle("Google")
                .setVideoHeight(1080)
                .setVideoWidth(1920)
                .build();

        ContentValues contentValues = fullyPopulatedProgram.toContentValues();
        compareProgram(fullyPopulatedProgram, Program.fromCursor(getProgramCursor(contentValues)));

        Program clonedFullyPopulatedProgram = new Program.Builder(fullyPopulatedProgram).build();
        compareProgram(fullyPopulatedProgram, clonedFullyPopulatedProgram);
    }

    private static void compareProgram(Program programA, Program programB) {
        assertTrue(Objects.equals(programA.getAudioLanguages(), programB.getAudioLanguages()));
        assertTrue(Arrays.deepEquals(programA.getBroadcastGenres(), programB.getBroadcastGenres()));
        assertTrue(Arrays.deepEquals(programA.getCanonicalGenres(), programB.getCanonicalGenres()));
        assertEquals(programA.getChannelId(), programB.getChannelId());
        assertTrue(Arrays.deepEquals(programA.getContentRatings(), programB.getContentRatings()));
        assertEquals(programA.getDescription(), programB.getDescription());
        assertEquals(programA.getEndTimeUtcMillis(), programB.getEndTimeUtcMillis());
        assertEquals(programA.getEpisodeNumber(), programB.getEpisodeNumber());
        assertEquals(programA.getEpisodeTitle(), programB.getEpisodeTitle());
        assertEquals(programA.getInternalProviderData(), programB.getInternalProviderData());
        assertEquals(programA.getLongDescription(), programB.getLongDescription());
        assertEquals(programA.getPosterArtUri(), programB.getPosterArtUri());
        assertEquals(programA.getId(), programB.getId());
        assertEquals(programA.getSeasonNumber(), programB.getSeasonNumber());
        if(android.support.v7.appcompat.BuildConfig.VERSION_CODE >= Build.VERSION_CODES.N) {
            assertTrue(Objects.equals(programA.getSeasonTitle(), programB.getSeasonTitle()));
            assertTrue(Objects.equals(programA.isRecordingProhibited(),
                    programB.isRecordingProhibited()));
        }
        assertEquals(programA.getStartTimeUtcMillis(), programB.getStartTimeUtcMillis());
        assertEquals(programA.getThumbnailUri(), programB.getThumbnailUri());
        assertEquals(programA.getTitle(), programB.getTitle());
        assertEquals(programA.getVideoHeight(), programB.getVideoHeight());
        assertEquals(programA.getVideoWidth(), programB.getVideoWidth());
        assertEquals(programA.isSearchable(), programB.isSearchable());
        assertEquals(programA.toContentValues(), programB.toContentValues());
        assertEquals(programA.toString(), programB.toString());
        assertEquals(programA, programB);
    }

    private static MatrixCursor getProgramCursor(ContentValues contentValues) {
        String[] rows = new String[] {
                TvContract.Programs._ID,
                TvContract.Programs.COLUMN_BROADCAST_GENRE,
                TvContract.Programs.COLUMN_PACKAGE_NAME,
                TvContract.Programs.COLUMN_SEASON_TITLE,
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_AUDIO_LANGUAGE,
                TvContract.Programs.COLUMN_CANONICAL_GENRE,
                TvContract.Programs.COLUMN_CHANNEL_ID,
                TvContract.Programs.COLUMN_CONTENT_RATING,
                TvContract.Programs.COLUMN_SEASON_TITLE,
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER,
                TvContract.Programs.COLUMN_EPISODE_TITLE,
                TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA,
                TvContract.Programs.COLUMN_LONG_DESCRIPTION,
                TvContract.Programs.COLUMN_POSTER_ART_URI,
                TvContract.Programs.COLUMN_RECORDING_PROHIBITED,
                TvContract.Programs.COLUMN_SEARCHABLE,
                TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER,
                TvContract.Programs.COLUMN_SEASON_TITLE,
                TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
                TvContract.Programs.COLUMN_THUMBNAIL_URI,
                TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_VERSION_NUMBER,
                TvContract.Programs.COLUMN_VIDEO_HEIGHT,
                TvContract.Programs.COLUMN_VIDEO_WIDTH,
                TvContract.Programs.COLUMN_EPISODE_NUMBER,
                TvContract.Programs.COLUMN_SEASON_NUMBER,
        };
        MatrixCursor cursor = new MatrixCursor(rows);
        MatrixCursor.RowBuilder builder = cursor.newRow();
        for(String row: rows) {
            builder.add(row, contentValues.get(row));
        }
        cursor.moveToFirst();
        return cursor;
    }
}
