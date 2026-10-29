#!/usr/bin/env node
'use strict';

// 폴더블 카운터 PC 허브
// - 폰 앱이 같은 Wi-Fi 안에서 이 서버를 UDP 브로드캐스트로 찾습니다.
// - 폰이 접었다 펼칠 때마다 /api/fold 로 알려주면 카운트를 올립니다.
// - OBS / XSplit 은 /overlay 페이지를 브라우저 소스로 추가하면 됩니다.
// - 외부 인터넷으로는 아무것도 보내지 않습니다. 외부 패키지 의존성도 없습니다.

const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const dgram = require('dgram');

const PORT = Number(process.env.PORT || process.argv[2] || 8787);
const DISCOVERY_PORT = 47777;
const DISCOVERY_MAGIC = 'FOLDCOUNTER_DISCOVER';
const DISCOVERY_REPLY = 'FOLDCOUNTER_HUB';
const ONLINE_MS = 15000;

const ROOT = __dirname;
const PUBLIC_DIR = path.join(ROOT, 'public');
const DATA_DIR = path.join(ROOT, 'data');
const DATA_FILE = path.join(DATA_DIR, 'counts.json');
const TEXT_DIR = path.join(DATA_DIR, 'text');

// ---------- 저장 ----------
function load() {
  try {
    const s = JSON.parse(fs.readFileSync(DATA_FILE, 'utf8'));
    if (!s.devices || typeof s.devices !== 'object') s.devices = {};
    if (!Array.isArray(s.order)) s.order = Object.keys(s.devices);
    return s;
  } catch {
    return { devices: {}, order: [] };
  }
}
const state = load();

let saveTimer = null;
function scheduleSave() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => { saveTimer = null; save(); }, 200);
}
function save() {
  try {
    fs.mkdirSync(DATA_DIR, { recursive: true });
    const tmp = DATA_FILE + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(state, null, 2));
    fs.renameSync(tmp, DATA_FILE);
    writeTextFiles();
  } catch (e) {
    console.error('저장 실패:', e.message);
  }
}
function safeName(s) {
  return String(s).replace(/[\\/:*?"<>|]/g, '_').trim() || 'device';
}
// OBS 텍스트 소스(파일에서 읽기)용 텍스트 파일도 함께 씁니다.
function writeTextFiles() {
  fs.mkdirSync(TEXT_DIR, { recursive: true });
  const lines = [];
  for (const d of listDevices()) {
    const line = `${d.displayName} ${d.count}`;
    lines.push(line);
    fs.writeFileSync(path.join(TEXT_DIR, safeName(d.displayName) + '.txt'), line);
    fs.writeFileSync(path.join(TEXT_DIR, safeName(d.displayName) + '.count.txt'), String(d.count));
  }
  fs.writeFileSync(path.join(TEXT_DIR, 'all.txt'), lines.join('\n'));
}

// ---------- 모델 ----------
function view(d) {
  return {
    ...d,
    displayName: d.customName || d.name || d.model || d.id,
    online: Date.now() - (d.lastSeen || 0) < ONLINE_MS,
  };
}
function listDevices() {
  const ids = state.order.filter((id) => state.devices[id]);
  for (const id of Object.keys(state.devices)) if (!ids.includes(id)) ids.push(id);
  state.order = ids;
  return ids.map((id) => view(state.devices[id]));
}
function lanAddresses() {
  const out = [];
  for (const infos of Object.values(os.networkInterfaces())) {
    for (const i of infos || []) {
      if (i.family === 'IPv4' && !i.internal) out.push(i.address);
    }
  }
  return out;
}
function snapshot() {
  return { hub: { port: PORT, addresses: lanAddresses(), version: '0.1.0' }, devices: listDevices() };
}
function clampInt(v, min, max, dflt) {
  const n = Math.floor(Number(v));
  if (!Number.isFinite(n)) return dflt;
  return Math.min(max, Math.max(min, n));
}
function str(v, max) {
  if (v === undefined || v === null) return undefined;
  return String(v).slice(0, max);
}

function upsertRegister(body) {
  const id = str(body.deviceId, 64);
  if (!id) throw new Error('deviceId 가 필요합니다');
  const now = Date.now();
  const d = state.devices[id] || { id, count: 0, firstSeen: now, customName: null };
  d.name = str(body.name, 80) ?? d.name ?? '';
  d.model = str(body.model, 80) ?? d.model ?? '';
  d.manufacturer = str(body.manufacturer, 80) ?? d.manufacturer ?? '';
  d.platform = str(body.platform, 20) ?? d.platform ?? 'android';
  d.osVersion = str(body.osVersion, 40) ?? d.osVersion ?? '';
  d.detector = str(body.detector, 80) ?? d.detector ?? '';
  d.appVersion = str(body.appVersion, 40) ?? d.appVersion ?? '';
  // 폰이 가지고 있던 카운트가 더 크면 그 값을 받아들입니다 (허브 데이터가 날아간 경우 대비).
  const c = clampInt(body.count, 0, 1e9, 0);
  if (c > d.count) d.count = c;
  d.lastSeen = now;
  state.devices[id] = d;
  if (!state.order.includes(id)) state.order.push(id);
  return d;
}

// ---------- SSE ----------
const clients = new Set();
function broadcast() {
  if (clients.size === 0) return;
  const data = `event: state\ndata: ${JSON.stringify(snapshot())}\n\n`;
  for (const res of clients) res.write(data);
}
function changed() {
  scheduleSave();
  broadcast();
}
setInterval(broadcast, 5000).unref(); // 온라인 표시 갱신
setInterval(() => { for (const res of clients) res.write(': ping\n\n'); }, 15000).unref();

// ---------- HTTP 도우미 ----------
const CORS = { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Headers': 'Content-Type', 'Access-Control-Allow-Methods': 'GET,POST,DELETE,OPTIONS' };
function readJson(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', (c) => { body += c; if (body.length > 1e6) req.destroy(); });
    req.on('end', () => { try { resolve(body ? JSON.parse(body) : {}); } catch (e) { reject(new Error('JSON 형식 오류')); } });
    req.on('error', reject);
  });
}
function send(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...CORS });
  res.end(JSON.stringify(obj));
}
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml' };
function serveStatic(res, name) {
  const file = path.join(PUBLIC_DIR, path.basename(name));
  fs.readFile(file, (err, buf) => {
    if (err) return send(res, 404, { error: 'not found' });
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    res.end(buf);
  });
}

// ---------- 라우팅 ----------
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname;
  const m = req.method;
  try {
    if (m === 'OPTIONS') { res.writeHead(204, CORS); return res.end(); }
    if (p === '/') { res.writeHead(302, { Location: '/admin' }); return res.end(); }
    if (p === '/admin') return serveStatic(res, 'admin.html');
    if (p === '/overlay') return serveStatic(res, 'overlay.html');
    if (p === '/logo.png') return serveStatic(res, 'logo.png'); // public/logo.png 가 있으면 자막에 로고 표시

    if (p === '/api/ping') return send(res, 200, { ok: true, hub: 'foldcounter', port: PORT, version: '0.1.0' });
    if (p === '/api/state') return send(res, 200, snapshot());

    if (p === '/api/events') {
      res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store', Connection: 'keep-alive', ...CORS });
      res.write(`event: state\ndata: ${JSON.stringify(snapshot())}\n\n`);
      clients.add(res);
      req.on('close', () => clients.delete(res));
      return;
    }

    if (p === '/api/register' && m === 'POST') {
      const d = upsertRegister(await readJson(req));
      changed();
      return send(res, 200, { device: view(d) });
    }

    if (p === '/api/fold' && m === 'POST') {
      const body = await readJson(req);
      const d = state.devices[str(body.deviceId, 64)];
      if (!d) return send(res, 404, { error: '등록되지 않은 기기입니다. 먼저 register 하세요' });
      const delta = clampInt(body.delta ?? 1, 1, 1000, 1);
      d.count += delta;
      d.lastSeen = Date.now();
      d.lastFoldAt = d.lastSeen;
      changed();
      return send(res, 200, { device: view(d) });
    }

    if (p === '/api/heartbeat' && m === 'POST') {
      const body = await readJson(req);
      const d = state.devices[str(body.deviceId, 64)];
      if (!d) return send(res, 404, { error: '등록되지 않은 기기입니다. 먼저 register 하세요' });
      const wasOnline = view(d).online;
      d.lastSeen = Date.now();
      if (body.detector) d.detector = str(body.detector, 80);
      if (body.posture) d.posture = str(body.posture, 20);
      scheduleSave();
      if (!wasOnline) broadcast();
      return send(res, 200, { device: view(d) });
    }

    const dm = p.match(/^\/api\/device\/([^/]+)(\/reset)?$/);
    if (dm) {
      const id = decodeURIComponent(dm[1]);
      const d = state.devices[id];
      if (!d) return send(res, 404, { error: '없는 기기입니다' });
      if (m === 'GET') return send(res, 200, { device: view(d) });
      if (m === 'DELETE') {
        delete state.devices[id];
        state.order = state.order.filter((x) => x !== id);
        changed();
        return send(res, 200, { ok: true });
      }
      if (m === 'POST' && dm[2]) {
        d.count = 0;
        changed();
        return send(res, 200, { device: view(d) });
      }
      if (m === 'POST') {
        const body = await readJson(req);
        if ('customName' in body) d.customName = body.customName ? str(body.customName, 80) : null;
        if (body.count !== undefined) d.count = clampInt(body.count, 0, 1e9, d.count);
        changed();
        return send(res, 200, { device: view(d) });
      }
    }

    if (p === '/api/reorder' && m === 'POST') {
      const body = await readJson(req);
      const ids = Array.isArray(body.ids) ? body.ids.filter((id) => state.devices[id]) : [];
      state.order = [...ids, ...state.order.filter((id) => !ids.includes(id))];
      changed();
      return send(res, 200, snapshot());
    }

    if (p === '/api/reset-all' && m === 'POST') {
      for (const d of Object.values(state.devices)) d.count = 0;
      changed();
      return send(res, 200, snapshot());
    }

    send(res, 404, { error: 'not found' });
  } catch (e) {
    send(res, 400, { error: String((e && e.message) || e) });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  const addrs = lanAddresses();
  console.log('========================================');
  console.log(' 폴더블 카운터 허브 실행 중  (포트 ' + PORT + ')');
  console.log('========================================');
  console.log(' 관리 페이지 :  http://localhost:' + PORT + '/admin');
  console.log(' 오버레이    :  http://localhost:' + PORT + '/overlay   <- OBS / XSplit 브라우저 소스');
  if (addrs.length) {
    console.log(' 이 PC 의 LAN 주소 (폰에서 수동 연결할 때):');
    for (const a of addrs) console.log('   ' + a + ':' + PORT);
  }
  console.log(' 폰 앱은 같은 Wi-Fi 에서 자동으로 이 허브를 찾습니다. (UDP ' + DISCOVERY_PORT + ')');
  console.log(' 종료: Ctrl + C');
  console.log('');
  save();
});

// ---------- 자동 검색 (UDP) ----------
const udp = dgram.createSocket({ type: 'udp4', reuseAddr: true });
udp.on('message', (msg, rinfo) => {
  if (msg.toString().startsWith(DISCOVERY_MAGIC)) {
    udp.send(`${DISCOVERY_REPLY} ${PORT}`, rinfo.port, rinfo.address);
  }
});
udp.on('error', (e) => console.warn('자동 검색(UDP) 오류:', e.message, '- 폰에서 IP 를 직접 입력하면 됩니다.'));
udp.bind(DISCOVERY_PORT);

process.on('SIGINT', () => { save(); process.exit(0); });
process.on('SIGTERM', () => { save(); process.exit(0); });
