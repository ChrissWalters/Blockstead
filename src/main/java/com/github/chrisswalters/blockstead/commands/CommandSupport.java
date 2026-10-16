package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.storage.DataException;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * Gemeinsame Hilfsmethoden für die Befehlsklassen.
 */
final class CommandSupport {

    /** Wie lange eine Sicherheitsabfrage gilt. */
    private static final long CONFIRM_MILLIS = 30_000;

    /** Offene Sicherheitsabfragen pro Absender (UUID bzw. "CONSOLE"): welche Aktion, gültig bis wann. */
    private static final Map<String, PendingConfirmation> pending = new HashMap<>();

    private record PendingConfirmation(String action, long expiresAt) {
    }

    private CommandSupport() {
    }

    /**
     * Sicherheitsabfrage für unwiderrufliche Aktionen: Beim ersten Aufruf gibt es die Warnung und
     * {@code false}; wird dieselbe Aktion innerhalb von 30 Sekunden erneut ausgeführt, {@code true}.
     *
     * @param action eindeutiger Schlüssel der Aktion inkl. Ziel, z.B. "unclaim-reset:world:plot_3"
     */
    static boolean confirmed(CommandSender sender, String action, Component warning) {
        String senderKey = sender instanceof Player player ? player.getUniqueId().toString() : sender.getName();
        long now = System.currentTimeMillis();
        pending.values().removeIf(entry -> entry.expiresAt() < now);
        PendingConfirmation open = pending.get(senderKey);
        if (open != null && open.action().equals(action) && open.expiresAt() >= now) {
            pending.remove(senderKey);
            return true;
        }
        pending.put(senderKey, new PendingConfirmation(action, now + CONFIRM_MILLIS));
        Messages.send(sender, "confirm.repeat", Messages.arg("warning", warning));
        return false;
    }

    /**
     * Führt eine Befehlsaktion aus und wandelt das Ergebnis in den Brigadier-Rückgabewert um.
     * Datenbankfehler werden abgefangen: die ausführende Person bekommt eine kurze Meldung,
     * die Details landen im Server-Log.
     */
    static int run(CommandSender sender, BooleanSupplier action) {
        try {
            return action.getAsBoolean() ? Command.SINGLE_SUCCESS : 0;
        } catch (DataException e) {
            Blockstead.getInstance().getLogger().log(Level.SEVERE, e.getMessage(), e);
            Messages.send(sender, "error.database");
            return 0;
        }
    }

    /**
     * Liefert die ausführende Person, wenn der Befehl im Spiel ausgeführt wurde.
     * Sonst (Konsole, Befehlsblock) gibt es eine Meldung und {@code null}.
     */
    static Player requirePlayer(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getSender() instanceof Player player) {
            return player;
        }
        Messages.send(context.getSource().getSender(), "error.players-only");
        return null;
    }
}
