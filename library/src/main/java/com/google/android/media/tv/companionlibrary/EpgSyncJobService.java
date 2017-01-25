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

package com.google.android.media.tv.companionlibrary;

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

import com.google.android.media.tv.companionlibrary.model.Advertisement;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.InternalProviderData;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.utils.TvContractUtils;

import junit.framework.Assert;

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
 * {@link #setUpPeriodicSync(Context, String, ComponentName, long, long)}.
 * <p />
 * To sync manually, call {@link #requestImmediateSync(Context, String, long, ComponentName)}.
 */
public abstract class EpgSyncJobService extends JobService {
    private static final String TAG = "EpgSyncJobService";
    private static final boolean DEBUG = true;

    /** The action that will be broadcast when the job service's status changes. */
    public static final String ACTION_SYNC_STATUS_CHANGED =
            EpgSyncJobService.class.getPackage().getName() + ".ACTION_SYNC_STATUS_CHANGED";
    /** The key representing the component name for the app's TvInputService. */
    public static final String BUNDLE_KEY_INPUT_ID = EpgSyncJobService.class.getPackage().getName()
            + ".bundle_key_input_id";
    /** The key representing the number of channels that have been scanned and populated in the EPG.
     */
    public static final String BUNDLE_KEY_CHANNELS_SCANNED =
            EpgSyncJobService.class.getPackage().getName() + ".bundle_key_channels_scanned";
    /** The key representing the total number of channels for this input. */
    public static final String BUNDLE_KEY_CHANNEL_COUNT =
            EpgSyncJobService.class.getPackage().getName() + ".bundle_key_channel_count";
    /** The key representing the most recently scanned channel display name. */
    public static final String BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NAME =
            EpgSyncJobService.class.getPackage().getName() +
                    ".bundle_key_scanned_channel_display_name";
    /** The key representing the most recently scanned channel display number. */
    public static final String BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NUMBER =
            EpgSyncJobService.class.getPackage().getName() +
                    ".bundle_key_scanned_channel_display_number";
    /** The key representing the error that occurred during an EPG sync */
    public static final String BUNDLE_KEY_ERROR_REASON =
            EpgSyncJobService.class.getPackage().getName() + ".bundle_key_error_reason";

    /** The name for the {@link android.content.SharedPreferences} file used for storing syncing
     * metadata. */
    public static final String PREFERENCE_EPG_SYNC = EpgSyncJobService.class.getPackage().getName()
            + ".preference_epg_sync";

    /** The status of the job service when syncing has begun. */
    public static final String SYNC_STARTED = "sync_started";
    /** The status of the job service when a channel has been scanned and the EPG for that channel
     * has been populated. */
    public static final String SYNC_SCANNED = "sync_scanned";
    /** The status of the job service when syncing has completed. */
    public static final String SYNC_FINISHED = "sync_finished";
    /** The status of the job when a problem occurs during syncing. A {@link #SYNC_FINISHED}
     *  broadcast will still be sent when the service is done. This status can be used to identify
     *  specific issues in your EPG sync.
     * */
    public static final String SYNC_ERROR = "sync_error";
    /** The key corresponding to the job service's status. */
    public static final String SYNC_STATUS = "sync_status";

    /** Indicates that the EPG sync was canceled before being completed. */
    public static final int ERROR_EPG_SYNC_CANCELED = 1;
    /** Indicates that the input id was not defined and the EPG sync cannot complete. */
    public static final int ERROR_INPUT_ID_NULL = 2;
    /** Indicates that no programs were found. */
    public static final int ERROR_NO_PROGRAMS = 3;
    /** Indicates that no channels were found. */
    public static final int ERROR_NO_CHANNELS = 4;
    /** Indicates an error occurred when updating programs in the database */
    public static final int ERROR_DATABASE_INSERT = 5;

    /** The default period between full EPG syncs, one day. */
    private static final long DEFAULT_SYNC_PERIOD_MILLIS = 1000 * 60 * 60 * 12; // 12 hour
    private static final long DEFAULT_IMMEDIATE_EPG_DURATION_MILLIS = 1000 * 60 * 60; // 1 Hour
    private static final long DEFAULT_PERIODIC_EPG_DURATION_MILLIS = 1000 * 60 * 60 * 48; // 48 Hour

    private static final int PERIODIC_SYNC_JOB_ID = 0;
    private static final int REQUEST_SYNC_JOB_ID = 1;
    private static final int BATCH_OPERATION_COUNT = 100;
    private static final long OVERRIDE_DEADLINE_MILLIS = 1000;  // 1 second
    private static final String BUNDLE_KEY_SYNC_PERIOD = "bundle_key_sync_period";

    private final SparseArray<EpgSyncTask> mTaskArray = new SparseArray<>();
    private static final Object mContextLock = new Object();
    private Context mContext;

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
        if (DEBUG) {
            Log.d(TAG, "Created EpgSyncJobService");
        }
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
        // Broadcast status
        Intent intent = new Intent(ACTION_SYNC_STATUS_CHANGED);
        intent.putExtra(BUNDLE_KEY_INPUT_ID, params.getExtras().getString(BUNDLE_KEY_INPUT_ID));
        intent.putExtra(SYNC_STATUS, SYNC_STARTED);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);

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
        int result = jobScheduler.schedule(job);
        Assert.assertEquals(result, JobScheduler.RESULT_SUCCESS);
        if (DEBUG) {
            Log.d(TAG, "Scheduling result is " + result);
        }
    }

    /**
     * Initializes a job that will periodically update the app's channels and programs with a
     * default period of 24 hours.
     *
     * @param context Application's context.
     * @param inputId Component name for the app's TvInputService. This can be received through an
     * Intent extra parameter {@link TvInputInfo#EXTRA_INPUT_ID}.
     * @param jobServiceComponent The {@link EpgSyncJobService} component name that will run.
     */
    public static void setUpPeriodicSync(Context context, String inputId,
            ComponentName jobServiceComponent) {
        setUpPeriodicSync(context, inputId, jobServiceComponent, DEFAULT_SYNC_PERIOD_MILLIS,
                DEFAULT_PERIODIC_EPG_DURATION_MILLIS);
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
     * @param syncDuration The duration of EPG content to fetch in milliseconds. For a manual sync,
     * this should be relatively short. For a background sync this should be long.
     */
    public static void setUpPeriodicSync(Context context, String inputId,
            ComponentName jobServiceComponent, long fullSyncPeriod, long syncDuration) {
        if (jobServiceComponent.getClass().isAssignableFrom(EpgSyncJobService.class)) {
            throw new IllegalArgumentException("This class does not extend EpgSyncJobService");
        }
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        persistableBundle.putLong(EpgSyncJobService.BUNDLE_KEY_SYNC_PERIOD, syncDuration);
        JobInfo.Builder builder = new JobInfo.Builder(PERIODIC_SYNC_JOB_ID, jobServiceComponent);
        JobInfo jobInfo = builder
                .setExtras(persistableBundle)
                .setPeriodic(fullSyncPeriod)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);
        if (DEBUG) {
            Log.d(TAG, "Job has been scheduled for every " + fullSyncPeriod + "ms");
        }
    }

    /**
     * Manually requests a job to run now to retrieve EPG content for the next hour.
     *
     * @param context Application's context.
     * @param inputId Component name for the app's TvInputService. This can be received through an
     * Intent extra parameter {@link TvInputInfo#EXTRA_INPUT_ID}.
     * @param jobServiceComponent The {@link EpgSyncJobService} class that will run.
     */
    public static void requestImmediateSync(Context context, String inputId,
            ComponentName jobServiceComponent) {
        requestImmediateSync(context, inputId, DEFAULT_IMMEDIATE_EPG_DURATION_MILLIS,
                jobServiceComponent);
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
     * @param syncDuration The duration of EPG content to fetch in milliseconds. For a manual sync,
     * this should be relatively short. For a background sync this should be long.
     * @param jobServiceComponent The {@link EpgSyncJobService} class that will run.
     */
    public static void requestImmediateSync(Context context, String inputId, long syncDuration,
            ComponentName jobServiceComponent) {
        if (jobServiceComponent.getClass().isAssignableFrom(EpgSyncJobService.class)) {
            throw new IllegalArgumentException("This class does not extend EpgSyncJobService");
        }
        PersistableBundle persistableBundle = new PersistableBundle();
        persistableBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        persistableBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        persistableBundle.putString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        persistableBundle.putLong(EpgSyncJobService.BUNDLE_KEY_SYNC_PERIOD, syncDuration);
        JobInfo.Builder builder = new JobInfo.Builder(REQUEST_SYNC_JOB_ID, jobServiceComponent);
        JobInfo jobInfo = builder
                .setExtras(persistableBundle)
                .setOverrideDeadline(EpgSyncJobService.OVERRIDE_DEADLINE_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);
        if (DEBUG) {
            Log.d(TAG, "Single job scheduled");
        }
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
    public class EpgSyncTask extends AsyncTask<Void, Void, Void> {
        private final JobParameters params;
        private String mInputId;

        public EpgSyncTask(JobParameters params) {
            this.params = params;
        }

        @Override
        public Void doInBackground(Void... voids) {
            PersistableBundle extras = params.getExtras();
            mInputId = extras.getString(BUNDLE_KEY_INPUT_ID);
            if (mInputId == null) {
                broadcastError(ERROR_INPUT_ID_NULL);
                return null;
            }

            if (isCancelled()) {
                broadcastError(ERROR_EPG_SYNC_CANCELED);
                return null;
            }

            List<Channel> tvChannels = getChannels();
            TvContractUtils.updateChannels(mContext, mInputId, tvChannels);
            LongSparseArray<Channel> channelMap = TvContractUtils.buildChannelMap(
                    mContext.getContentResolver(), mInputId);
            if (channelMap == null) {
                broadcastError(ERROR_NO_CHANNELS);
                return null;
            }
            // Default to one hour sync
            long durationMs = extras.getLong(
                    BUNDLE_KEY_SYNC_PERIOD, DEFAULT_IMMEDIATE_EPG_DURATION_MILLIS);
            long startMs = System.currentTimeMillis();
            long endMs = startMs + durationMs;
            for (int i = 0; i < channelMap.size(); ++i) {
                Uri channelUri = TvContract.buildChannelUri(channelMap.keyAt(i));
                if (isCancelled()) {
                    broadcastError(ERROR_EPG_SYNC_CANCELED);
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
                    broadcastError(ERROR_EPG_SYNC_CANCELED);
                    return null;
                }
                updatePrograms(channelUri,
                        getPrograms(channelMap.valueAt(i), programs, startMs, endMs));
                Intent intent = new Intent(ACTION_SYNC_STATUS_CHANGED);
                intent.putExtra(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, mInputId);
                intent.putExtra(EpgSyncJobService.BUNDLE_KEY_CHANNELS_SCANNED, i);
                intent.putExtra(EpgSyncJobService.BUNDLE_KEY_CHANNEL_COUNT, channelMap.size());
                intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NAME,
                        channelMap.valueAt(i).getDisplayName());
                intent.putExtra(EpgSyncJobService.BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NUMBER,
                        channelMap.valueAt(i).getDisplayNumber());
                intent.putExtra(EpgSyncJobService.SYNC_STATUS, EpgSyncJobService.SYNC_SCANNED);
                LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
            }
            return null;
        }

        @Override
        public void onPostExecute(Void success) {
            finishEpgSync(params);
        }

        @Override
        public void onCancelled(Void ignore) {
            finishEpgSync(params);
        }

        private void finishEpgSync(JobParameters jobParams) {
            if (DEBUG) {
                Log.d(TAG, "taskFinished(" + jobParams.getJobId() + ")");
            }
            mTaskArray.delete(jobParams.getJobId());
            jobFinished(jobParams, false);
            if (DEBUG) {
                Log.d(TAG, "Send out broadcast");
            }
            Intent intent = new Intent(ACTION_SYNC_STATUS_CHANGED);
            intent.putExtra(
                    BUNDLE_KEY_INPUT_ID, jobParams.getExtras().getString(BUNDLE_KEY_INPUT_ID));
            intent.putExtra(SYNC_STATUS, SYNC_FINISHED);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        }

        private void broadcastError(int reason) {
            Intent intent = new Intent(ACTION_SYNC_STATUS_CHANGED);
            intent.putExtra(BUNDLE_KEY_INPUT_ID, mInputId);
            intent.putExtra(SYNC_STATUS, SYNC_ERROR);
            intent.putExtra(BUNDLE_KEY_ERROR_REASON, reason);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
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
            if (channel.getInternalProviderData() != null
                    && !channel.getInternalProviderData().isRepeatable()) {
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
                InternalProviderData updateInternalProviderData =
                        programInfo.getInternalProviderData();
                shiftAdsTimeWithProgram(updateInternalProviderData,
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
         * Shift advertisement time to match program playback time. For channels with repeated program,
         * the time for current program may vary from what it was defined previously.
         *
         * @param oldProgramStartTimeMs Outdated program start time.
         * @param newProgramStartTimeMs Updated program start time.
         */
        private void shiftAdsTimeWithProgram(InternalProviderData internalProviderData,
                    long oldProgramStartTimeMs, long newProgramStartTimeMs) {
            if (internalProviderData == null) {
                return;
            }
            long timeShift = newProgramStartTimeMs - oldProgramStartTimeMs;
            List<Advertisement> oldAds = internalProviderData.getAds();
            List<Advertisement> newAds = new ArrayList<>();
            for (Advertisement oldAd : oldAds) {
                newAds.add(new Advertisement.Builder(oldAd)
                        .setStartTimeUtcMillis(oldAd.getStartTimeUtcMillis() + timeShift)
                        .setStopTimeUtcMillis(oldAd.getStopTimeUtcMillis() + timeShift)
                        .build());
            }
            internalProviderData.setAds(newAds);
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
                broadcastError(ERROR_NO_PROGRAMS);
                return;
            }
            List<Program> oldPrograms = TvContractUtils.getPrograms(mContext.getContentResolver(),
                    channelUri);
            Program firstNewProgram = newPrograms.get(0);
            int oldProgramsIndex = 0;
            int newProgramsIndex = 0;
            // Skip the past programs. They will be automatically removed by the system.
            for (Program program : oldPrograms) {
                if (program.getEndTimeUtcMillis() < System.currentTimeMillis() ||
                        program.getEndTimeUtcMillis() < firstNewProgram.getStartTimeUtcMillis()) {
                    oldProgramsIndex++;
                } else {
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
                        broadcastError(ERROR_DATABASE_INSERT);
                        return;
                    }
                    ops.clear();
                }
            }
        }
    }
}
