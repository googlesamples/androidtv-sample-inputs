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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Static helper methods for working with the SyncJobService.
 */
public class SyncUtils {

    public static final int PERIODIC_SYNC_JOB_ID = 0;
    public static final int REQUEST_SYNC_JOB_ID = 1;

    /** Send the job to JobScheduler. **/
    private static void scheduleJob(Context context, JobInfo job) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.schedule(job);
    }

    public static void setUpPeriodicSync(Context context, String inputId) {
        PersistableBundle pBundle = new PersistableBundle();
        pBundle.putString(SyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        JobInfo.Builder builder = new JobInfo.Builder(PERIODIC_SYNC_JOB_ID,
                new ComponentName(context, SyncJobService.class));
        JobInfo jobInfo = builder
                .setExtras(pBundle)
                .setPeriodic(SyncJobService.FULL_SYNC_FREQUENCY_MILLIS)
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);
    }

    public static void requestSync(Context context, String inputId, boolean currentProgramOnly) {
        PersistableBundle pBundle = new PersistableBundle();
        pBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        pBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        pBundle.putString(SyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        pBundle.putBoolean(SyncJobService.BUNDLE_KEY_CURRENT_PROGRAM_ONLY, currentProgramOnly);
        JobInfo.Builder builder = new JobInfo.Builder(REQUEST_SYNC_JOB_ID,
                new ComponentName(context, SyncJobService.class));
        JobInfo jobInfo = builder
                .setExtras(pBundle)
                .setOverrideDeadline(SyncJobService.OVERRIDE_DEADLINE_MILLIS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build();
        scheduleJob(context, jobInfo);
        Intent intent = new Intent(SyncJobService.ACTION_SYNC_STATUS_CHANGED);
        intent.putExtra(SyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        intent.putExtra(SyncJobService.SYNC_STATUS, SyncJobService.SYNC_STARTED);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    public static void cancelAll(Context context) {
        JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancelAll();
    }
}
