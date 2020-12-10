/*
 * Copyright 2015 The Android Open Source Project.
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
import android.os.Bundle;
import androidx.leanback.app.GuidedStepFragment;

/** The setup activity for demonstrating {@link RichTvInputService}. */
public class RichTvInputSetupActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (null == savedInstanceState) {
            GuidedStepFragment.addAsRoot(this, new FirstStepFragment(), android.R.id.content);
        }
    }
}
