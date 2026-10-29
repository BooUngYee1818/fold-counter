#!/usr/bin/env node
'use strict';
// Fake-phone simulator for testing the hub and overlay without a foldable device.
// Usage: node tools/simulate.js [count] [--hub http://localhost:8787] [--interval 4]
//   Registers <count> fake phones and sends a fold every 2..(interval*2) seconds.
// 폴더블 기기가 없을 때 허브와 오버레이를 테스트하는 가짜 폰 시뮬레이터.
// 사용: node tools/simulate.js [기기 수] [--hub http://localhost:8787] [--interval 4]

const args = process.argv.slice(2);
const opt = { count: 2, hub: 'http://localhost:8787', interval: 4 };
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--hub') opt.hub = args[++i];
  else if (args[i] === '--interval') opt.interval = Number(args[++i]) || 4;
  else if (/^\d+$/.test(args[i])) opt.count = Number(args[i]);
}

const CATALOG = [
  { name: 'Galaxy Z Fold7', model: 'SM-F966N', manufacturer: 'samsung' },
  { name: 'Galaxy Z Flip7', model: 'SM-F766N', manufacturer: 'samsung' },
  { name: 'Pixel 10 Pro Fold', model: 'Pixel 10 Pro Fold', manufacturer: 'Google' },
  { name: 'Surface Duo 2', model: 'Surface Duo 2', manufacturer: 'Microsoft' },
  { name: 'motorola razr 60 ultra', model: 'XT2553-1', manufacturer: 'motorola' },
  { name: 'OnePlus Open', model: 'CPH2551', manufacturer: 'OnePlus' },
  { name: 'Galaxy Fold', model: 'SM-F900N', manufacturer: 'samsung' },
  { name: 'HONOR Magic V3', model: 'FCP-N49', manufacturer: 'HONOR' },
];

async function api(path, body) {
  const r = await fetch(opt.hub + path, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  if (!r.ok) throw new Error(path + ' -> HTTP ' + r.status + ' ' + (await r.text()));
  return r.json();
}

async function main() {
  const devices = [];
  for (let i = 0; i < Math.min(opt.count, CATALOG.length); i++) {
    const c = CATALOG[i];
    const d = { deviceId: 'sim-' + (i + 1), ...c, platform: 'simulator', osVersion: '-', detector: 'simulated', appVersion: 'sim', count: 0 };
    const res = await api('/api/register', d);
    console.log('Registered / 등록:', res.device.displayName, 'count', res.device.count);
    devices.push(d);
  }
  console.log(`\nSimulating ${devices.length} device(s) / ${devices.length}개 기기 시뮬레이션 중.  Overlay: ${opt.hub}/overlay  Admin: ${opt.hub}/admin  (Ctrl+C to quit)\n`);

  setInterval(async () => {
    for (const d of devices) {
      try { await api('/api/heartbeat', { deviceId: d.deviceId, posture: 'OPEN' }); } catch (e) { console.warn(e.message); }
    }
  }, 5000);

  for (const d of devices) {
    const tick = async () => {
      try {
        const res = await api('/api/fold', { deviceId: d.deviceId, delta: 1 });
        console.log(new Date().toLocaleTimeString([], { hour12: false }), 'fold / 접었다 펼침:', res.device.displayName, '→', res.device.count);
      } catch (e) { console.warn(e.message); }
      setTimeout(tick, (2 + Math.random() * opt.interval * 2) * 1000);
    };
    setTimeout(tick, 1000 + Math.random() * 2000);
  }
}

main().catch((e) => { console.error('Cannot reach the hub / 허브에 연결할 수 없습니다:', e.message); process.exit(1); });
