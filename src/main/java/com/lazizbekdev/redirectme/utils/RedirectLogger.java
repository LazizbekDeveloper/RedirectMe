package com.lazizbekdev.redirectme.utils;

import com.lazizbekdev.redirectme.RedirectMe;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/**
 * RedirectLogger - Handles daily log files for redirect events
 * 
 * Creates and manages log files in the logs/ folder with daily rotation.
 * Each log entry includes timestamp and player information.
 * 
 * @author lazizbekdev
 */
public class RedirectLogger {

    private final RedirectMe plugin;
    private final File logsFolder;
    private PrintWriter currentWriter;
    private String currentDate;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat timeFormat;

    public RedirectLogger(RedirectMe plugin) {
        this.plugin = plugin;
        this.logsFolder = new File(plugin.getDataFolder(), "logs");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        this.timeFormat = new SimpleDateFormat("HH:mm:ss");
        
        // Create logs folder if it doesn't exist
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        
        // Initialize with current date
        this.currentDate = dateFormat.format(new Date());
        openLogFile();
    }

    /**
     * Opens or creates the log file for the current date.
     */
    private void openLogFile() {
        try {
            // Close existing writer if open
            if (currentWriter != null) {
                currentWriter.close();
            }
            
            // Create new log file for current date
            String logFileName = "redirect-" + currentDate + ".log";
            File logFile = new File(logsFolder, logFileName);
            
            // Use FileWriter with append mode
            currentWriter = new PrintWriter(new FileWriter(logFile, true));
            
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open log file: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if the date has changed and rotates log file if needed.
     */
    private void checkDateRotation() {
        String newDate = dateFormat.format(new Date());
        if (!newDate.equals(currentDate)) {
            currentDate = newDate;
            openLogFile();
        }
    }

    /**
     * Logs a player redirect event with timestamp.
     * 
     * @param playerName The name of the redirected player
     * @param targetServer The target server name
     * @param reason The reason for redirect
     */
    public void logRedirect(String playerName, String targetServer, String reason) {
        checkDateRotation();
        
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format("[%s] REDIRECT | Player: %s | Target: %s | Reason: %s",
                timestamp, playerName, targetServer, reason);
        
        // Write to file
        if (currentWriter != null) {
            currentWriter.println(logEntry);
            currentWriter.flush();
        }
        
        // Also log to console
        plugin.getLogger().info("[Redirect] " + playerName + " -> " + targetServer + " (" + reason + ")");
    }

    /**
     * Logs a redirect failure event.
     * 
     * @param playerName The name of the player
     * @param reason The reason for failure
     */
    public void logRedirectFailure(String playerName, String reason) {
        checkDateRotation();
        
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format("[%s] FAILED | Player: %s | Reason: %s",
                timestamp, playerName, reason);
        
        // Write to file
        if (currentWriter != null) {
            currentWriter.println(logEntry);
            currentWriter.flush();
        }
        
        // Also log to console as warning
        plugin.getLogger().warning("[Redirect Failed] " + playerName + " - " + reason);
    }

    /**
     * Logs a shutdown event.
     * 
     * @param command The shutdown command that was executed
     * @param playerCount Number of players that were redirected
     */
    public void logShutdown(String command, int playerCount) {
        checkDateRotation();
        
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format("[%s] SHUTDOWN | Command: %s | Players Redirected: %d",
                timestamp, command, playerCount);
        
        // Write to file
        if (currentWriter != null) {
            currentWriter.println(logEntry);
            currentWriter.flush();
        }
        
        // Also log to console
        plugin.getLogger().info("[Shutdown] Executing " + command + " after redirecting " + playerCount + " players");
    }

    /**
     * Logs plugin startup information.
     */
    public void logStartup() {
        checkDateRotation();
        
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format("[%s] STARTUP | RedirectMe v%s enabled | Target: %s",
                timestamp, plugin.getDescription().getVersion(), 
                plugin.getConfig().getString("target-server", "lobby"));
        
        // Write to file
        if (currentWriter != null) {
            currentWriter.println(logEntry);
            currentWriter.flush();
        }
    }

    /**
     * Logs plugin shutdown information.
     */
    public void logPluginDisable() {
        checkDateRotation();
        
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format("[%s] DISABLE | RedirectMe disabled", timestamp);
        
        // Write to file
        if (currentWriter != null) {
            currentWriter.println(logEntry);
            currentWriter.flush();
            currentWriter.close();
        }
    }

    /**
     * Closes the log file writer.
     */
    public void close() {
        if (currentWriter != null) {
            currentWriter.close();
        }
    }
}
