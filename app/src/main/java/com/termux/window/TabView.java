package com.termux.window;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.termux.shared.view.ViewUtils;

/**
 * A view representing a single tab in the tab bar.
 * Shows the tab title, pause indicator, and close button.
 */
public class TabView extends LinearLayout {
    private static final int TAB_MIN_WIDTH_DP = 60;
    private static final int TAB_MAX_WIDTH_DP = 150;
    private static final int TAB_HEIGHT_DP = 36;
    private static final int TAB_PADDING_DP = 8;
    private static final int CLOSE_BUTTON_SIZE_DP = 16;

    private TextView mTitleView;
    private View mPauseIndicator;
    private ImageButton mCloseButton;

    private String mTabId;
    private boolean mIsActive;
    private boolean mIsPaused;

    private TabActionListener mListener;

    public interface TabActionListener {
        void onTabClicked(String tabId);
        void onTabCloseClicked(String tabId);
        void onTabLongClicked(String tabId, View anchorView);
    }

    public TabView(Context context) {
        super(context);
        init();
    }

    public TabView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TabView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        int paddingPx = (int) ViewUtils.dpToPx(getContext(), TAB_PADDING_DP);
        setPadding(paddingPx, paddingPx / 2, paddingPx / 2, paddingPx / 2);

        int minWidthPx = (int) ViewUtils.dpToPx(getContext(), TAB_MIN_WIDTH_DP);
        int maxWidthPx = (int) ViewUtils.dpToPx(getContext(), TAB_MAX_WIDTH_DP);
        int heightPx = (int) ViewUtils.dpToPx(getContext(), TAB_HEIGHT_DP);

        setMinimumWidth(minWidthPx);
        setMinimumHeight(heightPx);

        // Create pause indicator (small dot)
        mPauseIndicator = new View(getContext());
        int indicatorSize = (int) ViewUtils.dpToPx(getContext(), 6);
        LayoutParams indicatorParams = new LayoutParams(indicatorSize, indicatorSize);
        indicatorParams.setMarginEnd((int) ViewUtils.dpToPx(getContext(), 4));
        mPauseIndicator.setLayoutParams(indicatorParams);
        GradientDrawable indicatorBg = new GradientDrawable();
        indicatorBg.setShape(GradientDrawable.OVAL);
        indicatorBg.setColor(Color.parseColor("#FFA500")); // Orange for paused
        mPauseIndicator.setBackground(indicatorBg);
        mPauseIndicator.setVisibility(GONE);
        addView(mPauseIndicator);

        // Create title text
        mTitleView = new TextView(getContext());
        mTitleView.setTextColor(Color.WHITE);
        mTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mTitleView.setSingleLine(true);
        mTitleView.setMaxWidth(maxWidthPx - minWidthPx);
        LayoutParams titleParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        mTitleView.setLayoutParams(titleParams);
        addView(mTitleView);

        // Create close button
        int closeBtnSize = (int) ViewUtils.dpToPx(getContext(), CLOSE_BUTTON_SIZE_DP);
        mCloseButton = new ImageButton(getContext());
        LayoutParams closeParams = new LayoutParams(closeBtnSize, closeBtnSize);
        closeParams.setMarginStart((int) ViewUtils.dpToPx(getContext(), 4));
        mCloseButton.setLayoutParams(closeParams);
        mCloseButton.setBackgroundColor(Color.TRANSPARENT);
        mCloseButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        mCloseButton.setColorFilter(Color.parseColor("#888888"));
        mCloseButton.setScaleType(ImageButton.ScaleType.FIT_CENTER);
        mCloseButton.setOnClickListener(v -> {
            if (mListener != null && mTabId != null) {
                mListener.onTabCloseClicked(mTabId);
            }
        });
        addView(mCloseButton);

        // Set up click listeners
        setOnClickListener(v -> {
            if (mListener != null && mTabId != null) {
                mListener.onTabClicked(mTabId);
            }
        });

        setOnLongClickListener(v -> {
            if (mListener != null && mTabId != null) {
                mListener.onTabLongClicked(mTabId, this);
                return true;
            }
            return false;
        });

        // Apply default (inactive) style
        updateStyle();
    }

    /**
     * Set the tab data.
     */
    public void setTab(TerminalTab tab) {
        mTabId = tab.getId();
        mTitleView.setText(tab.getTitle());
        mIsPaused = tab.isPaused();
        mIsActive = tab.isActive();
        updateStyle();
    }

    /**
     * Set the tab title.
     */
    public void setTitle(String title) {
        mTitleView.setText(title);
    }

    /**
     * Set whether this tab is active.
     */
    public void setActive(boolean active) {
        mIsActive = active;
        updateStyle();
    }

    /**
     * Set whether this tab is paused.
     */
    public void setPaused(boolean paused) {
        mIsPaused = paused;
        mPauseIndicator.setVisibility(paused ? VISIBLE : GONE);
        updateStyle();
    }

    /**
     * Get the tab ID.
     */
    public String getTabId() {
        return mTabId;
    }

    /**
     * Set the action listener.
     */
    public void setListener(TabActionListener listener) {
        mListener = listener;
    }

    /**
     * Update the visual style based on state.
     */
    private void updateStyle() {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadii(new float[]{
                ViewUtils.dpToPx(getContext(), 4), ViewUtils.dpToPx(getContext(), 4),
                ViewUtils.dpToPx(getContext(), 4), ViewUtils.dpToPx(getContext(), 4),
                0, 0, 0, 0
        });

        if (mIsActive) {
            // Active tab - brighter background
            background.setColor(Color.parseColor("#444444"));
            mTitleView.setTextColor(Color.WHITE);
            mCloseButton.setColorFilter(Color.WHITE);
        } else if (mIsPaused) {
            // Paused tab - muted appearance
            background.setColor(Color.parseColor("#222222"));
            mTitleView.setTextColor(Color.parseColor("#888888"));
            mCloseButton.setColorFilter(Color.parseColor("#666666"));
        } else {
            // Inactive tab
            background.setColor(Color.parseColor("#333333"));
            mTitleView.setTextColor(Color.parseColor("#CCCCCC"));
            mCloseButton.setColorFilter(Color.parseColor("#888888"));
        }

        setBackground(background);
    }

    /**
     * Show the context menu for this tab.
     */
    public void showContextMenu(TabContextMenuListener menuListener) {
        PopupMenu popup = new PopupMenu(getContext(), this);

        if (mIsPaused) {
            popup.getMenu().add(0, 1, 0, "Resume");
        } else {
            popup.getMenu().add(0, 2, 0, "Pause");
        }

        popup.getMenu().add(0, 3, 0, "Never Pause");
        popup.getMenu().add(0, 4, 0, "Rename");
        popup.getMenu().add(0, 5, 0, "Close");

        popup.setOnMenuItemClickListener(item -> {
            if (menuListener != null) {
                switch (item.getItemId()) {
                    case 1:
                        menuListener.onResume(mTabId);
                        return true;
                    case 2:
                        menuListener.onPause(mTabId);
                        return true;
                    case 3:
                        menuListener.onToggleNeverPause(mTabId);
                        return true;
                    case 4:
                        menuListener.onRename(mTabId);
                        return true;
                    case 5:
                        menuListener.onClose(mTabId);
                        return true;
                }
            }
            return false;
        });

        popup.show();
    }

    public interface TabContextMenuListener {
        void onResume(String tabId);
        void onPause(String tabId);
        void onToggleNeverPause(String tabId);
        void onRename(String tabId);
        void onClose(String tabId);
    }
}
