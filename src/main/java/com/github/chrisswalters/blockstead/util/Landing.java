package com.github.chrisswalters.blockstead.util;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Sucht einen sicheren Landepunkt in einer Region – für {@code /plot tp} und {@code /city tp} ohne gesetzten
 * Landepunkt.
 *
 * <p>Spiralförmig vom {@link #landingCenter Mittelpunkt} aus wird die erste Spalte genommen, die in der Region
 * liegt und innerhalb ihrer Höhe einen Platz zum Stehen hat: fester Boden (oder Wasser) ohne gefährlichen Block
 * ({@link #UNSAFE_GROUND}) und zwei freie Blöcke darüber. Gesucht wird von oben nach unten, beginnend am obersten
 * Block der Spalte bzw. an der Obergrenze der Region – bei Regionen über die ganze Welthöhe landet man also auf
 * der Oberfläche, bei einem Geschoss eines Hauses auf dessen Boden statt auf dem Dach.
 */
public final class Landing {

    /** Boden, auf dem man nicht landen soll (Schaden oder Festhängen). */
    private static final Set<Material> UNSAFE_GROUND = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE);

    /** Höchstens so viele Spalten werden abgesucht (jede kann einen Chunk laden). */
    private static final int MAX_LANDING_COLUMNS = 4096;

    private Landing() {
    }

    /**
     * Sicherer Landepunkt in der Region.
     *
     * @param accept zusätzliche Bedingung für den Landepunkt (z.B. "nicht auf einem Grundstück")
     * @return leer, wenn kein Punkt gefunden wurde
     */
    public static Optional<Location> find(World world, ProtectedRegion region, Predicate<Location> accept) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int[] center = landingCenter(region);
        int centerX = center[0];
        int centerZ = center[1];
        // Weit genug, um vom Startpunkt aus das ganze umgebende Rechteck abzudecken
        int radius = Math.max(Math.max(centerX - min.x(), max.x() - centerX), Math.max(centerZ - min.z(), max.z() - centerZ)) + 1;

        int checked = 0;
        for (int r = 0; r <= radius; r++) {
            for (int[] column : ring(centerX, centerZ, r)) {
                if (!region.contains(column[0], min.y(), column[1])) {
                    continue;
                }
                if (++checked > MAX_LANDING_COLUMNS) {
                    return Optional.empty();
                }
                Location spot = standingSpot(world, column[0], column[1], min.y(), max.y());
                if (spot != null && accept.test(spot)) {
                    return Optional.of(spot);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Oberster Platz zum Stehen in der Spalte, an dem die Füße zwischen {@code minY} und {@code maxY} sind;
     * {@code null}, wenn es keinen gibt oder der Boden gefährlich ist.
     */
    private static Location standingSpot(World world, int x, int z, int minY, int maxY) {
        int top = Math.min(maxY - 1, world.getHighestBlockYAt(x, z));
        int bottom = Math.max(minY - 1, world.getMinHeight());
        for (int y = top; y >= bottom; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (ground.isPassable() && !ground.isLiquid()) {
                continue;
            }
            if (UNSAFE_GROUND.contains(ground.getType())) {
                return null;
            }
            Block feet = ground.getRelative(0, 1, 0);
            Block head = ground.getRelative(0, 2, 0);
            if (y + 2 < world.getMaxHeight() && isFree(feet) && isFree(head)) {
                return ground.getLocation().add(0.5, 1, 0.5);
            }
        }
        return null;
    }

    /** Frei zum Stehen: durchlässig, keine Flüssigkeit, nicht gefährlich (z.B. Feuer). */
    private static boolean isFree(Block block) {
        return block.isPassable() && !block.isLiquid() && !UNSAFE_GROUND.contains(block.getType());
    }

    /**
     * Startpunkt der Suche – möglichst mittig im Grundstück. Bei Quadern die Mitte. Bei Polygonen in zwei
     * Schritten:
     * <ol>
     *     <li>alle Punkte im Grundstück, die gut innen liegen: Randabstand mindestens 75 % des größten
     *     Randabstands (bei einem 3 Blöcke breiten Schenkel also nur die Mittellinie, nie der Rand);</li>
     *     <li>davon der Punkt, der dem Flächenschwerpunkt am nächsten liegt (sonst wäre bei schmalen Formen,
     *     wo viele Punkte gleich weit vom Rand entfernt sind, z.B. auch das Ende eines Schenkels möglich).</li>
     * </ol>
     * Die Mitte des umgebenden Rechtecks allein läge bei einem L in der Einbuchtung, also außerhalb.
     * Gerastert wird mit höchstens 33 × 33 Punkten; für die Landung reicht diese Genauigkeit.
     */
    private static int[] landingCenter(ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int[] boxCenter = {(min.x() + max.x()) / 2, (min.z() + max.z()) / 2};
        if (!(region instanceof ProtectedPolygonalRegion polygon)) {
            return boxCenter;
        }
        List<BlockVector2> points = polygon.getPoints();
        double[] centroid = centroid(points, boxCenter);

        int stepX = Math.max(1, (max.x() - min.x()) / 32);
        int stepZ = Math.max(1, (max.z() - min.z()) / 32);
        List<double[]> candidates = new ArrayList<>(); // {x, z, Randabstand}
        double maxDistance = 0;
        for (int x = min.x(); x <= max.x(); x += stepX) {
            for (int z = min.z(); z <= max.z(); z += stepZ) {
                if (region.contains(x, min.y(), z)) {
                    double distance = distanceToEdges(points, x + 0.5, z + 0.5);
                    candidates.add(new double[]{x, z, distance});
                    maxDistance = Math.max(maxDistance, distance);
                }
            }
        }

        int[] best = boxCenter;
        double bestToCentroid = Double.MAX_VALUE;
        for (double[] candidate : candidates) {
            if (candidate[2] < maxDistance * 0.75) {
                continue;
            }
            double toCentroid = Math.hypot(candidate[0] + 0.5 - centroid[0], candidate[1] + 0.5 - centroid[1]);
            if (toCentroid < bestToCentroid) {
                bestToCentroid = toCentroid;
                best = new int[]{(int) candidate[0], (int) candidate[1]};
            }
        }
        return best;
    }

    /** Flächenschwerpunkt des Polygons (Gaußsche Trapezformel, Eckpunkte als Blockmitten); bei Fläche 0 der Ersatzwert. */
    private static double[] centroid(List<BlockVector2> points, int[] fallback) {
        double area = 0, sumX = 0, sumZ = 0;
        for (int i = 0; i < points.size(); i++) {
            BlockVector2 a = points.get(i);
            BlockVector2 b = points.get((i + 1) % points.size());
            double ax = a.x() + 0.5, az = a.z() + 0.5, bx = b.x() + 0.5, bz = b.z() + 0.5;
            double cross = ax * bz - bx * az;
            area += cross;
            sumX += (ax + bx) * cross;
            sumZ += (az + bz) * cross;
        }
        if (Math.abs(area) < 1e-9) {
            return new double[]{fallback[0] + 0.5, fallback[1] + 0.5};
        }
        return new double[]{sumX / (3 * area), sumZ / (3 * area)};
    }

    /** Kleinster Abstand des Punkts zu den Kanten des Polygons (Eckpunkte als Blockmitten). */
    private static double distanceToEdges(List<BlockVector2> points, double px, double pz) {
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            BlockVector2 a = points.get(i);
            BlockVector2 b = points.get((i + 1) % points.size());
            double ax = a.x() + 0.5, az = a.z() + 0.5, dx = b.x() - a.x(), dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / lengthSquared));
            nearest = Math.min(nearest, Math.hypot(px - (ax + t * dx), pz - (az + t * dz)));
        }
        return nearest;
    }

    /** Alle Spalten auf dem Rand des Quadrats mit Abstand {@code r} um den Mittelpunkt. */
    private static List<int[]> ring(int centerX, int centerZ, int r) {
        List<int[]> columns = new ArrayList<>();
        if (r == 0) {
            columns.add(new int[]{centerX, centerZ});
            return columns;
        }
        for (int i = -r; i <= r; i++) {
            columns.add(new int[]{centerX + i, centerZ - r});
            columns.add(new int[]{centerX + i, centerZ + r});
        }
        for (int i = -r + 1; i <= r - 1; i++) {
            columns.add(new int[]{centerX - r, centerZ + i});
            columns.add(new int[]{centerX + r, centerZ + i});
        }
        return columns;
    }
}
