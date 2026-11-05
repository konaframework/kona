var __typeError = (msg) => {
  throw TypeError(msg);
};
var __accessCheck = (obj, member, msg) => member.has(obj) || __typeError("Cannot " + msg);
var __privateAdd = (obj, member, value) => member.has(obj) ? __typeError("Cannot add the same private member more than once") : member instanceof WeakSet ? member.add(obj) : member.set(obj, value);
var __privateMethod = (obj, member, method) => (__accessCheck(obj, member, "access private method"), method);

// src/connector.js
var _Connector_instances, emit_fn, onMessage_fn;
var Connector = class {
  constructor(options = {}) {
    __privateAdd(this, _Connector_instances);
    this.options = {
      key: options.key || "kona-key",
      wsHost: options.wsHost || options.host || "127.0.0.1",
      wsPort: options.wsPort || options.port || 8080,
      wssPort: options.wssPort || options.port || 443,
      forceTLS: options.forceTLS ?? options.scheme === "https",
      authEndpoint: options.authEndpoint || "/broadcasting/auth",
      auth: options.auth || {},
      ...options
    };
    this.socket = null;
    this.socketId = null;
    this.listeners = /* @__PURE__ */ new Map();
    this.subscriptions = /* @__PURE__ */ new Map();
    this.queue = [];
    this.connected = false;
  }
  connect() {
    if (this.socket) return this;
    const scheme = this.options.forceTLS ? "wss" : "ws";
    const port = this.options.forceTLS ? this.options.wssPort : this.options.wsPort;
    const url = `${scheme}://${this.options.wsHost}:${port}/app/${this.options.key}`;
    this.socket = this.options.WebSocket ? new this.options.WebSocket(url) : new WebSocket(url);
    this.socket.addEventListener("message", (event) => __privateMethod(this, _Connector_instances, onMessage_fn).call(this, event.data));
    this.socket.addEventListener("close", () => {
      this.connected = false;
      this.socket = null;
    });
    return this;
  }
  disconnect() {
    this.socket?.close();
    this.socket = null;
    this.connected = false;
    this.socketId = null;
  }
  on(event, handler) {
    if (!this.listeners.has(event)) this.listeners.set(event, /* @__PURE__ */ new Set());
    this.listeners.get(event).add(handler);
    return () => this.listeners.get(event)?.delete(handler);
  }
  send(payload) {
    const raw = typeof payload === "string" ? payload : JSON.stringify(payload);
    if (this.connected && this.socket?.readyState === 1) {
      this.socket.send(raw);
    } else {
      this.queue.push(raw);
    }
  }
  async authorize(channel, socketId) {
    const body = new URLSearchParams({
      socket_id: socketId,
      channel_name: channel
    });
    const headers = {
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
      ...this.options.auth.headers || {}
    };
    const response = await fetch(this.options.authEndpoint, {
      method: "POST",
      headers,
      body,
      credentials: "same-origin"
    });
    if (!response.ok) {
      throw new Error(`Broadcast auth failed (${response.status})`);
    }
    return response.json();
  }
};
_Connector_instances = new WeakSet();
emit_fn = function(event, data) {
  for (const handler of this.listeners.get(event) || []) {
    handler(data);
  }
  for (const handler of this.listeners.get("*") || []) {
    handler({ event, data });
  }
};
onMessage_fn = function(raw) {
  let message;
  try {
    message = JSON.parse(raw);
  } catch {
    return;
  }
  const event = message.event;
  let data = message.data;
  if (typeof data === "string") {
    try {
      data = JSON.parse(data);
    } catch {
    }
  }
  if (event === "pusher:connection_established") {
    this.socketId = data.socket_id;
    this.connected = true;
    while (this.queue.length) {
      this.socket.send(this.queue.shift());
    }
    __privateMethod(this, _Connector_instances, emit_fn).call(this, "connected", data);
    return;
  }
  if (event === "pusher:pong") {
    __privateMethod(this, _Connector_instances, emit_fn).call(this, "pong", data);
    return;
  }
  if (message.channel) {
    __privateMethod(this, _Connector_instances, emit_fn).call(this, `${message.channel}:${event}`, data);
  }
  __privateMethod(this, _Connector_instances, emit_fn).call(this, event, { channel: message.channel, data });
};

// src/channel.js
var Channel = class {
  constructor(echo, name) {
    this.echo = echo;
    this.name = name;
    this.handlers = /* @__PURE__ */ new Map();
  }
  listen(event, callback) {
    const name = event.startsWith(".") ? event.slice(1) : event;
    const key = `${this.name}:${name}`;
    const stop = this.echo.connector.on(key, callback);
    if (!this.handlers.has(event)) this.handlers.set(event, []);
    this.handlers.get(event).push({ callback, stop });
    return this;
  }
  listenForWhisper(event, callback) {
    return this.listen(`client-${event}`, callback);
  }
  stopListening(event, callback) {
    const bucket = this.handlers.get(event) || [];
    for (const entry of bucket.filter((e) => !callback || e.callback === callback)) {
      entry.stop();
    }
    this.handlers.set(
      event,
      bucket.filter((e) => callback && e.callback !== callback)
    );
    return this;
  }
  subscribed(callback) {
    return this.listen("pusher_internal:subscription_succeeded", callback);
  }
  error(callback) {
    return this.listen("pusher:error", callback);
  }
  whisper(eventName, data = {}) {
    this.echo.connector.send({
      event: `client-${eventName}`,
      channel: this.name,
      data
    });
    return this;
  }
  unsubscribe() {
    this.echo.connector.send({
      event: "pusher:unsubscribe",
      data: { channel: this.name }
    });
    for (const bucket of this.handlers.values()) {
      for (const entry of bucket) entry.stop();
    }
    this.handlers.clear();
    this.echo.forget(this.name);
  }
};
var PresenceChannel = class extends Channel {
  here(callback) {
    return this.subscribed((data) => callback(data?.presence?.ids || []));
  }
  joining(callback) {
    return this.listen("pusher_internal:member_added", callback);
  }
  leaving(callback) {
    return this.listen("pusher_internal:member_removed", callback);
  }
};

// src/Echo.js
var _Echo_instances, subscribe_fn, waitForSocketId_fn;
var Echo = class {
  constructor(options = {}) {
    __privateAdd(this, _Echo_instances);
    this.options = options;
    this.connector = new Connector(options);
    this.channels = /* @__PURE__ */ new Map();
    this.connector.connect();
  }
  socketId() {
    return this.connector.socketId;
  }
  channel(name) {
    return __privateMethod(this, _Echo_instances, subscribe_fn).call(this, name, Channel, false);
  }
  private(name) {
    return __privateMethod(this, _Echo_instances, subscribe_fn).call(this, `private-${name}`, Channel, true);
  }
  join(name) {
    return __privateMethod(this, _Echo_instances, subscribe_fn).call(this, `presence-${name}`, PresenceChannel, true);
  }
  leave(name) {
    for (const key of [`${name}`, `private-${name}`, `presence-${name}`]) {
      this.channels.get(key)?.unsubscribe();
    }
  }
  leaveChannel(name) {
    this.channels.get(name)?.unsubscribe();
  }
  disconnect() {
    for (const channel of [...this.channels.values()]) {
      channel.unsubscribe();
    }
    this.connector.disconnect();
  }
  forget(name) {
    this.channels.delete(name);
  }
};
_Echo_instances = new WeakSet();
subscribe_fn = function(name, ChannelType, privateChannel) {
  if (this.channels.has(name)) {
    return this.channels.get(name);
  }
  const channel = new ChannelType(this, name);
  this.channels.set(name, channel);
  const subscribe = async () => {
    const payload = { channel: name };
    if (privateChannel) {
      const socketId = await __privateMethod(this, _Echo_instances, waitForSocketId_fn).call(this);
      const auth = await this.connector.authorize(name, socketId);
      payload.auth = auth.auth;
      if (auth.channel_data) payload.channel_data = auth.channel_data;
    }
    this.connector.send({ event: "pusher:subscribe", data: payload });
  };
  if (this.connector.connected) {
    subscribe().catch(() => {
    });
  } else {
    this.connector.on("connected", () => {
      subscribe().catch(() => {
      });
    });
  }
  return channel;
};
waitForSocketId_fn = function() {
  if (this.connector.socketId) {
    return Promise.resolve(this.connector.socketId);
  }
  return new Promise((resolve) => {
    const stop = this.connector.on("connected", (data) => {
      stop();
      resolve(data.socket_id);
    });
  });
};

// src/index.js
var index_default = Echo;
export {
  Echo,
  index_default as default
};
