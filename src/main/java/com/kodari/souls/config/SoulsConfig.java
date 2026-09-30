package com.kodari.souls.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulsConfig {
    private final JavaPlugin plugin;

    public SoulsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
    }

    public String databaseType() {
        return plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
    }

    public String sqliteFile() {
        return plugin.getConfig().getString("database.file", "souls.db");
    }

    public String databaseHost() {
        return plugin.getConfig().getString("database.host", "localhost");
    }

    public int databasePort() {
        return plugin.getConfig().getInt("database.port", 3306);
    }

    public String databaseName() {
        return plugin.getConfig().getString("database.name", "souls");
    }

    public String databaseUsername() {
        return plugin.getConfig().getString("database.username", "root");
    }

    public String databasePassword() {
        return plugin.getConfig().getString("database.password", "change-me");
    }

    public String databaseParameters() {
        return plugin.getConfig().getString("database.parameters", "");
    }

    public long maxBalance() {
        return Math.max(0, plugin.getConfig().getLong("souls.max-balance", 1000));
    }

    public boolean localDataEnabled() {
        return plugin.getConfig().getBoolean("local-data.enabled", true);
    }

    public String localDataFolder() {
        String folder = plugin.getConfig().getString("local-data.folder", "server-data");
        return folder == null || folder.isBlank() ? "server-data" : folder;
    }

    public boolean pveEnabled() {
        return plugin.getConfig().getBoolean("earning.pve.enabled", true);
    }

    public long pveReward(EntityType type) {
        return Math.max(0, plugin.getConfig().getLong("earning.pve.rewards." + type.name(), 0));
    }

    public boolean pvpEnabled() {
        return plugin.getConfig().getBoolean("earning.pvp.enabled", true);
    }

    public long pvpBaseReward() {
        return Math.max(0, plugin.getConfig().getLong("earning.pvp.base-reward", 10));
    }

    public long pvpCooldownSeconds() {
        return Math.max(0, plugin.getConfig().getLong("earning.pvp.cooldown-seconds", 3600));
    }

    public long pvpDiminishingPeriodSeconds() {
        return Math.max(0, plugin.getConfig().getLong("earning.pvp.diminishing-period-seconds", 86400));
    }

    public double pvpDiminishingFactor() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("earning.pvp.diminishing-factor", 0.5)));
    }

    public boolean fragmentsEnabled() {
        return plugin.getConfig().getBoolean("fragments.enabled", true);
    }

    public long fragmentConversionRate() {
        return Math.max(1, plugin.getConfig().getLong("fragments.conversion-rate", 10));
    }

    public String fragmentMaterial() {
        return plugin.getConfig().getString("fragments.item-material", "PRISMARINE_SHARD");
    }

    public String fragmentName() {
        return plugin.getConfig().getString("fragments.item-name", "&bSoul Fragment");
    }

    public long pveFragmentDrop(EntityType type) {
        return Math.max(0, plugin.getConfig().getLong("fragments.pve-drops." + type.name(), 0));
    }

    public boolean boostEnabled() {
        return plugin.getConfig().getBoolean("boost.enabled", true);
    }

    public long boostSoulsPerStep() {
        return Math.max(1, plugin.getConfig().getLong("boost.souls-per-step", 100));
    }

    public double boostAmplifierPerStep() {
        return Math.max(0, plugin.getConfig().getDouble("boost.amplifier-per-step", 0.5));
    }

    public double boostMaxNerf() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("boost.max-nerf", 0.80)));
    }

    public boolean boostHaste() {
        return plugin.getConfig().getBoolean("boost.haste", true);
    }

    public boolean boostStrength() {
        return plugin.getConfig().getBoolean("boost.strength", true);
    }

    public boolean boostSpeed() {
        return plugin.getConfig().getBoolean("boost.speed", true);
    }

    public boolean boostAbsorption() {
        return plugin.getConfig().getBoolean("boost.absorption", true);
    }

    public boolean lossEnabled() {
        return plugin.getConfig().getBoolean("loss.enabled", true);
    }

    public double lossPercentage() {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble("loss.percentage", 0.50)));
    }

    public long lossMaximum() {
        return Math.max(0, plugin.getConfig().getLong("loss.maximum", 0));
    }

    public boolean worldEnabled(String worldName, String system) {
        String worldPath = "worlds." + worldName;
        ConfigurationSection world = plugin.getConfig().getConfigurationSection(worldPath);
        String path = world == null ? "worlds.default" : worldPath;
        return plugin.getConfig().getBoolean(path + ".enabled", true)
                && plugin.getConfig().getBoolean(path + "." + system, true);
    }

    public boolean featureEnabled(String feature) {
        return plugin.getConfig().getBoolean("features." + feature + ".enabled", true);
    }

    public boolean guideAutoOpen() {
        return plugin.getConfig().getBoolean("guide.auto-open", true);
    }

    public long altarCost() {
        return Math.max(1, plugin.getConfig().getLong("altar.cost", 50));
    }

    public String altarPermission() {
        return plugin.getConfig().getString("altar.permission", "souls.altar");
    }

    public double altarDamageMin() {
        return boundedPercent("altar.upgrades.damage.min", 0.02);
    }

    public double altarDamageMax() {
        return Math.max(altarDamageMin(), boundedPercent("altar.upgrades.damage.max", 0.30));
    }

    public double altarDurabilityMin() {
        return boundedPercent("altar.upgrades.durability.min", 0.02);
    }

    public double altarDurabilityMax() {
        return Math.max(altarDurabilityMin(), boundedPercent("altar.upgrades.durability.max", 0.35));
    }

    public double altarLifestealMin() {
        return boundedPercent("altar.upgrades.lifesteal.min", 0.02);
    }

    public double altarLifestealMax() {
        return Math.max(altarLifestealMin(), boundedPercent("altar.upgrades.lifesteal.max", 0.20));
    }

    public double altarAttractMin() {
        return boundedPercent("altar.upgrades.attract.min", 0.02);
    }

    public double altarAttractMax() {
        return Math.max(altarAttractMin(), boundedPercent("altar.upgrades.attract.max", 0.20));
    }

    public double altarCritMin() {
        return boundedPercent("altar.upgrades.crit.min", 0.0225);
    }

    public double altarCritMax() {
        return Math.max(altarCritMin(), boundedPercent("altar.upgrades.crit.max", 0.045));
    }

    public double altarDamageCap() {
        return positiveDouble("altar.caps.damage", 1.50);
    }

    public double altarDurabilityCap() {
        return positiveDouble("altar.caps.durability", 3.00);
    }

    public double altarLifestealCap() {
        return boundedPercent("altar.caps.lifesteal", 0.70);
    }

    public double altarAttractCap() {
        return boundedPercent("altar.caps.attract", 0.70);
    }

    public double altarCritCap() {
        return Math.min(0.45, positiveDouble("altar.caps.crit", 0.45));
    }

    public String altarSound() {
        return plugin.getConfig().getString("altar.feedback.sound", "BLOCK_ENCHANTMENT_TABLE_USE");
    }

    public String altarParticle() {
        return plugin.getConfig().getString("altar.feedback.particle", "ENCHANT");
    }

    public long shrineCooldownHours() {
        return Math.max(0, plugin.getConfig().getLong("shrine.cooldown-hours", 2));
    }

    public String shrinePermission() {
        return plugin.getConfig().getString("shrine.permission", "souls.shrine");
    }

    public String shrineRewardType() {
        return plugin.getConfig().getString("shrine.reward.type", "random");
    }

    public long shrineSoulReward() {
        return Math.max(1, plugin.getConfig().getLong("shrine.reward.souls", 25));
    }

    public long shrineFragmentReward() {
        return Math.max(1, plugin.getConfig().getLong("shrine.reward.fragments", 2));
    }

    public double shrineEventChance() {
        return boundedPercent("shrine.reward.event-chance", 1.0);
    }

    public boolean shrineEventBroadcast() {
        return plugin.getConfig().getBoolean("shrine.reward.broadcast", true);
    }

    public String shrineBuffType() {
        return plugin.getConfig().getString("shrine.reward.buff.type", "SPEED");
    }

    public int shrineBuffDurationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("shrine.reward.buff.duration-seconds", 300));
    }

    public int shrineBuffAmplifier() {
        return Math.max(0, plugin.getConfig().getInt("shrine.reward.buff.amplifier", 1));
    }

    public String shrineSound() {
        return plugin.getConfig().getString("shrine.feedback.sound", "BLOCK_BEACON_ACTIVATE");
    }

    public String shrineParticle() {
        return plugin.getConfig().getString("shrine.feedback.particle", "SOUL_FIRE_FLAME");
    }

    private double boundedPercent(String path, double fallback) {
        return Math.max(0, Math.min(1, plugin.getConfig().getDouble(path, fallback)));
    }

    private double positiveDouble(String path, double fallback) {
        return Math.max(0, plugin.getConfig().getDouble(path, fallback));
    }
}
