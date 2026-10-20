// Run after browser-full.cjs. It uses a copy of that test's game save.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.TQ_CHROMIUM || '/usr/bin/chromium', headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 }, storageState: '/workspace/.cache/tq-allclear-state.json' });
  const page = await context.newPage(); const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const url = process.env.TQ_GAME_URL || 'http://127.0.0.1:8080';
  const load = async () => { await page.goto(url, { waitUntil: 'domcontentloaded' }); await page.waitForSelector('canvas'); await page.waitForTimeout(500); };
  const tap = async (x, y) => { await page.mouse.click(x, y); await page.waitForTimeout(250); };
  const shot = name => page.screenshot({ path: path.resolve(__dirname, '../docs/screenshots/' + name + '.png') });
  const read = key => page.evaluate(k => localStorage.getItem('terminal-quest:' + k), key);
  try {
    await load(); assert.equal(await read('xpi'), '8400');
    await tap(1200, 49); await tap(450, 209); await tap(630, 276); await tap(450, 343); await shot('settings');
    await tap(640, 600);
    assert.equal(await read('mutedb'), 'true'); assert.equal(await read('reducedMotionb'), 'true');
    assert.ok(Math.abs(+(await read('volumef')) - .25) < .051);
    await load(); assert.equal(await read('mutedb'), 'true'); assert.equal(await read('xpi'), '8400');
    await tap(1200, 49); await tap(640, 476); await shot('reset-confirm');
    await tap(580, 476); assert.equal(await read('xpi'), '8400', 'Cancel preserves the save');
    await tap(640, 476); await tap(700, 476);
    await page.waitForFunction(() => localStorage.getItem('terminal-quest:xpi') === '0', null, { timeout: 5000 });
    await load(); assert.equal(await read('completeds'), ''); assert.equal(await read('xpi'), '0');
    assert.equal(await read('mutedb'), 'false'); assert.equal(await read('reducedMotionb'), 'false');
    await shot('reset-complete');
    await page.setViewportSize({ width: 720, height: 500 });
    assert.equal(await page.locator('#mobile-note').evaluate(el => getComputedStyle(el).display), 'block');
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ status: 'passed', settingsRestored: true, cancelledResetPreserved: true, confirmedResetRestoredEmpty: true, pcRecommendation: true, errors }));
  } catch (error) { await shot('settings-failure'); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
