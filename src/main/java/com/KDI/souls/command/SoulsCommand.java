package com.KDI.souls.command;

import com.KDI.souls.SoulsPlugin;
import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.database.DatabaseManager;
import com.KDI.souls.database.LeaderboardEntry;
import com.KDI.souls.message.MessageService;
import com.KDI.souls.gui.SoulStatsGui;
import com.KDI.souls.service.FragmentService;
import com.KDI.souls.service.LocalSoulStore;
import com.KDI.souls.service.SoulService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class SoulsCommand implements CommandExecutor, TabCompleter {
    private final SoulsPlugin plugin;
    private final SoulService souls;
    private final FragmentService fragments;
    private final DatabaseManager database;
    private final SoulsConfig config;
    private final MessageService messages;
    private final SoulStatsGui statsGui;
    public SoulsCommand(SoulsPlugin plugin, SoulService souls, FragmentService fragments, DatabaseManager database,
                        SoulsConfig config, MessageService messages, SoulStatsGui statsGui) {
        this.plugin = plugin;
        this.souls = souls;
        this.fragments = fragments;
        this.database = database;
        this.config = config;
        this.messages = messages;
        this.statsGui = statsGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("souls.use") && !sender.hasPermission("souls.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                messages.sendList(sender, "help");
                return true;
            }
            statsGui.open(player);
            return true;
        }
        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "stats", "profile" -> openStats(sender);
            case "top" -> top(sender);
            case "pay" -> pay(sender, args);
            case "fragments" -> fragments(sender);
            case "convert" -> convert(sender);
            case "give" -> admin(sender, args, AdminAction.GIVE);
            case "take" -> admin(sender, args, AdminAction.TAKE);
            case "set" -> admin(sender, args, AdminAction.SET);
            case "remove" -> remove(sender, args);
            case "inspect" -> inspect(sender, args);
            case "reload" -> reload(sender);
            default -> messages.sendList(sender, "help");
        }
        return true;
    }

    private void openStats(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        statsGui.open(player);
    }

    private void top(CommandSender sender) {
        database.top(10).thenAccept(entries -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (entries.isEmpty()) {
                messages.send(sender, "top-empty");
                return;
            }
            sender.sendMessage(messages.get("top-header"));
            for (int index = 0; index < entries.size(); index++) {
                LeaderboardEntry entry = entries.get(index);
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.uuid());
                sender.sendMessage(messages.get("top-entry")
                        .replace("{position}", String.valueOf(index + 1))
                        .replace("{player}", player.getName() == null ? entry.uuid().toString() : player.getName())
                        .replace("{souls}", String.valueOf(entry.balance())));
            }
        }));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (!sender.hasPermission("souls.pay")) {
            messages.send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            messages.sendList(sender, "help");
            return;
        }
        Long amount = parseAmount(sender, args[2]);
        if (amount == null) {
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        souls.loadBalance(player.getUniqueId()).thenCombine(souls.loadBalance(target.getUniqueId()),
                (payerBalance, targetBalance) -> true).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            SoulService.OperationResult result = souls.transfer(player.getUniqueId(), target.getUniqueId(), amount);
            if (!result.successful()) {
                messages.send(sender, souls.getBalance(player.getUniqueId()) < amount ? "insufficient" : "max-reached",
                        Map.of("max", config.maxBalance()));
                return;
            }
            messages.send(player, "pay-sent", Map.of("amount", result.amount(), "player", target.getName()));
            Player online = Bukkit.getPlayer(target.getUniqueId());
            if (online != null) {
                messages.send(online, "pay-received", Map.of("amount", result.amount(), "player", player.getName()));
            }
        }));
    }

    private void fragments(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        messages.send(player, "fragments", Map.of("amount", fragments.count(player)));
    }

    private void convert(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        souls.loadBalance(player.getUniqueId()).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            long before = souls.getBalance(player.getUniqueId());
            SoulService.OperationResult result = fragments.convert(player);
            if (!result.successful()) {
                messages.send(player, "no-convert");
                return;
            }
            messages.send(player, "converted", Map.of("fragments", result.amount() * config.fragmentConversionRate(),
                    "souls", souls.getBalance(player.getUniqueId()) - before));
        }));
    }

    private void inspect(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 2) {
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        souls.loadBalance(target.getUniqueId()).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin,
                () -> {
                    LocalSoulStore.StoredData data = souls.localData(target.getUniqueId());
                    messages.send(sender, "inspect", Map.of("player", target.getName(),
                            "souls", souls.getBalance(target.getUniqueId())));
                    if (data != null) {
                        messages.send(sender, "inspect-details", Map.of(
                                "max", data.maxSouls(),
                                "percentage", String.format(java.util.Locale.ROOT, "%.2f", data.percentage()),
                                "last-saved", data.lastSaved(),
                                "reason", data.lastSaveReason()));
                    }
                }));
    }

    private void remove(CommandSender sender, String[] args) {
        if (!requireAdmin(sender) || args.length < 2) {
            return;
        }
        boolean removeAll = args.length < 3 || args[2].equalsIgnoreCase("all");
        Long requested = removeAll ? null : parseAmount(sender, args[2]);
        if (!removeAll && requested == null) {
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        souls.loadBalance(target.getUniqueId()).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            long before = souls.getBalance(target.getUniqueId());
            SoulService.OperationResult result = removeAll
                    ? souls.setSouls(target.getUniqueId(), 0)
                    : souls.loseSouls(target.getUniqueId(), requested, "admin-remove");
            if (!result.successful()) {
                messages.send(sender, "remove-failed");
                return;
            }
            messages.send(sender, "admin-removed", Map.of("player", target.getName(),
                    "amount", removeAll ? before : result.amount()));
        }));
    }

    private void admin(CommandSender sender, String[] args, AdminAction action) {
        if (!requireAdmin(sender) || args.length < 3) {
            return;
        }
        Long amount = parseAmount(sender, args[2]);
        if (amount == null) {
            return;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        souls.loadBalance(target.getUniqueId()).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            SoulService.OperationResult result = switch (action) {
                case GIVE -> souls.addSouls(target.getUniqueId(), amount, "admin-give");
                case TAKE -> souls.loseSouls(target.getUniqueId(), amount, "admin-take");
                case SET -> souls.setSouls(target.getUniqueId(), amount);
            };
            if (result.successful()) {
                messages.send(sender, "admin-success", Map.of("player", target.getName(),
                        "amount", souls.getBalance(target.getUniqueId())));
            } else {
                messages.send(sender, "max-reached", Map.of("max", config.maxBalance()));
            }
        }));
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        plugin.reloadSettings();
        messages.send(sender, "reload");
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("souls.admin")) {
            messages.send(sender, "no-permission");
            return false;
        }
        return true;
    }

    private Long parseAmount(CommandSender sender, String value) {
        try {
            long amount = Long.parseLong(value);
            if (amount <= 0) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (NumberFormatException exception) {
            messages.send(sender, "invalid-amount");
            return null;
        }
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(offline.getName())) {
                return offline;
            }
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("stats", "profile", "top", "pay", "fragments", "convert", "give", "take", "set", "remove", "inspect", "reload").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && List.of("pay", "give", "take", "set", "remove", "inspect").contains(args[0].toLowerCase())) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("remove")) {
            return List.of("all");
        }
        return List.of();
    }

    private enum AdminAction { GIVE, TAKE, SET }
}
