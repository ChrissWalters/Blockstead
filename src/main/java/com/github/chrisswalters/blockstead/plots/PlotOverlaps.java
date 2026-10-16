package com.github.chrisswalters.blockstead.plots;

import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Prüft Überlappungen von Grundstücken mit anderen Regionen.
 *
 * <p>Regeln von WorldGuard bei Überlappung: Nur die Regionen mit der höchsten Priorität zählen; bei gleicher
 * Priorität muss man in <em>allen</em> Mitglied sein, um zu bauen, und {@code deny} schlägt {@code allow}.
 * Daraus folgt:
 * <ul>
 *     <li>Zwei Grundstücke dürfen sich nie überlappen – im gemeinsamen Bereich dürfte sonst kaum jemand bauen,
 *     und Blockstead könnte nicht eindeutig sagen, welches Grundstück dort gilt.</li>
 *     <li>Andere Regionen (z.B. eine Stadt mit Straßen) dürfen Grundstücke überlappen, brauchen aber eine
 *     <em>niedrigere</em> Priorität als die Grundstücke, sonst überstimmen sie deren Besitzende und Flags.</li>
 * </ul>
 */
public final class PlotOverlaps {

    private PlotOverlaps() {
    }

    /** Alle Grundstücke, die {@code region} überlappen (ohne {@code region} selbst). */
    public static List<ProtectedRegion> overlappingPlots(ProtectedRegion region, RegionManager manager) {
        List<ProtectedRegion> result = new ArrayList<>();
        for (ProtectedRegion other : intersecting(region, manager)) {
            if (other.getId().startsWith(CreatePlot.PLOT_PREFIX)) {
                result.add(other);
            }
        }
        return result;
    }

    /**
     * Andere Regionen (keine Grundstücke), die {@code region} überlappen und eine Priorität von mindestens
     * {@code plotPriority} haben – sie würden den Grundstücken dort Rechte und Flags streitig machen.
     */
    public static List<ProtectedRegion> conflictingRegions(ProtectedRegion region, RegionManager manager, int plotPriority) {
        List<ProtectedRegion> result = new ArrayList<>();
        for (ProtectedRegion other : intersecting(region, manager)) {
            if (!other.getId().startsWith(CreatePlot.PLOT_PREFIX) && other.getPriority() >= plotPriority) {
                result.add(other);
            }
        }
        return result;
    }

    /** Überlappende Regionen ohne die globale Region und ohne {@code region} selbst. */
    private static List<ProtectedRegion> intersecting(ProtectedRegion region, RegionManager manager) {
        List<ProtectedRegion> candidates = new ArrayList<>();
        for (ProtectedRegion other : manager.getRegions().values()) {
            if (!(other instanceof GlobalProtectedRegion) && !other.getId().equals(region.getId())) {
                candidates.add(other);
            }
        }
        return region.getIntersectingRegions(candidates);
    }

    /** IDs (optional mit Priorität) für Meldungen, z.B. "stadt (10)" – sprachneutral. */
    public static String describe(List<ProtectedRegion> regions, boolean withPriority) {
        List<String> parts = new ArrayList<>();
        for (ProtectedRegion region : regions) {
            parts.add(withPriority ? region.getId() + " (" + region.getPriority() + ")" : region.getId());
        }
        return String.join(", ", parts);
    }
}
