package com.github.chrisswalters.blockstead.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Befehl {@code /spawn}: teleportiert zum Spawnpunkt der Hauptwelt (setzbar mit {@code /setworldspawn}).
 * Recht: {@code bs.spawn} (Standard: alle) – auch als Ausweg, falls jemand irgendwo eingesperrt ist.
 */
public final class SpawnCommand {

    private SpawnCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("spawn")
                .requires(source -> source.getSender().hasPermission("bs.spawn"))
                .executes(SpawnCommand::execute)
                .build();
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        // Hauptwelt = erste geladene Welt (level-name aus server.properties)
        player.teleportAsync(Bukkit.getWorlds().getFirst().getSpawnLocation());
        player.sendMessage("Teleportiert zum Spawn.");
        return Command.SINGLE_SUCCESS;
    }
}
