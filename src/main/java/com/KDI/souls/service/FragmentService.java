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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FragmentService {
    private final JavaPlugin plugin;
    private final SoulService souls;
    private final SoulsConfig config;
    private final NamespacedKey key;
    private final XParticle claimParticle = XParticle.of("SOUL").orElse(null);
    private final XParticle claimAccentParticle = XParticle.of("END_ROD").orElse(null);
    private final Set<UUID> playersWithClaimEffect = new HashSet<>();
    private final Set<UUID> playersWithActiveClaim = ConcurrentHashMap.newKeySet();

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
        UUID playerId = player.getUniqueId();
        if (!playersWithActiveClaim.add(playerId)) {
            return SoulService.OperationResult.failure();
        }
        try {
            long rate = config.fragmentConversionRate();
            long possible = Math.min(count(player) / rate, souls.getRemainingCapacity(playerId));
            if (possible <= 0) {
                return SoulService.OperationResult.failure();
            }
            long plannedFragments = possible * rate;
            List<ItemStack> reserved = reserve(player, plannedFragments);
            if (reserved == null) {
                return SoulService.OperationResult.failure();
            }
            SoulService.OperationResult result = souls.addSouls(playerId, possible, "fragment-conversion");
            if (!result.successful()) {
                restore(player, reserved, plannedFragments);
                return result;
            }
            long usedFragments = Math.min(possible, result.amount()) * rate;
            restore(player, reserved, plannedFragments - usedFragments);
            playClaimEffect(player);
            return result;
        } finally {
            playersWithActiveClaim.remove(playerId);
        }
    }

    public ClaimResult claimAll(Player player) {
        UUID playerId = player.getUniqueId();
        long available = count(player);
        if (!config.fragmentsEnabled() || available <= 0 || !playersWithActiveClaim.add(playerId)) {
            return new ClaimResult(false, 0, 0, available);
        }
        try {
            long capacity = souls.getRemainingCapacity(playerId);
            if (capacity <= 0) {
                return new ClaimResult(false, 0, 0, available);
            }
            byte[] rewards = new byte[(int) Math.min(available, capacity)];
            int rewardCount = 0;
            long requested = 0;
            while (rewardCount < rewards.length && requested < capacity) {
                int reward = ThreadLocalRandom.current().nextInt(1, 6);
                int accepted = (int) Math.min(reward, capacity - requested);
                rewards[rewardCount++] = (byte) accepted;
                requested += accepted;
            }
            long plannedFragments = rewardCount;
            List<ItemStack> reserved = reserve(player, plannedFragments);
            if (reserved == null) {
                return new ClaimResult(false, 0, 0, count(player));
            }
            SoulService.OperationResult result = souls.addSouls(playerId, requested, "fragment-bulk-use");
            if (!result.successful()) {
                restore(player, reserved, plannedFragments);
                return new ClaimResult(false, 0, 0, count(player));
            }
            long unspentSouls = result.amount();
            long consumed = 0;
            for (int index = 0; index < rewardCount && unspentSouls > 0; index++) {
                unspentSouls -= Math.min(unspentSouls, rewards[index]);
                consumed++;
            }
            restore(player, reserved, plannedFragments - consumed);
            playClaimEffect(player);
            return new ClaimResult(true, result.amount(), consumed, count(player));
        } finally {
            playersWithActiveClaim.remove(playerId);
        }
    }

    public record ClaimResult(boolean successful, long souls, long fragmentsUsed, long fragmentsRemaining) {
    }

    public void playClaimEffect(Player player) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || !playersWithClaimEffect.add(playerId)) {
            return;
        }
        new BukkitRunnable() {
            private int frame;

            @Override
            public void run() {
                if (!player.isOnline() || frame >= 6) {
                    cancel();
                    return;
                }
                if (claimParticle != null) {
                    Location center = player.getLocation();
                    double centerX = center.getX();
                    double centerY = center.getY();
                    double centerZ = center.getZ();
                    double radius = 0.45 + frame * 0.14;
                    double rotation = frame * 0.42;
                    for (int point = 0; point < 16; point++) {
                        double angle = rotation + Math.PI * 2 * point / 16;
                        double x = centerX + Math.cos(angle) * radius;
                        double y = centerY + 0.85 + Math.sin(angle * 2 - rotation) * 0.12;
                        double z = centerZ + Math.sin(angle) * radius;
                        player.getWorld().spawnParticle(claimParticle.get(), x, y, z, 1, 0, 0, 0, 0);
                        if (claimAccentParticle != null && point % 4 == 0) {
                            player.getWorld().spawnParticle(claimAccentParticle.get(), x, y + 0.22, z,
                                    1, 0, 0, 0, 0);
                        }
                    }
                    for (int point = 0; point < 3; point++) {
                        player.getWorld().spawnParticle(claimParticle.get(), centerX, centerY + 0.2 + point * 0.3,
                                centerZ, 1, 0, 0, 0, 0);
                    }
                }
                frame++;
                if (frame >= 6) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> playersWithClaimEffect.remove(playerId), 20L);
    }

    private List<ItemStack> reserve(Player player, long amount) {
        List<ItemStack> reserved = new ArrayList<>();
        long remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isFragment(item)) {
                continue;
            }
            int remove = (int) Math.min(remaining, item.getAmount());
            ItemStack taken = item.clone();
            taken.setAmount(remove);
            reserved.add(taken);
            if (remove >= item.getAmount()) {
                player.getInventory().setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - remove);
                player.getInventory().setItem(slot, item);
            }
            remaining -= remove;
        }
        if (remaining > 0) {
            restore(player, reserved, amount - remaining);
            return null;
        }
        return reserved;
    }

    private void restore(Player player, List<ItemStack> reserved, long amount) {
        long remaining = amount;
        for (int index = reserved.size() - 1; index >= 0 && remaining > 0; index--) {
            ItemStack stack = reserved.get(index);
            int restored = (int) Math.min(remaining, stack.getAmount());
            ItemStack item = stack.clone();
            item.setAmount(restored);
            player.getInventory().addItem(item).values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= restored;
        }
    }
}
