package com.github.chrisswalters.blockstead.plots;

import com.github.chrisswalters.blockstead.cities.City;
import com.github.chrisswalters.blockstead.cities.CityService;
import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.economy.EconomyService;
import com.github.chrisswalters.blockstead.economy.Transaction;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.roles.PlotRight;
import com.github.chrisswalters.blockstead.roles.PlotRole;
import com.github.chrisswalters.blockstead.roles.RoleService;
import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;
import com.github.chrisswalters.blockstead.util.BlocksteadFlags;
import com.github.chrisswalters.blockstead.util.Landing;
import com.github.chrisswalters.blockstead.util.PlayerLookup;
import com.github.chrisswalters.blockstead.util.WorldGuardUtils;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verwaltung bestehender Grundstücke: finden, kaufen ({@code claim}), zuweisen ({@code assign}),
 * freigeben ({@code unclaim}/{@code unassign}) und löschen ({@code remove}).
 * Angelegt werden Grundstücke über {@link CreatePlot}.
 *
 * <p>Besitzende und Mitglieder stehen in WorldGuard. Rollen unterhalb von "owner" (Besitzende) sind
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
    private final CityService cities;
    private final PluginConfig config;
    private final Logger log;

    /** Woher der Preis eines Grundstücks kommt (Reihenfolge: eigener Preis vor Stadt vor Standard). */
    public enum PriceSource { PLOT, CITY, DEFAULT }

    /** Preis, der beim Kauf eines Grundstücks gilt, und woher er kommt. */
    public record Price(long amount, PriceSource source) {
    }

    public PlotService(Database database, EconomyService economy, HomeService homes, RoleService roles,
                       SnapshotService snapshots, CityService cities, PluginConfig config, Logger log) {
        this.database = database;
        this.economy = economy;
        this.homes = homes;
        this.roles = roles;
        this.snapshots = snapshots;
        this.cities = cities;
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

    /**
     * Preis für {@code /plot claim}: der eigene Preis des Grundstücks ({@code /plot price}), sonst der Preis seiner
     * Stadt ({@code /city price}), sonst {@code plots.price} aus der config.yml.
     */
    public Price priceOf(Plot plot) {
        Integer own = BlocksteadFlags.PRICE == null ? null : plot.region().getFlag(BlocksteadFlags.PRICE);
        if (own != null) {
            return new Price(own, PriceSource.PLOT);
        }
        OptionalLong cityPrice = cities.cityOf(plot).map(City::price).orElse(OptionalLong.empty());
        if (cityPrice.isPresent()) {
            return new Price(cityPrice.getAsLong(), PriceSource.CITY);
        }
        return new Price(config.getPlotPrice(), PriceSource.DEFAULT);
    }

    /** Rolle der Person als Anzeigename für den Empfänger, z.B. "Besitzende" bzw. "Owner"; {@code null} ohne Rolle. */
    public Component describeRole(Plot plot, UUID uuid, CommandSender recipient) {
        if (plot.isOwner(uuid)) {
            return PlotRole.ownerName(recipient);
        }
        return plot.region().getMembers().contains(uuid) ? roles.describeMember(plot, uuid, recipient) : null;
    }

    // ---------------------------------------------------------------- Ändern

    /**
     * Kauft ein freies Grundstück zu seinem Preis ({@link #priceOf}) über das Economy-Plugin.
     * Reihenfolge: Economy-Plugin erreichbar? → genug Guthaben? → abbuchen → erst dann Besitz vergeben.
     * Bei Preis 0 geht der Kauf auch ohne Economy-Plugin.
     */
    public boolean claim(Player player, Plot plot) {
        if (!plot.isFree()) {
            Messages.send(player, "claim.taken", Messages.arg("id", plot.id()));
            return false;
        }
        long price = priceOf(plot).amount();
        if (price > 0) {
            if (!economy.isAvailable()) {
                Messages.send(player, "claim.economy-unavailable");
                log.warning("/plot claim by " + player.getName() + " refused: no economy plugin available through Vault");
                return false;
            }
            if (!economy.has(player, price)) {
                Messages.send(player, "claim.not-enough", Messages.arg("id", plot.id()), Messages.arg("price", economy.format(price)),
                        Messages.arg("balance", economy.format(economy.getBalance(player))));
                return false;
            }
            Transaction payment = economy.withdraw(player, price);
            if (!payment.success()) {
                Messages.send(player, "claim.payment-failed", Messages.arg("error", payment.error()));
                log.warning("Payment for " + plot.id() + " by " + player.getName() + " failed: " + payment.error());
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

        if (price > 0) {
            Messages.send(player, "claim.success-paid", Messages.arg("id", plot.id()), Messages.arg("price", economy.format(price)),
                    Messages.arg("balance", economy.format(economy.getBalance(player))));
        } else {
            Messages.send(player, "claim.success-free", Messages.arg("id", plot.id()));
        }
        log.info(player.getName() + " bought " + plot.id() + " in " + plot.world().getName() + " for " + price);
        return true;
    }

    /** Weist ein freies Grundstück kostenlos zu (Team-Befehl). */
    public boolean assign(CommandSender sender, Plot plot, OfflinePlayer target) {
        if (!plot.isFree()) {
            Messages.send(sender, "assign.taken", Messages.arg("id", plot.id()));
            return false;
        }
        plot.region().getOwners().addPlayer(target.getUniqueId());
        saveRegions(plot.world());

        String name = PlayerLookup.nameOf(target.getUniqueId());
        Messages.send(sender, "assign.done", Messages.arg("id", plot.id()), Messages.arg("name", name));
        if (target.getPlayer() != null && target.getPlayer() != sender) {
            Messages.send(target.getPlayer(), "assign.received", Messages.arg("id", plot.id()));
        }
        log.info(sender.getName() + " assigned " + plot.id() + " in " + plot.world().getName() + " to " + name);
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
            Messages.send(sender, "release.already-free", Messages.arg("id", plot.id()));
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

        Messages.send(sender, reset ? "release.done-reset" : "release.done", Messages.arg("id", plot.id()));
        log.info(sender.getName() + " released " + plot.id() + " in " + plot.world().getName() + (reset ? " and restored it" : ""));
        return true;
    }

    /**
     * Vergibt eine Rolle auf dem Grundstück. {@code roleKey} ist "owner" (bzw. ein Alias) oder ein Schlüssel aus der
     * config.yml. Eine bisherige Rolle der Person wird ersetzt; die letzte besitzende Person kann nicht
     * herabgestuft werden.
     */
    public boolean trust(CommandSender sender, Plot plot, OfflinePlayer target, String roleKey) {
        UUID uuid = target.getUniqueId();
        String name = PlayerLookup.nameOf(uuid);
        PlotRole role = null;

        if (PlotRole.isOwnerKey(roleKey)) {
            plot.region().getMembers().removePlayer(uuid);
            plot.region().getOwners().addPlayer(uuid);
            roles.removeRole(plot, uuid);
        } else {
            role = config.findRole(roleKey);
            if (role == null) {
                Messages.send(sender, "trust.unknown-role", Messages.arg("role", roleKey), Messages.arg("roles", roleKeys()));
                return false;
            }
            if (plot.isOwner(uuid)) {
                if (isLastOwner(plot, uuid)) {
                    Messages.send(sender, "trust.last-owner-demote", Messages.arg("name", name), Messages.arg("id", plot.id()));
                    return false;
                }
                plot.region().getOwners().removePlayer(uuid);
            }
            plot.region().getMembers().addPlayer(uuid);
            // Immer den eigentlichen Schlüssel speichern, auch wenn ein Alias eingegeben wurde
            roles.setRole(plot, uuid, role.key());
            // Ohne Home-Recht ist ein Home auf diesem Grundstück nicht mehr gültig
            if (!role.has(PlotRight.HOME)) {
                homes.deleteHomeIfOnPlot(uuid, plot);
            }
        }
        saveRegions(plot.world());

        final PlotRole given = role;
        Messages.send(sender, "trust.done", Messages.arg("name", name), Messages.arg("id", plot.id()),
                Messages.arg("role", given == null ? PlotRole.ownerName(sender) : given.displayName(sender)));
        Player online = target.getPlayer();
        if (online != null && online != sender) {
            Messages.send(online, "trust.received", Messages.arg("id", plot.id()),
                    Messages.arg("role", given == null ? PlotRole.ownerName(online) : given.displayName(online)));
        }
        log.info(sender.getName() + ": " + name + " on " + plot.id() + " in " + plot.world().getName() + " -> "
                + (given == null ? PlotRole.OWNER_KEY : given.key()));
        return true;
    }

    /** Entzieht der Person jede Rolle auf dem Grundstück (Besitz, Mitgliedschaft, Home darauf). */
    public boolean untrust(CommandSender sender, Plot plot, OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        String name = PlayerLookup.nameOf(uuid);
        if (plot.isOwner(uuid)) {
            if (isLastOwner(plot, uuid)) {
                Messages.send(sender, "untrust.last-owner", Messages.arg("name", name), Messages.arg("id", plot.id()));
                return false;
            }
            plot.region().getOwners().removePlayer(uuid);
        } else if (plot.region().getMembers().contains(uuid)) {
            plot.region().getMembers().removePlayer(uuid);
        } else {
            Messages.send(sender, "untrust.no-role", Messages.arg("name", name), Messages.arg("id", plot.id()));
            return false;
        }
        roles.removeRole(plot, uuid);
        homes.deleteHomeIfOnPlot(uuid, plot);
        saveRegions(plot.world());

        Messages.send(sender, "untrust.done", Messages.arg("name", name), Messages.arg("id", plot.id()));
        Player online = target.getPlayer();
        if (online != null && online != sender) {
            Messages.send(online, "untrust.received", Messages.arg("id", plot.id()));
        }
        log.info(sender.getName() + ": removed role of " + name + " on " + plot.id() + " in " + plot.world().getName());
        return true;
    }

    /**
     * Setzt den eigenen Preis des Grundstücks (geht vor dem Preis der Stadt und dem Standardpreis);
     * {@code null} entfernt ihn wieder. Ein schon bezahlter Kauf bleibt unberührt – erstattet wird immer der
     * tatsächlich bezahlte Betrag.
     */
    public boolean setPrice(CommandSender sender, Plot plot, Integer price) {
        if (BlocksteadFlags.PRICE == null) {
            Messages.send(sender, "error.flag-unavailable");
            return false;
        }
        plot.region().setFlag(BlocksteadFlags.PRICE, price);
        saveRegions(plot.world());
        if (price == null) {
            Messages.send(sender, "price.cleared", Messages.arg("id", plot.id()),
                    Messages.arg("price", economy.format(priceOf(plot).amount())));
        } else {
            Messages.send(sender, "price.set", Messages.arg("id", plot.id()), Messages.arg("price", economy.format(price)));
        }
        log.info(sender.getName() + ": price of " + plot.id() + " in " + plot.world().getName() + " -> "
                + (price == null ? "city/default" : price));
        return true;
    }

    /** Setzt den Inhalt auf die Sicherung zurück, ohne Besitz, Rollen oder Homes zu ändern (Team-Befehl). */
    public boolean reset(CommandSender sender, Plot plot) {
        if (!resetToSnapshot(sender, plot)) {
            return false;
        }
        Messages.send(sender, "reset.done", Messages.arg("id", plot.id()));
        log.info(sender.getName() + " restored " + plot.id() + " in " + plot.world().getName());
        return true;
    }

    /** Speichert den aktuellen Zustand als Sicherung zum Zurücksetzen (überschreibt eine vorhandene). */
    public boolean saveSnapshot(CommandSender sender, Plot plot) {
        try {
            snapshots.save(plot);
        } catch (IOException | WorldEditException e) {
            Messages.send(sender, "snapshot.failed", Messages.arg("id", plot.id()));
            log.log(Level.SEVERE, "Saving the snapshot of " + plot.id() + " failed", e);
            return false;
        }
        Messages.send(sender, "snapshot.saved", Messages.arg("id", plot.id()));
        log.info(sender.getName() + " saved a snapshot of " + plot.id() + " in " + plot.world().getName());
        return true;
    }

    /** Ob es für das Grundstück eine Sicherung zum Zurücksetzen gibt. */
    public boolean hasSnapshot(Plot plot) {
        return snapshots.hasSnapshot(plot);
    }

    /** Alle vergebbaren Rollenschlüssel (ohne Aliase), z.B. "owner, resident, builder, guest". */
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
            Messages.send(sender, "plot.no-regions");
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

        Messages.send(sender, "remove.done", Messages.arg("id", plot.id()));
        log.info(sender.getName() + " deleted " + plot.id() + " in " + plot.world().getName());
        return true;
    }

    /**
     * Sicherer Teleportpunkt auf dem Grundstück, möglichst mittig und innerhalb seiner Höhe ({@link Landing}).
     *
     * @return leer, wenn kein sicherer Punkt gefunden wurde
     */
    public Optional<Location> getTeleportLocation(Plot plot) {
        return Landing.find(plot.world(), plot.region(), location -> true);
    }

    // ---------------------------------------------------------------- intern

    /** Setzt das Grundstück auf die Sicherung zurück; meldet selbst, wenn es keine gibt oder es fehlschlägt. */
    private boolean resetToSnapshot(CommandSender sender, Plot plot) {
        if (!snapshots.hasSnapshot(plot)) {
            Messages.send(sender, "reset.no-snapshot", Messages.arg("id", plot.id()));
            return false;
        }
        try {
            snapshots.restore(plot);
            return true;
        } catch (IOException | WorldEditException e) {
            Messages.send(sender, "reset.failed", Messages.arg("id", plot.id()));
            log.log(Level.SEVERE, "Restoring " + plot.id() + " failed", e);
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
            throw new DataException("Could not save purchase", e);
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
                    Messages.send(online, "refund.done", Messages.arg("id", plot.id()), Messages.arg("price", economy.format(price)));
                }
                return;
            }
            String name = PlayerLookup.nameOf(payer);
            log.severe("Refund failed: " + plot.id() + " in " + plot.world().getName() + ", " + name + " (" + payer
                    + "), amount " + price + " – " + refund.error() + ". Please refund manually, e.g. /eco give " + name + " " + price);
            if (online != null) {
                Messages.send(online, "refund.failed", Messages.arg("id", plot.id()), Messages.arg("price", economy.format(price)));
            }
        } catch (SQLException e) {
            throw new DataException("Refund failed", e);
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
            log.severe("Could not save WorldGuard regions in " + world.getName() + ": " + e.getMessage());
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
