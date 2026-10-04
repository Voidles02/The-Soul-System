package com.KDI.souls.service;

import com.KDI.souls.config.SoulsConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class LocalSoulStore {
    private final JavaPlugin plugin;
    private final SoulsConfig config;
    private final File playersFolder;
    private final Map<UUID, PendingSave> pendingWrites = new LinkedHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "souls-local-storage");
        thread.setDaemon(true);
        return thread;
    });
    private boolean draining;

    public LocalSoulStore(JavaPlugin plugin, SoulsConfig config) {
        this.plugin = plugin;
        this.config = config;
        File root = new File(plugin.getDataFolder(), config.localDataFolder());
        this.playersFolder = new File(root, "players");
        playersFolder.mkdirs();
    }

    public StoredData load(UUID uuid) {
        if (!config.localDataEnabled()) {
            return null;
        }
        synchronized (pendingWrites) {
            PendingSave pending = pendingWrites.get(uuid);
            if (pending != null) {
                return pending.data();
            }
        }
        Path file = playerFile(uuid).toPath();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            long souls = Long.parseLong(value(lines, "souls", "0"));
            long maxSouls = Long.parseLong(value(lines, "max-souls", "0"));
            double percentage = Double.parseDouble(value(lines, "percentage", "0"));
            long lastSaved = Long.parseLong(value(lines, "last-saved", "0"));
            boolean databasePending = Boolean.parseBoolean(value(lines, "database-pending", "false"));
            return new StoredData(uuid, unquote(value(lines, "name", "")), souls, maxSouls,
                    percentage, lastSaved, unquote(value(lines, "last-save-reason", "unknown")), databasePending);
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not read local soul data for " + uuid + ": " + exception.getMessage());
            return null;
        }
    }

    public void save(UUID uuid, String name, long souls, long maxSouls, String reason, boolean databasePending) {
        saveAsync(uuid, name, souls, maxSouls, reason, databasePending);
    }

    public CompletableFuture<Void> saveAsync(UUID uuid, String name, long souls, long maxSouls,
                                              String reason, boolean databasePending) {
        if (!config.localDataEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        StoredData data = new StoredData(uuid, name, souls, maxSouls,
                maxSouls <= 0 ? 0 : (souls * 100D) / maxSouls,
                System.currentTimeMillis(), reason, databasePending);
        synchronized (pendingWrites) {
            if (writer.isShutdown()) {
                return CompletableFuture.failedFuture(new IllegalStateException("Local soul storage is closed"));
            }
            PendingSave previous = pendingWrites.get(uuid);
            CompletableFuture<Void> completion = previous == null
                    ? new CompletableFuture<>() : previous.completion();
            pendingWrites.put(uuid, new PendingSave(data, completion));
            if (!draining) {
                draining = true;
                writer.execute(this::drainWrites);
            }
            return completion;
        }
    }

    private void drainWrites() {
        while (true) {
            PendingSave pending;
            synchronized (pendingWrites) {
                if (pendingWrites.isEmpty()) {
                    draining = false;
                    return;
                }
                pending = pendingWrites.values().iterator().next();
            }
            Exception failure = null;
            try {
                write(pending.data());
            } catch (Exception exception) {
                failure = exception;
                plugin.getLogger().warning("Could not save local soul data for " + pending.data().uuid()
                        + ": " + exception.getMessage());
            }
            synchronized (pendingWrites) {
                if (!pendingWrites.remove(pending.data().uuid(), pending)) {
                    continue;
                }
            }
            if (failure == null) {
                pending.completion().complete(null);
            } else {
                pending.completion().completeExceptionally(failure);
            }
        }
    }

    private void write(StoredData data) throws IOException {
        playersFolder.mkdirs();
        String content = "data-version: 1\n"
                + "uuid: " + data.uuid() + "\n"
                + "name: " + quote(data.name()) + "\n"
                + "souls: " + data.souls() + "\n"
                + "max-souls: " + data.maxSouls() + "\n"
                + "percentage: " + String.format(java.util.Locale.ROOT, "%.2f", data.percentage()) + "\n"
                + "last-saved: " + data.lastSaved() + "\n"
                + "last-saved-utc: " + Instant.ofEpochMilli(data.lastSaved()) + "\n"
                + "database-pending: " + data.databasePending() + "\n"
                + "last-save-reason: " + quote(data.lastSaveReason()) + "\n";
        Path target = playerFile(data.uuid()).toPath();
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void close() {
        synchronized (pendingWrites) {
            writer.shutdown();
        }
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Local soul storage is still flushing after 5 seconds.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    public File playersFolder() {
        return playersFolder;
    }

    private File playerFile(UUID uuid) {
        return new File(playersFolder, uuid + ".yml");
    }

    private String value(List<String> lines, String key, String fallback) {
        String prefix = key + ":";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return fallback;
    }

    private String quote(String value) {
        return "'" + (value == null ? "" : value.replace("'", "''").replace("\n", " ")) + "'";
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    public record StoredData(UUID uuid, String name, long souls, long maxSouls, double percentage,
                             long lastSaved, String lastSaveReason, boolean databasePending) {
    }

    private record PendingSave(StoredData data, CompletableFuture<Void> completion) {
    }
}