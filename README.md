# ServerX 
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
| `/prison <player> <time> [reason]` | Timed prison. Time: `30s`, `10m`, `2h`, `1d`, `1h30m` (bare number = minutes). Timer runs while offline too. |
| `/setprison` | Sets the prison to where you stand. |
| `/rmprison` | Removes the prison location. Refuses while anyone is jailed; `/rmprison force` releases everyone first. |
| `/prisonlist` | Shows the prison location and every prisoner (permanent / time left, who jailed them, reason, offline marker). |
| `/strictprotect make <name>` | Like `/protect make`, but fully locked: no placing, breaking or using anything (chests, doors, signs too). |
| `/strictprotect set <region> <true/false>` | Turn an existing region strict, or back to normal. |
| `/serverx` / `/serverx reload` | Status / reload config, regions, prison. |

### Region rules (defaults)
Players without `serverx.protect.bypass` inside a region: cannot break blocks, can place blocks (they vanish after 2 s),
can use chests/barrels/doors/trapdoors/buttons/levers/crafting etc., cannot touch signs. Explosions
(creepers, TNT, crystals...) never destroy blocks there. Regions can overlap; flags: `explosion-proof`, `no-break`,
`no-place`, `block-decay`, `lock-signs`, `allow-interact`.

### Prisoners
Cannot use items, break/place/use blocks, hit or use entities, or run commands. They are pulled back if they walk
more than `prisonLeashRadius` (default 16) blocks away or change dimension.

## LuckPerms nodes
Grant with e.g. `/lp group mod permission set serverx.prison true`. Without LuckPerms (or if a node is unset),
vanilla op levels are the fallback shown below.

| Node | Fallback |
|---|---|
| `serverx.cmd` | op level 4 (owners) |
| `serverx.admin` | op 3 |
| `serverx.protect.make` / `.edit` / `.del` | op 3 |
| `serverx.protect.list` / `.help` | op 2 |
| `serverx.protect.bypass` | op 3 |
| `serverx.punishban` / `serverx.pardonban` / `serverx.setprison` | op 3 |
| `serverx.prison` / `serverx.prisonlist` | op 2 |
| `serverx.rmprison` / `serverx.strictprotect` | op 3 |
| `serverx.prison.exempt` | nobody (must be granted) |

`serverx.cmd` is effectively console access. Give it to nobody you would not give the panel to.

## Adding extensions
Everything is a module (`com.serverx.api.ServerXModule`): `onInit`, `registerCommands`, `onEnable`, `onDisable`, `onReload`.

**Inside this project:** create a class implementing `ServerXModule`, then add `modules.register(new MyModule());` in `ServerX.java`.

**As a separate mod:** depend on ServerX, implement `ServerXModule`, and declare it in your `fabric.mod.json`:

    "entrypoints": { "serverx": [ "com.me.MyModule" ] }

Modules get `ServerXApi`: scheduler, config, player directory (offline lookups), join/leave hooks, a service registry
(`api.service(ProtLib.class)`, `api.service(PrisonService.class)`), plus `Perm`/`Perms` for LuckPerms nodes,
`JsonStore` for saving data and `Msg` for consistent chat messages.

## ProtLib (the protection library)
`com.serverx.protlib`: `Region`, `ProtFlag`, `Interactables`, `ProtLib`. Other modules can add their own flags
(`protLib.registerFlag(new ProtFlag("no-pvp", false, "..."))`, shows up in `/protect edit/info` automatically), add
interactable blocks (`Interactables.addBlock`), query regions (`protLib.at(level, pos)`) and use the decay tracker.
