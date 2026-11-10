// Every text/background token pair the web UI uses. tokens.test.ts checks each against WCAG AA (4.5:1) in both
// themes, so a component may only put text in colour X on background Y if [X, Y] is listed here.
// (outline / outline-variant are for borders and dividers only: they are not text colours.)
export const TEXT_PAIRS: [text: string, background: string][] = [
  ["onBackground", "background"],
  ["onSurface", "surface"],
  ["onSurfaceVariant", "surface"],
  ["primary", "surface"],
  ["onPrimary", "primary"],
  ["onPrimaryContainer", "primaryContainer"],
  ["onSecondary", "secondary"],
  ["onSecondaryContainer", "secondaryContainer"],
  ["onTertiary", "tertiary"],
  ["onTertiaryContainer", "tertiaryContainer"],
  ["onError", "error"],
  ["onErrorContainer", "errorContainer"],
  ["error", "surface"],
  ["inverseOnSurface", "inverseSurface"],
  ["onSurfaceVariant", "surfaceVariant"],
  ...(["surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest"] as const)
    .flatMap((bg): [string, string][] => [["onSurface", bg], ["onSurfaceVariant", bg]]),
  // primary text (links, text buttons) only up to surfaceContainerHigh: on Highest it is 4.4:1 in light.
  ...(["surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh"] as const)
    .map((bg): [string, string] => ["primary", bg]),
];

export function luminance(hex: string): number {
  const n = parseInt(hex.replace("#", ""), 16);
  const ch = [(n >> 16) & 255, (n >> 8) & 255, n & 255].map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * ch[0] + 0.7152 * ch[1] + 0.0722 * ch[2];
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}
