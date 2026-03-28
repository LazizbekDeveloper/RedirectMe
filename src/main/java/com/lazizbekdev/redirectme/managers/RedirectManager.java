package com.lazizbekdev.redirectme.managers;

import com.lazizbekdev.redirectme.RedirectMe;
import com.lazizbekdev.redirectme.utils.RedirectLogger;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;

/**
 * RedirectManager - Handles all player redirection logic
 * 
 * This manager is responsible for:
 * - Sending players to the target server via BungeeCord messaging
 * - Playing sound effects during redirect
 * - Logging all redirect events to file and console
 * 
 * NO VISUAL MESSAGES: Only sound effect is played during redirect.
 * 
 * @author lazizbekdev
 */
public class RedirectManager implements PluginMessageListener {

    private final RedirectMe plugin;
    private final String targetServer;
    private final RedirectLogger logger;
    
    // Sound configuration
    private final boolean soundEnabled;
    private final Sound soundType;
    private final float soundVolume;
    private final float soundPitch;

    public RedirectManager(RedirectMe plugin) {
        this.plugin = plugin;
        this.targetServer = plugin.getConfig().getString("target-server", "lobby");
        this.logger = plugin.getRedirectLogger();
        
        // Load sound configuration
        this.soundEnabled = plugin.getConfig().getBoolean("effects.sound-enabled", true);
        
        String soundName = plugin.getConfig().getString("effects.sound-type", "BLOCK_NOTE_BLOCK_PLING");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound type: " + soundName + ". Using BLOCK_NOTE_BLOCK_PLING instead.");
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }
        this.soundType = sound;
        this.soundVolume = (float) plugin.getConfig().getDouble("effects.sound-volume", 1.0);
        this.soundPitch = (float) plugin.getConfig().getDouble("effects.sound-pitch", 1.5);
    }

    /**
     * Starts the redirect sequence.
     * 
     * This method:
     * 1. Plays a sound effect to all players
     * 2. Redirects all players to the target server
     * 3. Logs all redirect events
     * 
     * NO VISUAL MESSAGES are sent (no title, actionbar, bossbar, broadcast).
     */
    public void startRedirect() {
        // Play sound effect if enabled
        if (soundEnabled) {
            playRedirectSound();
        }

        // Redirect all players immediately
        redirectAllPlayers("Server shutdown/restart");
    }

    /**
     * Redirects all online players to the target server.
     * 
     * Uses BungeeCord PluginMessage channel to send the "Connect" command.
     * All redirect events are logged to file and console.
     * 
     * @param reason The reason for the redirect
     */
    public void redirectAllPlayers(String reason) {
        int redirectedCount = 0;
        int failedCount = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sendToLobby(player, reason)) {
                redirectedCount++;
            } else {
                failedCount++;
                
                // Kick player if redirect fails and configured
                if (plugin.getConfig().getBoolean("kick-on-fail", true)) {
                    String kickMsg = plugin.colorize(plugin.getConfig().getString("kick-message",
                            "&cServer is currently restarting. Please reconnect in a few seconds."));
                    player.kickPlayer(kickMsg);
                    logger.logRedirectFailure(player.getName(), "Redirect failed - kicked");
                }
            }
        }

        // Log summary to console
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  Redirect Summary");
        plugin.getLogger().info("  Total Players: " + (redirectedCount + failedCount));
        plugin.getLogger().info("  Successfully Redirected: " + redirectedCount);
        plugin.getLogger().info("  Failed: " + failedCount);
        plugin.getLogger().info("  Target Server: " + targetServer);
        plugin.getLogger().info("========================================");
        
        // Send to BungeeCord console if enabled
        if (plugin.getConfig().getBoolean("logging.bungee-console", true)) {
            sendToBungeeConsole("[RedirectMe] Redirected " + redirectedCount + " players to " + targetServer);
        }
    }

    /**
     * Sends a single player to the lobby/target server.
     * 
     * This method uses the BungeeCord PluginMessage channel to send
     * a "Connect" command to BungeeCord, which then transfers the player.
     * 
     * GHOST CONNECTIONS PREVENTION:
     * - We send the message synchronously on the main thread
     * - We don't close the connection immediately after sending
     * - The server shutdown is delayed to allow BungeeCord to process
     * 
     * @param player The player to redirect
     * @param reason The reason for redirect (for logging)
     * @return true if the redirect message was sent successfully
     */
    public boolean sendToLobby(Player player, String reason) {
        try {
            // Create the PluginMessage payload
            // Format: [Command: String][Target Server: String]
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            
            // Write the "Connect" command
            out.writeUTF("Connect");
            // Write the target server name
            out.writeUTF(targetServer);
            
            // Send the plugin message to the player
            // BungeeCord intercepts this and handles the transfer
            player.sendPluginMessage(plugin, RedirectMe.BUNGEE_CHANNEL, byteArray.toByteArray());
            
            // Log the redirect
            logger.logRedirect(player.getName(), targetServer, reason);
            
            return true;
            
        } catch (IOException e) {
            logger.logRedirectFailure(player.getName(), "IOException: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Failed to send redirect for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Plays the redirect sound effect to all players.
     */
    private void playRedirectSound() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), soundType, soundVolume, soundPitch);
        }

        plugin.getLogger().info("Played redirect sound: " + soundType.name());
    }

    /**
     * Sends a message to the BungeeCord proxy console.
     * 
     * @param message The message to send
     */
    private void sendToBungeeConsole(String message) {
        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            
            out.writeUTF("Message");
            out.writeUTF("CONSOLE");
            out.writeUTF(message);
            
            // Send to first online player (BungeeCord will route to proxy)
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            if (players.length > 0) {
                players[0].sendPluginMessage(plugin, RedirectMe.BUNGEE_CHANNEL, byteArray.toByteArray());
            }
            
        } catch (IOException e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().log(Level.WARNING, "Failed to send message to BungeeCord console", e);
            }
        }
    }

    /**
     * Handles incoming plugin messages (not used for outgoing BungeeCord messages,
     * but required for the PluginMessageListener interface).
     */
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        // This is for incoming messages from BungeeCord
        // Currently not used
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Received plugin message on channel: " + channel);
        }
    }

    /**
     * Gets the configured target server name.
     * @return The target server name
     */
    public String getTargetServer() {
        return targetServer;
    }
}
