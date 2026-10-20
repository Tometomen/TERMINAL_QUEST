// Uses an isolated copy of the save produced by browser-typing.cjs.
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const { outputChanged } = require('./browser-helpers.cjs');
(async () => {
  const browser = await chromium.launch({
    executablePath: process.env.TQ_CHROMIUM || '/usr/bin/chromium', headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-angle=swiftshader', '--enable-unsafe-swiftshader']
  });
  const context = await browser.newContext({ viewport: { width: 1280, height: 800 }, storageState: '/workspace/.cache/tq-typing-state.json' });
  // A new build must never be validated against cached GWT permutations.
  await context.route('**/*', route => route.continue());
  const page = await context.newPage();
  for (const previous of context.pages()) if (previous !== page) await previous.close();
  await page.bringToFront(); const errors = [];
  await page.goto('about:blank');
  page.on('pageerror', error => errors.push(error.message));
  const tap = async (x, y) => { await page.mouse.click(x, y); await page.waitForTimeout(250); };
  const command = async text => { await page.keyboard.type(text); await page.keyboard.press('Enter'); await page.waitForTimeout(120); };
  const shot = name => page.screenshot({ path: path.resolve(__dirname, '../docs/screenshots/' + name + '.png') });
  try {
    await page.goto(process.env.TQ_GAME_URL || 'http://127.0.0.1:8080', { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('canvas'); await page.waitForTimeout(500);
    assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:typing.1i')), '5');
    await tap(200, 543); await tap(460, 438); await tap(1150, 420);
    await tap(990, 568); await command('echo hint-focus');
    await tap(1130, 543); await shot('command-book');
    await tap(640, 690); await command('echo book-focus');
    for (let i = 0; i < 8; i++) await command('help');
    await command('echo ' + 'log '.repeat(500) + '> large.log');
    for (let i = 0; i < 8; i++) await command('cat large.log');
    await command('df'); const beforeScroll = await shot('long-log');
    await page.mouse.move(500, 350); await page.waitForTimeout(200); await page.mouse.wheel(0, -3000); await page.waitForTimeout(400);
    const afterScroll = await shot('long-log-scrolled');
    assert.ok(await outputChanged(page, beforeScroll, afterScroll), 'Wheel must change rendered terminal output');
    // Wheel changes scroll focus only; keyboard focus remains in the terminal.
    await command('clear'); await command('touch notes.txt');
    await page.waitForFunction(() => (localStorage.getItem('terminal-quest:completeds') || '').includes('L2-1'), null, { timeout: 5000 });
    assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:rank.L2-1s')), 'B');
    for (const commandName of ['echo', 'help', 'cat', 'df', 'clear', 'touch']) {
      assert.ok((await page.evaluate(() => localStorage.getItem('terminal-quest:mastereds'))).split(',').includes(commandName));
    }
    assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:xpi')), '750');
    await tap(1150, 125); await tap(1190, 125); await tap(1200, 49); await shot('settings');
    assert.deepEqual(errors, []); console.log(JSON.stringify({ status: 'passed', focusAfterHintAndBook: true, longLogsAndWheel: true, rank: 'B', errors }));
  } catch (error) { await shot('input-failure'); throw error; }
  finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
