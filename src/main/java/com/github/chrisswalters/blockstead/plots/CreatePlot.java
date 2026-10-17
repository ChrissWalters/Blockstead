package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.cities.City;
import com.github.chrisswalters.blockstead.util.Selections;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Legt Grundstücke (Plots) als WorldGuard-Regionen aus einer WorldEdit-Auswahl an.
 *
 * <p>Ein Plot reicht vom tiefsten bis zum höchsten Punkt der Welt, unabhängig von der Höhe der Auswahl, oder
 * zwischen den beim Anlegen angegebenen Höhen ({@link Selections}). Überlappungen mit anderen Grundstücken werden abgelehnt, bei anderen Regionen mit zu
 * hoher Priorität gibt es eine Warnung ({@link PlotOverlaps}). Neue Plots haben noch keine Besitzenden und Mitglieder. Direkt nach dem Anlegen
 * wird der Zustand gesichert ({@link SnapshotService}), damit das Grundstück später zurückgesetzt werden kann.
 */
public class CreatePlot {

    /** Präfix der Region-IDs von Plots. WorldGuard-IDs sind immer kleingeschrieben. */
    public static final String PLOT_PREFIX = "plot_";

    /**
     * Legt aus der aktuellen WorldEdit-Auswahl der ausführenden Person einen neuen Plot an, optional mit
     * Höhengrenzen. Grundstücke dürfen dann auch übereinander liegen (z.B. Wohnungen), solange sie sich nicht
     * überschneiden.
     * Flags und Priorität kommen aus der config.yml ({@link PluginConfig}).
     * Rückmeldungen gehen direkt an diese Person.
     *
     * @return {@code true}, wenn der Plot angelegt wurde
     */
    public static boolean createPlot(Player player, Selections.Heights heights) {
        Selections.Shape shape = Selections.read(player);
        if (shape == null || !shape.checkHeights(player, heights)) {
            return false;
        }
        World selectionWorld = shape.weWorld();
        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(selectionWorld);
        if (regionManager == null) {
            Messages.send(player, "plot.no-regions");
            return false;
        }

        String plotId = nextFreePlotId(regionManager);
        ProtectedRegion plot = shape.toRegion(plotId, heights);

        // Grundstücke dürfen sich nicht überlappen (siehe PlotOverlaps)
        List<ProtectedRegion> overlapping = PlotOverlaps.overlappingPlots(plot, regionManager);
        if (!overlapping.isEmpty()) {
            Messages.send(player, "create.overlap", Messages.arg("plots", PlotOverlaps.describe(overlapping, false)));
            return false;
        }

        PluginConfig config = Blockstead.getPluginConfig();
        plot.setFlags(new HashMap<>(config.getDefaultPlotFlags()));
        // region-name bleibt leer: die Standardmeldung beim Betreten kommt übersetzt aus der Sprachdatei
        plot.setPriority(config.getPlotPriority());
        regionManager.addRegion(plot);

        Messages.send(player, "create.done", Messages.arg("id", plotId));
        List<ProtectedRegion> conflicting = PlotOverlaps.conflictingRegions(plot, regionManager, config.getPlotPriority());
        if (!conflicting.isEmpty()) {
            Messages.send(player, "create.priority-warning", Messages.arg("regions", PlotOverlaps.describe(conflicting, true)),
                    Messages.arg("priority", config.getPlotPriority()));
        }
        // Zu welcher Stadt das Grundstück gehört – bei mehreren gilt die mit dem größten gemeinsamen Raum
        Plot created = new Plot(shape.world(), plot);
        List<City> cities = Blockstead.getCities().citiesTouching(created);
        if (heights.limited() && cities.isEmpty()) {
            // Über bzw. unter dem Grundstück gelten dessen Regeln nicht; ohne Stadt schützt dort nichts
            Messages.send(player, "create.height-unprotected", Messages.arg("bottom", plot.getMinimumPoint().y()),
                    Messages.arg("top", plot.getMaximumPoint().y()));
        }
        if (cities.size() == 1) {
            Messages.send(player, "create.in-city", Messages.arg("city", cities.getFirst().name()));
        } else if (cities.size() > 1) {
            Messages.send(player, "create.several-cities", Messages.arg("cities",
                            cities.stream().map(City::name).collect(Collectors.joining(", "))),
                    Messages.arg("city", Blockstead.getCities().cityOf(created).map(City::name).orElse("?")));
        }
        // Zustand beim Anlegen sichern, damit das Grundstück beim Freigeben zurückgesetzt werden kann
        Blockstead.getPlotService().saveSnapshot(player, created);
        Blockstead.getInstance().getLogger().info("Plot " + plotId + " in world " + selectionWorld.getName()
                + " created by " + player.getName());
        return true;
    }

    /** Liefert die kleinste noch freie Plot-ID der Welt, z.B. "plot_3" (Lücken durch gelöschte Plots werden wieder belegt). */
    private static String nextFreePlotId(RegionManager regionManager) {
        int number = 0;
        while (regionManager.hasRegion(PLOT_PREFIX + number)) {
            number++;
        }
        return PLOT_PREFIX + number;
    }
}
