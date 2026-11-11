# Calories without the counting

Plan: `.hermes/plans/2026-11-11_171941-diet-calories.md`. Calories are opt-in: they appear only when
**Settings › Diet › Show calories** is on (off by default).

## Writing calories

One syntax, accepted at the end of a food text (typed, spoken, notification reply, widget, watch) and in
the capture sheet's `kcal` slot (shown for Food when Show calories is on) and on the entry screen.

| You write | Saved as |
|---|---|
| `banana` | "banana"; calories filled in for you (below) |
| `oatmeal 150 kcal`, `oatmeal 150kcal`, `oatmeal 150 cal` | "oatmeal", **150 kcal** (yours) |
| `rice 200g`, `rice 200 grams`, `rice 0.2 kg` | "rice", 200 g; calories filled in and scaled to 200 g |
| `chips 150x100x200` (also `×`, `*`, `150kcal x 100g x 200g`) | "chips", **300 kcal** (yours), 200 g |

`KCAL x PER x ATE`: 150 kcal per 100 g, ate 200 g → 150 × 200 / 100 = 300 kcal.

- A bare number is never calories: "2 eggs" and "coffee 2" stay as written.
- The slot wins over a token in the text; the token is still stripped from the text.
- Anything the slot can't read ("Not sure what that means" in the preview) is ignored; logging never fails
  over calories. Sanity caps: 1–20,000 kcal, 1–10,000 g.
- Code: `shared/.../diet/CalorieInput.kt` (`parse`, `splitTrailing`, `EXTRA_KEY = "diet.kcal"`).

## Filling in (when you didn't give calories)

Order: **yours → earlier entries → AI → none.**

1. **The entry's own AI items**, if enrichment already produced them (their sum, marked AI).
2. **Earlier entries** of the same food (exact match after lowercasing, dropping apostrophes and
   punctuation, collapsing spaces), newest first, from the last 365 days. Only entries whose calories
   were yours or AI's count, never ones filled from history, so estimates don't compound. With grams on
   both sides it scales per gram; grams now but none before goes on to the AI. Runs on the phone, no AI.
3. **AI**, only if a provider is assigned to *Calorie estimates* in Settings › AI (Gemini, any
   OpenAI-compatible server, or on-device). A `CalorieFillWorker` runs right after saving (network
   constraint only for network providers; up to 5 attempts when the provider is unreachable). Skipped when
   the same provider is already parsing the entry, because the parse fills it.
4. Otherwise the entry has no calories. That's fine, and nothing nags about it.

A fill writes only while the entry still has no calories and still says what was asked (text and grams),
so a user edit in between always wins. Code: `feature/diet/.../calories/`.

## Privacy

- History matching never leaves the phone.
- The AI is used only if you assigned one to Calorie estimates. For a calorie fill it receives the food
  text and the grams (if given), never a photo.
- `kcal`, `kcal_source` and `grams` sync with the entry like every other column (schema v4).

## How it shows

- Your own number shows plainly: `300 kcal`. Filled-in numbers show with `≈`: `≈ 105 kcal`.
- The entry screen names the source: `≈ 105 kcal · from earlier`, `≈ 260 kcal · estimate`.
- Totals show `≈` when any part is filled in. Entries without calories count as zero, and the Diet hero
  says so quietly (`1 food without calories`), with ⋮ › **Fill in calories** to try again.
- **Daily calorie goal** (Settings › Diet, under Show calories; blank = none): the Diet hero shows
  `1,240 of 2,000 kcal` over a bar. Past the goal the bar is simply full, same colour. No red, no "over".

## Copy today

Diet ⋮ › **Copy today** (and ⋮ › **Copy this day** on a past day) puts this on the clipboard, for pasting
into any assistant:

```
What I ate on Wednesday, 11 November 2026
09:00  oatmeal: 150 kcal
11:27  banana: ≈ 105 kcal
11:45  McDonald's cheeseburger: 300 kcal
13:10  salad
Total: ≈ 555 kcal (goal 2,000 kcal) · 1 food without calories
```

Times follow the phone's 12/24-hour setting. With Show calories off, the text has no calories. Below
Android 13 a "Copied" snackbar confirms; from 13 on the system shows its own confirmation.
