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
            log.info(pending.size() + " Kontostand/Kontostände aus Blockstead 1.x werden nicht übertragen "
                    + "(economy.import-old-balances: false).");
            return;
        }
        if (!economy.isAvailable()) {
            log.warning(pending.size() + " Kontostand/Kontostände aus Blockstead 1.x warten auf die Übertragung, aber kein "
                    + "Economy-Plugin ist über Vault erreichbar. Neuer Versuch beim nächsten Start.");
            return;
        }

        log.info("Übertrage " + pending.size() + " Kontostand/Kontostände aus Blockstead 1.x in das Economy-Plugin …");
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
                log.warning("Kontostand NICHT übertragen: " + name + " (" + uuid + ") = " + balance + " – " + result.error()
                        + ". Neuer Versuch beim nächsten Start.");
                continue;
            }
            markImported(database, uuid);
            log.info("Kontostand übertragen: " + name + " (" + uuid + ") = " + balance);
            done++;
        }
        log.info("Übertragung abgeschlossen: " + done + " von " + pending.size() + " Konto/Konten übertragen.");
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
            throw new DataException("Alte Kontostände konnten nicht gelesen werden", e);
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
            throw new DataException("Übertragung von " + uuid + " konnte nicht vermerkt werden", e);
        }
    }
}
