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
package com.google.android.media.tv.companionlibrary.test;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.v4.content.LocalBroadcastManager;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.media.tv.companionlibrary.setup.ChannelSetupFragment;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;

public class ChannelSetupFragmentTest extends ActivityInstrumentationTestCase2<TestActivity> {
    private static final String TAG = ChannelSetupFragmentTest.class.getSimpleName();

    private ChannelSetupFragment mChannelSetupFragment;
    private CountDownLatch mCountDownLatch;

    private final BroadcastReceiver mSyncStatusChangedReceiver = new BroadcastReceiver() {
        private boolean mFinished;
        @Override
        public void onReceive(Context context, final Intent intent) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mFinished) {
                        return;
                    }
                    String syncStatusChangedInputId = intent.getStringExtra(
                            EpgSyncJobService.BUNDLE_KEY_INPUT_ID);
                    Assert.assertNotNull(syncStatusChangedInputId);
                    Assert.assertNotNull("CountDownLatch is null, reset the test", mCountDownLatch);
                    String syncStatus = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
                    Log.d(TAG, "Received sync status " + syncStatus + " for input id "
                            + syncStatusChangedInputId);
                    if (syncStatusChangedInputId.equals(TestTvInputService.INPUT_ID)) {
                        if (syncStatus.equals(EpgSyncJobService.SYNC_STARTED)) {
                            mCountDownLatch.countDown();
                        } else if (syncStatus.equals(EpgSyncJobService.SYNC_SCANNED)) {
                            mCountDownLatch.countDown();
                            Assert.assertTrue("Scan property CHANNELS_SCANNED doesn't exist.",
                                    intent.hasExtra(EpgSyncJobService.BUNDLE_KEY_CHANNELS_SCANNED));
                            Assert.assertTrue("Scan property CHANNEL_COUNT doesn't exist.",
                                    intent.hasExtra(EpgSyncJobService.BUNDLE_KEY_CHANNEL_COUNT));
                        } else if (syncStatus.equals(EpgSyncJobService.SYNC_FINISHED)) {
                            mFinished = true;
                            mCountDownLatch.countDown();
                        } else if (syncStatus.equals(EpgSyncJobService.SYNC_ERROR)) {
                            Assert.fail("Sync error occurred: " + intent.getIntExtra(
                                    EpgSyncJobService.BUNDLE_KEY_ERROR_REASON, 0));
                        }
                    }
                }
            });
        }
    };

    public ChannelSetupFragmentTest() {
        super(TestActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        getActivity();
        mChannelSetupFragment = new ChannelSetupFragmentImpl();
        getActivity().getFragmentManager().beginTransaction()
                .add(com.google.android.media.tv.companionlibrary.test.R.id.viewgroup,
                        mChannelSetupFragment, "Setup")
                .commitAllowingStateLoss();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mSyncStatusChangedReceiver,
                new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
        TestJobService.mContext = getActivity();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        // Delete content
        getActivity().getContentResolver().delete(TvContract.buildChannelsUriForInput(
                TestTvInputService.INPUT_ID), null, null);
        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mSyncStatusChangedReceiver);
    }

    public void testSync() throws InterruptedException {
        mCountDownLatch = new CountDownLatch(4);
        mCountDownLatch.await();
        try {
            // Add some delay so that we are able to see the UI change
            CountDownLatch delayLatch = new CountDownLatch(1);
            Assert.assertFalse(delayLatch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }
    }

    public static class ChannelSetupFragmentImpl extends ChannelSetupFragment {
        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View fragmentView = super.onCreateView(inflater, container, savedInstanceState);
            setTitle("Channel Setup UI Test");
            setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            setDescription("Hello world");
            setBadge(getResources().getDrawable(
                    com.google.android.media.tv.companionlibrary.test.R.drawable.usb_antenna));
            setButtonText("Start scanning");
            setChannelListVisibility(true);
            return fragmentView;
        }

        @Override
        public void onScanStarted() {
            setButtonText("Stop!");
            EpgSyncJobService.cancelAllSyncRequests(getActivity());
            EpgSyncJobService.requestImmediateSync(getActivity(),
                    TestTvInputService.INPUT_ID, new ComponentName(getContext(),
                            TestJobService.class));
        }

        @Override
        public String getInputId() {
            return TestTvInputService.INPUT_ID;
        }

        @Override
        public void onScanFinished() {
            setButtonText("Done!");
        }

        @Override
        public void onScannedChannel(CharSequence displayName, CharSequence displayNumber) {
            super.onScannedChannel(displayName, displayNumber);
            Assert.assertNotNull(displayName);
            Assert.assertNotNull(displayNumber);
        }

        @Override
        public void onScanStepCompleted(int completedStep, int totalSteps) {
            super.onScanStepCompleted(completedStep, totalSteps);
            super.onScanStepCompleted(completedStep, totalSteps);
            Assert.assertTrue("Channels scanned cannot be less than or equal to 0.",
                completedStep > 0);
            Assert.assertEquals(2, totalSteps);
            setDescription(completedStep + " channels scanned.");
        }
    }
}
