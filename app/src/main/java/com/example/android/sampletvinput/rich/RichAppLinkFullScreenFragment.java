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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
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
import android.util.Log;

import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.TvContractUtils;
import com.example.android.sampletvinput.data.Program;
import com.example.android.sampletvinput.xmltv.XmlTvParser;

import java.io.IOException;
import java.io.InputStream;

/**
 * Fragment which shows a simple full screen UI for promoting channels.
 */
public class RichAppLinkFullScreenFragment extends DetailsFragment {
    private static final String TAG = "FullScreenFragment";

    // Change the app-link activity from RichAppLinkFullScreenActivity
    // to RichAppLinkSidePanelActivity.
    private static final int ACTION_CHANGE_ACTIVITY = 1;
    private static final int ACTION_PROMOTE_CHANNEL = 2;
    private static final int ACTION_CLOSE = 3;

    private String mInputId;
    private XmlTvParser.TvListing mTvListing;

    private Action mChangeActivityAction;
    private Action mPromoteChannelAction;
    private Action mCloseAction;
    private ArrayObjectAdapter mAdapter;

    @TargetApi(23)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInputId = TvContract.buildInputId(
                new ComponentName(getContext(), RichTvInputService.class));
        new InitTask().execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private class InitTask extends AsyncTask<Uri, String, Void> {
        Bitmap mPoster;

        @Override
        protected Void doInBackground(Uri... params) {
            mTvListing = RichFeedUtil.getRichTvListings(getActivity());
            mPoster = fetchPoster();
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            initUIs(mPoster);
        }

        private void initUIs(Bitmap bitmap) {
            final String displayNumber = getActivity().getIntent().getStringExtra(
                    RichFeedUtil.EXTRA_DISPLAY_NUMBER);
            XmlTvParser.XmlTvChannel linkChannel = null;
            if (displayNumber != null) {
                for (XmlTvParser.XmlTvChannel channel : mTvListing.channels) {
                    if (displayNumber.equals(channel.displayNumber)) {
                        linkChannel = channel;
                        break;
                    }
                }
            }

            DetailsOverviewRowPresenter dorPresenter = new DetailsOverviewRowPresenter(
                    new DetailsDescriptionPresenter(linkChannel));
            dorPresenter.setSharedElementEnterTransition(getActivity(), "AppLinkFragment");

            mChangeActivityAction = new Action(ACTION_CHANGE_ACTIVITY,
                    getResources().getString(R.string.rich_app_link_full_change));
            mPromoteChannelAction = new Action(ACTION_PROMOTE_CHANNEL,
                    getResources().getString(R.string.rich_app_link_promote));
            mCloseAction = new Action(ACTION_CLOSE,
                    getResources().getString(R.string.rich_app_link_close));

            DetailsOverviewRow row = new DetailsOverviewRow(mTvListing);
            if (bitmap != null) {
                int length = Math.min(bitmap.getWidth(), bitmap.getHeight());
                Bitmap croppedBitmap = Bitmap.createBitmap(bitmap,
                        (bitmap.getWidth() - length) / 2,
                        (bitmap.getHeight() - length) / 2,
                        length, length);
                row.setImageBitmap(getActivity(), croppedBitmap);
            }
            row.addAction(mChangeActivityAction);
            row.addAction(mPromoteChannelAction);
            row.addAction(mCloseAction);

            ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
            dorPresenter.setBackgroundColor(getResources().getColor(R.color.detail_background));
            dorPresenter.setStyleLarge(true);

            dorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
                @TargetApi(23)
                @Override
                public void onActionClicked(Action action) {
                    if (action.getId() == ACTION_CHANGE_ACTIVITY) {
                        RichFeedUtil.setAppLinkActivity(getContext(),
                                RichAppLinkSidePanelActivity.class);
                        TvContractUtils.updateChannels(getContext(), mInputId, mTvListing.channels);
                        Intent intent = getActivity().getIntent();
                        intent.setComponent(new ComponentName(
                                getActivity(), RichAppLinkSidePanelActivity.class));
                        startActivity(intent);
                    } else if (action.getId() == ACTION_PROMOTE_CHANNEL) {
                        RichTvInputService.removePromotionMessage(
                                TvContractUtils.getChannelUriByNumber(
                                        getActivity().getContentResolver(), displayNumber));
                    }
                    getActivity().finish();
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
            Uri uri = Uri.parse(getString(R.string.rich_app_link_poster_url)).normalizeScheme();
            try (InputStream inputStream = RichFeedUtil.getInputStream(getActivity(), uri)) {
                return BitmapFactory.decodeStream(inputStream);
            } catch (IOException e) {
                Log.e(TAG, "Failed to fetch the app link poster: " + uri, e);
                return null;
            }
        }
    }

    private class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {
        private final XmlTvParser.XmlTvChannel mChannel;

        public DetailsDescriptionPresenter(XmlTvParser.XmlTvChannel channel) {
            mChannel = channel;
        }

        @Override
        protected void onBindDescription(ViewHolder viewHolder, Object item) {
            String title = getResources().getString(R.string.rich_app_link_title);
            String subTitle = getResources().getString(R.string.rich_app_link_full_subtitle);
            String body = getResources().getString(R.string.rich_app_link_full_body);

            if (mChannel != null) {
                title += " for CH " + mChannel.displayNumber;
                ContentResolver resolver =
                        RichAppLinkFullScreenFragment.this.getActivity().getContentResolver();
                Uri channelUri = TvContractUtils.getChannelUriByNumber(
                        resolver, mChannel.displayNumber);
                Program program = TvContractUtils.getCurrentProgram(resolver, channelUri);
                if (program != null) {
                    subTitle = program.getTitle();
                    body = program.getDescription();
                }
            }

            viewHolder.getTitle().setText(title);
            viewHolder.getSubtitle().setText(subTitle);
            viewHolder.getBody().setText(body);
        }
    }
}
