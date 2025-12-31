package com.termux.window;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import com.termux.shared.data.IntentUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE;
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.List;

/**
 * Enhanced TermuxFloatService with multi-session tab support, edge panel UI,
 * and memory management.
 */
public class TermuxFloatService extends Service implements
        TabManager.TabEventListener,
        MemoryManager.MemoryEventListener {

    private static final String LOG_TAG = "TermuxFloatService";

    // UI
    private TermuxFloatView mFloatingWindow;
    private EdgePanelManager mEdgePanelManager;
    private TabBarView mTabBarView;

    // Session management
    private TabManager mTabManager;
    private MemoryManager mMemoryManager;

    // State
    private boolean mVisibleWindow = true;
    private boolean mIsInitialized = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        runStartForeground();
        TermuxFloatApplication.setLogConfig(this, false);
        Logger.logVerbose(LOG_TAG, "onCreate");

        // Register for memory callbacks
        getApplicationContext().registerComponentCallbacks(null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.logDebug(LOG_TAG, "onStartCommand");

        // Run again in case service is already started
        runStartForeground();

        if (!mIsInitialized && !initializeFloatView()) {
            return Service.START_NOT_STICKY;
        }

        String action = null;
        if (intent != null) {
            Logger.logVerboseExtended(LOG_TAG, "Received intent:\n" + IntentUtils.getIntentString(intent));
            action = intent.getAction();
        }

        if (action != null) {
            switch (action) {
                case TERMUX_FLOAT_SERVICE.ACTION_STOP_SERVICE:
                    actionStopService();
                    break;
                case TERMUX_FLOAT_SERVICE.ACTION_SHOW:
                    expandPanel();
                    break;
                case TERMUX_FLOAT_SERVICE.ACTION_HIDE:
                    collapsePanel();
                    break;
                case "ACTION_NEW_TAB":
                    createNewTab();
                    break;
                default:
                    Logger.logError(LOG_TAG, "Invalid action: \"" + action + "\"");
                    break;
            }
        } else if (!mVisibleWindow) {
            expandPanel();
        }

        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logVerbose(LOG_TAG, "onDestroy");

        if (mMemoryManager != null) {
            mMemoryManager.cleanup();
        }

        if (mTabManager != null) {
            // Kill all sessions
            for (TerminalTab tab : mTabManager.getTabs()) {
                if (tab.getSession() != null) {
                    tab.getSession().killIfExecuting(this, false);
                }
            }
            mTabManager.cleanup();
        }

        if (mEdgePanelManager != null) {
            mEdgePanelManager.cleanup();
        }

        if (mFloatingWindow != null) {
            mFloatingWindow.closeFloatingWindow();
        }

        runStopForeground();
    }

    /**
     * Request to stop service.
     */
    public void requestStopService() {
        Logger.logDebug(LOG_TAG, "Requesting to stop service");
        runStopForeground();
        stopSelf();
    }

    /**
     * Process action to stop service.
     */
    private void actionStopService() {
        // Kill all sessions
        if (mTabManager != null) {
            for (TerminalTab tab : mTabManager.getTabs()) {
                if (tab.getSession() != null) {
                    tab.getSession().killIfExecuting(this, false);
                }
            }
        }
        requestStopService();
    }

    /**
     * Make service run in foreground mode.
     */
    private void runStartForeground() {
        setupNotificationChannel();
        startForeground(TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_ID, buildNotification());
    }

    /**
     * Make service leave foreground mode.
     */
    private void runStopForeground() {
        stopForeground(true);
    }

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationUtils.setupNotificationChannel(this,
                TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID,
                TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW);
    }

    private Notification buildNotification() {
        final Resources res = getResources();

        String notificationText = res.getString(
                mVisibleWindow ? R.string.notification_message_visible : R.string.notification_message_hidden);

        // Add tab count to notification
        if (mTabManager != null) {
            int tabCount = mTabManager.getTabCount();
            if (tabCount > 1) {
                notificationText += " (" + tabCount + " tabs)";
            }
        }

        final String intentAction = mVisibleWindow ?
                TERMUX_FLOAT_SERVICE.ACTION_HIDE : TERMUX_FLOAT_SERVICE.ACTION_SHOW;
        Intent notificationIntent = new Intent(this, TermuxFloatService.class).setAction(intentAction);
        PendingIntent contentIntent = PendingIntent.getService(this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification.Builder builder = NotificationUtils.geNotificationBuilder(this,
                TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID,
                Notification.PRIORITY_LOW,
                TermuxConstants.TERMUX_FLOAT_APP_NAME, notificationText, null,
                contentIntent, null, NotificationUtils.NOTIFICATION_MODE_SILENT);
        if (builder == null) return null;

        builder.setShowWhen(false);
        builder.setSmallIcon(R.mipmap.ic_service_notification);
        builder.setColor(0xFF000000);
        builder.setOngoing(true);

        // Exit action
        Intent exitIntent = new Intent(this, TermuxFloatService.class)
                .setAction(TERMUX_FLOAT_SERVICE.ACTION_STOP_SERVICE);
        builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit),
                PendingIntent.getService(this, 0, exitIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        // New tab action
        Intent newTabIntent = new Intent(this, TermuxFloatService.class)
                .setAction("ACTION_NEW_TAB");
        builder.addAction(android.R.drawable.ic_input_add, "New Tab",
                PendingIntent.getService(this, 1, newTabIntent,
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        return builder.build();
    }

    @SuppressLint("InflateParams")
    private boolean initializeFloatView() {
        Logger.logDebug(LOG_TAG, "Initializing float view");

        // Initialize tab manager
        mTabManager = new TabManager(this);
        mTabManager.setListener(this);

        // Initialize memory manager
        mMemoryManager = new MemoryManager(this, mTabManager);
        mMemoryManager.setListener(this);

        // Inflate the new side panel layout
        mFloatingWindow = (TermuxFloatView) ((LayoutInflater)
                getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .inflate(R.layout.side_panel, null);

        mFloatingWindow.initFloatView(this);

        // Set up tab bar
        mTabBarView = mFloatingWindow.findViewById(R.id.tab_bar);
        mTabBarView.setListener(new TabBarView.TabBarListener() {
            @Override
            public void onNewTabClicked() {
                createNewTab();
            }

            @Override
            public void onSettingsClicked() {
                openSettings();
            }

            @Override
            public void onCollapseClicked() {
                collapsePanel();
            }

            @Override
            public void onTabClicked(String tabId) {
                switchToTab(tabId);
            }

            @Override
            public void onTabCloseClicked(String tabId) {
                closeTab(tabId);
            }

            @Override
            public void onTabLongClicked(String tabId, View anchorView) {
                showTabContextMenu(tabId, anchorView);
            }
        });

        // Initialize edge panel manager
        mEdgePanelManager = new EdgePanelManager(mFloatingWindow);
        mEdgePanelManager.setStateListener(new EdgePanelManager.PanelStateListener() {
            @Override
            public void onPanelExpanding() {
                mVisibleWindow = true;
                updateNotification();
            }

            @Override
            public void onPanelExpanded() {
                // Focus the terminal
                TerminalTab activeTab = mTabManager.getActiveTab();
                if (activeTab != null) {
                    mFloatingWindow.getTerminalView().requestFocus();
                }
            }

            @Override
            public void onPanelCollapsing() {
                mVisibleWindow = false;
                updateNotification();
            }

            @Override
            public void onPanelCollapsed() {
                // Check memory pressure when panel is collapsed
                mMemoryManager.checkMemoryPressure();
            }
        });

        // Create initial tab
        createInitialTab();

        try {
            mFloatingWindow.launchFloatingWindow();
            // Start collapsed, showing edge indicator
            mEdgePanelManager.initEdgeIndicator();
            mFloatingWindow.setVisibility(View.GONE);
            mEdgePanelManager.showEdgeIndicator();
            mVisibleWindow = false;
        } catch (Exception e) {
            Logger.logStackTrace(LOG_TAG, e);
            startActivity(new Intent(this, TermuxFloatPermissionActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            requestStopService();
            return false;
        }

        Logger.showToast(this, getString(R.string.panel_instruction_toast), true);
        mIsInitialized = true;
        return true;
    }

    /**
     * Create the initial tab when service starts.
     */
    private void createInitialTab() {
        TerminalTab tab = mTabManager.createTab();
        TermuxSession session = createTermuxSession(
                new ExecutionCommand(0, null, null, null,
                        mFloatingWindow.getProperties().getDefaultWorkingDirectory(),
                        ExecutionCommand.Runner.TERMINAL_SESSION.getName(), false),
                null);

        if (session != null) {
            tab.setSession(session);
            mTabManager.switchToTab(0);
            mFloatingWindow.getTerminalView().attachSession(session.getTerminalSession());
            mTabBarView.addTab(tab);
            mTabBarView.setActiveTab(tab.getId());
        }
    }

    /**
     * Create a new tab.
     */
    public void createNewTab() {
        TerminalTab tab = mTabManager.createTab();
        TermuxSession session = createTermuxSession(
                new ExecutionCommand(0, null, null, null,
                        mFloatingWindow.getProperties().getDefaultWorkingDirectory(),
                        ExecutionCommand.Runner.TERMINAL_SESSION.getName(), false),
                null);

        if (session != null) {
            tab.setSession(session);
            mTabBarView.addTab(tab);
            mTabManager.switchToTab(mTabManager.getTabCount() - 1);
        }

        updateNotification();
    }

    /**
     * Switch to a tab by ID.
     */
    public void switchToTab(String tabId) {
        int index = mTabManager.getTabIndexById(tabId);
        if (index >= 0) {
            TerminalTab tab = mTabManager.getTab(index);

            // Resume if paused
            if (tab.isPaused()) {
                resumeTab(tab);
            }

            mTabManager.switchToTab(index);
        }
    }

    /**
     * Close a tab by ID.
     */
    public void closeTab(String tabId) {
        TerminalTab tab = mTabManager.getTabById(tabId);
        if (tab == null) return;

        // Kill the session
        if (tab.getSession() != null) {
            tab.getSession().killIfExecuting(this, false);
        }

        mTabBarView.removeTab(tabId);
        mTabManager.closeTab(tabId);

        // If no tabs left, stop the service
        if (mTabManager.getTabCount() == 0) {
            requestStopService();
        }

        updateNotification();
    }

    /**
     * Show context menu for a tab.
     */
    private void showTabContextMenu(String tabId, View anchorView) {
        TabView tabView = mTabBarView.getTabView(tabId);
        if (tabView != null) {
            tabView.showContextMenu(new TabView.TabContextMenuListener() {
                @Override
                public void onResume(String tabId) {
                    TerminalTab tab = mTabManager.getTabById(tabId);
                    if (tab != null) resumeTab(tab);
                }

                @Override
                public void onPause(String tabId) {
                    TerminalTab tab = mTabManager.getTabById(tabId);
                    if (tab != null) pauseTab(tab);
                }

                @Override
                public void onToggleNeverPause(String tabId) {
                    TerminalTab tab = mTabManager.getTabById(tabId);
                    if (tab != null) {
                        tab.setNeverPause(!tab.isNeverPause());
                        mTabBarView.updateTab(tab);
                    }
                }

                @Override
                public void onRename(String tabId) {
                    // TODO: Show rename dialog
                }

                @Override
                public void onClose(String tabId) {
                    closeTab(tabId);
                }
            });
        }
    }

    /**
     * Pause a tab to save memory.
     */
    private void pauseTab(TerminalTab tab) {
        if (tab == null || tab.isPaused()) return;

        // Can't pause the active tab
        if (tab.isActive()) {
            Logger.showToast(this, "Cannot pause active tab", false);
            return;
        }

        mMemoryManager.pauseTab(tab);

        // Kill the session
        if (tab.getSession() != null) {
            tab.getSession().killIfExecuting(this, false);
            tab.setSession(null);
        }

        mTabBarView.updateTab(tab);
        Logger.showToast(this, getString(R.string.tab_paused), false);
    }

    /**
     * Resume a paused tab.
     */
    private void resumeTab(TerminalTab tab) {
        if (tab == null || !tab.isPaused()) return;

        // Create new session
        TermuxSession session = createTermuxSession(
                new ExecutionCommand(0, null, null, null,
                        mFloatingWindow.getProperties().getDefaultWorkingDirectory(),
                        ExecutionCommand.Runner.TERMINAL_SESSION.getName(), false),
                null);

        if (session != null) {
            tab.setSession(session);
            mMemoryManager.resumeTab(tab);

            // Restore saved state if available
            mMemoryManager.restoreTabState(tab);

            mTabBarView.updateTab(tab);
            Logger.showToast(this, getString(R.string.tab_resumed), false);
        }
    }

    /**
     * Expand the side panel.
     */
    public void expandPanel() {
        if (mEdgePanelManager != null) {
            mEdgePanelManager.expand();
        }
    }

    /**
     * Collapse the side panel.
     */
    public void collapsePanel() {
        if (mEdgePanelManager != null) {
            mEdgePanelManager.collapse();
        }
    }

    /**
     * Open the settings activity.
     */
    private void openSettings() {
        Intent intent = new Intent(this, TermuxFloatSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * Update the notification.
     */
    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(TermuxConstants.TERMUX_FLOAT_APP_NOTIFICATION_ID, buildNotification());
    }

    /**
     * Create a TermuxSession.
     */
    @Nullable
    public synchronized TermuxSession createTermuxSession(ExecutionCommand executionCommand, String sessionName) {
        if (executionCommand == null) return null;

        Logger.logDebug(LOG_TAG, "Creating \"" + executionCommand.getCommandIdAndLabelLogString() + "\" TermuxSession");

        if (ExecutionCommand.Runner.APP_SHELL.getName().equals(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring a background execution command");
            return null;
        }

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE) {
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString());
        }

        executionCommand.shellName = sessionName;
        executionCommand.terminalTranscriptRows = mFloatingWindow.getProperties().getTerminalTranscriptRows();
        TermuxSession newSession = TermuxSession.execute(this, executionCommand,
                mFloatingWindow.getTermuxFloatSessionClient(), null, new TermuxShellEnvironment(),
                null, executionCommand.isPluginExecutionCommand);

        if (newSession == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxSession");
            return null;
        }

        mFloatingWindow.reloadViewStyling();
        return newSession;
    }

    // TabManager.TabEventListener implementation

    @Override
    public void onTabCreated(TerminalTab tab) {
        Logger.logDebug(LOG_TAG, "Tab created: " + tab.getId());
    }

    @Override
    public void onTabActivated(TerminalTab tab) {
        Logger.logDebug(LOG_TAG, "Tab activated: " + tab.getId());
        if (tab.getSession() != null) {
            mFloatingWindow.getTerminalView().attachSession(tab.getSession().getTerminalSession());
        }
        mTabBarView.setActiveTab(tab.getId());
    }

    @Override
    public void onTabDeactivated(TerminalTab tab) {
        Logger.logDebug(LOG_TAG, "Tab deactivated: " + tab.getId());
    }

    @Override
    public void onTabClosed(TerminalTab tab) {
        Logger.logDebug(LOG_TAG, "Tab closed: " + tab.getId());
    }

    @Override
    public void onTabPaused(TerminalTab tab) {
        Logger.logDebug(LOG_TAG, "Tab paused: " + tab.getId());
        mTabBarView.updateTab(tab);
    }

    @Override
    public void onTabResumed(TerminalTab tab) {
        Logger.logDebug(LOG_TAG, "Tab resumed: " + tab.getId());
        mTabBarView.updateTab(tab);
    }

    @Override
    public void onTabsReordered() {
        Logger.logDebug(LOG_TAG, "Tabs reordered");
        mTabBarView.updateTabs(mTabManager.getTabs());
    }

    @Override
    public void onActiveTabChanged(int oldIndex, int newIndex) {
        Logger.logDebug(LOG_TAG, "Active tab changed: " + oldIndex + " -> " + newIndex);
    }

    // MemoryManager.MemoryEventListener implementation

    @Override
    public void onTabPauseRequested(TerminalTab tab) {
        pauseTab(tab);
    }

    @Override
    public void onTabResumeRequested(TerminalTab tab) {
        resumeTab(tab);
    }

    @Override
    public void onLowMemoryWarning() {
        Logger.showToast(this, getString(R.string.low_memory_warning), false);
    }

    @Override
    public void onCriticalMemory() {
        Logger.logWarn(LOG_TAG, "Critical memory situation");
    }

    // Getters for compatibility

    public TabManager getTabManager() {
        return mTabManager;
    }

    public TerminalTab getActiveTab() {
        return mTabManager != null ? mTabManager.getActiveTab() : null;
    }

    public TerminalSession getCurrentSession() {
        TerminalTab tab = getActiveTab();
        return (tab != null && tab.getSession() != null) ?
                tab.getSession().getTerminalSession() : null;
    }

    public EdgePanelManager getEdgePanelManager() {
        return mEdgePanelManager;
    }
}
