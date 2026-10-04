package com.KDI.souls.service;

import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.message.MessageService;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class FragmentService {
    private final JavaPlugin plugin;
    private final SoulService souls;
    private final SoulsConfig config;
    private final NamespacedKey key;
    private final XParticle claimParticle = XParticle.of("SOUL").orElse(null);
    private final Set<UUID> playersWithClaimEffect = new HashSet<>();

    public FragmentService(JavaPlugin plugin, SoulService souls, SoulsConfig config) {
        this.plugin = plugin;
        this.souls = souls;
        this.config = config;
        this.key = new NamespacedKey(plugin, "soul_fragment");
        if (!"PRISMARINE_SHARD".equalsIgnoreCase(config.fragmentMaterial())) {
            plugin.getLogger().warning("fragments.item-material must be PRISMARINE_SHARD for the Soul Fragment resource-pack texture.");
        }
    }

    public ItemStack createItem(int amount) {
        ItemStack item = XMaterial.matchXMaterial(config.fragmentMaterial())
                .map(XMaterial::parseItem)
                .orElse(null);
        if (item == null) {
            return null;
        }
        item.setAmount(Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Soul Shard");
        if ("PRISMARINE_SHARD".equals(XMaterial.matchXMaterial(item.getType()).name())) {
            meta.setCustomModelData(19000);
        }
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isFragment(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public long count(Player player) {
        long total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFragment(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public void drop(Location location, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(64, remaining);
            ItemStack item = createItem(stackAmount);
            if (item == null) {
                return;
            }
            location.getWorld().dropItemNaturally(location, item);
            remaining -= stackAmount;
        }
    }

    public SoulService.OperationResult convert(Player player) {
        if (!config.fragmentsEnabled()) {
            return SoulService.OperationResult.failure();
        }
        long total = count(player);
        long rate = config.fragmentConversionRate();
        long possible = total / rate;
        possible = Math.min(possible, souls.getRemainingCapacity(player.getUniqueId()));
        if (possible <= 0) {
            return SoulService.OperationResult.failure();
        }
        SoulService.OperationResult result = souls.addSouls(player.getUniqueId(), possible, "fragment-conversion");
        if (!result.successful()) {
            return result;
        }
        remove(player, result.amount() * rate);
        playClaimEffect(player);
        return result;
    }

    public void playClaimEffect(Player player) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || !playersWithClaimEffect.add(playerId)) {
            return;
        }
        if (claimParticle != null) {
            Location center = player.getLocation();
            center.add(0, 0.85, 0);
            for (int i = 0; i < 16; i++) {
                double angle = 2 * Math.PI * i / 16;
                double radius = 0.7;
                Location point = center.clone().add(Math.cos(angle) * radius, Math.sin(angle * 2) * 0.12,
                        Math.sin(angle) * radius);
                player.getWorld().spawnParticle(claimParticle.get(), point, 1, 0, 0, 0, 0);
            }
            for (int i = 0; i < 4; i++) {
                player.getWorld().spawnParticle(claimParticle.get(), center.clone().add(0, 0.12 * i, 0),
                        1, 0, 0, 0, 0);
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> playersWithClaimEffect.remove(playerId), 20L);
    }

    private void remove(Player player, long amount) {
        long remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isFragment(item)) {
                continue;
            }
            int remove = (int) Math.min(remaining, item.getAmount());
            if (remove >= item.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - remove);
                player.getInventory().setItem(slot, item);
            }
            remaining -= remove;
        }
    }
}
