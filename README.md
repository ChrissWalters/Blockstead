# Blockstead

**Your own plot in the city.** Blockstead is a CityBuild plugin for Minecraft servers (Paper): buy plots,
invite others with their own roles, set a home – and what you build stays protected.

*Auf Deutsch: [README.de.md](README.de.md)*

This page explains the plugin for **players**. For server owners and staff: [docs/admin.md](docs/admin.md).
For development: [docs/development.md](docs/development.md). What changed between versions:
[CHANGELOG.md](CHANGELOG.md).

Blockstead speaks the language of your Minecraft client – English and German are included, more can be
added (see [Translating](#translating)).

## How it works

The server team creates plots – from the lowest to the highest point of the world. You can buy free plots
with the server currency; at the start you usually have enough money for **one plot**. Your balance is
shown by your server's command, usually `/balance`. The team gives out further plots.

Nobody can touch anything on your plot unless you have given them a role – not even open doors. When you
enter or leave a plot, you see where you are.

## Commands

You can leave out `[ID]` – then the plot you are standing on is used. Plot IDs look like this: `plot_3`.
All commands have tab completion.

| Command | What it does |
|---|---|
| `/plot`, `/plot help` | Overview of all commands you may use – click a line to put the command into the chat, then add the ID or name and press Enter |
| `/plot info [ID]` | Who owns the plot, who has which role, is it free? |
| `/plot claim [ID]` | Buy a free plot |
| `/plot list` | Your plots |
| `/plot free [World]` | Free plots you can buy, with size and price – click a line to put `/plot info` for it into the chat |
| `/plot home` | Teleport to your home |
| `/plot home set` | Set your home here (on your plot or with a suitable role) |
| `/plot trust <name> <role> [ID]` | Give someone a role on your plot |
| `/plot untrust <name> [ID]` | Take the role away again |
| `/plot unclaim [ID]` | Give the plot back – you get the price back, everything you built stays |
| `/plot unclaim [ID] reset` | Give it back **and** restore its original state (asks for confirmation) |
| `/blockstead` | Version, licence and source code of the plugin |

What you may use is decided by the server – commands you have no permission for aren't shown at all.

## Roles

With `/plot trust <name> <role>` you invite others to your plot. These are the default roles – your server
can change them, `/plot info` shows who has which role:

| | Owner | Resident | Builder | Guest |
|---|:-:|:-:|:-:|:-:|
| Build and break | ✔ | ✔ | ✔ | |
| Chests and other containers | ✔ | ✔ | | |
| Doors, gates, buttons, levers | ✔ | ✔ | ✔ | ✔ |
| Crafting table, furnaces, anvil, beds | ✔ | ✔ | ✔ | |
| Animals, armor stands, item frames | ✔ | ✔ | | |
| Set a home | ✔ | ✔ | | |
| Give roles, give the plot back | ✔ | | | |

The role names in the command: `owner`, `resident`, `builder`, `guest`. The German names from earlier
versions (`besitzende`, `mitbewohnende`, `bauhilfe`, `gast`) still work.

Good to know:

* **Owners are equals.** Whoever becomes `owner` can do everything you can – including taking your rights
  away or giving the plot back. Only give this role to people you trust. The last owner can't be removed.
* **Giving back:** The price always goes back to the person who bought the plot. All roles and homes on the
  plot are removed. Restoring (`reset`) is only possible every few minutes.
* **Home:** If you lose your rights on a plot, your home there is no longer valid.

## Issues yes, pull requests only for translations

**Bug reports, questions and ideas are very welcome – please open an
[issue](https://github.com/ChrissWalters/Blockstead/issues).** That is the one channel, and it is read: what
people run into is what gets fixed.

**Pull requests are only accepted for translations** – that is, changes to files in
[`src/main/resources/lang/`](src/main/resources/lang/). Translations need no code review, so they are easy
to take. Pull requests that touch anything else are closed automatically by a check, and it is friendlier to
say so before you spend an evening on one. This is a single-maintainer project; reviewing other people's
code well enough to be sure of what runs on other servers is a time commitment I can't make right now.

Nothing about that limits what you may do with the software. It is AGPL-3.0: fork it, change it, run your
changed version, pass it on. The licence only asks that your version stays under the same terms and keeps
the notices intact.

If you have fixed something in your fork, an issue describing it – or linking to your commit – is genuinely
useful. It just travels back through me rather than through a merge button.

Please report security problems privately, not as a public issue – see [SECURITY.md](SECURITY.md).

## Translating

Every text lives in a flat YAML file under [`src/main/resources/lang/`](src/main/resources/lang/), keyed like
`plot.claim.success`. Adding a language:

1. Copy `en.yml` to `<code>.yml` – a language code as Minecraft uses it, e.g. `fr` (or `pt_br` for a
   regional variant).
2. Translate the texts. Leave the keys alone and keep every placeholder such as `<id>` or `<name>` exactly as
   it is. Formatting tags like `<gold>` or `<bold>` may be changed.
3. Try it on a server: put the file into `plugins/Blockstead/lang/` and restart – no rebuild needed. Players
   whose Minecraft client uses that language see it right away.
4. **To get it shipped with the next release, open a pull request** that only adds or changes the file in
   `src/main/resources/lang/`. Please say how you would like to be credited, or that you would rather not be.

The build checks that every language file has the same keys and placeholders as `en.yml`, so a typo in a key
doesn't get far.

## Supporting the project

Blockstead is free and stays free: no paid tier, no licence key, no feature held back. If you like the
plugin and would like to say thank you:

* [Ko-fi](https://ko-fi.com/chrisswalters)

Entirely voluntary, and nothing about the software depends on it.

## Development

AI coding tools were and are used to support the development of Blockstead.

## Licence

Copyright © 2026 ChrissWalters

[GNU Affero General Public License v3.0 or later](LICENSE). Provided **without any warranty**.

Whoever runs a modified version on a server must offer the players its source code – easiest by setting
`website` in `plugin.yml` to your own repository; `/blockstead` shows the link.
