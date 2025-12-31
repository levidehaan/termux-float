package com.termux.window;

import android.content.Context;

import com.termux.shared.logger.Logger;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages multiple terminal tabs including creation, switching, pausing, and cleanup.
 */
public class TabManager {
    private static final String LOG_TAG = "TabManager";

    // Configuration
    private static final int MAX_ACTIVE_SESSIONS = 5;
    private static final long INACTIVE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

    private final Context mContext;
    private final List<TerminalTab> mTabs;
    private int mActiveTabIndex = -1;

    // Listener for tab events
    private TabEventListener mListener;

    public interface TabEventListener {
        void onTabCreated(TerminalTab tab);
        void onTabActivated(TerminalTab tab);
        void onTabDeactivated(TerminalTab tab);
        void onTabClosed(TerminalTab tab);
        void onTabPaused(TerminalTab tab);
        void onTabResumed(TerminalTab tab);
        void onTabsReordered();
        void onActiveTabChanged(int oldIndex, int newIndex);
    }

    public TabManager(Context context) {
        mContext = context;
        mTabs = new ArrayList<>();
    }

    /**
     * Create a new tab and add it to the list.
     * @return The newly created tab
     */
    public TerminalTab createTab() {
        return createTab(null);
    }

    /**
     * Create a new tab with a specific title.
     * @param title The tab title (can be null for default)
     * @return The newly created tab
     */
    public TerminalTab createTab(String title) {
        TerminalTab tab = new TerminalTab(title);
        tab.setIndex(mTabs.size());
        mTabs.add(tab);

        Logger.logDebug(LOG_TAG, "Created tab: " + tab.getId());

        if (mListener != null) {
            mListener.onTabCreated(tab);
        }

        return tab;
    }

    /**
     * Add an existing tab to the manager.
     */
    public void addTab(TerminalTab tab) {
        tab.setIndex(mTabs.size());
        mTabs.add(tab);

        if (mListener != null) {
            mListener.onTabCreated(tab);
        }
    }

    /**
     * Switch to a specific tab by index.
     * @param index The tab index to switch to
     * @return true if switch was successful
     */
    public boolean switchToTab(int index) {
        if (index < 0 || index >= mTabs.size()) {
            Logger.logError(LOG_TAG, "Invalid tab index: " + index);
            return false;
        }

        if (index == mActiveTabIndex) {
            return true; // Already on this tab
        }

        int oldIndex = mActiveTabIndex;
        TerminalTab oldTab = getActiveTab();
        TerminalTab newTab = mTabs.get(index);

        // Deactivate old tab
        if (oldTab != null) {
            oldTab.setActive(false);
            if (mListener != null) {
                mListener.onTabDeactivated(oldTab);
            }
        }

        // Activate new tab
        mActiveTabIndex = index;
        newTab.setActive(true);
        newTab.updateLastActiveTimestamp();

        // If the tab was paused, we need to resume it
        if (newTab.isPaused()) {
            // Resume will be handled by the service
        }

        Logger.logDebug(LOG_TAG, "Switched to tab " + index + ": " + newTab.getId());

        if (mListener != null) {
            mListener.onTabActivated(newTab);
            mListener.onActiveTabChanged(oldIndex, index);
        }

        return true;
    }

    /**
     * Close a tab by index.
     * @param index The tab index to close
     * @return true if the tab was closed
     */
    public boolean closeTab(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return false;
        }

        TerminalTab tab = mTabs.get(index);
        Logger.logDebug(LOG_TAG, "Closing tab: " + tab.getId());

        // Remove from list
        mTabs.remove(index);

        // Update indices
        for (int i = index; i < mTabs.size(); i++) {
            mTabs.get(i).setIndex(i);
        }

        // Handle active tab adjustment
        if (mTabs.isEmpty()) {
            mActiveTabIndex = -1;
        } else if (index == mActiveTabIndex) {
            // Switch to adjacent tab
            int newIndex = Math.min(index, mTabs.size() - 1);
            mActiveTabIndex = -1; // Reset so switchToTab works
            switchToTab(newIndex);
        } else if (index < mActiveTabIndex) {
            mActiveTabIndex--;
        }

        if (mListener != null) {
            mListener.onTabClosed(tab);
        }

        return true;
    }

    /**
     * Close a tab by ID.
     */
    public boolean closeTab(String tabId) {
        int index = getTabIndexById(tabId);
        if (index >= 0) {
            return closeTab(index);
        }
        return false;
    }

    /**
     * Pause a tab to save memory.
     * @param index The tab index to pause
     * @return true if the tab was paused
     */
    public boolean pauseTab(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return false;
        }

        TerminalTab tab = mTabs.get(index);

        // Don't pause if already paused or marked as never pause
        if (tab.isPaused() || tab.isNeverPause()) {
            return false;
        }

        // Don't pause the active tab
        if (index == mActiveTabIndex) {
            return false;
        }

        Logger.logDebug(LOG_TAG, "Pausing tab: " + tab.getId());

        // State saving will be handled by TerminalStateSerializer via service
        tab.setPaused(true);

        if (mListener != null) {
            mListener.onTabPaused(tab);
        }

        return true;
    }

    /**
     * Resume a paused tab.
     * @param index The tab index to resume
     * @return true if the tab was resumed (or wasn't paused)
     */
    public boolean resumeTab(int index) {
        if (index < 0 || index >= mTabs.size()) {
            return false;
        }

        TerminalTab tab = mTabs.get(index);

        if (!tab.isPaused()) {
            return true; // Already active
        }

        Logger.logDebug(LOG_TAG, "Resuming tab: " + tab.getId());

        // State restoration will be handled by service
        tab.setPaused(false);

        if (mListener != null) {
            mListener.onTabResumed(tab);
        }

        return true;
    }

    /**
     * Reorder tabs by moving a tab from one position to another.
     */
    public void reorderTabs(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= mTabs.size() ||
            toIndex < 0 || toIndex >= mTabs.size() ||
            fromIndex == toIndex) {
            return;
        }

        TerminalTab tab = mTabs.remove(fromIndex);
        mTabs.add(toIndex, tab);

        // Update all indices
        for (int i = 0; i < mTabs.size(); i++) {
            mTabs.get(i).setIndex(i);
        }

        // Update active index if needed
        if (mActiveTabIndex == fromIndex) {
            mActiveTabIndex = toIndex;
        } else if (fromIndex < mActiveTabIndex && toIndex >= mActiveTabIndex) {
            mActiveTabIndex--;
        } else if (fromIndex > mActiveTabIndex && toIndex <= mActiveTabIndex) {
            mActiveTabIndex++;
        }

        if (mListener != null) {
            mListener.onTabsReordered();
        }
    }

    /**
     * Get the currently active tab.
     */
    public TerminalTab getActiveTab() {
        if (mActiveTabIndex >= 0 && mActiveTabIndex < mTabs.size()) {
            return mTabs.get(mActiveTabIndex);
        }
        return null;
    }

    /**
     * Get the active tab index.
     */
    public int getActiveTabIndex() {
        return mActiveTabIndex;
    }

    /**
     * Get a tab by index.
     */
    public TerminalTab getTab(int index) {
        if (index >= 0 && index < mTabs.size()) {
            return mTabs.get(index);
        }
        return null;
    }

    /**
     * Get a tab by ID.
     */
    public TerminalTab getTabById(String id) {
        for (TerminalTab tab : mTabs) {
            if (tab.getId().equals(id)) {
                return tab;
            }
        }
        return null;
    }

    /**
     * Get tab index by ID.
     */
    public int getTabIndexById(String id) {
        for (int i = 0; i < mTabs.size(); i++) {
            if (mTabs.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Get all tabs.
     */
    public List<TerminalTab> getTabs() {
        return Collections.unmodifiableList(mTabs);
    }

    /**
     * Get the number of tabs.
     */
    public int getTabCount() {
        return mTabs.size();
    }

    /**
     * Get the number of active (non-paused) sessions.
     */
    public int getActiveSessionCount() {
        int count = 0;
        for (TerminalTab tab : mTabs) {
            if (!tab.isPaused() && tab.hasRunningSession()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get tabs that are candidates for auto-pausing.
     * Returns inactive, non-protected tabs sorted by last active time (oldest first).
     */
    public List<TerminalTab> getAutoPauseCandidates() {
        return mTabs.stream()
                .filter(t -> !t.isActive())
                .filter(t -> !t.isNeverPause())
                .filter(t -> !t.isPaused())
                .sorted(Comparator.comparingLong(TerminalTab::getLastActiveTimestamp))
                .collect(Collectors.toList());
    }

    /**
     * Check if we have too many active sessions and should auto-pause some.
     */
    public boolean shouldAutoPause() {
        return getActiveSessionCount() > MAX_ACTIVE_SESSIONS;
    }

    /**
     * Get tabs that should be auto-paused based on inactivity threshold.
     */
    public List<TerminalTab> getInactiveTabsForPausing() {
        long now = System.currentTimeMillis();
        return mTabs.stream()
                .filter(t -> !t.isActive())
                .filter(t -> !t.isNeverPause())
                .filter(t -> !t.isPaused())
                .filter(t -> (now - t.getLastActiveTimestamp()) > INACTIVE_THRESHOLD_MS)
                .collect(Collectors.toList());
    }

    /**
     * Set the tab event listener.
     */
    public void setListener(TabEventListener listener) {
        mListener = listener;
    }

    /**
     * Clean up all tabs.
     */
    public void cleanup() {
        for (TerminalTab tab : mTabs) {
            if (tab.getSession() != null) {
                // Session cleanup will be handled by service
            }
        }
        mTabs.clear();
        mActiveTabIndex = -1;
    }
}
