package com.KDI.souls.listener;

import com.cryptomorin.xseries.particles.XParticle;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.message.MessageService;
import com.KDI.souls.service.FragmentService;
import com.KDI.souls.service.SoulService;
import com.KDI.souls.structure.SoulStructureService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulShrineListener implements Listener {
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final SoulService souls;
    private final FragmentService fragments;
    private final SoulsConfig config;
    private final MessageService messages;
    private final SoulStructureService structures;
    private final NamespacedKey cooldownKey;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();

    public SoulShrineListener(org.bukkit.plugin.java.JavaPlugin plugin, SoulService souls, FragmentService fragments,
                              SoulsConfig config, MessageService messages, SoulStructureService structures) {
        this.plugin = plugin;
        this.souls = souls;
        this.fragments = fragments;
        this.config = config;
        this.messages = messages;
        this.structures = structures;
        this.cooldownKey = new NamespacedKey(plugin, "soul_shrine_cooldown");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!"shrine".equals(structures.itemType(event.getItemInHand()))) {
            return;
        }
        if (!structures.registerPlacedStructure(event.getBlockPlaced(), "shrine")) {
            retryRegistration(event.getBlockPlaced(), 1);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!structures.isStructureBlock(event.getBlock(), "shrine")) {
            return;
        }
        ItemStack item = structures.createItem("shrine");
        if (item != null) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
        }
    }

    private void retryRegistration(org.bukkit.block.Block block, int attempt) {
        if (attempt > 3) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (structures.isStructureBlock(block, "shrine")) {
                return;
            }
            if (!structures.registerPlacedStructure(block, "shrine")) {
                retryRegistration(block, attempt + 1);
            }
        }, attempt);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().name().startsWith("RIGHT_CLICK") || event.getClickedBlock() == null
                || !structures.isStructureBlock(event.getClickedBlock(), "shrine")
                || !config.featureEnabled("shrines")) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(config.shrinePermission())) {
            messages.send(player, "shrine-no-permission");
            return;
        }
        long remaining = player.getPersistentDataContainer().getOrDefault(cooldownKey, PersistentDataType.LONG, 0L)
                - System.currentTimeMillis();
        if (remaining > 0) {
            messages.send(player, "shrine-cooldown", Map.of("hours", Math.max(1, remaining / 3600000)));
            return;
        }
        if (!processing.add(player.getUniqueId())) {
            return;
        }
        souls.loadBalance(player.getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline()) {
                    return;
                }
                if (activate(player)) {
                    player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG,
                            System.currentTimeMillis() + config.shrineCooldownHours() * 3600000L);
                    feedback(player, event.getClickedBlock().getLocation());
                }
            } finally {
                processing.remove(player.getUniqueId());
            }
        })).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                processing.remove(player.getUniqueId());
                if (player.isOnline()) {
                    messages.send(player, "shrine-unavailable");
                }
            });
            return null;
        });
    }

    private boolean activate(Player player) {
        String reward = config.shrineRewardType().toLowerCase();
        if (reward.equals("random") || reward.equals("event")) {
            if (config.featureEnabled("random-events") && (reward.equals("event")
                    || ThreadLocalRandom.current().nextDouble() < config.shrineEventChance())) {
                return randomEvent(player);
            }
            reward = switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> "souls";
                case 1 -> "fragments";
                default -> "buff";
            };
        }
        return switch (reward) {
            case "souls" -> rewardSouls(player);
            case "fragments", "fragment" -> rewardFragments(player);
            case "buff" -> rewardBuff(player);
            default -> {
                messages.send(player, "shrine-unavailable");
                yield false;
            }
        };
    }

    private boolean randomEvent(Player player) {
        boolean result = switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> rewardSouls(player);
            case 1 -> rewardFragments(player);
            default -> rewardBuff(player);
        };
        if (result && config.shrineEventBroadcast()) {
            Bukkit.broadcastMessage(messages.get("shrine-broadcast").replace("{player}", player.getName()));
        }
        return result;
    }

    private boolean rewardSouls(Player player) {
        SoulService.OperationResult result = souls.addSouls(player.getUniqueId(), config.shrineSoulReward(), "soul-shrine");
        if (!result.successful()) {
            messages.send(player, "shrine-no-room");
            return false;
        }
        messages.send(player, "shrine-souls", Map.of("amount", result.amount()));
        return true;
    }

    private boolean rewardFragments(Player player) {
        if (!config.fragmentsEnabled()) {
            return rewardSouls(player);
        }
        ItemStack fragment = fragments.createItem((int) config.shrineFragmentReward());
        if (fragment == null) {
            messages.send(player, "shrine-unavailable");
            return false;
        }
        player.getInventory().addItem(fragment).values().forEach(item ->
                player.getWorld().dropItemNaturally(player.getLocation(), item));
        messages.send(player, "shrine-fragments", Map.of("amount", config.shrineFragmentReward()));
        return true;
    }

    private boolean rewardBuff(Player player) {
        boolean applied = XPotion.matchXPotion(config.shrineBuffType())
                .map(potion -> player.addPotionEffect(potion.buildPotionEffect(
                        config.shrineBuffDurationSeconds() * 20, config.shrineBuffAmplifier())))
                .orElse(false);
        if (!applied) {
            messages.send(player, "shrine-unavailable");
            return false;
        }
        messages.send(player, "shrine-buff", Map.of("effect", config.shrineBuffType(),
                "duration", config.shrineBuffDurationSeconds()));
        return true;
    }

    private void feedback(Player player, org.bukkit.Location location) {
        XSound.matchXSound(config.shrineSound()).ifPresent(sound -> sound.play(player));
        XParticle.of(config.shrineParticle()).ifPresent(particle ->
                player.getWorld().spawnParticle(particle.get(), location.clone().add(0.5, 1, 0.5), 25, 0.45, 0.45, 0.45, 0.02));
    }
}