#!/usr/bin/env node
/* Runs the web version's engine (from web/index.html) over the test households and saves every
   result to parity/fixtures.json. The Kotlin engine must produce the same numbers. */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');

const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'web', 'index.html'), 'utf8');
const coreSrc = html.match(/<script id="core">([\s\S]*?)<\/script>/)[1];
const ctx = { console }; vm.createContext(ctx); vm.runInContext(coreSrc.replace('typeof window!==\'undefined\'?window:globalThis', 'this'), ctx);
const C = ctx.Core;
if (!C) throw new Error('Engine not found in web/index.html');

/* the same defaults the web app fills in (see DEFAULTS and settings() in web/index.html) */
const DEFAULTS = JSON.parse(html.match(/const DEFAULTS=(\{[\s\S]*?\});/)[1].replace(/([{,]\s*)([A-Za-z_]\w*)\s*:/g, '$1"$2":').replace(/'/g, '"'));

function fromDocs(docs, today) {
  const s = Object.assign({}, DEFAULTS, docs['settings/main'] || {});
  if (!C.isYm(s.startYm)) s.startYm = C.ymOf(today);
  if (!C.isDate(s.trackFrom)) s.trackFrom = today;
  const st = { settings: s, bills: {}, months: {}, expenses: {} };
  for (const k of Object.keys(docs)) {
    let m;
    if ((m = k.match(/^bills\/([^/]+)$/))) st.bills[m[1]] = docs[k];
    else if ((m = k.match(/^months\/(\d{4}-\d{2})$/))) st.months[m[1]] = docs[k];
    else if ((m = k.match(/^months\/(\d{4}-\d{2})\/expenses\/([^/]+)$/))) (st.expenses[m[1]] = st.expenses[m[1]] || {})[m[2]] = docs[k];
  }
  return st;
}

const label = e => e.note || 'One-time expense';
/* the status badge, using the web app's own getStatus */
const UI = (() => {
  const app = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const pick = re => app.match(re)[0];
  const src = [pick(/const fmt=n=>[^\n]*/), pick(/const monthOnly=ym=>[^\n]*/), pick(/const dShort=s=>[^\n]*/),
    pick(/function selfTip\(c,future\)\{[^\n]*\n[^\n]*/), pick(/function getStatus\(c,up,phase,today,s\)\{[\s\S]*?\n\}/)].join('\n');
  const u = { C, num: C.num, round2: C.round2, ymOf: C.ymOf, diffDays: C.diffDays, Math, Date, Object, JSON, String, Number };
  vm.createContext(u); vm.runInContext(src + ';this.getStatus=getStatus;', u);
  return { getStatus: u.getStatus, phaseOf: (ym, today, s) => { const cur = C.ymOf(today); if (ym === cur) return 'now'; if (ym < cur) return 'past'; if (ym === s.startYm && cur < s.startYm) return 'now'; return 'future'; } };
})();
/* balance checks, using the web app's own expectedSince() and coverage() */
const CK = (() => {
  const app = html.match(/<script>([\s\S]*?)<\/script>/)[1];
  const pick = re => app.match(re)[0];
  const src = [pick(/function expectedSince\(prev,today\)\{[\s\S]*?\n\}/), pick(/function coverage\(bal,today\)\{[\s\S]*?\n\}/)].join('\n');
  const u = { C, num: C.num, round2: C.round2, ymOf: C.ymOf, ymAdd: C.ymAdd, addDays: C.addDays, Math, Object, String, cur: null, exp: null };
  vm.createContext(u); vm.runInContext('function st(){return cur}function allExpenses(){return exp}' + src + ';this.expectedSince=expectedSince;this.coverage=coverage;', u);
  return { run: (st, docs, prev, bal, today) => { u.cur = st; u.exp = [];
    for (const k of Object.keys(docs)) { const m = k.match(/^months\/(\d{4}-\d{2})\/expenses\/([^/]+)$/); if (m && docs[k] && C.isDate(docs[k].date)) u.exp.push(Object.assign({ id: m[2], ym: m[1] }, docs[k])); }
    return { expected: u.expectedSince(prev, today), coverage: u.coverage(bal, today) }; } };
})();
/* the made-up balances the parity check types in: some accounts short, some not */
const ckBal = s => { const bal = {}; C.accountsOf(s).forEach((a, i) => { bal[a.id] = i % 2 ? 900 + i * 13.37 : 25.5 * (i + 1); }); return bal; };
const scenarios = require(process.env.SCENARIOS || './scenarios.js');
const out = { generatedFrom: 'web/index.html', defaults: DEFAULTS, scenarios: [], holidays: {}, sched: [], bills: [] };

for (const sc of scenarios) {
  const docs = JSON.parse(JSON.stringify(sc.docs));
  /* months marked lockMe are locked with a snapshot made by the web engine, the way the app does it */
  for (const k of Object.keys(docs)) if (docs[k] && docs[k].lockMe) {
    delete docs[k].lockMe; const ym = k.slice(7);
    const st0 = fromDocs(Object.assign({}, docs, { [k]: Object.assign({}, docs[k], { closed: false }) }), ym + '-28');
    const snap = C.snapshot(C.monthCalc(ym, st0, ym + '-28'));
    docs[k] = Object.assign({}, docs[k], { closed: true, closedOn: ym + '-28', snapshot: snap });
  }
  const res = {};
  for (const today of sc.todays) {
    const st = fromDocs(docs, today), cur = C.ymOf(today), r = {};
    r.months = {}; for (let i = -2; i <= 3; i++) { const ym = C.ymAdd(cur, i); r.months[ym] = C.monthCalc(ym, st, today); }
    r.upcoming = C.upcoming(st, today, 10, label);
    r.food = C.foodPeriod(C.monthCalc(cur, st, today), today, st.settings);
    r.moves = C.moneyMoves(st, C.addDays(today, -10), C.addDays(today, 40));
    r.savings = C.savingsState(st);
    r.targets = C.targetsThrough(st, cur, today);
    r.projection = C.projection(st, today, cur, 6);
    r.debts = {}; for (const id of Object.keys(st.bills)) r.debts[id] = C.debtInfo(st, id, today);
    r.events = C.billEvents(st, C.ymAdd(cur, -1) + '-01', C.dayIn(C.ymAdd(cur, 1), 31));
    r.paydays = C.mainPaydays(st.settings, cur);
    r.funds = C.fundsOf(st.settings);
    r.runway = C.runway(st, today, ckBal(st.settings));
    r.checkin = CK.run(st, docs, { date: C.addDays(today, -12), day: 1500.25 }, ckBal(st.settings), today);
    r.status = {}; for (const ym of [cur, C.ymAdd(cur, 1), C.ymAdd(cur, -1)]) { const c = C.monthCalc(ym, st, today); r.status[ym] = UI.getStatus(c, C.upcoming(st, today, 10, label), UI.phaseOf(ym, today, st.settings), today, st.settings); }
    res[today] = r;
  }
  out.scenarios.push({ name: sc.name, docs, todays: sc.todays, results: res });
}

/* Where you stand from a starting point (docs/plans/getting-on-track-fixes.md): the balance is the truth, today's paydays
   in the balance or not yet, and food / Personal Expenses money the person confirmed */
{ /* a plain household: ODSP pays the rent on the 1st, a paycheque every two weeks, a few bills, food and Personal Expenses */
  const base = {
    'settings/main': { householdName: 'Plain', people: ['Alex'], foodPct: 40, foodMin: 400, startYm: '2026-11', trackFrom: '2026-11-01',
      accounts: [{ id: 'm', name: 'Main', type: 'chequing', purposes: ['main', 'rent', 'everyday', 'online'] }, { id: 'sv', name: 'Savings', type: 'savings', purposes: [] }],
      incomes: [{ id: 'od', name: 'ODSP', amount: 1400, assist: 'odsp', sched: { type: 'odsp' }, account: 'm', role: 'rent', ded: {} },
                { id: 'pay', name: 'Paycheque', amount: 900, kind: 'pay', sched: { type: 'biweekly', anchor: '2026-11-13' }, account: 'm', role: 'main', odsp: 'spouse' }],
      selfPlan: [{ name: 'Coffee', amount: 25, per: 'week' }] },
    'bills/rent': { name: 'Rent', group: 'adults', category: 'housing', amount: 1350, freq: 'monthly', day: 1, account: 'm' },
    'bills/phone': { name: 'Phone', group: 'adults', category: 'phone', amount: 55, freq: 'monthly', day: 24, account: 'm' },
    'bills/net': { name: 'Internet', group: 'adults', category: 'phone', amount: 80, freq: 'monthly', day: 26, account: 'm' },
    'months/2026-11': { starting: true, paid: { 'rent@2026-11-01': true } },
  };
  const withStart = start => { const d = JSON.parse(JSON.stringify(base)); d['months/2026-11'].start = start; return d; };
  const cases = [
    { name: '$0 mid-half', docs: base, today: '2026-11-20', bal: { m: 0 } },
    { name: '$0 first day of the half', docs: base, today: '2026-11-16', bal: { m: 0, sv: 50 } },
    { name: 'some money, bills come first', docs: base, today: '2026-11-20', bal: { m: 210.4 } },
    { name: 'more than the plan', docs: base, today: '2026-11-20', bal: { m: 2600, sv: 999 } },
    { name: 'payday today, already in', docs: base, today: '2026-11-27', bal: { m: 1040 } },
    { name: 'payday today, not in yet', docs: withStart({ date: '2026-11-27', notIn: ['pay'] }), today: '2026-11-27', bal: { m: 140 } },
    { name: 'not in yet, but a later day', docs: withStart({ date: '2026-11-26', notIn: ['pay'] }), today: '2026-11-27', bal: { m: 140 } },
    { name: 'confirmed amounts', docs: withStart({ date: '2026-11-20', foodLeft: 85, selfLeft: '12.5' }), today: '2026-11-20', bal: { m: 90 } },
    { name: 'confirmed blank', docs: withStart({ date: '2026-11-20', foodLeft: '', selfLeft: null, notIn: 'x' }), today: '2026-11-20', bal: { m: 500 } },
    { name: 'first half', docs: base, today: '2026-11-05', bal: { m: 300 } },
  ];
  out.runways = cases.map(k => ({ name: k.name, docs: k.docs, today: k.today, bal: k.bal, result: C.runway(fromDocs(k.docs, k.today), k.today, k.bal) }));
}

/* backups and merging, using the web version's own functions */
async function backupVectors() {
  const src = html.match(/(const B64=[\s\S]*?)function backupPayload/)[1];
  const bctx = { crypto: globalThis.crypto, TextEncoder, TextDecoder, btoa, atob, Uint8Array, JSON, Math, Object, Set, Error, String, num: C.num };
  vm.createContext(bctx); vm.runInContext(src + ';this.encryptBackup=encryptBackup;this.decryptBackup=decryptBackup;this.mergeData=mergeData;', bctx);
  const plain = { app: 'family-budget', schema: 1, exportedAt: '2026-10-25T12:00:00.000Z', data: scenarios[0].docs };
  out.backup = { password: 'Tr1cky pässwörd ✓', plain, text: await bctx.encryptBackup(plain, 'Tr1cky pässwörd ✓') };
  const a = { 'settings/main': { householdName: 'A', _u: 5 }, 'bills/x': { name: 'X', _u: 10 }, 'bills/y': { name: 'Y', _u: 3 }, 'bills/gone': { name: 'G', _u: 2 },
    'months/2026-11': { paid: { 'x@2026-11-01': true, 'y@2026-11-02': false }, moves: { m1: true }, note: 'a', _u: 20 }, 'meta/tombstones': { 'bills/gone': 4, 'bills/z': 1 } };
  const b = { 'settings/main': { householdName: 'B', _u: 9 }, 'bills/x': { name: 'X2', _u: 8 }, 'bills/y': { name: 'Y2', _u: 30 }, 'bills/new': { name: 'N', _u: 1 }, 'bills/z': { name: 'Z', _u: 5 },
    'months/2026-11': { paid: { 'y@2026-11-02': true, 'q@2026-11-09': 1 }, note: 'b', _u: 15 }, 'months/2026-11/expenses/e': { amount: 5, _u: 3 }, 'meta/tombstones': { 'bills/new': 2 } };
  out.merge = [a, b].length && [[a, b], [b, a], [a, {}], [{}, b]].map(([x, y]) => ({ a: x, b: y, result: bctx.mergeData(x, y) }));
}

(async () => {
await backupVectors();
for (let y = 2024; y <= 2028; y++) for (const p of ['ON', 'QC', 'NS', 'BC', 'AB', 'MB', 'YT', 'NL']) out.holidays[y + p] = C.holidaysOf(y, p);

const scheds = [{ type: 'monthly', day: 31 }, { type: 'monthly' }, { type: 'monthly', day: 15, shift: 'after' }, { type: 'monthly', day: 1, shift: 'none' }, { type: 'semimonthly' },
  { type: 'quarterly', months: [1, 4, 7, 10], day: 5 }, { type: 'quarterly', months: [2, 5, 8, 11], day: 27 }, { type: 'ccb' }, { type: 'cwb' }, { type: 'thirdthu' },
  { type: 'weekly', anchor: '2026-01-02' }, { type: 'biweekly', anchor: '2026-10-26' }, { type: 'biweekly', anchor: '2027-03-19', shift: 'after' }, { type: 'weekly', anchor: '2027-06-04' }, { type: 'biweekly', anchor: '2026-10-30' }, { type: 'lastbiz' }, { type: 'firstbiz' },
  { type: 'odsp' }, { type: 'qc' }, { type: 'ns', slot: 1 }, { type: 'ns', slot: 2 }, { type: 'ns' }, { type: 'biweekly' }, {}];
for (const sch of scheds) { const r = {}; for (let ym = '2025-12'; ym <= '2028-01'; ym = C.ymAdd(ym, 1)) { r[ym] = C.schedDates(sch, ym); if (C.schedEstimated(sch, ym)) r[ym + '?'] = true; } out.sched.push({ sch, dates: r }); }

const odd = [{ freq: 'monthly', day: 31, amount: 10 }, { freq: 'weekly', anchor: '2026-12-30', amount: 5 }, { freq: 'quarterly', anchor: '2026-11-30', amount: 90 }, { freq: 'yearly', anchor: '2024-02-29', amount: 120 },
  { freq: 'biweekly', amount: 7 }, { amount: 3, start: '2027-01-15', end: '2027-06-01' }, { freq: 'monthly', day: 0, amount: 1 }, { group: 'baby', amount: 4, day: 9 }];
for (const b of odd) out.bills.push({ b, dates: C.billDates(b, '2026-11-01', '2028-03-31', { babyDue: '2027-02-10' }), plan: ['2026-11', '2027-01', '2027-02', '2027-12'].map(ym => C.billPlanForMonth(b, ym, { babyDue: '2027-02-10' })) });

/* government savings programs, using the web app's own rdspMoney() and respMoney() */
{ const app = html.match(/<script>([\s\S]*?)<\/script>/)[1]; const pick = re => app.match(re)[0];
  const u = { num: C.num, round2: C.round2, Math }; vm.createContext(u);
  vm.runInContext([pick(/const PROG=\{[^\n]*/), pick(/function rdspMoney\([^\n]*\n[^\n]*/), pick(/function respMoney\([^\n]*/)].join('\n') + ';this.rdspMoney=rdspMoney;this.respMoney=respMoney;', u);
  out.programs = [];
  for (const c of [0, 120, 500, 777.77, 1500, 2600]) for (const inc of ['', null, 0, 20000, 38237, 45000, 58523, 60000, 100000, 114750, 117045, 130000])
    out.programs.push({ c, inc, rdsp: u.rdspMoney(c, inc), resp: u.respMoney(c, inc) }); }

fs.writeFileSync(process.env.OUT || path.join(__dirname, 'fixtures.json'), JSON.stringify(out));
console.log('fixtures.json: ' + out.scenarios.length + ' households, ' + Object.keys(out.holidays).length + ' holiday years, ' + out.sched.length + ' schedules');
})();
