package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Befehl {@code /invincible [Name]}: schaltet die Unverwundbarkeit an bzw. aus.
 * <ul>
 *     <li>ohne Argument für sich selbst (Recht {@code bs.invincible}, nur im Spiel)</li>
 *     <li>mit Name für eine andere Person, die online ist (zusätzlich {@code bs.invincible.others};
 *     auch aus der Konsole)</li>
 * </ul>
 *
 * <p>Hinweis: Der Zustand wird in den Spieldaten gespeichert und bleibt auch nach Relog/Neustart erhalten.
 */
public final class InvincibleCommand {

    private InvincibleCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("invincible")
                .requires(source -> source.getSender().hasPermission("bs.invincible"))
                .executes(InvincibleCommand::toggleSelf)
                .then(Commands.argument("name", ArgumentTypes.player())
                        .requires(source -> source.getSender().hasPermission("bs.invincible.others"))
                        .executes(InvincibleCommand::toggleOther))
                .build();
    }

    private static int toggleSelf(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Aus der Konsole bitte einen Namen angeben: invincible <Name>");
            return 0;
        }
        toggle(sender, player);
        return Command.SINGLE_SUCCESS;
    }

    private static int toggleOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        // Das Argument kann auch ein Selektor wie @p sein; ArgumentTypes.player() lässt nur genau eine Person zu
        Player target = context.getArgument("name", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource()).getFirst();
        toggle(context.getSource().getSender(), target);
        return Command.SINGLE_SUCCESS;
    }

    private static void toggle(CommandSender sender, Player target) {
        boolean invulnerable = !target.isInvulnerable();
        target.setInvulnerable(invulnerable);
        String state = invulnerable ? "aktiviert" : "deaktiviert";

        target.sendMessage("Unsterblichkeit " + state);
        if (target != sender) {
            sender.sendMessage("Unsterblichkeit bei " + target.getName() + " " + state);
        }
        Blockstead.getInstance().getLogger().info("Unsterblichkeit bei " + target.getName() + " " + state + " durch " + sender.getName());
    }
}
