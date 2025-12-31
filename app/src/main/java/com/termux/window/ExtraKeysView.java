package com.termux.window;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.termux.shared.view.ViewUtils;
import com.termux.view.TerminalView;

/**
 * A row of extra keys (ESC, CTRL, ALT, TAB, arrows, etc.) that appears above the keyboard.
 * Provides quick access to special keys commonly needed in terminal sessions.
 */
public class ExtraKeysView extends HorizontalScrollView {
    private static final String LOG_TAG = "ExtraKeysView";

    private static final int KEY_HEIGHT_DP = 40;
    private static final int KEY_MIN_WIDTH_DP = 48;
    private static final int KEY_PADDING_DP = 4;

    private LinearLayout mKeysContainer;
    private TerminalView mTerminalView;

    // Modifier key states
    private boolean mCtrlPressed = false;
    private boolean mAltPressed = false;
    private boolean mShiftPressed = false;

    // Modifier key buttons (for visual feedback)
    private Button mCtrlButton;
    private Button mAltButton;
    private Button mShiftButton;

    public ExtraKeysView(Context context) {
        super(context);
        init(context);
    }

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ExtraKeysView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setBackgroundColor(Color.parseColor("#1a1a1a"));

        mKeysContainer = new LinearLayout(context);
        mKeysContainer.setOrientation(LinearLayout.HORIZONTAL);
        int padding = (int) ViewUtils.dpToPx(context, KEY_PADDING_DP);
        mKeysContainer.setPadding(padding, padding, padding, padding);

        // Add the keys
        addKey("ESC", KeyEvent.KEYCODE_ESCAPE, false);
        mCtrlButton = addModifierKey("CTRL", ModifierType.CTRL);
        mAltButton = addModifierKey("ALT", ModifierType.ALT);
        mShiftButton = addModifierKey("SHIFT", ModifierType.SHIFT);
        addKey("TAB", KeyEvent.KEYCODE_TAB, false);
        addKey("─", KeyEvent.KEYCODE_MINUS, false); // dash
        addKey("/", KeyEvent.KEYCODE_SLASH, false);
        addKey("|", KeyEvent.KEYCODE_BACKSLASH, true); // pipe (shift+backslash)
        addKey("↑", KeyEvent.KEYCODE_DPAD_UP, false);
        addKey("↓", KeyEvent.KEYCODE_DPAD_DOWN, false);
        addKey("←", KeyEvent.KEYCODE_DPAD_LEFT, false);
        addKey("→", KeyEvent.KEYCODE_DPAD_RIGHT, false);
        addKey("HOME", KeyEvent.KEYCODE_MOVE_HOME, false);
        addKey("END", KeyEvent.KEYCODE_MOVE_END, false);
        addKey("PGUP", KeyEvent.KEYCODE_PAGE_UP, false);
        addKey("PGDN", KeyEvent.KEYCODE_PAGE_DOWN, false);

        addView(mKeysContainer);
    }

    /**
     * Set the terminal view to send key events to.
     */
    public void setTerminalView(TerminalView terminalView) {
        mTerminalView = terminalView;
    }

    private enum ModifierType {
        CTRL, ALT, SHIFT
    }

    /**
     * Add a regular key button.
     */
    private Button addKey(String label, int keyCode, boolean withShift) {
        Context context = getContext();
        Button button = createKeyButton(context, label);

        button.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            sendKey(keyCode, withShift);
        });

        // Long press for repeat
        button.setOnLongClickListener(v -> {
            // For arrow keys, allow repeat
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                sendKey(keyCode, withShift);
            }
            return true;
        });

        mKeysContainer.addView(button);
        return button;
    }

    /**
     * Add a modifier key button (CTRL, ALT, SHIFT).
     */
    private Button addModifierKey(String label, ModifierType type) {
        Context context = getContext();
        Button button = createKeyButton(context, label);

        button.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            toggleModifier(type, button);
        });

        mKeysContainer.addView(button);
        return button;
    }

    /**
     * Create a styled key button.
     */
    private Button createKeyButton(Context context, String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.parseColor("#333333"));

        int heightPx = (int) ViewUtils.dpToPx(context, KEY_HEIGHT_DP);
        int minWidthPx = (int) ViewUtils.dpToPx(context, KEY_MIN_WIDTH_DP);
        int marginPx = (int) ViewUtils.dpToPx(context, 2);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, heightPx);
        params.setMargins(marginPx, 0, marginPx, 0);
        button.setLayoutParams(params);
        button.setMinWidth(minWidthPx);
        button.setMinimumWidth(minWidthPx);
        button.setPadding(marginPx * 2, 0, marginPx * 2, 0);

        return button;
    }

    /**
     * Toggle a modifier key state.
     */
    private void toggleModifier(ModifierType type, Button button) {
        boolean newState;
        switch (type) {
            case CTRL:
                mCtrlPressed = !mCtrlPressed;
                newState = mCtrlPressed;
                break;
            case ALT:
                mAltPressed = !mAltPressed;
                newState = mAltPressed;
                break;
            case SHIFT:
                mShiftPressed = !mShiftPressed;
                newState = mShiftPressed;
                break;
            default:
                return;
        }

        // Update visual state
        if (newState) {
            button.setBackgroundColor(Color.parseColor("#03dac6"));
            button.setTextColor(Color.BLACK);
        } else {
            button.setBackgroundColor(Color.parseColor("#333333"));
            button.setTextColor(Color.WHITE);
        }
    }

    /**
     * Send a key event to the terminal.
     */
    private void sendKey(int keyCode, boolean withShift) {
        if (mTerminalView == null) return;

        // Build meta state from modifier keys
        int metaState = 0;
        if (mCtrlPressed) metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        if (mAltPressed) metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
        if (mShiftPressed || withShift) metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;

        long now = System.currentTimeMillis();

        // Send key down
        KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState);
        mTerminalView.onKeyDown(keyCode, downEvent);

        // Send key up
        KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState);
        mTerminalView.onKeyUp(keyCode, upEvent);

        // Reset modifier keys after use (one-shot behavior)
        resetModifiers();
    }

    /**
     * Reset all modifier keys to unpressed state.
     */
    private void resetModifiers() {
        if (mCtrlPressed) {
            mCtrlPressed = false;
            mCtrlButton.setBackgroundColor(Color.parseColor("#333333"));
            mCtrlButton.setTextColor(Color.WHITE);
        }
        if (mAltPressed) {
            mAltPressed = false;
            mAltButton.setBackgroundColor(Color.parseColor("#333333"));
            mAltButton.setTextColor(Color.WHITE);
        }
        if (mShiftPressed) {
            mShiftPressed = false;
            mShiftButton.setBackgroundColor(Color.parseColor("#333333"));
            mShiftButton.setTextColor(Color.WHITE);
        }
    }

    /**
     * Check if any modifier is currently pressed.
     */
    public boolean hasModifiersPressed() {
        return mCtrlPressed || mAltPressed || mShiftPressed;
    }
}
