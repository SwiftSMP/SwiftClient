'use strict';
/* global swift */

const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
const el = (tag, props = {}, ...kids) => {
  const n = Object.assign(document.createElement(tag), props);
  for (const k of kids) n.append(k);
  return n;
};

const state = { info: null, account: null, skipLogin: false, settings: null, versions: [], running: false, busy: false };

function toast(msg, err = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = `toast${err ? ' err' : ''}`;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => t.classList.add('hidden'), err ? 9000 : 4000);
}

function notice(msg, kind = '') {
  $('#notices').append(el('div', { className: `notice ${kind}`, textContent: msg }));
}

const currentVersion = () => state.settings.version || (state.versions[0] && state.versions[0].id);
const currentLoader = () => (state.settings && state.settings.loader) || 'fabric';

// ---- window + navigation --------------------------------------------------------

$$('[data-win]').forEach((b) => b.addEventListener('click', () => swift.call(`win:${b.dataset.win}`)));
$$('[data-folder]').forEach((b) => b.addEventListener('click', () => swift.call('shell:openFolder', b.dataset.folder)));

function show(view) {
  $$('.nav-link').forEach((n) => n.classList.toggle('active', n.dataset.view === view));
  $$('.view').forEach((v) => v.classList.toggle('active', v.id === `view-${view}`));
  if (view === 'mods') { renderMods(); if (!explore.loaded) loadExplore(); }
  if (view === 'servers') { setServers(state.info && state.info.servers); refreshStatus(); }
  if (view === 'cosmetics') { renderCapes(); renderAdminCodes(); }
  if (view === 'friends') { renderFriends(); startFriendsPolling(); }
}
$$('.nav-link').forEach((n) => n.addEventListener('click', () => show(n.dataset.view)));

// ---- search row ------------------------------------------------------------------

const VIEWS = ['play', 'servers', 'mods', 'cosmetics', 'friends', 'settings'];
const searchCache = { mods: [], friends: [], capes: [], warmed: false };

// Filled once, on the first time the box is used; the views keep it fresh afterwards.
async function warmSearch() {
  if (searchCache.warmed) return;
  searchCache.warmed = true;
  const mc = currentVersion();
  const [mods, social, capes] = await Promise.all([
    mc ? swift.call('mods:list', mc, currentLoader()).catch(() => []) : Promise.resolve([]),
    state.account ? swift.call('social:state').catch(() => null) : Promise.resolve(null),
    state.info && state.info.capesEnabled ? swift.call('capes:catalog').catch(() => null) : Promise.resolve(null),
  ]);
  searchCache.mods = mods || [];
  searchCache.friends = (social && social.friends) || [];
  searchCache.capes = (capes && capes.capes) || [];
  if (document.activeElement === $('#globalSearch')) renderSuggest();
}

function searchMatches(q) {
  const hit = (s) => s && s.toLowerCase().includes(q);
  const out = [];
  for (const v of VIEWS) if (hit(v)) out.push({ kind: 'View', label: v[0].toUpperCase() + v.slice(1), run: () => show(v) });
  for (const s of (state.info && state.info.servers) || []) {
    if (s.hidden || !(hit(s.name) || hit(s.address))) continue;
    out.push({ kind: 'Server', label: s.name, sub: s.address, img: s.icon || 'img/logo.png', run: () => { show('servers'); play(s.address); } });
  }
  for (const m of searchCache.mods) if (hit(m.name)) {
    out.push({ kind: 'Mod', label: m.name, sub: m.enabled ? 'Installed' : 'Installed · off', run: () => show('mods') });
  }
  for (const c of searchCache.capes) if (hit(c.name)) {
    out.push({ kind: 'Cloak', label: c.name, sub: c.owned ? 'Owned' : 'Locked', run: () => show('cosmetics') });
  }
  for (const f of searchCache.friends) if (hit(f.name)) {
    out.push({ kind: 'Friend', label: f.name, sub: f.online ? 'online' : 'offline', run: () => { show('friends'); openChat(f); } });
  }
  return out.slice(0, 12);
}

function hideSuggest() {
  const list = $('#suggest');
  list.hidden = true;
  list.replaceChildren();
}

let lastHits = [];

function renderSuggest() {
  const list = $('#suggest');
  const q = $('#globalSearch').value.trim().toLowerCase();
  if (!q) { hideSuggest(); return; }
  lastHits = searchMatches(q);
  list.replaceChildren(...(lastHits.length
    ? lastHits.map((h) => el('li', {}, el('button', {
        type: 'button',
        onclick: () => { hideSuggest(); $('#globalSearch').value = ''; h.run(); },
      },
      h.img ? el('img', { src: h.img, alt: '' }) : '',
      el('span', { className: 'grow' }, el('b', { textContent: h.label }), h.sub ? el('small', { textContent: h.sub }) : ''),
      el('span', { className: 'kind', textContent: h.kind }))))
    : [el('li', { className: 'none', textContent: 'Nothing matches that yet.' })]));
  list.hidden = false;
}

$('#globalSearch').addEventListener('input', renderSuggest);
$('#globalSearch').addEventListener('focus', () => { warmSearch(); renderSuggest(); });
$('#globalSearch').addEventListener('keydown', (e) => {
  if (e.key === 'Escape') { hideSuggest(); e.target.blur(); }
  if (e.key === 'Enter' && lastHits[0]) { hideSuggest(); e.target.value = ''; lastHits[0].run(); }
});
document.addEventListener('click', (e) => { if (!e.target.closest('.player-search')) hideSuggest(); });

// ---- account ---------------------------------------------------------------------

function acctItem({ head, name, kind, active, onPick }) {
  const item = el('button', { className: `acct-item${active ? ' active' : ''}${onPick ? '' : ' static'}`, role: 'menuitem' },
    el('img', { src: head, alt: '' }),
    el('span', { className: 'grow' }, el('b', { textContent: name }), el('small', { textContent: kind })));
  if (active) item.append(el('span', { className: 'check-mark', textContent: '✓' }));
  if (onPick) item.addEventListener('click', onPick);
  return item;
}

function renderAccount() {
  const a = state.account;
  $('#loginOverlay').classList.toggle('hidden', !!a || state.skipLogin);

  $('#acctName').textContent = a ? a.name : 'Not signed in';
  $('#acctKind').textContent = a ? 'Microsoft' : 'Add an account';
  $('#acctHead').src = a ? `https://mc-heads.net/avatar/${a.uuid}/64` : 'img/logo.png';
  $('#logoutBtn').classList.toggle('hidden', !a);

  const items = [];
  if (a) items.push(acctItem({ head: `https://mc-heads.net/avatar/${a.uuid}/64`, name: a.name, kind: 'Microsoft', active: true }));
  $('#acctList').replaceChildren(...items);
}

function closeAcctMenu() {
  $('#acctPop').classList.add('hidden');
  $('#acctBtn').setAttribute('aria-expanded', 'false');
}
$('#acctBtn').addEventListener('click', (e) => {
  e.stopPropagation();
  const open = $('#acctPop').classList.toggle('hidden');
  $('#acctBtn').setAttribute('aria-expanded', String(!open));
});
$('#acctPop').addEventListener('click', (e) => e.stopPropagation());
document.addEventListener('click', closeAcctMenu);
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { closeAcctMenu(); closeAddAccount(); } });

// Add account dialog: Microsoft sign-in.
function openAddAccount() {
  closeAcctMenu();
  $('#addError').textContent = '';
  $('#addOverlay').classList.remove('hidden');
}
function closeAddAccount() { $('#addOverlay').classList.add('hidden'); }
$('#acctAddBtn').addEventListener('click', openAddAccount);
$('#addCancel').addEventListener('click', closeAddAccount);

async function signIn(button, errorBox) {
  errorBox.textContent = '';
  button.disabled = true;
  try {
    state.account = await swift.call('auth:login');
    renderAccount();
    toast(`Signed in as ${state.account.name}`);
    return true;
  } catch (e) {
    errorBox.textContent = e.message;
    return false;
  } finally {
    button.disabled = false;
  }
}
$('#loginBtn').addEventListener('click', () => signIn($('#loginBtn'), $('#loginError')));
$('#addMsBtn').addEventListener('click', async () => { if (await signIn($('#addMsBtn'), $('#addError'))) closeAddAccount(); });

function askToSignIn() {
  state.skipLogin = false;
  renderAccount();
}
$('#skipLoginBtn').addEventListener('click', () => { state.skipLogin = true; renderAccount(); });

$('#logoutBtn').addEventListener('click', async () => {
  closeAcctMenu();
  await swift.call('auth:logout');
  state.account = null;
  renderAccount();
});

// ---- play ------------------------------------------------------------------------

function renderVersions() {
  const sel = $('#versionSelect');
  sel.replaceChildren(...state.versions.map((v, i) => el('option', {
    value: v.id, textContent: `${v.id}${i === 0 ? '  (latest)' : ''}${v.swiftMod ? '' : '  · no cloaks'}`,
  })));
  sel.value = currentVersion() || '';
  $('#loaderSelect').value = currentLoader();
}

$('#loaderSelect').addEventListener('change', async (e) => {
  state.settings = await swift.call('settings:set', { loader: e.target.value });
  explore.loader = currentLoader();
  explore.loaded = false;
  $$('#modrinthLoaders .chip').forEach((x) => x.classList.toggle('active', x.dataset.loader === explore.loader));
  if ($('#view-mods').classList.contains('active')) { renderMods(); loadExplore(); }
});

$('#versionSelect').addEventListener('change', async (e) => {
  state.settings = await swift.call('settings:set', { version: e.target.value });
  $('#modsVersion').textContent = e.target.value;
  $('#exploreVersion').textContent = e.target.value;
  explore.loaded = false;
  if ($('#view-mods').classList.contains('active')) loadExplore();
});

function setBusy(busy) {
  state.busy = busy;
  const off = busy || state.running;
  $('#playBtn').disabled = off;
  $('#joinBtn').disabled = off;
  $('#versionSelect').disabled = off;
  $('#loaderSelect').disabled = off;
  $$('.server-join').forEach((b) => { b.disabled = off; });
  $('#playBtn').textContent = state.running ? 'Playing' : busy ? 'Starting…' : 'Play';
}

async function play(join) {
  if (!state.account) { askToSignIn(); return; }
  $('#notices').replaceChildren();
  $('#gameLog').textContent = '';
  $('#progress').classList.remove('hidden');
  setBusy(true);
  try {
    await swift.call('game:launch', { join });
  } catch (e) {
    notice(e.message, 'err');
    $('#progress').classList.add('hidden');
  } finally {
    setBusy(false);
  }
}
$('#playBtn').addEventListener('click', () => play(false));
$('#joinBtn').addEventListener('click', () => play(true));

swift.onProgress(({ done, total, label }) => {
  $('#progress').classList.remove('hidden');
  $('#progressBar').style.width = `${total ? Math.round((done / total) * 100) : 0}%`;
  $('#progressLabel').textContent = total > 1 ? `${label} · ${done} / ${total}` : label;
});
swift.onNotice((msg) => notice(msg, 'warn'));
swift.onGameLog((line) => {
  const log = $('#gameLog');
  log.textContent = (log.textContent + line).slice(-60000);
  log.scrollTop = log.scrollHeight;
});
swift.onGameState((s) => {
  state.running = s.running;
  setBusy(false);
  if (s.running) {
    $('#progressLabel').textContent = `Minecraft ${s.mc} is running`;
  } else {
    $('#progress').classList.add('hidden');
    if (s.crashed) {
      notice(`Minecraft stopped unexpectedly (code ${s.code}). If you added mods recently, try turning them off in Mods. Log: ${s.logFile}`, 'err');
      $('.console').open = true;
    }
  }
});
swift.onUpdate((u) => {
  const b = $('#updateBanner');
  b.classList.remove('hidden');
  if (u.state === 'downloading') b.textContent = `Downloading SwiftClient ${u.version}…`;
  if (u.state === 'ready') {
    b.textContent = `SwiftClient ${u.version} is ready. Restart to update`;
    b.onclick = () => swift.installUpdate();
  }
  // Builds that cannot install updates themselves still get a one-click download.
  if (u.state === 'manual' && u.url) {
    b.textContent = `SwiftClient ${u.version} is out — get the update`;
    b.onclick = () => swift.call('shell:open', u.url);
  }
});

// ---- live counts -------------------------------------------------------------------

const num = (n) => Number(n).toLocaleString('en-US');

function setServerStatus(server) {
  const card = $('#serverCard');
  const dot = $('#serverDot');
  card.classList.remove('off', 'busy', 'on');
  dot.classList.remove('on');
  if (!server) return;
  if (server.offline) {
    $('#serverOnline').textContent = 'Offline';
    $('#serverLabel').textContent = 'SwiftSMP is not answering right now';
    card.classList.add('off');
    return;
  }
  card.classList.add('on');
  dot.classList.add('on');
  $('#serverOnline').textContent = num(server.players.online);
  $('#serverLabel').replaceChildren(
    document.createTextNode(server.players.max ? `of ${num(server.players.max)} on ` : 'players on '),
    el('span', { className: 'server-name', textContent: state.info.server.name }));
  card.title = `${serverPlayers(server)} on ${state.info.server.name}\n${server.latencyMs} ms${server.motd ? `\n${server.motd}` : ''}`;
}

function setClientStatus(client, downloads) {
  const card = $('#clientCard');
  card.classList.remove('off', 'busy', 'on');
  $('#totalDownloads').textContent = downloads == null ? '—' : num(downloads);
  $('#downloadsCard').title = downloads == null ? 'Total downloads' : `${num(downloads)} total downloads`;
  if (!client) {
    $('#clientCard').classList.add('busy');
    return;
  }
  card.classList.add('on');
  $('#clientDot').classList.add('on');
  card.title = `${num(client.online)} SwiftClient players online`;
  $('#clientOnline').textContent = num(client.online);
  $('#clientLabel').textContent = 'SwiftClient players online';
}


function serverPlayers(s) {
  if (s.offline) return 'Offline';
  if (!s.players) return '— players';
  return s.players.max
    ? `${num(s.players.online)} / ${num(s.players.max)} players`
    : `${num(s.players.online)} players`;
}

function setServers(list) {
  const box = $('#serverList');
  if (!box) return;
  const servers = (list || []).filter((s) => !s.hidden)
    .sort((a, b) => (a.partner ? 1 : 0) - (b.partner ? 1 : 0));
  if (!servers.length) {
    box.replaceChildren(el('div', { className: 'empty', textContent: 'No servers are available right now.' }));
    return;
  }
  box.replaceChildren(...servers.map((s) => {
    const motd = s.offline ? (s.error || 'Offline right now') : (s.motd || (s.partner ? 'Partner server' : 'The SwiftSMP home server'));
    const join = el('button', { className: 'btn primary small server-join', textContent: s.partner ? 'Join partner' : 'Join' });
    join.disabled = state.busy || state.running;
    join.addEventListener('click', () => play(s.address));
    return el('article', { className: `server-card wide${s.offline ? ' off' : ''}` },
      el('div', { className: 'server-card-top' },
        el('img', { className: 'server-icon', src: s.icon || 'img/logo.png', alt: '' }),
        el('div', { className: 'server-card-title' },
          el('div', { className: 'server-name-row' },
            el('b', { textContent: s.name }),
            el('span', { className: `tag${s.partner ? ' partner' : ''}`, textContent: s.partner ? 'PARTNER' : 'MAIN' })),
          el('small', { className: 'server-address', textContent: s.address }))),
      el('p', { className: 'server-motd', textContent: motd, title: s.motd || '' }),
      el('div', { className: 'server-meta' },
        el('span', { className: `server-state${s.offline ? ' off' : ' on'}`, textContent: serverPlayers(s) }),
        el('span', { className: 'server-latency', textContent: s.offline ? '—' : `${s.latencyMs} ms` })),
      join);
  }));
}

async function refreshStatus() {
  try {
    const s = await swift.call('stats:get');
    setServerStatus(s.server);
    setClientStatus(s.client, s.downloads);
    setServers(s.servers);
  } catch { /* offline: keep the last counts */ }
}

swift.onStats((s) => setClientStatus(s.client));
setInterval(refreshStatus, 60000);
$('#serverCard').addEventListener('click', () => refreshStatus());
$('#serversRefresh').addEventListener('click', () => refreshStatus());
$('#partnerTicket').addEventListener('click', () => swift.call('shell:open', 'https://swiftsmp.com'));

// ---- mods ------------------------------------------------------------------------

async function renderMods() {
  const mc = currentVersion();
  $('#modsVersion').textContent = `${mc} · ${currentLoader()}`;
  const list = $('#modList');
  let mods = [];
  try { mods = await swift.call('mods:list', mc, currentLoader()); } catch (e) { toast(e.message, true); }
  if (!mods.length) {
    list.replaceChildren(el('div', { className: 'empty', textContent: 'No mods yet. Built-in mods are installed the first time you press Play.' }));
    return;
  }
  list.replaceChildren(...mods.map((m) => {
    const row = el('div', { className: `mod${m.enabled ? '' : ' off'}` },
      el('span', { className: `badge ${m.managed ? 'builtin' : 'user'}`, textContent: m.managed ? 'Built-in' : 'Yours' }),
      el('div', { className: 'grow' }, el('div', { className: 'name', textContent: m.name }), el('div', { className: 'ver', textContent: `${m.version || ''}  ${m.file}` })));
    if (!m.managed) {
      const toggle = el('label', { className: 'check' }, el('input', { type: 'checkbox', checked: m.enabled }), 'On');
      toggle.querySelector('input').addEventListener('change', async (e) => {
        await swift.call('mods:toggle', mc, m.file, e.target.checked, currentLoader()).catch((err) => toast(err.message, true));
        renderMods();
      });
      const remove = el('button', { className: 'btn small ghost danger', textContent: 'Remove' });
      remove.addEventListener('click', async () => {
        await swift.call('mods:remove', mc, m.file, currentLoader()).catch((err) => toast(err.message, true));
        renderMods();
      });
      row.append(toggle, remove);
    }
    return row;
  }));
}

async function importFiles(files) {
  if (!files.length) return;
  const mc = currentVersion();
  const results = await swift.call('mods:import', mc, files, currentLoader()).catch((e) => { toast(e.message, true); return []; });
  $('#importResults').replaceChildren(...results.map((r) => {
    const name = (r.meta && r.meta.name) || r.file;
    const items = [...(r.problems || []), ...(r.warnings || [])];
    return el('div', { className: `result ${r.ok ? 'ok' : 'bad'}` },
      el('b', { textContent: r.ok ? `Added ${name}` : `Not added: ${name}` }),
      items.length ? el('ul', {}, ...items.map((t) => el('li', { textContent: t }))) : '');
  }));
  renderMods();
}

$('#importBtn').addEventListener('click', async () => importFiles(await swift.call('mods:pick')));

// ---- Modrinth explorer -------------------------------------------------------

const explore = { loader: 'fabric', sort: 'downloads', category: '', query: '', offset: 0, hits: [], loaded: false, loading: false };

function exploreBadge(text, accent = false) {
  return el('span', { className: `badge ${accent ? 'builtin' : 'user'}`, textContent: text });
}

function renderExplore(more) {
  const box = $('#modrinthResults');
  if (!explore.hits.length) {
    box.replaceChildren(el('div', { className: 'empty', textContent: `No ${explore.loader} mods for ${currentVersion()} match your filters.` }));
    $('#modrinthMore').hidden = true;
    return;
  }
  box.replaceChildren(...explore.hits.map((h) => {
    const installable = h.loader === currentLoader();
    const actions = [];
    if (installable) {
      const install = el('button', { className: 'btn small primary', textContent: 'Install' });
      install.addEventListener('click', async () => {
        install.disabled = true;
        install.textContent = 'Installing…';
        const r = await swift.call('mods:install', currentVersion(), h.slug, currentLoader()).catch((e) => ({ ok: false, problems: [e.message] }));
        toast(r.ok ? `Installed ${r.name} ${r.version}` : (r.problems || ['Could not install']).join(' '), !r.ok);
        install.disabled = false;
        install.textContent = 'Install';
        if (r.ok) renderMods();
      });
      actions.push(install);
    }
    const open = el('button', { className: 'btn small ghost', textContent: installable ? 'View' : 'Open' });
    open.addEventListener('click', () => swift.call('shell:open', `https://modrinth.com/mod/${h.slug}`));
    actions.push(open);
    const badges = [h.loader, ...h.categories.filter((c) => c !== h.loader).slice(0, 3)]
      .map((c) => exploreBadge(c, c === h.loader));
    return el('div', { className: 'mod-hit' },
      h.icon ? el('img', { className: 'mod-icon', src: h.icon, alt: '' }) : el('div', { className: 'mod-icon' }),
      el('div', { className: 'grow' },
        el('div', { className: 'title' }, el('b', { textContent: h.title }), ...badges),
        el('div', { className: 'desc', textContent: h.description }),
        el('div', { className: 'meta' },
          ...[`${num(h.downloads)} downloads`, h.author ? `by ${h.author}` : '', installable ? '' : 'browse only']
            .filter(Boolean).map((t) => el('span', { textContent: t })))),
      el('div', { className: 'actions' }, ...actions));
  }));
  $('#modrinthMore').hidden = !more;
}

async function loadExplore(reset = true) {
  if (explore.loading) return;
  explore.loading = true;
  const box = $('#modrinthResults');
  if (reset) {
    explore.offset = 0;
    explore.hits = [];
    box.replaceChildren(el('div', { className: 'muted', textContent: 'Loading Modrinth…' }));
  }
  try {
    const hits = await swift.call('mods:search', currentVersion(), {
      query: explore.query,
      loader: explore.loader,
      sort: explore.sort,
      category: explore.category,
      offset: explore.offset,
      limit: 20,
    });
    explore.hits = reset ? hits : explore.hits.concat(hits);
    explore.offset += hits.length;
    explore.loaded = true;
    renderExplore(hits.length === 20);
  } catch (e) {
    toast(e.message, true);
    if (reset) box.replaceChildren();
  } finally {
    explore.loading = false;
  }
}

$('#modrinthSearch').addEventListener('click', () => { explore.query = $('#modrinthQuery').value.trim(); loadExplore(); });
$('#modrinthQuery').addEventListener('keydown', (e) => { if (e.key === 'Enter') { explore.query = $('#modrinthQuery').value.trim(); loadExplore(); } });
$('#modrinthSort').addEventListener('change', (e) => { explore.sort = e.target.value; loadExplore(); });
$('#modrinthCategory').addEventListener('change', (e) => { explore.category = e.target.value; loadExplore(); });
$$('#modrinthLoaders .chip').forEach((b) => b.addEventListener('click', () => {
  $$('#modrinthLoaders .chip').forEach((x) => x.classList.toggle('active', x === b));
  explore.loader = b.dataset.loader;
  loadExplore();
}));
$('#modrinthMore').addEventListener('click', () => loadExplore(false));
const drop = $('#dropZone');
['dragenter', 'dragover'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach((ev) => drop.addEventListener(ev, () => drop.classList.remove('over')));
drop.addEventListener('drop', (e) => {
  e.preventDefault();
  importFiles([...e.dataTransfer.files].map((f) => swift.pathFor(f)).filter(Boolean));
});
// Dropping anywhere else must not navigate the window to the file.
window.addEventListener('dragover', (e) => e.preventDefault());
window.addEventListener('drop', (e) => e.preventDefault());

// ---- capes -----------------------------------------------------------------------

function capePreview(src) {
  // Draw the outer face of the cape layout (x 1..10, y 1..16), the side seen from behind.
  // Rendered at 16x so HD cloak art keeps its detail instead of being crushed to 10x16.
  const scale = 16;
  const c = el('canvas', { width: 10 * scale, height: 16 * scale });
  const img = new Image();
  img.onload = () => {
    const s = img.width / 64;
    const ctx = c.getContext('2d');
    // HD atlases downsample smoothly; classic 64x32 capes keep their crisp pixels.
    ctx.imageSmoothingEnabled = img.width > 64;
    ctx.imageSmoothingQuality = 'high';
    ctx.drawImage(img, 1 * s, 1 * s, 10 * s, 16 * s, 0, 0, c.width, c.height);
  };
  img.src = src;
  return c;
}

async function renderCapes() {
  const grid = $('#capeGrid');
  if (!state.info.capesEnabled) {
    grid.replaceChildren(el('div', { className: 'empty', textContent: 'Shared cloaks are not configured for this build.' }));
    return;
  }
  let data;
  try { data = await swift.call('capes:catalog'); } catch (e) {
    grid.replaceChildren(el('div', { className: 'empty', textContent: `Could not reach the cloak server: ${e.message}` }));
    return;
  }
  const card = (cape) => {
    const selected = cape ? data.selected === cape.id : !data.selected;
    const locked = cape && !cape.owned;
    const n = el('div', { className: `cape${selected ? ' selected' : ''}${locked ? ' locked' : ''}` },
      cape ? capePreview(cape.image) : el('div', { className: 'none' }),
      el('div', { className: 'cname', textContent: cape ? cape.name : 'No cloak' }),
      el('div', { className: 'cdesc', textContent: cape ? (cape.description || '') : 'Show your Minecraft cloak, if any' }));
    if (locked) n.append(el('span', { className: 'lock', textContent: 'Locked' }));
    if (!locked && !selected) {
      n.addEventListener('click', async () => {
        try {
          await swift.call('capes:select', cape ? cape.id : null);
          toast(cape ? `Equipped ${cape.name}` : 'Cloak removed');
          renderCapes();
        } catch (e) { toast(e.message, true); }
      });
    }
    return n;
  };
  grid.replaceChildren(card(null), ...data.capes.map(card));
}

async function redeemCapeCode() {
  const input = $('#capeCode');
  const button = $('#capeRedeem');
  if (!state.account) { askToSignIn(); return; }
  button.disabled = true;
  try {
    const r = await swift.call('capes:redeem', input.value);
    input.value = '';
    toast(`Unlocked the ${r.cape} cloak`);
    renderCapes();
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
  }
}
const ADMIN_UUID = '5b79930d65184ca08b836bb77386cecf';

async function adminCall(request) {
  return swift.call('capes:admin', request);
}

async function renderAdminCodes() {
  const panel = $('#adminCodes');
  const isAdmin = !!state.account && String(state.account.uuid || '').replace(/-/g, '').toLowerCase() === ADMIN_UUID;
  panel.hidden = !isAdmin;
  if (!isAdmin) return;
  const select = $('#adminCape');
  try {
    const data = await swift.call('capes:catalog');
    const previous = select.value;
    const privateCapes = data.capes.filter((c) => !c.public);
    select.replaceChildren(...privateCapes.map((c) => el('option', { value: c.id, textContent: c.name })));
    if (privateCapes.some((c) => c.id === previous)) select.value = previous;
  } catch (e) {
    toast(e.message, true);
  }
  try {
    const { codes } = await adminCall({ action: 'list' });
    $('#adminList').replaceChildren(...codes.map((c) => el('div', {
      className: 'admin-row',
      textContent: `${c.cape} · ${c.redeemed}/${c.uses || 'unlimited'} used · ${c.created ? c.created.slice(0, 10) : ''}`,
    })));
  } catch (e) {
    toast(e.message, true);
  }
  renderAdminPartners();
  renderAdminCapes();
}

async function renderAdminPartners() {
  const list = $('#adminPartnerList');
  if (!list) return;
  const servers = (state.info.servers || []).filter((s) => s.partner);
  if (!servers.length) {
    list.replaceChildren(el('div', { className: 'empty', textContent: 'No partner servers configured.' }));
    return;
  }
  list.replaceChildren(...servers.map((s) => {
    const hidden = !!s.hidden;
    const button = el('button', { className: `btn small ${hidden ? 'ghost' : 'danger'}`, textContent: hidden ? 'Show' : 'Hide' });
    button.addEventListener('click', async () => {
      button.disabled = true;
      try {
        await swift.call('partners:admin', { action: hidden ? 'show' : 'hide', address: s.address });
        state.info = await swift.call('app:info');
        setServers(state.info.servers);
        renderAdminPartners();
        refreshStatus();
        toast(`${s.name} is ${hidden ? 'visible' : 'hidden'} for SwiftClient.`);
      } catch (e) {
        toast(e.message, true);
      } finally {
        button.disabled = false;
      }
    });
    return el('div', { className: 'admin-row' },
      el('span', { className: 'grow', textContent: `${s.name} · ${s.address}${hidden ? ' · HIDDEN' : ''}` }),
      button);
  }));
}

async function renderAdminCapes() {
  const list = $('#adminCapeList');
  if (!list) return;
  try {
    const { capes } = await swift.call('capes:adminCapes', { action: 'list' });
    list.replaceChildren(...capes.map((c) => {
      const button = el('button', { className: `btn small ${c.hidden ? 'ghost' : 'danger'}`, textContent: c.hidden ? 'Show' : 'Hide' });
      button.addEventListener('click', async () => {
        button.disabled = true;
        try {
          await swift.call('capes:adminCapes', { action: c.hidden ? 'show' : 'hide', cape: c.id });
          renderCapes();
          renderAdminCodes();
          toast(`${c.name} is ${c.hidden ? 'visible' : 'hidden'}.`);
        } catch (e) {
          toast(e.message, true);
        } finally {
          button.disabled = false;
        }
      });
      return el('div', { className: 'admin-row' },
        el('span', { className: 'grow', textContent: `${c.name} · ${c.id}${c.public ? '' : ' · private'}${c.hidden ? ' · HIDDEN' : ''}` }),
        button);
    }));
  } catch (e) {
    toast(e.message, true);
  }
}

async function createAdminCodes() {
  const button = $('#adminCreate');
  const cape = $('#adminCape').value;
  const uses = Number($('#adminUses').value);
  const count = Number($('#adminCount').value);
  if (!cape) { toast('Pick a cloak first.', true); return; }
  button.disabled = true;
  try {
    const r = await adminCall({ action: 'create', cape, uses, count });
    const out = $('#adminOut');
    out.hidden = false;
    out.value = r.codes.join('\n');
    out.select();
    toast(`Created ${r.codes.length} code${r.codes.length === 1 ? '' : 's'} for ${cape}`);
    renderAdminCodes();
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
  }
}

$('#capeRedeem').addEventListener('click', redeemCapeCode);
$('#adminCreate').addEventListener('click', createAdminCodes);
$('#capeCode').addEventListener('keydown', (e) => { if (e.key === 'Enter') redeemCapeCode(); });

// ---- settings --------------------------------------------------------------------

function bindSettings(curated, totalMemMb) {
  const s = state.settings;
  const mem = $('#memoryMb');
  mem.max = Math.max(2048, Math.floor((totalMemMb * 0.75) / 512) * 512);
  const memLabel = () => { $('#memoryLabel').textContent = `${(mem.value / 1024).toFixed(1)} GB`; };
  mem.value = s.memoryMb;
  memLabel();
  mem.addEventListener('input', memLabel);

  const save = async (patch) => { state.settings = await swift.call('settings:set', patch); };
  mem.addEventListener('change', () => save({ memoryMb: Number(mem.value) }));
  for (const id of ['width', 'height']) {
    $(`#${id}`).value = s[id];
    $(`#${id}`).addEventListener('change', (e) => save({ [id]: Number(e.target.value) || s[id] }));
  }
  for (const id of ['fullscreen', 'performanceMods', 'closeOnLaunch', 'autoJoin', 'discordPresence', 'discordShowServer', 'shareStats']) {
    $(`#${id}`).checked = !!s[id];
    $(`#${id}`).addEventListener('change', (e) => save({ [id]: e.target.checked }));
  }
  $('#extraJvmArgs').value = s.extraJvmArgs;
  $('#extraJvmArgs').addEventListener('change', (e) => save({ extraJvmArgs: e.target.value.trim() }));

  $('#perfList').replaceChildren(...curated.filter((m) => m.group === 'performance')
    .map((m) => el('span', { className: 'chip', title: m.desc, textContent: m.name })));
  $('#optionalMods').replaceChildren(...curated.filter((m) => m.group === 'optional').map((m) => {
    const on = (s.optionalMods || {})[m.slug] ?? m.default;
    const label = el('label', { className: 'check' }, el('input', { type: 'checkbox', checked: on }), `${m.name} `, el('span', { className: 'muted small', textContent: m.desc }));
    label.querySelector('input').addEventListener('change', (e) => save({ optionalMods: { ...state.settings.optionalMods, [m.slug]: e.target.checked } }));
    return label;
  }));
}

// ---- friends and chat ------------------------------------------------------------

let openFriend = null;   // uuid of the friend whose chat is open
let friendsTimer = null; // refreshes the list and open chat while the view is visible

async function renderFriends() {
  const list = $('#friendList');
  if (!state.account) {
    list.replaceChildren(el('div', { className: 'empty', textContent: 'Sign in to add friends and chat.' }));
    return;
  }
  let data;
  try {
    data = await swift.call('social:state');
  } catch (e) {
    list.replaceChildren(el('div', { className: 'empty', textContent: e.message }));
    return;
  }
  const rows = [];
  for (const r of data.requests) {
    rows.push(el('div', { className: 'friend-row request' },
      el('span', { className: 'grow', textContent: `${r.from.name} wants to be friends` }),
      el('button', { className: 'btn small', textContent: 'Accept', onclick: () => friendAction('social:accept', r.from.uuid) }),
      el('button', { className: 'btn ghost small', textContent: 'Decline', onclick: () => friendAction('social:decline', r.from.uuid) })));
  }
  for (const f of data.friends) {
    const row = el('button', { className: `friend-row${openFriend === f.uuid ? ' active' : ''}` },
      el('span', { className: `dot${f.online ? ' on' : ''}` }),
      el('span', { className: 'grow', textContent: f.name }),
      el('small', { textContent: f.online ? 'online' : 'offline' }));
    row.addEventListener('click', () => openChat(f));
    rows.push(row);
  }
  if (!rows.length) rows.push(el('div', { className: 'empty', textContent: 'No friends yet. Add someone by their Minecraft name.' }));
  list.replaceChildren(...rows);
}

async function friendAction(channel, arg) {
  try {
    await swift.call(channel, arg);
    renderFriends();
  } catch (e) {
    toast(e.message, true);
  }
}

async function openChat(friend) {
  openFriend = friend.uuid;
  $('#chatPane').hidden = false;
  $('#chatPane').dataset.name = friend.name;
  renderChat();
  renderFriends();
}

async function renderChat() {
  if (!openFriend) return;
  const log = $('#chatLog');
  const name = $('#chatPane').dataset.name || '';
  let messages;
  try {
    ({ messages } = await swift.call('social:messages', openFriend));
  } catch (e) {
    log.replaceChildren(el('div', { className: 'empty', textContent: e.message }));
    return;
  }
  const me = state.account && state.account.uuid;
  log.replaceChildren(...(messages.length
    ? messages.map((m) => el('div', { className: `msg${m.from === me ? ' mine' : ''}` },
        el('b', { textContent: m.from === me ? 'You' : name }),
        el('span', { textContent: m.text })))
    : [el('div', { className: 'empty', textContent: `Say hi to ${name}.` })]));
  log.scrollTop = log.scrollHeight;
}

async function sendChat() {
  const input = $('#chatInput');
  const text = input.value.trim();
  if (!text || !openFriend) return;
  try {
    await swift.call('social:send', openFriend, text);
    input.value = '';
    renderChat();
  } catch (e) {
    toast(e.message, true);
  }
}

$('#friendAdd').addEventListener('click', async () => {
  const input = $('#friendName');
  const name = input.value.trim();
  if (!name) return;
  try {
    await swift.call('social:request', name);
    input.value = '';
    toast(`Friend request sent to ${name}.`);
    renderFriends();
  } catch (e) {
    toast(e.message, true);
  }
});
$('#chatSend').addEventListener('click', sendChat);
$('#chatInput').addEventListener('keydown', (e) => { if (e.key === 'Enter') sendChat(); });

// Keep the list and the open chat fresh while the Friends view is on screen.
function startFriendsPolling() {
  if (friendsTimer) return;
  friendsTimer = setInterval(() => {
    if (!$('#view-friends').classList.contains('active')) return;
    renderFriends();
    if (openFriend) renderChat();
  }, 5000);
}

// ---- boot ------------------------------------------------------------------------

(async function boot() {
  state.info = await swift.call('app:info');
  setServers(state.info.servers);
  $('#appVersion').textContent = `v${state.info.version}`;
  $$('.server-name').forEach((n) => { n.textContent = state.info.server.name; });
  if (!state.info.authConfigured) $('#loginError').textContent = 'Microsoft sign-in is not configured in this build (msClientId is empty).';

  state.settings = await swift.call('settings:get');
  state.account = await swift.call('auth:current');
  renderAccount();

  const [curated, system] = await Promise.all([swift.call('mods:curated'), swift.call('settings:system')]);
  bindSettings(curated, system.totalMemMb);

  try {
    state.versions = await swift.call('versions:list');
  } catch (e) {
    notice(`Could not load the version list: ${e.message}`, 'err');
  }
  renderVersions();
  $('#modsVersion').textContent = currentVersion();
  $('#exploreVersion').textContent = currentVersion();
  state.running = await swift.call('game:running');
  setBusy(false);
  refreshStatus();
})();
