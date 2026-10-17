# Blockstead – Betrieb und Team

*In English: [admin.md](admin.md)*

Diese Seite ist für Serverbetreibende und das Server-Team. Was Spielende wissen müssen, steht in der
[README](../README.de.md).

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
(„Server currency through Vault: …“ bzw. eine Warnung).

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
3. Hinweise zur jeweiligen Version in [CHANGELOG.de.md](../CHANGELOG.de.md) lesen – insbesondere neue
   Einträge der `config.yml`, die nicht automatisch ergänzt werden.

### Von 2.2.x/2.3.x auf 2.4.0

* Neu sind `/city teleport` (Recht `bs.city.tp`, alle – ergänzen, wenn Spielende ihre Rechte in LuckPerms
  ausdrücklich bekommen), `/city setspawn` und optionale Höhen (`bottom`, `top` oder `selection`) für
  `/plot create`, `/city create` und `/city redefine`. `/plot home` hat jetzt ebenfalls die Wartezeit von
  einer Sekunde. Bestehende Grundstücke und Städte bleiben unverändert.

### Von 2.2.x auf 2.3.0

* **Städte** sind neu (siehe [Städte](#städte)). Bestehende Grundstücke und WorldGuard-Regionen bleiben
  unverändert. Eine bisher von Hand gepflegte Stadtregion wird so zur Blockstead-Stadt:
  [Bestehende Stadtregion übernehmen](#bestehende-stadtregion-übernehmen).
* **`config.yml`:** neuer Abschnitt `cities` (Priorität und Standard-Flags neuer Städte). Ohne ihn gelten
  Priorität 0 und die Standard-Flags der Vorlage.
* **Rechte:** neu sind `bs.city` (alle), `bs.city.manage`, `bs.city.trust` und `bs.plot.price` (Op). Wer
  Spielenden ihre Rechte in LuckPerms ausdrücklich gibt, ergänzt `bs.city`.
* Die Daten bleiben in WorldGuard: Name, Preise und Texte sind Flags der Regionen (`blockstead-name`,
  `blockstead-price`, `blockstead-greeting`, `blockstead-farewell`). Die Datenbank ist unverändert; zurück auf
  2.2.x geht (die Flags wirken dann einfach nicht).

### Von 2.0.x auf 2.1.0

* **Sprachen:** Alle Texte kommen aus Sprachdateien (siehe [Sprachen](#sprachen)). Ohne Änderung an der
  `config.yml` bekommt jede Person die Sprache ihres Minecraft-Clients, die Konsole Englisch. Für einen
  rein deutschen Server: `language: de` und `fallback-language: de` in die `config.yml` eintragen.
* **Log-Meldungen sind englisch** (z.B. „Transfer finished: …“ statt „Übertragung abgeschlossen: …“).
* **Rollen:** Die neuen Standard-Schlüssel sind `owner`, `resident`, `builder`, `guest`. Eine bestehende
  `config.yml` behält ihre deutschen Schlüssel – alles läuft weiter wie bisher, `besitzende` gilt als Alias
  für `owner`. Wer umstellen möchte, übernimmt den Abschnitt `roles` aus der neuen Vorlage
  ([`src/main/resources/config.yml`](../src/main/resources/config.yml)); die dortigen `aliases` sorgen dafür,
  dass gespeicherte Rollen mit deutschen Schlüsseln gültig bleiben. Ohne `name` werden die Rollennamen
  übersetzt angezeigt.
* **`region-name`:** `/plot create` setzt die Flag nicht mehr. Die Meldung beim Betreten kommt übersetzt aus
  der Sprachdatei; alte Grundstücke mit dem früheren Standardtext „Du betrittst das Grundstück …“ zeigen
  ebenfalls die übersetzte Meldung. Eigene Texte bleiben unverändert.
* **`config.yml`:** neu sind `language` und `fallback-language`; die Kommentare der Vorlage sind englisch.

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
  Konto eine Zeile und am Ende „Transfer finished: x of N account(s) transferred.“ (bis 2.0.1 auf Deutsch).
  Fehlgeschlagene Konten werden beim nächsten Start erneut versucht. Mit `economy.import-old-balances: false`
  werden die alten Stände nicht übertragen. EssentialsX kennt die Namen übertragener Konten erst, wenn die
  Person wieder online war (`/baltop` zeigt bis dahin „null“). Auf Offline-Mode-Servern legt EssentialsX die
  Konten als NPC an – dann fehlen sie in `/baltop`.
* **Datenbank:** Schema-Version 4. Zurück auf 1.x geht nur über die Sicherung von `data.db` vor dem Update
  (ältester Ordner vom Update-Tag unter `plugins/Blockstead/backups/`) – 1.x startet mit Schema 4 absichtlich nicht.
* **`config.yml`:** `economy.currency-name` und `economy.start-balance` werden ignoriert (Name, Zeichen und
  Startguthaben stellt man im Economy-Plugin ein). Neu ist `economy.import-old-balances` (Standard `true`).

Vom Vorgänger **„Buildserver“**: Die alte Jar (z.B. `buildserver-0.0.1-SNAPSHOT.jar`) muss entfernt werden –
beide gleichzeitig würden sich gegenseitig stören. Rechte (`bs.*`) und WorldGuard-Regionen bleiben gleich;
Details im [CHANGELOG](../CHANGELOG.de.md).

## Grundstücke anlegen

Grundstücke sind WorldGuard-Regionen mit der ID `plot_<Nummer>`:

1. Mit WorldEdit eine Fläche auswählen – Quader (`//wand`, zwei Punkte) oder Polygon (`//sel poly`).
   Die Höhe der Auswahl spielt keine Rolle.
2. `/plot create` – legt das Grundstück mit den Standard-Flags aus der `config.yml` an und sichert seinen
   Zustand (für das spätere Zurücksetzen). Ohne weitere Angaben reicht es vom tiefsten bis zum höchsten Punkt
   der Welt.

**Höhengrenzen (optional):** `/plot create bottom <Y>`, `/plot create top <Y>` oder beides, in beliebiger
Reihenfolge (z.B. `/plot create bottom 60 top 70`; beide Werte gehören dazu) – oder `/plot create selection`:
genau die Höhe der WorldEdit-Auswahl, praktisch für Stockwerke. Alle Höhen sind absolute Y-Koordinaten (F3
oder die Meldung von `//pos1` zeigt sie). Relativ zur Auswahl: erst mit WorldEdit erweitern, z.B.
`//expand 60 up` oder `//expand 80 down`, dann `/plot create selection`. Grundstücke dürfen dann auch
übereinander liegen – z.B. Wohnungen in einem Haus –, solange sie sich nicht überschneiden. Über und unter so
einem Grundstück gelten dessen Regeln nicht; in einer Stadt schützt die Stadt diesen Raum, außerhalb von
Städten nichts (`/plot create` warnt). `/plot info` zeigt die Höhe nur, wenn sie begrenzt ist; `/plot tp`
landet innerhalb der Höhe (auf dem Boden eines Geschosses, nicht auf dem Dach).

Neue Grundstücke haben keine Besitzenden. Spielende kaufen sie mit `/plot claim`, das Team vergibt sie
mit `/plot assign`. Beim Betreten erscheint die übersetzte Standardmeldung; einen eigenen Text setzt du mit
der WorldGuard-Flag `region-name`, z.B. `/rg flag plot_3 region-name Willkommen bei Alex` (der gilt dann für
alle Sprachen).

### Überlappungen

* **Grundstücke dürfen sich nicht überlappen.** `/plot create` lehnt eine Auswahl ab, die ein bestehendes
  Grundstück berührt.
* **Andere Regionen dürfen Grundstücke überlappen** – z.B. eine Stadt ([Städte](#städte)) oder eine von Hand
  in WorldGuard gepflegte Stadtregion. Sie
  brauchen dafür eine **niedrigere Priorität** als die Grundstücke (Standard 10, `plots.priority`), am
  einfachsten 0: Dann gelten auf den Grundstücken deren Besitzende, Rollen und Flags, auf den Straßen die
  Regeln der Stadtregion. Bei gleicher oder höherer Priorität überstimmt die Stadtregion die Grundstücke –
  `/plot create` warnt dann, Priorität ändern mit `/rg setpriority <ID> 0`.
* Grundstücke **nicht** per `/rg setparent` unter eine Stadtregion hängen: Kinder erben in WorldGuard die
  Mitglieder der Eltern-Region – alle Mitglieder der Stadt dürften dann auf jedem Grundstück bauen.
* Beim Start meldet die Konsistenzprüfung überlappende Grundstücke und Regionen mit zu hoher Priorität im
  Log (ohne etwas zu ändern).

## Städte

Eine Stadt ist eine WorldGuard-Region, die Blockstead verwaltet (`city_<Name>`), vom tiefsten bis zum höchsten
Punkt der Welt – oder, mit `bottom <Y>` / `top <Y>` wie bei Grundstücken, zwischen festen Höhen (z.B. eine
Stadt unter der Erde). Sie schützt alles, was der Stadt selbst gehört:

* **Außerhalb der Grundstücke** bauen nur die Personen, die mit `/city trust` eingetragen sind – Straßen,
  Plätze und Gebäude der Stadt lassen sich nicht griefen. Das Team mit WorldGuard-Bypass (`/rg bypass`) kann
  immer bauen.
* **Auf Grundstücken** zählt nur das Grundstück (Grundstücke haben die höhere Priorität, `plots.priority` >
  `cities.priority`): Besitzende und Rollen funktionieren wie bisher, wer in der Stadt bauen darf, hat dort
  keine Rechte.
* Ein Grundstück **gehört zu einer Stadt**, wenn es mindestens einen Block mit ihr teilt – auch wenn es über
  die Stadtgrenze hinausragt. Berührt es mehrere Städte, gilt die mit dem größten gemeinsamen Raum
  (`/plot create` weist darauf hin). Städte dürfen sich nicht überlappen (übereinander geht). Grundstücke
  außerhalb von Städten funktionieren wie bisher.
* **Teleport:** `/city teleport <City>` (kurz `/city tp`) bringt alle zum Landepunkt der Stadt. Das Team setzt
  ihn mit `/city setspawn <City>` an der eigenen Position (mit Blickrichtung; sie muss in der Stadt liegen –
  `/city create` erinnert daran). Ohne Landepunkt sucht Blockstead einen sicheren Punkt möglichst mittig, nach
  Möglichkeit nicht auf einem Grundstück. Zwischen zwei Teleports einer Person (`/city teleport` und
  `/plot home`) liegt mindestens eine Sekunde (Team ausgenommen). Der Landepunkt ist WorldGuards Flag `teleport` –
  `/rg teleport city_<name>` landet also ebenfalls dort.
* **Preis** eines Grundstücks: eigener Preis (`/plot price`) > Preis seiner Stadt (`/city price`) >
  `plots.price`. Erstattet wird immer der tatsächlich bezahlte Betrag.
* **Begrüßung und Abschied:** Beim Betreten und Verlassen sehen alle einen übersetzten Standardtext
  („Willkommen in Neustadt!“). `/city greeting` und `/city farewell` setzen einen eigenen Text (MiniMessage,
  `<city>` = Name der Stadt; er gilt dann für alle Sprachen), `default` stellt den Standardtext wieder her,
  `off` schaltet die Meldung ab.

| Befehl | Beschreibung | Recht |
|---|---|---|
| `/city`, `/city help` | Anklickbare Übersicht | `bs.city` |
| `/city list` | Alle Städte mit Grundstücken, freien Grundstücken und Preis | `bs.city` |
| `/city info [City]` | Grundstücke, Preis; fürs Team auch Baurechte, Begrüßung und Abschied | `bs.city` |
| `/city free <City> [Seite]` | Freie Grundstücke der Stadt | `bs.plot.free` |
| `/city teleport <City>`, `/city tp …` | In die Stadt teleportieren (nur im Spiel) | `bs.city.tp` |
| `/city plots <City> [all\|free\|taken] [Seite]` | Alle Grundstücke der Stadt, wie `/plot plots` | `bs.plot.list.all` |
| `/city create <Name> [bottom <Y>] [top <Y>\|selection]` | Stadt aus der WorldEdit-Auswahl anlegen (nur im Spiel); Name: Buchstaben, Ziffern, `-`, `_`, auf dem Server eindeutig | `bs.city.manage` |
| `/city redefine <City> [bottom <Y>] [top <Y>\|selection]` | Fläche durch die WorldEdit-Auswahl ersetzen; alles andere bleibt (nur im Spiel; ein Landepunkt außerhalb der neuen Fläche wird entfernt) | `bs.city.manage` |
| `/city setspawn <City> [none]` | Landepunkt für `/city teleport` an der eigenen Position (nur im Spiel); `none` entfernt ihn | `bs.city.manage` |
| `/city remove <City>` | Stadt löschen (mit Sicherheitsabfrage); ihre Grundstücke bleiben | `bs.city.manage` |
| `/city price <City> <Betrag\|none>` | Preis der Grundstücke in der Stadt; `none` = Standardpreis | `bs.city.manage` |
| `/city greeting <City> <Text\|default\|off>` | Meldung beim Betreten | `bs.city.manage` |
| `/city farewell <City> <Text\|default\|off>` | Meldung beim Verlassen | `bs.city.manage` |
| `/city trust <City> <Name>`, `/city untrust <City> <Name>` | Wer in der Stadt außerhalb der Grundstücke bauen darf | `bs.city.trust` |

Alles außer `tp`, `create`, `redefine`, `setspawn` (ohne `none`) und `info` ohne Name geht auch aus der
Konsole.

**Flags einer Stadt gelten auch auf ihren Grundstücken**, solange ein Grundstück die Flag nicht selbst setzt
(WorldGuard nimmt den Wert der höchsten Priorität, die sie setzt). Deshalb ignoriert `cities.default-flags`
Schutz-Flags wie `use`, `interact` oder `chest-access`: `allow` würde Türen und Kisten auf allen Grundstücken
für Fremde öffnen, `deny` die Besitzenden aussperren. Auch per `/rg flag` nicht an einer Stadt setzen. Folge:
Türen und Knöpfe auf der Stadtfläche können nur Personen mit Baurecht in der Stadt benutzen – öffentliche
Türen bei Bedarf auf ein eigenes Grundstück legen.

### Bestehende Stadtregion übernehmen

1. `/rg select <alte Region>` – WorldGuard setzt die WorldEdit-Auswahl auf die Region.
2. `/city create <Name>` – legt die Stadt mit derselben Fläche an.
3. Baurechte mit `/city trust` vergeben, dann die alte Region löschen: `/rg remove <alte Region>`.

Von Hand gesetzte Flags der alten Region werden nicht übernommen; bei Bedarf mit `/rg flag city_<name> …` neu
setzen.

## Team-Befehle

`[ID]` weglassen = das Grundstück, auf dem man steht. `[World]` ist im Spiel optional (Standard: eigene
Welt) und **aus der Server-Konsole Pflicht**, z.B. `plot assign plot_3 Alex world`.

| Befehl | Beschreibung | Recht |
|---|---|---|
| `/plot create [bottom <Y>] [top <Y>\|selection]` | Grundstück aus der WorldEdit-Auswahl anlegen, optional mit Höhengrenzen oder der Höhe der Auswahl (nur im Spiel) | `bs.plot.create` |
| `/plot remove <ID> [World] [reset]` | Grundstück löschen; Kaufpreis wird erstattet | `bs.plot.remove` |
| `/plot assign <ID> <Name> [World]` | Freies Grundstück kostenlos zuweisen (auch an Personen, die offline sind) | `bs.plot.assign` |
| `/plot unassign <ID> [World] [reset]` | Beliebiges Grundstück freigeben; Kaufpreis wird erstattet | `bs.plot.unassign` |
| `/plot reset [ID] [World]` | Auf die Sicherung zurücksetzen, Besitz und Rollen bleiben | `bs.plot.reset` |
| `/plot snapshot <ID> [World]` | Aktuellen Zustand als Sicherung zum Zurücksetzen speichern | `bs.plot.snapshot` |
| `/plot teleport <ID> [World]`, `/plot tp …` | Zu einem Grundstück teleportieren – möglichst mittig (Abstand zum Rand, nahe dem Flächenschwerpunkt) und nie auf Lava, Feuer o.Ä. (nur im Spiel) | `bs.plot.tp` |
| `/plot list <Name>` | Grundstücke einer anderen Person | `bs.plot.list.others` |
| `/plot price <ID> <Betrag\|none> [World]` | Eigener Preis eines Grundstücks, geht vor dem Preis der Stadt; `none` = wieder Stadt- bzw. Standardpreis | `bs.plot.price` |
| `/plot plots [all\|free\|taken] [World\|*] [Seite]` | Alle Grundstücke einer Welt oder aller Welten (`*`, Standard) mit Status, Besitzenden und Größe; 10 pro Seite, « zurück / weiter » sind anklickbar; ein Klick auf eine Zeile schreibt `/plot tp <ID> <World>` in den Chat | `bs.plot.list.all` |
| `/plot trust`, `/plot untrust` auf fremden Grundstücken | Rollen verwalten wie Besitzende | `bs.plot.trust.others` |
| – | Wird von Grundstücks-Rollen nicht eingeschränkt | `bs.plot.bypass` |
| `/announce <Nachricht>` | Auffällige Server-Nachricht an alle (Chat, Einblendung, Ton) | `bs.announce` |

**Zurücksetzen:** `reset` stellt den Zustand beim Anlegen (oder beim letzten `/plot snapshot`) wieder her –
exakt in der Grundstücksform, Nachbargrundstücke bleiben unberührt; Tiere, Rahmen und Rüstungsständer auf
dem Grundstück werden entfernt. Unwiderrufliche Aktionen fragen nach: denselben Befehl innerhalb von 30
Sekunden wiederholen. Ohne Sicherung (Grundstücke von vor Version 1.0.0) wird `reset` abgelehnt und nichts
geändert. Zurücksetzen läuft im Hauptthread – bei großen Grundstücken kann der Server kurz stocken.

**Aus der Konsole** gehen `info`, `trust`, `untrust`, `remove`, `assign`, `unassign`, `reset`, `snapshot`,
`price`, `list <Name>`, `plots`, `free`, `/city` (siehe [Städte](#städte)) und `/announce`.

**Erstattungen:** Beim Freigeben oder Löschen geht der Kaufpreis über das Economy-Plugin an die Person, die
bezahlt hat. Kann das nicht gebucht werden (z.B. Economy-Plugin fehlt), wird das Grundstück trotzdem
freigegeben; im Log steht dann ein Fehler „Refund failed: …“ mit Name, UUID, Betrag und dem Hinweis, von
Hand zu erstatten (z.B. `/eco give <Name> <Betrag>`).

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
| `bs.plot.free` | alle | `/plot free [World\|*] [Seite]` und `/city free` – Liste der freien Grundstücke |
| `bs.city` | alle | `/city`, `/city list`, `/city info` |
| `bs.city.tp` | alle | `/city teleport` |
| alle übrigen `bs.*` | Op | siehe Team-Befehle |

`/blockstead` (Version, Lizenz, Quellcode) braucht kein Recht.

## Sprachen

* **`language: auto`** (Standard): Jede Person bekommt die Sprache ihres Minecraft-Clients – auch
  Bedrock-Spielende über Geyser. Ändert jemand die Sprache im Client, gilt sie ab der nächsten Meldung.
* **`language: de`** (oder ein anderer Code): Diese Sprache für alle.
* **`fallback-language`** (Standard `en`): für die Konsole und für Client-Sprachen ohne eigene Datei.
* Mitgeliefert sind `en` und `de`. Eigene Texte: eine Datei `plugins/Blockstead/lang/<Code>.yml` anlegen. Sie
  muss nur die Schlüssel enthalten, die geändert werden sollen – alles andere kommt aus der mitgelieferten
  Datei (so erscheinen auch verbesserte Texte aus Updates). Eine neue Datei, z.B. `fr.yml` als Kopie von
  [`en.yml`](../src/main/resources/lang/en.yml), ergänzt eine neue Sprache. Änderungen gelten nach einem Neustart.
* Die **Befehle** bleiben überall englisch (`/plot claim` …), nur Hilfe und Meldungen sind übersetzt. Wer
  eigene Kurzbefehle möchte, legt sie mit Paper-Bordmitteln in der `commands.yml` im Server-Hauptordner an:
  ```yaml
  aliases:
    grundstueck:
    - plot $1-
    gs-kaufen:
    - plot claim $1-
  ```
  Solche Aliase haben keine Tab-Vervollständigung für die Unterbefehle.

## Konfiguration

`plugins/Blockstead/config.yml` – jeder Eintrag ist in der Datei selbst erklärt (englisch); Änderungen gelten
nach einem Neustart.

| Abschnitt | Inhalt |
|---|---|
| `language`, `fallback-language` | Sprache der Meldungen, siehe [Sprachen](#sprachen) |
| `display` | LuckPerms-Präfixe an/aus, Vorlagen für Chat und Tab-Liste (`{prefix}`, `{name}`, `{suffix}`, `{message}`), Sortierung der Tab-Liste nach Gruppengewicht, Nametag |
| `join-info` | Login-Nachricht (z.B. Teamspeak, Discord); MiniMessage-Formatierung (ohne `run_command`), `http(s)://`-Adressen werden anklickbar. Für Teamspeak einen Einladungslink wie `https://invite.teamspeak.com/<Adresse>` verwenden – Minecraft öffnet nur http(s)-Links |
| `announce` | Präfix, Einblendung und Ton von `/announce` |
| `backups` | Sicherung beim Start an/aus, Anzahl der aufbewahrten Sicherungen |
| `economy` | `import-old-balances`: alte Kontostände aus 1.x einmalig übertragen (Währungsname und Startguthaben stellt man im Economy-Plugin ein) |
| `plots` | Preis (ganze Zahl in der Währung des Economy-Plugins), Wartezeit für `unclaim reset`, WorldGuard-Priorität, Standard-Flags neuer Grundstücke |
| `cities` | WorldGuard-Priorität (niedriger als `plots.priority`) und Standard-Flags neuer Städte |
| `roles` | Rollen unterhalb von „owner“: Rechte (`build`, `containers`, `doors`, `workstations`, `entities`, `home`), optional `aliases` (weitere Schlüssel) und `name` (fester Anzeigename statt Übersetzung) |

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
stehen nur noch als Nachweis in der Tabelle `accounts` (mit Zeitpunkt der Übertragung). Besitzende und
Mitglieder stehen in WorldGuard; die Sicherungen zum Zurücksetzen liegen als WorldEdit-Schematic in
`plugins/Blockstead/snapshots/<Welt>/<ID>.schem`.

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
