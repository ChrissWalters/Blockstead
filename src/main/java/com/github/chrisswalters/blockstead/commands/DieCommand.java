package com.github.chrisswalters.blockstead.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Befehl {@code /die} (Alias {@code /suicide}): Die ausführende Person tötet sich selbst, alle sehen eine Nachricht.
 * Recht: {@code bs.die}.
 */
public final class DieCommand {

    private DieCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("die")
                .requires(source -> source.getSender().hasPermission("bs.die"))
                .executes(DieCommand::execute)
                .build();
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getSender() instanceof Player player)) {
            context.getSource().getSender().sendMessage("Dieser Befehl geht nur im Spiel.");
            return 0;
        }

        player.setHealth(0);
        player.sendMessage("Du hast dich selbst getötet");
        Bukkit.broadcast(Component.text(player.getName(), NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(" hat den einfachen Weg genommen.", NamedTextColor.WHITE)));
        return Command.SINGLE_SUCCESS;
    }
}
