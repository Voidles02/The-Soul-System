package com.KDI.souls.service;

import com.KDI.souls.listener.SoulAltarListener;
import com.KDI.souls.message.MessageService;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.particles.XParticle;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SuperweaponService implements Listener {
    private final JavaPlugin plugin;
    private final SoulAltarListener altar;
    private final MessageService messages;
    private final NamespacedKey artifactKey;
    private final NamespacedKey dashCooldownKey;
    private final NamespacedKey slamCooldownKey;
    private final NamespacedKey activateCooldownKey;
    private final Map<UUID, BukkitTask> activeActions = new HashMap<>();
    private final Map<UUID, BukkitTask> cooldownHudTasks = new HashMap<>();
    private final Map<UUID, Long> fallProtection = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();

    public SuperweaponService(JavaPlugin plugin, SoulAltarListener altar, MessageService messages) {
        this.plugin = plugin;
        this.altar = altar;
        this.messages = messages;
        this.artifactKey = new NamespacedKey(plugin, "superweapon");
        this.dashCooldownKey = new NamespacedKey(plugin, "sarculum_dash_cooldown");
        this.slamCooldownKey = new NamespacedKey(plugin, "sarculum_slam_cooldown");
        this.activateCooldownKey = new NamespacedKey(plugin, "superweapon_activate_cooldown");
    }

    public void open(Player player) {
        if (!player.hasPermission("souls.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        MenuHolder holder = new MenuHolder();
        holder.getInventory().setItem(11, createBook());
        holder.getInventory().setItem(15, createSarculum());
        player.openInventory(holder.getInventory());
    }

    public void clearCooldown(Player player, String ability) {
        if (ability.equals("dash") || ability.equals("all")) {
            player.getPersistentDataContainer().remove(dashCooldownKey);
        }
        if (ability.equals("slam") || ability.equals("all")) {
            player.getPersistentDataContainer().remove(slamCooldownKey);
        }
        if (ability.equals("activate") || ability.equals("all")) {
            player.getPersistentDataContainer().remove(activateCooldownKey);
        }
        startCooldownHud(player);
    }

    public void setCooldownDuration(String ability, long durationMillis) {
        plugin.getConfig().set("superweapon.cooldowns." + ability + "-millis", durationMillis);
        plugin.saveConfig();
    }

    private long cooldownDurationMillis(String ability, long defaultMillis) {
        long configuredMillis = plugin.getConfig().getLong(
                "superweapon.cooldowns." + ability + "-millis", defaultMillis);
        if (ability.equals("activate") && configuredMillis <= 0L) {
            return defaultMillis;
        }
        return Math.max(0L, configuredMillis);
    }

    private ItemStack createBook() {
        ItemStack item = XMaterial.matchXMaterial("SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE")
                .map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageService.color("&d&lThe Book of Bōc"));
        meta.setLore(List.of(
                MessageService.color("&7Right-click to invoke the book."),
                MessageService.color("&cStrength III &7— 8 minutes"),
                MessageService.color("&dRegeneration V &7— 5 minutes"),
                MessageService.color("&8Slowness I &7— 8 minutes"),
                MessageService.color("&6Hunger I &7— 5 minutes"),
                MessageService.color("&8Non-craftable artifact")));
        meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, "boc");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSarculum() {
        ItemStack item = XMaterial.matchXMaterial("NETHERITE_HOE").map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(MessageService.color("&5&lThe Sarculum"));
        meta.setLore(List.of(
                MessageService.color("&7Sneak + left-click: freely steerable dash up to 12 blocks; 2-minute cooldown."),
                MessageService.color("&7Dash: 3 hearts in a 4-block radius along the path."),
                MessageService.color("&7Sneak (Shift) + right-click: slam; 5-minute cooldown."),
                MessageService.color("&7Slam: launch 4 blocks, then strike on landing."),
                MessageService.color("&7Within 2 blocks: 5 hearts; at 8 blocks: 2 hearts."),
                MessageService.color("&8Non-craftable artifact")));
        meta.getPersistentDataContainer().set(artifactKey, PersistentDataType.STRING, "sarculum");
        XEnchantment.matchXEnchantment("EFFICIENCY").ifPresent(enchant -> meta.addEnchant(enchant.getEnchant(), 5, true));
        XEnchantment.matchXEnchantment("UNBREAKING").ifPresent(enchant -> meta.addEnchant(enchant.getEnchant(), 3, true));
        XEnchantment.matchXEnchantment("MENDING").ifPresent(enchant -> meta.addEnchant(enchant.getEnchant(), 1, true));
        XEnchantment.matchXEnchantment("SHARPNESS").ifPresent(enchant -> meta.addEnchant(enchant.getEnchant(), 5, true));
        item.setItemMeta(meta);
        altar.applyMaxSoulEnchants(item);
        return item;
    }

    private String artifact(ItemStack item) {
        return item != null && item.hasItemMeta()
                ? item.getItemMeta().getPersistentDataContainer().get(artifactKey, PersistentDataType.STRING) : null;
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }
        if (!player.hasPermission("souls.admin")) {
            player.closeInventory();
            messages.send(player, "no-permission");
            return;
        }
        int slot = event.getRawSlot();
        String selected = artifact(event.getCurrentItem());
        if ((slot != 11 || !"boc".equals(selected)) && (slot != 15 || !"sarculum".equals(selected))) {
            return;
        }
        ItemStack item = slot == 11 ? createBook() : createSarculum();
        if (item == null) {
            return;
        }
        if (!player.getInventory().addItem(item).isEmpty()) {
            messages.send(player, "sw-inventory-full");
            return;
        }
        messages.send(player, "sw-given", Map.of("item", item.getItemMeta().getDisplayName()));
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String artifact = artifact(event.getItem());
        if (!"boc".equals(artifact) && !"sarculum".equals(artifact)) {
            return;
        }
        Player player = event.getPlayer();
        if (artifact.equals("sarculum")) {
            boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR
                    || event.getAction() == Action.LEFT_CLICK_BLOCK;
            boolean rightClick = event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.RIGHT_CLICK_BLOCK;
            if ((!leftClick && !rightClick) || (leftClick && !player.isSneaking())
                    || (event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && event.useItemInHand() == Event.Result.DENY)) {
                return;
            }
            event.setCancelled(true);
            if (activeActions.containsKey(player.getUniqueId()) || player.isInsideVehicle()) {
                return;
            }
            if (leftClick) {
                dash(player);
            } else if (player.isSneaking()) {
                slam(player);
            }
            return;
        }
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
                || (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.useItemInHand() == Event.Result.DENY)) {
            return;
        }
        event.setCancelled(true);
        long activateCooldown = cooldownDurationMillis("activate", 15 * 60 * 1000L);
        if (activateCooldown > 0 && !startCooldown(player, activateCooldownKey, activateCooldown)) {
            return;
        }
        XPotion.matchXPotion("STRENGTH").map(potion -> potion.buildPotionEffect(8 * 60 * 20, 2))
                .ifPresent(player::addPotionEffect);
        XPotion.matchXPotion("REGENERATION").map(potion -> potion.buildPotionEffect(5 * 60 * 20, 4))
                .ifPresent(player::addPotionEffect);
        XPotion.matchXPotion("SLOWNESS").map(potion -> potion.buildPotionEffect(8 * 60 * 20, 0))
                .ifPresent(player::addPotionEffect);
        XPotion.matchXPotion("HUNGER").map(potion -> potion.buildPotionEffect(5 * 60 * 20, 0))
                .ifPresent(player::addPotionEffect);
    }

    private boolean startCooldown(Player player, NamespacedKey key, long durationMillis) {
        long now = System.currentTimeMillis();
        long remaining = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L) - now;
        if (remaining > 0) {
            startCooldownHud(player);
            return false;
        }
        long expiresAt = durationMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + durationMillis;
        player.getPersistentDataContainer().set(key, PersistentDataType.LONG, expiresAt);
        startCooldownHud(player);
        return true;
    }

    private void startCooldownHud(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask currentTask = cooldownHudTasks.remove(uuid);
        if (currentTask != null) {
            currentTask.cancel();
        }
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cooldownHudTasks.remove(uuid);
                    cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                StringBuilder cooldowns = new StringBuilder();
                appendCooldown(cooldowns, "Dash", player.getPersistentDataContainer()
                        .getOrDefault(dashCooldownKey, PersistentDataType.LONG, 0L) - now);
                appendCooldown(cooldowns, "Slam", player.getPersistentDataContainer()
                        .getOrDefault(slamCooldownKey, PersistentDataType.LONG, 0L) - now);
                appendCooldown(cooldowns, "Activate", player.getPersistentDataContainer()
                        .getOrDefault(activateCooldownKey, PersistentDataType.LONG, 0L) - now);
                if (cooldowns.isEmpty()) {
                    cooldownHudTasks.remove(uuid);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(""));
                    cancel();
                    return;
                }
                String template = messages.get("sw-cooldown-hud");
                if (template.equals("sw-cooldown-hud")) {
                    template = "&5Cooldown &8» &f{cooldowns}";
                }
                String message = template.replace("{cooldowns}", cooldowns.toString());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(MessageService.color(message)));
            }
        }.runTaskTimer(plugin, 0L, 10L);
        cooldownHudTasks.put(uuid, task);
    }

    private void appendCooldown(StringBuilder cooldowns, String ability, long remainingMillis) {
        if (remainingMillis <= 0) {
            return;
        }
        long totalSeconds = (remainingMillis + 999) / 1000;
        if (!cooldowns.isEmpty()) {
            cooldowns.append(" &8| ");
        }
        cooldowns.append("&f").append(ability).append(": ");
        if (totalSeconds >= 60) {
            cooldowns.append(totalSeconds / 60).append("m ").append(totalSeconds % 60).append("s");
        } else {
            cooldowns.append(totalSeconds).append('s');
        }
    }

    private void dash(Player player) {
        if (!startCooldown(player, dashCooldownKey, cooldownDurationMillis("dash", 2 * 60 * 1000L))) {
            return;
        }
        Location start = player.getLocation().clone();
        Set<UUID> hit = new HashSet<>();
        BukkitTask task = new BukkitRunnable() {
            private double distanceTravelled;
            private Location previousLocation = start;
            private int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getWorld() != start.getWorld()) {
                    stopAction(player);
                    return;
                }
                ticks++;
                Vector direction = player.getLocation().getDirection();
                if (direction.lengthSquared() < 0.001) {
                    stopAction(player);
                    return;
                }
                Location current = player.getLocation();
                distanceTravelled += current.distance(previousLocation);
                previousLocation = current.clone();
                Vector dashDirection = direction.normalize();
                player.setVelocity(player.getVelocity().multiply(0.82).add(dashDirection.multiply(0.3)));
                strikeNearby(player, current, 4, false, hit);
                XParticle.of("SWEEP_ATTACK").ifPresent(particle -> player.getWorld().spawnParticle(
                        particle.get(), current.clone().add(0, 1, 0), 2, 0.25, 0.3, 0.25, 0.01));
                XParticle.of("CLOUD").ifPresent(particle -> player.getWorld().spawnParticle(
                        particle.get(), current.clone().add(0, 0.8, 0), 5, 0.25, 0.2, 0.25, 0.04));
                XParticle.of("CRIT").ifPresent(particle -> player.getWorld().spawnParticle(
                        particle.get(), current.clone().add(0, 1, 0), 3, 0.3, 0.35, 0.3, 0.15));
                if (distanceTravelled >= 12 || ticks >= 40) {
                    stopAction(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        activeActions.put(player.getUniqueId(), task);
        XSound.matchXSound("ENTITY_PLAYER_ATTACK_SWEEP").ifPresent(sound -> sound.play(player));
        XSound.matchXSound("ENTITY_ENDER_DRAGON_FLAP").ifPresent(sound -> sound.play(player));
    }

    private void slam(Player player) {
        if (!player.isOnGround()) {
            messages.send(player, "sw-slam-ground");
            return;
        }
        if (!startCooldown(player, slamCooldownKey, cooldownDurationMillis("slam", 5 * 60 * 1000L))) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Location start = player.getLocation();
        fallProtection.put(uuid, System.currentTimeMillis() + 30000);
        Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtection.remove(uuid), 600L);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, 1.2, 0));
        XSound.matchXSound("ENTITY_FIREWORK_ROCKET_LAUNCH").ifPresent(sound -> sound.play(player));
        BukkitTask task = new BukkitRunnable() {
            private int ticks;
            private boolean airborne;
            private boolean dropping;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getWorld() != start.getWorld()) {
                    stopAction(player);
                    fallProtection.remove(uuid);
                    return;
                }
                ticks++;
                Location current = player.getLocation();
                if (!player.isOnGround()) {
                    airborne = true;
                }
                if (airborne) {
                    XParticle.of("CLOUD").ifPresent(particle -> player.getWorld().spawnParticle(
                            particle.get(), current.clone().add(0, 0.5, 0), 5, 0.2, 0.2, 0.2, 0.03));
                    XParticle.of("CRIT").ifPresent(particle -> player.getWorld().spawnParticle(
                            particle.get(), current.clone().add(0, 0.4, 0), 3, 0.15, 0.15, 0.15, 0.08));
                }
                if (player.isOnGround() && airborne) {
                    strikeNearby(player, current, 8, true, new HashSet<>());
                    XSound.matchXSound("ENTITY_GENERIC_EXPLODE").ifPresent(sound -> sound.play(player));
                    XParticle.of("EXPLOSION").ifPresent(particle -> player.getWorld().spawnParticle(
                            particle.get(), current.clone().add(0, 0.2, 0), 1));
                    XParticle.of("CLOUD").ifPresent(particle -> player.getWorld().spawnParticle(
                            particle.get(), current.clone().add(0, 0.2, 0), 24, 0.8, 0.15, 0.8, 0.12));
                    XParticle.of("CRIT").ifPresent(particle -> {
                        for (int i = 0; i < 24; i++) {
                            double angle = Math.PI * 2 * i / 24;
                            Location point = current.clone().add(Math.cos(angle) * 3, 0.2, Math.sin(angle) * 3);
                            player.getWorld().spawnParticle(particle.get(), point, 2, 0.15, 0.05, 0.15, 0.1);
                        }
                    });
                    player.setFallDistance(0);
                    stopAction(player);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtection.remove(uuid), 2L);
                    return;
                }
                if (!airborne && ticks >= 5) {
                    stopAction(player);
                    fallProtection.remove(uuid);
                    return;
                }
                if (ticks >= 200) {
                    stopAction(player);
                    return;
                }
                if (!dropping && (current.getY() >= start.getY() + 4 || ticks >= 30
                        || (airborne && player.getVelocity().getY() <= 0))) {
                    if (current.getY() > start.getY() + 4) {
                        Location peak = current.clone();
                        peak.setY(start.getY() + 4);
                        if (canOccupy(player, peak) && !move(player, peak)) {
                            stopAction(player);
                            fallProtection.remove(uuid);
                            return;
                        }
                    }
                    dropping = true;
                    player.setVelocity(new Vector(0, -0.45, 0));
                } else if (dropping) {
                    player.setVelocity(new Vector(0, Math.max(-1.8, player.getVelocity().getY() - 0.12), 0));
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        activeActions.put(uuid, task);
    }

    private void strikeNearby(Player attacker, Location center, double radius, boolean slam, Set<UUID> hit) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(attacker)
                    || target.isDead() || !target.isValid()) {
                continue;
            }
            if (target instanceof Player player
                    && (!center.getWorld().getPVP() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                continue;
            }
            double distance = target.getLocation().distance(center);
            if (distance > radius || !hit.add(target.getUniqueId())) {
                continue;
            }
            double damage = slam ? (distance <= 2 ? 10 : 10 - (distance - 2)) : 6;
            if (target instanceof Player player) {
                altar.damageFromAbility(player, attacker, damage);
            } else {
                target.damage(damage, attacker);
            }
        }
    }

    private boolean canOccupy(Player player, Location destination) {
        if (!destination.getWorld().getWorldBorder().isInside(destination)
                || destination.getY() < destination.getWorld().getMinHeight()
                || destination.getY() + player.getHeight() >= destination.getWorld().getMaxHeight()) {
            return false;
        }
        Vector shift = destination.toVector().subtract(player.getLocation().toVector());
        BoundingBox box = player.getBoundingBox().shift(shift);
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                    Block block = destination.getWorld().getBlockAt(x, y, z);
                    if (!block.isPassable() && box.overlaps(block.getBoundingBox())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private boolean move(Player player, Location destination) {
        internalTeleports.add(player.getUniqueId());
        try {
            return player.teleport(destination);
        } finally {
            internalTeleports.remove(player.getUniqueId());
        }
    }

    private void stopAction(Player player) {
        BukkitTask task = activeActions.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && fallProtection.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) {
            event.setCancelled(true);
            player.setFallDistance(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!internalTeleports.contains(event.getPlayer().getUniqueId())) {
            stopAction(event.getPlayer());
            fallProtection.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (player.getPersistentDataContainer().getOrDefault(dashCooldownKey, PersistentDataType.LONG, 0L) > now
                || player.getPersistentDataContainer().getOrDefault(slamCooldownKey, PersistentDataType.LONG, 0L) > now
                || player.getPersistentDataContainer().getOrDefault(activateCooldownKey, PersistentDataType.LONG, 0L) > now) {
            startCooldownHud(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopAction(player);
        fallProtection.remove(event.getPlayer().getUniqueId());
        BukkitTask cooldownTask = cooldownHudTasks.remove(player.getUniqueId());
        if (cooldownTask != null) {
            cooldownTask.cancel();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        stopAction(event.getEntity());
        fallProtection.remove(event.getEntity().getUniqueId());
    }

    private static final class MenuHolder implements InventoryHolder {
        @Getter
        private final Inventory inventory = Bukkit.createInventory(this, 27, MessageService.color("&5Superweapons"));
    }
}