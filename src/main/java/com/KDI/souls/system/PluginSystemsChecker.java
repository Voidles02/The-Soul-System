package com.KDI.souls.system;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class PluginSystemsChecker {
    private static final List<String> MANAGED_RESOURCES = List.of("config.yml", "messages.yml");
    private final JavaPlugin plugin;

    public PluginSystemsChecker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> checkAndUpdate() {
        List<String> updatedFiles = new ArrayList<>();
        plugin.getLogger().info("Running Souls systems check...");
        try {
            for (String resourceName : MANAGED_RESOURCES) {
                if (updateYamlFile(resourceName)) {
                    updatedFiles.add(resourceName);
                }
            }
            plugin.getLogger().info(updatedFiles.isEmpty()
                    ? "Souls systems check complete. No files needed updating."
                    : "Souls systems check complete. Updated files: " + String.join(", ", updatedFiles));
        } catch (IOException exception) {
            plugin.getLogger().severe("Souls systems check failed: " + exception.getMessage());
        }
        return updatedFiles;
    }

    private boolean updateYamlFile(String resourceName) throws IOException {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.isFile()) {
            plugin.saveResource(resourceName, false);
            return true;
        }

        try (InputStream resource = plugin.getResource(resourceName)) {
            if (resource == null) {
                return false;
            }

            FileConfiguration current = YamlConfiguration.loadConfiguration(file);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            boolean changed = migrateLegacyDefaults(current, defaults, resourceName);
            for (String path : defaults.getKeys(true)) {
                if (!defaults.isConfigurationSection(path) && !current.contains(path)) {
                    current.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) {
                current.save(file);
            }
            return changed;
        }
    }

    private boolean migrateLegacyDefaults(FileConfiguration current, YamlConfiguration defaults, String resourceName) {
        if (!resourceName.equals("config.yml")) {
            return false;
        }
        boolean changed = false;
        changed |= replaceDefault(current, defaults, "altar.caps.speed", 1.20);
        changed |= replaceDefault(current, defaults, "altar.upgrades.speed.min", 0.01);
        changed |= replaceDefault(current, defaults, "altar.upgrades.speed.max", 0.04);
        return changed;
    }

    private boolean replaceDefault(FileConfiguration current, YamlConfiguration defaults, String path, double oldValue) {
        if (!current.contains(path) || !defaults.contains(path)
                || Math.abs(current.getDouble(path) - oldValue) > 0.000001) {
            return false;
        }
        current.set(path, defaults.get(path));
        return true;
    }
}