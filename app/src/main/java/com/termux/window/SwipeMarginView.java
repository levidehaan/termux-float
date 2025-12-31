package com.termux.window;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import com.termux.shared.logger.Logger;
import com.termux.shared.view.ViewUtils;
import com.termux.view.TerminalView;

/**
 * A dedicated left margin view for handling swipe-to-close gestures.
 * This view sits on the left edge of the side panel and provides a clear
 * touch zone for closing the panel without interfering with the terminal.
 *
 * Features:
 * - Swipe right to close the panel (detected by finger exiting view or velocity)
 * - Vertical scroll to fast-scroll the terminal
 * - Long press to toggle transparent mode (same as terminal long press)
 * - Visual feedback when touched
 */
public class SwipeMarginView extends View {
    private static final String LOG_TAG = "SwipeMarginView";

    // Velocity threshold for swipe detection (pixels per second)
    private static final int SWIPE_VELOCITY_THRESHOLD = 500;

    // Long press timeout
    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    // Touch tracking
    private float mStartX;
    private float mStartY;
    private float mLastY;
    private boolean mIsSwipeGesture = false;
    private boolean mIsScrollGesture = false;
    private boolean mIsLongPressed = false;

    // Velocity tracking
    private VelocityTracker mVelocityTracker;

    // Long press handling
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mLongPressRunnable;

    // Pixel values
    private int mTouchSlop;

    // Visual feedback
    private Paint mHighlightPaint;
    private Paint mLongPressPaint;
    private Paint mIndicatorPaint;
    private Paint mArrowPaint;
    private boolean mIsTouched = false;

    // References
    private EdgePanelManager mEdgePanelManager;
    private TerminalView mTerminalView;
    private TermuxFloatView mTermuxFloatView;

    public SwipeMarginView(Context context) {
        super(context);
        init(context);
    }

    public SwipeMarginView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SwipeMarginView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Highlight paint for when touched
        mHighlightPaint = new Paint();
        mHighlightPaint.setColor(0x30ffffff); // Semi-transparent white
        mHighlightPaint.setStyle(Paint.Style.FILL);

        // Long press paint (slightly brighter/different color)
        mLongPressPaint = new Paint();
        mLongPressPaint.setColor(0x4003dac6); // Semi-transparent teal
        mLongPressPaint.setStyle(Paint.Style.FILL);

        // Indicator paint for subtle visual cue
        mIndicatorPaint = new Paint();
        mIndicatorPaint.setColor(0x40ffffff);
        mIndicatorPaint.setStyle(Paint.Style.FILL);
        mIndicatorPaint.setAntiAlias(true);

        // Arrow paint for swipe hint
        mArrowPaint = new Paint();
        mArrowPaint.setColor(0x60ffffff);
        mArrowPaint.setStyle(Paint.Style.FILL);
        mArrowPaint.setAntiAlias(true);

        // Make view clickable to receive touch events
        setClickable(true);
        setFocusable(false);
        setLongClickable(true);
    }

    /**
     * Set the EdgePanelManager for triggering collapse.
     */
    public void setEdgePanelManager(EdgePanelManager manager) {
        mEdgePanelManager = manager;
    }

    /**
     * Set the TerminalView for scroll passthrough.
     */
    public void setTerminalView(TerminalView terminalView) {
        mTerminalView = terminalView;
    }

    /**
     * Set the TermuxFloatView for long press mode.
     */
    public void setTermuxFloatView(TermuxFloatView floatView) {
        mTermuxFloatView = floatView;
    }

    /**
     * Update the width based on settings.
     */
    public void updateWidth() {
        int widthDp = TermuxFloatSettingsActivity.getSwipeMarginWidth(getContext());
        int widthPx = (int) ViewUtils.dpToPx(getContext(), widthDp);

        android.view.ViewGroup.LayoutParams params = getLayoutParams();
        if (params != null) {
            params.width = widthPx;
            setLayoutParams(params);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Apply width from settings when attached
        updateWidth();

        // Find parent TermuxFloatView if not set
        if (mTermuxFloatView == null) {
            View parent = (View) getParent();
            while (parent != null) {
                if (parent instanceof TermuxFloatView) {
                    mTermuxFloatView = (TermuxFloatView) parent;
                    break;
                }
                if (parent.getParent() instanceof View) {
                    parent = (View) parent.getParent();
                } else {
                    break;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        // Draw long press highlight if in that mode
        if (mIsLongPressed) {
            canvas.drawRect(0, 0, width, height, mLongPressPaint);
        }
        // Draw touch highlight
        else if (mIsTouched) {
            canvas.drawRect(0, 0, width, height, mHighlightPaint);
        }

        // Draw subtle vertical line indicator on right edge
        float indicatorWidth = 2f;
        canvas.drawRect(
            width - indicatorWidth,
            0,
            width,
            height,
            mIndicatorPaint
        );

        // Draw small arrow hints in the middle
        float centerY = height / 2f;
        float arrowSize = Math.min(width * 0.4f, 12f);

        // Draw a simple ">" arrow hint
        float arrowX = width * 0.3f;
        canvas.drawCircle(arrowX, centerY, arrowSize / 3, mArrowPaint);
        canvas.drawCircle(arrowX + arrowSize / 2, centerY, arrowSize / 4, mArrowPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Initialize velocity tracker
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getRawX();
                mStartY = event.getRawY();
                mLastY = mStartY;
                mIsSwipeGesture = false;
                mIsScrollGesture = false;
                mIsLongPressed = false;
                mIsTouched = true;
                invalidate();

                // Schedule long press
                mLongPressRunnable = () -> {
                    if (mIsTouched && !mIsSwipeGesture && !mIsScrollGesture) {
                        mIsLongPressed = true;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        toggleLongPressMode();
                        invalidate();
                    }
                };
                mHandler.postDelayed(mLongPressRunnable, LONG_PRESS_TIMEOUT);

                Logger.logDebug(LOG_TAG, "Touch down in swipe margin at x=" + event.getX());
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - mStartX;
                float deltaY = event.getRawY() - mStartY;
                float moveY = event.getRawY() - mLastY;
                mLastY = event.getRawY();
                float localX = event.getX();

                // Cancel long press if moved too much
                if (Math.abs(deltaX) > mTouchSlop || Math.abs(deltaY) > mTouchSlop) {
                    cancelLongPress();
                }

                // If long pressed, don't handle swipe/scroll
                if (mIsLongPressed) {
                    return true;
                }

                // Determine gesture type if not yet decided
                if (!mIsSwipeGesture && !mIsScrollGesture) {
                    if (Math.abs(deltaX) > mTouchSlop || Math.abs(deltaY) > mTouchSlop) {
                        if (Math.abs(deltaX) > Math.abs(deltaY)) {
                            mIsSwipeGesture = true;
                            Logger.logDebug(LOG_TAG, "Detected horizontal swipe gesture, deltaX=" + deltaX);
                        } else {
                            mIsScrollGesture = true;
                            Logger.logDebug(LOG_TAG, "Detected vertical scroll gesture");
                        }
                    }
                }

                // Handle horizontal swipe to close
                if (mIsSwipeGesture && deltaX > 0) {
                    // Check if finger has exited the view bounds to the right
                    if (localX > getWidth()) {
                        Logger.logDebug(LOG_TAG, "Finger exited view, collapsing panel");
                        triggerCollapse();
                        return true;
                    }

                    // Check velocity
                    mVelocityTracker.computeCurrentVelocity(1000);
                    float velocityX = mVelocityTracker.getXVelocity();
                    if (velocityX > SWIPE_VELOCITY_THRESHOLD) {
                        Logger.logDebug(LOG_TAG, "Swipe velocity threshold reached: " + velocityX);
                        triggerCollapse();
                        return true;
                    }
                }

                // Handle vertical scroll - pass to terminal with multiplier for fast scrolling
                if (mIsScrollGesture && mTerminalView != null) {
                    if (TermuxFloatSettingsActivity.isFastScrollEnabled(getContext())) {
                        int multiplier = TermuxFloatSettingsActivity.getFastScrollMultiplier(getContext());
                        int scrollAmount = (int) (moveY * multiplier);
                        mTerminalView.scrollBy(0, -scrollAmount);
                    } else {
                        mTerminalView.scrollBy(0, (int) -moveY);
                    }
                }

                return true;

            case MotionEvent.ACTION_UP:
                cancelLongPress();
                float finalDeltaX = event.getRawX() - mStartX;

                // If long pressed, handle release
                if (mIsLongPressed) {
                    mIsLongPressed = false;
                    mIsTouched = false;
                    invalidate();
                    // Turn off long press mode
                    if (mTermuxFloatView != null) {
                        mTermuxFloatView.updateLongPressMode(false);
                    }
                    cleanupVelocityTracker();
                    return true;
                }

                // Check for swipe on release - if moved right significantly
                if (mIsSwipeGesture && finalDeltaX > getWidth() * 0.5f) {
                    Logger.logDebug(LOG_TAG, "Swipe completed on release, deltaX=" + finalDeltaX);
                    triggerCollapse();
                    return true;
                }

                // Fall through to cleanup
            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                mIsTouched = false;
                mIsLongPressed = false;
                invalidate();

                // If it was just a tap (no gesture detected), show keyboard
                if (!mIsSwipeGesture && !mIsScrollGesture && event.getAction() == MotionEvent.ACTION_UP) {
                    float tapDeltaX = Math.abs(event.getRawX() - mStartX);
                    float tapDeltaY = Math.abs(event.getRawY() - mStartY);
                    if (tapDeltaX < mTouchSlop && tapDeltaY < mTouchSlop) {
                        Logger.logDebug(LOG_TAG, "Tap detected, focusing terminal");
                        if (mTerminalView != null) {
                            mTerminalView.requestFocus();
                        }
                    }
                }

                mIsSwipeGesture = false;
                mIsScrollGesture = false;
                cleanupVelocityTracker();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void cancelLongPress() {
        if (mLongPressRunnable != null) {
            mHandler.removeCallbacks(mLongPressRunnable);
            mLongPressRunnable = null;
        }
    }

    private void toggleLongPressMode() {
        if (mTermuxFloatView != null) {
            // Toggle the long press mode
            boolean currentState = mTermuxFloatView.isInLongPressState;
            mTermuxFloatView.updateLongPressMode(!currentState);
            Logger.logDebug(LOG_TAG, "Toggled long press mode to: " + !currentState);
        }
    }

    private void triggerCollapse() {
        cancelLongPress();
        mIsTouched = false;
        mIsLongPressed = false;
        invalidate();
        mIsSwipeGesture = false;
        mIsScrollGesture = false;
        cleanupVelocityTracker();

        if (mEdgePanelManager != null) {
            mEdgePanelManager.collapse();
        }
    }

    private void cleanupVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }
}
