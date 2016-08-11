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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v17.leanback.app.BackgroundManager;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.AbstractDetailsDescriptionPresenter;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.example.android.sampletvinput.R;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import com.example.android.sampletvinput.sync.SampleJobService;
import com.google.android.media.tv.companionlibrary.xmltv.XmlTvParser;

/**
 * Fragment which shows a sample UI for registering channels and setting up SampleJobService to
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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate SetupFragment");
        super.onCreate(savedInstanceState);

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mSyncStatusChangedReceiver,
                new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));

        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        new SetupRowTask().execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mSyncStatusChangedReceiver);
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

            mAddChannelAction = new Action(ACTION_ADD_CHANNELS,
                    getResources().getString(R.string.rich_setup_add_channel));
            Action cancelAction = new Action(ACTION_CANCEL,
                    getResources().getString(R.string.rich_setup_cancel));
            mInProgressAction = new Action(ACTION_IN_PROGRESS,
                    getResources().getString(R.string.rich_setup_in_progress));

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
            dorPresenter.setBackgroundColor(ContextCompat.getColor(getActivity(),
                    R.color.detail_background));
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
            return BitmapFactory.decodeResource(getResources(), R.drawable.your_company);
        }
    }

    private void onError(int errorResId) {
        Toast.makeText(getActivity(), errorResId, Toast.LENGTH_SHORT).show();
        getActivity().finish();
    }

    private void setupChannels(String inputId) {
        EpgSyncJobService.cancelAllSyncRequests(getActivity());
        EpgSyncJobService.requestImmediateSync(getActivity(), inputId,
                new ComponentName(getActivity(), SampleJobService.class));

        // Set up SharedPreference to share inputId. If there is not periodic sync job after reboot,
        // RichBootReceiver can use the shared inputId to set up periodic sync job.
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(
                EpgSyncJobService.PREFERENCE_EPG_SYNC, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(EpgSyncJobService.BUNDLE_KEY_INPUT_ID, inputId);
        editor.apply();
    }

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
                    if (syncStatusChangedInputId != null
                            && syncStatusChangedInputId.equals(mInputId)) {
                        String syncStatus = intent.getStringExtra(EpgSyncJobService.SYNC_STATUS);
                        if (syncStatus.equals(EpgSyncJobService.SYNC_STARTED)) {
                            DetailsOverviewRow detailRow = (DetailsOverviewRow) mAdapter.get(0);
                            detailRow.removeAction(mAddChannelAction);
                            if (detailRow.getActionForKeyCode(ACTION_IN_PROGRESS) == null) {
                                detailRow.addAction(0, mInProgressAction);
                            }
                            mAdapter.notifyArrayItemRangeChanged(0, 1);
                        } else if (syncStatus.equals(EpgSyncJobService.SYNC_FINISHED)) {
                            EpgSyncJobService.setUpPeriodicSync(getActivity(), mInputId,
                                    new ComponentName(getActivity(), SampleJobService.class));
                            getActivity().setResult(Activity.RESULT_OK);
                            getActivity().finish();
                            mFinished = true;
                        }
                    }
                }
            });
        }
    };

    private class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {
        @Override
        protected void onBindDescription(ViewHolder viewHolder, Object item) {
            viewHolder.getTitle().setText(R.string.rich_input_label);
            viewHolder.getSubtitle().setText(R.string.company_name);
        }
    }
}
