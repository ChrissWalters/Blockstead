package com.github.chrisswalters.blockstead.listeners;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.players.PlayerUtility;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;


/**
 * Beim Betreten des Servers: Laufzeitdaten anlegen ({@link PlayerUtility#initBSPlayer(Player)}),
 * eigene Join-Nachricht setzen und die
 * Login-Nachricht aus der config.yml ({@code join-info}) an die Person schicken.
 *
 * <p>Wiederkehrende Spielende werden für alle sichtbar zurück begrüßt. Neue Spielende werden allen
 * angekündigt und bekommen zusätzlich nur für sich den Hinweis, sich im Chat vorzustellen.
 */
public class PlayerJoinListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerUtility.initBSPlayer(player);

        // Login-Nachricht aus der config.yml (z.B. Teamspeak, Discord) nur für diese Person
        Blockstead.getPluginConfig().getJoinInfo().forEach(player::sendMessage);

        // Statt einer Nachricht für alle: an jede Person in ihrer Sprache
        event.joinMessage(null);
        if (player.hasPlayedBefore()) {
            Messages.broadcast("join.welcome-back", Messages.arg("name", player.getName()));
            return;
        }
        Messages.broadcast("join.new", Messages.arg("name", player.getName()));
        Messages.send(player, "join.new-hint");
    }
}
