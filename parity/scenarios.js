/* Made-up households used to check that the Android engine gives exactly the same
   answers as the web version. Each one is a set of saved documents, the same shape as a backup. */
'use strict';

function household(over) { return Object.assign({ householdName: 'Test', people: ['Alex', 'Sam'], foodPct: 40, foodMin: 400, emergencyGoal: 2000, upcomingDays: 10 }, over); }

const accountsON = [
  { id: 'a-main', name: 'Main Chequing', type: 'chequing', purposes: ['main'] },
  { id: 'a-rent', name: 'Rent Chequing', type: 'chequing', purposes: ['rent'] },
  { id: 'a-day', name: 'Everyday Card', type: 'chequing', purposes: ['everyday'] },
  { id: 'a-web', name: 'Prepaid Card', type: 'prepaid', purposes: ['online'] },
  { id: 'a-cash', name: 'Cash', type: 'cash', purposes: [] },
  { id: 'a-sav', name: 'Savings', type: 'savings', kind: 'hisa', purposes: [] },
];

/* 1. Ontario: ODSP for rent, a bi-weekly paycheck, benefits, every kind of bill */
const on = {
  'settings/main': household({
    startYm: '2026-11', trackFrom: '2026-10-20', babyDue: '2026-11-28', ccbEmergencyPct: 10, emergencyStart: 150, childStart: 40, foodPct: 100,
    accounts: accountsON,
    incomes: [
      { id: 'i-odsp', name: "Alex's ODSP", amount: 1289, assist: 'odsp', sched: { type: 'odsp' }, account: 'a-rent', role: 'rent', ded: { dis: true, work: true, wrb: true } },
      { id: 'i-pay', name: "Sam's paycheck", amount: 1263.33, sched: { type: 'biweekly', anchor: '2026-10-26' }, account: 'a-main', role: 'main', odsp: 'spouse', person: 'Sam' },
      { id: 'i-side', name: 'Side work', amount: 120, sched: { type: 'monthly', day: 3 }, account: 'a-main', role: 'other', odsp: 'dis', person: 'Alex', start: '2026-12-01' },
    ],
    credits: [
      { id: 'c-ccb', type: 'ccb', amount: 580, account: 'a-main', protect: true, start: '2026-12' },
      { id: 'c-otb', type: 'otb', amount: 70, account: 'a-main' },
      { id: 'c-cgeb', type: 'cgeb', amount: 129.5, account: 'a-day' },
      { id: 'c-cwb', type: 'cwb', amount: 400, account: 'a-main' },
      { id: 'c-oth', type: 'other', name: 'Rebate', amount: 25, sched: { day: 28 }, account: 'a-main' },
    ],
    funds: [{ key: 'emergency', account: 'a-sav' }, { key: 'child', account: 'a-sav' }, { key: 'fvet', name: 'Vet fund', account: 'a-sav', goal: 500, start: 30 }],
    quick: [{ category: 'groceries', amount: 50, note: '' }],
    selfPlan: [{ name: 'Coffee', amount: 3.25, times: 5, per: 'week' }, { name: 'Game', amount: 35, per: 'pay' }, { name: 'Odd', amount: 'abc', per: 'month' },
      { name: 'Plain', amount: 15 }, { name: 'Snack', amount: 2.5, times: 1, per: 'day' }, { name: 'Bad times', amount: 4, times: 'x', per: 'week' }, { amount: 10, times: 0, per: 'month' }, null],
  }),
  'bills/rent': { name: 'Rent', group: 'adults', category: 'housing', amount: 1364, freq: 'monthly', day: 1, account: 'a-rent', method: 'Cash' },
  'bills/hydro': { name: 'Hydro', group: 'adults', category: 'utilities', amount: 60, freq: 'monthly', day: 18, account: 'a-main', method: 'Pre-authorized debit (PAD)', deferrable: true },
  'bills/phone': { name: 'Phone', group: 'adults', category: 'phone', amount: 45.2, freq: 'monthly', day: 29, account: 'a-web', method: 'Pre-authorized debit (PAD)', deferrable: true },
  'bills/net': { name: 'Internet', group: 'adults', category: 'phone', amount: 100, freq: 'monthly', day: 12, account: 'a-main', method: 'Debit card', deferrable: true },
  'bills/car': { name: 'Car loan', group: 'adults', category: 'loan', amount: 290, freq: 'biweekly', anchor: '2026-10-30', account: 'a-main', method: 'Pre-authorized debit (PAD)',
    debt: { mode: 'balance', owed: 9000, asOf: '2026-09-01', rate: 6.9 } },
  'bills/prop': { name: 'Proposal', group: 'adults', category: 'loan', amount: 125, freq: 'monthly', day: 22, account: 'a-main', method: 'Pre-authorized debit (PAD)', start: '2025-07-01',
    debt: { mode: 'original', principal: 3000, start: '2025-06-10', missed: 1, extra: [{ date: '2025-06-10', amount: 225 }, { date: '2027-04-30', amount: 500 }] } },
  'bills/takeout': { name: 'Takeout', group: 'adults', category: 'other', amount: 40, freq: 'weekly', anchor: '2026-10-25', account: 'a-main', method: 'Debit card', essential: false, deferrable: true },
  'bills/stream': { name: 'Streaming', group: 'adults', category: 'subscription', amount: 18.99, freq: 'monthly', day: 15, account: 'a-web', method: 'Debit card', essential: false },
  'bills/ins': { name: 'Car insurance', group: 'adults', category: 'insurance', amount: 225, freq: 'monthly', day: 13, account: 'a-main', method: 'Pre-authorized debit (PAD)' },
  'bills/plate': { name: 'Plates', group: 'adults', category: 'transport', amount: 120, freq: 'yearly', anchor: '2027-03-10', account: 'a-main', method: 'Debit card' },
  'bills/water': { name: 'Water filter', group: 'pets', category: 'other', amount: 45, freq: 'quarterly', anchor: '2026-12-03', account: 'a-day', method: 'Debit card' },
  'bills/litter': { name: 'Litter', group: 'pets', category: 'litter', amount: 25, freq: 'biweekly', anchor: '2026-10-25', account: 'a-day', method: 'Debit card' },
  'bills/food': { name: 'Pet food', group: 'pets', category: 'petfood', amount: 39, freq: 'monthly', day: 2, account: 'a-day', method: 'Debit card' },
  'bills/vet': { name: 'Vet savings', group: 'pets', category: 'vet', amount: 30, freq: 'monthly', day: 9, account: 'a-main', method: 'Interac e-Transfer', fund: 'fvet', essential: false },
  'bills/diap': { name: 'Diapers', group: 'baby', category: 'diapers', amount: 40, freq: 'biweekly', anchor: '2026-11-30', account: 'a-day', method: 'Debit card' },
  'bills/wipes': { name: 'Wipes', group: 'baby', category: 'diapers', amount: 12, freq: 'monthly', day: 5, account: 'a-day', method: 'Debit card' },
  'bills/old': { name: 'Old gym', group: 'adults', category: 'other', amount: 30, freq: 'monthly', day: 20, account: 'a-main', method: 'Debit card', end: '2026-12-31' },
  'bills/pause': { name: 'Paused', group: 'adults', category: 'other', amount: 9, freq: 'monthly', day: 4, account: 'a-main', active: false },
  'bills/odd': { name: 'Odd name', group: 'adults', amount: '19.5', freq: 'monthly', day: '31', account: 'Main Chequing', method: 'Cheque', example: true },
  'months/2026-10': { paid: { 'rent@2026-10-01': true }, moves: {}, moved: { emergency: 100 } },
  'months/2026-11': { income: { amt: { 'i-pay': 1300 }, otherCredit: 15, ccbToEmergency: '' }, paid: { 'hydro@2026-11-18': true, 'takeout@2026-11-01': true },
    skipped: { takeout: { amount: 50 }, stream: true }, deferred: { net: { date: '2026-12-09', amount: 60 }, phone: '2026-12-01' },
    moves: { 'emer-a-main@2026-11-30': true }, moved: { emergency: 40, child: 0, fvet: 30 }, selfHold: 25 },
  'months/2026-12': { income: { amt: { 'i-odsp': 1300 }, date: { 'i-odsp': '2026-12-30' }, ccbToEmergency: 100 }, skipped: { stream: { amount: 0 } }, selfHold: 99999 },
  'months/2027-02': { income: { cr: { 'c-ccb': 600 } }, deferred: { hydro: { date: '2027-03-03' } } },
  'months/2026-11/expenses/e1': { date: '2026-11-02', amount: 62.4, category: 'groceries', note: 'Store', oneTime: false, created: 1 },
  'months/2026-11/expenses/e2': { date: '2026-11-14', amount: 18, category: 'takeout', note: '', oneTime: false, created: 2 },
  'months/2026-11/expenses/e3': { date: '2026-11-20', amount: 240, category: 'groceries', note: '', oneTime: false, created: 3 },
  'months/2026-11/expenses/e4': { date: '2026-11-20', amount: 30, category: 'pet', note: '', oneTime: false, created: 5 },
  'months/2026-11/expenses/e5': { date: '2026-11-28', amount: 160, category: 'oneoff', note: 'Car seat', oneTime: true, group: 'baby', created: 6 },
  'months/2026-11/expenses/bad': { date: 'soon', amount: 5, category: 'other' },
  'months/2026-11/expenses/s1': { date: '2026-11-06', amount: 42.5, category: 'self', note: '', oneTime: false, created: 8 },
  'months/2026-11/expenses/s2': { date: '2026-11-15', amount: 230, category: 'self', note: '', oneTime: false, created: 9 },
  'months/2026-11/expenses/s3': { date: '2026-11-15', amount: 10, category: 'self', note: 'One-off', oneTime: true, group: 'adults', created: 10 },
  'months/2026-12/expenses/e6': { date: '2026-12-03', amount: 80, category: 'oneoff', note: '', oneTime: true, group: 'household', created: 7 },
};

/* 2. Ontario with a locked month, older-style settings (settings.odsp, income.credits) and per-month overrides */
const legacy = {
  'settings/main': household({ startYm: '2026-09', trackFrom: '2026-09-01', accounts: accountsON.slice(0, 3),
    incomes: [{ id: 'odsp', name: 'ODSP', amount: 1400, sched: { type: 'odsp' }, account: 'a-rent', role: 'rent' },
              { id: 'pay', name: 'Pay', amount: 1100, sched: { type: 'biweekly', anchor: '2026-09-04' }, account: 'a-main', role: 'main' }],
    credits: [{ id: 'ccb', type: 'ccb', amount: 500, protect: true }, { id: 'gst', type: 'cgeb', amount: 80 }],
    odsp: { dis: false, spouse: true, full: false, wrb: true, lag: 0, couple: true, deps: 1 } }),
  'bills/r': { name: 'Rent', category: 'housing', amount: 1500, day: 1, account: 'a-rent' },
  'bills/h': { name: 'Heat', amount: 70, day: 10, account: 'a-main', deferrable: true },
  'bills/s': { name: 'Sub', amount: 12, day: 3, account: 'a-main', essential: false },
  'months/2026-09': { income: { amt: { odsp: 1450, pay: 1150 }, cr: { ccb: 520 } }, closed: true },
  'months/2026-10': { income: { credits: { gst: 90 } }, deferred: { h: '2026-11-06' } },
};
{ // lock September with a snapshot made by the web version itself (filled in by gen.js)
  legacy['months/2026-09'].lockMe = true;
}

/* 3. Quebec: last-resort assistance, the custom rule, a couple */
const qc = {
  'settings/main': household({ startYm: '2026-12', trackFrom: '2026-11-15', province: 'QC',
    accounts: [{ id: 'm', name: 'Desjardins', type: 'chequing', purposes: ['main', 'rent', 'everyday'] }],
    incomes: [{ id: 'sa', name: 'Aide sociale', amount: 900, assist: 'qcsa', sched: { type: 'qc' }, account: 'm', role: 'rent', ded: { work: true, couple: true } },
              { id: 'rb', name: 'Revenu de base', amount: 1300, assist: 'qcrb', sched: { type: 'qc' }, account: 'm', role: 'other', ded: { work: true, flat: 100, rate: 55 } },
              { id: 'w', name: 'Part-time', amount: 260, sched: { type: 'weekly', anchor: '2026-11-06' }, account: 'm', role: 'main', odsp: 'spouse', person: 'Sam' },
              { id: 'w2', name: 'Gig', amount: 300, sched: { type: 'semimonthly' }, account: 'm', role: 'other', odsp: 'full' }],
    credits: [{ id: 'k', type: 'ccb', amount: 700, protect: true }, { id: 'cdb', type: 'cdb', amount: 200 }],
    selfPlan: [{ name: 'Us', amount: 900, per: 'month' }, { name: 'Sam', amount: 25, per: 'week' }] }),
  'bills/loyer': { name: 'Loyer', category: 'housing', amount: 980, day: 1, account: 'm' },
  'bills/hydro': { name: 'Hydro-Québec', amount: 110, freq: 'biweekly', anchor: '2026-12-08', account: 'm' },
  /* negative amounts from imported or synced data count as $0 */
  'bills/neg': { name: 'Bad data', amount: -500, day: 5, account: 'm' },
  'months/2027-01/expenses/neg': { date: '2027-01-06', amount: -200, category: 'other', oneTime: false, created: 1 },
  'months/2027-01/expenses/negone': { date: '2027-01-25', amount: -50, category: 'oneoff', note: 'Bad', oneTime: true, created: 2 },
  'months/2027-01': { income: { amt: { w: -260 } } },
};

/* 4. Nova Scotia: income assistance, supported employment, a payday chosen by slot */
const ns = {
  'settings/main': household({ startYm: '2026-11', trackFrom: '2026-11-01', province: 'NS',
    accounts: [{ id: 'm', name: 'Main', type: 'chequing', purposes: ['main'] }, { id: 'e', name: 'Spend', type: 'prepaid', purposes: ['everyday', 'online'] }],
    incomes: [{ id: 'ia', name: 'Income Assistance', amount: 1100, assist: 'nsia', sched: { type: 'ns', slot: 1 }, account: 'm', role: 'rent', ded: { dis: true, work: true, supported: true, lag: 1 } },
              { id: 'job', name: 'Job', amount: 420, sched: { type: 'biweekly', anchor: '2026-11-13' }, account: 'm', role: 'main', odsp: 'dis', person: 'Jo' },
              { id: 'job2', name: 'Job 2', amount: 90, sched: { type: 'lastbiz' }, account: 'm', role: 'main', odsp: 'spouse', person: 'Pat' },
              { id: 'ow', name: 'OW', amount: 700, assist: 'ow', sched: { type: 'odsp' }, account: 'm', role: 'other', ded: { work: true, dis: true } }],
    selfPlan: [{ name: 'Jo', amount: 12.34, per: 'pay' }],
    credits: [{ id: 'nscb', type: 'nscb', amount: 125, protect: true }, { id: 'alt', type: 'nsaltc', amount: 60, account: 'e' }] }),
  'bills/rent': { name: 'Rent', category: 'housing', amount: 1050, day: 1, account: 'm' },
  'bills/pw': { name: 'Power', amount: 95, day: 20, account: 'e', method: 'Cash' },
  'bills/ph': { name: 'Phone', amount: 35, day: 8, account: 'e', fund: 'emergency' },
};

/* 5. Nothing set up yet */
const empty = { 'settings/main': { startYm: '2026-11', trackFrom: '2026-11-01' } };

/* 6. The Ontario household starting partway through November (a starting month: no savings target or savings moves) */
const onStart = JSON.parse(JSON.stringify(on));
onStart['months/2026-11'] = Object.assign({}, onStart['months/2026-11'] || {}, { starting: true });
onStart['settings/main'].catchUp = [{ id: 'cu1', name: 'Bell (behind)', amount: 180.5, added: '2026-11-14' }, { id: 'cu2', name: 'Landlord', amount: 300, added: '2026-11-14' }];

module.exports = [
  { name: 'ontario', docs: on, todays: ['2026-10-25', '2026-11-16', '2026-12-31', '2027-03-15'] },
  { name: 'legacy-locked', docs: legacy, todays: ['2026-10-05', '2026-11-30'] },
  { name: 'quebec', docs: qc, todays: ['2026-12-01', '2027-01-20'] },
  { name: 'nova-scotia', docs: ns, todays: ['2026-11-03', '2026-12-24'] },
  { name: 'empty', docs: empty, todays: ['2026-11-01'] },
  { name: 'ontario-starting', docs: onStart, todays: ['2026-11-16', '2026-12-31'] },
];
