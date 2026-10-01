// Generates design/ux-copy.md from design/prototype.html so the copy deck can never drift from the screens.
// Run: node android/Phase-One/design/tools/build-copy-deck.mjs
// Review-only controls (dashed boxes: variant tabs, "Demo:" and "Simulate" buttons) are excluded: they are not product copy.
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const designDir = path.resolve(here, '..');
const html = fs.readFileSync(path.join(designDir, 'prototype.html'), 'utf8');
const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)];
const code = scripts[scripts.length - 1][1];

// Minimal stub browser: every DOM access returns a harmless proxy.
const dummy = () => new Proxy(function () {}, {
  get: (_t, p) => (p === 'style' ? { setProperty() {} } : p === 'dataset' ? {} : p === Symbol.toPrimitive ? () => '' : dummy()),
  set: () => true,
  apply: () => dummy(),
});
const sandbox = { console, setTimeout: () => 0, history: { replaceState() {} }, location: { hash: '' }, document: dummy() };
sandbox.window = sandbox;
sandbox.addEventListener = () => {};
vm.createContext(sandbox);
vm.runInContext(code, sandbox);
const EQO = vm.runInContext('EQO', sandbox);

// Screens that have switchable variants (review controls in the prototype).
const VARIANTS = {
  s05: ['401', '429', 'credit', 'model', 'net'],
  s09: ['port', 'conn', 'reboot', 'revoked'],
  s11: ['ok', 'fail'],
  s13: ['ok', 'fail'],
  s22d: ['denied', 'changed'],
  s25: ['rate', 'adb', 'a11y', 'helper', 'unsure', 'chrome', 'revoked', 'update'],
};
// Action-dependent states (what the screen says after the person has done something).
const STATES = [
  ['s03', 'all required steps ready', (s) => { s.model = s.a11y = s.helper = true; }],
  ['s03', 'optional steps ready', (s) => { s.model = s.a11y = s.helper = true; s.browser = 'ready'; s.vd = 'ready'; s.sms = 'ready'; }],
  ['s04', 'test running', (s) => { s.v.test = 'run'; }],
  ['s04', 'test passed', (s) => { s.v.test = 'pass'; }],
  ['s06', 'after turning it on', (s) => { s.a11y = true; }],
  ['s08', 'first three checks done', (s) => { s.adb = [true, true, true, false, false]; }],
  ['s08', 'connect step', (s) => { s.adb = [true, true, true, true, false]; }],
  ['s08', 'all five checks done', (s) => { s.adb = [true, true, true, true, true]; }],
  ['s13', 'Chrome verified', (s) => { s.chromeDebug = true; }],
  ['s19', 'everything ready', (s) => { s.model = s.a11y = s.helper = true; s.adb.fill(true); s.browser = 'ready'; s.vd = 'ready'; s.sms = 'ready'; }],
  ['s20', 'after the result is confirmed', (s) => { s.v.toast = 'Thanks. Marked as done.'; }],
  ['s21', 'step 3 of 4', (s) => { s.runStep = 2; }],
  ['s21', 'paused', (s) => { s.paused = true; }],
  ['s21', 'taken over, not watching', (s) => { s.tookOver = true; }],
  ['s21', 'taken over, watching allowed', (s) => { s.tookOver = true; s.observe = true; }],
  ['s21', 'foreground fallback', (s) => { s.fg = true; }],
];

const decode = (s) => s.replace(/&nbsp;/g, ' ').replace(/&rsaquo;/g, '›').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
const stripReview = (h) => h
  .replace(/<div class="vtabs review"[^>]*>[\s\S]*?<\/div>/g, '')
  .replace(/<button[^>]*data-demo="1"[^>]*>[\s\S]*?<\/button>/g, '');
const toLines = (h) => decode(
  stripReview(h).replace(/<svg[\s\S]*?<\/svg>/g, '')
    .replace(/<(br|\/p|\/h\d|\/button|\/li|\/div|\/small|\/label|\/span|\/summary)\b[^>]*>/g, '\n')
    .replace(/<[^>]+>/g, ' '),
).split('\n').map((l) => l.replace(/\s+/g, ' ').trim()).filter(Boolean)
  .filter((l) => !/^(9:41|LTE 100%)$/.test(l));
const names = (h) => [...stripReview(h).matchAll(/(?:aria-label|placeholder)="([^"]+)"/g)].map((m) => decode(m[1]));

const out = [];
let count = 0;
const emit = (sc, label, h) => {
  const lines = toLines(h);
  const n = [...new Set(names(h))];
  out.push(`### ${sc.title}${label ? ` [${label}]` : ''}\n`);
  out.push(`Requirements: ${sc.reqs}. Source: ${sc.src}.\n`);
  out.push(lines.map((l) => `- ${l}`).join('\n') + '\n');
  if (n.length) out.push(`Screen reader names (not visible text): ${n.join('; ')}\n`);
  count += 1;
};

out.push('# EQO UX copy deck (generated)\n');
out.push('Generated from `design/prototype.html` by `design/tools/build-copy-deck.mjs`. Do not edit by hand: change the prototype and run the script.\n');
out.push('Conventions: US English (Android\'s default); neutral example names; Android and Chrome setting names verbatim; every readiness claim is per check; no blame. These follow the binding microcopy principles in `docs/USER-FLOWS.md` section 18. Review-only controls (variant tabs, "Demo" and "Simulate" buttons) are not included.\n');
out.push('Placeholders are marked PENDING in the screens (exact Chrome flag, privacy "what is sent" list, legal wording, OEM menu paths) and are not final copy.\n');

for (const [flow, ids] of EQO.NAV) {
  out.push(`\n## ${flow}\n`);
  for (const id of ids) {
    const sc = EQO.SC[id];
    EQO.reset();
    if (VARIANTS[id]) {
      for (const v of VARIANTS[id]) {
        EQO.reset();
        EQO.S.v[id] = v;
        emit(sc, `variant: ${v}`, sc.render());
      }
    } else {
      emit(sc, '', sc.render());
    }
    for (const [sid, label, mutate] of STATES) {
      if (sid !== id) continue;
      EQO.reset();
      mutate(EQO.S);
      emit(sc, `state: ${label}`, sc.render());
    }
  }
}
EQO.reset();
out.push(`\n---\n${count} screen states.\n`);
fs.writeFileSync(path.join(designDir, 'ux-copy.md'), out.join('\n'), 'utf8');
console.log(`wrote ux-copy.md with ${count} screen states`);
