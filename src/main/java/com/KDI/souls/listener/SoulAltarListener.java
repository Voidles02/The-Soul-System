package com.KDI.souls.listener;

import com.cryptomorin.xseries.particles.XParticle;
import com.cryptomorin.xseries.XSound;
import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.gui.SoulAltarHolder;
import com.KDI.souls.message.MessageService;
import com.KDI.souls.service.SoulService;
import com.KDI.souls.structure.SoulStructureService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulAltarListener implements Listener {
    private final JavaPlugin plugin;
    private final SoulService souls;
    private final SoulsConfig config;
    private final MessageService messages;
    private final SoulStructureService structures;
    private final org.bukkit.NamespacedKey damageKey;
    private final org.bukkit.NamespacedKey durabilityKey;
    private final org.bukkit.NamespacedKey lifestealKey;
    private final org.bukkit.NamespacedKey attractKey;
    private final org.bukkit.NamespacedKey legacySpeedKey;
    private final org.bukkit.NamespacedKey critKey;
    private final org.bukkit.NamespacedKey yieldKey;
    private final org.bukkit.NamespacedKey projectileDamageKey;
    private final org.bukkit.NamespacedKey projectileCritKey;
    private final org.bukkit.NamespacedKey projectileLifestealKey;
    private final org.bukkit.NamespacedKey projectileAttractKey;
    private final org.bukkit.NamespacedKey projectileWeaponKey;
    private final UUID legacySpeedModifierUuid;
    private final UUID damageModifierUuid;
    private final UUID vanillaDamageModifierUuid;
    private final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> abilityAttackers = ConcurrentHashMap.newKeySet();

    public SoulAltarListener(JavaPlugin plugin, SoulService souls, SoulsConfig config,
                             MessageService messages, SoulStructureService structures) {
        this.plugin = plugin;
        this.souls = souls;
        this.config = config;
        this.messages = messages;
        this.structures = structures;
        this.damageKey = new org.bukkit.NamespacedKey(plugin, "soul_weapon_damage");
        this.durabilityKey = new org.bukkit.NamespacedKey(plugin, "soul_weapon_durability");
        this.lifestealKey = new org.bukkit.NamespacedKey(plugin, "soul_weapon_lifesteal");
        this.attractKey = new org.bukkit.NamespacedKey(plugin, "soul_weapon_attract");
        this.legacySpeedKey = new org.bukkit.NamespacedKey(plugin, "soul_weapon_speed");
        this.critKey = new org.bukkit.NamespacedKey(plugin, "soul_weapon_crit");
        this.yieldKey = new org.bukkit.NamespacedKey(plugin, "soul_yield");
        this.projectileDamageKey = new org.bukkit.NamespacedKey(plugin, "soul_projectile_damage");
        this.projectileCritKey = new org.bukkit.NamespacedKey(plugin, "soul_projectile_crit");
        this.projectileLifestealKey = new org.bukkit.NamespacedKey(plugin, "soul_projectile_lifesteal");
        this.projectileAttractKey = new org.bukkit.NamespacedKey(plugin, "soul_projectile_attract");
        this.projectileWeaponKey = new org.bukkit.NamespacedKey(plugin, "soul_projectile_weapon");
        this.legacySpeedModifierUuid = UUID.nameUUIDFromBytes((plugin.getName() + ":speed").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.damageModifierUuid = UUID.nameUUIDFromBytes((plugin.getName() + ":damage").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.vanillaDamageModifierUuid = UUID.nameUUIDFromBytes((plugin.getName() + ":vanilla_damage").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (ItemStack item : player.getInventory().getContents()) {
            refreshSoulAttributes(item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            refreshSoulAttributes(item);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        String type = structures.itemType(event.getItemInHand());
        if ("altar".equals(type)) {
            structures.tag(event.getBlockPlaced(), "altar");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!structures.isStructureBlock(event.getBlock(), "altar")) {
            return;
        }
        ItemStack item = structures.createItem("altar");
        if (item != null) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().name().startsWith("RIGHT_CLICK") || event.getClickedBlock() == null
                || !structures.isStructureBlock(event.getClickedBlock(), "altar")
                || !config.featureEnabled("altar")) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(config.altarPermission())) {
            messages.send(player, "altar-no-permission");
            return;
        }
        open(player, event.getClickedBlock().getLocation());
    }

    private void open(Player player, Location altar) {
        SoulAltarHolder holder = new SoulAltarHolder(altar);
        Inventory inventory = Bukkit.createInventory(holder, 9, MessageService.color("&8Soul Altar"));
        holder.setInventory(inventory);
        ItemStack filler = item("BLACK_STAINED_GLASS_PANE", "&r");
        if (filler != null) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        inventory.setItem(4, item("AIR", "&r"));
        holder.clearPreview();
        refreshUpgradeButton(holder, inventory);
        inventory.setItem(8, item("BARRIER", "&c&lClose"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SoulAltarHolder holder)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack clicked = event.getCurrentItem();
                if (!isWeapon(clicked)) {
                    messages.send((Player) event.getWhoClicked(), "altar-invalid-item");
                    return;
                }
                ItemStack center = event.getView().getTopInventory().getItem(4);
                if (center == null || center.getType().isAir()) {
                    ItemStack moved = clicked.clone();
                    moved.setAmount(1);
                    event.getView().getTopInventory().setItem(4, moved);
                    if (clicked.getAmount() == 1) {
                        event.getClickedInventory().setItem(event.getSlot(), null);
                    } else {
                        clicked.setAmount(clicked.getAmount() - 1);
                    }
                    refreshLater(holder, event.getView().getTopInventory());
                }
            }
            return;
        }
        if (rawSlot == 4) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isWeapon(cursor)) {
                event.setCancelled(true);
                messages.send((Player) event.getWhoClicked(), "altar-invalid-item");
            }
            if (cursor != null && !cursor.getType().isAir() && cursor.getAmount() > 1) {
                event.setCancelled(true);
                messages.send((Player) event.getWhoClicked(), "altar-invalid-item");
            }
            if (event.getClick().name().equals("NUMBER_KEY")) {
                ItemStack hotbar = ((Player) event.getWhoClicked()).getInventory().getItem(event.getHotbarButton());
                if (hotbar != null && !hotbar.getType().isAir() && !isWeapon(hotbar)) {
                    event.setCancelled(true);
                    messages.send((Player) event.getWhoClicked(), "altar-invalid-item");
                }
            }
            refreshLater((SoulAltarHolder) event.getView().getTopInventory().getHolder(),
                    event.getView().getTopInventory());
            return;
        }
        event.setCancelled(true);
        if (rawSlot == 6) {
            upgrade((Player) event.getWhoClicked(), holder, event.getView().getTopInventory());
        } else if (rawSlot == 8) {
            event.getWhoClicked().closeInventory();
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SoulAltarHolder)) {
            return;
        }
        if (event.getRawSlots().size() != 1 || !event.getRawSlots().contains(4)
                || !isWeapon(event.getOldCursor()) || event.getOldCursor().getAmount() != 1) {
            event.setCancelled(true);
        } else if (event.getView().getTopInventory().getHolder() instanceof SoulAltarHolder holder) {
            refreshLater(holder, event.getView().getTopInventory());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SoulAltarHolder)) {
            return;
        }
        ItemStack item = event.getView().getTopInventory().getItem(4);
        if (item == null || item.getType().isAir() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        event.getView().getTopInventory().setItem(4, null);
    }

    private void upgrade(Player player, SoulAltarHolder holder, Inventory inventory) {
        if (!structures.isStructureBlock(holder.getAltar().getBlock(), "altar")) {
            messages.send(player, "altar-unavailable");
            return;
        }
        ItemStack selected = inventory.getItem(4);
        if (!isWeapon(selected) || selected.getAmount() != 1) {
            messages.send(player, "altar-invalid-item");
            return;
        }
        ItemStack snapshot = selected.clone();
        if (!processing.add(player.getUniqueId())) {
            return;
        }
        souls.loadBalance(player.getUniqueId()).whenComplete((balance, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    try {
                        if (error != null) {
                            if (player.isOnline()) {
                                messages.send(player, "altar-unavailable");
                            }
                            return;
                        }
                        if (!player.isOnline()
                                || !(player.getOpenInventory().getTopInventory().getHolder() instanceof SoulAltarHolder)
                                || player.getOpenInventory().getTopInventory() != inventory) {
                            return;
                        }
                        ItemStack current = inventory.getItem(4);
                        if (!isWeapon(current) || current.getAmount() != snapshot.getAmount() || !current.isSimilar(snapshot)) {
                            messages.send(player, "altar-invalid-item");
                            return;
                        }
                        UpgradeRoll roll = previewRoll(holder, current);
                        if (roll == null) {
                            messages.send(player, "altar-maxed");
                            return;
                        }
                        SoulService.OperationResult result = souls.spendSouls(player.getUniqueId(), roll.cost(), "soul-altar");
                        if (!result.successful()) {
                            messages.send(player, "altar-insufficient");
                            return;
                        }
                        ItemStack upgraded = current.clone();
                        String upgrade = applyUpgrade(upgraded, roll);
                        inventory.setItem(4, upgraded);
                        refreshUpgradeButton(holder, inventory);
                        messages.send(player, "altar-success", Map.of("upgrade", upgrade, "cost", roll.cost()));
                        player.sendActionBar(MessageService.color("&6Soul Altar &8» &f" + upgrade + " &7applied"));
                        feedback(player, holder.getAltar());
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Soul Altar upgrade failed for " + player.getName() + ": "
                                + exception.getMessage());
                        if (player.isOnline()) {
                            messages.send(player, "altar-unavailable");
                        }
                    } finally {
                        processing.remove(player.getUniqueId());
                    }
                })).exceptionally(error -> {
                    processing.remove(player.getUniqueId());
                    return null;
                });
    }

    private UpgradeRoll rollUpgrade(ItemStack item) {
        var pdc = item.getItemMeta().getPersistentDataContainer();
        String material = com.cryptomorin.xseries.XMaterial.matchXMaterial(item.getType()).name();
        boolean book = material.equals("BOOK") || material.equals("ENCHANTED_BOOK");
        boolean tool = material.endsWith("_PICKAXE") || material.endsWith("_SHOVEL") || material.endsWith("_HOE");
        List<String> upgrades = book ? new ArrayList<>()
                : tool ? new ArrayList<>(List.of("durability", "yield"))
                : new ArrayList<>(List.of("damage", "durability", "crit"));
        boolean hasLifesteal = pdc.getOrDefault(lifestealKey, PersistentDataType.DOUBLE, 0D) > 0;
        boolean hasAttract = pdc.has(attractKey, PersistentDataType.BYTE)
                || pdc.getOrDefault(attractKey, PersistentDataType.DOUBLE, 0D) > 0;
        if (!tool && !hasAttract) {
            upgrades.add("lifesteal");
        }
        if (!tool && !hasLifesteal) {
            upgrades.add("attract");
        }
        if (book) {
            upgrades.add("yield");
        }
        upgrades.removeIf(type -> currentValue(pdc, type) >= cap(item, type));
        if (upgrades.isEmpty()) {
            return null;
        }
        String upgrade = upgrades.get(ThreadLocalRandom.current().nextInt(upgrades.size()));
        double amount;
        double maximum;
        if (upgrade.equals("yield")) {
            amount = 1;
            maximum = 1;
        } else if (upgrade.equals("damage")) {
            amount = random(config.altarDamageMin(), config.altarDamageMax());
            maximum = config.altarDamageMax();
        } else if (upgrade.equals("durability")) {
            amount = random(config.altarDurabilityMin(), config.altarDurabilityMax());
            maximum = config.altarDurabilityMax();
        } else if (upgrade.equals("lifesteal")) {
            amount = random(config.altarLifestealMin(), config.altarLifestealMax());
            maximum = config.altarLifestealMax();
        } else if (upgrade.equals("attract")) {
            amount = random(config.altarAttractMin(), config.altarAttractMax());
            maximum = config.altarAttractMax();
        } else if (upgrade.equals("crit")) {
            amount = random(config.altarCritMin(), config.altarCritMax());
            maximum = config.altarCritCap();
        } else {
            amount = random(config.altarCritMin(), config.altarCritMax());
            maximum = config.altarCritCap();
        }
        long cost = Math.max(1, (long) Math.ceil(config.altarCost() * amount / maximum));
        return new UpgradeRoll(upgrade, amount, cost);
    }

    private UpgradeRoll previewRoll(SoulAltarHolder holder, ItemStack item) {
        ItemStack previewItem = holder.getPreviewItem();
        if (previewItem != null && previewItem.isSimilar(item) && holder.getPreviewType() != null) {
            return new UpgradeRoll(holder.getPreviewType(), holder.getPreviewAmount(), holder.getPreviewCost());
        }
        UpgradeRoll roll = rollUpgrade(item);
        if (roll == null) {
            holder.clearPreview();
        }
        return roll;
    }

    private void refreshLater(SoulAltarHolder holder, Inventory inventory) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (inventory.getHolder() == holder) {
                refreshUpgradeButton(holder, inventory);
            }
        });
    }

    private void refreshUpgradeButton(SoulAltarHolder holder, Inventory inventory) {
        ItemStack selected = inventory.getItem(4);
        refreshSoulAttributes(selected);
        if (!isWeapon(selected) || selected.getAmount() != 1) {
            holder.clearPreview();
            inventory.setItem(6, item("NETHER_STAR", "&6&lUpgrade",
                    "&7Place a supported weapon, tool, or book in the center.",
                    "&7Soul limit: &b" + config.maxBalance(), "&aNo cooldown"));
            return;
        }
        UpgradeRoll roll = rollUpgrade(selected);
        if (roll == null) {
            holder.clearPreview();
            inventory.setItem(6, item("NETHER_STAR", "&e&lMaxed",
                    "&7This item has reached every compatible upgrade cap.",
                    "&7Soul limit: &b" + config.maxBalance()));
            return;
        }
        holder.setPreview(selected, roll.type(), roll.amount(), roll.cost());
        inventory.setItem(6, item("NETHER_STAR", "&6&lUpgrade &8(&e" + roll.cost() + " Souls&8)",
                "&7Next upgrade: &f" + displayUpgrade(roll.type()),
                "&7Price: &b" + roll.cost() + " Souls",
                "&7Your soul limit: &b" + config.maxBalance(), "&aNo cooldown"));
    }

    private void refreshSoulAttributes(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean hadCustomAttributes = hasSoulModifier(meta);
        com.cryptomorin.xseries.XAttribute.of("attack_speed").ifPresent(attribute -> removeSoulModifier(meta,
                attribute.get(), "souls:speed", legacySpeedModifierUuid));
        com.cryptomorin.xseries.XAttribute.of("attack_damage").ifPresent(attribute -> removeSoulModifier(meta,
                attribute.get(), "souls:damage", damageModifierUuid));
        com.cryptomorin.xseries.XAttribute.of("attack_damage").ifPresent(attribute -> removeSoulModifier(meta,
                attribute.get(), "souls:vanilla_damage", vanillaDamageModifierUuid));

        var container = meta.getPersistentDataContainer();
        String material = com.cryptomorin.xseries.XMaterial.matchXMaterial(item.getType()).name();
        if (container.has(legacySpeedKey, PersistentDataType.DOUBLE)) {
            if (!container.has(critKey, PersistentDataType.DOUBLE)) {
                container.set(critKey, PersistentDataType.DOUBLE, critValue(container));
            }
            container.remove(legacySpeedKey);
        }
        removeLore(meta, "Soul Speed:");
        if (container.has(critKey, PersistentDataType.DOUBLE)) {
            double crit = critValue(container);
            container.set(critKey, PersistentDataType.DOUBLE, crit);
            setLore(meta, "Crit:", "&dCrit: &f" + percent(crit));
        }

        double damage = container.getOrDefault(damageKey, PersistentDataType.DOUBLE, 0D);
        double baseDamage = baseAttackDamage(material);
        if ((hadCustomAttributes || critValue(container) > 0 || damage > 0) && baseDamage > 0 && isCombatWeapon(item)) {
            double soulAmount = Double.isFinite(damage) && damage > 0
                    ? Math.min(baseDamage * (damage * 0.40), Math.max(0, damageTotalCap(material) - baseDamage)) : 0;
            com.cryptomorin.xseries.XAttribute.of("attack_damage").ifPresent(attribute -> {
                double totalModifier = Math.max(0, baseDamage - 1.0 + soulAmount);
                meta.addAttributeModifier(attribute.get(), new AttributeModifier(damageModifierUuid, "souls:damage",
                        totalModifier, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
            });
        }
        item.setItemMeta(meta);
    }

    private boolean hasSoulModifier(ItemMeta meta) {
        return com.cryptomorin.xseries.XAttribute.of("attack_speed")
                .map(attribute -> hasSoulModifier(meta, attribute.get()))
                .orElse(false)
                || com.cryptomorin.xseries.XAttribute.of("attack_damage")
                .map(attribute -> hasSoulModifier(meta, attribute.get()))
                .orElse(false);
    }

    private boolean hasSoulModifier(ItemMeta meta, org.bukkit.attribute.Attribute attribute) {
        var modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null) {
            return false;
        }
        for (var modifier : modifiers) {
            if (legacySpeedModifierUuid.equals(modifier.getUniqueId())
                    || damageModifierUuid.equals(modifier.getUniqueId())
                    || vanillaDamageModifierUuid.equals(modifier.getUniqueId())
                    || "souls:speed".equals(modifier.getName())
                    || "souls:damage".equals(modifier.getName())
                    || "souls:vanilla_damage".equals(modifier.getName())) {
                return true;
            }
        }
        return false;
    }

    private void removeSoulModifier(ItemMeta meta, org.bukkit.attribute.Attribute attribute, String name, UUID uuid) {
        var modifiers = meta.getAttributeModifiers(attribute);
        if (modifiers == null) {
            return;
        }
        for (var modifier : new ArrayList<>(modifiers)) {
            if (name.equals(modifier.getName()) || uuid.equals(modifier.getUniqueId())) {
                meta.removeAttributeModifier(attribute, modifier);
            }
        }
    }

    private String displayUpgrade(String type) {
        return type.equals("lifesteal") ? "LifeSteal" : type.substring(0, 1).toUpperCase() + type.substring(1);
    }

    public void applyMaxSoulEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        var container = meta.getPersistentDataContainer();
        container.set(damageKey, PersistentDataType.DOUBLE, config.altarDamageCap());
        container.set(durabilityKey, PersistentDataType.DOUBLE, config.altarDurabilityCap());
        container.set(lifestealKey, PersistentDataType.DOUBLE, config.altarLifestealCap());
        container.set(attractKey, PersistentDataType.DOUBLE, config.altarAttractCap());
        container.set(critKey, PersistentDataType.DOUBLE, config.altarCritCap());
        container.set(yieldKey, PersistentDataType.INTEGER, 3);
        setLore(meta, "Soul Damage:", "&cSoul Damage: &f" + percent(config.altarDamageCap()));
        setLore(meta, "Soul Durability:", "&aSoul Durability: &f" + percent(config.altarDurabilityCap()));
        setLore(meta, "LifeSteal Chance:", "&dLifeSteal Chance: &f" + percent(config.altarLifestealCap()));
        setLore(meta, "Attract Chance:", "&bAttract Chance: &f" + percent(config.altarAttractCap()));
        setLore(meta, "Crit:", "&dCrit: &f" + percent(config.altarCritCap()));
        setLore(meta, "Yield ", "&6Yield III");
        item.setItemMeta(meta);
        refreshSoulAttributes(item);
    }

    public void damageFromAbility(Player target, Player attacker, double damage) {
        abilityAttackers.add(attacker.getUniqueId());
        try {
            target.damage(damage, attacker);
        } finally {
            abilityAttackers.remove(attacker.getUniqueId());
        }
    }

    private String applyUpgrade(ItemStack item, UpgradeRoll roll) {
        ItemMeta meta = item.getItemMeta();
        var container = meta.getPersistentDataContainer();
        String upgrade = roll.type();
        if (upgrade.equals("yield")) {
            int level = Math.min(3, container.getOrDefault(yieldKey, PersistentDataType.INTEGER, 0) + 1);
            container.set(yieldKey, PersistentDataType.INTEGER, level);
            setLore(meta, "Yield ", "&6Yield " + roman(level));
        } else if (upgrade.equals("damage")) {
            double value = Math.min(config.altarDamageCap(), container.getOrDefault(damageKey, PersistentDataType.DOUBLE, 0D)
                    + roll.amount());
            container.set(damageKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "Soul Damage:", "&cSoul Damage: &f" + percent(value));
        } else if (upgrade.equals("durability")) {
            double value = Math.min(config.altarDurabilityCap(), container.getOrDefault(durabilityKey, PersistentDataType.DOUBLE, 0D)
                    + roll.amount());
            container.set(durabilityKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "Soul Durability:", "&aSoul Durability: &f" + percent(value));
        } else if (upgrade.equals("lifesteal")) {
            double value = Math.min(config.altarLifestealCap(), container.getOrDefault(lifestealKey, PersistentDataType.DOUBLE, 0D)
                    + roll.amount());
            container.set(lifestealKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "LifeSteal Chance:", "&dLifeSteal Chance: &f" + percent(value));
        } else if (upgrade.equals("attract")) {
            double oldValue = container.has(attractKey, PersistentDataType.BYTE) ? config.altarAttractMin()
                    : container.getOrDefault(attractKey, PersistentDataType.DOUBLE, 0D);
            container.remove(attractKey);
            double value = Math.min(config.altarAttractCap(), oldValue + roll.amount());
            container.set(attractKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "Attract Chance:", "&bAttract Chance: &f" + percent(value));
        } else {
            double value = Math.min(config.altarCritCap(), critValue(container) + roll.amount());
            container.set(critKey, PersistentDataType.DOUBLE, value);
            removeLore(meta, "Soul Speed:");
            setLore(meta, "Crit:", "&dCrit: &f" + percent(value));
        }
        item.setItemMeta(meta);
        refreshSoulAttributes(item);
        return upgrade.equals("lifesteal") ? "LifeSteal" : upgrade.substring(0, 1).toUpperCase() + upgrade.substring(1);
    }

    private record UpgradeRoll(String type, double amount, long cost) {
    }

    private double currentValue(org.bukkit.persistence.PersistentDataContainer pdc, String type) {
        if (type.equals("yield")) {
            return pdc.getOrDefault(yieldKey, PersistentDataType.INTEGER, 0);
        }
        if (type.equals("attract") && pdc.has(attractKey, PersistentDataType.BYTE)) {
            return config.altarAttractMin();
        }
        org.bukkit.NamespacedKey key = switch (type) {
            case "damage" -> damageKey;
            case "durability" -> durabilityKey;
            case "lifesteal" -> lifestealKey;
            case "attract" -> attractKey;
            case "crit" -> critKey;
            default -> critKey;
        };
        return type.equals("crit") ? critValue(pdc) : pdc.getOrDefault(key, PersistentDataType.DOUBLE, 0D);
    }

    private double cap(ItemStack item, String type) {
        return switch (type) {
            case "yield" -> 3;
            case "damage" -> config.altarDamageCap();
            case "durability" -> config.altarDurabilityCap();
            case "lifesteal" -> config.altarLifestealCap();
            case "attract" -> config.altarAttractCap();
            case "crit" -> config.altarCritCap();
            default -> config.altarCritCap();
        };
    }

    private double critValue(org.bukkit.persistence.PersistentDataContainer pdc) {
        double value = pdc.has(critKey, PersistentDataType.DOUBLE)
                ? pdc.getOrDefault(critKey, PersistentDataType.DOUBLE, 0D)
                : pdc.getOrDefault(legacySpeedKey, PersistentDataType.DOUBLE, 0D);
        return Double.isFinite(value) ? Math.min(config.altarCritCap(), Math.max(0, value)) : 0;
    }

    private double theoreticalWeaponStrength(String material) {
        if (material.equals("MACE")) {
            return 24.0;
        }
        if (material.equals("TRIDENT") || material.equals("SPEAR")) {
            return 9.0;
        }
        if (material.equals("BOW")) {
            return 6.0;
        }
        if (material.equals("CROSSBOW")) {
            return 7.0;
        }
        return baseAttackDamage(material);
    }

    private boolean isWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        String name = com.cryptomorin.xseries.XMaterial.matchXMaterial(item.getType()).name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.equals("BOW") || name.equals("CROSSBOW")
                || name.equals("MACE") || name.endsWith("_SPEAR") || name.equals("SPEAR") || name.equals("TRIDENT")
                || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.equals("BOOK")
                || name.equals("ENCHANTED_BOOK");
    }

    private boolean isCombatWeapon(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return baseAttackDamage(com.cryptomorin.xseries.XMaterial.matchXMaterial(item.getType()).name()) > 0;
    }

    private boolean isAttributeWeapon(ItemStack item) {
        if (!isWeapon(item)) {
            return false;
        }
        String material = com.cryptomorin.xseries.XMaterial.matchXMaterial(item.getType()).name();
        return !material.equals("BOOK") && !material.equals("ENCHANTED_BOOK");
    }

    private double damageTotalCap(String material) {
        if (material.endsWith("_SWORD")) {
            return material.startsWith("NETHERITE") ? 12 : material.startsWith("DIAMOND") ? 11
                    : material.startsWith("IRON") ? 10 : material.startsWith("STONE") ? 9 : 8;
        }
        if (material.endsWith("_AXE")) {
            return material.startsWith("NETHERITE") ? 15 : material.startsWith("DIAMOND") ? 13.5
                    : material.startsWith("IRON") ? 12.5 : material.startsWith("STONE") ? 11.5 : 10.5;
        }
        return soulDamageCap(material);
    }

    private double baseAttackDamage(String material) {
        if (material.endsWith("_SWORD")) {
            return material.startsWith("NETHERITE") ? 8 : material.startsWith("DIAMOND") ? 7
                    : material.startsWith("IRON") ? 6 : material.startsWith("STONE") ? 5 : 4;
        }
        if (material.endsWith("_AXE")) {
            return material.startsWith("NETHERITE") ? 10 : material.startsWith("DIAMOND") ? 9
                    : material.startsWith("IRON") || material.startsWith("STONE") ? 9 : 7;
        }
        if (material.endsWith("_PICKAXE")) {
            return material.startsWith("NETHERITE") ? 6 : material.startsWith("DIAMOND") ? 5
                    : material.startsWith("IRON") ? 4 : material.startsWith("STONE") ? 3 : 2;
        }
        if (material.endsWith("_SHOVEL")) {
            return material.startsWith("NETHERITE") ? 6.5 : material.startsWith("DIAMOND") ? 5.5
                    : material.startsWith("IRON") ? 5 : material.startsWith("STONE") ? 4 : 2.5;
        }
        if (material.endsWith("_HOE")) {
            return material.startsWith("NETHERITE") ? 6 : material.startsWith("DIAMOND") ? 5
                    : material.startsWith("IRON") ? 1 : 1;
        }
        return switch (material) {
            case "MACE" -> 7;
            case "TRIDENT", "SPEAR" -> 9;
            default -> 0;
        };
    }

    private String roman(int level) {
        return switch (level) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack first = event.getInventory().getItem(0);
        ItemStack second = event.getInventory().getItem(1);
        if (first == null || second == null || !first.hasItemMeta() || !second.hasItemMeta()) {
            return;
        }
        var firstPdc = first.getItemMeta().getPersistentDataContainer();
        var secondPdc = second.getItemMeta().getPersistentDataContainer();
        int firstYield = firstPdc.getOrDefault(yieldKey, PersistentDataType.INTEGER, 0);
        int secondYield = secondPdc.getOrDefault(yieldKey, PersistentDataType.INTEGER, 0);
        double firstDamage = firstPdc.getOrDefault(damageKey, PersistentDataType.DOUBLE, 0D);
        double secondDamage = secondPdc.getOrDefault(damageKey, PersistentDataType.DOUBLE, 0D);
        double firstCrit = critValue(firstPdc);
        double secondCrit = critValue(secondPdc);
        double firstDurability = firstPdc.getOrDefault(durabilityKey, PersistentDataType.DOUBLE, 0D);
        double secondDurability = secondPdc.getOrDefault(durabilityKey, PersistentDataType.DOUBLE, 0D);
        boolean secondLifesteal = secondPdc.getOrDefault(lifestealKey, PersistentDataType.DOUBLE, 0D) > 0;
        boolean secondAttract = secondPdc.has(attractKey, PersistentDataType.BYTE)
                || secondPdc.getOrDefault(attractKey, PersistentDataType.DOUBLE, 0D) > 0;
        if (secondYield == 0 && secondDamage <= 0 && secondCrit <= 0 && secondDurability <= 0 && !secondLifesteal && !secondAttract) {
            return;
        }
        String firstType = com.cryptomorin.xseries.XMaterial.matchXMaterial(first.getType()).name();
        boolean yieldTool = firstType.endsWith("_PICKAXE") || firstType.endsWith("_SHOVEL") || firstType.endsWith("_HOE")
                || firstType.equals("BOOK") || firstType.equals("ENCHANTED_BOOK");
        boolean majorTarget = isWeapon(first) && (!yieldTool || firstType.equals("BOOK") || firstType.equals("ENCHANTED_BOOK"));
        if ((secondYield > 0 && !yieldTool) || ((secondDamage > 0 || secondCrit > 0) && !isCombatWeapon(first))
                || (secondDurability > 0 && !isWeapon(first))
                || ((secondLifesteal || secondAttract) && !majorTarget)) {
            return;
        }
        if (secondLifesteal && (firstPdc.has(attractKey, PersistentDataType.BYTE)
                || firstPdc.getOrDefault(attractKey, PersistentDataType.DOUBLE, 0D) > 0)
                || secondAttract && firstPdc.getOrDefault(lifestealKey, PersistentDataType.DOUBLE, 0D) > 0) {
            return;
        }
        ItemStack result = first.clone();
        ItemMeta meta = result.getItemMeta();
        var resultPdc = meta.getPersistentDataContainer();
        if (secondDamage > 0) {
            double value = Math.min(config.altarDamageCap(), firstDamage + secondDamage);
            resultPdc.set(damageKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "Soul Damage:", "&cSoul Damage: &f" + percent(value));
        }
        if (secondCrit > 0) {
            double value = Math.min(config.altarCritCap(), firstCrit + secondCrit);
            resultPdc.remove(legacySpeedKey);
            resultPdc.set(critKey, PersistentDataType.DOUBLE, value);
            removeLore(meta, "Soul Speed:");
            setLore(meta, "Crit:", "&dCrit: &f" + percent(value));
        }
        if (secondYield > 0) {
            int level = firstYield == secondYield ? Math.min(3, firstYield + 1) : Math.max(firstYield, secondYield);
            resultPdc.set(yieldKey, PersistentDataType.INTEGER, level);
            setLore(meta, "Yield ", "&6Yield " + roman(level));
        }
        if (secondDurability > 0) {
            double value = Math.min(config.altarDurabilityCap(), firstDurability + secondDurability);
            resultPdc.set(durabilityKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "Soul Durability:", "&aSoul Durability: &f" + percent(value));
        }
        if (secondLifesteal) {
            double value = Math.min(config.altarLifestealCap(), firstPdc.getOrDefault(lifestealKey,
                    PersistentDataType.DOUBLE, 0D) + secondPdc.getOrDefault(lifestealKey, PersistentDataType.DOUBLE, 0D));
            resultPdc.set(lifestealKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "LifeSteal Chance:", "&dLifeSteal Chance: &f" + percent(value));
        }
        if (secondAttract) {
            double firstValue = firstPdc.has(attractKey, PersistentDataType.BYTE) ? 1D
                    : firstPdc.getOrDefault(attractKey, PersistentDataType.DOUBLE, 0D);
            double secondValue = secondPdc.has(attractKey, PersistentDataType.BYTE) ? 1D
                    : secondPdc.getOrDefault(attractKey, PersistentDataType.DOUBLE, 0D);
            double value = Math.min(config.altarAttractCap(), firstValue + secondValue);
            resultPdc.remove(attractKey);
            resultPdc.set(attractKey, PersistentDataType.DOUBLE, value);
            setLore(meta, "Attract Chance:", "&bAttract Chance: &f" + percent(value));
        }
        result.setItemMeta(meta);
        refreshSoulAttributes(result);
        event.setResult(result);
    }

    @EventHandler
    public void onYieldDrop(BlockDropItemEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!tool.hasItemMeta()) {
            return;
        }
        int level = tool.getItemMeta().getPersistentDataContainer().getOrDefault(yieldKey, PersistentDataType.INTEGER, 0);
        String block = com.cryptomorin.xseries.XMaterial.matchXMaterial(event.getBlockState().getType()).name();
        boolean matureCrop = event.getBlockState().getBlockData() instanceof org.bukkit.block.data.Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge();
        if (level <= 0 || (!block.contains("ORE") && !block.equals("ANCIENT_DEBRIS") && !matureCrop)) {
            return;
        }
        double bonus = level == 1 ? 0.15 : level == 2 ? 0.45 : 1.30;
        for (org.bukkit.entity.Item dropped : event.getItems()) {
            ItemStack extra = dropped.getItemStack().clone();
            double amount = extra.getAmount() * bonus;
            int whole = (int) Math.floor(amount);
            if (ThreadLocalRandom.current().nextDouble() < amount - whole) {
                whole++;
            }
            if (whole > 0) {
                extra.setAmount(Math.min(extra.getMaxStackSize(), whole));
                event.getPlayer().getWorld().dropItemNaturally(dropped.getLocation(), extra);
            }
        }
    }

    private double random(double min, double max) {
        return min + ThreadLocalRandom.current().nextDouble() * Math.max(0, max - min);
    }

    private String percent(double value) {
        return Math.round(value * 100) + "%";
    }

    private void setLore(ItemMeta meta, String prefix, String line) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(existing -> org.bukkit.ChatColor.stripColor(existing).startsWith(prefix));
        lore.add(MessageService.color(line));
        meta.setLore(lore);
    }

    private void removeLore(ItemMeta meta, String prefix) {
        if (!meta.hasLore()) {
            return;
        }
        List<String> lore = new ArrayList<>(meta.getLore());
        lore.removeIf(existing -> org.bukkit.ChatColor.stripColor(existing).startsWith(prefix));
        meta.setLore(lore);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!isWeapon(weapon) || !weapon.hasItemMeta()) {
            return;
        }
        var source = weapon.getItemMeta().getPersistentDataContainer();
        var target = event.getEntity().getPersistentDataContainer();
        copyDouble(source, target, damageKey, projectileDamageKey);
        double crit = critValue(source);
        if (crit > 0) {
            target.set(projectileCritKey, PersistentDataType.DOUBLE, crit);
        }
        target.set(projectileWeaponKey, PersistentDataType.STRING,
                com.cryptomorin.xseries.XMaterial.matchXMaterial(weapon.getType()).name());
        copyDouble(source, target, lifestealKey, projectileLifestealKey);
        if (source.has(attractKey, PersistentDataType.BYTE)) {
            target.set(projectileAttractKey, PersistentDataType.DOUBLE, 1D);
        } else {
            copyDouble(source, target, attractKey, projectileAttractKey);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && abilityAttackers.contains(player.getUniqueId())) {
            return;
        }
        Player attacker = null;
        ItemStack weapon = null;
        double damageBonus = 0;
        double crit = 0;
        double lifesteal = 0;
        double attract = 0;
        String weaponMaterial = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
            weapon = player.getInventory().getItemInMainHand();
            if (weapon != null && weapon.hasItemMeta()) {
                var pdc = weapon.getItemMeta().getPersistentDataContainer();
                damageBonus = pdc.getOrDefault(damageKey, PersistentDataType.DOUBLE, 0D);
                crit = critValue(pdc);
                lifesteal = pdc.getOrDefault(lifestealKey, PersistentDataType.DOUBLE, 0D);
                attract = pdc.has(attractKey, PersistentDataType.BYTE) ? 1D
                        : pdc.getOrDefault(attractKey, PersistentDataType.DOUBLE, 0D);
                weaponMaterial = com.cryptomorin.xseries.XMaterial.matchXMaterial(weapon.getType()).name();
            }
        } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            attacker = player;
            var pdc = projectile.getPersistentDataContainer();
            damageBonus = pdc.getOrDefault(projectileDamageKey, PersistentDataType.DOUBLE, 0D);
            crit = pdc.getOrDefault(projectileCritKey, PersistentDataType.DOUBLE, 0D);
            lifesteal = pdc.getOrDefault(projectileLifestealKey, PersistentDataType.DOUBLE, 0D);
            attract = pdc.getOrDefault(projectileAttractKey, PersistentDataType.DOUBLE, 0D);
            weaponMaterial = pdc.get(projectileWeaponKey, PersistentDataType.STRING);
        }
        if (attacker == null) {
            return;
        }
        if (event.getDamager() instanceof Projectile) {
            double effectiveDamageBonus = damageBonus * 0.40;
            double modifiedDamage = event.getDamage() * (1 + effectiveDamageBonus)
                    + (effectiveDamageBonus > 0 ? random(0.1, 0.5) : 0);
            if (damageBonus > 0 && weaponMaterial != null) {
                modifiedDamage = Math.min(modifiedDamage, soulDamageCap(weaponMaterial));
            }
            event.setDamage(modifiedDamage);
        } else if (damageBonus > 0 && weaponMaterial != null) {
            event.setDamage(Math.min(event.getDamage(), soulDamageCap(weaponMaterial)));
        }
        boolean critical = event.getDamager() instanceof Projectile projectile
                ? projectile instanceof AbstractArrow arrow && arrow.isCritical()
                : isCriticalMelee(attacker);
        if (critical && crit > 0) {
            event.setDamage(event.getDamage() * (1 + crit));
        }
        if (event.getEntity() instanceof LivingEntity target && ThreadLocalRandom.current().nextDouble() < lifesteal) {
            attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + 4 + ThreadLocalRandom.current().nextDouble() * 4));
        }
        if (event.getEntity() instanceof LivingEntity target && ThreadLocalRandom.current().nextDouble() < attract
                && !target.equals(attacker)) {
            Vector direction = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
            if (direction.lengthSquared() > 0) {
                target.setVelocity(direction.normalize().multiply(0.7).setY(0.25));
            }
        }
    }

    private boolean isCriticalMelee(Player player) {
        boolean blinded = player.getActivePotionEffects().stream()
                .anyMatch(effect -> effect.getType().getKey().getKey().equals("blindness"));
        return player.getFallDistance() > 0 && !player.isOnGround() && !player.isClimbing()
                && !player.isInWater() && !player.isInsideVehicle() && !player.isSprinting() && !blinded;
    }

    private double soulDamageCap(String material) {
        if (material.endsWith("_SWORD")) {
            return material.startsWith("NETHERITE") ? 12 : material.startsWith("DIAMOND") ? 11
                    : material.startsWith("IRON") ? 10 : material.startsWith("STONE") ? 9 : 8;
        }
        if (material.endsWith("_AXE")) {
            return material.startsWith("NETHERITE") ? 15 : material.startsWith("DIAMOND") ? 13.5
                    : material.startsWith("IRON") ? 12.5 : material.startsWith("STONE") ? 11.5 : 10.5;
        }
        if (material.endsWith("_PICKAXE")) {
            return 12.5;
        }
        if (material.endsWith("_SHOVEL")) {
            return 10.5;
        }
        if (material.endsWith("_HOE")) {
            return 8.5;
        }
        return switch (material) {
            case "MACE" -> 20.5;
            case "TRIDENT", "SPEAR" -> 13.5;
            case "BOW", "CROSSBOW" -> 15.5;
            default -> Double.MAX_VALUE;
        };
    }

    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (!item.hasItemMeta()) {
            return;
        }
        double bonus = item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(durabilityKey, PersistentDataType.DOUBLE, 0D);
        if (!Double.isFinite(bonus) || bonus <= 0) {
            return;
        }
        double saveChance = Math.min(0.95, (bonus * 2) / (1 + bonus * 2));
        if (ThreadLocalRandom.current().nextDouble() < saveChance) {
            event.setCancelled(true);
        }
    }

    private void copyDouble(org.bukkit.persistence.PersistentDataContainer source,
                            org.bukkit.persistence.PersistentDataContainer target,
                            org.bukkit.NamespacedKey sourceKey, org.bukkit.NamespacedKey targetKey) {
        Double value = source.get(sourceKey, PersistentDataType.DOUBLE);
        if (value != null) {
            target.set(targetKey, PersistentDataType.DOUBLE, value);
        }
    }

    private ItemStack item(String material, String name, String... lore) {
        ItemStack item = com.cryptomorin.xseries.XMaterial.matchXMaterial(material).map(com.cryptomorin.xseries.XMaterial::parseItem).orElse(null);
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

    private void feedback(Player player, Location location) {
        XSound.matchXSound(config.altarSound()).ifPresent(sound -> sound.play(player));
        XParticle.of(config.altarParticle()).ifPresent(particle ->
                player.getWorld().spawnParticle(particle.get(), location.clone().add(0.5, 1, 0.5), 20, 0.35, 0.35, 0.35, 0.02));
    }
}