# Blockstead – Betrieb und Team

Diese Seite ist für Serverbetreibende und das Server-Team. Was Spielende wissen müssen, steht in der
[README](../README.md).

## Voraussetzungen

| Komponente | Version |
|---|---|
| Paper | 26.3 oder neuer |
| Java | 25 |
| WorldEdit | 7.4.x (getestet mit 7.4.6) |
| WorldGuard | 7.0.19 |
| LuckPerms (optional) | 5.5.85 oder neuer – für Präfixe in Chat, Tab-Liste und über dem Kopf |
| Vault oder VaultUnlocked (optional) | Vault 1.7.3 – nötig, damit Grundstücke **gekauft** werden können |
| Economy-Plugin (optional) | z.B. EssentialsX – führt die Kontostände; für Paper 26.3 derzeit nur als Dev-Build (2.22.1-SNAPSHOT) |

Ohne Vault bzw. Economy-Plugin läuft Blockstead weiter: `/plot claim` ist dann gesperrt (außer bei Preis 0),
das Team kann Grundstücke mit `/plot assign` vergeben. Den Status zeigt eine Zeile im Log kurz nach dem Start
(„Server-Währung über Vault: …“ bzw. eine Warnung).

**Aufgabenteilung:** Blockstead kümmert sich nur um Grundstücke (plus `/announce`, Login-Nachricht und
LuckPerms-Präfixe). Allgemeine Befehle wie `/spawn`, `/balance`, `/pay`, `/god` oder `/suicide` und die
Währung selbst kommen von EssentialsX (bzw. einem anderen Plugin).

## Installation

1. `blockstead-<Version>.jar` aus einem [GitHub-Release](https://github.com/ChrissWalters/Blockstead/releases)
   laden (oder selbst bauen, siehe [development.md](development.md)).
2. Nach `plugins/` des Servers kopieren, Server neu starten.
3. Beim ersten Start wird `plugins/Blockstead/config.yml` angelegt.

## Update

1. **Vorher ein komplettes Server-Backup machen** (Welten und `plugins/`).
2. Die alte Jar aus `plugins/` entfernen, die neue hineinlegen, Server neu starten.
3. Hinweise zur jeweiligen Version in [CHANGELOG.md](../CHANGELOG.md) lesen – insbesondere neue Einträge
   der `config.yml`, die nicht automatisch ergänzt werden.

### Von 1.x auf 2.0.0

Blockstead 2.0.0 gibt die eigene Währung und die allgemeinen Befehle ab:

* **Entfernt:** `/coins`, `/spawn`, `/die` (`/suicide`), `/invincible` und die Rechte `bs.coins*`, `bs.spawn`,
  `bs.die`, `bs.invincible*`; `/announce` hat keinen Alias `/broadcast` mehr. Ersatz bei EssentialsX:
  `/balance`, `/pay`, `/eco`, `/baltop`, `/spawn`, `/setspawn`, `/suicide`, `/god` – Rechte dafür im
  Economy- bzw. Rechte-Plugin vergeben (`essentials.*`).
* **Vorher:** `/invincible` mit 1.0.1 bei allen ausschalten, die es benutzt haben. Die Unverwundbarkeit steht
  in den Spieldaten und lässt sich ohne den Befehl nicht mehr abschalten (`/god` ist ein eigener Schalter).
* **Kontostände:** Beim ersten Start mit Vault und Economy-Plugin überträgt Blockstead jedes alte Konto
  einmal. Der Stand im Economy-Plugin wird auf den alten Wert **gesetzt**, nicht addiert. Im Log steht je
  Konto eine Zeile und am Ende „Übertragung abgeschlossen: x von N Konto/Konten übertragen.“ Fehlgeschlagene
  Konten werden beim nächsten Start erneut versucht. Mit `economy.import-old-balances: false` werden die alten
  Stände nicht übertragen. EssentialsX kennt die Namen übertragener Konten erst, wenn die Person wieder online
  war (`/baltop` zeigt bis dahin „null“). Auf Offline-Mode-Servern legt EssentialsX die Konten als NPC an –
  dann fehlen sie in `/baltop`.
* **Datenbank:** Schema-Version 4. Zurück auf 1.x geht nur über die Sicherung von `data.db` vor dem Update
  (ältester Ordner vom Update-Tag unter `plugins/Blockstead/backups/`) – 1.x startet mit Schema 4 absichtlich nicht.
* **`config.yml`:** `economy.currency-name` und `economy.start-balance` werden ignoriert (Name, Zeichen und
  Startguthaben stellt man im Economy-Plugin ein). Neu ist `economy.import-old-balances` (Standard `true`).

Vom Vorgänger **„Buildserver“**: Die alte Jar (z.B. `buildserver-0.0.1-SNAPSHOT.jar`) muss entfernt werden – beide gleichzeitig
würden sich gegenseitig stören. Rechte (`bs.*`) und WorldGuard-Regionen bleiben gleich; Details im
[CHANGELOG](../CHANGELOG.md).

## Grundstücke anlegen

Grundstücke sind WorldGuard-Regionen mit der ID `plot_<Nummer>`:

1. Mit WorldEdit eine Fläche auswählen – Quader (`//wand`, zwei Punkte) oder Polygon (`//sel poly`).
   Die Höhe spielt keine Rolle: Grundstücke reichen immer vom tiefsten bis zum höchsten Punkt der Welt.
2. `/plot create` – legt das Grundstück mit den Standard-Flags aus der `config.yml` an und sichert seinen
   Zustand (für das spätere Zurücksetzen).

Neue Grundstücke haben keine Besitzenden. Spielende kaufen sie mit `/plot claim`, das Team vergibt sie
mit `/plot assign`. Den Text beim Betreten setzt du mit der WorldGuard-Flag `region-name`, z.B.
`/rg flag plot_3 region-name Willkommen bei Alex`.

### Überlappungen und Stadtbereiche

* **Grundstücke dürfen sich nicht überlappen.** `/plot create` lehnt eine Auswahl ab, die ein bestehendes
  Grundstück berührt.
* **Andere Regionen dürfen Grundstücke überlappen** – z.B. eine Stadtregion mit Straßen dazwischen. Sie
  brauchen dafür eine **niedrigere Priorität** als die Grundstücke (Standard 10, `plots.priority`), am
  einfachsten 0: Dann gelten auf den Grundstücken deren Besitzende, Rollen und Flags, auf den Straßen die
  Regeln der Stadtregion. Bei gleicher oder höherer Priorität überstimmt die Stadtregion die Grundstücke –
  `/plot create` warnt dann, Priorität ändern mit `/rg setpriority <ID> 0`.
* Grundstücke **nicht** per `/rg setparent` unter eine Stadtregion hängen: Kinder erben in WorldGuard die
  Mitglieder der Eltern-Region – alle Mitglieder der Stadt dürften dann auf jedem Grundstück bauen.
* Beim Start meldet die Konsistenzprüfung überlappende Grundstücke und Regionen mit zu hoher Priorität im
  Log (ohne etwas zu ändern).

## Team-Befehle

`[ID]` weglassen = das Grundstück, auf dem man steht. `[Welt]` ist im Spiel optional (Standard: eigene
Welt) und **aus der Server-Konsole Pflicht**, z.B. `plot assign plot_3 Alex world`.

| Befehl | Beschreibung | Recht |
|---|---|---|
| `/plot create` | Grundstück aus der WorldEdit-Auswahl anlegen (nur im Spiel) | `bs.plot.create` |
| `/plot remove <ID> [Welt] [reset]` | Grundstück löschen; Kaufpreis wird erstattet | `bs.plot.remove` |
| `/plot assign <ID> <Name> [Welt]` | Freies Grundstück kostenlos zuweisen (auch an Personen, die offline sind) | `bs.plot.assign` |
| `/plot unassign <ID> [Welt] [reset]` | Beliebiges Grundstück freigeben; Kaufpreis wird erstattet | `bs.plot.unassign` |
| `/plot reset [ID] [Welt]` | Auf die Sicherung zurücksetzen, Besitz und Rollen bleiben | `bs.plot.reset` |
| `/plot snapshot <ID> [Welt]` | Aktuellen Zustand als Sicherung zum Zurücksetzen speichern | `bs.plot.snapshot` |
| `/plot tp <ID> [Welt]`, `/plot teleport …` | Zu einem Grundstück teleportieren (nur im Spiel) | `bs.plot.tp` |
| `/plot list <Name>` | Grundstücke einer anderen Person | `bs.plot.list.others` |
| `/plot trust`, `/plot untrust` auf fremden Grundstücken | Rollen verwalten wie Besitzende | `bs.plot.trust.others` |
| – | Wird von Grundstücks-Rollen nicht eingeschränkt | `bs.plot.bypass` |
| `/announce <Nachricht>` | Auffällige Server-Nachricht an alle (Chat, Einblendung, Ton) | `bs.announce` |

**Zurücksetzen:** `reset` stellt den Zustand beim Anlegen (oder beim letzten `/plot snapshot`) wieder her –
exakt in der Grundstücksform, Nachbargrundstücke bleiben unberührt; Tiere, Rahmen und Rüstungsständer auf
dem Grundstück werden entfernt. Unwiderrufliche Aktionen fragen nach: denselben Befehl innerhalb von 30
Sekunden wiederholen. Ohne Sicherung (Grundstücke von vor Version 1.0.0) wird `reset` abgelehnt und nichts
geändert. Zurücksetzen läuft im Hauptthread – bei großen Grundstücken kann der Server kurz stocken.

**Aus der Konsole** gehen `info`, `trust`, `untrust`, `remove`, `assign`, `unassign`, `reset`, `snapshot`,
`list <Name>` und `/announce`.

**Erstattungen:** Beim Freigeben oder Löschen geht der Kaufpreis über das Economy-Plugin an die Person, die
bezahlt hat. Kann das nicht gebucht werden (z.B. Economy-Plugin fehlt), wird das Grundstück trotzdem
freigegeben; im Log steht dann ein Fehler „Erstattung fehlgeschlagen: …“ mit Name, UUID, Betrag und dem
Hinweis, von Hand zu erstatten (z.B. `/eco give <Name> <Betrag>`).

## Rechte

Alle Rechte beginnen mit `bs.`. „Alle“ = alle Spielenden; „Op“ = Ops und die Konsole automatisch, alle
anderen über LuckPerms, z.B. `lp group team permission set bs.plot.assign true`.

| Recht | Standard | Wofür |
|---|---|---|
| `bs.plot` | alle | `/plot`, `/plot info`, `/plot list` |
| `bs.plot.claim` | alle | Grundstück kaufen |
| `bs.plot.unclaim` | alle | Eigenes Grundstück zurückgeben |
| `bs.plot.home` | alle | Zuhause setzen und hinteleportieren |
| `bs.plot.trust` | alle | Rollen auf eigenen Grundstücken vergeben |
| alle übrigen `bs.*` | Op | siehe Team-Befehle |

`/blockstead` (Version, Lizenz, Quellcode) braucht kein Recht.

## Konfiguration

`plugins/Blockstead/config.yml` – jeder Eintrag ist in der Datei selbst erklärt; Änderungen gelten nach
einem Neustart.

| Abschnitt | Inhalt |
|---|---|
| `display` | LuckPerms-Präfixe an/aus, Vorlagen für Chat und Tab-Liste (`{prefix}`, `{name}`, `{suffix}`, `{message}`), Sortierung der Tab-Liste nach Gruppengewicht, Nametag |
| `join-info` | Login-Nachricht (z.B. Teamspeak, Discord); MiniMessage-Formatierung, `http(s)://`-Adressen werden anklickbar. Für Teamspeak einen Einladungslink wie `https://invite.teamspeak.com/<Adresse>` verwenden – Minecraft öffnet nur http(s)-Links |
| `announce` | Präfix, Einblendung und Ton von `/announce` |
| `backups` | Sicherung beim Start an/aus, Anzahl der aufbewahrten Sicherungen |
| `economy` | `import-old-balances`: alte Kontostände aus 1.x einmalig übertragen (Währungsname und Startguthaben stellt man im Economy-Plugin ein) |
| `plots` | Preis (ganze Zahl in der Währung des Economy-Plugins), Wartezeit für `unclaim reset`, WorldGuard-Priorität, Standard-Flags neuer Grundstücke |
| `roles` | Rollen unterhalb von „besitzende“ und ihre Rechte (`build`, `containers`, `doors`, `workstations`, `entities`, `home`) |

Hinweise:

* Neue Einträge aus Updates werden **nicht** automatisch in eine bestehende `config.yml` übernommen; fehlende
  Einträge haben ihren Standardwert. Die aktuelle Vorlage: [`src/main/resources/config.yml`](../src/main/resources/config.yml).
* Schutz-Flags wie `use`, `interact`, `chest-access` oder `build` gehören nicht in `plots.default-flags`: In
  WorldGuard gelten sie auch für Besitzende und Mitglieder. Fremde sperrt WorldGuard ohnehin aus; was
  Mitglieder dürfen, regeln die Rollen. Solche Einträge werden mit Warnung ignoriert.
* Ohne LuckPerms läuft das Plugin ohne Präfix-Anzeige (Warnung im Log).

## Daten und Sicherungen

`plugins/Blockstead/data.db` (SQLite) enthält Zuhause, Rollen und wer welches Grundstück für wie viel gekauft
hat (für die Erstattung). Die Kontostände führt seit 2.0.0 das Economy-Plugin; die alten Stände aus 1.x
stehen nur noch als Nachweis in der Tabelle `accounts` (mit Zeitpunkt der Übertragung). Besitzende und Mitglieder stehen in WorldGuard; die Sicherungen zum Zurücksetzen
liegen als WorldEdit-Schematic in `plugins/Blockstead/snapshots/<Welt>/<ID>.schem`.

* **Sicherung bei jedem Start:** Bevor das Plugin etwas ändert (auch vor Datenmigrationen eines Updates),
  werden `data.db` und die WorldGuard-Regionsdateien aller Welten nach
  `plugins/Blockstead/backups/<Datum_Uhrzeit>/` kopiert; die neuesten 10 bleiben. Speichert WorldGuard in
  MySQL, gibt es eine Warnung – dann die Regionen separat sichern.
* **Wiederherstellen:** Server stoppen, `data.db` aus dem Sicherungsordner nach `plugins/Blockstead/` und
  `worldguard/<Welt>/regions.yml` nach `plugins/WorldGuard/worlds/<Welt>/` kopieren, Server starten.
* **Schutz vor älteren Versionen:** Ist die Datenbank neuer als die installierte Plugin-Version, startet
  das Plugin nicht und verändert nichts.
* **Konsistenzprüfung:** Beim Start werden Einträge zu Grundstücken gemeldet, die es in WorldGuard nicht
  (mehr) gibt (z.B. nach `/rg remove` statt `/plot remove`). Es wird nichts automatisch gelöscht.
* Die Welten selbst sichert das Plugin nicht – dafür ist ein Server-Backup zuständig.
