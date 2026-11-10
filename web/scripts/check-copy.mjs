// Copy guard for the web client (mirrors app's ForbiddenCopyTest and scripts/check-copy.sh):
// calm tone, no "!", no guilt words, and never a medical label in anything the user reads.
//
//   node scripts/check-copy.mjs src    every string in src/copy.ts, text inside html`` templates in src/**/*.ts,
//                                      index.html and public/manifest.webmanifest
//   node scripts/check-copy.mjs dist   the built HTML and manifest, plus medical labels anywhere in the bundle
import { readdirSync, readFileSync, statSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

// "ADD" only in capitals, so the everyday verb "Add" stays allowed (see medical()).
export const GUILT = /\b(missed|streaks?|behind|forgot\w*|overdue|failed to log|late again|lazy)\b/i;

/** Problems in one piece of user-facing text, or an empty list. */
export function problems(text) {
  const out = [];
  if (text.includes("!")) out.push("exclamation mark");
  if (GUILT.test(text)) out.push("guilt word: " + text.match(GUILT)[0]);
  if (medical(text)) out.push("medical label: " + medical(text));
  return out;
}

/** JS has no inline (?-i:) groups, so the capital-only ADD is checked separately. */
export function medical(text, { capitalAdd = true } = {}) {
  const words = /\b(adhd|autis\w*|audhd|neurodiver\w*|neurotypical|executive dysfunction|asperger\w*|spectrum disorder)/i;
  const m = text.match(words);
  if (m) return m[0];
  const add = capitalAdd ? text.match(/\bADD\b/) : null;
  return add ? add[0] : null;
}

/** String literals in a TS source ("…", '…', `…` without the ${} parts). */
export function stringLiterals(src) {
  const out = [];
  for (const m of src.matchAll(/"((?:[^"\\\n]|\\.)*)"|'((?:[^'\\\n]|\\.)*)'|`((?:[^`\\]|\\.)*)`/g)) {
    const s = m[1] ?? m[2] ?? m[3];
    out.push(...s.split(/\$\{[^}]*\}/));
  }
  return out;
}

/** Text nodes inside lit html`` templates: what sits between a `>` and the next `<`, minus ${} expressions. */
export function templateText(src) {
  const out = [];
  for (const m of src.matchAll(/html`((?:[^`\\]|\\.)*)`/g)) {
    // Drop attribute-heavy tags first, then expressions.
    const body = m[1].replace(/\$\{[^}]*\}/g, " ");
    for (const t of body.matchAll(/>([^<>]*)</g)) {
      const text = t[1].trim();
      if (text) out.push(text);
    }
  }
  return out;
}

function walk(dir, filter, acc = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) {
      if (name !== "generated" && name !== "node_modules") walk(p, filter, acc);
    } else if (filter(p)) acc.push(p);
  }
  return acc;
}

function htmlText(html) {
  return [...html.replace(/<script[\s\S]*?<\/script>/g, "").matchAll(/>([^<>]+)</g)].map((m) => m[1].trim()).filter(Boolean);
}

export function checkSrc(web) {
  const found = [];
  const report = (file, text) => problems(text).forEach((p) => found.push(`${relative(web, file)}: ${p}: "${text}"`));
  const copy = join(web, "src/copy.ts");
  for (const s of stringLiterals(readFileSync(copy, "utf8"))) report(copy, s);
  for (const f of walk(join(web, "src"), (p) => p.endsWith(".ts") && !p.endsWith(".test.ts"))) {
    for (const t of templateText(readFileSync(f, "utf8"))) report(f, t);
  }
  const index = join(web, "index.html");
  for (const t of htmlText(readFileSync(index, "utf8"))) report(index, t);
  const manifest = join(web, "public/manifest.webmanifest");
  const m = JSON.parse(readFileSync(manifest, "utf8"));
  for (const k of ["name", "short_name", "description"]) if (m[k]) report(manifest, m[k]);
  return found;
}

export function checkDist(dist) {
  const found = [];
  for (const f of walk(dist, (p) => /\.(html|webmanifest|js)$/.test(p))) {
    const text = readFileSync(f, "utf8");
    if (f.endsWith(".js")) {
      // Library code may use ADD as an identifier, so the bundle is checked for the spelled-out labels only;
      // our own strings were checked with the capital ADD rule in `src` mode.
      const m = medical(text, { capitalAdd: false });
      if (m) found.push(`${relative(dist, f)}: medical label: ${m}`);
    } else if (f.endsWith(".html")) {
      for (const t of htmlText(text)) problems(t).forEach((p) => found.push(`${relative(dist, f)}: ${p}: "${t}"`));
    } else {
      const j = JSON.parse(text);
      for (const k of ["name", "short_name", "description"]) if (j[k]) problems(j[k]).forEach((p) => found.push(`${relative(dist, f)}: ${p}`));
    }
  }
  return found;
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  const web = resolve(dirname(fileURLToPath(import.meta.url)), "..");
  const mode = process.argv[2] ?? "src";
  const found = mode === "dist" ? checkDist(join(web, "dist")) : checkSrc(web);
  if (found.length) {
    console.error("Copy check failed:\n" + found.join("\n"));
    process.exit(1);
  }
  console.log(`copy check (${mode}) done`);
}
