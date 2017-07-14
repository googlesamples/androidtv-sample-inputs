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

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.tv.TvContract;
import android.support.test.InstrumentationRegistry;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.PermissionChecker;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.util.LongSparseArray;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.ModelUtils;
import com.google.android.media.tv.companionlibrary.model.Program;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests the periodic syncing functionality of the EpgSyncJobService to ensure the reliability of
 * the JobScheduler.
 */
public class PeriodicEpgSyncJobServiceTest extends ActivityInstrumentationTestCase2<TestActivity> {
    private final static String TAG = PeriodicEpgSyncJobServiceTest.class.getSimpleName();

    private final static int NUMBER_OF_SYNCS = 2;
    private final static int SYNC_PERIOD = 1000 * 60 * 15; // JobInfo min interval is 15m
    private final static int SYNC_DURATION = 1000 * 60 * 60;

    private CountDownLatch mSyncStatusLatch;
    private final BroadcastReceiver mSyncStatusChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, final Intent intent) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String syncStatusChangedInputId = intent.getStringExtra(
                            EpgSyncJobService.BUNDLE_KEY_INPUT_ID);
                    if (syncStatusChangedInputId != null) {
                        String syncStatus = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
                        Log.d(TAG, "Sync status changed: " + syncStatus);
                        if (syncStatus.equals(EpgSyncJobService.SYNC_STARTED)) {
                            mSyncStatusLatch.countDown();
                        }
                    } else {
                        Assert.fail("syncStatusChangedInputId is null.");
                    }
                }
            });
        }
    };
    private String mInputId =
            "com.google.android.media.tv.companionlibrary.test/.TestTvInputService";
    private List<Program> mProgramList;
    private List<Channel> mChannelList;
    private TestJobService mSampleJobService;
    private LongSparseArray<Channel> mChannelMap;

    public PeriodicEpgSyncJobServiceTest() {
        super(TestActivity.class);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        getActivity();
        // Delete all channels
        getActivity().getContentResolver().delete(TvContract.buildChannelsUriForInput(mInputId),
                null, null);

        mSampleJobService = new TestJobService();
        mSampleJobService.mContext = getActivity();
        mChannelList = mSampleJobService.getChannels();
        ModelUtils.updateChannels(getActivity(), mInputId, mChannelList, null);
        mChannelMap = ModelUtils.buildChannelMap(getActivity().getContentResolver(), mInputId);
        assertEquals(2, mChannelMap.size());
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mSyncStatusChangedReceiver);

        // Delete these programs
        getActivity().getContentResolver().delete(TvContract.buildChannelsUriForInput(mInputId),
                null, null);
    }

    @Test
    public void testPeriodicSync() throws InterruptedException {
        // Do many syncs in a small period of time and make sure they all start
        // Tests that a sync can be requested
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mSyncStatusChangedReceiver,
                new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));
        // The CountDownLatch decrements every time sync starts
        mSyncStatusLatch = new CountDownLatch(NUMBER_OF_SYNCS);
        EpgSyncJobService.cancelAllSyncRequests(getActivity());

        // Make sure that we can set up a sync that can persist after boot
        assertEquals(getActivity().checkSelfPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED),
                PermissionChecker.PERMISSION_GRANTED);
        EpgSyncJobService.setUpPeriodicSync(getActivity(), mInputId,
                new ComponentName(getActivity(), TestJobService.class),
                SYNC_PERIOD, SYNC_DURATION); // 15m is the lowest period
        // Wait for every sync to start, with some leeway.
        long timeoutSeconds = SYNC_PERIOD * (NUMBER_OF_SYNCS + 1);
        boolean syncStatusLatchComplete = mSyncStatusLatch.await(timeoutSeconds,
                TimeUnit.MILLISECONDS);
        if (!syncStatusLatchComplete) {
            Assert.fail("Syncing did not complete. The remaining count is " +
                    mSyncStatusLatch.getCount() + " after " + timeoutSeconds + " seconds.");
        }
    }
}
