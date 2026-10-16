package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;

/**
 * Legt Grundstücke (Plots) als WorldGuard-Regionen aus einer WorldEdit-Auswahl an.
 *
 * <p>Ein Plot reicht immer vom tiefsten bis zum höchsten Punkt der Welt, unabhängig von der Höhe
 * der Auswahl. Unterstützt werden Quader-Auswahlen ({@code //sel cuboid}) und Polygon-Auswahlen
 * ({@code //sel poly}). Überlappungen mit anderen Grundstücken werden abgelehnt, bei anderen Regionen mit zu
 * hoher Priorität gibt es eine Warnung ({@link PlotOverlaps}). Neue Plots haben noch keine Besitzenden und Mitglieder. Direkt nach dem Anlegen
 * wird der Zustand gesichert ({@link SnapshotService}), damit das Grundstück später zurückgesetzt werden kann.
 */
public class CreatePlot {

    /** Präfix der Region-IDs von Plots. WorldGuard-IDs sind immer kleingeschrieben. */
    public static final String PLOT_PREFIX = "plot_";

    /**
     * Legt aus der aktuellen WorldEdit-Auswahl der ausführenden Person einen neuen Plot an.
     * Flags und Priorität kommen aus der config.yml ({@link PluginConfig}).
     * Rückmeldungen gehen direkt an diese Person.
     *
     * @return {@code true}, wenn der Plot angelegt wurde
     */
    public static boolean createPlot(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        // Die Auswahl kann in einer anderen Welt liegen als die Person gerade ist
        World selectionWorld = session.getSelectionWorld();

        Region selection;
        try {
            if (selectionWorld == null) throw new IncompleteRegionException();
            selection = session.getSelection(selectionWorld);
        } catch (IncompleteRegionException e) {
            player.sendMessage("Bitte triff zuerst eine vollständige WorldEdit-Auswahl.");
            return false;
        }

        RegionManager regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(selectionWorld);
        if (regionManager == null) {
            player.sendMessage("In dieser Welt sind keine WorldGuard-Regionen verfügbar.");
            return false;
        }

        String plotId = nextFreePlotId(regionManager);
        int minY = selectionWorld.getMinY();
        int maxY = selectionWorld.getMaxY();

        // Die Auswahl selbst wird nicht verändert, sonst hätte die Person danach eine Auswahl über die ganze Welthöhe
        ProtectedRegion plot;
        if (selection instanceof Polygonal2DRegion polygon) {
            plot = new ProtectedPolygonalRegion(plotId, polygon.getPoints(), minY, maxY);
        } else if (selection instanceof CuboidRegion cuboid) {
            BlockVector3 min = cuboid.getMinimumPoint().withY(minY);
            BlockVector3 max = cuboid.getMaximumPoint().withY(maxY);
            plot = new ProtectedCuboidRegion(plotId, min, max);
        } else {
            player.sendMessage("Die Auswahl muss ein Quader (//sel cuboid) oder ein Polygon (//sel poly) sein.");
            return false;
        }

        // Grundstücke dürfen sich nicht überlappen (siehe PlotOverlaps)
        List<ProtectedRegion> overlapping = PlotOverlaps.overlappingPlots(plot, regionManager);
        if (!overlapping.isEmpty()) {
            player.sendMessage("Die Auswahl überlappt " + PlotOverlaps.describe(overlapping, false)
                    + ". Grundstücke dürfen sich nicht überlappen – bitte die Auswahl anpassen.");
            return false;
        }

        PluginConfig config = Blockstead.getPluginConfig();
        plot.setFlags(new HashMap<>(config.getDefaultPlotFlags()));
        if (Blockstead.REGIONNAME != null) {
            plot.setFlag(Blockstead.REGIONNAME, "Du betrittst das Grundstück " + plotId);
        }
        plot.setPriority(config.getPlotPriority());
        regionManager.addRegion(plot);

        player.sendMessage("Grundstück " + plotId + " wurde angelegt.");
        List<ProtectedRegion> conflicting = PlotOverlaps.conflictingRegions(plot, regionManager, config.getPlotPriority());
        if (!conflicting.isEmpty()) {
            player.sendMessage("Achtung: " + PlotOverlaps.describe(conflicting, true) + " überlappt das Grundstück mit "
                    + "gleicher oder höherer Priorität und überstimmt dort Besitzende und Flags. Priorität dieser Region(en) "
                    + "unter " + config.getPlotPriority() + " setzen, z.B. /rg setpriority <ID> 0.");
        }
        // Zustand beim Anlegen sichern, damit das Grundstück beim Freigeben zurückgesetzt werden kann
        Blockstead.getPlotService().saveSnapshot(player, new Plot(BukkitAdapter.adapt(selectionWorld), plot));
        Blockstead.getInstance().getLogger().info("Grundstück " + plotId + " in Welt " + selectionWorld.getName()
                + " angelegt von " + player.getName());
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
