#!/usr/bin/env node
'use strict';
// Builds one SwiftClient jar per Minecraft 1.21.x release into mod/dist, and records
// each jar in dist/index.json so the launcher installs the right one.
//
//   node build-1.21.js              every 1.21 release
//   node build-1.21.js 1.21.8 1.21.5   just those

const { spawnSync } = require('node:child_process');
const path = require('node:path');

const VERSIONS = [
  '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5',
  '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11',
];

const asked = process.argv.slice(2).filter((a) => !a.startsWith('-'));
const unknown = asked.filter((v) => !VERSIONS.includes(v));
if (unknown.length) {
  console.error(`Not a 1.21.x release: ${unknown.join(', ')}`);
  process.exit(1);
}

const versions = asked.length ? asked : VERSIONS;
const windows = process.platform === 'win32';
const gradlew = path.join(__dirname, windows ? 'gradlew.bat' : 'gradlew');

const failed = [];
for (const version of versions) {
  console.log(`\n===== Minecraft ${version} =====`);
  const result = spawnSync(
    gradlew,
    ['-p', 'legacy', 'dist', `-Pminecraft_version=${version}`, '--console=plain'],
    { cwd: __dirname, stdio: 'inherit', shell: windows },
  );
  if (result.status !== 0) failed.push(version);
}

if (failed.length) {
  console.error(`\nFailed: ${failed.join(', ')}`);
  process.exit(1);
}
console.log(`\nBuilt ${versions.length} jar${versions.length === 1 ? '' : 's'} into mod/dist.`);
