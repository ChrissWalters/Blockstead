package com.github.chrisswalters.blockstead.lang;

import com.github.chrisswalters.blockstead.util.TextFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;

/**
 * Übersetzte Texte für Spielende und das Team.
 *
 * <p>Sprachdateien sind flache YAML-Dateien {@code <code>.yml} (z.B. {@code de.yml}) mit Schlüsseln wie
 * {@code plot.claim.success} und MiniMessage-Texten. Platzhalter sind Tags wie {@code <id>}; ihre Werte werden
 * nie als Formatierung gelesen ({@link #arg(String, Object)}).
 *
 * <p>Quellen, in dieser Reihenfolge je Text:
 * <ol>
 *     <li>{@code plugins/Blockstead/lang/<code>.yml} – eigene Dateien des Servers (überschreiben einzelne Texte
 *     oder fügen neue Sprachen hinzu)</li>
 *     <li>die in der Jar mitgelieferte Datei derselben Sprache</li>
 *     <li>die Rückfall-Sprache ({@code fallback-language}), zuletzt Englisch</li>
 * </ol>
 * Mitgelieferte Dateien werden nicht in den Plugin-Ordner kopiert – so erscheinen verbesserte Texte nach einem
 * Update, und eigene Dateien müssen nur die geänderten Schlüssel enthalten.
 *
 * <p>Welche Sprache: bei {@code language: auto} die des Minecraft-Clients ({@code de_DE} → {@code de_de.yml},
 * sonst {@code de.yml}), sonst der feste Code für alle. Die Konsole bekommt die Rückfall-Sprache.
 */
public final class Messages {

    /** In der Jar mitgelieferte Sprachen. */
    private static final String[] BUNDLED = {"en", "de"};

    private static final String ENGLISH = "en";

    private static final Map<String, Map<String, String>> bundled = new HashMap<>();
    private static final Map<String, Map<String, String>> custom = new HashMap<>();
    private static String fixedLanguage;
    private static String fallbackLanguage = ENGLISH;

    private Messages() {
    }

    /**
     * Lädt alle Sprachdateien. {@code language} ist {@code "auto"} oder ein Sprachcode; {@code fallback} ein
     * Sprachcode (Standard {@code en}).
     */
    public static void load(JavaPlugin plugin, String language, String fallback) {
        Logger log = plugin.getLogger();
        bundled.clear();
        custom.clear();
        for (String code : BUNDLED) {
            try (InputStream in = plugin.getResource("lang/" + code + ".yml")) {
                if (in != null) {
                    bundled.put(code, read(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8))));
                }
            } catch (java.io.IOException e) {
                log.warning("Could not read bundled language file " + code + ".yml: " + e.getMessage());
            }
        }

        File folder = new File(plugin.getDataFolder(), "lang");
        if (!folder.exists() && !folder.mkdirs()) {
            log.warning("Could not create folder " + folder);
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                custom.put(code, read(YamlConfiguration.loadConfiguration(file)));
            }
        }

        fallbackLanguage = normalize(fallback == null || fallback.isBlank() ? ENGLISH : fallback);
        fixedLanguage = language == null || language.isBlank() || language.equalsIgnoreCase("auto") ? null : normalize(language);

        TreeSet<String> available = new TreeSet<>(bundled.keySet());
        available.addAll(custom.keySet());
        if (fixedLanguage != null && !available.contains(fixedLanguage)) {
            log.warning("config.yml: language '" + fixedLanguage + "' has no language file – using '" + fallbackLanguage + "'.");
        }
        if (!available.contains(fallbackLanguage)) {
            log.warning("config.yml: fallback-language '" + fallbackLanguage + "' has no language file – using 'en'.");
            fallbackLanguage = ENGLISH;
        }
        log.info("Languages: " + available + ", mode " + (fixedLanguage == null ? "auto (client language)" : "fixed '" + fixedLanguage + "'")
                + ", fallback '" + fallbackLanguage + "'" + (custom.isEmpty() ? "" : ", own files: " + new TreeSet<>(custom.keySet())));
    }

    private static Map<String, String> read(YamlConfiguration yaml) {
        Map<String, String> texts = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                texts.put(key, yaml.getString(key));
            }
        }
        return texts;
    }

    private static String normalize(String code) {
        return code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    /** Sprachcode für den Empfänger. */
    public static String languageOf(CommandSender recipient) {
        if (fixedLanguage != null) {
            return fixedLanguage;
        }
        if (recipient instanceof Player player) {
            Locale locale = player.locale();
            String full = normalize(locale.toString());
            if (hasLanguage(full)) {
                return full;
            }
            String language = normalize(locale.getLanguage());
            if (hasLanguage(language)) {
                return language;
            }
        }
        return fallbackLanguage;
    }

    private static boolean hasLanguage(String code) {
        return custom.containsKey(code) || bundled.containsKey(code);
    }

    /** Rohtext (MiniMessage) zum Schlüssel in der Sprache; nie {@code null} (zur Not der Schlüssel selbst). */
    public static String raw(String language, String key) {
        for (String code : new String[]{language, fallbackLanguage, ENGLISH}) {
            String text = lookup(custom, code, key);
            if (text == null) {
                text = lookup(bundled, code, key);
            }
            if (text != null) {
                return text;
            }
        }
        return key;
    }

    /** Ob es den Schlüssel in irgendeiner Sprachdatei gibt. */
    public static boolean has(String key) {
        for (Map<String, String> texts : bundled.values()) {
            if (texts.containsKey(key)) {
                return true;
            }
        }
        for (Map<String, String> texts : custom.values()) {
            if (texts.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static String lookup(Map<String, Map<String, String>> source, String code, String key) {
        Map<String, String> texts = source.get(code);
        return texts == null ? null : texts.get(key);
    }

    /** Fertiger Text für den Empfänger. */
    public static Component get(CommandSender recipient, String key, TagResolver... args) {
        return TextFormat.format(raw(languageOf(recipient), key), TagResolver.resolver(args));
    }

    /** Fertiger Text in der Rückfall-Sprache (z.B. für Ausgaben ohne bestimmten Empfänger). */
    public static Component getFallback(String key, TagResolver... args) {
        return TextFormat.format(raw(fallbackLanguage, key), TagResolver.resolver(args));
    }

    /** Schickt den Text in der Sprache des Empfängers. */
    public static void send(CommandSender recipient, String key, TagResolver... args) {
        recipient.sendMessage(get(recipient, key, args));
    }

    /** Schickt den Text an alle, die online sind, und an die Konsole – jeweils in deren Sprache. */
    public static void broadcast(String key, TagResolver... args) {
        Bukkit.getOnlinePlayers().forEach(player -> send(player, key, args));
        send(Bukkit.getConsoleSender(), key, args);
    }

    /** Platzhalter mit einem Wert, der nie als Formatierung gelesen wird (Namen, IDs, Zahlen …). */
    public static TagResolver arg(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Platzhalter mit fertigem Text (z.B. einem anderen übersetzten Text). */
    public static TagResolver arg(String name, Component value) {
        return Placeholder.component(name, value);
    }
}
