# The Soul System

A **modular Paper plugin** that adds an optional Souls-based progression system to Minecraft servers.

Souls provide an additional layer of progression without being required for normal survival or gameplay. Players can earn, spend, and manage persistent Soul balances through configurable PvE and optional PvP activities.

## 💀 Core Soul System

* Persistent **UUID-based Soul balances**
* Configurable **PvE Soul rewards**
* Optional **PvP rewards** with cooldowns and diminishing returns
* **1000 Soul maximum balance**
* Every **100 Souls** grants stacking bonuses to:

  * ⚡ Haste
  * 💪 Strength
  * 🏃 Speed
  * 🛡️ Absorption
* Configurable **Soul Fragment** system using `PersistentDataContainer`
* Configurable Fragment → Soul conversion rates
* Optional configurable **death-loss** system

## ⚔️ Soul Weapons & Abilities

The Soul System includes powerful abilities with configurable cooldowns.

### Dash

* **2-minute default cooldown**
* Travels up to **12 blocks**
* Deals area damage to nearby mobs and players along its path
* Player damage respects the server's **PvP settings**
* Enhanced movement speed, intensity, and particle effects

### Slam

* **5-minute default cooldown**
* Enhanced speed, impact, intensity, and particle effects

### Book of Bōc

* **15-minute default activation cooldown**
* Cooldown applies to existing configurations as well as new setups

All ability cooldowns can be customized through the admin cooldown system.

## ⏱️ Cooldown System

The plugin features a persistent and configurable cooldown system with reliable tracking across server sessions.

### Admin Commands

```text
/souls sw-cooldown <player> [dash|slam|activate|all]
```

Clears one or more active cooldowns for a player.

```text
/souls sw-cooldown set <dash|slam|activate> <duration>
```

Customizes and saves the cooldown duration.

Supported duration formats include:

* Seconds
* Minutes
* Hours
* Days

### 📊 Cooldown HUD

* Displays **multiple active cooldowns** simultaneously
* Shows accurate remaining cooldown time
* Correctly restores cooldown states after **rejoining the server**
* Handles long-duration cooldowns reliably

## 🛠️ Commands

### Player Commands

```text
/souls
/souls top
/souls pay <player> <amount>
```

### Admin Commands

```text
/souls give <player> <amount>
/souls take <player> <amount>
/souls set <player> <amount>
/souls inspect <player>
/souls reload
```

## 🔮 Optional Features

Individually configurable systems include:

* 🛒 **Soul Shop**
* ⛩️ **Soul Altar**
* ✨ **Soul Powers**
* 🎯 **Bounties**
* 🗿 **Shrines**
* 🎲 **Random Soul Events**

## ⚙️ Configuration & Integrations

* Per-world configuration
* Configurable messages, permissions, rewards, and mechanics
* Configurable Soul and Fragment rewards
* **PlaceholderAPI** support
* **SQLite** storage by default
* Optional **MySQL/MariaDB** support
* Asynchronous database operations
* Persistent data storage
* Configurable PvP rewards and protection
* Configurable cooldown durations

## 🛡️ Protection & Reliability

The system includes safeguards against common exploits and reward-management issues:

* PvP cooldowns and diminishing returns to reduce farming
* Protection against excessive or incorrectly granted rewards
* Shrine rewards exceeding **64 Soul Fragments** are automatically split into multiple stacks
* Death-loss calculations wait for the player's Soul balance to finish loading
* Long-duration cooldowns are handled correctly without premature expiration

## 🚀 Performance

Built with performance and server stability in mind:

* Event-driven architecture
* No unnecessary constant scanning
* Minimal background processing
* Asynchronous database operations
* Lightweight runtime design
* No NMS dependencies

## 🔌 Developer API

Other plugins can integrate with the Soul System through dedicated API events:

```text
SoulEarnEvent
SoulSpendEvent
SoulLoseEvent
```

These events allow developers to react to Soul gains, spending, and losses.

---

## 📌 Update Types

> **Major**
>
> Introduces significant new features, major gameplay changes, or complete system overhauls.
>
> **Enhanced**
>
> Focuses on bug fixes, minor adjustments, performance improvements, and quality-of-life enhancements without introducing major gameplay changes.

---
