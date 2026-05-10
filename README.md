# RedirectMe

**Author:** lazizbekdev  
**Version:** 1.0.1
**Platform:** Spigot/Paper 1.20+

A Minecraft Spigot plugin that automatically redirects all players to a fallback server (e.g., Lobby) before the current server shuts down, restarts, or crashes.

## Features

- **Command Interception:** Automatically intercepts `/stop`, `/restart`, `/reload`, and other configured commands
- **BungeeCord Integration:** Uses PluginMessage channel to seamlessly transfer players
- **Sound-Only Notifications:** No visual messages (titles, actionbars, bossbars) - only sound effect
- **Daily Log Files:** All redirect events logged to `logs/` folder with timestamps
- **Console Logging:** Full redirect logging to server console
- **BungeeCord Console:** Optional logging to BungeeCord proxy console
- **Safety Net:** Handles unexpected shutdowns via `onDisable()` hook
- **Configurable:** Fully customizable settings, commands, and permissions

## Requirements

- **Spigot** or **Paper** server (1.20+)
- **BungeeCord** or **Waterfall** proxy (required for player redirection)

## Installation

1. Build the plugin using Maven:
   ```bash
   mvn clean package
   ```

2. Copy the generated JAR file from `target/RedirectMe-1.0.0.jar` to your server's `plugins/` folder

3. Restart your server

4. Configure `plugins/RedirectMe/config.yml` with your BungeeCord server name

## Configuration

### config.yml

```yaml
# Target BungeeCord server name
target-server: "lobby"

# Delay before shutdown (in ticks, 20 ticks = 1 second)
redirect-delay-ticks: 40

# Commands to intercept
intercept-commands:
  - "stop"
  - "restart"
  - "rl"
  - "reload"

# Sound effects
effects:
  sound-enabled: true
  sound-type: "BLOCK_NOTE_BLOCK_PLING"
  sound-volume: 1.0
  sound-pitch: 1.5

# Logging settings
logging:
  enabled: true
  console: true
  bungee-console: true
```

## Commands

| Command | Alias | Permission | Description |
|---------|-------|------------|-------------|
| `/redirectme` | `/rdm`, `/redirect` | `redirectme.admin` | Main command |
| `/redirectme reload` | - | `redirectme.reload` | Reload configuration |
| `/redirectme help` | - | `redirectme.admin` | Show help |
| `/redirectme version` | - | `redirectme.admin` | Show version info |
| `/rdmreload` | `/redirectmereload` | `redirectme.reload` | Quick reload |

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `redirectme.admin` | OP | Full admin access |
| `redirectme.reload` | OP | Reload configuration |

## Logging

### Log Files Location
```
plugins/RedirectMe/logs/redirect-YYYY-MM-DD.log
```

### Log Format
```
[HH:mm:ss] REDIRECT | Player: <name> | Target: <server> | Reason: <reason>
[HH:mm:ss] FAILED | Player: <name> | Reason: <reason>
[HH:mm:ss] SHUTDOWN | Command: <cmd> | Players Redirected: <count>
[HH:mm:ss] STARTUP | RedirectMe v<version> enabled | Target: <server>
[HH:mm:ss] DISABLE | RedirectMe disabled
```

### Example Log Output
```
[15:30:45] REDIRECT | Player: Steve | Target: lobby | Reason: Server shutdown/restart
[15:30:45] REDIRECT | Player: Alex | Target: lobby | Reason: Server shutdown/restart
[15:30:47] SHUTDOWN | Command: stop | Players Redirected: 2
```

## How It Works

### Redirect Flow

```
User executes /stop
       ↓
CommandGuardListener intercepts
       ↓
Cancel original command
       ↓
Play sound to all players (NO visual messages)
       ↓
Send PluginMessage to BungeeCord for each player
       ↓
Log each redirect to file and console
       ↓
Wait for redirect-delay-ticks (40 ticks = 2 seconds)
       ↓
Execute original /stop command
       ↓
Server shuts down
```

### Ghost Connections Prevention

When using BungeeCord messaging, "ghost connections" can occur when a player appears to be online on multiple servers simultaneously. This plugin prevents ghost connections through:

1. **Delayed Shutdown:** After sending the redirect message, the plugin waits for the configured `redirect-delay-ticks` (default: 40 ticks = 2 seconds) before executing the actual shutdown command.

2. **Synchronous Message Sending:** PluginMessages are sent on the main server thread to prevent race conditions.

3. **BungeeCord Processing Time:** The delay allows BungeeCord's 50ms tick cycle to:
   - Receive the Connect request
   - Transfer the player to the target server
   - Update internal player-server mappings
   - Send confirmation

4. **Safety Net:** The `onDisable()` method attempts to kick any remaining players if the server shuts down unexpectedly.

## Building from Source

```bash
# Clone the repository
git clone https://github.com/lazizbekdev/RedirectMe.git
cd RedirectMe

# Build with Maven
mvn clean package

# The JAR will be in target/RedirectMe-1.0.0.jar
```

## Project Structure

```
RedirectMe/
├── src/main/java/com/lazizbekdev/redirectme/
│   ├── RedirectMe.java              # Main plugin class
│   ├── commands/
│   │   └── ReloadCommand.java       # Reload command handler
│   ├── listeners/
│   │   └── CommandGuardListener.java # Command interception
│   ├── managers/
│   │   └── RedirectManager.java     # Player redirection + logging
│   └── utils/
│       └── RedirectLogger.java      # Daily log file management
├── src/main/resources/
│   ├── plugin.yml                   # Plugin metadata
│   └── config.yml                   # Default configuration
└── pom.xml                          # Maven build configuration
```

## Troubleshooting

### Players not being redirected

1. Ensure BungeeCord/Waterfall is running
2. Verify `target-server` matches your BungeeCord config.yml
3. Check that the target server is online and accessible
4. Enable debug mode in config.yml for detailed logs
5. Check `logs/` folder for redirect logs

### No sound playing

1. Check `effects.sound-enabled` is set to `true`
2. Verify the `sound-type` is valid for your server version
3. Check player client sound settings

### Logs not being created

1. Check `logging.enabled` is set to `true`
2. Verify the plugin has write permissions to the `plugins/RedirectMe/` folder
3. Check server console for any error messages

## License

This project is proprietary software. All rights reserved by lazizbekdev.

## Support

For issues, suggestions, or questions, please contact lazizbekdev.
