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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v7.widget.RecyclerView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.example.android.sampletvinput.R;
import com.example.android.sampletvinput.TvContractUtils;
import com.example.android.sampletvinput.xmltv.XmlTvParser;

/**
 * Activity which shows a simple side panel UI for promoting channels.
 */
public class RichAppLinkSidePanelActivity extends Activity {
    private XmlTvParser.XmlTvChannel mAppLinkChannel;
    private VerticalGridView mAppLinkMenuList;
    private String mInputId;
    private XmlTvParser.TvListing mTvListing = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInputId = TvContract.buildInputId(new ComponentName(this, RichTvInputService.class));
        mTvListing = RichFeedUtil.getRichTvListings(this);

        String displayNumber = getIntent().getStringExtra(RichFeedUtil.EXTRA_DISPLAY_NUMBER);
        if (displayNumber != null) {
            for (XmlTvParser.XmlTvChannel channel : RichFeedUtil.getRichTvListings(this).channels) {
                if (displayNumber.equals(channel.displayNumber)) {
                    mAppLinkChannel = channel;
                    break;
                }
            }
        }

        // Sets the size and position of dialog activity.
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        layoutParams.width = getResources().getDimensionPixelSize(R.dimen.side_panel_width);
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(layoutParams);

        setContentView(R.layout.rich_app_link_side_panel);

        TextView titleView = (TextView) findViewById(R.id.title);
        titleView.setText(getAppLinkTitle());
        titleView.setBackgroundColor(mAppLinkChannel.appLink.color);

        mAppLinkMenuList = (VerticalGridView) findViewById(R.id.list);
        mAppLinkMenuList.setAdapter(new AppLinkMenuAdapter());
    }

    private String getAppLinkTitle() {
        String title = getResources().getString(R.string.rich_app_link_title);
        if (mAppLinkChannel != null) {
            title += " for CH " + mAppLinkChannel.displayNumber;
        }
        return title;
    }

    /**
     * Adapter class that provides the app link menu list.
     */
    public class AppLinkMenuAdapter extends RecyclerView.Adapter<ViewHolder> {
        // Change the app-link activity from RichAppLinkSidePanelActivity
        // to RichAppLinkFullScreenActivity.
        public static final int POSITION_CHANGE_ACTIVITY = 0;
        public static final int POSITION_PROMOTE = 1;
        public static final int POSITION_CLOSE = 2;

        private static final int ITEM_COUNT = 3;

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = getLayoutInflater().inflate(viewType, mAppLinkMenuList, false);
            return new ViewHolder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return R.layout.rich_app_link_side_panel_item;
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int position) {
            View view = viewHolder.itemView;
            TextView itemText = (TextView) view.findViewById(R.id.item_text);
            TextView itemSubText = (TextView) view.findViewById(R.id.item_sub_text);
            switch (position) {
                case POSITION_CHANGE_ACTIVITY:
                    itemText.setText(R.string.rich_app_link_side_change);
                    itemSubText.setVisibility(View.VISIBLE);
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Context context = RichAppLinkSidePanelActivity.this;
                            RichFeedUtil.setAppLinkActivity(context,
                                    RichAppLinkFullScreenActivity.class);
                            TvContractUtils.updateChannels(context, mInputId, mTvListing.channels);
                            Intent intent = getIntent();
                            intent.setComponent(new ComponentName(
                                    RichAppLinkSidePanelActivity.this,
                                    RichAppLinkFullScreenActivity.class));
                            startActivity(intent);
                            finish();
                        }
                    });
                    break;
                case POSITION_PROMOTE:
                    itemText.setText(R.string.rich_app_link_promote);
                    itemSubText.setVisibility(View.GONE);
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            RichTvInputService.removePromotionMessage(
                                    TvContractUtils.getChannelUriByNumber(
                                            RichAppLinkSidePanelActivity.this.getContentResolver(),
                                            mAppLinkChannel.displayNumber));
                            finish();
                        }
                    });
                    break;
                case POSITION_CLOSE:
                    itemText.setText(R.string.rich_app_link_close);
                    itemSubText.setVisibility(View.GONE);
                    view.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            finish();
                        }
                    });
                    break;
            }
        }

        @Override
        public int getItemCount() {
            return ITEM_COUNT;
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }
    }
}
