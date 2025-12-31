package com.termux.window.droid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.termux.shared.logger.Logger;

/**
 * Receives boot completed broadcast to start DroidEventService if needed.
 * This allows droid daemon jobs to persist across device reboots.
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "BootCompletedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Logger.logDebug(LOG_TAG, "Boot completed, checking for persistent droid jobs");

        // TODO: Check if there are any persistent (daemon) jobs saved
        // If so, start the DroidEventService to restore them

        // For now, we just log the boot event
        // In a future version, we could persist job configurations to SharedPreferences
        // and restore them on boot

        // Start the service if we have persistent jobs
        // Intent serviceIntent = new Intent(context, DroidEventService.class);
        // serviceIntent.setAction("RESTORE_JOBS");
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        //     context.startForegroundService(serviceIntent);
        // } else {
        //     context.startService(serviceIntent);
        // }
    }
}
