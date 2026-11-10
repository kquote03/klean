import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { contrast, TEXT_PAIRS } from "./contrast";
// @ts-expect-error plain ESM build script without types
import { cssFor, kebab } from "../../scripts/tokens.mjs";

const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");
const tokens: Record<"light" | "dark", Record<string, string>> = JSON.parse(read("../../../core/design/tokens/klean-color-tokens.json"));

describe("design tokens", () => {
  it("the generated CSS is current and covers every token in both themes", () => {
    const css = read("../generated/tokens.css");
    expect(css).toBe(cssFor(tokens));
    for (const scheme of ["light", "dark"] as const) {
      for (const [k, v] of Object.entries(tokens[scheme])) expect(css).toContain(`--md-sys-color-${kebab(k)}: ${v.toLowerCase()};`);
    }
    expect(css).toMatch(/@media \(prefers-color-scheme: dark\)/);
  });

  for (const scheme of ["light", "dark"] as const) {
    describe(scheme, () => {
      for (const [fg, bg] of TEXT_PAIRS) {
        it(`${fg} on ${bg} meets WCAG AA 4.5:1`, () => {
          expect(tokens[scheme][fg], fg).toBeDefined();
          expect(tokens[scheme][bg], bg).toBeDefined();
          expect(contrast(tokens[scheme][fg], tokens[scheme][bg])).toBeGreaterThanOrEqual(4.5);
        });
      }
    });
  }

  it("dark mode has no pure black or pure white blocks (only the scrim is black)", () => {
    for (const [k, v] of Object.entries(tokens.dark)) {
      if (k === "scrim") continue;
      expect(v.toLowerCase(), k).not.toBe("#000000");
      expect(v.toLowerCase(), k).not.toBe("#ffffff");
    }
  });

  it("the contrast maths matches known WCAG values", () => {
    expect(contrast("#000000", "#ffffff")).toBeCloseTo(21, 5);
    expect(contrast("#777777", "#ffffff")).toBeCloseTo(4.48, 2);
  });

  it("no component stylesheet or template uses a raw hex colour (tokens only)", () => {
    const files = import.meta.glob("../**/*.{ts,css}", { query: "?raw", import: "default", eager: true }) as Record<string, string>;
    const offenders = Object.entries(files)
      .filter(([p]) => !p.includes("/generated/") && !p.endsWith(".test.ts") && !p.includes("/theme/contrast.ts"))
      .filter(([, src]) => /#[0-9a-fA-F]{6}\b|#[0-9a-fA-F]{3}\b(?![0-9a-fA-F])/.test(src.replace(/&#\d+;/g, "")))
      .map(([p]) => p);
    expect(offenders).toEqual([]);
  });
});
