package com.github.chrisswalters.blockstead.listeners;

import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.plots.Plot;
import com.github.chrisswalters.blockstead.plots.PlotService;
import com.github.chrisswalters.blockstead.roles.PlotRight;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;

/**
 * Setzt die Rollen auf Grundstücken durch.
 *
 * <p>Arbeitsteilung mit WorldGuard: Fremde (weder besitzend noch Mitglied) sperrt WorldGuard über die
 * Mitgliedschaft komplett aus. Mitglieder dürfen in WorldGuard alles – dieser Listener <em>verbietet</em>
 * ihnen zusätzlich, was ihre Rolle nicht erlaubt ({@link PlotService#hasRight}). Besitzende und
 * Personen mit {@code bs.plot.bypass} werden nie eingeschränkt.
 *
 * <p>Jede Aktion wird genau einem {@link PlotRight} zugeordnet, siehe {@link #classifyBlock(Block)}
 * und die einzelnen Event-Handler. Im Zweifel gilt eine Aktion als {@link PlotRight#BUILD}.
 */
public class PlotProtectionListener implements Listener {

    /** Gegenstände, die beim Rechtsklick auf einen Block die Welt verändern (Bauen im weiteren Sinn). */
    private static final Set<Material> WORLD_CHANGING_ITEMS = Set.of(
            Material.BONE_MEAL, Material.FLINT_AND_STEEL, Material.FIRE_CHARGE, Material.BRUSH,
            Material.HONEYCOMB, Material.SHEARS, Material.ENDER_EYE, Material.END_CRYSTAL, Material.LEAD);

    /** Siehe {@link #isInteractable(Block)}. */
    private static final Set<Material> OTHER_INTERACTABLE_BLOCKS = Set.of(
            Material.REPEATER, Material.COMPARATOR, Material.NOTE_BLOCK, Material.DAYLIGHT_DETECTOR,
            Material.REDSTONE_WIRE, Material.COMPOSTER, Material.CAKE, Material.RESPAWN_ANCHOR,
            Material.BEEHIVE, Material.BEE_NEST, Material.SWEET_BERRY_BUSH, Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT, Material.DRAGON_EGG, Material.LODESTONE, Material.VAULT);

    private final PlotService plots;

    public PlotProtectionListener(PlotService plots) {
        this.plots = plots;
    }

    // ---------------------------------------------------------------- Blöcke

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        check(event, event.getPlayer(), event.getBlock().getLocation(), PlotRight.BUILD);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        check(event, event.getPlayer(), event.getBlock().getLocation(), PlotRight.BUILD);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        check(event, event.getPlayer(), event.getBlock().getLocation(), PlotRight.BUILD);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        check(event, event.getPlayer(), event.getBlock().getLocation(), PlotRight.BUILD);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onTakeLecternBook(PlayerTakeLecternBookEvent event) {
        check(event, event.getPlayer(), event.getLectern().getLocation(), PlotRight.CONTAINERS);
    }

    /**
     * Rechtsklicks und Treten auf Blöcke. Zuordnung:
     * <ul>
     *     <li>Türen/Truhen/Arbeitsblöcke → deren Recht ({@link #classifyBlock(Block)}); beim Schleichen mit
     *     Gegenstand in der Hand öffnet Minecraft sie nicht, dann zählt nur der Gegenstand</li>
     *     <li>sonstige Blöcke mit Zustandsänderung (Repeater, Notenblock, Schild, Kessel, ..., siehe
     *     {@link #isInteractable(Block)}) → BUILD</li>
     *     <li>Werkzeuge mit Wirkung auf Blöcke (Hacke, Axt, Schaufel, Knochenmehl, Feuerzeug, Spawn-Eier, ...) → BUILD</li>
     *     <li>Treten: Druckplatten/Stolperdraht → DOORS, Ackerboden/Schildkröteneier → BUILD</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();

        if (event.getAction() == Action.PHYSICAL) {
            boolean trigger = Tag.PRESSURE_PLATES.isTagged(block.getType()) || block.getType() == Material.TRIPWIRE;
            check(event, player, block.getLocation(), trigger ? PlotRight.DOORS : PlotRight.BUILD);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        boolean holdsItem = item != null && !item.getType().isAir();
        boolean opensBlock = !(player.isSneaking() && holdsItem);

        PlotRight blockRight = classifyBlock(block);
        if (opensBlock && blockRight != null) {
            check(event, player, block.getLocation(), blockRight);
        } else if (opensBlock && isInteractable(block)) {
            check(event, player, block.getLocation(), PlotRight.BUILD);
        } else if (item != null && holdsItem && changesWorld(item.getType())) {
            check(event, player, block.getLocation(), PlotRight.BUILD);
        }
    }

    // ---------------------------------------------------------------- Entities

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        checkEntity(event, event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        checkEntity(event, event.getPlayer(), event.getRightClicked());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        check(event, event.getPlayer(), event.getRightClicked().getLocation(), PlotRight.ENTITIES);
    }

    /** Angriffe auf nicht-feindliche Entities (Tiere, Rüstungsständer, Rahmen, ...), auch mit Pfeil & Co. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamageEntity(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        Entity victim = event.getEntity();
        if (attacker != null && !(victim instanceof Monster) && !(victim instanceof Player)) {
            check(event, attacker, victim.getLocation(), PlotRight.ENTITIES);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player attacker = attackingPlayer(event.getAttacker());
        if (attacker != null) {
            check(event, attacker, event.getVehicle().getLocation(), PlotRight.ENTITIES);
        }
    }

    /** Bilder und Rahmen aufhängen, Rüstungsständer/Boote/Loren/Endkristalle aufstellen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null) {
            check(event, event.getPlayer(), event.getEntity().getLocation(), PlotRight.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getPlayer() != null) {
            check(event, event.getPlayer(), event.getEntity().getLocation(), PlotRight.BUILD);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = attackingPlayer(event.getRemover());
        if (remover != null) {
            check(event, remover, event.getEntity().getLocation(), PlotRight.BUILD);
        }
    }

    // ---------------------------------------------------------------- Prüfung

    /**
     * Bricht das Event ab, wenn die Person auf dem Grundstück an {@code location} Mitglied ist,
     * aber ihre Rolle das Recht nicht hat. Fremde und Besitzende werden hier nicht behandelt.
     */
    private void check(Cancellable event, Player player, Location location, PlotRight right) {
        if (player.hasPermission("bs.plot.bypass")) {
            return;
        }
        Optional<Plot> found = plots.getPlotAt(location);
        if (found.isEmpty()) {
            return;
        }
        Plot plot = found.get();
        if (plot.isOwner(player.getUniqueId()) || !plot.region().getMembers().contains(player.getUniqueId())) {
            return;
        }
        if (!plots.hasRight(plot, player.getUniqueId(), right)) {
            event.setCancelled(true);
            player.sendActionBar(Messages.get(player, "protection.denied", Messages.arg("id", plot.id()),
                    Messages.arg("role", plots.describeRole(plot, player.getUniqueId(), player))));
        }
    }

    /** Rechtsklick auf Entities: Monster/Personen/normale Fahrzeuge frei, Fahrzeuge mit Truhe = Behälter, sonst ENTITIES. */
    private void checkEntity(Cancellable event, Player player, Entity entity) {
        if (entity instanceof Monster || entity instanceof Player) {
            return;
        }
        if (entity instanceof StorageMinecart || entity instanceof ChestBoat) {
            check(event, player, entity.getLocation(), PlotRight.CONTAINERS);
        } else if (!(entity instanceof Vehicle)) {
            check(event, player, entity.getLocation(), PlotRight.ENTITIES);
        }
    }

    /**
     * Ordnet benutzbare Blöcke einem Recht zu; {@code null} für alle anderen.
     * Öfen und Braustände sind technisch Behälter, gelten hier aber als Arbeitsblöcke.
     */
    private static PlotRight classifyBlock(Block block) {
        Material type = block.getType();
        if (Tag.DOORS.isTagged(type) || Tag.TRAPDOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type)
                || Tag.BUTTONS.isTagged(type) || Tag.PRESSURE_PLATES.isTagged(type)
                || type == Material.LEVER || type == Material.BELL) {
            return PlotRight.DOORS;
        }
        if (Tag.ANVIL.isTagged(type) || Tag.BEDS.isTagged(type)) {
            return PlotRight.WORKSTATIONS;
        }
        switch (type) {
            case CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER, GRINDSTONE, STONECUTTER, LOOM,
                 CARTOGRAPHY_TABLE, SMITHING_TABLE, ENCHANTING_TABLE, BREWING_STAND, ENDER_CHEST -> {
                return PlotRight.WORKSTATIONS;
            }
            case LECTERN, CHISELED_BOOKSHELF, DECORATED_POT, JUKEBOX -> {
                return PlotRight.CONTAINERS;
            }
            default -> {
            }
        }
        return block.getState(false) instanceof Container ? PlotRight.CONTAINERS : null;
    }

    /**
     * Sonstige Blöcke, deren Zustand sich per Rechtsklick ändert (zählen als Bauen): Redstone-Bauteile,
     * Schilder bearbeiten, Kessel, Komposter, Blumentöpfe, Kuchen, Bienenstöcke, Beeren ernten usw.
     * Bewusst eine feste Liste statt Material#isInteractable (laut Paper ungenau, z.B. zählen Treppen dazu).
     */
    private static boolean isInteractable(Block block) {
        Material type = block.getType();
        return OTHER_INTERACTABLE_BLOCKS.contains(type)
                || Tag.ALL_SIGNS.isTagged(type) || Tag.CAULDRONS.isTagged(type) || Tag.FLOWER_POTS.isTagged(type)
                || Tag.CANDLE_CAKES.isTagged(type) || Tag.CAMPFIRES.isTagged(type);
    }

    private static boolean changesWorld(Material item) {
        return WORLD_CHANGING_ITEMS.contains(item)
                || Tag.ITEMS_HOES.isTagged(item) || Tag.ITEMS_AXES.isTagged(item) || Tag.ITEMS_SHOVELS.isTagged(item)
                || item.name().endsWith("_SPAWN_EGG");
    }

    /** Die angreifende Person – direkt oder als Schütze eines Projektils; sonst {@code null}. */
    private static Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
