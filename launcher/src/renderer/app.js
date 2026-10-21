'use strict';
/* global swift */

const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
const el = (tag, props = {}, ...kids) => {
  const n = Object.assign(document.createElement(tag), props);
  for (const k of kids) n.append(k);
  return n;
};

const state = { info: null, account: null, settings: null, versions: [], running: false, busy: false };

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

// ---- window + navigation --------------------------------------------------------

$$('[data-win]').forEach((b) => b.addEventListener('click', () => swift.call(`win:${b.dataset.win}`)));
$$('[data-folder]').forEach((b) => b.addEventListener('click', () => swift.call('shell:openFolder', b.dataset.folder)));

function show(view) {
  $$('.nav').forEach((n) => n.classList.toggle('active', n.dataset.view === view));
  $$('.view').forEach((v) => v.classList.toggle('active', v.id === `view-${view}`));
  if (view === 'mods') renderMods();
  if (view === 'cosmetics') renderCapes();
}
$$('.nav').forEach((n) => n.addEventListener('click', () => show(n.dataset.view)));

// ---- account ---------------------------------------------------------------------

function renderAccount() {
  const a = state.account;
  $('#loginOverlay').classList.toggle('hidden', !!a);
  $('#accountName').textContent = a ? (a.offlineName ? `${a.offlineName} (offline)` : a.name) : 'Not signed in';
  $('#accountHead').src = a ? `https://mc-heads.net/avatar/${a.uuid}/64` : 'img/logo.png';
  $('#logoutBtn').classList.toggle('hidden', !a);
  $('#offlineEnabled').checked = !!(a && a.offlineName);
  $('#offlineName').value = (a && a.offlineName) || '';
}

$('#offlineSave').addEventListener('click', async () => {
  const name = $('#offlineEnabled').checked ? $('#offlineName').value.trim() : null;
  try {
    state.account = await swift.call('auth:offlineName', name);
    renderAccount();
    toast(name ? `Playing offline as ${name}` : `Playing as ${state.account.name}`);
  } catch (e) {
    toast(e.message, true);
  }
});

$('#loginBtn').addEventListener('click', async () => {
  $('#loginError').textContent = '';
  $('#loginBtn').disabled = true;
  try {
    state.account = await swift.call('auth:login');
    renderAccount();
    toast(`Signed in as ${state.account.name}`);
  } catch (e) {
    $('#loginError').textContent = e.message;
  } finally {
    $('#loginBtn').disabled = false;
  }
});

$('#logoutBtn').addEventListener('click', async () => {
  await swift.call('auth:logout');
  state.account = null;
  renderAccount();
});

// ---- play ------------------------------------------------------------------------

function renderVersions() {
  const sel = $('#versionSelect');
  sel.replaceChildren(...state.versions.map((v, i) => el('option', {
    value: v.id, textContent: `${v.id}${i === 0 ? '  (latest)' : ''}${v.swiftMod ? '' : '  · no capes'}`,
  })));
  sel.value = currentVersion() || '';
}

$('#versionSelect').addEventListener('change', async (e) => {
  state.settings = await swift.call('settings:set', { version: e.target.value });
  $('#modsVersion').textContent = e.target.value;
});

function setBusy(busy) {
  state.busy = busy;
  const off = busy || state.running;
  $('#playBtn').disabled = off;
  $('#joinBtn').disabled = off;
  $('#versionSelect').disabled = off;
  $('#playBtn').textContent = state.running ? 'Playing' : busy ? 'Starting…' : 'Play';
}

async function play(join) {
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
});

// ---- mods ------------------------------------------------------------------------

async function renderMods() {
  const mc = currentVersion();
  $('#modsVersion').textContent = mc;
  const list = $('#modList');
  let mods = [];
  try { mods = await swift.call('mods:list', mc); } catch (e) { toast(e.message, true); }
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
        await swift.call('mods:toggle', mc, m.file, e.target.checked).catch((err) => toast(err.message, true));
        renderMods();
      });
      const remove = el('button', { className: 'btn small ghost danger', textContent: 'Remove' });
      remove.addEventListener('click', async () => {
        await swift.call('mods:remove', mc, m.file).catch((err) => toast(err.message, true));
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
  const results = await swift.call('mods:import', mc, files).catch((e) => { toast(e.message, true); return []; });
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
  // Draw the outer face of a 64x32 cape texture (x 1..10, y 1..16), the side seen from behind.
  const c = el('canvas', { width: 10, height: 16 });
  const img = new Image();
  img.onload = () => {
    const s = img.width / 64;
    c.getContext('2d').drawImage(img, 1 * s, 1 * s, 10 * s, 16 * s, 0, 0, 10, 16);
  };
  img.src = src;
  return c;
}

async function renderCapes() {
  const grid = $('#capeGrid');
  if (!state.info.capesEnabled) {
    grid.replaceChildren(el('div', { className: 'empty', textContent: 'Shared capes are not configured for this build.' }));
    return;
  }
  let data;
  try { data = await swift.call('capes:catalog'); } catch (e) {
    grid.replaceChildren(el('div', { className: 'empty', textContent: `Could not reach the cape server: ${e.message}` }));
    return;
  }
  const card = (cape) => {
    const selected = cape ? data.selected === cape.id : !data.selected;
    const locked = cape && !cape.owned;
    const n = el('div', { className: `cape${selected ? ' selected' : ''}${locked ? ' locked' : ''}` },
      cape ? capePreview(cape.image) : el('div', { className: 'none' }),
      el('div', { className: 'cname', textContent: cape ? cape.name : 'No cape' }),
      el('div', { className: 'cdesc', textContent: cape ? (cape.description || '') : 'Show your Minecraft cape, if any' }));
    if (locked) n.append(el('span', { className: 'lock', textContent: 'Locked' }));
    if (!locked && !selected) {
      n.addEventListener('click', async () => {
        try {
          await swift.call('capes:select', cape ? cape.id : null);
          toast(cape ? `Equipped ${cape.name}` : 'Cape removed');
          renderCapes();
        } catch (e) { toast(e.message, true); }
      });
    }
    return n;
  };
  grid.replaceChildren(card(null), ...data.capes.map(card));
}

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
  for (const id of ['fullscreen', 'performanceMods', 'closeOnLaunch', 'autoJoin', 'discordPresence', 'discordShowServer']) {
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

// ---- boot ------------------------------------------------------------------------

(async function boot() {
  state.info = await swift.call('app:info');
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
  state.running = await swift.call('game:running');
  setBusy(false);
})();
