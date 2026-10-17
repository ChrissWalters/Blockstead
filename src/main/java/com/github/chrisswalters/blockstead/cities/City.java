package com.github.chrisswalters.blockstead.cities;

import com.github.chrisswalters.blockstead.util.BlocksteadFlags;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;

import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * Eine Stadt: eine WorldGuard-Region mit {@link #PREFIX} in einer bestimmten Welt. Dünne Hülle um die Region;
 * alle Daten stehen in WorldGuard – Baurechte als Mitglieder, Name, Preis und Texte als Flags
 * ({@link BlocksteadFlags}).
 */
public record City(World world, ProtectedRegion region) {

    /** Präfix der Region-IDs von Städten. WorldGuard-IDs sind immer kleingeschrieben. */
    public static final String PREFIX = "city_";

    /** Region-ID, z.B. "city_neustadt". */
    public String id() {
        return region.getId();
    }

    /** Anzeigename, z.B. "Neustadt"; ohne Flag der Teil der ID nach dem Präfix. */
    public String name() {
        String name = BlocksteadFlags.NAME == null ? null : region.getFlag(BlocksteadFlags.NAME);
        return name != null && !name.isBlank() ? name : id().substring(PREFIX.length());
    }

    /** Preis der Stadt für ihre Grundstücke; leer = es gilt der Standardpreis. */
    public OptionalLong price() {
        Integer price = BlocksteadFlags.PRICE == null ? null : region.getFlag(BlocksteadFlags.PRICE);
        return price == null ? OptionalLong.empty() : OptionalLong.of(price);
    }

    /** UUIDs aller Personen, die in der Stadt bauen dürfen (WorldGuard-Mitglieder). */
    public Set<UUID> builderIds() {
        return region.getMembers().getUniqueIds();
    }
}
