import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
import { basename } from 'node:path';
import { pathToFileURL } from 'node:url';

// Never delete an old asset. Re-runs may reuse identical bytes, but not replace them.
export function uploadAssets({ tag, assets, gh, digest }) {
  if (assets.length === 0) throw new Error('No plugin ZIP was built.');
  const release = JSON.parse(gh(['api', `repos/{owner}/{repo}/releases/tags/${encodeURIComponent(tag)}`]));
  const existing = JSON.parse(gh(['api', '--paginate', '--slurp', `repos/{owner}/{repo}/releases/${release.id}/assets`])).flat();
  for (const path of assets) {
    const matches = existing.filter(asset => asset.name === basename(path));
    if (matches.length) {
      if (matches.length !== 1 || matches[0].state !== 'uploaded' || matches[0].digest !== digest(path)) {
        throw new Error(`Asset ${basename(path)} already exists with different or unverifiable bytes; leaving it untouched.`);
      }
    } else gh(['release', 'upload', tag, path]);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const tag = process.argv[2];
  if (!tag) throw new Error('Usage: release-assets.mjs <tag>');
  uploadAssets({
    tag,
    assets: readdirSync('build/distributions').filter(name => name.endsWith('.zip')).map(name => `build/distributions/${name}`),
    digest: path => `sha256:${createHash('sha256').update(readFileSync(path)).digest('hex')}`,
    gh: args => execFileSync('gh', args, { encoding: 'utf8', maxBuffer: 16 * 1024 * 1024 }),
  });
}
