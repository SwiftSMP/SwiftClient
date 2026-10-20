# SwiftClient

The SwiftSMP Minecraft client for Windows. It is a launcher in the spirit of Lunar Client: it
installs Minecraft Java Edition through **Fabric** with a curated performance mod set, adds
**SwiftClient capes** that every SwiftClient player can see, lets players **import their own
mods**, shows **Discord Rich Presence** ("Playing on swiftsmp.net"), and updates itself.
In game it has its own Lunar-style title screen, a Right Shift menu with movable HUD mods,
and SwiftSMP pinned at the top of the server list.

Nothing from Mojang or the mod authors is bundled. Minecraft, the Java runtime and the
libraries come from Mojang's official servers, Fabric from meta.fabricmc.net, and the mods
from Modrinth, all downloaded on the player's PC at first launch and checked against their
published hashes.

```
SwiftClient/
├─ launcher/       Electron app (UI, Microsoft sign-in, installer, mod manager, Discord)
├─ mod/            "swiftclient" Fabric mod: in-game UI, HUD mods, capes, server pin, presence
└─ cape-server/    Tiny Node server for the shared cape catalog and player selections
```

## How it works

| Piece | What it does |
|---|---|
| Sign-in | Microsoft OAuth (authorization code + PKCE) in an isolated window on Microsoft's own page → Xbox Live → XSTS → Minecraft services. Only the refresh token is stored, encrypted with Windows DPAPI (`safeStorage`). Passwords never touch SwiftClient. |
| Install | `launcher/src/main/minecraft.js`: version manifest, client jar, libraries, assets and Mojang's own Java runtime for that version (Java 21 for 1.21.x, Java 25 for 26.x), plus the newest stable Fabric loader. |
| Versions | Every Minecraft release from 1.21 to the newest one that Fabric supports (picker on the Play screen). |
| Performance | `launcher/src/main/mods.js` `CURATED`: Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, More Culling, BadOptimizations, ModernFix, Krypton, Dynamic FPS, plus optional Sodium Extra, Reese's Sodium Options, Mod Menu, Zoomify, Iris. The newest Modrinth build for the chosen Minecraft version is picked (release before beta before alpha), required libraries are added automatically, and a mod with no build for that version is skipped with a notice. G1 JVM flags tuned for short pauses. |
| Your mods | Mods tab: drop `.jar` files or use Import. Each jar is checked: Fabric (not Forge/NeoForge/Quilt), the Minecraft version range in `fabric.mod.json`, not already included, not OptiFine/Canvas-style conflicts, `breaks` declarations, missing dependencies (warning). Each version has its own isolated folder in `%APPDATA%\.swiftclient\instances\<version>`. |
| Capes | Players pick a cape in the Cosmetics tab. The launcher proves who they are to the cape server with Mojang's `join`/`hasJoined` handshake (the same one Minecraft servers use), so the access token never leaves Mojang. The mod looks up each player it renders and swaps in their cape (and elytra, unless they have their own). |
| Discord | `launcher/src/main/discord.js` talks to Discord's local pipe. The mod prints join/leave events that the launcher turns into "Playing on swiftsmp.net", "Playing Singleplayer", "In the menus". On versions without the SwiftClient mod it falls back to reading Minecraft's "Connecting to…" log line. |
| Updates | `electron-updater` checks GitHub Releases every 6 hours and installs on restart. |

## In game (the SwiftClient mod)

| Feature | Details |
|---|---|
| Title screen | Replaces the vanilla one: panorama under a navy glass wash, SwiftClient logo, an orange **Join SwiftSMP** button (connects straight to `server.address`), glass Singleplayer / Multiplayer / Options / Quit, and SwiftClient / Language / Mods pills top right. |
| Server list | SwiftSMP is added to Multiplayer if missing and moved back to the top on every start and whenever that screen opens. An old `play.swiftsmp.net` entry is rewritten to the current address. Other servers keep their order. |
| SwiftClient menu | **Right Shift**. Sidebar with Mods / Visibility / Tweaks / Server, type-to-search, glass cards with ENABLED/DISABLED bars, scrolling. **Edit HUD** opens the layout editor. |
| HUD mods | FPS, CPS, Ping, Coordinates (with facing), Clock, Keystrokes (WASD, mouse with CPS, jump), Armor status (durability colours), SwiftSMP badge (players online, only on SwiftSMP). Positions are stored as screen fractions so they survive resolution changes. |
| HUD editor | Drag to move (snaps to edges and centre lines), scroll to resize (50–250 %), right-click to hide, Reset, Done. |
| Visibility | **F6** toggles all SwiftClient HUD mods. Switches to hide the vanilla scoreboard, boss bar, potion effects, titles and held-item name (Fabric `HudElementRegistry.replaceElement`). |
| Tweaks | Auto sprint, Bright mode (max brightness, restores your old value), Zoom (**C**, 2–10×, eased), show/hide your own cape. |
| Capes | Fetched per player from the cape server, cached, never blocking a frame. |
| Vanilla screens | Every vanilla button (pause menu, Options, Multiplayer, icon buttons...) gets SwiftSMP glass with an orange hover; sliders get a glass track, orange fill and white knob (`AbstractButtonMixin`, `AbstractSliderButtonMixin`). Labels and icons stay vanilla. |

Settings live in `<game dir>/config/swiftclient.json`. Fonts: Pixelify Sans ships inside the mod as
a TTF font provider (`assets/swiftclient/font/pixel.json`).

Minecraft 26.3 notes for whoever ports this: the game moved from GLFW to SDL, so key codes come from
`InputConstants.KEY_*` and mouse buttons are SDL-numbered (`MOUSE_BUTTON_LEFT = 1`, `RIGHT = 3`, not 0/1).
Item components are only bound once a world loads, so the title screen cannot draw item icons.

## One-time setup (things only you can do)

1. **Microsoft sign-in (required)**
   1. Azure portal → *App registrations* → *New registration*. Name `SwiftClient`; supported account types **Personal Microsoft accounts only**; redirect URI platform **Public client/native (mobile & desktop)** with `https://login.microsoftonline.com/common/oauth2/nativeclient`.
   2. In *Authentication* enable **Allow public client flows**. No client secret is needed.
   3. New apps must be approved for the Minecraft API: submit the form at <https://aka.ms/mce-reviewappid> with the Application (client) ID. Until Mojang approves it, sign-in fails at the last step with HTTP 403.
   4. Put the client ID in `launcher/swiftclient.config.json` → `msClientId`.
2. **Cape server**: deploy `cape-server/` (see below) at e.g. `https://capes.swiftsmp.net` and set `capeApi`.
3. **Discord**: <https://discord.com/developers/applications> → New Application named **SwiftClient** (Discord shows "Playing SwiftClient"). In *Rich Presence → Art Assets* upload the logo with the key `logo`. Put the Application ID in `discordClientId`.
4. **Releases**: create the GitHub repository the updater reads (currently `SwiftSMP/SwiftClient` in `launcher/package.json` → `build.publish` and `repository`; change both if it lives elsewhere). It must be **public** so players can download installers and updates. The website links to `https://github.com/SwiftSMP/SwiftClient/releases/latest/download/SwiftClient-Setup.exe`; update `CLIENT_DOWNLOAD` in the website's `app.js` and `SeoPages.java` if you change the repo.

`launcher/swiftclient.config.json`:

```json
{
  "msClientId": "00000000-0000-0000-0000-000000000000",
  "capeApi": "https://capes.swiftsmp.net",
  "discordClientId": "000000000000000000",
  "server": { "name": "SwiftSMP", "address": "swiftsmp.net" },
  "website": "https://swiftsmp.net",
  "minVersion": "1.21"
}
```

## Building

Requirements: Node 20+ (tested with 24), JDK 25 (for the mod), internet access.

```bash
# 1. the Fabric mod (one build per Minecraft version; see "Mod for more versions")
cd mod
./gradlew dist            # -> mod/dist/swiftclient-1.0.0+26.3.jar and mod/dist/index.json

# 2. the launcher
cd ../launcher
npm install
npm test                  # unit tests (version ranges, mod checks, presence, cape server)
npm start                 # run from source
npm run dist              # -> launcher/dist/SwiftClient-Setup.exe (NSIS installer)
```

The installer is per-user (no admin prompt), lets the player choose the folder, and creates
Desktop and Start-menu shortcuts. Game data lives in `%APPDATA%\.swiftclient` and is kept on
uninstall.

`node scripts/smoke-install.js 26.3 [--run]` installs a version without the UI and prints the
launch command (test session without a Microsoft account; `--run` starts the game, which reaches
the title screen but cannot join online-mode servers). Useful environment variables:
`SWIFTCLIENT_HOME` (data folder), `CAPE_API` (e.g. `http://127.0.0.1:8765`), `QUICKPLAY_WORLD`
(open a saved world directly) and `SWIFT_DEBUG` (`cape` = third-person camera after 2 s, `menu` =
open the menu after 10 s and the Visibility tab after 15 s, `editor` = open the HUD editor after 20 s, `options` = open vanilla Options from the title screen;
these map to `-Dswiftclient.debug=...` and do nothing in normal play).

> Testing from inside a packaged (MSIX) app such as the Claude desktop app: Windows redirects
> `%APPDATA%` writes to `AppData\Local\Packages\<app>\LocalCache\Roaming`, and Fabric then sees
> two paths for the same jars ("IMixinConfigPlugin cannot be cast"). Set `SWIFTCLIENT_HOME` to the
> LocalCache path. A normally installed SwiftClient is not affected.

### Releasing an update

1. Bump `version` in `launcher/package.json` (and `mod/gradle.properties` if the mod changed; rebuild with `./gradlew dist`).
2. `set GH_TOKEN=<token with repo scope>` then `npm run release`. This builds the installer and uploads `SwiftClient-Setup.exe`, its `.blockmap` and `latest.yml` to a GitHub release (created as a draft: publish it on GitHub).
3. Running launchers pick it up within 6 hours (or on next start) and update on restart.

The installer is not code-signed, so Windows SmartScreen warns on first run ("More info →
Run anyway"). To remove the warning, buy an OV/EV code-signing certificate and set
`CSC_LINK`/`CSC_KEY_PASSWORD` before `npm run dist`.

### Mod for more versions

The SwiftClient mod is built for **26.3**. The launcher reads `mod/dist/index.json`
(`{"<mc version>": "<jar>"}`) and installs the matching jar; versions without one still get
all performance mods, Discord presence (via log parsing) and imported mods, but no SwiftClient
capes or window title (the picker shows "no capes"). To add a version, change
`minecraft_version`, `fabric_api_version` and `loader_version` in `mod/gradle.properties`
and run `./gradlew dist` again: each run adds its jar to `index.json`. 26.1 and newer use
Mojang's own names, so the code builds as is if the classes did not move. For 1.21.x the
mixins must be ported to Yarn/intermediary mappings (`PlayerSkin`/`SkinTextures` differ).

## Cape server

```bash
cd cape-server
node server.js            # PORT=8765 HOST=127.0.0.1 by default
```

Zero dependencies. Put it behind your existing reverse proxy with HTTPS (Caddy/nginx), e.g.
`capes.swiftsmp.net → 127.0.0.1:8765`, and run it with systemd or pm2.

- Catalog: `capes/catalog.json` + `capes/<id>.png` (64×32 Minecraft cape layout; HD 128×64 works too). Regenerate the defaults with `python tools/make_capes.py`.
- `"public": true` capes are free for everyone. Others need a grant in `data/grants.json`: `{"<uuid without dashes>": ["staff", "founder"]}` (read on every request, no restart needed).
- Selections are stored in `data/selections.json`.

API: `GET /capes`, `GET /capes/<id>.png`, `GET /players/<uuid>.json`, `GET /players/<uuid>/available`, `POST /auth/challenge`, `POST /select {name, serverId, cape}`.

## Accounts

SwiftClient signs in with Microsoft only. There is no offline/"cracked" account mode for people who
do not own Minecraft: that would distribute and run Minecraft without a licence, against Mojang's
EULA, and would put the GitHub releases and the SwiftSMP name at risk of takedowns. Players who use
other launchers can still join SwiftSMP as before.

## Licenses

SwiftClient's own code is © SwiftSMP. Third-party mods are **not redistributed**: the
launcher downloads them from Modrinth on the player's machine, as any launcher does (Sodium
is under the Polyform Shield license, others are LGPL/MIT/etc.; each stays under its own
license). Minecraft is © Mojang/Microsoft and is downloaded from Mojang. SwiftClient is not
affiliated with Mojang or Microsoft. Fonts: Pixelify Sans and Instrument Sans (SIL OFL,
`launcher/src/renderer/fonts`).
