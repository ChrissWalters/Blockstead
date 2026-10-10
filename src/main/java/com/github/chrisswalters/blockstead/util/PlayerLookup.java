package com.github.chrisswalters.blockstead.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hilfsmethoden, um Personen über ihren Namen zu finden – auch wenn sie gerade offline sind.
 */
public final class PlayerLookup {

    private PlayerLookup() {
    }

    /**
     * Sucht eine Person, die online ist oder schon einmal auf dem Server war.
     *
     * @return {@code null}, wenn der Name auf diesem Server unbekannt ist
     */
    public static OfflinePlayer findByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }

    /** Anzeigename zur UUID; die UUID selbst, wenn der Name unbekannt ist. */
    public static String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    /** Tab-Vervollständigung mit den Namen aller, die gerade online sind. */
    public static CompletableFuture<Suggestions> suggestOnlineNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(input)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }
}
