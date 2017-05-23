/*
 * Copyright 2017 The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.media.tv.companionlibrary.setup;

import android.content.Context;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;

// Gradle puts all resources in the base package, so companionlibrary.R must be imported.
import com.google.android.media.tv.companionlibrary.R;

/** List view adapter for a channel using a pair of strings for the display number and name. */
final class ChannelAdapter extends BaseAdapter {

    private final ArrayList<Pair<String, String>> mChannels;

    public ChannelAdapter() {
        mChannels = new ArrayList<>();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int pos) {
        return false;
    }

    @Override
    public int getCount() {
        return mChannels.size();
    }

    @Override
    public Object getItem(int pos) {
        return mChannels.get(pos);
    }

    @Override
    public long getItemId(int pos) {
        return pos;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final Context context = parent.getContext();

        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.tif_channel_list, parent, false);
        }

        TextView channelName = (TextView) convertView.findViewById(R.id.channel_name);
        channelName.setText(mChannels.get(position).first);

        TextView channelNum = (TextView) convertView.findViewById(R.id.channel_num);
        channelNum.setText(mChannels.get(position).second);
        return convertView;
    }

    public void add(Pair<String, String> channelData) {
        mChannels.add(channelData);
        notifyDataSetChanged();
    }
}
