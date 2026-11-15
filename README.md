# UltimateDonut SMP Minecraft Plugin

UltimateDonut SMP is a Minecraft server plugin for Paper that provides an economy, player payments, coinflip and dice wagering, configurable spawn and PvP arena teleportation, arena-only player combat, persistent balances, and administrator controls.

## Install

1. Use a Paper Minecraft server running version 1.21.11 or a compatible 1.21 server.
2. Install Java 21 or newer.
3. Build the plugin with Maven:

```bash
mvn clean package
```

4. Copy the generated `target/plugin.jar` file into the server's `plugins` directory.
5. Start or restart the server.
6. Confirm that the plugin loads successfully in the server console.

The plugin stores balances and configured locations in `plugins/DonutCore/data.yml`.

## Use

Players can use the following commands:

- `/balance [player]` or `/bal [player]` shows a player's balance.
- `/pay <player> <amount>` transfers money to an online player.
- `/coinflip <amount> <heads|tails>` wagers on a coinflip. A correct guess returns the wager; an incorrect guess loses it.
- `/dice <amount> <number 1-6>` wagers on a dice roll. A correct guess pays five times the wager amount after the original wager is removed.
- `/spawn` teleports the player to the configured spawn location.
- `/arena join` adds the player to the PvP arena and teleports them to its configured location.
- `/arena leave` removes the player from the arena and teleports them to spawn when spawn is configured.

Operators or users with the `donut.admin` permission can use:

- `/donut setspawn` saves the administrator's current location as spawn.
- `/donut setarena` saves the administrator's current location as the arena.
- `/donut give <player> <amount>` adds money to a player's balance.
- `/donut take <player> <amount>` removes money from a player's balance.
- `/donut set <player> <amount>` sets a player's balance.

Money amounts support up to two decimal places. Balances are saved when they change, when a player leaves, and when the server shuts down. Player-versus-player damage is cancelled unless both players are currently in the arena.

---

More Minecraft plugin projects by EnderDevelopment: https://github.com/EnderDevelopment

Project page: https://dash.enderdevelopment.com/community/d67ccd29-3e42-4272-874d-0c7267db248f?utm_source=github&utm_medium=readme&utm_campaign=auto_publish&utm_content=d67ccd29-3e42-4272-874d-0c7267db248f
