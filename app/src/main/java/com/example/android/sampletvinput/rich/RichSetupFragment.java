/*
 * Copyright 2016 The Android Open Source Project.
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

import android.graphics.drawable.Drawable;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.SampleJobService;
import com.google.android.media.tv.companionlibrary.setup.ChannelSetupStepFragment;

/**
 * Fragment which shows a sample UI for registering channels and setting up SampleJobService to
 * provide program information in the background.
 */
public class RichSetupFragment extends ChannelSetupStepFragment<SampleJobService> {

    private String mInputId = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
    }

    @Override
    public Class<SampleJobService> getEpgSyncJobServiceClass() {
        return SampleJobService.class;
    }

    @Override
    public Guidance onCreateGuidance(@NonNull Bundle savedInstanceState) {
        String title = getString(R.string.rich_input_label);
        String description = getString(R.string.tif_channel_setup_description);
        Drawable icon = getActivity().getDrawable(R.drawable.android_48dp);
        return new Guidance(title, description, null, icon);
    }

    public String getInputId() {
        return mInputId;
    }
}
