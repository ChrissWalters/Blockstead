package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.economy.EconomyService;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.plots.CreatePlot;
import com.github.chrisswalters.blockstead.plots.HomeService;
import com.github.chrisswalters.blockstead.plots.Plot;
import com.github.chrisswalters.blockstead.plots.PlotService;
import com.github.chrisswalters.blockstead.roles.PlotRight;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * Befehl {@code /plot} (Alias {@code /p}) für Grundstücke.
 *
 * <p>Für alle (Recht {@code bs.plot} bzw. eigene Unterrechte mit Standard "alle"):
 * <ul>
 *     <li>{@code /plot}, {@code /plot help} – Hilfe; jede Zeile ist anklickbar und schreibt den Befehl in die Chatzeile</li>
 *     <li>{@code /plot info [ID] [World]} – Infos zum Grundstück</li>
 *     <li>{@code /plot claim [ID]} – freies Grundstück kaufen ({@code bs.plot.claim})</li>
 *     <li>{@code /plot unclaim [ID] [reset]} – eigenes Grundstück freigeben, mit Erstattung ({@code bs.plot.unclaim})</li>
 *     <li>{@code /plot list} – eigene Grundstücke</li>
 *     <li>{@code /plot home} – zum eigenen Home teleportieren ({@code bs.plot.home})</li>
 *     <li>{@code /plot home set} – Home an der aktuellen Position setzen; nur auf Grundstücken, die man
 *     besitzt oder auf denen man eine Rolle mit Home-Recht hat ({@code bs.plot.home})</li>
 *     <li>{@code /plot trust <Name> <Role> [ID] [World]} – Rolle auf einem eigenen Grundstück vergeben ({@code bs.plot.trust})</li>
 *     <li>{@code /plot untrust <Name> [ID] [World]} – Rolle entziehen ({@code bs.plot.trust})</li>
 * </ul>
 * Mit {@code bs.plot.trust.others} gehen trust/untrust auch auf fremden Grundstücken.
 * Für das Team:
 * <ul>
 *     <li>{@code /plot list <Name>} – Grundstücke einer anderen Person ({@code bs.plot.list.others})</li>
 *     <li>{@code /plot create} – Grundstück aus der WorldEdit-Auswahl anlegen ({@code bs.plot.create})</li>
 *     <li>{@code /plot remove <ID> [World] [reset]} – Grundstück löschen ({@code bs.plot.remove})</li>
 *     <li>{@code /plot assign <ID> <Name> [World]} – freies Grundstück kostenlos zuweisen ({@code bs.plot.assign})</li>
 *     <li>{@code /plot unassign <ID> [World] [reset]} – beliebiges Grundstück freigeben ({@code bs.plot.unassign})</li>
 *     <li>{@code /plot reset [ID] [World]} – auf die Sicherung zurücksetzen, Besitz und Rollen bleiben ({@code bs.plot.reset})</li>
 *     <li>{@code /plot snapshot <ID> [World]} – aktuellen Zustand als Sicherung zum Zurücksetzen speichern ({@code bs.plot.snapshot})</li>
 *     <li>{@code /plot tp <ID> [World]} (Alias {@code teleport}) – zu einem Grundstück teleportieren ({@code bs.plot.tp})</li>
 * </ul>
 * {@code reset} setzt den Inhalt auf die Sicherung vom Anlegen zurück (mit Sicherheitsabfrage), sonst
 * bleibt alles Gebaute stehen. Ohne {@code [ID]} gilt das Grundstück, auf dem die ausführende Person
 * steht. {@code [World]} ist im Spiel optional (Standard: eigene Welt) und aus der Konsole Pflicht.
 * Aus der Konsole gehen info, trust, untrust, remove, assign, unassign, reset, snapshot und list &lt;Name&gt;;
 * Befehle für die eigene Person (claim, unclaim, home, tp, create) nur im Spiel.
 *
 * <p>Gebaut mit dem Brigadier-Befehlssystem von Paper: Der Befehl wird als Baum beschrieben,
 * Minecraft übernimmt daraus Tab-Vervollständigung, Syntaxprüfung und Fehlermeldungen.
 * Unterbefehle ohne Recht werden gar nicht erst angezeigt ({@code requires}).
 */
public final class PlotCommand {

    /** Zeitpunkt des letzten {@code unclaim reset} pro Person (nur im Speicher, siehe {@link #resetCooldownOver}). */
    private static final Map<UUID, Long> lastReset = new HashMap<>();

    private PlotCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("plot")
                .requires(source -> source.getSender().hasPermission("bs.plot"))
                .executes(PlotCommand::help)
                .then(Commands.literal("help")
                        .executes(PlotCommand::help))
                .then(Commands.literal("info")
                        .executes(context -> info(context, null, null))
                        .then(idWithWorld((player, plot) -> true, false,
                                (context, world, reset) -> info(context, idArgument(context), world))))
                .then(Commands.literal("claim")
                        .requires(source -> source.getSender().hasPermission("bs.plot.claim"))
                        .executes(context -> claim(context, null))
                        .then(plotIdArgument((player, plot) -> plot.isFree())
                                .executes(context -> claim(context, idArgument(context)))))
                .then(Commands.literal("unclaim")
                        .requires(source -> source.getSender().hasPermission("bs.plot.unclaim"))
                        .executes(context -> unclaim(context, null, false))
                        .then(Commands.literal("reset")
                                .executes(context -> unclaim(context, null, true)))
                        .then(plotIdArgument((player, plot) -> plot.isOwner(player.getUniqueId()))
                                .executes(context -> unclaim(context, idArgument(context), false))
                                .then(Commands.literal("reset")
                                        .executes(context -> unclaim(context, idArgument(context), true)))))
                .then(Commands.literal("list")
                        .executes(PlotCommand::listOwn)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .requires(source -> source.getSender().hasPermission("bs.plot.list.others"))
                                .suggests(PlayerLookup::suggestOnlineNames)
                                .executes(PlotCommand::listOther)))
                .then(PlotListCommand.plots())
                .then(PlotListCommand.free())
                .then(Commands.literal("home")
                        .requires(source -> source.getSender().hasPermission("bs.plot.home"))
                        .executes(PlotCommand::home)
                        .then(Commands.literal("set")
                                .executes(PlotCommand::homeSet)))
                .then(Commands.literal("trust")
                        .requires(source -> source.getSender().hasPermission("bs.plot.trust"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(PlayerLookup::suggestOnlineNames)
                                .then(Commands.argument("role", StringArgumentType.word())
                                        .suggests(PlotCommand::suggestRoles)
                                        .executes(context -> trust(context, null, null))
                                        .then(idWithWorld(PlotCommand::canManage, false,
                                                (context, world, reset) -> trust(context, idArgument(context), world))))))
                .then(Commands.literal("untrust")
                        .requires(source -> source.getSender().hasPermission("bs.plot.trust"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(PlayerLookup::suggestOnlineNames)
                                .executes(context -> untrust(context, null, null))
                                .then(idWithWorld(PlotCommand::canManage, false,
                                        (context, world, reset) -> untrust(context, idArgument(context), world)))))
                .then(teleport("tp"))
                .then(teleport("teleport"))
                .then(Commands.literal("create")
                        .requires(source -> source.getSender().hasPermission("bs.plot.create"))
                        .executes(PlotCommand::create))
                .then(Commands.literal("remove")
                        .requires(source -> source.getSender().hasPermission("bs.plot.remove"))
                        .then(idWithWorld((player, plot) -> true, true, PlotCommand::remove)))
                .then(Commands.literal("assign")
                        .requires(source -> source.getSender().hasPermission("bs.plot.assign"))
                        .then(plotIdArgument((player, plot) -> plot.isFree())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(PlayerLookup::suggestOnlineNames)
                                        .executes(context -> assign(context, null))
                                        .then(worldArgument()
                                                .executes(context -> assign(context, worldArgument(context)))))))
                .then(Commands.literal("unassign")
                        .requires(source -> source.getSender().hasPermission("bs.plot.unassign"))
                        .then(idWithWorld((player, plot) -> !plot.isFree(), true, PlotCommand::unassign)))
                .then(Commands.literal("reset")
                        .requires(source -> source.getSender().hasPermission("bs.plot.reset"))
                        .executes(context -> reset(context, null, null))
                        .then(idWithWorld((player, plot) -> true, false,
                                (context, world, reset) -> reset(context, idArgument(context), world))))
                .then(Commands.literal("snapshot")
                        .requires(source -> source.getSender().hasPermission("bs.plot.snapshot"))
                        .then(idWithWorld((player, plot) -> true, false,
                                (context, world, reset) -> snapshot(context, world))))
                .build();
    }

    // ---------------------------------------------------------------- Unterbefehle

    /**
     * Zeigt die Unterbefehle an, die der Absender benutzen darf ({@code /plot} bzw. {@code /plot help}).
     * Jede Zeile ist anklickbar: Ein Klick schreibt den Befehl in die Chatzeile, ohne ihn abzuschicken –
     * so lassen sich ID, Name usw. noch ergänzen.
     */
    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Messages.send(sender, "help.header");
        helpLine(sender, "help.info", "/plot info ");
        if (sender.hasPermission("bs.plot.claim")) {
            helpLine(sender, "help.claim", "/plot claim ", Messages.arg("price", economy().format(config().getPlotPrice())));
        }
        if (sender.hasPermission("bs.plot.unclaim")) {
            helpLine(sender, "help.unclaim", "/plot unclaim ");
        }
        helpLine(sender, "help.list", "/plot list");
        if (sender.hasPermission("bs.plot.free")) {
            helpLine(sender, "help.free", "/plot free");
        }
        if (sender.hasPermission("bs.plot.home")) {
            helpLine(sender, "help.home", "/plot home");
            helpLine(sender, "help.home-set", "/plot home set");
        }
        if (sender.hasPermission("bs.plot.trust")) {
            helpLine(sender, "help.trust", "/plot trust ", Messages.arg("roles", plots().roleKeys()));
            helpLine(sender, "help.untrust", "/plot untrust ");
        }
        String[][] staffLines = {
                {"bs.plot.tp", "help.tp", "/plot tp "}, {"bs.plot.list.others", "help.list-others", "/plot list "},
                {"bs.plot.list.all", "help.plots", "/plot plots "},
                {"bs.plot.create", "help.create", "/plot create"}, {"bs.plot.remove", "help.remove", "/plot remove "},
                {"bs.plot.assign", "help.assign", "/plot assign "}, {"bs.plot.unassign", "help.unassign", "/plot unassign "},
                {"bs.plot.reset", "help.reset", "/plot reset "}, {"bs.plot.snapshot", "help.snapshot", "/plot snapshot "}};
        for (String[] line : staffLines) {
            if (sender.hasPermission(line[0])) {
                helpLine(sender, line[1], line[2]);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Eine Hilfezeile, die beim Anklicken {@code command} in die Chatzeile schreibt (nicht abschickt). */
    private static void helpLine(CommandSender sender, String key, String command, TagResolver... args) {
        Component hint = Messages.get(sender, "help.click-hint", Messages.arg("command", command.strip()));
        sender.sendMessage(Messages.get(sender, key, args)
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(hint)));
    }

    private static int info(CommandContext<CommandSourceStack> context, String plotId, World world) {
        CommandSender sender = context.getSource().getSender();
        return CommandSupport.run(sender, () -> {
            Plot plot = findPlot(sender, plotId, world);
            if (plot == null) return false;

            Messages.send(sender, "info.header", Messages.arg("id", plot.id()), Messages.arg("world", plot.world().getName()));
            if (plot.isFree()) {
                Messages.send(sender, "info.free", Messages.arg("id", plot.id()),
                        Messages.arg("price", economy().format(config().getPlotPrice())));
            } else {
                Messages.send(sender, "info.owners", Messages.arg("names", names(plot.ownerIds().stream().toList())));
                for (UUID member : plot.memberIds()) {
                    Messages.send(sender, "info.member", Messages.arg("name", PlayerLookup.nameOf(member)),
                            Messages.arg("role", plots().describeRole(plot, member, sender)));
                }
                if (sender instanceof Player player) {
                    Component ownRole = plots().describeRole(plot, player.getUniqueId(), sender);
                    if (ownRole != null) {
                        Messages.send(sender, "info.own-role", Messages.arg("role", ownRole));
                    }
                }
            }
            if (sender.hasPermission("bs.plot.snapshot")) {
                Messages.send(sender, plots().hasSnapshot(plot) ? "info.snapshot-yes" : "info.snapshot-no");
            }
            return true;
        });
    }

    private static int claim(CommandContext<CommandSourceStack> context, String plotId) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CommandSupport.run(player, () -> {
            Plot plot = findPlot(player, plotId, null);
            return plot != null && plots().claim(player, plot);
        });
    }

    private static int unclaim(CommandContext<CommandSourceStack> context, String plotId, boolean reset) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CommandSupport.run(player, () -> {
            Plot plot = findPlot(player, plotId, null);
            if (plot == null) return false;
            if (!plot.isOwner(player.getUniqueId())) {
                Messages.send(player, "plot.not-yours", Messages.arg("id", plot.id()));
                return false;
            }
            if (reset && !resetCooldownOver(player)) return false;
            if (reset && !confirmReset(player, "unclaim", plot)) return false;
            boolean released = plots().release(player, plot, reset);
            if (released && reset) {
                lastReset.put(player.getUniqueId(), System.currentTimeMillis());
            }
            return released;
        });
    }

    private static int listOwn(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CommandSupport.run(player, () -> {
            sendPlotList(player, player.getUniqueId(), Messages.get(player, "list.own-title"));
            return true;
        });
    }

    private static int listOther(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        OfflinePlayer target = findPerson(sender, StringArgumentType.getString(context, "name"));
        if (target == null) return 0;
        return CommandSupport.run(sender, () -> {
            sendPlotList(sender, target.getUniqueId(),
                    Messages.get(sender, "list.other-title", Messages.arg("name", PlayerLookup.nameOf(target.getUniqueId()))));
            return true;
        });
    }

    private static int home(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CommandSupport.run(player, () -> {
            Optional<HomeService.Home> home = homes().getHome(player.getUniqueId());
            if (home.isEmpty()) {
                Messages.send(player, "home.none");
                return false;
            }
            Location location = home.get().location().orElse(null);
            if (location == null) {
                Messages.send(player, "home.world-not-loaded", Messages.arg("world", home.get().worldName()));
                return false;
            }
            Optional<Plot> plot = plots().getPlot(location.getWorld(), home.get().plotId());
            if (plot.isEmpty() || !plots().hasRight(plot.get(), player.getUniqueId(), PlotRight.HOME)) {
                // Rechte wurden entzogen (z.B. per /rg removemember) – Home ist nicht mehr gültig
                homes().deleteHome(player.getUniqueId());
                Messages.send(player, "home.invalid", Messages.arg("id", home.get().plotId()));
                return false;
            }
            player.teleportAsync(location);
            Messages.send(player, "home.welcome", Messages.arg("id", plot.get().id()));
            return true;
        });
    }

    private static int homeSet(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CommandSupport.run(player, () -> {
            Plot plot = findPlot(player, null, null);
            if (plot == null) return false;
            if (!plots().hasRight(plot, player.getUniqueId(), PlotRight.HOME)) {
                Messages.send(player, "home.set-denied", Messages.arg("id", plot.id()));
                return false;
            }
            homes().setHome(player.getUniqueId(), plot, player.getLocation());
            Messages.send(player, "home.set", Messages.arg("id", plot.id()));
            return true;
        });
    }

    private static int trust(CommandContext<CommandSourceStack> context, String plotId, World world) {
        CommandSender sender = context.getSource().getSender();
        OfflinePlayer target = findPerson(sender, StringArgumentType.getString(context, "name"));
        if (target == null) return 0;
        String roleKey = StringArgumentType.getString(context, "role").toLowerCase();
        return CommandSupport.run(sender, () -> {
            Plot plot = findManagedPlot(sender, plotId, world);
            return plot != null && plots().trust(sender, plot, target, roleKey);
        });
    }

    private static int untrust(CommandContext<CommandSourceStack> context, String plotId, World world) {
        CommandSender sender = context.getSource().getSender();
        OfflinePlayer target = findPerson(sender, StringArgumentType.getString(context, "name"));
        if (target == null) return 0;
        return CommandSupport.run(sender, () -> {
            Plot plot = findManagedPlot(sender, plotId, world);
            return plot != null && plots().untrust(sender, plot, target);
        });
    }

    /** Nur im Spiel: die ausführende Person wird teleportiert. */
    private static int teleport(CommandContext<CommandSourceStack> context, World world) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CommandSupport.run(player, () -> {
            Plot plot = findPlot(player, idArgument(context), world);
            if (plot == null) return false;
            Optional<Location> target = plots().getTeleportLocation(plot);
            if (target.isEmpty()) {
                Messages.send(player, "tp.no-safe-spot", Messages.arg("id", plot.id()));
                return false;
            }
            player.teleportAsync(target.get());
            Messages.send(player, "tp.done", Messages.arg("id", plot.id()), Messages.arg("world", plot.world().getName()));
            return true;
        });
    }

    /** Nur im Spiel: braucht die WorldEdit-Auswahl der Person. */
    private static int create(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return CreatePlot.createPlot(player) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int remove(CommandContext<CommandSourceStack> context, World world, boolean reset) {
        CommandSender sender = context.getSource().getSender();
        return CommandSupport.run(sender, () -> {
            Plot plot = findPlot(sender, idArgument(context), world);
            if (plot == null) return false;
            if (reset && !confirmReset(sender, "remove", plot)) return false;
            return plots().remove(sender, plot, reset);
        });
    }

    private static int assign(CommandContext<CommandSourceStack> context, World world) {
        CommandSender sender = context.getSource().getSender();
        OfflinePlayer target = findPerson(sender, StringArgumentType.getString(context, "name"));
        if (target == null) return 0;
        return CommandSupport.run(sender, () -> {
            Plot plot = findPlot(sender, idArgument(context), world);
            return plot != null && plots().assign(sender, plot, target);
        });
    }

    private static int unassign(CommandContext<CommandSourceStack> context, World world, boolean reset) {
        CommandSender sender = context.getSource().getSender();
        return CommandSupport.run(sender, () -> {
            Plot plot = findPlot(sender, idArgument(context), world);
            if (plot == null) return false;
            if (reset && !confirmReset(sender, "unassign", plot)) return false;
            return plots().release(sender, plot, reset);
        });
    }

    private static int reset(CommandContext<CommandSourceStack> context, String plotId, World world) {
        CommandSender sender = context.getSource().getSender();
        return CommandSupport.run(sender, () -> {
            Plot plot = findPlot(sender, plotId, world);
            if (plot == null) return false;
            if (!confirmReset(sender, "reset", plot)) return false;
            return plots().reset(sender, plot);
        });
    }

    private static int snapshot(CommandContext<CommandSourceStack> context, World world) {
        CommandSender sender = context.getSource().getSender();
        return CommandSupport.run(sender, () -> {
            Plot plot = findPlot(sender, idArgument(context), world);
            if (plot == null) return false;
            if (plots().hasSnapshot(plot) && !CommandSupport.confirmed(sender, "snapshot:" + key(plot),
                    Messages.get(sender, "snapshot.confirm-replace", Messages.arg("id", plot.id())))) {
                return false;
            }
            return plots().saveSnapshot(sender, plot);
        });
    }

    /**
     * Wartezeit zwischen zwei {@code unclaim reset} einer Person ({@code plots.reset-cooldown-minutes}).
     * Zurücksetzen ist für den Server aufwendig; ohne Wartezeit könnte jemand mit claim/unclaim reset im
     * Wechsel den Server ausbremsen. Das Team ({@code bs.plot.reset}) ist ausgenommen.
     */
    private static boolean resetCooldownOver(Player player) {
        if (player.hasPermission("bs.plot.reset")) {
            return true;
        }
        long waitMillis = config().getResetCooldownMinutes() * 60_000L;
        long now = System.currentTimeMillis();
        // Abgelaufene Einträge entfernen (nicht beim Verlassen – sonst ließe sich die Wartezeit per Relog umgehen)
        lastReset.values().removeIf(time -> now - time >= waitMillis);
        long since = now - lastReset.getOrDefault(player.getUniqueId(), 0L);
        if (since >= waitMillis) {
            return true;
        }
        long minutesLeft = (waitMillis - since + 59_999) / 60_000;
        Messages.send(player, "reset.cooldown", Messages.arg("minutes", minutesLeft));
        return false;
    }

    /** Sicherheitsabfrage vor dem Zurücksetzen; nur nötig, wenn es überhaupt eine Sicherung gibt. */
    private static boolean confirmReset(CommandSender sender, String action, Plot plot) {
        if (!plots().hasSnapshot(plot)) {
            return true; // release/remove/reset melden dann selbst, dass es keine Sicherung gibt
        }
        return CommandSupport.confirmed(sender, action + "-reset:" + key(plot),
                Messages.get(sender, "reset.confirm", Messages.arg("id", plot.id())));
    }

    private static String key(Plot plot) {
        return plot.world().getName() + ":" + plot.id();
    }

    // ---------------------------------------------------------------- Bausteine für den Befehlsbaum

    /** Ausführung eines Unterbefehls mit optionaler Welt ({@code null} = nicht angegeben) und reset-Option. */
    @FunctionalInterface
    private interface PlotHandler {
        int handle(CommandContext<CommandSourceStack> context, World world, boolean reset) throws CommandSyntaxException;
    }

    /**
     * Baustein {@code <id> [World]} bzw. mit {@code withReset} {@code <id> [World] [reset]}.
     * Die Welt ist im Spiel optional (Standard: eigene Welt) und aus der Konsole Pflicht.
     *
     * @param filter welche Grundstücke bei der Tab-Vervollständigung vorgeschlagen werden
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> idWithWorld(
            BiPredicate<Player, Plot> filter, boolean withReset, PlotHandler handler) {
        RequiredArgumentBuilder<CommandSourceStack, String> id = plotIdArgument(filter)
                .executes(context -> handler.handle(context, null, false));
        RequiredArgumentBuilder<CommandSourceStack, String> world = worldArgument()
                .executes(context -> handler.handle(context, worldArgument(context), false));
        if (withReset) {
            id.then(Commands.literal("reset")
                    .executes(context -> handler.handle(context, null, true)));
            world.then(Commands.literal("reset")
                    .executes(context -> handler.handle(context, worldArgument(context), true)));
        }
        return id.then(world);
    }

    /** Unterbefehl {@code <name> <ID> [World]}; wird für "tp" und "teleport" gebaut. */
    private static LiteralArgumentBuilder<CommandSourceStack> teleport(String name) {
        return Commands.literal(name)
                .requires(source -> source.getSender().hasPermission("bs.plot.tp"))
                .then(idWithWorld((player, plot) -> true, false,
                        (context, world, reset) -> teleport(context, world)));
    }

    /**
     * Argument {@code <id>} mit Tab-Vorschlägen aus der Welt der ausführenden Person
     * (aus der Konsole ohne Vorschläge, da die Welt erst danach kommt).
     *
     * @param filter welche Grundstücke vorgeschlagen werden (z.B. nur freie)
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> plotIdArgument(
            BiPredicate<Player, Plot> filter) {
        return Commands.argument("id", StringArgumentType.word())
                .suggests((context, builder) -> suggestPlotIds(context, builder, filter));
    }

    /**
     * Argument {@code [World]} mit dem Weltnamen, wie ihn das Plugin überall anzeigt (z.B. "world").
     * Bewusst nicht {@code ArgumentTypes.world()}: das erwartet den Dimensionsschlüssel
     * ("minecraft:overworld") und lehnt "world" ab.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> worldArgument() {
        return Commands.argument("world", StringArgumentType.word())
                .suggests(PlotCommand::suggestWorlds);
    }

    /**
     * Welt zum Argument {@code [World]}: per Name ("world") oder Schlüssel ohne Namensraum ("overworld").
     *
     * @throws CommandSyntaxException wenn es die Welt nicht gibt (Minecraft zeigt die Meldung rot an)
     */
    static World worldArgument(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "world");
        World world = Bukkit.getWorld(name);
        if (world == null) {
            for (World candidate : Bukkit.getWorlds()) {
                if (candidate.getKey().getKey().equalsIgnoreCase(name)) {
                    world = candidate;
                }
            }
        }
        if (world == null) {
            String known = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.joining(", "));
            Component message = Messages.get(context.getSource().getSender(), "world.unknown",
                    Messages.arg("world", name), Messages.arg("worlds", known));
            throw new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(message)).create();
        }
        return world;
    }

    static CompletableFuture<Suggestions> suggestWorlds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        for (World world : Bukkit.getWorlds()) {
            if (world.getName().toLowerCase().startsWith(input)) {
                builder.suggest(world.getName());
            }
        }
        return builder.buildFuture();
    }

    private static String idArgument(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "id");
    }

    private static CompletableFuture<Suggestions> suggestPlotIds(CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder,
                                                                 BiPredicate<Player, Plot> filter) {
        if (context.getSource().getSender() instanceof Player player) {
            String input = builder.getRemainingLowerCase();
            for (Plot plot : plots().getPlots(player.getWorld())) {
                if (plot.id().startsWith(input) && filter.test(player, plot)) {
                    builder.suggest(plot.id());
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRoles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        for (String key : plots().roleKeys().split(", ")) {
            if (key.startsWith(input)) {
                builder.suggest(key);
            }
        }
        return builder.buildFuture();
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    /** Rollen vergeben dürfen Besitzende und das Team ({@code bs.plot.trust.others}, z.B. auch die Konsole). */
    private static boolean canManage(CommandSender sender, Plot plot) {
        return (sender instanceof Player player && plot.isOwner(player.getUniqueId()))
                || sender.hasPermission("bs.plot.trust.others");
    }

    /** Wie {@link #findPlot}, meldet aber zusätzlich, wenn der Absender dort keine Rollen vergeben darf. */
    private static Plot findManagedPlot(CommandSender sender, String plotId, World world) {
        Plot plot = findPlot(sender, plotId, world);
        if (plot != null && !canManage(sender, plot)) {
            Messages.send(sender, "trust.not-owner", Messages.arg("id", plot.id()));
            return null;
        }
        return plot;
    }

    /**
     * Sucht das Grundstück zu den Befehlsangaben und meldet selbst, wenn das nicht geht.
     * <ul>
     *     <li>ohne ID: das Grundstück, auf dem die Person steht (nur im Spiel)</li>
     *     <li>mit ID ohne Welt: in der Welt der Person (aus der Konsole: Welt ist Pflicht)</li>
     *     <li>mit ID und Welt: in dieser Welt</li>
     * </ul>
     */
    private static Plot findPlot(CommandSender sender, String plotId, World world) {
        Player player = sender instanceof Player p ? p : null;
        if (plotId == null && player == null) {
            Messages.send(sender, "plot.console-need-id-world");
            return null;
        }
        if (plotId != null && world == null) {
            if (player == null) {
                Messages.send(sender, "plot.console-need-world", Messages.arg("id", plotId));
                return null;
            }
            world = player.getWorld();
        }

        Optional<Plot> plot = plotId == null
                ? plots().getPlotAt(player.getLocation())
                : plots().getPlot(world, plotId);
        if (plot.isEmpty()) {
            if (plotId == null) {
                Messages.send(sender, "plot.not-on-plot");
            } else {
                Messages.send(sender, "plot.not-found", Messages.arg("id", plotId), Messages.arg("world", world.getName()));
            }
            return null;
        }
        return plot.get();
    }

    /** Person per Name; meldet selbst, wenn der Name unbekannt ist. */
    private static OfflinePlayer findPerson(CommandSender sender, String name) {
        OfflinePlayer target = PlayerLookup.findByName(name);
        if (target == null) {
            Messages.send(sender, "person.unknown", Messages.arg("name", name));
        }
        return target;
    }

    private static void sendPlotList(CommandSender sender, UUID owner, Component title) {
        List<Plot> owned = plots().getOwnedPlots(owner);
        if (owned.isEmpty()) {
            Messages.send(sender, "list.empty", Messages.arg("title", title));
            return;
        }
        Messages.send(sender, "list.header", Messages.arg("title", title), Messages.arg("count", owned.size()));
        for (Plot plot : owned) {
            Messages.send(sender, "list.entry", Messages.arg("id", plot.id()), Messages.arg("world", plot.world().getName()));
        }
    }

    private static String names(List<UUID> uuids) {
        return uuids.stream().map(PlayerLookup::nameOf).sorted().collect(Collectors.joining(", "));
    }

    private static PlotService plots() {
        return Blockstead.getPlotService();
    }

    private static HomeService homes() {
        return Blockstead.getHomes();
    }

    private static EconomyService economy() {
        return Blockstead.getEconomy();
    }

    private static PluginConfig config() {
        return Blockstead.getPluginConfig();
    }
}
