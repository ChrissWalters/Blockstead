package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Prüft beim Start, ob die Datenbank zu den WorldGuard-Regionen passt.
 *
 * <p>Meldet Einträge (Käufe, Homes, Rollen) zu Grundstücken, die es in WorldGuard nicht gibt –
 * z.B. weil eine Region per {@code /rg remove} statt {@code /plot remove} gelöscht oder WorldGuard-Daten
 * aus einer Sicherung zurückgespielt wurden. Es wird bewusst nichts automatisch gelöscht; die Meldung
 * im Log ist ein Hinweis für das Team. Welten, die gerade nicht geladen sind, werden übersprungen.
 */
public final class ConsistencyCheck {

    private ConsistencyCheck() {
    }

    /** Führt die Prüfung aus und schreibt das Ergebnis ins Log. */
    public static void run(Database database, PlotService plots, Logger log) {
        List<String> problems = new ArrayList<>();
        for (String table : List.of("plot_claims", "homes", "plot_roles")) {
            try (Statement statement = database.getConnection().createStatement();
                 ResultSet result = statement.executeQuery("SELECT DISTINCT world, plot_id FROM " + table)) {
                while (result.next()) {
                    World world = Bukkit.getWorld(result.getString(1));
                    String plotId = result.getString(2);
                    if (world != null && plots.getPlot(world, plotId).isEmpty()) {
                        problems.add(table + ": " + plotId + " in " + world.getName());
                    }
                }
            } catch (SQLException e) {
                throw new DataException("Konsistenzprüfung fehlgeschlagen", e);
            }
        }
        if (problems.isEmpty()) {
            log.info("Konsistenzprüfung: Datenbank passt zu den WorldGuard-Regionen.");
            return;
        }
        log.warning("Konsistenzprüfung: " + problems.size() + " Eintrag/Einträge zu Grundstücken, die es in WorldGuard nicht gibt "
                + "(es wurde nichts gelöscht):");
        problems.forEach(problem -> log.warning("  - " + problem));
    }
}
