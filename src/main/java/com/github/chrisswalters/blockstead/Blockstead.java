package com.github.chrisswalters.blockstead;

import com.github.chrisswalters.blockstead.cities.CityService;
import com.github.chrisswalters.blockstead.commands.AnnounceCommand;
import com.github.chrisswalters.blockstead.commands.CityCommand;
import com.github.chrisswalters.blockstead.commands.InfoCommand;
import com.github.chrisswalters.blockstead.commands.PlotCommand;
import com.github.chrisswalters.blockstead.config.PluginConfig;
import com.github.chrisswalters.blockstead.display.DisplayService;
import com.github.chrisswalters.blockstead.display.DisplaySetup;
import com.github.chrisswalters.blockstead.economy.BalanceImport;
import com.github.chrisswalters.blockstead.economy.EconomyService;
import com.github.chrisswalters.blockstead.lang.Messages;
import com.github.chrisswalters.blockstead.listeners.*;
import com.github.chrisswalters.blockstead.players.PlayerUtility;
import com.github.chrisswalters.blockstead.plots.ConsistencyCheck;
import com.github.chrisswalters.blockstead.plots.HomeService;
import com.github.chrisswalters.blockstead.plots.LegacyFlagFix;
import com.github.chrisswalters.blockstead.plots.PlotService;
import com.github.chrisswalters.blockstead.plots.SnapshotService;
import com.github.chrisswalters.blockstead.roles.RoleService;
import com.github.chrisswalters.blockstead.storage.Backups;
import com.github.chrisswalters.blockstead.storage.DataException;
import com.github.chrisswalters.blockstead.storage.Database;
import com.github.chrisswalters.blockstead.util.BlocksteadFlags;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.StringFlag;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

/**
 * Hauptklasse des Blockstead-Plugins.
 *
 * <p>Ablauf beim Serverstart:
 * <ol>
 *     <li>{@link #onLoad()}: eigene WorldGuard-Flags registrieren ({@link BlocksteadFlags}). Das muss passieren,
 *     bevor WorldGuard aktiviert wird, deshalb nicht erst in {@link #onEnable()}.</li>
 *     <li>{@link #onEnable()}: Abhängigkeiten (WorldEdit, WorldGuard; LuckPerms optional) holen und prüfen,
 *     config.yml laden, Sicherung anlegen, Datenbank öffnen (inkl. Migrationen), einmalige Korrekturen
 *     und Konsistenzprüfung, Listener und Befehle registrieren.</li>
 * </ol>
 *
 * <p>Andere Klassen erreichen das Plugin und die Abhängigkeiten über die statischen Getter,
 * z.B. {@link #getInstance()}. Diese liefern erst nach {@link #onEnable()} Werte.
 */
public final class Blockstead extends JavaPlugin {

    private static Blockstead instance;
    private static WorldGuardPlugin worldGuardPlugin;
    private static WorldEditPlugin worldEditPlugin;
    private static DisplayService display;
    private static PluginConfig pluginConfig;
    private static Database database;
    private static EconomyService economy;
    private static HomeService homes;
    private static PlotService plotService;
    private static CityService cities;

    /**
     * WorldGuard-Flag {@code region-name}: Text, der beim Betreten einer Region angezeigt wird.
     * Setzen z.B. mit {@code /rg flag <id> region-name <Text>}.
     * Ist {@code null}, wenn die Registrierung fehlgeschlagen ist (siehe Log).
     */
    public static StringFlag REGIONNAME;

    @Override
    public void onLoad() {
        // Gehört schon ein anderes Plugin "region-name" mit passendem Typ, wird dessen Flag mitbenutzt
        REGIONNAME = BlocksteadFlags.register(new StringFlag("region-name"), StringFlag.class, getLogger());
        BlocksteadFlags.registerAll(getLogger());
    }

    @Override
    public void onEnable() {
        instance = this;

        worldEditPlugin = (WorldEditPlugin) getServer().getPluginManager().getPlugin("WorldEdit");
        worldGuardPlugin = (WorldGuardPlugin) getServer().getPluginManager().getPlugin("WorldGuard");
        pluginConfig = PluginConfig.load(this);
        Messages.load(this, pluginConfig.getLanguage(), pluginConfig.getFallbackLanguage());
        if (pluginConfig.isBackupsEnabled()) {
            // Vor dem Öffnen der Datenbank, also auch vor Migrationen und Datenkorrekturen eines Updates
            Backups.createStartupBackup(this, pluginConfig.getBackupsKeep());
        }

        try {
            database = new Database(new File(getDataFolder(), "data.db"));
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not open the database. Disabling the plugin. " + e.getMessage(), e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        economy = new EconomyService();
        homes = new HomeService(database);
        cities = new CityService(pluginConfig, getLogger());
        plotService = new PlotService(database, economy, homes, new RoleService(database, pluginConfig),
                new SnapshotService(getDataFolder()), cities, pluginConfig, getLogger());
        try {
            LegacyFlagFix.runOnce(database, getLogger());
            ConsistencyCheck.run(database, plotService, cities, pluginConfig.getPlotPriority(), getLogger());
        } catch (DataException e) {
            // Korrektur/Prüfung sind nicht kritisch: loggen und normal weiterstarten
            getLogger().log(Level.SEVERE, e.getMessage(), e);
        }

        // Listener
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerDisconnectListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(), this);
        getServer().getPluginManager().registerEvents(new PlotProtectionListener(plotService), this);

        // Befehle über das Brigadier-Befehlssystem von Paper (nicht über plugin.yml)
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(PlotCommand.build(), "Plot commands", List.of("p"));
            commands.register(CityCommand.build(), "City commands");
            commands.register(InfoCommand.build(), "Version, license and source code of Blockstead");
            // Ohne Alias /broadcast – den belegt EssentialsX
            commands.register(AnnounceCommand.build("announce"), "Highlighted server message from the team to everyone");
        });

        // LuckPerms ist optional: nur dann die Klassen aus dem Paket display laden
        if (pluginConfig.isDisplayEnabled() && getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            display = DisplaySetup.enable(this, pluginConfig);
        } else if (pluginConfig.isDisplayEnabled()) {
            getLogger().warning("LuckPerms is not installed – prefixes in chat and tab list are disabled.");
        }

        // Wer schon online ist (z.B. nach /reload), bekommt keinen PlayerJoinEvent
        for (Player player : getServer().getOnlinePlayers()) {
            PlayerUtility.initBSPlayer(player);
        }

        // Erst im ersten Tick, wenn alle Plugins aktiv sind: Economy-Plugins wie EssentialsX melden sich bei
        // Vault schon beim Laden an, sind aber erst nach ihrem eigenen onEnable benutzbar
        getServer().getScheduler().runTask(this, () -> {
            economy.logStatus(getLogger());
            try {
                BalanceImport.run(database, economy, pluginConfig.isImportOldBalances(), getLogger());
            } catch (DataException e) {
                getLogger().log(Level.SEVERE, e.getMessage(), e);
            }
        });
    }

    @Override
    public void onDisable() {
        if (display != null) {
            display.stop();
        }
        if (database != null) {
            try {
                database.close();
            } catch (SQLException e) {
                getLogger().log(Level.WARNING, "Could not close the database cleanly.", e);
            }
        }
    }

    /** @return die laufende Plugin-Instanz */
    public static Blockstead getInstance() {
        return instance;
    }

    /** @return das WorldGuard-Plugin (Pflichtabhängigkeit) */
    public static WorldGuardPlugin getWorldGuardPlugin() {
        return worldGuardPlugin;
    }

    /** @return das WorldEdit-Plugin (Pflichtabhängigkeit) */
    public static WorldEditPlugin getWorldEditPlugin() {
        return worldEditPlugin;
    }

    /** @return Kontostände der Server-Währung */
    public static EconomyService getEconomy() {
        return economy;
    }

    /** @return Homes auf Grundstücken */
    public static HomeService getHomes() {
        return homes;
    }

    /** @return Verwaltung bestehender Grundstücke */
    public static PlotService getPlotService() {
        return plotService;
    }

    /** @return Verwaltung der Städte */
    public static CityService getCities() {
        return cities;
    }

    /** @return die beim Start eingelesene config.yml */
    public static PluginConfig getPluginConfig() {
        return pluginConfig;
    }
}
