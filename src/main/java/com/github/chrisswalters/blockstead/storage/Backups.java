package com.github.chrisswalters.blockstead.storage;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Sicherung der Plugin-Daten beim Serverstart.
 *
 * <p>Läuft in {@code onEnable()} <em>bevor</em> die Datenbank geöffnet wird – also auch vor Schema-Migrationen
 * und einmaligen Datenkorrekturen eines Plugin-Updates. Gesichert werden:
 * <ul>
 *     <li>{@code data.db} (samt {@code -wal}/{@code -shm}, falls nach einem Absturz vorhanden)</li>
 *     <li>die WorldGuard-Regionsdateien aller Welten ({@code plugins/WorldGuard/worlds/<Welt>/regions.yml}),
 *     da das Plugin Besitzende, Mitglieder und Flags der Grundstücke dort ändert</li>
 * </ul>
 * Ziel: {@code plugins/Blockstead/backups/<Datum_Uhrzeit>/}. Es bleiben die neuesten {@code keep} Sicherungen.
 *
 * <p>Wiederherstellen: Server stoppen, Dateien aus dem Sicherungsordner an ihren Platz zurückkopieren
 * (die {@code .db}-Dateien nach {@code plugins/Blockstead/}, {@code regions.yml} in den Welt-Ordner von
 * WorldGuard), Server starten.
 */
public final class Backups {

    private static final DateTimeFormatter FOLDER_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private Backups() {
    }

    /**
     * Legt eine Sicherung an und räumt alte auf. Fehler werden geloggt, verhindern den Start aber nicht.
     *
     * @param keep wie viele Sicherungen erhalten bleiben (mindestens 1)
     */
    public static void createStartupBackup(Plugin plugin, int keep) {
        Logger log = plugin.getLogger();
        Path backupRoot = plugin.getDataFolder().toPath().resolve("backups");
        Path target = backupRoot.resolve(LocalDateTime.now().format(FOLDER_NAME));
        try {
            Files.createDirectories(target);
            int files = 0;

            Path dataFolder = plugin.getDataFolder().toPath();
            for (String name : List.of("data.db", "data.db-wal", "data.db-shm")) {
                files += copyIfExists(dataFolder.resolve(name), target.resolve(name));
            }

            Plugin worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard");
            if (worldGuard != null) {
                Path worlds = worldGuard.getDataFolder().toPath().resolve("worlds");
                int regionFiles = 0;
                for (World world : Bukkit.getWorlds()) {
                    regionFiles += copyIfExists(worlds.resolve(world.getName()).resolve("regions.yml"),
                            target.resolve("worldguard").resolve(world.getName()).resolve("regions.yml"));
                }
                if (regionFiles == 0) {
                    log.warning("Keine WorldGuard-Regionsdateien gefunden (speichert WorldGuard in MySQL?) – "
                            + "die Regionen werden nicht mitgesichert, bitte separat sichern.");
                }
                files += regionFiles;
            }

            log.info("Sicherung angelegt: " + plugin.getDataFolder().getName() + "/backups/" + target.getFileName() + " (" + files + " Dateien)");
            prune(backupRoot, Math.max(1, keep), log);
        } catch (IOException e) {
            log.severe("Sicherung beim Start fehlgeschlagen: " + e.getMessage());
        }
    }

    /** @return 1, wenn kopiert wurde; 0, wenn die Quelle nicht existiert */
    private static int copyIfExists(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source)) {
            return 0;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return 1;
    }

    /** Löscht alle Sicherungsordner außer den neuesten {@code keep} (Ordnernamen sind zeitlich sortierbar). */
    private static void prune(Path backupRoot, int keep, Logger log) throws IOException {
        List<Path> folders;
        try (Stream<Path> stream = Files.list(backupRoot)) {
            folders = stream.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).toList();
        }
        for (Path old : folders.subList(Math.min(keep, folders.size()), folders.size())) {
            try (Stream<Path> walk = Files.walk(old)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
            log.info("Alte Sicherung gelöscht: " + old.getFileName());
        }
    }
}
