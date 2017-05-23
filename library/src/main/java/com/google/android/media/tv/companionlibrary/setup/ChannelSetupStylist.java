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

import android.support.v17.leanback.widget.GuidanceStylist;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;

// Gradle puts all resources in the base package, so companionlibrary.R must be imported.
import com.google.android.media.tv.companionlibrary.R;

/**
 * {@link GuidanceStylist} for {@link ChannelSetupStepFragment} and {@link
 * ChannelSetupStepSupportFragment}.
 */
public class ChannelSetupStylist extends GuidanceStylist {

    private ProgressBar mProgressBar;
    private ChannelAdapter mAdapter;

    @Override
    public View onCreateView(
            LayoutInflater layoutInflater, ViewGroup viewGroup, Guidance guidance) {
        View view = super.onCreateView(layoutInflater, viewGroup, guidance);
        mProgressBar = (ProgressBar) view.findViewById(R.id.tif_tune_progress);
        ListView channelList = (ListView) view.findViewById(R.id.tif_channel_list);
        mAdapter = new ChannelAdapter();
        channelList.setAdapter(mAdapter);
        channelList.setOnItemClickListener(null);
        return view;
    }

    @Override
    public int onProvideLayoutId() {
        return R.layout.tif_channel_setup_guidance;
    }

    public ProgressBar getProgressBar() {
        return mProgressBar;
    }

    public ChannelAdapter getChannelAdapter() {
        return mAdapter;
    }
}
