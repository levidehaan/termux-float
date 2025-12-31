package com.termux.window;

import android.content.Context;
import android.content.res.AssetManager;

import com.termux.shared.logger.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class to install CLI scripts from assets to Termux's bin directory.
 * Scripts are installed to $PREFIX/bin where they can be executed from the terminal.
 */
public class ScriptInstaller {
    private static final String LOG_TAG = "ScriptInstaller";

    // Termux prefix path
    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String TERMUX_BIN = TERMUX_PREFIX + "/bin";

    // Scripts to install
    private static final String[] SCRIPTS = {
            "droid",
            "llama",
            "llama-models"
    };

    /**
     * Install all CLI scripts from assets to Termux bin directory.
     * Scripts are only installed if they don't exist or are outdated.
     */
    public static void installScripts(Context context) {
        File binDir = new File(TERMUX_BIN);

        // Check if Termux is installed
        if (!binDir.exists()) {
            Logger.logDebug(LOG_TAG, "Termux bin directory not found, skipping script installation");
            return;
        }

        AssetManager assetManager = context.getAssets();

        for (String script : SCRIPTS) {
            try {
                installScript(assetManager, script, binDir);
            } catch (IOException e) {
                Logger.logError(LOG_TAG, "Failed to install script: " + script);
                Logger.logStackTrace(LOG_TAG, e);
            }
        }
    }

    /**
     * Install a single script from assets.
     */
    private static void installScript(AssetManager assetManager, String scriptName, File binDir) throws IOException {
        String assetPath = "bin/" + scriptName;
        File destFile = new File(binDir, scriptName);

        // Read script from assets
        InputStream in = null;
        OutputStream out = null;

        try {
            in = assetManager.open(assetPath);

            // Check if we need to update
            if (destFile.exists()) {
                // Compare sizes - if different, update
                int assetSize = in.available();
                if (destFile.length() == assetSize) {
                    Logger.logDebug(LOG_TAG, "Script already installed: " + scriptName);
                    return;
                }
            }

            // Write to destination
            out = new FileOutputStream(destFile);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();

            // Make executable
            destFile.setExecutable(true, false);

            Logger.logDebug(LOG_TAG, "Installed script: " + scriptName + " to " + destFile.getAbsolutePath());

        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Check if the CLI scripts are installed.
     */
    public static boolean areScriptsInstalled() {
        File binDir = new File(TERMUX_BIN);
        if (!binDir.exists()) {
            return false;
        }

        for (String script : SCRIPTS) {
            File scriptFile = new File(binDir, script);
            if (!scriptFile.exists() || !scriptFile.canExecute()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Uninstall all CLI scripts.
     */
    public static void uninstallScripts() {
        File binDir = new File(TERMUX_BIN);
        if (!binDir.exists()) {
            return;
        }

        for (String script : SCRIPTS) {
            File scriptFile = new File(binDir, script);
            if (scriptFile.exists()) {
                if (scriptFile.delete()) {
                    Logger.logDebug(LOG_TAG, "Uninstalled script: " + script);
                } else {
                    Logger.logError(LOG_TAG, "Failed to uninstall script: " + script);
                }
            }
        }
    }
}
