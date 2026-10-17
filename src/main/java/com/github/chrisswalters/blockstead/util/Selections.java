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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Liest die WorldEdit-Auswahl einer Person als Fläche, die vom tiefsten bis zum höchsten Punkt der Welt reicht –
 * unabhängig von der Höhe der Auswahl – oder, mit {@link Heights}, zwischen festen Höhen. Grundlage für Grundstücke und Städte. Unterstützt werden Quader
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

        /**
         * Neue WorldGuard-Region mit dieser Fläche (ohne Flags, Priorität 0) – in der Höhe von {@code heights}, ohne
         * Angabe vom tiefsten bis zum höchsten Punkt der Welt. Vorher mit {@link #checkHeights} prüfen.
         */
        public ProtectedRegion toRegion(String id, Heights heights) {
            if (heights.fromSelection()) {
                return toRegion(id, new Heights(selection.getMinimumPoint().y(), selection.getMaximumPoint().y(), false));
            }
            int minY = heights.bottom() != null ? heights.bottom() : weWorld.getMinY();
            int maxY = heights.top() != null ? heights.top() : weWorld.getMaxY();
            if (selection instanceof Polygonal2DRegion polygon) {
                return new ProtectedPolygonalRegion(id, polygon.getPoints(), minY, maxY);
            }
            BlockVector3 min = selection.getMinimumPoint().withY(minY);
            BlockVector3 max = selection.getMaximumPoint().withY(maxY);
            return new ProtectedCuboidRegion(id, min, max);
        }

        /**
         * Prüft die Höhengrenzen gegen die Welt (Weltboden bis Bauhöhe, unten nicht über oben); meldet selbst, wenn sie
         * nicht passen.
         */
        public boolean checkHeights(CommandSender sender, Heights heights) {
            if (heights.fromSelection()) {
                return true; // die Auswahl liegt immer innerhalb der Welt
            }
            int worldMin = weWorld.getMinY();
            int worldMax = weWorld.getMaxY();
            int bottom = heights.bottom() != null ? heights.bottom() : worldMin;
            int top = heights.top() != null ? heights.top() : worldMax;
            if (bottom < worldMin || top > worldMax || bottom > top) {
                Messages.send(sender, "create.height-invalid", Messages.arg("bottom", bottom), Messages.arg("top", top),
                        Messages.arg("min", worldMin), Messages.arg("max", worldMax));
                return false;
            }
            return true;
        }
    }

    /**
     * Optionale Höhengrenzen beim Anlegen: {@code bottom <Y>}, {@code top <Y>} (beide einschließlich; {@code null} =
     * Weltboden bzw. Bauhöhe) oder {@code selection} – genau die Höhe der WorldEdit-Auswahl, z.B. für Stockwerke.
     */
    public record Heights(Integer bottom, Integer top, boolean fromSelection) {

        /** Keine Grenzen: vom tiefsten bis zum höchsten Punkt der Welt. */
        public static final Heights NONE = new Heights(null, null, false);

        /** Ob die Höhe begrenzt ist (Grenze angegeben oder Höhe der Auswahl). */
        public boolean limited() {
            return fromSelection || bottom != null || top != null;
        }
    }

    /**
     * Ob die Region nicht vom tiefsten bis zum höchsten Punkt der Welt reicht – dann zeigen {@code /plot info} und
     * {@code /city info} ihre Höhe an.
     */
    public static boolean isHeightLimited(ProtectedRegion region, World world) {
        return region.getMinimumPoint().y() > world.getMinHeight() || region.getMaximumPoint().y() < world.getMaxHeight() - 1;
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
