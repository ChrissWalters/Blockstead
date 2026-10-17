package com.github.chrisswalters.blockstead.cities;

import com.github.chrisswalters.blockstead.Blockstead;
import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.plots.CreatePlot;
import com.github.chrisswalters.blockstead.plots.Plot;
import com.github.chrisswalters.blockstead.util.BlocksteadFlags;
import com.github.chrisswalters.blockstead.util.Landing;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import com.github.chrisswalters.blockstead.util.Selections;
import com.github.chrisswalters.blockstead.util.WorldGuardUtils;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Verwaltung von Städten: anlegen, Fläche ändern, löschen, Baurechte, Preis, Begrüßung und Abschied.
 *
 * <p>Eine Stadt ist eine WorldGuard-Region ({@link City}) mit einer
 * niedrigeren Priorität als Grundstücke ({@code cities.priority}). Daraus folgt der Schutz: In der Stadt bauen
 * nur ihre Mitglieder ({@link #trust}); auf Grundstücken gilt wegen der höheren Priorität allein das Grundstück –
 * Mitglieder der Stadt haben dort keine Rechte, Besitzende bauen ungestört. Städte dürfen sich nicht überlappen.
 *
 * <p>Ein Grundstück gehört zu einer Stadt, wenn es mindestens einen Block mit ihr teilt; bei mehreren
 * Städten gilt die mit dem größten gemeinsamen Raum ({@link #cityOf}). Städte und Grundstücke reichen über die
 * ganze Welthöhe oder zwischen beim Anlegen angegebenen Höhen. Das wird immer frisch berechnet, nichts
 * wird gespeichert – ändert sich die Fläche einer Stadt, ändert sich auch die Zugehörigkeit.
 *
 * <p>Methoden mit {@link CommandSender}-Parameter schicken ihre Rückmeldungen direkt an diesen.
 */
public class CityService {

    /** Erlaubte Stadtnamen; daraus wird die Region-ID {@code city_<name in Kleinbuchstaben>}. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

    private final PluginConfig config;
    private final Logger log;

    public CityService(PluginConfig config, Logger log) {
        this.config = config;
        this.log = log;
    }

    // ---------------------------------------------------------------- Abfragen

    /** Alle Städte aller Welten, sortiert nach Name. */
    public List<City> getCities() {
        List<City> cities = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            cities.addAll(getCities(world));
        }
        cities.sort(Comparator.comparing(City::name, String.CASE_INSENSITIVE_ORDER));
        return cities;
    }

    /** Alle Städte einer Welt. */
    public List<City> getCities(World world) {
        List<City> cities = new ArrayList<>();
        RegionManager manager = WorldGuardUtils.getRegionManager(world);
        if (manager != null) {
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (region.getId().startsWith(City.PREFIX)) {
                    cities.add(new City(world, region));
                }
            }
        }
        return cities;
    }

    /** Stadt per Name (Groß-/Kleinschreibung egal), in allen Welten. Namen sind serverweit eindeutig. */
    public Optional<City> findCity(String name) {
        String id = City.PREFIX + name.toLowerCase();
        for (World world : Bukkit.getWorlds()) {
            RegionManager manager = WorldGuardUtils.getRegionManager(world);
            ProtectedRegion region = manager == null ? null : manager.getRegion(id);
            if (region != null) {
                return Optional.of(new City(world, region));
            }
        }
        return Optional.empty();
    }

    /** Stadt an der Position; leer, wenn dort keine ist. */
    public Optional<City> cityAt(Location location) {
        RegionManager manager = WorldGuardUtils.getRegionManager(location.getWorld());
        if (manager == null) {
            return Optional.empty();
        }
        for (String id : WorldGuardUtils.getRegionsAtLocation(location)) {
            if (id.startsWith(City.PREFIX)) {
                return Optional.of(new City(location.getWorld(), manager.getRegion(id)));
            }
        }
        return Optional.empty();
    }

    /** Alle Städte, mit denen das Grundstück mindestens einen Block teilt (WorldGuard prüft auch die Höhe). */
    public List<City> citiesTouching(Plot plot) {
        List<City> candidates = getCities(plot.world());
        List<ProtectedRegion> touching = plot.region().getIntersectingRegions(candidates.stream().map(City::region).toList());
        return candidates.stream().filter(city -> touching.contains(city.region())).toList();
    }

    /** Stadt, zu der das Grundstück gehört; bei mehreren die mit dem größten gemeinsamen Raum. */
    public Optional<City> cityOf(Plot plot) {
        List<City> touching = citiesTouching(plot);
        if (touching.size() <= 1) {
            return touching.stream().findFirst();
        }
        return touching.stream().max(Comparator.comparingLong(city -> sharedBlocks(plot.region(), city.region())));
    }

    /**
     * Ziel für {@code /city tp}: der gesetzte Landepunkt ({@link #setSpawn}), sonst ein sicherer Punkt möglichst
     * mittig in der Stadt – nach Möglichkeit nicht auf einem Grundstück.
     *
     * @return leer, wenn es keinen sicheren Punkt gibt
     */
    public Optional<Location> getTeleportLocation(City city) {
        com.sk89q.worldedit.util.Location spawn = city.region().getFlag(Flags.TELE_LOC);
        if (spawn != null) {
            return Optional.of(new Location(city.world(), spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()));
        }
        Optional<Location> outsidePlots = Landing.find(city.world(), city.region(), location -> !isOnPlot(location));
        return outsidePlots.isPresent() ? outsidePlots : Landing.find(city.world(), city.region(), location -> true);
    }

    /** Ob die Stadt einen gesetzten Landepunkt hat. */
    public boolean hasSpawn(City city) {
        return city.region().getFlag(Flags.TELE_LOC) != null;
    }

    /** Alle Grundstücke aus {@code plots} (z.B. alle der Welt), die zu der Stadt gehören. */
    public List<Plot> plotsIn(City city, List<Plot> plots) {
        return plots.stream()
                .filter(plot -> plot.world().equals(city.world()))
                .filter(plot -> cityOf(plot).map(found -> found.id().equals(city.id())).orElse(false))
                .toList();
    }

    // ---------------------------------------------------------------- Ändern

    /**
     * Legt eine Stadt aus der WorldEdit-Auswahl der Person an (Flags und Priorität aus der config.yml), optional mit
     * Höhengrenzen.
     */
    public boolean create(Player player, String name, Selections.Heights heights) {
        if (!NAME.matcher(name).matches()) {
            Messages.send(player, "city.invalid-name", Messages.arg("name", name));
            return false;
        }
        if (findCity(name).isPresent()) {
            Messages.send(player, "city.exists", Messages.arg("city", name));
            return false;
        }
        Selections.Shape shape = Selections.read(player);
        if (shape == null || !shape.checkHeights(player, heights)) {
            return false;
        }
        RegionManager manager = WorldGuardUtils.getRegionManager(shape.world());
        if (manager == null) {
            Messages.send(player, "plot.no-regions");
            return false;
        }
        ProtectedRegion region = shape.toRegion(City.PREFIX + name.toLowerCase(), heights);
        if (overlapsOtherCity(player, region, shape.world())) {
            return false;
        }
        region.setFlags(new HashMap<>(config.getDefaultCityFlags()));
        if (BlocksteadFlags.NAME != null) {
            region.setFlag(BlocksteadFlags.NAME, name);
        }
        region.setPriority(config.getCityPriority());
        manager.addRegion(region);
        saveRegions(shape.world());

        City city = new City(shape.world(), region);
        Messages.send(player, "city.created", Messages.arg("city", name), Messages.arg("plots", countPlots(city)));
        Messages.send(player, "city.created-spawn-hint", Messages.arg("command", "/city setspawn " + name));
        log.info("City " + name + " in world " + shape.world().getName() + " created by " + player.getName());
        return true;
    }

    /**
     * Ersetzt die Fläche der Stadt durch die WorldEdit-Auswahl der Person (Höhe wie bei {@link #create}); Name,
     * Baurechte, Preis, Texte, Flags und Priorität bleiben. Die Auswahl muss in der Welt der Stadt liegen. Ein
     * Landepunkt, der danach außerhalb liegt, wird entfernt.
     */
    public boolean redefine(Player player, City city, Selections.Heights heights) {
        Selections.Shape shape = Selections.read(player);
        if (shape == null || !shape.checkHeights(player, heights)) {
            return false;
        }
        if (!shape.world().equals(city.world())) {
            Messages.send(player, "city.other-world", Messages.arg("city", city.name()), Messages.arg("world", city.world().getName()));
            return false;
        }
        RegionManager manager = WorldGuardUtils.getRegionManager(city.world());
        if (manager == null) {
            Messages.send(player, "plot.no-regions");
            return false;
        }
        ProtectedRegion region = shape.toRegion(city.id(), heights);
        if (overlapsOtherCity(player, region, city.world())) {
            return false;
        }
        region.copyFrom(city.region());
        com.sk89q.worldedit.util.Location spawn = region.getFlag(Flags.TELE_LOC);
        if (spawn != null && !region.contains(spawn.toVector().toBlockPoint())) {
            region.setFlag(Flags.TELE_LOC, null);
            Messages.send(player, "city.spawn-removed-outside", Messages.arg("city", city.name()));
        }
        manager.addRegion(region); // ersetzt die Region mit derselben ID
        saveRegions(city.world());

        Messages.send(player, "city.redefined", Messages.arg("city", city.name()),
                Messages.arg("plots", countPlots(new City(city.world(), region))));
        log.info("City " + city.name() + " in world " + city.world().getName() + " redefined by " + player.getName());
        return true;
    }

    /** Löscht die Stadt. Die Grundstücke darin bleiben unverändert bestehen. */
    public boolean remove(CommandSender sender, City city) {
        RegionManager manager = WorldGuardUtils.getRegionManager(city.world());
        if (manager == null) {
            Messages.send(sender, "plot.no-regions");
            return false;
        }
        manager.removeRegion(city.id());
        saveRegions(city.world());
        Messages.send(sender, "city.removed", Messages.arg("city", city.name()));
        log.info(sender.getName() + " deleted city " + city.name() + " in " + city.world().getName());
        return true;
    }

    /**
     * Setzt den Landepunkt für {@code /city tp} auf die Position der Person (mit Blickrichtung); sie muss in der Stadt
     * stehen. Gespeichert in WorldGuards Flag {@code teleport} – {@code /rg teleport city_<name>} landet also dort
     * auch.
     */
    public boolean setSpawn(Player player, City city) {
        Location location = player.getLocation();
        if (!location.getWorld().equals(city.world()) || !city.region().contains(BukkitAdapter.asBlockVector(location))) {
            Messages.send(player, "city.spawn-outside", Messages.arg("city", city.name()));
            return false;
        }
        city.region().setFlag(Flags.TELE_LOC, BukkitAdapter.adapt(location));
        saveRegions(city.world());
        Messages.send(player, "city.spawn-set", Messages.arg("city", city.name()));
        log.info(player.getName() + " set the spawn of city " + city.name() + " to " + location.getBlockX() + " "
                + location.getBlockY() + " " + location.getBlockZ());
        return true;
    }

    /** Entfernt den Landepunkt; {@code /city tp} sucht dann selbst einen sicheren Punkt. */
    public boolean clearSpawn(CommandSender sender, City city) {
        city.region().setFlag(Flags.TELE_LOC, null);
        saveRegions(city.world());
        Messages.send(sender, "city.spawn-cleared", Messages.arg("city", city.name()));
        log.info(sender.getName() + " removed the spawn of city " + city.name());
        return true;
    }

    /** Erlaubt der Person, in der Stadt zu bauen (außerhalb von Grundstücken). */
    public boolean trust(CommandSender sender, City city, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        String name = PlayerLookup.nameOf(uuid);
        if (city.region().getMembers().contains(uuid)) {
            Messages.send(sender, "city.trust-already", Messages.arg("name", name), Messages.arg("city", city.name()));
            return false;
        }
        city.region().getMembers().addPlayer(uuid);
        saveRegions(city.world());
        Messages.send(sender, "city.trust-done", Messages.arg("name", name), Messages.arg("city", city.name()));
        Player online = target.getPlayer();
        if (online != null && online != sender) {
            Messages.send(online, "city.trust-received", Messages.arg("city", city.name()));
        }
        log.info(sender.getName() + ": " + name + " may build in city " + city.name());
        return true;
    }

    /** Entzieht der Person das Baurecht in der Stadt. */
    public boolean untrust(CommandSender sender, City city, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        String name = PlayerLookup.nameOf(uuid);
        if (!city.region().getMembers().contains(uuid)) {
            Messages.send(sender, "city.untrust-none", Messages.arg("name", name), Messages.arg("city", city.name()));
            return false;
        }
        city.region().getMembers().removePlayer(uuid);
        saveRegions(city.world());
        Messages.send(sender, "city.untrust-done", Messages.arg("name", name), Messages.arg("city", city.name()));
        log.info(sender.getName() + ": " + name + " may no longer build in city " + city.name());
        return true;
    }

    /** Setzt den Preis der Stadt für ihre Grundstücke; {@code null} = wieder der Standardpreis. */
    public boolean setPrice(CommandSender sender, City city, Integer price) {
        if (!flagAvailable(sender, BlocksteadFlags.PRICE)) {
            return false;
        }
        city.region().setFlag(BlocksteadFlags.PRICE, price);
        saveRegions(city.world());
        Messages.send(sender, price == null ? "city.price-cleared" : "city.price-set", Messages.arg("city", city.name()),
                Messages.arg("price", price == null ? "" : Blockstead.getEconomy().format(price)));
        log.info(sender.getName() + ": price of city " + city.name() + " -> " + (price == null ? "default" : price));
        return true;
    }

    /**
     * Setzt Begrüßung oder Abschied ({@link BlocksteadFlags#GREETING}/{@link BlocksteadFlags#FAREWELL}).
     *
     * @param text MiniMessage-Text; {@code ""} = keine Meldung; {@code null} = wieder der Standardtext
     */
    public boolean setText(CommandSender sender, City city, Flag<String> flag, String text) {
        if (!flagAvailable(sender, flag)) {
            return false;
        }
        city.region().setFlag(flag, text);
        saveRegions(city.world());
        String key = text == null ? "city.text-default" : text.isEmpty() ? "city.text-off" : "city.text-set";
        Messages.send(sender, key, Messages.arg("city", city.name()));
        log.info(sender.getName() + ": " + flag.getName() + " of city " + city.name() + " -> "
                + (text == null ? "default" : text.isEmpty() ? "off" : text));
        return true;
    }

    // ---------------------------------------------------------------- intern

    /** Meldet und liefert {@code true}, wenn die Fläche eine andere Stadt derselben Welt überlappt. */
    private boolean overlapsOtherCity(CommandSender sender, ProtectedRegion region, World world) {
        List<ProtectedRegion> others = getCities(world).stream().map(City::region)
                .filter(other -> !other.getId().equals(region.getId())).toList();
        List<ProtectedRegion> overlapping = region.getIntersectingRegions(others);
        if (overlapping.isEmpty()) {
            return false;
        }
        String names = overlapping.stream().map(other -> new City(world, other).name()).sorted().reduce((a, b) -> a + ", " + b).orElse("");
        Messages.send(sender, "city.overlap", Messages.arg("cities", names));
        return true;
    }

    private int countPlots(City city) {
        RegionManager manager = WorldGuardUtils.getRegionManager(city.world());
        if (manager == null) {
            return 0;
        }
        List<Plot> plots = new ArrayList<>();
        for (ProtectedRegion region : manager.getRegions().values()) {
            if (region.getId().startsWith(CreatePlot.PLOT_PREFIX)) {
                plots.add(new Plot(city.world(), region));
            }
        }
        return plotsIn(city, plots).size();
    }

    /**
     * Anzahl der Blöcke, die beide Regionen enthalten. Beide sind senkrechte Prismen (Fläche × Höhe), also
     * gemeinsame Blockspalten (nur im gemeinsamen umgebenden Rechteck gezählt) × gemeinsame Höhe.
     */
    private static long sharedBlocks(ProtectedRegion a, ProtectedRegion b) {
        BlockVector3 aMin = a.getMinimumPoint(), aMax = a.getMaximumPoint();
        BlockVector3 bMin = b.getMinimumPoint(), bMax = b.getMaximumPoint();
        int y = Math.max(aMin.y(), bMin.y());
        int height = Math.min(aMax.y(), bMax.y()) - y + 1;
        if (height <= 0) {
            return 0;
        }
        long count = 0;
        for (int x = Math.max(aMin.x(), bMin.x()); x <= Math.min(aMax.x(), bMax.x()); x++) {
            for (int z = Math.max(aMin.z(), bMin.z()); z <= Math.min(aMax.z(), bMax.z()); z++) {
                if (a.contains(x, y, z) && b.contains(x, y, z)) {
                    count++;
                }
            }
        }
        return count * height;
    }

    /** Ob an der Position ein Grundstück ist. */
    private static boolean isOnPlot(Location location) {
        return WorldGuardUtils.getRegionsAtLocation(location).stream().anyMatch(id -> id.startsWith(CreatePlot.PLOT_PREFIX));
    }

    /** Meldet, wenn die Flag nicht registriert werden konnte (anderes Plugin belegt den Namen, siehe Log). */
    private static boolean flagAvailable(CommandSender sender, Flag<?> flag) {
        if (flag == null) {
            Messages.send(sender, "error.flag-unavailable");
            return false;
        }
        return true;
    }

    /** Speichert die WorldGuard-Regionen sofort. */
    private void saveRegions(World world) {
        RegionManager manager = WorldGuardUtils.getRegionManager(world);
        if (manager == null) {
            return;
        }
        try {
            manager.saveChanges();
        } catch (StorageException e) {
            log.severe("Could not save WorldGuard regions in " + world.getName() + ": " + e.getMessage());
        }
    }
}
