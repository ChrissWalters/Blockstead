package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.economy.EconomyService;
import com.github.chrisswalters.blockstead.economy.Transaction;
import com.github.chrisswalters.blockstead.roles.PlotRight;
import com.github.chrisswalters.blockstead.roles.PlotRole;
import com.github.chrisswalters.blockstead.roles.RoleService;
import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import com.github.chrisswalters.blockstead.util.WorldGuardUtils;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verwaltung bestehender Grundstücke: finden, kaufen ({@code claim}), zuweisen ({@code assign}),
 * freigeben ({@code unclaim}/{@code unassign}) und löschen ({@code remove}).
 * Angelegt werden Grundstücke über {@link CreatePlot}.
 *
 * <p>Besitzende und Mitglieder stehen in WorldGuard. Rollen unterhalb von "besitzende" sind
 * WorldGuard-Mitglieder mit zusätzlich gespeicherter Rolle ({@link RoleService}), vergeben über
 * {@link #trust} und entzogen über {@link #untrust}. In der Tabelle {@code plot_claims} steht, wer ein
 * Grundstück für welchen Preis gekauft hat; beim Freigeben oder Löschen bekommt genau diese Person
 * den Betrag zurück.
 *
 * <p>Methoden mit {@link CommandSender}-Parameter schicken ihre Rückmeldungen direkt an diesen.
 */
public class PlotService {

    private final Database database;
    private final EconomyService economy;
    private final HomeService homes;
    private final RoleService roles;
    private final SnapshotService snapshots;
    private final PluginConfig config;
    private final Logger log;

    public PlotService(Database database, EconomyService economy, HomeService homes, RoleService roles,
                       SnapshotService snapshots, PluginConfig config, Logger log) {
        this.database = database;
        this.economy = economy;
        this.homes = homes;
        this.roles = roles;
        this.snapshots = snapshots;
        this.config = config;
        this.log = log;
    }

    // ---------------------------------------------------------------- Abfragen

    /** Grundstück mit der ID in der Welt; leer, wenn es keins gibt oder die Region kein Grundstück ist. */
    public Optional<Plot> getPlot(World world, String plotId) {
        String id = plotId.toLowerCase();
        RegionManager manager = WorldGuardUtils.getRegionManager(world);
        if (manager == null || !id.startsWith(CreatePlot.PLOT_PREFIX)) {
            return Optional.empty();
        }
        return Optional.ofNullable(manager.getRegion(id)).map(region -> new Plot(world, region));
    }

    /** Grundstück an der Position; leer, wenn dort keins ist. */
    public Optional<Plot> getPlotAt(Location location) {
        RegionManager manager = WorldGuardUtils.getRegionManager(location.getWorld());
        if (manager == null) {
            return Optional.empty();
        }
        for (String id : WorldGuardUtils.getRegionsAtLocation(location)) {
            if (id.startsWith(CreatePlot.PLOT_PREFIX)) {
                return Optional.of(new Plot(location.getWorld(), manager.getRegion(id)));
            }
        }
        return Optional.empty();
    }

    /** Alle Grundstücke einer Welt, sortiert nach Nummer. */
    public List<Plot> getPlots(World world) {
        List<Plot> plots = new ArrayList<>();
        RegionManager manager = WorldGuardUtils.getRegionManager(world);
        if (manager != null) {
            for (ProtectedRegion region : manager.getRegions().values()) {
                if (region.getId().startsWith(CreatePlot.PLOT_PREFIX)) {
                    plots.add(new Plot(world, region));
                }
            }
        }
        plots.sort(Comparator.comparingInt(PlotService::plotNumber));
        return plots;
    }

    /** Alle Grundstücke in allen Welten, die {@code uuid} besitzt. */
    public List<Plot> getOwnedPlots(UUID uuid) {
        List<Plot> owned = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            for (Plot plot : getPlots(world)) {
                if (plot.isOwner(uuid)) {
                    owned.add(plot);
                }
            }
        }
        return owned;
    }

    /**
     * Ob die Person auf dem Grundstück das Recht hat: Besitzende immer, Mitglieder je nach Rolle,
     * alle anderen nie (die sperrt WorldGuard aus).
     */
    public boolean hasRight(Plot plot, UUID uuid, PlotRight right) {
        if (plot.isOwner(uuid)) {
            return true;
        }
        return plot.region().getMembers().contains(uuid) && roles.memberHasRight(plot, uuid, right);
    }

    /** Rolle der Person als Text, z.B. "Besitzende", "Gast"; {@code null} ohne Rolle. */
    public String describeRole(Plot plot, UUID uuid) {
        if (plot.isOwner(uuid)) {
            return "Besitzende";
        }
        return plot.region().getMembers().contains(uuid) ? roles.describeMember(plot, uuid) : null;
    }

    // ---------------------------------------------------------------- Ändern

    /**
     * Kauft ein freies Grundstück zum Preis aus der config.yml ({@code plots.price}) über das Economy-Plugin.
     * Reihenfolge: Economy-Plugin erreichbar? → genug Guthaben? → abbuchen → erst dann Besitz vergeben.
     * Bei Preis 0 geht der Kauf auch ohne Economy-Plugin.
     */
    public boolean claim(Player player, Plot plot) {
        if (!plot.isFree()) {
            player.sendMessage("Grundstück " + plot.id() + " ist bereits vergeben.");
            return false;
        }
        long price = config.getPlotPrice();
        if (price > 0) {
            if (!economy.isAvailable()) {
                player.sendMessage("Grundstücke können gerade nicht gekauft werden, weil die Server-Währung nicht erreichbar ist. "
                        + "Bitte gib dem Server-Team Bescheid.");
                log.warning("/plot claim von " + player.getName() + " abgelehnt: kein Economy-Plugin über Vault erreichbar");
                return false;
            }
            if (!economy.has(player, price)) {
                player.sendMessage("Du hast nicht genug Guthaben: " + plot.id() + " kostet " + economy.format(price)
                        + ", du hast " + economy.format(economy.getBalance(player)) + ".");
                return false;
            }
            Transaction payment = economy.withdraw(player, price);
            if (!payment.success()) {
                player.sendMessage("Die Abbuchung hat nicht geklappt: " + payment.error() + ". Es wurde nichts abgebucht.");
                log.warning("Abbuchung für " + plot.id() + " von " + player.getName() + " fehlgeschlagen: " + payment.error());
                return false;
            }
            try {
                saveClaim(plot, player.getUniqueId(), price);
            } catch (DataException e) {
                // Ohne gespeicherten Kauf gäbe es später keine Erstattung – Betrag sofort zurückbuchen
                economy.deposit(player, price);
                throw e;
            }
        }
        plot.region().getOwners().addPlayer(player.getUniqueId());
        saveRegions(plot.world());

        player.sendMessage("Grundstück " + plot.id() + " gehört jetzt dir. "
                + (price > 0 ? "Bezahlt: " + economy.format(price) + ", Guthaben: " + economy.format(economy.getBalance(player)) + "."
                             : "Es hat nichts gekostet."));
        log.info(player.getName() + " hat " + plot.id() + " in " + plot.world().getName() + " für " + price + " gekauft");
        return true;
    }

    /** Weist ein freies Grundstück kostenlos zu (Team-Befehl). */
    public boolean assign(CommandSender sender, Plot plot, OfflinePlayer target) {
        if (!plot.isFree()) {
            sender.sendMessage("Grundstück " + plot.id() + " ist bereits vergeben. Erst mit /plot unassign freigeben.");
            return false;
        }
        plot.region().getOwners().addPlayer(target.getUniqueId());
        saveRegions(plot.world());

        String name = PlayerLookup.nameOf(target.getUniqueId());
        sender.sendMessage("Grundstück " + plot.id() + " wurde " + name + " zugewiesen.");
        if (target.getPlayer() != null && target.getPlayer() != sender) {
            target.getPlayer().sendMessage("Dir wurde das Grundstück " + plot.id() + " zugewiesen.");
        }
        log.info(sender.getName() + " hat " + plot.id() + " in " + plot.world().getName() + " an " + name + " vergeben");
        return true;
    }

    /**
     * Gibt ein Grundstück frei: entfernt alle Besitzenden, Mitglieder, Rollen und Homes darauf und erstattet
     * den Kaufpreis an die Person, die bezahlt hat.
     *
     * @param reset {@code true}: Inhalt auf die Sicherung zurücksetzen ({@link SnapshotService});
     *              {@code false}: Inhalt bleibt unverändert. Schlägt das Zurücksetzen fehl, wird nichts freigegeben.
     */
    public boolean release(CommandSender sender, Plot plot, boolean reset) {
        if (plot.isFree()) {
            sender.sendMessage("Grundstück " + plot.id() + " ist bereits frei.");
            return false;
        }
        if (reset && !resetToSnapshot(sender, plot)) {
            return false;
        }
        refundClaim(plot);
        homes.deleteHomesOnPlot(plot);
        roles.deleteRolesOnPlot(plot);
        plot.region().getOwners().clear();
        plot.region().getMembers().clear();
        saveRegions(plot.world());

        sender.sendMessage("Grundstück " + plot.id() + " ist jetzt wieder frei" + (reset ? " und zurückgesetzt." : "."));
        log.info(sender.getName() + " hat " + plot.id() + " in " + plot.world().getName() + " freigegeben" + (reset ? " und zurückgesetzt" : ""));
        return true;
    }

    /**
     * Vergibt eine Rolle auf dem Grundstück. {@code roleKey} ist "besitzende" oder ein Schlüssel aus der
     * config.yml. Eine bisherige Rolle der Person wird ersetzt; die letzte besitzende Person kann nicht
     * herabgestuft werden.
     */
    public boolean trust(CommandSender sender, Plot plot, OfflinePlayer target, String roleKey) {
        UUID uuid = target.getUniqueId();
        String name = PlayerLookup.nameOf(uuid);
        String displayName;

        if (roleKey.equals(PlotRole.OWNER_KEY)) {
            plot.region().getMembers().removePlayer(uuid);
            plot.region().getOwners().addPlayer(uuid);
            roles.removeRole(plot, uuid);
            displayName = "Besitzende";
        } else {
            PlotRole role = config.getRoles().get(roleKey);
            if (role == null) {
                sender.sendMessage("Unbekannte Rolle '" + roleKey + "'. Möglich: " + roleKeys());
                return false;
            }
            if (plot.isOwner(uuid)) {
                if (isLastOwner(plot, uuid)) {
                    sender.sendMessage(name + " ist die einzige besitzende Person von " + plot.id()
                            + " und kann nicht herabgestuft werden. Zum Abgeben: /plot unclaim");
                    return false;
                }
                plot.region().getOwners().removePlayer(uuid);
            }
            plot.region().getMembers().addPlayer(uuid);
            roles.setRole(plot, uuid, role.key());
            displayName = role.displayName();
            // Ohne Home-Recht ist ein Home auf diesem Grundstück nicht mehr gültig
            if (!role.has(PlotRight.HOME)) {
                homes.deleteHomeIfOnPlot(uuid, plot);
            }
        }
        saveRegions(plot.world());

        sender.sendMessage(name + " hat auf " + plot.id() + " jetzt die Rolle " + displayName + ".");
        Player online = target.getPlayer();
        if (online != null && online != sender) {
            online.sendMessage("Du hast auf Grundstück " + plot.id() + " jetzt die Rolle " + displayName + ".");
        }
        log.info(sender.getName() + ": " + name + " auf " + plot.id() + " in " + plot.world().getName() + " -> " + displayName);
        return true;
    }

    /** Entzieht der Person jede Rolle auf dem Grundstück (Besitz, Mitgliedschaft, Home darauf). */
    public boolean untrust(CommandSender sender, Plot plot, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        String name = PlayerLookup.nameOf(uuid);
        if (plot.isOwner(uuid)) {
            if (isLastOwner(plot, uuid)) {
                sender.sendMessage(name + " ist die einzige besitzende Person von " + plot.id() + ". Zum Abgeben: /plot unclaim");
                return false;
            }
            plot.region().getOwners().removePlayer(uuid);
        } else if (plot.region().getMembers().contains(uuid)) {
            plot.region().getMembers().removePlayer(uuid);
        } else {
            sender.sendMessage(name + " hat auf " + plot.id() + " keine Rolle.");
            return false;
        }
        roles.removeRole(plot, uuid);
        homes.deleteHomeIfOnPlot(uuid, plot);
        saveRegions(plot.world());

        sender.sendMessage(name + " hat auf " + plot.id() + " keine Rolle mehr.");
        Player online = target.getPlayer();
        if (online != null && online != sender) {
            online.sendMessage("Deine Rolle auf Grundstück " + plot.id() + " wurde entfernt.");
        }
        log.info(sender.getName() + ": Rolle von " + name + " auf " + plot.id() + " in " + plot.world().getName() + " entfernt");
        return true;
    }

    /** Setzt den Inhalt auf die Sicherung zurück, ohne Besitz, Rollen oder Homes zu ändern (Team-Befehl). */
    public boolean reset(CommandSender sender, Plot plot) {
        if (!resetToSnapshot(sender, plot)) {
            return false;
        }
        sender.sendMessage("Grundstück " + plot.id() + " wurde zurückgesetzt.");
        log.info(sender.getName() + " hat " + plot.id() + " in " + plot.world().getName() + " zurückgesetzt");
        return true;
    }

    /** Speichert den aktuellen Zustand als Sicherung zum Zurücksetzen (überschreibt eine vorhandene). */
    public boolean saveSnapshot(CommandSender sender, Plot plot) {
        try {
            snapshots.save(plot);
        } catch (IOException | WorldEditException e) {
            sender.sendMessage("Sicherung von " + plot.id() + " fehlgeschlagen, Details im Server-Log.");
            log.log(Level.SEVERE, "Sicherung von " + plot.id() + " fehlgeschlagen", e);
            return false;
        }
        sender.sendMessage("Aktueller Zustand von " + plot.id() + " ist jetzt die Sicherung zum Zurücksetzen.");
        log.info(sender.getName() + " hat eine Sicherung von " + plot.id() + " in " + plot.world().getName() + " angelegt");
        return true;
    }

    /** Ob es für das Grundstück eine Sicherung zum Zurücksetzen gibt. */
    public boolean hasSnapshot(Plot plot) {
        return snapshots.hasSnapshot(plot);
    }

    /** Alle vergebbaren Rollenschlüssel, z.B. "besitzende, mitbewohnende, bauhilfe, gast". */
    public String roleKeys() {
        List<String> keys = new ArrayList<>();
        keys.add(PlotRole.OWNER_KEY);
        keys.addAll(config.getRoles().keySet());
        return String.join(", ", keys);
    }

    /**
     * Löscht ein Grundstück endgültig (Region, Rollen, Homes und Sicherung); ein bezahlter Preis wird vorher erstattet.
     *
     * @param reset {@code true}: vorher den Inhalt auf die Sicherung zurücksetzen
     */
    public boolean remove(CommandSender sender, Plot plot, boolean reset) {
        RegionManager manager = WorldGuardUtils.getRegionManager(plot.world());
        if (manager == null) {
            sender.sendMessage("In dieser Welt sind keine WorldGuard-Regionen verfügbar.");
            return false;
        }
        if (reset && !resetToSnapshot(sender, plot)) {
            return false;
        }
        refundClaim(plot);
        homes.deleteHomesOnPlot(plot);
        roles.deleteRolesOnPlot(plot);
        snapshots.delete(plot);
        manager.removeRegion(plot.id());
        saveRegions(plot.world());

        sender.sendMessage("Grundstück " + plot.id() + " wurde gelöscht.");
        log.info(sender.getName() + " hat " + plot.id() + " in " + plot.world().getName() + " gelöscht");
        return true;
    }

    /** Boden, auf dem man bei {@code /plot tp} nicht landen soll (Schaden oder Festhängen). */
    private static final Set<Material> UNSAFE_GROUND = Set.of(
            Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK, Material.CACTUS,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE,
            Material.POWDER_SNOW, Material.POINTED_DRIPSTONE);

    /** Höchstens so viele Spalten werden nach einem Landeplatz abgesucht (jede kann einen Chunk laden). */
    private static final int MAX_LANDING_COLUMNS = 4096;

    /**
     * Sicherer Teleportpunkt auf dem Grundstück: spiralförmig vom {@link #landingCenter Mittelpunkt} aus der
     * erste Punkt, der im Grundstück liegt und dessen oberster Block kein gefährlicher Boden ist
     * ({@link #UNSAFE_GROUND}); gelandet wird einen Block darüber.
     *
     * @return leer, wenn kein sicherer Punkt gefunden wurde
     */
    public Optional<Location> getTeleportLocation(Plot plot) {
        ProtectedRegion region = plot.region();
        World world = plot.world();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int[] center = landingCenter(region);
        int centerX = center[0];
        int centerZ = center[1];
        // Weit genug, um vom Startpunkt aus das ganze umgebende Rechteck abzudecken
        int radius = Math.max(Math.max(centerX - min.x(), max.x() - centerX), Math.max(centerZ - min.z(), max.z() - centerZ)) + 1;

        int checked = 0;
        for (int r = 0; r <= radius; r++) {
            for (int[] column : ring(centerX, centerZ, r)) {
                if (!region.contains(column[0], min.y(), column[1])) {
                    continue;
                }
                if (++checked > MAX_LANDING_COLUMNS) {
                    return Optional.empty();
                }
                Block ground = world.getHighestBlockAt(column[0], column[1]);
                if (!UNSAFE_GROUND.contains(ground.getType()) && ground.getY() < world.getMaxHeight() - 2) {
                    return Optional.of(ground.getLocation().add(0.5, 1, 0.5));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Startpunkt der Suche – möglichst mittig im Grundstück. Bei Quadern die Mitte. Bei Polygonen in zwei
     * Schritten:
     * <ol>
     *     <li>alle Punkte im Grundstück, die gut innen liegen: Randabstand mindestens 75 % des größten
     *     Randabstands (bei einem 3 Blöcke breiten Schenkel also nur die Mittellinie, nie der Rand);</li>
     *     <li>davon der Punkt, der dem Flächenschwerpunkt am nächsten liegt (sonst wäre bei schmalen Formen,
     *     wo viele Punkte gleich weit vom Rand entfernt sind, z.B. auch das Ende eines Schenkels möglich).</li>
     * </ol>
     * Die Mitte des umgebenden Rechtecks allein läge bei einem L in der Einbuchtung, also außerhalb.
     * Gerastert wird mit höchstens 33 × 33 Punkten; für die Landung reicht diese Genauigkeit.
     */
    private static int[] landingCenter(ProtectedRegion region) {
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int[] boxCenter = {(min.x() + max.x()) / 2, (min.z() + max.z()) / 2};
        if (!(region instanceof ProtectedPolygonalRegion polygon)) {
            return boxCenter;
        }
        List<BlockVector2> points = polygon.getPoints();
        double[] centroid = centroid(points, boxCenter);

        int stepX = Math.max(1, (max.x() - min.x()) / 32);
        int stepZ = Math.max(1, (max.z() - min.z()) / 32);
        List<double[]> candidates = new ArrayList<>(); // {x, z, Randabstand}
        double maxDistance = 0;
        for (int x = min.x(); x <= max.x(); x += stepX) {
            for (int z = min.z(); z <= max.z(); z += stepZ) {
                if (region.contains(x, min.y(), z)) {
                    double distance = distanceToEdges(points, x + 0.5, z + 0.5);
                    candidates.add(new double[]{x, z, distance});
                    maxDistance = Math.max(maxDistance, distance);
                }
            }
        }

        int[] best = boxCenter;
        double bestToCentroid = Double.MAX_VALUE;
        for (double[] candidate : candidates) {
            if (candidate[2] < maxDistance * 0.75) {
                continue;
            }
            double toCentroid = Math.hypot(candidate[0] + 0.5 - centroid[0], candidate[1] + 0.5 - centroid[1]);
            if (toCentroid < bestToCentroid) {
                bestToCentroid = toCentroid;
                best = new int[]{(int) candidate[0], (int) candidate[1]};
            }
        }
        return best;
    }

    /** Flächenschwerpunkt des Polygons (Gaußsche Trapezformel, Eckpunkte als Blockmitten); bei Fläche 0 der Ersatzwert. */
    private static double[] centroid(List<BlockVector2> points, int[] fallback) {
        double area = 0, sumX = 0, sumZ = 0;
        for (int i = 0; i < points.size(); i++) {
            BlockVector2 a = points.get(i);
            BlockVector2 b = points.get((i + 1) % points.size());
            double ax = a.x() + 0.5, az = a.z() + 0.5, bx = b.x() + 0.5, bz = b.z() + 0.5;
            double cross = ax * bz - bx * az;
            area += cross;
            sumX += (ax + bx) * cross;
            sumZ += (az + bz) * cross;
        }
        if (Math.abs(area) < 1e-9) {
            return new double[]{fallback[0] + 0.5, fallback[1] + 0.5};
        }
        return new double[]{sumX / (3 * area), sumZ / (3 * area)};
    }

    /** Kleinster Abstand des Punkts zu den Kanten des Polygons (Eckpunkte als Blockmitten). */
    private static double distanceToEdges(List<BlockVector2> points, double px, double pz) {
        double nearest = Double.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            BlockVector2 a = points.get(i);
            BlockVector2 b = points.get((i + 1) % points.size());
            double ax = a.x() + 0.5, az = a.z() + 0.5, dx = b.x() - a.x(), dz = b.z() - a.z();
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / lengthSquared));
            nearest = Math.min(nearest, Math.hypot(px - (ax + t * dx), pz - (az + t * dz)));
        }
        return nearest;
    }

    /** Alle Spalten auf dem Rand des Quadrats mit Abstand {@code r} um den Mittelpunkt. */
    private static List<int[]> ring(int centerX, int centerZ, int r) {
        List<int[]> columns = new ArrayList<>();
        if (r == 0) {
            columns.add(new int[]{centerX, centerZ});
            return columns;
        }
        for (int i = -r; i <= r; i++) {
            columns.add(new int[]{centerX + i, centerZ - r});
            columns.add(new int[]{centerX + i, centerZ + r});
        }
        for (int i = -r + 1; i <= r - 1; i++) {
            columns.add(new int[]{centerX - r, centerZ + i});
            columns.add(new int[]{centerX + r, centerZ + i});
        }
        return columns;
    }

    // ---------------------------------------------------------------- intern

    /** Setzt das Grundstück auf die Sicherung zurück; meldet selbst, wenn es keine gibt oder es fehlschlägt. */
    private boolean resetToSnapshot(CommandSender sender, Plot plot) {
        if (!snapshots.hasSnapshot(plot)) {
            sender.sendMessage("Für " + plot.id() + " gibt es keine Sicherung zum Zurücksetzen (z.B. vor dieser Plugin-Version angelegt). "
                    + "Es wurde nichts geändert. Ohne 'reset' bleibt der Inhalt stehen.");
            return false;
        }
        try {
            snapshots.restore(plot);
            return true;
        } catch (IOException | WorldEditException e) {
            sender.sendMessage("Zurücksetzen von " + plot.id() + " fehlgeschlagen, es wurde sonst nichts geändert. Details im Server-Log.");
            log.log(Level.SEVERE, "Zurücksetzen von " + plot.id() + " fehlgeschlagen", e);
            return false;
        }
    }

    private static boolean isLastOwner(Plot plot, UUID uuid) {
        return plot.isOwner(uuid) && plot.region().getOwners().size() == 1;
    }

    private void saveClaim(Plot plot, UUID payer, long price) {
        try (PreparedStatement statement = database.getConnection().prepareStatement(
                "INSERT OR REPLACE INTO plot_claims (world, plot_id, payer, price, claimed_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setString(1, plot.world().getName());
            statement.setString(2, plot.id());
            statement.setString(3, payer.toString());
            statement.setLong(4, price);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataException("Kauf konnte nicht gespeichert werden", e);
        }
    }

    /**
     * Erstattet den gespeicherten Kaufpreis (falls vorhanden) an die Person, die bezahlt hat, und löscht den Eintrag.
     * Schlägt die Buchung fehl (z.B. Economy-Plugin fehlt), wird das Grundstück trotzdem freigegeben; im Log steht
     * dann ein Fehler mit allen Angaben für eine Erstattung von Hand.
     */
    private void refundClaim(Plot plot) {
        try {
            UUID payer = null;
            long price = 0;
            try (PreparedStatement select = database.getConnection().prepareStatement(
                    "SELECT payer, price FROM plot_claims WHERE world = ? AND plot_id = ?")) {
                select.setString(1, plot.world().getName());
                select.setString(2, plot.id());
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        payer = UUID.fromString(result.getString(1));
                        price = result.getLong(2);
                    }
                }
            }
            if (payer == null) {
                return;
            }
            try (PreparedStatement delete = database.getConnection().prepareStatement(
                    "DELETE FROM plot_claims WHERE world = ? AND plot_id = ?")) {
                delete.setString(1, plot.world().getName());
                delete.setString(2, plot.id());
                delete.executeUpdate();
            }
            OfflinePlayer payerPlayer = Bukkit.getOfflinePlayer(payer);
            Transaction refund = economy.deposit(payerPlayer, price);
            Player online = Bukkit.getPlayer(payer);
            if (refund.success()) {
                if (online != null) {
                    online.sendMessage("Für Grundstück " + plot.id() + " wurden dir " + economy.format(price) + " erstattet.");
                }
                return;
            }
            String name = PlayerLookup.nameOf(payer);
            log.severe("Erstattung fehlgeschlagen: " + plot.id() + " in " + plot.world().getName() + ", " + name + " (" + payer
                    + "), Betrag " + price + " – " + refund.error() + ". Bitte von Hand erstatten, z.B. /eco give " + name + " " + price);
            if (online != null) {
                online.sendMessage("Die Erstattung für Grundstück " + plot.id() + " (" + economy.format(price)
                        + ") konnte gerade nicht gebucht werden. Das Server-Team kümmert sich darum.");
            }
        } catch (SQLException e) {
            throw new DataException("Erstattung fehlgeschlagen", e);
        }
    }

    /** Speichert die WorldGuard-Regionen sofort, damit Datenbank und Regionen bei einem Absturz zusammenpassen. */
    private void saveRegions(World world) {
        RegionManager manager = WorldGuardUtils.getRegionManager(world);
        if (manager == null) {
            return;
        }
        try {
            manager.saveChanges();
        } catch (StorageException e) {
            log.severe("WorldGuard-Regionen in " + world.getName() + " konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Nummer aus "plot_12" → 12 (für die Sortierung); ungültige IDs ans Ende. */
    private static int plotNumber(Plot plot) {
        try {
            return Integer.parseInt(plot.id().substring(CreatePlot.PLOT_PREFIX.length()));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }
}
