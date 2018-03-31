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

/** Tests for {@link SyncStatusBroadcastReceiver}. */
import android.content.Intent;
import com.google.android.media.tv.companionlibrary.BuildConfig;
import com.google.android.media.tv.companionlibrary.sync.SyncStatusBroadcastReceiver.SyncListener;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests that channels can be created using the Builder pattern and correctly obtain values from
 * them
 */
@RunWith(RobolectricTestRunner.class)
@Config(
    constants = BuildConfig.class,
    sdk = 23,
    manifest =
            "src/main/AndroidManifest.xml"
)
public class SyncStatusBroadcastReceiverTest {

    private static final String TEST_INPUT = "test_input";
    private TestSyncListener mSyncListener = new TestSyncListener();
    SyncStatusBroadcastReceiver mReceiver =
            new SyncStatusBroadcastReceiver(TEST_INPUT, mSyncListener);

    @Test
    public void receiveSyncScannedIntent() {
        Intent intent =
                EpgSyncJobService.createSyncScannedIntent(TEST_INPUT, 5, 10, "display", "1.1");
        mReceiver.onReceive(null, intent);
        Assert.assertEquals("finished", false, mSyncListener.mFinished);
        Assert.assertEquals("scanned", 5, mSyncListener.mChannelsScanned);
        Assert.assertEquals("count", 10, mSyncListener.mChannelCount);
        Assert.assertEquals("displayName", "display", mSyncListener.mDisplayName);
        Assert.assertEquals("displayNumber", "1.1", mSyncListener.mDisplayNumber);
    }

    @Test
    public void receiveSyncFinishedIntent() {
        Intent intent = EpgSyncJobService.createSyncFinishedIntent(TEST_INPUT);
        mReceiver.onReceive(null, intent);
        Assert.assertEquals("finished", true, mSyncListener.mFinished);
    }

    @Test
    public void receiveSyncErrorIntent() {
        Intent intent = EpgSyncJobService.createSyncErrorIntent(TEST_INPUT, 42);
        mReceiver.onReceive(null, intent);
        Assert.assertEquals("finished", false, mSyncListener.mFinished);
        Assert.assertEquals("errorCode", 42, mSyncListener.mErrorCode);
    }

    @Test
    public void receiveSyncFinishedIntent_noOtherChanges() {
        Intent intent = EpgSyncJobService.createSyncFinishedIntent(TEST_INPUT);
        mReceiver.onReceive(null, intent);
        Assert.assertEquals("finished", true, mSyncListener.mFinished);
        Intent after =
                EpgSyncJobService.createSyncScannedIntent(TEST_INPUT, 5, 10, "display", "1.1");
        mReceiver.onReceive(null, after);
        Assert.assertEquals("finished", true, mSyncListener.mFinished);
        Assert.assertEquals("scanned", 0, mSyncListener.mChannelsScanned);
        Assert.assertEquals("count", 0, mSyncListener.mChannelCount);
        Assert.assertEquals("displayName", null, mSyncListener.mDisplayName);
        Assert.assertEquals("displayNumber", null, mSyncListener.mDisplayNumber);
    }

    @Test
    public void receiveSyncFinishedIntent_otherInput() {
        Intent intent = EpgSyncJobService.createSyncFinishedIntent("other");
        mReceiver.onReceive(null, intent);
        Assert.assertEquals("finished", false, mSyncListener.mFinished);
    }

    @Test
    public void receiveSyncFinishedIntent_nullInput() {
        Intent intent = new Intent(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED);
        // NOTE BUNDLE_KEY_INPUT_ID is not set
        intent.putExtra(EpgSyncJobService.SYNC_STATUS, EpgSyncJobService.SYNC_FINISHED);
        mReceiver.onReceive(null, intent);
        Assert.assertEquals("finished", false, mSyncListener.mFinished);
    }

    private static final class TestSyncListener implements SyncListener {

        private int mChannelsScanned;
        private int mChannelCount;
        private CharSequence mDisplayName;
        private CharSequence mDisplayNumber;
        private boolean mFinished = false;
        private int mErrorCode = 0;

        @Override
        public void onScanStepCompleted(int completedStep, int totalSteps) {
            this.mChannelsScanned = completedStep;
            this.mChannelCount = totalSteps;
        }

        @Override
        public void onScannedChannel(CharSequence displayName, CharSequence displayNumber) {
            mDisplayName = displayName;
            mDisplayNumber = displayNumber;
        }

        @Override
        public void onScanFinished() {
            mFinished = true;
        }

        @Override
        public void onScanError(int errorCode) {
            mErrorCode = errorCode;
        }
    }
}
