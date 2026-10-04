package com.KDI.souls;

import com.KDI.souls.command.SoulsCommand;
import com.KDI.souls.command.SoulsPerformanceCommand;
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
import com.KDI.souls.service.ResourcePackHost;
import com.KDI.souls.service.SoulService;
import com.KDI.souls.service.SuperweaponService;
import com.KDI.souls.structure.SoulStructureService;
import com.KDI.souls.system.PluginSystemsChecker;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SoulsPlugin extends JavaPlugin {
    private static final int MAX_RESOURCE_PACK_RETRIES = 2;
    private final Map<UUID, Integer> resourcePackRetries = new HashMap<>();
    private final Map<UUID, UUID> resourcePackRequests = new HashMap<>();
    private SoulsConfig soulsConfig;
    private ResourcePackHost resourcePackHost;
    private MessageService messageService;
    private DatabaseManager databaseManager;
    private volatile boolean databaseAvailable;
    private volatile boolean databaseInitializationComplete;
    private SoulService soulService;
    private FragmentService fragmentService;
    private SoulRecipeService recipeService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        soulsConfig = new SoulsConfig(this);
        resourcePackHost = new ResourcePackHost(this);
        try {
            resourcePackHost.start();
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().severe("Could not start the embedded resource pack host: " + exception.getMessage());
            resourcePackHost.stop();
        }
        messageService = new MessageService(this);
        databaseManager = new DatabaseManager(this, soulsConfig);
        databaseManager.initialize().whenComplete((ignored, error) -> {
            if (!isEnabled()) {
                return;
            }
            getServer().getScheduler().runTask(this, () -> {
                databaseInitializationComplete = true;
                databaseAvailable = error == null;
                if (error == null) {
                    return;
                }
                getLogger().severe("Could not initialize the Souls database: " + error.getMessage());
                if (!soulsConfig.localDataEnabled()) {
                    getLogger().severe("Local player storage is disabled; Souls cannot start without a database.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                getLogger().warning("Continuing with local player-data fallback. Database-backed leaderboards and combat stats may be unavailable.");
            });
        });

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
        getServer().getScheduler().runTaskTimer(this, soulService::evictOfflineBalances, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (databaseManager.isAvailable()) {
                databaseAvailable = true;
                return;
            }
            databaseAvailable = false;
            databaseManager.retryInitialize().thenAccept(recovered -> {
                if (!recovered || !isEnabled()) {
                    return;
                }
                getServer().getScheduler().runTask(this, () -> {
                    databaseAvailable = true;
                    getLogger().info("Souls database connection recovered; synchronizing cached balances.");
                    soulService.persistAll().exceptionally(error -> {
                        getLogger().severe("Could not synchronize cached Souls balances after database recovery: "
                                + error.getMessage());
                        return null;
                    });
                });
            });
        }, 1200L, 1200L);

        SuperweaponService superweapons = new SuperweaponService(this, altar, messageService);
        getServer().getPluginManager().registerEvents(superweapons, this);
        SoulsCommand command = new SoulsCommand(this, soulService, fragmentService, databaseManager,
                soulsConfig, messageService, new SoulStatsGui(this, soulService, fragmentService, soulsConfig, databaseManager), superweapons);
        getCommand("souls").setExecutor(command);
        getCommand("souls").setTabCompleter(command);
        getCommand("guide").setExecutor(new GuideCommand(guideService, messageService));
        getCommand("souls-performance").setExecutor(
                new SoulsPerformanceCommand(this, messageService));

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new SoulsPlaceholderExpansion(this, soulService, soulsConfig).register();
            getLogger().info("PlaceholderAPI support enabled.");
        }

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                resourcePackRetries.remove(player.getUniqueId());
                resourcePackRequests.remove(player.getUniqueId());
                getServer().getScheduler().runTaskLater(SoulsPlugin.this, () -> {
                    if (player.isOnline()) {
                        sendRequiredResourcePack(player);
                    }
                }, 1L);
            }

            @EventHandler
            public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
                Player player = event.getPlayer();
                UUID playerId = player.getUniqueId();
                if (!event.getID().equals(resourcePackRequests.get(playerId))) {
                    return;
                }
                String status = event.getStatus().name();

                if ("SUCCESSFULLY_LOADED".equals(status)) {
                    resourcePackRetries.remove(playerId);
                    resourcePackRequests.remove(playerId);
                    getLogger().info("Resource pack loaded by " + player.getName() + ".");
                    return;
                }
                if ("DECLINED".equals(status)) {
                    resourcePackRequests.remove(playerId);
                    getLogger().warning(player.getName() + " declined the required resource pack.");
                    return;
                }
                if ("INVALID_URL".equals(status)) {
                    resourcePackRequests.remove(playerId);
                    getLogger().severe("The embedded resource pack URL was rejected by the client.");
                    return;
                }
                if (!"FAILED_DOWNLOAD".equals(status) && !"FAILED_RELOAD".equals(status)) {
                    return;
                }

                int retries = resourcePackRetries.getOrDefault(playerId, 0);
                if (retries >= MAX_RESOURCE_PACK_RETRIES) {
                    resourcePackRequests.remove(playerId);
                    resourcePackRetries.remove(playerId);
                    getLogger().warning("Resource pack failed for " + player.getName()
                            + " after " + retries + " retries.");
                    return;
                }

                int nextRetry = retries + 1;
                resourcePackRetries.put(playerId, nextRetry);
                getLogger().warning("Resource pack failed for " + player.getName() + " ("
                        + status + "); retrying in 2 seconds (" + nextRetry + "/"
                        + MAX_RESOURCE_PACK_RETRIES + ").");
                getServer().getScheduler().runTaskLater(SoulsPlugin.this, () -> {
                    if (player.isOnline() && resourcePackRetries.getOrDefault(playerId, 0) == nextRetry) {
                        sendRequiredResourcePack(player);
                    }
                }, 40L);
            }

            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                resourcePackRetries.remove(event.getPlayer().getUniqueId());
                resourcePackRequests.remove(event.getPlayer().getUniqueId());
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
        if (resourcePackHost == null || resourcePackHost.getPublicUrl() == null) {
            return;
        }
        UUID packId = UUID.randomUUID();
        resourcePackRequests.put(player.getUniqueId(), packId);
        player.addResourcePack(packId, resourcePackHost.getPublicUrl(), resourcePackHost.getSha1Bytes(),
                "Souls custom item textures", true);
    }

    private void logStartupBanner(java.util.List<String> updatedFiles) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576;
        long maxMemory = runtime.maxMemory() / 1_048_576;
        boolean resourcePackAvailable = resourcePackHost != null && resourcePackHost.getPublicUrl() != null;
        boolean placeholderApiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        boolean systemFilesPresent = new java.io.File(getDataFolder(), "config.yml").isFile()
                && new java.io.File(getDataFolder(), "messages.yml").isFile();
        boolean commandsRegistered = getCommand("souls") != null && getCommand("guide") != null;
        String storageStatus = databaseAvailable ? "Database initialized"
                : databaseInitializationComplete ? "Unavailable; local fallback active" : "Database initialization pending";

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
        logBannerLine("Resource pack " + (resourcePackAvailable ? "self-hosted" : "host/pack unavailable"));
        logBannerLine("System files  " + (systemFilesPresent ? "config.yml and messages.yml present" : "managed file missing"));
        logBannerLine("Commands     " + (commandsRegistered ? "/souls and /guide registered" : "required command missing"));
        logBannerLine("");
        logBannerLine("SOULS SYSTEMS");
        logBannerLine("Progression  Souls, fragments, boosts");
        logBannerLine("Content      Altars, shrines, recipes");
        logBannerLine("Weapons      Sarculum and Book of Bōc");
        logBannerLine("Interfaces   Guide, stats, commands");
        logBannerLine("Storage      " + storageStatus);
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
                Bukkit.getOnlinePlayers().forEach(soulService::clearBoostEffects);
                soulService.persistAll().get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            getLogger().warning("Interrupted while persisting soul balances during shutdown.");
        } catch (Exception exception) {
            getLogger().severe("Could not persist soul balances during shutdown: " + exception.getMessage());
        } finally {
            if (resourcePackHost != null) {
                resourcePackHost.stop();
                resourcePackHost = null;
            }
            if (databaseManager != null) {
                databaseManager.close();
            }
            if (soulService != null) {
                soulService.close();
            }
        }
    }

    public java.util.concurrent.CompletableFuture<Boolean> reloadSettings() {
        reloadConfig();
        soulsConfig.reload();
        messageService.reload();
        recipeService.registerRecipes();
        return databaseManager.initialize().handle((ignored, error) -> {
            databaseAvailable = error == null;
            if (error != null) {
                getLogger().severe("Could not initialize the reloaded Souls database settings: " + error.getMessage());
            }
            return error == null;
        });
    }

    public ResourcePackHost.AuditResult repairResourcePack() {
        if (resourcePackHost == null) {
            resourcePackHost = new ResourcePackHost(this);
        }
        return resourcePackHost.repairAndAudit();
    }

    public int reapplyResourcePackToOnlinePlayers() {
        if (resourcePackHost == null || resourcePackHost.getPublicUrl() == null) {
            return 0;
        }
        int sent = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            resourcePackRetries.remove(playerId);
            resourcePackRequests.remove(playerId);
            sendRequiredResourcePack(player);
            sent++;
        }
        return sent;
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
