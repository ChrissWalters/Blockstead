package com.github.chrisswalters.blockstead.roles;

import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.plots.Plot;
import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gespeicherte Rollen unterhalb von "besitzende" (Tabelle {@code plot_roles}).
 *
 * <p>Wer eine solche Rolle hat, ist zusätzlich WorldGuard-Mitglied des Grundstücks; WorldGuard sperrt
 * so alle Fremden aus, und das Plugin schränkt Mitglieder je nach Rolle weiter ein. Weil der
 * Schutz-Listener bei sehr vielen Events nachfragt, werden alle Rollen beim Start in den Speicher
 * geladen; Änderungen gehen in Speicher und Datenbank gleichzeitig.
 */
public class RoleService {

    private final Database database;
    private final PluginConfig config;
    /** Schlüssel "welt/plot_id" → (Person → Rollenschlüssel) */
    private final Map<String, Map<UUID, String>> cache = new HashMap<>();

    public RoleService(Database database, PluginConfig config) {
        this.database = database;
        this.config = config;
        loadAll();
    }

    private void loadAll() {
        try (Statement statement = database.getConnection().createStatement();
             ResultSet result = statement.executeQuery("SELECT world, plot_id, uuid, role FROM plot_roles")) {
            while (result.next()) {
                cache.computeIfAbsent(key(result.getString(1), result.getString(2)), k -> new HashMap<>())
                        .put(UUID.fromString(result.getString(3)), result.getString(4));
            }
        } catch (SQLException e) {
            throw new DataException("Rollen konnten nicht geladen werden", e);
        }
    }

    /** Gespeicherter Rollenschlüssel der Person auf dem Grundstück; {@code null}, wenn keiner gespeichert ist. */
    public String getRoleKey(Plot plot, UUID uuid) {
        Map<UUID, String> roles = cache.get(key(plot));
        return roles == null ? null : roles.get(uuid);
    }

    /**
     * Ob ein Mitglied (nicht Besitzende) das Recht hat.
     * Mitglieder ohne gespeicherte Rolle haben alle Rechte (z.B. per {@code /rg addmember} hinzugefügt);
     * eine Rolle, die es in der config.yml nicht mehr gibt, hat keine Rechte.
     */
    public boolean memberHasRight(Plot plot, UUID uuid, PlotRight right) {
        String roleKey = getRoleKey(plot, uuid);
        if (roleKey == null) {
            return true;
        }
        PlotRole role = config.getRoles().get(roleKey);
        return role != null && role.has(right);
    }

    /** Anzeigename der Rolle eines Mitglieds, z.B. "Gast". */
    public String describeMember(Plot plot, UUID uuid) {
        String roleKey = getRoleKey(plot, uuid);
        if (roleKey == null) {
            return "Mitglied mit allen Rechten";
        }
        PlotRole role = config.getRoles().get(roleKey);
        return role != null ? role.displayName() : roleKey + " (unbekannte Rolle, keine Rechte)";
    }

    /** Speichert die Rolle der Person auf dem Grundstück (ersetzt eine vorhandene). */
    public void setRole(Plot plot, UUID uuid, String roleKey) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO plot_roles (world, plot_id, uuid, role) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, plot.world().getName());
            statement.setString(2, plot.id());
            statement.setString(3, uuid.toString());
            statement.setString(4, roleKey);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Rolle konnte nicht gespeichert werden", e);
        }
        cache.computeIfAbsent(key(plot), k -> new HashMap<>()).put(uuid, roleKey);
    }

    /** Entfernt die gespeicherte Rolle der Person auf dem Grundstück. */
    public void removeRole(Plot plot, UUID uuid) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "DELETE FROM plot_roles WHERE world = ? AND plot_id = ? AND uuid = ?")) {
            statement.setString(1, plot.world().getName());
            statement.setString(2, plot.id());
            statement.setString(3, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Rolle konnte nicht entfernt werden", e);
        }
        Map<UUID, String> roles = cache.get(key(plot));
        if (roles != null) {
            roles.remove(uuid);
        }
    }

    /** Entfernt alle Rollen auf dem Grundstück (beim Freigeben oder Löschen). */
    public void deleteRolesOnPlot(Plot plot) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "DELETE FROM plot_roles WHERE world = ? AND plot_id = ?")) {
            statement.setString(1, plot.world().getName());
            statement.setString(2, plot.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Rollen konnten nicht gelöscht werden", e);
        }
        cache.remove(key(plot));
    }

    private static String key(Plot plot) {
        return key(plot.world().getName(), plot.id());
    }

    private static String key(String world, String plotId) {
        return world + "/" + plotId;
    }
}
