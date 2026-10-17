package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.util.Selections;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    /** Mindestabstand zwischen zwei Teleports einer Person ({@code /plot home}, {@code /city teleport}). */
    private static final long TELEPORT_COOLDOWN_MILLIS = 1_000;

    /** Zeitpunkt des letzten Teleports pro Person (nur im Speicher, abgelaufene werden entfernt). */
    private static final Map<UUID, Long> lastTeleport = new HashMap<>();

    /**
     * Wartezeit zwischen zwei Teleports von Blockstead (gemeinsam für {@code /plot home} und {@code /city teleport}):
     * Teleports laden Chunks, und ohne Landepunkt sucht {@code /city teleport} erst einen sicheren Punkt – in
     * Dauerschleife könnte das den Server ausbremsen. Wer {@code exemptPermission} hat (Team), ist ausgenommen.
     * Meldet selbst, wenn noch gewartet werden muss; sonst wird der Teleport vermerkt.
     */
    static boolean teleportAllowed(Player player, String exemptPermission) {
        if (player.hasPermission(exemptPermission)) {
            return true;
        }
        long now = System.currentTimeMillis();
        lastTeleport.values().removeIf(time -> now - time >= TELEPORT_COOLDOWN_MILLIS);
        if (lastTeleport.containsKey(player.getUniqueId())) {
            Messages.send(player, "teleport.wait");
            return false;
        }
        lastTeleport.put(player.getUniqueId(), now);
        return true;
    }

    /**
     * Hängt die optionalen Höhengrenzen {@code [bottom <Y>] [top <Y>]} (beliebige Reihenfolge) bzw. {@code selection}
     * an {@code node}; jede Stufe führt {@code command} aus. Auslesen mit {@link #heights}.
     */
    static <T extends ArgumentBuilder<CommandSourceStack, T>> T withHeights(T node, Command<CommandSourceStack> command) {
        return node.executes(command)
                .then(Commands.literal("selection").executes(command))
                .then(Commands.literal("bottom").then(Commands.argument("bottom", IntegerArgumentType.integer()).executes(command)
                        .then(Commands.literal("top").then(Commands.argument("top", IntegerArgumentType.integer()).executes(command)))))
                .then(Commands.literal("top").then(Commands.argument("top", IntegerArgumentType.integer()).executes(command)
                        .then(Commands.literal("bottom").then(Commands.argument("bottom", IntegerArgumentType.integer()).executes(command)))));
    }

    /** Die angegebenen Höhengrenzen aus {@link #withHeights}; fehlende sind {@code null}. */
    static Selections.Heights heights(CommandContext<CommandSourceStack> context) {
        boolean fromSelection = context.getNodes().stream().anyMatch(node -> node.getNode().getName().equals("selection"));
        return new Selections.Heights(optionalInt(context, "bottom"), optionalInt(context, "top"), fromSelection);
    }

    private static Integer optionalInt(CommandContext<CommandSourceStack> context, String name) {
        try {
            return IntegerArgumentType.getInteger(context, name);
        } catch (IllegalArgumentException e) {
            return null; // Argument nicht angegeben
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
