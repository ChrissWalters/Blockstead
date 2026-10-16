package com.github.chrisswalters.blockstead.economy;

import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Einmalige Übertragung der Kontostände aus Blockstead 1.x (Tabelle {@code accounts}) in das Economy-Plugin.
 *
 * <p>Der Stand im Economy-Plugin wird auf den alten Wert <em>gesetzt</em>, nicht addiert – das Startguthaben
 * des Economy-Plugins spielt für übertragene Konten also keine Rolle. Jedes erfolgreich übertragene Konto
 * bekommt einen Zeitstempel ({@code imported_at}) und wird danach nie wieder angefasst. Konten, die nicht
 * übertragen werden konnten, werden beim nächsten Start erneut versucht.
 *
 * <p>Läuft im ersten Tick nach dem Start aller Plugins (siehe {@code Blockstead.onEnable()}), weil
 * Economy-Plugins wie EssentialsX erst dann benutzbar sind. Die Tabelle {@code accounts} bleibt als Nachweis
 * erhalten. Abschaltbar mit {@code economy.import-old-balances: false}.
 */
public final class BalanceImport {

    private BalanceImport() {
    }

    /** Überträgt alle noch nicht übertragenen Konten; schreibt Verlauf und Ergebnis ins Log. */
    public static void run(Database database, EconomyService economy, boolean enabled, Logger log) {
        Map<UUID, Long> pending = readPending(database);
        if (pending.isEmpty()) {
            return;
        }
        if (!enabled) {
            log.info(pending.size() + " balance(s) from Blockstead 1.x are not transferred "
                    + "(economy.import-old-balances: false).");
            return;
        }
        if (!economy.isAvailable()) {
            log.warning(pending.size() + " balance(s) from Blockstead 1.x are waiting to be transferred, but no "
                    + "economy plugin is available through Vault. Trying again at the next start.");
            return;
        }

        log.info("Transferring " + pending.size() + " balance(s) from Blockstead 1.x to the economy plugin …");
        int done = 0;
        for (Map.Entry<UUID, Long> account : pending.entrySet()) {
            UUID uuid = account.getKey();
            long balance = account.getValue();
            String name = PlayerLookup.nameOf(uuid);
            Transaction result;
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                result = economy.setBalance(player, balance);
            } catch (RuntimeException e) {
                result = Transaction.failed(e.toString());
            }
            if (!result.success()) {
                log.warning("Balance NOT transferred: " + name + " (" + uuid + ") = " + balance + " – " + result.error()
                        + ". Trying again at the next start.");
                continue;
            }
            markImported(database, uuid);
            log.info("Balance transferred: " + name + " (" + uuid + ") = " + balance);
            done++;
        }
        log.info("Transfer finished: " + done + " of " + pending.size() + " account(s) transferred.");
    }

    /** Alle Konten ohne Zeitstempel, nach UUID sortiert (für ein gleichbleibendes Log). */
    private static Map<UUID, Long> readPending(Database database) {
        Map<UUID, Long> pending = new LinkedHashMap<>();
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "SELECT uuid, balance FROM accounts WHERE imported_at IS NULL ORDER BY uuid");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                pending.put(UUID.fromString(result.getString(1)), result.getLong(2));
            }
        } catch (SQLException e) {
            throw new DataException("Could not read old balances", e);
        }
        return pending;
    }

    private static void markImported(Database database, UUID uuid) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "UPDATE accounts SET imported_at = ? WHERE uuid = ?")) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Could not record the transfer of " + uuid, e);
        }
    }
}
