package com.github.chrisswalters.blockstead.roles;

import com.github.chrisswalters.blockstead.lang.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.Locale;
import java.util.Set;

/**
 * Eine Rolle unterhalb von "owner" (Besitzende), definiert in der config.yml unter {@code roles}.
 *
 * @param key         Schlüssel für Befehle und Datenbank, z.B. "guest"
 * @param displayName fester Anzeigename aus der config.yml; {@code null} = übersetzt aus der Sprachdatei
 *                    ({@code role.<key>}), sonst der Schlüssel
 * @param rights      Rechte der Rolle (nicht veränderbar)
 * @param aliases     weitere Schlüssel, die im Befehl und in gespeicherten Daten als diese Rolle gelten
 *                    (z.B. die deutschen Schlüssel von Blockstead 1.x/2.0: "gast" für "guest")
 */
public record PlotRole(String key, String displayName, Set<PlotRight> rights, Set<String> aliases) {

    /** Fest eingebaute höchste Rolle; entspricht den WorldGuard-Besitzenden. */
    public static final String OWNER_KEY = "owner";

    /** Weitere Schlüssel für die Besitzenden (Schlüssel bis Blockstead 2.0). */
    public static final Set<String> OWNER_ALIASES = Set.of("besitzende");

    /** Ob die Rolle das Recht enthält. */
    public boolean has(PlotRight right) {
        return rights.contains(right);
    }

    /** Ob {@code input} der Schlüssel oder ein Alias dieser Rolle ist (Groß-/Kleinschreibung egal). */
    public boolean matches(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return key.equals(normalized) || aliases.contains(normalized);
    }

    /** Ob {@code input} die Besitzenden meint. */
    public static boolean isOwnerKey(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return OWNER_KEY.equals(normalized) || OWNER_ALIASES.contains(normalized);
    }

    /** Anzeigename für den Empfänger: fester Name aus der config.yml, sonst übersetzt, sonst der Schlüssel. */
    public Component displayName(CommandSender recipient) {
        if (displayName != null) {
            return Component.text(displayName);
        }
        String langKey = "role." + key;
        return Messages.has(langKey) ? Messages.get(recipient, langKey) : Component.text(key);
    }

    /** Anzeigename der Besitzenden für den Empfänger. */
    public static Component ownerName(CommandSender recipient) {
        return Messages.get(recipient, "role.owner");
    }
}
