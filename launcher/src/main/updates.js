'use strict';
// Update helpers: what the newest published release is, and where its installer lives.
// electron-updater installs updates itself; this is the fallback that lets every build
// (including an old or unpackaged one) find and download the newest release.

const semver = require('semver');

const REPO = { owner: 'SwiftSMP', repo: 'SwiftClient' };
const API = 'https://api.github.com';

function parseTag(tag) {
  const value = semver.valid(String(tag || '').trim().replace(/^v/i, ''));
  return value || null;
}

function isNewer(latest, current) {
  const a = parseTag(latest);
  const b = parseTag(current);
  if (!a || !b) return false;
  return semver.gt(a, b);
}

async function latestRelease({ owner = REPO.owner, repo = REPO.repo, fetchImpl = fetch, timeoutMs = 8000 } = {}) {
  const res = await fetchImpl(`${API}/repos/${owner}/${repo}/releases/latest`, {
    headers: { Accept: 'application/vnd.github+json', 'User-Agent': 'SwiftClient-Launcher' },
    signal: AbortSignal.timeout(timeoutMs),
  });
  if (!res.ok) throw new Error(`GitHub releases lookup failed (HTTP ${res.status}).`);
  const json = await res.json();
  return {
    version: parseTag(json.tag_name) || String(json.tag_name || ''),
    url: json.html_url,
    notes: json.body || '',
    publishedAt: json.published_at || null,
    assets: (json.assets || []).map((a) => ({ name: a.name, url: a.browser_download_url, size: a.size })),
  };
}

async function installerUrl(release, name = 'SwiftClient-Setup.exe') {
  if (!release) return null;
  const asset = (release.assets || []).find((a) => a.name === name);
  return asset ? asset.url : release.url || null;
}

module.exports = { parseTag, isNewer, latestRelease, installerUrl, REPO };
