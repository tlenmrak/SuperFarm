// ---------- лог/утилиты ----------
const $ = (id) => document.getElementById(id);
const logBox = $('log');
function log(...args) {
  const line = args.map(a => typeof a === 'object' ? JSON.stringify(a) : String(a)).join(' ');
  console.log('[APP]', ...args);
  logBox.textContent += line + '\n';
  logBox.scrollTop = logBox.scrollHeight;
}
function resolveWsUrl() {
  const wsParam = new URLSearchParams(location.search).get('wsUrl');
  if (wsParam) return wsParam;
  if (location.protocol === 'http:' || location.protocol === 'https:') {
    return (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';
  }
  return 'ws://localhost:8080/ws';
}

// ---------- надёжная загрузка janus.es.js ----------
let Janus;
async function loadJanus() {
  // строим корректный относительный URL с учётом возможного context-path
  const localUrl = new URL('./lib/janus.es.js', document.baseURI).href;
  const candidates = [
    localUrl, // локальная копия (рекомендовано)
    'https://cdn.jsdelivr.net/npm/janus-gateway@1.3.2-2/npm/dist/janus.es.js',
    'https://unpkg.com/janus-gateway@1.3.2-2/npm/dist/janus.es.js'
  ];
  let lastErr;
  for (const url of candidates) {
    try {
      const mod = await import(/* @vite-ignore */ url + '?v=' + Date.now());
      // принимаем и именованный, и дефолтный экспорт
      Janus = mod?.Janus ?? mod?.default;
      if (Janus) { log('Janus loaded from', url); return; }
      lastErr = new Error('Module loaded but no Janus export');
    } catch (e) {
      lastErr = e;
    }
  }
  console.error('Failed to load Janus', lastErr);
  throw new Error('janus.js missing');
}
await loadJanus();

// ---------- переменные ----------
let janus = null;
let streamHandle = null;

const startBtn = $('start');
const stopBtn  = $('stop');
const videoEl  = $('remote');
const deviceSelect = $('deviceSelect');
const refreshDevicesBtn = $('refreshDevices');
const deviceMeta = $('deviceMeta');
const actionSelect = $('actionSelect');
const gestureOverlay = $('gestureOverlay');
const coordsOut = $('coordsOut');
let selectedDevice = null;
let deviceWidth = 1179;   // iPhone 15 / 15 Pro logical px
let deviceHeight = 2556;  // default fallback
const signalingUrl = resolveWsUrl();
log('Signaling URL =', signalingUrl);

// ---------- устройства (айфоны) ----------
async function fetchDevices() {
  try {
    const res = await fetch('/api/devices', { cache: 'no-store' });
    if (!res.ok) throw new Error('HTTP ' + res.status);
    const list = await res.json();
    const iphones = (Array.isArray(list) ? list : []).filter(d => {
      const cls = d?.Info?.DeviceClass || d?.DeviceClass;
      const type = d?.Info?.ProductType || d?.ProductType || '';
      return cls === 'iPhone' || String(type).startsWith('iPhone');
    });
    populateDevices(iphones);
  } catch (e) {
    log('Failed to load /api/devices:', e.toString());
    populateDevices([]);
  }
}

function populateDevices(devices) {
  deviceSelect.innerHTML = '';
  devices.forEach(d => {
    const type = d?.Info?.ProductType || '';
    const ver = d?.Info?.ProductVersion || '';
    const name = d?.Info?.DeviceName || 'iPhone';
    const opt = document.createElement('option');
    opt.value = d.UDID || d.UniqueDeviceID || d.id || '';
    opt.textContent = `${name} (${type}) iOS ${ver} — ${String(opt.value).slice(0,8)}…`;
    opt.dataset.productType = type;
    deviceSelect.appendChild(opt);
  });
  if (devices.length) {
    deviceSelect.selectedIndex = 0;
    updateSelectedDevice(devices);
  } else {
    selectedDevice = null;
    deviceMeta.textContent = 'Устройства не найдены';
  }
}

function updateSelectedDevice(devicesCache = null) {
  const udid = deviceSelect.value;
  const list = devicesCache || Array.from(deviceSelect.options).map(o => ({
    UDID: o.value,
    Info: { ProductType: o.dataset.productType }
  }));
  selectedDevice = (list || []).find(d => (d.UDID || d.UniqueDeviceID) === udid) || null;
  const ptype = selectedDevice?.Info?.ProductType || '';
  deviceMeta.textContent = selectedDevice ? `UDID: ${udid} • ${ptype}` : '';
  // Adjust target logical resolution for iPhone 15 family if known
  // iPhone 15/15 Pro: 1179×2556, 15 Plus/15 Pro Max: 1290×2796
  if (ptype === 'iPhone15,4' || ptype === 'iPhone16,1') { deviceWidth = 1179; deviceHeight = 2556; }
  else if (ptype === 'iPhone15,5' || ptype === 'iPhone16,2') { deviceWidth = 1290; deviceHeight = 2796; }
  else { deviceWidth = 1179; deviceHeight = 2556; }
}

refreshDevicesBtn?.addEventListener('click', () => fetchDevices());
deviceSelect?.addEventListener('change', () => updateSelectedDevice());
// initial load
fetchDevices();

// ---------- жесты по экрану ----------
function toDeviceCoords(clientX, clientY) {
  const rect = gestureOverlay.getBoundingClientRect();
  const x = Math.max(0, Math.min(rect.width, clientX - rect.left));
  const y = Math.max(0, Math.min(rect.height, clientY - rect.top));
  const dx = Math.round(x * (deviceWidth / rect.width));
  const dy = Math.round(y * (deviceHeight / rect.height));
  return { x: dx, y: dy };
}

async function sendFingerAction(action, x1, y1, x2, y2, timeMs) {
  if (!selectedDevice) { alert('Сначала выберите устройство'); return; }
  const udid = selectedDevice.UDID || selectedDevice.UniqueDeviceID || deviceSelect.value;
  const payload = { action, x1, y1, x2, y2, time: Math.max(0, Math.round(timeMs||0)) };
  coordsOut.textContent = `${action} (${x1},${y1})→(${x2},${y2}) t=${payload.time}ms`;
  log('FingerAction', { udid, ...payload });
  try {
    const res = await fetch(`/api/devices/${encodeURIComponent(udid)}/finger`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
    });
    if (!res.ok) throw new Error('HTTP ' + res.status);
  } catch (e) {
    log('Finger send failed:', e.toString());
  }
}

let drag = { active: false, x1: 0, y1: 0, t0: 0, x2: 0, y2: 0 };
gestureOverlay?.addEventListener('pointerdown', (e) => {
  gestureOverlay.setPointerCapture(e.pointerId);
  const p = toDeviceCoords(e.clientX, e.clientY);
  drag.active = true; drag.x1 = p.x; drag.y1 = p.y; drag.t0 = Date.now(); drag.x2 = p.x; drag.y2 = p.y;
});
gestureOverlay?.addEventListener('pointermove', (e) => {
  if (!drag.active) return; const p = toDeviceCoords(e.clientX, e.clientY); drag.x2 = p.x; drag.y2 = p.y;
});
gestureOverlay?.addEventListener('pointerup', (e) => {
  if (!drag.active) return; drag.active = false; const t = Date.now() - drag.t0;
  const action = actionSelect?.value || 'tap';
  if (action === 'swipe') {
    sendFingerAction('swipe', drag.x1, drag.y1, drag.x2, drag.y2, Math.max(1, t));
  } else if (action === 'long_press') {
    sendFingerAction('long_press', drag.x1, drag.y1, drag.x1, drag.y1, Math.max(600, t));
  } else {
    sendFingerAction('tap', drag.x1, drag.y1, drag.x1, drag.y1, Math.max(50, Math.min(300, t)));
  }
});
gestureOverlay?.addEventListener('pointercancel', () => { drag.active = false; });

// ---------- старт ----------
startBtn.addEventListener('click', () => {
  startBtn.disabled = true;
  stopBtn.disabled  = false;

  log('Init Janus…');

  Janus.init({
    debug: 'all',
    callback: function () {
      if (!Janus.isWebrtcSupported()) {
        alert('WebRTC не поддерживается этим браузером');
        startBtn.disabled = false;
        stopBtn.disabled = true;
        return;
      }

      janus = new Janus({
        server: signalingUrl,
        iceServers: [
          { urls: 'stun:stun.l.google.com:19302' },
          { urls: 'stun:stun1.l.google.com:19302' },
          { urls: 'stun:stun.cloudflare.com:3478' }
        ],

        success: function () {
          log('Janus session created, attaching plugin…');
          janus.attach({
            plugin: 'janus.plugin.streaming',
            opaqueId: 'stream-' + Janus.randomString(6),

            success: function (ph) {
              streamHandle = ph;
              log('Plugin attached. Sending watch id=1');
              streamHandle.send({ message: { request: 'watch', id: 1 } });
            },

            error: function (err) {
              console.error(err); log('Attach error:', err.toString());
              cleanup();
            },

            onmessage: function (msg, jsep) {
              log('onmessage:', msg);

              const pdata = msg && msg.plugindata && msg.plugindata.data;
              if (pdata && pdata.error) {
                log('STREAMING ERROR:', pdata.error);
                alert('Janus: ' + pdata.error);
                cleanup();
                return;
              }

              if (jsep) {
                log('Got JSEP offer; creating answer…');
                streamHandle.createAnswer({
                  jsep,
                  media: { audioSend: false, videoSend: false, data: false },
                  success: function (answer) {
                    log('Answer created; sending start');
                    streamHandle.send({ message: { request: 'start' }, jsep: answer });
                  },
                  error: function (e) {
                    console.error(e); log('createAnswer error:', e.toString());
                    cleanup();
                  }
                });
              }
            },

            onremotetrack: function (track, mid, on) {
              if (!on) return;
              let ms = videoEl.srcObject;
              if (!ms) { ms = new MediaStream(); videoEl.srcObject = ms; }
              ms.addTrack(track);
              log('Remote track:', track.kind);
            },

            onremotestream: function (stream) {
              videoEl.srcObject = stream;
              log('Remote stream received');
            },

            webrtcState: function (isUp) {
              log('WebRTC state =', isUp ? 'UP' : 'DOWN');
              if (isUp) setTimeout(printSelectedCandidate, 1500);
            },

            iceState: function (state) {
              log('ICE state =', state);
            },

            oncleanup: function () {
              log('Plugin cleanup');
            }
          });
        },

        error: function (err) {
          console.error(err); log('Janus error:', err.toString());
          cleanup();
        },

        destroyed: function () {
          log('Janus destroyed');
          cleanup();
        }
      });
    }
  });
});

// ---------- стоп ----------
stopBtn.addEventListener('click', () => {
  stopBtn.disabled  = true;
  try { streamHandle && streamHandle.hangup && streamHandle.hangup(); } catch {}
  try { streamHandle && streamHandle.detach && streamHandle.detach(); } catch {}
  try { janus && janus.destroy && janus.destroy(); } catch {}
  cleanup();
});

// ---------- уборка ----------
function cleanup() {
  if (videoEl.srcObject) {
    try { videoEl.srcObject.getTracks().forEach(t => t.stop()); } catch {}
    videoEl.srcObject = null;
  }
  streamHandle = null;
  janus = null;
  startBtn.disabled = false;
  stopBtn.disabled  = true;
  log('Cleanup done');
}

function printSelectedCandidate() {
  try {
    const pc = streamHandle?.webrtcStuff?.pc;
    if (!pc) return;
    pc.getStats(null).then(stats => {
      stats.forEach(r => {
        if (r.type === 'candidate-pair' && r.selected) {
          const remote = stats.get(r.remoteCandidateId);
          if (remote) log('ICE remote:', `${remote.address}:${remote.port}`, remote.protocol, remote.candidateType);
        }
      });
    });
  } catch {}
}
