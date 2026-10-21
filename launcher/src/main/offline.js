'use strict';
// Offline-mode play: a different in-game name for servers running online-mode=false.
//
// Only available on top of a signed-in Microsoft account that owns Minecraft: Java Edition,
// so every player still owns the game (Minecraft EULA). The offline session carries no
// Mojang token, so online-mode servers reject it and Realms are unavailable.

const crypto = require('crypto');

const NAME_RE = /^[A-Za-z0-9_]{3,16}$/;

function validOfflineName(name) {
  return typeof name === 'string' && NAME_RE.test(name);
}

/** The UUID offline-mode servers give a player: UUID v3 of "OfflinePlayer:<name>". */
function offlineUuid(name) {
  const md5 = crypto.createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  md5[6] = (md5[6] & 0x0f) | 0x30;
  md5[8] = (md5[8] & 0x3f) | 0x80;
  return md5.toString('hex');
}

function offlineSession(name) {
  return { name, uuid: offlineUuid(name), accessToken: '0', xuid: '', userType: 'legacy', offline: true };
}

module.exports = { validOfflineName, offlineUuid, offlineSession };
