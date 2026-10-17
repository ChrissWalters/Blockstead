# Security policy

Blockstead runs on Minecraft servers, manages plots and permissions and books purchases through the server
currency. Bugs there can leave buildings unprotected or give players rights they shouldn't have.

## Reporting a vulnerability

Please report security problems **privately first**, not as a public issue:

* GitHub → *Security* → *Report a vulnerability* (private advisory), or
* by e-mail to the address in the repository profile.

Useful in a report: what you did, what happened, what you expected, and the version (`/blockstead` shows it).
A reproducible example helps, a working exploit is not required. Reports in German are welcome, too.

You will get a first answer within **seven days**. Fixed problems are named in the release notes – and,
unless you would rather not be, you are credited there.

There is no bounty programme; this is a volunteer project under the AGPL-3.0.

## What is in scope

The plugin itself: protection of plots and roles, permission checks of the commands, bookings when buying and
refunding, the database and backups, the handling of chat, configuration and language-file texts.

Out of scope: problems that require an already compromised server, bugs in Paper, WorldEdit, WorldGuard,
LuckPerms, Vault or EssentialsX themselves (please report them there), and rights the server team granted on
purpose.

## How the plugin protects itself

| Area | Measure |
|---|---|
| Plots | WorldGuard keeps strangers out; roles additionally restrict members, never the other way round |
| Cities | Lower priority than plots, so city builders get no rights on plots; protection flags are not allowed as default flags of cities (they would apply on the plots inside) |
| Commands | Every subcommand checks its permission; without it, it isn't even shown |
| Chat | Players' messages are never parsed as formatting (no colour, click or hover codes); in staff texts (`/announce`, `join-info`, city greetings, language files) clicks that run commands are blocked |
| Placeholders | Values inserted into translated texts (names, IDs …) are never parsed as formatting |
| Database | Prepared SQL statements only |
| Currency | Payment before the plot is given out; bookings through the economy plugin, failed refunds in the log |
| Files | Names of snapshot files are reduced to safe characters |
| Server load | Waiting time between two restores by players; at least one second between two teleports of a person (`/plot home`, `/city teleport`) |
| Build | The pipeline runs with read permission; only the release job for version tags may write. Pull requests may only change language files |
| Updates | Backup of database and regions at every start; no start with a newer database |
| Privacy | No telemetry, no outgoing connections |
