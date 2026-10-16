package com.github.chrisswalters.blockstead.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Verbindung zur SQLite-Datenbank des Plugins ({@code plugins/Blockstead/data.db}).
 *
 * <p>Der SQLite-Treiber wird von Paper mitgeliefert, das Plugin braucht dafür keine eigene Abhängigkeit.
 * Die Datenbank ist klein und wird nur kurz abgefragt, deshalb laufen alle Zugriffe synchron
 * über eine einzige Verbindung.
 *
 * <p>Schema-Änderungen laufen über {@link #migrate()}: Die aktuelle Version steht in
 * {@code PRAGMA user_version}; für jede neue Version kommt ein weiterer Block dazu und
 * {@link #SCHEMA_VERSION} wird erhöht. Ist die Datenbank neuer als {@link #SCHEMA_VERSION},
 * verweigert das Plugin den Start (Schutz vor älteren Plugin-Versionen).
 */
public class Database implements AutoCloseable {

    /** Schema-Version, die diese Plugin-Version kennt. Bei jedem neuen Migrationsblock erhöhen. */
    public static final int SCHEMA_VERSION = 4;

    private final Connection connection;

    /** Öffnet (bzw. erzeugt) die Datenbankdatei und bringt das Schema auf den aktuellen Stand. */
    public Database(File file) throws SQLException {
        file.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            // WAL: Schreiben blockiert Lesen nicht und ist robuster bei Abstürzen
            statement.execute("PRAGMA journal_mode=WAL");
        }
        migrate();
    }

    /** Die gemeinsame Verbindung; nicht schließen (das macht {@link #close()} beim Abschalten). */
    public Connection getConnection() {
        return connection;
    }

    private void migrate() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            int version;
            try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                version = result.getInt(1);
            }
            if (version > SCHEMA_VERSION) {
                // Datenbank stammt von einer neueren Plugin-Version (z.B. alte .jar zurückgespielt):
                // nichts anfassen, sonst könnten Daten verloren gehen
                connection.close();
                throw new SQLException("The database has version " + version + ", this plugin version only knows up to "
                        + SCHEMA_VERSION + ". Please use the newer plugin version or restore a matching backup "
                        + "from plugins/Blockstead/backups/.");
            }

            if (version < 1) {
                // Kontostände der Währung
                statement.execute("CREATE TABLE accounts ("
                        + "uuid TEXT PRIMARY KEY, "
                        + "balance INTEGER NOT NULL)");
                // Wer ein Grundstück für wie viel gekauft hat (für die Erstattung beim Freigeben).
                // Grundstücke ohne Eintrag wurden kostenlos vergeben oder vor diesem System angelegt.
                statement.execute("CREATE TABLE plot_claims ("
                        + "world TEXT NOT NULL, "
                        + "plot_id TEXT NOT NULL, "
                        + "payer TEXT NOT NULL, "
                        + "price INTEGER NOT NULL, "
                        + "claimed_at INTEGER NOT NULL, "
                        + "PRIMARY KEY (world, plot_id))");
                statement.execute("PRAGMA user_version = 1");
            }

            if (version < 2) {
                // Ein Home pro Person, immer auf einem Grundstück (plot_id), auf dem sie Rechte hat
                statement.execute("CREATE TABLE homes ("
                        + "uuid TEXT PRIMARY KEY, "
                        + "world TEXT NOT NULL, "
                        + "plot_id TEXT NOT NULL, "
                        + "x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, "
                        + "yaw REAL NOT NULL, pitch REAL NOT NULL)");
                statement.execute("PRAGMA user_version = 2");
            }

            if (version < 3) {
                // Rollen unterhalb von "owner" (Besitzende); die Person ist zusätzlich WorldGuard-Mitglied
                statement.execute("CREATE TABLE plot_roles ("
                        + "world TEXT NOT NULL, "
                        + "plot_id TEXT NOT NULL, "
                        + "uuid TEXT NOT NULL, "
                        + "role TEXT NOT NULL, "
                        + "PRIMARY KEY (world, plot_id, uuid))");
                // Merker für einmalige Datenkorrekturen (siehe getMeta/setMeta)
                statement.execute("CREATE TABLE meta ("
                        + "key TEXT PRIMARY KEY, "
                        + "value TEXT NOT NULL)");
                statement.execute("PRAGMA user_version = 3");
            }

            if (version < 4) {
                // Ab 2.0.0 führt das Economy-Plugin die Kontostände; accounts bleibt nur als Quelle für die
                // einmalige Übertragung (BalanceImport). NULL = noch nicht übertragen.
                statement.execute("ALTER TABLE accounts ADD COLUMN imported_at INTEGER");
                statement.execute("PRAGMA user_version = 4");
            }
        }
    }

    /** Liest einen Merker aus der Tabelle {@code meta}; {@code null}, wenn nicht gesetzt. */
    public String getMeta(String key) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT value FROM meta WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new DataException("Could not read marker '" + key + "'", e);
        }
    }

    /** Setzt einen Merker in der Tabelle {@code meta}. */
    public void setMeta(String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO meta (key, value) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Could not save marker '" + key + "'", e);
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
