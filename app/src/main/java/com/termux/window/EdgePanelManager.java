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

        // Set to full screen dimensions
        final int startX = mDisplayWidth;
        final int endX = 0;

        params.x = startX;
        params.y = 0;
        params.width = mDisplayWidth;
        params.height = mDisplayHeight;
        params.gravity = Gravity.TOP | Gravity.START;

        // Update layout before animation
        if (mTermuxFloatView.getWindowToken() != null) {
            mWindowManager.updateViewLayout(mTermuxFloatView, params);
        }

        // Animate slide in from right
        mSlideAnimator = ValueAnimator.ofInt(startX, endX);
        mSlideAnimator.setDuration(ANIMATION_DURATION_MS);
        mSlideAnimator.setInterpolator(new DecelerateInterpolator());

        final WindowManager.LayoutParams animParams = params;
        mSlideAnimator.addUpdateListener(animation -> {
            animParams.x = (int) animation.getAnimatedValue();
            if (mTermuxFloatView.getWindowToken() != null) {
                mWindowManager.updateViewLayout(mTermuxFloatView, animParams);
            }
        });

        mSlideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIsExpanded = true;
                mIsAnimating = false;
                if (mStateListener != null) {
                    mStateListener.onPanelExpanded();
                }
                // Show keyboard
                mTermuxFloatView.showTouchKeyboard();
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
        mIsAnimating = true;

        if (mStateListener != null) {
            mStateListener.onPanelCollapsing();
        }

        // Hide keyboard first
        mTermuxFloatView.hideTouchKeyboard();

        WindowManager.LayoutParams params = (WindowManager.LayoutParams) mTermuxFloatView.getLayoutParams();

        final int startX = 0;
        final int endX = mDisplayWidth;

        // Animate slide out to right
        mSlideAnimator = ValueAnimator.ofInt(startX, endX);
        mSlideAnimator.setDuration(ANIMATION_DURATION_MS);
        mSlideAnimator.setInterpolator(new DecelerateInterpolator());

        final WindowManager.LayoutParams animParams = params;
        mSlideAnimator.addUpdateListener(animation -> {
            animParams.x = (int) animation.getAnimatedValue();
            if (mTermuxFloatView.getWindowToken() != null) {
                mWindowManager.updateViewLayout(mTermuxFloatView, animParams);
            }
        });

        mSlideAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIsExpanded = false;
                mIsAnimating = false;
                // Hide main view and show edge indicator
                mTermuxFloatView.setVisibility(View.GONE);
                showEdgeIndicator();
                if (mStateListener != null) {
                    mStateListener.onPanelCollapsed();
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
        if (mSlideAnimator != null && mSlideAnimator.isRunning()) {
            mSlideAnimator.cancel();
        }
        hideEdgeIndicator();
        mEdgeIndicator = null;
    }

    /**
     * Called when display dimensions change (rotation, etc.)
     */
    public void onDisplayChanged() {
        updateDisplayDimensions();

        if (mIsExpanded) {
            // Update panel size to match new display
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) mTermuxFloatView.getLayoutParams();
            params.width = mDisplayWidth;
            params.height = mDisplayHeight;
            if (mTermuxFloatView.getWindowToken() != null) {
                mWindowManager.updateViewLayout(mTermuxFloatView, params);
            }
        }
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
