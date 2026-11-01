package me.senkuro.unofficial.debugtools

/**
 * JS, который подменяет fetch и XMLHttpRequest и передаёт вызовы в Kotlin через SenkuroDebug.log:
 * все GraphQL-запросы и любые не-GET запросы к хостам senkuro/shiruho/senkognito (вход, OAuth и т.п.).
 * Дополнительно ловит вычисление sha256 (persisted queries): так получаем текст запроса по его хешу.
 */
internal object CaptureScript {
    val JS = """
(function () {
  if (window.__senkuroCapture) return;
  window.__senkuroCapture = true;

  function absUrl(u) {
    try { return new URL(String(u), location.href).href; } catch (e) { return String(u); }
  }
  function isGql(u) {
    return String(u).indexOf('/graphql') !== -1;
  }
  function ownHost(u) {
    try { return /senkuro|shiruho|senkognito/i.test(new URL(String(u), location.href).host); } catch (e) { return false; }
  }
  function wanted(u, m) {
    return isGql(u) || (m !== 'GET' && m !== 'HEAD' && m !== 'OPTIONS' && ownHost(u));
  }
  function send(o) {
    try { SenkuroDebug.log(JSON.stringify(o)); } catch (e) {}
  }
  function bodyText(b) {
    if (typeof b === 'string') return b;
    try { if (b && typeof URLSearchParams !== 'undefined' && b instanceof URLSearchParams) return b.toString(); } catch (e) {}
    return null;
  }
  // GET-запросы (persisted queries через query string) превращаем в тело {operationName, variables, extensions}.
  function bodyFromUrl(u) {
    try {
      var p = new URL(String(u), location.href).searchParams;
      if (!p.has('operationName') && !p.has('query') && !p.has('extensions')) return null;
      var o = {};
      p.forEach(function (v, k) {
        if (k === 'variables' || k === 'extensions') { try { o[k] = JSON.parse(v); } catch (e) { o[k] = v; } }
        else { o[k] = v; }
      });
      return JSON.stringify(o);
    } catch (e) { return null; }
  }

  // ---- fetch
  var origFetch = window.fetch;
  if (origFetch) {
    window.fetch = function (input, init) {
      var rawUrl = (typeof input === 'string') ? input : (input && input.url);
      var url = absUrl(rawUrl);
      var method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
      var promise = origFetch.apply(this, arguments);
      if (wanted(url, method)) {
        var initBody = init && init.body;
        var bodyPromise;
        if (bodyText(initBody) !== null) {
          bodyPromise = Promise.resolve(bodyText(initBody));
        } else if (!initBody && input && typeof input.clone === 'function' && method !== 'GET') {
          try { bodyPromise = input.clone().text().catch(function () { return null; }); }
          catch (e) { bodyPromise = Promise.resolve(null); }
        } else {
          bodyPromise = Promise.resolve(method === 'GET' ? bodyFromUrl(url) : null);
        }
        promise.then(function (res) {
          var textPromise;
          try { textPromise = res.clone().text(); }
          catch (e) { textPromise = Promise.resolve(null); }
          Promise.all([bodyPromise, textPromise.catch(function () { return null; })]).then(function (r) {
            send({ url: url, method: method, body: r[0], status: res.status, response: r[1],
                   note: r[1] === null ? 'fetch: тело ответа недоступно' : null });
          });
        }, function (err) {
          bodyPromise.then(function (b) {
            send({ url: url, method: method, body: b, error: String(err) });
          });
        });
      }
      return promise;
    };
  }

  // ---- XMLHttpRequest
  var xhrOpen = XMLHttpRequest.prototype.open;
  var xhrSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__senkuro = { url: absUrl(url), method: String(method || 'GET').toUpperCase() };
    return xhrOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function (body) {
    var meta = this.__senkuro;
    if (meta && wanted(meta.url, meta.method)) {
      meta.body = (bodyText(body) !== null) ? bodyText(body) : (meta.method === 'GET' ? bodyFromUrl(meta.url) : null);
      this.addEventListener('loadend', function () {
        meta.status = this.status;
        try {
          var t = this.responseType;
          if (t === '' || t === 'text') { meta.response = this.responseText; }
          else if (t === 'json') { meta.response = JSON.stringify(this.response); }
          else { meta.note = 'XHR responseType=' + t; }
        } catch (e) { meta.note = 'XHR: не удалось прочитать ответ: ' + e; }
        send(meta);
      });
    }
    return xhrSend.apply(this, arguments);
  };

  // ---- sha256 для persisted queries: связываем хеш с текстом запроса
  try {
    var subtle = window.crypto && window.crypto.subtle;
    if (subtle) {
      var proto = Object.getPrototypeOf(subtle);
      var origDigest = proto.digest;
      proto.digest = function (alg, data) {
        var promise = origDigest.apply(this, arguments);
        try {
          var name = (typeof alg === 'string') ? alg : ((alg && alg.name) || '');
          if (/sha-?256/i.test(name)) {
            var text = new TextDecoder().decode(data);
            if (/^\s*(query|mutation|subscription|fragment)\b/.test(text)) {
              promise.then(function (buf) {
                var hex = Array.prototype.map.call(new Uint8Array(buf), function (b) {
                  return ('0' + b.toString(16)).slice(-2);
                }).join('');
                send({ kind: 'hash', hash: hex, query: text });
              });
            }
          }
        } catch (e) {}
        return promise;
      };
    }
  } catch (e) {}
})();
""".trimIndent()
}
