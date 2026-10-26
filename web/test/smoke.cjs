/* Tries the web version in a real (headless) Chrome: loads the Ontario test household, opens every tab, ticks
   something off, opens a row's menu and the "why" of a money move, and saves a screenshot of each. Fails on any
   error in the page. Run by the build on GitHub (see .github/workflows/build.yml):
     CHROME=/usr/bin/google-chrome OUT=app/build/screens node web/test/smoke.cjs */
'use strict';
const path = require('path'), fs = require('fs');
const puppeteer = require('puppeteer-core');
const root = path.join(__dirname, '..', '..');
const out = path.resolve(process.env.OUT || 'app/build/screens'); fs.mkdirSync(out, { recursive: true });
const scenarios = require(path.join(root, 'parity', 'scenarios.js'));
const docs = scenarios.find(s => s.name === 'ontario').docs;
const TODAY = process.env.TODAY || '2026-10-26';

(async () => {
  const browser = await puppeteer.launch({ executablePath: process.env.CHROME || '/usr/bin/google-chrome', headless: true, args: ['--no-sandbox', '--allow-file-access-from-files'] });
  const problems = [];
  for (const theme of ['light', 'dark']) {
    const page = await browser.newPage();
    await page.setViewport({ width: 412, height: 915, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
    await page.emulateMediaFeatures([{ name: 'prefers-color-scheme', value: theme }]);
    let where = 'start';
    page.on('pageerror', e => problems.push(`${theme} (${where}): page error: ${(e.stack || e.message).split('\n').slice(0, 4).join(' | ')}`));
    page.on('console', m => { if (m.type() === 'error') problems.push(`${theme}: console: ${m.text()}`); });
    await page.evaluateOnNewDocument((data, today) => {
      /* a fixed day, so the screenshots don't change by themselves */
      const fixed = new Date(today + 'T12:00:00').getTime(), RealDate = Date;
      class FixedDate extends RealDate { constructor(...a) { super(...(a.length ? a : [fixed])); } static now() { return fixed; } }
      window.Date = FixedDate;
      if (!localStorage.getItem('smoke-seeded')) { localStorage.clear(); localStorage.setItem('family-budget-v1', JSON.stringify(data)); localStorage.setItem('smoke-seeded', '1');
        localStorage.setItem('rs-ios-ok', '1'); localStorage.setItem('rs-tab', 'today'); }
    }, docs, TODAY);
    await page.goto('file://' + path.join(root, 'web', 'index.html'));
    const settle = () => new Promise(r => setTimeout(r, 500));
    const shot = async name => { await settle(); await page.screenshot({ path: path.join(out, `web-${theme}-${name}.png`), fullPage: true }); };
    const click = async sel => { where = sel; const el = await page.$(sel); if (!el) { problems.push(`${theme}: nothing to click for ${sel}`); return false; } await el.click(); await settle(); return true; };
    await settle();
    for (const tab of ['today', 'overview', 'fixed', 'spending', 'savings']) { await click(`#tabs [data-tab="${tab}"]`); await shot(tab === 'overview' ? 'month' : tab === 'fixed' ? 'bills' : tab); }
    await click('#gear'); await shot('settings');
    /* Today: the To do list, a row's ⋯ menu, ticking something off, and the why of a money move */
    await click('#tabs [data-tab="today"]');
    if (!(await page.$('#todo'))) problems.push(`${theme}: Today has no To do list`);
    if (await click('#todo .trow details.rowmenu summary')) await shot('today-menu');
    await page.mouse.click(5, 5); await settle();
    const before = await page.$$eval('#todo .trow .tick:not(.on)', l => l.length);
    if (before) { await click('#todo .trow .tick:not(.on)'); const after = await page.$$eval('#todo .trow .tick:not(.on)', l => l.length);
      if (after >= before) problems.push(`${theme}: ticking a To do item didn't take it off the list (${before} → ${after})`); }
    if (await page.$('#todo [data-act="why"]')) { await click('#todo [data-act="why"]'); await shot('why'); await click('[data-act="sheet-close"]'); }
    /* accounts, with the ATM settings */
    await page.evaluate(() => { localStorage.setItem('rs-tab', 'accounts'); }); await page.reload(); await settle();
    await page.evaluate(() => document.querySelectorAll('details.item').forEach(d => d.open = true)); await shot('accounts');
    await page.close();
  }
  await browser.close();
  if (problems.length) { console.error('Web version problems:\n - ' + problems.join('\n - ')); process.exit(1); }
  console.log('Web version: every tab opens, To do ticks, menus and the why view work, no errors.');
})().catch(e => { console.error(e); process.exit(1); });
