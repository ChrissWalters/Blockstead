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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    private final String language;
    private final String fallbackLanguage;
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
    private final int cityPriority;
    private final Map<Flag<?>, Object> defaultCityFlags;
    private final Map<String, PlotRole> roles;

    private PluginConfig(String language, String fallbackLanguage, boolean displayEnabled, String chatFormat, String tabFormat, boolean tabSorting, boolean nametag,
                         List<Component> joinInfo, String announcePrefix, boolean announceTitle, boolean announceSound,
                         boolean backupsEnabled, int backupsKeep, boolean importOldBalances, long plotPrice,
                         int resetCooldownMinutes, int plotPriority, Map<Flag<?>, Object> defaultPlotFlags,
                         int cityPriority, Map<Flag<?>, Object> defaultCityFlags, Map<String, PlotRole> roles) {
        this.language = language;
        this.fallbackLanguage = fallbackLanguage;
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
        this.cityPriority = cityPriority;
        this.defaultCityFlags = Collections.unmodifiableMap(defaultCityFlags);
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

        String language = config.getString("language", "auto");
        String fallbackLanguage = config.getString("fallback-language", "en");
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
        Map<Flag<?>, Object> flags = parseFlags(config.getConfigurationSection("plots.default-flags"), "plots.default-flags", log);
        // Städte müssen unter den Grundstücken liegen, sonst überstimmen sie deren Besitzende und Flags
        int cityPriority = config.getInt("cities.priority", 0);
        if (cityPriority >= priority) {
            log.warning("config.yml: 'cities.priority' (" + cityPriority + ") must be lower than 'plots.priority' ("
                    + priority + "), using " + (priority - 1) + ".");
            cityPriority = priority - 1;
        }
        // Fehlt der Abschnitt (config.yml von vor 2.3.0), gelten die Flags der mitgelieferten Vorlage – sonst hätten
        // neue Städte auf aktualisierten Servern keinen Schutz vor PvP, TNT usw.
        ConfigurationSection citySection = config.contains("cities.default-flags", true)
                ? config.getConfigurationSection("cities.default-flags")
                : config.getDefaults() == null ? null : config.getDefaults().getConfigurationSection("cities.default-flags");
        Map<Flag<?>, Object> cityFlags = parseFlags(citySection, "cities.default-flags", log);
        Map<String, PlotRole> roles = parseRoles(config.getConfigurationSection("roles"), log);
        log.info("Configuration loaded: plot price " + plotPrice + ", plot priority " + priority + ", " + flags.size()
                + " default flags, city priority " + cityPriority + ", " + cityFlags.size() + " city flags, roles " + roles.keySet());
        return new PluginConfig(language, fallbackLanguage, displayEnabled, chatFormat, tabFormat, tabSorting, nametag,
                joinInfo, announcePrefix, announceTitle, announceSound,
                backupsEnabled, backupsKeep, importOldBalances, plotPrice,
                resetCooldownMinutes, priority, flags, cityPriority, cityFlags, roles);
    }

    /** Negative Beträge sind nicht erlaubt und werden mit Warnung durch 0 ersetzt. */
    private static long nonNegative(long value, String key, Logger log) {
        if (value < 0) {
            log.warning("config.yml: '" + key + "' must not be negative, using 0.");
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
        Set<String> taken = new HashSet<>();
        for (String key : section.getKeys(false)) {
            if (!key.matches("[a-z0-9_-]+") || PlotRole.isOwnerKey(key) || taken.contains(key)) {
                log.warning("config.yml: role '" + key + "' ignored (invalid, reserved or already used key).");
                continue;
            }
            // Ohne name: Anzeigename aus der Sprachdatei (role.<key>)
            String displayName = section.getString(key + ".name");
            Set<PlotRight> rights = EnumSet.noneOf(PlotRight.class);
            for (String right : section.getStringList(key + ".rights")) {
                try {
                    rights.add(PlotRight.valueOf(right.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    log.warning("config.yml: unknown right '" + right + "' in role '" + key + "' ignored.");
                }
            }
            Set<String> aliases = new HashSet<>();
            for (String alias : section.getStringList(key + ".aliases")) {
                String normalized = alias.toLowerCase(Locale.ROOT);
                if (PlotRole.isOwnerKey(normalized) || taken.contains(normalized) || section.contains(normalized)) {
                    log.warning("config.yml: alias '" + alias + "' of role '" + key + "' ignored (already used).");
                } else {
                    aliases.add(normalized);
                }
            }
            taken.add(key);
            taken.addAll(aliases);
            roles.put(key, new PlotRole(key, displayName, Collections.unmodifiableSet(rights), Set.copyOf(aliases)));
        }
        return roles;
    }

    /**
     * Wandelt Einträge "Flagname: Wert" über die WorldGuard-Flag-Registry in echte Flags um. Schutz-Flags werden
     * übersprungen: bei Grundstücken sperren sie Besitzende aus; bei Städten gelten sie zusätzlich auf allen
     * Grundstücken darin, die die Flag nicht selbst setzen ({@code deny} sperrt dort die Besitzenden aus,
     * {@code allow} öffnet sie für Fremde).
     *
     * @param path Pfad in der config.yml für Log-Meldungen
     */
    private static Map<Flag<?>, Object> parseFlags(ConfigurationSection section, String path, Logger log) {
        Map<Flag<?>, Object> flags = new HashMap<>();
        if (section == null) {
            return flags;
        }
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        for (String name : section.getKeys(false)) {
            Flag<?> flag = registry.get(name);
            if (flag == null) {
                log.warning("config.yml: unknown WorldGuard flag '" + name + "' ignored.");
                continue;
            }
            String input = section.getString(name);
            if (isMemberBlockingFlag(flag)) {
                log.warning("config.yml: Flag '" + name + "' in " + path + " ignored – protection flags would lock out owners "
                        + "and members (or, on cities, open plots inside to strangers). WorldGuard keeps strangers out anyway. "
                        + "Please remove it from config.yml.");
                continue;
            }
            try {
                flags.put(flag, flag.parseInput(FlagContext.create().setInput(input).build()));
            } catch (InvalidFlagFormat e) {
                log.warning("config.yml: invalid value '" + input + "' for flag '" + name + "' ignored (" + e.getMessage() + ").");
            }
        }
        return flags;
    }

    /** {@code auto} (Sprache des Minecraft-Clients) oder ein fester Sprachcode für alle (Dateiname unter {@code lang/}). */
    public String getLanguage() {
        return language;
    }

    /** Sprache für die Konsole und für Client-Sprachen ohne eigene Datei (Standard {@code en}). */
    public String getFallbackLanguage() {
        return fallbackLanguage;
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

    /** Rollen unterhalb von "owner" (Besitzende), nach Schlüssel, in der Reihenfolge der config.yml. */
    public Map<String, PlotRole> getRoles() {
        return roles;
    }

    /** Rolle zu einem Schlüssel oder Alias (Groß-/Kleinschreibung egal); {@code null}, wenn es keine gibt. */
    public PlotRole findRole(String keyOrAlias) {
        for (PlotRole role : roles.values()) {
            if (role.matches(keyOrAlias)) {
                return role;
            }
        }
        return null;
    }

    /** Flags für neue Grundstücke (nicht veränderbar; vor dem Setzen ggf. kopieren). */
    public Map<Flag<?>, Object> getDefaultPlotFlags() {
        return defaultPlotFlags;
    }

    /** WorldGuard-Priorität für neue Städte; immer niedriger als {@link #getPlotPriority()}. */
    public int getCityPriority() {
        return cityPriority;
    }

    /** Flags für neue Städte (nicht veränderbar; vor dem Setzen ggf. kopieren). */
    public Map<Flag<?>, Object> getDefaultCityFlags() {
        return defaultCityFlags;
    }
}
