package com.termux.window.droid;

import java.util.UUID;

/**
 * Represents a registered event job that triggers shell commands on Android events.
 */
public class DroidJob {
    private final String mId;
    private final String mEventType;
    private final String mCommand;
    private final int mMaxCount;      // 0 = infinite (daemon mode)
    private int mExecutionCount;
    private final long mCreatedAt;
    private boolean mIsActive;

    /**
     * Create a new DroidJob.
     *
     * @param eventType The Android event type to listen for
     * @param command   The shell command to execute
     * @param maxCount  Maximum executions (0 = infinite)
     */
    public DroidJob(String eventType, String command, int maxCount) {
        mId = UUID.randomUUID().toString().substring(0, 8);
        mEventType = eventType;
        mCommand = command;
        mMaxCount = maxCount;
        mExecutionCount = 0;
        mCreatedAt = System.currentTimeMillis();
        mIsActive = true;
    }

    public String getId() {
        return mId;
    }

    public String getEventType() {
        return mEventType;
    }

    public String getCommand() {
        return mCommand;
    }

    public int getMaxCount() {
        return mMaxCount;
    }

    public int getExecutionCount() {
        return mExecutionCount;
    }

    public void incrementExecutionCount() {
        mExecutionCount++;
    }

    public long getCreatedAt() {
        return mCreatedAt;
    }

    public boolean isActive() {
        return mIsActive;
    }

    public void setActive(boolean active) {
        mIsActive = active;
    }

    /**
     * Check if this job should be removed after execution.
     */
    public boolean shouldRemoveAfterExecution() {
        if (mMaxCount == 0) {
            return false; // Daemon mode, never auto-remove
        }
        return mExecutionCount >= mMaxCount;
    }

    /**
     * Check if this is a daemon (persistent) job.
     */
    public boolean isDaemon() {
        return mMaxCount == 0;
    }

    @Override
    public String toString() {
        return String.format("DroidJob[%s] event=%s, cmd=%s, count=%d/%s",
                mId, mEventType, truncateCommand(), mExecutionCount,
                mMaxCount == 0 ? "inf" : String.valueOf(mMaxCount));
    }

    private String truncateCommand() {
        if (mCommand.length() > 30) {
            return mCommand.substring(0, 27) + "...";
        }
        return mCommand;
    }
}
