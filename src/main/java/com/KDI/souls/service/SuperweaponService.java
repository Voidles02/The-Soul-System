package com.KDI.souls.service;

import com.KDI.souls.listener.SoulAltarListener;
import com.KDI.souls.message.MessageService;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XPotion;
import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.particles.XParticle;
import lombok.Getter;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
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
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SuperweaponService implements Listener {
    private static final int COOLDOWN_BAR_CELLS = 2;
    private static final int COOLDOWN_BAR_LEVELS = 8;
    private static final double DEATH_EFFECT_RANGE = 100 * 16.0;
    private static final String[] COOLDOWN_PARTIAL_BLOCKS = {"", "▏", "▎", "▍", "▌", "▋", "▊", "▉"};
    private static final double SARCuLUM_DASH_DAMAGE = 13.5;
    private static final double SARCULUM_GROUND_SLAM_DAMAGE = 18.0;
    private static final double SARCULUM_MACE_SLAM_DAMAGE = 30.0;
    private static final DateTimeFormatter ARTIFACT_DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final JavaPlugin plugin;
    private final SoulAltarListener altar;
    private final MessageService messages;
    private final NamespacedKey artifactKey;
    private final NamespacedKey dashCooldownKey;
    private final NamespacedKey slamCooldownKey;
    private final NamespacedKey maceSlamCooldownKey;
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
    private final Object artifactSaveLock = new Object();
    private final ExecutorService artifactSaveExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Souls-artifact-storage");
        thread.setDaemon(true);
        return thread;
    });
    private Map<UUID, ArtifactOwnership> pendingArtifactSnapshot;
    private boolean artifactSaveWorkerRunning;
    private final XParticle beamSoulParticle = XParticle.of("SCULK_SOUL").orElse(null);
    private final XParticle beamEndRodParticle = XParticle.of("END_ROD").orElse(null);
    private final XParticle beamSonicParticle = XParticle.of("SONIC_BOOM").orElse(null);
    private final XParticle beamSoulFireParticle = XParticle.of("SOUL_FIRE_FLAME").orElse(null);
    private final XParticle slamSmokeParticle = XParticle.of("SMOKE").orElse(null);
    private final XParticle slamCloudParticle = XParticle.of("CLOUD").orElse(null);
    private final XParticle slamCritParticle = XParticle.of("CRIT").orElse(null);
    private final XParticle dashSweepParticle = XParticle.of("SWEEP_ATTACK").orElse(null);
    private final XParticle dashCloudParticle = XParticle.of("CLOUD").orElse(null);
    private final XParticle dashCritParticle = XParticle.of("CRIT").orElse(null);
    private final XParticle slamExplosionParticle = XParticle.of("EXPLOSION").orElse(null);
    private final XParticle slamSonicParticle = XParticle.of("SONIC_BOOM").orElse(null);

    public SuperweaponService(JavaPlugin plugin, SoulAltarListener altar, MessageService messages) {
        this.plugin = plugin;
        this.altar = altar;
        this.messages = messages;
        this.artifactKey = new NamespacedKey(plugin, "superweapon");
        this.dashCooldownKey = new NamespacedKey(plugin, "sarculum_dash_cooldown");
        this.slamCooldownKey = new NamespacedKey(plugin, "sarculum_slam_cooldown");
        this.maceSlamCooldownKey = new NamespacedKey(plugin, "sarculum_mace_slam_cooldown");
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
        Inventory inventory = holder.getInventory();
        ItemStack filler = menuItem("BLACK_STAINED_GLASS_PANE", "&r");
        ItemStack border = menuItem("PURPLE_STAINED_GLASS_PANE", "&r");
        if (filler != null) {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                inventory.setItem(slot, filler);
            }
        }
        if (border != null) {
            for (int slot = 0; slot < 9; slot++) {
                inventory.setItem(slot, border);
                inventory.setItem(slot + 18, border);
            }
            inventory.setItem(9, border);
            inventory.setItem(17, border);
        }
        if (artifactEnabled("boc")) {
            inventory.setItem(11, createBook());
        } else {
            inventory.setItem(11, menuItem("BARRIER", "&c&lBook of Bōc disabled",
                    "&7Enable it in the superweapon config."));
        }
        if (artifactEnabled("sarculum")) {
            inventory.setItem(15, createSarculum());
        } else {
            inventory.setItem(15, menuItem("BARRIER", "&c&lSarculum disabled",
                    "&7Enable it in the superweapon config."));
        }
        inventory.setItem(13, menuItem("NETHER_STAR", "&5&lSOUL WEAPON ARSENAL",
                "&7Click an enabled artifact to give it to yourself.",
                "&7Book of Bōc: invocations and a sustained beam.",
                "&7Sarculum: a steerable dash and two slam styles.",
                "&8Artifact access is controlled by the server."));
        player.openInventory(inventory);
    }

    private ItemStack menuItem(String materialName, String name, String... lore) {
        ItemStack item = XMaterial.matchXMaterial(materialName).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(MessageService.color(name));
        meta.setLore(java.util.Arrays.stream(lore).map(MessageService::color).toList());
        item.setItemMeta(meta);
        return item;
    }

    public void openInfoBook(Player player) {
        ItemStack book = XMaterial.matchXMaterial("WRITTEN_BOOK").map(XMaterial::parseItem).orElse(null);
        if (book == null || !(book.getItemMeta() instanceof BookMeta meta)) {
            return;
        }
        String title = plugin.getConfig().getString("superweapon.info-book.title", "The Superweapon Codex");
        meta.setTitle(MessageService.color(title.length() > 32 ? title.substring(0, 32) : title));
        meta.setAuthor(plugin.getConfig().getString("superweapon.info-book.author", "The Keepers of Souls"));
        meta.setPages(
                infoPage("&5&lSOULBOUND RELICS", "&8Two weapons. Two paths.\n\n"
                        + "&0The Book of Bōc channels\n"
                        + "&0invocations and a sustained\n"
                        + "&0beam. The Sarculum turns\n"
                        + "&0movement into a dash and\n"
                        + "&0devastating slams.\n\n"
                        + "&0Both are non-craftable\n"
                        + "&0artifacts with configurable\n"
                        + "&0server cooldowns."),
                infoPage("&d&lBOOK OF BŌC", "&8Invocation | Right-click\n\n"
                        + "&0Strength III — 8 min\n"
                        + "&0Regeneration V — 5 min\n"
                        + "&0Slowness I — 8 min\n"
                        + "&0Hunger I — 5 min\n\n"
                        + "&0Default cooldown: " + formatCooldown("activate", 15 * 60 * 1000L)),
                infoPage("&b&lWARDEN'S BEAM", "&8Left-click | 5 seconds\n\n"
                        + "&0Range: 45 blocks\n"
                        + "&0Damage: 20 per hit\n"
                        + "&0(10 hearts)\n"
                        + "&0Can hit the same target\n"
                        + "&0every 10 ticks.\n\n"
                        + "&0Default cooldown: " + formatCooldown("beam", 10 * 60 * 1000L)),
                infoPage("&3&lFOLLOWING SHOCKWAVES", "&8Three impacts after the beam\n\n"
                        + "&0Three waves track your\n"
                        + "&0position and strike nearby\n"
                        + "&0targets.\n\n"
                        + "&0At 2 blocks: 64 damage\n"
                        + "&0At 12 blocks: 24 damage\n"
                        + "&0Slowness II for 3 seconds."),
                infoPage("&5&lTHE SARCULUM", "&8Netherite Hoe artifact\n\n"
                        + "&0Sneak + left-click: dash.\n"
                        + "&0Steer by aiming as you move.\n"
                        + "&0Travel up to 25 blocks;\n"
                        + "&0strike within 4 blocks of\n"
                        + "&0the path for up to 13.5\n"
                        + "&0damage (6.75 hearts).\n\n"
                        + "&0Default cooldown: " + formatCooldown("dash", 2 * 60 * 1000L)),
                infoPage("&6&lGROUND SLAM", "&8One impact\n\n"
                        + "&0While grounded, sneak +\n"
                        + "&0right-click to launch up to\n"
                        + "&08 blocks, then strike once.\n\n"
                        + "&0Up to 18 damage (9 hearts)\n"
                        + "&0near the center, falling to\n"
                        + "&0half at the 8-block edge.\n\n"
                        + "&0Default cooldown: " + formatCooldown("slam", 5 * 60 * 1000L)),
                infoPage("&c&lAIRBORNE MACE SLAM", "&8Two impacts\n\n"
                        + "&0While airborne, sneak +\n"
                        + "&0right-click with a mace in\n"
                        + "&0your hotbar. Launch up to\n"
                        + "&012 blocks for two impacts.\n"
                        + "&0Keep the mace in your\n"
                        + "&0hotbar until it finishes.\n\n"
                        + "&0Each impact: up to 30 damage;\n"
                        + "&0half at the 12-block edge.\n"
                        + "&0Default cooldown: " + formatCooldown("mace-slam", 15 * 60 * 1000L)),
                infoPage("&3&lCOOLDOWNS & USE", "&8Quick controls\n\n"
                        + "&7Book of Bōc\n"
                        + "&0Right-click: invoke\n"
                        + "&0Left-click: beam\n"
                        + "&7Sarculum\n"
                        + "&0Sneak + left-click: dash\n"
                        + "&0Sneak + right-click: slam\n\n"
                        + "&0Cooldowns are adjustable.\n"
                        + "&0Hold an artifact to see its\n"
                        + "&0active cooldown on the HUD."));
        book.setItemMeta(meta);
        player.openBook(book);
    }

    private String infoPage(String title, String body) {
        return MessageService.color((title + "\n\n" + body)
                .replace("&f", "&0")
                .replace("&7", "&0")
                .replace("&8", "&0")
                .replace("&b", "&1")
                .replace("&3", "&1")
                .replace("&9", "&1")
                .replace("&d", "&5")
                .replace("&e", "&2")
                .replace("&6", "&3"));
    }

    private String formatCooldown(String ability, long defaultMillis) {
        long durationMillis = cooldownDurationMillis(ability, defaultMillis);
        if (durationMillis <= 0L) {
            return "No cooldown";
        }
        long seconds = durationMillis / 1000L + (durationMillis % 1000L == 0L ? 0L : 1L);
        if (seconds >= 86400L) {
            long days = seconds / 86400L;
            long hours = seconds % 86400L / 3600L;
            return hours == 0L ? days + "d" : days + "d " + hours + "h";
        }
        if (seconds >= 3600L) {
            long hours = seconds / 3600L;
            long minutes = seconds % 3600L / 60L;
            return minutes == 0L ? hours + "h" : hours + "h " + minutes + "m";
        }
        if (seconds >= 60L) {
            long minutes = seconds / 60L;
            long remainingSeconds = seconds % 60L;
            return remainingSeconds == 0L ? minutes + "m" : minutes + "m " + remainingSeconds + "s";
        }
        return seconds + "s";
    }

    private String featureStatus(String feature) {
        return plugin.getConfig().getBoolean("features." + feature + ".enabled", true)
                ? "&aEnabled" : "&cDisabled";
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
        if (ability.equals("mace-slam") || ability.equals("all")) {
            player.getPersistentDataContainer().remove(maceSlamCooldownKey);
        }
        if (ability.equals("activate") || ability.equals("all")) {
            player.getPersistentDataContainer().remove(activateCooldownKey);
        }
        if (ability.equals("beam") || ability.equals("all")) {
            player.getPersistentDataContainer().remove(beamCooldownKey);
        }
        long now = System.currentTimeMillis();
        refreshArtifactCooldown(player, "boc", now);
        refreshArtifactCooldown(player, "sarculum", now);
        startCooldownHud(player);
    }

    public void setCooldownDuration(String ability, long durationMillis) {
        plugin.getConfig().set("superweapon.cooldowns." + ability + "-millis", durationMillis);
        plugin.saveConfig();
        NamespacedKey key = switch (ability) {
            case "dash" -> dashCooldownKey;
            case "slam" -> slamCooldownKey;
            case "mace-slam" -> maceSlamCooldownKey;
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
        Set<UUID> seenArtifacts = new HashSet<>();
        boolean changed = scanArtifactInventory(player, player.getInventory(), seenArtifacts);
        changed |= scanArtifactInventory(player, player.getEnderChest(), seenArtifacts);

        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (top != player.getInventory() && top != player.getEnderChest() && isCustomEnderChest(view)) {
            changed |= scanArtifactInventory(player, top, seenArtifacts);
        }
        return changed;
    }

    private boolean scanArtifactInventory(Player player, Inventory inventory, Set<UUID> seenArtifacts) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (artifact(item) == null) {
                continue;
            }
            boolean idChanged = ensureArtifactId(item);
            UUID artifactId = artifactId(item);
            if (artifactId == null) {
                continue;
            }
            if (item.getAmount() > 1) {
                item.setAmount(1);
                inventory.setItem(slot, item);
                messages.send(player, "sw-duplicate-invalidated");
                changed = true;
            }
            if (!seenArtifacts.add(artifactId) || hasOnlineArtifactCopy(player, artifactId)) {
                inventory.setItem(slot, null);
                messages.send(player, "sw-duplicate-invalidated");
                changed = true;
                continue;
            }
            if (idChanged || recordArtifactOwner(player, item)) {
                inventory.setItem(slot, item);
                changed = true;
            }
        }
        return changed;
    }

    private UUID artifactId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String storedId = item.getItemMeta().getPersistentDataContainer()
                .get(artifactIdKey, PersistentDataType.STRING);
        try {
            return UUID.fromString(storedId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private boolean hasOnlineArtifactCopy(Player player, UUID artifactId) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) {
                continue;
            }
            if (containsArtifactId(online.getInventory(), artifactId)
                    || containsArtifactId(online.getEnderChest(), artifactId)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsArtifactId(Inventory inventory, UUID artifactId) {
        for (ItemStack item : inventory.getContents()) {
            if (artifactId.equals(artifactId(item))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDuplicateArtifact(Player player, ItemStack item, int ignoredSlot) {
        UUID id = artifactId(item);
        if (id == null) {
            return false;
        }
        if (item.getAmount() > 1 || hasOnlineArtifactCopy(player, id)) {
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != ignoredSlot && id.equals(artifactId(inventory.getItem(slot)))) {
                return true;
            }
        }
        if (containsArtifactId(player.getEnderChest(), id)) {
            return true;
        }
        InventoryView view = player.getOpenInventory();
        Inventory top = view.getTopInventory();
        return top != inventory && top != player.getEnderChest() && isCustomEnderChest(view)
                && containsArtifactId(top, id);
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
        Map<UUID, ArtifactOwnership> snapshot = new HashMap<>();
        artifactOwnerships.forEach((id, ownership) -> snapshot.put(id, new ArtifactOwnership(
                ownership.artifact(), ownership.owner(), ownership.ownerName(), ownership.startedAt(),
                List.copyOf(ownership.profiles()))));
        synchronized (artifactSaveLock) {
            pendingArtifactSnapshot = Map.copyOf(snapshot);
            if (artifactSaveWorkerRunning) {
                return;
            }
            artifactSaveWorkerRunning = true;
            try {
                artifactSaveExecutor.execute(this::writePendingArtifactOwnerships);
            } catch (RejectedExecutionException exception) {
                artifactSaveWorkerRunning = false;
                plugin.getLogger().warning("Could not queue artifact ownership data for saving: " + exception.getMessage());
            }
        }
    }

    private void writePendingArtifactOwnerships() {
        while (true) {
            Map<UUID, ArtifactOwnership> snapshot;
            synchronized (artifactSaveLock) {
                snapshot = pendingArtifactSnapshot;
                pendingArtifactSnapshot = null;
                if (snapshot == null) {
                    artifactSaveWorkerRunning = false;
                    artifactSaveLock.notifyAll();
                    return;
                }
            }
            writeArtifactOwnerships(snapshot);
        }
    }

    private void writeArtifactOwnerships(Map<UUID, ArtifactOwnership> snapshot) {
        YamlConfiguration data = new YamlConfiguration();
        snapshot.forEach((id, ownership) -> {
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

    public void shutdownArtifactPersistence() {
        artifactSaveExecutor.shutdown();
        try {
            if (!artifactSaveExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                artifactSaveExecutor.shutdownNow();
                plugin.getLogger().warning("Artifact ownership save did not finish before shutdown.");
            }
        } catch (InterruptedException exception) {
            artifactSaveExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while saving artifact ownership data during shutdown.");
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
        return Math.max(0L, plugin.getConfig().getLong(
                "superweapon.cooldowns." + ability + "-millis", defaultMillis));
    }

    private ItemStack createBook() {
        ItemStack item = XMaterial.matchXMaterial("SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE")
                .map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("The Book of Bōc");
        meta.setCustomModelData(19001);
        meta.setLore(List.of(
                MessageService.color("&7Right-click to invoke the book."),
                MessageService.color("&7Left-click for a 45-block beam and 3 shockwaves; 10-minute cooldown."),
                MessageService.color("&7Beam: 20 damage (10 hearts) per hit."),
                MessageService.color("&7Shockwaves: up to 64 damage (32 hearts) at 2 blocks, falling to 24 damage (12 hearts) at 12 blocks."),
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
        meta.setDisplayName("The Sarculum");
        meta.setCustomModelData(19002);
        meta.setLore(List.of(
                MessageService.color("&7Sneak + left-click: faster, freely steerable dash up to 25 blocks; 2-minute cooldown."),
                MessageService.color("&7Dash: 6.75 hearts (13.5 damage points) in a 4-block radius along the path."),
                MessageService.color("&7Sneak (Shift) + right-click: slam."),
                MessageService.color("&7Ground slam cooldown: " + formatCooldown("slam", 5 * 60 * 1000L) + "."),
                MessageService.color("&7Airborne mace slam cooldown: " + formatCooldown("mace-slam", 15 * 60 * 1000L) + "."),
                MessageService.color("&7Slam: launch up to 8 blocks for one impact."),
                MessageService.color("&7Airborne with a mace in your hotbar: launch up to 12 blocks for two impacts."),
                MessageService.color("&7Ground slam: up to 18 damage (9 hearts) near the center, falling to half at the 8-block edge."),
                MessageService.color("&7Airborne mace slam: up to 30 damage (15 hearts) per impact, falling to half at the 12-block edge."),
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

    private boolean artifactAlreadyIssued(String type) {
        if (plugin.getConfig().getBoolean("superweapon.artifacts." + type + ".issued", false)
                || artifactOwnerships.values().stream().anyMatch(ownership -> ownership.artifact().equals(type))) {
            return true;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (containsArtifactType(online.getInventory(), type)
                    || containsArtifactType(online.getEnderChest(), type)) {
                return true;
            }
            InventoryView view = online.getOpenInventory();
            Inventory top = view.getTopInventory();
            if (top != online.getInventory() && top != online.getEnderChest() && isCustomEnderChest(view)
                    && containsArtifactType(top, type)) {
                return true;
            }
        }
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Item dropped : world.getEntitiesByClass(org.bukkit.entity.Item.class)) {
                if (type.equals(artifact(dropped.getItemStack()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsArtifactType(Inventory inventory, String type) {
        for (ItemStack item : inventory.getContents()) {
            if (type.equals(artifact(item))) {
                return true;
            }
        }
        return false;
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
        String issuedPath = "superweapon.artifacts." + artifactType + ".issued";
        if (artifactAlreadyIssued(artifactType)) {
            if (!plugin.getConfig().getBoolean(issuedPath, false)) {
                plugin.getConfig().set(issuedPath, true);
                plugin.saveConfig();
            }
            player.sendMessage(MessageService.color("&cThis artifact has already been issued; duplicate grants are blocked."));
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
        plugin.getConfig().set(issuedPath, true);
        plugin.saveConfig();
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArtifactPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (hasDuplicateArtifact(player, item, -1)) {
            event.setCancelled(true);
            event.getItem().remove();
            messages.send(player, "sw-duplicate-invalidated");
            return;
        }
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
        if (event.getItem().getAmount() > 1) {
            event.getItem().setAmount(1);
            player.getInventory().setItemInMainHand(event.getItem());
            messages.send(player, "sw-duplicate-invalidated");
        }
        if (hasDuplicateArtifact(player, event.getItem(), player.getInventory().getHeldItemSlot())) {
            player.getInventory().setItemInMainHand(null);
            event.setCancelled(true);
            messages.send(player, "sw-duplicate-invalidated");
            return;
        }
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
                if (beamEndRodParticle != null) {
                    for (int point = 0; point < 8; point++) {
                        double angle = rotation + Math.PI * 2 * point / 8;
                        player.getWorld().spawnParticle(beamEndRodParticle.get(),
                                center.getX() + Math.cos(angle) * 0.9,
                                center.getY() + 0.2 + point * 0.22 + Math.sin(rotation + point) * 0.08,
                                center.getZ() + Math.sin(angle) * 0.75, 1, 0, 0, 0, 0);
                    }
                }
                if (beamSoulParticle != null) {
                    for (int point = 0; point < 4; point++) {
                        double angle = -rotation * 1.4 + Math.PI * 2 * point / 4;
                        player.getWorld().spawnParticle(beamSoulParticle.get(),
                                center.getX() + Math.cos(angle) * 0.5,
                                center.getY() + 0.55 + point * 0.3 + Math.sin(rotation + point) * 0.1,
                                center.getZ() + Math.sin(angle) * 0.48, 1, 0, 0, 0, 0);
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
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
        if (cooldownHudTasks.containsKey(uuid)) {
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            private boolean hudVisible;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cooldownHudTasks.remove(uuid);
                    cancel();
                    return;
                }
                long now = System.currentTimeMillis();
                refreshHeldArtifactCooldown(player, now);
                hudVisible = sendCooldownActionBar(player, now, hudVisible);
                if (player.getPersistentDataContainer().getOrDefault(dashCooldownKey, PersistentDataType.LONG, 0L) <= now
                        && player.getPersistentDataContainer().getOrDefault(slamCooldownKey, PersistentDataType.LONG, 0L) <= now
                        && player.getPersistentDataContainer().getOrDefault(maceSlamCooldownKey, PersistentDataType.LONG, 0L) <= now
                        && player.getPersistentDataContainer().getOrDefault(activateCooldownKey, PersistentDataType.LONG, 0L) <= now
                        && player.getPersistentDataContainer().getOrDefault(beamCooldownKey, PersistentDataType.LONG, 0L) <= now) {
                    cooldownHudTasks.remove(uuid);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
        cooldownHudTasks.put(uuid, task);
    }

    private boolean sendCooldownActionBar(Player player, long now, boolean hudVisible) {
        String heldArtifact = artifact(player.getInventory().getItemInMainHand());
        StringBuilder cooldowns = new StringBuilder();
        if ("sarculum".equals(heldArtifact)) {
            appendCooldownBar(cooldowns, player, "D", dashCooldownKey, "dash", 2 * 60 * 1000L, now, "&b", "&3");
            appendCooldownBar(cooldowns, player, "S", slamCooldownKey, "slam", 5 * 60 * 1000L, now, "&6", "&e");
            appendCooldownBar(cooldowns, player, "M", maceSlamCooldownKey, "mace-slam", 15 * 60 * 1000L, now, "&d", "&5");
        } else if ("boc".equals(heldArtifact)) {
            appendCooldownBar(cooldowns, player, "A", activateCooldownKey, "activate", 15 * 60 * 1000L, now, "&d", "&5");
            appendCooldownBar(cooldowns, player, "B", beamCooldownKey, "beam", 10 * 60 * 1000L, now, "&c", "&4");
        }
        if (cooldowns.length() == 0) {
            if (hudVisible) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            }
            return false;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(cooldowns.toString()));
        return true;
    }

    private void appendCooldownBar(StringBuilder cooldowns, Player player, String label, NamespacedKey key,
                                   String ability, long defaultDurationMillis, long now,
                                   String labelColor, String barColor) {
        if (player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L) <= now) {
            return;
        }
        if (cooldowns.length() > 0) {
            cooldowns.append(' ');
        }
        cooldowns.append(cooldownBar(player, label, key, ability, defaultDurationMillis, now, labelColor, barColor));
    }

    private String cooldownBar(Player player, String label, NamespacedKey key, String ability,
                               long defaultDurationMillis, long now, String labelColor, String barColor) {
        long expiresAt = player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
        long remainingMillis = Math.max(0L, expiresAt - now);
        long durationMillis = cooldownDurationMillis(ability, defaultDurationMillis);
        int totalUnits = COOLDOWN_BAR_CELLS * COOLDOWN_BAR_LEVELS;
        int filledUnits = remainingMillis == 0L || durationMillis <= 0L
                ? totalUnits
                : (int) Math.min(totalUnits, Math.max(0L,
                        Math.round((double) Math.max(0L, durationMillis - remainingMillis) / durationMillis * totalUnits)));
        String fillColor = filledUnits == totalUnits ? "&a" : barColor;
        StringBuilder bar = new StringBuilder(labelColor).append(label);
        for (int cell = 0; cell < COOLDOWN_BAR_CELLS; cell++) {
            int cellFill = Math.min(COOLDOWN_BAR_LEVELS, Math.max(0, filledUnits - cell * COOLDOWN_BAR_LEVELS));
            if (cellFill == COOLDOWN_BAR_LEVELS) {
                bar.append(fillColor).append('█');
            } else if (cellFill > 0) {
                bar.append(fillColor).append(COOLDOWN_PARTIAL_BLOCKS[cellFill]);
            } else {
                bar.append("&8░");
            }
        }
        return MessageService.color(bar.append("&7 ").append(formatCooldownRemaining(remainingMillis)).toString());
    }

    private String formatCooldownRemaining(long remainingMillis) {
        long totalSeconds = remainingMillis / 1_000L + (remainingMillis % 1_000L == 0L ? 0L : 1L);
        long hours = totalSeconds / 3_600L;
        long minutes = totalSeconds / 60L % 60L;
        long seconds = totalSeconds % 60L;
        String minuteText = minutes < 10 ? "0" + minutes : Long.toString(minutes);
        String secondText = seconds < 10 ? "0" + seconds : Long.toString(seconds);
        return hours > 0 ? hours + ":" + minuteText + ":" + secondText
                : totalSeconds / 60L + ":" + secondText;
    }

    private void refreshHeldArtifactCooldown(Player player, long now) {
        String heldArtifact = artifact(player.getInventory().getItemInMainHand());
        if (heldArtifact != null) {
            refreshArtifactCooldown(player, heldArtifact, now);
        }
    }

    private void refreshArtifactCooldown(Player player, String artifact, long now) {
        long expiresAt;
        String materialName;
        if (artifact.equals("boc")) {
            expiresAt = Math.max(
                    player.getPersistentDataContainer().getOrDefault(activateCooldownKey, PersistentDataType.LONG, 0L),
                    player.getPersistentDataContainer().getOrDefault(beamCooldownKey, PersistentDataType.LONG, 0L));
            materialName = "SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE";
        } else if (artifact.equals("sarculum")) {
            expiresAt = Math.max(
                    player.getPersistentDataContainer().getOrDefault(dashCooldownKey, PersistentDataType.LONG, 0L),
                    Math.max(
                            player.getPersistentDataContainer().getOrDefault(slamCooldownKey, PersistentDataType.LONG, 0L),
                            player.getPersistentDataContainer().getOrDefault(maceSlamCooldownKey, PersistentDataType.LONG, 0L)));
            materialName = "NETHERITE_HOE";
        } else {
            return;
        }
        long remainingMillis = expiresAt - now;
        long ticks = remainingMillis <= 0L ? 0L
                : remainingMillis / 50L + (remainingMillis % 50L == 0L ? 0L : 1L);
        XMaterial.matchXMaterial(materialName)
                .map(XMaterial::parseMaterial)
                .ifPresent(material -> player.setCooldown(material, (int) Math.min(Integer.MAX_VALUE, ticks)));
    }

    private void beam(Player player) {
        if (!startCooldown(player, beamCooldownKey, cooldownDurationMillis("beam", 10 * 60 * 1000L))) {
            return;
        }
        Map<UUID, Integer> lastHitTicks = new HashMap<>();
        BukkitTask task = new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!player.isOnline() || player.isDead()) {
                    stopAction(player);
                    return;
                }
                fireBeam(player, lastHitTicks, ticks);
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

    private void fireBeam(Player attacker, Map<UUID, Integer> lastHitTicks, int ticks) {
        Location origin = attacker.getEyeLocation();
        Vector direction = origin.getDirection();
        if (direction.lengthSquared() < 0.001) {
            return;
        }
        direction.normalize();
        World world = attacker.getWorld();
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.001) {
            right = direction.clone().crossProduct(new Vector(1, 0, 0));
        }
        right.normalize();
        Vector up = right.clone().crossProduct(direction).normalize();
        int sample = 0;
        for (double distance = 0.5; distance <= 45; distance += 0.75, sample++) {
            double pointX = origin.getX() + direction.getX() * distance;
            double pointY = origin.getY() + direction.getY() * distance;
            double pointZ = origin.getZ() + direction.getZ() * distance;
            if (beamSoulParticle != null) {
                world.spawnParticle(beamSoulParticle.get(), pointX, pointY, pointZ, 1, 0.04, 0.04, 0.04, 0.01);
            }
            if (beamEndRodParticle != null && sample % 2 == 0) {
                world.spawnParticle(beamEndRodParticle.get(), pointX, pointY, pointZ, 1, 0.02, 0.02, 0.02, 0);
            }
            if (sample % 2 == 0 && (beamSoulFireParticle != null || beamEndRodParticle != null)) {
                double horizontal = Math.cos(distance * 2.4) * 0.28;
                double vertical = Math.sin(distance * 2.4) * 0.28;
                double spiralX = pointX + right.getX() * horizontal + up.getX() * vertical;
                double spiralY = pointY + right.getY() * horizontal + up.getY() * vertical;
                double spiralZ = pointZ + right.getZ() * horizontal + up.getZ() * vertical;
                if (beamSoulFireParticle != null) {
                    world.spawnParticle(beamSoulFireParticle.get(), spiralX, spiralY, spiralZ, 1);
                } else if (beamEndRodParticle != null) {
                    world.spawnParticle(beamEndRodParticle.get(), spiralX, spiralY, spiralZ, 1);
                }
            }
            if (beamSonicParticle != null && sample % 8 == 0) {
                world.spawnParticle(beamSonicParticle.get(), pointX, pointY, pointZ, 1);
            }
        }
        Location candidatesCenter = origin.clone().add(direction.clone().multiply(22.5));
        double xRange = Math.abs(direction.getX()) * 22.5 + 1.5;
        double yRange = Math.abs(direction.getY()) * 22.5 + 1.5;
        double zRange = Math.abs(direction.getZ()) * 22.5 + 1.5;
        for (Entity entity : world.getNearbyEntities(candidatesCenter, xRange, yRange, zRange,
                entity -> entity instanceof LivingEntity)) {
            if (!(entity instanceof LivingEntity target) || target.equals(attacker)
                    || target.isDead() || !target.isValid()
                    || target instanceof Player targetPlayer
                    && (!world.getPVP() || targetPlayer.getGameMode() == org.bukkit.GameMode.SPECTATOR)
                    || ticks - lastHitTicks.getOrDefault(target.getUniqueId(), Integer.MIN_VALUE / 2) < 10) {
                continue;
            }
            BoundingBox bounds = target.getBoundingBox();
            double entityCenterX = (bounds.getMinX() + bounds.getMaxX()) * 0.5;
            double entityCenterY = (bounds.getMinY() + bounds.getMaxY()) * 0.5;
            double entityCenterZ = (bounds.getMinZ() + bounds.getMaxZ()) * 0.5;
            double offsetX = entityCenterX - origin.getX();
            double offsetY = entityCenterY - origin.getY();
            double offsetZ = entityCenterZ - origin.getZ();
            double distance = offsetX * direction.getX() + offsetY * direction.getY()
                    + offsetZ * direction.getZ();
            if (distance < 0 || distance > 45) {
                continue;
            }
            double beamX = origin.getX() + direction.getX() * distance;
            double beamY = origin.getY() + direction.getY() * distance;
            double beamZ = origin.getZ() + direction.getZ() * distance;
            double closestX = Math.max(bounds.getMinX(), Math.min(bounds.getMaxX(), beamX));
            double closestY = Math.max(bounds.getMinY(), Math.min(bounds.getMaxY(), beamY));
            double closestZ = Math.max(bounds.getMinZ(), Math.min(bounds.getMaxZ(), beamZ));
            double xDistance = closestX - beamX;
            double yDistance = closestY - beamY;
            double zDistance = closestZ - beamZ;
            if (xDistance * xDistance + yDistance * yDistance + zDistance * zDistance > 1.0) {
                continue;
            }
            lastHitTicks.put(target.getUniqueId(), ticks);
            target.setNoDamageTicks(0);
            altar.damageFromAbility(target, attacker, 20.0);
        }
    }

    private void shockwave(Player attacker) {
        World world = attacker.getWorld();
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
                Location center = attacker.getLocation().clone().add(0, 0.5, 0);
                if (ticks % 10 == 0 && ticks < 30) {
                    damageShockwave(attacker, center, world);
                    XSound.matchXSound("ENTITY_WARDEN_SONIC_BOOM").ifPresent(sound -> sound.play(attacker));
                    if (beamSonicParticle != null) {
                        world.spawnParticle(beamSonicParticle.get(), center, 1);
                    }
                }
                int waveTick = ticks % 30;
                double radius = 40.0 * (waveTick + 1) / 30.0;
                for (int point = 0; point < 72; point++) {
                    double angle = Math.PI * 2 * point / 72;
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
            double damage = (distance <= 2 ? 16 : 18 - distance) * 4;
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
                strikeNearby(player, current, 4, SARCuLUM_DASH_DAMAGE, hit, false);
                World world = current.getWorld();
                if (dashSweepParticle != null) {
                    world.spawnParticle(dashSweepParticle.get(), current.getX(), current.getY() + 1.0,
                            current.getZ(), 3, 0.18, 0.24, 0.18, 0.01);
                }
                if (dashCloudParticle != null) {
                    world.spawnParticle(dashCloudParticle.get(), current.getX(), current.getY() + 0.75,
                            current.getZ(), 5, 0.2, 0.16, 0.2, 0.035);
                }
                if (dashCritParticle != null && ticks % 2 == 0) {
                    world.spawnParticle(dashCritParticle.get(), current.getX(), current.getY() + 1.0,
                            current.getZ(), 2, 0.22, 0.25, 0.22, 0.12);
                }
                if (beamSoulFireParticle != null) {
                    world.spawnParticle(beamSoulFireParticle.get(),
                            current.getX() - dashDirection.getX() * 0.55, current.getY() + 0.85,
                            current.getZ() - dashDirection.getZ() * 0.55, 2, 0.06, 0.08, 0.06, 0.01);
                }
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
        NamespacedKey cooldownKey = aerialSlam ? maceSlamCooldownKey : slamCooldownKey;
        String cooldownAbility = aerialSlam ? "mace-slam" : "slam";
        long defaultCooldown = aerialSlam ? 15 * 60 * 1000L : 5 * 60 * 1000L;
        if (!startCooldown(player, cooldownKey, cooldownDurationMillis(cooldownAbility, defaultCooldown))) {
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
                        if (beamSoulParticle != null) {
                            player.getWorld().spawnParticle(beamSoulParticle.get(), current.getX(), current.getY() + 0.4,
                                    current.getZ(), 8, 0.35, 0.35, 0.35, 0.06);
                        }
                        if (beamSoulFireParticle != null) {
                            player.getWorld().spawnParticle(beamSoulFireParticle.get(), current.getX(), current.getY() + 0.2,
                                    current.getZ(), 4, 0.25, 0.25, 0.25, 0.015);
                        }
                        if (slamSmokeParticle != null) {
                            player.getWorld().spawnParticle(slamSmokeParticle.get(), current.getX(), current.getY() + 0.5,
                                    current.getZ(), 6, 0.3, 0.3, 0.3, 0.045);
                        }
                    } else {
                        if (slamCloudParticle != null) {
                            player.getWorld().spawnParticle(slamCloudParticle.get(), current.getX(), current.getY() + 0.5,
                                    current.getZ(), 8, 0.2, 0.2, 0.2, 0.035);
                        }
                        if (slamCritParticle != null) {
                            player.getWorld().spawnParticle(slamCritParticle.get(), current.getX(), current.getY() + 0.4,
                                    current.getZ(), 5, 0.15, 0.15, 0.15, 0.08);
                        }
                        if (beamSoulFireParticle != null) {
                            for (int point = 0; point < 4; point++) {
                                double angle = ticks * 0.38 + Math.PI * 2 * point / 4;
                                player.getWorld().spawnParticle(beamSoulFireParticle.get(),
                                        current.getX() + Math.cos(angle) * 0.65, current.getY() + 0.25,
                                        current.getZ() + Math.sin(angle) * 0.65, 1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
                if (player.isOnGround() && airborne) {
                    strikeNearby(player, current, aerialSlam ? 12 : 8,
                            aerialSlam ? SARCULUM_MACE_SLAM_DAMAGE : SARCULUM_GROUND_SLAM_DAMAGE,
                            new HashSet<>(), true);
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
                    Bukkit.getScheduler().runTaskLater(plugin,
                            () -> fallProtection.remove(uuid, fallProtectionExpiry), 2L);
                    return;
                }
                if (!airborne && phaseTicks >= 5) {
                    if (aerialSlam && impacts == 1 && player.isOnGround()) {
                        strikeNearby(player, current, aerialSlam ? 12 : 8,
                                aerialSlam ? SARCULUM_MACE_SLAM_DAMAGE : SARCULUM_GROUND_SLAM_DAMAGE,
                                new HashSet<>(), true);
                        XSound.matchXSound("ENTITY_GENERIC_EXPLODE").ifPresent(sound -> sound.play(player));
                        XSound.matchXSound("ENTITY_WARDEN_SONIC_BOOM").ifPresent(sound -> sound.play(player));
                        playSlamImpact(current, aerialSlam);
                        player.setFallDistance(0);
                        stopAction(player);
                        Bukkit.getScheduler().runTaskLater(plugin,
                                () -> fallProtection.remove(uuid, fallProtectionExpiry), 2L);
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
        double centerX = center.getX();
        double centerY = center.getY();
        double centerZ = center.getZ();
        if (slamExplosionParticle != null) {
            world.spawnParticle(slamExplosionParticle.get(), centerX, centerY + 0.8, centerZ, 1);
        }
        if (slamSonicParticle != null) {
            world.spawnParticle(slamSonicParticle.get(), centerX, centerY + 0.8, centerZ, 1);
        }
        if (aerialSlam) {
            if (beamSoulParticle != null) {
                world.spawnParticle(beamSoulParticle.get(), centerX, centerY + 0.25, centerZ,
                        72, 2.6, 0.55, 2.6, 0.12);
            }
            if (beamSoulFireParticle != null) {
                world.spawnParticle(beamSoulFireParticle.get(), centerX, centerY + 0.12, centerZ,
                        48, 2.0, 0.25, 2.0, 0.025);
            }
            if (slamSmokeParticle != null) {
                world.spawnParticle(slamSmokeParticle.get(), centerX, centerY + 0.4, centerZ,
                        60, 2.5, 0.55, 2.5, 0.09);
            }
        } else {
            if (slamCloudParticle != null) {
                world.spawnParticle(slamCloudParticle.get(), centerX, centerY + 0.12, centerZ,
                        44, 1.35, 0.2, 1.35, 0.1);
            }
            if (slamCritParticle != null) {
                world.spawnParticle(slamCritParticle.get(), centerX, centerY + 0.5, centerZ,
                        28, 1.0, 0.5, 1.0, 0.14);
            }
        }
        XParticle plumeParticle = aerialSlam ? beamSoulParticle : beamEndRodParticle;
        if (plumeParticle != null) {
            for (int level = 0; level < 6; level++) {
                double angle = level * Math.PI / 3;
                world.spawnParticle(plumeParticle.get(), centerX + Math.cos(angle) * 0.16,
                        centerY + 0.2 + level * 0.34, centerZ + Math.sin(angle) * 0.16,
                        aerialSlam ? 2 : 1, 0.06, 0.1, 0.06, 0.015);
            }
        }
        new BukkitRunnable() {
            private int wave;

            @Override
            public void run() {
                int waveCount = aerialSlam ? 28 : 18;
                if (wave >= waveCount || !world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) {
                    cancel();
                    return;
                }
                double radius = aerialSlam ? (wave + 1) * (12.0 / waveCount) : (wave + 1) * (8.0 / waveCount);
                double innerRadius = radius * (aerialSlam ? 0.78 : 0.68);
                double height = aerialSlam ? 0.1 + Math.sin(wave * 0.35) * 0.16
                        : 0.08 + (wave % 2) * 0.18;
                int pointCount = aerialSlam ? 28 : 20;
                for (int point = 0; point < pointCount; point++) {
                    double angle = Math.PI * 2 * point / pointCount + wave * 0.12;
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    double x = centerX + cos * radius;
                    double z = centerZ + sin * radius;
                    if (aerialSlam) {
                        if (beamSoulParticle != null) {
                            world.spawnParticle(beamSoulParticle.get(), x, centerY + height, z, 1, 0, 0, 0, 0);
                        }
                        if (beamSoulFireParticle != null && point % 2 == 0) {
                            world.spawnParticle(beamSoulFireParticle.get(), centerX + cos * innerRadius,
                                    centerY + 0.12, centerZ + sin * innerRadius, 1, 0, 0, 0, 0);
                        }
                        if (slamSmokeParticle != null && point % 7 == 0 && wave % 3 == 0) {
                            world.spawnParticle(slamSmokeParticle.get(), x, centerY + 0.32, z,
                                    1, 0.04, 0.08, 0.04, 0.015);
                        }
                    } else {
                        if (beamSoulFireParticle != null) {
                            world.spawnParticle(beamSoulFireParticle.get(), x, centerY + height, z, 1, 0, 0, 0, 0);
                        }
                        if (beamEndRodParticle != null && point % 2 == 0) {
                            world.spawnParticle(beamEndRodParticle.get(), centerX + cos * innerRadius,
                                    centerY + height + 0.2, centerZ + sin * innerRadius, 1, 0, 0, 0, 0);
                        }
                        if (slamCloudParticle != null && point % 3 == 0) {
                            world.spawnParticle(slamCloudParticle.get(), centerX + cos * innerRadius,
                                    centerY + 0.14, centerZ + sin * innerRadius, 1, 0, 0, 0, 0);
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

    private void strikeNearby(Player attacker, Location center, double radius, double damage, Set<UUID> hit,
                              boolean distanceFalloff) {
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(attacker)
                    || target.isDead() || !target.isValid()) {
                continue;
            }
            if (target instanceof Player player
                    && (!center.getWorld().getPVP() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                    || player.getGameMode() == org.bukkit.GameMode.CREATIVE)) {
                continue;
            }
            double distanceSquared = target.getLocation().distanceSquared(center);
            if (distanceSquared > radius * radius || !hit.add(target.getUniqueId())) {
                continue;
            }
            target.setNoDamageTicks(0);
            double appliedDamage = distanceFalloff
                    ? damage * (1.0 - 0.5 * distanceSquared / (radius * radius))
                    : damage;
            altar.damageFromAbility(target, attacker, appliedDamage);
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
                || player.getPersistentDataContainer().getOrDefault(maceSlamCooldownKey, PersistentDataType.LONG, 0L) > now
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
        playDeathEffect(player);
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

    private void playDeathEffect(Player player) {
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.strikeLightningEffect(location);
        double deathX = location.getX();
        double deathY = location.getY();
        double deathZ = location.getZ();
        XParticle.of("ELECTRIC_SPARK").ifPresent(particle -> world.spawnParticle(
                particle.get(), deathX, deathY + 1, deathZ, 32, 0.55, 0.8, 0.55, 0.08));
        XParticle.of("SOUL").ifPresent(particle -> {
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24;
                double x = deathX + Math.cos(angle) * 1.2;
                double z = deathZ + Math.sin(angle) * 1.2;
                world.spawnParticle(particle.get(), x, deathY + 0.25, z, 1, 0, 0, 0, 0);
            }
        });
        playDeathSound(location, "ENTITY_ENDER_DRAGON_GROWL");
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> playDeathSound(location, "ENTITY_WARDEN_EMERGE"), 8L);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> playDeathSound(location, "ENTITY_WARDEN_DEATH"), 16L);
    }

    private void playDeathSound(Location location, String soundName) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        XSound.matchXSound(soundName).ifPresent(sound -> {
            double rangeSquared = DEATH_EFFECT_RANGE * DEATH_EFFECT_RANGE;
            for (Player listener : world.getPlayers()) {
                if (listener.getLocation().distanceSquared(location) <= rangeSquared) {
                    sound.play(listener);
                }
            }
        });
    }

    private static final class MenuHolder implements InventoryHolder {
        @Getter
        private final Inventory inventory = Bukkit.createInventory(this, 27,
                MessageService.color("&5&lSoul Weapon Arsenal"));
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