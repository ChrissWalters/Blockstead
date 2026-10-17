package com.github.chrisswalters.blockstead.util;

import com.github.chrisswalters.blockstead.lang.Messages;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Liest die WorldEdit-Auswahl einer Person als Fläche, die vom tiefsten bis zum höchsten Punkt der Welt reicht –
 * unabhängig von der Höhe der Auswahl. Grundlage für Grundstücke und Städte. Unterstützt werden Quader
 * ({@code //sel cuboid}) und Polygone ({@code //sel poly}). Die Auswahl selbst wird nicht verändert, sonst hätte
 * die Person danach eine Auswahl über die ganze Welthöhe.
 */
public final class Selections {

    /** Eine gültige Auswahl in einer Welt (sie kann in einer anderen Welt liegen als die Person gerade ist). */
    public record Shape(com.sk89q.worldedit.world.World weWorld, Region selection) {

        /** Die Welt der Auswahl als Bukkit-Welt. */
        public World world() {
            return BukkitAdapter.adapt(weWorld);
        }

        /** Neue WorldGuard-Region mit dieser Fläche über die ganze Welthöhe (ohne Flags, Priorität 0). */
        public ProtectedRegion toRegion(String id) {
            int minY = weWorld.getMinY();
            int maxY = weWorld.getMaxY();
            if (selection instanceof Polygonal2DRegion polygon) {
                return new ProtectedPolygonalRegion(id, polygon.getPoints(), minY, maxY);
            }
            BlockVector3 min = selection.getMinimumPoint().withY(minY);
            BlockVector3 max = selection.getMaximumPoint().withY(maxY);
            return new ProtectedCuboidRegion(id, min, max);
        }
    }

    private Selections() {
    }

    /**
     * Die aktuelle WorldEdit-Auswahl der Person; meldet selbst, wenn es keine vollständige Auswahl gibt oder sie
     * weder Quader noch Polygon ist.
     *
     * @return {@code null} bei fehlender oder falscher Auswahl
     */
    public static Shape read(Player player) {
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
        com.sk89q.worldedit.world.World world = session.getSelectionWorld();
        Region selection;
        try {
            if (world == null) throw new IncompleteRegionException();
            selection = session.getSelection(world);
        } catch (IncompleteRegionException e) {
            Messages.send(player, "create.no-selection");
            return null;
        }
        if (!(selection instanceof Polygonal2DRegion) && !(selection instanceof CuboidRegion)) {
            Messages.send(player, "create.wrong-shape");
            return null;
        }
        return new Shape(world, selection);
    }
}
