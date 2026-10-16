package com.github.chrisswalters.blockstead.config;

import com.github.chrisswalters.blockstead.plots.LegacyFlagFix;
import com.github.chrisswalters.blockstead.roles.PlotRight;
import com.github.chrisswalters.blockstead.roles.PlotRole;
import com.github.chrisswalters.blockstead.util.TextFormat;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Eingelesene Werte aus {@code plugins/Blockstead/config.yml}.
 *
 * <p>Die Datei wird beim ersten Start aus {@code src/main/resources/config.yml} angelegt.
 * Ungültige Einträge werden mit einer Warnung im Log übersprungen, damit ein Tippfehler
 * nicht das ganze Plugin lahmlegt.
 */
public class PluginConfig {

    private final boolean displayEnabled;
    private final String chatFormat;
    private final String tabFormat;
    private final boolean tabSorting;
    private final boolean nametag;
    private final List<Component> joinInfo;
    private final String announcePrefix;
    private final boolean announceTitle;
    private final boolean announceSound;
    private final boolean backupsEnabled;
    private final int backupsKeep;
    private final boolean importOldBalances;
    private final long plotPrice;
    private final int resetCooldownMinutes;
    private final int plotPriority;
    private final Map<Flag<?>, Object> defaultPlotFlags;
    private final Map<String, PlotRole> roles;

    private PluginConfig(boolean displayEnabled, String chatFormat, String tabFormat, boolean tabSorting, boolean nametag,
                         List<Component> joinInfo, String announcePrefix, boolean announceTitle, boolean announceSound,
                         boolean backupsEnabled, int backupsKeep, boolean importOldBalances, long plotPrice,
                         int resetCooldownMinutes, int plotPriority, Map<Flag<?>, Object> defaultPlotFlags, Map<String, PlotRole> roles) {
        this.displayEnabled = displayEnabled;
        this.chatFormat = chatFormat;
        this.tabFormat = tabFormat;
        this.tabSorting = tabSorting;
        this.nametag = nametag;
        this.joinInfo = List.copyOf(joinInfo);
        this.announcePrefix = announcePrefix;
        this.announceTitle = announceTitle;
        this.announceSound = announceSound;
        this.backupsEnabled = backupsEnabled;
        this.backupsKeep = backupsKeep;
        this.importOldBalances = importOldBalances;
        this.plotPrice = plotPrice;
        this.resetCooldownMinutes = resetCooldownMinutes;
        this.plotPriority = plotPriority;
        this.defaultPlotFlags = Collections.unmodifiableMap(defaultPlotFlags);
        this.roles = Collections.unmodifiableMap(roles);
    }

    /**
     * Legt die config.yml an, falls sie fehlt, und liest sie ein.
     * Muss nach dem Aktivieren von WorldGuard aufgerufen werden (Flag-Registry).
     */
    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        Logger log = plugin.getLogger();

        boolean displayEnabled = config.getBoolean("display.enabled", true);
        String chatFormat = config.getString("display.chat-format", "{prefix}{name}{suffix}: {message}");
        String tabFormat = config.getString("display.tab-format", "{prefix}{name}{suffix}");
        boolean tabSorting = config.getBoolean("display.sort-tab-by-weight", true);
        boolean nametag = config.getBoolean("display.nametag", true);
        List<Component> joinInfo = config.getStringList("join-info").stream().map(TextFormat::format).toList();
        String announcePrefix = config.getString("announce.prefix", "<dark_red><bold>[Server]</bold></dark_red> ");
        boolean announceTitle = config.getBoolean("announce.title", true);
        boolean announceSound = config.getBoolean("announce.sound", true);
        boolean backupsEnabled = config.getBoolean("backups.enabled", true);
        int backupsKeep = Math.max(1, config.getInt("backups.keep", 10));
        // economy.currency-name / economy.start-balance aus 1.x werden ignoriert (übernimmt das Economy-Plugin)
        boolean importOldBalances = config.getBoolean("economy.import-old-balances", true);
        long plotPrice = nonNegative(config.getLong("plots.price", 100), "plots.price", log);
        int resetCooldownMinutes = Math.max(0, config.getInt("plots.reset-cooldown-minutes", 10));
        int priority = config.getInt("plots.priority", 10);
        Map<Flag<?>, Object> flags = parseFlags(config.getConfigurationSection("plots.default-flags"), log);
        Map<String, PlotRole> roles = parseRoles(config.getConfigurationSection("roles"), log);
        log.info("Konfiguration geladen: Grundstückspreis " + plotPrice + ", Grundstücks-Priorität " + priority + ", " + flags.size()
                + " Standard-Flags, Rollen " + roles.keySet());
        return new PluginConfig(displayEnabled, chatFormat, tabFormat, tabSorting, nametag,
                joinInfo, announcePrefix, announceTitle, announceSound,
                backupsEnabled, backupsKeep, importOldBalances, plotPrice,
                resetCooldownMinutes, priority, flags, roles);
    }

    /** Negative Beträge sind nicht erlaubt und werden mit Warnung durch 0 ersetzt. */
    private static long nonNegative(long value, String key, Logger log) {
        if (value < 0) {
            log.warning("config.yml: '" + key + "' darf nicht negativ sein, verwende 0.");
            return 0;
        }
        return value;
    }

    /** Liest die Rollen; ungültige Schlüssel oder Rechte werden mit Warnung übersprungen. Reihenfolge wie in der Datei. */
    private static Map<String, PlotRole> parseRoles(ConfigurationSection section, Logger log) {
        Map<String, PlotRole> roles = new LinkedHashMap<>();
        if (section == null) {
            return roles;
        }
        for (String key : section.getKeys(false)) {
            if (!key.matches("[a-z0-9_-]+") || key.equals(PlotRole.OWNER_KEY)) {
                log.warning("config.yml: Rolle '" + key + "' wird ignoriert (ungültiger oder reservierter Schlüssel).");
                continue;
            }
            String displayName = section.getString(key + ".name", key);
            Set<PlotRight> rights = EnumSet.noneOf(PlotRight.class);
            for (String right : section.getStringList(key + ".rights")) {
                try {
                    rights.add(PlotRight.valueOf(right.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warning("config.yml: unbekanntes Recht '" + right + "' bei Rolle '" + key + "' wird ignoriert.");
                }
            }
            roles.put(key, new PlotRole(key, displayName, Collections.unmodifiableSet(rights)));
        }
        return roles;
    }

    /** Wandelt Einträge "Flagname: Wert" über die WorldGuard-Flag-Registry in echte Flags um. */
    private static Map<Flag<?>, Object> parseFlags(ConfigurationSection section, Logger log) {
        Map<Flag<?>, Object> flags = new HashMap<>();
        if (section == null) {
            return flags;
        }
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        for (String name : section.getKeys(false)) {
            Flag<?> flag = registry.get(name);
            if (flag == null) {
                log.warning("config.yml: unbekannte WorldGuard-Flag '" + name + "' wird ignoriert.");
                continue;
            }
            String input = section.getString(name);
            if (isMemberBlockingFlag(flag)) {
                log.warning("config.yml: Flag '" + name + "' in plots.default-flags wird ignoriert – sie würde auch Besitzende "
                        + "und Mitglieder aussperren. Fremde sperrt WorldGuard ohnehin aus. Bitte aus der config.yml entfernen.");
                continue;
            }
            try {
                flags.put(flag, flag.parseInput(FlagContext.create().setInput(input).build()));
            } catch (InvalidFlagFormat e) {
                log.warning("config.yml: ungültiger Wert '" + input + "' für Flag '" + name + "' wird ignoriert (" + e.getMessage() + ").");
            }
        }
        return flags;
    }

    /** Ob Chat, Tab-Liste und Nametag mit LuckPerms-Präfixen gestaltet werden (nur mit LuckPerms). */
    public boolean isDisplayEnabled() {
        return displayEnabled;
    }

    /** Vorlage für Chat-Zeilen (MiniMessage) mit {prefix}, {name}, {suffix}, {message}. */
    public String getChatFormat() {
        return chatFormat;
    }

    /** Vorlage für Tab-Namen (MiniMessage) mit {prefix}, {name}, {suffix}. */
    public String getTabFormat() {
        return tabFormat;
    }

    /** Ob die Tab-Liste nach dem höchsten Gruppengewicht sortiert wird. */
    public boolean isTabSorting() {
        return tabSorting;
    }

    /** Ob Präfix/Suffix auch über dem Kopf angezeigt werden. */
    public boolean isNametag() {
        return nametag;
    }

    /** Login-Nachricht (bereits formatiert, Links anklickbar); leer = keine. */
    public List<Component> getJoinInfo() {
        return joinInfo;
    }

    /** MiniMessage-Präfix für {@code /announce}. */
    public String getAnnouncePrefix() {
        return announcePrefix;
    }

    /** Ob {@code /announce} zusätzlich auf dem Bildschirm eingeblendet wird. */
    public boolean isAnnounceTitle() {
        return announceTitle;
    }

    /** Ob {@code /announce} einen Hinweiston abspielt. */
    public boolean isAnnounceSound() {
        return announceSound;
    }

    /** Ob beim Start eine Sicherung angelegt wird. */
    public boolean isBackupsEnabled() {
        return backupsEnabled;
    }

    /** Wie viele Sicherungen erhalten bleiben (mindestens 1). */
    public int getBackupsKeep() {
        return backupsKeep;
    }

    /** Ob alte Kontostände aus Blockstead 1.x einmalig in das Economy-Plugin übertragen werden. */
    public boolean isImportOldBalances() {
        return importOldBalances;
    }

    /** Preis für {@code /plot claim} (ganze Zahl in der Währung des Economy-Plugins). */
    public long getPlotPrice() {
        return plotPrice;
    }

    /** Schutz-Flags, die als Standard-Flag auch Besitzende/Mitglieder sperren würden (siehe {@link LegacyFlagFix}). */
    private static boolean isMemberBlockingFlag(Flag<?> flag) {
        return LegacyFlagFix.PROTECTION_FLAGS.contains(flag)
                || flag == Flags.BUILD || flag == Flags.BLOCK_BREAK || flag == Flags.BLOCK_PLACE;
    }

    /** Wartezeit in Minuten zwischen zwei "unclaim reset" einer Person (Team ausgenommen); 0 = keine. */
    public int getResetCooldownMinutes() {
        return resetCooldownMinutes;
    }

    /** WorldGuard-Priorität für neue Grundstücke. */
    public int getPlotPriority() {
        return plotPriority;
    }

    /** Rollen unterhalb von "besitzende", nach Schlüssel, in der Reihenfolge der config.yml. */
    public Map<String, PlotRole> getRoles() {
        return roles;
    }

    /** Flags für neue Grundstücke (nicht veränderbar; vor dem Setzen ggf. kopieren). */
    public Map<Flag<?>, Object> getDefaultPlotFlags() {
        return defaultPlotFlags;
    }
}
