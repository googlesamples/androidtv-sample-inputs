/*
 * Copyright 2017 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.media.tv.companionlibrary.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.android.media.tv.companionlibrary.model.Channel;

/**
 * Receives broadcasts from a {@link EpgSyncJobService}.
 *
 * <p>This a single use receiver. No events are handled after the scan is complete.
 */
public final class SyncStatusBroadcastReceiver extends BroadcastReceiver {

    private static final boolean DEBUG = false;
    private static final String TAG = "SyncStatusBroadcastRcvr";

    /** Listens for sync events broadcast by a @{link {@link EpgSyncJobService}. */
    public interface SyncListener {

        /**
         * This method is used to display progress to the user.
         *
         * <p>Before this method is called for the first time amount of progress is considered
         * indeterminate.
         *
         * @param completedStep The number scanning steps that have been completed.
         * @param totalSteps The total number scanning steps.
         */
        void onScanStepCompleted(int completedStep, int totalSteps);

        /**
         * This method will be called when a channel has been completely scanned. It can be
         * overridden to display custom information about this channel to the user.
         *
         * @param displayName {@link Channel#getDisplayName()} for the scanned channel.
         * @param displayNumber {@link Channel#getDisplayNumber()} ()} for the scanned channel.
         */
        void onScannedChannel(CharSequence displayName, CharSequence displayNumber);

        /**
         * This method will be called when scanning ends. Developers may want to notify the user
         * that scanning has completed and allow them to exit the activity.
         */
        void onScanFinished();

        /**
         * Update the description that will be displayed underneath the progress bar. This could be
         * used to state the current progress of the scan.
         *
         * @param errorCode The error code causing the scan to fail.
         */
        void onScanError(int errorCode);
    }

    private final String mInputId;
    private final SyncListener mSyncListener;

    private boolean mFinishedScan = false;

    public SyncStatusBroadcastReceiver(String inputId, SyncListener syncListener) {
        this.mInputId = inputId;
        this.mSyncListener = syncListener;
    }

    @Override
    public void onReceive(Context context, final Intent intent) {
        if (mFinishedScan) {
            return;
        }
        String syncStatusChangedInputId =
                intent.getStringExtra(EpgSyncJobService.BUNDLE_KEY_INPUT_ID);
        if (syncStatusChangedInputId != null && syncStatusChangedInputId.equals(mInputId)) {
            String syncStatus = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
            if (syncStatus.equals(EpgSyncJobService.SYNC_STARTED)) {
                if (DEBUG) {
                    Log.d(TAG, "Sync status: Started");
                }
            } else if (syncStatus.equals(EpgSyncJobService.SYNC_SCANNED)) {
                int channelsScanned =
                        intent.getIntExtra(EpgSyncJobService.BUNDLE_KEY_CHANNELS_SCANNED, 0);
                int channelCount =
                        intent.getIntExtra(EpgSyncJobService.BUNDLE_KEY_CHANNEL_COUNT, 0);
                mSyncListener.onScanStepCompleted(channelsScanned, channelCount);
                String channelDisplayName =
                        intent.getStringExtra(
                                EpgSyncJobService.BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NAME);
                String channelDisplayNumber =
                        intent.getStringExtra(
                                EpgSyncJobService.BUNDLE_KEY_SCANNED_CHANNEL_DISPLAY_NUMBER);
                if (DEBUG) {
                    Log.d(TAG, "Sync status: Channel Scanned");
                    Log.d(TAG, "Scanned " + channelsScanned + " out of " + channelCount);
                }
                mSyncListener.onScannedChannel(channelDisplayName, channelDisplayNumber);
            } else if (syncStatus.equals(EpgSyncJobService.SYNC_FINISHED)) {
                if (DEBUG) {
                    Log.d(TAG, "Sync status: Finished");
                }
                mFinishedScan = true;
                mSyncListener.onScanFinished();
            } else if (syncStatus.equals(EpgSyncJobService.SYNC_ERROR)) {
                int errorCode = intent.getIntExtra(EpgSyncJobService.BUNDLE_KEY_ERROR_REASON, 0);
                if (DEBUG) {
                    Log.d(TAG, "Error occurred: " + errorCode);
                }
                mSyncListener.onScanError(errorCode);
            }
        }
    }
}
