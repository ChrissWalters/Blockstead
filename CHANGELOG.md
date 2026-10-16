# Änderungen

## 2.0.1

Fehlerbehebungen und Absicherung nach einer Durchsicht des gesamten Projekts.

- `/plot tp` landet möglichst mittig und sicher: bei Polygonen auf einem Punkt mit gutem Abstand zum Rand,
  nahe dem Flächenschwerpunkt (bei L-förmigen Grundstücken vorher: auf einem Eckpunkt am Rand), und nie auf
  Lava, Feuer, Magma, Kaktus o.Ä. – sonst auf dem nächstgelegenen sicheren Punkt im Grundstück. Gibt es keinen,
  wird nicht teleportiert.
- Nametag-Teams (`bs_…`), die nach einem Absturz mit der Welt gespeichert blieben, werden beim Start entfernt.
- `/announce` und `join-info`: Klick-Aktionen, die einen Befehl ausführen (`<click:run_command:…>`), werden
  entfernt; Links und `suggest_command` bleiben.
- `/blockstead` zeigt bei veränderten Fassungen ohne `website` in der plugin.yml den Link zum Original statt
  „null“.
- Abgelaufene Wartezeiten und Sicherheitsabfragen werden aus dem Speicher entfernt.
- Build-Pipeline: Schreibrecht nur noch für den Release-Job bei Versions-Tags.

## 2.0.0

Blockstead konzentriert sich auf Grundstücke. Allgemeine Befehle und die Währung übernimmt EssentialsX (bzw.
ein anderes Economy-Plugin), angebunden über Vault.

### Entfernt
- `/coins` (inkl. `give|take|set`) und die eigene Kontoführung – Ersatz: `/balance`, `/pay`, `/eco`, `/baltop`
  des Economy-Plugins.
- `/spawn` – Ersatz: `/spawn` und `/setspawn` von EssentialsXSpawn.
- `/die` und `/suicide` – Ersatz: `/suicide` von EssentialsX.
- `/invincible` – Ersatz: `/god` von EssentialsX.
- Alias `/broadcast` für `/announce` (Konflikt mit EssentialsX); `/announce` selbst bleibt.
- Rechte `bs.coins`, `bs.coins.others`, `bs.coins.admin`, `bs.spawn`, `bs.die`, `bs.invincible`,
  `bs.invincible.others`.
- `config.yml`: `economy.currency-name` und `economy.start-balance` (werden ignoriert; Name, Zeichen und
  Startguthaben stellt man im Economy-Plugin ein).

### Geändert
- `/plot claim` bucht über Vault beim Economy-Plugin ab; ohne Vault bzw. Economy-Plugin ist Kaufen gesperrt
  (außer bei Preis 0), `/plot assign` geht weiter.
- Erstattungen beim Freigeben und Löschen laufen über das Economy-Plugin. Schlägt eine Erstattung fehl, wird
  das Grundstück trotzdem freigegeben und der Betrag mit Name im Log gemeldet.
- Beträge erscheinen im Format des Economy-Plugins.
- Vault ist eine optionale Abhängigkeit (`softdepend`), wie LuckPerms.

### Neu
- Einmalige Übertragung der Kontostände aus 1.x in das Economy-Plugin (gesetzt, nicht addiert), mit Log je
  Konto; abschaltbar mit `economy.import-old-balances: false`.
- Log-Zeile zum Status der Server-Währung kurz nach dem Start.

### Hinweise zum Update von 1.x
- **Vorher ein komplettes Server-Backup machen.**
- **`/invincible` vorher bei allen ausschalten**, die es benutzt haben (mit 1.0.1). Die Unverwundbarkeit steht
  in den Spieldaten und lässt sich danach ohne den Befehl nicht mehr abschalten.
- Vault (oder VaultUnlocked) und ein Economy-Plugin installieren, z.B. EssentialsX – für Paper 26.3 derzeit
  der Dev-Build 2.22.1-SNAPSHOT. Ohne beides können Grundstücke nicht gekauft werden.
- Rechte für die Ersatzbefehle im Rechte-Plugin vergeben (`essentials.balance`, `essentials.spawn`, …).
- Datenbank-Schema 4: Zurück auf 1.x geht nur über die Sicherung von `data.db` vor dem Update; 1.x startet mit
  Schema 4 absichtlich nicht.
- Details: `docs/admin.md`, Abschnitt „Von 1.x auf 2.0.0“.

## 1.0.1

- `/plot create` lehnt eine Auswahl ab, die ein bestehendes Grundstück überlappt, und warnt, wenn eine andere
  Region (z.B. eine Stadt) das neue Grundstück mit gleicher oder höherer Priorität überlappt.
- Die Konsistenzprüfung beim Start meldet überlappende Grundstücke und Regionen mit zu hoher Priorität.
- Doku: Abschnitt „Überlappungen und Stadtbereiche“ in `docs/admin.md`.
- **Benötigt jetzt Paper 26.3** (statt 26.2): Unter 26.3 meldet die alte Abfrage der Unverwundbarkeit falsche
  Werte, `/invincible` nutzt deshalb die neue Methode aus 26.3. Getestet mit WorldEdit 7.4.6.

## 1.0.0

Erste öffentliche Version von **Blockstead** (vorher „Buildserver“) – ein CityBuild-Plugin für Paper 26.2.

### Grundstücke
- Anlegen aus einer WorldEdit-Auswahl (Quader oder Polygon), immer vom tiefsten bis zum höchsten Punkt der Welt (`/plot create`).
- Kaufen mit der Server-Währung (`/plot claim`); alle starten mit genug Guthaben für ein Grundstück.
- Zuweisen und Entziehen durch das Team (`/plot assign`, `/plot unassign`), Freigeben durch Besitzende (`/plot unclaim`) mit voller Erstattung des Kaufpreises.
- Infos und Übersicht (`/plot info`, `/plot list`).
- Rollen auf Grundstücken: Besitzende, Mitbewohnende, Bauhilfe, Gast (`/plot trust`, `/plot untrust`); Rechte pro Rolle in der `config.yml` anpassbar.
- Homes (`/plot home`, `/plot home set`), Teleport zu Grundstücken für das Team (`/plot tp`), `/spawn` für alle.
- Zurücksetzen auf den Zustand beim Anlegen (`reset`-Option, `/plot reset`, `/plot snapshot`) mit Sicherheitsabfrage und Wartezeit für Spielende.
- Nachrichten beim Betreten und Verlassen von Grundstücken (eigener Text über die Flag `region-name`).
- Team-Befehle auch aus der Server-Konsole (mit Weltangabe).

### Server
- Server-Währung „Coins“ (Name einstellbar) mit `/coins`.
- LuckPerms-Präfixe und -Suffixe in Chat, Tab-Liste und über dem Kopf; Tab-Liste nach Gruppengewicht sortiert. LuckPerms ist optional.
- Login-Nachricht mit anklickbaren Links (z.B. Teamspeak, Discord, Website).
- Auffällige Server-Nachrichten für das Team (`/announce`).
- `/blockstead` zeigt Version, Lizenz und Link zum Quellcode.
- Eigene Join- und Quit-Nachrichten.

### Betrieb
- Sicherung von Plugin-Datenbank und WorldGuard-Regionen bei jedem Start, Schutz vor älteren Plugin-Versionen, Konsistenzprüfung.
- Alle Befehle mit Tab-Vervollständigung; Befehle ohne Recht werden ausgeblendet.
- Build-Pipeline auf GitHub mit Release bei Tags `v*`, lokaler Testserver (`./gradlew runServer`).

### Hinweise zum Update vom Vorgänger „Buildserver“
- **Vorher ein komplettes Server-Backup machen.**
- **Die alte Jar entfernen** (z.B. `buildserver-0.0.1-SNAPSHOT.jar`) – das Plugin heißt jetzt Blockstead, beide gleichzeitig würden sich stören. Der Datenordner ist jetzt `plugins/Blockstead/`; der Vorgänger hatte keine eigenen Daten, es muss nichts umgezogen werden. Rechte (`bs.*`) und die WorldGuard-Flag `region-name` bleiben gleich.
- Java 25 und Paper 26.2 werden benötigt; WorldEdit und WorldGuard sind Pflicht, LuckPerms ist optional (ab 5.5.85).
- Beim ersten Start werden von allen bestehenden Grundstücken die Flags `interact`, `use`, `chest-access`, `ride`, `vehicle-place`, `vehicle-destroy` und `lighter` entfernt, falls sie auf `deny` stehen. Sie sperrten auch Besitzende und Mitglieder aus; Fremde sperrt WorldGuard ohnehin aus. Jede Änderung steht im Log.
- Beim ersten Start wird `plugins/Blockstead/config.yml` mit allen Einstellungen neu angelegt – danach prüfen und anpassen (z.B. `join-info`, Preise, Rollen).
- Alle Spielenden bekommen beim nächsten Join das Startguthaben (Standard 100 Coins), auch wer schon ein Grundstück hat.
- Grundstücke von vor dieser Version haben keine Sicherung zum Zurücksetzen; bei Bedarf mit `/plot snapshot <ID>` anlegen.
- Der alte, ungenutzte MySQL-Code wurde entfernt.
- Lizenz: GNU AGPL-3.0 oder später.
