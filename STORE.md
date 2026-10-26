# Publishing SwiftClient on the Microsoft Store

Why: installs from the Microsoft Store show **no SmartScreen warning**. Microsoft signs Store
packages itself, so no paid code-signing certificate is needed. The current
`SwiftClient-Setup.exe` stays available as a fallback download.

What's already done in this repo:
- `npm run dist:store` (in `launcher/`) builds `dist/SwiftClient-Store.appx`, the MSIX package format
  (`.appx` and `.msix` are the same thing to Partner Center).
- Store tiles in SwiftSMP navy/orange are generated into `launcher/build/appx/` by
  `build/make_store_assets.py`.
- Capabilities are kept minimal: `internetClient`, plus `runFullTrust`, which every Electron app needs.
- The Store build stores game files, mods, logs and settings in the package's own
  `LocalState\.swiftclient` folder (see `storeRoot()` in `src/main/config.js`). Packaged apps get their
  AppData writes redirected by Windows, which would make Fabric crash, so this is required.
- The Store build turns off SwiftClient's own updater and the GitHub "new version" banner. The Store
  delivers updates instead.
- Microsoft sign-in works the same in the Store build.

---

## 1. Create the Partner Center developer account (free)

1. Go to <https://storedeveloper.microsoft.com> and choose **Individual developer**. Registration is
   free for individuals.
2. Sign in with the Microsoft account that should own the listing.
3. Complete the identity verification Microsoft asks for (government ID and selfie). Enter these
   yourself; verification can take a day or two.

## 2. Reserve the name

1. In Partner Center: **Apps and games → New product → MSIX or PWA app**.
2. Reserve **SwiftClient**. If it's taken, try e.g. *SwiftClient for SwiftSMP*. The name can't contain
   Minecraft, Mojang or Microsoft.

## 3. Copy the identity values into the build

Open the app in Partner Center → **Product management → Product identity**. Copy the three values into
`launcher/package.json` → `build.appx`, replacing the `REPLACE_WITH_…` placeholders:

| Partner Center field | `package.json` key |
|---|---|
| Package/Identity/Name (e.g. `12345SwiftSMP.SwiftClient`) | `identityName` |
| Package/Identity/Publisher (e.g. `CN=1A2B3C4D-…`) | `publisher` |
| Package/Properties/PublisherDisplayName | `publisherDisplayName` |

The build fails on purpose until these are filled in.

## 4. Build the package

From a normal Command Prompt or PowerShell window (not inside the Claude app: its sandbox stops
Windows' `makeappx.exe` from starting):

```
cd C:\Users\ACER\SwiftClient\mod
gradlew dist
cd ..\launcher
npm run dist:store
```

The result is `launcher\dist\SwiftClient-Store.appx`. Don't sign it: Microsoft signs it on upload.

To try the Store build on this PC first (Windows Developer Mode must be on), run
`build\build-store-test.cmd install`. It builds a copy with test identity values and installs it as
"SwiftClient". **Close your normal SwiftClient first**, because only one copy can run at a time.
Remove the test copy with `Get-AppxPackage SwiftSMP.SwiftClientTest | Remove-AppxPackage`.

## 5. Create the submission

In Partner Center → your app → **Start submission**:

- **Pricing and availability**: Free; all markets (or pick); visible in the Store.
- **Properties**: category **Games** (or *Utilities & tools*). Privacy policy URL:
  `https://swiftsmp.com/privacy` (host the draft below there first). Website: `https://swiftsmp.com/client`.
  Support contact: your support email or Discord.
- **Age ratings**: fill in the IARC questionnaire. Answer honestly: players can chat and play with
  others online (through Minecraft servers), there are no purchases in the launcher, and it shares
  the player's Minecraft username with servers they join.
- **Packages**: upload `SwiftClient-Store.appx`.
- **Store listings** (English):
  - Description: use the feature list from `marketing/announcement.md` (More FPS, capes, in-game menu,
    your own mods, one-click Join SwiftSMP). State clearly that **Minecraft: Java Edition is required**,
    that players sign in with their own Microsoft account, and that the game is downloaded from Mojang.
  - Screenshots: 1366×768 or larger. The ones in `marketing/` work (`launcher-play.png`,
    `ingame-title-screen.png`, `ingame-menu-mods.png`, `ingame-cape-hud.png`).
  - Logos: the Store uses the package tiles; you can also upload the 300×300 logo from `build/icon.png`.
- **Submission options → restricted capabilities**: Partner Center asks why the app needs
  `runFullTrust`. Suggested answer: *"SwiftClient is an Electron desktop app (Win32). It needs full trust
  to run its Electron runtime and to start the Java runtime that runs Minecraft: Java Edition, which the
  player signs in to with their own Microsoft account."*

Submit. Certification usually takes **a few days** (up to about a week). Microsoft emails you the
result.

**A possible review issue:** Store policy is strict about apps that download and run code after
install. SwiftClient downloads Minecraft and Java from Mojang and mods from Modrinth, like the
official Minecraft Launcher. If the reviewer objects, reply in the certification notes that it's a game
launcher: all downloads come from Mojang's and Modrinth's official servers, and nothing changes the
launcher itself. Some reviewers may still refuse. The `.exe` download stays as the fallback.

## 6. After it's live

- Add a **"Get it from Microsoft"** badge on swiftsmp.com/client that links to the Store listing
  (`https://apps.microsoft.com/detail/<Store ID>`; the Store ID is on the Product identity page).
  Keep the `.exe` download next to it.
- **Updates:** bump `version` in `launcher/package.json` and run `npm run dist:store`. In Partner
  Center: **Update** → upload the new `.appx` → submit. Each update is reviewed again (usually faster).
  Store versions must keep increasing.
- Players on the `.exe` version keep updating through GitHub releases as before. The two installs are
  separate apps with separate game folders.

---

## Privacy policy draft (host at swiftsmp.com/privacy)

> **SwiftClient privacy policy**
>
> SwiftClient is a Minecraft: Java Edition launcher made by SwiftSMP.
>
> **Sign-in.** You sign in with your Microsoft account on Microsoft's own sign-in page. SwiftClient
> never sees your password. It receives sign-in tokens from Microsoft, Xbox Live and Minecraft, and
> stores them encrypted on your PC so you stay signed in. They are only sent to Microsoft and Mojang.
>
> **What we receive.** When you use SwiftClient's online features, our servers receive your Minecraft
> username and UUID. This covers choosing a cape, showing capes of other SwiftClient players, friends
> and chat, and player counts. We use them only to run these features. SwiftClient also reports
> anonymous launcher counts (how many people are using it right now). You can turn this off in Settings.
>
> **Downloads.** SwiftClient downloads Minecraft and Java from Mojang, and mods from Modrinth. Those
> services see your IP address, as with any download.
>
> **Discord.** If Discord is running, SwiftClient shows what you are playing on your Discord profile.
> You can turn this off in Settings.
>
> **No ads, no selling data.** We don't show ads, and we don't sell or share your data with anyone else.
>
> **Contact / deletion.** To have your SwiftClient data (cape choice, friends) removed, contact us at
> <your support email or Discord>.
>
> SwiftClient is not affiliated with Mojang or Microsoft.
