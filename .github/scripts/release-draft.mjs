import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
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
  const matches = releases.filter(release => release.tag_name === version);
  const block = `${START}\n${notes.trim()}\n${END}`;
  if (matches.length === 0) return { action: 'create', body: block };
  if (matches.length !== 1) return { action: 'skip', reason: 'Multiple releases have this tag; leaving them unchanged.' };
  const release = matches[0];
  if (!release.draft) return { action: 'skip', reason: 'This version is already published; leaving it unchanged.' };
  const body = release.body ?? '';
  const range = managedRange(body);
  if (release.author?.login !== 'github-actions[bot]' || !range) {
    return { action: 'skip', reason: 'Existing draft is manual or unmarked; leaving its notes and assets unchanged.' };
  }
  return {
    action: 'update',
    id: release.id,
    // Only replace the generated block; preserve any manually maintained text around it.
    body: body.slice(0, range[0]) + block + body.slice(range[1]),
  };
}

export function syncDraft({ version, notes, assets, gh, log = console.log }) {
  const pages = JSON.parse(gh(['api', '--paginate', '--slurp', 'repos/{owner}/{repo}/releases']));
  const plan = planDraft(pages.flat(), version, notes);
  if (plan.action === 'skip') {
    log(`::warning::${plan.reason}`);
    return plan;
  }
  if (assets.length === 0) throw new Error('No plugin ZIP was built; refusing to create or update a draft.');
  if (plan.action === 'create') {
    gh(['release', 'create', version, '--draft', '--title', version, '--notes-file', '-', ...assets], plan.body);
  } else {
    gh(['api', '--method', 'PATCH', `repos/{owner}/{repo}/releases/${plan.id}`, '--input', '-'],
      JSON.stringify({ body: plan.body }));
    gh(['release', 'upload', version, ...assets, '--clobber']);
  }
  log(`Release draft ${plan.action}: ${version}`);
  return plan;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [version, notesPath] = process.argv.slice(2);
  if (!version || !notesPath) throw new Error('Usage: release-draft.mjs <version> <notes-file>');
  syncDraft({
    version,
    notes: readFileSync(notesPath, 'utf8'),
    assets: readdirSync('build/distributions').filter(name => name.endsWith('.zip'))
      .map(name => `build/distributions/${name}`),
    gh: (args, input) => execFileSync('gh', args, { encoding: 'utf8', input, maxBuffer: 16 * 1024 * 1024 }),
  });
}
