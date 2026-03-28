package com.lazizbekdev.redirectme;

import com.lazizbekdev.redirectme.commands.ReloadCommand;
import com.lazizbekdev.redirectme.listeners.CommandGuardListener;
import com.lazizbekdev.redirectme.managers.RedirectManager;
import com.lazizbekdev.redirectme.utils.RedirectLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * RedirectMe - Main Plugin Class
 * 
 * Automatically redirects all players to a fallback server (e.g., Lobby)
 * before the current server shuts down, restarts, or crashes.
 * 
 * Features:
 * - Sound-only notifications (no visual messages)
 * - Daily log files in logs/ folder
 * - Console and BungeeCord console logging
 * 
 * @author lazizbekdev
 * @version 1.0.0
 */
public class RedirectMe extends JavaPlugin {

    // BungeeCord messaging channel name
    public static final String BUNGEE_CHANNEL = "BungeeCord";

    // Configuration instance
    private FileConfiguration config;

    // Manager and Logger instances
    private RedirectManager redirectManager;
    private CommandGuardListener commandGuardListener;
    private RedirectLogger redirectLogger;

    // Plugin state
    private boolean enabled = false;

    @Override
    public void onEnable() {
        // Save default config if not exists
        saveDefaultConfig();

        // Load configuration
        config = getConfig();

        // Validate configuration
        if (!validateConfig()) {
            getLogger().severe("Configuration validation failed. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize logger first
        redirectLogger = new RedirectLogger(this);

        // Initialize managers and listeners
        initializeComponents();

        // Register BungeeCord messaging channel
        registerBungeeChannel();

        // Register commands
        registerCommands();

        // Log startup message
        logStartupMessage();
        
        // Log startup to file
        redirectLogger.logStartup();

        enabled = true;
    }

    @Override
    public void onDisable() {
        enabled = false;

        // Safety Net: Attempt to kick remaining players
        if (Bukkit.getOnlinePlayers().isEmpty() == false) {
            getLogger().info("Safety Net triggered: Kicking remaining players...");
            
            Bukkit.getOnlinePlayers().forEach(player -> {
                String kickMessage = colorize(config.getString("kick-message", 
                        "&cServer is restarting. Please reconnect in a few seconds."));
                player.kickPlayer(kickMessage);
                redirectLogger.logRedirect(player.getName(), "KICKED", "Server shutdown");
            });
        }

        // Unregister BungeeCord channel to prevent memory leaks
        try {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(this, BUNGEE_CHANNEL);
            Bukkit.getMessenger().unregisterIncomingPluginChannel(this, BUNGEE_CHANNEL);
        } catch (Exception e) {
            // Channel may already be unregistered
        }

        // Close logger
        if (redirectLogger != null) {
            redirectLogger.logPluginDisable();
            redirectLogger.close();
        }

        getLogger().info("RedirectMe has been disabled.");
    }

    /**
     * Validates the configuration file for required values.
     * @return true if configuration is valid
     */
    private boolean validateConfig() {
        // Check target server is configured
        String targetServer = config.getString("target-server");
        if (targetServer == null || targetServer.isEmpty()) {
            getLogger().severe("target-server is not configured in config.yml!");
            return false;
        }

        // Check redirect delay is positive
        int delayTicks = config.getInt("redirect-delay-ticks", 40);
        if (delayTicks < 0) {
            getLogger().severe("redirect-delay-ticks must be positive!");
            return false;
        }

        return true;
    }

    /**
     * Initializes all plugin components (managers, listeners).
     */
    private void initializeComponents() {
        // Initialize RedirectManager - handles all player redirection logic
        redirectManager = new RedirectManager(this);

        // Initialize CommandGuardListener - intercepts shutdown commands
        commandGuardListener = new CommandGuardListener(this);

        // Register event listeners
        Bukkit.getPluginManager().registerEvents(commandGuardListener, this);
    }

    /**
     * Registers the BungeeCord outgoing plugin channel.
     * 
     * GHOST CONNECTIONS EXPLANATION:
     * ==============================
     * When using BungeeCord messaging, "ghost connections" can occur when:
     * 1. A player is sent to another server but the original server crashes
     *    before receiving confirmation from BungeeCord
     * 2. The player appears online on both servers temporarily
     * 3. BungeeCord may think the player is still on the original server
     * 
     * HOW WE AVOID GHOST CONNECTIONS:
     * ===============================
     * 1. We use a delay (redirect-delay-ticks) between sending the Connect message
     *    and executing the shutdown. This gives BungeeCord time to:
     *    - Receive the Connect request
     *    - Move the player to the target server
     *    - Send confirmation back
     *    - Update its internal player-server mapping
     * 
     * 2. We use Bukkit.getScheduler().runTask() to ensure the PluginMessage
     *    is sent on the main server thread, preventing race conditions.
     * 
     * 3. We don't immediately shutdown - we wait for the configured delay,
     *    allowing BungeeCord's 50ms tick to process the connection transfer.
     * 
     * 4. In onDisable(), we attempt to kick remaining players as a safety net.
     */
    private void registerBungeeChannel() {
        try {
            // Register outgoing channel for sending Connect messages to BungeeCord
            Bukkit.getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);
            getLogger().info("BungeeCord channel registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register BungeeCord channel: " + e.getMessage());
            getLogger().severe("Player redirection will NOT work without BungeeCord/Waterfall!");
        }
    }

    /**
     * Registers plugin commands.
     */
    private void registerCommands() {
        // Register ReloadCommand for /redirectme reload and /rdmreload
        ReloadCommand reloadCommand = new ReloadCommand(this);
        
        getCommand("redirectme").setExecutor(reloadCommand);
        getCommand("redirectme").setTabCompleter(reloadCommand);
        getCommand("rdmreload").setExecutor(reloadCommand);
    }

    /**
     * Logs the startup message to console.
     */
    private void logStartupMessage() {
        getLogger().info("========================================");
        getLogger().info("  RedirectMe v" + getDescription().getVersion());
        getLogger().info("  Author: lazizbekdev");
        getLogger().info("========================================");
        getLogger().info("  Target Server: " + config.getString("target-server"));
        getLogger().info("  Redirect Delay: " + config.getInt("redirect-delay-ticks", 40) + " ticks");
        getLogger().info("  Intercepted Commands: " + config.getStringList("intercept-commands"));
        getLogger().info("  Logs Folder: " + new java.io.File(getDataFolder(), "logs").getAbsolutePath());
        getLogger().info("========================================");
        getLogger().info("Plugin enabled successfully!");
    }

    /**
     * Utility method to translate color codes in strings.
     * Supports legacy color codes (&c, &a, etc.) and hex colors (&#RRGGBB).
     * 
     * @param text The text to colorize
     * @return The colorized text
     */
    public String colorize(String text) {
        if (text == null) return "";
        
        // Use Bukkit's ChatColor translator for legacy codes
        String colored = org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
        
        // For hex colors (&#RRGGBB), use reflection to check for ChatColor.of() method
        try {
            java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
            java.util.regex.Matcher hexMatcher = hexPattern.matcher(colored);
            StringBuffer buffer = new StringBuffer();
            
            while (hexMatcher.find()) {
                String hex = hexMatcher.group(1);
                // Use reflection to call ChatColor.of() if available
                java.lang.reflect.Method ofMethod = org.bukkit.ChatColor.class.getMethod("of", String.class);
                org.bukkit.ChatColor chatColor = (org.bukkit.ChatColor) ofMethod.invoke(null, "#" + hex);
                hexMatcher.appendReplacement(buffer, chatColor.toString());
            }
            hexMatcher.appendTail(buffer);
            colored = buffer.toString();
        } catch (Exception e) {
            // Hex colors not supported on this version - ignore silently
            if (config.getBoolean("debug", false)) {
                getLogger().log(Level.FINE, "Hex color support not available, using legacy colors only", e);
            }
        }
        
        return colored;
    }

    /**
     * Gets the RedirectManager instance.
     * @return The RedirectManager
     */
    public RedirectManager getRedirectManager() {
        return redirectManager;
    }

    /**
     * Gets the RedirectLogger instance.
     * @return The RedirectLogger
     */
    public RedirectLogger getRedirectLogger() {
        return redirectLogger;
    }

    /**
     * Checks if the plugin is fully enabled.
     * @return true if enabled
     */
    public boolean isPluginEnabled() {
        return enabled;
    }
}
