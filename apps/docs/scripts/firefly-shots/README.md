# Firefly docs screenshots

Capture light/dark PNG pairs for `FireflyShot` from a running Playground sample.

```bash
# terminal 1 — sample app with KONA_HOME
cd /path/to/firefly-app
export KONA_HOME=/path/to/konaframework
./grind serve --port=4002

# terminal 2
cd apps/docs/scripts/firefly-shots
npm install
npx playwright install chromium
PLAYGROUND_URL=http://127.0.0.1:4002 npm run capture
```

Scenes live in `scenes.json`. Output: `apps/docs/public/images/firefly/<scene>-{light,dark}.png` (trimmed via sharp).
