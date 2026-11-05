// SwiftClient website: live counters, SwiftSMP players, cape previews, gallery, mobile menu, copy.
const CAPE_API = 'https://capes.swiftsmp.com';
const SMP_API = 'https://swiftsmp.com/api/status';

const $ = (id) => document.getElementById(id);
const fmt = (n) => (typeof n === 'number' ? n.toLocaleString('en-US') : '–');
const set = (id, text) => { const el = $(id); if (el) el.textContent = text; };

async function json(url) {
  const res = await fetch(url, { cache: 'no-store' });
  if (!res.ok) throw new Error(res.status);
  return res.json();
}

async function loadStats() {
  try {
    const { users, online } = await json(CAPE_API + '/stats');
    set('stUsers', fmt(users));
    set('stOnline', fmt(online));
  } catch { /* counters stay as dashes */ }
  try {
    const s = await json(SMP_API);
    set('stSmp', fmt(s.online));
    set('srvSmp', `${fmt(s.online)} / ${fmt(s.max)} playing now`);
    set('heroOnline', `${fmt(s.online)} playing on SwiftSMP now`);
    const heads = $('smpHeads');
    if (heads && Array.isArray(s.players)) {
      heads.replaceChildren(...s.players.slice(0, 8).map((name) => {
        const img = new Image(22, 22);
        img.src = `https://mc-heads.net/avatar/${encodeURIComponent(name)}/22`;
        img.alt = name;
        img.title = name;
        img.loading = 'lazy';
        return img;
      }));
    }
  } catch { /* leave defaults */ }
}

// Front of each public cape (10x16 at 1,1 of the 64x32 texture), drawn pixel-sharp.
async function loadCapes() {
  const strip = $('capeStrip');
  if (!strip) return;
  try {
    const capes = (await json(CAPE_API + '/capes')).filter((c) => c.public).slice(0, 4);
    for (const cape of capes) {
      const img = new Image();
      await new Promise((done) => {
        img.onload = img.onerror = done;
        img.src = `${CAPE_API}/capes/${cape.id}.png`;
      });
      if (!img.naturalWidth) continue;
      const k = img.naturalWidth / 64;
      const c = document.createElement('canvas');
      c.width = 10; c.height = 16;
      c.title = cape.name;
      const ctx = c.getContext('2d');
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(img, k, k, 10 * k, 16 * k, 0, 0, 10, 16);
      strip.append(c);
    }
  } catch { /* decorative only */ }
}

// Gallery: tabs and thumbnails swap the big image.
for (const tab of document.querySelectorAll('.tabs button')) {
  tab.addEventListener('click', () => {
    for (const t of document.querySelectorAll('.tabs button')) t.setAttribute('aria-selected', String(t === tab));
    for (const g of document.querySelectorAll('.show')) g.hidden = g.dataset.set !== tab.dataset.set;
  });
}
for (const show of document.querySelectorAll('.show')) {
  const main = show.querySelector('.main img');
  const cap = show.querySelector('.main figcaption');
  for (const thumb of show.querySelectorAll('.thumb')) {
    thumb.addEventListener('click', () => {
      main.src = thumb.dataset.src;
      cap.textContent = thumb.dataset.cap;
      main.alt = thumb.dataset.cap;
      for (const t of show.querySelectorAll('.thumb')) t.setAttribute('aria-current', String(t === thumb));
    });
  }
  main.style.cursor = 'zoom-in';
  main.addEventListener('click', () => {
    const box = document.createElement('div');
    box.className = 'lightbox';
    const img = new Image();
    img.src = main.src;
    img.alt = main.alt;
    box.append(img);
    const close = () => { box.remove(); document.removeEventListener('keydown', onKey); };
    const onKey = (e) => { if (e.key === 'Escape') close(); };
    box.addEventListener('click', close);
    document.addEventListener('keydown', onKey);
    document.body.append(box);
  });
}

// Mobile menu
const burger = document.querySelector('.nav-burger');
const links = $('navLinks');
if (burger && links) {
  burger.addEventListener('click', () => {
    const open = links.classList.toggle('open');
    burger.setAttribute('aria-expanded', String(open));
  });
  links.addEventListener('click', (e) => {
    if (e.target.closest('a')) { links.classList.remove('open'); burger.setAttribute('aria-expanded', 'false'); }
  });
}

// Copy server addresses
const toast = $('toast');
let toastTimer;
function showToast(text) {
  if (!toast) return;
  toast.textContent = text;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 1800);
}
for (const el of document.querySelectorAll('[data-copy]')) {
  el.addEventListener('click', async () => {
    try { await navigator.clipboard.writeText(el.dataset.copy); showToast('Copied ' + el.dataset.copy); }
    catch { showToast(el.dataset.copy); }
  });
}

loadStats();
loadCapes();
setInterval(loadStats, 60000);
