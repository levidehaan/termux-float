package com.termux.window.droid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;
import com.termux.shared.notification.NotificationUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.window.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service that manages Android event listeners and executes shell commands when events occur.
 * Communicates with the droid CLI via Unix domain socket.
 */
public class DroidEventService extends Service implements DroidEventReceiver.DroidEventCallback {
    private static final String LOG_TAG = "DroidEventService";
    private static final String SOCKET_NAME = "droid_events";
    private static final String NOTIFICATION_CHANNEL_ID = "droid_events_channel";
    private static final int NOTIFICATION_ID = 2001;

    // Registered jobs
    private final List<DroidJob> mJobs = new ArrayList<>();

    // Active broadcast receivers (keyed by event type)
    private final Map<String, BroadcastReceiver> mReceivers = new HashMap<>();

    // Socket server for CLI communication
    private LocalServerSocket mServerSocket;
    private Thread mSocketThread;
    private volatile boolean mRunning = false;

    // Executor for running commands
    private ExecutorService mCommandExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.logDebug(LOG_TAG, "onCreate");

        mCommandExecutor = Executors.newCachedThreadPool();

        // Start socket server
        startSocketServer();

        // Set up notification channel
        setupNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.logDebug(LOG_TAG, "onStartCommand");

        if (intent != null) {
            String action = intent.getAction();
            if ("STOP_ALL".equals(action)) {
                stopAllJobs();
                if (mJobs.isEmpty()) {
                    stopSelf();
                }
            }
        }

        // Update notification
        updateNotification();

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logDebug(LOG_TAG, "onDestroy");

        mRunning = false;

        // Close socket
        if (mServerSocket != null) {
            try {
                mServerSocket.close();
            } catch (IOException e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        }

        // Unregister all receivers
        for (BroadcastReceiver receiver : mReceivers.values()) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        }
        mReceivers.clear();

        // Shutdown executor
        if (mCommandExecutor != null) {
            mCommandExecutor.shutdown();
        }

        // Stop foreground
        stopForeground(true);
    }

    /**
     * Register a new job.
     */
    public synchronized DroidJob registerJob(String eventType, String command, int maxCount) {
        if (!DroidEventTypes.exists(eventType)) {
            Logger.logError(LOG_TAG, "Unknown event type: " + eventType);
            return null;
        }

        DroidJob job = new DroidJob(eventType, command, maxCount);
        mJobs.add(job);
        Logger.logDebug(LOG_TAG, "Registered job: " + job);

        // Ensure receiver is registered for this event type
        ensureReceiverRegistered(eventType);

        // Start foreground if we have daemon jobs
        if (job.isDaemon()) {
            startForeground(NOTIFICATION_ID, buildNotification());
        }

        updateNotification();
        return job;
    }

    /**
     * Unregister a job by ID.
     */
    public synchronized boolean unregisterJob(String jobId) {
        Iterator<DroidJob> iterator = mJobs.iterator();
        while (iterator.hasNext()) {
            DroidJob job = iterator.next();
            if (job.getId().equals(jobId)) {
                iterator.remove();
                Logger.logDebug(LOG_TAG, "Unregistered job: " + jobId);

                // Check if we still need the receiver for this event type
                cleanupReceiverIfUnused(job.getEventType());

                updateNotification();
                return true;
            }
        }
        return false;
    }

    /**
     * Stop all jobs.
     */
    public synchronized void stopAllJobs() {
        mJobs.clear();
        for (String eventType : new ArrayList<>(mReceivers.keySet())) {
            cleanupReceiverIfUnused(eventType);
        }
        updateNotification();
    }

    /**
     * Get list of all jobs.
     */
    public synchronized List<DroidJob> getJobs() {
        return new ArrayList<>(mJobs);
    }

    /**
     * Ensure a broadcast receiver is registered for the given event type.
     */
    private void ensureReceiverRegistered(String eventType) {
        if (mReceivers.containsKey(eventType)) {
            return; // Already registered
        }

        IntentFilter filter = DroidEventTypes.createFilter(eventType);
        if (filter == null) {
            Logger.logError(LOG_TAG, "Failed to create filter for: " + eventType);
            return;
        }

        DroidEventReceiver receiver = new DroidEventReceiver(eventType, this);
        registerReceiver(receiver, filter);
        mReceivers.put(eventType, receiver);
        Logger.logDebug(LOG_TAG, "Registered receiver for: " + eventType);
    }

    /**
     * Unregister a receiver if no more jobs need it.
     */
    private void cleanupReceiverIfUnused(String eventType) {
        boolean needed = false;
        for (DroidJob job : mJobs) {
            if (job.getEventType().equals(eventType)) {
                needed = true;
                break;
            }
        }

        if (!needed && mReceivers.containsKey(eventType)) {
            BroadcastReceiver receiver = mReceivers.remove(eventType);
            if (receiver != null) {
                try {
                    unregisterReceiver(receiver);
                    Logger.logDebug(LOG_TAG, "Unregistered receiver for: " + eventType);
                } catch (Exception e) {
                    Logger.logStackTrace(LOG_TAG, e);
                }
            }
        }
    }

    /**
     * Called when an event is received.
     */
    @Override
    public void onEventReceived(String eventType, Map<String, String> extras) {
        Logger.logDebug(LOG_TAG, "Event received: " + eventType);

        List<DroidJob> jobsToRemove = new ArrayList<>();

        synchronized (this) {
            for (DroidJob job : mJobs) {
                if (job.getEventType().equals(eventType) && job.isActive()) {
                    // Execute the command
                    executeJobCommand(job, extras);

                    // Check if job should be removed
                    if (job.shouldRemoveAfterExecution()) {
                        jobsToRemove.add(job);
                    }
                }
            }

            // Remove completed jobs
            for (DroidJob job : jobsToRemove) {
                mJobs.remove(job);
                Logger.logDebug(LOG_TAG, "Removed completed job: " + job.getId());
            }

            // Cleanup unused receivers
            for (DroidJob job : jobsToRemove) {
                cleanupReceiverIfUnused(job.getEventType());
            }
        }

        updateNotification();
    }

    /**
     * Execute a job's command with environment variables from the event.
     */
    private void executeJobCommand(DroidJob job, Map<String, String> extras) {
        mCommandExecutor.execute(() -> {
            try {
                Logger.logDebug(LOG_TAG, "Executing command for job " + job.getId());

                // Build environment
                String[] env = new String[extras.size() + 1];
                int i = 0;
                env[i++] = "DROID_JOB_ID=" + job.getId();
                for (Map.Entry<String, String> entry : extras.entrySet()) {
                    env[i++] = entry.getKey() + "=" + entry.getValue();
                }

                // Execute command using Termux's shell
                ProcessBuilder pb = new ProcessBuilder(
                        "/data/data/com.termux/files/usr/bin/bash",
                        "-c",
                        job.getCommand()
                );

                // Set environment
                Map<String, String> environment = pb.environment();
                for (Map.Entry<String, String> entry : extras.entrySet()) {
                    environment.put(entry.getKey(), entry.getValue());
                }
                environment.put("DROID_JOB_ID", job.getId());

                // Set working directory
                pb.directory(new java.io.File("/data/data/com.termux/files/home"));

                Process process = pb.start();
                int exitCode = process.waitFor();

                job.incrementExecutionCount();
                Logger.logDebug(LOG_TAG, "Command completed with exit code: " + exitCode);

            } catch (Exception e) {
                Logger.logError(LOG_TAG, "Failed to execute command: " + e.getMessage());
                Logger.logStackTrace(LOG_TAG, e);
            }
        });
    }

    /**
     * Start the Unix domain socket server for CLI communication.
     */
    private void startSocketServer() {
        mRunning = true;
        mSocketThread = new Thread(() -> {
            try {
                // Remove any stale socket
                String socketPath = getApplicationInfo().dataDir + "/" + SOCKET_NAME;
                new java.io.File(socketPath).delete();

                mServerSocket = new LocalServerSocket(SOCKET_NAME);
                Logger.logDebug(LOG_TAG, "Socket server started");

                while (mRunning) {
                    try {
                        LocalSocket client = mServerSocket.accept();
                        handleClientConnection(client);
                    } catch (IOException e) {
                        if (mRunning) {
                            Logger.logStackTrace(LOG_TAG, e);
                        }
                    }
                }
            } catch (IOException e) {
                Logger.logStackTrace(LOG_TAG, e);
            }
        });
        mSocketThread.start();
    }

    /**
     * Handle a client connection from the droid CLI.
     */
    private void handleClientConnection(LocalSocket client) {
        mCommandExecutor.execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
                 PrintWriter writer = new PrintWriter(client.getOutputStream(), true)) {

                String line = reader.readLine();
                if (line == null) return;

                Logger.logDebug(LOG_TAG, "Received command: " + line);
                String response = processCommand(line);
                writer.println(response);

            } catch (IOException e) {
                Logger.logStackTrace(LOG_TAG, e);
            } finally {
                try {
                    client.close();
                } catch (IOException ignored) {}
            }
        });
    }

    /**
     * Process a command from the CLI.
     */
    private String processCommand(String line) {
        String[] parts = line.split("\\|", 4);
        String cmd = parts[0];

        switch (cmd) {
            case "REGISTER":
                if (parts.length >= 4) {
                    String eventType = parts[1];
                    String command = parts[2];
                    int maxCount = Integer.parseInt(parts[3]);
                    DroidJob job = registerJob(eventType, command, maxCount);
                    if (job != null) {
                        return "OK|" + job.getId();
                    } else {
                        return "ERROR|Unknown event type: " + eventType;
                    }
                }
                return "ERROR|Invalid REGISTER command";

            case "UNREGISTER":
                if (parts.length >= 2) {
                    boolean success = unregisterJob(parts[1]);
                    return success ? "OK|Job removed" : "ERROR|Job not found";
                }
                return "ERROR|Invalid UNREGISTER command";

            case "LIST":
                StringBuilder sb = new StringBuilder();
                List<DroidJob> jobs = getJobs();
                if (jobs.isEmpty()) {
                    sb.append("No active jobs\n");
                } else {
                    for (DroidJob job : jobs) {
                        sb.append(String.format("%s\t%s\t%s\t%d/%s\n",
                                job.getId(),
                                job.getEventType(),
                                job.isDaemon() ? "daemon" : "once",
                                job.getExecutionCount(),
                                job.getMaxCount() == 0 ? "inf" : String.valueOf(job.getMaxCount())));
                    }
                }
                return "OK|" + sb.toString();

            case "STOP_ALL":
                stopAllJobs();
                return "OK|All jobs stopped";

            default:
                return "ERROR|Unknown command: " + cmd;
        }
    }

    /**
     * Set up notification channel.
     */
    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationUtils.setupNotificationChannel(this,
                    NOTIFICATION_CHANNEL_ID,
                    "Droid Events",
                    NotificationManager.IMPORTANCE_LOW);
        }
    }

    /**
     * Build the foreground notification.
     */
    private Notification buildNotification() {
        int activeJobs;
        int daemonJobs;

        synchronized (this) {
            activeJobs = mJobs.size();
            daemonJobs = (int) mJobs.stream().filter(DroidJob::isDaemon).count();
        }

        String text = String.format("%d active listener%s", activeJobs, activeJobs == 1 ? "" : "s");
        if (daemonJobs > 0) {
            text += String.format(" (%d daemon%s)", daemonJobs, daemonJobs == 1 ? "" : "s");
        }

        Intent stopIntent = new Intent(this, DroidEventService.class).setAction("STOP_ALL");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification.Builder builder = NotificationUtils.geNotificationBuilder(this,
                NOTIFICATION_CHANNEL_ID,
                Notification.PRIORITY_LOW,
                getString(R.string.droid_notification_title),
                text, null, null, null,
                NotificationUtils.NOTIFICATION_MODE_SILENT);

        if (builder == null) return null;

        builder.setSmallIcon(android.R.drawable.ic_menu_manage);
        builder.setOngoing(true);
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.droid_stop_all), stopPendingIntent);

        return builder.build();
    }

    /**
     * Update the notification.
     */
    private void updateNotification() {
        synchronized (this) {
            boolean hasDaemons = mJobs.stream().anyMatch(DroidJob::isDaemon);

            if (hasDaemons) {
                // Must stay in foreground
                Notification notification = buildNotification();
                if (notification != null) {
                    startForeground(NOTIFICATION_ID, notification);
                }
            } else if (mJobs.isEmpty()) {
                // No jobs, can stop foreground
                stopForeground(true);
            } else {
                // Has jobs but no daemons - still show notification
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                Notification notification = buildNotification();
                if (notification != null) {
                    nm.notify(NOTIFICATION_ID, notification);
                }
            }
        }
    }
}
