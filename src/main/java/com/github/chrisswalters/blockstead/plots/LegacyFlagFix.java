package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.storage.Database;
import com.github.chrisswalters.blockstead.util.WorldGuardUtils;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.List;
import java.util.logging.Logger;

/**
 * Einmalige Korrektur alter Grundstücke (vor Version 0.1.0 angelegt).
 *
 * <p>Früher wurden Grundstücke mit Schutz-Flags wie {@code use: deny} angelegt. Diese Flags gelten in
 * WorldGuard für alle – auch für Besitzende und Mitglieder –, wodurch diese z.B. keine Türen oder Truhen
 * auf dem eigenen Grundstück benutzen konnten (Ops fielen nicht auf, da WorldGuard ihnen alles erlaubt).
 * Fremde sperrt WorldGuard über die Mitgliedschaft ohnehin aus, die Flags werden also nicht gebraucht.
 *
 * <p>Entfernt werden nur Flags, die auf {@code deny} stehen und keine Gruppe ({@code -g}) haben.
 * Läuft genau einmal (Merker in der Tabelle {@code meta}); jede Änderung wird geloggt.
 */
public final class LegacyFlagFix {

    private static final String META_KEY = "legacy-protection-flags-removed";

    /** Schutz-Flags, die auf Grundstücken nicht per deny gesetzt werden dürfen (auch von PluginConfig genutzt). */
    public static final List<StateFlag> PROTECTION_FLAGS = List.of(
            Flags.INTERACT, Flags.USE, Flags.CHEST_ACCESS, Flags.RIDE,
            Flags.PLACE_VEHICLE, Flags.DESTROY_VEHICLE, Flags.LIGHTER);

    private LegacyFlagFix() {
    }

    /** Führt die Korrektur aus, falls sie noch nie gelaufen ist (Merker in der Tabelle {@code meta}). */
    public static void runOnce(Database database, Logger log) {
        if (database.getMeta(META_KEY) != null) {
            return;
        }
        int changedRegions = 0;
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = WorldGuardUtils.getRegionManager(world);
            if (manager == null) {
                continue;
            }
            boolean worldChanged = false;
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (!region.getId().startsWith(CreatePlot.PLOT_PREFIX)) {
                    continue;
                }
                StringBuilder removed = new StringBuilder();
                for (StateFlag flag : PROTECTION_FLAGS) {
                    if (region.getFlag(flag) == StateFlag.State.DENY && region.getFlag(flag.getRegionGroupFlag()) == null) {
                        region.setFlag(flag, null);
                        removed.append(removed.isEmpty() ? "" : ", ").append(flag.getName());
                    }
                }
                if (!removed.isEmpty()) {
                    log.info("Altdaten-Korrektur: " + region.getId() + " in " + world.getName() + " – entfernt: " + removed);
                    changedRegions++;
                    worldChanged = true;
                }
            }
            if (worldChanged) {
                try {
                    manager.saveChanges();
                } catch (StorageException e) {
                    log.severe("Altdaten-Korrektur: Regionen in " + world.getName() + " konnten nicht gespeichert werden: " + e.getMessage());
                    return; // Merker nicht setzen, beim nächsten Start erneut versuchen
                }
            }
        }
        database.setMeta(META_KEY, String.valueOf(System.currentTimeMillis()));
        log.info("Altdaten-Korrektur abgeschlossen: " + changedRegions + " Grundstück(e) angepasst.");
    }
}
