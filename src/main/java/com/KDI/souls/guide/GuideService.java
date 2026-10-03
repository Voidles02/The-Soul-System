package com.KDI.souls.guide;

import com.KDI.souls.config.SoulsConfig;
import com.KDI.souls.message.MessageService;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

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
        meta.setTitle("Soul Guide");
        meta.setAuthor("Souls");
        long maxBalance = config.maxBalance();
        long soulsPerTier = config.boostSoulsPerStep();
        long maxTiers = maxBalance / Math.max(1, soulsPerTier);
        long maxNerf = Math.round(config.boostMaxNerf() * 100);
        meta.setPages(
                page("&b&lSOULS GUIDE", "&8Welcome to the Soul system!\n\n"
                        + "Earn Souls through combat, collect Fragments, unlock tiers, and enhance equipment at a Soul Altar.\n\n"
                        + "This guide explains the complete player journey, from your first Soul to the maximum reserve of &b" + maxBalance + "&7.\n\n"
                        + "Open your live profile with &f/souls&7. Your balance and combat statistics are saved to your UUID."),
                page("&d&lQUICK START", "&8Your first steps\n\n"
                        + "&f1. &7Defeat configured hostile mobs to earn Souls and Fragments.\n"
                        + "&f2. &7Use &f/souls &7to watch your balance and tier.\n"
                        + "&f3. &7Reach each threshold to unlock stronger automatic effects.\n"
                        + "&f4. &7Craft an Altar to improve compatible equipment.\n"
                        + "&f5. &7Craft a Shrine for a repeatable random reward.\n\n"
                        + "Keep your balance safe: player death removes a percentage of Souls by default."),
                page("&b&lBALANCE", "&8Soul memory\n\n"
                        + "Your current reserve is limited to &b" + maxBalance + " Souls&7. Any reward that would pass the limit is reduced to the remaining capacity.\n\n"
                        + "Balances are stored by player UUID, not by name. They load when you join and are saved during balance changes, logout, reload, and shutdown.\n\n"
                        + "Every &b" + soulsPerTier + " Souls &7unlocks one tier, for up to &b" + maxTiers + " tiers&7. Tier 0 has no boost, and losing Souls recalculates effects immediately.\n\n"
                        + "Use &f/souls top &7to compare the highest balances on the server."),
                page("&5&lSOUL EFFECTS", "&8Automatic powers\n\n"
                        + "Enabled Soul boosts can include Haste, Strength, Speed, and Absorption. They are applied automatically while you are online.\n\n"
                        + "Effects are recalculated after earning, spending, losing, or setting Souls, and are restored after joining or respawning.\n\n"
                        + "The server configuration controls which effects are enabled and how quickly tiers grow."),
                page("&4&lEFFECT SCALING", "&8A controlled power curve\n\n"
                        + "Soul effects grow with your tier instead of giving the full maximum power immediately. The final effect amplifier is also reduced progressively as your balance approaches the cap.\n\n"
                        + "At &b" + maxBalance + " Souls&7, the configured maximum reduction is &c" + maxNerf + "%&7 by default. Lower balances receive a smaller reduction, so early progression remains useful.\n\n"
                        + "This scaling applies to the automatic Soul effects, not temporary Shrine rewards or Altar item upgrades."),
                page("&a&lPVE EARNING", "&8Defeat hostile mobs\n\n"
                        + "Configured hostile mobs can award Souls when they die. The amount depends on the mob and the server configuration.\n\n"
                        + "Examples from the default setup:\n"
                        + "&7Zombie, Skeleton, Spider: &b1 Soul\n"
                        + "&7Creeper: &b2 Souls\n"
                        + "&7Blaze: &b3 Souls\n"
                        + "&7Enderman: &b4 Souls\n"
                        + "&7Wither Skeleton: &b5 Souls\n\n"
                        + "World feature settings can disable PvE earning."),
                page("&c&lPVP EARNING", "&8Player combat\n\n"
                        + "The default PvP reward is &b10 Souls &7for an eligible kill. A killer-versus-victim cooldown prevents repeated farming.\n\n"
                        + "The default cooldown is one hour. Repeated claims diminish by 50%, and the diminishing history resets after 24 hours.\n\n"
                        + "PvP rewards only work in worlds where PvP Soul earning is enabled."),
                page("&4&lDEATH LOSS", "&8The risk of carrying Souls\n\n"
                        + "By default, death removes &c50% &7of your current Souls. This loss is uncapped unless the server sets a maximum loss value.\n\n"
                        + "The loss setting is separate from PvE and PvP earning. A world can allow earning while disabling loss, or enable loss with different rules.\n\n"
                        + "Spend or transfer Souls before a dangerous fight if you want to reduce your risk."),
                page("&3&lSOUL FRAGMENTS", "&8Collectible currency\n\n"
                        + "Soul Fragments are tagged collectible items. Common mobs usually drop one, while dangerous mobs and bosses drop more.\n\n"
                        + "Default high-value drops include: Ravager 5, Elder Guardian 8, Warden 16, Wither 24, and Ender Dragon 32.\n\n"
                        + "Only tagged Soul Fragments count; ordinary items with the same material are not accepted."),
                page("&3&lUSING FRAGMENTS", "&8Two ways to convert them\n\n"
                        + "Hold a Soul Fragment in your main hand and right-click to consume one. It awards a random &b1-5 Souls&7. If your balance is full, the Fragment is not consumed.\n\n"
                        + "You can also use &f/souls convert&7. The default conversion is &b10 Fragments = 1 Soul&7, limited by your available Fragments and remaining balance capacity.\n\n"
                        + "Use &f/souls fragments &7to check your carried tagged Fragments."),
                page("&e&lPLAYER COMMANDS", "&8Everyday commands\n\n"
                        + "&f/souls &8or &f/soul &7- open your profile\n"
                        + "&f/souls stats &7- open your profile and stats\n"
                        + "&f/souls profile &7- open your profile\n"
                        + "&f/souls top &7- show the top 10 balances\n"
                        + "&f/souls pay <player> <amount> &7- transfer Souls\n"
                        + "&f/souls fragments &7- count your Fragments\n"
                        + "&f/souls convert &7- convert Fragments\n"
                        + "&f/guide &8or &f/soul-guide &7- open this book"),
                page("&e&lPROFILE MENU", "&8Read your progression\n\n"
                        + "The &f/souls &7menu displays your current balance and maximum, tier and maximum tier, next threshold, carried Fragments, kills, deaths, and K/D ratio.\n\n"
                        + "The Soul Tier icon shows the configured amplifier calculation. Actual effects also include the progressive maximum-balance reduction described earlier.\n\n"
                        + "Click the close button or press your inventory close key when finished."),
                page("&6&lSOUL ALTAR", "&8Crafting recipe\n\n"
                        + "Craft the tagged Altar in a crafting table:\n\n"
                        + "&6G &fN &6G\n"
                        + "&bD &8C &bD\n"
                        + "&8O &8O &8O\n\n"
                        + "G Gold Ingot   N Nether Star\n"
                        + "D Diamond Block   C Crying Obsidian\n"
                        + "O Obsidian\n\n"
                        + "The result is a special Enchanting Table. Ordinary Enchanting Tables are not Soul Altars."),
                page("&6&lALTAR SETUP", "&8Automatic registration\n\n"
                        + "Place the crafted Soul Altar. It is detected and registered automatically; no registration command is needed.\n\n"
                        + "Right-click the placed Altar to open its simple interface. Place one supported item or book in the center input slot.\n\n"
                        + "Click &6Upgrade &7to roll an available Soul upgrade. Close the menu to safely return the input item."),
                page("&6&lALTAR UPGRADES", "&8Spend Souls on equipment\n\n"
                        + "Weapons can roll Damage, Soul Durability, Crit, LifeSteal, or Attract when compatible. Crit increases critical-hit damage; each roll adds 2.25%-4.5% toward its 45% maximum. Pickaxes and hoes can roll Soul Durability or Yield. Books can roll Yield.\n\n"
                        + "The default base cost is &b50 Souls&7. Each Crit roll adds 5%-10% of the maximum effect and costs the same share of the base Altar cost. There is no Altar upgrade cooldown.\n\n"
                        + "The balance is charged only after a valid upgrade is rolled and applied."),
                page("&6&lALTAR LIMITS", "&8Upgrade caps\n\n"
                        + "Default maximum values are:\n"
                        + "&7Damage: &b150%\n"
                        + "&7Soul Durability: &b300%\n"
                        + "&7Crit: &b45%\n"
                        + "&7LifeSteal: &b70%\n"
                        + "&7Attract: &b70%\n"
                        + "&7Yield: &bIII\n\n"
                        + "An upgrade that would pass its cap is rejected. Ranges and caps can be changed by the server owner."),
                page("&b&lSOUL DURABILITY", "&8Better than Unbreaking\n\n"
                        + "Soul Durability is not a cosmetic enchantment. Each time a supported item would take durability damage, the upgrade gets a chance to ignore that damage event.\n\n"
                        + "Higher Soul Durability values provide a stronger save chance, up to the configured 300% cap. It works on supported weapons, pickaxes, and hoes.\n\n"
                        + "The effect is checked when damage is applied; it does not repair an item that is already damaged."),
                page("&a&lYIELD & COMPATIBILITY", "&8Books, tools, and restrictions\n\n"
                        + "Yield can be upgraded on pickaxes, hoes, and compatible books. Two equal Yield book levels can combine through an anvil into the next level, up to Yield III.\n\n"
                        + "LifeSteal and Attract cannot share the same item. LifeSteal and Attract are not valid on pickaxes, hoes, or books.\n\n"
                        + "Use the Altar interface to see whether the item in the input slot is supported before spending Souls."),
                page("&5&lSOUL SHRINE", "&8Crafting recipe\n\n"
                        + "Craft the tagged Shrine in a crafting table:\n\n"
                        + "&dA &8S &dA\n"
                        + "&6G &fN &6G\n"
                        + "&8O &8O &8O\n\n"
                        + "A Amethyst Shard   S Soul Sand\n"
                        + "G Gold Ingot   N Nether Star\n"
                        + "O Obsidian\n\n"
                        + "The result is a marked Beacon. A normal Beacon is not a Soul Shrine."),
                page("&5&lSHRINE DETECTION", "&8Place and click\n\n"
                        + "Place the crafted Soul Shrine. Automatic detection checks the Beacon block and its Soul Shrine marker, then registers it without a command.\n\n"
                        + "Right-click the placed Shrine to activate it. You need the Shrine permission and the Shrines feature must be enabled.\n\n"
                        + "Breaking it returns the marked Shrine item. Do not replace the block with an ordinary Beacon if you want Shrine behavior."),
                page("&d&lSHRINE REWARDS", "&8Random outcomes\n\n"
                        + "The default random reward can grant one of these outcomes:\n"
                        + "&7Souls: &b25 Souls\n"
                        + "&7Fragments: &b2 Soul Fragments\n"
                        + "&7Buff: &bSpeed for 5 minutes\n\n"
                        + "The Souls reward respects the balance cap. If Fragments are disabled, the Fragment outcome falls back to Souls. Server settings control reward mode, amounts, sound, particles, and broadcasts."),
                page("&d&lSHRINE COOLDOWN", "&8Use it regularly\n\n"
                        + "Each player has an individual Shrine cooldown. The default cooldown is &b2 hours&7, and the cooldown is stored persistently so restarts do not reset it.\n\n"
                        + "The Altar has no upgrade cooldown; this timer applies only to Shrine activation.\n\n"
                        + "Wait until your personal timer expires, then right-click the Shrine again."),
                page("&9&lACCESS & STORAGE", "&8Permissions and server tools\n\n"
                        + "Players normally need &fsouls.use &7for the commands, &fsouls.pay &7to transfer Souls, &fsouls.altar &7for Altars, and &fsouls.shrine &7for Shrines.\n\n"
                        + "Administrators use &fsouls.admin &7for management commands. Features and worlds can be enabled or disabled in the configuration.\n\n"
                        + "Admin commands include:\n"
                        + "&f/souls give <player> <amount>\n"
                        + "&f/souls take <player> <amount>\n"
                        + "&f/souls set <player> <amount>\n"
                        + "&f/souls remove <player> [amount|all]\n"
                        + "&f/souls inspect <player>\n"
                        + "&f/souls reload\n\n"
                        + "The default database is &fsouls.db &7in the plugin data folder. Stop the server before making a backup or editing it. The guide auto-opens once when &fguide.auto-open&7 is enabled."),
                page("&c&lTROUBLESHOOTING", "&8Quick fixes\n\n"
                        + "&7No boost? &fCheck your balance, tier threshold, boost settings, and world settings.\n"
                        + "&7No reward? &fCheck the balance cap, permissions, feature toggle, and Shrine cooldown.\n"
                        + "&7Altar not opening? &fUse the crafted marked Altar and verify &fsouls.altar&7.\n"
                        + "&7Shrine not working? &fUse the crafted marked Beacon, right-click the block, and verify &fsouls.shrine&7.\n"
                        + "&7Progress missing? &fConfirm the database file and check the server log for database errors.\n\n"
                        + "For configuration changes, reload only when safe and restart after database or recipe changes."),
                page("&5&lBOOK OF BŌC", "&8A powerful superweapon\n\n"
                        + "The Book of Bōc is an admin-granted artifact. Use &f/souls sw &7to open the superweapon menu and receive it.\n\n"
                        + "Right-click to invoke the book's self-effects. Left-click to channel its beam, followed by a shockwave.\n\n"
                        + "The wielder is immune to damage from both attacks."),
                page("&5&lTHE SARCULUM", "&8A close-range superweapon\n\n"
                        + "Sneak + left-click: steerable dash up to 25 blocks, dealing up to &c13.5 damage (6.75 hearts)&7 to targets within 4 blocks of its path.\n\n"
                        + "Sneak + right-click: slam. Ground slam deals up to &c18 damage (9 hearts)&7 near the center, falling to half at the 8-block edge.\n\n"
                        + "While airborne with a mace in your hotbar, the slam hits twice. Each impact deals up to &c30 damage (15 hearts)&7, falling to half at the 12-block edge."),
                page("&5&lSONIC BEAM", "&8Long-range attack\n\n"
                        + "Hold The Book of Bōc and left-click to fire a thick beam up to &b45 blocks&7. It lasts 5 seconds and damages players directly in its path for &c2.5 hearts every 50 milliseconds&7.\n\n"
                        + "The beam has a &b10-minute default cooldown&7. The cooldown appears in the action-bar HUD."),
                page("&5&lTHREE SHOCKWAVES", "&8A follow-up attack\n\n"
                        + "After the beam ends, three expanding shockwaves strike nearby mobs and players. Each shock deals up to &c8 hearts &7within 2 blocks, decreasing with distance to &c3 hearts &7at 12 blocks.\n\n"
                        + "Each target hit receives &9Slowness II &7for 3 seconds. The wielder is not harmed by the shockwaves."),
                page("&e&lARTIFACT COOLDOWNS", "&8Admin controls\n\n"
                        + "Admins can clear a player's artifact cooldowns with &f/souls sw-cooldown <player> [beam|dash|slam|activate|all]&7.\n\n"
                        + "Set a cooldown with &f/souls sw-cooldown set <beam|dash|slam|activate> <duration>&7. Examples: &f20s&7, &f60seconds&7, &f2m&7, &f1.5h&7, or &f2d&7.\n\n"
                        + "Seconds, minutes, hours, and days accept short or full suffixes. The Book beam defaults to 10 minutes."),
                page("&b&lARTIFACT OWNERS", "&8Find artifact holders\n\n"
                        + "Use &f/souls artifacts &7to view which players own each tracked artifact. An artifact with no current owner is left blank.\n\n"
                        + "Configure &fsuperweapon.artifacts.track-owners&7, &fsuperweapon.artifacts.boc.enabled&7, and &fsuperweapon.artifacts.sarculum.enabled &7in config.yml. Ownership records are saved in &fartifacts.yml&7.\n\n"
                        + "Use &f/souls reload &7after changing settings. Admin access is required for ownership lookup.")
        );
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