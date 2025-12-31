package com.termux.window;

import android.content.Context;

import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalBuffer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Handles serialization and deserialization of terminal state for tab pausing.
 * Saves terminal buffer content so it can be restored when a tab is resumed.
 */
public class TerminalStateSerializer {
    private static final String LOG_TAG = "TerminalStateSerializer";
    private static final int SERIALIZATION_VERSION = 1;

    private final Context mContext;
    private final File mCacheDir;

    public TerminalStateSerializer(Context context) {
        mContext = context;
        mCacheDir = new File(context.getCacheDir(), "terminal_state");
        if (!mCacheDir.exists()) {
            mCacheDir.mkdirs();
        }
    }

    /**
     * Serialize terminal state to a byte array.
     * Captures the visible terminal content and cursor position.
     */
    public byte[] serializeTerminalState(TerminalSession session) throws IOException {
        if (session == null) {
            throw new IOException("Session is null");
        }

        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null) {
            throw new IOException("Emulator is null");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(baos);
        DataOutputStream dos = new DataOutputStream(gzip);

        try {
            // Write version
            dos.writeInt(SERIALIZATION_VERSION);

            // Write screen dimensions
            dos.writeInt(emulator.mRows);
            dos.writeInt(emulator.mColumns);

            // Write cursor position
            dos.writeInt(emulator.getCursorRow());
            dos.writeInt(emulator.getCursorCol());

            // Write screen content
            // We'll capture the visible content as text for simplicity
            // A full implementation would save the buffer with colors/attributes
            TerminalBuffer screen = emulator.getScreen();

            // Get transcript (scrollback + screen)
            int totalRows = screen.getActiveTranscriptRows() + emulator.mRows;
            dos.writeInt(totalRows);

            // Write each row
            for (int row = -screen.getActiveTranscriptRows(); row < emulator.mRows; row++) {
                String line = screen.getSelectedText(0, row, emulator.mColumns - 1, row, true, true);
                if (line == null) line = "";
                dos.writeUTF(line);
            }

            // Write title if available
            String title = session.getTitle();
            dos.writeUTF(title != null ? title : "");

            dos.flush();
            gzip.finish();

            Logger.logDebug(LOG_TAG, "Serialized terminal state: " + baos.size() + " bytes");
            return baos.toByteArray();

        } finally {
            dos.close();
        }
    }

    /**
     * Restore terminal state from a byte array.
     * Writes the saved content back to the terminal.
     */
    public void restoreTerminalState(TerminalSession session, byte[] data) throws IOException {
        if (session == null || data == null) {
            throw new IOException("Session or data is null");
        }

        TerminalEmulator emulator = session.getEmulator();
        if (emulator == null) {
            throw new IOException("Emulator is null");
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        GZIPInputStream gzip = new GZIPInputStream(bais);
        DataInputStream dis = new DataInputStream(gzip);

        try {
            // Read and verify version
            int version = dis.readInt();
            if (version != SERIALIZATION_VERSION) {
                throw new IOException("Unsupported serialization version: " + version);
            }

            // Read screen dimensions (we may need to handle size changes)
            int savedRows = dis.readInt();
            int savedCols = dis.readInt();

            // Read cursor position
            int cursorRow = dis.readInt();
            int cursorCol = dis.readInt();

            // Read total rows
            int totalRows = dis.readInt();

            // Build content to write to terminal
            StringBuilder content = new StringBuilder();

            // Write a header indicating this is restored content
            content.append("\r\n[Session restored from saved state]\r\n");
            content.append("─".repeat(Math.min(savedCols, 40))).append("\r\n");

            // Read and display saved lines
            // Only show the last screenful of content to avoid overwhelming
            int skipRows = Math.max(0, totalRows - emulator.mRows + 5);
            for (int i = 0; i < totalRows; i++) {
                String line = dis.readUTF();
                if (i >= skipRows) {
                    content.append(line).append("\r\n");
                }
            }

            content.append("─".repeat(Math.min(savedCols, 40))).append("\r\n");

            // Read title
            String title = dis.readUTF();

            // Write content to terminal
            // This will display the saved content to the user
            byte[] bytes = content.toString().getBytes();
            session.write(bytes, 0, bytes.length);

            Logger.logDebug(LOG_TAG, "Restored terminal state: " + totalRows + " rows");

        } finally {
            dis.close();
        }
    }

    /**
     * Save terminal state to a file.
     * Used for persistent storage across app restarts.
     */
    public void saveToFile(String tabId, byte[] data) throws IOException {
        File file = new File(mCacheDir, tabId + ".state");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        Logger.logDebug(LOG_TAG, "Saved state to file: " + file.getPath());
    }

    /**
     * Load terminal state from a file.
     */
    public byte[] loadFromFile(String tabId) throws IOException {
        File file = new File(mCacheDir, tabId + ".state");
        if (!file.exists()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            Logger.logDebug(LOG_TAG, "Loaded state from file: " + file.getPath());
            return baos.toByteArray();
        }
    }

    /**
     * Delete saved state file.
     */
    public void deleteStateFile(String tabId) {
        File file = new File(mCacheDir, tabId + ".state");
        if (file.exists()) {
            file.delete();
            Logger.logDebug(LOG_TAG, "Deleted state file: " + file.getPath());
        }
    }

    /**
     * Clean up all cached state files.
     */
    public void cleanup() {
        File[] files = mCacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".state")) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Get total size of cached state files.
     */
    public long getCacheSize() {
        long size = 0;
        File[] files = mCacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
        return size;
    }
}
