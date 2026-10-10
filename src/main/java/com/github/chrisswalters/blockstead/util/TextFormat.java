package com.github.chrisswalters.blockstead.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Pattern;

/**
 * Wandelt Texte aus der config.yml bzw. vom Server-Team in formatierte Nachrichten um.
 *
 * <p>Formatierung über MiniMessage-Tags, z.B. {@code <gold>}, {@code <bold>}, {@code <red>…</red>}
 * (Übersicht: https://docs.papermc.io/adventure/minimessage/format). Adressen mit {@code http://} oder
 * {@code https://} werden automatisch anklickbar und bleiben dabei als Adresse sichtbar.
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
                        .hoverEvent(HoverEvent.showText(Component.text("Klicken zum Öffnen: " + url)));
            })
            .build();

    private TextFormat() {
    }

    /** MiniMessage-Text mit anklickbaren Adressen. */
    public static Component format(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage).replaceText(CLICKABLE_LINKS);
    }
}
