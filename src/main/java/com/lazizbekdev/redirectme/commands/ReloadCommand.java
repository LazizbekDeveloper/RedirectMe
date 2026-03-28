package com.lazizbekdev.redirectme.commands;

import com.lazizbekdev.redirectme.RedirectMe;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ReloadCommand - Handles /redirectme reload and /rdmreload
 * 
 * Allows administrators to reload the plugin configuration
 * without restarting the server.
 * 
 * @author lazizbekdev
 */
public class ReloadCommand implements CommandExecutor, TabCompleter {

    private final RedirectMe plugin;
    
    // Available subcommands for tab completion
    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "help", "version");

    public ReloadCommand(RedirectMe plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Check permission
        if (!sender.hasPermission("redirectme.admin") && !sender.hasPermission("redirectme.reload")) {
            String noPermMsg = plugin.getConfig().getString("messages.no-permission",
                    "&cYou don't have permission to use this command!");
            sender.sendMessage(plugin.colorize(noPermMsg));
            return true;
        }

        // Handle subcommands
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
            case "help":
                sendHelp(sender);
                break;
            case "version":
                handleVersion(sender);
                break;
            default:
                sender.sendMessage(plugin.colorize("&cUnknown subcommand. Use /redirectme help"));
                break;
        }

        return true;
    }

    /**
     * Handles the reload subcommand.
     * 
     * @param sender The command sender
     */
    private void handleReload(CommandSender sender) {
        try {
            // Reload the configuration
            plugin.reloadConfig();
            
            // Validate the reloaded configuration
            String targetServer = plugin.getConfig().getString("target-server");
            if (targetServer == null || targetServer.isEmpty()) {
                sender.sendMessage(plugin.colorize("&cReload failed: target-server is not configured!"));
                return;
            }

            // Success message
            String successMsg = plugin.getConfig().getString("messages.reload-success",
                    "&aRedirectMe configuration reloaded successfully!");
            sender.sendMessage(plugin.colorize(successMsg));
            
            // Log to console
            plugin.getLogger().info("Configuration reloaded by " + 
                    (sender instanceof Player ? ((Player) sender).getName() : "Console"));
            
            // Show updated config summary
            sender.sendMessage(plugin.colorize("&7Target Server: &e" + targetServer));
            sender.sendMessage(plugin.colorize("&7Redirect Delay: &e" + 
                    plugin.getConfig().getInt("redirect-delay-ticks", 40) + " ticks"));

        } catch (Exception e) {
            sender.sendMessage(plugin.colorize("&cReload failed: " + e.getMessage()));
            plugin.getLogger().warning("Failed to reload configuration: " + e.getMessage());
        }
    }

    /**
     * Handles the version subcommand.
     * 
     * @param sender The command sender
     */
    private void handleVersion(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String author = plugin.getDescription().getAuthors().isEmpty() ? 
                "lazizbekdev" : plugin.getDescription().getAuthors().get(0);
        
        sender.sendMessage(plugin.colorize("&6&lRedirectMe &7v" + version));
        sender.sendMessage(plugin.colorize("&7Author: &e" + author));
        sender.sendMessage(plugin.colorize("&7Platform: &eSpigot/Paper 1.20+"));
    }

    /**
     * Sends help message to the sender.
     * 
     * @param sender The command sender
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.colorize("&6&l=== RedirectMe Help ==="));
        sender.sendMessage(plugin.colorize("&e/redirectme reload &7- Reload configuration"));
        sender.sendMessage(plugin.colorize("&e/redirectme version &7- Show plugin version"));
        sender.sendMessage(plugin.colorize("&e/redirectme help &7- Show this help"));
        sender.sendMessage(plugin.colorize("&e/rdmreload &7- Quick reload command"));
        sender.sendMessage(plugin.colorize("&6&l======================"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Only show tab completion for admin users
        if (!sender.hasPermission("redirectme.admin") && !sender.hasPermission("redirectme.reload")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(sub -> sub.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
