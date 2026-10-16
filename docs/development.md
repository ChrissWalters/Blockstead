# Blockstead – Entwicklung

Pull Requests werden nicht angenommen (siehe [README](../README.md#issues-ja-pull-requests-nein)); diese
Seite ist für alle, die an einer eigenen Kopie arbeiten. Offene Punkte, Erkenntnisse und Stolperfallen:
[notes.md](notes.md).

## Einrichtung (VSCodium / VS Code)

1. JDK 25 installieren (über den Paketmanager des Systems oder von https://adoptium.net). Ohne lokales JDK
   lädt Gradle beim Bauen selbst eins herunter, die Java-Erweiterung im Editor braucht aber ein installiertes.
2. Projektordner öffnen und die vorgeschlagenen Erweiterungen installieren (`.vscode/extensions.json`):
   Language Support for Java, Debugger for Java, Gradle for Java, YAML.

## Bauen

```sh
./gradlew build
```

Ergebnis: `build/libs/blockstead-<Version>.jar`. Alle Abhängigkeiten (Paper-API, WorldEdit, WorldGuard,
LuckPerms-API) sind `compileOnly` – der Server stellt sie bereit, in der Jar steckt nur Blockstead selbst.

## Lokaler Testserver

```sh
./gradlew runServer
```

Startet einen Paper-26.3-Server im Ordner `run/` mit dem Plugin sowie WorldEdit, WorldGuard und
LuckPerms. Beim ersten Start in `run/eula.txt` `eula=true` setzen und erneut starten. Verbinden mit
`localhost`.

Nicht bauen, während der Testserver läuft – das ersetzt die Jar unter dem laufenden Server.

**Debuggen:** `./gradlew runServer --debug-jvm` starten (wartet auf den Debugger), dann in VSCodium unter
„Run and Debug“ **„Attach to test server“** wählen. Breakpoints im Code halten dann an.

## Release

Die GitHub Action baut bei jedem Push und Pull Request und hängt die Jar als Artefakt an. Ein Tag `v*`
(z.B. `git tag v1.0.0 && git push origin v1.0.0`) erzeugt ein GitHub-Release mit der Jar. Vorher die
Version in `build.gradle` setzen und [CHANGELOG.md](../CHANGELOG.md) ergänzen.

## Projektstruktur

```
src/main/java/com/github/chrisswalters/blockstead/
├── Blockstead.java    Hauptklasse: Start, Abhängigkeiten, Registrierung von Befehlen und Listenern
├── commands/          Befehle (Paper-Brigadier)
├── config/            Einlesen der config.yml
├── display/           LuckPerms-Präfixe in Chat, Tab-Liste und Nametag (nur mit LuckPerms geladen)
├── economy/           Server-Währung (Kontostände)
├── listeners/         Event-Listener (Join, Quit, Bewegung, Schutz der Rollen)
├── players/           Laufzeitdaten pro Person, die online ist
├── plots/             Grundstücke anlegen, kaufen, zuweisen, freigeben, löschen, zurücksetzen
├── roles/             Rollen und Rechte auf Grundstücken
├── storage/           SQLite-Datenbank, Schema-Migrationen, Sicherungen
└── util/              Hilfsmethoden (WorldGuard, Namen, Textformat, Versionen)
```

## Konventionen

* **Sprache:** Nachrichten, Javadoc, Kommentare und Doku auf Deutsch, mit echten Umlauten (UTF-8).
* **Gendergerecht:** neutrale Formen („Spielende“, „Besitzende“, „Person“), wo nicht möglich Genderstern
  („Spieler*innen“) – nie das generische Maskulinum.
* **Javadoc** für jede Klasse und jede nicht-triviale Methode.
* **Datenbank:** Schema-Änderungen nur als neuer Block in `Database.migrate()`, dabei
  `Database.SCHEMA_VERSION` erhöhen; alte Blöcke nie ändern.
* **LuckPerms-Typen** nur im Paket `display` – LuckPerms ist optional.
* **Texte mit Formatierung** (`TextFormat`) nur aus vertrauenswürdigen Quellen (config.yml, Team-Befehle),
  nie aus dem Chat von Spielenden.
