const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const solutions = require('./campaign-solutions.json');
const { primaryY } = require('./browser-helpers.cjs');
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.TQ_CHROMIUM || '/usr/bin/chromium', headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  const page = await context.newPage(); const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const tap = async (x, y) => { await page.mouse.click(x, y); await page.waitForTimeout(180); };
  const shot = name => page.screenshot({ path: path.resolve(__dirname, '../docs/screenshots/' + name + '.png') });
  try {
    await page.goto(process.env.TQ_GAME_URL || 'http://127.0.0.1:8080', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('canvas'); await page.waitForTimeout(600); await page.bringToFront();
    await shot('title'); await tap(200, 543); await shot('levels'); await tap(1150, 420);
    for (let i = 0; i < solutions.length; i++) {
      const [id, commands] = solutions[i];
      for (const text of commands) { await page.keyboard.type(text); await page.keyboard.press('Enter'); await page.waitForTimeout(120); }
      await page.waitForFunction(target => (localStorage.getItem('terminal-quest:completeds') || '').split(',').includes(target), id, { timeout: 5000 });
      console.log('PASS ' + id);
      if (id === 'L6-5') { await shot('final-boss'); break; }
      await tap(1100, await primaryY(page, 940, 400)); await page.mouse.move(700, 500); await page.waitForTimeout(200);
      await page.mouse.wheel(0, 2500); await page.waitForTimeout(500);
      if (i === 0) await shot('result');
      await tap(180, await primaryY(page, 75, 500));
    }
    const saved = await page.evaluate(() => ({ completed: localStorage.getItem('terminal-quest:completeds'), xp: localStorage.getItem('terminal-quest:xpi') }));
    assert.equal(saved.completed.split(',').length, 30); assert.equal(saved.xp, '8400');
    await page.reload({ waitUntil: 'domcontentloaded' }); await page.waitForSelector('canvas'); await page.waitForTimeout(500);
    assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:xpi')), '8400');
    await tap(200, 543); await page.mouse.move(300, 620); await page.waitForTimeout(200); await page.mouse.wheel(0, 1800); await page.waitForTimeout(500);
    await shot('all-levels-clear');
    await context.storageState({ path: '/workspace/.cache/tq-allclear-state.json' });
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ status: 'passed', missions: 30, xp: saved.xp, reloadRestored: true, errors }));
  } catch (error) { await shot('campaign-failure'); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
