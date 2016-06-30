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
package com.example.android.sampletvinputlib;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Intent;
import android.database.MatrixCursor;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.os.Build;
import android.support.v7.appcompat.BuildConfig;

import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.utils.TvContractUtils;

import junit.framework.TestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Objects;

/**
 * Tests that channels and programs can be created using the Builder pattern and correctly obtain
 * values from them
 */
@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 23, manifest = "src/main/AndroidManifest.xml")
public class ProgrammaticGenerationTest extends TestCase {
    @Test
    public void testChannelCreation() {
        Channel emptyChannel = new Channel.Builder().build();
        ContentValues contentValues = emptyChannel.toContentValues();
        compareChannel(emptyChannel, Channel.fromCursor(getChannelCursor(contentValues)));

        Channel sampleChannel = new Channel.Builder()
                .setDisplayName("Google")
                .setDisplayNumber("3")
                .setDescription("This is a sample channel")
                .setAppLinkIntentUri(new Intent().toUri(Intent.URI_INTENT_SCHEME))
                .build();
        contentValues = sampleChannel.toContentValues();
        compareChannel(sampleChannel, Channel.fromCursor(getChannelCursor(contentValues)));

        Channel clonedSampleChannel = new Channel.Builder(sampleChannel).build();
        compareChannel(sampleChannel, clonedSampleChannel);

        Channel fullyPopulatedChannel = new Channel.Builder()
                .setAppLinkColor(RuntimeEnvironment.application.getResources().getColor(android.R.color.holo_orange_light))
                .setAppLinkIconUri("http://example.com/icon.png")
                .setAppLinkIntent(new Intent())
                .setAppLinkPosterArtUri("http://example.com/poster.png")
                .setAppLinkText("Open an intent")
                .setDescription("Channel description")
                .setDisplayName("Display Name")
                .setDisplayNumber("100")
                .setId(1)
                .setInputId("TestInputService")
                .setNetworkAffiliation("Network Affiliation")
                .setOriginalNetworkId(2)
                .setPackageName("com.example.android.sampletvinput")
                .setSearchable(false)
                .setServiceId(3)
                .setTransportStreamId(4)
                .setType(TvContract.Channels.TYPE_1SEG)
                .setServiceType(TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO)
                .setVideoFormat(TvContract.Channels.VIDEO_FORMAT_240P)
                .build();
        contentValues = fullyPopulatedChannel.toContentValues();
        compareChannel(fullyPopulatedChannel, Channel.fromCursor(getChannelCursor(contentValues)));

        Channel clonedFullyPopulatedChannel = new Channel.Builder(fullyPopulatedChannel).build();
        compareChannel(fullyPopulatedChannel, clonedFullyPopulatedChannel);
    }

    private static void compareChannel(Channel channelA, Channel channelB) {
        assertEquals(channelA.getAppLinkColor(), channelB.getAppLinkColor());
        assertEquals(channelA.getAppLinkIconUri(), channelB.getAppLinkIconUri());
        assertEquals(channelA.getAppLinkIntentUri(), channelB.getAppLinkIntentUri());
        assertEquals(channelA.getAppLinkPosterArtUri(), channelB.getAppLinkPosterArtUri());
        assertEquals(channelA.getAppLinkText(), channelB.getAppLinkText());
        assertEquals(channelA.isSearchable(), channelB.isSearchable());
        assertEquals(channelA.getDescription(), channelB.getDescription());
        assertEquals(channelA.getDisplayName(), channelB.getDisplayName());
        assertEquals(channelA.getDisplayNumber(), channelB.getDisplayNumber());
        assertEquals(channelA.getId(), channelB.getId());
        assertEquals(channelA.getInputId(), channelB.getInputId());
        assertEquals(channelA.getNetworkAffiliation(), channelB.getNetworkAffiliation());
        assertEquals(channelA.getOriginalNetworkId(), channelB.getOriginalNetworkId());
        assertEquals(channelA.getPackageName(), channelB.getPackageName());
        assertEquals(channelA.getServiceId(), channelB.getServiceId());
        assertEquals(channelA.getServiceType(), channelB.getServiceType());
        assertEquals(channelA.getTransportStreamId(), channelB.getTransportStreamId());
        assertEquals(channelA.getType(), channelB.getType());
        assertEquals(channelA.getVideoFormat(), channelB.getVideoFormat());
        assertEquals(channelA.toContentValues(), channelB.toContentValues());
        assertEquals(channelA.toString(), channelB.toString());
    }

    private static MatrixCursor getChannelCursor(ContentValues contentValues) {
        String[] rows = new String[] {
                TvContract.Channels._ID,
                TvContract.Channels.COLUMN_APP_LINK_COLOR,
                TvContract.Channels.COLUMN_APP_LINK_ICON_URI,
                TvContract.Channels.COLUMN_APP_LINK_INTENT_URI,
                TvContract.Channels.COLUMN_APP_LINK_POSTER_ART_URI,
                TvContract.Channels.COLUMN_APP_LINK_TEXT,
                TvContract.Channels.COLUMN_DESCRIPTION,
                TvContract.Channels.COLUMN_DISPLAY_NAME,
                TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_INPUT_ID,
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
                TvContract.Channels.COLUMN_NETWORK_AFFILIATION,
                TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID,
                TvContract.Channels.COLUMN_PACKAGE_NAME,
                TvContract.Channels.COLUMN_SEARCHABLE,
                TvContract.Channels.COLUMN_SERVICE_ID,
                TvContract.Channels.COLUMN_SERVICE_TYPE,
                TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID,
                TvContract.Channels.COLUMN_TYPE,
                TvContract.Channels.COLUMN_VERSION_NUMBER,
                TvContract.Channels.COLUMN_VIDEO_FORMAT
        };
        MatrixCursor cursor = new MatrixCursor(rows);
        MatrixCursor.RowBuilder builder = cursor.newRow();
        for(String row: rows) {
            builder.add(row, contentValues.get(row));
        }
        cursor.moveToFirst();
        return cursor;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Test
    public void testProgramCreation() {
        Program emptyProgram = new Program.Builder().build();
        ContentValues contentValues = emptyProgram.toContentValues();
        compareProgram(emptyProgram, Program.fromCursor(getProgramCursor(contentValues)));

        Program sampleProgram = new Program.Builder()
                .setTitle("Program Title")
                .setDescription("This is a sample program")
                .setChannelId(3)
                .setEpisodeNumber(5)
                .setSeasonNumber("The Final Season", 7)
                .setThumbnailUri("http://www.example.com/programs/poster.png")
                .build();
        contentValues = sampleProgram.toContentValues();
        compareProgram(sampleProgram, Program.fromCursor(getProgramCursor(contentValues)));

        Program clonedSampleProgram = new Program.Builder(sampleProgram).build();
        compareProgram(sampleProgram, clonedSampleProgram);

        Program fullyPopulatedProgram = new Program.Builder()
                .setSearchable(false)
                .setChannelId(3)
                .setThumbnailUri("http://example.com/thumbnail.png")
                .setInternalProviderData(TvContractUtils.convertVideoInfoToInternalProviderData(TvContractUtils.SOURCE_TYPE_HLS, "http://example.com/stream.m3u8"))
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
                .setProgramId(10)
                .setRecordingProhibited(false)
                .setSeasonNumber("The Final Season", 7)
                .setSeasonTitle("The Final Season")
                .setStartTimeUtcMillis(0)
                .setTitle("Google")
                .setVideoHeight(1080)
                .setVideoWidth(1920)
                .build();

        contentValues = fullyPopulatedProgram.toContentValues();
        compareProgram(fullyPopulatedProgram, Program.fromCursor(getProgramCursor(contentValues)));

        Program clonedFullyPopulatedProgram = new Program.Builder(fullyPopulatedProgram).build();
        compareProgram(fullyPopulatedProgram, clonedFullyPopulatedProgram);
    }

    private static void compareProgram(Program programA, Program programB) {
        assertTrue(Arrays.deepEquals(programA.getAudioLanguages(), programB.getAudioLanguages()));
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
        assertEquals(programA.getProgramId(), programB.getProgramId());
        assertEquals(programA.getSeasonNumber(), programB.getSeasonNumber());
        if(BuildConfig.VERSION_CODE >= Build.VERSION_CODES.N) {
            assertTrue(Objects.equals(programA.getSeasonTitle(), programB.getSeasonTitle()));
            assertTrue(Objects.equals(programA.isRecordingProhibited(), programB.isRecordingProhibited()));
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