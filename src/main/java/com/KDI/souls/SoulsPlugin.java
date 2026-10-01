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
import org.bukkit.plugin.java.JavaPlugin;

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
        getServer().getPluginManager().registerEvents(new SoulStatsListener(), this);
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

    private void logStartupBanner(java.util.List<String> updatedFiles) {
        getLogger().info("§a╔════════════════════════════════════╗");
        logBannerLine("Souls Enabled");
        logBannerLine("Name: Souls");
        logBannerLine("Version: " + getDescription().getVersion());
        logBannerLine("Prefix: Major");
        logBannerLine("Files updated:");
        if (updatedFiles.isEmpty()) {
            logBannerLine("  None");
        } else {
            updatedFiles.forEach(file -> logBannerLine("  " + file));
        }
        getLogger().info("§a╚════════════════════════════════════╝");
    }

    private void logBannerLine(String text) {
        String value = text.length() > 34 ? text.substring(0, 31) + "..." : text;
        getLogger().info("§a║ " + String.format("%-34s", value) + " ║");
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
