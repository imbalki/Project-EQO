// Generates design/ux-copy.md from design/prototype.html so the copy deck can never drift from the screens.
// Run: node android/Phase-One/design/tools/build-copy-deck.mjs
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

const VARIANTS = {
  s05: ['401', '429', 'credit', 'model', 'net'],
  s09: ['port', 'conn', 'reboot', 'revoked'],
  s25: ['rate', 'adb', 'a11y', 'binder', 'unknown'],
};
const decode = (s) => s.replace(/&nbsp;/g, ' ').replace(/&rsaquo;/g, '›').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
const toLines = (h) => decode(
  h.replace(/<svg[\s\S]*?<\/svg>/g, '')
    .replace(/<(br|\/p|\/h\d|\/button|\/li|\/div|\/small|\/label|\/span)\b[^>]*>/g, '\n')
    .replace(/<[^>]+>/g, ' '),
).split('\n').map((l) => l.replace(/\s+/g, ' ').trim()).filter(Boolean)
  .filter((l) => !/^(9:41|LTE 100%)$/.test(l));
const names = (h) => [...h.matchAll(/(?:aria-label|placeholder)="([^"]+)"/g)].map((m) => decode(m[1]));

const out = [];
out.push('# EQO UX copy deck (generated)\n');
out.push('Generated from `design/prototype.html` by `design/tools/build-copy-deck.mjs`. Do not edit by hand: change the prototype and run the script.\n');
out.push('Conventions: US English (Android\'s default); neutral example names; Android and Chrome setting names verbatim; every readiness claim is per check; no blame. These follow the binding microcopy principles in `docs/USER-FLOWS.md` section 18.\n');
out.push('Placeholders are marked PENDING in the screens (exact Chrome flag, privacy "what is sent" list, legal wording, OEM menu paths) and are not final copy.\n');

let count = 0;
for (const [flow, ids] of EQO.NAV) {
  out.push(`\n## ${flow}\n`);
  for (const id of ids) {
    const sc = EQO.SC[id];
    const variants = VARIANTS[id] || [null];
    for (const v of variants) {
      if (v) EQO.S.v[id] = v;
      const h = sc.render();
      if (v) delete EQO.S.v[id];
      const lines = toLines(h);
      const n = [...new Set(names(h))];
      out.push(`### ${sc.title}${v ? ` [variant: ${v}]` : ''}\n`);
      out.push(`Requirements: ${sc.reqs}. Source: ${sc.src}.\n`);
      out.push(lines.map((l) => `- ${l}`).join('\n') + '\n');
      if (n.length) out.push(`Screen reader names (not visible text): ${n.join('; ')}\n`);
      count += 1;
    }
  }
}
out.push(`\n---\n${count} screen states.\n`);
fs.writeFileSync(path.join(designDir, 'ux-copy.md'), out.join('\n'), 'utf8');
console.log(`wrote ux-copy.md with ${count} screen states`);
