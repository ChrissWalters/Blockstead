package com.github.chrisswalters.blockstead.util;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Hilfsmethoden für den Zugriff auf WorldGuard-Regionen mit Bukkit-Typen.
 */
public class WorldGuardUtils {

    /**
     * Liefert den RegionManager einer Welt.
     *
     * @return {@code null}, wenn in der Welt Regionen deaktiviert sind oder die Regionsdaten nicht geladen werden konnten
     */
    public static RegionManager getRegionManager(World world) {
        return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
    }

    /**
     * Liefert die IDs aller Regionen, die die angegebene Position enthalten
     * (ohne die globale Region {@code __global__}).
     *
     * @return veränderbares Set; leer, wenn die Welt keine Regionen unterstützt
     */
    public static Set<String> getRegionsAtLocation(Location location) {
        Set<String> regionNames = new HashSet<>();
        RegionManager regionManager = getRegionManager(location.getWorld());
        if (regionManager == null) {
            return regionNames;
        }
        for (ProtectedRegion region : regionManager.getApplicableRegions(BukkitAdapter.asBlockVector(location))) {
            regionNames.add(region.getId());
        }
        return regionNames;
    }
}
