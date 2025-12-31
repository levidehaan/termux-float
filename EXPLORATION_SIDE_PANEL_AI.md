# Termux Float: Side Panel + AI Integration Exploration

This document explores the architectural changes needed to transform Termux Float from a floating bubble/window into an edge-triggered side panel with tabs, memory management, and integrated AI capabilities.

## Table of Contents
1. [Current Architecture Overview](#current-architecture-overview)
2. [UI Redesign: Edge Panel with Swipe Gestures](#ui-redesign)
3. [Tab System Implementation](#tab-system)
4. [Memory Management & Tab Pausing](#memory-management)
5. [AI Integration (llama.cpp)](#ai-integration)
6. [Android Event System (droid command)](#android-events)
7. [Implementation Phases](#implementation-phases)

---

## 1. Current Architecture Overview <a name="current-architecture-overview"></a>

### Existing Components

| Component | Purpose |
|-----------|---------|
| `TermuxFloatService` | Foreground service managing the floating window lifecycle |
| `TermuxFloatView` | Main UI container (LinearLayout) with drag/resize gestures |
| `FloatingBubbleManager` | Handles minimization to 56dp circular bubble |
| `TermuxFloatViewClient` | Terminal view event handler (keyboard, gestures) |
| `TermuxFloatSessionClient` | Terminal session handler (bell, clipboard, colors) |

### Current Flow
```
Launcher → TermuxFloatActivity → TermuxFloatService
                                      ↓
                               TermuxFloatView (overlay)
                                      ↓
                               Single TermuxSession
```

### Key Technical Details
- Uses `WindowManager.TYPE_APPLICATION_OVERLAY` for overlay
- Single terminal session per service instance
- Position/size persisted to SharedPreferences
- Gesture handling: long-press to drag, pinch to resize

---

## 2. UI Redesign: Edge Panel with Swipe Gestures <a name="ui-redesign"></a>

### Proposed Design

Replace the floating bubble with:
1. **Minimized State**: A thin vertical edge bar (2-4px wide) anchored to the right edge of the screen, vertically centered
2. **Expanded State**: Full-screen terminal panel that slides in from the right

### Edge Bar Specifications
```
┌─────────────────────────────────────────┐
│                                         │
│                                         │
│                                       ║ │ ← 2-4px wide, ~200px tall
│                                       ║ │   semi-transparent indicator
│                                         │
│                                         │
└─────────────────────────────────────────┘
```

### Gesture System

| Gesture | Direction | Action |
|---------|-----------|--------|
| Swipe from edge bar inward | Right → Left | Expand to full-screen panel |
| Swipe from left screen edge | Left → Right | Collapse to edge bar |
| Tap on edge bar | - | Quick expand |
| Tap outside panel | - | Quick collapse |

### Implementation Approach

#### New Class: `EdgePanelManager.java`

```java
public class EdgePanelManager {
    private static final int EDGE_BAR_WIDTH_PX = 4;  // 2-4 pixels
    private static final int EDGE_BAR_HEIGHT_DP = 200;
    private static final int SWIPE_THRESHOLD_DP = 50;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;

    private boolean isExpanded = false;
    private View edgeIndicator;
    private GestureDetector gestureDetector;

    // Animation for slide in/out
    private ValueAnimator slideAnimator;

    public void expand() { /* Animate panel from right edge to full screen */ }
    public void collapse() { /* Animate panel to edge, show edge indicator */ }
}
```

#### Modifications to `TermuxFloatView.java`

```java
// Replace FloatingBubbleManager with EdgePanelManager
private EdgePanelManager mEdgePanelManager;

// Add swipe gesture detection
private GestureDetector mSwipeDetector;

@Override
public boolean onTouchEvent(MotionEvent event) {
    if (mSwipeDetector.onTouchEvent(event)) {
        return true;
    }
    // ... existing logic
}
```

#### Edge Detection View (Invisible Touch Target)

A separate invisible overlay view that covers the right edge of the screen to detect swipes when minimized:

```java
public class EdgeSwipeDetector extends View {
    // Always stays at right edge
    // Listens for left-swipe to trigger expand
    // Width: ~20dp for comfortable touch detection
    // Height: ~200dp centered vertically
}
```

### Layout Changes

**New: `layout/side_panel.xml`**
```xml
<FrameLayout>
    <!-- Edge indicator (visible when minimized) -->
    <View
        android:id="@+id/edge_indicator"
        android:layout_width="4dp"
        android:layout_height="200dp"
        android:layout_gravity="center_vertical|end"
        android:background="@drawable/edge_indicator" />

    <!-- Main panel (full screen when expanded) -->
    <LinearLayout
        android:id="@+id/panel_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:translationX="100%">  <!-- Initially off-screen -->

        <!-- Tab bar -->
        <HorizontalScrollView ... />

        <!-- Terminal view -->
        <com.termux.view.TerminalView ... />
    </LinearLayout>
</FrameLayout>
```

---

## 3. Tab System Implementation <a name="tab-system"></a>

### Design

```
┌─────────────────────────────────────────────────────────────┐
│ [Tab 1] [Tab 2] [Tab 3] [Tab 4] [+]    ←scroll→    [✕]     │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                     Terminal Content                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Tab Bar Specifications
- **Height**: 32-40dp (compact but touchable)
- **Tab width**: Auto-sized to content, min 60dp, max 120dp
- **Scrollable**: `HorizontalScrollView` wrapping `LinearLayout`
- **New tab button**: `[+]` at the end, always visible
- **Close panel button**: `[✕]` fixed at far right

### Data Model

```java
public class TerminalTab {
    private String id;
    private String title;                    // Auto-generated or user-set
    private TermuxSession session;
    private boolean isPaused;
    private boolean neverPause;              // Exempt from auto-pause
    private long lastActiveTimestamp;
    private String savedScrollPosition;      // For restoration
    private String savedTerminalBuffer;      // For paused tabs
}

public class TabManager {
    private List<TerminalTab> tabs;
    private int activeTabIndex;
    private static final int MAX_ACTIVE_TABS = 5;  // Before auto-pausing

    public TerminalTab createTab();
    public void switchToTab(int index);
    public void closeTab(int index);
    public void pauseTab(int index);
    public void resumeTab(int index);
    public void reorderTabs(int from, int to);
}
```

### Tab View Component

**New: `TabView.java`**
```java
public class TabView extends LinearLayout {
    private TextView titleView;
    private ImageButton closeButton;
    private View pauseIndicator;

    // Visual states
    private boolean isActive;
    private boolean isPaused;

    // Long-press context menu
    private PopupMenu contextMenu;

    public void setOnLongClickListener(...) {
        // Show menu: "Pause", "Never Pause", "Rename", "Close"
    }
}
```

### Tab Context Menu (Long-Press)

| Option | Description |
|--------|-------------|
| Pause | Immediately pause this tab |
| Resume | Resume a paused tab |
| Never Pause | Toggle auto-pause exemption |
| Rename | Set custom tab title |
| Close | Close tab and session |

### Service Changes

`TermuxFloatService` must now manage multiple sessions:

```java
public class TermuxFloatService extends Service {
    private TabManager mTabManager;
    private List<TermuxSession> mSessions;
    private int mActiveSessionIndex;

    public TermuxSession createNewSession() { ... }
    public void switchSession(int index) { ... }
    public void closeSession(int index) { ... }
}
```

---

## 4. Memory Management & Tab Pausing <a name="memory-management"></a>

### Pausing Strategy

When a tab is paused:
1. Save terminal buffer (transcript) to disk/memory
2. Save scroll position
3. Kill the underlying shell process (SIGTERM)
4. Release TerminalSession resources
5. Show visual indicator (grayed out tab)

When resumed:
1. Create new TerminalSession
2. Restore terminal buffer from saved state
3. Restore scroll position
4. Start new shell (user returns to prompt)

### Auto-Pause Logic

```java
public class MemoryManager {
    private static final int MAX_ACTIVE_SESSIONS = 3;
    private static final long INACTIVE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

    public void checkAndPauseInactiveTabs() {
        long now = System.currentTimeMillis();
        List<TerminalTab> candidates = tabs.stream()
            .filter(t -> !t.isActive())
            .filter(t -> !t.isNeverPause())
            .filter(t -> !t.isPaused())
            .sorted(Comparator.comparing(TerminalTab::getLastActiveTimestamp))
            .collect(Collectors.toList());

        // Pause oldest inactive tabs to stay under limit
        int activeSessions = countActiveSessions();
        for (TerminalTab tab : candidates) {
            if (activeSessions <= MAX_ACTIVE_SESSIONS) break;
            pauseTab(tab);
            activeSessions--;
        }
    }
}
```

### Terminal Buffer Serialization

```java
public class TerminalStateSerializer {
    public byte[] serializeBuffer(TerminalSession session) {
        // Serialize transcript rows
        // Serialize cursor position
        // Serialize color state
        // Compress with GZIP
    }

    public void deserializeIntoSession(byte[] data, TerminalSession session) {
        // Restore transcript
        // Restore cursor
        // Restore colors
    }
}
```

### Low Memory Handling

Register for system memory callbacks:

```java
public class TermuxFloatApplication extends Application
    implements ComponentCallbacks2 {

    @Override
    public void onTrimMemory(int level) {
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            // Aggressively pause all non-active tabs
            memoryManager.pauseAllInactive();
        }
    }
}
```

---

## 5. AI Integration (llama.cpp) <a name="ai-integration"></a>

### Overview

Integrate llama.cpp to provide a first-class AI assistant accessible via command line.

### Build Integration

Based on [llama.cpp Android documentation](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md):

**Option A: NDK Cross-Compilation (Recommended)**
```bash
cmake \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DCMAKE_C_FLAGS="-march=armv8.7a" \
  -DCMAKE_CXX_FLAGS="-march=armv8.7a" \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF \
  -B build-android
```

**Option B: Native Termux Build**
- Build within Termux environment directly
- Simpler integration, uses system libraries

### Command-Line Interface: `llama`

```bash
# Basic usage
$ echo "Explain this error" | llama

# With explicit prompt
$ cat error.log | llama -c "Summarize these errors and suggest fixes"

# Piped output
$ llama -c "Generate a bash script to..." | bash

# Interactive mode
$ llama -i

# Model selection
$ llama --model phi-3 -c "..."
```

### Implementation

**New: `llama` shell script in Termux bin**
```bash
#!/data/data/com.termux/files/usr/bin/bash
# Wrapper for llama.cpp

LLAMA_BIN="$PREFIX/lib/llama.cpp/llama-cli"
MODEL_DIR="$HOME/.local/share/llama/models"
DEFAULT_MODEL="$MODEL_DIR/default.gguf"

# Parse arguments
while getopts "c:m:i" opt; do
    case $opt in
        c) PROMPT="$OPTARG";;
        m) MODEL="$MODEL_DIR/$OPTARG.gguf";;
        i) INTERACTIVE=1;;
    esac
done

# Read stdin if available
if [ ! -t 0 ]; then
    STDIN_DATA=$(cat)
fi

# Build full prompt
if [ -n "$STDIN_DATA" ]; then
    FULL_PROMPT="$STDIN_DATA\n\n$PROMPT"
else
    FULL_PROMPT="$PROMPT"
fi

# Execute
$LLAMA_BIN -m "${MODEL:-$DEFAULT_MODEL}" \
    -c 4096 \
    --no-display-prompt \
    -p "$FULL_PROMPT"
```

### Model Management

**New: `llama-models` command**
```bash
$ llama-models list           # List installed models
$ llama-models download phi-3 # Download model
$ llama-models default phi-3  # Set default model
$ llama-models info phi-3     # Show model info
```

### Integration with Android (via JNI)

For deeper integration, create JNI bindings:

```java
public class LlamaInterface {
    static {
        System.loadLibrary("llama-android");
    }

    public native void loadModel(String modelPath);
    public native String generate(String prompt, int maxTokens);
    public native void unloadModel();
}
```

### AI Tools (Extended Capabilities)

The AI should have access to tools it can invoke:

| Tool | Description |
|------|-------------|
| `read_file` | Read file contents |
| `write_file` | Write to a file |
| `run_command` | Execute shell command |
| `search_files` | Search with grep/find |
| `android_intent` | Send Android intents |
| `clipboard` | Read/write clipboard |
| `notifications` | Send notifications |

---

## 6. Android Event System (`droid` command) <a name="android-events"></a>

### Overview

Create a command-line tool that hooks into Android system events and triggers shell commands.

### Command Syntax

```bash
# List available events
$ droid --list

# One-time trigger
$ droid battery_low "notify-send 'Battery Low!'"

# Loop N times
$ droid --count 5 screen_on "echo 'Screen turned on'"

# Persistent daemon (runs forever)
$ droid --daemon connectivity_change "./check-network.sh"

# Stop a running daemon
$ droid --stop <job-id>

# List running droid daemons
$ droid --jobs
```

### Available Events

Based on [Android Broadcast Receivers](https://developer.android.com/develop/api-guide/components/broadcasts):

| Event Name | Android Intent | Description |
|------------|----------------|-------------|
| `battery_low` | `ACTION_BATTERY_LOW` | Battery dropped below threshold |
| `battery_okay` | `ACTION_BATTERY_OKAY` | Battery back to acceptable level |
| `battery_changed` | `ACTION_BATTERY_CHANGED` | Any battery state change |
| `power_connected` | `ACTION_POWER_CONNECTED` | Charger plugged in |
| `power_disconnected` | `ACTION_POWER_DISCONNECTED` | Charger unplugged |
| `screen_on` | `ACTION_SCREEN_ON` | Screen turned on |
| `screen_off` | `ACTION_SCREEN_OFF` | Screen turned off |
| `connectivity_change` | `CONNECTIVITY_ACTION` | Network state changed |
| `wifi_state_changed` | `WIFI_STATE_CHANGED_ACTION` | WiFi enabled/disabled |
| `airplane_mode` | `ACTION_AIRPLANE_MODE_CHANGED` | Airplane mode toggled |
| `boot_completed` | `ACTION_BOOT_COMPLETED` | Device finished booting |
| `package_added` | `ACTION_PACKAGE_ADDED` | New app installed |
| `package_removed` | `ACTION_PACKAGE_REMOVED` | App uninstalled |
| `timezone_changed` | `ACTION_TIMEZONE_CHANGED` | Timezone changed |
| `locale_changed` | `ACTION_LOCALE_CHANGED` | System locale changed |
| `headset_plug` | `ACTION_HEADSET_PLUG` | Headphones plugged/unplugged |
| `media_mounted` | `ACTION_MEDIA_MOUNTED` | External storage mounted |
| `user_present` | `ACTION_USER_PRESENT` | User unlocked device |

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     droid CLI Tool                          │
│                   (Shell Script / Binary)                   │
└───────────────────────────┬─────────────────────────────────┘
                            │ Socket/IPC
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  DroidEventService                          │
│               (Android Foreground Service)                  │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │             BroadcastReceiver Pool                   │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐            │   │
│  │  │ Battery  │ │ Network  │ │ Screen   │ ...        │   │
│  │  │ Receiver │ │ Receiver │ │ Receiver │            │   │
│  │  └────┬─────┘ └────┬─────┘ └────┬─────┘            │   │
│  └───────┼────────────┼────────────┼───────────────────┘   │
│          │            │            │                        │
│          ▼            ▼            ▼                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Event Handler / Dispatcher              │   │
│  │    - Match event to registered jobs                  │   │
│  │    - Execute shell command                           │   │
│  │    - Track execution count                           │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Implementation

**New: `DroidEventService.java`**

```java
public class DroidEventService extends Service {
    private Map<String, BroadcastReceiver> activeReceivers = new HashMap<>();
    private List<DroidJob> registeredJobs = new ArrayList<>();

    public void registerJob(DroidJob job) {
        // Register appropriate BroadcastReceiver if not already active
        // Add job to list
    }

    public void unregisterJob(String jobId) {
        // Remove job
        // Unregister receiver if no more jobs for that event
    }

    private void onEventReceived(String eventType, Intent intent) {
        for (DroidJob job : registeredJobs) {
            if (job.getEventType().equals(eventType)) {
                executeJob(job, intent);
            }
        }
    }

    private void executeJob(DroidJob job, Intent intent) {
        // Build environment variables from intent extras
        Map<String, String> env = buildEnvFromIntent(intent);

        // Execute shell command
        Runtime.getRuntime().exec(new String[]{
            "/data/data/com.termux/files/usr/bin/bash",
            "-c",
            job.getCommand()
        }, env);

        // Track execution
        job.incrementCount();
        if (job.getMaxCount() > 0 && job.getCount() >= job.getMaxCount()) {
            unregisterJob(job.getId());
        }
    }
}
```

**Data Model:**

```java
public class DroidJob {
    private String id;           // Unique job ID
    private String eventType;    // e.g., "battery_low"
    private String command;      // Shell command to execute
    private int maxCount;        // 0 = infinite (daemon mode)
    private int executionCount;  // Current count
    private long createdAt;
}
```

### Notification Bar Integration

When droid daemons are running, show in notification:

```java
private Notification buildDaemonNotification() {
    int activeJobs = registeredJobs.size();
    return new NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Droid Event Monitor")
        .setContentText(activeJobs + " active event listeners")
        .setSmallIcon(R.drawable.ic_droid)
        .addAction(R.drawable.ic_stop, "Stop All", stopAllPendingIntent)
        .setOngoing(true)
        .build();
}
```

### CLI Implementation

**New: `droid` shell script**

```bash
#!/data/data/com.termux/files/usr/bin/bash

SOCKET="/data/data/com.termux/files/usr/tmp/droid.sock"

case "$1" in
    --list)
        cat <<EOF
Available events:
  battery_low        - Battery dropped below threshold
  battery_okay       - Battery back to acceptable level
  power_connected    - Charger plugged in
  power_disconnected - Charger unplugged
  screen_on          - Screen turned on
  screen_off         - Screen turned off
  connectivity_change - Network state changed
  wifi_state_changed - WiFi enabled/disabled
  boot_completed     - Device finished booting
  user_present       - User unlocked device
  ...
EOF
        ;;
    --daemon)
        # Register persistent job
        echo "REGISTER|$2|$3|0" | nc -U $SOCKET
        ;;
    --count)
        # Register counted job
        echo "REGISTER|$3|$4|$2" | nc -U $SOCKET
        ;;
    --stop)
        echo "UNREGISTER|$2" | nc -U $SOCKET
        ;;
    --jobs)
        echo "LIST" | nc -U $SOCKET
        ;;
    *)
        # One-time trigger
        echo "REGISTER|$1|$2|1" | nc -U $SOCKET
        ;;
esac
```

### Environment Variables Passed to Commands

When an event triggers, these environment variables are set:

| Variable | Description |
|----------|-------------|
| `DROID_EVENT` | Event type name |
| `DROID_TIMESTAMP` | Unix timestamp of event |
| `DROID_JOB_ID` | Job identifier |
| `DROID_BATTERY_LEVEL` | Battery percentage (for battery events) |
| `DROID_BATTERY_PLUGGED` | Charging type (for power events) |
| `DROID_NETWORK_TYPE` | Network type (for connectivity events) |
| `DROID_WIFI_STATE` | WiFi state (for WiFi events) |

---

## 7. Implementation Phases <a name="implementation-phases"></a>

### Phase 1: Edge Panel UI (Core)
1. Replace `FloatingBubbleManager` with `EdgePanelManager`
2. Implement swipe gesture detection
3. Create slide-in/out animations
4. Add edge indicator view
5. Update layout files
6. Handle screen rotation

**Files to modify:**
- `TermuxFloatView.java`
- `TermuxFloatService.java`
- `activity_main.xml` → `side_panel.xml`

**New files:**
- `EdgePanelManager.java`
- `EdgeSwipeDetector.java`
- `res/drawable/edge_indicator.xml`

### Phase 2: Tab System
1. Create `TabManager` and `TerminalTab` classes
2. Implement `TabView` component
3. Add horizontal scrolling tab bar
4. Modify service to manage multiple sessions
5. Implement tab switching
6. Add long-press context menu

**Files to modify:**
- `TermuxFloatService.java` (multi-session support)
- Layout files

**New files:**
- `TabManager.java`
- `TerminalTab.java`
- `TabView.java`
- `TabBarView.java`
- `res/layout/tab_item.xml`

### Phase 3: Memory Management
1. Implement terminal buffer serialization
2. Add pause/resume logic
3. Create `MemoryManager` class
4. Register for system memory callbacks
5. Add visual pause indicators
6. Implement never-pause flag

**New files:**
- `MemoryManager.java`
- `TerminalStateSerializer.java`

### Phase 4: llama.cpp Integration
1. Set up NDK cross-compilation for llama.cpp
2. Create JNI bindings (optional, for deep integration)
3. Bundle compiled binaries
4. Create `llama` CLI wrapper script
5. Implement `llama-models` management tool
6. Add AI tools framework

**New files:**
- `jni/llama-android.cpp`
- `LlamaInterface.java`
- `assets/bin/llama`
- `assets/bin/llama-models`

### Phase 5: Android Event System
1. Create `DroidEventService`
2. Implement broadcast receiver pool
3. Create IPC mechanism (Unix socket)
4. Build `droid` CLI tool
5. Add notification integration
6. Document all available events

**New files:**
- `DroidEventService.java`
- `DroidJob.java`
- `DroidEventReceiver.java`
- `assets/bin/droid`

---

## Technical Considerations

### Permissions Required

```xml
<!-- Existing -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.INTERNET" />

<!-- New for droid events -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

### Android API Considerations

- **Min SDK 24**: Supports all proposed features
- **Broadcast Restrictions (API 26+)**: Many broadcasts require dynamic registration
- **Foreground Service Requirements (API 28+)**: Must show notification
- **Background Execution Limits**: Use WorkManager for guaranteed execution

### Memory Footprint

| Component | Estimated Memory |
|-----------|------------------|
| Edge Panel UI | ~5 MB |
| Per active tab | ~10-20 MB |
| Per paused tab | ~1-2 MB (serialized buffer) |
| llama.cpp (loaded) | ~2-8 GB depending on model |
| DroidEventService | ~5 MB |

### Model Size Recommendations for Mobile

| Model | Size | RAM Required | Quality |
|-------|------|--------------|---------|
| Phi-3-mini-4k (Q4) | ~2 GB | 4 GB | Good |
| Llama-3.2-1B (Q4) | ~1 GB | 2 GB | Basic |
| Qwen2-1.5B (Q4) | ~1.5 GB | 3 GB | Good |
| TinyLlama-1.1B (Q4) | ~0.6 GB | 1.5 GB | Basic |

---

## References

- [llama.cpp Android Build Docs](https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md)
- [Android Gesture Detection](https://developer.android.com/develop/ui/views/touch-and-input/gestures/detector)
- [Android Broadcast Receivers](https://developer.android.com/develop/api-guide/components/broadcasts)
- [Battery Monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring)
- [Termux Shared Library](https://github.com/termux/termux-app/tree/master/termux-shared)
