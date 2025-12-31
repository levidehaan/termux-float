package com.termux.window.droid;

import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines available Android event types that can trigger shell commands.
 */
public class DroidEventTypes {

    /**
     * Event type definition with Android intent action and description.
     */
    public static class EventType {
        public final String name;
        public final String intentAction;
        public final String description;
        public final boolean requiresDynamicRegistration;

        public EventType(String name, String intentAction, String description, boolean dynamic) {
            this.name = name;
            this.intentAction = intentAction;
            this.description = description;
            this.requiresDynamicRegistration = dynamic;
        }
    }

    private static final Map<String, EventType> EVENT_TYPES = new HashMap<>();

    static {
        // Battery events
        register("battery_low", Intent.ACTION_BATTERY_LOW,
                "Battery dropped below threshold", true);
        register("battery_okay", Intent.ACTION_BATTERY_OKAY,
                "Battery back to acceptable level", true);
        register("battery_changed", Intent.ACTION_BATTERY_CHANGED,
                "Any battery state change", true);

        // Power events
        register("power_connected", Intent.ACTION_POWER_CONNECTED,
                "Charger plugged in", true);
        register("power_disconnected", Intent.ACTION_POWER_DISCONNECTED,
                "Charger unplugged", true);

        // Screen events
        register("screen_on", Intent.ACTION_SCREEN_ON,
                "Screen turned on", true);
        register("screen_off", Intent.ACTION_SCREEN_OFF,
                "Screen turned off", true);
        register("user_present", Intent.ACTION_USER_PRESENT,
                "User unlocked device", true);

        // Connectivity events
        register("connectivity_change", ConnectivityManager.CONNECTIVITY_ACTION,
                "Network state changed", true);
        register("wifi_state_changed", WifiManager.WIFI_STATE_CHANGED_ACTION,
                "WiFi enabled/disabled", true);

        // System events
        register("airplane_mode", Intent.ACTION_AIRPLANE_MODE_CHANGED,
                "Airplane mode toggled", true);
        register("timezone_changed", Intent.ACTION_TIMEZONE_CHANGED,
                "Timezone changed", true);
        register("locale_changed", Intent.ACTION_LOCALE_CHANGED,
                "System locale changed", true);
        register("boot_completed", Intent.ACTION_BOOT_COMPLETED,
                "Device finished booting", false);

        // Hardware events
        register("headset_plug", Intent.ACTION_HEADSET_PLUG,
                "Headphones plugged/unplugged", true);
        register("media_mounted", Intent.ACTION_MEDIA_MOUNTED,
                "External storage mounted", true);
        register("media_unmounted", Intent.ACTION_MEDIA_UNMOUNTED,
                "External storage unmounted", true);

        // Package events
        register("package_added", Intent.ACTION_PACKAGE_ADDED,
                "New app installed", true);
        register("package_removed", Intent.ACTION_PACKAGE_REMOVED,
                "App uninstalled", true);

        // Time events
        register("time_tick", Intent.ACTION_TIME_TICK,
                "System time tick (every minute)", true);
        register("date_changed", Intent.ACTION_DATE_CHANGED,
                "Date has changed", true);
    }

    private static void register(String name, String action, String description, boolean dynamic) {
        EVENT_TYPES.put(name, new EventType(name, action, description, dynamic));
    }

    /**
     * Get an event type by name.
     */
    public static EventType get(String name) {
        return EVENT_TYPES.get(name);
    }

    /**
     * Check if an event type exists.
     */
    public static boolean exists(String name) {
        return EVENT_TYPES.containsKey(name);
    }

    /**
     * Get all event types.
     */
    public static Map<String, EventType> getAll() {
        return new HashMap<>(EVENT_TYPES);
    }

    /**
     * Create an IntentFilter for a given event type.
     */
    public static IntentFilter createFilter(String eventName) {
        EventType type = get(eventName);
        if (type == null) return null;

        IntentFilter filter = new IntentFilter(type.intentAction);

        // Some intents need data scheme
        if (eventName.equals("package_added") || eventName.equals("package_removed")) {
            filter.addDataScheme("package");
        }

        return filter;
    }

    /**
     * Get a formatted list of all available events for display.
     */
    public static String getFormattedList() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available events:\n");

        for (Map.Entry<String, EventType> entry : EVENT_TYPES.entrySet()) {
            sb.append(String.format("  %-20s - %s\n",
                    entry.getKey(), entry.getValue().description));
        }

        return sb.toString();
    }
}
