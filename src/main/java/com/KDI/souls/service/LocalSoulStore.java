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
import java.util.List;
import java.util.UUID;

public final class LocalSoulStore {
    private final JavaPlugin plugin;
    private final SoulsConfig config;
    private final File playersFolder;

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
            return new StoredData(uuid, unquote(value(lines, "name", "")), souls, maxSouls,
                    percentage, lastSaved, unquote(value(lines, "last-save-reason", "unknown")));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not read local soul data for " + uuid + ": " + exception.getMessage());
            return null;
        }
    }

    public void save(UUID uuid, String name, long souls, long maxSouls, String reason) {
        if (!config.localDataEnabled()) {
            return;
        }
        try {
            playersFolder.mkdirs();
            long now = System.currentTimeMillis();
            double percentage = maxSouls <= 0 ? 0 : (souls * 100D) / maxSouls;
            String content = "data-version: 1\n"
                    + "uuid: " + uuid + "\n"
                    + "name: " + quote(name) + "\n"
                    + "souls: " + souls + "\n"
                    + "max-souls: " + maxSouls + "\n"
                    + "percentage: " + String.format(java.util.Locale.ROOT, "%.2f", percentage) + "\n"
                    + "last-saved: " + now + "\n"
                    + "last-saved-utc: " + Instant.ofEpochMilli(now) + "\n"
                    + "last-save-reason: " + quote(reason) + "\n";
            Path target = playerFile(uuid).toPath();
            Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temporary, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save local soul data for " + uuid + ": " + exception.getMessage());
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
                             long lastSaved, String lastSaveReason) {
    }
}