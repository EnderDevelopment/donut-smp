package com.enderdevelopment.plugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class Main extends JavaPlugin implements Listener {
    private final Map<UUID, Long> balances = new HashMap<>();
    private final java.util.Set<UUID> arenaPlayers = new java.util.HashSet<>();
    private final Random random = new Random();
    private YamlConfiguration data;
    private Location spawn;
    private Location arena;

    @Override public void onEnable() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "data.yml"));
        if (data.getConfigurationSection("balances") != null) {
            for (String key : data.getConfigurationSection("balances").getKeys(false)) {
                try { balances.put(UUID.fromString(key), data.getLong("balances." + key)); }
                catch (IllegalArgumentException ex) { getLogger().warning("Skipping invalid balance UUID: " + key); }
            }
        }
        spawn = loadLocation("spawn");
        arena = loadLocation("arena");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override public void onDisable() { saveData(); }

    @EventHandler public void onQuit(PlayerQuitEvent event) { arenaPlayers.remove(event.getPlayer().getUniqueId()); saveData(); }

    @EventHandler public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker
                && (!arenaPlayers.contains(victim.getUniqueId()) || !arenaPlayers.contains(attacker.getUniqueId()))) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("PvP is enabled only for players in the arena.", NamedTextColor.RED));
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balanceCommand(sender, args);
            case "pay" -> payCommand(sender, args);
            case "coinflip" -> coinflipCommand(sender, args);
            case "dice" -> diceCommand(sender, args);
            case "spawn" -> spawnCommand(sender);
            case "arena" -> arenaCommand(sender, args);
            case "donut" -> adminCommand(sender, args);
            default -> false;
        };
    }

    private boolean balanceCommand(CommandSender sender, String[] args) {
        if (args.length > 1) return false;
        OfflinePlayer target;
        if (args.length == 1) {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) { error(sender, "Player not found."); return true; }
        } else {
            if (!(sender instanceof Player player)) { error(sender, "Specify a player from console."); return true; }
            target = player;
        }
        sender.sendMessage(Component.text(target.getName() + "'s balance: $" + money(balance(target.getUniqueId())), NamedTextColor.GOLD));
        return true;
    }

    private boolean payCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { error(sender, "Only players can pay."); return true; }
        if (args.length != 2) return false;
        Player target = Bukkit.getPlayerExact(args[0]);
        Long amount = parseAmount(args[1]);
        if (target == null) { error(sender, "That player is not online."); return true; }
        if (target.equals(player)) { error(sender, "You cannot pay yourself."); return true; }
        if (amount == null) { error(sender, "Enter a positive amount with at most two decimals."); return true; }
        if (!withdraw(player.getUniqueId(), amount)) { error(sender, "Insufficient funds."); return true; }
        deposit(target.getUniqueId(), amount);
        player.sendMessage(Component.text("Paid $" + money(amount) + " to " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Received $" + money(amount) + " from " + player.getName() + ".", NamedTextColor.GREEN));
        saveData(); return true;
    }

    private boolean coinflipCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { error(sender, "Only players can wager."); return true; }
        if (args.length != 2) return false;
        Long amount = parseAmount(args[0]);
        if (amount == null || !(args[1].equalsIgnoreCase("heads") || args[1].equalsIgnoreCase("tails"))) return false;
        if (!withdraw(player.getUniqueId(), amount)) { error(sender, "Insufficient funds."); return true; }
        String result = random.nextBoolean() ? "heads" : "tails";
        if (result.equalsIgnoreCase(args[1])) { deposit(player.getUniqueId(), amount); player.sendMessage(Component.text("Coin landed " + result + ": wager returned, plus equal winnings.", NamedTextColor.GREEN)); }
        else player.sendMessage(Component.text("Coin landed " + result + ": wager lost.", NamedTextColor.RED));
        saveData(); return true;
    }

    private boolean diceCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { error(sender, "Only players can wager."); return true; }
        if (args.length != 2) return false;
        Long amount = parseAmount(args[0]);
        int guess;
        try { guess = Integer.parseInt(args[1]); } catch (NumberFormatException ex) { return false; }
        if (amount == null || guess < 1 || guess > 6) return false;
        if (!withdraw(player.getUniqueId(), amount)) { error(sender, "Insufficient funds."); return true; }
        int result = random.nextInt(6) + 1;
        if (guess == result) { deposit(player.getUniqueId(), amount * 5); player.sendMessage(Component.text("Rolled " + result + ": you won five times your wager.", NamedTextColor.GREEN)); }
        else player.sendMessage(Component.text("Rolled " + result + ": wager lost.", NamedTextColor.RED));
        saveData(); return true;
    }

    private boolean spawnCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) { error(sender, "Only players can teleport."); return true; }
        if (spawn == null) { error(sender, "Spawn is not configured."); return true; }
        player.teleport(spawn); return true;
    }

    private boolean arenaCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { error(sender, "Only players can use the arena."); return true; }
        if (args.length != 1) return false;
        if (args[0].equalsIgnoreCase("join")) {
            if (arena == null) { error(sender, "Arena is not configured."); return true; }
            arenaPlayers.add(player.getUniqueId()); player.teleport(arena);
            player.sendMessage(Component.text("Arena joined. Use /arena leave to exit.", NamedTextColor.RED));
        } else if (args[0].equalsIgnoreCase("leave")) {
            arenaPlayers.remove(player.getUniqueId());
            if (spawn != null) player.teleport(spawn);
            player.sendMessage(Component.text("Arena left.", NamedTextColor.GREEN));
        } else return false;
        return true;
    }

    private boolean adminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("donut.admin")) { error(sender, "You do not have permission."); return true; }
        if (args.length == 1 && (args[0].equalsIgnoreCase("setspawn") || args[0].equalsIgnoreCase("setarena"))) {
            if (!(sender instanceof Player player)) { error(sender, "Run this command in game."); return true; }
            String key = args[0].equalsIgnoreCase("setspawn") ? "spawn" : "arena";
            if (key.equals("spawn")) spawn = player.getLocation().clone(); else arena = player.getLocation().clone();
            saveLocation(key, key.equals("spawn") ? spawn : arena);
            sender.sendMessage(Component.text(key + " location saved.", NamedTextColor.GREEN)); return true;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take") || args[0].equalsIgnoreCase("set"))) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            Long amount = parseAmount(args[2]);
            if (amount == null) { error(sender, "Enter a positive amount with at most two decimals."); return true; }
            long current = balance(target.getUniqueId());
            if (args[0].equalsIgnoreCase("give")) deposit(target.getUniqueId(), amount);
            else if (args[0].equalsIgnoreCase("take")) { if (current < amount) { error(sender, "Insufficient balance."); return true; } withdraw(target.getUniqueId(), amount); }
            else balances.put(target.getUniqueId(), amount);
            saveData(); sender.sendMessage(Component.text("Balance updated.", NamedTextColor.GREEN)); return true;
        }
        return false;
    }

    private long balance(UUID id) { return balances.getOrDefault(id, 0L); }
    private void deposit(UUID id, long amount) { balances.put(id, Math.addExact(balance(id), amount)); }
    private boolean withdraw(UUID id, long amount) { if (balance(id) < amount) return false; balances.put(id, balance(id) - amount); return true; }
    private String money(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    private Long parseAmount(String text) {
        try { BigDecimal value = new BigDecimal(text); if (value.signum() <= 0 || value.scale() > 2) return null; return value.movePointRight(2).longValueExact(); }
        catch (NumberFormatException | ArithmeticException ex) { return null; }
    }
    private void error(CommandSender sender, String message) { sender.sendMessage(Component.text(message, NamedTextColor.RED)); }

    private Location loadLocation(String key) {
        String path = "locations." + key;
        if (!data.contains(path + ".world")) return null;
        World world = Bukkit.getWorld(data.getString(path + ".world", ""));
        if (world == null) return null;
        return new Location(world, data.getDouble(path + ".x"), data.getDouble(path + ".y"), data.getDouble(path + ".z"), (float) data.getDouble(path + ".yaw"), (float) data.getDouble(path + ".pitch"));
    }
    private void saveLocation(String key, Location location) {
        String path = "locations." + key; data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX()); data.set(path + ".y", location.getY()); data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw()); data.set(path + ".pitch", location.getPitch()); saveData();
    }
    private void saveData() {
        if (data == null) return;
        for (Map.Entry<UUID, Long> entry : balances.entrySet()) data.set("balances." + entry.getKey(), entry.getValue());
        try { data.save(new File(getDataFolder(), "data.yml")); }
        catch (IOException ex) { getLogger().severe("Could not save plugin data: " + ex.getMessage()); }
    }
}
