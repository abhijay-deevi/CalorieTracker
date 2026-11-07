# Calorie Tracker (Android)

Snap a photo or type what you ate → an AI estimates calories, protein, carbs and fat → progress bars show what's left for the day and the week.

## Features
- **Paste from ChatGPT** – Today → Add food: tap **Copy ChatGPT instructions** once and give them to ChatGPT;
  it then ends each food reply with `LOG: name | 1660 cal | 200 carbs | 54 protein | 75 fat`. Copy that line,
  tap **Paste**, and the review opens with the numbers filled in. Labelled numbers in other formats work too.
  Several LOG lines (one per food) become separate foods, reviewed one after another ("1 of 2", **Skip** to drop
  one) and logged as separate entries; a repeated food name counts as a correction (the later line wins) and a
  "Total" line is ignored.
- **Camera button** (middle of the bottom bar) – a live camera block with a note box underneath and a send button.
  Tap the shutter (or the gallery icon), add details, send. The X (top left) goes back.
  Take up to 3 photos of the same meal from different angles — they're sent together in one AI request
  (one request against the free-tier limit), and the AI is told they show a single meal so nothing is counted twice.
  Photos taken in the app are kept only in its private cache (never your gallery) and deleted once the meal is logged.
- **Plan tab** – calorie calculator (Mifflin-St Jeor, US or metric units, activity guide), pick a goal
  (maintain / **body recomposition** / mild / 1 lb / 2 lb per week), pick a macro split (Balanced 25% fat,
  Lower carb 35%, Higher carb 20%, each shown in grams and %), then **Standard** or **Zigzag 1 / Zigzag 2**.
  - Protein comes from body weight and goal: 1.6 g/kg maintain, 1.8–2.2 g/kg for bigger deficits, 2.2 g/kg for
    recomp (Morton 2018 meta-analysis: benefit plateaus ~1.6 g/kg, upper range 2.2). Above a BMI of 27 the weight
    for BMI 27 is used. Recomp eats ~5% under maintenance.
  - Protein and fat are fixed grams every day; carbs fill the rest, so zigzag high days add carbs, not protein.
  - **Custom plan** – type your own calories and protein (and carbs or fat; leave one blank to fill the rest),
    see the % of each, then pick a schedule. Custom zigzag high days are ~20% above your average with the
    same weekly total.
- **Automatic backup** – once a plan exists, the app saves everything (foods, check-ins, plan) to
  `Documents/CalorieTracker/calorie-tracker-backup.json` (no setup), which survives deleting the app.
  Restoring adds the backup to what's in the app; nothing already there is deleted.
- **Today tab** – calorie arc (eaten / remaining / target) with carbs, protein and fat bars; add food by
  photo, gallery, text, or manual entry; review and edit every estimate before it's logged.
- **Backup** – Check-in → gear → Backup → **Choose backup file** (Documents, Downloads or Google Drive). The app rewrites
  that file a couple of seconds after every change: plan, every logged food, check-ins, saved foods, week swaps and settings
  (API keys are left out). Updating the app keeps your data; deleting it erases the app's own storage, but the
  backup file stays — after reinstalling, tap **Reinstalled? Restore from a backup** on the first screen.
  Updates must be signed with the same key: keep `%USERPROFILE%\.android\debug.keystore` from the PC that built it.
- **Look** – Check-in → gear → **Appearance**: System / Light / Dark, and a color (red, orange, yellow, green,
  teal, blue, indigo, purple, pink) for buttons, tabs and bars. Over-target red and high-day amber stay the same.
  Chakra Petch font (SIL Open Font License, bundled in `res/font`).
- **Barcode scanning** – the barcode icon (camera screen, bottom right; Today → Add food) opens Google's
  scanner and looks the product up in [Open Food Facts](https://world.openfoodfacts.org) (free, no key, no AI),
  so packaged foods get their exact label values; type how many servings you ate. Scanned products are
  remembered, and it works even while the AI is rate-limited.
- **Brand lookups (Gemini)** – on by default: when you mention a branded packaged food (Barebells, Fairlife,
  Orgain…), Gemini calls a lookup tool that searches Open Food Facts and uses the label values (free — Google's
  own Search tool isn't offered on the free tier for current models). Generic food is estimated from the photo.
- **Overload fallback** – if Gemini Flash answers "high demand" (503), the request is retried on an older Flash
  and then Flash-Lite, and the review notes which model was used.
- **Chat-style corrections** – the review shows every item like a food-log table; type a fix ("I meant 2
  sandwiches", "the slices were thick") and the AI redoes the estimate with that in mind.
- **Food memory** – everything you log is remembered. Typing "i ate peanut butter jelly" again logs it
  instantly with no AI call, and saved foods are suggested while you type.
- **Zigzag high-day shifting** – if you go over a regular day's target and a high day is still coming this
  week, today becomes the high day automatically (weekly total stays the same). You can also do it by hand
  ("Use Saturday's higher calories today") or swap any two days on the Week tab.
- **Check-in tab** (bottom bar; Settings is the gear in its top right) – per day: morning weight, gym session
  on/off, cardio on/off, and waist once a week. Below that, this week's numbers (weight, waist, average calories
  and protein, gym and cardio counts).
- **Copy for ChatGPT** – Check-in → **Copy this week for ChatGPT** / **Copy the last 4 weeks** (also on the
  Progress tab for a week or a month). Plain text, one block per week: daily morning weights, waist, average
  calories and protein per day, gym sessions and cardio sessions.
- **Progress tab** – **Week**: weekly total bar, per-day bars with weight, gym and cardio, pace indicator.
  **Month**: calendar with calories, morning weight and gym/cardio icons per day, a month summary and a weight
  chart. Tap a day to open it on Today.
- **AI rate limits** – when the provider says "too many requests" (e.g. Gemini free daily quota), photo and AI
  buttons are paused and the app shows how long until they work again. Saved foods and manual entry keep working.

## AI provider & cost
A ChatGPT Plus subscription **cannot** be used by other apps — OpenAI bills API use separately.

| Provider | Cost | Default model |
|---|---|---|
| Google Gemini | Free tier (rate-limited) — get a key at aistudio.google.com | `gemini-flash-latest` |
| Anthropic Claude | Pay per use (platform.claude.com) | `claude-opus-5-5` (set `claude-haiku-5-5` for much cheaper) |
| OpenAI | Pay per use (platform.openai.com) | `gpt-5-mini` |

Set the provider, key and (optionally) model in **Settings**. Keys are stored only on the phone.

## Build & install
Requires Android Studio's SDK and JDK 17.

```
gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`.

To install on a Samsung phone: Settings → About phone → Software information → tap **Build number** 7 times →
Developer options → **USB debugging** on, plug in, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
(or press Run in Android Studio). Alternatively copy the APK to the phone and open it (allow "Install unknown apps").

Unit tests (calculator matches the reference calculator's numbers, food memory, high-day shifting):
```
gradlew testDebugUnitTest
```
