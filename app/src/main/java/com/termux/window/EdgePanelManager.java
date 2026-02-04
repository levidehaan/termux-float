package com.termux.window;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import com.termux.shared.logger.Logger;
import com.termux.shared.view.ViewUtils;

/**
 * Manages the edge-triggered side panel behavior.
 * Replaces the floating bubble with a thin edge bar that expands to full screen.
 */
public class EdgePanelManager {
    private static final String LOG_TAG = "EdgePanelManager";

    // Edge bar dimensions
    private static final int EDGE_BAR_WIDTH_DP = 4;
    private static final int EDGE_BAR_HEIGHT_DP = 200;
    private static final int EDGE_TOUCH_TARGET_WIDTH_DP = 20;

    // Animation settings
    private static final int ANIMATION_DURATION_MS = 250;

    // Swipe thresholds
    private static final int SWIPE_THRESHOLD_DP = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    private final TermuxFloatView mTermuxFloatView;
    private final Context mContext;
    private final WindowManager mWindowManager;

    // Edge indicator view (visible when collapsed)
    private View mEdgeIndicator;
    private final WindowManager.LayoutParams mEdgeIndicatorParams;

    // State
    private boolean mIsExpanded = false;
    private boolean mIsAnimating = false;

    // Display dimensions
    private int mDisplayWidth;
    private int mDisplayHeight;

    // Pixel values
    private final int mEdgeBarWidthPx;
    private final int mEdgeBarHeightPx;
    private final int mEdgeTouchTargetWidthPx;
    private final int mSwipeThresholdPx;

    // Animation
    private ValueAnimator mSlideAnimator;

    // Callbacks
    private PanelStateListener mStateListener;

    public interface PanelStateListener {
        void onPanelExpanding();
        void onPanelExpanded();
        void onPanelCollapsing();
        void onPanelCollapsed();
    }

    public EdgePanelManager(TermuxFloatView termuxFloatView) {
        mTermuxFloatView = termuxFloatView;
        mContext = termuxFloatView.getContext();
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);

        // Convert dp to pixels
        mEdgeBarWidthPx = (int) ViewUtils.dpToPx(mContext, EDGE_BAR_WIDTH_DP);
        mEdgeBarHeightPx = (int) ViewUtils.dpToPx(mContext, EDGE_BAR_HEIGHT_DP);
        mEdgeTouchTargetWidthPx = (int) ViewUtils.dpToPx(mContext, EDGE_TOUCH_TARGET_WIDTH_DP);
        mSwipeThresholdPx = (int) ViewUtils.dpToPx(mContext, SWIPE_THRESHOLD_DP);

        // Initialize edge indicator params
        mEdgeIndicatorParams = createEdgeIndicatorParams();

        // Get display dimensions
        updateDisplayDimensions();
    }

    private void updateDisplayDimensions() {
        Display display = mWindowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mDisplayWidth = size.x;
        mDisplayHeight = size.y;
    }

    private WindowManager.LayoutParams createEdgeIndicatorParams() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();

        params.width = mEdgeTouchTargetWidthPx;
        params.height = mEdgeBarHeightPx;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        return params;
    }

    /**
     * Initialize the edge indicator view that shows when panel is collapsed.
     */
    public void initEdgeIndicator() {
        if (mEdgeIndicator != null) return;

        mEdgeIndicator = new View(mContext);
        mEdgeIndicator.setBackgroundResource(R.drawable.edge_indicator);
        mEdgeIndicator.setAlpha(0.7f);

        // Set up touch handling on the edge indicator
        mEdgeIndicator.setOnTouchListener(new EdgeIndicatorTouchListener());

        Logger.logDebug(LOG_TAG, "Edge indicator initialized");
    }

    /**
     * Show the edge indicator (when panel is collapsed).
     */
    public void showEdgeIndicator() {
        if (mEdgeIndicator == null) {
            initEdgeIndicator();
        }

        if (mEdgeIndicator.getWindowToken() == null) {
            try {
                mWindowManager.addView(mEdgeIndicator, mEdgeIndicatorParams);
                Logger.logDebug(LOG_TAG, "Edge indicator shown");
            } catch (Exception e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        }
    }

    /**
     * Hide the edge indicator (when panel is expanded).
     */
    public void hideEdgeIndicator() {
        if (mEdgeIndicator != null && mEdgeIndicator.getWindowToken() != null) {
            try {
                mWindowManager.removeView(mEdgeIndicator);
                Logger.logDebug(LOG_TAG, "Edge indicator hidden");
            } catch (Exception e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        }
    }

    /**
     * Expand the panel to full screen with animation.
     */
    public void expand() {
        if (mIsExpanded || mIsAnimating) return;

        Logger.logDebug(LOG_TAG, "Expanding panel");

        // Ensure we have fresh display dimensions
        updateDisplayDimensions();

        // Validate we can proceed
        if (mTermuxFloatView.getWindowToken() == null) {
            Logger.logWarn(LOG_TAG, "Cannot expand: no window token");
            return;
        }

        mIsAnimating = true;

        if (mStateListener != null) {
            mStateListener.onPanelExpanding();
        }

        // Hide edge indicator
        hideEdgeIndicator();

        // Make the main view visible
        mTermuxFloatView.setVisibility(View.VISIBLE);

        // Get current layout params
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) mTermuxFloatView.getLayoutParams();
        if (params == null) {
            Logger.logWarn(LOG_TAG, "Cannot expand: null layout params");
            mIsAnimating = false;
            return;
        }

        // Capture current display dimensions for animation (they shouldn't change during animation)
        final int animDisplayWidth = mDisplayWidth;
        final int animDisplayHeight = mDisplayHeight;

        // Set to full screen dimensions
        final int startX = animDisplayWidth;
        final int endX = 0;

        params.x = startX;
        params.y = 0;
        params.width = animDisplayWidth;
        params.height = animDisplayHeight;
        params.gravity = Gravity.TOP | Gravity.START;

        // Update layout before animation
        try {
            mWindowManager.updateViewLayout(mTermuxFloatView, params);
        } catch (IllegalArgumentException e) {
            Logger.logStackTrace(LOG_TAG, e);
            mIsAnimating = false;
            return;
        }

        // Animate slide in from right
        mSlideAnimator = ValueAnimator.ofInt(startX, endX);
        mSlideAnimator.setDuration(ANIMATION_DURATION_MS);
        mSlideAnimator.setInterpolator(new DecelerateInterpolator());

        final WindowManager.LayoutParams animParams = params;
        mSlideAnimator.addUpdateListener(animation -> {
            if (mTermuxFloatView.getWindowToken() != null) {
                try {
                    animParams.x = (int) animation.getAnimatedValue();
                    mWindowManager.updateViewLayout(mTermuxFloatView, animParams);
                } catch (IllegalArgumentException e) {
                    // View may have been removed, cancel animation
                    animation.cancel();
                }
            }
        });

        mSlideAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
                Logger.logDebug(LOG_TAG, "Expand animation cancelled");
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimating = false;
                if (!mCancelled) {
                    mIsExpanded = true;
                    if (mStateListener != null) {
                        mStateListener.onPanelExpanded();
                    }
                    // Show keyboard
                    mTermuxFloatView.showTouchKeyboard();
                }
            }
        });

        mSlideAnimator.start();
    }

    /**
     * Collapse the panel to edge bar with animation.
     */
    public void collapse() {
        if (!mIsExpanded || mIsAnimating) return;

        Logger.logDebug(LOG_TAG, "Collapsing panel");

        // Ensure we have fresh display dimensions
        updateDisplayDimensions();

        // Validate we can proceed
        if (mTermuxFloatView.getWindowToken() == null) {
            Logger.logWarn(LOG_TAG, "Cannot collapse with animation: no window token, using force collapse");
            forceCollapse();
            return;
        }

        mIsAnimating = true;

        if (mStateListener != null) {
            mStateListener.onPanelCollapsing();
        }

        // Hide keyboard first
        mTermuxFloatView.hideTouchKeyboard();

        WindowManager.LayoutParams params = (WindowManager.LayoutParams) mTermuxFloatView.getLayoutParams();
        if (params == null) {
            Logger.logWarn(LOG_TAG, "Cannot collapse: null layout params");
            mIsAnimating = false;
            return;
        }

        // Capture current display dimensions for animation
        final int animDisplayWidth = mDisplayWidth;

        final int startX = 0;
        final int endX = animDisplayWidth;

        // Animate slide out to right
        mSlideAnimator = ValueAnimator.ofInt(startX, endX);
        mSlideAnimator.setDuration(ANIMATION_DURATION_MS);
        mSlideAnimator.setInterpolator(new DecelerateInterpolator());

        final WindowManager.LayoutParams animParams = params;
        mSlideAnimator.addUpdateListener(animation -> {
            if (mTermuxFloatView.getWindowToken() != null) {
                try {
                    animParams.x = (int) animation.getAnimatedValue();
                    mWindowManager.updateViewLayout(mTermuxFloatView, animParams);
                } catch (IllegalArgumentException e) {
                    // View may have been removed, cancel animation
                    animation.cancel();
                }
            }
        });

        mSlideAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mCancelled = true;
                Logger.logDebug(LOG_TAG, "Collapse animation cancelled");
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimating = false;
                if (!mCancelled) {
                    mIsExpanded = false;
                    // Hide main view and show edge indicator
                    mTermuxFloatView.setVisibility(View.GONE);
                    showEdgeIndicator();
                    if (mStateListener != null) {
                        mStateListener.onPanelCollapsed();
                    }
                }
            }
        });

        mSlideAnimator.start();
    }

    /**
     * Toggle between expanded and collapsed states.
     */
    public void toggle() {
        if (mIsExpanded) {
            collapse();
        } else {
            expand();
        }
    }

    /**
     * Check if panel is currently expanded.
     */
    public boolean isExpanded() {
        return mIsExpanded;
    }

    /**
     * Check if panel is currently animating.
     */
    public boolean isAnimating() {
        return mIsAnimating;
    }

    /**
     * Set the state listener for panel events.
     */
    public void setStateListener(PanelStateListener listener) {
        mStateListener = listener;
    }

    /**
     * Handle swipe gesture from edge.
     * @param deltaX The horizontal swipe distance (negative = swipe left/inward)
     * @param velocityX The swipe velocity
     * @return true if the swipe triggered an action
     */
    public boolean handleEdgeSwipe(float deltaX, float velocityX) {
        if (mIsAnimating) return false;

        // Swipe left (inward) from edge to expand
        if (!mIsExpanded && deltaX < -mSwipeThresholdPx) {
            expand();
            return true;
        }

        return false;
    }

    /**
     * Handle swipe gesture from left edge of expanded panel.
     * @param startX The starting X position of the swipe
     * @param deltaX The horizontal swipe distance (positive = swipe right/outward)
     * @param velocityX The swipe velocity
     * @return true if the swipe triggered an action
     */
    public boolean handlePanelSwipe(float startX, float deltaX, float velocityX) {
        if (mIsAnimating) return false;

        // Swipe right from left edge to collapse
        if (mIsExpanded && startX < mSwipeThresholdPx && deltaX > mSwipeThresholdPx) {
            collapse();
            return true;
        }

        return false;
    }

    /**
     * Clean up resources.
     */
    public void cleanup() {
        Logger.logDebug(LOG_TAG, "cleanup");

        // Cancel any running animation
        if (mSlideAnimator != null) {
            if (mSlideAnimator.isRunning()) {
                mSlideAnimator.cancel();
            }
            mSlideAnimator = null;
        }

        mIsAnimating = false;

        // Remove edge indicator
        hideEdgeIndicator();
        mEdgeIndicator = null;

        // Clear listener
        mStateListener = null;
    }

    /**
     * Called when display dimensions change (rotation, etc.)
     * This is critical for handling rotation properly.
     */
    public void onDisplayChanged() {
        Logger.logDebug(LOG_TAG, "onDisplayChanged called, isExpanded=" + mIsExpanded + ", isAnimating=" + mIsAnimating);

        // Cancel any running animation to prevent crashes
        if (mSlideAnimator != null && mSlideAnimator.isRunning()) {
            Logger.logDebug(LOG_TAG, "Cancelling running animation due to display change");
            mSlideAnimator.cancel();
            mIsAnimating = false;
        }

        // Update dimensions
        int oldWidth = mDisplayWidth;
        int oldHeight = mDisplayHeight;
        updateDisplayDimensions();
        Logger.logDebug(LOG_TAG, "Display dimensions changed: " + oldWidth + "x" + oldHeight + " -> " + mDisplayWidth + "x" + mDisplayHeight);

        // Make sure we have a valid window token before updating layout
        if (mTermuxFloatView.getWindowToken() == null) {
            Logger.logDebug(LOG_TAG, "No window token, skipping layout update");
            return;
        }

        if (mIsExpanded) {
            // Update panel size to match new display dimensions
            try {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) mTermuxFloatView.getLayoutParams();
                if (params != null) {
                    params.x = 0;
                    params.y = 0;
                    params.width = mDisplayWidth;
                    params.height = mDisplayHeight;
                    params.gravity = Gravity.TOP | Gravity.START;
                    mWindowManager.updateViewLayout(mTermuxFloatView, params);
                    Logger.logDebug(LOG_TAG, "Updated expanded panel to: " + mDisplayWidth + "x" + mDisplayHeight);
                }
            } catch (IllegalArgumentException e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        } else {
            // If collapsed, make sure edge indicator is correctly positioned
            // The edge indicator uses Gravity.END so it should reposition automatically,
            // but we may need to re-add it if there were issues
            if (mEdgeIndicator != null) {
                try {
                    // Remove and re-add to ensure proper positioning
                    if (mEdgeIndicator.getWindowToken() != null) {
                        mWindowManager.removeView(mEdgeIndicator);
                    }
                    mWindowManager.addView(mEdgeIndicator, mEdgeIndicatorParams);
                    Logger.logDebug(LOG_TAG, "Re-added edge indicator after rotation");
                } catch (Exception e) {
                    Logger.logStackTrace(LOG_TAG, e);
                }
            }
        }
    }

    /**
     * Force a safe collapse without animation - useful during rotation recovery.
     */
    public void forceCollapse() {
        Logger.logDebug(LOG_TAG, "Force collapsing panel");

        // Cancel any running animation
        if (mSlideAnimator != null && mSlideAnimator.isRunning()) {
            mSlideAnimator.cancel();
        }
        mIsAnimating = false;

        // Hide keyboard
        mTermuxFloatView.hideTouchKeyboard();

        // Hide main view
        mTermuxFloatView.setVisibility(View.GONE);

        // Show edge indicator
        showEdgeIndicator();

        mIsExpanded = false;

        if (mStateListener != null) {
            mStateListener.onPanelCollapsed();
        }
    }

    /**
     * Force a safe expand without animation - useful during rotation recovery.
     */
    public void forceExpand() {
        Logger.logDebug(LOG_TAG, "Force expanding panel");

        // Cancel any running animation
        if (mSlideAnimator != null && mSlideAnimator.isRunning()) {
            mSlideAnimator.cancel();
        }
        mIsAnimating = false;

        // Hide edge indicator
        hideEdgeIndicator();

        // Update dimensions first
        updateDisplayDimensions();

        // Set up full screen panel
        try {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) mTermuxFloatView.getLayoutParams();
            if (params != null && mTermuxFloatView.getWindowToken() != null) {
                params.x = 0;
                params.y = 0;
                params.width = mDisplayWidth;
                params.height = mDisplayHeight;
                params.gravity = Gravity.TOP | Gravity.START;
                mWindowManager.updateViewLayout(mTermuxFloatView, params);
            }
        } catch (IllegalArgumentException e) {
            Logger.logStackTrace(LOG_TAG, e);
        }

        // Show main view
        mTermuxFloatView.setVisibility(View.VISIBLE);

        mIsExpanded = true;

        if (mStateListener != null) {
            mStateListener.onPanelExpanded();
        }

        // Show keyboard
        mTermuxFloatView.showTouchKeyboard();
    }

    /**
     * Touch listener for the edge indicator.
     */
    private class EdgeIndicatorTouchListener implements View.OnTouchListener {
        private float mStartX;
        private float mStartY;
        private long mStartTime;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mStartX = event.getRawX();
                    mStartY = event.getRawY();
                    mStartTime = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - mStartX;
                    // Visual feedback could be added here
                    return true;

                case MotionEvent.ACTION_UP:
                    float endX = event.getRawX();
                    float endY = event.getRawY();
                    long duration = System.currentTimeMillis() - mStartTime;

                    float totalDeltaX = endX - mStartX;
                    float velocityX = duration > 0 ? (totalDeltaX / duration) * 1000 : 0;

                    // Check for tap (quick touch with minimal movement)
                    if (duration < 300 && Math.abs(totalDeltaX) < mSwipeThresholdPx / 2) {
                        expand();
                        return true;
                    }

                    // Check for swipe
                    return handleEdgeSwipe(totalDeltaX, velocityX);

                case MotionEvent.ACTION_CANCEL:
                    return true;
            }
            return false;
        }
    }
}
