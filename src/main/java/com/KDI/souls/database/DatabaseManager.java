package com.KDI.souls.database;

import com.KDI.souls.config.SoulsConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DatabaseManager {
    private final JavaPlugin plugin;
    private final SoulsConfig config;
    private volatile boolean available;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "souls-database");
        thread.setDaemon(true);
        return thread;
    });

    public DatabaseManager(JavaPlugin plugin, SoulsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void initialize() throws SQLException {
        available = false;
        String type = config.databaseType();
        if (!type.equals("sqlite") && !type.equals("mariadb")) {
            throw new SQLException("Unsupported database.type '" + type + "'. Use 'sqlite' or 'mariadb'.");
        }
        if (type.equals("sqlite")) {
            File databaseFile = new File(plugin.getDataFolder(), config.sqliteFile());
            File parent = databaseFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                throw new SQLException("Could not create SQLite database directory: " + parent);
            }
        }
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS souls_balances (uuid VARCHAR(36) PRIMARY KEY, souls BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS soul_fragments (uuid VARCHAR(36) PRIMARY KEY, fragments BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS soul_pvp (killer_uuid VARCHAR(36) NOT NULL, victim_uuid VARCHAR(36) NOT NULL, last_reward BIGINT NOT NULL, reward_count BIGINT NOT NULL, PRIMARY KEY (killer_uuid, victim_uuid))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS soul_combat_stats (uuid VARCHAR(36) PRIMARY KEY, kills BIGINT NOT NULL, deaths BIGINT NOT NULL)");
            available = true;
        } catch (SQLException exception) {
            available = false;
            throw exception;
        }
    }

    public CompletableFuture<Boolean> retryInitialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                initialize();
                return true;
            } catch (SQLException exception) {
                return false;
            }
        }, executor);
    }

    public boolean isAvailable() {
        return available;
    }

    private Connection connection() throws SQLException {
        if (config.databaseType().equals("sqlite")) {
            File file = new File(plugin.getDataFolder(), config.sqliteFile());
            return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        }
        String url = "jdbc:mariadb://" + config.databaseHost() + ":" + config.databasePort()
                + "/" + config.databaseName() + config.databaseParameters();
        return DriverManager.getConnection(url, config.databaseUsername(), config.databasePassword());
    }

    public CompletableFuture<Long> loadBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("SELECT souls FROM souls_balances WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    long balance = result.next() ? result.getLong(1) : 0L;
                    available = true;
                    return balance;
                }
            } catch (SQLException exception) {
                available = false;
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> saveBalance(UUID uuid, long balance) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "REPLACE INTO souls_balances (uuid, souls) VALUES (?, ?)")) {
                statement.setString(1, uuid.toString());
                statement.setLong(2, balance);
                statement.executeUpdate();
                available = true;
            } catch (SQLException exception) {
                available = false;
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<List<LeaderboardEntry>> top(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT uuid, souls FROM souls_balances ORDER BY souls DESC LIMIT ?")) {
                statement.setInt(1, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new LeaderboardEntry(UUID.fromString(result.getString(1)), result.getLong(2)));
                    }
                }
                available = true;
                return entries;
            } catch (SQLException | IllegalArgumentException exception) {
                available = false;
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Long> tryClaimPvp(UUID killer, UUID victim, long cooldownSeconds,
                                                long diminishingPeriodSeconds, long baseReward, double factor) {
        return CompletableFuture.supplyAsync(() -> {
            long now = System.currentTimeMillis() / 1000L;
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT last_reward, reward_count FROM soul_pvp WHERE killer_uuid = ? AND victim_uuid = ?")) {
                    select.setString(1, killer.toString());
                    select.setString(2, victim.toString());
                    long last = 0;
                    long count = 0;
                    try (ResultSet result = select.executeQuery()) {
                        if (result.next()) {
                            last = result.getLong(1);
                            count = result.getLong(2);
                        }
                    }
                    if (last > 0 && now - last < cooldownSeconds) {
                        connection.rollback();
                        return 0L;
                    }
                    if (last > 0 && now - last > diminishingPeriodSeconds) {
                        count = 0;
                    }
                    long reward = Math.max(0L, (long) Math.floor(baseReward * Math.pow(factor, count)));
                    try (PreparedStatement upsert = connection.prepareStatement(
                            "REPLACE INTO soul_pvp (killer_uuid, victim_uuid, last_reward, reward_count) VALUES (?, ?, ?, ?)")) {
                        upsert.setString(1, killer.toString());
                        upsert.setString(2, victim.toString());
                        upsert.setLong(3, now);
                        upsert.setLong(4, count + 1);
                        upsert.executeUpdate();
                    }
                    connection.commit();
                    available = true;
                    return reward;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                } finally {
                    connection.setAutoCommit(true);
                }
            } catch (Exception exception) {
                available = false;
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<Void> recordPvpResult(UUID killer, UUID victim) {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try {
                    incrementCombatStat(connection, killer, "kills");
                    incrementCombatStat(connection, victim, "deaths");
                    connection.commit();
                    available = true;
                } catch (Exception exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (Exception exception) {
                available = false;
                throw new CompletionException(exception);
            }
        }, executor);
    }

    public CompletableFuture<CombatStats> loadCombatStats(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement(
                         "SELECT kills, deaths FROM soul_combat_stats WHERE uuid = ?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    CombatStats stats = result.next()
                            ? new CombatStats(result.getLong(1), result.getLong(2))
                            : new CombatStats(0, 0);
                    available = true;
                    return stats;
                }
            } catch (SQLException exception) {
                available = false;
                throw new CompletionException(exception);
            }
        }, executor);
    }

    private void incrementCombatStat(Connection connection, UUID uuid, String column) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO soul_combat_stats (uuid, kills, deaths) VALUES (?, 0, 0)")) {
            insert.setString(1, uuid.toString());
            try {
                insert.executeUpdate();
            } catch (SQLException ignored) {
            }
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE soul_combat_stats SET " + column + " = " + column + " + 1 WHERE uuid = ?")) {
            update.setString(1, uuid.toString());
            update.executeUpdate();
        }
    }

    public record CombatStats(long kills, long deaths) {
        public double ratio() {
            return deaths == 0 ? kills : (double) kills / deaths;
        }
    }

    public void close() {
        available = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
