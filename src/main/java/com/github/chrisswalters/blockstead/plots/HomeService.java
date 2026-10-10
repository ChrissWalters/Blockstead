package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/**
 * Homes: genau ein gespeicherter Teleportpunkt pro Person, immer auf einem Grundstück.
 *
 * <p>Ob die Person auf dem Grundstück noch das Home-Recht hat, prüft {@code /plot home} beim Teleport.
 * Beim Freigeben oder Löschen eines Grundstücks werden alle Homes darauf entfernt
 * ({@link #deleteHomesOnPlot(Plot)}), bei Rollenentzug das Home der Person ({@link #deleteHomeIfOnPlot}).
 */
public class HomeService {

    /** Gespeichertes Home; {@code location} ist leer, wenn die Welt nicht (mehr) geladen ist. */
    public record Home(String worldName, String plotId, Optional<Location> location) {
    }

    private final Database database;

    public HomeService(Database database) {
        this.database = database;
    }

    /** Speichert {@code location} als Home auf dem Grundstück {@code plot}; ein altes Home wird ersetzt. */
    public void setHome(UUID uuid, Plot plot, Location location) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO homes (uuid, world, plot_id, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, plot.world().getName());
            statement.setString(3, plot.id());
            statement.setDouble(4, location.getX());
            statement.setDouble(5, location.getY());
            statement.setDouble(6, location.getZ());
            statement.setFloat(7, location.getYaw());
            statement.setFloat(8, location.getPitch());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Home konnte nicht gespeichert werden", e);
        }
    }

    /** Gespeichertes Home der Person; leer, wenn sie keins gesetzt hat. */
    public Optional<Home> getHome(UUID uuid) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "SELECT world, plot_id, x, y, z, yaw, pitch FROM homes WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String worldName = result.getString(1);
                World world = Bukkit.getWorld(worldName);
                Optional<Location> location = world == null ? Optional.empty() : Optional.of(new Location(world,
                        result.getDouble(3), result.getDouble(4), result.getDouble(5),
                        result.getFloat(6), result.getFloat(7)));
                return Optional.of(new Home(worldName, result.getString(2), location));
            }
        } catch (SQLException e) {
            throw new DataException("Home konnte nicht gelesen werden", e);
        }
    }

    /** Löscht das Home der Person (z.B. wenn es nicht mehr gültig ist). */
    public void deleteHome(UUID uuid) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "DELETE FROM homes WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Home konnte nicht gelöscht werden", e);
        }
    }

    /** Entfernt das Home der Person, falls es auf diesem Grundstück liegt (bei Rollenentzug). */
    public void deleteHomeIfOnPlot(UUID uuid, Plot plot) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "DELETE FROM homes WHERE uuid = ? AND world = ? AND plot_id = ?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, plot.world().getName());
            statement.setString(3, plot.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Home konnte nicht gelöscht werden", e);
        }
    }

    /** Entfernt alle Homes auf dem Grundstück (beim Freigeben oder Löschen). */
    public void deleteHomesOnPlot(Plot plot) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "DELETE FROM homes WHERE world = ? AND plot_id = ?")) {
            statement.setString(1, plot.world().getName());
            statement.setString(2, plot.id());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Homes konnten nicht gelöscht werden", e);
        }
    }
}
