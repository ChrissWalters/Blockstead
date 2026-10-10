package com.github.chrisswalters.blockstead.display;

import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.util.Versions;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Startet die LuckPerms-Anzeige, falls möglich. LuckPerms ist für Blockstead optional.
 *
 * <p>Wichtig: Diese Klasse (und alles im Paket {@code display}) verwendet LuckPerms-Typen und darf nur
 * geladen werden, wenn das Plugin LuckPerms installiert ist – sonst bricht Java mit
 * {@code NoClassDefFoundError} ab. {@code Blockstead} prüft das vorher.
 */
public final class DisplaySetup {

    /** Mindestversion von LuckPerms für die Anzeige (plugin.yml kann keine Versionen prüfen). */
    public static final String MIN_LUCKPERMS_VERSION = "5.5.85";

    private DisplaySetup() {
    }

    /**
     * @return die laufende Anzeige oder {@code null}, wenn LuckPerms nicht nutzbar ist (mit Warnung im Log)
     */
    public static DisplayService enable(JavaPlugin plugin, PluginConfig config) {
        LuckPerms luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms-API nicht verfügbar – Präfixe in Chat und Tab-Liste sind deaktiviert.");
            return null;
        }
        String version = luckPerms.getPluginMetadata().getVersion();
        if (Versions.compare(version, MIN_LUCKPERMS_VERSION) < 0) {
            plugin.getLogger().warning("LuckPerms " + version + " ist zu alt (mindestens " + MIN_LUCKPERMS_VERSION
                    + ") – Präfixe in Chat und Tab-Liste sind deaktiviert.");
            return null;
        }
        DisplayService display = new DisplayService(plugin, luckPerms, config);
        plugin.getServer().getPluginManager().registerEvents(new DisplayListener(display), plugin);
        display.start();
        plugin.getLogger().info("LuckPerms " + version + " gefunden – Präfixe in Chat und Tab-Liste aktiv.");
        return display;
    }
}
