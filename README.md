# Blockstead

**Dein eigenes Grundstück in der Stadt.** Blockstead ist ein CityBuild-Plugin für Minecraft-Server (Paper):
Grundstücke kaufen, andere mit eigenen Rollen einladen, ein Zuhause setzen – und das Gebaute bleibt
geschützt.

Diese Seite erklärt das Plugin für **Spielende**. Für Serverbetreibende und das Team:
[docs/admin.md](docs/admin.md). Für die Entwicklung: [docs/development.md](docs/development.md).
Was sich zwischen den Versionen geändert hat: [CHANGELOG.md](CHANGELOG.md).

## So funktioniert's

Das Server-Team legt Grundstücke an – vom tiefsten bis zum höchsten Punkt der Welt. Freie Grundstücke
kannst du mit der Server-Währung kaufen; zum Start hast du in der Regel genug Guthaben für **ein
Grundstück**. Dein Guthaben zeigt der Befehl deines Servers, meist `/balance`. Weitere Grundstücke vergibt
das Team.

Auf deinem Grundstück darf niemand etwas anfassen, dem du keine Rolle gegeben hast – auch keine Türen
öffnen. Beim Betreten und Verlassen eines Grundstücks siehst du, wo du bist.

## Befehle

`[ID]` kannst du weglassen – dann gilt das Grundstück, auf dem du gerade stehst. Grundstücks-IDs sehen so
aus: `plot_3`. Alle Befehle haben Tab-Vervollständigung.

| Befehl | Was er tut |
|---|---|
| `/plot` | Übersicht aller Befehle, die du nutzen darfst |
| `/plot info [ID]` | Wem gehört das Grundstück, wer hat welche Rolle, ist es frei? |
| `/plot claim [ID]` | Freies Grundstück kaufen |
| `/plot list` | Deine Grundstücke |
| `/plot home` | Zu deinem Zuhause teleportieren |
| `/plot home set` | Dein Zuhause hier setzen (auf deinem Grundstück oder mit passender Rolle) |
| `/plot trust <Name> <Rolle> [ID]` | Jemandem eine Rolle auf deinem Grundstück geben |
| `/plot untrust <Name> [ID]` | Rolle wieder wegnehmen |
| `/plot unclaim [ID]` | Grundstück zurückgeben – du bekommst den Kaufpreis zurück, alles Gebaute bleibt stehen |
| `/plot unclaim [ID] reset` | Zurückgeben **und** auf den Ursprungszustand zurücksetzen (mit Sicherheitsabfrage) |
| `/blockstead` | Version, Lizenz und Quellcode des Plugins |

Was davon du nutzen darfst, legt der Server fest – Befehle ohne Berechtigung werden dir gar nicht erst
angezeigt.

## Rollen

Mit `/plot trust <Name> <Rolle>` lädst du andere auf dein Grundstück ein. So sind die Rollen
voreingestellt – dein Server kann sie anpassen, `/plot info` zeigt, wer welche Rolle hat:

| | Besitzende | Mitbewohnende | Bauhilfe | Gast |
|---|:-:|:-:|:-:|:-:|
| Bauen und Abbauen | ✔ | ✔ | ✔ | |
| Truhen und andere Behälter | ✔ | ✔ | | |
| Türen, Tore, Knöpfe, Hebel | ✔ | ✔ | ✔ | ✔ |
| Werkbank, Öfen, Amboss, Betten | ✔ | ✔ | ✔ | |
| Tiere, Rüstungsständer, Rahmen | ✔ | ✔ | | |
| Zuhause setzen | ✔ | ✔ | | |
| Rollen vergeben, Grundstück zurückgeben | ✔ | | | |

Die Rollennamen im Befehl: `besitzende`, `mitbewohnende`, `bauhilfe`, `gast`.

Gut zu wissen:

* **Besitzende sind gleichberechtigt.** Wer `besitzende` wird, kann alles, was du kannst – auch dir Rechte
  entziehen oder das Grundstück zurückgeben. Vergib diese Rolle nur an Menschen, denen du vertraust. Die
  letzte besitzende Person kann nicht entfernt werden.
* **Zurückgeben:** Den Kaufpreis bekommt immer die Person zurück, die das Grundstück gekauft hat. Alle
  Rollen und Zuhause auf dem Grundstück werden dabei entfernt. Zurücksetzen (`reset`) geht nur alle paar
  Minuten.
* **Zuhause:** Verlierst du die Rechte auf einem Grundstück, gilt dein Zuhause dort nicht mehr.

## Issues ja, Pull Requests nein

**Fehlermeldungen, Fragen und Ideen sind sehr willkommen – bitte als
[Issue](https://github.com/ChrissWalters/Blockstead/issues).** Das ist der eine Kanal, und er wird
gelesen: Woran Leute hängenbleiben, wird behoben.

**Pull Requests werden nicht angenommen**, und das gehört vorher gesagt, nicht hinterher. Dies ist ein
Ein-Personen-Projekt; fremden Code so gründlich zu prüfen, dass ich für das geradestehen kann, was auf
anderen Servern läuft, ist ein Zeitaufwand, den ich derzeit nicht aufbringen kann. Eine fertige Arbeit
abzulehnen wäre unhöflicher als dieser Absatz.

Was du mit der Software machen darfst, schränkt das nicht ein. Sie steht unter der AGPL-3.0: abspalten,
ändern, die geänderte Fassung betreiben, weitergeben. Die Lizenz verlangt nur, dass deine Fassung unter
denselben Bedingungen bleibt und die Vermerke erhalten bleiben.

Wenn du in deiner Abspaltung etwas repariert hast, ist ein Issue mit Beschreibung – oder mit Link auf
deinen Commit – wirklich hilfreich. Es nimmt nur den Weg über mich statt über einen Merge-Knopf.

Sicherheitsprobleme bitte nicht als öffentliches Issue melden, sondern wie in [SECURITY.md](SECURITY.md)
beschrieben.

## Projekt unterstützen

Blockstead ist kostenlos und bleibt es: keine Bezahlversion, kein Lizenzschlüssel, keine zurückgehaltenen
Funktionen. Wenn dir das Plugin gefällt und du Danke sagen möchtest:

* [Ko-fi](https://ko-fi.com/chrisswalters)

Völlig freiwillig, und nichts an der Software hängt davon ab.

## Entwicklung

KI-Werkzeuge wurden und werden zur Unterstützung der Entwicklung von Blockstead eingesetzt.

## Lizenz

Copyright © 2026 ChrissWalters

[GNU Affero General Public License v3.0 oder später](LICENSE). Bereitgestellt **ohne jede Gewährleistung**.

Wer eine veränderte Fassung auf einem Server betreibt, muss den Spielenden den Quellcode anbieten – am
einfachsten, indem `website` in der `plugin.yml` auf das eigene Repository zeigt; `/blockstead` zeigt den
Link an.
