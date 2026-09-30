package com.KDI.souls.message;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class MessageService {
    private final JavaPlugin plugin;
    private FileConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        messages = plugin.getConfig();
        plugin.saveResource("messages.yml", false);
        plugin.getConfig().options().copyDefaults(true);
        messages = plugin.getConfig();
        try {
            org.bukkit.configuration.file.YamlConfiguration configuration =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.File(plugin.getDataFolder(), "messages.yml"));
            InputStream resource = plugin.getResource("messages.yml");
            if (resource != null) {
                try (InputStreamReader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                    configuration.setDefaults(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader));
                    configuration.options().copyDefaults(true);
                }
            }
            messages = configuration;
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not load messages.yml: " + exception.getMessage());
        }
    }

    public String get(String path) {
        return color(messages.getString(path, path));
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, Map.of());
    }

    public void send(CommandSender sender, String path, Map<String, Object> replacements) {
        String message = get(path);
        for (Map.Entry<String, Object> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        String prefix = get("prefix");
        sender.sendMessage(prefix + message);
    }

    public void sendList(CommandSender sender, String path) {
        for (String line : messages.getStringList(path)) {
            sender.sendMessage(color(line));
        }
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }
}
