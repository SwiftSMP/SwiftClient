'use strict';
// Microsoft account -> Xbox Live -> XSTS -> Minecraft services sign-in.
//
// The user signs in on Microsoft's own page inside an isolated browser window
// (OAuth 2.0 authorization code + PKCE). SwiftClient never sees a password.
// Only the refresh token is stored, encrypted with the OS keychain (safeStorage/DPAPI).

const fs = require('fs');
const crypto = require('crypto');
const { BrowserWindow, safeStorage, session } = require('electron');
const { fetchJson } = require('./download');
const { paths } = require('./config');

const AUTHORITY = 'https://login.microsoftonline.com/consumers/oauth2/v2.0';
const REDIRECT_URI = 'https://login.microsoftonline.com/common/oauth2/nativeclient';
const SCOPE = 'XboxLive.signin offline_access';
const AUTH_PARTITION = 'persist:swiftclient-msauth';

const XSTS_ERRORS = {
  2148916233: 'This Microsoft account has no Xbox profile yet. Sign in once at xbox.com to create one, then try again.',
  2148916235: 'Xbox Live is not available in your country or region.',
  2148916236: 'This account needs adult verification on xbox.com before it can play.',
  2148916237: 'This account needs adult verification on xbox.com before it can play.',
  2148916238: 'This is a child account. An adult must add it to a Microsoft family group before it can play.',
};

// Errors Microsoft's sign-in page can send back instead of a code.
const OAUTH_ERRORS = {
  server_error: 'Microsoft had a temporary problem signing you in. Please try again in a minute.',
  temporarily_unavailable: 'Microsoft sign-in is temporarily unavailable. Please try again in a minute.',
  access_denied: 'Sign-in was cancelled.',
};

function b64url(buf) {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function interactiveLogin(clientId, parent) {
  const verifier = b64url(crypto.randomBytes(32));
  const challenge = b64url(crypto.createHash('sha256').update(verifier).digest());
  const state = b64url(crypto.randomBytes(16));
  const url = `${AUTHORITY}/authorize?` + new URLSearchParams({
    client_id: clientId,
    response_type: 'code',
    redirect_uri: REDIRECT_URI,
    scope: SCOPE,
    prompt: 'select_account',
    code_challenge: challenge,
    code_challenge_method: 'S256',
    state,
  });

  return new Promise((resolve, reject) => {
    const win = new BrowserWindow({
      width: 520, height: 680, parent, modal: !!parent, autoHideMenuBar: true,
      title: 'Sign in with Microsoft', backgroundColor: '#0d1020',
      webPreferences: { partition: AUTH_PARTITION, nodeIntegration: false, contextIsolation: true, sandbox: true },
    });
    let finished = false;
    const finish = (err, value) => {
      if (finished) return;
      finished = true;
      if (!win.isDestroyed()) win.close();
      err ? reject(err) : resolve(value);
    };
    const check = (target) => {
      if (!target.startsWith(REDIRECT_URI)) return false;
      const q = new URL(target).searchParams;
      if (q.get('error')) finish(new Error(OAUTH_ERRORS[q.get('error')] || q.get('error_description') || q.get('error')));
      else if (q.get('state') !== state) finish(new Error('Sign-in state mismatch, please try again.'));
      else finish(null, { code: q.get('code'), verifier });
      return true;
    };
    win.webContents.on('will-redirect', (e, target) => { if (check(target)) e.preventDefault(); });
    win.webContents.on('will-navigate', (e, target) => { if (check(target)) e.preventDefault(); });
    win.webContents.on('did-navigate', (_e, target) => check(target));
    win.on('closed', () => finish(new Error('Sign-in window was closed.')));
    win.loadURL(url);
  });
}

async function msToken(clientId, params) {
  return fetchJson(`${AUTHORITY}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: clientId, scope: SCOPE, redirect_uri: REDIRECT_URI, ...params }),
  });
}

async function minecraftFromMsAccessToken(msAccessToken) {
  const json = { 'Content-Type': 'application/json', Accept: 'application/json' };
  const xbl = await fetchJson('https://user.auth.xboxlive.com/user/authenticate', {
    method: 'POST', headers: json,
    body: JSON.stringify({
      Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${msAccessToken}` },
      RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT',
    }),
  });
  let xsts;
  try {
    xsts = await fetchJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
      method: 'POST', headers: json,
      body: JSON.stringify({
        Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.Token] },
        RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT',
      }),
    });
  } catch (e) {
    let xerr;
    try { xerr = JSON.parse(e.body).XErr; } catch { /* not JSON */ }
    throw new Error(XSTS_ERRORS[xerr] || `Xbox Live sign-in failed (${xerr || e.status}).`);
  }
  const uhs = xsts.DisplayClaims.xui[0].uhs;
  let mc;
  try {
    mc = await fetchJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
      method: 'POST', headers: json,
      body: JSON.stringify({ identityToken: `XBL3.0 x=${uhs};${xsts.Token}` }),
    });
  } catch (e) {
    // Mojang answers 403 "Invalid app registration" until it has approved this app's client ID.
    if (e.status === 403 && /app registration/i.test(e.body || '')) {
      throw new Error('SwiftClient is still waiting for Mojang to approve its sign-in. Your Microsoft and Xbox sign-in worked; please try again later.');
    }
    if (e.status === 429) throw new Error('Too many sign-in attempts. Please wait a few minutes and try again.');
    throw e;
  }
  const auth = { Authorization: `Bearer ${mc.access_token}` };
  let profile;
  try {
    profile = await fetchJson('https://api.minecraftservices.com/minecraft/profile', { headers: auth });
  } catch (e) {
    if (e.status === 404) throw new Error('This Microsoft account does not own Minecraft: Java Edition.');
    throw e;
  }
  return {
    accessToken: mc.access_token,
    expiresAt: Date.now() + (mc.expires_in - 60) * 1000,
    uuid: profile.id,
    name: profile.name,
    xuid: decodeXuid(xsts.Token),
    skins: profile.skins || [],
  };
}

function decodeXuid(xstsToken) {
  try {
    const payload = JSON.parse(Buffer.from(xstsToken.split('.')[1], 'base64').toString());
    return payload.xid || payload.xuid || '';
  } catch { return ''; }
}

// ---- storage --------------------------------------------------------------

function saveAccount(account) {
  fs.mkdirSync(paths.root, { recursive: true });
  const data = Buffer.from(JSON.stringify(account));
  const blob = safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(data.toString()) : null;
  if (!blob) throw new Error('OS encryption is unavailable; refusing to store the sign-in token in plain text.');
  fs.writeFileSync(paths.account, blob);
}

function readAccount() {
  try {
    const a = clean(JSON.parse(safeStorage.decryptString(fs.readFileSync(paths.account))));
    // A half-written or old-schema file must not look like a signed-in account.
    return a && a.refreshToken && a.mc && a.mc.uuid ? a : null;
  } catch { return null; }
}

// 0.1.1-0.1.3 also stored offline names; that feature was removed, so drop them.
function clean(a) {
  if (!a) return a;
  const { offlineName, offlineNames, active, ...rest } = a;
  return rest;
}

function publicAccount(a) {
  return a && a.mc ? { uuid: a.mc.uuid, name: a.mc.name } : null;
}

// ---- API --------------------------------------------------------------------

class Auth {
  constructor(clientId) {
    this.clientId = clientId;
    // Read lazily: safeStorage is only usable once Electron's app is ready.
    this.loaded = false;
    this.account = null;
  }

  load() {
    if (!this.loaded) {
      this.account = readAccount();
      this.loaded = true;
    }
    return this.account;
  }

  current() { return publicAccount(this.load()); }

  async login(parent) {
    if (!this.clientId) {
      throw new Error('Microsoft sign-in is not configured yet: set msClientId in swiftclient.config.json (see README).');
    }
    const { code, verifier } = await interactiveLogin(this.clientId, parent);
    const ms = await msToken(this.clientId, { grant_type: 'authorization_code', code, code_verifier: verifier });
    const mc = await minecraftFromMsAccessToken(ms.access_token);
    this.account = { refreshToken: ms.refresh_token, mc };
    saveAccount(this.account);
    return publicAccount(this.account);
  }

  /** Returns a fresh Minecraft session ({accessToken, uuid, name, xuid}), refreshing if needed. */
  async session() {
    if (!this.load()) throw new Error('Not signed in.');
    if (this.account.mc.expiresAt > Date.now()) return this.account.mc;
    let ms;
    try {
      ms = await msToken(this.clientId, { grant_type: 'refresh_token', refresh_token: this.account.refreshToken });
    } catch (e) {
      if (e.status === 400) { await this.logout(); throw new Error('Your sign-in expired. Please sign in again.'); }
      throw e;
    }
    const mc = await minecraftFromMsAccessToken(ms.access_token);
    this.account = { ...this.account, refreshToken: ms.refresh_token || this.account.refreshToken, mc };
    saveAccount(this.account);
    return mc;
  }

  async logout() {
    this.account = null;
    try { fs.unlinkSync(paths.account); } catch { /* already gone */ }
    await session.fromPartition(AUTH_PARTITION).clearStorageData();
  }
}

module.exports = { Auth };
