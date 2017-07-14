/*
 * Copyright 2015 The Android Open Source Project.
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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
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
import com.example.android.sampletvinput.R;

/**
 * Fragment that shows a simple details fragment UI.
 */
public class RichAppLinkDetailsFragment extends DetailsFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String displayNumber = getActivity().getIntent().getStringExtra(
                RichFeedUtil.EXTRA_DISPLAY_NUMBER);

        DetailsOverviewRowPresenter dorPresenter = new DetailsOverviewRowPresenter(
                new DetailsDescriptionPresenter());
        dorPresenter.setSharedElementEnterTransition(getActivity(), "RichAppLinkDetailsFragment");

        DetailsOverviewRow row = new DetailsOverviewRow(displayNumber);
        Resources res = getResources();
        Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.your_company);
        int length = Math.min(bitmap.getWidth(), bitmap.getHeight());
        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap,
                (bitmap.getWidth() - length) / 2,
                (bitmap.getHeight() - length) / 2,
                length, length);
        row.setImageBitmap(getActivity(), croppedBitmap);

        Action action1 = new Action(0, res.getString(R.string.details_fragment_action_1));
        Action action2 = new Action(1, res.getString(R.string.details_fragment_action_2));
        row.addAction(action1);
        row.addAction(action2);

        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        dorPresenter.setBackgroundColor(ContextCompat.getColor(getActivity(),
                R.color.detail_background));
        dorPresenter.setStyleLarge(true);

        dorPresenter.setOnActionClickedListener(new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                getActivity().finish();
            }
        });

        presenterSelector.addClassPresenter(DetailsOverviewRow.class, dorPresenter);
        presenterSelector.addClassPresenter(ListRow.class, new ListRowPresenter());
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenterSelector);
        adapter.add(row);

        setAdapter(adapter);
    }

    private class DetailsDescriptionPresenter extends AbstractDetailsDescriptionPresenter {
        @Override
        protected void onBindDescription(ViewHolder viewHolder, Object item) {
            Resources res = getResources();
            viewHolder.getTitle().setText(res.getString(R.string.app_link_title_2));
            viewHolder.getSubtitle().setText(res.getString(R.string.details_fragment_subtitle));
            viewHolder.getBody().setText(res.getString(R.string.details_fragment_body));
        }
    }
}
