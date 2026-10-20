// Renders the Play Store phone screenshots (1080x1920) with Roo.
// Run: npm i playwright-core && node build.js <outDir>  (needs Chromium; path set in launch() below)
const { chromium } = require('playwright-core');
const fs = require('fs');
const path = require('path');

const OUT = path.resolve(process.argv[2] || path.join(__dirname, 'out'));
const ROO = '/home/user/Clearoo/docs/roo';
const FONT = '/home/user/Clearoo/app/src/main/res/font';
const roo = (name) => `file://${ROO}/${name}.png`;

const css = `
@font-face { font-family: N; src: url(file://${FONT}/nunito_regular.ttf); font-weight: 400; }
@font-face { font-family: N; src: url(file://${FONT}/nunito_semibold.ttf); font-weight: 600; }
@font-face { font-family: N; src: url(file://${FONT}/nunito_bold.ttf); font-weight: 700; }
@font-face { font-family: N; src: url(file://${FONT}/nunito_extrabold.ttf); font-weight: 800; }
@font-face { font-family: N; src: url(file://${FONT}/nunito_black.ttf); font-weight: 900; }
* { box-sizing: border-box; margin: 0; padding: 0; }
body { width: 1080px; height: 1920px; overflow: hidden; font-family: N, sans-serif, 'Noto Color Emoji'; color: #F7F3FF;
  background: radial-gradient(900px 700px at 85% 8%, rgba(255,95,158,.35), transparent 70%),
              radial-gradient(900px 900px at 0% 100%, rgba(142,124,255,.35), transparent 70%), #130F1E; position: relative; }
.head { position: absolute; top: 96px; left: 72px; right: 72px; text-align: center; }
.head h1 { font-weight: 900; font-size: 92px; line-height: 1.04; letter-spacing: -1px; }
.head p { margin-top: 22px; font-size: 38px; font-weight: 600; color: #B4A9CC; }
.g { background: linear-gradient(90deg, #FF5F6D, #FF5F9E 50%, #FFB36B); -webkit-background-clip: text; color: transparent; }
.phone { position: absolute; top: 470px; left: 150px; width: 780px; height: 1600px; border-radius: 84px; background: #07050c;
  padding: 18px; box-shadow: 0 40px 120px rgba(0,0,0,.6), 0 0 0 3px #2b2340; }
.screen { width: 100%; height: 100%; border-radius: 68px; overflow: hidden; background: #130F1E; position: relative; font-size: 28px; }
.status { height: 70px; display: flex; justify-content: space-between; align-items: center; padding: 0 48px; font-weight: 700; font-size: 26px; }
.roo { position: absolute; width: 360px; filter: drop-shadow(0 20px 30px rgba(0,0,0,.45)); }
.bubble { position: absolute; background: #fff; color: #2A1B3D; font-weight: 800; font-size: 40px; padding: 26px 34px; border-radius: 40px; box-shadow: 0 16px 40px rgba(0,0,0,.35); line-height: 1.15; }
.row { display: flex; align-items: center; }
.pill { display: inline-flex; align-items: center; padding: 10px 22px; border-radius: 999px; font-weight: 700; font-size: 24px; color: #fff; }
.card { background: #1E182D; border-radius: 44px; }
.lo { color: #B4A9CC; }
.hi { color: #F7F3FF; }
`;

/** A pleasant made-up landscape for photo cards (no real people or photos). */
const photo = (hue = 0, sun = true) => `<svg viewBox="0 0 400 600" preserveAspectRatio="xMidYMid slice" width="100%" height="100%" style="display:block;filter:hue-rotate(${hue}deg)">
<defs><linearGradient id="sky${hue}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2b3a8f"/><stop offset=".55" stop-color="#ff7a7a"/><stop offset="1" stop-color="#ffc58a"/></linearGradient></defs>
<rect width="400" height="600" fill="url(#sky${hue})"/>${sun ? '<circle cx="270" cy="300" r="70" fill="#ffe3a3" opacity=".95"/>' : ''}
<path d="M0 380 L90 290 L160 350 L250 250 L400 370 L400 600 L0 600Z" fill="#5b3a7a"/>
<path d="M0 440 L120 360 L220 420 L320 340 L400 400 L400 600 L0 600Z" fill="#3c2758"/>
<path d="M0 500 Q200 450 400 510 L400 600 L0 600Z" fill="#241838"/></svg>`;

const status = `<div class="status"><span>9:41</span><span style="font-size:22px">▂▄▆ 5G &nbsp;🔋</span></div>`;

const slides = [];

// 1. Swipe
slides.push({ name: '01-swipe', html: `
<div class="head"><h1>Swipe left to <span class="g">delete.</span><br>Right to keep.</h1><p>Clean your gallery in 2 minutes a day</p></div>
<div class="phone"><div class="screen">${status}
  <div class="row" style="padding:10px 32px;gap:22px">
    <span style="font-size:40px">←</span>
    <div style="flex:1"><div class="lo" style="text-align:center;font-size:24px;font-weight:700">🎲 Quick mix · 6/10</div>
      <div style="height:18px;border-radius:9px;background:#2A2140;margin-top:10px"><div style="width:60%;height:100%;border-radius:9px;background:linear-gradient(90deg,#FF5F6D,#FF5F9E,#FFB36B)"></div></div></div>
    <span class="pill" style="background:#FF4D6D;font-size:28px">🗑️ 4</span>
  </div>
  <div class="row" style="padding:10px 40px;gap:14px"><img src="${roo('moods/roo-shocked')}" style="width:130px"><div style="background:#fff;color:#2A1B3D;border-radius:8px 36px 36px 36px;padding:20px 28px;font-weight:700;font-size:30px">Delete it? 😱</div></div>
  <div style="position:relative;height:860px;margin:10px 34px">
    <div style="position:absolute;inset:40px 30px 0;border-radius:56px;overflow:hidden;transform:scale(.94) translateY(30px);opacity:.7">${photo(140, false)}</div>
    <div style="position:absolute;inset:0;border-radius:56px;overflow:hidden;transform:translateX(-40px) rotate(-7deg);transform-origin:50% 110%;box-shadow:0 30px 60px rgba(0,0,0,.5)">
      ${photo(0)}
      <div style="position:absolute;inset:0;background:rgba(255,77,109,.28)"></div>
      <div style="position:absolute;top:110px;right:60px;border:8px solid #FF4D6D;color:#FF4D6D;font-weight:900;font-size:76px;padding:6px 28px;border-radius:24px;transform:rotate(14deg)">DELETE</div>
      <div style="position:absolute;left:0;right:0;bottom:0;padding:120px 40px 40px;background:linear-gradient(transparent,rgba(0,0,0,.85))">
        <div style="font-weight:800;font-size:54px">2 years ago</div>
        <div class="row" style="gap:12px;margin-top:16px"><span class="pill" style="background:rgba(255,255,255,.2)">12 Mar 2024</span><span class="pill" style="background:rgba(255,255,255,.2)">4.2 MB</span><span class="pill" style="background:rgba(255,255,255,.2)">Camera</span></div>
      </div>
    </div>
  </div>
  <div class="row" style="justify-content:space-evenly;margin-top:40px">
    <div style="width:150px;height:150px;border-radius:50%;background:#1E182D;border:6px solid #FF4D6D;display:flex;align-items:center;justify-content:center;font-size:64px;color:#FF4D6D;font-weight:900">✕</div>
    <div style="width:110px;height:110px;border-radius:50%;background:#1E182D;border:6px solid #FF5F9E;display:flex;align-items:center;justify-content:center;font-size:48px">↺</div>
    <div style="width:150px;height:150px;border-radius:50%;background:#1E182D;border:6px solid #2EE59D;display:flex;align-items:center;justify-content:center;font-size:64px;color:#2EE59D">♥</div>
  </div>
</div></div>
<img class="roo" src="${roo('moods/roo-excited')}" style="right:-30px;bottom:250px;width:330px">
<div class="bubble" style="right:150px;bottom:560px">Not this one.<br>Bye bye! 👋</div>
`});

// 2. Smart decks
const tile = (emoji, title, sub, pick = false) => `<div class="card" style="flex:1;padding:28px 26px;border-radius:40px;${pick ? 'border:4px solid #8E7CFF;' : ''}">
  <div class="row" style="justify-content:space-between"><span style="font-size:52px">${emoji}</span>${pick ? '<span class="pill" style="background:#8E7CFF;font-size:22px">Roo\'s pick</span>' : ''}</div>
  <div style="font-weight:800;font-size:34px;margin-top:10px">${title}</div><div class="lo" style="font-size:25px;margin-top:4px">${sub}</div></div>`;
slides.push({ name: '02-smart-decks', html: `
<div class="head"><h1>Roo finds the<br><span class="g">space hogs</span></h1><p>Smart decks sort your mess for you</p></div>
<div class="phone"><div class="screen">${status}
  <div style="padding:20px 36px">
    <div style="font-weight:800;font-size:44px;margin:10px 0 24px">Smart decks</div>
    <div class="row" style="gap:22px">${tile('🐘', 'Biggest', '4,157 · 69.2 GB')}${tile('📸', 'Screenshots', '127 · 279 MB')}</div>
    <div class="row" style="gap:22px;margin-top:22px">${tile('💬', 'Chat media', '1,555 · 3.8 GB')}${tile('👯', 'Look-alikes', '1,834 · 9.6 GB')}</div>
    <div class="row" style="gap:22px;margin-top:22px">${tile('🎬', 'Videos', '989 · 51.3 GB', true)}${tile('😵', 'Blurry', 'Roo scans for you')}</div>
    <div class="row" style="gap:22px;margin-top:22px">${tile('🕰️', 'Old memories', '42 · 337 MB')}${tile('🩹', 'Broken files', '36 found')}</div>
  </div>
</div></div>
<img class="roo" src="${roo('moods/roo-proud')}" style="left:30px;bottom:30px;width:280px">
<div class="bubble" style="left:320px;bottom:150px">51 GB of videos?!<br>Start here 🎬</div>
`});

// 3. Albums
const deckRow = (emoji, title, sub) => `<div class="card row" style="padding:30px;gap:26px;border-radius:40px;margin-top:20px"><span style="font-size:56px">${emoji}</span>
  <div style="flex:1"><div style="font-weight:800;font-size:34px">${title}</div><div class="lo" style="font-size:25px">${sub}</div></div><span style="color:#8E7CFF;font-weight:800;font-size:28px">Swipe ›</span></div>`;
slides.push({ name: '03-albums', html: `
<div class="head"><h1>Back from a trip?<br><span class="g">Tidy it up</span></h1><p>Albums find the blurry shots and duplicates</p></div>
<div class="phone"><div class="screen">${status}
  <div class="row" style="padding:16px 36px;gap:22px"><span style="font-size:40px">←</span><span style="font-weight:800;font-size:44px">London trip</span></div>
  <div style="padding:0 36px">
    <div class="row" style="gap:20px"><img src="${roo('outfits/roo-sunglasses')}" style="width:170px"><div><div style="font-weight:800;font-size:38px">312 photos · 1.9 GB</div><div class="lo" style="font-size:27px;margin-top:6px">Found 23 blurry and 41 duplicates</div></div></div>
    ${deckRow('😵', 'Blurry', '23 photos')}
    ${deckRow('👯', 'Duplicates', '41 photos')}
    ${deckRow('🃏', 'Everything', '312 photos, in date order')}
    <div class="card" style="padding:32px;border-radius:40px;margin-top:20px">
      <div style="font-weight:800;font-size:34px">📁 Make it a gallery album</div>
      <div class="lo" style="font-size:25px;margin-top:8px">Optional. Moves these photos into Pictures/London trip, so it shows up in your gallery app.</div>
      <div style="margin-top:22px;border:4px solid #3A3054;border-radius:60px;text-align:center;padding:22px;font-weight:800;font-size:30px">Move 312 photos</div>
    </div>
  </div>
</div></div>
<img class="roo" src="${roo('outfits/roo-headphones')}" style="right:10px;bottom:30px;width:280px">
<div class="bubble" style="right:300px;bottom:150px">41 duplicates!<br>Let's swipe 👯</div>
`});

// 4. Broken files
slides.push({ name: '04-broken-files', html: `
<div class="head"><h1><span class="g">Broken files?</span><br>Gone in one tap</h1><p>Empty or damaged files that won't even open</p></div>
<div class="phone"><div class="screen">${status}
  <div class="row" style="padding:10px 32px;gap:22px">
    <span style="font-size:40px">←</span>
    <div style="flex:1"><div class="lo" style="text-align:center;font-size:24px;font-weight:700">🩹 Broken files · 4/10</div>
      <div style="height:18px;border-radius:9px;background:#2A2140;margin-top:10px"><div style="width:40%;height:100%;border-radius:9px;background:linear-gradient(90deg,#FF5F6D,#FF5F9E,#FFB36B)"></div></div></div>
    <span class="pill" style="background:#FF4D6D;font-size:28px">🗑️ 2</span>
  </div>
  <div style="padding:20px 40px"><span class="pill" style="background:#FF4D6D;font-size:28px;padding:16px 30px">🩹 Bin all 36 broken files</span></div>
  <div style="position:relative;height:820px;margin:10px 34px">
    <div style="position:absolute;inset:0;border-radius:56px;background:#1E182D;box-shadow:0 30px 60px rgba(0,0,0,.5);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;padding:60px">
      <div style="font-size:150px">🩹</div>
      <div style="font-weight:800;font-size:56px;margin-top:10px">Broken file</div>
      <div class="lo" style="font-size:32px;margin-top:16px;line-height:1.35">It's empty or damaged and won't open anywhere. Safe to delete.</div>
      <div style="position:absolute;left:0;right:0;bottom:0;padding:40px;text-align:left;background:linear-gradient(transparent,rgba(0,0,0,.6));border-radius:0 0 56px 56px">
        <div class="hi" style="font-size:28px;font-weight:700">IMG-20190312-WA0042.jpg</div>
        <div class="row" style="gap:12px;margin-top:14px"><span class="pill" style="background:rgba(255,255,255,.15)">12 Mar 2019</span><span class="pill" style="background:rgba(255,255,255,.15)">WhatsApp Images</span></div>
      </div>
      <div class="pill" style="position:absolute;top:36px;left:36px;background:rgba(0,0,0,.6);font-size:24px">🩹 Broken file</div>
    </div>
  </div>
</div></div>
<img class="roo" src="${roo('outfits/roo-wizard-hat')}" style="right:10px;bottom:30px;width:280px">
<div class="bubble" style="right:300px;bottom:150px">No more grey<br>mystery photos ✨</div>
`});

// 5. Phone full
slides.push({ name: '05-phone-full', html: `
<div class="head"><h1>Works even when<br>your phone is <span class="g">full</span></h1><p>Free space now, no trash to empty later</p></div>
<div class="phone"><div class="screen">${status}
  <div style="padding:20px 36px">
    <div style="border:4px solid #FF4D6D;border-radius:40px;background:#1E182D;padding:28px">
      <div class="row" style="gap:20px"><img src="${roo('moods/roo-worried')}" style="width:120px"><div><div style="font-weight:800;font-size:36px">Your phone is full</div><div class="lo" style="font-size:25px;margin-top:4px">Only 180 MB left. Big files first, that's the fastest win!</div></div></div>
      <div class="row" style="gap:14px;margin-top:20px"><span class="pill" style="background:#2A2140">🐘 Biggest</span><span class="pill" style="background:#2A2140">🎬 Videos</span><span class="pill" style="background:#2A2140">🩹 Broken files</span></div>
    </div>
    <div style="font-weight:800;font-size:44px;margin:40px 0 20px">The bin</div>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:16px">
      ${[0, 120, 200].map(h => `<div style="aspect-ratio:1;border-radius:28px;overflow:hidden">${photo(h, h % 80 === 0)}</div>`).join('')}
    </div>
    <div class="card row" style="padding:28px;border-radius:36px;margin-top:30px;gap:20px">
      <div style="flex:1"><div style="font-weight:800;font-size:32px">Free the space now</div><div class="lo" style="font-size:24px;margin-top:4px">Skip the trash so 2.4 GB comes back right away.</div></div>
      <div style="width:110px;height:64px;border-radius:32px;background:#FF5F6D;position:relative"><div style="position:absolute;right:8px;top:8px;width:48px;height:48px;border-radius:50%;background:#fff"></div></div>
    </div>
    <div style="margin-top:30px;height:120px;border-radius:60px;background:linear-gradient(90deg,#FF4D6D,#FF5F6D);display:flex;align-items:center;justify-content:center;font-weight:800;font-size:36px">Delete forever (48)</div>
  </div>
</div></div>
<img class="roo" src="${roo('outfits/roo-cape')}" style="left:30px;bottom:30px;width:280px">
<div class="bubble" style="left:320px;bottom:150px">2.4 GB back,<br>instantly 🚀</div>
`});

// 6. Streak
slides.push({ name: '06-streak', html: `
<div class="head"><h1>Make it a habit<br>with <span class="g">Roo</span></h1><p>Daily goal, streaks, a reminder and outfits to unlock</p></div>
<div class="phone"><div class="screen">${status}
  <div style="padding:10px 36px">
    <div class="row" style="justify-content:space-between"><span class="g" style="font-weight:900;font-size:50px">Clearoo</span><span class="pill" style="background:rgba(255,138,61,.2);color:#FF8A3D;font-size:30px">🔥 12</span></div>
    <div style="background:#fff;color:#2A1B3D;border-radius:36px;padding:22px 30px;font-weight:700;font-size:30px;margin:30px auto 0;width:fit-content">12 days in a row! You're on fire 🔥</div>
    <div style="text-align:center"><img src="${roo('outfits/roo-party-hat')}" style="width:380px"></div>
    <div class="row" style="justify-content:space-between;padding:0 10px">
      ${['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((d, i) => `<div style="text-align:center"><div class="lo" style="font-size:24px">${d}</div><div style="width:68px;height:68px;border-radius:50%;margin-top:8px;background:${i < 6 ? 'rgba(255,138,61,.22)' : '#1E182D'};${i === 6 ? 'border:4px solid #3A3054;' : ''}display:flex;align-items:center;justify-content:center;font-size:34px">${i < 6 ? '🔥' : ''}</div></div>`).join('')}
    </div>
    <div class="card row" style="padding:34px;margin-top:34px;gap:34px;border-radius:56px">
      <div style="width:200px;height:200px;border-radius:50%;background:conic-gradient(#FF5F6D, #FF5F9E, #FFB36B 80%, #2A2140 80%);display:flex;align-items:center;justify-content:center">
        <div style="width:160px;height:160px;border-radius:50%;background:#1E182D;display:flex;flex-direction:column;align-items:center;justify-content:center"><div style="font-weight:900;font-size:56px">8</div><div class="lo" style="font-size:22px">of 10</div></div></div>
      <div><div style="font-weight:800;font-size:38px">Today's clean-up</div><div class="lo" style="font-size:26px;margin-top:6px">Streak secured 🔥 2 to the goal</div><div style="color:#FF8A3D;font-size:24px;margin-top:6px;font-weight:700">640 MB freed today</div></div>
    </div>
  </div>
</div></div>
<img class="roo" src="${roo('outfits/roo-crown')}" style="left:-30px;bottom:80px;width:300px">
<img class="roo" src="${roo('outfits/roo-scarf')}" style="right:-30px;bottom:80px;width:300px">
`});

// 7. Private
slides.push({ name: '07-private', html: `
<div class="head"><h1>Your photos<br><span class="g">never leave</span><br>your phone</h1></div>
<img src="${roo('moods/roo-love')}" style="position:absolute;left:50%;top:560px;transform:translateX(-50%);width:640px;filter:drop-shadow(0 30px 40px rgba(0,0,0,.45))">
<div style="position:absolute;left:90px;right:90px;top:1240px">
  ${[
    ['📵', 'No account, no cloud, no ads', 'Everything happens on your phone'],
    ['🗑️', 'Safe deletes', 'Recover from Trash for 30 days'],
    ['✅', 'You stay in control', 'Android asks before anything is removed'],
  ].map(([e, t, s]) => `<div class="card row" style="padding:30px 34px;gap:28px;margin-top:22px;border-radius:40px"><span style="font-size:58px">${e}</span><div><div style="font-weight:800;font-size:38px">${t}</div><div class="lo" style="font-size:28px;margin-top:4px">${s}</div></div></div>`).join('')}
</div>
`});

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files'], executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  for (const s of slides) {
    const file = path.join(OUT, `${s.name}.html`);
    fs.writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"><style>${css}</style></head><body>${s.html}</body></html>`);
    await page.goto(`file://${file}`, { waitUntil: 'load' });
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: path.join(OUT, `${s.name}.png`) });
    console.log('wrote', s.name);
  }
  await browser.close();
})();
