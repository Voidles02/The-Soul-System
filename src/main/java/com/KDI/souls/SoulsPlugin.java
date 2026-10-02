package com.KDI.souls;

import com.KDI.souls.command.SoulsCommand;
import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.database.DatabaseManager;
import com.KDI.souls.gui.SoulStatsGui;
import com.KDI.souls.gui.SoulStatsListener;
import com.KDI.souls.guide.GuideCommand;
import com.KDI.souls.guide.GuideListener;
import com.KDI.souls.guide.GuideService;
import com.KDI.souls.listener.SoulListener;
import com.KDI.souls.message.MessageService;
import com.KDI.souls.placeholder.SoulsPlaceholderExpansion;
import com.KDI.souls.recipe.SoulRecipeService;
import com.KDI.souls.service.FragmentService;
import com.KDI.souls.service.SoulService;
import com.KDI.souls.service.SuperweaponService;
import com.KDI.souls.structure.SoulStructureService;
import com.KDI.souls.system.PluginSystemsChecker;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.util.HexFormat;

public final class SoulsPlugin extends JavaPlugin {
    private SoulsConfig soulsConfig;
    private MessageService messageService;
    private DatabaseManager databaseManager;
    private SoulService soulService;
    private FragmentService fragmentService;
    private SoulRecipeService recipeService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);

        soulsConfig = new SoulsConfig(this);
        messageService = new MessageService(this);
        databaseManager = new DatabaseManager(this, soulsConfig);
        try {
            databaseManager.initialize();
        } catch (Exception exception) {
            getLogger().severe("Could not initialize the Souls database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        soulService = new SoulService(this, databaseManager, soulsConfig);
        fragmentService = new FragmentService(this, soulService, soulsConfig);
        recipeService = new SoulRecipeService(this, soulsConfig);
        recipeService.registerRecipes();
        SoulStructureService structures = new SoulStructureService(this);
        GuideService guideService = new GuideService(this, soulsConfig);

        getServer().getPluginManager().registerEvents(
                new SoulListener(this, soulService, fragmentService, databaseManager, soulsConfig, messageService), this);
        getServer().getPluginManager().registerEvents(new SoulStatsListener(guideService), this);
        getServer().getPluginManager().registerEvents(new GuideListener(guideService), this);
        com.KDI.souls.listener.SoulAltarListener altar =
                new com.KDI.souls.listener.SoulAltarListener(this, soulService, soulsConfig, messageService, structures);
        getServer().getPluginManager().registerEvents(altar, this);
        getServer().getPluginManager().registerEvents(
                new com.KDI.souls.listener.SoulShrineListener(this, soulService, fragmentService, soulsConfig, messageService, structures), this);
        getServer().getScheduler().runTaskTimer(this,
                () -> Bukkit.getOnlinePlayers().forEach(soulService::ensureBoost), 40L, 40L);

        SuperweaponService superweapons = new SuperweaponService(this, altar, messageService);
        getServer().getPluginManager().registerEvents(superweapons, this);
        SoulsCommand command = new SoulsCommand(this, soulService, fragmentService, databaseManager,
                soulsConfig, messageService, new SoulStatsGui(this, soulService, fragmentService, soulsConfig, databaseManager), superweapons);
        getCommand("souls").setExecutor(command);
        getCommand("souls").setTabCompleter(command);
        getCommand("guide").setExecutor(new GuideCommand(guideService, messageService));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SoulsPlaceholderExpansion(this, soulService, soulsConfig).register();
            getLogger().info("PlaceholderAPI support enabled.");
        }

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                sendRequiredResourcePack(event.getPlayer());
            }

            @EventHandler
            public void onServerLoad(ServerLoadEvent event) {
                if (event.getType() != ServerLoadEvent.LoadType.STARTUP) {
                    return;
                }
                getServer().getScheduler().runTask(SoulsPlugin.this, () -> {
                    java.util.List<String> updatedFiles = new PluginSystemsChecker(SoulsPlugin.this).checkAndUpdate();
                    logStartupBanner(updatedFiles);
                });
            }
        }, this);
    }

    private void sendRequiredResourcePack(Player player) {
        String url = getConfig().getString("resource-pack.url", "").trim();
        if (url.isEmpty()) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            getLogger().warning("resource-pack.url must be a publicly accessible HTTP or HTTPS URL.");
            return;
        }

        String sha1 = getConfig().getString("resource-pack.sha1", "").trim();
        byte[] hash = null;
        if (!sha1.isEmpty()) {
            try {
                hash = HexFormat.of().parseHex(sha1);
            } catch (IllegalArgumentException exception) {
                getLogger().warning("resource-pack.sha1 must be a 40-character hexadecimal SHA-1 hash.");
                return;
            }
            if (hash.length != 20) {
                getLogger().warning("resource-pack.sha1 must be a 40-character hexadecimal SHA-1 hash.");
                return;
            }
        }

        player.setResourcePack(url, hash, true);
    }

    private void logStartupBanner(java.util.List<String> updatedFiles) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576;
        long maxMemory = runtime.maxMemory() / 1_048_576;
        String resourcePackUrl = getConfig().getString("resource-pack.url", "").trim();
        boolean placeholderApiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        getLogger().info("§a╔" + "═".repeat(76) + "╗");
        logBannerLine("SOULS  //  STARTUP REPORT");
        logBannerLine("STATUS       ONLINE  |  Prefix: Enhanced");
        logBannerLine("");
        logBannerLine("PLUGIN");
        logBannerLine("Name         " + getDescription().getName());
        logBannerLine("Version      " + getDescription().getVersion());
        logBannerLine("Authors      " + String.join(", ", getDescription().getAuthors()));
        String website = getDescription().getWebsite();
        logBannerLine("Website      " + (website == null ? "not set" : website));
        logBannerLine("");
        logBannerLine("SERVER ENVIRONMENT");
        logBannerLine("Server       " + Bukkit.getName() + " " + Bukkit.getVersion());
        logBannerLine("Bukkit API   " + Bukkit.getBukkitVersion());
        logBannerLine("Java         " + System.getProperty("java.version"));
        logBannerLine("Platform     " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        logBannerLine("Memory       " + usedMemory + " MB used / " + maxMemory + " MB max");
        logBannerLine("");
        logBannerLine("SERVER STATUS");
        logBannerLine("Players      " + Bukkit.getOnlinePlayers().size() + " online / " + Bukkit.getMaxPlayers() + " max");
        logBannerLine("Worlds       " + Bukkit.getWorlds().size());
        logBannerLine("Plugins      " + Bukkit.getPluginManager().getPlugins().length + " loaded");
        logBannerLine("PlaceholderAPI " + (placeholderApiEnabled ? "enabled" : "not detected"));
        logBannerLine("Resource pack " + (resourcePackUrl.isEmpty() ? "not configured" : "configured"));
        logBannerLine("");
        logBannerLine("SOULS SYSTEMS");
        logBannerLine("Progression  Souls, fragments, boosts");
        logBannerLine("Content      Altars, shrines, recipes");
        logBannerLine("Weapons      Sarculum and Book of Bōc");
        logBannerLine("Interfaces   Guide, stats, commands");
        logBannerLine("Storage      Database initialized");
        logBannerLine("");
        logBannerLine("STARTUP CHECK  |  " + updatedFiles.size() + " file(s) updated");
        if (updatedFiles.isEmpty()) {
            logBannerLine("No system files needed updating");
        } else {
            updatedFiles.forEach(file -> logBannerLine("- " + file));
        }
        getLogger().info("§a╚" + "═".repeat(76) + "╝");
    }

    private void logBannerLine(String text) {
        String value = text.length() > 76 ? text.substring(0, 73) + "..." : text;
        getLogger().info("§a║ " + String.format("%-76s", value) + " ║");
    }

    @Override
    public void onDisable() {
        try {
            if (soulService != null) {
                soulService.persistAll().join();
            }
        } catch (Exception exception) {
            getLogger().severe("Could not persist soul balances during shutdown: " + exception.getMessage());
        } finally {
            if (databaseManager != null) {
                databaseManager.close();
            }
        }
    }

    public void reloadSettings() {
        reloadConfig();
        soulsConfig.reload();
        messageService.reload();
        recipeService.registerRecipes();
    }

    public SoulsConfig getSoulsConfig() {
        return soulsConfig;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public SoulService getSoulService() {
        return soulService;
    }

    public FragmentService getFragmentService() {
        return fragmentService;
    }
}
