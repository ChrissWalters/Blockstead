package com.github.chrisswalters.blockstead.players;

import com.github.chrisswalters.blockstead.util.WorldGuardUtils;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Hält zu jeder Person, die online ist, ein {@link BSPlayer}-Objekt.
 *
 * <p>Lebenszyklus: angelegt beim Join bzw. Plugin-Start ({@link #initBSPlayer(Player)}),
 * entfernt beim Verlassen ({@link #removeBSPlayer(Player)}).
 */
public class PlayerUtility {

    private static final Map<UUID, BSPlayer> playerClasses = new HashMap<>();

    /**
     * Legt die Daten für eine Person neu an und merkt sich die Regionen an ihrer aktuellen Position,
     * damit sie dafür keine "Region betreten"-Nachricht bekommt.
     */
    public static BSPlayer initBSPlayer(Player player) {
        BSPlayer bsPlayer = new BSPlayer();
        bsPlayer.setLastKnownRegions(WorldGuardUtils.getRegionsAtLocation(player.getLocation()));
        playerClasses.put(player.getUniqueId(), bsPlayer);
        return bsPlayer;
    }

    /** Liefert die Daten zur Person; legt sie an, falls noch nicht vorhanden. */
    public static BSPlayer getBSPlayer(Player player) {
        BSPlayer bsPlayer = playerClasses.get(player.getUniqueId());
        return bsPlayer != null ? bsPlayer : initBSPlayer(player);
    }

    /** Entfernt die Daten zur Person, damit sie nach dem Verlassen nicht im Speicher bleiben. */
    public static void removeBSPlayer(Player player) {
        playerClasses.remove(player.getUniqueId());
    }
}
