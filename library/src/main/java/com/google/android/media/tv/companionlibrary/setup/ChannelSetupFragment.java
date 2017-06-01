/*
 * Copyright 2016 The Android Open Source Project.
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
package com.google.android.media.tv.companionlibrary.setup;

import android.animation.LayoutTransition;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.android.media.tv.companionlibrary.model.Channel;
import com.google.android.media.tv.companionlibrary.sync.EpgSyncJobService;
import com.google.android.media.tv.companionlibrary.sync.SyncStatusBroadcastReceiver;
import com.google.android.media.tv.companionlibrary.sync.SyncStatusBroadcastReceiver.SyncListener;

// Gradle puts all resources in the base package, so companionlibrary.R must be imported.
import com.google.android.media.tv.companionlibrary.R;

/**
 * The ChannelSetupFragment class provides a simple extendable class to create a user interface for
 * scanning channels. This fragment will be displayed to the user when they are setting up your
 * app's channels for the first time in the setup activity.
 *
 * <p>There are a handful of methods which can be used to customize and theme the user interface.
 * Methods like {@link #setBackgroundColor(int)}, {@link #setTitle(CharSequence)}, and {@link
 * #setBadge(Drawable)} can be called when your fragment first is initialized to change their
 * values.
 *
 * <p>Additionally, your fragment can override certain methods to provide custom functionality to
 * your setup activity. When the user clicks on the button to start scanning for your channels, the
 * {@link #onScanStarted()} method will be called. Here your {@link EpgSyncJobService} should run
 * the {@link EpgSyncJobService#requestImmediateSync(Context, String, ComponentName)} and start
 * syncing.
 *
 * <p>When your service is done scanning, the method {@link #onScanFinished()} will called. Here
 * should be the code to exit the setup activity and return to the system TV app.
 *
 * <p>While channels are being scanned, the methods {@link #onScanStepCompleted(int, int)} and
 * {@link #onScannedChannel(CharSequence, CharSequence)} will be called to provide status updates
 * during scans. This information can be provided to the user by calling {@link
 * #setDescription(CharSequence)}. Additionally, a progress bar will automatically increment.
 *
 * <p>Users should be able to manually start and stop the scanning process and the button text
 * should be updated by calling {@link #setButtonText(CharSequence)}. If {@link
 * #setChannelListVisibility(boolean)} is {@code true}, the channels will appear on the screen as
 * they are scanned.
 *
 * <p><b>Deprecated</b> use {@link ChannelSetupStepFragment} instead.
 */
@Deprecated
public abstract class ChannelSetupFragment extends Fragment implements SyncListener {
    private static final String TAG = "ScanFragment";
    private static final boolean DEBUG = false;

    private ProgressBar mProgressBar;
    private View mChannelHolder;
    private TextView mScanningMessage;
    private ChannelAdapter mAdapter;
    private Button mCancelButton;
    private View mChannelScanLayout;
    private TextView mTitle;
    private ImageView mBadge;

    private BroadcastReceiver mSyncStatusChangedReceiver;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(getLayoutResourceId(), container, false);
        // Make sure this view is focused
        view.requestFocus();
        mProgressBar = (ProgressBar) view.findViewById(R.id.tune_progress);
        mScanningMessage = (TextView) view.findViewById(R.id.tune_description);
        mTitle = (TextView) view.findViewById(R.id.tune_title);
        mBadge = (ImageView) view.findViewById(R.id.tune_icon);
        mChannelHolder = view.findViewById(R.id.channel_holder);
        mCancelButton = (Button) view.findViewById(R.id.tune_cancel);

        ListView channelList = (ListView) view.findViewById(R.id.channel_list);
        mAdapter = new ChannelAdapter();
        channelList.setAdapter(mAdapter);
        channelList.setOnItemClickListener(null);

        ViewGroup progressHolder = (ViewGroup) view.findViewById(R.id.progress_holder);
        LayoutTransition transition = new LayoutTransition();
        transition.enableTransitionType(LayoutTransition.CHANGING);
        progressHolder.setLayoutTransition(transition);

        mCancelButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finishScan();
                    }
                });
        mSyncStatusChangedReceiver = new SyncStatusBroadcastReceiver(getInputId(), this);
        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(
                        mSyncStatusChangedReceiver,
                        new IntentFilter(EpgSyncJobService.ACTION_SYNC_STATUS_CHANGED));

        mChannelScanLayout = view;
        setChannelListVisibility(false);
        setBackgroundColor(getResources().getColor(android.R.color.holo_blue_dark));
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        onScanStarted();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mSyncStatusChangedReceiver);
    }

    /**
     * This method returns the layout for this fragment.
     *
     * @return Resource id for the fragment layout.
     */
    public int getLayoutResourceId() {
        return R.layout.tif_channel_setup;
    }

    /**
     * Sets the background color for the layout. This allows the setup fragment to be themed.
     *
     * @param backgroundColor The color for the background.
     */
    public void setBackgroundColor(@ColorInt int backgroundColor) {
        mChannelScanLayout
                .findViewById(R.id.channel_setup_layout)
                .setBackgroundColor(backgroundColor);
    }

    /**
     * Finishes the current scan thread. This fragment will be popped after the scan thread ends.
     */
    private void finishScan() {
        // Hides the cancel button.
        mCancelButton.setEnabled(false);
        onScanFinished();
    }

    /**
     * This method will be called when scanning should begin. Developers should request a new
     * immediate sync by calling {@link EpgSyncJobService#requestImmediateSync(Context, String,
     * ComponentName)}.
     */
    public abstract void onScanStarted();

    /** @return The input id for your Tv service. */
    public abstract String getInputId();

    /**
     * This method will be called when scanning ends. Developers may want to notify the user that
     * scanning has completed and allow them to exit the activity.
     */
    @Override
    public abstract void onScanFinished();

    /**
     * This method will be called when an error occurs in scanning. Developers may want to notify
     * the user that an error has happened or resolve the error.
     *
     * @param reason A constant indicating the type of error that has happened. Possible values are
     *     {@link EpgSyncJobService#ERROR_EPG_SYNC_CANCELED}, {@link
     *     EpgSyncJobService#ERROR_INPUT_ID_NULL}, {@link EpgSyncJobService#ERROR_NO_PROGRAMS},
     *     {@link EpgSyncJobService#ERROR_NO_CHANNELS}, or {@link
     *     EpgSyncJobService#ERROR_DATABASE_INSERT},
     */
    @Override
    public void onScanError(int reason) {}

    /**
     * Update the description that will be displayed underneath the progress bar. This could be used
     * to state the current progress of the scan.
     *
     * @param message The message to be displayed.
     */
    public void setDescription(CharSequence message) {
        mScanningMessage.setText(message);
    }

    /**
     * Update the description that will be displayed underneath the progress bar. This could be used
     * to state the current progress of the scan.
     *
     * @param resId The string resource to be displayed.
     */
    public void setDescription(int resId) {
        mScanningMessage.setText(resId);
    }

    /**
     * Sets the title that will be displayed above the progress bar. This could be used to display
     * your app's title.
     *
     * @param title The title to be displayed.
     */
    public void setTitle(CharSequence title) {
        mTitle.setText(title);
    }

    /**
     * Sets the title that will be displayed above the progress bar. This could be used to display
     * your app's title.
     *
     * @param resId The string resource to be displayed.
     */
    public void setTitle(int resId) {
        mTitle.setText(resId);
    }

    /**
     * Sets the image that will be displayed to the left of the progress bar. This could be used to
     * display your app's icon.
     *
     * @param drawable The drawable to be displayed.
     */
    public void setBadge(Drawable drawable) {
        mBadge.setImageDrawable(drawable);
    }

    /**
     * Sets the image that will be displayed to the left of the progress bar. This could be used to
     * display your app's icon.
     *
     * @param bitmap The bitmap image to be displayed.
     */
    public void setBadge(Bitmap bitmap) {
        mBadge.setImageBitmap(bitmap);
    }

    /**
     * Sets the text that will appear on the button on the screen.
     *
     * @param message The button text.
     */
    public void setButtonText(CharSequence message) {
        mCancelButton.setText(message);
    }

    /**
     * Sets the text that will appear on the button on the screen.
     *
     * @param resId The string resource to be displayed.
     */
    public void setButtonText(int resId) {
        mCancelButton.setText(resId);
    }

    /**
     * Sets whether the channel list will be displayed to the right of the screen, displaying each
     * channel as it is scanned.
     *
     * @param visible If true, the list will be displayed. Otherwise it will be hidden.
     */
    public void setChannelListVisibility(boolean visible) {
        mChannelHolder.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * This method will be called when a channel has been completely scanned. It can be overriden to
     * display custom information about this channel to the user.
     *
     * @param displayName {@link Channel#getDisplayName()} for the scanned channel.
     * @param displayNumber {@link Channel#getDisplayNumber()} ()} for the scanned channel.
     */
    @Override
    public void onScannedChannel(CharSequence displayName, CharSequence displayNumber) {
        if (DEBUG) {
            Log.d(TAG, "Scanned channel data: " + displayName + ", " + displayNumber);
        }
        mAdapter.add(new Pair<>(displayName.toString(), displayNumber.toString()));
    }

    @Override
    public void onScanStepCompleted(int completedStep, int totalSteps) {
        if (totalSteps > 0) {
            mProgressBar.setIndeterminate(false);
            mProgressBar.setMax(totalSteps);
            mProgressBar.setProgress(completedStep);
        }
    }
}
