package com.github.chrisswalters.blockstead.listeners;

import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.players.PlayerUtility;
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

        // Statt einer Nachricht für alle: an jede Person in ihrer Sprache
        event.quitMessage(null);
        Messages.broadcast("quit", Messages.arg("name", player.getName()));
    }
}
