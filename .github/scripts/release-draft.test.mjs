import assert from 'node:assert/strict';
import test from 'node:test';
import { START, END, planDraft, syncDraft } from './release-draft.mjs';

function automatic(overrides = {}) {
  return {
    id: 42, tag_name: '1.2.8', draft: true,
    author: { login: 'github-actions[bot]' },
    body: `${START}\nold notes\n${END}`,
    ...overrides,
  };
}

test('new draft has a managed changelog block', () => {
  assert.deepEqual(planDraft([], '1.2.8', '  new notes\n'), {
    action: 'create', body: `${START}\nnew notes\n${END}`,
  });
});

test('update preserves manual text outside the generated block', () => {
  const draft = automatic();
  draft.body = `Manual introduction\n\n${draft.body}\n\nManual checklist`;
  const plan = planDraft([draft], '1.2.8', 'new notes');
  assert.equal(plan.action, 'update');
  assert.equal(plan.id, 42);
  assert.equal(plan.body, `Manual introduction\n\n${START}\nnew notes\n${END}\n\nManual checklist`);
});

test('other versions, including manual drafts, are untouched', () => {
  const releases = [automatic({ tag_name: '1.2.7' }), automatic({ tag_name: '2.0.0', author: { login: 'human' } })];
  assert.equal(planDraft(releases, '1.2.8', 'new').action, 'create');
});

test('manual and legacy unmarked drafts of this version are preserved', () => {
  for (const release of [automatic({ author: { login: 'human' } }), automatic({ body: 'hand-written notes' })]) {
    assert.equal(planDraft([release], '1.2.8', 'new').action, 'skip');
  }
});

test('published releases are never rewritten', () => {
  assert.equal(planDraft([automatic({ draft: false })], '1.2.8', 'new').action, 'skip');
});

test('ambiguous releases and malformed markers are preserved', () => {
  assert.equal(planDraft([automatic(), automatic({ id: 43 })], '1.2.8', 'new').action, 'skip');
  for (const body of [START, `${END}${START}`, `${START}${START}${END}`, `${START}${END}${END}`]) {
    assert.equal(planDraft([automatic({ body })], '1.2.8', 'new').action, 'skip');
  }
});

function simulate(pages, assets = ['build/distributions/HttpMate-1.2.8.zip']) {
  const calls = [];
  const plan = syncDraft({
    version: '1.2.8', notes: 'new notes', assets, log: () => {},
    gh: (args, input) => {
      calls.push({ args, input });
      return calls.length === 1 ? JSON.stringify(pages) : '';
    },
  });
  return { plan, calls };
}

test('finds the current draft across paginated API responses and never deletes releases', () => {
  const { calls } = simulate([[automatic({ tag_name: '1.2.7' })], [automatic()]]);
  assert.deepEqual(calls[0].args, ['api', '--paginate', '--slurp', 'repos/{owner}/{repo}/releases']);
  assert.deepEqual(calls[1].args, ['api', '--method', 'PATCH', 'repos/{owner}/{repo}/releases/42', '--input', '-']);
  assert.equal(JSON.parse(calls[1].input).body, `${START}\nnew notes\n${END}`);
  assert.deepEqual(calls[2].args, ['release', 'upload', '1.2.8', 'build/distributions/HttpMate-1.2.8.zip', '--clobber']);
  assert.equal(calls.some(call => call.args.includes('DELETE') || call.args.includes('delete')), false);
});

test('creates a draft with notes on stdin, not shell-interpolated text', () => {
  const { calls } = simulate([[]]);
  assert.deepEqual(calls[1].args, ['release', 'create', '1.2.8', '--draft', '--title', '1.2.8', '--notes-file', '-',
    'build/distributions/HttpMate-1.2.8.zip']);
  assert.equal(calls[1].input, `${START}\nnew notes\n${END}`);
});

test('skipped drafts receive no write calls or asset uploads', () => {
  const { calls } = simulate([[automatic({ body: 'manual notes' })]]);
  assert.equal(calls.length, 1);
});

test('does not mutate releases when plugin ZIPs are missing', () => {
  assert.throws(() => simulate([[]], []), /No plugin ZIP/);
});
