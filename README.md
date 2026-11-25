# Vanilla Finest Helper

The players' side of the Vanilla Finest modpacks, in one Fabric mod: the setup screen, an automatic GUI
scale, and where screenshots, schematics, replays and blueprints are kept. It changes little between pack
versions.

## Setup screen (26.3 for now)

One screen for everything the player sets, in five steps: **Welcome**, **Settings**, **Folders**, **Look**
and **Ready to play**. It opens on the first start of an instance (`setup.done=true` in
`config/vanilla-finest-helper.properties` afterwards), from the settings icon on the title and pause
screens, and from Mod Menu's Configure button.

- Drawn like Options opened from the title screen: panorama, blur, the real Minecraft logo, then a fixed
  panel of 430×262 GUI pixels with Minecraft's own buttons, sliders, font and tooltips (so resource packs
  and Enhanced Tooltips style it). On small windows the panel shrinks and the step scrolls.
- Minecraft's own settings (languages, mouse, key bindings, graphics API, render distance, GUI scale)
  apply at once, as in Options. Voxy, the folders and the look are applied with ✔.
- Every other mod is optional and reached by name: Language Reload (several languages), Multi Key
  Bindings (profile name), Keycaps (Keyboard Map), Voxy, Iris, Packed Packs (profiles), Screenshot
  Gallery, Litematica, Flashback (Settings), Axiom, EaseGUI (animations kept off this screen).
- Texts: `assets/vanilla-finest-helper/setup/<language>.json` and `welcome.md` / `welcome.<language>.md`
  in the jar (English and Hungarian); a file with the same name in `config/vanilla-finest-helper/setup/`
  wins, so the wording changes without a new build. `{key:<key binding id>}` (or `{key:minihud:<hotkey>}`
  for malilib) shows a key's current name. `look.json` says which shaders each profile offers.

## GUI scale module (26.3 for now)

An automatic GUI scale chosen from the window's width: `guiscale.rules` (`width:scale` pairs; the highest
width not wider than the window wins) and `guiscale.mode` (`auto` or a number) in
`config/vanilla-finest-helper.properties`. The default table gives 640×360 GUI pixels on every 16:9
screen (1280 → 2, 1920 → 3, 2560 → 4, 3840 → 6). It stays off while GUI Scaler is installed; the setup
screen's slider then switches GUI Scaler's own automatic setting.

The fixes for the packs' mods are a separate mod,
[Vanilla Finest Fixes](https://github.com/Hiveko75/vanilla-finest-fixes), which is bound to the mod
versions each pack ships.

## Version check

A folder is only handled when its mod is installed in a version the module was made for, listed per
Minecraft version in `platform/mc<version>/resources/vanilla-finest-helper.modules.properties` and
compared with the number part of the mod's version. With another version that folder is left alone and
the log says why (`Flashback replays module off: made for Flashback 0.43.6, found …`).

| Minecraft | Flashback | Axiom |
|---|---|---|
| 26.3 | 0.43.6 | 6.1.x |
| 26.2 | 0.43.6 | 6.1.x |
| 26.1.2 | 0.43.3 | 6.1.x |
| 1.21.11 | 0.39.10 | 6.1.x |
| 1.21.1 | 0.39.10 | 6.1.x |

## Home folders module (first start of an instance, off by default)

**Off by default since 1.1.1:** screenshots, schematics, replays and blueprints stay in the
instance's own folders (each mod's default). To use the shared Minecraft folder instead, set
`homefolders.enabled=true` in `config/vanilla-finest-helper.properties`; the module then runs once
on the next start. Instances where it already ran (1.0.0, 1.1.0) keep the folders it set.

When switched on, the first time an instance starts, this module points the folder settings of three
mods at the player's shared Minecraft folder, so all instances use the same folders:

| Mod | Setting | Folder |
|---|---|---|
| Screenshot Gallery 2.x | screenshot folder, for saving and for the gallery (`customScreenshotDir`) | `<minecraft>/screenshots` |
| Litematica | custom schematic folder, switched on | `<minecraft>/schematics` |
| Flashback Settings | Flashback's replay folder (`replayFolder`) | `<minecraft>/flashback/replays` |

- `<minecraft>` is the launcher's Minecraft folder, found the way One World Folder finds it:
  `%APPDATA%\.minecraft` on Windows, `~/Library/Application Support/minecraft` on macOS,
  `~/.minecraft` on Linux. If it does not exist, nothing is changed.
- It runs once per instance, before the mods read their settings, and leaves the file
  `config/vanilla-finest-helper.home-folders.done`. After that it never changes these settings
  again. Delete that file to let it run once more.
- A folder the player already chose is kept: a setting that names a folder which exists on this
  computer is not changed. An empty setting, or a folder that is not there, is replaced.
- Only the settings files of installed mods are changed. A missing settings file is started from
  the modpack's default for it (`config/modpack_defaults/config/`).
- With Screenshot Gallery's folder set, the game saves new screenshots there and the gallery shows
  that folder. Screenshot Gallery 1.x (Minecraft 1.21.1) has no such setting.

## Folders module (Flashback, Axiom)

Two more folders the helper sets:

| Mod | Folder | How | Versions |
|---|---|---|---|
| Flashback (without Flashback Settings) | where replays are saved and listed | Flashback has no setting, so the helper redirects `Flashback.getReplayFolder`; the folder is `folder.flashback.replays` in `config/vanilla-finest-helper.properties` | 0.43.6, 0.43.3, 0.39.10 |
| Axiom | blueprint folder | Axiom's own setting: `customPath` in the `blueprint` part of `config/axiom/axiom.json` (Axiom reads it once at start-up; the folder must exist, the helper creates it) | 6.1.x |

- Empty means the mod's own folder in the instance (`flashback/replays`, `config/axiom/blueprints`); any
  other value is the folder to use (a full path).
- When switched on, the Home folders module sets both to the shared Minecraft folder once (see
  above). For Axiom, a folder already set in Axiom's settings is kept. By default both stay empty
  (the instance's own folders).
- **No settings screen of its own:** the setup screen (not built yet) will set these folders, and Mod
  Menu's Configure button for the helper will open that same setup screen. Until then the folders are
  set in the files above; Axiom's own settings screen shows and changes the same Axiom setting.
- Flashback reads the setting each time (a change counts at once); Axiom only at start-up.
- When Flashback Settings is installed (26.3, 26.2), its own setting decides the replay folder and the
  helper's Flashback hook is not applied.
- Files are never moved: replays or blueprints already saved stay where they are.
- The Flashback hook is in its own mixin configuration (`vanilla-finest-helper.mixins.json`,
  not required), so if another mod ever loads Flashback's class too early, the hook is skipped with an
  error in the log instead of stopping the game. Axiom cannot be hooked at all: its mixin plugin loads
  Axiom's main class before other mods' mixins are prepared (that crashed 1.1.0 at start-up).

## Files

| File | Minecraft |
|---|---|
| `vanilla-finest-helper-*+mc26.3.jar` | 26.3 |
| `vanilla-finest-helper-*+mc26.2.jar` | 26.2 |
| `vanilla-finest-helper-*+mc26.1.2.jar` | 26.1.2 |
| `vanilla-finest-helper-*+mc1.21.11.jar` | 1.21.11 |
| `vanilla-finest-helper-*+mc1.21.1.jar` | 1.21, 1.21.1 |

The Flashback hook is in a non-required mixin configuration (`vanilla-finest-helper.mixins.json`): if
a mod ever loads Flashback's class too early, the hook is skipped (an error in the log) instead of
stopping the game.

## Building

GitHub Actions: *Build* builds every jar; *Release* builds them and publishes a release with the
version's `CHANGELOG.md` section as notes. Locally (Java 25 and 21):

```
./gradlew build -Pmc=26.3        # or 26.2, 26.1.2 -> build/libs
./gradlew -p mc1.21.11 build     # -> mc1.21.11/build/libs
./gradlew -p mc1.21.1 build      # -> mc1.21.1/build/libs
```

Client-side only. Settings are in `config/vanilla-finest-helper.properties`.

## Source and licence

Vanilla Finest Helper is made for the Vanilla Finest modpacks and ships inside them; it is not published
on its own. The source is public so that players can see what it does with their folders and settings.

It is **not open source**: all rights reserved, see [LICENSE](LICENSE). Please don't copy the code into
other projects or include the mod in other modpacks without asking. Bug reports and questions are
welcome as issues.
