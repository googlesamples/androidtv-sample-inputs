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
package com.google.android.media.tv.companionlibrary.setup;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.v17.leanback.app.GuidedStepSupportFragment;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import com.google.android.media.tv.companionlibrary.sync.SyncStatusBroadcastReceiver;
import com.google.android.media.tv.companionlibrary.sync.SyncStatusBroadcastReceiver.SyncListener;
import java.util.ArrayList;
import java.util.List;

// Gradle puts all resources in the base package, so companionlibrary.R must be imported.
import com.google.android.media.tv.companionlibrary.R;

/**
 * A guided step for scanning TV Input Service channels using a {@link EpgSyncJobService}.
 *
 * <p>The EpgSyncJobService is started during onStart().
 */
public abstract class ChannelSetupStepSupportFragment<J extends EpgSyncJobService>
        extends GuidedStepSupportFragment implements SyncListener {
    private static final String TAG = "ChannelSetupStep";

    private static final long FULL_SYNC_FREQUENCY_MILLIS = 1000 * 60 * 60 * 24; // 24 hour
    private static final long FULL_SYNC_WINDOW_SEC = 1000 * 60 * 60 * 24 * 14; // 2 weeks

    private ChannelSetupStylist mChannelSetupStylist;
    private String mInputId;
    private SyncStatusBroadcastReceiver mSyncStatusChangedReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
    }

    @Override
    public ChannelSetupStylist onCreateGuidanceStylist() {
        mChannelSetupStylist = new ChannelSetupStylist();
        return mChannelSetupStylist;
    }

    @Override
    public Guidance onCreateGuidance(Bundle savedInstanceState) {
        String title = getString(R.string.tif_channel_setup_title);
        String description = getString(R.string.tif_channel_setup_description);
        return new Guidance(title, description, null, null);
    }

    @Override
    public void onCreateActions(List<GuidedAction> actions, Bundle savedInstanceState) {
        actions.add(
                new GuidedAction.Builder(getContext())
                        .id(GuidedAction.ACTION_ID_CANCEL)
                        .title(R.string.tif_channel_setup_cancel)
                        .build());
    }

    @Override
    public ChannelSetupStylist getGuidanceStylist() {
        return mChannelSetupStylist;
    }

    @Override
    public void onStart() {
        super.onStart();
        ProgressBar progressBar = mChannelSetupStylist.getProgressBar();
        if (progressBar != null) {
            progressBar.setIndeterminate(true);
        }
        mSyncStatusChangedReceiver = new SyncStatusBroadcastReceiver(mInputId, this);
        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(
                        mSyncStatusChangedReceiver,
                        new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
        startScan();
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getActivity())
            .unregisterReceiver(mSyncStatusChangedReceiver);
    }

    private void startScan() {
        EpgSyncJobService.cancelAllSyncRequests(getContext());
        EpgSyncJobService.requestImmediateSync(
                getContext(),
                mInputId,
                new ComponentName(getContext(), getEpgSyncJobServiceClass()));

        // Set up SharedPreference to share inputId. If there is not periodic sync job after reboot,
        // RichBootReceiver can use the shared inputId to set up periodic sync job.
        SharedPreferences sharedPreferences =
                getActivity()
                        .getSharedPreferences(
                                EpgSyncJobService.PREFERENCE_EPG_SYNC, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, mInputId);
        editor.apply();
    }

    /** The {@link EpgSyncJobService} for this TV Input Services */
    public abstract Class<J> getEpgSyncJobServiceClass();

    @Override
    public void onGuidedActionClicked(GuidedAction action) {
        if (action.getId() == GuidedAction.ACTION_ID_FINISH) {
            getActivity().setResult(Activity.RESULT_OK);
            finishGuidedStepSupportFragments();
        } else if (action.getId() == GuidedAction.ACTION_ID_CANCEL) {
            getActivity().setResult(Activity.RESULT_CANCELED);
            getActivity().finishAfterTransition();

        } else {
            Log.w(TAG, "Unexpected action " + action);
        }
    }

    @Override
    public void onScanStepCompleted(int completedStep, int totalSteps) {
        ProgressBar progressBar = mChannelSetupStylist.getProgressBar();
        if (totalSteps > 0 && progressBar != null) {
            progressBar.setIndeterminate(false);
            progressBar.setMax(totalSteps);
            progressBar.setProgress(completedStep);
        }
    }

    @Override
    public void onScannedChannel(CharSequence displayName, CharSequence displayNumber) {
        ChannelAdapter adapter = mChannelSetupStylist.getChannelAdapter();
        if (adapter != null) {
            adapter.add(Pair.create(displayName.toString(), displayNumber.toString()));
        }
    }

    @Override
    public void onScanFinished() {
        List<GuidedAction> actions = new ArrayList<>(1);
        actions.add(
                new GuidedAction.Builder(getContext())
                        .id(GuidedAction.ACTION_ID_FINISH)
                        .title(R.string.lb_guidedaction_finish_title)
                        .build());
        setActions(actions);
        setupPeriodicSync();
    }

    private void setupPeriodicSync() {
        EpgSyncJobService.cancelAllSyncRequests(getActivity());
        EpgSyncJobService.setUpPeriodicSync(
                getActivity(),
                mInputId,
                new ComponentName(getActivity(), getEpgSyncJobServiceClass()),
                getFullSyncFrequencyMillis(),
                getFullSyncWindowSec());
    }

    /**
     * How may seconds of EPG data to download during each full sync
     *
     * <p>Defaults to {@value FULL_SYNC_WINDOW_SEC}
     */
    public long getFullSyncWindowSec() {
        return FULL_SYNC_WINDOW_SEC;
    }

    /**
     * How often to do a full sync in milliseconds
     *
     * <p>Defaults to {@value FULL_SYNC_FREQUENCY_MILLIS}
     */
    public long getFullSyncFrequencyMillis() {
        return FULL_SYNC_FREQUENCY_MILLIS;
    }

    @Override
    public void onScanError(int errorCode) {
        String message;
        switch (errorCode) {
            case EpgSyncJobService.ERROR_EPG_SYNC_CANCELED:
                message = getString(R.string.tif_channel_scan_canceled);
                break;
            case EpgSyncJobService.ERROR_NO_PROGRAMS:
                message = getString(R.string.tif_channel_error_no_programs);
                break;
            case EpgSyncJobService.ERROR_NO_CHANNELS:
                message = getString(R.string.tif_channel_error_no_channels);
                break;
            default:
                message = getString(R.string.tif_channel_error_unknown);
                break;
        }
        // TODO create a proper error display
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }
}
