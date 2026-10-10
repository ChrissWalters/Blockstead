package com.github.chrisswalters.blockstead.economy;

import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Kontostände der Server-Währung (Name und Startguthaben aus der config.yml).
 *
 * <p>Ein Konto wird beim ersten Zugriff automatisch mit dem Startguthaben angelegt
 * ({@link #ensureAccount(UUID)}), z.B. beim ersten Join nach Installation des Plugins.
 * Beträge sind ganze Zahlen und nie negativ.
 */
public class EconomyService {

    private final Database database;
    private final PluginConfig config;

    public EconomyService(Database database, PluginConfig config) {
        this.database = database;
        this.config = config;
    }

    /** Legt das Konto mit dem Startguthaben an, falls es noch nicht existiert. */
    public void ensureAccount(UUID uuid) {
        try (PreparedStatement statement = connection().prepareStatement(
                "INSERT OR IGNORE INTO accounts (uuid, balance) VALUES (?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setLong(2, config.getStartBalance());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Konto konnte nicht angelegt werden", e);
        }
    }

    /** Kontostand; legt das Konto mit Startguthaben an, falls es noch keins gibt. */
    public long getBalance(UUID uuid) {
        ensureAccount(uuid);
        try (PreparedStatement statement = connection().prepareStatement(
                "SELECT balance FROM accounts WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataException("Kontostand konnte nicht gelesen werden", e);
        }
    }

    /** Zahlt {@code amount} ein (darf nicht negativ sein). */
    public void deposit(UUID uuid, long amount) {
        requireNotNegative(amount);
        ensureAccount(uuid);
        update("UPDATE accounts SET balance = balance + ? WHERE uuid = ?", amount, uuid);
    }

    /**
     * Bucht {@code amount} ab, aber nur, wenn genug Guthaben da ist.
     *
     * @return {@code false}, wenn das Guthaben nicht reicht (dann wird nichts abgebucht)
     */
    public boolean withdraw(UUID uuid, long amount) {
        requireNotNegative(amount);
        ensureAccount(uuid);
        try (PreparedStatement statement = connection().prepareStatement(
                "UPDATE accounts SET balance = balance - ? WHERE uuid = ? AND balance >= ?")) {
            statement.setLong(1, amount);
            statement.setString(2, uuid.toString());
            statement.setLong(3, amount);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new DataException("Abbuchung fehlgeschlagen", e);
        }
    }

    /** Setzt den Kontostand auf {@code amount} (darf nicht negativ sein). */
    public void setBalance(UUID uuid, long amount) {
        requireNotNegative(amount);
        ensureAccount(uuid);
        update("UPDATE accounts SET balance = ? WHERE uuid = ?", amount, uuid);
    }

    /** Betrag mit Währungsname, z.B. "100 Coins". */
    public String format(long amount) {
        return amount + " " + config.getCurrencyName();
    }

    private void update(String sql, long amount, UUID uuid) {
        try (PreparedStatement statement = connection().prepareStatement(sql)) {
            statement.setLong(1, amount);
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Kontostand konnte nicht geändert werden", e);
        }
    }

    private static void requireNotNegative(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Betrag darf nicht negativ sein: " + amount);
        }
    }

    private Connection connection() {
        return database.getConnection();
    }
}
