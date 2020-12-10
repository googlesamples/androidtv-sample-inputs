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

import android.app.Activity;
import android.os.Bundle;
import androidx.leanback.widget.VerticalGridView;
import androidx.recyclerview.widget.RecyclerView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import com.example.android.sampletvinput.R;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.model.ModelUtils;
import java.util.List;

/**
 * Activity that shows a simple side panel UI.
 */
public class RichAppLinkSidePanelActivity extends Activity {
    private VerticalGridView mAppLinkMenuList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        List<Channel> channels = ModelUtils.getChannels(getContentResolver());
        Channel appLinkChannel = null;

        String displayNumber = getIntent().getStringExtra(RichFeedUtil.EXTRA_DISPLAY_NUMBER);
        if (displayNumber != null) {
            for (Channel channel : channels) {
                if (displayNumber.equals(channel.getDisplayNumber())) {
                    appLinkChannel = channel;
                    break;
                }
            }
        }

        // Sets the size and position of dialog activity.
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        layoutParams.width = getResources().getDimensionPixelSize(R.dimen.side_panel_width);
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        getWindow().setAttributes(layoutParams);

        setContentView(R.layout.rich_app_link_side_panel);

        if (appLinkChannel != null && appLinkChannel.getAppLinkColor() != 0) {
            TextView titleView = (TextView) findViewById(R.id.title);
            titleView.setBackgroundColor(appLinkChannel.getAppLinkColor());
        }
        mAppLinkMenuList = (VerticalGridView) findViewById(R.id.list);
        mAppLinkMenuList.setAdapter(new AppLinkMenuAdapter());
    }

    /**
     * Adapter class that provides the app link menu list.
     */
    public class AppLinkMenuAdapter extends RecyclerView.Adapter<ViewHolder> {
        private static final int ITEM_COUNT = 2;
        private final int[] ITEM_STRING_RES_IDS = new int[]{R.string.item_1, R.string.item_2};

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
            TextView view = (TextView) viewHolder.itemView;
            view.setText(getResources().getString(ITEM_STRING_RES_IDS[position]));
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
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
