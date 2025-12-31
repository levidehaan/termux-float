package com.termux.window;

import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;

import java.util.UUID;

/**
 * Represents a single terminal tab with its associated session and state.
 */
public class TerminalTab {
    private final String mId;
    private String mTitle;
    private TermuxSession mSession;

    // State flags
    private boolean mIsPaused;
    private boolean mNeverPause;
    private boolean mIsActive;

    // Timing
    private long mCreatedAt;
    private long mLastActiveTimestamp;

    // Saved state for paused tabs
    private byte[] mSavedTerminalBuffer;
    private int mSavedCursorRow;
    private int mSavedCursorCol;
    private int mSavedScrollPosition;

    // Tab index for display order
    private int mIndex;

    /**
     * Create a new terminal tab.
     */
    public TerminalTab() {
        mId = UUID.randomUUID().toString();
        mCreatedAt = System.currentTimeMillis();
        mLastActiveTimestamp = mCreatedAt;
        mIsPaused = false;
        mNeverPause = false;
        mIsActive = false;
        mIndex = -1;
    }

    /**
     * Create a new terminal tab with a specific title.
     */
    public TerminalTab(String title) {
        this();
        mTitle = title;
    }

    // Getters and setters

    public String getId() {
        return mId;
    }

    public String getTitle() {
        if (mTitle != null && !mTitle.isEmpty()) {
            return mTitle;
        }
        // Default title based on index
        return "Terminal " + (mIndex + 1);
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public TermuxSession getSession() {
        return mSession;
    }

    public void setSession(TermuxSession session) {
        mSession = session;
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public void setPaused(boolean paused) {
        mIsPaused = paused;
        if (paused) {
            // Clear session reference when paused (after saving state)
            mSession = null;
        }
    }

    public boolean isNeverPause() {
        return mNeverPause;
    }

    public void setNeverPause(boolean neverPause) {
        mNeverPause = neverPause;
    }

    public boolean isActive() {
        return mIsActive;
    }

    public void setActive(boolean active) {
        mIsActive = active;
        if (active) {
            mLastActiveTimestamp = System.currentTimeMillis();
        }
    }

    public long getCreatedAt() {
        return mCreatedAt;
    }

    public long getLastActiveTimestamp() {
        return mLastActiveTimestamp;
    }

    public void updateLastActiveTimestamp() {
        mLastActiveTimestamp = System.currentTimeMillis();
    }

    public int getIndex() {
        return mIndex;
    }

    public void setIndex(int index) {
        mIndex = index;
    }

    // Saved state for pausing

    public byte[] getSavedTerminalBuffer() {
        return mSavedTerminalBuffer;
    }

    public void setSavedTerminalBuffer(byte[] buffer) {
        mSavedTerminalBuffer = buffer;
    }

    public int getSavedCursorRow() {
        return mSavedCursorRow;
    }

    public void setSavedCursorRow(int row) {
        mSavedCursorRow = row;
    }

    public int getSavedCursorCol() {
        return mSavedCursorCol;
    }

    public void setSavedCursorCol(int col) {
        mSavedCursorCol = col;
    }

    public int getSavedScrollPosition() {
        return mSavedScrollPosition;
    }

    public void setSavedScrollPosition(int position) {
        mSavedScrollPosition = position;
    }

    public void clearSavedState() {
        mSavedTerminalBuffer = null;
        mSavedCursorRow = 0;
        mSavedCursorCol = 0;
        mSavedScrollPosition = 0;
    }

    /**
     * Check if this tab has a valid running session.
     */
    public boolean hasRunningSession() {
        return mSession != null && mSession.getTerminalSession() != null
                && mSession.getTerminalSession().isRunning();
    }

    /**
     * Get a display-friendly status string.
     */
    public String getStatusString() {
        if (mIsPaused) {
            return "Paused";
        } else if (hasRunningSession()) {
            return "Running";
        } else {
            return "Stopped";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TerminalTab that = (TerminalTab) o;
        return mId.equals(that.mId);
    }

    @Override
    public int hashCode() {
        return mId.hashCode();
    }
}
