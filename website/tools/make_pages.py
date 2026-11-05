"""Builds the Android and Download pages from index.html's head, nav and footer.

Run from the website folder after editing index.html:  python tools/make_pages.py
"""
import os
import re

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
os.chdir(HERE)
home = open('index.html', encoding='utf-8').read()

head_common = home[home.index('  <link rel="icon"'):home.index('  <script type="application/ld+json">')]
head_common = head_common.replace('  <link rel="preload" href="/assets/img/ingame-title.webp" as="image" fetchpriority="high">\n', '')
head_common = re.sub(r'  <meta name="keywords"[^\n]*\n', '', head_common)
nav = home[home.index('  <a class="skip"'):home.index('  <main id="main">')]
footer = home[home.index('  <footer class="footer">'):]
platforms = home[home.index('        <div class="plat">'):home.index('    </section>\n\n    <section class="sec" id="android">')]


def page(path, title, desc, canonical, ld, active, body, og_title):
    n = nav.replace('<a class="nav-link active" href="/">', '<a class="nav-link" href="/">')
    if active:
        n = n.replace(f'<a class="nav-link" href="{active}">', f'<a class="nav-link active" href="{active}">', 1)
    h = head_common
    h = re.sub(r'<meta property="og:url" content="[^"]*">', f'<meta property="og:url" content="{canonical}">', h)
    h = re.sub(r'<meta property="og:title" content="[^"]*">', f'<meta property="og:title" content="{og_title}">', h)
    h = re.sub(r'<meta property="og:description" content="[^"]*">', f'<meta property="og:description" content="{desc}">', h)
    h = re.sub(r'<meta name="twitter:title" content="[^"]*">', f'<meta name="twitter:title" content="{og_title}">', h)
    h = re.sub(r'<meta name="twitter:description" content="[^"]*">', f'<meta name="twitter:description" content="{desc}">', h)
    html = f'''<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{title}</title>
  <meta name="description" content="{desc}">
  <meta name="robots" content="index, follow, max-image-preview:large, max-snippet:-1">
  <meta name="theme-color" content="#0D1020">
  <link rel="canonical" href="{canonical}">
{h}  <script type="application/ld+json">
{ld}
  </script>
</head>
<body>
{n}  <main id="main">
{body}
  </main>

{footer}'''
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, 'w', encoding='utf-8').write(html)


ANDROID_LD = '''  {"@context":"https://schema.org","@graph":[
    {"@type":"WebPage","@id":"https://swiftclient.swiftsmp.net/android/#webpage","url":"https://swiftclient.swiftsmp.net/android/","name":"SwiftClient for Android – Minecraft Java Edition APK","isPartOf":{"@id":"https://swiftclient.swiftsmp.net/#website"},"about":{"@id":"https://swiftclient.swiftsmp.net/android/#app"},"inLanguage":"en","dateModified":"2026-11-05"},
    {"@type":"SoftwareApplication","@id":"https://swiftclient.swiftsmp.net/android/#app","name":"SwiftClient for Android","applicationCategory":"GameApplication","applicationSubCategory":"Minecraft Java Edition launcher","operatingSystem":"Android 8+","softwareRequirements":"A Microsoft account that owns Minecraft: Java Edition","url":"https://swiftclient.swiftsmp.net/android/","downloadUrl":"https://swiftclient.swiftsmp.net/downloads/SwiftClient.apk","fileSize":"131MB","softwareVersion":"1.0.0","screenshot":["https://swiftclient.swiftsmp.net/assets/img/android-play.webp","https://swiftclient.swiftsmp.net/assets/img/android-servers.webp"],"offers":{"@type":"Offer","price":"0","priceCurrency":"USD"},"inLanguage":"en","sameAs":["https://github.com/SwiftSMP/SwiftClient-Android"],"publisher":{"@type":"Organization","name":"SwiftSMP","url":"https://swiftsmp.com/"}},
    {"@type":"BreadcrumbList","itemListElement":[{"@type":"ListItem","position":1,"name":"SwiftClient","item":"https://swiftclient.swiftsmp.net/"},{"@type":"ListItem","position":2,"name":"Android","item":"https://swiftclient.swiftsmp.net/android/"}]},
    {"@type":"HowTo","name":"How to play Minecraft Java Edition on Android with SwiftClient","step":[
      {"@type":"HowToStep","name":"Download the APK","text":"Download SwiftClient.apk from swiftclient.swiftsmp.net and allow your browser to install apps."},
      {"@type":"HowToStep","name":"Sign in","text":"Open SwiftClient and sign in with the Microsoft account that owns Minecraft: Java Edition."},
      {"@type":"HowToStep","name":"Pick a version and tap Play","text":"SwiftClient installs Fabric, the SwiftClient mod, Fabric API and a phone performance pack, then downloads Minecraft and Java from Mojang."},
      {"@type":"HowToStep","name":"Join a server","text":"Use the Servers tab to join SwiftSMP, CyanRealms or ValenMC in one tap."}]},
    {"@type":"FAQPage","mainEntity":[
      {"@type":"Question","name":"Can you play Minecraft Java Edition on Android?","acceptedAnswer":{"@type":"Answer","text":"Yes. SwiftClient for Android runs the real Minecraft: Java Edition on your phone with your Microsoft account, touch controls and Fabric mods."}},
      {"@type":"Question","name":"Do I need to own Minecraft Java Edition?","acceptedAnswer":{"@type":"Answer","text":"Yes. You sign in with the Microsoft account that owns Minecraft: Java Edition. There is no offline mode."}},
      {"@type":"Question","name":"Which phones work?","acceptedAnswer":{"@type":"Answer","text":"Android 8 or newer. A 64-bit phone with 4 GB of RAM or more is recommended; Minecraft 1.21.x runs best."}},
      {"@type":"Question","name":"How do I open the SwiftClient menu without a keyboard?","acceptedAnswer":{"@type":"Answer","text":"Add Right Shift as a button in Custom controls. Zoom (C) works the same way."}},
      {"@type":"Question","name":"Is it open source?","acceptedAnswer":{"@type":"Answer","text":"Yes. It is built on Amethyst and PojavLauncher (LGPL-3.0); the source is on GitHub at github.com/SwiftSMP/SwiftClient-Android."}}]}
  ]}'''

MC = 'https://assets.mcasset.cloud/26.2/assets/minecraft/textures/'

ANDROID_BODY = f'''    <section class="page-hero">
      <div class="shell split">
        <div>
          <nav class="crumbs" aria-label="Breadcrumb"><a href="/">SwiftClient</a> › Android</nav>
          <span class="eyebrow" style="margin-top:14px"><i class="fa-brands fa-android"></i> SwiftClient for Android</span>
          <h1>Minecraft <b>Java Edition</b> on Android</h1>
          <p class="lead">Play the real Java Edition on your phone, with your own Microsoft account, Fabric mods, SwiftClient capes and HUD mods, and SwiftSMP one tap away. Free.</p>
          <div class="cta" style="margin-top:22px">
            <a class="btn big primary" href="/downloads/SwiftClient.apk"><i class="fa-solid fa-download"></i><span class="two">Download APK<small>v1.0.0 · 131 MB</small></span></a>
            <a class="btn big" href="https://github.com/SwiftSMP/SwiftClient-Android"><i class="fa-brands fa-github"></i> Source code</a>
          </div>
          <div class="req">
            <div class="stat"><b>Android 8+</b><span>64-bit recommended</span></div>
            <div class="stat"><b>4 GB RAM</b><span>recommended</span></div>
            <div class="stat"><b>1.21 → 26.3</b><span>Minecraft versions</span></div>
          </div>
        </div>
        <div class="phones">
          <div class="phone"><img src="/assets/img/android-play.webp" alt="SwiftClient for Android Play tab with live player counts" width="540" height="1045" fetchpriority="high"></div>
          <div class="phone back"><img src="/assets/img/android-servers.webp" alt="SwiftClient for Android Servers tab" width="540" height="1045" loading="lazy"></div>
        </div>
      </div>
    </section>

    <section class="sec">
      <div class="shell">
        <div class="sec-head"><span class="eyebrow"><i class="fa-solid fa-list-ol"></i> Setup</span><h2>Playing in <b>four steps</b></h2></div>
        <ol class="steps" style="max-width:820px">
          <li><b>Download the APK</b> above. When Android asks, allow your browser to install apps.</li>
          <li><b>Sign in with Microsoft</b> using the account that owns Minecraft: Java Edition.</li>
          <li><b>Pick a version and tap Play.</b> SwiftClient installs Fabric, the SwiftClient mod, Fabric API and the phone performance pack, then downloads Minecraft and Java from Mojang.</li>
          <li><b>Join a server</b> from the Servers tab, or play singleplayer. Pick your cape in Cloaks.</li>
        </ol>
      </div>
    </section>

    <section class="sec">
      <div class="shell">
        <div class="sec-head"><span class="eyebrow"><i class="fa-solid fa-gauge-high"></i> Performance</span><h2>Tuned for <b>phones</b></h2>
          <p>Phones heat up and throttle. SwiftClient for Android is set up to stay smooth.</p></div>
        <div class="tiles">
          <article class="tile wide f-orange"><div><span class="k">Performance pack</span><h3>Phone-safe mods</h3><p>Lithium, FerriteCore, ModernFix and EntityCulling, newest build for your version. Renderer mods that break on phone GPUs are left out on purpose.</p></div>
            <img class="deco" src="{MC}item/sugar.png" alt="" loading="lazy"></article>
          <article class="tile wide f-gold"><div><span class="k">Smart defaults</span><h3>Settings that matter</h3><p>Render distance 8, fast graphics, no clouds, fewer particles and a 60 FPS cap on new installs. Change anything in game.</p></div>
            <img class="deco" src="{MC}item/clock_00.png" alt="" loading="lazy"></article>
          <article class="tile"><div><span class="k">Less stutter</span><h3>Tuned memory</h3><p>Garbage collection tuned for short pauses, so fewer hitches mid-fight.</p></div>
            <img class="deco" src="{MC}item/redstone.png" alt="" loading="lazy"></article>
          <article class="tile"><div><span class="k">Fast cores</span><h3>Big-core mode</h3><p>The game stays on your phone's fastest CPU cores by default.</p></div>
            <img class="deco" src="{MC}item/firework_rocket.png" alt="" loading="lazy"></article>
          <article class="tile"><div><span class="k">Controls</span><h3>Touch + gamepad</h3><p>On-screen controls you can edit, plus keyboard, mouse and controllers.</p></div>
            <img class="deco" src="{MC}item/compass_00.png" alt="" loading="lazy"></article>
          <article class="tile"><div><span class="k">Mods</span><h3>Mods tab</h3><p>Switch mods on and off or add your own Fabric jars.</p></div>
            <img class="deco" src="{MC}item/name_tag.png" alt="" loading="lazy"></article>
        </div>
      </div>
    </section>

    <section class="sec">
      <div class="shell">
        <div class="sec-head center"><span class="eyebrow"><i class="fa-solid fa-circle-question"></i> Questions</span><h2>Android FAQ</h2></div>
        <div class="faq">
          <details open><summary>Can you play Minecraft Java Edition on Android?</summary><p>Yes. SwiftClient for Android runs the real Minecraft: Java Edition on your phone with your Microsoft account, touch controls and Fabric mods.</p></details>
          <details><summary>Do I need to own Java Edition?</summary><p>Yes. Sign in with the Microsoft account that owns Minecraft: Java Edition. There is no offline mode.</p></details>
          <details><summary>Which phones work?</summary><p>Android 8 or newer. A 64-bit phone with 4 GB of RAM or more is recommended; Minecraft 1.21.x runs best, 26.x needs a stronger phone.</p></details>
          <details><summary>How do I open the SwiftClient menu without a keyboard?</summary><p>Add Right Shift as a button in Custom controls. Zoom (C) works the same way.</p></details>
          <details><summary>Is it open source?</summary><p>Yes. It is built on Amethyst and PojavLauncher (LGPL-3.0); the source is on <a style="color:var(--orange-hi)" href="https://github.com/SwiftSMP/SwiftClient-Android">GitHub</a>.</p></details>
        </div>
      </div>
    </section>

    <section class="sec" style="padding-top:10px">
      <div class="shell">
        <div class="banner">
          <img src="/assets/img/icon-256.png" alt="" width="84" height="84">
          <div class="banner-text"><h2>Java Edition, <b>in your pocket</b></h2><p>Free. Your account, your mods, your cape.</p></div>
          <div class="cta"><a class="btn big primary" href="/downloads/SwiftClient.apk"><i class="fa-solid fa-download"></i> Download APK</a></div>
        </div>
      </div>
    </section>'''

DOWNLOAD_LD = '''  {"@context":"https://schema.org","@graph":[
    {"@type":"WebPage","@id":"https://swiftclient.swiftsmp.net/download/#webpage","url":"https://swiftclient.swiftsmp.net/download/","name":"Download SwiftClient – Free Minecraft Client","isPartOf":{"@id":"https://swiftclient.swiftsmp.net/#website"},"about":{"@id":"https://swiftclient.swiftsmp.net/download/#app"},"inLanguage":"en","dateModified":"2026-11-05"},
    {"@type":"BreadcrumbList","itemListElement":[{"@type":"ListItem","position":1,"name":"SwiftClient","item":"https://swiftclient.swiftsmp.net/"},{"@type":"ListItem","position":2,"name":"Download","item":"https://swiftclient.swiftsmp.net/download/"}]},
    {"@type":"SoftwareApplication","@id":"https://swiftclient.swiftsmp.net/download/#app","name":"SwiftClient","applicationCategory":"GameApplication","operatingSystem":"Windows 10, Windows 11, Android 8+","softwareRequirements":"A Microsoft account that owns Minecraft: Java Edition","url":"https://swiftclient.swiftsmp.net/download/","downloadUrl":"https://github.com/SwiftSMP/SwiftClient/releases/latest/download/SwiftClient-Setup.exe","installUrl":"https://apps.microsoft.com/detail/9P118N2JM63C","offers":{"@type":"Offer","price":"0","priceCurrency":"USD"},"inLanguage":"en","sameAs":["https://github.com/SwiftSMP/SwiftClient","https://github.com/SwiftSMP/SwiftClient-Android"],"publisher":{"@type":"Organization","name":"SwiftSMP","url":"https://swiftsmp.com/"}}
  ]}'''

DOWNLOAD_BODY = '''    <section class="page-hero">
      <div class="shell">
        <nav class="crumbs" aria-label="Breadcrumb"><a href="/">SwiftClient</a> › Download</nav>
        <span class="eyebrow" style="margin-top:14px"><i class="fa-solid fa-download"></i> Free download</span>
        <h1>Download Swift<b>Client</b></h1>
        <p class="lead">The free Minecraft: Java Edition client for Windows and Android. Pick how you want to install it.</p>
      </div>
    </section>
    <section class="sec" style="padding-top:30px">
      <div class="shell">
''' + platforms + '''    </section>
    <section class="sec" style="padding-top:10px">
      <div class="shell">
        <div class="sec-head"><span class="eyebrow"><i class="fa-solid fa-microchip"></i> Requirements</span><h2>What you <b>need</b></h2></div>
        <div class="req req4">
          <div class="stat"><b>Java Edition</b><span>a Microsoft account that owns Minecraft</span></div>
          <div class="stat"><b>Windows 10/11</b><span>64-bit, 4 GB RAM+</span></div>
          <div class="stat"><b>Android 8+</b><span>64-bit, 4 GB RAM+ recommended</span></div>
          <div class="stat"><b>Internet</b><span>for the first download</span></div>
        </div>
        <p class="faint" style="margin-top:18px">SwiftClient downloads Minecraft and Java from Mojang and mods from Modrinth on your device, checked against their published hashes. Nothing from Mojang is bundled.</p>
      </div>
    </section>'''

page('android/index.html',
     'SwiftClient for Android – Minecraft Java Edition APK',
     'Play Minecraft: Java Edition on Android with SwiftClient. Free APK with your Microsoft account, Fabric mods, capes, HUD mods and touch controls.',
     'https://swiftclient.swiftsmp.net/android/', ANDROID_LD, '/android/', ANDROID_BODY,
     'SwiftClient for Android – Minecraft Java Edition')
page('download/index.html',
     'Download SwiftClient – Free Minecraft Client',
     'Download SwiftClient free for Windows or Android: Microsoft Store, Windows installer (.exe) or Android APK. More FPS, capes and Fabric mods, no ads.',
     'https://swiftclient.swiftsmp.net/download/', DOWNLOAD_LD, None, DOWNLOAD_BODY,
     'Download SwiftClient – Free Minecraft Client')
print('ok')
