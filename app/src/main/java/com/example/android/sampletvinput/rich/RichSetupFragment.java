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

package com.example.android.sampletvinput.rich;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.SyncStatusObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.util.Log;
import android.widget.Toast;

import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.TvContractUtils;
import com.example.android.sampletvinput.syncadapter.DummyAccountService;
import com.example.android.sampletvinput.syncadapter.SyncUtils;
import com.example.android.sampletvinput.xmltv.XmlTvParser;

import java.io.IOException;
import java.io.InputStream;

/**
 * Fragment which shows a sample UI for registering channels and setting up SyncAdapter to
 * provide program information in the background.
 */
public class RichSetupFragment extends DetailsFragment {
    private static final String TAG = "SetupFragment";

    private static final int ACTION_ADD_CHANNELS = 1;
    private static final int ACTION_CANCEL = 2;
    private static final int ACTION_IN_PROGRESS = 3;

    private XmlTvParser.TvListing mTvListing = null;
    private String mInputId = null;

    private Action mAddChannelAction;
    private Action mInProgressAction;
    private ArrayObjectAdapter mAdapter;
    private Object mSyncObserverHandle;
    private boolean mSyncRequested;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate SetupFragment");
        super.onCreate(savedInstanceState);

        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        new SetupRowTask().execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        }
    }

    private class SetupRowTask extends AsyncTask<Uri, String, Boolean> {
        Bitmap mPoster;

        @Override
        protected Boolean doInBackground(Uri... params) {
            mTvListing = RichFeedUtil.getRichTvListings(getActivity());
            mPoster = fetchPoster();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                initUIs(mPoster);
            } else {
                onError(R.string.feed_error_message);
            }
        }

        private void initUIs(Bitmap bitmap) {
            DetailsOverviewRowPresenter dorPresenter =
                    new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());
            dorPresenter.setSharedElementEnterTransition(getActivity(), "SetUpFragment");

            mAddChannelAction = new Action(ACTION_ADD_CHANNELS, getResources().getString(
                    R.string.rich_setup_add_channel));
            Action cancelAction = new Action(ACTION_CANCEL,
                    getResources().getString(R.string.rich_setup_cancel));
            mInProgressAction = new Action(ACTION_IN_PROGRESS, getResources().getString(
                    R.string.rich_setup_in_progress));

            DetailsOverviewRow row = new DetailsOverviewRow(mTvListing);
            if (bitmap != null) {
                int length = Math.min(bitmap.getWidth(), bitmap.getHeight());
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap,
                        (bitmap.getWidth() - length) / 2,
                        (bitmap.getHeight() - length) / 2,
                        length, length);
                row.setImageBitmap(getActivity(), croppedBitmap);
            }
            row.addAction(mAddChannelAction);
            row.addAction(cancelAction);

            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            // set detail background and style
            dorPresenter.setBackgroundColor(getResources().getColor(R.color.detail_background));
            dorPresenter.setStyleLarge(true);

            dorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
                @Override
                public void onActionClicked(Action action) {
                    if (action.getId() == ACTION_ADD_CHANNELS) {
                        setupChannels(mInputId);
                    } else if (action.getId() == ACTION_CANCEL) {
                        getActivity().finish();
                    }
                }
            });

            presenterSelector.addClassPresenter(DetailsOverviewRow.class, dorPresenter);
            presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
            mAdapter = new ArrayObjectAdapter(presenterSelector);
            mAdapter.add(row);

            setAdapter(mAdapter);

            BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
            backgroundManager.attach(getActivity().getWindow());
            if (bitmap != null) {
                backgroundManager.setBitmap(bitmap);
            } else {
                backgroundManager.setDrawable(
                        getActivity().getDrawable(R.drawable.default_background));
            }
        }

        private Bitmap fetchPoster() {
            Uri uri = Uri.parse(getString(R.string.rich_setup_background_url)).normalizeScheme();
            try (InputStream inputStream = RichFeedUtil.getInputStream(getActivity(), uri)) {
                return BitmapFactory.decodeStream(inputStream);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                return null;
            }
        }
    }

    private void onError(int errorResId) {
        Toast.makeText(getActivity(), errorResId, Toast.LENGTH_SHORT).show();
        getActivity().finish();
    }

    private void setupChannels(String inputId) {
        if (mTvListing == null) {
            onError(R.string.feed_error_message);
            return;
        }
        TvContractUtils.updateChannels(getActivity(), inputId, mTvListing.channels);
        SyncUtils.setUpPeriodicSync(getActivity(), inputId);
        SyncUtils.requestSync(inputId, true);
        mSyncRequested = true;
        // Watch for sync state changes
        if (mSyncObserverHandle == null) {
            final int mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING |
                    ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;
            mSyncObserverHandle = ContentResolver.addStatusChangeListener(mask,
                    mSyncStatusObserver);
        }
    }

    private final SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        private boolean mSyncServiceStarted;
        private boolean mFinished;

        @Override
        public void onStatusChanged(int which) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mFinished) {
                        return;
                    }
                    Account account = DummyAccountService.getAccount(SyncUtils.ACCOUNT_TYPE);
                    boolean syncActive = ContentResolver.isSyncActive(account,
                            TvContract.AUTHORITY);
                    boolean syncPending = ContentResolver.isSyncPending(account,
                            TvContract.AUTHORITY);
                    boolean syncServiceInProgress = syncActive || syncPending;
                    if (mSyncRequested && mSyncServiceStarted && !syncServiceInProgress) {
                        // Only current programs are registered at this point. Request a full sync.
                        SyncUtils.requestSync(mInputId, false);

                        getActivity().setResult(Activity.RESULT_OK);
                        getActivity().finish();
                        mFinished = true;
                    }
                    if (!mSyncServiceStarted && syncServiceInProgress) {
                        mSyncServiceStarted = syncServiceInProgress;
                        DetailsOverviewRow detailRow = (DetailsOverviewRow) mAdapter.get(0);
                        detailRow.removeAction(mAddChannelAction);
                        detailRow.addAction(0, mInProgressAction);
                        mAdapter.notifyArrayItemRangeChanged(0, 1);
                    }
                }
            });
        }
    };
}
