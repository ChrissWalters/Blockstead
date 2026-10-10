package com.github.chrisswalters.blockstead.display;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Chat-Format (eigener {@link io.papermc.paper.chat.ChatRenderer}) sowie Tab-Name und Team beim Join/Quit.
 * Wird nur registriert, wenn LuckPerms installiert und {@code display.enabled} an ist.
 */
public class DisplayListener implements Listener {

    private final DisplayService display;

    public DisplayListener(DisplayService display) {
        this.display = display;
    }

    /**
     * Ersetzt das Chat-Format. Die Nachricht selbst bleibt die reine Text-Component, die Paper aus der
     * Eingabe gebaut hat – {@code &c} oder {@code <red>} darin erscheinen als normaler Text.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.renderer((source, sourceDisplayName, message, viewer) -> display.chatLine(source, message));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        display.apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        display.removeTeam(event.getPlayer());
    }
}
