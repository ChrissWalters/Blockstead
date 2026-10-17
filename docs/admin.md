# Blockstead – Running it and staff

*Auf Deutsch: [admin.de.md](admin.de.md)*

This page is for server owners and the server team. What players need to know is in the
[README](../README.md).

## Requirements

| Component | Version |
|---|---|
| Paper | 26.3 or newer |
| Java | 25 |
| WorldEdit | 7.4.x (tested with 7.4.6) |
| WorldGuard | 7.0.19 |
| LuckPerms (optional) | 5.5.85 or newer – for prefixes in chat, tab list and above the head |
| Vault or VaultUnlocked (optional) | Vault 1.7.3 – needed so that plots can be **bought** |
| Economy plugin (optional) | e.g. EssentialsX – keeps the balances; for Paper 26.3 currently only as a dev build (2.22.1-SNAPSHOT) |

Without Vault or an economy plugin Blockstead keeps running: `/plot claim` is then blocked (unless the price
is 0), staff can give out plots with `/plot assign`. A line in the log shortly after startup shows the status
("Server currency through Vault: …" or a warning).

**Division of work:** Blockstead only takes care of plots (plus `/announce`, the join message and LuckPerms
prefixes). General commands like `/spawn`, `/balance`, `/pay`, `/god` or `/suicide` and the currency itself
come from EssentialsX (or another plugin).

## Installation

1. Download `blockstead-<version>.jar` from a [GitHub release](https://github.com/ChrissWalters/Blockstead/releases)
   (or build it yourself, see [development.md](development.md)).
2. Copy it into the server's `plugins/` folder and restart the server.
3. On first start, `plugins/Blockstead/config.yml` is created.

## Updating

1. **Make a complete server backup first** (worlds and `plugins/`).
2. Remove the old jar from `plugins/`, put in the new one, restart the server.
3. Read the notes for the version in [CHANGELOG.md](../CHANGELOG.md) – especially new `config.yml` entries,
   which are not added automatically.

### From 2.2.x/2.3.x to 2.4.0

* New are `/city teleport` (permission `bs.city.tp`, everyone – add it if players get their permissions explicitly in
  LuckPerms), `/city setspawn` and optional heights (`bottom`, `top` or `selection`) for `/plot create`,
  `/city create` and `/city redefine`. `/plot home` now also has the one-second waiting time.
  Existing plots and cities are unchanged.

### From 2.2.x to 2.3.0

* **Cities** are new (see [Cities](#cities)). Existing plots and WorldGuard regions stay unchanged. To turn a
  city region you maintained by hand into a Blockstead city, see [Taking over an existing city region](#taking-over-an-existing-city-region).
* **`config.yml`:** new section `cities` (priority and default flags of new cities). Without it, priority 0
  and the default flags of the template apply.
* **Permissions:** new are `bs.city` (everyone), `bs.city.manage`, `bs.city.trust` and `bs.plot.price` (Op).
  If players get their permissions explicitly in LuckPerms, add `bs.city`.
* Plot data stays in WorldGuard: name, prices and texts are flags of the regions (`blockstead-name`,
  `blockstead-price`, `blockstead-greeting`, `blockstead-farewell`). The database is unchanged; going back to
  2.2.x works (the flags then simply have no effect).

### From 2.0.x to 2.1.0

* **Languages:** all texts come from language files (see [Languages](#languages)). Without any change to
  `config.yml`, every person gets the language of their Minecraft client and the console gets English. For a
  German-only server: set `language: de` and `fallback-language: de` in `config.yml`.
* **Log messages are English** (e.g. "Transfer finished: …" instead of "Übertragung abgeschlossen: …").
* **Roles:** the new default keys are `owner`, `resident`, `builder`, `guest`. An existing `config.yml`
  keeps its German keys – everything keeps working as before, `besitzende` is an alias of `owner`. To switch,
  take the `roles` section from the new template ([`src/main/resources/config.yml`](../src/main/resources/config.yml));
  its `aliases` keep stored roles with German keys valid. Without `name`, role names are shown translated.
* **`region-name`:** `/plot create` no longer sets this flag. The message when entering comes translated from
  the language file; old plots with the former default text "Du betrittst das Grundstück …" show the
  translated message, too. Custom texts stay unchanged.
* **`config.yml`:** new are `language` and `fallback-language`; the comments in the template are English.

### From 1.x to 2.0.0

Blockstead 2.0.0 hands over its own currency and the general commands:

* **Removed:** `/coins`, `/spawn`, `/die` (`/suicide`), `/invincible` and the permissions `bs.coins*`,
  `bs.spawn`, `bs.die`, `bs.invincible*`; `/announce` no longer has the alias `/broadcast`. Replacements in
  EssentialsX: `/balance`, `/pay`, `/eco`, `/baltop`, `/spawn`, `/setspawn`, `/suicide`, `/god` – give the
  permissions for them in the economy or permissions plugin (`essentials.*`).
* **Before:** turn `/invincible` off with 1.0.1 for everyone who used it. Invulnerability is stored in the
  player data and can't be switched off without the command any more (`/god` is a separate switch).
* **Balances:** on the first start with Vault and an economy plugin, Blockstead transfers every old account
  once. The balance in the economy plugin is **set** to the old value, not added. The log shows one line per
  account and finally "Transfer finished: x of N account(s) transferred." (German up to 2.0.1). Failed
  accounts are retried at the next start. With `economy.import-old-balances: false` the old balances are not
  transferred. EssentialsX only knows the names of transferred accounts once the person has been online again
  (`/baltop` shows "null" until then). On offline-mode servers EssentialsX creates the accounts as NPCs – then
  they are missing from `/baltop`.
* **Database:** schema version 4. Going back to 1.x is only possible with the backup of `data.db` from before
  the update (oldest folder of the update day under `plugins/Blockstead/backups/`) – 1.x refuses to start with
  schema 4 on purpose.
* **`config.yml`:** `economy.currency-name` and `economy.start-balance` are ignored (name, symbol and starting
  balance are set in the economy plugin). New: `economy.import-old-balances` (default `true`).

From the predecessor **"Buildserver"**: the old jar (e.g. `buildserver-0.0.1-SNAPSHOT.jar`) must be removed –
both at the same time would get in each other's way. Permissions (`bs.*`) and WorldGuard regions stay the
same; details in the [CHANGELOG](../CHANGELOG.md).

## Creating plots

Plots are WorldGuard regions with the ID `plot_<number>`:

1. Select an area with WorldEdit – a cuboid (`//wand`, two points) or a polygon (`//sel poly`). The height of
   the selection doesn't matter.
2. `/plot create` – creates the plot with the default flags from `config.yml` and saves its state (for
   restoring it later). Without further details, the plot reaches from the lowest to the highest point of the
   world.

**Height limits (optional):** `/plot create bottom <Y>`, `/plot create top <Y>` or both, in any order (e.g.
`/plot create bottom 60 top 70`; both values are included) – or `/plot create selection`: exactly the height
of your WorldEdit selection, handy for storeys. All heights are absolute Y coordinates (F3 or the message of
`//pos1` shows them). Relative to the selection: expand it with WorldEdit first, e.g. `//expand 60 up` or
`//expand 80 down`, then `/plot create selection`. Plots may then lie on top of each other – e.g.
flats in a house – as long as they don't overlap. Above and below such a plot its rules don't apply; inside a
city the city protects that space, outside of cities nothing does (`/plot create` warns). `/plot info` shows
the height only when it is limited; `/plot tp` lands inside the height (on the floor of a storey, not on the
roof).

New plots have no owners. Players buy them with `/plot claim`, staff give them out with `/plot assign`. When
entering, the translated default message is shown; you set a custom text with the WorldGuard flag
`region-name`, e.g. `/rg flag plot_3 region-name Welcome to Alex's place` (it then applies to all languages).

### Overlaps

* **Plots must not overlap.** `/plot create` refuses a selection that touches an existing plot.
* **Other regions may overlap plots** – e.g. a city ([Cities](#cities)) or a city region you maintain by hand
  with WorldGuard. They need a **lower
  priority** than the plots (default 10, `plots.priority`), simplest is 0: then the owners, roles and flags of
  the plots apply on the plots and the city region's rules on the streets. With the same or a higher priority
  the city region overrides the plots – `/plot create` then warns; change the priority with
  `/rg setpriority <ID> 0`.
* **Don't** put plots under a city region with `/rg setparent`: in WorldGuard, children inherit the members of
  the parent region – every member of the city could then build on every plot.
* At startup, the consistency check reports overlapping plots and regions with a too high priority in the log
  (without changing anything).

## Cities

A city is a WorldGuard region managed by Blockstead (`city_<name>`), from the lowest to the highest point of
the world – or, with `bottom <Y>` / `top <Y>` as for plots, between fixed heights (e.g. a city underground).
It protects everything the city itself owns:

* **Outside of plots** only the people added with `/city trust` may build – streets, squares and the city's
  buildings can't be griefed. Staff with WorldGuard bypass (`/rg bypass`) can always build.
* **On plots** only the plot counts (plots have a higher priority, `plots.priority` > `cities.priority`):
  owners and roles work as before, city builders have no rights there.
* A plot **belongs to a city** if it shares at least one block with it – also if it reaches beyond the city's
  border. If it touches several cities, the one with the largest shared space counts (`/plot create` points
  that out). Cities must not overlap each other (on top of each other is fine). Plots outside of cities work
  as before.
* **Teleport:** `/city teleport <City>` (short `/city tp`) takes everyone to the city's landing point. Staff
  set it with `/city setspawn <City>` at their own position (including the viewing direction; it must be
  inside the city – `/city create` reminds you). Without a landing point, Blockstead looks for a safe spot as
  central as possible, preferably not on a plot. Between two teleports of a person (`/city teleport` and
  `/plot home`) there is at least one second (staff exempt). The landing point is WorldGuard's `teleport`
  flag, so `/rg teleport city_<name>` lands there, too.
* **Price** of a plot: its own price (`/plot price`) > the price of its city (`/city price`) > `plots.price`.
  Refunds are always the amount that was actually paid.
* **Greeting and farewell:** when entering or leaving, everyone sees a translated default text ("Welcome to
  Newtown!"). `/city greeting` and `/city farewell` set a custom text (MiniMessage, `<city>` = name of the
  city; it then applies to all languages), `default` restores the default text, `off` turns the message off.

| Command | Description | Permission |
|---|---|---|
| `/city`, `/city help` | Clickable overview | `bs.city` |
| `/city list` | All cities with plots, free plots and price | `bs.city` |
| `/city info [City]` | Plots, price; for staff also builders, greeting and farewell | `bs.city` |
| `/city free <City> [page]` | Free plots of the city | `bs.plot.free` |
| `/city teleport <City>`, `/city tp …` | Teleport to the city (in the game only) | `bs.city.tp` |
| `/city plots <City> [all\|free\|taken] [page]` | All plots of the city, like `/plot plots` | `bs.plot.list.all` |
| `/city create <Name> [bottom <Y>] [top <Y>\|selection]` | Create a city from the WorldEdit selection (in the game only); name: letters, digits, `-`, `_`, unique on the server | `bs.city.manage` |
| `/city redefine <City> [bottom <Y>] [top <Y>\|selection]` | Replace the area with the WorldEdit selection; everything else stays (in the game only; a landing point outside the new area is removed) | `bs.city.manage` |
| `/city setspawn <City> [none]` | Landing point for `/city teleport` at your position (in the game only); `none` removes it | `bs.city.manage` |
| `/city remove <City>` | Delete the city (asks for confirmation); its plots stay | `bs.city.manage` |
| `/city price <City> <amount\|none>` | Price of the plots in the city; `none` = default price | `bs.city.manage` |
| `/city greeting <City> <text\|default\|off>` | Message when entering | `bs.city.manage` |
| `/city farewell <City> <text\|default\|off>` | Message when leaving | `bs.city.manage` |
| `/city trust <City> <Name>`, `/city untrust <City> <Name>` | Who may build in the city outside of plots | `bs.city.trust` |

Everything except `tp`, `create`, `redefine`, `setspawn` (without `none`) and `info` without a name also works
from the console.

**Flags of a city apply on its plots, too**, as long as a plot doesn't set the flag itself (WorldGuard takes
the value of the highest priority that sets it). That's why `cities.default-flags` ignores protection flags
like `use`, `interact` or `chest-access`: `allow` would open the doors and chests on all plots to strangers,
`deny` would lock out their owners. Don't set them on a city with `/rg flag` either. Consequence: doors and
buttons in the city area can only be used by city builders – put public doors on a plot of their own if
needed.

### Taking over an existing city region

1. `/rg select <old region>` – WorldGuard sets your WorldEdit selection to the region.
2. `/city create <Name>` – creates the city with the same area.
3. Add the builders with `/city trust`, then delete the old region: `/rg remove <old region>`.

Flags you set on the old region by hand are not copied; set them again with `/rg flag city_<name> …` if needed.

## Staff commands

Leave out `[ID]` = the plot you are standing on. `[World]` is optional in the game (default: your own world)
and **required from the server console**, e.g. `plot assign plot_3 Alex world`.

| Command | Description | Permission |
|---|---|---|
| `/plot create [bottom <Y>] [top <Y>\|selection]` | Create a plot from the WorldEdit selection, optionally with height limits or the height of the selection (in the game only) | `bs.plot.create` |
| `/plot remove <ID> [World] [reset]` | Delete a plot; the price is refunded | `bs.plot.remove` |
| `/plot assign <ID> <Name> [World]` | Give a free plot to someone at no cost (also to people who are offline) | `bs.plot.assign` |
| `/plot unassign <ID> [World] [reset]` | Release any plot; the price is refunded | `bs.plot.unassign` |
| `/plot reset [ID] [World]` | Restore from the snapshot, ownership and roles stay | `bs.plot.reset` |
| `/plot snapshot <ID> [World]` | Save the current state as the reset snapshot | `bs.plot.snapshot` |
| `/plot teleport <ID> [World]`, `/plot tp …` | Teleport to a plot – as central as possible (distance to the edge, close to the centroid) and never onto lava, fire or similar (in the game only) | `bs.plot.tp` |
| `/plot list <Name>` | Plots of another person | `bs.plot.list.others` |
| `/plot price <ID> <amount\|none> [World]` | Own price of a plot, overrides the price of the city; `none` = city or default price again | `bs.plot.price` |
| `/plot plots [all\|free\|taken] [World\|*] [page]` | All plots of one world or of all worlds (`*`, default), with status, owners and size; 10 per page, « back / next » are clickable; clicking a line puts `/plot tp <ID> <World>` into the chat | `bs.plot.list.all` |
| `/plot trust`, `/plot untrust` on other people's plots | Manage roles like an owner | `bs.plot.trust.others` |
| – | Not restricted by plot roles | `bs.plot.bypass` |
| `/announce <message>` | Highlighted server message to everyone (chat, title, sound) | `bs.announce` |

**Restoring:** `reset` brings back the state at creation (or at the last `/plot snapshot`) – exactly in the
shape of the plot, neighbouring plots stay untouched; animals, item frames and armor stands on the plot are
removed. Irreversible actions ask for confirmation: repeat the same command within 30 seconds. Without a
snapshot (plots from before version 1.0.0), `reset` is refused and nothing changes. Restoring runs on the
main thread – with large plots the server may stall briefly.

**From the console** you can use `info`, `trust`, `untrust`, `remove`, `assign`, `unassign`, `reset`,
`snapshot`, `price`, `list <Name>`, `plots`, `free`, `/city` (see [Cities](#cities)) and `/announce`.

**Refunds:** when a plot is released or deleted, the price goes back through the economy plugin to the person
who paid. If that can't be booked (e.g. the economy plugin is missing), the plot is released anyway; the log
then shows an error "Refund failed: …" with name, UUID, amount and a note to refund manually
(e.g. `/eco give <Name> <amount>`).

## Permissions

All permissions start with `bs.`. "Everyone" = all players; "Op" = ops and the console automatically, everyone
else through LuckPerms, e.g. `lp group team permission set bs.plot.assign true`.

| Permission | Default | For |
|---|---|---|
| `bs.plot` | everyone | `/plot`, `/plot info`, `/plot list` |
| `bs.plot.claim` | everyone | Buying a plot |
| `bs.plot.unclaim` | everyone | Giving your own plot back |
| `bs.plot.home` | everyone | Setting a home and teleporting there |
| `bs.plot.trust` | everyone | Giving roles on your own plots |
| `bs.plot.free` | everyone | `/plot free [World\|*] [page]` and `/city free` – list of free plots |
| `bs.city` | everyone | `/city`, `/city list`, `/city info` |
| `bs.city.tp` | everyone | `/city teleport` |
| all other `bs.*` | Op | see staff commands |

`/blockstead` (version, licence, source code) needs no permission.

## Languages

* **`language: auto`** (default): every person gets the language of their Minecraft client – including
  Bedrock players through Geyser. If someone changes the language in the client, it applies from the next
  message on.
* **`language: de`** (or another code): this language for everyone.
* **`fallback-language`** (default `en`): for the console and for client languages without their own file.
* Bundled are `en` and `de`. Own texts: create a file `plugins/Blockstead/lang/<code>.yml`. It only has to
  contain the keys you want to change – everything else comes from the bundled file (so improved texts from
  updates show up, too). A new file, e.g. `fr.yml` as a copy of [`en.yml`](../src/main/resources/lang/en.yml),
  adds a new language. Changes take effect after a restart.
* The **commands** stay English everywhere (`/plot claim` …), only help and messages are translated. For your
  own shortcuts, use Paper's built-in aliases in `commands.yml` in the server's main folder:
  ```yaml
  aliases:
    grundstueck:
    - plot $1-
    gs-kaufen:
    - plot claim $1-
  ```
  Such aliases have no tab completion for the subcommands.

## Configuration

`plugins/Blockstead/config.yml` – every entry is explained in the file itself; changes take effect after a
restart.

| Section | Contents |
|---|---|
| `language`, `fallback-language` | Language of the messages, see [Languages](#languages) |
| `display` | LuckPerms prefixes on/off, templates for chat and tab list (`{prefix}`, `{name}`, `{suffix}`, `{message}`), sorting the tab list by group weight, nametag |
| `join-info` | Join message (e.g. Teamspeak, Discord); MiniMessage formatting (without `run_command`), `http(s)://` addresses become clickable. For Teamspeak use an invite link like `https://invite.teamspeak.com/<address>` – Minecraft only opens http(s) links |
| `announce` | Prefix, title and sound of `/announce` |
| `backups` | Backup at startup on/off, number of backups kept |
| `economy` | `import-old-balances`: transfer old balances from 1.x once (currency name and starting balance are set in the economy plugin) |
| `plots` | Price (a whole number in the economy plugin's currency), waiting time for `unclaim reset`, WorldGuard priority, default flags of new plots |
| `cities` | WorldGuard priority (lower than `plots.priority`) and default flags of new cities |
| `roles` | Roles below "owner": rights (`build`, `containers`, `doors`, `workstations`, `entities`, `home`), optional `aliases` (further keys) and `name` (fixed display name instead of a translation) |

Notes:

* New entries from updates are **not** added to an existing `config.yml` automatically; missing entries have
  their default value. The current template: [`src/main/resources/config.yml`](../src/main/resources/config.yml).
* Protection flags like `use`, `interact`, `chest-access` or `build` don't belong in `plots.default-flags`:
  in WorldGuard they apply to owners and members, too. WorldGuard keeps strangers out anyway; what members may
  do is defined by the roles. Such entries are ignored with a warning.
* Without LuckPerms the plugin runs without the prefix display (warning in the log).

## Data and backups

`plugins/Blockstead/data.db` (SQLite) holds homes, roles and who bought which plot for how much (for refunds).
Since 2.0.0 the economy plugin keeps the balances; the old balances from 1.x only remain as a record in the
`accounts` table (with the time of the transfer). Owners and members are stored in WorldGuard; the reset
snapshots are WorldEdit schematics in `plugins/Blockstead/snapshots/<world>/<ID>.schem`.

* **Backup at every start:** before the plugin changes anything (including data migrations of an update),
  `data.db` and the WorldGuard region files of all worlds are copied to
  `plugins/Blockstead/backups/<date_time>/`; the newest 10 are kept. If WorldGuard stores regions in MySQL,
  there is a warning – back them up separately then.
* **Restoring:** stop the server, copy `data.db` from the backup folder to `plugins/Blockstead/` and
  `worldguard/<world>/regions.yml` to `plugins/WorldGuard/worlds/<world>/`, start the server.
* **Protection against older versions:** if the database is newer than the installed plugin version, the
  plugin refuses to start and changes nothing.
* **Consistency check:** at startup, entries for plots that don't exist (any more) in WorldGuard are reported
  (e.g. after `/rg remove` instead of `/plot remove`). Nothing is deleted automatically.
* The plugin doesn't back up the worlds themselves – that's what a server backup is for.
