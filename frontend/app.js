// ====== динамический импорт Janus (ESM) с фолбэком ======
let Janus;
try {
  ({ Janus } = await import('https://unpkg.com/janus-gateway@1.3.2-2/npm/dist/janus.es.js'));
} catch (e1) {
  try {
    ({ Janus } = await import('https://unpkg.com/janus-gateway@1.3.2/npm/dist/janus.es.js'));
  } catch (e2) {
    ({ Janus } = await import('https://cdn.jsdelivr.net/npm/janus-gateway@1/npm/dist/janus.es.js'));
  }
}

// ====== утилиты и лог ======
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
  return 'ws://localhost:8080/ws'; // если открыли file://
}

// ====== ссылки на объекты ======
let janus = null;
let streamHandle = null;

const startBtn = $('start');
const stopBtn  = $('stop');
const videoEl  = $('remote');

const signalingUrl = resolveWsUrl();
log('Signaling URL =', signalingUrl);

// ====== старт ======
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
        iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],

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

// ====== стоп ======
stopBtn.addEventListener('click', () => {
  stopBtn.disabled  = true;
  try { streamHandle && streamHandle.hangup && streamHandle.hangup(); } catch {}
  try { streamHandle && streamHandle.detach && streamHandle.detach(); } catch {}
  try { janus && janus.destroy && janus.destroy(); } catch {}
  cleanup();
});

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
