package com.lazizbekdev.redirectme.listeners;

import com.lazizbekdev.redirectme.RedirectMe;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.logging.Level;

/**
 * CommandGuardListener - Intercepts shutdown commands
 * 
 * This listener monitors both player and console commands for shutdown-related
 * commands. When detected, it cancels the original command, initiates a player
 * redirect with sound only (no visual messages), and then executes the shutdown
 * after a safe delay.
 * 
 * @author lazizbekdev
 */
public class CommandGuardListener implements Listener {

    private final RedirectMe plugin;
    
    // Flag to prevent recursive command execution
    private boolean isShuttingDown = false;

    public CommandGuardListener(RedirectMe plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player-executed commands.
     * 
     * Uses HIGH priority to ensure we process before most other plugins,
     * but after essential plugins like permissions.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage();
        
        // Check if this is an intercepted command
        if (!isInterceptedCommand(command)) {
            return;
        }

        // Check for bypass permission
        if (player.hasPermission("redirectme.bypass")) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("Player " + player.getName() + " bypassed redirect.");
            }
            return;
        }

        // Check for admin permission
        if (!player.hasPermission("redirectme.admin")) {
            // Player doesn't have permission - show error and cancel
            event.setCancelled(true);
            String noPermMsg = plugin.getConfig().getString("messages.no-permission", 
                    "&cYou don't have permission to use this command!");
            player.sendMessage(plugin.colorize(noPermMsg));
            return;
        }

        // Cancel the original command - we'll execute it after redirect
        event.setCancelled(true);

        // Initiate the shutdown sequence
        handleShutdownCommand(player, command);
    }

    /**
     * Handles console-executed commands.
     * 
     * This catches commands from server console, command blocks, and
     * any other non-player sources.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        String command = event.getCommand();
        
        // Check if this is an intercepted command
        if (!isInterceptedCommand(command)) {
            return;
        }

        // Don't intercept if we're already shutting down (prevents loops)
        if (isShuttingDown) {
            return;
        }

        // Cancel the original command - we'll execute it after redirect
        event.setCancelled(true);

        // Initiate the shutdown sequence
        // Use null sender for console commands
        handleShutdownCommand(null, command);
    }

    /**
     * Checks if a command string matches any intercepted command.
     * 
     * @param command The full command string (e.g., "/stop", "restart")
     * @return true if the command should be intercepted
     */
    private boolean isInterceptedCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }

        // Remove leading slash and get the base command
        String baseCommand = command.startsWith("/") ? command.substring(1) : command;
        
        // Extract just the command name (before any arguments)
        String[] parts = baseCommand.split("\\s+");
        String commandName = parts[0].toLowerCase();

        // Get configured intercept commands
        List<String> interceptCommands = plugin.getConfig().getStringList("intercept-commands");
        
        // Case-insensitive comparison
        for (String intercepted : interceptCommands) {
            if (intercepted.equalsIgnoreCase(commandName)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Handles the shutdown sequence when an intercepted command is detected.
     * 
     * @param sender The command sender (null for console)
     * @param command The original command that was intercepted
     */
    private void handleShutdownCommand(CommandSender sender, String command) {
        // Prevent recursive shutdown attempts
        if (isShuttingDown) {
            return;
        }
        isShuttingDown = true;

        // Get configuration values
        int delayTicks = plugin.getConfig().getInt("redirect-delay-ticks", 40);
        int playerCount = Bukkit.getOnlinePlayers().size();

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  SHUTDOWN COMMAND INTERCEPTED");
        plugin.getLogger().info("  Command: " + command);
        plugin.getLogger().info("  Online Players: " + playerCount);
        plugin.getLogger().info("  Starting redirect sequence...");
        plugin.getLogger().info("========================================");

        // Send message to BungeeCord console if enabled
        if (plugin.getConfig().getBoolean("logging.bungee-console", true)) {
            sendToBungeeConsole("[RedirectMe] Server shutdown initiated by " + 
                    (sender != null ? sender.getName() : "Console"));
        }

        // Start the redirect (sound only, no visual messages)
        plugin.getRedirectManager().startRedirect();

        // Schedule the actual shutdown command after the redirect delay
        // This delay is CRITICAL to avoid ghost connections:
        // - Gives BungeeCord time to process the Connect message
        // - Allows players to fully transfer to the target server
        // - Ensures BungeeCord's player-server mapping is updated
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                plugin.getLogger().info("Executing shutdown command: " + command);
                
                // Log shutdown to file
                plugin.getRedirectLogger().logShutdown(command, playerCount);
                
                // Dispatch the original command to actually shut down the server
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to execute shutdown command: " + e.getMessage(), e);
                
                // Fallback: Force server shutdown
                plugin.getLogger().warning("Attempting forced shutdown...");
                Bukkit.shutdown();
            }
        }, delayTicks);
    }

    /**
     * Sends a message to the BungeeCord proxy console.
     * 
     * @param message The message to send
     */
    private void sendToBungeeConsole(String message) {
        try {
            java.io.ByteArrayOutputStream byteArray = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(byteArray);
            
            out.writeUTF("Message");
            out.writeUTF("CONSOLE");
            out.writeUTF(message);
            
            // Send to first online player (BungeeCord will route to proxy)
            Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
            if (players.length > 0) {
                players[0].sendPluginMessage(plugin, RedirectMe.BUNGEE_CHANNEL, byteArray.toByteArray());
            }
            
        } catch (java.io.IOException e) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().log(Level.WARNING, "Failed to send message to BungeeCord console", e);
            }
        }
    }

    /**
     * Checks if the plugin is currently in a shutdown sequence.
     * This is used to prevent recursive command interception.
     * 
     * @return true if shutdown is in progress
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }
}
