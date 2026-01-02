package com.termux.window;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;
import com.termux.window.settings.properties.TermuxFloatAppSharedProperties;

public class TermuxFloatView extends LinearLayout implements EdgeSwipeDetector.EdgeSwipeListener {

    public static final float ALPHA_FOCUS = 0.9f;
    public static final float ALPHA_NOT_FOCUS = 0.7f;
    public static final float ALPHA_MOVING = 0.5f;

    // Left edge swipe detection zone width in dp
    private static final int LEFT_EDGE_ZONE_DP = 40;
    private static final int SWIPE_THRESHOLD_DP = 80;

    private int DISPLAY_WIDTH, DISPLAY_HEIGHT;

    final WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
    WindowManager mWindowManager;

    private TerminalView mTerminalView;
    ViewGroup mWindowControls;
    FloatingBubbleManager mFloatingBubbleManager;

    // Edge panel mode support
    private EdgeSwipeDetector mEdgeSwipeDetector;
    private boolean mUseSidePanelMode = false;
    private EdgePanelManager mEdgePanelManager;

    // Swipe tracking for close gesture
    private boolean mTrackingLeftEdgeSwipe = false;
    private float mSwipeStartX;
    private float mSwipeStartY;
    private int mLeftEdgeZonePx;
    private int mSwipeThresholdPx;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxFloatView}.
     */
    TermuxFloatViewClient mTermuxFloatViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxFloatService}.
     */
    TermuxFloatSessionClient mTermuxFloatSessionClient;

    /**
     * Termux Float app shared preferences manager.
     */
    private TermuxFloatAppSharedPreferences mPreferences;

    /**
     * Termux app shared properties manager, loaded from termux.properties
     */
    private TermuxFloatAppSharedProperties mProperties;

    private boolean withFocus = true;
    int initialX;
    int initialY;
    float initialTouchX;
    float initialTouchY;

    boolean isInLongPressState;

    final int[] location = new int[2];

    final int[] windowControlsLocation = new int[2];

    private static final String LOG_TAG = "TermuxFloatView";

    final ScaleGestureDetector mScaleDetector = new ScaleGestureDetector(getContext(), new OnScaleGestureListener() {
        private static final int MIN_SIZE = 50;

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            int widthChange = (int) (detector.getCurrentSpanX() - detector.getPreviousSpanX());
            int heightChange = (int) (detector.getCurrentSpanY() - detector.getPreviousSpanY());
            layoutParams.width += widthChange;
            layoutParams.height += heightChange;
            layoutParams.width = Math.max(MIN_SIZE, layoutParams.width);
            layoutParams.height = Math.max(MIN_SIZE, layoutParams.height);
            mWindowManager.updateViewLayout(TermuxFloatView.this, layoutParams);
            if (mPreferences != null) {
                mPreferences.setWindowWidth(layoutParams.width);
                mPreferences.setWindowHeight(layoutParams.height);
            }
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            // Do nothing.
        }
    });

    public TermuxFloatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setAlpha(ALPHA_FOCUS);
    }

    private static int computeLayoutFlags(boolean withFocus) {
        if (withFocus) {
            return 0;
        } else {
            return WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
    }

    public void initFloatView(TermuxFloatService service) {
        Logger.logDebug(LOG_TAG, "initFloatView");

        // Load termux shared properties
        mProperties = new TermuxFloatAppSharedProperties(getContext());

        // Load termux float shared preferences
        // This will also fail if TermuxConstants.TERMUX_FLOAT_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxFloatAppSharedPreferences.build(getContext(), true);
        if (mPreferences == null) {
            return;
        }

        mTermuxFloatSessionClient = new TermuxFloatSessionClient(service, this);

        mTerminalView = findViewById(R.id.terminal_view);
        mTermuxFloatViewClient = new TermuxFloatViewClient(this, mTermuxFloatSessionClient);
        mTerminalView.setTerminalViewClient(mTermuxFloatViewClient);
        mTermuxFloatViewClient.initFloatView();

        // Check if we're using the new side panel layout or the old floating window layout
        mWindowControls = findViewById(R.id.window_controls);
        if (mWindowControls != null) {
            // Old floating window mode
            mUseSidePanelMode = false;
            mFloatingBubbleManager = new FloatingBubbleManager(this);
            initWindowControls();
        } else {
            // New side panel mode
            mUseSidePanelMode = true;
            mEdgeSwipeDetector = new EdgeSwipeDetector(getContext(), this);

            // Initialize swipe detection thresholds in pixels
            float density = getContext().getResources().getDisplayMetrics().density;
            mLeftEdgeZonePx = (int) (LEFT_EDGE_ZONE_DP * density);
            mSwipeThresholdPx = (int) (SWIPE_THRESHOLD_DP * density);

            // Set up the swipe margin view
            SwipeMarginView swipeMargin = findViewById(R.id.swipe_margin);
            if (swipeMargin != null) {
                swipeMargin.setTerminalView(mTerminalView);
                // EdgePanelManager will be set later via setEdgePanelManager()
            }

            // Set up extra keys view
            ExtraKeysView extraKeys = findViewById(R.id.extra_keys);
            if (extraKeys != null) {
                extraKeys.setTerminalView(mTerminalView);
            }
        }
    }

    private void initWindowControls() {
        if (mWindowControls == null) return;

        mWindowControls.setOnClickListener(v -> changeFocus(true));

        Button minimizeButton = findViewById(R.id.minimize_button);
        if (minimizeButton != null) {
            minimizeButton.setOnClickListener(v -> {
                if (mFloatingBubbleManager != null) {
                    mFloatingBubbleManager.toggleBubble();
                }
            });
        }

        Button exitButton = findViewById(R.id.exit_button);
        if (exitButton != null) {
            exitButton.setOnClickListener(v -> exit());
        }
    }

    /**
     * Set the edge panel manager for side panel mode.
     */
    public void setEdgePanelManager(EdgePanelManager manager) {
        mEdgePanelManager = manager;

        // Also set on the swipe margin view
        SwipeMarginView swipeMargin = findViewById(R.id.swipe_margin);
        if (swipeMargin != null) {
            swipeMargin.setEdgePanelManager(manager);
        }
    }

    /**
     * Check if using side panel mode.
     */
    public boolean isUsingSidePanelMode() {
        return mUseSidePanelMode;
    }

    // EdgeSwipeListener implementation
    @Override
    public boolean onSwipeInward() {
        if (mEdgePanelManager != null) {
            mEdgePanelManager.expand();
            return true;
        }
        return false;
    }

    @Override
    public boolean onSwipeOutward() {
        if (mEdgePanelManager != null) {
            mEdgePanelManager.collapse();
            return true;
        }
        return false;
    }

    @Override
    public void onTap() {
        changeFocus(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        Point displaySize = new Point();
        getDisplay().getSize(displaySize);
        DISPLAY_WIDTH = displaySize.x;
        DISPLAY_HEIGHT = displaySize.y;

        if (mTermuxFloatSessionClient != null)
            mTermuxFloatSessionClient.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mTermuxFloatSessionClient != null)
            mTermuxFloatSessionClient.onDetachedFromWindow();
    }

    @SuppressLint("RtlHardcoded")
    public void launchFloatingWindow() {
        int widthAndHeight = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.flags = computeLayoutFlags(true);
        layoutParams.width = widthAndHeight;
        layoutParams.height = widthAndHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            // Tell the system not to animate this window during rotation
            // This helps prevent AsyncRotationController crashes
            layoutParams.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_JUMPCUT;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        layoutParams.format = PixelFormat.RGBA_8888;

        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;

        if (mPreferences != null) {
            layoutParams.x = mPreferences.getWindowX();
            layoutParams.y = mPreferences.getWindowY();
            layoutParams.width = mPreferences.getWindowWidth();
            layoutParams.height = mPreferences.getWindowHeight();
        }

        mWindowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        if (getWindowToken() == null)
            mWindowManager.addView(this, layoutParams);
        showTouchKeyboard();
    }

    /**
     * Intercept touch events to obtain and loose focus on touch events.
     * In side panel mode, intercepts touches in left edge zone for swipe-to-close gesture.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        // In side panel mode, check for left edge swipe to close
        if (mUseSidePanelMode && mEdgePanelManager != null) {
            float touchX = event.getX();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Check if touch started in left edge zone
                    if (touchX <= mLeftEdgeZonePx) {
                        mTrackingLeftEdgeSwipe = true;
                        mSwipeStartX = event.getRawX();
                        mSwipeStartY = event.getRawY();
                        Logger.logDebug(LOG_TAG, "Left edge touch detected at x=" + touchX);
                        // Intercept to prevent keyboard from opening
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mTrackingLeftEdgeSwipe) {
                        return true; // Continue intercepting
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (mTrackingLeftEdgeSwipe) {
                        mTrackingLeftEdgeSwipe = false;
                        return true;
                    }
                    break;
            }
        }

        if (isInLongPressState) return true;

        getLocationOnScreen(location);
        int x = location[0];
        int y = location[1];
        float touchX = event.getRawX();
        float touchY = event.getRawY();

        if (didClickInsideWindowControls(touchX, touchY)) {
            // avoid unintended focus event if we are tapping on our window controls
            // so that keyboard doesn't possibly show briefly
            return false;
        }

        boolean clickedInside = (touchX >= x) && (touchX <= (x + layoutParams.width)) && (touchY >= y) && (touchY <= (y + layoutParams.height));

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!clickedInside) changeFocus(false);
                break;
            case MotionEvent.ACTION_UP:
                if (clickedInside) {
                    changeFocus(true);
                    showTouchKeyboard();
                }
                break;
        }
        return false;
    }

    private boolean didClickInsideWindowControls(float touchX, float touchY) {
        if (mWindowControls == null || mWindowControls.getVisibility() == View.GONE) {
            return false;
        }
        mWindowControls.getLocationOnScreen(windowControlsLocation);
        int controlsX = windowControlsLocation[0];
        int controlsY = windowControlsLocation[1];

        return (touchX >= controlsX && touchX <= controlsX + mWindowControls.getWidth()) &&
                (touchY >= controlsY && touchY <= controlsY + mWindowControls.getHeight());
    }

    void showTouchKeyboard() {
        mTerminalView.post(() -> KeyboardUtils.showSoftKeyboard(getContext(), mTerminalView));

    }

    void hideTouchKeyboard() {
        mTerminalView.post(() -> KeyboardUtils.hideSoftKeyboard(getContext(), mTerminalView));
    }

    void updateLongPressMode(boolean newValue) {
        isInLongPressState = newValue;
        if (mFloatingBubbleManager != null) {
            mFloatingBubbleManager.updateLongPressBackgroundResource(isInLongPressState);
        }
        setAlpha(newValue ? ALPHA_MOVING : (withFocus ? ALPHA_FOCUS : ALPHA_NOT_FOCUS));
        if (newValue && mFloatingBubbleManager != null && !mFloatingBubbleManager.isMinimized()) {
            Logger.showToast(getContext(), getContext().getString(R.string.after_long_press), false);
        }
    }

    /**
     * Motion events should only be dispatched here when {@link #onInterceptTouchEvent(MotionEvent)} returns true.
     */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Handle left edge swipe gesture for closing panel
        if (mTrackingLeftEdgeSwipe && mUseSidePanelMode && mEdgePanelManager != null) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - mSwipeStartX;
                    float deltaY = Math.abs(event.getRawY() - mSwipeStartY);

                    // Check if this is a horizontal swipe (not vertical)
                    if (deltaX > mSwipeThresholdPx && deltaX > deltaY * 2) {
                        Logger.logDebug(LOG_TAG, "Swipe-to-close detected, deltaX=" + deltaX);
                        mTrackingLeftEdgeSwipe = false;
                        mEdgePanelManager.collapse();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // Swipe didn't reach threshold - treat as tap
                    float swipeDistance = event.getRawX() - mSwipeStartX;
                    if (swipeDistance < mSwipeThresholdPx / 4) {
                        // This was just a tap, not a swipe - show keyboard
                        Logger.logDebug(LOG_TAG, "Left edge tap detected, showing keyboard");
                        changeFocus(true);
                        showTouchKeyboard();
                    }
                    mTrackingLeftEdgeSwipe = false;
                    return true;
            }
            return true;
        }

        if (isInLongPressState) {
            mScaleDetector.onTouchEvent(event);
            if (mScaleDetector.isInProgress()) return true;
            switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    layoutParams.x = Math.min(DISPLAY_WIDTH - layoutParams.width, Math.max(0, initialX + (int) (event.getRawX() - initialTouchX)));
                    layoutParams.y = Math.min(DISPLAY_HEIGHT - layoutParams.height, Math.max(0, initialY + (int) (event.getRawY() - initialTouchY)));
                    mWindowManager.updateViewLayout(TermuxFloatView.this, layoutParams);
                    if (mPreferences != null) {
                        mPreferences.setWindowX(layoutParams.x);
                        mPreferences.setWindowY(layoutParams.y);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    updateLongPressMode(false);
                    break;
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    /**
     * Visually indicate focus and show the soft input as needed.
     */
    void changeFocus(boolean newFocus) {
        if (newFocus && mFloatingBubbleManager != null && mFloatingBubbleManager.isMinimized()) {
            mFloatingBubbleManager.displayAsFloatingWindow();
        }
        if (newFocus == withFocus) {
            if (newFocus) showTouchKeyboard();
            return;
        }
        withFocus = newFocus;
        layoutParams.flags = computeLayoutFlags(withFocus);
        if (getWindowToken() != null)
            mWindowManager.updateViewLayout(this, layoutParams);
        setAlpha(newFocus ? ALPHA_FOCUS : ALPHA_NOT_FOCUS);
    }

    public void closeFloatingWindow() {
        if (getWindowToken() != null)
            mWindowManager.removeView(this);

        if (mFloatingBubbleManager != null) {
            mFloatingBubbleManager.cleanup();
            mFloatingBubbleManager = null;
        }

        if (mEdgePanelManager != null) {
            mEdgePanelManager.cleanup();
            mEdgePanelManager = null;
        }
    }

    private void exit() {
        Intent exitIntent = new Intent(getContext(), TermuxFloatService.class).setAction(TermuxConstants.TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE.ACTION_STOP_SERVICE);
        getContext().startService(exitIntent);
    }



    public boolean isVisible() {
        return isAttachedToWindow() && isShown();
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxFloatViewClient getTermuxFloatViewClient() {
        return mTermuxFloatViewClient;
    }

    public TermuxFloatSessionClient getTermuxFloatSessionClient() {
        return mTermuxFloatSessionClient;
    }

    public TermuxFloatAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxFloatAppSharedProperties getProperties() {
        return mProperties;
    }


    public void reloadViewStyling() {
        // Leaving here for future support for termux-reload-settings
        if (mTermuxFloatSessionClient != null)
            mTermuxFloatSessionClient.onReload();
    }
}
