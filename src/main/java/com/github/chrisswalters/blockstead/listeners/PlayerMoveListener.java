package com.github.chrisswalters.blockstead.listeners;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.players.BSPlayer;
import com.github.chrisswalters.blockstead.players.PlayerUtility;
import com.github.chrisswalters.blockstead.plots.CreatePlot;
import com.github.chrisswalters.blockstead.util.WorldGuardUtils;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;

/**
 * Zeigt Nachrichten an, wenn jemand eine WorldGuard-Region betritt oder verlässt.
 *
 * <p>Vergleicht dazu bei jedem Blockwechsel die Regionen an der neuen Position mit den zuletzt
 * bekannten Regionen aus {@link BSPlayer#getLastKnownRegions()}. Beim Betreten wird der Text der
 * Flag {@link Blockstead#REGIONNAME} angezeigt, falls gesetzt, sonst eine Standardnachricht.
 *
 * <p>Einschränkung: Teleports und Fahrten in Fahrzeugen lösen keinen {@link PlayerMoveEvent} aus;
 * die Nachrichten kommen dann erst bei der nächsten eigenen Bewegung.
 */
public class PlayerMoveListener implements Listener {

    private static final TextColor REGION_NAME_COLOR = TextColor.color(0xFF7A58);

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Der Event kommt auch bei reinen Kopfbewegungen; Regionen ändern sich aber nur beim Blockwechsel
        if (event.hasChangedBlock()) {
            updateRegionIntersection(event.getPlayer());
        }
    }

    private void updateRegionIntersection(Player player) {
        RegionManager manager = WorldGuardUtils.getRegionManager(player.getWorld());
        if (manager == null) {
            return;
        }

        Set<String> currentRegions = WorldGuardUtils.getRegionsAtLocation(player.getLocation());
        BSPlayer bsPlayer = PlayerUtility.getBSPlayer(player);
        Set<String> lastKnownRegions = bsPlayer.getLastKnownRegions();

        for (String regionId : currentRegions) {
            if (!lastKnownRegions.contains(regionId)) {
                ProtectedRegion region = manager.getRegion(regionId);
                String regionName = (region != null && Blockstead.REGIONNAME != null)
                        ? region.getFlag(Blockstead.REGIONNAME) : null;
                if (regionName != null) {
                    player.sendMessage(Component.text(regionName, REGION_NAME_COLOR));
                } else {
                    player.sendMessage("Du betrittst " + describe(regionId));
                }
            }
        }

        for (String regionId : lastKnownRegions) {
            if (!currentRegions.contains(regionId)) {
                player.sendMessage("Du verlässt " + describe(regionId));
            }
        }

        bsPlayer.setLastKnownRegions(currentRegions);
    }

    /** "das Grundstück plot_3" bzw. "die Region spawn" – je nachdem, ob die Region ein Plot ist. */
    private static String describe(String regionId) {
        return regionId.startsWith(CreatePlot.PLOT_PREFIX)
                ? "das Grundstück " + regionId
                : "die Region " + regionId;
    }
}
