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

package com.example.android.sampletvinput.syncservice;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.LongSparseArray;
import android.util.Log;
import android.util.SparseArray;

import com.example.android.sampletvinput.TvContractUtils;
import com.example.android.sampletvinput.data.Program;
import com.example.android.sampletvinput.rich.RichFeedUtil;
import com.example.android.sampletvinput.xmltv.XmlTvParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to handle callbacks from JobSchduler.
 */
public class SyncJobService extends JobService {
    private static final String TAG = "SyncJobService";
    private static final boolean DEBUG = false;

    public static final String ACTION_SYNC_STATUS_CHANGED = "action_sync_status_changed";
    public static final String BUNDLE_KEY_INPUT_ID = "bundle_key_input_id";
    public static final String BUNDLE_KEY_CURRENT_PROGRAM_ONLY = "bundle_key_current_program_only";
    public static final String PREFERENCE_EPG_SYNC = "preference_epg_sync";
    public static final String SYNC_FINISHED = "sync_finished";
    public static final String SYNC_STARTED = "sync_started";
    public static final String SYNC_STATUS = "sync_status";
    public static final long FULL_SYNC_FREQUENCY_MILLIS = 60 * 60 * 24 * 1000;  // daily
    public static final long OVERRIDE_DEADLINE_MILLIS = 1000;  // 1 second
    private static final int FULL_SYNC_WINDOW_SEC = 60 * 60 * 24 * 14;  // 2 weeks
    private static final int SHORT_SYNC_WINDOW_SEC = 60 * 60;  // 1 hour
    private static final int BATCH_OPERATION_COUNT = 100;

    private final SparseArray<EpgSyncTask> mTaskArray = new SparseArray<>();

    private static final Object mContextLock = new Object();
    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (mContextLock) {
            if (mContext == null) {
                mContext = getApplicationContext();
            }
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (DEBUG) {
            Log.d(TAG, "onStartJob(" + params.getJobId() + ")");
        }
        EpgSyncTask epgSyncTask = new EpgSyncTask(params);
        synchronized (mTaskArray) {
            mTaskArray.put(params.getJobId(), epgSyncTask);
        }
        epgSyncTask.execute();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        synchronized (mTaskArray) {
            int jobId = params.getJobId();
            EpgSyncTask epgSyncTask = mTaskArray.get(jobId);
            if (epgSyncTask != null) {
                epgSyncTask.cancel(true);
                mTaskArray.delete(params.getJobId());
            }
        }
        return false;
    }

    private class EpgSyncTask extends AsyncTask<Void, Void, Void> {
        private final JobParameters params;

        private EpgSyncTask(JobParameters params) {
            this.params = params;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (isCancelled()) {
                return null;
            }

            PersistableBundle extras = params.getExtras();
            String inputId = extras.getString(SyncJobService.BUNDLE_KEY_INPUT_ID);
            if (inputId == null) {
                return null;
            }
            XmlTvParser.TvListing listings = RichFeedUtil.getRichTvListings(mContext);
            LongSparseArray<XmlTvParser.XmlTvChannel> channelMap = TvContractUtils.buildChannelMap(
                    mContext.getContentResolver(), inputId, listings.channels);
            if (channelMap == null) {
                return null;
            }
            boolean currentProgramOnly = extras.getBoolean(
                    SyncJobService.BUNDLE_KEY_CURRENT_PROGRAM_ONLY, false);
            long startMs = System.currentTimeMillis();
            long endMs = startMs + FULL_SYNC_WINDOW_SEC * 1000;
            if (currentProgramOnly) {
                // This is requested from the setup activity, in this case, users don't need to wait
                // for the full sync. Sync the current programs first and do the full sync later in
                // the background.
                endMs = startMs + SHORT_SYNC_WINDOW_SEC * 1000;
            }
            for (int i = 0; i < channelMap.size(); ++i) {
                Uri channelUri = TvContract.buildChannelUri(channelMap.keyAt(i));
                if (isCancelled()) {
                    return null;
                }
                List<Program> programs = getPrograms(channelUri, channelMap.valueAt(i),
                        listings.programs, startMs, endMs);
                // Double check if the job is cancelled, so that this task can be finished faster
                // after cancel() is called.
                if (isCancelled()) {
                    return null;
                }
                updatePrograms(channelUri, programs);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void success) {
            finishEpgSync(params);
        }

        @Override
        protected void onCancelled(Void ignore) {
            finishEpgSync(params);
        }

        private void finishEpgSync(JobParameters jobParams) {
            if (DEBUG) {
                Log.d(TAG, "taskFinished(" + jobParams.getJobId() + ")");
            }
            mTaskArray.delete(jobParams.getJobId());
            jobFinished(jobParams, false);
            if (jobParams.getJobId() == SyncUtils.REQUEST_SYNC_JOB_ID) {
                Intent intent = new Intent(ACTION_SYNC_STATUS_CHANGED);
                intent.putExtra(
                        BUNDLE_KEY_INPUT_ID, jobParams.getExtras().getString(BUNDLE_KEY_INPUT_ID));
                intent.putExtra(SYNC_STATUS, SYNC_FINISHED);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            }
        }

        /**
         * Returns a list of programs for the given time range.
         *
         * @param channelUri The channel where the program info will be added.
         * @param channel The {@link XmlTvParser.XmlTvChannel} for the programs to return.
         * @param programs The feed fetched from cloud.
         * @param startTimeMs The start time of the range requested.
         * @param endTimeMs The end time of the range requested.
         */
        private List<Program> getPrograms(Uri channelUri, XmlTvParser.XmlTvChannel channel,
                List<XmlTvParser.XmlTvProgram> programs, long startTimeMs, long endTimeMs) {
            if (startTimeMs > endTimeMs) {
                throw new IllegalArgumentException();
            }
            List<XmlTvParser.XmlTvProgram> channelPrograms = new ArrayList<>();
            for (XmlTvParser.XmlTvProgram program : programs) {
                if (program.channelId.equals(channel.id)) {
                    channelPrograms.add(program);
                }
            }

            List<Program> programForGivenTime = new ArrayList<>();
            if (!channel.repeatPrograms) {
                for (XmlTvParser.XmlTvProgram program : channelPrograms) {
                    if (program.startTimeUtcMillis <= endTimeMs
                            && program.endTimeUtcMillis >= startTimeMs) {
                        programForGivenTime.add(new Program.Builder()
                                        .setChannelId(ContentUris.parseId(channelUri))
                                        .setTitle(program.title)
                                        .setDescription(program.description)
                                        .setContentRatings(XmlTvParser.xmlTvRatingToTvContentRating(
                                                program.rating))
                                        .setCanonicalGenres(program.category)
                                        .setPosterArtUri(program.icon.src)
                                        .setInternalProviderData(TvContractUtils.
                                                convertVideoInfoToInternalProviderData(
                                                        program.videoType, program.videoSrc))
                                        .setStartTimeUtcMillis(program.startTimeUtcMillis)
                                        .setEndTimeUtcMillis(program.endTimeUtcMillis)
                                        .build()
                        );
                    }
                }
                return programForGivenTime;
            }

            // If repeat-programs is on, schedule the programs sequentially in a loop. To make every
            // device play the same program in a given channel and time, we assumes the loop started
            // from the epoch time.
            long totalDurationMs = 0;
            for (XmlTvParser.XmlTvProgram program : channelPrograms) {
                totalDurationMs += program.getDurationMillis();
            }

            long programStartTimeMs = startTimeMs - startTimeMs % totalDurationMs;
            int i = 0;
            final int programCount = channelPrograms.size();
            while (programStartTimeMs < endTimeMs) {
                XmlTvParser.XmlTvProgram programInfo = channelPrograms.get(i++ % programCount);
                long programEndTimeMs = programStartTimeMs + programInfo.getDurationMillis();
                if (programEndTimeMs < startTimeMs) {
                    programStartTimeMs = programEndTimeMs;
                    continue;
                }
                programForGivenTime.add(new Program.Builder()
                                .setChannelId(ContentUris.parseId(channelUri))
                                .setTitle(programInfo.title)
                                .setDescription(programInfo.description)
                                .setContentRatings(XmlTvParser.xmlTvRatingToTvContentRating(
                                        programInfo.rating))
                                .setCanonicalGenres(programInfo.category)
                                .setPosterArtUri(programInfo.icon.src)
                                // NOTE: {@code COLUMN_INTERNAL_PROVIDER_DATA} is a private field
                                // where TvInputService can store anything it wants. Here, we store
                                // video type and video URL so that TvInputService can play the
                                // video later with this field.
                                .setInternalProviderData(
                                        TvContractUtils.convertVideoInfoToInternalProviderData(
                                                programInfo.videoType, programInfo.videoSrc))
                                .setStartTimeUtcMillis(programStartTimeMs)
                                .setEndTimeUtcMillis(programEndTimeMs)
                                .build()
                );
                programStartTimeMs = programEndTimeMs;
            }
            return programForGivenTime;
        }

        /**
         * Updates the system database, TvProvider, with the given programs.
         *
         * <p>If there is any overlap between the given and existing programs, the existing ones
         * will be updated with the given ones if they have the same title or replaced.
         *
         * @param channelUri The channel where the program info will be added.
         * @param newPrograms A list of {@link Program} instances which includes program
         *         information.
         */
        private void updatePrograms(Uri channelUri, List<Program> newPrograms) {
            final int fetchedProgramsCount = newPrograms.size();
            if (fetchedProgramsCount == 0) {
                return;
            }
            List<Program> oldPrograms = TvContractUtils.getPrograms(mContext.getContentResolver(),
                    channelUri);
            Program firstNewProgram = newPrograms.get(0);
            int oldProgramsIndex = 0;
            int newProgramsIndex = 0;
            // Skip the past programs. They will be automatically removed by the system.
            for (Program program : oldPrograms) {
                oldProgramsIndex++;
                if (program.getEndTimeUtcMillis() > firstNewProgram.getStartTimeUtcMillis()) {
                    break;
                }
            }
            // Compare the new programs with old programs one by one and update/delete the old one
            // or insert new program if there is no matching program in the database.
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            if (isCancelled()) {
                return;
            }
            while (newProgramsIndex < fetchedProgramsCount) {
                Program oldProgram = oldProgramsIndex < oldPrograms.size()
                        ? oldPrograms.get(oldProgramsIndex) : null;
                Program newProgram = newPrograms.get(newProgramsIndex);
                boolean addNewProgram = false;
                if (oldProgram != null) {
                    if (oldProgram.equals(newProgram)) {
                        // Exact match. No need to update. Move on to the next programs.
                        oldProgramsIndex++;
                        newProgramsIndex++;
                    } else if (needsUpdate(oldProgram, newProgram)) {
                        // Partial match. Update the old program with the new one.
                        // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                        // could be application specific settings which belong to the old program.
                        ops.add(ContentProviderOperation.newUpdate(
                                TvContract.buildProgramUri(oldProgram.getProgramId()))
                                .withValues(newProgram.toContentValues())
                                .build());
                        oldProgramsIndex++;
                        newProgramsIndex++;
                    } else if (oldProgram.getEndTimeUtcMillis()
                            < newProgram.getEndTimeUtcMillis()) {
                        // No match. Remove the old program first to see if the next program in
                        // {@code oldPrograms} partially matches the new program.
                        ops.add(ContentProviderOperation.newDelete(
                                TvContract.buildProgramUri(oldProgram.getProgramId()))
                                .build());
                        oldProgramsIndex++;
                    } else {
                        // No match. The new program does not match any of the old programs. Insert
                        // it as a new program.
                        addNewProgram = true;
                        newProgramsIndex++;
                    }
                } else {
                    // No old programs. Just insert new programs.
                    addNewProgram = true;
                    newProgramsIndex++;
                }
                if (addNewProgram) {
                    ops.add(ContentProviderOperation
                            .newInsert(TvContract.Programs.CONTENT_URI)
                            .withValues(newProgram.toContentValues())
                            .build());
                }
                // Throttle the batch operation not to cause TransactionTooLargeException.
                if (ops.size() > BATCH_OPERATION_COUNT
                        || newProgramsIndex >= fetchedProgramsCount) {
                    try {
                        mContext.getContentResolver().applyBatch(TvContract.AUTHORITY, ops);
                    } catch (RemoteException | OperationApplicationException e) {
                        Log.e(TAG, "Failed to insert programs.", e);
                        return;
                    }
                    ops.clear();
                }
            }
        }

        /**
         * Returns {@code true} if the {@code oldProgram} program needs to be updated with the
         * {@code newProgram} program.
         */
        private boolean needsUpdate(Program oldProgram, Program newProgram) {
            // NOTE: Here, we update the old program if it has the same title and overlaps with the
            // new program. The test logic is just an example and you can modify this. E.g. check
            // whether the both programs have the same program ID if your EPG supports any ID for
            // the programs.
            return oldProgram.getTitle().equals(newProgram.getTitle())
                    && oldProgram.getStartTimeUtcMillis() <= newProgram.getEndTimeUtcMillis()
                    && newProgram.getStartTimeUtcMillis() <= oldProgram.getEndTimeUtcMillis();
        }
    }
}
