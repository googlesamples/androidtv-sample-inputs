/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.example.android.sampletvinput;

import android.app.Activity;
import android.content.Context;
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
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.util.DisplayMetrics;
import android.util.Log;

import com.example.android.sampletvinput.BaseTvInputService.TvInput;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.IOException;
import java.util.List;

/*
 * SetupFragment extends DetailsFragment. It shows a sample UI for setup TV input channels.
 */
public class SetupFragment extends DetailsFragment {
    private static final String TAG = "SetupFragment";

    private static final int ACTION_ADD_CHANNELS = 1;
    private static final int ACTION_CANCEL = 2;

    private static final int DETAIL_THUMB_WIDTH = 274;
    private static final int DETAIL_THUMB_HEIGHT = 274;

    private Drawable mDefaultBackground;
    private Target mBackgroundTarget;
    private DisplayMetrics mMetrics;
    private DetailsOverviewRowPresenter mDorPresenter;

    private List<BaseTvInputService.ChannelInfo> mChannels = null;
    private Class mServiceClass = null;
    private TvInput mTvInput = null;

    private String mInputId = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate SetupFragment");
        super.onCreate(savedInstanceState);

        mInputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        getChannels(mInputId);
        mDorPresenter =
                new DetailsOverviewRowPresenter(new DetailsDescriptionPresenter());

        BackgroundManager backgroundManager = BackgroundManager.getInstance(getActivity());
        backgroundManager.attach(getActivity().getWindow());
        mBackgroundTarget = new PicassoBackgroundManagerTarget(backgroundManager);

        mDefaultBackground = getResources().getDrawable(R.drawable.default_background);

        mMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        Log.d(TAG, "setup mInputId: " + mInputId);
        new SetupRowTask().execute(mChannels);
        mDorPresenter.setSharedElementEnterTransition(getActivity(), "SetUp");
        setOnItemViewClickedListener(new ItemViewClickedListener());
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    protected void updateBackground(String uri) {
        Picasso.with(getActivity())
                .load(uri)
                .resize(mMetrics.widthPixels, mMetrics.heightPixels)
                .error(mDefaultBackground)
                .into(mBackgroundTarget);
    }

    private class SetupRowTask extends AsyncTask<List<BaseTvInputService.ChannelInfo>,
            Integer, DetailsOverviewRow> {

        private volatile boolean running = true;

        @Override
        protected DetailsOverviewRow doInBackground(List<BaseTvInputService.ChannelInfo>...
                                                                    channels) {

            while (running) {
                Log.d(TAG, "doInBackground: " + mInputId);
                DetailsOverviewRow row = new DetailsOverviewRow(mTvInput);
                try {
                    Bitmap poster = Picasso.with(getActivity())
                            .load(mTvInput.getLogoBackgroundUrl())
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

                row.addAction(new Action(ACTION_ADD_CHANNELS, getResources().getString(
                        R.string.add_channel_1), getResources().getString(R.string.add_channel_2)));
                row.addAction(new Action(ACTION_CANCEL, getResources().getString(R.string.cancel_1),
                        getResources().getString(R.string.cancel_2)));
                return row;
            }
            return null;
        }

        @Override
        protected void onPostExecute(DetailsOverviewRow detailRow) {
            if (!running) {
                return;
            }
            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            // set detail background and style
            mDorPresenter.setBackgroundColor(getResources().getColor(R.color.detail_background));
            mDorPresenter.setStyleLarge(true);

            updateBackground(mTvInput.getLogoBackgroundUrl());

            mDorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
                @Override
                public void onActionClicked(Action action) {
                    if (action.getId() == ACTION_ADD_CHANNELS) {
                        setupChannels(mInputId);
                        getActivity().setResult(Activity.RESULT_OK);
                    }
                    getActivity().finish();
                }
            });

            presenterSelector.addClassPresenter(DetailsOverviewRow.class, mDorPresenter);
            presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenterSelector);
            adapter.add(detailRow);

            setAdapter(adapter);
        }

        @Override
        protected void onCancelled() {
            running = false;
        }
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

        }
    }

    private void getChannels(String inputId) {

        String serviceName = Utils.getServiceNameFromInputId(getActivity(), inputId);

        if (serviceName.equals(OnlineTvInputService.class.getName())) {
            mChannels = OnlineTvInputService.createOnlineChannelsStatic(getActivity());
            mTvInput = OnlineTvInputService.getTvInput();
            mServiceClass = OnlineTvInputService.class;
        }
    }

    private void setupChannels(String inputId) {
        if (mChannels == null || mServiceClass == null) {
            return;
        }

        Uri uri = TvContract.buildChannelsUriForInput(inputId);
        String[] projection = {TvContract.Channels._ID};

        Cursor cursor = null;
        try {
            cursor = getActivity().getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                return;
            }
            if (cursor != null) {
                cursor.close();
            }
            // Insert mChannels into the database. This needs to be done only for the
            // first time.
            ChannelUtils.populateChannels(getActivity(), inputId, mChannels);
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
}
