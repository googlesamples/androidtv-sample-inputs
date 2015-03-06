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

package com.example.android.sampletvinput.syncadapter;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SyncResult;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;

import com.example.android.sampletvinput.rich.RichFeedUtil;
import com.example.android.sampletvinput.rich.RichTvInputService.ChannelInfo;
import com.example.android.sampletvinput.rich.RichTvInputService.ProgramInfo;
import com.example.android.sampletvinput.TvContractUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A SyncAdapter implementation which updates program info periodically.
 */
class SyncAdapter extends AbstractThreadedSyncAdapter {
    public static final String TAG = "SyncAdapter";

    public static final String BUNDLE_KEY_INPUT_ID = "bundle_key_input_id";
    public static final long SYNC_FREQUENCY_SEC = 60 * 60 * 6;  // 6 hours
    private static final int SYNC_WINDOW_SEC = 60 * 60 * 12;  // 12 hours
    private static final int BATCH_OPERATION_COUNT = 100;

    private final Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
    }

    public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
        super(context, autoInitialize, allowParallelSyncs);
        mContext = context;
    }

    /**
     * Called periodically by the system in every {@code SYNC_FREQUENCY_SEC}.
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        Log.d(TAG, "onPerformSync(" + account + ", " + authority + ", " + extras + ")");
        String inputId = extras.getString(SyncAdapter.BUNDLE_KEY_INPUT_ID);
        if (inputId == null) {
            return;
        }
        List<ChannelInfo> channels = RichFeedUtil.getRichChannels(mContext);
        LongSparseArray<ChannelInfo> channelMap = TvContractUtils.buildChannelMap(
                mContext.getContentResolver(), inputId, channels);
        for (int i = 0; i < channelMap.size(); ++i) {
            Uri channelUri = TvContract.buildChannelUri(channelMap.keyAt(i));
            insertPrograms(channelUri, channelMap.valueAt(i));
        }
    }

    /**
     * Inserts programs from now to {@link SyncAdapter#SYNC_WINDOW_SEC}.
     *
     * @param channelUri The channel where the program info will be added.
     * @param channelInfo {@link ChannelInfo} instance which includes program information.
     */
    private void insertPrograms(Uri channelUri, ChannelInfo channelInfo) {
        long durationSumSec = 0;
        List<ContentValues> programs = new ArrayList<>();
        for (ProgramInfo program : channelInfo.programs) {
            durationSumSec += program.durationSec;

            ContentValues values = new ContentValues();
            values.put(TvContract.Programs.COLUMN_CHANNEL_ID, ContentUris.parseId(channelUri));
            values.put(TvContract.Programs.COLUMN_TITLE, program.title);
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, program.description);
            values.put(TvContract.Programs.COLUMN_CONTENT_RATING,
                    TvContractUtils.contentRatingsToString(program.contentRatings));
            if (!TextUtils.isEmpty(program.posterArtUri)) {
                values.put(TvContract.Programs.COLUMN_POSTER_ART_URI, program.posterArtUri);
            }
            // NOTE: {@code COLUMN_INTERNAL_PROVIDER_DATA} is a private field where TvInputService
            // can store anything it wants. Here, we store video type and video URL so that
            // TvInputService can play the video later with this field.
            values.put(TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA,
                    TvContractUtils.convertVideoInfoToInternalProviderData(program.videoType,
                            program.videoUrl));
            programs.add(values);
        }

        long nowSec = System.currentTimeMillis() / 1000;
        long insertionEndSec = nowSec + SYNC_WINDOW_SEC;
        long lastProgramEndTimeSec = TvContractUtils.getLastProgramEndTimeMillis(
                mContext.getContentResolver(), channelUri) / 1000;
        if (nowSec < lastProgramEndTimeSec) {
            nowSec = lastProgramEndTimeSec;
        }
        long insertionStartTimeSec = nowSec - nowSec % durationSumSec;
        long nextPos = insertionStartTimeSec;
        for (int i = 0; nextPos < insertionEndSec; ++i) {
            long programStartSec = nextPos;
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            int programsCount = channelInfo.programs.size();
            for (int j = 0; j < programsCount; ++j) {
                ProgramInfo program = channelInfo.programs.get(j);
                ops.add(ContentProviderOperation.newInsert(
                        TvContract.Programs.CONTENT_URI)
                        .withValues(programs.get(j))
                        .withValue(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                                programStartSec * 1000)
                        .withValue(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
                                (programStartSec + program.durationSec) * 1000)
                        .build());
                programStartSec = programStartSec + program.durationSec;

                // Throttle the batch operation not to face TransactionTooLargeException.
                if (j % BATCH_OPERATION_COUNT == BATCH_OPERATION_COUNT - 1
                        || j == programsCount - 1) {
                    try {
                        mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                    } catch (RemoteException | OperationApplicationException e) {
                        Log.e(TAG, "Failed to insert programs.", e);
                        return;
                    }
                    ops.clear();
                }
            }
            nextPos = insertionStartTimeSec + i * durationSumSec;
        }
    }
}
