# Changelog

## 1.0.0 (not released yet)

First version as the players' side of the Vanilla Finest modpacks. The earlier helper builds (never
published) also held the fixes for the packs' mods; those moved to Vanilla Finest Fixes.

- **Setup screen (all versions):** one screen for everything the player sets, in five steps: Welcome
  (Markdown text with the current key names, links), Settings (languages with Language Reload, mouse
  sensitivity, key bindings and the Multi Key Bindings profile, Keyboard Map with Keycaps, graphics API,
  Voxy on/off, GUI scale, render distance), Folders (Modpack / Minecraft / Desktop / Documents, or one
  folder at a time, written into each mod's own setting), Look (Packed Packs profile and shaders, with
  their descriptions as tooltips; preview pictures later) and Ready to play. Drawn like Options from the
  title screen (panorama, blur, the Minecraft logo, a fixed 430×262 panel) with Minecraft's own widgets,
  font and tooltips. Opens on the first start, from a settings icon on the title and pause screens, and
  from Mod Menu's Configure button. Texts in `config/vanilla-finest-helper/setup/` (English and
  Hungarian labels in the jar). The welcome text (`welcome.md`) and its link buttons (`welcome.links`) are the
  modpack's own, kept there; the jar has neither, and without a text the Welcome page says which file to
  create. The computer's language is added as a second language on the first start.
  The Look page lists every shader pack (it showed only the active one: Shader Folder's filter answer was
  read the wrong way round), with "None" first, as the pack's list goes from light to demanding.
  When a folder is kept outside the instance, a shortcut to it (and a `.txt` with the path) is written where
  the files would normally be, kept up to date at start-up, when the setup screen applies its folders and
  when a folder mod's settings screen closes.
  On 26.2, 26.1.2, 1.21.11 and 1.21.1 the folder picker is LWJGL's tinyfd; before 26.2 there is no graphics
  API to choose (a greyed-out "Graphics API: OpenGL" button) and Vulkan is never reported. On 1.21.11 (Voxy
  0.2.16) Voxy is switched off through its older renderer interface; 1.21.1 has no Voxy, and Screenshot
  Gallery 1.2 there has no folder setting, so that row is not shown.
- **Shader Folder tooltips (all versions):** Iris Shader Folder's one-language tooltip texts are shown in
  the game's language from `"shader.tooltips"` in the helper's `<lang>.json` (English text → translation),
  in Iris's shader list and on the setup screen. A pack with a tooltip rule shows only that tooltip, without
  the pack's own description above it (packs without a rule keep theirs).
  A written `\n` in a tooltip rule is a line break, and untranslated texts are translated line by line.
- **Packed Packs profile descriptions (all versions):** resource and data pack profile descriptions are
  shown in the game's language, line by line, from `"profile.descriptions"` in the helper's `<lang>.json`
  (in Packed Packs' profile list and on the setup screen). The profile files keep the English text.
- **GUI scale (all versions):** an automatic GUI scale from the window's width (`guiscale.rules`,
  `guiscale.mode` in `config/vanilla-finest-helper.properties`; 640×360 GUI pixels on 16:9 screens). Off
  while GUI Scaler is installed; the setup screen's slider then switches GUI Scaler's own setting.
- **Folders (all versions):** where Flashback saves and lists replays (without Flashback Settings; the
  helper redirects `Flashback.getReplayFolder`) and where Axiom keeps blueprints (Axiom's own
  `blueprint.customPath` setting in `config/axiom/axiom.json`, which the helper writes). Empty means the
  mod's own folder in the instance. No settings screen of its own and no Mod Menu or Cloth Config
  dependency: the setup screen (not built yet) will set these folders, and Mod Menu's Configure button
  will open it.
- **Home folders (all versions), off by default:** with `homefolders.enabled=true` in
  `config/vanilla-finest-helper.properties`, the first start of an instance points Screenshot Gallery
  (2.x), Litematica, Flashback (Flashback Settings, or the Folders setting) and Axiom at the shared
  Minecraft folder (`.minecraft`, found like One World Folder finds it). It runs once per instance and
  keeps a folder the player already chose.
- **Flashback 0.50.0** (26.3): checked and accepted by the Flashback module (replay folder).
