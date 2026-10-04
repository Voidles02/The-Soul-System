package com.KDI.souls.command;

import com.KDI.souls.SoulsPlugin;
import com.KDI.souls.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class SoulsPerformanceCommand implements CommandExecutor {
    private final SoulsPlugin plugin;
    private final MessageService messages;

    public SoulsPerformanceCommand(SoulsPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("souls.performance")) {
            messages.send(sender, "no-permission");
            return true;
        }

        double[] tps = Bukkit.getServer().getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576;
        long maxMemory = runtime.maxMemory() / 1_048_576;
        long memoryPercent = maxMemory == 0 ? 0 : Math.round(usedMemory * 100.0 / maxMemory);
        long cachedBalances = plugin.getSoulService().getCachedBalanceCount();
        long pendingBalanceLoads = plugin.getSoulService().getPendingBalanceLoadCount();
        long pluginTasks = Bukkit.getScheduler().getPendingTasks().stream()
                .filter(task -> task.getOwner() == plugin)
                .count();
        long pingTotal = 0;
        int highestPing = 0;
        int onlinePlayers = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int ping = Math.max(0, player.getPing());
            pingTotal += ping;
            highestPing = Math.max(highestPing, ping);
            onlinePlayers++;
        }
        String averagePing = onlinePlayers == 0 ? "n/a" : String.valueOf(Math.round((double) pingTotal / onlinePlayers));
        String peakPing = onlinePlayers == 0 ? "n/a" : String.valueOf(highestPing);

        messages.send(sender, "performance-header");
        messages.send(sender, "performance-plugin", java.util.Map.of("version", plugin.getDescription().getVersion()));
        messages.send(sender, "performance-cache", java.util.Map.of(
                "cached", cachedBalances, "loading", pendingBalanceLoads));
        messages.send(sender, "performance-tasks", java.util.Map.of("tasks", pluginTasks));
        messages.send(sender, "performance-database", java.util.Map.of(
                "status", plugin.getDatabaseManager().isAvailable() ? "available" : "unavailable"));
        messages.send(sender, "performance-tps", java.util.Map.of(
                "1m", formatTps(tps[0]), "5m", formatTps(tps[1]), "15m", formatTps(tps[2])));
        messages.send(sender, "performance-memory", java.util.Map.of(
                "used", usedMemory, "max", maxMemory, "percent", memoryPercent));
        messages.send(sender, "performance-ping", java.util.Map.of(
                "average", averagePing,
                "max", peakPing,
                "players", onlinePlayers));
        if (sender instanceof Player player) {
            messages.send(sender, "performance-your-ping", java.util.Map.of("ping", Math.max(0, player.getPing())));
        }
        messages.send(sender, "performance-memory-note");
        return true;
    }

    private String formatTps(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}