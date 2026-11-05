'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { forgeVersionFor, neoforgeVersionFor, normalizeInstallerProfile } = require('../src/main/loaders');

const xml = (versions) => `<?xml version="1.0"?><metadata><versioning><versions>${versions.map((v) => `<version>${v}</version>`).join('')}</versions></versioning></metadata>`;

test('forgeVersionFor picks the newest build for the Minecraft version', () => {
  const data = xml(['1.21-51.0.0', '1.21.1-52.0.16', '1.21.1-52.1.0', '1.21.8-53.0.2']);
  assert.equal(forgeVersionFor('1.21.1', data), '52.1.0');
  assert.equal(forgeVersionFor('1.21.8', data), '53.0.2');
  assert.throws(() => forgeVersionFor('1.20.4', data), /does not support/);
});

test('neoforgeVersionFor drops the leading 1. and prefers stable builds', () => {
  const data = xml(['21.7.10', '21.8.40', '21.8.47-beta', '26.1.0', '26.3.0-beta']);
  assert.equal(neoforgeVersionFor('1.21.8', data), '21.8.40');
  assert.equal(neoforgeVersionFor('1.21.7', data), '21.7.10');
  assert.equal(neoforgeVersionFor('26.1', data), '26.1.0');
  assert.equal(neoforgeVersionFor('26.3', data), '26.3.0-beta');
  assert.throws(() => neoforgeVersionFor('1.20.4', data), /does not support/);
});

test('normalizeInstallerProfile maps installer JSON to the Fabric profile shape', () => {
  const profile = normalizeInstallerProfile({
    id: '1.21.1-forge-52.1.0',
    mainClass: 'cpw.mods.bootstraplauncher.BootstrapLauncher',
    arguments: { game: ['--username', '${auth_player_name}'], jvm: ['-Dforgewrapper.librariesDir=${library_directory}'] },
    libraries: [
      { name: 'cpw.mods:securejarhandler:3.0.8', downloads: { artifact: { url: 'https://example/secure.jar', sha1: 'abc', size: 42 } } },
      { name: 'org.ow2.asm:asm:9.7' },
    ],
  }, 'https://maven.minecraftforge.net/');
  assert.equal(profile.mainClass, 'cpw.mods.bootstraplauncher.BootstrapLauncher');
  assert.equal(profile.libraries[0].url, 'https://example/secure.jar');
  assert.equal(profile.libraries[0].sha1, 'abc');
  assert.equal(profile.libraries[0].size, 42);
  assert.equal(profile.libraries[1].url, 'https://maven.minecraftforge.net/org/ow2/asm/asm/9.7/asm-9.7.jar');
  assert.deepEqual(profile.arguments.game, ['--username', '${auth_player_name}']);
});
