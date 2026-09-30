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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class SoulListener implements Listener {
    private final SoulsConfig config;
    private final SoulService souls;
    private final FragmentService fragments;
    private final DatabaseManager database;
    private final org.bukkit.plugin.java.JavaPlugin plugin;
    private final MessageService messages;

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
        souls.loadBalance(player.getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(plugin,
                () -> souls.applyBoost(player.getUniqueId())));
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
        souls.loadBalance(player.getUniqueId()).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> {
            int reward = ThreadLocalRandom.current().nextInt(1, 6);
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!fragments.isFragment(held)) {
                return;
            }
            SoulService.OperationResult result = souls.addSouls(player.getUniqueId(), reward, "fragment-use");
            if (!result.successful()) {
                messages.send(player, "max-reached", Map.of("max", config.maxBalance()));
                return;
            }
            held.setAmount(held.getAmount() - 1);
            messages.send(player, "fragment-collected", Map.of("amount", result.amount()));
        }));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            database.recordPvpResult(killer.getUniqueId(), victim.getUniqueId());
        }
        if (config.lossEnabled() && config.worldEnabled(victim.getWorld().getName(), "pvp")) {
            long loss = (long) Math.floor(souls.getBalance(victim.getUniqueId()) * config.lossPercentage());
            if (config.lossMaximum() > 0) {
                loss = Math.min(loss, config.lossMaximum());
            }
            souls.loseSouls(victim.getUniqueId(), loss, "death");
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
                }));
    }
}
