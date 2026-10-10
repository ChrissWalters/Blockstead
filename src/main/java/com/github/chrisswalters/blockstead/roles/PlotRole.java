package com.github.chrisswalters.blockstead.roles;

import java.util.Set;

/**
 * Eine Rolle unterhalb von "besitzende", definiert in der config.yml unter {@code roles}.
 *
 * @param key         Schlüssel für Befehle und Datenbank, z.B. "gast"
 * @param displayName Anzeigename, z.B. "Gast"
 * @param rights      Rechte der Rolle (nicht veränderbar)
 */
public record PlotRole(String key, String displayName, Set<PlotRight> rights) {

    /** Fest eingebaute höchste Rolle; entspricht den WorldGuard-Besitzenden. */
    public static final String OWNER_KEY = "besitzende";

    /** Ob die Rolle das Recht enthält. */
    public boolean has(PlotRight right) {
        return rights.contains(right);
    }
}
