/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.portraitlauncher.controlbar;

import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.getFirstCustomActionInSet;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.skipBackStandardActions;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.skipForwardStandardActions;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateActionsWithPlaybackState;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updatePlayButtonWithPlaybackState;
import static com.android.car.media.common.ui.PlaybackCardControllerUtilities.updateTextViewAndVisibility;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;

import androidx.constraintlayout.motion.widget.MotionLayout;

import com.android.car.apps.common.RoundedDrawable;
import com.android.car.apps.common.util.ViewUtils;
import com.android.car.carlauncher.homescreen.audio.media.MediaIntentRouter;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.playback.PlaybackProgress;
import com.android.car.media.common.playback.PlaybackViewModel;
import com.android.car.media.common.playback.PlaybackViewModel.PlaybackController;
import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.ui.PlaybackCardController;
import com.android.car.media.common.ui.PlaybackHistoryController;
import com.android.car.media.common.ui.PlaybackQueueController;
import com.android.car.portraitlauncher.R;

import java.util.ArrayList;
import java.util.List;

public class ControlBarMediaController extends PlaybackCardController {

    private static final int MAX_ACTIONS_IN_DEFAULT_LAYOUT = 6;
    private final MediaIntentRouter mMediaIntentRouter = MediaIntentRouter.getInstance();

    private ViewGroup mCustomActionLayout;
    private ViewGroup mCustomActionOverflowLayout;
    private View mCustomActionOverflowConcealLayout;
    private ImageButton mActionOverflowExitButton;
    private ViewGroup mQueueContainer;
    private ViewGroup mHistoryContainer;

    private PlaybackQueueController mPlaybackQueueController;
    private PlaybackHistoryController mPlaybackHistoryController;
    private MotionLayout mMotionLayout;

    private int mSubtitleVisibility;
    private int mDescriptionVisibility;
    private int mLogoVisibility;
    private int mCurrentTimeVisibility;
    private int mTimeSeparatorVisibility;
    private int mMaxTimeVisibility;
    private int mCustomActionLayoutVisibility;
    private int mCustomActionOverflowLayoutVisibility;

    private Animation mOverflowConcealOpenAnimation;
    private Animation mOverflowConcealCloseAnimation;

    /**
     * Builder for {@link ControlBarMediaController}. Overrides build() method to return
     * ControlBarMediaController rather than base {@link PlaybackCardController}
     */
    public static class Builder extends PlaybackCardController.Builder {

        @Override
        public ControlBarMediaController build() {
            ControlBarMediaController controller = new ControlBarMediaController(this);
            controller.setupController();
            return controller;
        }
    }

    public ControlBarMediaController(ControlBarMediaController.Builder builder) {
        super(builder);

        GestureDetector gestureDetector =
                new GestureDetector(
                        mView.getContext(),
                        new GestureDetector.SimpleOnGestureListener() {
                            private void sendMediaIntent() {
                                MediaSource mediaSource = mDataModel.getMediaSource().getValue();
                                Intent intent =
                                        mediaSource != null ? mediaSource.getIntent() : null;
                                mMediaIntentRouter.handleMediaIntent(intent);
                            }

                            @Override
                            public boolean onDown(MotionEvent event) {
                                return true;
                            }

                            @Override
                            public boolean onSingleTapUp(MotionEvent e) {
                                sendMediaIntent();
                                return true;
                            }

                            @Override
                            public boolean onFling(
                                    MotionEvent e1,
                                    MotionEvent e2,
                                    float velocityX,
                                    float velocityY) {
                                if (velocityY < 0) {
                                    sendMediaIntent();
                                }
                                return true;
                            }
                        });

        mView.setOnTouchListener(
                (v, event) -> {
                    if (gestureDetector.onTouchEvent(event)) {
                        return true;
                    }

                    return v.onTouchEvent(event);
                });

        mCustomActionLayout = mView.findViewById(R.id.custom_action_container);
        mCustomActionOverflowLayout = mView.findViewById(R.id.custom_action_overflow_container);
        mCustomActionOverflowConcealLayout = mView.findViewById(
                R.id.custom_action_overflow_conceal_container);
        mActionOverflowExitButton = mView.findViewById(R.id.overflow_exit_button);
        mActionOverflowExitButton.setOnClickListener(view ->
                handleCustomActionsOverflowButtonClicked(mActionOverflowButton));

        mQueueContainer = mView.findViewById(R.id.queue_list_container);
        mHistoryContainer = mView.findViewById(R.id.history_list_container);

        mPlaybackQueueController = new PlaybackQueueController(
                mView.findViewById(R.id.queue_list), Resources.ID_NULL,
                R.layout.control_bar_media_queue_item, Resources.ID_NULL, getViewLifecycleOwner(),
                mDataModel, mViewModel.getMediaItemsRepository(),
                /* LifeCycleObserverUxrContentLimiter */ null, /* uxrConfigurationId */ 0);
        mPlaybackQueueController.setShowTimeForActiveQueueItem(true);
        mPlaybackQueueController.setShowIconForActiveQueueItem(false);
        mPlaybackQueueController.setShowThumbnailForQueueItem(true);
        mPlaybackQueueController.setShowSubtitleForQueueItem(true);

        mPlaybackHistoryController = new PlaybackHistoryController(getViewLifecycleOwner(),
                mViewModel, mHistoryContainer, R.layout.control_bar_media_history_item,
                Resources.ID_NULL, /* uxrConfigurationId */ 0);
        mPlaybackHistoryController.setupView();

        mMotionLayout = mView.findViewById(R.id.control_bar_media_card_motion_layout);
        mMotionLayout.addTransitionListener(new MotionLayout.TransitionListener() {
            @Override
            public void onTransitionStarted(MotionLayout motionLayout, int i, int i1) {
            }

            @Override
            public void onTransitionChange(MotionLayout motionLayout, int i, int i1, float v) {
            }

            @Override
            public void onTransitionCompleted(MotionLayout motionLayout, int i) {
                if (isPanelOpen()) {
                    ViewUtils.setVisible(mCustomActionLayout, false);
                    ViewUtils.setVisible(mCustomActionOverflowLayout, false);
                } else {
                    ViewUtils.setVisible(mQueueContainer, false);
                    ViewUtils.setVisible(mHistoryContainer, false);
                }
            }

            @Override
            public void onTransitionTrigger(MotionLayout motionLayout, int i, boolean b, float v) {
            }
        });

        mOverflowConcealOpenAnimation = AnimationUtils.loadAnimation(
                mView.getContext(), R.anim.media_card_overflow_open_animation);
        mOverflowConcealCloseAnimation = AnimationUtils.loadAnimation(
                mView.getContext(), R.anim.media_card_overflow_close_animation);
    }

    @Override
    protected void setupController() {
        super.setupController();

        mSubtitleVisibility = mSubtitle.getVisibility();
        mDescriptionVisibility = mDescription.getVisibility();
        mLogoVisibility = mLogo.getVisibility();
        mCurrentTimeVisibility = mCurrentTime.getVisibility();
        mTimeSeparatorVisibility = mTimeSeparator.getVisibility();
        mMaxTimeVisibility = mMaxTime.getVisibility();

        mCustomActionLayoutVisibility = mCustomActionLayout.getVisibility();
        mCustomActionOverflowLayoutVisibility = mCustomActionOverflowLayout.getVisibility();
    }

    @Override
    protected void updateMetadata(MediaItemMetadata metadata) {
        super.updateMetadata(metadata);
        if (metadata == null) {
            updateTextViewAndVisibility(mTitle,
                    mView.getContext().getResources().getString(R.string.default_media_song_title));
        }
        if (isPanelOpen()) {
            mSubtitleVisibility = mSubtitle.getVisibility();
            mDescriptionVisibility = mDescription.getVisibility();
            mSubtitle.setVisibility(View.GONE);
            mDescription.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updateAlbumCoverWithDrawable(Drawable drawable) {
        Drawable drawableToUse = drawable == null ? mView.getResources().getDrawable(
                /* drawable */ R.drawable.media_card_default_album_art, /* theme */ null)
                : drawable;
        RoundedDrawable roundedDrawable = new RoundedDrawable(drawableToUse, mView.getResources()
                .getFloat(R.dimen.control_bar_media_card_album_art_drawable_corner_ratio));
        super.updateAlbumCoverWithDrawable(roundedDrawable);
    }

    @Override
    protected void updateLogoWithDrawable(Drawable drawable) {
        super.updateLogoWithDrawable(drawable);
        if (isPanelOpen()) {
            mLogoVisibility = mLogo.getVisibility();
            mLogo.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updateMediaSource(MediaSource mediaSource) {
        super.updateMediaSource(mediaSource);
        if (isPanelOpen()) {
            mAppIcon.setVisibility(View.GONE);
        }
    }

    // TODO b/336857625: Possibly move SeekBar hide logic to parent class
    @Override
    protected void updateProgress(PlaybackProgress progress) {
        super.updateProgress(progress);
        if (progress == null || !progress.hasTime()) {
            mSeekBar.setVisibility(View.GONE);
        }
        if (isPanelOpen()) {
            mCurrentTimeVisibility = mCurrentTime.getVisibility();
            mMaxTimeVisibility = mMaxTime.getVisibility();
            mTimeSeparatorVisibility = mTimeSeparator.getVisibility();
            mCurrentTime.setVisibility(View.GONE);
            mMaxTime.setVisibility(View.GONE);
            mTimeSeparator.setVisibility(View.GONE);
        }
    }

    @Override
    protected void updatePlaybackState(PlaybackViewModel.PlaybackStateWrapper playbackState) {
        boolean hasOverflow = false;
        PlaybackController playbackController = mDataModel.getPlaybackController().getValue();
        if (playbackState != null) {
            updatePlayButtonWithPlaybackState(mPlayPauseButton, playbackState, playbackController);
            int count = 0;
            if ((playbackState.isSkipNextEnabled() || playbackState.isSkipNextReserved())
                    || (!playbackState.isSkipNextEnabled() && !playbackState.isSkipNextReserved()
                    && getFirstCustomActionInSet(playbackState.getCustomActions(),
                    skipForwardStandardActions) != null)) {
                count++;
            }
            if (playbackState.isSkipPreviousEnabled() || playbackState.iSkipPreviousReserved()
                    || (!playbackState.isSkipPreviousEnabled()
                    && !playbackState.iSkipPreviousReserved()
                    && getFirstCustomActionInSet(playbackState.getCustomActions(),
                    skipBackStandardActions) != null)) {
                count++;
            }
            Drawable skipNextDrawableBackground = count == 1 ? mView.getContext()
                    .getDrawable(R.drawable.pill_button_shape) : mView.getContext()
                    .getDrawable(R.drawable.right_half_pill_button_shape);
            List<ImageButton> mActionsCopy = new ArrayList<>(mActions);
            if (playbackState.getCustomActions().size() > (MAX_ACTIONS_IN_DEFAULT_LAYOUT - count)) {
                while (count >= 0) {
                    int actionSlotIndexToSkip = 8 - count - 1;
                    mActionsCopy.remove(actionSlotIndexToSkip);
                    mActionsCopy.add(actionSlotIndexToSkip, null);
                    count--;
                }
                hasOverflow = true;
                mActionOverflowButton.setVisibility(View.VISIBLE);
            } else {
                mActionOverflowButton.setVisibility(View.GONE);
            }
            updateActionsWithPlaybackState(mView.getContext(), mActionsCopy, playbackState,
                    playbackController, mView.getContext().getDrawable(R.drawable.ic_skip_previous),
                    mView.getContext().getDrawable(R.drawable.ic_skip_next),
                    mView.getContext().getDrawable(R.drawable.left_half_pill_button_shape),
                    skipNextDrawableBackground, /* reserveSkipSlots */ true,
                    /* defaultButtonDrawable */ null);

            if (!hasOverflow && mViewModel.getOverflowExpanded()) {
                handleCustomActionsOverflowButtonClicked(mActionOverflowButton);
            }
            if (isPanelOpen()) {
                mCustomActionLayout.setVisibility(View.GONE);
                mCustomActionOverflowLayout.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void setUpActionsOverflowButton() {
        super.setUpActionsOverflowButton();
        setOverflowState(mViewModel.getOverflowExpanded());
    }

    @Override
    protected void handleCustomActionsOverflowButtonClicked(View overflow) {
        super.handleCustomActionsOverflowButtonClicked(overflow);
        setOverflowState(mViewModel.getOverflowExpanded());
    }

    private void setOverflowState(boolean isExpanded) {

        Animation anim = isExpanded ? mOverflowConcealOpenAnimation :
                mOverflowConcealCloseAnimation;
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (!isExpanded) {
                    ViewUtils.setVisible(mCustomActionOverflowLayout, false);
                    ViewUtils.setVisible(mActionOverflowExitButton, false);
                }
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        mCustomActionOverflowConcealLayout.startAnimation(anim);

        Animation fadeIn = AnimationUtils.loadAnimation(mView.getContext(),
                R.anim.media_card_overflow_fade_in);
        Animation fadeOut = AnimationUtils.loadAnimation(mView.getContext(),
                R.anim.media_card_overflow_fade_out);
        if (!isExpanded) {
            mCustomActionLayout.startAnimation(fadeIn);
            mCustomActionOverflowLayout.startAnimation(fadeOut);
        } else {
            mCustomActionOverflowLayout.startAnimation(fadeIn);
            ViewUtils.setVisible(mCustomActionOverflowLayout, true);
            ViewUtils.setVisible(mActionOverflowExitButton, true);
        }
        ViewUtils.setVisible(mCustomActionLayout, !isExpanded);
        mCustomActionLayoutVisibility = mCustomActionLayout.getVisibility();
        mCustomActionOverflowLayoutVisibility = mCustomActionOverflowLayout.getVisibility();
    }

    @Override
    protected void setUpQueueButton() {
        super.setUpQueueButton();
        setQueueState(mViewModel.getQueueVisible(), /* stateSetThroughClick */ false);
    }

    @Override
    protected void updateQueueState(boolean hasQueue, boolean isQueueVisible) {
        super.updateQueueState(hasQueue, isQueueVisible);
        ViewUtils.setVisible(mQueueButton, hasQueue);
        if (isPanelOpen() && mViewModel.getQueueVisible() && !hasQueue) {
            unselectPanelButtons();

            mViewModel.setQueueVisible(false);
            mViewModel.setHistoryVisible(false);

            showViewsAndTransitionPanelClosed();
        }
    }

    @Override
    protected void handleQueueButtonClicked(View queue) {
        super.handleQueueButtonClicked(queue);
        setQueueState(mViewModel.getQueueVisible(), /* stateSetThroughClick */ true);
    }

    @Override
    protected void setUpHistoryButton() {
        super.setUpHistoryButton();
        setHistoryState(mViewModel.getHistoryVisible(), /* stateSetThroughClick */ false);
    }

    @Override
    protected void handleHistoryButtonClicked(View history) {
        super.handleHistoryButtonClicked(history);
        setHistoryState(mViewModel.getHistoryVisible(), /* stateSetThroughClick */ true);
    }

    private void setQueueState(boolean isVisible, boolean stateSetThroughClick) {
        if (isVisible) {
            showQueue(true);
            showHistory(false);
            if (mViewModel.getHistoryVisible()) {
                mViewModel.setHistoryVisible(false);
            } else {
                saveViewVisibilityAndTransitionPanelOpen();
            }
        } else if (stateSetThroughClick) {
            unselectPanelButtons();

            mViewModel.setHistoryVisible(false);

            showViewsAndTransitionPanelClosed();
        }
    }

    private void setHistoryState(boolean isVisible, boolean stateSetThroughClick) {
        if (isVisible) {
            showHistory(true);
            showQueue(false);
            if (mViewModel.getQueueVisible()) {
                mViewModel.setQueueVisible(false);
            } else {
                saveViewVisibilityAndTransitionPanelOpen();
            }
        } else if (stateSetThroughClick) {
            unselectPanelButtons();

            mViewModel.setQueueVisible(false);

            showViewsAndTransitionPanelClosed();
        }
    }

    private boolean isPanelOpen() {
        return mViewModel.getQueueVisible() || mViewModel.getHistoryVisible();
    }

    private void saveViewVisibilityAndTransitionPanelOpen() {
        mSubtitleVisibility = mSubtitle.getVisibility();
        mDescriptionVisibility = mDescription.getVisibility();
        mLogoVisibility = mLogo.getVisibility();
        mCurrentTimeVisibility = mCurrentTime.getVisibility();
        mTimeSeparatorVisibility = mTimeSeparator.getVisibility();
        mMaxTimeVisibility = mMaxTime.getVisibility();

        mCustomActionLayoutVisibility = mCustomActionLayout.getVisibility();
        mCustomActionOverflowLayoutVisibility = mCustomActionOverflowLayout.getVisibility();

        mSeekBar.getThumb().mutate().setAlpha(0);
        mSeekBar.setEnabled(false);

        mMotionLayout.transitionToEnd();
    }

    private void showViewsAndTransitionPanelClosed() {
        mAppIcon.setVisibility(View.VISIBLE);
        mSubtitle.setVisibility(mSubtitleVisibility);
        mDescription.setVisibility(mDescriptionVisibility);
        mCurrentTime.setVisibility(mCurrentTimeVisibility);
        mTimeSeparator.setVisibility(mTimeSeparatorVisibility);
        mMaxTime.setVisibility(mMaxTimeVisibility);
        mLogo.setVisibility(mLogoVisibility);

        mCustomActionLayout.setVisibility(mCustomActionLayoutVisibility);
        mCustomActionOverflowLayout.setVisibility(mCustomActionOverflowLayoutVisibility);

        mSeekBar.getThumb().mutate().setAlpha(255);
        mSeekBar.setEnabled(true);

        mMotionLayout.transitionToStart();
    }

    private void showQueue(boolean shouldShow) {
        ViewUtils.setVisible(mQueueContainer, shouldShow);
        mQueueButton.setSelected(shouldShow);
    }

    private void showHistory(boolean shouldShow) {
        ViewUtils.setVisible(mHistoryContainer, shouldShow);
        mHistoryButton.setSelected(shouldShow);
    }

    private void unselectPanelButtons() {
        mQueueButton.setSelected(false);
        mHistoryButton.setSelected(false);
    }
}
