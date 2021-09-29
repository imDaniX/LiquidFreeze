package me.imdanix.liquid;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LiquidFreeze extends JavaPlugin implements Listener {
    private Database database;
    private Set<Location> toRestore;
    private Set<Location> cache;
    private boolean frozen;
    private boolean forced;

    private String broadcastOn;
    private String broadcastOff;
    private String messagePlace;
    private Timing timing;
    private boolean auto;
    private double low;
    private double recovery;
    private int recoversPerTick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        database = new Database(this);
        cache = new HashSet<>();
        reload();

        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.runTaskAsynchronously(this, () -> {
            toRestore = database.loadLocations();
            if (!toRestore.isEmpty()) getLogger().info("Loaded " + toRestore.size() + " blocks to update.");
        });
        scheduler.runTaskTimer(this, () -> {
            if (!auto) return;
            double tps = Bukkit.getTPS()[timing.ordinal()];
            if (frozen) {
                if (tps >= recovery && !forced) {
                    turn(false);
                }
            } else {
                if (tps <= low) {
                    turn(true);
                }
            }
        }, 1200, 1200);
        scheduler.runTaskTimer(this, () -> {
            dropCache(scheduler);
            if (!frozen && !toRestore.isEmpty()) {
                Iterator<Location> iterator = toRestore.iterator();
                List<Location> toRemove = new ArrayList<>(recoversPerTick);
                while (toRemove.size() <= recoversPerTick && iterator.hasNext()) {
                    Location location = iterator.next();
                    if (!location.isChunkLoaded()) {
                        location.getWorld().getChunkAtAsync(location).thenAccept((chunk) -> update(location));
                    } else {
                        update(location);
                    }
                    toRemove.add(location);
                    iterator.remove();
                }
                scheduler.runTaskLaterAsynchronously(this, () -> toRemove.forEach(database::removeLocation), 2);
            }
        }, 10, 10);
    }

    @Override
    public void onDisable() {
        dropCache(null);
    }

    private void dropCache(BukkitScheduler scheduler) {
        if (!cache.isEmpty()) {
            List<Location> diff = new ArrayList<>();
            for (Location location : cache) {
                if (toRestore.add(location)) {
                    diff.add(location);
                }
            }
            cache.clear();
            if (!diff.isEmpty()) {
                if (scheduler != null)
                    scheduler.runTaskAsynchronously(this, () -> diff.forEach(database::saveLocation));
                else
                    diff.forEach(database::saveLocation);
            }
        }
    }

    private static void update(Location location) {
        Block block = location.getBlock();
        if (block.isLiquid()) {
            Levelled data = (Levelled) block.getBlockData();
            if (data.getLevel() == 0) {
                Material type = block.getType();
                block.setType(Material.AIR, false);
                block.setType(type, true);
            } else {
                block.setType(block.getType(), false);
                block.setBlockData(data, true);
            }
        }
    }

    private void reload() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        broadcastOn = clr(cfg.getString("broadcast.freeze", ""));
        broadcastOff = clr(cfg.getString("broadcast.continue", ""));
        messagePlace = clr(cfg.getString("broadcast.place", ""));
        try {
            timing = Timing.valueOf(cfg.getString("trigger.timing", "FIVE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            timing = Timing.FIVE;
        }
        auto = cfg.getBoolean("trigger.enabled", true);
        low = cfg.getDouble("trigger.low", 10);
        recovery = cfg.getDouble("trigger.recovery", 16);
        recoversPerTick = cfg.getInt("trigger.recovers-per-tick", 2);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLiquid(BlockFromToEvent event) {
        Block block = event.getBlock();
        if (block.isLiquid()) {
            event.setCancelled(true);
            cache.add(block.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) {
        if (!messagePlace.isEmpty()) event.getPlayer().sendMessage(messagePlace);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIce(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.ICE && !messagePlace.isEmpty()) event.getPlayer().sendMessage(messagePlace);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || (args[0] = args[0].toLowerCase(Locale.ROOT)).equals("help")) {
            sender.sendMessage(clr("&6&lLiquidFreeze v" + getDescription().getVersion()));
            sender.sendMessage(clr("&a/liquidfreeze freeze&7 - Turn liquids halt on"));
            sender.sendMessage(clr("&a/liquidfreeze continue&7 - Turn liquids halt off"));
            sender.sendMessage(clr("&a/liquidfreeze reload&7 - Reload plugin's config file"));
            if (!toRestore.isEmpty()) sender.sendMessage(clr("&eCurrently &6&l" + toRestore.size() + " blocks&e are frozen"));
        } else if (args[0].equals("freeze")) {
            if (turn(true)) {
                forced = true;
                sender.sendMessage(clr("&aLiquids' flow was halted."));
            } else {
                sender.sendMessage(clr("&cLiquids' flow is already halted."));
            }
        } else if (args[0].equals("continue")) {
            if (turn(false)) {
                sender.sendMessage(clr("&aLiquids' flow was re-enabled."));
            } else {
                sender.sendMessage(clr("&cLiquids' flow is already enabled."));
            }
        } else if (args[0].equals("reload")) {
            reload();
            sender.sendMessage(clr("&aPlugin was successfully reloaded."));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            if (args[0].isEmpty()) {
                return Arrays.asList("freeze", "continue", "reload");
            } else if ("continue".startsWith(args[0] = args[0].toLowerCase())) {
                return Collections.singletonList("continue");
            } else if ("freeze".startsWith(args[0])) {
                return Collections.singletonList("freeze");
            } else if ("reload".startsWith(args[0])) {
                return Collections.singletonList("reload");
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("deprecation")
    private boolean turn(boolean freeze) {
        if (freeze == frozen) return false;
        if (freeze) {
            if (!broadcastOn.isEmpty()) Bukkit.broadcastMessage(broadcastOn);
            Bukkit.getPluginManager().registerEvents(this, this);
        } else {
            if (!broadcastOff.isEmpty()) Bukkit.broadcastMessage(broadcastOff);
            HandlerList.unregisterAll((Listener) this);
            forced = false;
        }
        frozen = freeze;
        return true;
    }

    private static String clr(String str) {
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    @SuppressWarnings("unused")
    private enum Timing {
        ONE, FIVE, FIFTEEN
    }
}
