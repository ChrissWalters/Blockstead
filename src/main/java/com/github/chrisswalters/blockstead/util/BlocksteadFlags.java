package com.github.chrisswalters.blockstead.util;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;

import java.util.logging.Logger;

/**
 * Eigene WorldGuard-Flags von Blockstead. Daten zu Städten und Preisen stehen bewusst als Flags an den Regionen
 * (in WorldGuards {@code regions.yml}) statt in der Datenbank: WorldGuard bleibt die maßgebliche Quelle, und das
 * Datenbankformat ändert sich nicht. Ältere Blockstead-Versionen kennen die Flags nicht; WorldGuard bewahrt
 * unbekannte Flags beim Laden auf.
 *
 * <p>Registriert werden die Flags in {@code Blockstead.onLoad()}, vor dem Aktivieren von WorldGuard. Jedes Feld
 * ist {@code null}, wenn ein anderes Plugin den Namen schon mit anderem Typ belegt hat (siehe Log) – immer prüfen.
 */
public final class BlocksteadFlags {

    /** Anzeigename einer Stadt mit Groß-/Kleinschreibung (die Region-ID ist immer kleingeschrieben). */
    public static StringFlag NAME;
    /** Eigener Preis eines Grundstücks bzw. Preis einer Stadt für ihre Grundstücke. */
    public static IntegerFlag PRICE;
    /** Begrüßung beim Betreten einer Stadt (MiniMessage); leer = keine Meldung, nicht gesetzt = Standardtext. */
    public static StringFlag GREETING;
    /** Abschied beim Verlassen einer Stadt; wie {@link #GREETING}. */
    public static StringFlag FAREWELL;

    private BlocksteadFlags() {
    }

    /** Registriert alle Flags; Fehler landen im Log. */
    public static void registerAll(Logger log) {
        NAME = register(new StringFlag("blockstead-name"), StringFlag.class, log);
        PRICE = register(new IntegerFlag("blockstead-price"), IntegerFlag.class, log);
        GREETING = register(new StringFlag("blockstead-greeting"), StringFlag.class, log);
        FAREWELL = register(new StringFlag("blockstead-farewell"), StringFlag.class, log);
    }

    /**
     * Registriert die Flag. Hat ein anderes Plugin schon eine Flag mit diesem Namen und passendem Typ registriert,
     * wird diese mitbenutzt; bei anderem Typ gibt es {@code null} und einen Fehler im Log.
     */
    public static <T extends Flag<?>> T register(T flag, Class<T> type, Logger log) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(flag.getName());
            if (type.isInstance(existing)) {
                return type.cast(existing);
            }
            log.severe("WorldGuard flag '" + flag.getName() + "' is already registered by another plugin with a different type. "
                    + "The Blockstead features using it are not available.");
            return null;
        }
    }
}
