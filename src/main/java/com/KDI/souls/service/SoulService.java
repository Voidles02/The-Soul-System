package com.KDI.souls.service;

import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.database.DatabaseManager;
import com.KDI.souls.event.SoulEarnEvent;
import com.KDI.souls.event.SoulLoseEvent;
import com.KDI.souls.event.SoulSpendEvent;
import com.cryptomorin.xseries.XPotion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SoulService {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final SoulsConfig config;
    private final LocalSoulStore localStore;
    private final Map<UUID, Long> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Map<UUID, Object> saveVersions = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> lastSaves = new HashMap<>();
    private final Set<UUID> pendingDatabaseSync = new HashSet<>();
    private final Map<UUID, Long> lastAccess = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Long>> loading = new ConcurrentHashMap<>();
    private final Set<UUID> unresolvedBalances = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<PotionEffectType>> ownedBoostEffects = new ConcurrentHashMap<>();
    private final Map<UUID, Map<PotionEffectType, PotionEffect>> preservedBoostEffects = new ConcurrentHashMap<>();

    public SoulService(JavaPlugin plugin, DatabaseManager database, SoulsConfig config) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.localStore = new LocalSoulStore(plugin, config);
    }

    public synchronized CompletableFuture<Long> loadBalance(UUID uuid) {
        lastAccess.put(uuid, System.nanoTime());
        Long loaded = balances.get(uuid);
        if (loaded != null) {
            return CompletableFuture.completedFuture(loaded);
        }
        CompletableFuture<Long> existing = loading.get(uuid);
        if (existing != null) {
            return existing;
        }
        CompletableFuture<Long> result = new CompletableFuture<>();
        loading.put(uuid, result);
        long maximum = config.maxBalance();
        database.loadBalance(uuid).handleAsync((databaseValue, error) -> {
            LocalSoulStore.StoredData local = localStore.load(uuid);
            synchronized (this) {
                if (error != null && local == null) {
                    unresolvedBalances.add(uuid);
                    plugin.getLogger().warning("Could not load souls for " + uuid
                            + " and no local backup exists; balance changes are blocked until the database recovers.");
                    return 0L;
                }
                unresolvedBalances.remove(uuid);
                boolean useLocal = error != null || local != null && local.databasePending();
                long value = useLocal ? local == null ? 0L : local.souls() : databaseValue;
                long safeValue = Math.max(0, Math.min(maximum, value));
                balances.put(uuid, safeValue);
                String name = playerNames.getOrDefault(uuid, local == null ? "" : local.name());
                if (!name.isBlank()) {
                    playerNames.putIfAbsent(uuid, name);
                }
                if (useLocal) {
                    pendingDatabaseSync.add(uuid);
                } else {
                    pendingDatabaseSync.remove(uuid);
                }
                lastSaves.put(uuid, localStore.saveAsync(uuid, name, safeValue, maximum, "load", useLocal));
                if (error == null && local != null && local.databasePending()) {
                    syncPendingBalance(uuid, name, safeValue);
                }
                return safeValue;
            }
        }).whenComplete((value, error) -> {
            synchronized (this) {
                loading.remove(uuid, result);
                lastAccess.put(uuid, System.nanoTime());
            }
            if (error == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    public void rememberPlayer(UUID uuid, String name) {
        lastAccess.put(uuid, System.nanoTime());
        if (name != null && !name.isBlank()) {
            playerNames.put(uuid, name);
        }
    }

    public long getBalance(UUID uuid) {
        Long balance = balances.get(uuid);
        if (balance != null) {
            lastAccess.put(uuid, System.nanoTime());
        }
        return balance == null ? 0L : balance;
    }

    public boolean isBalanceReady(UUID uuid) {
        return !unresolvedBalances.contains(uuid);
    }

    public boolean hasCachedBalance(UUID uuid) {
        return balances.containsKey(uuid);
    }

    public int getCachedBalanceCount() {
        return balances.size();
    }

    public int getPendingBalanceLoadCount() {
        return loading.size();
    }

    public long getRemainingCapacity(UUID uuid) {
        return Math.max(0, config.maxBalance() - getBalance(uuid));
    }

    public CompletableFuture<Void> persistBalance(UUID uuid) {
        Long balance = balances.get(uuid);
        if (balance == null) {
            return CompletableFuture.completedFuture(null);
        }
        return persist(uuid, balance, "player-quit");
    }

    public CompletableFuture<Void> persistAll() {
        CompletableFuture<?>[] saves = balances.entrySet().stream()
                .map(entry -> persist(entry.getKey(), entry.getValue(), "shutdown"))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(saves);
    }

    public synchronized void evictOfflineBalances() {
        long cutoff = System.nanoTime() - java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
        if (!database.isAvailable() && !config.localDataEnabled()) {
            return;
        }
        for (Map.Entry<UUID, Long> entry : lastAccess.entrySet()) {
            UUID uuid = entry.getKey();
            CompletableFuture<Void> save = lastSaves.get(uuid);
            if (entry.getValue() > cutoff || Bukkit.getPlayer(uuid) != null || loading.containsKey(uuid)
                    || unresolvedBalances.contains(uuid) || saveVersions.containsKey(uuid) || pendingDatabaseSync.contains(uuid)
                    || save != null && (!save.isDone() || save.isCompletedExceptionally())) {
                continue;
            }
            balances.remove(uuid);
            playerNames.remove(uuid);
            unresolvedBalances.remove(uuid);
            lastSaves.remove(uuid);
            lastAccess.remove(uuid);
        }
    }

    public OperationResult addSouls(UUID uuid, long requested, String reason) {
        if (unresolvedBalances.contains(uuid) || requested <= 0) {
            return OperationResult.failure();
        }
        SoulEarnEvent event = new SoulEarnEvent(uuid, requested, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return OperationResult.failure();
        }
        long amount = Math.max(0, event.getAmount());
        long actual = Math.min(amount, getRemainingCapacity(uuid));
        if (actual <= 0) {
            return OperationResult.failure();
        }
        long balance = getBalance(uuid) + actual;
        balances.put(uuid, balance);
        persist(uuid, balance, reason);
        applyBoost(uuid);
        return OperationResult.success(actual, balance);
    }

    public OperationResult spendSouls(UUID uuid, long requested, String reason) {
        if (unresolvedBalances.contains(uuid) || requested <= 0) {
            return OperationResult.failure();
        }
        SoulSpendEvent event = new SoulSpendEvent(uuid, requested, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return OperationResult.failure();
        }
        long actual = Math.max(0, event.getAmount());
        if (actual <= 0 || getBalance(uuid) < actual) {
            return OperationResult.failure();
        }
        long balance = getBalance(uuid) - actual;
        balances.put(uuid, balance);
        persist(uuid, balance, reason);
        applyBoost(uuid);
        return OperationResult.success(actual, balance);
    }

    public OperationResult loseSouls(UUID uuid, long requested, String reason) {
        if (unresolvedBalances.contains(uuid) || requested <= 0) {
            return OperationResult.failure();
        }
        SoulLoseEvent event = new SoulLoseEvent(uuid, requested, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return OperationResult.failure();
        }
        long actual = Math.min(Math.max(0, event.getAmount()), getBalance(uuid));
        if (actual <= 0) {
            return OperationResult.failure();
        }
        long balance = getBalance(uuid) - actual;
        balances.put(uuid, balance);
        persist(uuid, balance, reason);
        applyBoost(uuid);
        return OperationResult.success(actual, balance);
    }

    public OperationResult setSouls(UUID uuid, long amount) {
        if (unresolvedBalances.contains(uuid)) {
            return OperationResult.failure();
        }
        long balance = Math.max(0, Math.min(config.maxBalance(), amount));
        balances.put(uuid, balance);
        persist(uuid, balance, "admin-set");
        applyBoost(uuid);
        return OperationResult.success(balance, balance);
    }

    public OperationResult transfer(UUID payer, UUID recipient, long requested) {
        if (unresolvedBalances.contains(payer) || unresolvedBalances.contains(recipient)
                || payer.equals(recipient) || requested <= 0) {
            return OperationResult.failure();
        }
        SoulSpendEvent spendEvent = new SoulSpendEvent(payer, requested, "pay");
        Bukkit.getPluginManager().callEvent(spendEvent);
        if (spendEvent.isCancelled()) {
            return OperationResult.failure();
        }
        SoulEarnEvent earnEvent = new SoulEarnEvent(recipient, requested, "pay");
        Bukkit.getPluginManager().callEvent(earnEvent);
        if (earnEvent.isCancelled()) {
            return OperationResult.failure();
        }
        long amount = Math.min(Math.max(0, spendEvent.getAmount()), Math.max(0, earnEvent.getAmount()));
        amount = Math.min(amount, getBalance(payer));
        amount = Math.min(amount, getRemainingCapacity(recipient));
        if (amount <= 0) {
            return OperationResult.failure();
        }
        long payerBalance = getBalance(payer) - amount;
        long recipientBalance = getBalance(recipient) + amount;
        balances.put(payer, payerBalance);
        balances.put(recipient, recipientBalance);
        persist(payer, payerBalance, "pay");
        persist(recipient, recipientBalance, "pay");
        applyBoost(payer);
        applyBoost(recipient);
        return OperationResult.success(amount, payerBalance);
    }

    public LocalSoulStore.StoredData localData(UUID uuid) {
        return localStore.load(uuid);
    }

    private synchronized CompletableFuture<Void> persist(UUID uuid, long balance, String reason) {
        lastAccess.put(uuid, System.nanoTime());
        String name = playerNames.getOrDefault(uuid, "");
        long maxBalance = config.maxBalance();
        Object version = new Object();
        saveVersions.put(uuid, version);
        pendingDatabaseSync.add(uuid);
        CompletableFuture<Void> localSave = localStore.saveAsync(uuid, name, balance, maxBalance, reason, true);
        if (!database.isAvailable()) {
            saveVersions.remove(uuid, version);
            lastSaves.put(uuid, localSave);
            return localSave;
        }
        CompletableFuture<Void> databaseSave = database.saveBalance(uuid, balance).handle((ignored, error) -> {
            synchronized (this) {
                if (error != null) {
                    plugin.getLogger().warning("Could not persist souls for " + uuid
                            + "; the local copy is marked for database recovery: " + error.getMessage());
                }
                if (!saveVersions.remove(uuid, version)) {
                    return CompletableFuture.<Void>completedFuture(null);
                }
                if (error == null) {
                    pendingDatabaseSync.remove(uuid);
                    return localStore.saveAsync(uuid, name, balance, maxBalance, reason, false);
                }
                return CompletableFuture.<Void>failedFuture(error);
            }
        }).thenCompose(completion -> completion);
        CompletableFuture<Void> completion = CompletableFuture.allOf(localSave, databaseSave);
        lastSaves.put(uuid, completion);
        return completion;
    }

    private void syncPendingBalance(UUID uuid, String name, long balance) {
        persist(uuid, balance, "database-recovery").exceptionally(error -> {
                plugin.getLogger().warning("Could not synchronize recovered local souls for " + uuid
                        + ": " + error.getMessage());
                return null;
        });
    }

    public void close() {
        localStore.close();
    }

    public void applyBoost(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        clearBoostEffects(player);
        if (!config.boostEnabled()) {
            return;
        }
        long stages = getBalance(uuid) / config.boostSoulsPerStep();
        if (stages <= 0) {
            return;
        }
        double soulProgress = config.maxBalance() <= 0
                ? 0
                : Math.min(1, getBalance(uuid) / (double) config.maxBalance());
        double effectMultiplier = 1 - (config.boostMaxNerf() * soulProgress);
        int amplifier = Math.max(0, (int) Math.floor(
                stages * config.boostAmplifierPerStep() * effectMultiplier));
        if (config.boostHaste()) {
            addBoostEffect(player, "FAST_DIGGING", amplifier);
        }
        if (config.boostStrength()) {
            addBoostEffect(player, "INCREASE_DAMAGE", amplifier);
        }
        if (config.boostSpeed()) {
            addBoostEffect(player, "SPEED", amplifier);
        }
        if (config.boostAbsorption()) {
            addBoostEffect(player, "ABSORPTION", amplifier);
        }
    }

    public void ensureBoost(Player player) {
        if (!config.boostEnabled() || getBalance(player.getUniqueId()) < config.boostSoulsPerStep()) {
            clearBoostEffects(player);
            return;
        }
        Set<PotionEffectType> owned = ownedBoostEffects.get(player.getUniqueId());
        if (owned == null || owned.isEmpty() || owned.stream().anyMatch(type -> !player.hasPotionEffect(type))) {
            applyBoost(player.getUniqueId());
        }
    }

    public void clearBoostEffects(Player player) {
        UUID uuid = player.getUniqueId();
        Set<PotionEffectType> owned = ownedBoostEffects.remove(uuid);
        if (owned != null) {
            for (PotionEffectType type : owned) {
                player.removePotionEffect(type);
            }
        }
        Map<PotionEffectType, PotionEffect> preserved = preservedBoostEffects.remove(uuid);
        if (preserved != null) {
            for (PotionEffect effect : preserved.values()) {
                player.addPotionEffect(effect, true);
            }
        }
    }

    private void addBoostEffect(Player player, String potionName, int amplifier) {
        XPotion.matchXPotion(potionName)
                .ifPresent(potion -> {
                    PotionEffectType type = potion.buildPotionEffect(PotionEffect.INFINITE_DURATION, 0).getType();
                    PotionEffect previous = player.getPotionEffect(type);
                    if (previous != null) {
                        preservedBoostEffects.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                                .put(type, previous);
                    }
                    PotionEffect effect = invisibleInfiniteEffect(
                            potion.buildPotionEffect(PotionEffect.INFINITE_DURATION, amplifier));
                    player.addPotionEffect(effect, true);
                    ownedBoostEffects.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(type);
                });
    }

    private PotionEffect invisibleInfiniteEffect(PotionEffect effect) {
        return new PotionEffect(effect.getType(), PotionEffect.INFINITE_DURATION, effect.getAmplifier(), true, false, false);
    }

    public record OperationResult(boolean successful, long amount, long balance) {
        public static OperationResult success(long amount, long balance) {
            return new OperationResult(true, amount, balance);
        }

        public static OperationResult failure() {
            return new OperationResult(false, 0, 0);
        }
    }
}
