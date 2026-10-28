package com.KDI.souls.guide;

import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.message.MessageService;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuideService {
    private final JavaPlugin plugin;
    private final SoulsConfig config;
    private final NamespacedKey openedKey;

    public GuideService(JavaPlugin plugin, SoulsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.openedKey = new NamespacedKey(plugin, "soul_guide_opened");
    }

    public void open(Player player) {
        ItemStack book = XMaterial.matchXMaterial("WRITTEN_BOOK").map(XMaterial::parseItem).orElse(null);
        if (book == null || !(book.getItemMeta() instanceof BookMeta meta)) {
            return;
        }
        long maxBalance = config.maxBalance();
        long soulsPerTier = config.boostSoulsPerStep();
        long maxTiers = maxBalance / soulsPerTier;
        long cooldownHours = config.shrineCooldownHours();
        meta.setTitle("The Soulbound Codex");
        meta.setAuthor("The Keepers of Souls");
        meta.setPages(
                page("&5&lTHE SOULBOUND CODEX", "&8A field guide for the living\n\n"
                        + "&7Your choices shape your Soul. Earn it, protect it, and use it to strengthen your path.\n\n"
                        + "&dBegin with &f/souls&d. This codex stays available with &f/guide&d."),
                page("&d&lYOUR FIRST STEPS", "&8A short path forward\n\n"
                        + "&71. Defeat configured hostile mobs to earn Souls and Soul Shards.\n"
                        + "&72. Open &f/souls &7to view your balance and progression.\n"
                        + "&73. Build a Soul Altar to upgrade compatible gear.\n"
                        + "&74. Find or craft a Shrine for a repeatable reward.\n\n"
                        + "&7The server may adjust rewards and features."),
                page("&b&lGATHER SOULS", "&8Earn through the world\n\n"
                        + "&7Configured hostile mobs can reward Souls. The default rewards include Zombies, Skeletons and Spiders; tougher enemies can grant more.\n\n"
                        + "&7Player kills may also grant Souls when enabled. A cooldown and diminishing rewards help prevent farming.\n\n"
                        + "&8Exact amounts depend on server settings."),
                page("&4&lTHE PRICE OF DEATH", "&8Your reserve is not risk-free\n\n"
                        + "&7By default, death removes half of your current Souls. The server can disable this, change the percentage, or set a maximum loss.\n\n"
                        + "&7Keep your balance below the configured cap of &b" + maxBalance + "&7. Excess rewards cannot overflow it."),
                page("&5&lSOUL TIERS", "&8Power earned over time\n\n"
                        + "&7Every &b" + soulsPerTier + " Souls &7adds a progression step, up to &b" + maxTiers + "&7 at the current balance cap.\n\n"
                        + "&7Configured boosts can include Haste, Strength, Speed and Absorption. Losing Souls recalculates your tier.\n\n"
                        + "&8The server controls the power curve."),
                page("&3&lSOUL SHARDS", "&8Small fragments, real value\n\n"
                        + "&7Configured mobs can drop tagged Soul Shards. Only marked shards count; ordinary lookalike items do not.\n\n"
                        + "&7Hold one in your main hand and right-click to consume it for a random Soul reward. If you are at the balance cap, it is not consumed.\n\n"
                        + "&7Use &f/souls fragments &7to count yours."),
                page("&e&lCOMMANDS", "&8Your everyday tools\n\n"
                        + "&f/souls &7Open your profile\n"
                        + "&f/souls top &7View the top balances\n"
                        + "&f/souls pay <player> <amount> &7Transfer Souls\n"
                        + "&f/souls fragments &7Count Soul Shards\n"
                        + "&f/souls convert &7Convert shards\n"
                        + "&f/guide &7Open this codex\n\n"
                        + "&7Use &f/souls stats &7for your profile menu."),
                page("&6&lSOUL ALTAR", "&8The first rite\n\n"
                        + "&7Craft the marked Soul Altar:\n\n"
                        + "&6Gold &fNether Star &6Gold\n"
                        + "&bDiamond Block &8Crying Obsidian &bDiamond Block\n"
                        + "&8Obsidian &8Obsidian &8Obsidian\n\n"
                        + "&7Place it, right-click, and put a supported item or book in its input slot."),
                page("&6&lFORGE YOUR GEAR", "&8Enhancements with a cost\n\n"
                        + "&7Choose Upgrade in the Altar interface to roll a compatible enhancement. Weapons, tools and books have different options.\n\n"
                        + "&7The default base cost is &b50 Souls&7. Maximum values are configurable, and a roll is charged only when successfully applied.\n\n"
                        + "&8Soul Durability can prevent durability damage; it does not repair existing damage."),
                page("&d&lSOUL SHRINE", "&8A place of offering\n\n"
                        + "&7Craft the marked Shrine:\n\n"
                        + "&dAmethyst Shard &8Soul Sand &dAmethyst Shard\n"
                        + "&6Gold &fNether Star &6Gold\n"
                        + "&8Obsidian &8Obsidian &8Obsidian\n\n"
                        + "&7Place it and right-click the marked Beacon to claim its configured reward."),
                page("&d&lSHRINE BLESSING", "&8One player, one timer\n\n"
                        + "&7The default Shrine offers a random reward: &b25 Souls&7, &b2 Soul Shards&7, or &bSpeed II, Haste I and Jump Boost I for 6 minutes&7.\n\n"
                        + "&7Each player has a persistent cooldown of &b" + cooldownHours + " hours&7 by default. Rewards and feedback can be configured.\n\n"
                        + "&8The balance cap still applies."),
                page("&5&lARTIFACTS", "&8Relics of immense power\n\n"
                        + "&7When superweapons are enabled, authorized players can access the Book of Bōc and the Sarculum through the server's Souls commands.\n\n"
                        + "&7Their attacks have individual cooldowns. Use only where permitted, and ask staff about local rules.\n\n"
                        + "&8Artifact availability is controlled by the server."),
                page("&9&lPLAY FAIR, PLAY SAFE", "&8Permissions and help\n\n"
                        + "&7Players may need &fsouls.use&7, &fsouls.pay&7, &fsouls.altar&7, or &fsouls.shrine&7 for those features.\n\n"
                        + "&7Admins use &fsouls.admin&7. If a feature fails, check your permission, world settings, cooldown, and whether that feature is enabled.\n\n"
                        + "&dMay your next choice be wiser than your last."));
        book.setItemMeta(meta);
        player.openBook(book);
    }

    public void autoOpen(Player player) {
        if (!config.guideAutoOpen() || player.getPersistentDataContainer().has(openedKey, PersistentDataType.BYTE)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            player.getPersistentDataContainer().set(openedKey, PersistentDataType.BYTE, (byte) 1);
            open(player);
        }, 40L);
    }

    private String page(String title, String body) {
        return MessageService.color(title + "\n\n" + body);
    }
}