# Blockstead – Development

Pull requests are only accepted for translations (see the [README](../README.md#issues-yes-pull-requests-only-for-translations));
this page is for everyone working on their own copy. Open points, findings and pitfalls: [notes.md](notes.md).

## Setup (VSCodium / VS Code)

1. Install JDK 25 (from your system's package manager or https://adoptium.net). Without a local JDK, Gradle
   downloads one itself when building, but the Java extension in the editor needs an installed one.
2. Open the project folder and install the suggested extensions (`.vscode/extensions.json`): Language Support
   for Java, Debugger for Java, Gradle for Java, YAML.

## Building

```sh
./gradlew build
```

Result: `build/libs/blockstead-<version>.jar`. All dependencies (Paper API, WorldEdit, WorldGuard, LuckPerms
API, VaultAPI) are `compileOnly` – the server provides them, the jar contains only Blockstead itself. The
VaultAPI comes from JitPack (`com.github.MilkBowl:VaultAPI`) with `transitive = false`, because it would
otherwise pull in an old Bukkit API that clashes with the Paper API.

`./gradlew build` also runs `checkLanguageFiles`: every file in `src/main/resources/lang/` must have the same
keys and placeholders as `en.yml`.

## Local test server

```sh
./gradlew runServer
```

Starts a Paper 26.3 server in the `run/` folder with the plugin plus WorldEdit, WorldGuard, LuckPerms and
Vault. EssentialsX for 26.3 only exists as a dev build without a fixed download address
(https://essentialsx.net/downloads.html?branch=dev) – put `EssentialsX-*.jar` (and `EssentialsXSpawn-*.jar`
for `/spawn`) into `run/plugins/` by hand if needed. On first start, set `eula=true` in `run/eula.txt` and
start again. Connect to `localhost`.

Don't build while the test server is running – that replaces the jar underneath the running server.

**Debugging:** start `./gradlew runServer --debug-jvm` (it waits for the debugger), then choose
**"Attach to test server"** under "Run and Debug" in VSCodium. Breakpoints in the code then stop.

## Release

The GitHub Action builds on every push and pull request and attaches the jar as an artifact. A tag `v*`
(e.g. `git tag v2.1.0 && git push origin v2.1.0`) creates a GitHub release with the jar; the release text is
the section `## <version>` from [CHANGELOG.md](../CHANGELOG.md) (English) followed by the same section from
[CHANGELOG.de.md](../CHANGELOG.de.md) (German). Before that, set the version in `build.gradle` and add the
entry to both changelogs. New features and planned versions are developed in their own branch (e.g.
`release/2.1.0`) and merged into `main` after testing.

Pull requests are checked by a separate job: they may only change files in `src/main/resources/lang/`.

### Versioning

[Semantic Versioning 2.0.0](https://semver.org), scheme `MAJOR.MINOR.PATCH`:

* **Major:** changes to the public interface that are not backwards compatible. These include commands and
  their syntax, permissions (`bs.*`), `config.yml` entries, the data format (`data.db` – even if only the way
  back to the previous version no longer works) and required dependencies.
* **Minor:** new features, backwards compatible.
* **Patch:** bug fixes only.
* Pre-releases with a hyphen, e.g. `2.0.0-rc.1`.

## Project structure

```
src/main/java/com/github/chrisswalters/blockstead/
├── Blockstead.java    Main class: startup, dependencies, registering commands and listeners
├── cities/            Cities: WorldGuard regions with protection, price, greeting
├── commands/          Commands (Paper Brigadier)
├── config/            Reading config.yml
├── display/           LuckPerms prefixes in chat, tab list and nametag (only loaded with LuckPerms)
├── economy/           Server currency through Vault, transfer of old balances
├── lang/              Language files and translated messages (Messages)
├── listeners/         Event listeners (join, quit, movement, protection of roles)
├── players/           Runtime data per person who is online
├── plots/             Creating, buying, assigning, releasing, deleting, restoring plots
├── roles/             Roles and rights on plots
├── storage/           SQLite database, schema migrations, backups
└── util/              Helpers (WorldGuard, own flags, selections, names, text format, versions)
src/main/resources/
├── config.yml         Template of the configuration
├── lang/              Bundled language files (en.yml, de.yml)
└── plugin.yml
```

## Conventions

* **Language:** Javadoc and code comments in German (with real umlauts, UTF-8); log messages, `config.yml`
  comments and this documentation (except README/admin, which also exist in German) in English. Texts for
  players and staff only in language files, never hard-coded – send them with `Messages.send(...)`.
* **Gender-fair German:** neutral forms ("Spielende", "Besitzende", "Person"), otherwise the gender star
  ("Spieler*innen") – never the generic masculine.
* **Javadoc** for every class and every non-trivial method.
* **Placeholders** in language files are tags like `<id>`; their values are passed with `Messages.arg(...)` and
  are never parsed as formatting.
* **Database:** schema changes only as a new block in `Database.migrate()`, raising
  `Database.SCHEMA_VERSION`; never change old blocks.
* **LuckPerms types** only in the `display` package – LuckPerms is optional.
* **Vault types** only in `economy/VaultEconomy` – Vault is optional; `EconomyService` checks with
  `Class.forName` whether Vault is present.
* **Formatted text** (`TextFormat`) only from trusted sources (language files, config.yml, staff commands),
  never from players' chat.
* **Commands** stay English; argument names (`<world>`, `<role>`) too.
