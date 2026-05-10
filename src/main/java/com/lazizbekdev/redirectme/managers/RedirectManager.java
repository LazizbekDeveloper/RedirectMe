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
 * - Showing configurable visual messages
 * - Logging all redirect events to file and console
 * 
 * @author lazizbekdev
 */
public class RedirectManager implements PluginMessageListener {

    private final RedirectMe plugin;
    private final RedirectLogger logger;

    public RedirectManager(RedirectMe plugin) {
        this.plugin = plugin;
        this.logger = plugin.getRedirectLogger();
    }

    /**
     * Gets the configured target server name directly from config.
     * @return The target server name
     */
    public String getTargetServer() {
        return plugin.getConfig().getString("target-server", "lobby");
    }

    /**
     * Starts the redirect sequence.
     */
    public void startRedirect() {
        // Redirect all players immediately
        redirectAllPlayers("Server shutdown/restart");
    }

    /**
     * Redirects all online players to the target server.
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
                
                if (plugin.getConfig().getBoolean("kick-on-fail", true)) {
                    String kickMsg = plugin.colorize(plugin.getConfig().getString("messages.kick-message",
                            "&cServer is currently restarting. Please reconnect in a few seconds."));
                    player.kickPlayer(kickMsg);
                    logger.logRedirectFailure(player.getName(), "Redirect failed - kicked");
                }
            }
        }

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  Redirect Summary");
        plugin.getLogger().info("  Total Players: " + (redirectedCount + failedCount));
        plugin.getLogger().info("  Successfully Redirected: " + redirectedCount);
        plugin.getLogger().info("  Failed: " + failedCount);
        plugin.getLogger().info("  Target Server: " + getTargetServer());
        plugin.getLogger().info("========================================");
    }

    /**
     * Sends a single player to the lobby/target server.
     * 
     * @param player The player to redirect
     * @param reason The reason for redirect (for logging)
     * @return true if the redirect message was sent successfully
     */
    public boolean sendToLobby(Player player, String reason) {
        playRedirectSound(player);
        sendVisualMessages(player);

        try {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteArray);
            
            out.writeUTF("Connect");
            out.writeUTF(getTargetServer());
            
            player.sendPluginMessage(plugin, RedirectMe.BUNGEE_CHANNEL, byteArray.toByteArray());
            
            logger.logRedirect(player.getName(), getTargetServer(), reason);
            
            return true;
            
        } catch (IOException e) {
            logger.logRedirectFailure(player.getName(), "IOException: " + e.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Failed to send redirect for " + player.getName(), e);
            return false;
        }
    }

    /**
     * Plays the redirect sound effect to a specific player.
     */
    private void playRedirectSound(Player player) {
        if (!plugin.getConfig().getBoolean("effects.sound-enabled", true)) return;

        String soundName = plugin.getConfig().getString("effects.sound-type", "BLOCK_NOTE_BLOCK_PLING");
        Sound sound;
        try {
            sound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            sound = Sound.BLOCK_NOTE_BLOCK_PLING;
        }

        float volume = (float) plugin.getConfig().getDouble("effects.sound-volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("effects.sound-pitch", 1.5);

        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Sends configured visual messages (chat, title) to the player.
     */
    private void sendVisualMessages(Player player) {
        String chatMsg = plugin.getConfig().getString("messages.redirect-chat", "");
        if (chatMsg != null && !chatMsg.isEmpty()) {
            player.sendMessage(plugin.colorize(chatMsg));
        }

        String titleMsg = plugin.getConfig().getString("messages.redirect-title", "");
        String subtitleMsg = plugin.getConfig().getString("messages.redirect-subtitle", "");
        
        boolean hasTitle = titleMsg != null && !titleMsg.isEmpty();
        boolean hasSubtitle = subtitleMsg != null && !subtitleMsg.isEmpty();

        if (hasTitle || hasSubtitle) {
            player.sendTitle(
                hasTitle ? plugin.colorize(titleMsg) : "",
                hasSubtitle ? plugin.colorize(subtitleMsg) : "",
                10, 70, 20
            );
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("Received plugin message on channel: " + channel);
        }
    }
}
