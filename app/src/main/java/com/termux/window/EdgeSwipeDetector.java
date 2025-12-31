package com.termux.window;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.termux.shared.logger.Logger;
import com.termux.shared.view.ViewUtils;

/**
 * Detects edge swipe gestures for the side panel.
 * Handles both the edge-to-expand gesture (swipe inward from right edge)
 * and the collapse gesture (swipe outward from left edge of expanded panel).
 */
public class EdgeSwipeDetector implements View.OnTouchListener {
    private static final String LOG_TAG = "EdgeSwipeDetector";

    // Gesture thresholds
    private static final int SWIPE_THRESHOLD_DP = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private static final int LEFT_EDGE_ZONE_DP = 40;

    private final Context mContext;
    private final GestureDetector mGestureDetector;
    private final EdgeSwipeListener mListener;

    // Thresholds in pixels
    private final int mSwipeThresholdPx;
    private final int mLeftEdgeZonePx;

    // Touch tracking
    private float mStartX;
    private float mStartY;
    private boolean mIsTracking = false;

    public interface EdgeSwipeListener {
        /**
         * Called when user swipes inward from the right edge.
         * @return true if the event was handled
         */
        boolean onSwipeInward();

        /**
         * Called when user swipes outward from the left edge.
         * @return true if the event was handled
         */
        boolean onSwipeOutward();

        /**
         * Called when user taps on the panel (for focus handling).
         */
        void onTap();
    }

    public EdgeSwipeDetector(Context context, EdgeSwipeListener listener) {
        mContext = context;
        mListener = listener;

        mSwipeThresholdPx = (int) ViewUtils.dpToPx(context, SWIPE_THRESHOLD_DP);
        mLeftEdgeZonePx = (int) ViewUtils.dpToPx(context, LEFT_EDGE_ZONE_DP);

        mGestureDetector = new GestureDetector(context, new SwipeGestureListener());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // Let gesture detector handle flings
        mGestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mStartX = event.getRawX();
                mStartY = event.getRawY();
                mIsTracking = true;
                return false; // Don't consume, let children handle

            case MotionEvent.ACTION_MOVE:
                if (!mIsTracking) return false;
                // Could add visual feedback here (e.g., panel peeking)
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsTracking) {
                    mIsTracking = false;
                    float deltaX = event.getRawX() - mStartX;
                    float deltaY = event.getRawY() - mStartY;

                    // Check if this is a horizontal swipe (more horizontal than vertical)
                    if (Math.abs(deltaX) > Math.abs(deltaY)) {
                        // Check for outward swipe from left edge
                        if (mStartX < mLeftEdgeZonePx && deltaX > mSwipeThresholdPx) {
                            Logger.logDebug(LOG_TAG, "Swipe outward detected");
                            if (mListener != null) {
                                return mListener.onSwipeOutward();
                            }
                        }
                    }
                }
                return false;
        }

        return false;
    }

    /**
     * Check if a touch event started in the left edge zone.
     */
    public boolean isInLeftEdgeZone(float x) {
        return x < mLeftEdgeZonePx;
    }

    /**
     * Gesture listener for fling detection.
     */
    private class SwipeGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true; // Must return true to receive subsequent events
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (mListener != null) {
                mListener.onTap();
            }
            return false;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;

            float deltaX = e2.getX() - e1.getX();
            float deltaY = e2.getY() - e1.getY();

            // Check if horizontal fling
            if (Math.abs(deltaX) > Math.abs(deltaY) &&
                Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {

                // Fling right (outward) from left edge
                if (e1.getRawX() < mLeftEdgeZonePx && deltaX > mSwipeThresholdPx && velocityX > 0) {
                    Logger.logDebug(LOG_TAG, "Fling outward detected");
                    if (mListener != null) {
                        return mListener.onSwipeOutward();
                    }
                }

                // Fling left (inward) - this is handled by EdgePanelManager on the edge indicator
            }

            return false;
        }
    }
}
