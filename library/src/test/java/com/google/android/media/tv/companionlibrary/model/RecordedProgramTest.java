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
package com.google.android.media.tv.companionlibrary.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.database.MatrixCursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.google.android.media.tv.companionlibrary.BuildConfig;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 23,
    manifest = "src/main/AndroidManifest.xml")
public class RecordedProgramTest {
    private static final String TEST_INPUT_ID = "com.example.android.sampletvinput/.Test";

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Test
    public void testEmptyRecording() {
        // Tests creating an empty recorded program and handling the error because it's missing
        // required attributes.
        try {
            RecordedProgram emptyProgram = new RecordedProgram.Builder()
                    .build();
            ContentValues contentValues = emptyProgram.toContentValues();
            compareRecordedProgram(emptyProgram, RecordedProgram.fromCursor(getRecordedProgramCursor(contentValues)));
            fail("A recorded program should not be allowed to exist with an undefined input id.");
        } catch (IllegalArgumentException ignored) {
            // Exception correctly handled
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Test
    public void testProgramConversion() {
        // Tests that a RecordedProgram can be converted to a Program and back to a RecordedProgram
        RecordedProgram sampleRecordedProgram = new RecordedProgram.Builder()
                .setAudioLanguages("en-us")
                .setBroadcastGenres(new String[]{"Sports"})
                .setCanonicalGenres(new String[]{TvContract.Programs.Genres.ANIMAL_WILDLIFE})
                .setContentRatings(new TvContentRating[]{TvContentRating.UNRATED})
                .setEpisodeTitle("Sample Episode")
                .setInputId(TEST_INPUT_ID)
                .setTitle("Sample Title")
                .setSearchable(false)
                .setStartTimeUtcMillis(0)
                .setEndTimeUtcMillis(1000)
                .setSeasonTitle("First Season")
                .setSeasonDisplayNumber("1", 1)
                .setVideoHeight(1080)
                .setVideoWidth(1920)
                .build();

        Program sampleProgram = sampleRecordedProgram.toProgram();
        RecordedProgram clonedSampleRecordedProgram = new RecordedProgram.Builder(sampleProgram)
                .setInputId(TEST_INPUT_ID)
                .build();
        compareRecordedProgram(sampleRecordedProgram, clonedSampleRecordedProgram);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Test
    public void testFullyPopulatedRecording() {
        // Tests that every attribute in a RecordedProgram can be set and persist through database
        // I/O operations
        RecordedProgram fullyPopulatedProgram = new RecordedProgram.Builder()
                .setAudioLanguages("en-us")
                .setBroadcastGenres(new String[]{"Sports"})
                .setCanonicalGenres(new String[]{TvContract.Programs.Genres.ANIMAL_WILDLIFE})
                .setContentRatings(new TvContentRating[]{TvContentRating.UNRATED})
                .setEpisodeDisplayNumber("24", 24)
                .setEpisodeTitle("Sample Episode")
                .setInputId(TEST_INPUT_ID)
                .setTitle("Sample Title")
                .setSearchable(false)
                .setStartTimeUtcMillis(0)
                .setEndTimeUtcMillis(1000)
                .setInternalProviderData(new InternalProviderData())
                .setRecordingDataBytes(1024 * 1024)
                .setRecordingDataUri("file://sdcard/TV.apk")
                .setRecordingDurationMillis(1000)
                .setSeasonTitle("First Season")
                .setSeasonDisplayNumber("1", 1)
                .setVideoHeight(1080)
                .setVideoWidth(1920)
                .build();

        ContentValues contentValues = fullyPopulatedProgram.toContentValues();
        compareRecordedProgram(fullyPopulatedProgram,
                RecordedProgram.fromCursor(getRecordedProgramCursor(contentValues)));

        RecordedProgram clonedFullyPopulatedProgram =
                new RecordedProgram.Builder(fullyPopulatedProgram).build();
        compareRecordedProgram(fullyPopulatedProgram, clonedFullyPopulatedProgram);
    }

    private static void compareRecordedProgram(RecordedProgram programA, RecordedProgram programB) {
        assertEquals(programA.getAudioLanguages(), programB.getAudioLanguages());
        assertTrue(Arrays.deepEquals(programA.getBroadcastGenres(), programB.getBroadcastGenres()));
        assertTrue(Arrays.deepEquals(programA.getCanonicalGenres(), programB.getCanonicalGenres()));
        assertTrue(Arrays.deepEquals(programA.getContentRatings(), programB.getContentRatings()));
        assertEquals(programA.getEpisodeTitle(), programB.getEpisodeTitle());
        assertEquals(programA.getId(), programB.getId());
        assertEquals(programA.getInputId(), programB.getInputId());
        assertEquals(programA.getTitle(), programB.getTitle());
        assertEquals(programA.getStartTimeUtcMillis(), programB.getStartTimeUtcMillis());
        assertEquals(programA.getEndTimeUtcMillis(), programB.getEndTimeUtcMillis());
        assertEquals(programA.getRecordingDataBytes(), programB.getRecordingDataBytes());
        assertEquals(programA.getRecordingDataUri(), programB.getRecordingDataUri());
        assertEquals(programA.getRecordingDurationMillis(), programB.getRecordingDurationMillis());
        assertEquals(programA.getSeasonTitle(), programB.getSeasonTitle());
        assertEquals(programA.getShortDescription(), programB.getShortDescription());
        assertEquals(programA.getLongDescription(), programB.getLongDescription());
        assertEquals(programA.getVideoHeight(), programB.getVideoHeight());
        assertEquals(programA.getVideoWidth(), programB.getVideoWidth());
        assertEquals(programA.isSearchable(), programB.isSearchable());
        assertEquals(programA.toContentValues(), programB.toContentValues());
        assertEquals(programA.toString(), programB.toString());
        assertEquals(programA, programB);
    }

    private static MatrixCursor getRecordedProgramCursor(ContentValues contentValues) {
        String[] rows = RecordedProgram.PROJECTION;
        MatrixCursor cursor = new MatrixCursor(rows);
        MatrixCursor.RowBuilder builder = cursor.newRow();
        for (String row : rows) {
            builder.add(row, contentValues.get(row));
        }
        cursor.moveToFirst();
        return cursor;
    }
}
