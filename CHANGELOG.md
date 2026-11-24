# Changelog

## 1.0.0 (not released yet)

First version as the players' side of the Vanilla Finest modpacks. The earlier helper builds (never
published) also held the fixes for the packs' mods; those moved to Vanilla Finest Fixes.

- **Folders (all versions):** where Flashback saves and lists replays (without Flashback Settings; the
  helper redirects `Flashback.getReplayFolder`) and where Axiom keeps blueprints (Axiom's own
  `blueprint.customPath` setting in `config/axiom/axiom.json`, which the helper writes). Empty means the
  mod's own folder in the instance. Settings screen: Mod Menu → Vanilla Finest Helper → Configure (with
  Cloth Config): shared Minecraft folder, this instance, or another folder.
- **Home folders (all versions), off by default:** with `homefolders.enabled=true` in
  `config/vanilla-finest-helper.properties`, the first start of an instance points Screenshot Gallery
  (2.x), Litematica, Flashback (Flashback Settings, or the Folders setting) and Axiom at the shared
  Minecraft folder (`.minecraft`, found like One World Folder finds it). It runs once per instance and
  keeps a folder the player already chose.
