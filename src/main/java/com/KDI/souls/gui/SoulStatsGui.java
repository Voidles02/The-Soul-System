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

public final class SoulStatsGui {
    private static final String TITLE = "&8Soul Profile";

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
        Inventory inventory = Bukkit.createInventory(holder, 27, MessageService.color(TITLE));
        holder.setInventory(inventory);

        ItemStack filler = item("BLACK_STAINED_GLASS_PANE", "&r");
        if (filler != null) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }

        long balance = souls.getBalance(player.getUniqueId());
        long max = config.maxBalance();
        long step = config.boostSoulsPerStep();
        long tier = balance / step;
        long maxTier = max / Math.max(1, step);
        long fragmentsHeld = config.fragmentsEnabled() ? fragments.count(player) : 0;
        long nextThreshold = Math.min(max, (tier + 1) * step);

        inventory.setItem(10, item("NETHER_STAR", "&b&lSoul Balance",
                "&7Your current progression.",
                "&f" + balance + " &8/ &f" + max + " Souls"));
        inventory.setItem(12, item("BEACON", "&d&lSoul Tier",
                "&7Boost tier: &f" + tier,
                "&7Maximum tier: &f" + maxTier,
                "&7Current amplifier: &f" + (tier <= 0 ? 0 : Math.max(0,
                        (int) Math.floor(tier * config.boostAmplifierPerStep()))),
                tier >= max / Math.max(1, step) ? "&aMaximum tier reached." : "&7Next tier: &f" + nextThreshold + " Souls"));
        inventory.setItem(14, item("PRISMARINE_SHARD", "&3&lSoul Fragments",
                config.fragmentsEnabled() ? "&7Carried: &f" + fragmentsHeld : "&8Fragment conversion is disabled."));
        inventory.setItem(16, item("BOOK", "&e&lSoul Guide",
                "&7Use &f/guide &7for a quick tutorial.",
                "&7Earn Souls, reach tiers, and grow stronger.",
                "&7Kills: " + (stats == null ? "&cUnavailable" : "&f" + stats.kills()),
                "&7Deaths: " + (stats == null ? "&cUnavailable" : "&f" + stats.deaths()),
                "&7K/D: " + (stats == null ? "&cUnavailable" : "&f"
                        + String.format(java.util.Locale.US, "%.2f", stats.ratio()))));
        inventory.setItem(22, item("BARRIER", "&c&lClose",
                "&7Click to close this menu."));
        return inventory;
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