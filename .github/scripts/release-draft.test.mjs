import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, mkdtempSync, readFileSync, rmdirSync, unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { START, END, draftAssetName, planDraft, stageDraftAssets, syncDraft } from './release-draft.mjs';
import { uploadAssets } from './release-assets.mjs';
import { validateRelease } from './release-validate.mjs';

const sha = 'a'.repeat(40);
const zip = 'build/distributions/HttpMate-1.2.8.zip';
const digest = `sha256:${'1'.repeat(64)}`;
const name = draftAssetName(zip, sha, digest);
const asset = { path: `build/distributions/${name}`, name, digest };
const draft = { id: 42, tag_name: '1.2.8', name: '1.2.8', draft: true, target_commitish: 'b'.repeat(40),
  upload_url: 'https://uploads.github.com/repos/EachFly/HttpMate/releases/42/assets{?name,label}',
  author: { login: 'github-actions[bot]' }, body: `Manual introduction\n${START}old${END}\nManual checklist` };

function simulate({ releases = [], refs = [], target = sha, assets = [asset], commit = sha,
  initial = draft, existing = [], atRead = () => {}, onUpload = () => {}, onPatch = () => {}, onCreate = () => {}, calls = [] } = {}) {
  const live = structuredClone(initial);
  const remoteAssets = structuredClone(existing);
  let reads = 0;
  let created = false;
  const result = syncDraft({ version: '1.2.8', sha: commit, notes: 'new notes', assets, log: () => {},
    gh: (args, input) => {
      calls.push({ args, input });
      if (args.at(-1) === 'repos/{owner}/{repo}/releases') return JSON.stringify([[], created ? [live] : releases]);
      if (args[0] === 'release' && args[1] === 'create') {
        Object.assign(live, { tag_name: args[2], name: args[2], body: input, target_commitish: commit });
        onCreate(live);
        created = true;
        return 'https://github.com/EachFly/HttpMate/releases/tag/untagged-preview';
      }
      if (args.at(-1) === 'repos/{owner}/{repo}/git/matching-refs/tags/') return JSON.stringify([refs]);
      if (args.at(-1).includes('/commits/')) return JSON.stringify({ sha: target });
      if (args[0] === 'api' && args[1] === 'repos/{owner}/{repo}/releases/42') {
        atRead(live, ++reads);
        return JSON.stringify(live);
      }
      if (args.at(-1) === 'repos/{owner}/{repo}/releases/42/assets') return JSON.stringify([remoteAssets]);
      if (args.includes('POST')) {
        onUpload(live);
        const uploaded = { name, digest, state: 'uploaded' };
        remoteAssets.push(uploaded);
        return JSON.stringify(uploaded);
      }
      if (args.includes('PATCH')) {
        Object.assign(live, JSON.parse(input));
        onPatch(live);
        return JSON.stringify(live);
      }
      return '';
    },
  });
  return { result, calls, live, remoteAssets };
}

test('new draft contains the changelog block and is pinned to the build SHA', () => {
  const { calls } = simulate();
  const creation = calls.find(call => call.args[0] === 'release' && call.args[1] === 'create');
  assert.deepEqual(creation.args, ['release', 'create', '1.2.8', '--target', sha, '--draft', '--title', '1.2.8', '--notes-file', '-', asset.path]);
  assert.ok(creation.input.includes(`<!-- httpmate:build:${sha} -->`));
  assert.ok(creation.input.includes(name));
  assert.ok(creation.input.includes(`${START}\nnew notes`));
  assert.equal(calls.at(-1).args[1], 'repos/{owner}/{repo}/releases/42');
});

test('published releases, manual drafts and unmarked drafts are preserved', () => {
  for (const release of [{ ...draft, draft: false }, { ...draft, immutable: true },
    { ...draft, author: { login: 'human' } }, { ...draft, body: 'manual notes' }]) {
    const { result, calls } = simulate({ releases: [release] });
    assert.equal(result.action, 'skip');
    assert.equal(calls.length, 1);
  }
});

test('same-version automatic drafts refresh notes, source SHA and ZIP without removing old assets', () => {
  const { result, live, calls } = simulate({ releases: [draft], existing: [{ name: 'old.zip', digest: 'old', state: 'uploaded' }] });
  assert.equal(result.action, 'update');
  assert.equal(live.target_commitish, sha);
  assert.ok(live.body.includes('Manual introduction'));
  assert.ok(live.body.endsWith('Manual checklist'));
  assert.ok(live.body.includes('new notes'));
  assert.ok(live.body.includes(name));
  assert.equal(calls.filter(call => call.args.includes('POST')).length, 1);
  const patch = calls.find(call => call.args.includes('PATCH'));
  assert.equal(JSON.parse(patch.input).tag_name, '1.2.8');
  assert.equal(Object.hasOwn(JSON.parse(patch.input), 'draft'), false);
  assert.equal(calls.some(call => call.args.includes('DELETE') || call.args.includes('--clobber')), false);
});

test('fresh snapshot preserves manual changes made after the list request', () => {
  const { live } = simulate({ releases: [draft], atRead: (current, count) => {
    if (count === 1) current.body = `Latest manual note\n${current.body}`;
  } });
  assert.ok(live.body.includes('Latest manual note'));
});

test('published before the fresh snapshot is skipped without writes', () => {
  const { result, calls } = simulate({ releases: [draft], initial: { ...draft, draft: false } });
  assert.equal(result.action, 'skip');
  assert.equal(calls.some(call => call.args.includes('POST') || call.args.includes('PATCH')), false);
});

test('a human edit or publication before an upload aborts without write calls', () => {
  for (const change of [current => { current.draft = false; }, current => { current.body += '\nNew manual note'; }]) {
    const calls = [];
    assert.throws(() => simulate({ releases: [draft], calls, atRead: (current, count) => {
      if (count === 2) change(current);
    } }), /published or changed/);
    assert.equal(calls.some(call => call.args.includes('POST') || call.args.includes('PATCH')), false);
  }
});

test('publication during ZIP upload stops the later metadata patch and does not delete old ZIPs', () => {
  const calls = [];
  assert.throws(() => simulate({ releases: [draft], calls, onUpload: current => { current.draft = false; } }), /published or changed/);
  assert.equal(calls.some(call => call.args.includes('PATCH') || call.args.includes('DELETE')), false);
});

test('a failed upload leaves notes and existing ZIPs untouched', () => {
  const calls = [];
  assert.throws(() => simulate({ releases: [draft], calls, onUpload: () => { throw new Error('Upload failed'); } }), /Upload failed/);
  assert.equal(calls.some(call => call.args.includes('PATCH') || call.args.includes('DELETE')), false);
});

test('publication reported by PATCH is flagged without reverting the release to draft', () => {
  const calls = [];
  assert.throws(() => simulate({ releases: [draft], calls, onPatch: current => { current.draft = false; } }), /manually before publishing/);
  const writes = calls.filter(call => call.args.includes('PATCH'));
  assert.equal(writes.length, 1);
  assert.equal(Object.hasOwn(JSON.parse(writes[0].input), 'draft'), false);
});

test('temporary draft URL is allowed when the actual API tag remains the version', () => {
  const { result } = simulate({ onCreate: current => {
    current.html_url = 'https://github.com/EachFly/HttpMate/releases/tag/untagged-preview';
  } });
  assert.equal(result.action, 'create');
});

test('creation fails if GitHub does not retain the expected tag or build identity', () => {
  assert.throws(() => simulate({ onCreate: current => { current.tag_name = 'untagged-wrong'; } }), /could not be identified/);
  assert.throws(() => simulate({ onCreate: current => { current.target_commitish = 'b'.repeat(40); } }), /identity verification failed/);
  assert.throws(() => simulate({ onCreate: current => { current.draft = false; } }), /identity verification failed/);
});

test('a successful PATCH response with a changed tag is rejected without rollback', () => {
  const calls = [];
  assert.throws(() => simulate({ releases: [draft], calls, onPatch: current => { current.tag_name = 'untagged-wrong'; } }), /expected tag 1\.2\.8, received untagged-wrong/);
  assert.equal(calls.filter(call => call.args.includes('PATCH')).length, 1);
  assert.equal(calls.some(call => call.args.includes('DELETE')), false);
});

test('same-version untagged releases are preserved instead of creating duplicate drafts', () => {
  for (const published of [true, false]) {
    const orphan = { ...draft, tag_name: 'untagged-existing', draft: !published };
    const { result, calls } = simulate({ releases: [orphan] });
    assert.equal(result.action, 'skip');
    assert.match(result.reason, /untagged placeholder/);
    assert.equal(calls.length, 1);
  }
  assert.equal(simulate({ releases: [{ ...draft, tag_name: 'untagged-old', name: '1.2.7' }] }).result.action, 'create');
});

test('losing the tag before the fresh snapshot is safely skipped', () => {
  const { result, calls } = simulate({ releases: [draft], atRead: (current, count) => {
    if (count === 1) current.tag_name = 'untagged-changed';
  } });
  assert.equal(result.action, 'skip');
  assert.equal(calls.some(call => call.args.includes('POST') || call.args.includes('PATCH')), false);
});

test('identical staged ZIPs are reused; conflicting names abort before PATCH', () => {
  const { calls } = simulate({ releases: [draft], existing: [{ name, digest, state: 'uploaded' }] });
  assert.equal(calls.some(call => call.args.includes('POST')), false);
  const conflicts = [];
  assert.throws(() => simulate({ releases: [draft], calls: conflicts, existing: [{ name, digest: 'wrong', state: 'uploaded' }] }), /conflicting bytes/);
  assert.equal(conflicts.some(call => call.args.includes('PATCH')), false);
});

test('malformed or duplicate managed blocks and build markers are skipped', () => {
  for (const body of [START, `${START}${START}${END}`, `${END}${START}`, `${START}${END} <!-- httpmate:build:broken -->`]) {
    assert.equal(planDraft([{ ...draft, body }], '1.2.8', 'new').action, 'skip');
  }
});

test('artifact names distinguish commits and bytes even when the version is unchanged', () => {
  assert.notEqual(name, draftAssetName(zip, 'b'.repeat(40), digest));
  assert.notEqual(name, draftAssetName(zip, sha, `sha256:${'2'.repeat(64)}`));
});

test('ZIP staging preserves input files, verifies bytes and cleans up only its temporary copies', () => {
  const directory = mkdtempSync(join(tmpdir(), 'httpmate-test-'));
  const source = join(directory, 'plugin.zip');
  const bytes = Buffer.from('ZIP staging fixture');
  writeFileSync(source, bytes);
  let staged;
  try {
    staged = stageDraftAssets([source], sha);
    const uploaded = staged.assets[0];
    assert.deepEqual(readFileSync(uploaded.path), bytes);
    assert.equal(uploaded.name, draftAssetName(source, sha, uploaded.digest));
    assert.equal(existsSync(source), true);
    assert.throws(() => stageDraftAssets([source, source], sha), /Duplicate ZIP/);
  } finally {
    if (staged) {
      staged.cleanup();
      assert.equal(existsSync(staged.assets[0].path), false);
    }
    assert.deepEqual(readFileSync(source), bytes);
    unlinkSync(source);
    rmdirSync(directory);
  }
});

test('rerunning the same completed build does not upload or patch anything', () => {
  const first = simulate({ releases: [draft] });
  const second = simulate({ releases: [first.live], initial: first.live, existing: first.remoteAssets });
  assert.equal(second.calls.some(call => call.args.includes('POST') || call.args.includes('PATCH')), false);
});

test('other versions are untouched and duplicate current versions are skipped', () => {
  assert.equal(planDraft([{ ...draft, tag_name: '1.2.7' }], '1.2.8', 'new').action, 'create');
  assert.equal(planDraft([draft, draft], '1.2.8', 'new').action, 'skip');
});

test('matching existing tags are resolved through the commits API', () => {
  assert.equal(simulate({ refs: [{ ref: 'refs/tags/1.2.8' }] }).result.action, 'create');
});

test('a mismatched existing tag is rejected', () => {
  assert.throws(() => simulate({ refs: [{ ref: 'refs/tags/1.2.8' }], target: 'b'.repeat(40) }), /does not point/);
});

test('missing assets and missing SHA are rejected', () => {
  assert.throws(() => simulate({ assets: [] }), /No plugin ZIP/);
  assert.throws(() => simulate({ commit: '' }), /full build commit SHA/);
});

test('API failures are not mistaken for absent tags', () => {
  assert.throws(() => syncDraft({ version: '1.2.8', sha, notes: 'n', assets: [asset], gh: () => { throw new Error('403'); } }), /403/);
});

function upload(existing) {
  const calls = [];
  uploadAssets({ tag: '1.2.8', assets: [zip], digest: () => 'sha256:abc', gh: args => {
    calls.push(args);
    if (args.at(-1).includes('/tags/')) return JSON.stringify({ id: 42 });
    if (args.at(-1).endsWith('/assets')) return JSON.stringify([existing]);
    return '';
  } });
  return calls;
}

test('uploads new assets without clobber or delete', () => {
  assert.deepEqual(upload([]).at(-1), ['release', 'upload', '1.2.8', zip]);
});

test('identical assets are reused without writes', () => {
  assert.equal(upload([{ name: 'HttpMate-1.2.8.zip', state: 'uploaded', digest: 'sha256:abc' }]).length, 2);
});

test('conflicting and unverifiable assets are never replaced', () => {
  for (const digest of ['sha256:other', null]) {
    assert.throws(() => upload([{ name: 'HttpMate-1.2.8.zip', state: 'uploaded', digest }]), /leaving it untouched/);
  }
});

test('release validation rejects mismatched versions, moved tags and malformed build markers', () => {
  assert.throws(() => validateRelease({ tag: '1.2.7', version: '1.2.8', sha }), /tag must match/);
  assert.throws(() => validateRelease({ tag: '1.2.8', version: '1.2.8', sha, body: `<!-- httpmate:build:${'b'.repeat(40)} -->` }), /checked-out commit/);
  assert.throws(() => validateRelease({ tag: '1.2.8', version: '1.2.8', sha, body: '<!-- httpmate:build:broken -->' }), /checked-out commit/);
  assert.equal(validateRelease({ tag: '1.2.8', version: '1.2.8', sha, body: `<!-- httpmate:build:${sha} -->` }), true);
  assert.throws(() => validateRelease({ tag: 'untagged-example', version: '1.2.8', sha }), /tag: untagged-example, pluginVersion: 1\.2\.8/);
});

test('manual legacy releases are explicitly reported as unmarked', () => {
  assert.equal(validateRelease({ tag: '1.2.8', version: '1.2.8', sha, body: 'Manual notes' }), false);
});
