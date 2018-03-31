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

import static com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService.ERROR_NO_CHANNELS;

import android.app.Activity;
import android.net.Uri;
import android.support.v17.leanback.widget.GuidedAction;
import android.util.Pair;
import android.widget.ProgressBar;
import com.google.android.media.tv.companionlibrary.BuildConfig;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.FragmentController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

/**
 * Tests for {@link ChannelSetupStepFragment}
 *
 * Make sure to go to 'Edit Configurations' and set the 'Working Directory' to '$MODULE_DIR$'
 */
@RunWith(RobolectricTestRunner.class)
@Config(
    constants = BuildConfig.class,
    sdk = 23,
    manifest = "../../../../../src/test/java/com/google/android/media/tv/companionlibrary/setup/AndroidManifest.xml"
)
public class ChannelSetupStepFragmentTest {

    private TestChannelSetupStepFragment fragment;
    private ChannelSetupStylist guidanceStylist;

    @Before
    public void before() {
        fragment = new TestChannelSetupStepFragment();
        guidanceStylist = fragment.getGuidanceStylist();
        Robolectric.setupActivity(Activity.class)
                .getFragmentManager()
                .beginTransaction()
                .add(fragment, null)
                .commit();
        FragmentController<TestChannelSetupStepFragment> controller =
                Robolectric.buildFragment(TestChannelSetupStepFragment.class);
        controller.resume().visible();
    }

    @Test
    public void onResume() {
        Assert.assertEquals(
                "progressBar.isIndeterminate()",
                true,
                guidanceStylist.getProgressBar().isIndeterminate());
        Assert.assertEquals(
                "Channel list count", 0, guidanceStylist.getChannelAdapter().getCount());
        List<GuidedAction> actions = fragment.getActions();
        Assert.assertEquals("action count", 1, actions.size());
        Assert.assertEquals("action id", GuidedAction.ACTION_ID_CANCEL, actions.get(0).getId());
    }

    @Test
    public void onScanStepCompleted() {
        ProgressBar progressBar = guidanceStylist.getProgressBar();
        fragment.onScanStepCompleted(1, 10);
        Assert.assertEquals("progressBar.getProgress()", 1, progressBar.getProgress());
        Assert.assertEquals("progressBar.getMax()", 10, progressBar.getMax());
    }

    @Test
    public void onScannedChannel() {
        fragment.onScannedChannel("foo", "12.3");
        Assert.assertEquals(
                "Channel list count", 1, guidanceStylist.getChannelAdapter().getCount());
        Assert.assertEquals(
                Pair.create("foo", "12.3"), guidanceStylist.getChannelAdapter().getItem(0));
    }

    @Test
    public void onScanError_noChannels() {
        fragment.onScanError(ERROR_NO_CHANNELS);
        ShadowLooper.idleMainLooper();
        Assert.assertEquals("No channels found", ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void onScanFinished() {
        fragment.onScanFinished();
        List<GuidedAction> actions = fragment.getActions();
        Assert.assertEquals("action count", 1, actions.size());
        Assert.assertEquals("action id", GuidedAction.ACTION_ID_FINISH, actions.get(0).getId());
    }

    /** Test implementation. */
    public static class TestChannelSetupStepFragment
            extends ChannelSetupStepFragment<FakeJobService> {

        @Override
        public Class<FakeJobService> getEpgSyncJobServiceClass() {
            return FakeJobService.class;
        }
    }

    private static class FakeJobService extends EpgSyncJobService {

        @Override
        public List<Channel> getChannels() throws EpgSyncException {
            return null;
        }

        @Override
        public List<Program> getProgramsForChannel(
                Uri channelUri, Channel channel, long startMs, long endMs) throws EpgSyncException {
            return null;
        }
    }
}
