package com.KDI.souls.gui;

import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.database.DatabaseManager;
import com.KDI.souls.message.MessageService;
import com.KDI.souls.service.FragmentService;
import com.KDI.souls.service.SoulService;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Locale;

public final class SoulStatsGui {
    private static final String TITLE = "&5&lSoulbound Profile";

    private final JavaPlugin plugin;
    private final SoulService souls;
    private final FragmentService fragments;
    private final SoulsConfig config;
    private final DatabaseManager database;

    public SoulStatsGui(JavaPlugin plugin, SoulService souls, FragmentService fragments, SoulsConfig config,
                        DatabaseManager database) {
        this.plugin = plugin;
        this.souls = souls;
        this.fragments = fragments;
        this.config = config;
        this.database = database;
    }

    public void open(Player player) {
        database.loadCombatStats(player.getUniqueId()).exceptionally(error -> {
            plugin.getLogger().warning("Could not load Soul combat stats for " + player.getUniqueId()
                    + ": " + error.getMessage());
            return null;
        }).thenCombine(souls.loadBalance(player.getUniqueId()), (stats, balance) -> stats)
                .thenAccept(stats -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                if (!souls.isBalanceReady(player.getUniqueId())) {
                    player.sendMessage(MessageService.color("&cYour Soul balance could not be loaded because the database is unavailable."));
                    return;
                }
                player.openInventory(createInventory(player, stats));
            }
        })).exceptionally(error -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(MessageService.color("&cYour Soul profile could not be loaded right now."));
                }
            });
            return null;
        });
    }

    private Inventory createInventory(Player player, DatabaseManager.CombatStats stats) {
        SoulStatsHolder holder = new SoulStatsHolder();
        Inventory inventory = Bukkit.createInventory(holder, 45, MessageService.color(TITLE));
        holder.setInventory(inventory);

        ItemStack filler = item("BLACK_STAINED_GLASS_PANE", "&r");
        ItemStack border = item("PURPLE_STAINED_GLASS_PANE", "&r");
        if (filler != null) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        if (border != null) {
            for (int slot = 0; slot < 9; slot++) {
                inventory.setItem(slot, border);
                inventory.setItem(slot + 36, border);
            }
            for (int row = 1; row < 4; row++) {
                inventory.setItem(row * 9, border);
                inventory.setItem(row * 9 + 8, border);
            }
        }

        long balance = souls.getBalance(player.getUniqueId());
        long max = Math.max(0L, config.maxBalance());
        long step = Math.max(1L, config.boostSoulsPerStep());
        long tier = balance / step;
        long maxTier = max / step;
        long fragmentsHeld = config.fragmentsEnabled() ? fragments.count(player) : 0;
        long tierStart = tier * step;
        boolean maxTierReached = balance >= max || tier >= maxTier;
        long nextThreshold = balance >= max || tierStart > Long.MAX_VALUE - step
                ? max : Math.min(max, tierStart + step);
        double tierProgress = maxTierReached ? 1.0 : (balance - tierStart) / (double) Math.max(1L, nextThreshold - tierStart);
        int progressPercent = (int) Math.round(tierProgress * 100);
        String boost = String.format(Locale.US, "%.2f", tier * config.boostAmplifierPerStep());

        inventory.setItem(11, item("NETHER_STAR", "&b&lSoul Reserve",
                "&7Your current balance.",
                "&f" + balance + " &8/ &f" + max + " Souls",
                "&7Progress to cap: &f" + (max == 0 ? 100 : Math.min(100, balance * 100.0 / max)) + "%"));
        inventory.setItem(13, item("BEACON", "&d&lSoul Tier",
                "&7Current tier: &f" + tier + " &8/ &f" + maxTier,
                "&7Current boost: &f" + boost + "×",
                "&7Progress: &d" + progressBar(tierProgress) + " &f" + progressPercent + "%",
                maxTierReached ? "&aMaximum tier reached." : "&7Next tier: &f" + nextThreshold + " Souls"));
        inventory.setItem(15, item("PRISMARINE_SHARD", "&3&lSoul Fragments",
                config.fragmentsEnabled() ? "&7Carried: &f" + fragmentsHeld : "&8Fragment conversion is disabled."));
        inventory.setItem(29, item("DIAMOND_SWORD", "&c&lCombat Record",
                "&7Kills: " + (stats == null ? "&cUnavailable" : "&f" + stats.kills()),
                "&7Deaths: " + (stats == null ? "&cUnavailable" : "&f" + stats.deaths()),
                "&7Kill/death ratio: " + (stats == null ? "&cUnavailable" : "&f"
                        + String.format(Locale.US, "%.2f", stats.ratio()))));
        inventory.setItem(31, item("BOOK", "&e&lSoul Guide",
                "&7Use &f/guide &7for a quick tutorial.",
                "&7Earn Souls, reach tiers, and grow stronger."));
        inventory.setItem(40, item("BARRIER", "&c&lClose",
                "&7Click to close this menu."));
        return inventory;
    }

    private String progressBar(double progress) {
        int filled = Math.max(0, Math.min(10, (int) Math.round(progress * 10)));
        return "■".repeat(filled) + "&8" + "■".repeat(10 - filled);
    }

    private ItemStack item(String materialName, String name, String... lore) {
        ItemStack item = XMaterial.matchXMaterial(materialName).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(MessageService.color(name));
        meta.setLore(Arrays.stream(lore).map(MessageService::color).toList());
        item.setItemMeta(meta);
        return item;
    }
}