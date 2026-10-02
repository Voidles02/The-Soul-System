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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SuperweaponService implements Listener {
    private static final DateTimeFormatter ARTIFACT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin;
    private final SoulAltarListener altar;
    private final MessageService messages;
    private final NamespacedKey artifactKey;
    private final NamespacedKey dashCooldownKey;
    private final NamespacedKey slamCooldownKey;
    private final NamespacedKey activateCooldownKey;
    private final NamespacedKey beamCooldownKey;
    private final NamespacedKey artifactIdKey;
    private final Map<UUID, BukkitTask> activeActions = new HashMap<>();
    private final Map<UUID, BukkitTask> cooldownHudTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> bocActivationEffects = new HashMap<>();
    private final Map<UUID, Long> fallProtection = new HashMap<>();
    private final Set<UUID> internalTeleports = new HashSet<>();
    private final Map<UUID, ArtifactOwnership> artifactOwnerships = new HashMap<>();
    private final File artifactOwnershipFile;

    public SuperweaponService(JavaPlugin plugin, SoulAltarListener altar, MessageService messages) {
        this.plugin = plugin;
        this.altar = altar;
        this.messages = messages;
        this.artifactKey = new NamespacedKey(plugin, "superweapon");
        this.dashCooldownKey = new NamespacedKey(plugin, "sarculum_dash_cooldown");
        this.slamCooldownKey = new NamespacedKey(plugin, "sarculum_slam_cooldown");
        this.activateCooldownKey = new NamespacedKey(plugin, "superweapon_activate_cooldown");
        this.beamCooldownKey = new NamespacedKey(plugin, "boc_beam_cooldown");
        this.artifactIdKey = new NamespacedKey(plugin, "superweapon_id");
        this.artifactOwnershipFile = new File(plugin.getDataFolder(), "artifacts.yml");
        loadArtifactOwnerships();
    }

    public void open(Player player) {
        if (!player.hasPermission("souls.admin")) {
            messages.send(player, "no-permission");
            return;
        }
        MenuHolder holder = new MenuHolder();
        if (artifactEnabled("boc")) {
            holder.getInventory().setItem(11, createBook());
        }
        if (artifactEnabled("sarculum")) {
            holder.getInventory().setItem(15, createSarculum());
        }
        player.openInventory(holder.getInventory());
    }

    public void openArtifactViewer(Player player) {
        if (!artifactTrackingEnabled()) {
            messages.send(player, "sw-artifact-tracking-disabled");
            return;
        }
        registerOnlineArtifactOwnerships();
        ArtifactOverviewHolder holder = new ArtifactOverviewHolder();
        holder.getInventory().setItem(11, createArtifactOverviewItem("boc", createBook()));
        holder.getInventory().setItem(15, createArtifactOverviewItem("sarculum", createSarculum()));
        player.openInventory(holder.getInventory());
    }

    public void openArtifactHistory(Player player, String artifact) {
        openArtifactHistory(player, artifact, 0);
    }

    private void openArtifactHistory(Player player, String artifact, int page) {
        if (!List.of("boc", "sarculum").contains(artifact)) {
            return;
        }
        if (!artifactTrackingEnabled()) {
            messages.send(player, "sw-artifact-tracking-disabled");
            return;
        }
        ArtifactHistoryHolder holder = new ArtifactHistoryHolder(artifact, page);
        renderArtifactHistory(holder);
        player.openInventory(holder.getInventory());
    }

    private ItemStack createArtifactOverviewItem(String artifact, ItemStack item) {
        if (item == null) {
            return null;
        }
        List<ArtifactHistoryEntry> history = artifactHistory(artifact);
        List<ArtifactHistoryEntry> current = history.stream().filter(ArtifactHistoryEntry::current).toList();
        ItemMeta meta = item.getItemMeta();
        meta.setLore(List.of(
                MessageService.color("&7Live-verified holder(s): &f" + (current.isEmpty() ? "None found"
                        : String.join(", ", current.stream().map(ArtifactHistoryEntry::ownerName).distinct().toList()))),
                MessageService.color("&7Verified holders: &f" + current.size()),
                MessageService.color("&7Other profiles: &f" + history.stream().filter(entry -> !entry.current()).count()),
                MessageService.color("&eClick to view ownership history.")));
        item.setItemMeta(meta);
        return item;
    }

    private void renderArtifactHistory(ArtifactHistoryHolder holder) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        List<ArtifactHistoryEntry> history = artifactHistory(holder.artifact());
        ArtifactHistoryEntry current = history.stream().filter(ArtifactHistoryEntry::current).findFirst().orElse(null);
        ItemStack artifactItem = holder.artifact().equals("boc") ? createBook() : createSarculum();
        if (artifactItem != null) {
            ItemMeta meta = artifactItem.getItemMeta();
            meta.setLore(List.of(
                    MessageService.color("&7Live-verified holder: &f" + (current == null ? "None found" : current.ownerName())),
                    MessageService.color("&7Live check: &fOnline player inventories")));
            artifactItem.setItemMeta(meta);
            inventory.setItem(4, artifactItem);
        }

        List<ArtifactHistoryEntry> profiles = history;
        int pageSize = 25;
        int pageCount = Math.max(1, (profiles.size() + pageSize - 1) / pageSize);
        int page = Math.min(holder.page(), pageCount - 1);
        int start = page * pageSize;
        for (int index = start; index < Math.min(start + pageSize, profiles.size()); index++) {
            ItemStack entryItem = XMaterial.matchXMaterial("PLAYER_HEAD").map(XMaterial::parseItem).orElse(null);
            if (entryItem == null) {
                continue;
            }
            ItemMeta meta = entryItem.getItemMeta();
            ArtifactHistoryEntry profile = profiles.get(index);
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(profile.owner()));
            }
            meta.setDisplayName(MessageService.color((profile.current() ? "&a" : "&e") + profile.ownerName()));
            List<String> lore = new java.util.ArrayList<>();
            lore.add(MessageService.color("&7Got: &f" + formatArtifactDate(profile.gotAt())));
            if (profile.current()) {
                lore.add(MessageService.color("&7Status: &aHeld now &8(live-checked)"));
                if (profile.lostAt() > 0) {
                    lore.add(MessageService.color("&7Previous loss: &f" + formatArtifactDate(profile.lostAt())));
                    lore.add(MessageService.color("&7Cause: &f" + (profile.lostCause().isBlank()
                            ? "Unknown" : profile.lostCause())));
                }
            } else if (profile.lostAt() > 0) {
                lore.add(MessageService.color("&7Lost: &f" + formatArtifactDate(profile.lostAt())));
                lore.add(MessageService.color("&7Cause: &f" + (profile.lostCause().isBlank()
                        ? "Unknown" : profile.lostCause())));
                if (profile.recordedCurrent()) {
                    lore.add(MessageService.color(profile.liveChecked()
                            ? "&8Not found in scanned inventories"
                            : "&8Offline; not live-checked"));
                }
            } else {
                lore.add(MessageService.color(profile.liveChecked()
                        ? "&7Status: &8Not found in scanned inventories"
                        : profile.recordedCurrent() ? "&7Status: &8Offline; not live-checked"
                        : "&7Status: &8Not recorded"));
            }
            meta.setLore(lore);
            entryItem.setItemMeta(meta);
            inventory.setItem(19 + index - start, entryItem);
        }

        inventory.setItem(45, createViewerButton("ARROW", "&cBack to artifacts"));
        inventory.setItem(49, createViewerButton("COMPASS", "&bRefresh live holder checks"));
        if (page > 0) {
            inventory.setItem(48, createViewerButton("ARROW", "&ePrevious page"));
        }
        if (page + 1 < pageCount) {
            inventory.setItem(50, createViewerButton("ARROW", "&eNext page"));
        }
    }

    private ItemStack createViewerButton(String material, String name) {
        ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
        if (item != null) {
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(MessageService.color(name));
            item.setItemMeta(meta);
        }
        return item;
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
        if (ability.equals("beam") || ability.equals("all")) {
            player.getPersistentDataContainer().remove(beamCooldownKey);
        }
        startCooldownHud(player);
    }

    public void setCooldownDuration(String ability, long durationMillis) {
        plugin.getConfig().set("superweapon.cooldowns." + ability + "-millis", durationMillis);
        plugin.saveConfig();
        NamespacedKey key = switch (ability) {
            case "dash" -> dashCooldownKey;
            case "slam" -> slamCooldownKey;
            case "activate" -> activateCooldownKey;
            case "beam" -> beamCooldownKey;
            default -> throw new IllegalArgumentException("Unknown superweapon cooldown: " + ability);
        };
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            long expiresAt = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
            if (expiresAt <= now) {
                continue;
            }
            if (durationMillis <= 0) {
                player.getPersistentDataContainer().remove(key);
            } else {
                long updatedExpiry = durationMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + durationMillis;
                player.getPersistentDataContainer().set(key, PersistentDataType.LONG, updatedExpiry);
            }
            startCooldownHud(player);
        }
    }

    public boolean artifactTrackingEnabled() {
        return plugin.getConfig().getBoolean("superweapon.artifacts.track-owners", true);
    }

    public void registerOnlineArtifactOwnerships() {
        if (!artifactTrackingEnabled()) {
            return;
        }
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            changed |= refreshArtifactOwnership(player);
        }
        if (changed) {
            saveArtifactOwnerships();
        }
    }

    public Map<String, String> artifactOwnerSummary() {
        Map<String, Set<String>> owners = new LinkedHashMap<>();
        owners.put("boc", new LinkedHashSet<>());
        owners.put("sarculum", new LinkedHashSet<>());
        boolean changed = false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            changed |= refreshArtifactOwnership(player);
        }
        for (ArtifactOwnership ownership : artifactOwnerships.values()) {
            Set<String> artifactOwners = owners.get(ownership.artifact());
            if (artifactOwners != null) {
                ownership.profiles().stream().map(ArtifactOwnerProfile::ownerName)
                        .filter(name -> !name.isBlank()).forEach(artifactOwners::add);
            }
        }
        if (changed) {
            saveArtifactOwnerships();
        }
        Map<String, String> summary = new LinkedHashMap<>();
        owners.forEach((artifact, names) -> summary.put(artifact, String.join(", ", names)));
        return summary;
    }

    public List<ArtifactHistoryEntry> artifactHistory(String artifact) {
        boolean changed = false;
        Set<UUID> liveHolders = new LinkedHashSet<>();
        Set<UUID> liveCheckedOwners = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            changed |= refreshArtifactOwnership(player);
            liveCheckedOwners.add(player.getUniqueId());
            if (inventoryContainsArtifact(player.getInventory(), artifact)
                    || inventoryContainsArtifact(player.getEnderChest(), artifact)) {
                liveHolders.add(player.getUniqueId());
            }
            InventoryView view = player.getOpenInventory();
            Inventory top = view.getTopInventory();
            if (top != player.getInventory() && top != player.getEnderChest() && isCustomEnderChest(view)
                    && inventoryContainsArtifact(top, artifact)) {
                liveHolders.add(player.getUniqueId());
            }
        }
        if (changed) {
            saveArtifactOwnerships();
        }

        long now = System.currentTimeMillis();
        Map<UUID, ArtifactHistoryEntry> profiles = new LinkedHashMap<>();
        Set<UUID> recordedCurrentOwners = new LinkedHashSet<>();
        for (ArtifactOwnership ownership : artifactOwnerships.values()) {
            if (ownership.artifact().equals(artifact) && ownership.owner() != null) {
                recordedCurrentOwners.add(ownership.owner());
            }
        }
        for (ArtifactOwnership ownership : artifactOwnerships.values()) {
            if (!ownership.artifact().equals(artifact)) {
                continue;
            }
            for (ArtifactOwnerProfile ownerProfile : ownership.profiles()) {
                boolean current = liveHolders.contains(ownerProfile.owner());
                boolean liveChecked = liveCheckedOwners.contains(ownerProfile.owner());
                boolean recordedCurrent = recordedCurrentOwners.contains(ownerProfile.owner());
                long timestamp = ownerProfile.owner().equals(ownership.owner()) ? ownership.startedAt() : ownerProfile.lostAt();
                ArtifactHistoryEntry previous = profiles.get(ownerProfile.owner());
                if (previous == null) {
                    profiles.put(ownerProfile.owner(), new ArtifactHistoryEntry(ownerProfile.owner(),
                            ownerProfile.ownerName(), "", current, liveChecked, recordedCurrent, timestamp, ownerProfile.gotAt(),
                            ownerProfile.lostAt(), ownerProfile.lostCause()));
                    continue;
                }
                long gotAt = Math.min(previous.gotAt(), ownerProfile.gotAt());
                long lostAt = Math.max(previous.lostAt(), ownerProfile.lostAt());
                String lostCause = ownerProfile.lostAt() >= previous.lostAt()
                        ? ownerProfile.lostCause() : previous.lostCause();
                profiles.put(ownerProfile.owner(), new ArtifactHistoryEntry(ownerProfile.owner(),
                        ownerProfile.ownerName(), "", current, liveChecked, recordedCurrent,
                        Math.max(previous.timestamp(), timestamp), gotAt, lostAt, lostCause));
            }
        }
        List<ArtifactHistoryEntry> history = new java.util.ArrayList<>();
        for (ArtifactHistoryEntry profile : profiles.values()) {
            long end = profile.current() ? now : profile.lostAt() > 0 ? profile.lostAt() : profile.gotAt();
            history.add(new ArtifactHistoryEntry(profile.owner(), profile.ownerName(),
                    formatPossession(Math.max(0L, end - profile.gotAt())), profile.current(), profile.liveChecked(),
                    profile.recordedCurrent(), profile.timestamp(),
                    profile.gotAt(), profile.lostAt(), profile.lostCause()));
        }
        history.sort((first, second) -> {
            if (first.current() != second.current()) {
                return first.current() ? -1 : 1;
            }
            return Long.compare(second.timestamp(), first.timestamp());
        });
        return history;
    }

    private boolean inventoryContainsArtifact(Inventory inventory, String artifact) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (artifact.equals(artifact(inventory.getItem(slot)))) {
                return true;
            }
        }
        return false;
    }

    private String formatPossession(long durationMillis) {
        long seconds = Math.max(0L, durationMillis) / 1_000L;
        long days = seconds / 86_400L;
        long hours = seconds % 86_400L / 3_600L;
        long minutes = seconds % 3_600L / 60L;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m";
        return Math.max(1L, seconds) + "s";
    }

    private boolean artifactEnabled(String artifact) {
        return plugin.getConfig().getBoolean("superweapon.artifacts." + artifact + ".enabled", true);
    }

    private boolean refreshArtifactOwnership(Player player) {
        if (!artifactTrackingEnabled()) {
            return false;
        }
        boolean changed = scanArtifactInventory(player, player.getInventory());
        changed |= scanArtifactInventory(player, player.getEnderChest());

        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top != player.getInventory() && top != player.getEnderChest() && isCustomEnderChest(view)) {
            changed |= scanArtifactInventory(player, top);
        }
        return changed;
    }

    private boolean scanArtifactInventory(Player player, Inventory inventory) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (recordArtifactOwner(player, item)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }

    private boolean isCustomEnderChest(InventoryView view) {
        String title = view.getTitle().toLowerCase(Locale.ROOT);
        InventoryHolder holder = view.getTopInventory().getHolder();
        String holderType = holder == null ? "" : holder.getClass().getName().toLowerCase(Locale.ROOT);
        return title.contains("ender") || title.contains("vault") || title.contains("personal storage")
                || holderType.contains("ender") || holderType.contains("vault");
    }

    private boolean recordArtifactOwner(Player player, ItemStack item) {
        String type = artifact(item);
        if (type == null || !artifactTrackingEnabled()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean changed = ensureArtifactId(item);
        meta = item.getItemMeta();
        String storedId = meta.getPersistentDataContainer().get(artifactIdKey, PersistentDataType.STRING);
        UUID artifactId;
        try {
            artifactId = UUID.fromString(storedId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return changed;
        }
        ArtifactOwnership previous = artifactOwnerships.get(artifactId);
        long now = System.currentTimeMillis();
        List<ArtifactOwnerProfile> profiles = previous == null
                ? new java.util.ArrayList<>() : new java.util.ArrayList<>(previous.profiles());
        UUID playerId = player.getUniqueId();
        boolean alreadyOwned = previous != null && playerId.equals(previous.owner());
        if (previous != null && previous.owner() != null && !alreadyOwned) {
            upsertArtifactProfile(profiles, previous.owner(), previous.ownerName(), previous.startedAt(), now,
                    "Transferred to " + player.getName());
        }
        ArtifactOwnerProfile existingProfile = findArtifactProfile(profiles, playerId);
        long gotAt = existingProfile == null ? now : existingProfile.gotAt();
        upsertArtifactProfile(profiles, playerId, player.getName(), gotAt,
                existingProfile == null ? 0L : existingProfile.lostAt(),
                existingProfile == null ? "" : existingProfile.lostCause());
        ArtifactOwnership ownership = new ArtifactOwnership(type, playerId, player.getName(),
                alreadyOwned ? previous.startedAt() : now, profiles);
        if (!ownership.equals(previous)) {
            artifactOwnerships.put(artifactId, ownership);
            changed = true;
        }
        return changed;
    }

    private boolean ensureArtifactId(ItemStack item) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String storedId = meta.getPersistentDataContainer().get(artifactIdKey, PersistentDataType.STRING);
        try {
            UUID.fromString(storedId);
            return false;
        } catch (IllegalArgumentException | NullPointerException exception) {
            meta.getPersistentDataContainer().set(artifactIdKey, PersistentDataType.STRING,
                    UUID.randomUUID().toString());
            item.setItemMeta(meta);
            return true;
        }
    }

    private boolean loseArtifactOwner(Player player, ItemStack item, String cause) {
        if (artifact(item) == null || !artifactTrackingEnabled()) {
            return false;
        }
        recordArtifactOwner(player, item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String storedId = meta.getPersistentDataContainer().get(artifactIdKey, PersistentDataType.STRING);
        UUID artifactId;
        try {
            artifactId = UUID.fromString(storedId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
        ArtifactOwnership ownership = artifactOwnerships.get(artifactId);
        if (ownership == null || !player.getUniqueId().equals(ownership.owner())) {
            return false;
        }
        List<ArtifactOwnerProfile> profiles = new java.util.ArrayList<>(ownership.profiles());
        upsertArtifactProfile(profiles, player.getUniqueId(), player.getName(), ownership.startedAt(),
                System.currentTimeMillis(), cause);
        artifactOwnerships.put(artifactId, new ArtifactOwnership(ownership.artifact(), null, "", 0L, profiles));
        return true;
    }

    private ArtifactOwnerProfile findArtifactProfile(List<ArtifactOwnerProfile> profiles, UUID owner) {
        return profiles.stream().filter(profile -> profile.owner().equals(owner)).findFirst().orElse(null);
    }

    private void upsertArtifactProfile(List<ArtifactOwnerProfile> profiles, UUID owner, String ownerName,
                                       long gotAt, long lostAt, String lostCause) {
        ArtifactOwnerProfile existing = findArtifactProfile(profiles, owner);
        if (existing == null) {
            profiles.add(new ArtifactOwnerProfile(owner, ownerName, gotAt, lostAt, lostCause));
            return;
        }
        int index = profiles.indexOf(existing);
        long earliestGotAt = Math.min(existing.gotAt(), gotAt);
        if (lostAt < existing.lostAt()) {
            lostAt = existing.lostAt();
            lostCause = existing.lostCause();
        }
        profiles.set(index, new ArtifactOwnerProfile(owner, ownerName, earliestGotAt, lostAt, lostCause));
    }

    private String formatArtifactDate(long timestamp) {
        return ARTIFACT_DATE_FORMAT.format(Instant.ofEpochMilli(timestamp));
    }

    private void loadArtifactOwnerships() {
        if (!artifactOwnershipFile.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(artifactOwnershipFile);
        ConfigurationSection entries = data.getConfigurationSection("artifacts");
        if (entries == null) {
            return;
        }
        for (String id : entries.getKeys(false)) {
            try {
                ConfigurationSection entry = entries.getConfigurationSection(id);
                if (entry == null) {
                    continue;
                }
                String type = entry.getString("type", "");
                String owner = entry.getString("owner", "");
                String ownerName = entry.getString("owner-name", "");
                if (!List.of("boc", "sarculum").contains(type)) {
                    continue;
                }
                long startedAt = entry.getLong("started-at", System.currentTimeMillis());
                List<ArtifactOwnerProfile> profiles = new java.util.ArrayList<>();
                ConfigurationSection profileEntries = entry.getConfigurationSection("profiles");
                if (profileEntries != null) {
                    for (String profileId : profileEntries.getKeys(false)) {
                        ConfigurationSection profile = profileEntries.getConfigurationSection(profileId);
                        if (profile == null) continue;
                        try {
                            upsertArtifactProfile(profiles, UUID.fromString(profile.getString("owner", "")),
                                    profile.getString("owner-name", ""), profile.getLong("got-at"),
                                    profile.getLong("lost-at"), profile.getString("lost-cause", ""));
                        } catch (IllegalArgumentException exception) {
                            plugin.getLogger().warning("Ignoring invalid artifact profile: " + id + ".profiles." + profileId);
                        }
                    }
                } else {
                    ConfigurationSection pastEntries = entry.getConfigurationSection("history");
                    if (pastEntries != null) {
                        for (String pastId : pastEntries.getKeys(false)) {
                            ConfigurationSection past = pastEntries.getConfigurationSection(pastId);
                            if (past == null) continue;
                            try {
                                upsertArtifactProfile(profiles, UUID.fromString(past.getString("owner", "")),
                                        past.getString("owner-name", ""), past.getLong("started-at"),
                                        past.getLong("ended-at"), "Cause unknown (legacy record)");
                            } catch (IllegalArgumentException exception) {
                                plugin.getLogger().warning("Ignoring invalid artifact history entry: " + id + ".history." + pastId);
                            }
                        }
                    }
                }
                UUID currentOwner = null;
                if (!owner.isBlank()) {
                    currentOwner = UUID.fromString(owner);
                    if (!ownerName.isBlank()) {
                        upsertArtifactProfile(profiles, currentOwner, ownerName, startedAt, 0L, "");
                    }
                }
                if (profiles.isEmpty()) {
                    continue;
                }
                artifactOwnerships.put(UUID.fromString(id), new ArtifactOwnership(type, currentOwner,
                        currentOwner == null ? "" : ownerName, currentOwner == null ? 0L : startedAt, profiles));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid artifact ownership entry: " + id);
            }
        }
    }

    private void saveArtifactOwnerships() {
        YamlConfiguration data = new YamlConfiguration();
        artifactOwnerships.forEach((id, ownership) -> {
            String path = "artifacts." + id;
            data.set(path + ".type", ownership.artifact());
            data.set(path + ".owner", ownership.owner() == null ? null : ownership.owner().toString());
            data.set(path + ".owner-name", ownership.ownerName());
            data.set(path + ".started-at", ownership.startedAt());
            for (int i = 0; i < ownership.profiles().size(); i++) {
                ArtifactOwnerProfile profile = ownership.profiles().get(i);
                String profilePath = path + ".profiles." + i;
                data.set(profilePath + ".owner", profile.owner().toString());
                data.set(profilePath + ".owner-name", profile.ownerName());
                data.set(profilePath + ".got-at", profile.gotAt());
                data.set(profilePath + ".lost-at", profile.lostAt());
                data.set(profilePath + ".lost-cause", profile.lostCause());
            }
        });
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            data.save(artifactOwnershipFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save artifact ownership data: " + exception.getMessage());
        }
    }

    public record ArtifactHistoryEntry(UUID owner, String ownerName, String duration, boolean current,
                                       boolean liveChecked, boolean recordedCurrent, long timestamp,
                                       long gotAt, long lostAt, String lostCause) {
    }

    private record ArtifactOwnerProfile(UUID owner, String ownerName, long gotAt, long lostAt, String lostCause) {
    }

    private record ArtifactOwnership(String artifact, UUID owner, String ownerName, long startedAt,
                                    List<ArtifactOwnerProfile> profiles) {
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
                MessageService.color("&7Left-click for a 45-block beam and 3 shockwaves; 10-minute cooldown."),
                MessageService.color("&7Beam: 5 damage (2.5 hearts) per hit."),
                MessageService.color("&7Shockwaves: up to 16 damage (8 hearts) at 2 blocks, falling to 6 damage (3 hearts) at 12 blocks."),
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
                MessageService.color("&7Sneak + left-click: faster, freely steerable dash up to 25 blocks; 2-minute cooldown."),
                MessageService.color("&7Dash: 16.5 damage (8.25 hearts) in a 4-block radius along the path."),
                MessageService.color("&7Sneak (Shift) + right-click: slam; 5-minute cooldown."),
                MessageService.color("&7Slam: launch up to 8 blocks for one impact."),
                MessageService.color("&7Airborne with a mace in your hotbar: launch up to 12 blocks for two impacts."),
                MessageService.color("&7Each slam hit: 24 damage (12 hearts), with no distance falloff."),
                MessageService.color("&7Airborne with a mace in your hotbar: 36 damage (18 hearts) per hit in a 4x radius."),
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
        String artifactType = slot == 11 ? "boc" : "sarculum";
        if (!artifactEnabled(artifactType)) {
            messages.send(player, "sw-artifact-disabled");
            return;
        }
        if (item == null) {
            return;
        }
        ensureArtifactId(item);
        if (!player.getInventory().addItem(item).isEmpty()) {
            messages.send(player, "sw-inventory-full");
            return;
        }
        if (recordArtifactOwner(player, item)) {
            saveArtifactOwnerships();
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
    public void onArtifactViewerClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof ArtifactOverviewHolder) && !(holder instanceof ArtifactHistoryHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != top
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }

        int slot = event.getRawSlot();
        if (holder instanceof ArtifactOverviewHolder) {
            String selected = artifact(event.getCurrentItem());
            if (slot == 11 && "boc".equals(selected)) {
                openArtifactHistory(player, "boc");
            } else if (slot == 15 && "sarculum".equals(selected)) {
                openArtifactHistory(player, "sarculum");
            }
            return;
        }

        ArtifactHistoryHolder historyHolder = (ArtifactHistoryHolder) holder;
        if (slot == 45) {
            openArtifactViewer(player);
        } else if (slot == 48 && historyHolder.page() > 0) {
            openArtifactHistory(player, historyHolder.artifact(), historyHolder.page() - 1);
        } else if (slot == 49) {
            openArtifactHistory(player, historyHolder.artifact(), historyHolder.page());
        } else if (slot == 50) {
            openArtifactHistory(player, historyHolder.artifact(), historyHolder.page() + 1);
        }
    }

    @EventHandler
    public void onArtifactViewerDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof ArtifactOverviewHolder || holder instanceof ArtifactHistoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArtifactInventoryClick(InventoryClickEvent event) {
        if (!artifactTrackingEnabled() || !(event.getWhoClicked() instanceof Player player)
                || (artifact(event.getCurrentItem()) == null && artifact(event.getCursor()) == null)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && refreshArtifactOwnership(player)) {
                saveArtifactOwnerships();
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onArtifactInventoryDrag(InventoryDragEvent event) {
        if (!artifactTrackingEnabled() || !(event.getWhoClicked() instanceof Player player)
                || artifact(event.getOldCursor()) == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && refreshArtifactOwnership(player)) {
                saveArtifactOwnerships();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArtifactDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (loseArtifactOwner(event.getPlayer(), item, "Dropped by " + event.getPlayer().getName())) {
            event.getItemDrop().setItemStack(item);
            saveArtifactOwnerships();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArtifactPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (recordArtifactOwner(player, item)) {
            event.getItem().setItemStack(item);
            saveArtifactOwnerships();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArtifactItemSpawn(ItemSpawnEvent event) {
        if (artifact(event.getEntity().getItemStack()) != null) {
            event.getEntity().setUnlimitedLifetime(true);
        }
    }

    @EventHandler
    public void onArtifactItemDespawn(ItemDespawnEvent event) {
        if (artifact(event.getEntity().getItemStack()) != null) {
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
        if (!artifactEnabled(artifact)) {
            event.setCancelled(true);
            messages.send(player, "sw-artifact-disabled");
            return;
        }
        if (recordArtifactOwner(player, event.getItem())) {
            saveArtifactOwnerships();
        }
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
        boolean leftClick = event.getAction() == Action.LEFT_CLICK_AIR
                || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (leftClick) {
            event.setCancelled(true);
            if (!activeActions.containsKey(player.getUniqueId()) && !player.isInsideVehicle()) {
                beam(player);
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
        startBocActivationEffect(player);
    }

    private void startBocActivationEffect(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask previous = bocActivationEffects.remove(uuid);
        if (previous != null) {
            previous.cancel();
        }
        BukkitTask task = new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()
                        || (ticks % 5 == 0 && !hasBocActivationEffect(player))) {
                    bocActivationEffects.remove(uuid);
                    cancel();
                    return;
                }
                Location center = player.getLocation();
                double rotation = ticks * Math.PI / 16;
                XParticle.of("END_ROD").ifPresent(particle -> {
                    for (int point = 0; point < 12; point++) {
                        double angle = rotation + Math.PI * 2 * point / 6;
                        Location sparkle = center.clone().add(Math.cos(angle) * 0.9,
                                0.2 + (point % 6) * 0.28 + Math.sin(rotation + point) * 0.08,
                                Math.sin(angle) * 0.75);
                        player.getWorld().spawnParticle(particle.get(), sparkle, 1, 0, 0, 0, 0);
                    }
                });
                XParticle.of("SCULK_SOUL").ifPresent(particle -> {
                    for (int point = 0; point < 6; point++) {
                        double angle = -rotation * 1.4 + Math.PI * 2 * point / 6;
                        Location soul = center.clone().add(Math.cos(angle) * 0.5,
                                0.55 + point * 0.2 + Math.sin(rotation + point) * 0.1,
                                Math.sin(angle) * 0.48);
                        player.getWorld().spawnParticle(particle.get(), soul, 1, 0, 0, 0, 0);
                    }
                });
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        bocActivationEffects.put(uuid, task);
    }

    private boolean hasBocActivationEffect(Player player) {
        return hasPotionEffect(player, "STRENGTH") || hasPotionEffect(player, "REGENERATION")
                || hasPotionEffect(player, "SLOWNESS") || hasPotionEffect(player, "HUNGER");
    }

    private boolean hasPotionEffect(Player player, String potionName) {
        return XPotion.matchXPotion(potionName)
                .map(potion -> player.hasPotionEffect(potion.buildPotionEffect(1, 0).getType()))
                .orElse(false);
    }

    private void stopBocActivationEffect(Player player) {
        BukkitTask task = bocActivationEffects.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
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
                appendCooldown(cooldowns, "Beam", player.getPersistentDataContainer()
                        .getOrDefault(beamCooldownKey, PersistentDataType.LONG, 0L) - now);
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
        long totalSeconds = remainingMillis / 1000 + (remainingMillis % 1000 == 0 ? 0 : 1);
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

    private void beam(Player player) {
        if (!startCooldown(player, beamCooldownKey, cooldownDurationMillis("beam", 10 * 60 * 1000L))) {
            return;
        }
        Set<UUID> hitTargets = new HashSet<>();
        BukkitTask task = new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    stopAction(player);
                    return;
                }
                fireBeam(player, hitTargets);
                ticks++;
                if (ticks >= 100) {
                    stopAction(player);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && !player.isDead()) {
                            shockwave(player);
                        }
                    }, 1L);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        activeActions.put(player.getUniqueId(), task);
        XSound.matchXSound("ENTITY_WARDEN_SONIC_CHARGE").ifPresent(sound -> sound.play(player));
    }

    private void fireBeam(Player attacker, Set<UUID> hit) {
        Location origin = attacker.getEyeLocation();
        Vector direction = origin.getDirection();
        if (direction.lengthSquared() < 0.001) {
            return;
        }
        direction.normalize();
        World world = attacker.getWorld();
        List<Entity> candidates = new java.util.ArrayList<>(world.getNearbyEntities(origin, 45, 45, 45));
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.001) {
            right = direction.clone().crossProduct(new Vector(1, 0, 0));
        }
        right.normalize();
        Vector up = right.clone().crossProduct(direction).normalize();
        XParticle soulParticle = XParticle.of("SCULK_SOUL").orElse(null);
        XParticle endRodParticle = XParticle.of("END_ROD").orElse(null);
        XParticle sonicParticle = XParticle.of("SONIC_BOOM").orElse(null);
        XParticle soulFireParticle = XParticle.of("SOUL_FIRE_FLAME").orElse(null);
        for (double distance = 0.25; distance <= 45; distance += 0.25) {
            Location point = origin.clone().add(direction.clone().multiply(distance));
            if (soulParticle != null) {
                world.spawnParticle(soulParticle.get(), point, 3, 0.04, 0.04, 0.04, 0.01);
            }
            if (endRodParticle != null && ((int) (distance * 4)) % 2 == 0) {
                world.spawnParticle(endRodParticle.get(), point, 1, 0.02, 0.02, 0.02, 0);
            }
            double spiralAngle = distance * 2.4;
            for (int strand = 0; strand < 2; strand++) {
                double angle = spiralAngle + strand * Math.PI;
                Location spiral = point.clone()
                        .add(right.clone().multiply(Math.cos(angle) * 0.28))
                        .add(up.clone().multiply(Math.sin(angle) * 0.28));
                if (soulFireParticle != null) {
                    world.spawnParticle(soulFireParticle.get(), spiral, 1);
                } else if (endRodParticle != null) {
                    world.spawnParticle(endRodParticle.get(), spiral, 1);
                }
            }
            if (sonicParticle != null && Math.abs(distance / 6.0 - Math.rint(distance / 6.0)) < 0.001) {
                world.spawnParticle(sonicParticle.get(), point, 1);
            }
        }
        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity target) || target.equals(attacker) || target.isDead()
                    || !target.isValid()
                    || (target instanceof Player targetPlayer
                    && (!world.getPVP() || targetPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR))
                    || !hit.add(target.getUniqueId())
                    || !beamIntersects(target.getBoundingBox(), origin, direction)) {
                continue;
            }
            target.setNoDamageTicks(0);
            altar.damageFromAbility(target, attacker, 5.0);
        }
    }

    private boolean beamIntersects(BoundingBox bounds, Location origin, Vector direction) {
        double minX = bounds.getMinX() - 1.0;
        double minY = bounds.getMinY() - 1.0;
        double minZ = bounds.getMinZ() - 1.0;
        double maxX = bounds.getMaxX() + 1.0;
        double maxY = bounds.getMaxY() + 1.0;
        double maxZ = bounds.getMaxZ() + 1.0;
        for (double distance = 0; distance <= 45; distance += 0.1) {
            double x = origin.getX() + direction.getX() * distance;
            double y = origin.getY() + direction.getY() * distance;
            double z = origin.getZ() + direction.getZ() * distance;
            if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) {
                return true;
            }
        }
        return false;
    }

    private void shockwave(Player attacker) {
        Location center = attacker.getLocation().clone().add(0, 0.5, 0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        new BukkitRunnable() {
            private int ticks;
            private final XParticle soulParticle = XParticle.of("SCULK_SOUL").orElse(null);
            private final XParticle endRodParticle = XParticle.of("END_ROD").orElse(null);
            private final XParticle soulFireParticle = XParticle.of("SOUL_FIRE_FLAME").orElse(null);

            @Override
            public void run() {
                if (!attacker.isOnline() || attacker.isDead() || attacker.getWorld() != world) {
                    cancel();
                    return;
                }
                if (ticks % 10 == 0 && ticks < 30) {
                    damageShockwave(attacker, center, world);
                    XSound.matchXSound("ENTITY_WARDEN_SONIC_BOOM").ifPresent(sound -> sound.play(attacker));
                    XParticle.of("SONIC_BOOM").ifPresent(particle -> world.spawnParticle(particle.get(), center, 1));
                }
                int waveTick = ticks % 30;
                double radius = 40.0 * (waveTick + 1) / 30.0;
                for (int point = 0; point < 144; point++) {
                    double angle = Math.PI * 2 * point / 144;
                    double ripple = Math.sin(angle * 6 + ticks * 0.55) * 0.45;
                    for (int layer = 0; layer < 3; layer++) {
                        double layerRadius = Math.max(0, radius - layer * 0.16);
                        double height = layer * 0.38 + ripple;
                        Location location = center.clone().add(Math.cos(angle) * layerRadius, height,
                                Math.sin(angle) * layerRadius);
                        if (soulParticle != null) {
                            world.spawnParticle(soulParticle.get(), location, 1, 0, 0, 0, 0);
                        }
                        if (point % 4 == 0 && endRodParticle != null) {
                            world.spawnParticle(endRodParticle.get(), location, 1, 0, 0, 0, 0);
                        }
                        if (point % 12 == 0 && layer == 2 && soulFireParticle != null) {
                            world.spawnParticle(soulFireParticle.get(), location, 1, 0, 0, 0, 0);
                        }
                    }
                }
                ticks++;
                if (ticks >= 100) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void damageShockwave(Player attacker, Location center, World world) {
        for (Entity entity : world.getNearbyEntities(center, 12, 12, 12)) {
            if (!(entity instanceof LivingEntity target) || target.equals(attacker)
                    || target.isDead() || !target.isValid()) {
                continue;
            }
            if (target instanceof Player targetPlayer
                    && (!world.getPVP() || targetPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR)) {
                continue;
            }
            BoundingBox bounds = target.getBoundingBox();
            double closestX = Math.max(bounds.getMinX(), Math.min(bounds.getMaxX(), center.getX()));
            double closestY = Math.max(bounds.getMinY(), Math.min(bounds.getMaxY(), center.getY()));
            double closestZ = Math.max(bounds.getMinZ(), Math.min(bounds.getMaxZ(), center.getZ()));
            double xDistance = closestX - center.getX();
            double yDistance = closestY - center.getY();
            double zDistance = closestZ - center.getZ();
            double distance = Math.sqrt(xDistance * xDistance + yDistance * yDistance + zDistance * zDistance);
            if (distance > 12) {
                continue;
            }
            double damage = distance <= 2 ? 16 : 18 - distance;
            target.setNoDamageTicks(0);
            if (target instanceof Player targetPlayer) {
                altar.damageFromAbility(targetPlayer, attacker, damage);
            } else {
                target.damage(damage, attacker);
            }
            XPotion.matchXPotion("SLOWNESS")
                    .map(potion -> potion.buildPotionEffect(60, 1))
                    .ifPresent(target::addPotionEffect);
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
                player.setVelocity(player.getVelocity().multiply(0.65).add(dashDirection.clone().multiply(0.8)));
                strikeNearby(player, current, 4, false, false, hit);
                XParticle.of("SWEEP_ATTACK").ifPresent(particle -> player.getWorld().spawnParticle(
                        particle.get(), current.clone().add(0, 1, 0), 12, 0.25, 0.3, 0.25, 0.01));
                XParticle.of("CLOUD").ifPresent(particle -> player.getWorld().spawnParticle(
                        particle.get(), current.clone().add(0, 0.8, 0), 20, 0.25, 0.2, 0.25, 0.04));
                XParticle.of("CRIT").ifPresent(particle -> player.getWorld().spawnParticle(
                        particle.get(), current.clone().add(0, 1, 0), 12, 0.3, 0.35, 0.3, 0.15));
                XParticle.of("SOUL_FIRE_FLAME").ifPresent(particle -> {
                    for (int point = 0; point < 4; point++) {
                        Location trail = current.clone()
                                .subtract(dashDirection.clone().multiply(point * 0.4)).add(0, 0.9, 0);
                        player.getWorld().spawnParticle(particle.get(), trail, 2, 0.08, 0.08, 0.08, 0.01);
                    }
                });
                if (distanceTravelled >= 25 || ticks >= 80) {
                    stopAction(player);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        activeActions.put(player.getUniqueId(), task);
        XSound.matchXSound("ENTITY_PLAYER_ATTACK_SWEEP").ifPresent(sound -> sound.play(player));
        XSound.matchXSound("ENTITY_ENDER_DRAGON_FLAP").ifPresent(sound -> sound.play(player));
    }

    private void slam(Player player) {
        boolean aerialSlam = !player.isOnGround() && hasMaceInHotbar(player);
        if (!startCooldown(player, slamCooldownKey, cooldownDurationMillis("slam", 5 * 60 * 1000L))) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Location start = player.getLocation();
        long fallProtectionExpiry = System.currentTimeMillis() + 30000;
        fallProtection.put(uuid, fallProtectionExpiry);
        Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtection.remove(uuid, fallProtectionExpiry), 600L);
        player.setFallDistance(0);
        player.setVelocity(new Vector(0, aerialSlam ? 1.65 : 1.35, 0));
        if (!aerialSlam) {
            XSound.matchXSound("ENTITY_FIREWORK_ROCKET_LAUNCH").ifPresent(sound -> sound.play(player));
        }
        BukkitTask task = new BukkitRunnable() {
            private int ticks;
            private int phaseTicks;
            private int impacts;
            private double ascentStartY = start.getY();
            private boolean airborne = aerialSlam;
            private boolean dropping;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || player.getWorld() != start.getWorld()) {
                    stopAction(player);
                    fallProtection.remove(uuid);
                    return;
                }
                if (aerialSlam && !hasMaceInHotbar(player)) {
                    stopAction(player);
                    if (player.isOnGround()) {
                        fallProtection.remove(uuid);
                    }
                    return;
                }
                ticks++;
                phaseTicks++;
                Location current = player.getLocation();
                if (!player.isOnGround()) {
                    airborne = true;
                }
                if (airborne) {
                    if (aerialSlam) {
                        XParticle.of("SCULK_SOUL").ifPresent(particle -> player.getWorld().spawnParticle(
                                particle.get(), current.clone().add(0, 0.4, 0), 24, 0.45, 0.4, 0.45, 0.08));
                        XParticle.of("SOUL_FIRE_FLAME").ifPresent(particle -> player.getWorld().spawnParticle(
                                particle.get(), current.clone().add(0, 0.2, 0), 12, 0.3, 0.35, 0.3, 0.02));
                        XParticle.of("SMOKE").ifPresent(particle -> player.getWorld().spawnParticle(
                                particle.get(), current.clone().add(0, 0.5, 0), 18, 0.35, 0.4, 0.35, 0.06));
                    } else {
                        XParticle.of("CLOUD").ifPresent(particle -> player.getWorld().spawnParticle(
                                particle.get(), current.clone().add(0, 0.5, 0), 24, 0.25, 0.25, 0.25, 0.04));
                        XParticle.of("CRIT").ifPresent(particle -> player.getWorld().spawnParticle(
                                particle.get(), current.clone().add(0, 0.4, 0), 16, 0.18, 0.18, 0.18, 0.1));
                        XParticle.of("SOUL_FIRE_FLAME").ifPresent(particle -> {
                            for (int point = 0; point < 8; point++) {
                                double angle = ticks * 0.38 + Math.PI * 2 * point / 8;
                                Location orbit = current.clone().add(Math.cos(angle) * 0.65, 0.25,
                                        Math.sin(angle) * 0.65);
                                player.getWorld().spawnParticle(particle.get(), orbit, 1, 0, 0, 0, 0);
                            }
                        });
                    }
                }
                if (player.isOnGround() && airborne) {
                    strikeNearby(player, current, aerialSlam ? 32 : 8, true, aerialSlam, new HashSet<>());
                    XSound.matchXSound("ENTITY_GENERIC_EXPLODE").ifPresent(sound -> sound.play(player));
                    XSound.matchXSound("ENTITY_WARDEN_SONIC_BOOM").ifPresent(sound -> sound.play(player));
                    playSlamImpact(current, aerialSlam);
                    player.setFallDistance(0);
                    impacts++;
                    if (impacts < (aerialSlam ? 2 : 1)) {
                        airborne = false;
                        dropping = false;
                        ascentStartY = current.getY();
                        phaseTicks = 0;
                        player.setVelocity(new Vector(0, 0.72, 0));
                        XSound.matchXSound("ENTITY_FIREWORK_ROCKET_LAUNCH").ifPresent(sound -> sound.play(player));
                        return;
                    }
                    stopAction(player);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtection.remove(uuid), 2L);
                    return;
                }
                if (!airborne && phaseTicks >= 5) {
                    if (aerialSlam && impacts == 1 && player.isOnGround()) {
                        strikeNearby(player, current, aerialSlam ? 32 : 8, true, aerialSlam, new HashSet<>());
                        XSound.matchXSound("ENTITY_GENERIC_EXPLODE").ifPresent(sound -> sound.play(player));
                        XSound.matchXSound("ENTITY_WARDEN_SONIC_BOOM").ifPresent(sound -> sound.play(player));
                        playSlamImpact(current, aerialSlam);
                        player.setFallDistance(0);
                        stopAction(player);
                        Bukkit.getScheduler().runTaskLater(plugin, () -> fallProtection.remove(uuid), 2L);
                    } else {
                        stopAction(player);
                        fallProtection.remove(uuid);
                    }
                    return;
                }
                if (ticks >= 200) {
                    stopAction(player);
                    fallProtection.remove(uuid);
                    return;
                }
                double ascentHeight = impacts == 0 ? (aerialSlam ? 12 : 8) : 2;
                double ascentTargetY = ascentStartY + ascentHeight;
                if (!dropping && (current.getY() >= ascentTargetY || phaseTicks >= 60
                        || (airborne && player.getVelocity().getY() <= 0))) {
                    if (current.getY() > ascentTargetY) {
                        Location peak = current.clone();
                        peak.setY(ascentTargetY);
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

    private void playSlamImpact(Location impact, boolean aerialSlam) {
        Location center = impact.clone().add(0, 0.15, 0);
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        XParticle.of("EXPLOSION").ifPresent(particle -> world.spawnParticle(
                particle.get(), center.clone().add(0, 0.8, 0), 1));
        XParticle.of("SONIC_BOOM").ifPresent(particle -> world.spawnParticle(
                particle.get(), center.clone().add(0, 0.8, 0), 1));
        if (aerialSlam) {
            XParticle.of("SCULK_SOUL").ifPresent(particle -> world.spawnParticle(
                    particle.get(), center, 360, 3.6, 1.4, 3.6, 0.24));
            XParticle.of("SOUL_FIRE_FLAME").ifPresent(particle -> world.spawnParticle(
                    particle.get(), center, 240, 2.8, 1, 2.8, 0.07));
            XParticle.of("SMOKE").ifPresent(particle -> world.spawnParticle(
                    particle.get(), center, 280, 3.2, 1.2, 3.2, 0.16));
        } else {
            XParticle.of("CLOUD").ifPresent(particle -> world.spawnParticle(
                    particle.get(), center, 140, 1.4, 0.3, 1.4, 0.14));
            XParticle.of("CRIT").ifPresent(particle -> world.spawnParticle(
                    particle.get(), center.clone().add(0, 0.6, 0), 90, 1.2, 0.8, 1.2, 0.2));
        }
        XParticle.of(aerialSlam ? "SCULK_SOUL" : "END_ROD").ifPresent(particle -> {
            for (int level = 0; level < 7; level++) {
                double angle = level * Math.PI / 3;
                Location plume = center.clone().add(Math.cos(angle) * 0.18, 0.2 + level * 0.38,
                        Math.sin(angle) * 0.18);
                world.spawnParticle(particle.get(), plume, aerialSlam ? 3 : 2, 0.08, 0.12, 0.08, 0.02);
            }
        });
        XParticle soulFire = XParticle.of("SOUL_FIRE_FLAME").orElse(null);
        XParticle endRod = XParticle.of("END_ROD").orElse(null);
        XParticle cloud = XParticle.of("CLOUD").orElse(null);
        XParticle sculkSoul = XParticle.of("SCULK_SOUL").orElse(null);
        XParticle smoke = XParticle.of("SMOKE").orElse(null);
        new BukkitRunnable() {
            private int wave;

            @Override
            public void run() {
                int waveCount = aerialSlam ? 40 : 24;
                if (wave >= waveCount || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
                    cancel();
                    return;
                }
                double radius = aerialSlam ? (wave + 1) * (40.0 / waveCount) : (wave + 1) * (8.0 / waveCount);
                double innerRadius = radius * 0.72;
                double height = wave % 2 == 0 ? 0.08 : 0.45;
                int pointCount = aerialSlam ? 84 : 48;
                for (int point = 0; point < pointCount; point++) {
                    double angle = Math.PI * 2 * point / pointCount + wave * 0.12;
                    Location ringPoint = center.clone().add(Math.cos(angle) * radius, height,
                            Math.sin(angle) * radius);
                    if (aerialSlam) {
                        if (sculkSoul != null) {
                            world.spawnParticle(sculkSoul.get(), ringPoint, 1, 0, 0, 0, 0);
                        }
                        if (soulFire != null && point % 2 == 0) {
                            Location firePoint = center.clone().add(Math.cos(angle) * innerRadius,
                                    0.16, Math.sin(angle) * innerRadius);
                            world.spawnParticle(soulFire.get(), firePoint, 1, 0, 0, 0, 0);
                        }
                        if (smoke != null && point % 4 == 0) {
                            Location smokePoint = center.clone().add(Math.cos(angle) * Math.min(44, radius * 1.08),
                                    0.3, Math.sin(angle) * Math.min(44, radius * 1.08));
                            world.spawnParticle(smoke.get(), smokePoint, 1, 0, 0, 0, 0);
                        }
                        if (point % 14 == 0 && wave % 3 == 0) {
                            for (int strand = 0; strand < 2; strand++) {
                                double spiralAngle = angle + wave * 0.3 + strand * Math.PI;
                                double spiralRadius = Math.max(0.35, radius * 0.4);
                                for (int level = 0; level < 4; level++) {
                                    Location spiral = center.clone().add(Math.cos(spiralAngle) * spiralRadius,
                                            0.35 + (wave % 16) * 0.24 + level * 0.18,
                                            Math.sin(spiralAngle) * spiralRadius);
                                    if (sculkSoul != null) {
                                        world.spawnParticle(sculkSoul.get(), spiral, 1, 0, 0, 0, 0);
                                    }
                                    if (soulFire != null) {
                                        world.spawnParticle(soulFire.get(), spiral, 1, 0, 0, 0, 0);
                                    }
                                }
                            }
                        }
                    } else {
                        if (soulFire != null) {
                            world.spawnParticle(soulFire.get(), ringPoint, 1, 0, 0, 0, 0);
                        }
                        if (endRod != null && point % 2 == 0) {
                            world.spawnParticle(endRod.get(), ringPoint.clone().add(0, 0.2, 0), 1, 0, 0, 0, 0);
                        }
                        if (cloud != null && point % 3 == 0) {
                            Location innerPoint = center.clone().add(Math.cos(angle) * innerRadius, 0.18,
                                    Math.sin(angle) * innerRadius);
                            world.spawnParticle(cloud.get(), innerPoint, 1, 0, 0, 0, 0);
                        }
                    }
                }
                wave++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private boolean hasMaceInHotbar(Player player) {
        XMaterial mace = XMaterial.matchXMaterial("MACE").orElse(null);
        if (mace == null) {
            return false;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item != null && mace.equals(XMaterial.matchXMaterial(item.getType()))) {
                return true;
            }
        }
        return false;
    }

    private void strikeNearby(Player attacker, Location center, double radius, boolean slam, boolean aerialSlam,
                              Set<UUID> hit) {
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
            double damage = aerialSlam ? 36.0 : slam ? 24.0 : 16.5;
            target.setNoDamageTicks(0);
            double healthBefore = target.getHealth();
            double absorptionBefore = target.getAbsorptionAmount();
            if (target instanceof Player player) {
                altar.damageFromAbility(player, attacker, damage);
            } else {
                target.damage(damage, attacker);
            }
            if (target.isDead() || !target.isValid()) {
                continue;
            }
            double healthLost = Math.max(0, healthBefore - target.getHealth());
            double absorptionLost = Math.max(0, absorptionBefore - target.getAbsorptionAmount());
            double remainingDamage = damage - healthLost - absorptionLost;
            if (remainingDamage > 0) {
                double absorption = target.getAbsorptionAmount();
                double absorbedDamage = Math.min(absorption, remainingDamage);
                target.setAbsorptionAmount(absorption - absorbedDamage);
                remainingDamage -= absorbedDamage;
                if (remainingDamage > 0) {
                    target.setHealth(Math.max(0, target.getHealth() - remainingDamage));
                }
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
        if (refreshArtifactOwnership(player)) {
            saveArtifactOwnerships();
        }
        long now = System.currentTimeMillis();
        if (player.getPersistentDataContainer().getOrDefault(dashCooldownKey, PersistentDataType.LONG, 0L) > now
                || player.getPersistentDataContainer().getOrDefault(slamCooldownKey, PersistentDataType.LONG, 0L) > now
                || player.getPersistentDataContainer().getOrDefault(activateCooldownKey, PersistentDataType.LONG, 0L) > now
                || player.getPersistentDataContainer().getOrDefault(beamCooldownKey, PersistentDataType.LONG, 0L) > now) {
            startCooldownHud(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        stopAction(player);
        stopBocActivationEffect(player);
        fallProtection.remove(event.getPlayer().getUniqueId());
        BukkitTask cooldownTask = cooldownHudTasks.remove(player.getUniqueId());
        if (cooldownTask != null) {
            cooldownTask.cancel();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent damage = player.getLastDamageCause();
        String cause = player.getKiller() != null ? "Killed by " + player.getKiller().getName()
                : damage == null ? "Death" : "Death: " + damage.getCause().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        boolean ownershipChanged = false;
        for (ItemStack item : event.getDrops()) {
            if (artifact(item) != null) {
                ownershipChanged |= loseArtifactOwner(player, item, cause);
            }
        }
        if (ownershipChanged) {
            saveArtifactOwnerships();
        }
        stopAction(player);
        stopBocActivationEffect(player);
        fallProtection.remove(player.getUniqueId());
    }

    private static final class MenuHolder implements InventoryHolder {
        @Getter
        private final Inventory inventory = Bukkit.createInventory(this, 27, MessageService.color("&5Superweapons"));
    }

    private static final class ArtifactOverviewHolder implements InventoryHolder {
        @Getter
        private final Inventory inventory = Bukkit.createInventory(this, 27,
                MessageService.color("&5Artifact Ownership"));
    }

    private static final class ArtifactHistoryHolder implements InventoryHolder {
        private final String artifact;
        private final int page;
        @Getter
        private final Inventory inventory;

        private ArtifactHistoryHolder(String artifact, int page) {
            this.artifact = artifact;
            this.page = page;
            String name = artifact.equals("boc") ? "The Book of Bōc" : "The Sarculum";
            this.inventory = Bukkit.createInventory(this, 54, MessageService.color("&5" + name + " History"));
        }

        private String artifact() {
            return artifact;
        }

        private int page() {
            return page;
        }
    }
}