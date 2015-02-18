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

import android.graphics.Color;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.support.v17.leanback.app.BrowseFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.HeaderItem;
import android.support.v17.leanback.widget.ListRow;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.TvContractUtils;

/**
 * Fragment which shows a sample UI for configuring {@link RichTvInputService}.
 */
public class RichSettingsFragment extends BrowseFragment {
    private static final String TAG = "SettingsFragment";

    private static final int GRID_ITEM_WIDTH = 500;
    private static final int GRID_ITEM_HEIGHT = 200;

    private ArrayObjectAdapter mRowsAdapter;

    // Container Activity must implement this interface
    public interface SettingsClickedListener {
        public void onSettingsClicked();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupUIElements();

        loadRows();

        setupEventListeners();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void loadRows() {

        String inputId = getActivity().getIntent().getStringExtra(TvInputInfo.EXTRA_INPUT_ID);
        String serviceName = TvContractUtils.getServiceNameFromInputId(getActivity(), inputId);

        if (!TextUtils.isEmpty(serviceName)) {
            String[] a = serviceName.split("\\.");
            serviceName = a[a.length - 1];
        }

        GridItemPresenter mGridPresenter = new GridItemPresenter();

        mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());
        HeaderItem gridHeader = new HeaderItem(0, "SETTINGS", null);

        ArrayObjectAdapter gridRowAdapter = new ArrayObjectAdapter(mGridPresenter);
        gridRowAdapter.add(serviceName + " Settings");
        if (RichTvInputService.class.getName().contains(serviceName) ) {
            gridRowAdapter.add("Server URLs");
            gridRowAdapter.add("Update Frequency");
        }
        mRowsAdapter.add(new ListRow(gridHeader, gridRowAdapter));

        setAdapter(mRowsAdapter);
    }

    private void setupUIElements() {
        setTitle(getString(R.string.rich_settings_browse_title));
        setHeadersState(HEADERS_DISABLED);
        setHeadersTransitionOnBackEnabled(true);

        // set fastLane (or headers) background color
        setBrandColor(getResources().getColor(R.color.fastlane_background));
        // set search icon color
        setSearchAffordanceColor(getResources().getColor(R.color.search_opaque));
    }

    private void setupEventListeners() {
        setOnItemViewClickedListener(new ItemViewClickedListener());
        setOnItemViewSelectedListener(new ItemViewSelectedListener());
    }

    private final class ItemViewClickedListener implements OnItemViewClickedListener {
        @Override
        public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                  RowPresenter.ViewHolder rowViewHolder, Row row) {

            if (item instanceof String) {
                Toast.makeText(getActivity(), ((String) item), Toast.LENGTH_SHORT).show();
            }
        }
    }


    private final class ItemViewSelectedListener implements OnItemViewSelectedListener {
        @Override
        public void onItemSelected(Presenter.ViewHolder itemViewHolder, Object item,
                                   RowPresenter.ViewHolder rowViewHolder, Row row) {
        }
    }

    private class GridItemPresenter extends Presenter {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent) {
            TextView view = new TextView(parent.getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT));
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            view.setBackgroundColor(getResources().getColor(R.color.default_background));
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, Object item) {
            ((TextView) viewHolder.view).setText((String) item);
        }

        @Override
        public void onUnbindViewHolder(ViewHolder viewHolder) {
        }
    }

}
