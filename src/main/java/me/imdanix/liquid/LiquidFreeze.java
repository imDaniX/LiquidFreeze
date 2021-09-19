package me.imdanix.liquid;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class LiquidFreeze extends JavaPlugin implements Listener {
    private String broadcastOn;
    private String broadcastOff;
    private Timing timing;
    private boolean auto;
    private double low;
    private double recovery;
    private boolean registered;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!auto) return;
            double tps = Bukkit.getTPS()[timing.ordinal()];
            if (registered) {
                if (tps >= recovery) {
                    turn(false);
                }
            } else {
                if (tps <= low) {
                    turn(true);
                }
            }
        }, 1200, 1200);
    }

    private void reload() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        broadcastOn = clr(cfg.getString("broadcast.freeze", ""));
        broadcastOff = clr(cfg.getString("broadcast.continue", ""));
        try {
            timing = Timing.valueOf(cfg.getString("trigger.timing", "FIVE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            timing = Timing.FIVE;
        }
        auto = cfg.getBoolean("trigger.enable", true);
        low = cfg.getDouble("trigger.low", 10);
        recovery = cfg.getDouble("trigger.recovery", 16);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLiquid(BlockFromToEvent event) {
        if (event.getBlock().isLiquid()) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || (args[0] = args[0].toLowerCase(Locale.ROOT)).equals("help")) {
            sender.sendMessage(clr("&6&lLiquidFreeze v" + getDescription().getVersion()));
            sender.sendMessage(clr("&a/liquidfreeze freeze&7 - Turn liquids halt on"));
            sender.sendMessage(clr("&a/liquidfreeze continue&7 - Turn liquids halt off; will not restore the flow of existing liquids"));
            sender.sendMessage(clr("&a/liquidfreeze reload&7 - Reload plugin's config file"));
        } else if (args[0].equals("freeze")) {
            if (turn(true)) {
                if (!broadcastOn.isEmpty()) Bukkit.broadcastMessage(broadcastOn);
                sender.sendMessage(clr("&aLiquids' flow was halted."));
            } else {
                sender.sendMessage(clr("&cLiquids' flow is already halted."));
            }
        } else if (args[0].equals("continue")) {
            if (turn(false)) {
                if (!broadcastOff.isEmpty()) Bukkit.broadcastMessage(broadcastOff);
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
                return Arrays.asList("continue", "freeze");
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

    private boolean turn(boolean freeze) {
        if (freeze == registered) return false;
        if (freeze) {
            Bukkit.getPluginManager().registerEvents(this, this);
        } else {
            HandlerList.unregisterAll((Listener) this);
        }
        registered = freeze;
        return true;
    }

    private static String clr(String str) {
        return ChatColor.translateAlternateColorCodes('&', str);
    }

    private enum Timing {
        ONE, FIVE, FIFTEEN
    }
}
