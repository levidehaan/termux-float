package com.termux.window.droid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.termux.shared.logger.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Receives Android broadcast events and forwards them to DroidEventService.
 */
public class DroidEventReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "DroidEventReceiver";

    private final String mEventType;
    private final DroidEventCallback mCallback;

    public interface DroidEventCallback {
        void onEventReceived(String eventType, Map<String, String> extras);
    }

    public DroidEventReceiver(String eventType, DroidEventCallback callback) {
        mEventType = eventType;
        mCallback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Logger.logDebug(LOG_TAG, "Received broadcast: " + action + " for event: " + mEventType);

        // Extract relevant extras based on event type
        Map<String, String> extras = extractExtras(intent);
        extras.put("DROID_EVENT", mEventType);
        extras.put("DROID_TIMESTAMP", String.valueOf(System.currentTimeMillis()));

        if (mCallback != null) {
            mCallback.onEventReceived(mEventType, extras);
        }
    }

    /**
     * Extract relevant intent extras as environment variables.
     */
    private Map<String, String> extractExtras(Intent intent) {
        Map<String, String> extras = new HashMap<>();

        switch (mEventType) {
            case "battery_changed":
            case "battery_low":
            case "battery_okay":
                extractBatteryExtras(intent, extras);
                break;

            case "power_connected":
            case "power_disconnected":
                extractPowerExtras(intent, extras);
                break;

            case "connectivity_change":
                extractConnectivityExtras(intent, extras);
                break;

            case "wifi_state_changed":
                extractWifiExtras(intent, extras);
                break;

            case "airplane_mode":
                extras.put("DROID_AIRPLANE_MODE",
                        intent.getBooleanExtra("state", false) ? "on" : "off");
                break;

            case "headset_plug":
                int state = intent.getIntExtra("state", -1);
                extras.put("DROID_HEADSET_STATE", state == 1 ? "connected" : "disconnected");
                extras.put("DROID_HEADSET_NAME", intent.getStringExtra("name"));
                extras.put("DROID_HEADSET_MICROPHONE",
                        intent.getIntExtra("microphone", 0) == 1 ? "yes" : "no");
                break;

            case "package_added":
            case "package_removed":
                String packageName = intent.getData() != null ?
                        intent.getData().getSchemeSpecificPart() : "";
                extras.put("DROID_PACKAGE", packageName);
                extras.put("DROID_REPLACING",
                        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) ? "yes" : "no");
                break;

            case "timezone_changed":
                extras.put("DROID_TIMEZONE", java.util.TimeZone.getDefault().getID());
                break;

            case "locale_changed":
                extras.put("DROID_LOCALE", java.util.Locale.getDefault().toString());
                break;

            case "media_mounted":
            case "media_unmounted":
                if (intent.getData() != null) {
                    extras.put("DROID_MEDIA_PATH", intent.getData().getPath());
                }
                break;
        }

        return extras;
    }

    private void extractBatteryExtras(Intent intent, Map<String, String> extras) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level >= 0 && scale > 0) {
            int percent = (level * 100) / scale;
            extras.put("DROID_BATTERY_LEVEL", String.valueOf(percent));
        }

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        String statusStr;
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                statusStr = "charging";
                break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                statusStr = "discharging";
                break;
            case BatteryManager.BATTERY_STATUS_FULL:
                statusStr = "full";
                break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                statusStr = "not_charging";
                break;
            default:
                statusStr = "unknown";
        }
        extras.put("DROID_BATTERY_STATUS", statusStr);

        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        String pluggedStr;
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                pluggedStr = "ac";
                break;
            case BatteryManager.BATTERY_PLUGGED_USB:
                pluggedStr = "usb";
                break;
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                pluggedStr = "wireless";
                break;
            default:
                pluggedStr = "none";
        }
        extras.put("DROID_BATTERY_PLUGGED", pluggedStr);

        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        String healthStr;
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                healthStr = "good";
                break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                healthStr = "overheat";
                break;
            case BatteryManager.BATTERY_HEALTH_DEAD:
                healthStr = "dead";
                break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                healthStr = "over_voltage";
                break;
            default:
                healthStr = "unknown";
        }
        extras.put("DROID_BATTERY_HEALTH", healthStr);

        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        if (temp >= 0) {
            extras.put("DROID_BATTERY_TEMP", String.valueOf(temp / 10.0));
        }
    }

    private void extractPowerExtras(Intent intent, Map<String, String> extras) {
        // Power events are simple - just connected/disconnected
        extras.put("DROID_POWER_STATE",
                mEventType.equals("power_connected") ? "connected" : "disconnected");
    }

    private void extractConnectivityExtras(Intent intent, Map<String, String> extras) {
        boolean noConnectivity = intent.getBooleanExtra(
                android.net.ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
        extras.put("DROID_CONNECTED", noConnectivity ? "no" : "yes");

        android.net.NetworkInfo info = intent.getParcelableExtra(
                android.net.ConnectivityManager.EXTRA_NETWORK_INFO);
        if (info != null) {
            extras.put("DROID_NETWORK_TYPE", info.getTypeName());
            extras.put("DROID_NETWORK_STATE", info.getState().name());
        }
    }

    private void extractWifiExtras(Intent intent, Map<String, String> extras) {
        int wifiState = intent.getIntExtra(
                android.net.wifi.WifiManager.EXTRA_WIFI_STATE,
                android.net.wifi.WifiManager.WIFI_STATE_UNKNOWN);

        String stateStr;
        switch (wifiState) {
            case android.net.wifi.WifiManager.WIFI_STATE_DISABLED:
                stateStr = "disabled";
                break;
            case android.net.wifi.WifiManager.WIFI_STATE_DISABLING:
                stateStr = "disabling";
                break;
            case android.net.wifi.WifiManager.WIFI_STATE_ENABLED:
                stateStr = "enabled";
                break;
            case android.net.wifi.WifiManager.WIFI_STATE_ENABLING:
                stateStr = "enabling";
                break;
            default:
                stateStr = "unknown";
        }
        extras.put("DROID_WIFI_STATE", stateStr);
    }
}
