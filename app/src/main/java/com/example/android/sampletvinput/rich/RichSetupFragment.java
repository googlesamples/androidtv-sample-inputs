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
import android.content.Context;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
import android.util.DisplayMetrics;
import android.util.Log;

import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.TvContractUtils;
import com.example.android.sampletvinput.rich.RichTvInputService.ChannelInfo;
import com.example.android.sampletvinput.rich.RichTvInputService.TvInput;
import com.example.android.sampletvinput.syncadapter.DummyAccountService;
import com.example.android.sampletvinput.syncadapter.SyncUtils;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.List;

/**
 * Fragment which shows a sample UI for registering channels and setting up SyncAdapter to
 * provide program information in the background.
 */
public class RichSetupFragment extends DetailsFragment {
    private static final String TAG = "SetupFragment";

    private static final int ACTION_ADD_CHANNELS = 1;
    private static final int ACTION_CANCEL = 2;
    private static final int ACTION_IN_PROGRESS = 3;

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private Drawable mDefaultBackground;
    private Target mBackgroundTarget;
    private DisplayMetrics mMetrics;
    private DetailsOverviewRowPresenter mDorPresenter;

    private List<ChannelInfo> mChannels = null;
    private Class mServiceClass = null;
    private TvInput mTvInput = null;
    private String mInputId = null;

    private Action mAddChannelAction;
    private Action mCancelAction;
    private Action mInProgressAction;
    private ArrayObjectAdapter mAdapter;
    private Object mSyncObserverHandle;
    private boolean mSyncRequested;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate SetupFragment");
        super.onCreate(savedInstanceState);

        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        getChannels();
        mDorPresenter = new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());

        BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
        backgroundManager.attach(getActivity().getWindow());
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);

        mDefaultBackground = getResources().getDrawable(R.drawable.default_background);

        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        Log.d(TAG, "setup mInputId: " + mInputId);
        new SetupRowTask().execute(mChannels);
        mDorPresenter.setSharedElementEnterTransition(getActivity(), "SetUp");

        mAddChannelAction = new Action(ACTION_ADD_CHANNELS, getResources().getString(
                R.string.rich_setup_add_channel));
        mCancelAction = new Action(ACTION_CANCEL, getResources().getString(
                R.string.rich_setup_cancel));
        mInProgressAction = new Action(ACTION_IN_PROGRESS, getResources().getString(
                R.string.rich_setup_in_progress));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSyncObserverHandle != null) {
            ContentResolver.removeStatusChangeListener(mSyncObserverHandle);
            mSyncObserverHandle = null;
        }
    }

    protected void updateBackground(String uri) {
        Picasso.with(getActivity())
                .load(uri)
                .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                .error(mDefaultBackground)
                .into(mBackgroundTarget);
    }

    private class SetupRowTask extends AsyncTask<List<ChannelInfo>, Integer, DetailsOverviewRow> {

        private volatile boolean running = true;

        @Override
        protected DetailsOverviewRow doInBackground(List<ChannelInfo>...
                channels) {
            while (running) {
                Log.d(TAG, "doInBackground: " + mInputId);
                DetailsOverviewRow row = new DetailsOverviewRow(mTvInput);
                try {
                    Bitmap poster = Picasso.with(getActivity())
                            .load(mTvInput.logoBackgroundUrl)
                            .resize(convertDpToPixel(getActivity()
                                            .getApplicationContext(), DETAIL_THUMB_WIDTH),
                                    convertDpToPixel(getActivity()
                                            .getApplicationContext(), DETAIL_THUMB_HEIGHT))
                            .centerCrop()
                            .get();
                    row.setImageBitmap(getActivity(), poster);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }

                row.addAction(mAddChannelAction);
                row.addAction(mCancelAction);
                return row;
            }
            return null;
        }

        @Override
        protected void onPostExecute(final DetailsOverviewRow detailRow) {
            if (!running) {
                return;
            }
            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            // set detail background and style
            mDorPresenter.setBackgroundColor(getResources().getColor(R.color.detail_background));
            mDorPresenter.setStyleLarge(true);

            updateBackground(mTvInput.logoBackgroundUrl);

            mDorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
                @Override
                public void onActionClicked(Action action) {
                    if (action.getId() == ACTION_ADD_CHANNELS) {
                        setupChannels(mInputId);
                    } else if (action.getId() == ACTION_CANCEL) {
                        getActivity().finish();
                    }
                }
            });

            presenterSelector.addClassPresenter(DetailsOverviewRow.class, mDorPresenter);
            presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
            mAdapter = new ArrayObjectAdapter(presenterSelector);
            mAdapter.add(detailRow);

            setAdapter(mAdapter);
        }

        @Override
        protected void onCancelled() {
            running = false;
        }
    }

    private void getChannels() {
        mChannels = RichFeedUtil.createRichChannelsStatic(getActivity());
        mTvInput = RichFeedUtil.getTvInput();
        mServiceClass = RichTvInputService.class;
    }

    private void setupChannels(String inputId) {
        if (mChannels == null || mServiceClass == null) {
            return;
        }

        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {TvContract.Channels._ID};

        Cursor cursor = null;
        ContentResolver resolver = getActivity().getContentResolver();
        try {
            cursor = resolver.query(uri, projection, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                resolver.delete(uri, null, null);
            }
            // Insert mChannels into the database. This needs to be done only for the
            // first time.
            TvContractUtils.populateChannels(getActivity(), inputId, mChannels);

            SyncUtils.setUpPeriodicSync(getActivity(), inputId);
            SyncUtils.requestSync(inputId);
            mSyncRequested = true;
            // Watch for sync state changes
            if (mSyncObserverHandle == null) {
                final int mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING |
                        ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE;
                mSyncObserverHandle = ContentResolver.addStatusChangeListener(mask,
                        mSyncStatusObserver);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private int convertDpToPixel(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
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
