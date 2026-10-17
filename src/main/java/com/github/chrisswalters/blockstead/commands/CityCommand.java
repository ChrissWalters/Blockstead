package com.github.chrisswalters.blockstead.commands;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.cities.City;
import com.github.chrisswalters.blockstead.cities.CityService;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.plots.Plot;
import com.github.chrisswalters.blockstead.util.BlocksteadFlags;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import com.sk89q.worldguard.protection.flags.StringFlag;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Befehl {@code /city} für Städte ({@link CityService}).
 *
 * <p>Für alle ({@code bs.city}, Standard "alle"):
 * <ul>
 *     <li>{@code /city}, {@code /city help} – Hilfe; jede Zeile ist anklickbar</li>
 *     <li>{@code /city list} – alle Städte</li>
 *     <li>{@code /city info [City]} – Infos zur Stadt (ohne Name: die Stadt, in der man steht)</li>
 *     <li>{@code /city free <City> [Seite]} – freie Grundstücke der Stadt (zusätzlich {@code bs.plot.free})</li>
 * </ul>
 * Für das Team:
 * <ul>
 *     <li>{@code /city plots <City> [all|free|taken] [Seite]} – alle Grundstücke der Stadt ({@code bs.plot.list.all})</li>
 *     <li>{@code /city create <Name>}, {@code /city redefine <City>} – Stadt aus der WorldEdit-Auswahl anlegen bzw.
 *     ihre Fläche ändern (nur im Spiel), {@code /city remove <City>} – löschen, die Grundstücke bleiben
 *     ({@code bs.city.manage})</li>
 *     <li>{@code /city price <City> <Betrag|none>} – Preis der Grundstücke in der Stadt ({@code bs.city.manage})</li>
 *     <li>{@code /city greeting|farewell <City> <Text|default|off>} – Begrüßung/Abschied ({@code bs.city.manage})</li>
 *     <li>{@code /city trust|untrust <City> <Name>} – wer in der Stadt bauen darf ({@code bs.city.trust})</li>
 * </ul>
 * Stadtnamen sind serverweit eindeutig, deshalb gibt es kein {@code [World]}; alles außer create, redefine und
 * info ohne Name geht auch aus der Konsole.
 */
public final class CityCommand {

    private CityCommand() {
    }

    /** Baut den Befehlsbaum; registriert wird er in {@code Blockstead.onEnable()}. */
    public static LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> plots = Commands.literal("plots")
                .requires(source -> source.getSender().hasPermission("bs.plot.list.all"));
        RequiredArgumentBuilder<CommandSourceStack, String> plotsCity = cityArgument()
                .executes(context -> plots(context, PlotListCommand.Filter.ALL));
        for (PlotListCommand.Filter filter : PlotListCommand.Filter.values()) {
            plotsCity.then(Commands.literal(filter.key())
                    .executes(context -> plots(context, filter))
                    .then(PlotListCommand.page()
                            .executes(context -> plots(context, filter))));
        }

        return Commands.literal("city")
                .requires(source -> source.getSender().hasPermission("bs.city"))
                .executes(CityCommand::help)
                .then(Commands.literal("help")
                        .executes(CityCommand::help))
                .then(Commands.literal("list")
                        .executes(CityCommand::list))
                .then(Commands.literal("info")
                        .executes(context -> info(context, false))
                        .then(cityArgument()
                                .executes(context -> info(context, true))))
                .then(Commands.literal("free")
                        .requires(source -> source.getSender().hasPermission("bs.plot.free"))
                        .then(cityArgument()
                                .executes(CityCommand::free)
                                .then(PlotListCommand.page()
                                        .executes(CityCommand::free))))
                .then(plots.then(plotsCity))
                .then(Commands.literal("create")
                        .requires(source -> source.getSender().hasPermission("bs.city.manage"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(CityCommand::create)))
                .then(Commands.literal("redefine")
                        .requires(source -> source.getSender().hasPermission("bs.city.manage"))
                        .then(cityArgument()
                                .executes(CityCommand::redefine)))
                .then(Commands.literal("remove")
                        .requires(source -> source.getSender().hasPermission("bs.city.manage"))
                        .then(cityArgument()
                                .executes(CityCommand::remove)))
                .then(Commands.literal("price")
                        .requires(source -> source.getSender().hasPermission("bs.city.manage"))
                        .then(cityArgument()
                                .then(Commands.literal("none")
                                        .executes(context -> price(context, null)))
                                .then(Commands.argument("price", IntegerArgumentType.integer(0))
                                        .executes(context -> price(context, IntegerArgumentType.getInteger(context, "price"))))))
                .then(text("greeting", BlocksteadFlags.GREETING))
                .then(text("farewell", BlocksteadFlags.FAREWELL))
                .then(Commands.literal("trust")
                        .requires(source -> source.getSender().hasPermission("bs.city.trust"))
                        .then(cityArgument()
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(PlayerLookup::suggestOnlineNames)
                                        .executes(context -> trust(context, true)))))
                .then(Commands.literal("untrust")
                        .requires(source -> source.getSender().hasPermission("bs.city.trust"))
                        .then(cityArgument()
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(CityCommand::suggestBuilders)
                                        .executes(context -> trust(context, false)))))
                .build();
    }

    // ---------------------------------------------------------------- Unterbefehle

    /** Hilfe mit den Unterbefehlen, die der Absender benutzen darf; jede Zeile ist anklickbar. */
    private static int help(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Messages.send(sender, "help.city-header");
        String[][] lines = {
                {"bs.city", "help.city-list", "/city list"}, {"bs.city", "help.city-info", "/city info "},
                {"bs.plot.free", "help.city-free", "/city free "}, {"bs.plot.list.all", "help.city-plots", "/city plots "},
                {"bs.city.manage", "help.city-create", "/city create "}, {"bs.city.manage", "help.city-redefine", "/city redefine "},
                {"bs.city.manage", "help.city-remove", "/city remove "}, {"bs.city.manage", "help.city-price", "/city price "},
                {"bs.city.manage", "help.city-greeting", "/city greeting "}, {"bs.city.manage", "help.city-farewell", "/city farewell "},
                {"bs.city.trust", "help.city-trust", "/city trust "}, {"bs.city.trust", "help.city-untrust", "/city untrust "}};
        for (String[] line : lines) {
            if (sender.hasPermission(line[0])) {
                sender.sendMessage(clickable(sender, Messages.get(sender, line[1]), line[2]));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        List<City> all = cities().getCities();
        if (all.isEmpty()) {
            Messages.send(sender, "city.list-empty");
            return Command.SINGLE_SUCCESS;
        }
        Messages.send(sender, "city.list-header", Messages.arg("count", all.size()));
        for (City city : all) {
            List<Plot> plots = plotsOf(city);
            Component line = Messages.get(sender, "city.list-entry", Messages.arg("city", city.name()),
                    Messages.arg("world", city.world().getName()), Messages.arg("plots", plots.size()),
                    Messages.arg("free", plots.stream().filter(Plot::isFree).count()), Messages.arg("price", priceText(city)));
            sender.sendMessage(clickable(sender, line, "/city info " + city.name()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** @param named ob ein Name angegeben ist; sonst die Stadt an der Position der Person */
    private static int info(CommandContext<CommandSourceStack> context, boolean named) {
        CommandSender sender = context.getSource().getSender();
        City city;
        if (named) {
            city = findCity(context);
        } else if (sender instanceof Player player) {
            city = cities().cityAt(player.getLocation()).orElse(null);
            if (city == null) {
                Messages.send(sender, "city.not-in-city");
                return 0;
            }
        } else {
            Messages.send(sender, "city.console-need-name");
            return 0;
        }
        if (city == null) return 0;

        List<Plot> plots = plotsOf(city);
        Messages.send(sender, "city.info-header", Messages.arg("city", city.name()), Messages.arg("world", city.world().getName()));
        Messages.send(sender, "city.info-plots", Messages.arg("plots", plots.size()),
                Messages.arg("free", plots.stream().filter(Plot::isFree).count()));
        Messages.send(sender, city.price().isPresent() ? "city.info-price-city" : "city.info-price-default",
                Messages.arg("price", priceText(city)));
        if (sender.hasPermission("bs.city.trust") || sender.hasPermission("bs.city.manage")) {
            String builders = city.builderIds().stream().map(PlayerLookup::nameOf).sorted().collect(Collectors.joining(", "));
            Messages.send(sender, builders.isEmpty() ? "city.info-builders-none" : "city.info-builders",
                    Messages.arg("names", builders));
        }
        if (sender.hasPermission("bs.city.manage")) {
            Messages.send(sender, "city.info-greeting", Messages.arg("text", describeText(sender, city, BlocksteadFlags.GREETING)));
            Messages.send(sender, "city.info-farewell", Messages.arg("text", describeText(sender, city, BlocksteadFlags.FAREWELL)));
        }
        if (sender.hasPermission("bs.plot.free") && plots.stream().anyMatch(Plot::isFree)) {
            String command = "/city free " + city.name();
            sender.sendMessage(clickable(sender, Messages.get(sender, "city.info-free-hint", Messages.arg("command", command)), command));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int free(CommandContext<CommandSourceStack> context) {
        City city = findCity(context);
        if (city == null) return 0;
        List<Plot> free = plotsOf(city).stream().filter(Plot::isFree).toList();
        return PlotListCommand.sendFree(context.getSource().getSender(), free, PlotListCommand.requestedPage(context),
                "/city free " + city.name() + " ");
    }

    private static int plots(CommandContext<CommandSourceStack> context, PlotListCommand.Filter filter) {
        City city = findCity(context);
        if (city == null) return 0;
        return PlotListCommand.sendAll(context.getSource().getSender(), plotsOf(city), filter,
                PlotListCommand.requestedPage(context), "/city plots " + city.name() + " " + filter.key() + " ");
    }

    /** Nur im Spiel: braucht die WorldEdit-Auswahl der Person. */
    private static int create(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        return cities().create(player, StringArgumentType.getString(context, "name")) ? Command.SINGLE_SUCCESS : 0;
    }

    /** Nur im Spiel: braucht die WorldEdit-Auswahl der Person. */
    private static int redefine(CommandContext<CommandSourceStack> context) {
        Player player = CommandSupport.requirePlayer(context);
        if (player == null) return 0;
        City city = findCity(context);
        return city != null && cities().redefine(player, city) ? Command.SINGLE_SUCCESS : 0;
    }

    private static int remove(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        City city = findCity(context);
        if (city == null) return 0;
        if (!CommandSupport.confirmed(sender, "city-remove:" + city.world().getName() + ":" + city.id(),
                Messages.get(sender, "city.remove-confirm", Messages.arg("city", city.name())))) {
            return 0;
        }
        return cities().remove(sender, city) ? Command.SINGLE_SUCCESS : 0;
    }

    /** @param price {@code null} = Stadtpreis entfernen (dann gilt der Standardpreis) */
    private static int price(CommandContext<CommandSourceStack> context, Integer price) {
        City city = findCity(context);
        return city != null && cities().setPrice(context.getSource().getSender(), city, price) ? Command.SINGLE_SUCCESS : 0;
    }

    /** Unterbefehl {@code <name> <City> <Text|default|off>} für Begrüßung bzw. Abschied. */
    private static LiteralArgumentBuilder<CommandSourceStack> text(String name, StringFlag flag) {
        return Commands.literal(name)
                .requires(source -> source.getSender().hasPermission("bs.city.manage"))
                .then(cityArgument()
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (String keyword : List.of("default", "off")) {
                                        if (keyword.startsWith(builder.getRemainingLowerCase())) builder.suggest(keyword);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    City city = findCity(context);
                                    if (city == null) return 0;
                                    String text = StringArgumentType.getString(context, "text").strip();
                                    String value = text.equalsIgnoreCase("default") ? null : text.equalsIgnoreCase("off") ? "" : text;
                                    return cities().setText(context.getSource().getSender(), city, flag, value) ? Command.SINGLE_SUCCESS : 0;
                                })));
    }

    /** @param allow {@code true} = trust (darf bauen), {@code false} = untrust */
    private static int trust(CommandContext<CommandSourceStack> context, boolean allow) {
        CommandSender sender = context.getSource().getSender();
        City city = findCity(context);
        if (city == null) return 0;
        String name = StringArgumentType.getString(context, "name");
        OfflinePlayer target = PlayerLookup.findByName(name);
        if (target == null) {
            Messages.send(sender, "person.unknown", Messages.arg("name", name));
            return 0;
        }
        boolean done = allow ? cities().trust(sender, city, target) : cities().untrust(sender, city, target);
        return done ? Command.SINGLE_SUCCESS : 0;
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    /** Argument {@code <City>} mit Vorschlägen aller Stadtnamen. */
    private static RequiredArgumentBuilder<CommandSourceStack, String> cityArgument() {
        return Commands.argument("city", StringArgumentType.word()).suggests(CityCommand::suggestCities);
    }

    private static CompletableFuture<Suggestions> suggestCities(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        for (City city : cities().getCities()) {
            if (city.name().toLowerCase().startsWith(input)) {
                builder.suggest(city.name());
            }
        }
        return builder.buildFuture();
    }

    /** Für untrust: nur Personen vorschlagen, die in der gewählten Stadt bauen dürfen. */
    private static CompletableFuture<Suggestions> suggestBuilders(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        String input = builder.getRemainingLowerCase();
        cities().findCity(StringArgumentType.getString(context, "city")).ifPresent(city -> {
            for (UUID uuid : city.builderIds()) {
                String name = PlayerLookup.nameOf(uuid);
                if (name.toLowerCase().startsWith(input)) {
                    builder.suggest(name);
                }
            }
        });
        return builder.buildFuture();
    }

    /** Stadt zum Argument {@code <City>}; meldet selbst, wenn es sie nicht gibt. */
    private static City findCity(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "city");
        City city = cities().findCity(name).orElse(null);
        if (city == null) {
            String known = cities().getCities().stream().map(City::name).collect(Collectors.joining(", "));
            Messages.send(context.getSource().getSender(), known.isEmpty() ? "city.unknown-none" : "city.unknown",
                    Messages.arg("city", name), Messages.arg("cities", known));
        }
        return city;
    }

    /** Grundstücke, die zur Stadt gehören (sortiert nach Nummer). */
    private static List<Plot> plotsOf(City city) {
        return cities().plotsIn(city, Blockstead.getPlotService().getPlots(city.world()));
    }

    /** Preis der Grundstücke in der Stadt: Stadtpreis oder Standardpreis (eigene Grundstückspreise gehen jeweils vor). */
    private static String priceText(City city) {
        return Blockstead.getEconomy().format(city.price().orElse(Blockstead.getPluginConfig().getPlotPrice()));
    }

    /** Begrüßung/Abschied für die Anzeige: eigener Text, "Standard" oder "aus". */
    private static Component describeText(CommandSender sender, City city, StringFlag flag) {
        String text = flag == null ? null : city.region().getFlag(flag);
        if (text == null) return Messages.get(sender, "city.text-is-default");
        if (text.isEmpty()) return Messages.get(sender, "city.text-is-off");
        return Component.text(text);
    }

    /** Zeile, die beim Anklicken {@code command} in die Chatzeile schreibt (nicht abschickt). */
    private static Component clickable(CommandSender sender, Component line, String command) {
        return line.clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Messages.get(sender, "help.click-hint", Messages.arg("command", command.strip()))));
    }

    private static CityService cities() {
        return Blockstead.getCities();
    }
}
