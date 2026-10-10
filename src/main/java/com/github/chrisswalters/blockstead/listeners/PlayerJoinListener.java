package com.github.chrisswalters.blockstead.listeners;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.players.PlayerUtility;
import com.github.chrisswalters.blockstead.storage.DataException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;


/**
 * Beim Betreten des Servers: Laufzeitdaten anlegen ({@link PlayerUtility#initBSPlayer(Player)}),
 * beim ersten Mal ein Konto mit Startguthaben anlegen, eigene Join-Nachricht setzen und die
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
        try {
            // Beim ersten Join gibt es das Startguthaben
            Blockstead.getEconomy().ensureAccount(player.getUniqueId());
        } catch (DataException e) {
            Blockstead.getInstance().getLogger().log(Level.SEVERE, "Konto für " + player.getName() + " konnte nicht angelegt werden", e);
        }

        Component playerName = Component.text(player.getName(), NamedTextColor.LIGHT_PURPLE);

        // Login-Nachricht aus der config.yml (z.B. Teamspeak, Discord) nur für diese Person
        Blockstead.getPluginConfig().getJoinInfo().forEach(player::sendMessage);

        if (player.hasPlayedBefore()) {
            event.joinMessage(Component.text("Willkommen zurück, ", NamedTextColor.WHITE)
                    .append(playerName)
                    .append(Component.text("! Schön,").decoration(TextDecoration.BOLD, true))
                    .append(Component.text(" dass du wieder da bist!")));
            return;
        }

        event.joinMessage(playerName
                .append(Component.text(" ist neu auf dem Server – herzlich willkommen!", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Willkommen auf unserem Server! Drücke ", NamedTextColor.WHITE)
                .append(Component.keybind("key.chat").color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.BOLD, true))
                .append(Component.text(", um dich im Chat vorzustellen!")));
    }
}
