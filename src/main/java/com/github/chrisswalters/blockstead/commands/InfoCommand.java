package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import org.bukkit.command.CommandSender;

/**
 * Befehl {@code /blockstead}: Version, Lizenz und Link zum Quellcode – für alle, ohne Recht.
 *
 * <p>Erfüllt nebenbei die AGPL-3.0 (Abschnitt 13): Wer eine veränderte Fassung auf einem Server betreibt,
 * muss den Spielenden den Quellcode anbieten. Dazu genügt es, {@code website} in der plugin.yml auf das
 * eigene Repository zu setzen – dieser Befehl zeigt den Link an.
 */
public final class InfoCommand {

    /** Quellcode des Originals – angezeigt, wenn eine veränderte Fassung keine eigene {@code website} angibt. */
    private static final String ORIGINAL_SOURCE = "https://github.com/ChrissWalters/Blockstead";

    private InfoCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("blockstead")
                .executes(InfoCommand::execute)
                .build();
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        PluginMeta meta = Blockstead.getInstance().getPluginMeta();
        Messages.send(sender, "info.plugin", Messages.arg("name", meta.getName()), Messages.arg("version", meta.getVersion()));
        String website = meta.getWebsite();
        String source = website == null || website.isBlank() ? ORIGINAL_SOURCE + " (Original)" : website;
        Messages.send(sender, "info.license", Messages.arg("source", source));
        return Command.SINGLE_SUCCESS;
    }
}
