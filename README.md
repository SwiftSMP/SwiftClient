# SwiftClient

The SwiftSMP Minecraft client for Windows. It
installs Minecraft Java Edition through **Fabric** with a curated performance mod set, adds
**SwiftClient capes** that every SwiftClient player can see, pins **SwiftSMP and partner servers** such as
CyanRealms and ValenMC, lets players **import their own mods**, adds **friends and direct messages**,
shows **Discord Rich Presence** ("Playing on swiftsmp.net"), and updates itself.
In game it has its own title screen, a Servers screen for partners, a Right Shift
menu with movable HUD mods, and SwiftSMP pinned at the top of the server list.

Nothing from Mojang or the mod authors is bundled. Minecraft, the Java runtime and the
libraries come from Mojang's official servers, Fabric from meta.fabricmc.net, and the mods
from Modrinth, all downloaded on the player's PC at first launch and checked against their
published hashes.

```
SwiftClient/
├─ launcher/       Electron app (UI, Microsoft sign-in, installer, mod manager, Discord)
├─ mod/            "swiftclient" Fabric mod: in-game UI, HUD mods, capes, server pin, presence
└─ cape-server/    Node server for capes, player counts, friends, DMs and redeem codes
```

## How it works

| Piece | What it does |
|---|---|
| Sign-in | Microsoft OAuth (authorization code + PKCE) in an isolated window on Microsoft's own page → Xbox Live → XSTS → Minecraft services. Only the refresh token is stored, encrypted with Windows DPAPI (`safeStorage`). Passwords never touch SwiftClient. |
| Install | `launcher/src/main/minecraft.js`: version manifest, client jar, libraries, assets and Mojang's own Java runtime for that version (Java 21 for 1.21.x, Java 25 for 26.x), plus the newest stable Fabric loader. |
| Versions | Every Minecraft release from 1.21 to the newest one that Fabric supports (picker on the Play screen). |
| Performance | `launcher/src/main/mods.js` `CURATED`: Sodium, Lithium, FerriteCore, ImmediatelyFast, EntityCulling, More Culling, BadOptimizations, ModernFix, Krypton, Dynamic FPS, plus optional Sodium Extra, Reese's Sodium Options, Mod Menu, Zoomify, Iris. The newest Modrinth build for the chosen Minecraft version is picked (release before beta before alpha), required libraries are added automatically, and a mod with no build for that version is skipped with a notice. G1 JVM flags tuned for short pauses. |
| Your mods | Mods tab: drop `.jar` files or use Import. Each jar is checked: Fabric (not Forge/NeoForge/Quilt), the Minecraft version range in `fabric.mod.json`, not already included, not OptiFine/Canvas-style conflicts, `breaks` declarations, missing dependencies (warning). Each version has its own isolated folder in `%APPDATA%\.swiftclient\instances\<version>`. |
| Capes | Players pick a cape in the Cosmetics tab. The launcher proves who they are with Mojang's `join`/`hasJoined` handshake (the same one Minecraft servers use). That check is tied to the player's IP, so when Mojang will not confirm it the launcher retries with its Minecraft access token, which the cape server verifies with Mojang and never stores or logs. The mod looks up each player it renders and swaps in their cape (and elytra, unless they have their own). |
| Servers | SwiftSMP, CyanRealms and ValenMC are pinned in the server list from `servers` in the launcher config; the in-game title screen's **Servers** screen lists them with a PARTNER tag and joins in one click. |
| Friends | The Friends tab adds players by Minecraft name, accepts or declines requests, and keeps a direct-message thread per friend. Every action proves the player through the same Mojang challenge as capes, and only friends can read or send messages. |
| Discord | `launcher/src/main/discord.js` talks to Discord's local pipe. The mod prints join/leave events that the launcher turns into "Playing on swiftsmp.net", "Playing Singleplayer", "In the menus". On versions without the SwiftClient mod it falls back to reading Minecraft's "Connecting to…" log line. |
| Player counts | The Play screen shows who is on SwiftSMP (a normal Minecraft server ping, no third-party status site) and how many SwiftClient players are online and how many installs exist (anonymous counters on the cape server; switch off with *Show live SwiftClient player counts*). |
| Updates | `electron-updater` checks GitHub Releases on start and every hour, downloads in the background and installs on restart. If a build cannot install updates itself, the launcher still finds the newest release on GitHub and offers a one-click download. |

## In game (the SwiftClient mod)

| Feature | Details |
|---|---|
| Title screen | Replaces the vanilla one: panorama under a navy glass wash, SwiftClient logo, an orange **Join SwiftSMP** button (connects straight to `server.address`), glass Singleplayer / Multiplayer / Servers / Options / Quit, and SwiftClient / Language / Mods pills top right. |
| Server list | SwiftSMP is added to Multiplayer if missing and moved back to the top on every start and whenever that screen opens. An old `play.swiftsmp.net` entry is rewritten to the current address. Other servers keep their order. |
| SwiftClient menu | **Right Shift**. Sidebar with Mods / Visibility / Tweaks / Server, type-to-search, glass cards with ENABLED/DISABLED bars, scrolling. **Edit HUD** opens the layout editor. |
| HUD mods | FPS, CPS, Ping, Coordinates (with facing), Clock, Keystrokes (WASD, mouse with CPS, jump), Armor status (durability colours), SwiftSMP badge (players online, only on SwiftSMP). Positions are stored as screen fractions so they survive resolution changes. |
| HUD editor | Drag to move (snaps to edges and centre lines), scroll to resize (50–250 %), right-click to hide, Reset, Done. |
| Lyrics | **F7** toggles synced sky lyrics: the current karaoke line floats in front of you (sung word in white, the rest in orange) while your music plays in any Windows media app (Spotify, Apple Music, YouTube Music, browsers) or, on Android, the active media session. Words come from LRCLIB and are cached on disk; only the title and artist are sent. A Dynamic-Island-style now-playing pill (PANEL_2 navy) sits top-centre, and **Bass pulse** pumps the screen and words to the music (real WASAPI low-frequency capture where it works, otherwise word onsets). Height, size and a ± sync offset are set in the Lyrics tab. Built into every 26.x and 1.21.x jar; on 1.21.x the line is drawn as a screen billboard instead of world-space text. |
| Visibility | **F6** toggles all SwiftClient HUD mods. Switches to hide the vanilla scoreboard, boss bar, potion effects, titles and held-item name (Fabric `HudElementRegistry.replaceElement`). |
| Tweaks | Auto sprint, Bright mode (max brightness, restores your old value), Zoom (**C**, 2–10×, eased), show/hide your own cape. |
| Capes | Fetched per player from the cape server, cached, never blocking a frame. |
| Vanilla screens | Every vanilla button (pause menu, Options, Multiplayer, icon buttons...) gets SwiftSMP glass with an orange hover; sliders get a glass track, orange fill and white knob (`AbstractButtonMixin`, `AbstractSliderButtonMixin`). Labels and icons stay vanilla. |
| Pause menu | A SwiftClient button under the vanilla pause buttons opens the Right Shift menu. |

Settings live in `<game dir>/config/swiftclient.json`. Fonts: Pixelify Sans ships inside the mod as
a TTF font provider (`assets/swiftclient/font/pixel.json`).

Minecraft 26.3 notes for whoever ports this: the game moved from GLFW to SDL, so key codes come from
`InputConstants.KEY_*` and mouse buttons are SDL-numbered (`MOUSE_BUTTON_LEFT = 1`, `RIGHT = 3`, not 0/1).
Item components are only bound once a world loads, so the title screen cannot draw item icons.

The 26.x sky-lyrics renderer (`SubmitTextDisplay`) is built against 26.1 - 26.3, which differ:
`LevelRenderState.worldPartialTicks` became `Minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)`,
`GameRenderer.mainCamera()` is gone on 26.1 (read `CameraRenderState.pos` instead), and
`PoseStack.rotate(Quaternion)` exists only on 26.3 (use `mulPose(new Matrix4f().rotation(q))`, valid on all
three). All three stable calls are used so one source compiles on every 26.x.

## One-time setup (things only you can do)

1. **Microsoft sign-in (required)**
   1. Azure portal → *App registrations* → *New registration*. Name `SwiftClient`; supported account types **Personal Microsoft accounts only**; redirect URI platform **Public client/native (mobile & desktop)** with `https://login.microsoftonline.com/common/oauth2/nativeclient`.
   2. In *Authentication* enable **Allow public client flows**. No client secret is needed.
   3. New apps must be approved for the Minecraft API: submit the form at <https://aka.ms/mce-reviewappid> with the Application (client) ID. Until Mojang approves it, sign-in fails at the last step with HTTP 403.
   4. Put the client ID in `launcher/swiftclient.config.json` → `msClientId`.
2. **Cape server**: production runs `cape-server/server.js` on the VPS behind nginx at
   `https://capes.swiftsmp.com`, the default `capeApi` (see below). The Cloudflare Worker in
   `cape-server/worker/` is the alternative implementation.
3. **Discord**: <https://discord.com/developers/applications> → New Application named **SwiftClient** (Discord shows "Playing SwiftClient"). In *Rich Presence → Art Assets* upload the logo with the key `logo`. Put the Application ID in `discordClientId`.
4. **Releases**: create the GitHub repository the updater reads (currently `SwiftSMP/SwiftClient` in `launcher/package.json` → `build.publish` and `repository`; change both if it lives elsewhere). It must be **public** so players can download installers and updates. The website links to `https://github.com/SwiftSMP/SwiftClient/releases/latest/download/SwiftClient-Setup.exe`; update `CLIENT_DOWNLOAD` in the website's `app.js` and `SeoPages.java` if you change the repo.

`launcher/swiftclient.config.json`:

```json
{
  "msClientId": "00000000-0000-0000-0000-000000000000",
  "capeApi": "https://capes.swiftsmp.com",
  "discordClientId": "000000000000000000",
  "server": { "name": "SwiftSMP", "address": "swiftsmp.net" },
  "website": "https://swiftsmp.com",
  "minVersion": "1.21",
  "servers": [
    { "name": "SwiftSMP", "address": "swiftsmp.net", "partner": false, "pinned": true },
    { "name": "CyanRealms", "address": "play.cyanrealms.com", "partner": true, "pinned": true },
    { "name": "ValenMC", "address": "play.valenmc.fun", "partner": true, "pinned": true }
  ]
}
```

## Building

> Microsoft Store (MSIX) build: see [STORE.md](STORE.md). Run `npm run dist:store` in `launcher/`
> after filling in the Partner Center identity values.

Requirements: Node 20+ (tested with 24), JDK 25 (for the mod), internet access.

```bash
# 1. the Fabric mod (one build per Minecraft version; see "Mod for more versions")
cd mod
./gradlew dist            # 26.3 -> mod/dist/swiftclient-1.3.0+26.3.jar and index.json
./gradlew dist -Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2
./gradlew dist -Pminecraft_version=26.1 -Pfabric_api_version=0.155.3+26.1.2
node build-1.21.js         # all 12 1.21.x releases from mod/legacy
node build-1.21.js 1.21.8  # or just one

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

1. Bump `version` in `launcher/package.json` (and `mod/gradle.properties` if the mod changed; rebuild with `./gradlew dist` for each supported version, see below).
2. `set GH_TOKEN=<token with repo scope>` then `npm run release`. This builds the installer and uploads `SwiftClient-Setup.exe`, its `.blockmap` and `latest.yml` to a GitHub release (created as a draft: publish it with `gh release edit v<version> --draft=false`, or on GitHub).
3. Running launchers pick it up within the hour (or on next start) and install on restart. A draft or unpublished release is never offered, and neither is one whose `latest.yml` is missing: keep both files attached to every release so the old clients (which read `latest.yml`) can update.

The installer is not code-signed, so Windows SmartScreen warns on first run ("More info →
Run anyway"). To remove the warning, buy an OV/EV code-signing certificate and set
`CSC_LINK`/`CSC_KEY_PASSWORD` before `npm run dist`.

### Mod for more versions

The launcher reads `mod/dist/index.json` (`{"<mc version>": "<jar>"}`) and installs the
matching jar; versions without one still get all performance mods, Discord presence (via log
parsing) and imported mods, but no SwiftClient capes or window title (the picker shows
"no capes"). **26.1, 26.2 and 26.3** are built from `mod/`; add another version with
`./gradlew dist -Pminecraft_version=<mc> -Pfabric_api_version=<fabric api build for that version>`
(each run adds its jar to `index.json`).

**Every 1.21.x release (1.21 - 1.21.11)** is built from `mod/legacy`, which reuses the same
menu, HUD, config and cape code as `mod/src/main` but keeps a small platform layer per
rendering era: `src/platform/1.21.9` (1.21.9 - 1.21.11), `1.21.6` (1.21.6 - 1.21.8) and
`1.21.4` (1.21 - 1.21.5), plus `legacy` shared by the two older eras. The eras differ in
matrix API (`Matrix3x2fStack` vs `PoseStack`), resource names (`Identifier` vs
`ResourceLocation`), skin textures (`ClientAsset` vs `ResourceLocation` capes) and mouse/key
events; the shared layer hides all of that. `node mod/build-1.21.js` builds all twelve jars
into `mod/dist` and adds them to `index.json` (`node mod/build-1.21.js 1.21.8` builds one).
The zoom tweak changes the FOV option on 1.21.x instead of hooking `GameRenderer`, because
`getFov` returns `double` up to 1.21.3 and `float` afterwards.

## Cape server

Production runs `cape-server/server.js` (zero dependencies, Node 18+) behind nginx at
`https://capes.swiftsmp.com`, supervised by systemd. Capes ship in `capes/catalog.json` plus
`capes/<id>.png`; mutable state lives in `data/`: `selections.json`, `grants.json`,
`installs.json`, `codes.json` and `social.json` (friends, requests and DMs).

```bash
# on the server, from the cape-server directory
node --check server.js
systemctl restart swiftclient-capes
curl -s https://capes.swiftsmp.com/health   # {"ok":true,"capes":6,"version":2}
```

- Catalog: 64×32 Minecraft cape layout; HD 128×64 also works. Regenerate the defaults with
  `python tools/make_capes.py`, then copy `capes/` to the server and restart.
- `"public": true` capes are free for everyone. Others need a grant in `data/grants.json`,
  e.g. `{"<uuid without dashes>": ["staff", "founder"]}`. Staff and founder codes are created
  in the launcher by the admin UUID and stored only as SHA-256 hashes.
- `POST /social/<action>` covers `state`, `request`, `accept`, `decline`, `remove`, `messages`
  and `send`. Every call proves the player with a fresh challenge, and DMs are limited to
  accepted friends.
- `/health` includes `version: 2`, which is the quickest way to tell whether the social routes
  are deployed; a stale deploy answers 404 on `/social/state`.

`cape-server/worker/` is the Cloudflare Worker variant (Workers KV, same API). It is not what
production serves: Mojang answers 403 to session checks from Worker egress IPs, which is why
the Node server exists.

API: `GET /capes`, `GET /capes/<id>.png`, `GET /players/<uuid>.json`,
`GET /players/<uuid>/available`, `GET /stats`; `POST /stats/heartbeat {id, version}`,
`POST /auth/challenge`, `POST /select {name, serverId, cape, accessToken?}`,
`POST /social/<action> {name, serverId, accessToken?, ...}`,
`POST /redeem {name, serverId, code, accessToken?}` and `POST /admin/codes`.

Cape selection either passes Mojang's `hasJoined` check or, when Mojang will not confirm the
session (it binds a `join` to the player's IP), uses the player's Minecraft access token, which
is verified against `api.minecraftservices.com` and immediately discarded. A challenge is only
spent when verification succeeds, so the launcher can retry. Stats are anonymous: a random
install id, the version and a heartbeat; `/stats` returns `{users, online}` with a 25-minute
presence TTL.

## Android

**SwiftClient for Android** is a full Java Edition launcher built on Amethyst (a PojavLauncher fork),
source in [SwiftSMP/SwiftClient-Android](https://github.com/SwiftSMP/SwiftClient-Android). Picking a
version and tapping **Play** sets up Fabric, the mod (from this repo's latest release), Fabric API and an
Android-safe performance pack in one profile per version, and passes the cape server automatically. The APK is served from
`https://swiftclient.swiftsmp.net/downloads/SwiftClient.apk` (see [website/README.md](website/README.md)).

### Manual setup in PojavLauncher or another Android launcher

1. Install the Minecraft version you want in PojavLauncher **with Fabric**.
2. Download `swiftclient-<minecraft version>.jar` for that version from the
   [latest release](https://github.com/SwiftSMP/SwiftClient/releases/latest) — every supported
   version is attached there as its own asset — and drop it into that instance's `mods` folder.
3. In the instance's **JVM arguments**, add
   `-Dswiftclient.capeApi=https://capes.swiftsmp.com`. Optionally also
   `-Dswiftclient.server.address=swiftsmp.net -Dswiftclient.server.name=SwiftSMP`.
   Without `capeApi` the mod logs `capes: off` and skips cape lookups entirely.

What works: capes, the HUD mods, the SwiftClient title screen, the SwiftSMP server pin and the
vanilla-screen theme. Caveats: the Right Shift menu and Zoom need a physical keyboard — map them in
Pojav's controls, because touch input does not reach the mod's key checks. Discord Rich Presence is
launcher-side and therefore unavailable on Android. There is no SwiftClient updater on mobile; update
by downloading a newer jar.

## Accounts

SwiftClient signs in with Microsoft only. There is no offline/"cracked" account mode for people who
do not own Minecraft: that would distribute and run Minecraft without a licence, against Mojang's
EULA, and would put the GitHub releases and the SwiftSMP name at risk of takedowns. Players who use
other launchers can still join SwiftSMP as before.

The account menu in the title bar shows the signed-in Microsoft account, **Add account** (Microsoft
sign-in) and **Sign out**. There are no offline names.

Without signing in, the launcher still opens ("Not now" on the sign-in card) so players can set up
mods and settings; only Play asks for the account.

To test game launches without signing in, use `node scripts/smoke-install.js` (see Building).

## Licenses

SwiftClient's own code is © SwiftSMP. Third-party mods are **not redistributed**: the
launcher downloads them from Modrinth on the player's machine, as any launcher does (Sodium
is under the Polyform Shield license, others are LGPL/MIT/etc.; each stays under its own
license). Minecraft is © Mojang/Microsoft and is downloaded from Mojang. SwiftClient is not
affiliated with Mojang or Microsoft. Fonts: Pixelify Sans and Instrument Sans (SIL OFL,
`launcher/src/renderer/fonts`).
