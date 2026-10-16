# Changelog

*Auf Deutsch: [CHANGELOG.de.md](CHANGELOG.de.md)*

## 2.2.0

Plot lists: an overview of all plots for staff and of the free plots for everyone.

### New
- `/plot plots [all|free|taken] [World|*] [page]` (permission `bs.plot.list.all`, default Op, also from the
  console): all plots of one world or of all worlds with status, owners and size, 10 per page. « back / next »
  are clickable; clicking a line puts `/plot tp <ID> <World>` into the chat.
- `/plot free [World|*] [page]` (permission `bs.plot.free`, default everyone): free plots with size and price;
  clicking a line puts `/plot info <ID> <World>` into the chat.

### Notes on updating from 2.1.x
- Servers that give player permissions explicitly in LuckPerms (instead of the plugin defaults) add
  `bs.plot.free` to the group of the players, e.g. `lp group default permission set bs.plot.free true`.

## 2.1.0

Language support: Blockstead speaks the language of the Minecraft client.

### New
- All texts come from language files (`lang/en.yml`, `lang/de.yml`). Every person gets the language of their
  Minecraft client (`language: auto`, default) – Bedrock players through Geyser, too – or the server sets one
  language for everyone (`language: de`). Fallback language for the console and missing languages:
  `fallback-language` (default `en`).
- Own or changed texts: files in `plugins/Blockstead/lang/`. They only override the keys they contain; a new
  file adds a new language.
- Messages to everyone (join, quit) go to every person in their own language.
- Roles in `config.yml` can have `aliases`; without `name`, the role name is shown translated.
- The build checks that all language files have the same keys and placeholders.
- Translations can be contributed through pull requests (only files in `src/main/resources/lang/`).
- `/plot help` (besides `/plot`); every line of the help is clickable and puts the command into the chat line
  without sending it, so the ID, name etc. can still be added.

### Changed
- New default role keys: `owner`, `resident`, `builder`, `guest`. The previous keys (`besitzende`,
  `mitbewohnende`, `bauhilfe`, `gast`) remain valid as aliases – existing configs and stored roles keep working
  unchanged.
- Log messages and the comments in `config.yml` are English.
- `/plot create` no longer sets the `region-name` flag; the message when entering comes translated from the
  language file. Old plots with the former default text show the translated message, too.
- Argument names of the commands while typing are English (`<world>`, `<role>`); the commands themselves are
  unchanged.
- Documentation: README and `docs/admin` in English and German, the remaining documentation and the changelog
  also in English.

### Notes on updating from 2.0.x
- For a German-only server, set `language: de` and `fallback-language: de` in `config.yml` – otherwise every
  person gets the language of their client and the console English.
- If you want the new role keys, take the `roles` section from the new template; the `aliases` keep stored
  roles valid. It is not necessary.

## 2.0.1

Bug fixes and hardening after a review of the whole project.

- `/plot tp` lands as centrally and safely as possible: on polygons on a spot well away from the edge, close to
  the centroid (on L-shaped plots it used to land on a corner point at the edge), and never on lava, fire,
  magma, cactus or similar – otherwise on the nearest safe spot on the plot. If there is none, there is no
  teleport.
- Nametag teams (`bs_…`) that remained saved with the world after a crash are removed at startup.
- `/announce` and `join-info`: click actions that run a command (`<click:run_command:…>`) are removed; links
  and `suggest_command` remain.
- For modified versions without `website` in plugin.yml, `/blockstead` shows the link to the original instead
  of "null".
- Expired waiting times and confirmations are removed from memory.
- Build pipeline: write permission only for the release job on version tags.

## 2.0.0

Blockstead concentrates on plots. General commands and the currency are taken over by EssentialsX (or another
economy plugin), connected through Vault.

### Removed
- `/coins` (including `give|take|set`) and the own bookkeeping – replacement: `/balance`, `/pay`, `/eco`,
  `/baltop` of the economy plugin.
- `/spawn` – replacement: `/spawn` and `/setspawn` from EssentialsXSpawn.
- `/die` and `/suicide` – replacement: `/suicide` from EssentialsX.
- `/invincible` – replacement: `/god` from EssentialsX.
- Alias `/broadcast` for `/announce` (conflict with EssentialsX); `/announce` itself stays.
- Permissions `bs.coins`, `bs.coins.others`, `bs.coins.admin`, `bs.spawn`, `bs.die`, `bs.invincible`,
  `bs.invincible.others`.
- `config.yml`: `economy.currency-name` and `economy.start-balance` (ignored; name, symbol and starting balance
  are set in the economy plugin).

### Changed
- `/plot claim` charges through Vault at the economy plugin; without Vault or an economy plugin buying is
  blocked (unless the price is 0), `/plot assign` keeps working.
- Refunds when releasing and deleting go through the economy plugin. If a refund fails, the plot is released
  anyway and the amount is reported with the name in the log.
- Amounts appear in the economy plugin's format.
- Vault is an optional dependency (`softdepend`), like LuckPerms.

### New
- One-time transfer of the balances from 1.x to the economy plugin (set, not added), with a log line per
  account; can be switched off with `economy.import-old-balances: false`.
- A log line about the status of the server currency shortly after startup.

### Notes on updating from 1.x
- **Make a complete server backup first.**
- **Turn `/invincible` off for everyone** who used it (with 1.0.1) before updating. Invulnerability is stored in
  the player data and can't be switched off without the command afterwards.
- Install Vault (or VaultUnlocked) and an economy plugin, e.g. EssentialsX – for Paper 26.3 currently the dev
  build 2.22.1-SNAPSHOT. Without both, plots can't be bought.
- Give the permissions for the replacement commands in the permissions plugin (`essentials.balance`,
  `essentials.spawn`, …).
- Database schema 4: going back to 1.x is only possible with the backup of `data.db` from before the update;
  1.x refuses to start with schema 4 on purpose.
- Details: `docs/admin.md`, section "From 1.x to 2.0.0".

## 1.0.1

- `/plot create` refuses a selection that overlaps an existing plot and warns if another region (e.g. a city)
  overlaps the new plot with the same or a higher priority.
- The consistency check at startup reports overlapping plots and regions with a too high priority.
- Documentation: section "Overlaps and city areas" in `docs/admin.md`.
- **Now requires Paper 26.3** (instead of 26.2): under 26.3 the old check of invulnerability reports wrong
  values, so `/invincible` uses the new method from 26.3. Tested with WorldEdit 7.4.6.

## 1.0.0

First public version of **Blockstead** (formerly "Buildserver") – a CityBuild plugin for Paper 26.2.

### Plots
- Creating from a WorldEdit selection (cuboid or polygon), always from the lowest to the highest point of the
  world (`/plot create`).
- Buying with the server currency (`/plot claim`); everyone starts with enough money for one plot.
- Assigning and removing by staff (`/plot assign`, `/plot unassign`), giving back by owners (`/plot unclaim`)
  with a full refund of the price.
- Information and overview (`/plot info`, `/plot list`).
- Roles on plots: owner, resident, builder, guest (`/plot trust`, `/plot untrust`); rights per role adjustable
  in `config.yml`.
- Homes (`/plot home`, `/plot home set`), teleport to plots for staff (`/plot tp`), `/spawn` for everyone.
- Restoring to the state at creation (`reset` option, `/plot reset`, `/plot snapshot`) with confirmation and a
  waiting time for players.
- Messages when entering and leaving plots (own text through the `region-name` flag).
- Staff commands from the server console, too (with a world argument).

### Server
- Server currency "Coins" (name adjustable) with `/coins`.
- LuckPerms prefixes and suffixes in chat, tab list and above the head; tab list sorted by group weight.
  LuckPerms is optional.
- Join message with clickable links (e.g. Teamspeak, Discord, website).
- Highlighted server messages for staff (`/announce`).
- `/blockstead` shows the version, licence and a link to the source code.
- Own join and quit messages.

### Operation
- Backup of the plugin database and the WorldGuard regions at every start, protection against older plugin
  versions, consistency check.
- All commands with tab completion; commands without permission are hidden.
- Build pipeline on GitHub with a release for `v*` tags, local test server (`./gradlew runServer`).

### Notes on updating from the predecessor "Buildserver"
- **Make a complete server backup first.**
- **Remove the old jar** (e.g. `buildserver-0.0.1-SNAPSHOT.jar`) – the plugin is now called Blockstead, both at
  the same time would get in each other's way. The data folder is now `plugins/Blockstead/`; the predecessor
  had no data of its own, nothing has to be moved. Permissions (`bs.*`) and the WorldGuard flag `region-name`
  stay the same.
- Java 25 and Paper 26.2 are required; WorldEdit and WorldGuard are mandatory, LuckPerms is optional (5.5.85 or
  newer).
- At the first start, the flags `interact`, `use`, `chest-access`, `ride`, `vehicle-place`, `vehicle-destroy`
  and `lighter` are removed from all existing plots if they are set to `deny`. They locked out owners and
  members, too; WorldGuard keeps strangers out anyway. Every change is logged.
- At the first start, `plugins/Blockstead/config.yml` is created with all settings – check and adjust it
  afterwards (e.g. `join-info`, prices, roles).
- Every player gets the starting balance (default 100 Coins) at their next join, including those who already
  own a plot.
- Plots from before this version have no reset snapshot; create one with `/plot snapshot <ID>` if needed.
- The old, unused MySQL code was removed.
- Licence: GNU AGPL-3.0 or later.
