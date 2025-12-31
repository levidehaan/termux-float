package com.termux.window;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.termux.shared.view.ViewUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A horizontally scrollable tab bar containing TabViews.
 * Includes a "new tab" button at the end.
 */
public class TabBarView extends LinearLayout {
    private static final String LOG_TAG = "TabBarView";
    private static final int TAB_BAR_HEIGHT_DP = 40;
    private static final int NEW_TAB_BUTTON_SIZE_DP = 32;

    private HorizontalScrollView mScrollView;
    private LinearLayout mTabContainer;
    private ImageButton mNewTabButton;
    private ImageButton mCollapseButton;

    private Map<String, TabView> mTabViews = new HashMap<>();

    private TabBarListener mListener;

    public interface TabBarListener {
        void onNewTabClicked();
        void onCollapseClicked();
        void onTabClicked(String tabId);
        void onTabCloseClicked(String tabId);
        void onTabLongClicked(String tabId, View anchorView);
    }

    public TabBarView(Context context) {
        super(context);
        init();
    }

    public TabBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TabBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setBackgroundColor(Color.parseColor("#1a1a1a"));

        int heightPx = (int) ViewUtils.dpToPx(getContext(), TAB_BAR_HEIGHT_DP);
        setMinimumHeight(heightPx);

        // Create scroll view for tabs
        mScrollView = new HorizontalScrollView(getContext());
        mScrollView.setHorizontalScrollBarEnabled(false);
        mScrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        LayoutParams scrollParams = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        mScrollView.setLayoutParams(scrollParams);

        // Create tab container inside scroll view
        mTabContainer = new LinearLayout(getContext());
        mTabContainer.setOrientation(HORIZONTAL);
        mTabContainer.setLayoutParams(new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        mScrollView.addView(mTabContainer);

        addView(mScrollView);

        // Create new tab button
        int btnSize = (int) ViewUtils.dpToPx(getContext(), NEW_TAB_BUTTON_SIZE_DP);
        int margin = (int) ViewUtils.dpToPx(getContext(), 4);

        mNewTabButton = new ImageButton(getContext());
        LayoutParams newTabParams = new LayoutParams(btnSize, btnSize);
        newTabParams.setMargins(margin, margin, margin, margin);
        mNewTabButton.setLayoutParams(newTabParams);
        mNewTabButton.setBackgroundColor(Color.parseColor("#333333"));
        mNewTabButton.setImageResource(android.R.drawable.ic_input_add);
        mNewTabButton.setColorFilter(Color.WHITE);
        mNewTabButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        mNewTabButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onNewTabClicked();
            }
        });
        addView(mNewTabButton);

        // Create collapse button
        mCollapseButton = new ImageButton(getContext());
        LayoutParams collapseParams = new LayoutParams(btnSize, btnSize);
        collapseParams.setMargins(0, margin, margin, margin);
        mCollapseButton.setLayoutParams(collapseParams);
        mCollapseButton.setBackgroundColor(Color.TRANSPARENT);
        mCollapseButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        mCollapseButton.setColorFilter(Color.parseColor("#888888"));
        mCollapseButton.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        mCollapseButton.setOnClickListener(v -> {
            if (mListener != null) {
                mListener.onCollapseClicked();
            }
        });
        addView(mCollapseButton);
    }

    /**
     * Set the tab bar listener.
     */
    public void setListener(TabBarListener listener) {
        mListener = listener;
    }

    /**
     * Add a tab to the bar.
     */
    public void addTab(TerminalTab tab) {
        TabView tabView = new TabView(getContext());
        tabView.setTab(tab);
        tabView.setListener(new TabView.TabActionListener() {
            @Override
            public void onTabClicked(String tabId) {
                if (mListener != null) {
                    mListener.onTabClicked(tabId);
                }
            }

            @Override
            public void onTabCloseClicked(String tabId) {
                if (mListener != null) {
                    mListener.onTabCloseClicked(tabId);
                }
            }

            @Override
            public void onTabLongClicked(String tabId, View anchorView) {
                if (mListener != null) {
                    mListener.onTabLongClicked(tabId, anchorView);
                }
            }
        });

        // Add margin between tabs
        LayoutParams params = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        int marginPx = (int) ViewUtils.dpToPx(getContext(), 2);
        params.setMargins(marginPx, 0, marginPx, 0);
        tabView.setLayoutParams(params);

        mTabContainer.addView(tabView);
        mTabViews.put(tab.getId(), tabView);

        // Scroll to show new tab
        mScrollView.post(() -> mScrollView.fullScroll(HorizontalScrollView.FOCUS_RIGHT));
    }

    /**
     * Remove a tab from the bar.
     */
    public void removeTab(String tabId) {
        TabView tabView = mTabViews.remove(tabId);
        if (tabView != null) {
            mTabContainer.removeView(tabView);
        }
    }

    /**
     * Update a specific tab's display.
     */
    public void updateTab(TerminalTab tab) {
        TabView tabView = mTabViews.get(tab.getId());
        if (tabView != null) {
            tabView.setTab(tab);
        }
    }

    /**
     * Set the active tab.
     */
    public void setActiveTab(String tabId) {
        for (Map.Entry<String, TabView> entry : mTabViews.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(tabId));
        }

        // Scroll to show active tab
        TabView activeTab = mTabViews.get(tabId);
        if (activeTab != null) {
            final int scrollX = activeTab.getLeft() -
                    (mScrollView.getWidth() - activeTab.getWidth()) / 2;
            mScrollView.post(() -> mScrollView.smoothScrollTo(scrollX, 0));
        }
    }

    /**
     * Update all tabs from a list.
     */
    public void updateTabs(List<TerminalTab> tabs) {
        // Remove tabs that no longer exist
        for (String tabId : mTabViews.keySet().toArray(new String[0])) {
            boolean found = false;
            for (TerminalTab tab : tabs) {
                if (tab.getId().equals(tabId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                removeTab(tabId);
            }
        }

        // Add or update tabs
        for (TerminalTab tab : tabs) {
            if (mTabViews.containsKey(tab.getId())) {
                updateTab(tab);
            } else {
                addTab(tab);
            }
        }
    }

    /**
     * Get the TabView for a specific tab.
     */
    public TabView getTabView(String tabId) {
        return mTabViews.get(tabId);
    }

    /**
     * Clear all tabs.
     */
    public void clearTabs() {
        mTabContainer.removeAllViews();
        mTabViews.clear();
    }

    /**
     * Get the number of tabs.
     */
    public int getTabCount() {
        return mTabViews.size();
    }
}
