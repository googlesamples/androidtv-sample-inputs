/*
 * Copyright 2015 The Android Open Source Project
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

package com.example.android.sampletvinput.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.util.Log;

/**
 * Static helper methods for working with the SyncAdapter framework.
 */
public class SyncUtils {
    private static final String TAG = "SyncUtils";
    private static final String CONTENT_AUTHORITY = TvContract.AUTHORITY;
    public static final String ACCOUNT_TYPE = "com.example.android.sampletvinput.account";

    public static void setUpPeriodicSync(Context context, String inputId) {
        Account account = DummyAccountService.getAccount(ACCOUNT_TYPE);
        AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        if (!accountManager.addAccountExplicitly(account, null, null)) {
            Log.e(TAG, "Account already exists.");
        }
        ContentResolver.setIsSyncable(account, CONTENT_AUTHORITY, 1);
        ContentResolver.setSyncAutomatically(account, CONTENT_AUTHORITY, true);
        Bundle bundle = new Bundle();
        bundle.putString(SyncAdapter.BUNDLE_KEY_INPUT_ID, inputId);
        ContentResolver.addPeriodicSync(account, CONTENT_AUTHORITY, bundle,
                SyncAdapter.SYNC_FREQUENCY_SEC);
    }

    public static void requestSync(String inputId) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putString(SyncAdapter.BUNDLE_KEY_INPUT_ID, inputId);
        ContentResolver.requestSync(DummyAccountService.getAccount(ACCOUNT_TYPE), CONTENT_AUTHORITY,
                bundle);
    }
}
