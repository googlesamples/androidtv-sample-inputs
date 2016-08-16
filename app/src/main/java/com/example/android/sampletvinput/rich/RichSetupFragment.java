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
package com.example.android.sampletvinput.rich;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.android.sampletvinput.R;
import com.google.android.media.tv.companionlibrary.ChannelSetupFragment;
import com.google.android.media.tv.companionlibrary.EpgSyncJobService;
import com.example.android.sampletvinput.SampleJobService;

/**
 * Fragment which shows a sample UI for registering channels and setting up SampleJobService to
 * provide program information in the background.
 */
public class RichSetupFragment extends ChannelSetupFragment {
    private String mInputId = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View fragmentView = super.onCreateView(inflater, container, savedInstanceState);
        setBackgroundColor(ContextCompat.getColor(getActivity(), R.color.detail_background));
        setBadge(getResources().getDrawable(R.drawable.your_company));
        setChannelListVisibility(true);
        setTitle(getString(R.string.rich_input_label));
        setDescription(getString(R.string.company_name));
        setButtonText(getString(R.string.rich_setup_add_channel));
        return fragmentView;
    }

    @Override
    public void onScanStarted() {
        EpgSyncJobService.cancelAllSyncRequests(getActivity());
        EpgSyncJobService.requestImmediateSync(getActivity(), mInputId,
                new ComponentName(getActivity(), SampleJobService.class));

        // Set up SharedPreference to share inputId. If there is not periodic sync job after reboot,
        // RichBootReceiver can use the shared inputId to set up periodic sync job.
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(
                EpgSyncJobService.PREFERENCE_EPG_SYNC, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, mInputId);
        editor.apply();

        setButtonText(getString(R.string.rich_setup_in_progress));
    }

    @Override
    public String getInputId() {
        return mInputId;
    }

    @Override
    public void onScanFinished() {
        EpgSyncJobService.cancelAllSyncRequests(getActivity());
        EpgSyncJobService.setUpPeriodicSync(getActivity(), mInputId,
            new ComponentName(getActivity(), SampleJobService.class));
        getActivity().setResult(Activity.RESULT_OK);
        getActivity().finish();
    }
}
