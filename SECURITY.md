# Sicherheitsrichtlinie

Blockstead läuft auf Minecraft-Servern und verwaltet Grundstücke und Rechte und bucht Käufe über die
Server-Währung. Fehler
dort können Bauwerke ungeschützt lassen oder Spielenden Rechte geben, die sie nicht haben sollten.

## Eine Schwachstelle melden

Sicherheitsprobleme bitte **zuerst vertraulich** melden, nicht als öffentliches Issue:

* GitHub → *Security* → *Report a vulnerability* (privater Hinweis), oder
* per E-Mail an die Adresse im Profil des Repositorys.

Hilfreich sind: was du getan hast, was passiert ist, was du erwartet hast, und die Version (`/blockstead`
zeigt sie an). Ein nachvollziehbares Beispiel hilft, ein fertiger Exploit ist nicht nötig.

Eine erste Antwort kommt innerhalb von **sieben Tagen**. Behobene Probleme werden in den Versionshinweisen
genannt – und, wenn du nichts dagegen hast, du mit ihnen.

Es gibt kein Bounty-Programm; dies ist ein freiwilliges Projekt unter der AGPL-3.0.

## Was dazugehört

Das Plugin selbst: Schutz der Grundstücke und Rollen, Rechteprüfung der Befehle, Buchungen beim Kaufen und Erstatten,
Datenbank und Sicherungen, die Verarbeitung von Chat- und Konfigurationstexten.

Nicht dazu gehören: Probleme, die einen bereits kompromittierten Server voraussetzen, Fehler in Paper,
WorldEdit, WorldGuard oder LuckPerms selbst (bitte dort melden) sowie Rechte, die das Server-Team bewusst
vergeben hat.

## Wie sich das Plugin schützt

| Bereich | Maßnahme |
|---|---|
| Grundstücke | WorldGuard sperrt Fremde aus; Rollen schränken Mitglieder zusätzlich ein, nie umgekehrt |
| Befehle | Jeder Unterbefehl prüft sein Recht; ohne Recht wird er gar nicht angezeigt |
| Chat | Nachrichten von Spielenden werden nie als Formatierung gelesen (keine Farb-, Klick- oder Hover-Codes); in Team-Texten (`/announce`, `join-info`) sind Klicks, die Befehle ausführen, gesperrt |
| Datenbank | Ausschließlich vorbereitete SQL-Statements |
| Währung | Abbuchung vor der Vergabe des Grundstücks; Buchungen über das Economy-Plugin, fehlgeschlagene Erstattungen im Log |
| Dateien | Namen von Sicherungsdateien werden auf sichere Zeichen reduziert |
| Serverlast | Wartezeit zwischen zwei Zurücksetzungen durch Spielende |
| Build | Die Pipeline läuft mit Leserecht; nur der Release-Job bei Versions-Tags darf schreiben |
| Updates | Sicherung von Datenbank und Regionen bei jedem Start; kein Start mit einer neueren Datenbank |
| Datenschutz | Keine Telemetrie, keine Verbindungen nach außen |
