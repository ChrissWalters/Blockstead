package com.github.chrisswalters.blockstead.plots;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Sicherungen ("Snapshots") von Grundstücken als WorldEdit-Schematic, zum Zurücksetzen beim Freigeben.
 *
 * <p>Ablage: {@code plugins/Blockstead/snapshots/<Welt>/<ID>.schem}. Angelegt bei {@code /plot create}
 * (Zustand beim Anlegen) oder per {@code /plot snapshot <ID>}. Gespeichert wird der komplette
 * Grundstücksbereich vom tiefsten bis zum höchsten Punkt, inkl. Inhalt von Truhen usw., aber ohne
 * Entities.
 *
 * <p>Beim Wiederherstellen wird nur innerhalb der Grundstücksform eingefügt ({@link RegionMask}) –
 * wichtig bei Polygonen, deren umgebendes Rechteck in Nachbargrundstücke ragt. Alle Entities außer
 * Spielenden im Grundstück (Tiere, Rahmen, Rüstungsständer, ...) werden entfernt.
 *
 * <p>Läuft synchron im Hauptthread; bei großen Grundstücken kann der Server dabei kurz stocken.
 */
public class SnapshotService {

    private final File directory;

    public SnapshotService(File pluginDataFolder) {
        this.directory = new File(pluginDataFolder, "snapshots");
    }

    /** Ob die Sicherungsdatei für das Grundstück existiert. */
    public boolean hasSnapshot(Plot plot) {
        return file(plot).isFile();
    }

    /** Speichert den aktuellen Zustand des Grundstücks; eine vorhandene Sicherung wird überschrieben. */
    public void save(Plot plot) throws IOException, WorldEditException {
        Region region = toWorldEditRegion(plot);
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        clipboard.setOrigin(region.getMinimumPoint());

        try (EditSession session = WorldEdit.getInstance().newEditSession(region.getWorld())) {
            ForwardExtentCopy copy = new ForwardExtentCopy(session, region, clipboard, region.getMinimumPoint());
            copy.setCopyingEntities(false);
            Operations.complete(copy);
        }

        File file = file(plot);
        file.getParentFile().mkdirs();
        try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(new FileOutputStream(file))) {
            writer.write(clipboard);
        }
    }

    /**
     * Stellt das Grundstück aus der Sicherung wieder her und entfernt alle Entities außer Spielenden darauf.
     *
     * @throws IOException wenn es keine Sicherung gibt oder sie nicht lesbar ist
     */
    public void restore(Plot plot) throws IOException, WorldEditException {
        File file = file(plot);
        if (!file.isFile()) {
            throw new IOException("Keine Sicherung für " + plot.id());
        }
        Clipboard clipboard;
        try (ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(new FileInputStream(file))) {
            clipboard = reader.read();
        }

        Region region = toWorldEditRegion(plot);
        try (EditSession session = WorldEdit.getInstance().newEditSession(region.getWorld())) {
            // Nur innerhalb der Grundstücksform schreiben
            session.setMask(new RegionMask(region));
            Operations.complete(new ClipboardHolder(clipboard)
                    .createPaste(session)
                    .to(clipboard.getOrigin())
                    .ignoreAirBlocks(false)
                    .copyEntities(false)
                    .build());
        }
        removeEntities(plot);
    }

    /** Löscht die Sicherung (z.B. wenn das Grundstück gelöscht wird). */
    public void delete(Plot plot) {
        File file = file(plot);
        if (file.isFile() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private void removeEntities(Plot plot) {
        ProtectedRegion region = plot.region();
        BoundingBox box = new BoundingBox(
                region.getMinimumPoint().x(), region.getMinimumPoint().y(), region.getMinimumPoint().z(),
                region.getMaximumPoint().x() + 1, region.getMaximumPoint().y() + 1, region.getMaximumPoint().z() + 1);
        for (Entity entity : plot.world().getNearbyEntities(box)) {
            if (!(entity instanceof Player) && region.contains(BukkitAdapter.asBlockVector(entity.getLocation()))) {
                entity.remove();
            }
        }
    }

    /** Gleiche Form wie die WorldGuard-Region (Polygon oder Quader), als WorldEdit-Region. */
    private static Region toWorldEditRegion(Plot plot) {
        World world = BukkitAdapter.adapt(plot.world());
        ProtectedRegion region = plot.region();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        if (region instanceof ProtectedPolygonalRegion polygon) {
            return new Polygonal2DRegion(world, polygon.getPoints(), min.y(), max.y());
        }
        return new CuboidRegion(world, min, max);
    }

    /**
     * Ablageort der Sicherung. Welt- und Region-Namen werden auf sichere Zeichen reduziert, weil
     * WorldGuard in IDs z.B. auch "/" erlaubt – so kann kein Name aus dem Sicherungsordner herausführen.
     */
    private File file(Plot plot) {
        return new File(new File(directory, safeName(plot.world().getName())), safeName(plot.id()) + ".schem");
    }

    private static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
