// SwiftClient website: live counters, screenshot tabs + lightbox, copy-to-clipboard.
const CAPE_API = 'https://capes.swiftsmp.com';

const fmt = (n) => (typeof n === 'number' ? n.toLocaleString('en-US') : '–');

async function loadStats() {
  try {
    const res = await fetch(CAPE_API + '/stats', { cache: 'no-store' });
    if (!res.ok) return;
    const { users, online } = await res.json();
    document.getElementById('stUsers').textContent = fmt(users);
    document.getElementById('stOnline').textContent = fmt(online);
  } catch { /* counters stay as dashes */ }
}
loadStats();
setInterval(loadStats, 60000);

for (const tab of document.querySelectorAll('.tabs button')) {
  tab.addEventListener('click', () => {
    for (const t of document.querySelectorAll('.tabs button')) t.setAttribute('aria-selected', String(t === tab));
    for (const g of document.querySelectorAll('.gallery')) g.hidden = g.dataset.set !== tab.dataset.set;
  });
}

for (const shot of document.querySelectorAll('.shot img')) {
  shot.addEventListener('click', () => {
    const box = document.createElement('div');
    box.className = 'lightbox';
    const img = document.createElement('img');
    img.src = shot.src;
    img.alt = shot.alt;
    box.append(img);
    const close = () => { box.remove(); document.removeEventListener('keydown', onKey); };
    const onKey = (e) => { if (e.key === 'Escape') close(); };
    box.addEventListener('click', close);
    document.addEventListener('keydown', onKey);
    document.body.append(box);
  });
}

const toast = document.getElementById('toast');
let toastTimer;
function showToast(text) {
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
