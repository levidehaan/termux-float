package com.termux.window;

import android.app.ActivityManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;

import com.termux.shared.logger.Logger;

import java.util.List;

/**
 * Manages memory usage across terminal tabs.
 * Handles automatic pausing of inactive tabs and responds to system memory pressure.
 */
public class MemoryManager implements ComponentCallbacks2 {
    private static final String LOG_TAG = "MemoryManager";

    // Configuration
    private static final int MAX_ACTIVE_SESSIONS = 3;
    private static final long INACTIVE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

    private final Context mContext;
    private final TabManager mTabManager;
    private final TerminalStateSerializer mSerializer;

    private MemoryEventListener mListener;

    // Memory thresholds (in bytes)
    private long mLowMemoryThreshold;
    private long mCriticalMemoryThreshold;

    public interface MemoryEventListener {
        void onTabPauseRequested(TerminalTab tab);
        void onTabResumeRequested(TerminalTab tab);
        void onLowMemoryWarning();
        void onCriticalMemory();
    }

    public MemoryManager(Context context, TabManager tabManager) {
        mContext = context;
        mTabManager = tabManager;
        mSerializer = new TerminalStateSerializer(context);

        // Calculate memory thresholds
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);

        // Low memory: when available memory drops below 15% of total
        mLowMemoryThreshold = (long) (memInfo.totalMem * 0.15);
        // Critical: when below 10%
        mCriticalMemoryThreshold = (long) (memInfo.totalMem * 0.10);

        Logger.logDebug(LOG_TAG, "Memory thresholds - Low: " +
                (mLowMemoryThreshold / 1024 / 1024) + "MB, Critical: " +
                (mCriticalMemoryThreshold / 1024 / 1024) + "MB");
    }

    /**
     * Set the memory event listener.
     */
    public void setListener(MemoryEventListener listener) {
        mListener = listener;
    }

    /**
     * Get the terminal state serializer.
     */
    public TerminalStateSerializer getSerializer() {
        return mSerializer;
    }

    /**
     * Check memory status and pause tabs if needed.
     * Should be called periodically or when tabs are switched.
     */
    public void checkMemoryPressure() {
        int activeSessions = mTabManager.getActiveSessionCount();

        // Check if we have too many active sessions
        if (activeSessions > MAX_ACTIVE_SESSIONS) {
            Logger.logDebug(LOG_TAG, "Too many active sessions (" + activeSessions +
                    "), looking for tabs to pause");
            pauseInactiveTabs(activeSessions - MAX_ACTIVE_SESSIONS);
        }

        // Check inactive tabs
        pauseStaleInactiveTabs();
    }

    /**
     * Pause the N oldest inactive tabs.
     */
    private void pauseInactiveTabs(int count) {
        List<TerminalTab> candidates = mTabManager.getAutoPauseCandidates();

        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            TerminalTab tab = candidates.get(i);
            requestPauseTab(tab);
        }
    }

    /**
     * Pause tabs that have been inactive for too long.
     */
    private void pauseStaleInactiveTabs() {
        List<TerminalTab> staleTabs = mTabManager.getInactiveTabsForPausing();

        for (TerminalTab tab : staleTabs) {
            Logger.logDebug(LOG_TAG, "Auto-pausing stale tab: " + tab.getId());
            requestPauseTab(tab);
        }
    }

    /**
     * Request to pause a tab (notifies listener to handle actual pausing).
     */
    private void requestPauseTab(TerminalTab tab) {
        if (mListener != null) {
            mListener.onTabPauseRequested(tab);
        }
    }

    /**
     * Called when a tab should be paused.
     * Serializes the terminal state before the session is killed.
     */
    public void pauseTab(TerminalTab tab) {
        if (tab == null || tab.isPaused()) return;

        Logger.logDebug(LOG_TAG, "Pausing tab: " + tab.getId());

        // Serialize terminal state if there's a session
        if (tab.getSession() != null && tab.getSession().getTerminalSession() != null) {
            try {
                byte[] state = mSerializer.serializeTerminalState(
                        tab.getSession().getTerminalSession());
                tab.setSavedTerminalBuffer(state);
                Logger.logDebug(LOG_TAG, "Saved terminal state: " + state.length + " bytes");
            } catch (Exception e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        }

        tab.setPaused(true);
    }

    /**
     * Called when a tab should be resumed.
     */
    public void resumeTab(TerminalTab tab) {
        if (tab == null || !tab.isPaused()) return;

        Logger.logDebug(LOG_TAG, "Resuming tab: " + tab.getId());

        // State restoration will happen when a new session is created
        tab.setPaused(false);
    }

    /**
     * Restore terminal state to a session after resume.
     */
    public void restoreTabState(TerminalTab tab) {
        if (tab == null || tab.getSavedTerminalBuffer() == null) return;

        if (tab.getSession() != null && tab.getSession().getTerminalSession() != null) {
            try {
                mSerializer.restoreTerminalState(
                        tab.getSession().getTerminalSession(),
                        tab.getSavedTerminalBuffer());
                tab.clearSavedState();
                Logger.logDebug(LOG_TAG, "Restored terminal state for tab: " + tab.getId());
            } catch (Exception e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        }
    }

    /**
     * Force pause all inactive tabs (called during memory pressure).
     */
    public void pauseAllInactive() {
        Logger.logDebug(LOG_TAG, "Force pausing all inactive tabs");

        for (TerminalTab tab : mTabManager.getTabs()) {
            if (!tab.isActive() && !tab.isPaused() && !tab.isNeverPause()) {
                requestPauseTab(tab);
            }
        }
    }

    /**
     * Get current memory usage info.
     */
    public MemoryInfo getMemoryInfo() {
        ActivityManager activityManager =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);

        MemoryInfo info = new MemoryInfo();
        info.totalMemory = memInfo.totalMem;
        info.availableMemory = memInfo.availMem;
        info.lowMemory = memInfo.lowMemory;
        info.threshold = memInfo.threshold;

        return info;
    }

    // ComponentCallbacks2 implementation

    @Override
    public void onTrimMemory(int level) {
        Logger.logDebug(LOG_TAG, "onTrimMemory: level=" + level);

        switch (level) {
            case TRIM_MEMORY_RUNNING_MODERATE:
                // App is running but system wants to trim memory
                checkMemoryPressure();
                break;

            case TRIM_MEMORY_RUNNING_LOW:
                // App is running and system is low on memory
                if (mListener != null) {
                    mListener.onLowMemoryWarning();
                }
                pauseAllInactive();
                break;

            case TRIM_MEMORY_RUNNING_CRITICAL:
            case TRIM_MEMORY_COMPLETE:
                // System is in critical state
                if (mListener != null) {
                    mListener.onCriticalMemory();
                }
                pauseAllInactive();
                break;

            case TRIM_MEMORY_UI_HIDDEN:
                // App UI is hidden, good time to release resources
                checkMemoryPressure();
                break;

            case TRIM_MEMORY_BACKGROUND:
            case TRIM_MEMORY_MODERATE:
                // App is in background, aggressively release memory
                pauseAllInactive();
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Not used
    }

    @Override
    public void onLowMemory() {
        Logger.logDebug(LOG_TAG, "onLowMemory called");
        if (mListener != null) {
            mListener.onCriticalMemory();
        }
        pauseAllInactive();
    }

    /**
     * Clean up resources.
     */
    public void cleanup() {
        mSerializer.cleanup();
    }

    /**
     * Memory info data class.
     */
    public static class MemoryInfo {
        public long totalMemory;
        public long availableMemory;
        public boolean lowMemory;
        public long threshold;

        public float getUsagePercent() {
            return 100f * (1f - ((float) availableMemory / (float) totalMemory));
        }

        @Override
        public String toString() {
            return String.format("Memory: %.1f%% used, %dMB available of %dMB",
                    getUsagePercent(),
                    availableMemory / 1024 / 1024,
                    totalMemory / 1024 / 1024);
        }
    }
}
