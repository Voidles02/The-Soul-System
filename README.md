# ✦ SOULS ✦

### A complete soul-powered progression system for Paper

**Current release: 3.1.0**

Earn Souls through combat. Collect shimmering fragments. Build an altar, make your gear stronger, and risk your reserve when you fall. Souls brings progression, collectible currency, custom structures, artifacts, and a polished resource pack together in one plugin.

> **For players:** Start with `/guide` or `/souls`.
> **For server owners:** Configure the plugin in `plugins/Souls/config.yml` and `plugins/Souls/messages.yml`.

---

## ✨ What’s inside

- **Combat progression:** Earn Souls for configured PvE kills and eligible PvP kills. Your balance unlocks automatic effect tiers, respects a configurable cap, and is stored by UUID.
- **Soul Fragments:** Collect mob drops, consume a tagged Fragment for a random 1–5 Souls, or convert Fragments at the configured exchange rate.
- **Soul profile and leaderboard:** View your balance, tier, combat statistics, and carried Fragments; compare balances with the top-player leaderboard.
- **Player-to-player trading:** Transfer Souls directly with `/souls pay`.
- **Soul Altar:** Craft a marked Altar and spend Souls on compatible equipment upgrades.
- **Soul Shrine:** Craft a marked Shrine to claim configurable, cooldown-based rewards.
- **Artifacts:** The Book of Bōc and the Sarculum have active abilities, configurable cooldowns, and optional ownership tracking.
- **Performance report:** `/souls-performance` separates Souls-specific counters from shared server and JVM measurements.
- **Bundled resource pack:** Custom Souls item visuals ship with the plugin and are sent to players when a public pack host is configured.
- **Optional PlaceholderAPI support:** Exposes balance, maximum-balance, and boost-level placeholders when PlaceholderAPI is installed.

## 📋 Requirements

- Paper server; the project targets **Paper API 1.20.4** and **Java 25**.
- PlaceholderAPI is optional.
- The custom resource pack requires a publicly reachable host address and an open port if you want players to download it automatically.

The project’s target API does not guarantee compatibility with every Paper or Minecraft version. Test upgrades on a staging server before updating a live server.

## 🚀 Installation

1. Place the Souls plugin JAR in your server’s `plugins/` directory.
2. Start or restart the server.
3. Review `plugins/Souls/config.yml`; restart after changing database settings.
4. If you want automatic resource-pack delivery, set `resource-pack.host` to the public hostname or IP clients can reach and allow inbound TCP traffic on the configured port.
5. Grant any non-default permissions your server needs. New players receive the guide once on their first join; returning players can open it with `/guide`.

Souls creates its default configuration and messages file on startup. Player balances are stored in SQLite by default in `plugins/Souls/souls.db`.

## 🎮 Player guide

### Getting started

1. New players see `/guide` automatically once on their first join by default. Returning players can use `/guide` at any time.
2. Defeat configured mobs to earn Souls and Soul Fragments.
3. Run `/souls` to view your profile, tier, and statistics.
4. Save Fragments for direct use or convert them through `/souls convert`.
5. Craft a Soul Altar to enhance eligible gear, or a Soul Shrine for repeatable rewards.

The default maximum balance is **1,000 Souls**. Every **100 Souls** adds a progression tier. Losing Souls recalculates your Soul effects. By default, death removes **50%** of your balance.

### Commands

| Command | What it does |
|---|---|
| `/souls` or `/soul` | Open your Soul profile. |
| `/souls stats` or `/souls profile` | Open your profile and stats. |
| `/souls top` | Show the top 10 balances. |
| `/souls pay <player> <amount>` | Transfer Souls to a player. |
| `/souls fragments` | Count your tagged Soul Fragments. |
| `/souls convert` | Convert Fragments into Souls using the configured rate. |
| `/guide` or `/soul-guide` | Open the tutorial book. |
| `/souls sw-info` | Open the superweapon information book. |
| `/souls-performance` | View plugin counters and server-wide performance metrics. |

### Soul Fragments

Fragments are tagged items; an ordinary item with the same base material does not count. By default, right-clicking a Fragment in your main hand consumes one for a random **1–5 Souls**. A Fragment is not consumed if your balance is already full. `/souls convert` instead exchanges Fragments at the configured rate, **10 Fragments per Soul** by default.

The custom shard texture uses `PRISMARINE_SHARD` as its base material. Keep `fragments.item-material` set to that value if you want the bundled visual to display correctly.

### Soul Altar

Craft the marked Soul Altar in a crafting table:

```text
Gold Ingot   Nether Star    Gold Ingot
Diamond Block Crying Obsidian Diamond Block
Obsidian     Obsidian       Obsidian
```

Place the crafted Altar and right-click it to open the upgrade interface. Insert one supported item or book and choose an available upgrade. The default base cost is **50 Souls**. Only the marked crafted Altar works; an ordinary Enchanting Table is not an Altar.

Default upgrade caps include Damage **150%**, Soul Durability **300%**, Crit **45%**, LifeSteal **70%**, and Attract **70%**. Yield is available for compatible pickaxes, hoes, and books, up to Yield III. Compatibility and upgrade ranges are configurable.

### Soul Shrine

Craft the marked Shrine in a crafting table:

```text
Amethyst Shard Gold Ingot  Amethyst Shard
Soul Sand     Nether Star  Gold Ingot
Obsidian      Obsidian     Obsidian
```

Place the crafted Shrine and right-click the marked Beacon. By default, each player can activate it once every **2 hours**. A random reward can grant **25 Souls**, **2 Fragments**, or **Speed II, Haste I, and Jump Boost I for 6 minutes**. Reward effects, amounts, feedback, broadcasts, and cooldown are configurable. An ordinary Beacon is not a Soul Shrine.

### Artifacts

Artifact management is restricted to administrators. `/souls sw` opens the admin superweapon menu. The Book of Bōc and Sarculum abilities and cooldowns are described in `/souls sw-info`; their default cooldowns are configured under `superweapon.cooldowns`.

Ownership tracking can be enabled for both artifacts. Admins can use `/souls artifacts` to view ownership history, `/souls artifacts boc` or `/souls artifacts sarculum` to view one artifact, and `/souls artifact` to register ownership for artifacts currently held by online players.

## 🛡️ Administrator commands

All of the following require `souls.admin`:

| Command | What it does |
|---|---|
| `/souls give <player> <amount>` | Add Souls to a player. |
| `/souls take <player> <amount>` | Remove a specified amount. |
| `/souls set <player> <amount>` | Set a player’s balance. |
| `/souls remove <player> [amount\|all]` | Remove the specified amount; omit it or use `all` to clear the balance. |
| `/souls inspect <player>` | Inspect a player’s balance and available local save details. |
| `/souls reload` | Reload Souls settings and messages and refresh supported runtime settings. |
| `/souls textures` | Audit and repair the bundled pack files; if the host is ready, reapply the required pack to online players. |
| `/souls sw` | Open the superweapon admin menu. |
| `/souls sw-cooldown <player> [dash\|slam\|mace-slam\|activate\|beam\|all]` | Clear an artifact ability cooldown. |
| `/souls sw-cooldown set <dash\|slam\|mace-slam\|activate\|beam> <duration>` | Set an artifact cooldown duration, such as `20s`, `2m`, `1.5h`, or `2d`. |
| `/souls artifacts [boc\|sarculum]` | View tracked artifact ownership history. |
| `/souls artifact` | Register artifact ownership for online players currently holding tracked artifacts. |

`/souls sw-info` is player-facing and does not require administrator permission. Administrative commands require the `souls.admin` permission even if the sender has permission to use `/souls` generally.

## 🔐 Permissions

| Permission | Default | Purpose |
|---|---:|---|
| `souls.use` | Everyone | Use the main Souls command and open the guide. |
| `souls.pay` | Everyone | Transfer Souls with `/souls pay`. |
| `souls.performance` | Everyone | View `/souls-performance`. |
| `souls.admin` | Operators | Use administrative and artifact-management commands. |
| `souls.altar` | Everyone | Use Soul Altars by default; the required node is configurable. |
| `souls.shrine` | Everyone | Use Soul Shrines by default; the required node is configurable. |

## ⚙️ Configuration

Edit `plugins/Souls/config.yml`. Important settings include:

| Section | What you can configure |
|---|---|
| `database` | SQLite or MariaDB; file, host, port, database name, username, password, and connection parameters. SQLite is the default. |
| `resource-pack` | Public host, local bind address, and HTTP port. The default port is `8765`; the public host is blank until configured. |
| `souls.max-balance` | Maximum Soul balance; default `1000`. |
| `earning.pve` | Enable PvE earning and set Soul rewards per mob. |
| `earning.pvp` | Enable PvP rewards, base amount, repeated-kill cooldown, diminishing period, and diminishing factor. |
| `fragments` | Enable fragments, exchange rate, item material, and mob drop counts. |
| `boost` | Enable automatic effects, Souls required per tier, amplifier scaling, maximum reduction, and individual effects. |
| `loss` | Enable death loss, percentage, and maximum loss. A maximum of `0` means no configured limit. |
| `worlds` | Enable Souls, PvE, and PvP behavior per world. |
| `altar` | Permission node, cost, upgrade ranges, caps, sound, and particle feedback. |
| `shrine` | Permission node, cooldown, reward mode and amounts, primary/additional buff effects and strength, event chance, broadcast, and feedback. |
| `superweapon` | Artifact cooldowns, information-book pages, and artifact enablement/ownership tracking. |
| `guide.auto-open` | Whether to open the guide automatically once for a player; enabled by default. |
| `local-data` | Enable local player-data fallback and configure its folder. Enabled by default. |

`messages.yml` controls the plugin’s chat text and performance-report messages. Back up the plugin data folder before changing database files or migrating storage. Avoid editing the database while the server is running.

### Resource-pack delivery

Souls includes its resource pack in the plugin distribution and hosts it over HTTP. Set `resource-pack.host` to an address reachable by players, then make the configured TCP port reachable through your network/firewall. `bind-address` controls the interface used by the local listener; it is not the address clients should enter. When the public host is blank, players will not receive the pack automatically. Use `/souls textures` to run the in-game pack audit and reapply the pack after the host is ready.

## 📊 Performance report

Run `/souls-performance` to see:

- **Souls-specific counters:** cached balances, pending balance loads, and scheduled tasks owned by Souls.
- **Database state:** whether the plugin database is available.
- **Server-wide metrics:** TPS, shared JVM heap usage, and online-player ping statistics.

Bukkit does not expose exact per-plugin RAM or CPU consumption. Heap values are for the entire server JVM and must not be interpreted as Souls-only memory usage. Compare measurements under similar server conditions when evaluating changes.

## 🧰 Troubleshooting

- **The pack does not appear:** Set a public `resource-pack.host`, expose the configured TCP port, confirm `fragments.item-material: PRISMARINE_SHARD`, and run `/souls textures` as an administrator.
- **No Soul reward:** Check the balance cap, the relevant earning setting, world settings, and mob reward map.
- **No boost:** Check the player’s tier threshold and `boost` settings.
- **The Altar or Shrine does not activate:** Use the marked item crafted by Souls, check that its feature is enabled, and verify the player has the configured permission.
- **A leaderboard or balance cannot load:** Check the server log and database settings; local fallback is enabled by default, but database-backed features can be unavailable if the database cannot initialize.
- **Changed messages are not visible:** Review `messages.yml` and use `/souls reload` as an administrator.

## 🔧 Project notes

The project targets Java 25 and Paper API 1.20.4. Its Gradle build uses XSeries and SQLite/MariaDB JDBC drivers; PlaceholderAPI is a soft, optional integration. The resource-pack source folder is `TSS/`; the build packages its contents and generated Souls textures with the plugin artifact.

Some `features` switches are present for configuration and superweapon status display, but the `shop`, `powers`, and `bounties` gameplay systems are not implemented in this project. Enabling those settings alone does not add those systems.

---

<p align="center"><strong>Gather Souls. Shape your power. Leave a legacy.</strong></p>