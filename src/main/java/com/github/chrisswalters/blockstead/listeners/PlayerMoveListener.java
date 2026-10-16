package com.github.chrisswalters.blockstead.listeners;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.lang.Messages;
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
                if (regionName != null && !isLegacyDefault(regionName, regionId)) {
                    player.sendMessage(Component.text(regionName, REGION_NAME_COLOR));
                } else {
                    Messages.send(player, isPlot(regionId) ? "region.enter-plot" : "region.enter-region", Messages.arg("id", regionId));
                }
            }
        }

        for (String regionId : lastKnownRegions) {
            if (!currentRegions.contains(regionId)) {
                Messages.send(player, isPlot(regionId) ? "region.leave-plot" : "region.leave-region", Messages.arg("id", regionId));
            }
        }

        bsPlayer.setLastKnownRegions(currentRegions);
    }

    private static boolean isPlot(String regionId) {
        return regionId.startsWith(CreatePlot.PLOT_PREFIX);
    }

    /**
     * Blockstead bis 2.0.x hat beim Anlegen den deutschen Standardtext "Du betrittst das Grundstück plot_3" in die
     * Flag {@code region-name} geschrieben. Solche Texte werden wie "keine Flag" behandelt, damit auch alte
     * Grundstücke die übersetzte Standardmeldung zeigen; eigene Texte bleiben unverändert.
     */
    private static boolean isLegacyDefault(String regionName, String regionId) {
        return regionName.equals("Du betrittst das Grundstück " + regionId);
    }
}
