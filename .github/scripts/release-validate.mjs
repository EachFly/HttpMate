import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function validateRelease({ tag, version, sha, body = '' }) {
  if (!version || tag !== version) throw new Error('Release tag must match pluginVersion.');
  const markers = [...body.matchAll(/<!-- httpmate:build:([0-9a-f]{40}) -->/gi)];
  if (body.includes('httpmate:build:') && (markers.length !== 1 || markers[0][1] !== sha)) {
    throw new Error('Release build marker does not match the checked-out commit; refusing Marketplace publication.');
  }
  return markers.length === 1;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const version = readFileSync('gradle.properties', 'utf8').match(/^pluginVersion\s*=\s*(\S+)\s*$/m)?.[1];
  const marked = validateRelease({ tag: process.env.RELEASE_TAG, version,
    sha: execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim(), body: process.env.RELEASE_BODY ?? '' });
  if (!marked) console.log('::warning::Manual/legacy release has no build marker; only tag/version consistency can be verified.');
}
