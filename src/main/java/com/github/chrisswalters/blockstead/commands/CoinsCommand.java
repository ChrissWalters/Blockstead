package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.economy.EconomyService;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Befehl {@code /coins} für die Server-Währung (Name aus der config.yml).
 * <ul>
 *     <li>{@code /coins} – eigenes Guthaben ({@code bs.coins})</li>
 *     <li>{@code /coins <Name>} – Guthaben einer anderen Person ({@code bs.coins.others})</li>
 *     <li>{@code /coins give|take|set <Name> <Betrag>} – Guthaben ändern ({@code bs.coins.admin}, auch Konsole)</li>
 * </ul>
 */
public final class CoinsCommand {

    /** Obergrenze für einzelne Änderungen; verhindert Überläufe im Kontostand. */
    private static final long MAX_AMOUNT = 1_000_000_000L;

    private CoinsCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("coins")
                .requires(source -> source.getSender().hasPermission("bs.coins"))
                .executes(CoinsCommand::showOwn)
                .then(Commands.argument("name", StringArgumentType.word())
                        .requires(source -> source.getSender().hasPermission("bs.coins.others"))
                        .suggests(PlayerLookup::suggestOnlineNames)
                        .executes(CoinsCommand::showOther))
                .then(change("give"))
                .then(change("take"))
                .then(change("set"))
                .build();
    }

    /** Unterbefehl {@code give|take|set <Name> <Betrag>}. */
    private static LiteralArgumentBuilder<CommandSourceStack> change(String action) {
        return Commands.literal(action)
                .requires(source -> source.getSender().hasPermission("bs.coins.admin"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(PlayerLookup::suggestOnlineNames)
                        .then(Commands.argument("betrag", LongArgumentType.longArg(0, MAX_AMOUNT))
                                .executes(context -> change(context, action))));
    }

    private static int showOwn(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CommandSupport.run(player, () -> {
            player.sendMessage("Dein Guthaben: " + economy().format(economy().getBalance(player.getUniqueId())));
            return true;
        });
    }

    private static int showOther(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        OfflinePlayer target = findPerson(sender, StringArgumentType.getString(context, "name"));
        if (target == null) return 0;
        return CommandSupport.run(sender, () -> {
            sender.sendMessage("Guthaben von " + PlayerLookup.nameOf(target.getUniqueId()) + ": "
                    + economy().format(economy().getBalance(target.getUniqueId())));
            return true;
        });
    }

    private static int change(CommandContext<CommandSourceStack> context, String action) {
        CommandSender sender = context.getSource().getSender();
        OfflinePlayer target = findPerson(sender, StringArgumentType.getString(context, "name"));
        if (target == null) return 0;
        long amount = LongArgumentType.getLong(context, "betrag");
        String name = PlayerLookup.nameOf(target.getUniqueId());

        return CommandSupport.run(sender, () -> {
            switch (action) {
                case "give" -> economy().deposit(target.getUniqueId(), amount);
                case "take" -> {
                    if (!economy().withdraw(target.getUniqueId(), amount)) {
                        sender.sendMessage(name + " hat nur " + economy().format(economy().getBalance(target.getUniqueId())) + ".");
                        return false;
                    }
                }
                case "set" -> economy().setBalance(target.getUniqueId(), amount);
                default -> throw new IllegalArgumentException(action);
            }
            long balance = economy().getBalance(target.getUniqueId());
            sender.sendMessage("Guthaben von " + name + " ist jetzt " + economy().format(balance) + ".");
            Blockstead.getInstance().getLogger().info(sender.getName() + ": coins " + action + " " + name + " " + amount
                    + " -> " + balance);
            return true;
        });
    }

    private static OfflinePlayer findPerson(CommandSender sender, String name) {
        OfflinePlayer target = PlayerLookup.findByName(name);
        if (target == null) {
            sender.sendMessage(name + " war noch nie auf diesem Server.");
        }
        return target;
    }

    private static EconomyService economy() {
        return Blockstead.getEconomy();
    }
}
