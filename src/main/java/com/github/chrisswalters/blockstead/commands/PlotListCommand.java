package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.plots.Plot;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.math.BlockVector3;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Listen über mehrere Grundstücke, Teil von {@code /plot}:
 * <ul>
 *     <li>{@code /plot plots [all|free|taken] [World|*] [Seite]} – alle Grundstücke fürs Team
 *     ({@code bs.plot.list.all}, auch aus der Konsole); ein Klick auf eine Zeile fügt {@code /plot tp <ID> <World>} ein</li>
 *     <li>{@code /plot free [World|*] [Seite]} – freie Grundstücke für alle ({@code bs.plot.free}); ein Klick
 *     fügt {@code /plot info <ID> <World>} ein (kein Teleport für Spielende)</li>
 * </ul>
 * {@code *} oder keine Welt = alle Welten. 10 Einträge pro Seite, mit anklickbarem Blättern
 * („« zurück“/„weiter »“ führen den Befehl für die Nachbarseite direkt aus).
 */
final class PlotListCommand {

    private static final int PAGE_SIZE = 10;

    /** Platzhalter für "alle Welten" im Argument {@code [World]}. */
    private static final String ALL_WORLDS = "*";

    private PlotListCommand() {
    }

    /** Filter der Team-Liste. */
    private enum Filter {
        ALL(plot -> true), FREE(Plot::isFree), TAKEN(plot -> !plot.isFree());

        final Predicate<Plot> test;

        Filter(Predicate<Plot> test) {
            this.test = test;
        }

        String key() {
            return name().toLowerCase();
        }
    }

    /** {@code plots [all|free|taken] [World|*] [Seite]} */
    static LiteralArgumentBuilder<CommandSourceStack> plots() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("plots")
                .requires(source -> source.getSender().hasPermission("bs.plot.list.all"))
                .executes(context -> showAll(context, Filter.ALL));
        for (Filter filter : Filter.values()) {
            root.then(withWorldAndPage(Commands.literal(filter.key()), context -> showAll(context, filter)));
        }
        return root;
    }

    /** {@code free [World|*] [Seite]} */
    static LiteralArgumentBuilder<CommandSourceStack> free() {
        return withWorldAndPage(Commands.literal("free")
                .requires(source -> source.getSender().hasPermission("bs.plot.free")), PlotListCommand::showFree);
    }

    private static int showAll(CommandContext<CommandSourceStack> context, Filter filter) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        World world = world(context);
        List<Plot> all = plots(world, plot -> true);
        List<Plot> selected = all.stream().filter(filter.test).toList();
        long freeCount = all.stream().filter(Plot::isFree).count();

        Page page = page(context, selected.size());
        if (page == null) {
            Messages.send(sender, "plots.page-missing", Messages.arg("page", requestedPage(context)),
                    Messages.arg("pages", Math.max(1, pageCount(selected.size()))));
            return 0;
        }
        Messages.send(sender, "plots.header", Messages.arg("count", all.size()), Messages.arg("free", freeCount),
                Messages.arg("page", page.number()), Messages.arg("pages", page.count()));
        if (selected.isEmpty()) {
            Messages.send(sender, "plots.none");
            return Command.SINGLE_SUCCESS;
        }
        for (Plot plot : page.of(selected)) {
            String tp = "/plot tp " + plot.id() + " " + plot.world().getName();
            Component line = plot.isFree()
                    ? Messages.get(sender, "plots.entry-free", Messages.arg("id", plot.id()),
                            Messages.arg("world", plot.world().getName()), Messages.arg("size", size(plot)))
                    : Messages.get(sender, "plots.entry-taken", Messages.arg("id", plot.id()),
                            Messages.arg("world", plot.world().getName()), Messages.arg("owners", owners(plot)),
                            Messages.arg("size", size(plot)));
            sender.sendMessage(clickable(sender, line, tp));
        }
        sendNavigation(sender, page, "/plot plots " + filter.key() + " " + worldName(world) + " ");
        return Command.SINGLE_SUCCESS;
    }

    private static int showFree(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSender sender = context.getSource().getSender();
        World world = world(context);
        List<Plot> free = plots(world, Plot::isFree);
        String price = Blockstead.getEconomy().format(Blockstead.getPluginConfig().getPlotPrice());

        Page page = page(context, free.size());
        if (page == null) {
            Messages.send(sender, "plots.page-missing", Messages.arg("page", requestedPage(context)),
                    Messages.arg("pages", Math.max(1, pageCount(free.size()))));
            return 0;
        }
        if (free.isEmpty()) {
            Messages.send(sender, "free.none");
            return Command.SINGLE_SUCCESS;
        }
        Messages.send(sender, "free.header", Messages.arg("count", free.size()), Messages.arg("price", price),
                Messages.arg("page", page.number()), Messages.arg("pages", page.count()));
        for (Plot plot : page.of(free)) {
            String info = "/plot info " + plot.id() + " " + plot.world().getName();
            Component line = Messages.get(sender, "free.entry", Messages.arg("id", plot.id()),
                    Messages.arg("world", plot.world().getName()), Messages.arg("size", size(plot)));
            sender.sendMessage(clickable(sender, line, info));
        }
        sendNavigation(sender, page, "/plot free " + worldName(world) + " ");
        return Command.SINGLE_SUCCESS;
    }

    // ---------------------------------------------------------------- Seiten

    /** Eine Seite der Liste: Nummer (ab 1) und Anzahl der Seiten. */
    private record Page(int number, int count) {

        <T> List<T> of(List<T> items) {
            int from = (number - 1) * PAGE_SIZE;
            return items.subList(Math.min(from, items.size()), Math.min(from + PAGE_SIZE, items.size()));
        }
    }

    /** Gewünschte Seite; {@code null}, wenn es sie nicht gibt (eine leere Liste hat Seite 1). */
    private static Page page(CommandContext<CommandSourceStack> context, int items) {
        int count = Math.max(1, pageCount(items));
        int number = requestedPage(context);
        return number > count ? null : new Page(number, count);
    }

    private static int pageCount(int items) {
        return (items + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    private static int requestedPage(CommandContext<CommandSourceStack> context) {
        return hasArgument(context, "page") ? IntegerArgumentType.getInteger(context, "page") : 1;
    }

    /** „« zurück · Seite x/y · weiter »“; die Pfeile führen den Befehl für die Nachbarseite direkt aus. */
    private static void sendNavigation(CommandSender sender, Page page, String commandWithoutPage) {
        if (page.count() <= 1) {
            return;
        }
        Component navigation = Component.empty();
        if (page.number() > 1) {
            navigation = navigation.append(Messages.get(sender, "plots.previous")
                    .clickEvent(ClickEvent.runCommand(commandWithoutPage + (page.number() - 1))))
                    .append(Component.text("  "));
        }
        navigation = navigation.append(Messages.get(sender, "plots.page",
                Messages.arg("page", page.number()), Messages.arg("pages", page.count())));
        if (page.number() < page.count()) {
            navigation = navigation.append(Component.text("  "))
                    .append(Messages.get(sender, "plots.next")
                            .clickEvent(ClickEvent.runCommand(commandWithoutPage + (page.number() + 1))));
        }
        sender.sendMessage(navigation);
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    /**
     * Hängt {@code [World|*] [Seite]} an {@code node}; jede Stufe führt {@code command} aus.
     * {@code *} ist ein eigenes Literal, weil das Welt-Argument ({@code word()}) nur Buchstaben, Ziffern und
     * {@code _-.+} annimmt; Brigadier bevorzugt passende Literale vor Argumenten.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> withWorldAndPage(
            LiteralArgumentBuilder<CommandSourceStack> node, Command<CommandSourceStack> command) {
        return node.executes(command)
                .then(PlotCommand.worldArgument().executes(command).then(page().executes(command)))
                .then(Commands.literal(ALL_WORLDS).executes(command).then(page().executes(command)));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> page() {
        return Commands.argument("page", IntegerArgumentType.integer(1));
    }

    /** Gewählte Welt; {@code null} = alle Welten (keine Angabe oder {@code *}). */
    private static World world(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return hasArgument(context, "world") ? PlotCommand.worldArgument(context) : null;
    }

    private static String worldName(World world) {
        return world == null ? ALL_WORLDS : world.getName();
    }

    private static boolean hasArgument(CommandContext<CommandSourceStack> context, String name) {
        try {
            context.getArgument(name, Object.class);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Grundstücke einer Welt bzw. aller Welten (sortiert nach Weltname und Nummer), gefiltert. */
    private static List<Plot> plots(World world, Predicate<Plot> filter) {
        List<World> worlds = world != null ? List.of(world)
                : Bukkit.getWorlds().stream().sorted(Comparator.comparing(World::getName)).toList();
        List<Plot> result = new ArrayList<>();
        for (World candidate : worlds) {
            Blockstead.getPlotService().getPlots(candidate).stream().filter(filter).forEach(result::add);
        }
        return result;
    }

    /** Grundfläche des umgebenden Rechtecks, z.B. "24×18". */
    private static String size(Plot plot) {
        BlockVector3 min = plot.region().getMinimumPoint();
        BlockVector3 max = plot.region().getMaximumPoint();
        return (max.x() - min.x() + 1) + "×" + (max.z() - min.z() + 1);
    }

    private static String owners(Plot plot) {
        return plot.ownerIds().stream().map(PlayerLookup::nameOf).sorted().collect(Collectors.joining(", "));
    }

    /** Zeile, die beim Anklicken {@code command} in die Chatzeile schreibt (nicht abschickt). */
    private static Component clickable(CommandSender sender, Component line, String command) {
        return line.clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Messages.get(sender, "help.click-hint", Messages.arg("command", command))));
    }
}
