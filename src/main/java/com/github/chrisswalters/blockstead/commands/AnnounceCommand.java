package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.util.TextFormat;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Befehl {@code /announce <Nachricht>}: auffällige Server-Nachricht des Teams
 * an alle, z.B. vor einer Wartung. Recht: {@code bs.announce} (Standard: Op und Konsole).
 *
 * <p>Im Chat mit Präfix aus der config.yml ({@code announce.prefix}), optional zusätzlich als
 * Einblendung auf dem Bildschirm ({@code announce.title}) und mit Hinweiston ({@code announce.sound}).
 * Die Nachricht darf MiniMessage-Formatierung enthalten; Adressen werden anklickbar ({@link TextFormat}).
 */
public final class AnnounceCommand {

    private AnnounceCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build(String name) {
        return Commands.literal(name)
                .requires(source -> source.getSender().hasPermission("bs.announce"))
                .then(Commands.argument("nachricht", StringArgumentType.greedyString())
                        .executes(AnnounceCommand::execute))
                .build();
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        PluginConfig config = Blockstead.getPluginConfig();
        String raw = StringArgumentType.getString(context, "nachricht");
        Component message = TextFormat.format(raw);

        // An alle Online-Spielenden und die Konsole
        Bukkit.getServer().sendMessage(TextFormat.format(config.getAnnouncePrefix()).append(message));

        if (config.isAnnounceTitle()) {
            Title title = Title.title(TextFormat.format(config.getAnnouncePrefix().strip()), message);
            Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
        }
        if (config.isAnnounceSound()) {
            Sound sound = Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, Sound.Source.MASTER, 1f, 1f);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.playSound(sound, Sound.Emitter.self());
            }
        }
        Blockstead.getInstance().getLogger().info("Server message from " + context.getSource().getSender().getName() + ": " + raw);
        return Command.SINGLE_SUCCESS;
    }
}
