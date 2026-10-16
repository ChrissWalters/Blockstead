package com.github.chrisswalters.blockstead.display;

import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.util.TextFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * LuckPerms-Präfixe/-Suffixe in Chat, Tab-Liste und über dem Kopf – die zentrale Datenquelle dafür.
 *
 * <p>Präfix und Suffix werden fertig von LuckPerms übernommen ({@code meta-formatting} in der LuckPerms-config
 * baut sie bereits zusammen, z.B. "ADM | PC | "); hier wird nichts verkettet und kein Gruppenname hartkodiert.
 * Farbcodes mit {@code &} werden per {@link LegacyComponentSerializer#legacyAmpersand()} umgewandelt – nur bei
 * Präfix/Suffix, nie bei Chat-Nachrichten.
 *
 * <p>Tab-Sortierung und Nametag laufen über ein Scoreboard-Team pro Person auf dem Haupt-Scoreboard
 * (Teamname {@code bs_<9999-Gewicht>_<Name>}; Minecraft sortiert die Tab-Liste nach Teamnamen, also
 * steht das höchste Gruppengewicht oben). Der Tab-Name selbst wird per {@link Player#playerListName}
 * gesetzt; das Team-Präfix wirkt nur über dem Kopf.
 *
 * <p>Diese Klasse verwendet LuckPerms-Typen und darf nur erzeugt werden, wenn LuckPerms installiert ist.
 */
public class DisplayService {

    private static final String TEAM_PREFIX = "bs_";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Plugin plugin;
    private final LuckPerms luckPerms;
    private final PluginConfig config;
    private EventSubscription<UserDataRecalculateEvent> subscription;

    public DisplayService(Plugin plugin, LuckPerms luckPerms, PluginConfig config) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.config = config;
    }

    /** Hört auf Rechteänderungen in LuckPerms und wendet die Anzeige auf alle an, die schon online sind. */
    public void start() {
        // Kommt asynchron – Bukkit-Aufrufe daher im Hauptthread
        subscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player player = Bukkit.getPlayer(event.getUser().getUniqueId());
                    if (player != null) {
                        apply(player);
                    }
                }));
        // Teams aus einem früheren Lauf, die nach einem Absturz mit der Welt gespeichert wurden
        removeAllTeams();
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    /** Beendet das LuckPerms-Abo und entfernt alle eigenen Teams. */
    public void stop() {
        if (subscription != null) {
            subscription.close();
        }
        removeAllTeams();
    }

    /**
     * Entfernt alle eigenen Teams ({@code bs_…}) vom Haupt-Scoreboard. Minecraft speichert dessen Teams mit
     * der Welt; nach einem Absturz blieben sonst Teams von Personen liegen, die nicht wiederkommen.
     */
    private void removeAllTeams() {
        for (Team team : Bukkit.getScoreboardManager().getMainScoreboard().getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                team.unregister();
            }
        }
    }

    /** Präfix der Person als Component; leer, wenn keiner gesetzt ist. Thread-sicher (auch im Chat-Thread nutzbar). */
    public Component prefix(Player player) {
        return legacy(luckPerms.getPlayerAdapter(Player.class).getMetaData(player).getPrefix());
    }

    /** Suffix der Person als Component; leer, wenn keiner gesetzt ist. */
    public Component suffix(Player player) {
        return legacy(luckPerms.getPlayerAdapter(Player.class).getMetaData(player).getSuffix());
    }

    /** Chat-Zeile nach {@code display.chat-format}; {@code message} wird unverändert als Text eingesetzt. */
    public Component chatLine(Player player, Component message) {
        return fill(config.getChatFormat(), player, message);
    }

    /** Tab-Name nach {@code display.tab-format}. */
    public Component tabName(Player player) {
        return fill(config.getTabFormat(), player, Component.empty());
    }

    /** Setzt Tab-Name, Tab-Sortierung und Nametag der Person (Hauptthread). */
    public void apply(Player player) {
        player.playerListName(tabName(player));
        if (!config.isTabSorting() && !config.isNametag()) {
            return;
        }
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        removeTeam(player);
        int sortKey = config.isTabSorting() ? 9999 - Math.max(0, Math.min(9999, highestGroupWeight(player))) : 9999;
        Team team = scoreboard.registerNewTeam(TEAM_PREFIX + String.format("%04d", sortKey) + "_" + player.getName());
        if (config.isNametag()) {
            team.prefix(prefix(player));
            team.suffix(suffix(player));
        }
        team.addEntry(player.getName());
    }

    /** Entfernt das Team der Person (beim Verlassen). */
    public void removeTeam(Player player) {
        Team current = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
        if (current != null && current.getName().startsWith(TEAM_PREFIX)) {
            current.unregister();
        }
    }

    /** Höchstes Gewicht aller Gruppen der Person (auch geerbte); 0 ohne Gewicht. */
    private int highestGroupWeight(Player player) {
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        int highest = 0;
        for (Group group : user.getInheritedGroups(user.getQueryOptions())) {
            highest = Math.max(highest, group.getWeight().orElse(0));
        }
        return highest;
    }

    /**
     * Setzt die Platzhalter als Components in die (MiniMessage-)Vorlage ein. So wird keiner der
     * eingesetzten Texte – insbesondere nicht {@code {message}} – als Formatierung interpretiert.
     */
    private Component fill(String template, Player player, Component message) {
        Component result = TextFormat.format(template);
        result = replace(result, "{prefix}", prefix(player));
        result = replace(result, "{name}", Component.text(player.getName()));
        result = replace(result, "{suffix}", suffix(player));
        return replace(result, "{message}", message);
    }

    private static Component replace(Component component, String placeholder, Component value) {
        return component.replaceText(TextReplacementConfig.builder().matchLiteral(placeholder).replacement(value).build());
    }

    private static Component legacy(String text) {
        return text == null || text.isEmpty() ? Component.empty() : LEGACY.deserialize(text);
    }
}
