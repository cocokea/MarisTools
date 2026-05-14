# MarisTools

MarisTools is a tool distribution plugin for giving configured utility items to players.

## What It Handles

- Configurable tool definitions
- Command-based tool delivery
- Plugin reload for updated tool configuration

## Requirements

- Paper / Folia 1.21+
- Java 21

## Installation

1. Put the plugin jar in `plugins`.
2. Start the server once.
3. Edit `tools.yml`, `config.yml`, and `message.yml`.
4. Restart the server.

## Command

- `/tools give <player> <tool>` - Give a configured tool.
- `/tools reload` - Reload plugin files.

## Files

- `tools.yml` - Tool definitions.
- `config.yml` - Main settings.
- `message.yml` - Command and error messages.

## Notes

- Tool behavior depends on the definitions stored in `tools.yml`.
- Keep item identifiers and metadata consistent with your target server version.