package com.lazizbekdev.redirectme.listeners;

import com.lazizbekdev.redirectme.RedirectMe;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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
 * redirect with messages, and then executes the shutdown after a safe delay
 * (or immediately if no players are online).
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
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage();
        
        // Check if this is an intercepted command
        if (!isInterceptedCommand(command)) {
            return;
        }

        // Cancel the original command - we'll execute it after redirect
        event.setCancelled(true);

        // Initiate the shutdown sequence
        handleShutdownCommand(event.getPlayer(), command);
    }

    /**
     * Handles console-executed commands.
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
        handleShutdownCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Checks if a command string matches any intercepted command.
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
     */
    public void handleShutdownCommand(CommandSender sender, String command) {
        // Prevent recursive shutdown attempts
        if (isShuttingDown) {
            return;
        }
        isShuttingDown = true;

        int playerCount = Bukkit.getOnlinePlayers().size();

        plugin.getLogger().info("========================================");
        plugin.getLogger().info("  SHUTDOWN COMMAND INTERCEPTED");
        plugin.getLogger().info("  Command: " + command);
        plugin.getLogger().info("  Online Players: " + playerCount);
        plugin.getLogger().info("  Starting redirect sequence...");
        plugin.getLogger().info("========================================");

        // Start the redirect
        plugin.getRedirectManager().startRedirect();

        // If no players are online, we skip the delay and shutdown immediately
        int delayTicks = playerCount == 0 ? 0 : plugin.getConfig().getInt("redirect-delay-ticks", 40);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                plugin.getLogger().info("Executing shutdown command: " + command);
                
                // Log shutdown to file
                plugin.getRedirectLogger().logShutdown(command, playerCount);
                
                // Remove leading slash if present to prevent Bukkit bugs when dispatching from console
                String commandToExecute = command.startsWith("/") ? command.substring(1) : command;
                
                // Dispatch the original command to actually shut down the server
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToExecute);
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to execute shutdown command: " + e.getMessage(), e);
                
                // Fallback: Force server shutdown
                plugin.getLogger().warning("Attempting forced shutdown...");
                Bukkit.shutdown();
            }
        }, delayTicks);
    }

    /**
     * Checks if the plugin is currently in a shutdown sequence.
     */
    public boolean isShuttingDown() {
        return isShuttingDown;
    }
}
