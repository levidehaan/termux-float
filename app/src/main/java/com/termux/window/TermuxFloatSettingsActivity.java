package com.termux.window;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.shared.view.ViewUtils;

/**
 * Settings activity for Termux Float customization.
 */
public class TermuxFloatSettingsActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "termux_float_settings";

    // Preference keys
    public static final String KEY_SWIPE_MARGIN_WIDTH = "swipe_margin_width";
    public static final String KEY_TERMINAL_BG_COLOR = "terminal_bg_color";
    public static final String KEY_TAB_BAR_BG_COLOR = "tab_bar_bg_color";
    public static final String KEY_SWIPE_MARGIN_COLOR = "swipe_margin_color";
    public static final String KEY_FAST_SCROLL_ENABLED = "fast_scroll_enabled";
    public static final String KEY_FAST_SCROLL_MULTIPLIER = "fast_scroll_multiplier";

    // Default values
    public static final int DEFAULT_SWIPE_MARGIN_WIDTH = 24;
    public static final int DEFAULT_TERMINAL_BG_COLOR = Color.BLACK;
    public static final int DEFAULT_TAB_BAR_BG_COLOR = Color.parseColor("#1a1a1a");
    public static final int DEFAULT_SWIPE_MARGIN_COLOR = Color.parseColor("#0d0d0d");
    public static final boolean DEFAULT_FAST_SCROLL_ENABLED = true;
    public static final int DEFAULT_FAST_SCROLL_MULTIPLIER = 3;

    private SharedPreferences mPrefs;

    // UI elements
    private SeekBar mMarginWidthSeekBar;
    private TextView mMarginWidthValue;
    private Switch mFastScrollSwitch;
    private SeekBar mScrollMultiplierSeekBar;
    private TextView mScrollMultiplierValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Build UI programmatically
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#121212"));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

        // Title
        TextView title = new TextView(this);
        title.setText("Termux Float Settings");
        title.setTextSize(24);
        title.setTextColor(Color.WHITE);
        title.setPadding(0, 0, 0, dpToPx(24));
        container.addView(title);

        // Swipe Margin Section
        container.addView(createSectionHeader("Swipe Margin"));
        container.addView(createSwipeMarginSettings());

        // Fast Scroll Section
        container.addView(createSectionHeader("Fast Scrolling"));
        container.addView(createFastScrollSettings());

        // Theme Section
        container.addView(createSectionHeader("Theme"));
        container.addView(createThemeInfo());

        scrollView.addView(container);
        setContentView(scrollView);
    }

    private View createSectionHeader(String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextSize(18);
        header.setTextColor(Color.parseColor("#03dac6"));
        header.setPadding(0, dpToPx(16), 0, dpToPx(8));
        return header;
    }

    private View createSwipeMarginSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1e1e1e"));
        layout.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        // Label
        TextView label = new TextView(this);
        label.setText("Margin Width (dp)");
        label.setTextColor(Color.WHITE);
        layout.addView(label);

        // SeekBar with value display
        LinearLayout seekLayout = new LinearLayout(this);
        seekLayout.setOrientation(LinearLayout.HORIZONTAL);
        seekLayout.setPadding(0, dpToPx(8), 0, 0);

        mMarginWidthSeekBar = new SeekBar(this);
        mMarginWidthSeekBar.setMax(48); // 8 to 56 dp
        mMarginWidthSeekBar.setProgress(mPrefs.getInt(KEY_SWIPE_MARGIN_WIDTH, DEFAULT_SWIPE_MARGIN_WIDTH) - 8);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mMarginWidthSeekBar.setLayoutParams(seekParams);

        mMarginWidthValue = new TextView(this);
        mMarginWidthValue.setText(String.valueOf(mMarginWidthSeekBar.getProgress() + 8));
        mMarginWidthValue.setTextColor(Color.WHITE);
        mMarginWidthValue.setPadding(dpToPx(8), 0, 0, 0);

        mMarginWidthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 8;
                mMarginWidthValue.setText(String.valueOf(value));
                if (fromUser) {
                    mPrefs.edit().putInt(KEY_SWIPE_MARGIN_WIDTH, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekLayout.addView(mMarginWidthSeekBar);
        seekLayout.addView(mMarginWidthValue);
        layout.addView(seekLayout);

        // Description
        TextView desc = new TextView(this);
        desc.setText("Width of the left swipe margin for closing the panel");
        desc.setTextColor(Color.parseColor("#888888"));
        desc.setTextSize(12);
        desc.setPadding(0, dpToPx(4), 0, 0);
        layout.addView(desc);

        return layout;
    }

    private View createFastScrollSettings() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1e1e1e"));
        layout.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        // Enable fast scroll switch
        LinearLayout switchLayout = new LinearLayout(this);
        switchLayout.setOrientation(LinearLayout.HORIZONTAL);

        TextView switchLabel = new TextView(this);
        switchLabel.setText("Enable Fast Scroll");
        switchLabel.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        switchLabel.setLayoutParams(labelParams);

        mFastScrollSwitch = new Switch(this);
        mFastScrollSwitch.setChecked(mPrefs.getBoolean(KEY_FAST_SCROLL_ENABLED, DEFAULT_FAST_SCROLL_ENABLED));
        mFastScrollSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean(KEY_FAST_SCROLL_ENABLED, isChecked).apply();
            mScrollMultiplierSeekBar.setEnabled(isChecked);
        });

        switchLayout.addView(switchLabel);
        switchLayout.addView(mFastScrollSwitch);
        layout.addView(switchLayout);

        // Scroll multiplier
        TextView multiplierLabel = new TextView(this);
        multiplierLabel.setText("Scroll Speed Multiplier");
        multiplierLabel.setTextColor(Color.WHITE);
        multiplierLabel.setPadding(0, dpToPx(12), 0, 0);
        layout.addView(multiplierLabel);

        LinearLayout multiplierLayout = new LinearLayout(this);
        multiplierLayout.setOrientation(LinearLayout.HORIZONTAL);
        multiplierLayout.setPadding(0, dpToPx(8), 0, 0);

        mScrollMultiplierSeekBar = new SeekBar(this);
        mScrollMultiplierSeekBar.setMax(9); // 1 to 10
        mScrollMultiplierSeekBar.setProgress(mPrefs.getInt(KEY_FAST_SCROLL_MULTIPLIER, DEFAULT_FAST_SCROLL_MULTIPLIER) - 1);
        mScrollMultiplierSeekBar.setEnabled(mFastScrollSwitch.isChecked());
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mScrollMultiplierSeekBar.setLayoutParams(seekParams);

        mScrollMultiplierValue = new TextView(this);
        mScrollMultiplierValue.setText(String.valueOf(mScrollMultiplierSeekBar.getProgress() + 1) + "x");
        mScrollMultiplierValue.setTextColor(Color.WHITE);
        mScrollMultiplierValue.setPadding(dpToPx(8), 0, 0, 0);

        mScrollMultiplierSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 1;
                mScrollMultiplierValue.setText(value + "x");
                if (fromUser) {
                    mPrefs.edit().putInt(KEY_FAST_SCROLL_MULTIPLIER, value).apply();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        multiplierLayout.addView(mScrollMultiplierSeekBar);
        multiplierLayout.addView(mScrollMultiplierValue);
        layout.addView(multiplierLayout);

        // Description
        TextView desc = new TextView(this);
        desc.setText("Vertical scroll in the left margin scrolls the terminal faster");
        desc.setTextColor(Color.parseColor("#888888"));
        desc.setTextSize(12);
        desc.setPadding(0, dpToPx(4), 0, 0);
        layout.addView(desc);

        return layout;
    }

    private View createThemeInfo() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.parseColor("#1e1e1e"));
        layout.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        TextView info = new TextView(this);
        info.setText("Theme customization coming soon!\n\n" +
                "Available in future update:\n" +
                "- Terminal background color\n" +
                "- Tab bar color\n" +
                "- Swipe margin color\n" +
                "- Accent color");
        info.setTextColor(Color.parseColor("#888888"));
        layout.addView(info);

        return layout;
    }

    private int dpToPx(int dp) {
        return (int) ViewUtils.dpToPx(this, dp);
    }

    /**
     * Get the swipe margin width from preferences.
     */
    public static int getSwipeMarginWidth(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_SWIPE_MARGIN_WIDTH, DEFAULT_SWIPE_MARGIN_WIDTH);
    }

    /**
     * Check if fast scroll is enabled.
     */
    public static boolean isFastScrollEnabled(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_FAST_SCROLL_ENABLED, DEFAULT_FAST_SCROLL_ENABLED);
    }

    /**
     * Get the fast scroll multiplier.
     */
    public static int getFastScrollMultiplier(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getInt(KEY_FAST_SCROLL_MULTIPLIER, DEFAULT_FAST_SCROLL_MULTIPLIER);
    }
}
