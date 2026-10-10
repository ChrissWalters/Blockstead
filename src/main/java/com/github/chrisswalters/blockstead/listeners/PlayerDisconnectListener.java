package com.github.chrisswalters.blockstead.listeners;

import com.github.chrisswalters.blockstead.players.PlayerUtility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Beim Verlassen des Servers: Laufzeitdaten entfernen und eigene Quit-Nachricht setzen.
 */
public class PlayerDisconnectListener implements Listener {

    @EventHandler
    public void onPlayerDisconnect(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerUtility.removeBSPlayer(player);

        final TextComponent leaveMessage;
        leaveMessage = Component.text("").color(NamedTextColor.WHITE)
                .append(Component.text(player.getName(), NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(" hat den Server verlassen. Wir "))
                .append(Component.text("hoffen").decoration(TextDecoration.BOLD, true))
                .append(Component.text(" auf ein baldiges Wiedersehen!"));
        event.quitMessage(leaveMessage);
    }



}
