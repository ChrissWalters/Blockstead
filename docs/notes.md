# Blockstead – Project notes

Open points, findings and design decisions from development. Adds the "why" to
[development.md](development.md) – and the traps you would otherwise fall into a second time.

## Open points

* **City areas from Blockstead:** cities should be a concept of Blockstead itself instead of a WorldGuard
  region maintained by hand – showing the city in `/plot info`, a filter in `/plot list` and prices per city.
  Plots must keep working without a city, too. Mind the priority rules below (city region lower than plots,
  not as a parent region).
* **No reload command:** changes to `config.yml` and the language files need a restart.
* **Planned versions:** 2.3.0 city areas (in its own branch) – then `/plot plots` and `/plot free` also show
  the city and get a city filter.
* **Restoring runs synchronously** on the main thread (WorldEdit without asynchronous processing); with very
  large plots the server stalls briefly. The waiting time for `unclaim reset` limits abuse.

## Findings and pitfalls

**WorldGuard**
* Protection flags (`use`, `interact`, `chest-access`, `build`, …) apply to the group *all* unless set with
  `-g` – a `deny` therefore locks out owners and members, too. Ops don't notice because WorldGuard allows them
  everything. WorldGuard keeps strangers out through membership anyway; these flags don't belong in the
  default flags of plots (`PluginConfig` ignores them, `LegacyFlagFix` removed them once from old plots).
* Custom flags (here `region-name`) must be registered in `onLoad()`, before WorldGuard starts.
* Overlapping regions: only the highest priority counts. With the same priority you have to be a member of
  **all** regions to build, and `deny` beats `allow`. That's why plots must never overlap and regions like
  cities need a lower priority than the plots.
* Parent regions (`/rg setparent`) pass flags **and** members/owners on to their children.
* Region IDs may contain `/` – clean them up before using them in file names.
* The bounding box of a polygon plot reaches into neighbouring plots; when pasting a snapshot, always limit it
  to the real shape with a `RegionMask`.

**Paper**
* `api-version` in plugin.yml must be `major.minor` (`'26.3'`); `'26'` is rejected. It is the **minimum**
  version – compile and test against the newest Paper version and keep `api-version` at the oldest supported
  one as long as no newer API is used.
* Paper 26.3: `Entity#isInvulnerable()` no longer reflects `setInvulnerable()` – use `isMarkedInvulnerable()`
  instead (that's why Blockstead 1.0.1 needed Paper 26.3).
* `ArgumentTypes.world()` expects dimension keys (`minecraft:overworld`) and rejects the world name `world`
  shown everywhere else – hence an own world argument by name.
* `Material#isInteractable` / `BlockType#isInteractable` are deprecated and inaccurate (e.g. stairs count) –
  use fixed lists instead.
* Brigadier commands: don't confuse `com.mojang.brigadier.Command` with `org.bukkit.command.Command`.
* `StringArgumentType.word()` only accepts letters, digits and `_-.+` – a `*` ("all worlds") must be its own
  literal next to the argument (Brigadier prefers a matching literal).
* Clickable page links (« back / next ») use `ClickEvent.runCommand` set in code with a fixed own command – the
  removal of `run_command` only applies to texts from language files and the config.
  Permissions via `.requires(...)` hide commands without permission completely.
* A permission with `default: false` really has nobody – not even ops and the console; Paper then shows
  "Unknown command".
* Teams of the main scoreboard are saved with the world (`world/data/minecraft/scoreboard.dat`), also on
  autosave. After a crash they remain – that's why `DisplayService` removes its `bs_…` teams on startup and
  shutdown.
* MiniMessage allows `<click:run_command:…>`: whoever clicks runs the command with their own permissions.
  `TextFormat` removes this click action; links and `suggest_command` remain.
* `Player#locale()` is the client language and also works for Bedrock players through Geyser; it can change
  during a session, so the language is determined per message, not once at join.
* Join/quit messages for everyone are one component – for per-person languages, Blockstead sets them to
  `null` and sends a message to every person (and the console) itself.
* Bukkit's `YamlConfiguration` reads flat keys with dots (`plot.claim.success`) as nested sections –
  `getKeys(true)` + `isString` give the flat list again.
* The message `HorriblePlayerLoginEventHack` comes from LuckPerms/WorldGuard and is harmless.

**Economy (Vault/EssentialsX)**
* EssentialsX registers its Vault provider already in `onLoad()`, but it's only usable after its `onEnable()`.
  That's why the status check and the transfer of old balances run in the first tick after startup, and
  `isAvailable()` also checks the provider's `isEnabled()`. Fetch the provider on every access, don't cache it.
* Vault has no "set balance" – deposit or withdraw the difference instead.
* Vault calculates with `double`, Blockstead with whole numbers; `getBalance` may return decimals.
* People who are offline may not have an account in the economy plugin yet: call
  `hasAccount`/`createPlayerAccount` before depositing.
* EssentialsX creates accounts that are created through Vault for people who are offline as **NPCs** if the
  UUID has version 2 or 3 (offline-mode servers, NPC plugins). NPC accounts don't show up in `/baltop`. Real
  accounts in online mode have version 4 and aren't affected.
* Accounts created through Vault have no name in EssentialsX until the person's next join – `/baltop` shows
  "null" until then.
* EssentialsX `currency-symbol` allows only **one** character; longer values (e.g. "Coins") silently become `$`.
* EssentialsX supports Paper 26.3 only from the dev build 2.22.1-SNAPSHOT on; the release 2.22.0 ends at
  26.1.2.
* The VaultAPI is only available through JitPack; without `transitive = false` it pulls in an old Bukkit API.
* `Player#setInvulnerable` (the former command `/invincible`) is stored in the player data and remains even
  when the plugin that set it is gone.

**Dependencies**
* WorldEdit beta versions aren't in the Maven repository – compile against the last release then. On Modrinth
  the version number is shared with the Fabric build, which is why `runServer` downloads the Bukkit jar by URL.
* LuckPerms is optional: classes with LuckPerms types (`display` package) may only be loaded if LuckPerms is
  installed, otherwise Java fails with `NoClassDefFoundError`.
* The SQLite driver comes with Paper.

**Running a server**
* Enter LuckPerms commands one at a time: when several lines are pasted at once, LuckPerms reads the rest as
  context conditions (`server=…`) – the prefix then applies nowhere.
* Don't build while the local test server is running – that replaces the jar underneath the running server.

## Design decisions

* **Roles only restrict:** every role below "owner" is a WorldGuard member; WorldGuard keeps strangers out,
  Blockstead additionally forbids members what their role doesn't allow. Allowing something WorldGuard has
  forbidden would be error-prone.
* **WorldGuard is the source for ownership and membership;** the database only stores what WorldGuard doesn't
  know (purchases for refunds, roles, homes). Since 2.0.0 the economy plugin keeps the balances.
* **One snapshot per plot** for restoring, made at creation; there are no older states.
* **No general commands, no own currency** (since 2.0.0): Blockstead takes care of plots. `/spawn`, `/god`,
  `/suicide`, balances etc. come from EssentialsX or an economy plugin through Vault. Exceptions:
  `/announce` (staff message with a title), the join message and the LuckPerms prefixes.
* **Languages** (since 2.1.0): texts in language files `lang/<code>.yml`, per person from the client language
  or fixed per server; bundled files are not copied to the plugin folder, own files only override single
  keys. Log messages and `config.yml` comments are English.
* **Commands stay English** everywhere; only help and messages are translated. Localized command names would
  make help between players and guides inconsistent and let translations break commands – servers that want
  shortcuts use Paper's `commands.yml` aliases.
* **Role keys** are English (`owner`, `resident`, `builder`, `guest`); the German keys of earlier versions
  stay valid as aliases, so stored data and old configs keep working (minor release).
* **Versioning according to Semantic Versioning 2.0.0** (semver.org) – what counts as major is described in
  [development.md](development.md#versioning).
* **Permission prefix `bs.*`** (**B**lock**s**tead) – unchanged since the predecessor "Buildserver".
* **Licence AGPL-3.0-or-later;** issues welcome, pull requests only for translations (files in
  `src/main/resources/lang/`).
