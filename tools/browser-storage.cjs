const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
(async () => {
  const browser = await chromium.launch({ executablePath: process.env.TQ_CHROMIUM || '/usr/bin/chromium', headless: true,
    args: ['--no-sandbox', '--disable-dev-shm-usage', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  try {
    for (const scenario of ['unavailable', 'write-denied', 'corrupt']) {
      const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
      if (scenario === 'unavailable') await context.addInitScript(() => {
        Object.defineProperty(window, 'localStorage', { get() { throw new DOMException('Storage disabled for acceptance test', 'SecurityError'); } });
      });
      if (scenario === 'corrupt') await context.addInitScript(() => localStorage.setItem('terminal-quest:versioni', '999'));
      const page = await context.newPage(); const errors = [];
      page.on('pageerror', error => errors.push(error.message));
      await page.goto(process.env.TQ_GAME_URL || 'http://127.0.0.1:8080', { waitUntil: 'domcontentloaded' });
      await page.waitForSelector('canvas'); await page.waitForTimeout(600);
      await page.screenshot({ path: path.resolve(__dirname, '../docs/screenshots/storage-' + scenario + '.png') });
      if (scenario === 'write-denied') await page.evaluate(() => {
        Storage.prototype.setItem = function () { throw new DOMException('Acceptance test quota', 'QuotaExceededError'); };
      });
      await page.mouse.click(200, 543); await page.waitForTimeout(180);
      await page.mouse.click(1150, 420); await page.waitForTimeout(180);
      await page.keyboard.type('pwd'); await page.keyboard.press('Enter'); await page.waitForTimeout(400);
      await page.screenshot({ path: path.resolve(__dirname, '../docs/screenshots/storage-' + scenario + '-play.png') });
      if (scenario === 'write-denied') assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:completeds')), null);
      if (scenario === 'corrupt') {
        assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:versioni')), '1');
        assert.equal(await page.evaluate(() => localStorage.getItem('terminal-quest:completeds')), 'L1-1');
      }
      assert.deepEqual(errors, []);
      console.log('PASS storage ' + scenario);
      await context.close();
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
