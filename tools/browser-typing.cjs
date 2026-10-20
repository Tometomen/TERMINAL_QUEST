const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.TQ_CHROMIUM || '/usr/bin/chromium', headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']
  });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
  // A new build must never be validated against cached GWT permutations.
  await context.route('**/*', route => route.continue());
  const page = await context.newPage();
  for (const previous of context.pages()) if (previous !== page) await previous.close();
  await page.bringToFront(); const errors = [];
  await page.goto('about:blank');
  page.on('pageerror', error => errors.push(error.message));
  const shot = name => page.screenshot({ path: path.resolve(__dirname, '../docs/screenshots/' + name + '.png') });
  const load = async () => { await page.goto(process.env.TQ_GAME_URL || 'http://127.0.0.1:8080', { waitUntil: 'domcontentloaded' }); await page.waitForSelector('canvas'); await page.waitForTimeout(600); };
  try {
    await load();
    // A known, valid, completed level-one fixture in an isolated context.
    // The campaign script separately verifies how players earn this progress.
    await page.evaluate(() => {
      for (const key of Object.keys(localStorage)) if (key.startsWith('terminal-quest:')) localStorage.removeItem(key);
      localStorage.setItem('terminal-quest:versioni', '1');
      localStorage.setItem('terminal-quest:completeds', 'L1-1,L1-2,L1-3,L1-4,L1-5');
      for (let i = 1; i <= 5; i++) localStorage.setItem('terminal-quest:rank.L1-' + i + 's', 'S');
      localStorage.setItem('terminal-quest:xpi', '600');
      localStorage.setItem('terminal-quest:mastereds', 'pwd,ls,cd');
    });
    await load(); await page.mouse.click(430, 543); await page.waitForTimeout(300);
    await shot('typing');
    const startedAt = Date.now();
    await page.mouse.click(200, 690); await page.waitForTimeout(150);
    for (const text of ['pwd', 'ls', 'cd logs', 'cd ..', 'ls /home/cadet', 'wrong']) {
      await page.keyboard.type(text); await page.keyboard.press('Enter'); await page.waitForTimeout(100);
    }
    await shot('typing-active');
    await page.waitForFunction(() => localStorage.getItem('terminal-quest:typing.1i') === '5', null, { timeout: 80000 });
    const elapsed = Date.now() - startedAt;
    const score = await page.evaluate(() => ({
      completed: +localStorage.getItem('terminal-quest:typing.1i'),
      accuracy: +localStorage.getItem('terminal-quest:typingAccuracy.1f'),
      kpm: +localStorage.getItem('terminal-quest:typingKpm.1f'),
      combo: +localStorage.getItem('terminal-quest:typingCombo.1i'),
      xp: localStorage.getItem('terminal-quest:xpi')
    }));
    assert.ok(elapsed >= 59000, 'The challenge must last a real 60 seconds');
    assert.equal(score.completed, 5); assert.equal(score.combo, 5);
    assert.ok(Math.abs(score.accuracy - 86.11111) < .01); assert.ok(Math.abs(score.kpm - 36) < .01);
    assert.equal(score.xp, '600', 'Typing never changes mission XP');
    await shot('typing-result'); await load();
    assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:typing.1i')), '5');
    await context.storageState({ path: '/workspace/.cache/tq-typing-state.json' });
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ status: 'passed', elapsedMs: elapsed, score, reloadRestored: true, errors }));
  } catch (error) { await shot('typing-failure'); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
