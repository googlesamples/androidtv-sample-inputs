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

package com.example.android.sampletvinput.sync;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.support.annotation.VisibleForTesting;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseArray;

import com.example.android.sampletvinput.model.Channel;
import com.example.android.sampletvinput.model.InternalProviderData;
import com.example.android.sampletvinput.model.Program;
import com.example.android.sampletvinput.utils.InternalProviderDataUtil;
import com.example.android.sampletvinput.utils.TvContractUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to handle callbacks from JobScheduler. This service will be called by the system to
 * update the EPG with channels and programs periodically.
 * <p />
 * You can extend this class and add it your app by including it in your app's AndroidManfiest.xml:
 * <pre>
 *      &lt;service
 *          android:name=".SampleJobService"
 *          android:permission="android.permission.BIND_JOB_SERVICE"
 *          android:exported="true" /&gt;
 * </pre>
 *
 * You will need to implement several methods in your EpgSyncJobService to return your content.
 * <p />
 * To start periodically syncing data, call
 * {@link #setUpPeriodicSync(Context, String, ComponentName, long)}.
 * <p />
 * To sync manually, call {@link #requestSync(Context, String, long, ComponentName)}.
 */
public abstract class EpgSyncJobService extends JobService {
    private static final String TAG = "EpgSyncJobService";
    private static final boolean DEBUG = false;

    /** The action that will be broadcast when the job service's status changes. */
    public static final String ACTION_SYNC_STATUS_CHANGED =
            "com.example.android.sampletvinput.sync.ACTION_SYNC_STATUS_CHANGED";
    /** The key representing the component name for the app's TvInputService. */
    public static final String BUNDLE_KEY_INPUT_ID = "com.example.android.sampletvinput.sync.bund" +
            "le_key_input_id";
    /** The name for the {@link android.content.SharedPreferences} file used for storing syncing
     * metadata. */
    public static final String PREFERENCE_EPG_SYNC = "com.example.android.sampletvinput.sync.pref" +
            "erence_epg_sync";
    /** The status of the job service when syncing has completed. */
    public static final String SYNC_FINISHED = "sync_finished";
    /** The status of the job service when syncing has begun. */
    public static final String SYNC_STARTED = "sync_started";
    /** The key corresponding to the job service's status. */
    public static final String SYNC_STATUS = "sync_status";
    /** The default period between full EPG syncs, one day. */
    public static final long DEFAULT_SYNC_PERIOD_MILLIS = 1000 * 60 * 60 * 24; // 1 Day
    private static final long DEFAULT_EPG_SYNC_PERIOD_MILLIS = 1000 * 60 * 60; // 1 Hour

    private static final int PERIODIC_SYNC_JOB_ID = 0;
    private static final int REQUEST_SYNC_JOB_ID = 1;
    private static final int BATCH_OPERATION_COUNT = 100;
    private static final long OVERRIDE_DEADLINE_MILLIS = 1000;  // 1 second
    private static final String BUNDLE_KEY_SYNC_PERIOD = "bundle_key_sync_period";

    private final SparseArray<EpgSyncTask> mTaskArray = new SparseArray<>();
    private static final Object mContextLock = new Object();
    private static Context mContext;

    /**
     * Returns the channels that your app contains.
     *
     * @return The list of channels for your app.
     */
    public abstract List<Channel> getChannels();

    /**
     * Returns the programs that will appear for each channel.
     *
     * @param channelUri The Uri corresponding to the channel.
     * @param channel The channel your programs will appear on.
     * @param startMs The starting time in milliseconds since the epoch to generate programs. If
     * your program starts before this starting time, it should be be included.
     * @param endMs The ending time in milliseconds since the epoch to generate programs. If your
     * program starts before this ending time, it should be be included.
     * @return A list of programs for a given channel.
     */
    public abstract List<Program> getProgramsForChannel(Uri channelUri, Channel channel,
            long startMs, long endMs);


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

    /**
     * Returns {@code true} if the {@code oldProgram} program is the same as the
     * {@code newProgram} program but should update metadata. This updates the database instead
     * of deleting and inserting a new program to keep the user's intent, eg. recording this
     * program.
     */
    public boolean shouldUpdateProgramMetadata(Program oldProgram, Program newProgram) {
        // NOTE: Here, we update the old program if it has the same title and overlaps with the
        // new program. The test logic is just an example and you can modify this. E.g. check
        // whether the both programs have the same program ID if your EPG supports any ID for
        // the programs.
        return oldProgram.getTitle().equals(newProgram.getTitle())
                && oldProgram.getStartTimeUtcMillis() <= newProgram.getEndTimeUtcMillis()
                && newProgram.getStartTimeUtcMillis() <= oldProgram.getEndTimeUtcMillis();
    }

    /** Send the job to JobScheduler. */
    private static void scheduleJob(Context context, JobInfo job) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
    }

    /**
     * Initializes a job that will periodically update the app's channels and programs.
     *
     * @param context Application's context.
     * @param inputId Component name for the app's TvInputService. This can be received through an
     * Intent extra parameter {@link TvInputInfo#EXTRA_INPUT_ID}.
     * @param jobServiceComponent The {@link EpgSyncJobService} component name that will run.
     * @param fullSyncPeriod The period between when the job will run a full background sync in
     * milliseconds.
     */
    public static void setUpPeriodicSync(Context context, String inputId,
            ComponentName jobServiceComponent, long fullSyncPeriod) {
        if (jobServiceComponent.getClass().isAssignableFrom(EpgSyncJobService.class)) {
            throw new IllegalArgumentException("This class does not extend TifJobService");
        }
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        JobInfo.Builder builder = new JobInfo.Builder(PERIODIC_SYNC_JOB_ID, jobServiceComponent);
        JobInfo jobInfo = builder
                .setExtras(persistableBundle)
                .setPeriodic(fullSyncPeriod)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);
    }

    /**
     * Manually requests a job to run now.
     *
     * To check the current status of the sync, register a {@link android.content.BroadcastReceiver}
     * with an {@link android.content.IntentFilter} which checks for the action
     * {@link #ACTION_SYNC_STATUS_CHANGED}.
     * <p />
     * The sync status is an extra parameter in the {@link Intent} with key
     * {@link #SYNC_STATUS}. The sync status is either {@link #SYNC_STARTED} or
     * {@link #SYNC_FINISHED}.
     * <p />
     * Check that the value of {@link #BUNDLE_KEY_INPUT_ID} matches your
     * {@link android.media.tv.TvInputService}. If you're calling this from your setup activity,
     * you can get the extra parameter {@link TvInputInfo#EXTRA_INPUT_ID}.
     * <p />
     * @param context Application's context.
     * @param inputId Component name for the app's TvInputService. This can be received through an
     * Intent extra parameter {@link TvInputInfo#EXTRA_INPUT_ID}.
     * @param syncPeriod The length of time in milliseconds to sync. For a manual sync, this
     * should be relatively short. For a background sync this should be long.
     * @param jobServiceComponent The {@link EpgSyncJobService} class that will run.
     */
    public static void requestSync(Context context, String inputId, long syncPeriod,
            ComponentName jobServiceComponent) {
        if (jobServiceComponent.getClass().isAssignableFrom(EpgSyncJobService.class)) {
            throw new IllegalArgumentException("This class does not extend TifJobService");
        }
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        persistableBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        persistableBundle.putString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        persistableBundle.putLong(EpgSyncJobService.BUNDLE_KEY_SYNC_PERIOD, syncPeriod);
        JobInfo.Builder builder = new JobInfo.Builder(REQUEST_SYNC_JOB_ID, jobServiceComponent);
        JobInfo jobInfo = builder
                .setExtras(persistableBundle)
                .setOverrideDeadline(EpgSyncJobService.OVERRIDE_DEADLINE_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);
        Intent intent = new Intent(ACTION_SYNC_STATUS_CHANGED);
        intent.putExtra(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        intent.putExtra(EpgSyncJobService.SYNC_STATUS, EpgSyncJobService.SYNC_STARTED);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * Cancels all pending jobs.
     * @param context Application's context.
     */
    public static void cancelAllSyncRequests(Context context) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }

    /**
     * @hide
     */
    protected class EpgSyncTask extends AsyncTask<Void, Void, Void> {
        private final JobParameters params;

        protected EpgSyncTask(JobParameters params) {
            this.params = params;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (isCancelled()) {
                return null;
            }

            PersistableBundle extras = params.getExtras();
            String inputId = extras.getString(BUNDLE_KEY_INPUT_ID);
            if (inputId == null) {
                return null;
            }
            List<Channel> tvChannels = getChannels();
            TvContractUtils.updateChannels(mContext, inputId, tvChannels);
            LongSparseArray<Channel> channelMap = TvContractUtils.buildChannelMap(
                    mContext.getContentResolver(), inputId, tvChannels);
            if (channelMap == null) {
                return null;
            }
            // Default to one hour sync
            long durationMs = extras.getLong(
                    BUNDLE_KEY_SYNC_PERIOD, DEFAULT_EPG_SYNC_PERIOD_MILLIS);
            long startMs = System.currentTimeMillis();
            long endMs = startMs + durationMs;
            for (int i = 0; i < channelMap.size(); ++i) {
                Uri channelUri = TvContract.buildChannelUri(channelMap.keyAt(i));
                if (isCancelled()) {
                    return null;
                }
                List<Program> programs = getProgramsForChannel(channelUri, channelMap.valueAt(i),
                        startMs, endMs);
                if (DEBUG) {
                    Log.d(TAG, programs.toString());
                }
                for (int index = 0; index < programs.size(); index++) {
                    if (programs.get(index).getChannelId() == -1) {
                        // Automatically set the channel id if not set
                        programs.set(index,
                                new Program.Builder(programs.get(index))
                                        .setChannelId(channelMap.valueAt(i).getId())
                                        .build());
                    }
                }

                // Double check if the job is cancelled, so that this task can be finished faster
                // after cancel() is called.
                if (isCancelled()) {
                    return null;
                }
                updatePrograms(channelUri,
                        getPrograms(channelMap.valueAt(i), programs, startMs, endMs));
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
            if (jobParams.getJobId() == REQUEST_SYNC_JOB_ID) {
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
         * @param channel The {@link Channel} for the programs to return.
         * @param programs The feed fetched from cloud.
         * @param startTimeMs The start time of the range requested.
         * @param endTimeMs The end time of the range requested.
         * @return A list of programs for the channel within the specifed range. They may be
         * repeated.
         * @hide
         */
        @VisibleForTesting
        public List<Program> getPrograms(Channel channel, List<Program> programs,
                long startTimeMs, long endTimeMs) {
            if (startTimeMs > endTimeMs) {
                throw new IllegalArgumentException("Start time must be before end time");
            }
            List<Program> programForGivenTime = new ArrayList<>();
            if (!channel.getInternalProviderData().isRepeatable()) {
                for (Program program : programs) {
                    if (program.getStartTimeUtcMillis() <= endTimeMs
                            && program.getEndTimeUtcMillis() >= startTimeMs) {
                        programForGivenTime.add(new Program.Builder(program)
                                .setChannelId(channel.getId())
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
            for (Program program : programs) {
                totalDurationMs +=
                        (program.getEndTimeUtcMillis() - program.getStartTimeUtcMillis());
            }
            if (totalDurationMs <= 0) {
                throw new IllegalArgumentException("The duration of all programs must be greater " +
                        "than 0ms.");
            }

            long programStartTimeMs = startTimeMs - startTimeMs % totalDurationMs;
            int i = 0;
            final int programCount = programs.size();
            while (programStartTimeMs < endTimeMs) {
                Program programInfo = programs.get(i++ % programCount);
                long programEndTimeMs = programStartTimeMs + totalDurationMs;
                if (programInfo.getEndTimeUtcMillis() > -1
                        && programInfo.getStartTimeUtcMillis() > -1) {
                    programEndTimeMs = programStartTimeMs +
                            (programInfo.getEndTimeUtcMillis()
                                    - programInfo.getStartTimeUtcMillis());
                }
                if (programEndTimeMs < startTimeMs) {
                    programStartTimeMs = programEndTimeMs;
                    continue;
                }
                // Shift advertisement time to match current program time.
                InternalProviderData updateInternalProviderData = InternalProviderDataUtil
                        .shiftAdsTimeWithProgram(
                                programInfo.getInternalProviderData(),
                                programInfo.getStartTimeUtcMillis(),
                                programStartTimeMs);
                programForGivenTime.add(new Program.Builder(programInfo)
                        .setChannelId(channel.getId())
                        .setStartTimeUtcMillis(programStartTimeMs)
                        .setEndTimeUtcMillis(programEndTimeMs)
                        .setInternalProviderData(updateInternalProviderData)
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
                if (program.getEndTimeUtcMillis() > firstNewProgram.getStartTimeUtcMillis()
                        && program.getEndTimeUtcMillis() < System.currentTimeMillis()) {
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
                    } else if (shouldUpdateProgramMetadata(oldProgram, newProgram)) {
                        // Partial match. Update the old program with the new one.
                        // NOTE: Use 'update' in this case instead of 'insert' and 'delete'. There
                        // could be application specific settings which belong to the old program.
                        ops.add(ContentProviderOperation.newUpdate(
                                TvContract.buildProgramUri(oldProgram.getId()))
                                .withValues(newProgram.toContentValues())
                                .build());
                        oldProgramsIndex++;
                        newProgramsIndex++;
                    } else if (oldProgram.getEndTimeUtcMillis()
                            < newProgram.getEndTimeUtcMillis()) {
                        // No match. Remove the old program first to see if the next program in
                        // {@code oldPrograms} partially matches the new program.
                        ops.add(ContentProviderOperation.newDelete(
                                TvContract.buildProgramUri(oldProgram.getId()))
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
    }
}