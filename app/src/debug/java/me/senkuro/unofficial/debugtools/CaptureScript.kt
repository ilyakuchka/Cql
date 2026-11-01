package me.senkuro.unofficial.debugtools

/** JS, который подменяет fetch и XMLHttpRequest и передаёт GraphQL-вызовы в Kotlin через SenkuroDebug.log. */
internal object CaptureScript {
    val JS = """
(function () {
  if (window.__senkuroCapture) return;
  window.__senkuroCapture = true;

  function isGql(u) {
    try { return String(u).indexOf('/graphql') !== -1; } catch (e) { return false; }
  }
  function send(o) {
    try { SenkuroDebug.log(JSON.stringify(o)); } catch (e) {}
  }

  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var url = (typeof input === 'string') ? input : (input && input.url);
      var method = (init && init.method) || (input && input.method) || 'GET';
      var promise = origFetch.apply(this, arguments);
      if (isGql(url)) {
        var initBody = init && init.body;
        var bodyPromise;
        if (typeof initBody === 'string') {
          bodyPromise = Promise.resolve(initBody);
        } else if (!initBody && input && typeof input.clone === 'function') {
          try { bodyPromise = input.clone().text().catch(function () { return null; }); }
          catch (e) { bodyPromise = Promise.resolve(null); }
        } else {
          bodyPromise = Promise.resolve(null);
        }
        promise.then(function (res) {
          var copy = res.clone();
          Promise.all([bodyPromise, copy.text().catch(function () { return null; })]).then(function (r) {
            send({ url: String(url), method: method, body: r[0], status: res.status, response: r[1] });
          });
        }, function (err) {
          bodyPromise.then(function (b) {
            send({ url: String(url), method: method, body: b, error: String(err) });
          });
        });
      }
      return promise;
    };
  }

  var xhrOpen = XMLHttpRequest.prototype.open;
  var xhrSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__senkuro = { url: String(url), method: method };
    return xhrOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function (body) {
    var meta = this.__senkuro;
    if (meta && isGql(meta.url)) {
      meta.body = (typeof body === 'string') ? body : null;
      this.addEventListener('loadend', function () {
        meta.status = this.status;
        try {
          meta.response = (this.responseType === '' || this.responseType === 'text') ? this.responseText : null;
        } catch (e) {}
        send(meta);
      });
    }
    return xhrSend.apply(this, arguments);
  };
})();
""".trimIndent()
}
