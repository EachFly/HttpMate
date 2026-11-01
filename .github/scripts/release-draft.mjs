import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, existsSync, mkdtempSync, readFileSync, readdirSync, rmdirSync, unlinkSync } from 'node:fs';
import { basename, join } from 'node:path';
import { tmpdir } from 'node:os';
import { pathToFileURL } from 'node:url';

export const START = '<!-- httpmate:changelog:start -->';
export const END = '<!-- httpmate:changelog:end -->';

function managedRange(body) {
  const start = body.indexOf(START);
  const end = body.indexOf(END);
  if (start < 0 || end < start || body.indexOf(START, start + START.length) >= 0 ||
      body.indexOf(END, end + END.length) >= 0) return null;
  return [start, end + END.length];
}

export function planDraft(releases, version, notes) {
  // A published placeholder tag must not result in another release for the same version.
  if (releases.some(release => release.name === version && release.tag_name?.startsWith('untagged-'))) {
    return { action: 'skip', reason: 'A release with this version title still has an untagged placeholder. Check its Git tag manually; leaving it unchanged.' };
  }
  const matches = releases.filter(release => release.tag_name === version);
  const block = `${START}\n${notes.trim()}\n${END}`;
  if (matches.length === 0) return { action: 'create', body: block };
  if (matches.length !== 1) return { action: 'skip', reason: 'Multiple releases have this tag; leaving them unchanged.' };
  const release = matches[0];
  if (!release.draft || release.immutable) return { action: 'skip', reason: 'This version is published; leaving it unchanged.' };
  const body = release.body ?? '';
  const range = managedRange(body);
  if (release.author?.login !== 'github-actions[bot]' || !range) {
    return { action: 'skip', reason: 'Existing draft is manual or unmarked; leaving it unchanged.' };
  }
  const buildMarkers = [...body.matchAll(/<!-- httpmate:build:[0-9a-f]{40} -->/gi)];
  if (buildMarkers.length > 1 || (body.includes('httpmate:build:') && buildMarkers.length !== 1)) {
    return { action: 'skip', reason: 'Build marker is ambiguous or malformed; leaving the draft unchanged.' };
  }
  return { action: 'update', id: release.id, body: body.slice(0, range[0]) + block + body.slice(range[1]) };
}

export function draftAssetName(path, sha, digest) {
  if (!/^[0-9a-f]{40}$/i.test(sha) || !/^sha256:[0-9a-f]{64}$/i.test(digest)) throw new Error('Invalid build identity.');
  const name = basename(path);
  if (!name.endsWith('.zip')) throw new Error('Only ZIP assets are supported.');
  return `${name.slice(0, -4)}-${sha.slice(0, 12)}-${digest.slice(7, 19)}.zip`;
}

// Files are staged under unique names. No existing GitHub asset is ever deleted or clobbered.
export function stageDraftAssets(paths, sha) {
  const directory = mkdtempSync(join(tmpdir(), 'httpmate-draft-'));
  const staged = [];
  const cleanup = () => {
    for (const path of staged) if (existsSync(path)) unlinkSync(path);
    rmdirSync(directory);
  };
  try {
    const assets = paths.map(path => {
      const digest = `sha256:${createHash('sha256').update(readFileSync(path)).digest('hex')}`;
      const name = draftAssetName(path, sha, digest);
      const destination = join(directory, name);
      if (staged.includes(destination)) throw new Error('Duplicate ZIP asset names.');
      staged.push(destination);
      copyFileSync(path, destination);
      return { path: destination, name, digest };
    });
    return { assets, cleanup };
  } catch (error) {
    cleanup();
    throw error;
  }
}

function metadata(release) {
  // Asset uploads can change updated_at; compare the human-editable metadata itself.
  return JSON.stringify([release.id, release.tag_name, release.draft, release.immutable ?? false,
    release.author?.login, release.body ?? '', release.target_commitish, release.name, release.prerelease]);
}

function withBuild(body, sha) {
  const marker = `<!-- httpmate:build:${sha} -->`;
  return /<!-- httpmate:build:[0-9a-f]{40} -->/i.test(body)
    ? body.replace(/<!-- httpmate:build:[0-9a-f]{40} -->/i, marker) : `${marker}\n${body}`;
}

function assertDraftIdentity(release, version, sha, body) {
  if (!release.draft || release.immutable || release.tag_name !== version ||
      release.body !== body || release.target_commitish !== sha) {
    throw new Error(`Release identity verification failed (expected tag ${version}, received ${release.tag_name ?? '<missing>'}). ` +
      'Check the draft tag and build commit manually before publishing; no automatic rollback will be attempted.');
  }
}

export function syncDraft({ version, sha, notes, assets, gh, log = console.log }) {
  if (!/^[0-9a-f]{40}$/i.test(sha ?? '')) throw new Error('A full build commit SHA is required.');
  if (!/^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$/.test(version)) throw new Error('Invalid plugin version.');
  const pages = JSON.parse(gh(['api', '--paginate', '--slurp', 'repos/{owner}/{repo}/releases']));
  let plan = planDraft(pages.flat(), version, notes);
  if (plan.action === 'skip') {
    log(`::warning::${plan.reason}`);
    return plan;
  }
  if (assets.length === 0) throw new Error('No plugin ZIP was built; refusing to create or update a draft.');
  if (assets.some(asset => basename(asset.path) !== asset.name || !/^sha256:[0-9a-f]{64}$/i.test(asset.digest))) {
    throw new Error('Assets must have staged, content-addressed names and SHA-256 digests.');
  }
  const generatedNotes = `${notes.trim()}\n\n当前构建提交 / Build commit: \`${sha}\`\n\n` +
    `当前构建附件 / Current build assets:\n${assets.map(asset => `- \`${asset.name}\``).join('\n')}\n\n` +
    '其他附件为历史构建 / Other attachments are historical builds.';
  let expected;
  if (plan.action === 'update') {
    // A fresh read also preserves manual edits made since the initial list request.
    expected = JSON.parse(gh(['api', `repos/{owner}/{repo}/releases/${plan.id}`]));
    plan = planDraft([expected], version, generatedNotes);
    if (plan.action === 'skip') { log(`::warning::${plan.reason}`); return plan; }
  } else plan = planDraft([], version, generatedNotes);
  // List refs explicitly: a missing tag is normal, but an API/auth failure is not.
  const refs = JSON.parse(gh(['api', '--paginate', '--slurp', 'repos/{owner}/{repo}/git/matching-refs/tags/'])).flat();
  if (refs.some(ref => ref.ref === `refs/tags/${version}`)) {
    const target = JSON.parse(gh(['api', `repos/{owner}/{repo}/commits/${encodeURIComponent(version)}`])).sha;
    if (target !== sha) throw new Error('Existing tag does not point to the built commit; refusing to publish.');
  }
  const body = withBuild(plan.body, sha);
  if (plan.action === 'create') {
    gh(['release', 'create', version, '--target', sha, '--draft', '--title', version, '--notes-file', '-', ...assets.map(asset => asset.path)], body);
    // A draft's html_url may contain untagged-*; only the API tag_name establishes identity.
    const createdPages = JSON.parse(gh(['api', '--paginate', '--slurp', 'repos/{owner}/{repo}/releases']));
    const created = createdPages.flat().filter(release => release.tag_name === version);
    if (created.length !== 1) throw new Error('Created draft could not be identified by its version tag; inspect it manually before publishing.');
    const verified = JSON.parse(gh(['api', `repos/{owner}/{repo}/releases/${created[0].id}`]));
    assertDraftIdentity(verified, version, sha, body);
  } else {
    const assertUnchanged = () => {
      const live = JSON.parse(gh(['api', `repos/{owner}/{repo}/releases/${plan.id}`]));
      if (!live.draft || live.immutable || metadata(live) !== metadata(expected)) {
        throw new Error('Draft was published or changed during this build; stopping without further writes.');
      }
    };
    const existing = JSON.parse(gh(['api', '--paginate', '--slurp', `repos/{owner}/{repo}/releases/${plan.id}/assets`])).flat();
    for (const asset of assets) {
      const matches = existing.filter(item => item.name === asset.name);
      if (matches.length) {
        if (matches.length !== 1 || matches[0].state !== 'uploaded' || matches[0].digest !== asset.digest) {
          throw new Error('A staged asset name already exists with conflicting bytes; leaving it unchanged.');
        }
        continue;
      }
      assertUnchanged();
      const uploadUrl = expected.upload_url?.replace(/\{.*$/, '');
      if (!uploadUrl || !/^https:\/\/uploads\.github\.com\/repos\/[^/]+\/[^/]+\/releases\/\d+\/assets$/.test(uploadUrl) ||
          !uploadUrl.endsWith(`/releases/${plan.id}/assets`)) throw new Error('Invalid release upload URL.');
      gh(['api', '--method', 'POST', `${uploadUrl}?name=${encodeURIComponent(asset.name)}`, '--header', 'Content-Type: application/zip', '--input', asset.path]);
    }
    assertUnchanged();
    // Never set draft:true: a concurrent publication must not be reverted to a draft.
    // GitHub has no documented atomic "only update if still draft" precondition.
    if (body !== (expected.body ?? '') || expected.target_commitish !== sha) {
      const updated = JSON.parse(gh(['api', '--method', 'PATCH', `repos/{owner}/{repo}/releases/${plan.id}`, '--input', '-'],
        JSON.stringify({ tag_name: version, body, target_commitish: sha })));
      assertDraftIdentity(updated, version, sha, body);
    }
  }
  log(`Release draft ${plan.action}: ${version}`);
  return plan;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [version, notesPath] = process.argv.slice(2);
  if (!version || !notesPath) throw new Error('Usage: release-draft.mjs <version> <notes-file>');
  const staged = stageDraftAssets(readdirSync('build/distributions').filter(name => name.endsWith('.zip'))
    .map(name => `build/distributions/${name}`), process.env.GITHUB_SHA);
  try {
    syncDraft({ version, sha: process.env.GITHUB_SHA, notes: readFileSync(notesPath, 'utf8'), assets: staged.assets,
      gh: (args, input) => execFileSync('gh', args, { encoding: 'utf8', input, maxBuffer: 16 * 1024 * 1024 }),
    });
  } finally { staged.cleanup(); }
}
