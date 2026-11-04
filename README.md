# Calorie Tracker (Android)

Snap a photo or type what you ate → an AI estimates calories, protein, carbs and fat → progress bars show what's left for the day and the week.

## Features
- **Camera-first** – the app opens on a live camera block with a note box underneath and a send button.
  Tap the shutter (or the gallery icon), add details, send. The X (top left) goes to today's totals.
  Take up to 3 photos of the same meal from different angles — they're sent together in one AI request
  (one request against the free-tier limit), and the AI is told they show a single meal so nothing is counted twice.
  Photos taken in the app are kept only in its private cache (never your gallery) and deleted once the meal is logged.
- **Plan tab** – calorie calculator (Mifflin-St Jeor, US or metric units, activity guide), pick a goal
  (maintain / mild / 1 lb / 2 lb per week), then pick **Standard** or **Zigzag 1 / Zigzag 2** schedules.
- **Today tab** – calorie arc (eaten / remaining / target) with carbs, protein and fat bars; add food by
  photo, gallery, text, or manual entry; review and edit every estimate before it's logged.
- **Look** – clean light/dark theme with the Chakra Petch font (SIL Open Font License, bundled in `res/font`).
- **Food memory** – everything you log is remembered. Typing "i ate peanut butter jelly" again logs it
  instantly with no AI call, and saved foods are suggested while you type.
- **Zigzag high-day shifting** – if you go over a regular day's target and a high day is still coming this
  week, today becomes the high day automatically (weekly total stays the same). You can also do it by hand
  ("Use Saturday's higher calories today") or swap any two days on the Week tab.
- **Week tab** – weekly total bar (daily target × 7), per-day bars, pace indicator.
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
