package com.github.chrisswalters.blockstead.plots;

import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;

import java.util.Set;
import java.util.UUID;

/**
 * Ein Grundstück: eine WorldGuard-Region mit {@link CreatePlot#PLOT_PREFIX} in einer bestimmten Welt.
 * Dünne Hülle um die Region; alle Daten (Besitzende, Mitglieder, Flags) liegen in WorldGuard.
 */
public record Plot(World world, ProtectedRegion region) {

    /** Region-ID, z.B. "plot_3". */
    public String id() {
        return region.getId();
    }

    /** Frei = niemand besitzt das Grundstück (auch keine LuckPerms-/WorldGuard-Gruppe). */
    public boolean isFree() {
        return region.getOwners().size() == 0;
    }

    /** Ob die Person zu den Besitzenden gehört. */
    public boolean isOwner(UUID uuid) {
        return region.getOwners().contains(uuid);
    }

    /** UUIDs aller Besitzenden. */
    public Set<UUID> ownerIds() {
        return region.getOwners().getUniqueIds();
    }

    /** UUIDs aller Mitglieder (alle Rollen unterhalb von "owner" (Besitzende)). */
    public Set<UUID> memberIds() {
        return region.getMembers().getUniqueIds();
    }
}
