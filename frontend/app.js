Janus.init({
  debug: "all",
  callback: function() {
    const janus = new Janus({
      server: "ws://localhost:8080/ws",
      success: function() {
        janus.attach({
          plugin: "janus.plugin.streaming",
          success: function(pluginHandle) {
            window.stream = pluginHandle;
            pluginHandle.send({ message: { request: "watch", id: 1 } });
          },
          onmessage: function(msg, jsep) {
            if (jsep) {
              stream.createAnswer({
                jsep: jsep,
                media: { audioSend: false, videoSend: false },
                success: function(jsep) {
                  stream.send({ message: { request: "start" }, jsep: jsep });
                }
              });
            }
          },
          onremotestream: function(streamObj) {
            Janus.attachMediaStream(document.getElementById('remote'), streamObj);
          }
        });
      }
    });
  }
});
