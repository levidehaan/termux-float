package com.termux.window;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import com.termux.shared.logger.Logger;
import com.termux.view.TerminalView;

/**
 * A dedicated left margin view for handling swipe-to-close gestures.
 * This view sits on the left edge of the side panel and provides a clear
 * touch zone for closing the panel without interfering with the terminal.
 *
 * Features:
 * - Swipe right to close the panel
 * - Vertical scroll to fast-scroll the terminal
 * - Visual feedback when touched
 */
public class SwipeMarginView extends View {
    private static final String LOG_TAG = "SwipeMarginView";

    // Swipe detection thresholds
    private static final int SWIPE_THRESHOLD_DP = 60;

    // Touch tracking
    private float mStartX;
    private float mStartY;
    private float mLastY;
    private boolean mIsSwipeGesture = false;
    private boolean mIsScrollGesture = false;

    // Pixel values (calculated from dp)
    private int mSwipeThresholdPx;
    private int mTouchSlop;

    // Visual feedback
    private Paint mHighlightPaint;
    private Paint mIndicatorPaint;
    private boolean mIsTouched = false;

    // References
    private EdgePanelManager mEdgePanelManager;
    private TerminalView mTerminalView;

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
        float density = context.getResources().getDisplayMetrics().density;
        mSwipeThresholdPx = (int) (SWIPE_THRESHOLD_DP * density);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        // Highlight paint for when touched
        mHighlightPaint = new Paint();
        mHighlightPaint.setColor(0x20ffffff); // Semi-transparent white
        mHighlightPaint.setStyle(Paint.Style.FILL);

        // Indicator paint for subtle visual cue
        mIndicatorPaint = new Paint();
        mIndicatorPaint.setColor(0x30ffffff);
        mIndicatorPaint.setStyle(Paint.Style.FILL);
        mIndicatorPaint.setAntiAlias(true);

        // Make view clickable to receive touch events
        setClickable(true);
        setFocusable(false);
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

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw subtle vertical line indicator on right edge
        float indicatorWidth = 2f;
        canvas.drawRect(
            getWidth() - indicatorWidth,
            0,
            getWidth(),
            getHeight(),
            mIndicatorPaint
        );

        // Draw highlight when touched
        if (mIsTouched) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), mHighlightPaint);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getRawX();
                mStartY = event.getRawY();
                mLastY = mStartY;
                mIsSwipeGesture = false;
                mIsScrollGesture = false;
                mIsTouched = true;
                invalidate();
                Logger.logDebug(LOG_TAG, "Touch down in swipe margin");
                return true;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getRawX() - mStartX;
                float deltaY = event.getRawY() - mStartY;
                float moveY = event.getRawY() - mLastY;
                mLastY = event.getRawY();

                // Determine gesture type if not yet decided
                if (!mIsSwipeGesture && !mIsScrollGesture) {
                    if (Math.abs(deltaX) > mTouchSlop || Math.abs(deltaY) > mTouchSlop) {
                        if (Math.abs(deltaX) > Math.abs(deltaY)) {
                            mIsSwipeGesture = true;
                            Logger.logDebug(LOG_TAG, "Detected horizontal swipe gesture");
                        } else {
                            mIsScrollGesture = true;
                            Logger.logDebug(LOG_TAG, "Detected vertical scroll gesture");
                        }
                    }
                }

                // Handle horizontal swipe to close
                if (mIsSwipeGesture) {
                    if (deltaX > mSwipeThresholdPx) {
                        Logger.logDebug(LOG_TAG, "Swipe threshold reached, collapsing panel");
                        mIsTouched = false;
                        invalidate();
                        if (mEdgePanelManager != null) {
                            mEdgePanelManager.collapse();
                        }
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
            case MotionEvent.ACTION_CANCEL:
                mIsTouched = false;
                invalidate();

                // If it was just a tap (no gesture detected), show keyboard
                if (!mIsSwipeGesture && !mIsScrollGesture) {
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
                return true;
        }
        return super.onTouchEvent(event);
    }
}
