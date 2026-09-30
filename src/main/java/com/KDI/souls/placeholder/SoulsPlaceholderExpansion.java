package com.KDI.souls.placeholder;

import com.KDI.souls.SoulsPlugin;
import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.service.SoulService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class SoulsPlaceholderExpansion extends PlaceholderExpansion {
    private final SoulsPlugin plugin;
    private final SoulService souls;
    private final SoulsConfig config;

    public SoulsPlaceholderExpansion(SoulsPlugin plugin, SoulService souls, SoulsConfig config) {
        this.plugin = plugin;
        this.souls = souls;
        this.config = config;
    }

    @Override
    public String getIdentifier() {
        return "souls";
    }

    @Override
    public String getAuthor() {
        return "Kodari";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return "0";
        }
        long balance = souls.getBalance(player.getUniqueId());
        return switch (params.toLowerCase()) {
            case "balance", "souls" -> String.valueOf(balance);
            case "max" -> String.valueOf(config.maxBalance());
            case "boost_level" -> String.valueOf(balance / config.boostSoulsPerStep());
            default -> null;
        };
    }
}
