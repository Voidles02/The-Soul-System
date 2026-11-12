package com.KDI.souls.listener;

import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.database.DatabaseManager;
import com.KDI.souls.message.MessageService;
import com.KDI.souls.service.FragmentService;
import com.KDI.souls.service.SoulService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulListener implements Listener {
    private final SoulsConfig config;
    private final SoulService souls;
    private final FragmentService fragments;
    private final DatabaseManager database;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final MessageService messages;
    private final Set<UUID> pendingFragmentActions = ConcurrentHashMap.newKeySet();

    public SoulListener(org.bukkit.plugin.java.JavaPlugin plugin, SoulService souls, FragmentService fragments,
                        DatabaseManager database, SoulsConfig config, MessageService messages) {
        this.plugin = plugin;
        this.souls = souls;
        this.fragments = fragments;
        this.database = database;
        this.config = config;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        souls.rememberPlayer(player.getUniqueId(), player.getName());
        souls.loadBalance(player.getUniqueId()).thenRun(() -> {
            if (!plugin.isEnabled()) {
                return;
            }
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.isEnabled()) {
                        souls.applyBoost(player.getUniqueId());
                    }
                });
            } catch (IllegalPluginAccessException ignored) {
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        souls.persistBalance(event.getPlayer().getUniqueId()).exceptionally(exception -> {
            plugin.getLogger().warning("Could not persist souls for " + event.getPlayer().getName() + ": " + exception.getMessage());
            return null;
        });
        souls.clearBoostEffects(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> souls.applyBoost(event.getPlayer().getUniqueId()), 1L);
    }

    @EventHandler
    public void onFragmentUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().name().startsWith("RIGHT_CLICK")
                || !config.fragmentsEnabled() || !fragments.isFragment(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        useFragments(player, player.isSneaking());
    }

    @EventHandler
    public void onInventoryBulkFragmentUse(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClick() != ClickType.SHIFT_RIGHT
                || event.getClickedInventory() != player.getInventory()
                || !config.fragmentsEnabled() || !fragments.isFragment(event.getCurrentItem())) {
            return;
        }
        event.setCancelled(true);
        useFragments(player, true);
    }

    private void useFragments(Player player, boolean bulk) {
        UUID playerId = player.getUniqueId();
        if (!pendingFragmentActions.add(playerId)) {
            return;
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack heldSnapshot = bulk ? null : player.getInventory().getItemInMainHand().clone();
        souls.loadBalance(playerId).whenComplete((balance, error) -> {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null || !souls.isBalanceReady(playerId)) {
                        messages.send(player, "database-unavailable");
                        return;
                    }
                    if (bulk) {
                        FragmentService.ClaimResult result = fragments.claimAll(player);
                        if (!result.successful()) {
                            messages.send(player, "claim-failed", Map.of("remaining", result.fragmentsRemaining()));
                            return;
                        }
                        messages.send(player, "claim-success", Map.of("souls", result.souls(),
                                "fragments", result.fragmentsUsed(), "remaining", result.fragmentsRemaining()));
                        return;
                    }
                    if (player.getInventory().getHeldItemSlot() != heldSlot) {
                        return;
                    }
                    int reward = ThreadLocalRandom.current().nextInt(1, 6);
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (!fragments.isFragment(held) || held.getAmount() != heldSnapshot.getAmount()
                            || !held.isSimilar(heldSnapshot)) {
                        return;
                    }
                    SoulService.OperationResult result = souls.addSouls(playerId, reward, "fragment-use");
                    if (!result.successful()) {
                        messages.send(player, "max-reached", Map.of("max", config.maxBalance()));
                        return;
                    }
                    held.setAmount(held.getAmount() - 1);
                    messages.send(player, "fragment-collected", Map.of("amount", result.amount()));
                    fragments.playClaimEffect(player);
                    } finally {
                        pendingFragmentActions.remove(playerId);
                    }
                });
            } catch (IllegalPluginAccessException ignored) {
                pendingFragmentActions.remove(playerId);
            }
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            database.recordPvpResult(killer.getUniqueId(), victim.getUniqueId()).exceptionally(error -> {
                plugin.getLogger().warning("Could not record PvP stats: " + error.getMessage());
                return null;
            });
        }
        if (config.lossEnabled() && config.worldEnabled(victim.getWorld().getName(), "pvp")) {
            java.util.UUID victimId = victim.getUniqueId();
            souls.loadBalance(victimId).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                long loss = (long) Math.floor(souls.getBalance(victimId) * config.lossPercentage());
                if (config.lossMaximum() > 0) {
                    loss = Math.min(loss, config.lossMaximum());
                }
                souls.loseSouls(victimId, loss, "death");
            }));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        String world = event.getEntity().getWorld().getName();
        if (config.pveEnabled() && config.worldEnabled(world, "pve") && event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            long reward = config.pveReward(event.getEntityType());
            if (reward > 0) {
                souls.loadBalance(killer.getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                        () -> souls.addSouls(killer.getUniqueId(), reward, "pve-kill")));
            }
        }
        if (config.fragmentsEnabled() && config.worldEnabled(world, "pve") && event.getEntity().getKiller() != null) {
            long drop = config.pveFragmentDrop(event.getEntityType());
            if (drop > 0) {
                fragments.drop(event.getEntity().getLocation(), (int) Math.min(drop, Integer.MAX_VALUE));
            }
        }
    }

    @EventHandler
    public void onPlayerDeathForPvp(PlayerDeathEvent event) {
        if (!config.pvpEnabled()) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())
                || !config.worldEnabled(victim.getWorld().getName(), "pvp")) {
            return;
        }
        database.tryClaimPvp(killer.getUniqueId(), victim.getUniqueId(), config.pvpCooldownSeconds(),
                        config.pvpDiminishingPeriodSeconds(), config.pvpBaseReward(), config.pvpDiminishingFactor())
                .thenAccept(reward -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (reward <= 0) {
                        return;
                    }
                    souls.loadBalance(killer.getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                            () -> souls.addSouls(killer.getUniqueId(), reward, "pvp-kill")));
                })).exceptionally(error -> {
                    plugin.getLogger().warning("Could not claim a PvP Soul reward: " + error.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (killer.isOnline()) {
                            messages.send(killer, "database-unavailable");
                        }
                    });
                    return null;
                });
    }
}
