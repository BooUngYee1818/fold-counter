#!/usr/bin/env node
'use strict';
// OBS 자동 연동 프로그램
// 허브에 폰이 연결되면 OBS 에 "폴더블 카운터" 브라우저 소스를 자동으로 만들고 켭니다.
// 폰이 모두 끊기면 (옵션) 소스를 끕니다. OBS 28 이상의 내장 WebSocket 서버(v5)를 사용합니다.
//
// 준비: OBS → 도구 → WebSocket 서버 설정 → 서버 활성화 (비밀번호가 있으면 --password 로 전달)
// 사용: node obs-bridge.js [--hub http://localhost:8787] [--obs ws://localhost:4455] [--password 비밀번호]
//                          [--scene "장면 이름"] [--name "폴더블 카운터"] [--width 1920] [--height 1080]
//                          [--overlay "http://localhost:8787/overlay?size=72"] [--hide-when-idle]
//
// XSplit 은 공개 자동화 API 가 없어서 수동으로 '웹페이지' 소스에 오버레이 주소를 넣어야 합니다.

const crypto = require('crypto');

const args = process.argv.slice(2);
const opt = {
  hub: 'http://localhost:8787',
  obs: 'ws://localhost:4455',
  password: '',
  scene: '',
  name: '폴더블 카운터',
  width: 1920,
  height: 1080,
  overlay: '',
  hideWhenIdle: false,
};
for (let i = 0; i < args.length; i++) {
  const a = args[i];
  if (a === '--hub') opt.hub = args[++i];
  else if (a === '--obs') opt.obs = args[++i];
  else if (a === '--password') opt.password = args[++i];
  else if (a === '--scene') opt.scene = args[++i];
  else if (a === '--name') opt.name = args[++i];
  else if (a === '--width') opt.width = Number(args[++i]);
  else if (a === '--height') opt.height = Number(args[++i]);
  else if (a === '--overlay') opt.overlay = args[++i];
  else if (a === '--hide-when-idle') opt.hideWhenIdle = true;
}
if (!opt.overlay) opt.overlay = opt.hub + '/overlay';

if (typeof WebSocket === 'undefined') {
  console.error('Node.js 22 이상이 필요합니다 (내장 WebSocket). 현재:', process.version);
  process.exit(1);
}

// ---------- obs-websocket v5 클라이언트 (의존성 없음) ----------
class Obs {
  constructor(url, password) {
    this.url = url;
    this.password = password;
    this.ws = null;
    this.reqId = 0;
    this.pending = new Map();
    this.ready = false;
  }
  connect() {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(this.url, 'obswebsocket.json');
      this.ws = ws;
      ws.onerror = () => reject(new Error('OBS 에 연결할 수 없습니다: ' + this.url));
      ws.onclose = () => { this.ready = false; for (const p of this.pending.values()) p.reject(new Error('OBS 연결 종료')); this.pending.clear(); };
      ws.onmessage = (ev) => {
        const msg = JSON.parse(ev.data);
        if (msg.op === 0) {
          // Hello
          const d = { rpcVersion: 1, eventSubscriptions: 0 };
          if (msg.d.authentication) {
            if (!this.password) return reject(new Error('OBS WebSocket 에 비밀번호가 설정되어 있습니다. --password 로 전달하세요.'));
            const { challenge, salt } = msg.d.authentication;
            const secret = crypto.createHash('sha256').update(this.password + salt).digest('base64');
            d.authentication = crypto.createHash('sha256').update(secret + challenge).digest('base64');
          }
          ws.send(JSON.stringify({ op: 1, d }));
        } else if (msg.op === 2) {
          this.ready = true;
          resolve();
        } else if (msg.op === 7) {
          const p = this.pending.get(msg.d.requestId);
          if (!p) return;
          this.pending.delete(msg.d.requestId);
          if (msg.d.requestStatus.result) p.resolve(msg.d.responseData || {});
          else p.reject(new Error(msg.d.requestType + ': ' + (msg.d.requestStatus.comment || 'code ' + msg.d.requestStatus.code)));
        }
      };
    });
  }
  call(requestType, requestData) {
    return new Promise((resolve, reject) => {
      if (!this.ready) return reject(new Error('OBS 미연결'));
      const requestId = String(++this.reqId);
      this.pending.set(requestId, { resolve, reject });
      this.ws.send(JSON.stringify({ op: 6, d: { requestType, requestId, requestData: requestData || {} } }));
    });
  }
}

async function hubState() {
  const r = await fetch(opt.hub + '/api/state');
  if (!r.ok) throw new Error('허브 응답 오류 ' + r.status);
  return r.json();
}

async function ensureSource(obs) {
  const sceneName = opt.scene || (await obs.call('GetSceneList')).currentProgramSceneName;
  const items = (await obs.call('GetSceneItemList', { sceneName })).sceneItems || [];
  let item = items.find((it) => it.sourceName === opt.name);
  if (!item) {
    const res = await obs.call('CreateInput', {
      sceneName,
      inputName: opt.name,
      inputKind: 'browser_source',
      inputSettings: { url: opt.overlay, width: opt.width, height: opt.height, shutdown: false, restart_when_active: false },
      sceneItemEnabled: true,
    });
    console.log(`OBS 장면 "${sceneName}" 에 브라우저 소스 "${opt.name}" 을 만들었습니다 → ${opt.overlay}`);
    item = { sceneItemId: res.sceneItemId, sourceName: opt.name, sceneItemEnabled: true };
  } else {
    await obs.call('SetInputSettings', { inputName: opt.name, inputSettings: { url: opt.overlay }, overlay: true });
  }
  return { sceneName, sceneItemId: item.sceneItemId, enabled: item.sceneItemEnabled !== false };
}

async function main() {
  const obs = new Obs(opt.obs, opt.password);
  await obs.connect();
  console.log('OBS 연결됨:', opt.obs);
  let src = await ensureSource(obs);
  let lastEnabled = null;
  let lastOnline = null;

  const tick = async () => {
    try {
      const s = await hubState();
      const online = s.devices.filter((d) => d.online).length;
      if (online !== lastOnline) {
        console.log(new Date().toLocaleTimeString('ko-KR', { hour12: false }), '연결된 폰:', online, online ? s.devices.filter((d) => d.online).map((d) => `${d.displayName} ${d.count}`).join(', ') : '');
        lastOnline = online;
      }
      const wantEnabled = online > 0 || !opt.hideWhenIdle;
      if (wantEnabled !== lastEnabled) {
        if (!obs.ready) { await obs.connect(); src = await ensureSource(obs); }
        await obs.call('SetSceneItemEnabled', { sceneName: src.sceneName, sceneItemId: src.sceneItemId, sceneItemEnabled: wantEnabled });
        console.log(wantEnabled ? '오버레이 켬' : '오버레이 끔 (연결된 폰 없음)');
        lastEnabled = wantEnabled;
      }
    } catch (e) {
      console.warn('오류:', e.message);
      lastEnabled = null;
      if (!obs.ready) { try { await obs.connect(); src = await ensureSource(obs); } catch (_) {} }
    }
  };
  await tick();
  setInterval(tick, 3000);
}

main().catch((e) => { console.error(e.message); process.exit(1); });
