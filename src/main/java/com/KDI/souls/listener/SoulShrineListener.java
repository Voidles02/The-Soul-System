package com.KDI.souls.listener;

import com.cryptomorin.xseries.particles.XParticle;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.XMaterial;
import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.gui.SoulShrineHolder;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulShrineListener implements Listener {
    private static final int ACTIVATE_SLOT = 13;
    private static final int CLOSE_SLOT = 22;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final SoulService souls;
    private final FragmentService fragments;
    private final SoulsConfig config;
    private final MessageService messages;
    private final SoulStructureService structures;
    private final NamespacedKey cooldownKey;
    private final NamespacedKey activeEffectsKey;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeShrineFeedback = ConcurrentHashMap.newKeySet();
    private final Set<ShrinePosition> activeShrineLocations = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<PotionEffectType>> activeShrineEffects = new HashMap<>();
    private final XParticle shrineAccentParticle = XParticle.of("SOUL").orElse(null);
    private final XParticle shrineBuffParticle = XParticle.of("SOUL").orElse(null);
    private final XParticle shrineBuffAccentParticle = XParticle.of("END_ROD").orElse(null);
    private long particlePulse;

    private record ShrinePosition(UUID worldId, int x, int y, int z) {
    }

    public SoulShrineListener(org.bukkit.plugin.java.JavaPlugin plugin, SoulService souls, FragmentService fragments,
                              SoulsConfig config, MessageService messages, SoulStructureService structures) {
        this.plugin = plugin;
        this.souls = souls;
        this.fragments = fragments;
        this.config = config;
        this.messages = messages;
        this.structures = structures;
        this.cooldownKey = new NamespacedKey(plugin, "soul_shrine_cooldown");
        this.activeEffectsKey = new NamespacedKey(plugin, "soul_shrine_active_effects");
        Bukkit.getOnlinePlayers().forEach(this::restoreShrineEffects);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickShrineParticles, 5L, 5L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                restoreShrineEffects(player);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeShrineEffects.remove(event.getPlayer().getUniqueId());
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
        event.setDropItems(false);
        event.setExpToDrop(0);
        ItemStack item = structures.createItem("shrine");
        if (item != null) {
            org.bukkit.block.Block block = event.getBlock();
            org.bukkit.Location location = block.getLocation();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!structures.isStructureBlock(block, "shrine")) {
                    block.getWorld().dropItemNaturally(location, item);
                }
            });
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
        openShrine(player, event.getClickedBlock().getLocation());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SoulShrineHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() == ACTIVATE_SLOT) {
            activateFromShrine(player, holder.getShrine());
        } else if (event.getRawSlot() == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SoulShrineHolder) {
            event.setCancelled(true);
        }
    }

    private void openShrine(Player player, org.bukkit.Location location) {
        SoulShrineHolder holder = new SoulShrineHolder(location);
        Inventory inventory = Bukkit.createInventory(holder, 27, MessageService.color("&8&lSOUL SHRINE"));
        holder.setInventory(inventory);
        ItemStack filler = menuItem("BLACK_STAINED_GLASS_PANE", "&8 ");
        if (filler != null) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        inventory.setItem(4, menuItem("SOUL_LANTERN", "&b&lTHE SOUL WELLSPRING",
                "&7A place of renewal, not just battle.",
                "&7Its gifts can aid travel, gathering,",
                "&7recovery, and Soul progression."));
        inventory.setItem(10, rewardOverview());
        inventory.setItem(ACTIVATE_SLOT, activationButton(player));
        inventory.setItem(16, menuItem("BEACON", "&d&lBLESSING DETAILS",
                "&7Buff rewards grant configured potion effects.",
                "&7They benefit exploration and resource gathering",
                "&7as well as combat.",
                "&8Current buff: &f" + config.shrineBuffType() + " &8for &f"
                        + config.shrineBuffDurationSeconds() + "s"));
        inventory.setItem(CLOSE_SLOT, menuItem("BARRIER", "&c&lClose",
                "&7You can return to this shrine anytime."));
        player.openInventory(inventory);
    }

    private void activateFromShrine(Player player, org.bukkit.Location location) {
        long remaining = player.getPersistentDataContainer().getOrDefault(cooldownKey, PersistentDataType.LONG, 0L)
                - System.currentTimeMillis();
        if (remaining > 0) {
            messages.send(player, "shrine-cooldown", Map.of("hours", Math.max(1, remaining / 3600000)));
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SoulShrineHolder) {
                player.getOpenInventory().getTopInventory().setItem(ACTIVATE_SLOT, activationButton(player));
            }
            return;
        }
        if (!structures.isStructureBlock(location.getBlock(), "shrine")) {
            messages.send(player, "shrine-unavailable");
            player.closeInventory();
            return;
        }
        if (!processing.add(player.getUniqueId())) {
            return;
        }
        souls.loadBalance(player.getUniqueId()).whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (error != null) {
                    if (player.isOnline()) {
                        messages.send(player, "shrine-unavailable");
                    }
                    return;
                }
                if (!player.isOnline()) {
                    return;
                }
                if (!structures.isStructureBlock(location.getBlock(), "shrine")) {
                    messages.send(player, "shrine-unavailable");
                    return;
                }
                if (activate(player)) {
                    player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG,
                            System.currentTimeMillis() + config.shrineCooldownHours() * 3600000L);
                    feedback(player, location);
                    player.closeInventory();
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

    private ItemStack activationButton(Player player) {
        long remaining = player.getPersistentDataContainer().getOrDefault(cooldownKey, PersistentDataType.LONG, 0L)
                - System.currentTimeMillis();
        if (remaining > 0) {
            long minutes = Math.max(1, (remaining + 59999) / 60000);
            return menuItem("GRAY_DYE", "&7&lSHRINE RESTING",
                    "&7Ready in about &f" + (minutes / 60) + "h " + (minutes % 60) + "m&7.",
                    "&8The cooldown is saved to your player data.");
        }
        return menuItem("NETHER_STAR", "&a&lACCEPT THE BLESSING",
                "&7Click to receive this shrine's configured gift.",
                "&7Your personal cooldown begins after success.",
                "&eOne activation per cooldown.");
    }

    private ItemStack rewardOverview() {
        String reward = config.shrineRewardType().toLowerCase(java.util.Locale.ROOT);
        if (reward.equals("souls")) {
            return menuItem("AMETHYST_SHARD", "&b&lSOUL OFFERING",
                    "&7Configured reward: &f" + config.shrineSoulReward() + " Souls",
                    "&7Strengthen your progression and balance.");
        }
        if (reward.equals("fragments") || reward.equals("fragment")) {
            return menuItem("PRISMARINE_CRYSTALS", "&3&lSHARD OFFERING",
                    "&7Configured reward: &f" + config.shrineFragmentReward() + " Soul Shards",
                    "&7Claim them later when you need Souls.");
        }
        if (reward.equals("buff")) {
            return menuItem("GLOWSTONE_DUST", "&d&lWAYFARER'S BLESSING",
                    "&7Effects: &f" + configuredBuffNames(),
                    "&7Duration: &f" + config.shrineBuffDurationSeconds() + " seconds",
                    "&7Useful for travel, gathering, and recovery.");
        }
        return menuItem("CHEST", "&5&lRANDOM SHRINE GIFT",
                "&7Possible gifts include Souls, Soul Shards,",
                "&7and configured potion blessings.",
                "&7Events may also be triggered.",
                "&8Rewards: &f" + config.shrineSoulReward() + " Souls &8/ &f"
                        + config.shrineFragmentReward() + " Shards");
    }

    private String configuredBuffNames() {
        List<String> effects = new ArrayList<>();
        effects.add(config.shrineBuffType());
        effects.addAll(config.shrineAdditionalBuffTypes());
        return String.join(", ", effects);
    }

    private ItemStack menuItem(String material, String name, String... lore) {
        ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageService.color(name));
            meta.setLore(java.util.Arrays.stream(lore).map(MessageService::color).toList());
            item.setItemMeta(meta);
        }
        return item;
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
        long remaining = config.shrineFragmentReward();
        long reward = remaining;
        while (remaining > 0) {
            ItemStack fragment = fragments.createItem((int) Math.min(64L, remaining));
            if (fragment == null) {
                messages.send(player, "shrine-unavailable");
                return false;
            }
            player.getInventory().addItem(fragment).values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= fragment.getAmount();
        }
        messages.send(player, "shrine-fragments", Map.of("amount", reward));
        return true;
    }

    private boolean rewardBuff(Player player) {
        List<String> rewardTypes = new ArrayList<>();
        rewardTypes.add(config.shrineBuffType());
        rewardTypes.addAll(config.shrineAdditionalBuffTypes());
        List<String> validTypes = new ArrayList<>();
        Set<PotionEffectType> appliedTypes = new HashSet<>();
        int durationTicks = config.shrineBuffDurationSeconds() * 20;
        for (int index = 0; index < rewardTypes.size(); index++) {
            String type = rewardTypes.get(index);
            if (type == null || type.isBlank()) {
                continue;
            }
            int amplifier = index == 0 ? config.shrineBuffAmplifier() : config.shrineAdditionalBuffAmplifier();
            XPotion.matchXPotion(type.trim()).ifPresent(potion -> {
                PotionEffect effect = potion.buildPotionEffect(durationTicks, amplifier);
                if (player.addPotionEffect(effect)) {
                    appliedTypes.add(effect.getType());
                }
                validTypes.add(type.trim());
            });
        }
        if (validTypes.isEmpty()) {
            messages.send(player, "shrine-unavailable");
            return false;
        }
        if (!appliedTypes.isEmpty()) {
            persistShrineEffects(player, appliedTypes);
        }
        messages.send(player, "shrine-buff", Map.of("effect", String.join(", ", validTypes),
                "duration", config.shrineBuffDurationSeconds()));
        return true;
    }

    private void persistShrineEffects(Player player, Set<PotionEffectType> appliedTypes) {
        UUID playerId = player.getUniqueId();
        Set<PotionEffectType> tracked = activeShrineEffects.computeIfAbsent(playerId,
                ignored -> loadShrineEffectTypes(player));
        tracked.addAll(appliedTypes);
        saveShrineEffectTypes(player, tracked);
    }

    private void restoreShrineEffects(Player player) {
        Set<PotionEffectType> tracked = loadShrineEffectTypes(player);
        tracked.removeIf(type -> player.getPotionEffect(type) == null);
        if (tracked.isEmpty()) {
            activeShrineEffects.remove(player.getUniqueId());
            player.getPersistentDataContainer().remove(activeEffectsKey);
            return;
        }
        activeShrineEffects.put(player.getUniqueId(), tracked);
        saveShrineEffectTypes(player, tracked);
    }

    private Set<PotionEffectType> loadShrineEffectTypes(Player player) {
        String stored = player.getPersistentDataContainer().get(activeEffectsKey, PersistentDataType.STRING);
        Set<PotionEffectType> effects = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) {
            return effects;
        }
        for (String name : stored.split(",")) {
            XPotion.matchXPotion(name).map(potion -> potion.buildPotionEffect(1, 0).getType())
                    .ifPresent(effects::add);
        }
        return effects;
    }

    private void saveShrineEffectTypes(Player player, Set<PotionEffectType> effects) {
        if (effects.isEmpty()) {
            player.getPersistentDataContainer().remove(activeEffectsKey);
            return;
        }
        player.getPersistentDataContainer().set(activeEffectsKey, PersistentDataType.STRING,
                effects.stream().map(effect -> effect.getKey().getKey()).distinct()
                        .collect(java.util.stream.Collectors.joining(",")));
    }

    private void tickShrineParticles() {
        if (activeShrineEffects.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Set<PotionEffectType>>> players = activeShrineEffects.entrySet().iterator();
        while (players.hasNext()) {
            Map.Entry<UUID, Set<PotionEffectType>> entry = players.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                players.remove();
                continue;
            }
            int longestDuration = 0;
            boolean removedEffect = false;
            Iterator<PotionEffectType> effects = entry.getValue().iterator();
            while (effects.hasNext()) {
                PotionEffect effect = player.getPotionEffect(effects.next());
                if (effect == null) {
                    effects.remove();
                    removedEffect = true;
                } else {
                    longestDuration = Math.max(longestDuration, effect.getDuration());
                }
            }
            if (entry.getValue().isEmpty()) {
                player.getPersistentDataContainer().remove(activeEffectsKey);
                players.remove();
                continue;
            }
            if (removedEffect) {
                saveShrineEffectTypes(player, entry.getValue());
            }
            if (shrineBuffParticle == null) {
                continue;
            }
            if (longestDuration <= 100 && ThreadLocalRandom.current().nextInt(100) >= longestDuration) {
                continue;
            }
            Location location = player.getLocation();
            double x = location.getX();
            double y = location.getY();
            double z = location.getZ();
            player.getWorld().spawnParticle(shrineBuffParticle.get(), x, y + 0.15, z,
                    1, 0.16, 0.22, 0.16, 0.005);
            if (shrineBuffAccentParticle != null && ++particlePulse % 4 == 0) {
                player.getWorld().spawnParticle(shrineBuffAccentParticle.get(), x, y + 1.35, z,
                        1, 0.12, 0.16, 0.12, 0.002);
            }
        }
    }

    private void feedback(Player player, org.bukkit.Location location) {
        UUID playerId = player.getUniqueId();
        ShrinePosition position = new ShrinePosition(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        XSound.matchXSound(config.shrineSound()).ifPresent(sound -> sound.play(player));
        XParticle particle = XParticle.of(config.shrineParticle()).orElse(shrineAccentParticle);
        if (particle == null) {
            return;
        }
        double centerX = location.getX() + 0.5;
        double centerY = location.getY() + 1.0;
        double centerZ = location.getZ() + 0.5;
        org.bukkit.World world = location.getWorld();
        world.spawnParticle(particle.get(), centerX, centerY + 0.12, centerZ,
                8, 0.18, 0.2, 0.18, 0.01);
        if (!activeShrineFeedback.add(playerId)) {
            return;
        }
        if (!activeShrineLocations.add(position)) {
            activeShrineFeedback.remove(playerId);
            return;
        }
        new org.bukkit.scheduler.BukkitRunnable() {
            private int frame;

            @Override
            public void run() {
                if (!player.isOnline() || frame >= 16) {
                    cancel();
                    activeShrineFeedback.remove(playerId);
                    activeShrineLocations.remove(position);
                    return;
                }
                double progress = frame / 15.0;
                double radius = 0.18 + progress * 0.38;
                double rotation = frame * 0.58;
                for (int strand = 0; strand < 3; strand++) {
                    double angle = rotation + Math.PI * 2 * strand / 3;
                    double x = centerX + Math.cos(angle) * radius;
                    double y = centerY + 0.08 + progress * 1.05 + strand * 0.05;
                    double z = centerZ + Math.sin(angle) * radius;
                    world.spawnParticle(particle.get(), x, y, z, 1, 0, 0, 0, 0);
                    if (shrineAccentParticle != null && strand == 0 && frame % 4 == 0) {
                        world.spawnParticle(shrineAccentParticle.get(), x, y + 0.16, z,
                                1, 0, 0, 0, 0);
                    }
                }
                frame++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}