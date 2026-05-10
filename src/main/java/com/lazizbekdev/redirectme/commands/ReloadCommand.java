package com.lazizbekdev.redirectme.commands;

import com.lazizbekdev.redirectme.RedirectMe;
import org.bukkit.Bukkit;
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
 * RedirectMeCommand (formerly ReloadCommand) - Handles plugin commands
 * 
 * Allows administrators to reload configuration, manually redirect players,
 * or trigger a force shutdown.
 * 
 * @author lazizbekdev
 */
public class ReloadCommand implements CommandExecutor, TabCompleter {

    private final RedirectMe plugin;
    
    // Available subcommands for tab completion
    private static final List<String> SUBCOMMANDS = Arrays.asList("reload", "help", "version", "all", "force-shutdown");

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
            case "all":
                if (!sender.hasPermission("redirectme.admin")) {
                    sender.sendMessage(plugin.colorize("&cYou don't have permission to use this command!"));
                    return true;
                }
                plugin.getRedirectManager().redirectAllPlayers("Manual /redirectme all command");
                sender.sendMessage(plugin.colorize("&aRedirecting all online players to the fallback server."));
                break;
            case "force-shutdown":
                if (!sender.hasPermission("redirectme.admin")) {
                    sender.sendMessage(plugin.colorize("&cYou don't have permission to use this command!"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.colorize("&cUsage: /redirectme force-shutdown <command>"));
                    sender.sendMessage(plugin.colorize("&cExample: /redirectme force-shutdown restart"));
                    return true;
                }
                String targetCommand = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                sender.sendMessage(plugin.colorize("&aInitiating force shutdown with command: " + targetCommand));
                plugin.getCommandGuardListener().handleShutdownCommand(sender, targetCommand);
                break;
            default:
                // If it's not a known subcommand, try to interpret as a player name
                if (sender.hasPermission("redirectme.admin")) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null) {
                        plugin.getRedirectManager().sendToLobby(target, "Manual redirect");
                        sender.sendMessage(plugin.colorize("&aRedirected player " + target.getName() + "."));
                    } else {
                        sender.sendMessage(plugin.colorize("&cUnknown subcommand or player not found. Use /redirectme help"));
                    }
                } else {
                    sender.sendMessage(plugin.colorize("&cUnknown subcommand. Use /redirectme help"));
                }
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
        sender.sendMessage(plugin.colorize("&e/redirectme all &7- Redirect all players"));
        sender.sendMessage(plugin.colorize("&e/redirectme <player> &7- Redirect a specific player"));
        sender.sendMessage(plugin.colorize("&e/redirectme force-shutdown <cmd> &7- Triggers redirect and runs cmd"));
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
            List<String> completions = new ArrayList<>(SUBCOMMANDS);
            
            // Add online player names to completion if they have admin permission
            if (sender.hasPermission("redirectme.admin")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
            
            return completions.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("force-shutdown")) {
            return Arrays.asList("restart", "stop").stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
