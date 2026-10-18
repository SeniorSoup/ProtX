# ProtX
## Commands
| Command | What it does |
|---|---|
| `/cmd <command>` | Runs the command as the server console (as if typed in the panel). Logged to console. |
| `/protect make <name>` | Gives a golden pickaxe wand: left-click corner 1, right-click corner 2. |
| `/protect edit <name> bounds` | Re-select the corners. |
| `/protect edit <name> flag <flag> <true/false>` | Switch a rule on/off per region. |
| `/protect edit <name> decay <seconds>` | How long blocks placed in the region last. |
| `/protect edit <name> rename <newname>` | Rename. |
| `/protect del <name>` / `list` / `info <name>` / `help` / `cancel` | Manage regions. |
| `/punishban <player> [reason]` | Permanent prison. Works on offline players (jailed on next login). |
| `/pardonban <player>` | Release, and send them back to where they were. |
| `/prison <player> <time> [reason]` | Timed prison (tab-complete lists online players only). Time: `30s`, `10m`, `2h`, `1d`, `1h30m` (bare number = minutes). Timer runs while offline too. |
| `/prisonip <player> <time> [reason]` | Timed prison for the player's IP: jails them, every account seen on that IP, and anyone who joins from it until it expires. |
| `/punishbanip <player> [reason]` | Same, permanent. |
| `/pardonip <player>` | Lifts the IP sentence and releases everyone jailed by it. |
| `/setprison` | Sets the prison to where you stand. |
| `/rmprison` | Removes the prison location. Refuses while anyone is jailed; `/rmprison force` releases everyone first. |
| `/prisonlist` | Shows the prison location and every prisoner (permanent / time left, who jailed them, reason, offline marker). |
| `/strictprotect make <name>` | Like `/protect make`, but fully locked: no placing, breaking or using anything (chests, doors, signs too). |
| `/strictprotect set <region> <true/false>` | Turn an existing region strict, or back to normal. |
| `/protx` / `/protx reload` | Status / reload config, regions, prison. |

### Region rules (defaults)
Players without `protx.protect.bypass` inside a region: cannot break blocks, can place blocks (they vanish after 2 s),
can use chests/barrels/doors/trapdoors/buttons/levers/crafting etc., cannot touch signs. Explosions
(creepers, TNT, crystals...) never destroy blocks there. Regions can overlap; flags: `explosion-proof`, `no-break`,
`no-place`, `block-decay`, `lock-signs`, `allow-interact`.

### Prisoners
Cannot use items, break/place/use blocks, hit or use entities, or run commands. They are pulled back if they walk
more than `prisonLeashRadius` (default 16) blocks away or change dimension.

## LuckPerms nodes
Grant with e.g. `/lp group mod permission set protx.prison true`. Without LuckPerms (or if a node is unset),
vanilla op levels are the fallback shown below.

| Node | Fallback |
|---|---|
| `protx.cmd` | op level 4 (owners) |
| `protx.admin` | op 3 |
| `protx.protect.make` / `.edit` / `.del` | op 3 |
| `protx.protect.list` / `.help` | op 2 |
| `protx.protect.bypass` | op 3 |
| `protx.punishban` / `protx.pardonban` / `protx.setprison` / `protx.punishbanip` / `protx.pardonip` | op 3 |
| `protx.prisonip` | op 2 |
| `protx.prison` / `protx.prisonlist` | op 2 |
| `protx.rmprison` / `protx.strictprotect` | op 3 |
| `protx.prison.exempt` | nobody (must be granted) |

`protx.cmd` is effectively console access. Give it to nobody you would not give the panel to.

## Adding extensions
Everything is a module (`com.protx.api.ProtXModule`): `onInit`, `registerCommands`, `onEnable`, `onDisable`, `onReload`.

**Inside this project:** create a class implementing `ProtXModule`, then add `modules.register(new MyModule());` in `ProtX.java`.

**As a separate mod:** depend on ProtX, implement `ProtXModule`, and declare it in your `fabric.mod.json`:

    "entrypoints": { "protx": [ "com.me.MyModule" ] }

Modules get `ProtXApi`: scheduler, config, player directory (offline lookups), join/leave hooks, a service registry
(`api.service(ProtLib.class)`, `api.service(PrisonService.class)`), plus `Perm`/`Perms` for LuckPerms nodes,
`JsonStore` for saving data and `Msg` for consistent chat messages.

## ProtLib (the protection library)
`com.protx.protlib`: `Region`, `ProtFlag`, `Interactables`, `ProtLib`. Other modules can add their own flags
(`protLib.registerFlag(new ProtFlag("no-pvp", false, "..."))`, shows up in `/protect edit/info` automatically), add
interactable blocks (`Interactables.addBlock`), query regions (`protLib.at(level, pos)`) and use the decay tracker.
