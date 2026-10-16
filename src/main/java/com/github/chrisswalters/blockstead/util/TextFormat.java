package com.github.chrisswalters.blockstead.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Wandelt Texte aus der config.yml bzw. vom Server-Team in formatierte Nachrichten um.
 *
 * <p>Formatierung über MiniMessage-Tags, z.B. {@code <gold>}, {@code <bold>}, {@code <red>…</red>}
 * (Übersicht: https://docs.papermc.io/adventure/minimessage/format). Adressen mit {@code http://} oder
 * {@code https://} werden automatisch anklickbar und bleiben dabei als Adresse sichtbar. Klick-Aktionen, die
 * einen Befehl ausführen ({@code run_command}), werden entfernt.
 *
 * <p>Nur für vertrauenswürdige Texte (config.yml, Team-Befehle) – niemals für Chat-Nachrichten von
 * Spielenden, sonst könnten diese Formatierung und Klick-Aktionen einschleusen.
 */
public final class TextFormat {

    /** Adresse bis zum nächsten Leerzeichen; Satzzeichen am Ende gehören nicht dazu. */
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>\"]+[^\\s<>\".,;:!?)]");

    private static final TextReplacementConfig CLICKABLE_LINKS = TextReplacementConfig.builder()
            .match(URL)
            .replacement((match, builder) -> {
                String url = match.group();
                return Component.text(url)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Component.text("↗ " + url)));
            })
            .build();

    private TextFormat() {
    }

    /** MiniMessage-Text mit anklickbaren Adressen. */
    public static Component format(String miniMessage) {
        return format(miniMessage, TagResolver.empty());
    }

    /**
     * Wie {@link #format(String)}, mit Platzhaltern (z.B. aus {@code Messages}). Werte von Platzhaltern, die mit
     * {@code Placeholder.unparsed} gebaut sind, werden nicht als Formatierung gelesen.
     */
    public static Component format(String miniMessage, TagResolver placeholders) {
        return withoutRunCommand(MiniMessage.miniMessage().deserialize(miniMessage, placeholders).replaceText(CLICKABLE_LINKS));
    }

    /**
     * Entfernt Klick-Aktionen, die beim Anklicken einen Befehl <em>ausführen</em> ({@code <click:run_command:…>}) –
     * damit lassen sich Spielende nicht dazu bringen, mit einem Klick Befehle mit ihren eigenen Rechten
     * auszuführen. Links und {@code suggest_command} (Befehl nur in die Chatzeile schreiben) bleiben erlaubt.
     */
    private static Component withoutRunCommand(Component component) {
        ClickEvent<?> click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.RUN_COMMAND) {
            component = component.clickEvent(null);
        }
        List<Component> children = component.children();
        return children.isEmpty() ? component : component.children(children.stream().map(TextFormat::withoutRunCommand).toList());
    }
}
