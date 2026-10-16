# Blockstead – Projektnotizen

Offene Punkte, Erkenntnisse und Grundsatzentscheidungen aus der Entwicklung. Ergänzt
[development.md](development.md) um das „Warum“ – und um Fallen, in die man sonst ein zweites Mal tritt.

## Offene Punkte

* **v1.1.0: Sprachauswahl Deutsch/Englisch.** Alle Texte für Spielende und Team (Nachrichten, Hilfe,
  Kommentare der `config.yml`, Doku) sollen auf Deutsch oder Englisch verfügbar sein. Heute stehen die
  deutschen Texte direkt in den Befehls- und Service-Klassen; sie müssen in Sprachdateien wandern (z.B.
  `lang/de.yml`, `lang/en.yml`). Zu klären: Sprache pro Server (config.yml) oder pro Person (Sprache des
  Minecraft-Clients). Vor weiteren neuen Texten umsetzen, damit nicht noch mehr verschoben werden muss.
* **Stadtbereiche aus Blockstead:** Städte sollen als eigenes Konzept aus Blockstead kommen statt als von
  Hand gepflegte WorldGuard-Region – mit Anzeige der Stadt in `/plot info`, Filter in `/plot list` und
  Preisen pro Stadt. Grundstücke müssen weiterhin auch ohne Stadt funktionieren. Bei der Umsetzung die
  Prioritätsregeln unten beachten (Stadt-Region niedriger als Grundstücke, nicht als Eltern-Region).
* **Liste aller Grundstücke für das Team:** ein Befehl, der alle Grundstücke einer oder aller Welten mit
  Status (frei/vergeben), Besitzenden und ggf. Stadt auflistet – mit Seiten bei vielen Einträgen.
* **Kein Reload-Befehl:** Änderungen an der `config.yml` brauchen einen Neustart.
* **Zurücksetzen läuft synchron** im Hauptthread (WorldEdit ohne asynchrone Verarbeitung); bei sehr großen
  Grundstücken stockt der Server kurz. Die Wartezeit für `unclaim reset` begrenzt den Missbrauch.

## Erkenntnisse und Stolperfallen

**WorldGuard**
* Schutz-Flags (`use`, `interact`, `chest-access`, `build`, …) gelten ohne `-g` für die Gruppe *alle* –
  ein `deny` sperrt also auch Besitzende und Mitglieder aus. Ops merken das nicht, weil WorldGuard ihnen
  alles erlaubt. Fremde sperrt WorldGuard über die Mitgliedschaft ohnehin aus; diese Flags gehören nicht
  in die Standard-Flags von Grundstücken (`PluginConfig` ignoriert sie, `LegacyFlagFix` hat sie einmalig
  von alten Grundstücken entfernt).
* Eigene Flags (hier `region-name`) müssen in `onLoad()` registriert werden, bevor WorldGuard startet.
* Überlappende Regionen: Nur die höchste Priorität zählt. Bei gleicher Priorität muss man in **allen**
  Regionen Mitglied sein, um zu bauen, und `deny` schlägt `allow`. Darum dürfen sich Grundstücke nie
  überlappen, und Regionen wie Städte brauchen eine niedrigere Priorität als die Grundstücke.
* Eltern-Regionen (`/rg setparent`) vererben Flags **und** Mitglieder/Besitzende an ihre Kinder.
* Region-IDs dürfen auch `/` enthalten – vor der Verwendung in Dateinamen bereinigen.
* Das umgebende Rechteck eines Polygon-Grundstücks ragt in Nachbargrundstücke; beim Einfügen einer
  Sicherung deshalb immer mit `RegionMask` auf die echte Form begrenzen.

**Paper**
* `api-version` in der plugin.yml muss `major.minor` sein (`'26.2'`); `'26'` wird abgelehnt. Sie ist die
  **Mindestversion** – kompiliert und getestet wird gegen die neueste Paper-Version, `api-version` bleibt
  auf der ältesten unterstützten, solange keine neuere API genutzt wird.
* Paper 26.3: `Entity#isInvulnerable()` spiegelt `setInvulnerable()` nicht mehr wider – stattdessen
  `isMarkedInvulnerable()` (deshalb braucht Blockstead ab 1.0.1 Paper 26.3).
* `ArgumentTypes.world()` erwartet Dimensionsschlüssel (`minecraft:overworld`) und lehnt den Weltnamen
  `world` ab, der überall sonst angezeigt wird – daher ein eigenes Welt-Argument per Name.
* `Material#isInteractable` / `BlockType#isInteractable` sind veraltet und ungenau (z.B. zählen Treppen
  dazu) – stattdessen feste Listen.
* Befehle über Brigadier: `com.mojang.brigadier.Command` nicht mit `org.bukkit.command.Command`
  verwechseln. Rechte per `.requires(...)` blenden Befehle ohne Recht komplett aus.
* Ein Recht mit `default: false` hat wirklich niemand – auch nicht Ops und die Konsole; Paper zeigt dann
  „Unknown command“.
* Die Meldung `HorriblePlayerLoginEventHack` kommt von LuckPerms/WorldGuard und ist harmlos.

**Abhängigkeiten**
* WorldEdit-Betaversionen gibt es nicht im Maven-Repository – dann gegen die letzte Release-Version kompilieren. Auf Modrinth
  teilt sich die Versionsnummer mit dem Fabric-Build, darum lädt `runServer` die Bukkit-Jar per URL.
* LuckPerms ist optional: Klassen mit LuckPerms-Typen (Paket `display`) dürfen nur geladen werden, wenn
  LuckPerms installiert ist, sonst bricht Java mit `NoClassDefFoundError` ab.
* Der SQLite-Treiber wird von Paper mitgeliefert.

**Betrieb**
* LuckPerms-Befehle einzeln eingeben: Beim Einfügen mehrerer Zeilen auf einmal liest LuckPerms den Rest als
  Kontext-Bedingungen (`server=…`) – der Präfix gilt dann nirgends.
* Nicht bauen, während der lokale Testserver läuft – das ersetzt die Jar unter dem laufenden Server.

## Grundsatzentscheidungen

* **Rollen schränken nur ein:** Jede Rolle unterhalb von „besitzende“ ist WorldGuard-Mitglied; WorldGuard
  hält Fremde fern, Blockstead verbietet Mitgliedern zusätzlich, was ihre Rolle nicht darf. Etwas zu
  erlauben, das WorldGuard verboten hat, wäre fehleranfällig.
* **WorldGuard ist die Quelle für Besitz und Mitgliedschaft;** die Datenbank speichert nur, was WorldGuard
  nicht kennt (Kontostände, Käufe, Rollen, Zuhause).
* **Eine Sicherung pro Grundstück** zum Zurücksetzen, angelegt beim Anlegen; ältere Stände gibt es nicht.
* **Rechte-Präfix `bs.*`** (**B**lock**s**tead) – unverändert seit dem Vorgänger „Buildserver“.
* **Lizenz AGPL-3.0-or-later;** Issues willkommen, Pull Requests werden nicht angenommen.
