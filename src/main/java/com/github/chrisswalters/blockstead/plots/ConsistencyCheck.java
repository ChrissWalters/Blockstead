package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;
import com.github.chrisswalters.blockstead.util.WorldGuardUtils;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Prüft beim Start, ob die Datenbank zu den WorldGuard-Regionen passt und ob sich Grundstücke problematisch
 * überlappen ({@link PlotOverlaps}).
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
    public static void run(Database database, PlotService plots, int plotPriority, Logger log) {
        checkOverlaps(plots, plotPriority, log);
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

    /**
     * Meldet überlappende Grundstücke und Regionen, die Grundstücke mit zu hoher Priorität überlappen
     * (siehe {@link PlotOverlaps}). Ändert nichts.
     */
    private static void checkOverlaps(PlotService plots, int plotPriority, Logger log) {
        List<String> problems = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = WorldGuardUtils.getRegionManager(world);
            if (manager == null) {
                continue;
            }
            for (Plot plot : plots.getPlots(world)) {
                for (ProtectedRegion other : PlotOverlaps.overlappingPlots(plot.region(), manager)) {
                    // Jedes Paar nur einmal melden
                    if (plot.id().compareTo(other.getId()) < 0) {
                        problems.add(plot.id() + " und " + other.getId() + " in " + world.getName()
                                + " überlappen sich – eins davon verkleinern oder löschen");
                    }
                }
                List<ProtectedRegion> conflicting = PlotOverlaps.conflictingRegions(plot.region(), manager, plotPriority);
                if (!conflicting.isEmpty()) {
                    problems.add(PlotOverlaps.describe(conflicting, true) + " überlappt " + plot.id() + " in " + world.getName()
                            + " mit zu hoher Priorität (Grundstücke: " + plot.region().getPriority() + ")");
                }
            }
        }
        if (problems.isEmpty()) {
            log.info("Konsistenzprüfung: keine problematischen Überlappungen von Grundstücken.");
            return;
        }
        log.warning("Konsistenzprüfung: " + problems.size() + " Problem(e) mit Überlappungen (es wurde nichts geändert):");
        problems.forEach(problem -> log.warning("  - " + problem));
    }
}
