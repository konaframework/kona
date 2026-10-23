var __defProp = Object.defineProperty;
var __defNormalProp = (obj, key, value) => key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : obj[key] = value;
var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== "symbol" ? key + "" : key, value);

// node_modules/alpinejs/dist/module.esm.js
var flushPending = false;
var flushing = false;
var queue = [];
var lastFlushedIndex = -1;
var queueNeedsSort = false;
var transactionActive = false;
function scheduler(callback) {
  queueJob(callback);
}
function startTransaction() {
  transactionActive = true;
}
function commitTransaction() {
  transactionActive = false;
  queueFlush();
}
function queueJob(job) {
  if (!queue.includes(job)) {
    queue.push(job);
    if (job._x_schedulerPriority !== void 0)
      queueNeedsSort = true;
  }
  queueFlush();
}
function dequeueJob(job) {
  let index2 = queue.indexOf(job);
  if (index2 !== -1 && index2 > lastFlushedIndex)
    queue.splice(index2, 1);
}
function queueFlush() {
  if (!flushing && !flushPending) {
    if (transactionActive)
      return;
    flushPending = true;
    queueMicrotask(flushJobs);
  }
}
function flushJobs() {
  flushPending = false;
  flushing = true;
  for (let i = 0; i < queue.length; i++) {
    if (queueNeedsSort)
      sortPendingJobs(i);
    queue[i]();
    lastFlushedIndex = i;
  }
  queue.length = 0;
  lastFlushedIndex = -1;
  queueNeedsSort = false;
  flushing = false;
}
function sortPendingJobs(start22) {
  let depths = /* @__PURE__ */ new Map();
  let sorted = queue.slice(start22).sort((a, b) => compareJobs(a, b, depths));
  for (let i = 0; i < sorted.length; i++) {
    queue[start22 + i] = sorted[i];
  }
  queueNeedsSort = false;
}
function compareJobs(a, b, depths) {
  if (!isStructural(a))
    return isStructural(b) ? 1 : 0;
  if (!isStructural(b))
    return -1;
  let depthDifference = getElementDepth(a._x_schedulerPriority.el, depths) - getElementDepth(b._x_schedulerPriority.el, depths);
  return depthDifference || a._x_schedulerPriority.order - b._x_schedulerPriority.order;
}
function isStructural(job) {
  return job._x_schedulerPriority !== void 0;
}
function getElementDepth(el, depths) {
  if (depths.has(el))
    return depths.get(el);
  let depth = 0;
  let owner = el;
  while (el) {
    depth++;
    if (el._x_teleportBack) {
      el = el._x_teleportBack;
    } else if (typeof ShadowRoot === "function" && el.parentNode instanceof ShadowRoot) {
      el = el.parentNode.host;
    } else {
      el = el.parentElement;
    }
  }
  depths.set(owner, depth);
  return depth;
}
var reactive;
var effect;
var release;
var raw;
var nextStructuralEffectOrder = 0;
var shouldSchedule = true;
function disableEffectScheduling(callback) {
  shouldSchedule = false;
  callback();
  shouldSchedule = true;
}
function setReactivityEngine(engine) {
  reactive = engine.reactive;
  release = engine.release;
  effect = (callback) => engine.effect(callback, { scheduler: (task) => {
    if (shouldSchedule) {
      scheduler(task);
    } else {
      task();
    }
  } });
  raw = engine.raw;
}
function overrideEffect(override) {
  effect = override;
}
function elementBoundEffect(el) {
  let cleanup = () => {
  };
  let wrappedEffect = (callback, options) => {
    let priority = options?.priority === "structural" ? nextStructuralEffectOrder++ : void 0;
    let effectReference = effect(callback);
    if (priority !== void 0 && effectReference !== void 0) {
      effectReference._x_schedulerPriority = { el, order: priority };
    }
    if (!el._x_effects) {
      el._x_effects = /* @__PURE__ */ new Set();
      el._x_runEffects = () => {
        el._x_effects.forEach((i) => i());
      };
    }
    el._x_effects.add(effectReference);
    cleanup = () => {
      if (effectReference === void 0)
        return;
      el._x_effects.delete(effectReference);
      release(effectReference);
    };
    return effectReference;
  };
  return [wrappedEffect, () => {
    cleanup();
  }];
}
function watch(getter, callback) {
  let firstTime = true;
  let oldValue;
  let oldValueJSON;
  let effectReference = effect(() => {
    let value = getter();
    let newJSON = JSON.stringify(value);
    if (!firstTime) {
      if (typeof value === "object" || value !== oldValue) {
        let previousValue = typeof oldValue === "object" ? JSON.parse(oldValueJSON) : oldValue;
        queueMicrotask(() => {
          callback(value, previousValue);
        });
      }
    }
    oldValue = value;
    oldValueJSON = newJSON;
    firstTime = false;
  });
  return () => {
    dequeueJob(effectReference);
    release(effectReference);
  };
}
async function transaction(callback) {
  startTransaction();
  try {
    await callback();
    await Promise.resolve();
  } finally {
    commitTransaction();
  }
}
var onAttributeAddeds = [];
var onElRemoveds = [];
var onElAddeds = [];
function onElAdded(callback) {
  onElAddeds.push(callback);
}
function onElRemoved(el, callback) {
  if (typeof callback === "function") {
    if (!el._x_cleanups)
      el._x_cleanups = [];
    el._x_cleanups.push(callback);
  } else {
    callback = el;
    onElRemoveds.push(callback);
  }
}
function onAttributesAdded(callback) {
  onAttributeAddeds.push(callback);
}
function onAttributeRemoved(el, name, callback) {
  if (!el._x_attributeCleanups)
    el._x_attributeCleanups = {};
  if (!el._x_attributeCleanups[name])
    el._x_attributeCleanups[name] = [];
  el._x_attributeCleanups[name].push(callback);
}
function cleanupAttributes(el, names) {
  if (!el._x_attributeCleanups)
    return;
  Object.entries(el._x_attributeCleanups).forEach(([name, value]) => {
    if (names === void 0 || names.includes(name)) {
      value.forEach((i) => i());
      delete el._x_attributeCleanups[name];
    }
  });
}
function cleanupElement(el) {
  el._x_effects?.forEach(dequeueJob);
  while (el._x_cleanups?.length)
    el._x_cleanups.pop()();
}
var observer = new MutationObserver(onMutate);
var currentlyObserving = false;
function startObservingMutations() {
  observer.observe(document, { subtree: true, childList: true, attributes: true, attributeOldValue: true });
  currentlyObserving = true;
}
function stopObservingMutations() {
  flushObserver();
  observer.disconnect();
  currentlyObserving = false;
}
var queuedMutations = [];
function flushObserver() {
  let records = observer.takeRecords();
  queuedMutations.push(() => records.length > 0 && onMutate(records));
  let queueLengthWhenTriggered = queuedMutations.length;
  queueMicrotask(() => {
    if (queuedMutations.length === queueLengthWhenTriggered) {
      while (queuedMutations.length > 0)
        queuedMutations.shift()();
    }
  });
}
function flushPendingMutations() {
  while (queuedMutations.length > 0)
    queuedMutations.shift()();
  let records = observer.takeRecords();
  if (records.length > 0)
    onMutate(records);
}
function mutateDom(callback) {
  if (!currentlyObserving)
    return callback();
  stopObservingMutations();
  try {
    return callback();
  } finally {
    startObservingMutations();
  }
}
var isCollecting = false;
var deferredMutations = [];
function deferMutations() {
  isCollecting = true;
}
function flushAndStopDeferringMutations() {
  isCollecting = false;
  onMutate(deferredMutations);
  deferredMutations = [];
}
function onMutate(mutations) {
  if (isCollecting) {
    deferredMutations = deferredMutations.concat(mutations);
    return;
  }
  let addedNodes = [];
  let removedNodes = /* @__PURE__ */ new Set();
  let addedAttributes = /* @__PURE__ */ new Map();
  let removedAttributes = /* @__PURE__ */ new Map();
  for (let i = 0; i < mutations.length; i++) {
    if (mutations[i].target._x_ignoreMutationObserver)
      continue;
    if (mutations[i].type === "childList") {
      mutations[i].removedNodes.forEach((node) => {
        if (node.nodeType !== 1)
          return;
        if (!node._x_marker)
          return;
        removedNodes.add(node);
      });
      mutations[i].addedNodes.forEach((node) => {
        if (node.nodeType !== 1)
          return;
        if (removedNodes.has(node)) {
          removedNodes.delete(node);
          return;
        }
        if (node._x_marker)
          return;
        addedNodes.push(node);
      });
    }
    if (mutations[i].type === "attributes") {
      let el = mutations[i].target;
      let name = mutations[i].attributeName;
      let oldValue = mutations[i].oldValue;
      let add = () => {
        if (!addedAttributes.has(el))
          addedAttributes.set(el, []);
        addedAttributes.get(el).push({ name, value: el.getAttribute(name) });
      };
      let remove2 = () => {
        if (!removedAttributes.has(el))
          removedAttributes.set(el, []);
        removedAttributes.get(el).push(name);
      };
      if (el.hasAttribute(name) && oldValue === null) {
        add();
      } else if (el.hasAttribute(name)) {
        remove2();
        add();
      } else {
        remove2();
      }
    }
  }
  removedAttributes.forEach((attrs, el) => {
    cleanupAttributes(el, attrs);
  });
  addedAttributes.forEach((attrs, el) => {
    onAttributeAddeds.forEach((i) => i(el, attrs));
  });
  for (let node of removedNodes) {
    if (addedNodes.some((i) => i.contains(node)))
      continue;
    onElRemoveds.forEach((i) => i(node));
  }
  for (let node of addedNodes) {
    if (!node.isConnected)
      continue;
    onElAddeds.forEach((i) => i(node));
  }
  addedNodes = null;
  removedNodes = null;
  addedAttributes = null;
  removedAttributes = null;
}
function scope(node) {
  return mergeProxies(closestDataStack(node));
}
function addScopeToNode(node, data2, referenceNode) {
  node._x_dataStack = [data2, ...closestDataStack(referenceNode || node)];
  return () => {
    node._x_dataStack = node._x_dataStack.filter((i) => i !== data2);
  };
}
function closestDataStack(node) {
  if (node._x_dataStack)
    return node._x_dataStack;
  if (typeof ShadowRoot === "function" && node instanceof ShadowRoot) {
    return closestDataStack(node.host);
  }
  if (!node.parentNode) {
    return [];
  }
  return closestDataStack(node.parentNode);
}
function mergeProxies(objects) {
  return new Proxy({ objects }, mergeProxyTrap);
}
function keyInPrototypeChain(obj, key) {
  if (obj === null || obj === Object.prototype)
    return null;
  if (Object.prototype.hasOwnProperty.call(obj, key))
    return obj;
  return keyInPrototypeChain(Object.getPrototypeOf(obj), key);
}
var mergeProxyTrap = {
  ownKeys({ objects }) {
    return Array.from(
      new Set(objects.flatMap((i) => Object.keys(i)))
    );
  },
  has({ objects }, name) {
    if (name == Symbol.unscopables)
      return false;
    return objects.some(
      (obj) => Object.prototype.hasOwnProperty.call(obj, name) || Reflect.has(obj, name)
    );
  },
  get({ objects }, name, thisProxy) {
    if (name == "toJSON")
      return collapseProxies;
    return Reflect.get(
      objects.find(
        (obj) => Reflect.has(obj, name)
      ) || {},
      name,
      thisProxy
    );
  },
  set({ objects }, name, value, thisProxy) {
    let target;
    for (const obj of objects) {
      target = keyInPrototypeChain(obj, name);
      if (target)
        break;
    }
    if (!target)
      target = objects[objects.length - 1];
    const descriptor = Object.getOwnPropertyDescriptor(target, name);
    if (descriptor?.set && descriptor?.get)
      return descriptor.set.call(thisProxy, value) || true;
    return Reflect.set(target, name, value);
  }
};
function collapseProxies() {
  let keys = Reflect.ownKeys(this);
  return keys.reduce((acc, key) => {
    acc[key] = Reflect.get(this, key);
    return acc;
  }, {});
}
function initInterceptors(data2, cleanup = () => {
}) {
  let isObject32 = (val) => typeof val === "object" && !Array.isArray(val) && val !== null;
  let recurse = (obj, basePath = "") => {
    Object.entries(Object.getOwnPropertyDescriptors(obj)).forEach(([key, { value, enumerable }]) => {
      if (enumerable === false || value === void 0)
        return;
      if (typeof value === "object" && value !== null && value.__v_skip)
        return;
      let path = basePath === "" ? key : `${basePath}.${key}`;
      if (typeof value === "object" && value !== null && value._x_interceptor) {
        obj[key] = value.initialize(data2, path, key, cleanup);
      } else {
        if (isObject32(value) && value !== obj && !(value instanceof Element)) {
          recurse(value, path);
        }
      }
    });
  };
  return recurse(data2);
}
function interceptor(callback, mutateObj = () => {
}) {
  let obj = {
    initialValue: void 0,
    _x_interceptor: true,
    initialize(data2, path, key, cleanup) {
      return callback(this.initialValue, () => get(data2, path), (value) => set(data2, path, value), path, key, cleanup);
    }
  };
  mutateObj(obj);
  return (initialValue) => {
    if (typeof initialValue === "object" && initialValue !== null && initialValue._x_interceptor) {
      let initialize = obj.initialize.bind(obj);
      obj.initialize = (data2, path, key, cleanup) => {
        let innerValue = initialValue.initialize(data2, path, key, cleanup);
        obj.initialValue = innerValue;
        return initialize(data2, path, key, cleanup);
      };
    } else {
      obj.initialValue = initialValue;
    }
    return obj;
  };
}
function get(obj, path) {
  return path.split(".").reduce((carry, segment) => carry[segment], obj);
}
function set(obj, path, value) {
  if (typeof path === "string")
    path = path.split(".");
  if (path.length === 1)
    obj[path[0]] = value;
  else if (path.length === 0)
    throw error;
  else {
    if (obj[path[0]])
      return set(obj[path[0]], path.slice(1), value);
    else {
      obj[path[0]] = {};
      return set(obj[path[0]], path.slice(1), value);
    }
  }
}
var magics = {};
function magic(name, callback) {
  magics[name] = callback;
}
function injectMagics(obj, el) {
  let memoizedUtilities = getUtilities(el);
  Object.entries(magics).forEach(([name, callback]) => {
    Object.defineProperty(obj, `$${name}`, {
      get() {
        return callback(el, memoizedUtilities);
      },
      enumerable: false
    });
  });
  return obj;
}
function getUtilities(el) {
  let [utilities, cleanup] = getElementBoundUtilities(el);
  let utils = { interceptor, ...utilities };
  onElRemoved(el, cleanup);
  return utils;
}
function tryCatch(el, expression, callback, ...args) {
  try {
    return callback(...args);
  } catch (e) {
    handleError(e, el, expression);
  }
}
function handleError(...args) {
  return errorHandler(...args);
}
var errorHandler = normalErrorHandler;
function setErrorHandler(handler4) {
  errorHandler = handler4;
}
function normalErrorHandler(error2, el, expression = void 0) {
  error2 = Object.assign(
    error2 ?? { message: "No error message given." },
    { el, expression }
  );
  console.warn(`Alpine Expression Error: ${error2.message}

${expression ? 'Expression: "' + expression + '"\n\n' : ""}`, el);
  setTimeout(() => {
    throw error2;
  }, 0);
}
var shouldAutoEvaluateFunctions = true;
function dontAutoEvaluateFunctions(callback) {
  let cache = shouldAutoEvaluateFunctions;
  shouldAutoEvaluateFunctions = false;
  let result = callback();
  shouldAutoEvaluateFunctions = cache;
  return result;
}
function evaluate(el, expression, extras = {}) {
  let result;
  evaluateLater(el, expression)((value) => result = value, extras);
  return result;
}
function evaluateLater(...args) {
  return theEvaluatorFunction(...args);
}
var theEvaluatorFunction = () => {
};
function setEvaluator(newEvaluator) {
  theEvaluatorFunction = newEvaluator;
}
var theRawEvaluatorFunction;
function setRawEvaluator(newEvaluator) {
  theRawEvaluatorFunction = newEvaluator;
}
function normalEvaluator(el, expression) {
  let overriddenMagics = {};
  injectMagics(overriddenMagics, el);
  let dataStack = [overriddenMagics, ...closestDataStack(el)];
  let evaluator = typeof expression === "function" ? generateEvaluatorFromFunction(dataStack, expression) : generateEvaluatorFromString(dataStack, expression, el);
  return tryCatch.bind(null, el, expression, evaluator);
}
function generateEvaluatorFromFunction(dataStack, func) {
  return (receiver = () => {
  }, { scope: scope2 = {}, params = [], context } = {}) => {
    if (!shouldAutoEvaluateFunctions) {
      runIfTypeOfFunction(receiver, func, mergeProxies([scope2, ...dataStack]), params);
      return;
    }
    let result = func.apply(mergeProxies([scope2, ...dataStack]), params);
    runIfTypeOfFunction(receiver, result);
  };
}
var evaluatorMemo = {};
function generateFunctionFromString(expression, el) {
  if (evaluatorMemo[expression]) {
    return evaluatorMemo[expression];
  }
  let AsyncFunction = Object.getPrototypeOf(async function() {
  }).constructor;
  let rightSideSafeExpression = /^[\n\s]*if.*\(.*\)/.test(expression.trim()) || /^(let|const)\s/.test(expression.trim()) ? `(async()=>{ ${expression} })()` : expression;
  const safeAsyncFunction = () => {
    try {
      let func2 = new AsyncFunction(
        ["__self", "scope"],
        `with (scope) { __self.result = ${rightSideSafeExpression} }; __self.finished = true; return __self.result;`
      );
      Object.defineProperty(func2, "name", {
        value: `[Alpine] ${expression}`
      });
      return func2;
    } catch (error2) {
      handleError(error2, el, expression);
      return Promise.resolve();
    }
  };
  let func = safeAsyncFunction();
  evaluatorMemo[expression] = func;
  return func;
}
function generateEvaluatorFromString(dataStack, expression, el) {
  let func = generateFunctionFromString(expression, el);
  return (receiver = () => {
  }, { scope: scope2 = {}, params = [], context } = {}) => {
    func.result = void 0;
    func.finished = false;
    let completeScope = mergeProxies([scope2, ...dataStack]);
    if (typeof func === "function") {
      let promise = func.call(context, func, completeScope).catch((error2) => handleError(error2, el, expression));
      if (func.finished) {
        runIfTypeOfFunction(receiver, func.result, completeScope, params, el);
        func.result = void 0;
      } else {
        promise.then((result) => {
          runIfTypeOfFunction(receiver, result, completeScope, params, el);
        }).catch((error2) => handleError(error2, el, expression)).finally(() => func.result = void 0);
      }
    }
  };
}
function runIfTypeOfFunction(receiver, value, scope2, params, el) {
  if (shouldAutoEvaluateFunctions && typeof value === "function") {
    let result = value.apply(scope2, params);
    if (result instanceof Promise) {
      result.then((i) => runIfTypeOfFunction(receiver, i, scope2, params)).catch((error2) => handleError(error2, el, value));
    } else {
      receiver(result);
    }
  } else if (typeof value === "object" && value instanceof Promise) {
    value.then((i) => receiver(i));
  } else {
    receiver(value);
  }
}
function evaluateRaw(...args) {
  return theRawEvaluatorFunction(...args);
}
function normalRawEvaluator(el, expression, extras = {}) {
  let overriddenMagics = {};
  injectMagics(overriddenMagics, el);
  let dataStack = [overriddenMagics, ...closestDataStack(el)];
  let scope2 = mergeProxies([extras.scope ?? {}, ...dataStack]);
  let params = extras.params ?? [];
  if (expression.includes("await")) {
    let AsyncFunction = Object.getPrototypeOf(async function() {
    }).constructor;
    let rightSideSafeExpression = /^[\n\s]*if.*\(.*\)/.test(expression.trim()) || /^(let|const)\s/.test(expression.trim()) ? `(async()=>{ ${expression} })()` : expression;
    let func = new AsyncFunction(
      ["scope"],
      `with (scope) { let __result = ${rightSideSafeExpression}; return __result }`
    );
    let result = func.call(extras.context, scope2);
    return result;
  } else {
    let rightSideSafeExpression = /^[\n\s]*if.*\(.*\)/.test(expression.trim()) || /^(let|const)\s/.test(expression.trim()) ? `(()=>{ ${expression} })()` : expression;
    let func = new Function(
      ["scope"],
      `with (scope) { let __result = ${rightSideSafeExpression}; return __result }`
    );
    let result = func.call(extras.context, scope2);
    if (typeof result === "function" && shouldAutoEvaluateFunctions) {
      return result.apply(scope2, params);
    }
    return result;
  }
}
var prefixAsString = "x-";
function prefix(subject = "") {
  return prefixAsString + subject;
}
function setPrefix(newPrefix) {
  prefixAsString = newPrefix;
}
var directiveHandlers = {};
function directive(name, callback) {
  directiveHandlers[name] = callback;
  return {
    before(directive22) {
      if (!directiveHandlers[directive22]) {
        console.warn(String.raw`Cannot find directive \`${directive22}\`. \`${name}\` will use the default order of execution`);
        return;
      }
      const pos = directiveOrder.indexOf(directive22);
      directiveOrder.splice(pos >= 0 ? pos : directiveOrder.indexOf("DEFAULT"), 0, name);
    }
  };
}
function directiveExists(name) {
  return Object.keys(directiveHandlers).includes(name);
}
function directives(el, attributes, originalAttributeOverride) {
  attributes = Array.from(attributes);
  if (el._x_virtualDirectives) {
    let vAttributes = Object.entries(el._x_virtualDirectives).map(([name, value]) => ({ name, value }));
    let staticAttributes = attributesOnly(vAttributes);
    vAttributes = vAttributes.map((attribute) => {
      if (staticAttributes.find((attr) => attr.name === attribute.name)) {
        return {
          name: `x-bind:${attribute.name}`,
          value: `"${attribute.value}"`
        };
      }
      return attribute;
    });
    attributes = attributes.concat(vAttributes);
  }
  let transformedAttributeMap = {};
  let directives2 = attributes.map(toTransformedAttributes((newName, oldName) => transformedAttributeMap[newName] = oldName)).filter(outNonAlpineAttributes).map(toParsedDirectives(transformedAttributeMap, originalAttributeOverride)).sort(byPriority);
  return directives2.map((directive22) => {
    return getDirectiveHandler(el, directive22);
  });
}
function attributesOnly(attributes) {
  return Array.from(attributes).map(toTransformedAttributes()).filter((attr) => !outNonAlpineAttributes(attr));
}
var isDeferringHandlers = false;
var directiveHandlerStacks = /* @__PURE__ */ new Map();
var currentHandlerStackKey = Symbol();
function deferHandlingDirectives(callback) {
  isDeferringHandlers = true;
  let key = Symbol();
  currentHandlerStackKey = key;
  directiveHandlerStacks.set(key, []);
  let flushHandlers = () => {
    while (directiveHandlerStacks.get(key).length)
      directiveHandlerStacks.get(key).shift()();
    directiveHandlerStacks.delete(key);
  };
  let stopDeferring = () => {
    isDeferringHandlers = false;
    flushHandlers();
  };
  callback(flushHandlers);
  stopDeferring();
}
function getElementBoundUtilities(el) {
  let cleanups2 = [];
  let cleanup = (callback) => cleanups2.push(callback);
  let [effect3, cleanupEffect2] = elementBoundEffect(el);
  cleanups2.push(cleanupEffect2);
  let utilities = {
    Alpine: alpine_default,
    effect: effect3,
    cleanup,
    evaluateLater: evaluateLater.bind(evaluateLater, el),
    evaluate: evaluate.bind(evaluate, el)
  };
  let doCleanup = () => cleanups2.forEach((i) => i());
  return [utilities, doCleanup];
}
function getDirectiveHandler(el, directive22) {
  let noop2 = () => {
  };
  let handler4 = directiveHandlers[directive22.type] || noop2;
  let [utilities, cleanup] = getElementBoundUtilities(el);
  onAttributeRemoved(el, directive22.original, cleanup);
  let fullHandler = () => {
    if (el._x_ignore || el._x_ignoreSelf)
      return;
    handler4.inline && handler4.inline(el, directive22, utilities);
    handler4 = handler4.bind(handler4, el, directive22, utilities);
    isDeferringHandlers ? directiveHandlerStacks.get(currentHandlerStackKey).push(handler4) : handler4();
  };
  fullHandler.runCleanups = cleanup;
  return fullHandler;
}
var startingWith = (subject, replacement) => ({ name, value }) => {
  if (name.startsWith(subject))
    name = name.replace(subject, replacement);
  return { name, value };
};
var into = (i) => i;
function toTransformedAttributes(callback = () => {
}) {
  return ({ name, value }) => {
    let { name: newName, value: newValue } = attributeTransformers.reduce((carry, transform) => {
      return transform(carry);
    }, { name, value });
    if (newName !== name)
      callback(newName, name);
    return { name: newName, value: newValue };
  };
}
var attributeTransformers = [];
function mapAttributes(callback) {
  attributeTransformers.push(callback);
}
function outNonAlpineAttributes({ name }) {
  return alpineAttributeRegex().test(name);
}
var alpineAttributeRegex = () => new RegExp(`^${prefixAsString}([^:^.]+)\\b`);
function toParsedDirectives(transformedAttributeMap, originalAttributeOverride) {
  return ({ name, value }) => {
    if (name === value)
      value = "";
    let typeMatch = name.match(alpineAttributeRegex());
    let valueMatch = name.match(/:([a-zA-Z0-9\-_:]+)/);
    let modifiers = name.match(/\.[^.\]]+(?=[^\]]*$)/g) || [];
    let original = originalAttributeOverride || transformedAttributeMap[name] || name;
    return {
      type: typeMatch ? typeMatch[1] : null,
      value: valueMatch ? valueMatch[1] : null,
      modifiers: modifiers.map((i) => i.replace(".", "")),
      expression: value,
      original
    };
  };
}
var DEFAULT = "DEFAULT";
var directiveOrder = [
  "ignore",
  "ref",
  "id",
  "data",
  "anchor",
  "bind",
  "init",
  "for",
  "model",
  "modelable",
  "transition",
  "show",
  "if",
  DEFAULT,
  "teleport"
];
function byPriority(a, b) {
  let typeA = directiveOrder.indexOf(a.type) === -1 ? DEFAULT : a.type;
  let typeB = directiveOrder.indexOf(b.type) === -1 ? DEFAULT : b.type;
  return directiveOrder.indexOf(typeA) - directiveOrder.indexOf(typeB);
}
function walk(el, callback) {
  if (typeof ShadowRoot === "function" && el instanceof ShadowRoot) {
    Array.from(el.children).forEach((el2) => walk(el2, callback));
    return;
  }
  let skip = false;
  callback(el, () => skip = true);
  if (skip)
    return;
  let node = el.firstElementChild;
  while (node) {
    walk(node, callback, false);
    node = node.nextElementSibling;
  }
}
var isCloning = false;
function skipDuringClone(callback, fallback = () => {
}) {
  return (...args) => isCloning ? fallback(...args) : callback(...args);
}
function onlyDuringClone(callback) {
  return (...args) => isCloning && callback(...args);
}
var interceptors = [];
function interceptClone(callback) {
  interceptors.push(callback);
}
function cloneNode(from, to) {
  interceptors.forEach((i) => i(from, to));
  isCloning = true;
  dontRegisterReactiveSideEffects(() => {
    initTree(to, (el, callback) => {
      callback(el, () => {
      });
    });
  });
  isCloning = false;
}
var isCloningLegacy = false;
function clone(oldEl, newEl) {
  if (!newEl._x_dataStack)
    newEl._x_dataStack = oldEl._x_dataStack;
  isCloning = true;
  isCloningLegacy = true;
  dontRegisterReactiveSideEffects(() => {
    cloneTree(newEl);
  });
  isCloning = false;
  isCloningLegacy = false;
}
function cloneTree(el) {
  let hasRunThroughFirstEl = false;
  let shallowWalker = (el2, callback) => {
    walk(el2, (el3, skip) => {
      if (hasRunThroughFirstEl && isRoot(el3))
        return skip();
      hasRunThroughFirstEl = true;
      callback(el3, skip);
    });
  };
  initTree(el, shallowWalker);
}
function dontRegisterReactiveSideEffects(callback) {
  let cache = effect;
  overrideEffect((callback2, el) => {
    let storedEffect = cache(callback2);
    release(storedEffect);
    return () => {
    };
  });
  callback();
  overrideEffect(cache);
}
var activeDefers = 0;
function deferInit(el, promise) {
  let record = el._x_deferInit;
  if (!record) {
    record = el._x_deferInit = {
      pending: 0,
      ownsIgnore: !el._x_ignore,
      queuedAttributes: /* @__PURE__ */ new Map()
    };
    if (record.ownsIgnore)
      el._x_ignore = true;
    activeDefers++;
  }
  record.pending++;
  Promise.resolve(promise).catch((error2) => {
    try {
      handleError(error2, el);
    } catch (error3) {
      setTimeout(() => {
        throw error3;
      }, 0);
    }
  }).then(() => settle(el, record));
}
function settle(el, record) {
  record.pending--;
  if (record.pending > 0)
    return;
  flushPendingMutations();
  if (record.pending > 0)
    return;
  if (el._x_deferInit !== record)
    return;
  delete el._x_deferInit;
  if (record.ownsIgnore)
    delete el._x_ignore;
  activeDefers--;
  if (!el.isConnected)
    return;
  replayQueuedAttributes(record);
  initTree(el);
}
function queueAttributesForDeferredTree(el, attrs) {
  if (activeDefers === 0)
    return false;
  let root = findClosest(el, (i) => i._x_deferInit);
  if (!root)
    return false;
  queueInto(root._x_deferInit, el, attrs.map(({ name }) => name));
  return true;
}
function queueInto(record, el, names) {
  let entry = record.queuedAttributes.get(el);
  if (!entry || entry.marker !== el._x_marker) {
    entry = { marker: el._x_marker, names: /* @__PURE__ */ new Set() };
    record.queuedAttributes.set(el, entry);
  }
  names.forEach((name) => entry.names.add(name));
}
function replayQueuedAttributes(record) {
  record.queuedAttributes.forEach((entry, el) => {
    if (!el.isConnected)
      return;
    if (!el._x_marker)
      return;
    if (el._x_marker !== entry.marker)
      return;
    let suspendedAncestor = findClosest(el, (i) => i._x_deferInit);
    if (suspendedAncestor) {
      queueInto(suspendedAncestor._x_deferInit, el, Array.from(entry.names));
      return;
    }
    let attrs = Array.from(entry.names).filter((name) => el.hasAttribute(name)).map((name) => ({ name, value: el.getAttribute(name) }));
    if (attrs.length === 0)
      return;
    directives(el, attrs).forEach((handle) => handle());
  });
}
interceptClone((from, to) => {
  if (activeDefers === 0)
    return;
  if (!from || from.nodeType !== 1 || !to || to.nodeType !== 1)
    return;
  if (findClosest(from, (i) => i._x_deferInit))
    to._x_ignore = true;
});
function dispatch(el, name, detail = {}, options = {}) {
  return el.dispatchEvent(
    new CustomEvent(name, {
      detail,
      bubbles: true,
      // Allows events to pass the shadow DOM barrier.
      composed: true,
      cancelable: true,
      // Allows overriding the default event options.
      ...options
    })
  );
}
function warn(message, ...args) {
  console.warn(`Alpine Warning: ${message}`, ...args);
}
var started = false;
function start() {
  if (started)
    warn("Alpine has already been initialized on this page. Calling Alpine.start() more than once can cause problems.");
  started = true;
  if (!document.body)
    warn("Unable to initialize. Trying to load Alpine before `<body>` is available. Did you forget to add `defer` in Alpine's `<script>` tag?");
  dispatch(document, "alpine:init");
  dispatch(document, "alpine:initializing");
  startObservingMutations();
  onElAdded((el) => initTree(el, walk));
  onElRemoved((el) => destroyTree(el));
  onAttributesAdded((el, attrs) => {
    if (queueAttributesForDeferredTree(el, attrs))
      return;
    directives(el, attrs).forEach((handle) => handle());
  });
  let outNestedComponents = (el) => !closestRoot(el.parentElement, true);
  Array.from(document.querySelectorAll(allSelectors().join(","))).filter(outNestedComponents).forEach((el) => {
    initTree(el);
  });
  dispatch(document, "alpine:initialized");
  setTimeout(() => {
    warnAboutMissingPlugins();
  });
}
var rootSelectorCallbacks = [];
var initSelectorCallbacks = [];
function rootSelectors() {
  return rootSelectorCallbacks.map((fn) => fn());
}
function allSelectors() {
  return rootSelectorCallbacks.concat(initSelectorCallbacks).map((fn) => fn());
}
function addRootSelector(selectorCallback) {
  rootSelectorCallbacks.push(selectorCallback);
}
function addInitSelector(selectorCallback) {
  initSelectorCallbacks.push(selectorCallback);
}
function closestRoot(el, includeInitSelectors = false) {
  return findClosest(el, (element) => {
    const selectors = includeInitSelectors ? allSelectors() : rootSelectors();
    if (selectors.some((selector) => element.matches(selector)))
      return true;
  });
}
function findClosest(el, callback) {
  if (!el)
    return;
  if (callback(el))
    return el;
  if (el._x_teleportBack)
    return findClosest(el._x_teleportBack, callback);
  if (el.parentNode instanceof ShadowRoot) {
    return findClosest(el.parentNode.host, callback);
  }
  if (!el.parentElement)
    return;
  return findClosest(el.parentElement, callback);
}
function isRoot(el) {
  return rootSelectors().some((selector) => el.matches(selector));
}
var initInterceptors2 = [];
function interceptInit(callback) {
  initInterceptors2.push(callback);
}
var markerDispenser = 1;
function initTree(el, walker = walk, intercept = () => {
}) {
  if (findClosest(el, (i) => i._x_ignore))
    return;
  deferHandlingDirectives(() => {
    walker(el, (el2, skip) => {
      if (el2._x_marker)
        return;
      intercept(el2, skip);
      initInterceptors2.forEach((i) => i(el2, skip));
      directives(el2, el2.attributes).forEach((handle) => handle());
      if (!el2._x_ignore)
        el2._x_marker = markerDispenser++;
      el2._x_ignore && skip();
    });
  });
}
function destroyTree(root, walker = walk) {
  walker(root, (el) => {
    cleanupElement(el);
    cleanupAttributes(el);
    delete el._x_marker;
  });
}
function warnAboutMissingPlugins() {
  let pluginDirectives = [
    ["ui", "dialog", ["[x-dialog], [x-popover]"]],
    ["anchor", "anchor", ["[x-anchor]"]],
    ["sort", "sort", ["[x-sort]"]]
  ];
  pluginDirectives.forEach(([plugin2, directive22, selectors]) => {
    if (directiveExists(directive22))
      return;
    selectors.some((selector) => {
      if (document.querySelector(selector)) {
        warn(`found "${selector}", but missing ${plugin2} plugin`);
        return true;
      }
    });
  });
}
var tickStack = [];
var isHolding = false;
function nextTick(callback = () => {
}) {
  queueMicrotask(() => {
    isHolding || setTimeout(() => {
      releaseNextTicks();
    });
  });
  return new Promise((res) => {
    tickStack.push(() => {
      callback();
      res();
    });
  });
}
function releaseNextTicks() {
  isHolding = false;
  while (tickStack.length)
    tickStack.shift()();
}
function holdNextTicks() {
  isHolding = true;
}
function setClasses(el, value) {
  if (Array.isArray(value)) {
    return setClassesFromString(el, value.join(" "));
  } else if (typeof value === "object" && value !== null) {
    return setClassesFromObject(el, value);
  } else if (typeof value === "function") {
    return setClasses(el, value());
  }
  return setClassesFromString(el, value);
}
function splitClasses(classString) {
  return classString.split(/\s/).filter(Boolean);
}
function setClassesFromString(el, classString) {
  let missingClasses = (classString2) => splitClasses(classString2).filter((i) => !el.classList.contains(i)).filter(Boolean);
  let addClassesAndReturnUndo = (classes) => {
    el.classList.add(...classes);
    return () => {
      el.classList.remove(...classes);
    };
  };
  classString = classString === true ? classString = "" : classString || "";
  return addClassesAndReturnUndo(missingClasses(classString));
}
function setClassesFromObject(el, classObject) {
  let forAdd = Object.entries(classObject).flatMap(([classString, bool]) => bool ? splitClasses(classString) : false).filter(Boolean);
  let forRemove = Object.entries(classObject).flatMap(([classString, bool]) => !bool ? splitClasses(classString) : false).filter(Boolean);
  let added = [];
  let removed = [];
  forRemove.forEach((i) => {
    if (el.classList.contains(i)) {
      el.classList.remove(i);
      removed.push(i);
    }
  });
  forAdd.forEach((i) => {
    if (!el.classList.contains(i)) {
      el.classList.add(i);
      added.push(i);
    }
  });
  return () => {
    removed.forEach((i) => el.classList.add(i));
    added.forEach((i) => el.classList.remove(i));
  };
}
function setStyles(el, value) {
  if (typeof value === "object" && value !== null) {
    return setStylesFromObject(el, value);
  }
  return setStylesFromString(el, value);
}
function setStylesFromObject(el, value) {
  let previousStyles = {};
  Object.entries(value).forEach(([key, value2]) => {
    previousStyles[key] = el.style[key];
    if (!key.startsWith("--")) {
      key = kebabCase(key);
    }
    el.style.setProperty(key, value2);
  });
  setTimeout(() => {
    if (el.style.length === 0) {
      el.removeAttribute("style");
    }
  });
  return () => {
    setStyles(el, previousStyles);
  };
}
function setStylesFromString(el, value) {
  let cache = el.getAttribute("style", value);
  el.setAttribute("style", value);
  return () => {
    el.setAttribute("style", cache || "");
  };
}
function kebabCase(subject) {
  return subject.replace(/([a-z])([A-Z])/g, "$1-$2").toLowerCase();
}
function once(callback, fallback = () => {
}) {
  let called = false;
  return function() {
    if (!called) {
      called = true;
      callback.apply(this, arguments);
    } else {
      fallback.apply(this, arguments);
    }
  };
}
directive("transition", (el, { value, modifiers, expression }, { evaluate: evaluate22 }) => {
  if (typeof expression === "function")
    expression = evaluate22(expression);
  if (expression === false)
    return;
  if (!expression || typeof expression === "boolean") {
    registerTransitionsFromHelper(el, modifiers, value);
  } else {
    registerTransitionsFromClassString(el, expression, value);
  }
});
function registerTransitionsFromClassString(el, classString, stage) {
  registerTransitionObject(el, setClasses, "");
  let directiveStorageMap = {
    "enter": (classes) => {
      el._x_transition.enter.during = classes;
    },
    "enter-start": (classes) => {
      el._x_transition.enter.start = classes;
    },
    "enter-end": (classes) => {
      el._x_transition.enter.end = classes;
    },
    "leave": (classes) => {
      el._x_transition.leave.during = classes;
    },
    "leave-start": (classes) => {
      el._x_transition.leave.start = classes;
    },
    "leave-end": (classes) => {
      el._x_transition.leave.end = classes;
    }
  };
  directiveStorageMap[stage](classString);
}
function registerTransitionsFromHelper(el, modifiers, stage) {
  registerTransitionObject(el, setStyles);
  let doesntSpecify = !modifiers.includes("in") && !modifiers.includes("out") && !stage;
  let transitioningIn = doesntSpecify || modifiers.includes("in") || ["enter"].includes(stage);
  let transitioningOut = doesntSpecify || modifiers.includes("out") || ["leave"].includes(stage);
  if (modifiers.includes("in") && !doesntSpecify) {
    modifiers = modifiers.filter((i, index2) => index2 < modifiers.indexOf("out"));
  }
  if (modifiers.includes("out") && !doesntSpecify) {
    modifiers = modifiers.filter((i, index2) => index2 > modifiers.indexOf("out"));
  }
  let wantsAll = !modifiers.includes("opacity") && !modifiers.includes("scale");
  let wantsOpacity = wantsAll || modifiers.includes("opacity");
  let wantsScale = wantsAll || modifiers.includes("scale");
  let opacityValue = wantsOpacity ? 0 : 1;
  let scaleValue = wantsScale ? modifierValue(modifiers, "scale", 95) / 100 : 1;
  let delay3 = modifierValue(modifiers, "delay", 0) / 1e3;
  let origin = modifierValue(modifiers, "origin", "center");
  let property = "opacity, transform";
  let durationIn = modifierValue(modifiers, "duration", 150) / 1e3;
  let durationOut = modifierValue(modifiers, "duration", 75) / 1e3;
  let easing = `cubic-bezier(0.4, 0.0, 0.2, 1)`;
  if (transitioningIn) {
    el._x_transition.enter.during = {
      transformOrigin: origin,
      transitionDelay: `${delay3}s`,
      transitionProperty: property,
      transitionDuration: `${durationIn}s`,
      transitionTimingFunction: easing
    };
    el._x_transition.enter.start = {
      opacity: opacityValue,
      transform: `scale(${scaleValue})`
    };
    el._x_transition.enter.end = {
      opacity: 1,
      transform: `scale(1)`
    };
  }
  if (transitioningOut) {
    el._x_transition.leave.during = {
      transformOrigin: origin,
      transitionDelay: `${delay3}s`,
      transitionProperty: property,
      transitionDuration: `${durationOut}s`,
      transitionTimingFunction: easing
    };
    el._x_transition.leave.start = {
      opacity: 1,
      transform: `scale(1)`
    };
    el._x_transition.leave.end = {
      opacity: opacityValue,
      transform: `scale(${scaleValue})`
    };
  }
}
function registerTransitionObject(el, setFunction, defaultValue = {}) {
  if (!el._x_transition)
    el._x_transition = {
      enter: { during: defaultValue, start: defaultValue, end: defaultValue },
      leave: { during: defaultValue, start: defaultValue, end: defaultValue },
      in(before = () => {
      }, after = () => {
      }) {
        transition(el, setFunction, {
          during: this.enter.during,
          start: this.enter.start,
          end: this.enter.end
        }, before, after);
      },
      out(before = () => {
      }, after = () => {
      }) {
        transition(el, setFunction, {
          during: this.leave.during,
          start: this.leave.start,
          end: this.leave.end
        }, before, after);
      }
    };
}
window.Element.prototype._x_toggleAndCascadeWithTransitions = function(el, value, show, hide) {
  const nextTick2 = document.visibilityState === "visible" ? requestAnimationFrame : setTimeout;
  let clickAwayCompatibleShow = () => nextTick2(show);
  if (value) {
    if (el._x_transition && (el._x_transition.enter || el._x_transition.leave)) {
      el._x_transition.enter && (Object.entries(el._x_transition.enter.during).length || Object.entries(el._x_transition.enter.start).length || Object.entries(el._x_transition.enter.end).length) ? el._x_transition.in(show) : clickAwayCompatibleShow();
    } else {
      el._x_transition ? el._x_transition.in(show) : clickAwayCompatibleShow();
    }
    return;
  }
  el._x_hidePromise = el._x_transition ? new Promise((resolve, reject) => {
    el._x_transition.out(() => {
    }, () => resolve(hide));
    el._x_transitioning && el._x_transitioning.beforeCancel(() => reject({ isFromCancelledTransition: true }));
  }) : Promise.resolve(hide);
  queueMicrotask(() => {
    let closest2 = closestHide(el);
    if (closest2) {
      if (!closest2._x_hideChildren)
        closest2._x_hideChildren = [];
      closest2._x_hideChildren.push(el);
    } else {
      nextTick2(() => {
        let hideAfterChildren = (el2) => {
          let carry = Promise.all([
            el2._x_hidePromise,
            ...(el2._x_hideChildren || []).map(hideAfterChildren)
          ]).then(([i]) => i?.());
          delete el2._x_hidePromise;
          delete el2._x_hideChildren;
          return carry;
        };
        hideAfterChildren(el).catch((e) => {
          if (!e.isFromCancelledTransition)
            throw e;
        });
      });
    }
  });
};
function closestHide(el) {
  let parent = el.parentNode;
  if (!parent)
    return;
  return parent._x_hidePromise ? parent : closestHide(parent);
}
function transition(el, setFunction, { during, start: start22, end } = {}, before = () => {
}, after = () => {
}) {
  if (el._x_transitioning)
    el._x_transitioning.cancel();
  if (Object.keys(during).length === 0 && Object.keys(start22).length === 0 && Object.keys(end).length === 0) {
    before();
    after();
    return;
  }
  let undoStart, undoDuring, undoEnd;
  performTransition(el, {
    start() {
      undoStart = setFunction(el, start22);
    },
    during() {
      undoDuring = setFunction(el, during);
    },
    before,
    end() {
      undoStart();
      undoEnd = setFunction(el, end);
    },
    after,
    cleanup() {
      undoDuring();
      undoEnd();
    }
  });
}
function performTransition(el, stages) {
  let interrupted, reachedBefore, reachedEnd;
  let finish = once(() => {
    mutateDom(() => {
      interrupted = true;
      if (!reachedBefore)
        stages.before();
      if (!reachedEnd) {
        stages.end();
        releaseNextTicks();
      }
      stages.after();
      if (el.isConnected)
        stages.cleanup();
      delete el._x_transitioning;
    });
  });
  el._x_transitioning = {
    beforeCancels: [],
    beforeCancel(callback) {
      this.beforeCancels.push(callback);
    },
    cancel: once(function() {
      while (this.beforeCancels.length) {
        this.beforeCancels.shift()();
      }
      ;
      finish();
    }),
    finish
  };
  mutateDom(() => {
    stages.start();
    stages.during();
  });
  holdNextTicks();
  requestAnimationFrame(() => {
    if (interrupted)
      return;
    let duration2 = Number(getComputedStyle(el).transitionDuration.replace(/,.*/, "").replace("s", "")) * 1e3;
    let delay3 = Number(getComputedStyle(el).transitionDelay.replace(/,.*/, "").replace("s", "")) * 1e3;
    if (duration2 === 0)
      duration2 = Number(getComputedStyle(el).animationDuration.replace("s", "")) * 1e3;
    mutateDom(() => {
      stages.before();
    });
    reachedBefore = true;
    requestAnimationFrame(() => {
      if (interrupted)
        return;
      mutateDom(() => {
        stages.end();
      });
      releaseNextTicks();
      setTimeout(el._x_transitioning.finish, duration2 + delay3);
      reachedEnd = true;
    });
  });
}
function modifierValue(modifiers, key, fallback) {
  if (modifiers.indexOf(key) === -1)
    return fallback;
  const rawValue = modifiers[modifiers.indexOf(key) + 1];
  if (!rawValue)
    return fallback;
  if (key === "scale") {
    if (isNaN(rawValue))
      return fallback;
  }
  if (key === "duration" || key === "delay") {
    let match = rawValue.match(/([0-9]+)ms/);
    if (match)
      return match[1];
  }
  if (key === "origin") {
    if (["top", "right", "left", "center", "bottom"].includes(modifiers[modifiers.indexOf(key) + 2])) {
      return [rawValue, modifiers[modifiers.indexOf(key) + 2]].join(" ");
    }
  }
  return rawValue;
}
function bind(el, name, value, modifiers = []) {
  if (!el._x_bindings)
    el._x_bindings = reactive({});
  el._x_bindings[name] = value;
  name = modifiers.includes("camel") ? camelCase(name) : name;
  switch (name) {
    case "value":
      bindInputValue(el, value);
      break;
    case "style":
      bindStyles(el, value);
      break;
    case "class":
      bindClasses(el, value);
      break;
    case "selected":
    case "checked":
      bindAttributeAndProperty(el, name, value);
      break;
    default:
      bindAttribute(el, name, value);
      break;
  }
}
function bindInputValue(el, value) {
  if (isRadio(el)) {
    if (el.attributes.value === void 0) {
      el.value = value;
    }
  } else if (isCheckbox(el)) {
    if (Number.isInteger(value)) {
      el.value = value;
    } else if (!Array.isArray(value) && typeof value !== "boolean" && ![null, void 0].includes(value)) {
      el.value = String(value);
    } else {
      if (Array.isArray(value)) {
        el.checked = value.some((val) => checkedAttrLooseCompare(val, el.value));
      } else {
        el.checked = !!value;
      }
    }
  } else if (el.tagName === "SELECT") {
    updateSelect(el, value);
  } else if (el.tagName === "OPTION") {
    bindAttribute(el, "value", value);
  } else {
    if (el.value === value && (typeof value !== "object" || value === null))
      return;
    el.value = value === void 0 ? "" : value;
  }
}
function bindClasses(el, value) {
  if (el._x_undoAddedClasses)
    el._x_undoAddedClasses();
  el._x_undoAddedClasses = setClasses(el, value);
}
function bindStyles(el, value) {
  if (el._x_undoAddedStyles)
    el._x_undoAddedStyles();
  el._x_undoAddedStyles = setStyles(el, value);
}
function bindAttributeAndProperty(el, name, value) {
  bindAttribute(el, name, value);
  setPropertyIfChanged(el, name, value);
}
function bindAttribute(el, name, value) {
  if ([null, void 0, false].includes(value) && attributeShouldntBePreservedIfFalsy(name)) {
    el.removeAttribute(name);
  } else {
    if (isBooleanAttr(name))
      value = name;
    if (isObjectAttr(value))
      value = JSON.stringify(value);
    setIfChanged(el, name, value);
  }
}
function setIfChanged(el, attrName, value) {
  if (el.getAttribute(attrName) != value) {
    el.setAttribute(attrName, value);
  }
}
function setPropertyIfChanged(el, propName, value) {
  if (el[propName] !== value) {
    el[propName] = value;
  }
}
function updateSelect(el, value) {
  const arrayWrappedValue = [].concat(value).map((value2) => {
    return value2 + "";
  });
  Array.from(el.options).forEach((option2) => {
    option2.selected = arrayWrappedValue.includes(option2.value);
  });
}
function camelCase(subject) {
  return subject.toLowerCase().replace(/-(\w)/g, (match, char) => char.toUpperCase());
}
function checkedAttrLooseCompare(valueA, valueB) {
  return valueA == valueB;
}
function safeParseBoolean(rawValue) {
  if ([1, "1", "true", "on", "yes", true].includes(rawValue)) {
    return true;
  }
  if ([0, "0", "false", "off", "no", false].includes(rawValue)) {
    return false;
  }
  return rawValue ? Boolean(rawValue) : null;
}
var booleanAttributes = /* @__PURE__ */ new Set([
  "allowfullscreen",
  "async",
  "autofocus",
  "autoplay",
  "checked",
  "controls",
  "default",
  "defer",
  "disabled",
  "formnovalidate",
  "inert",
  "ismap",
  "itemscope",
  "loop",
  "multiple",
  "muted",
  "nomodule",
  "novalidate",
  "open",
  "playsinline",
  "readonly",
  "required",
  "reversed",
  "selected",
  "shadowrootclonable",
  "shadowrootdelegatesfocus",
  "shadowrootserializable"
]);
function isBooleanAttr(attrName) {
  return booleanAttributes.has(attrName);
}
function attributeShouldntBePreservedIfFalsy(name) {
  return !["aria-pressed", "aria-checked", "aria-expanded", "aria-selected"].includes(name);
}
function isObjectAttr(value) {
  return typeof value === "object" && value !== null;
}
function getBinding(el, name, fallback) {
  if (el._x_bindings && el._x_bindings[name] !== void 0)
    return el._x_bindings[name];
  return getAttributeBinding(el, name, fallback);
}
function extractProp(el, name, fallback, extract = true) {
  if (el._x_bindings && el._x_bindings[name] !== void 0)
    return el._x_bindings[name];
  if (el._x_inlineBindings && el._x_inlineBindings[name] !== void 0) {
    let binding = el._x_inlineBindings[name];
    binding.extract = extract;
    return dontAutoEvaluateFunctions(() => {
      return evaluate(el, binding.expression);
    });
  }
  return getAttributeBinding(el, name, fallback);
}
function getAttributeBinding(el, name, fallback) {
  let attr = el.getAttribute(name);
  if (attr === null)
    return typeof fallback === "function" ? fallback() : fallback;
  if (attr === "")
    return true;
  if (isBooleanAttr(name)) {
    return !![name, "true"].includes(attr);
  }
  return attr;
}
function isCheckbox(el) {
  return el.type === "checkbox" || el.localName === "ui-checkbox" || el.localName === "ui-switch";
}
function isRadio(el) {
  return el.type === "radio" || el.localName === "ui-radio";
}
function debounce(func, wait) {
  let timeout;
  return function() {
    const context = this, args = arguments;
    const later = function() {
      timeout = null;
      func.apply(context, args);
    };
    clearTimeout(timeout);
    timeout = setTimeout(later, wait);
  };
}
function throttle(func, limit) {
  let inThrottle;
  return function() {
    let context = this, args = arguments;
    if (!inThrottle) {
      func.apply(context, args);
      inThrottle = true;
      setTimeout(() => inThrottle = false, limit);
    }
  };
}
function entangle({ get: outerGet, set: outerSet }, { get: innerGet, set: innerSet }) {
  let firstRun = true;
  let outerHash;
  let innerHash;
  let reference = effect(() => {
    let outer = outerGet();
    let inner = innerGet();
    if (firstRun) {
      innerSet(cloneIfObject(outer));
      firstRun = false;
    } else {
      let outerHashLatest = JSON.stringify(outer);
      let innerHashLatest = JSON.stringify(inner);
      if (outerHashLatest !== outerHash) {
        innerSet(cloneIfObject(outer));
      } else if (outerHashLatest !== innerHashLatest) {
        outerSet(cloneIfObject(inner));
      } else {
      }
    }
    outerHash = JSON.stringify(outerGet());
    innerHash = JSON.stringify(innerGet());
  });
  return () => {
    dequeueJob(reference);
    release(reference);
  };
}
function cloneIfObject(value) {
  return typeof value === "object" ? JSON.parse(JSON.stringify(value)) : value;
}
function plugin(callback) {
  let callbacks = Array.isArray(callback) ? callback : [callback];
  callbacks.forEach((i) => i(alpine_default));
}
var stores = {};
var isReactive = false;
function store(name, value) {
  if (!isReactive) {
    stores = reactive(stores);
    isReactive = true;
  }
  if (value === void 0) {
    return stores[name];
  }
  stores[name] = value;
  if (typeof value === "object" && value !== null && value._x_interceptor) {
    stores[name] = value.initialize(stores, name, name, () => {
    });
  } else {
    initInterceptors(stores[name]);
  }
  if (typeof value === "object" && value !== null && value.hasOwnProperty("init") && typeof value.init === "function") {
    stores[name].init();
  }
}
function getStores() {
  return stores;
}
var binds = {};
function bind2(name, bindings) {
  let getBindings = typeof bindings !== "function" ? () => bindings : bindings;
  if (name instanceof Element) {
    return applyBindingsObject(name, getBindings());
  } else {
    binds[name] = getBindings;
  }
  return () => {
  };
}
function injectBindingProviders(obj) {
  Object.entries(binds).forEach(([name, callback]) => {
    Object.defineProperty(obj, name, {
      get() {
        return (...args) => {
          return callback(...args);
        };
      }
    });
  });
  return obj;
}
function applyBindingsObject(el, obj, original) {
  let cleanupRunners = [];
  while (cleanupRunners.length)
    cleanupRunners.pop()();
  let attributes = Object.entries(obj).map(([name, value]) => ({ name, value }));
  let staticAttributes = attributesOnly(attributes);
  attributes = attributes.map((attribute) => {
    if (staticAttributes.find((attr) => attr.name === attribute.name)) {
      return {
        name: `x-bind:${attribute.name}`,
        value: `"${attribute.value}"`
      };
    }
    return attribute;
  });
  directives(el, attributes, original).map((handle) => {
    cleanupRunners.push(handle.runCleanups);
    handle();
  });
  return () => {
    while (cleanupRunners.length)
      cleanupRunners.pop()();
  };
}
var datas = {};
function data(name, callback) {
  datas[name] = callback;
}
function injectDataProviders(obj, context) {
  Object.entries(datas).forEach(([name, callback]) => {
    Object.defineProperty(obj, name, {
      get() {
        return (...args) => {
          return callback.bind(context)(...args);
        };
      },
      enumerable: false
    });
  });
  return obj;
}
var Alpine = {
  get reactive() {
    return reactive;
  },
  get release() {
    return release;
  },
  get effect() {
    return effect;
  },
  get raw() {
    return raw;
  },
  get transaction() {
    return transaction;
  },
  version: "3.17.4",
  flushAndStopDeferringMutations,
  dontAutoEvaluateFunctions,
  disableEffectScheduling,
  startObservingMutations,
  stopObservingMutations,
  setReactivityEngine,
  onAttributeRemoved,
  onAttributesAdded,
  closestDataStack,
  skipDuringClone,
  onlyDuringClone,
  addRootSelector,
  addInitSelector,
  setErrorHandler,
  interceptClone,
  addScopeToNode,
  deferMutations,
  mapAttributes,
  evaluateLater,
  interceptInit,
  initInterceptors,
  injectMagics,
  setEvaluator,
  setRawEvaluator,
  mergeProxies,
  extractProp,
  findClosest,
  onElRemoved,
  closestRoot,
  destroyTree,
  interceptor,
  // INTERNAL: not public API and is subject to change without major release.
  transition,
  // INTERNAL
  setStyles,
  // INTERNAL
  mutateDom,
  deferInit,
  directive,
  entangle,
  throttle,
  debounce,
  evaluate,
  evaluateRaw,
  initTree,
  nextTick,
  prefixed: prefix,
  prefix: setPrefix,
  plugin,
  magic,
  store,
  start,
  clone,
  // INTERNAL
  cloneNode,
  // INTERNAL
  bound: getBinding,
  $data: scope,
  watch,
  walk,
  data,
  bind: bind2
};
var alpine_default = Alpine;
function makeMap(str) {
  const map = /* @__PURE__ */ Object.create(null);
  for (const key of str.split(","))
    map[key] = 1;
  return (val) => val in map;
}
var EMPTY_OBJ = true ? Object.freeze({}) : {};
var EMPTY_ARR = true ? Object.freeze([]) : [];
var extend = Object.assign;
var hasOwnProperty = Object.prototype.hasOwnProperty;
var hasOwn = (val, key) => hasOwnProperty.call(val, key);
var isArray = Array.isArray;
var isMap = (val) => toTypeString(val) === "[object Map]";
var isString = (val) => typeof val === "string";
var isSymbol = (val) => typeof val === "symbol";
var isObject = (val) => val !== null && typeof val === "object";
var objectToString = Object.prototype.toString;
var toTypeString = (value) => objectToString.call(value);
var toRawType = (value) => {
  return toTypeString(value).slice(8, -1);
};
var isIntegerKey = (key) => isString(key) && key !== "NaN" && key[0] !== "-" && "" + parseInt(key, 10) === key;
var cacheStringFunction = (fn) => {
  const cache = /* @__PURE__ */ Object.create(null);
  return (str) => {
    const hit = cache[str];
    return hit || (cache[str] = fn(str));
  };
};
var camelizeRE = /-\w/g;
var camelize = cacheStringFunction(
  (str) => {
    return str.replace(camelizeRE, (c) => c.slice(1).toUpperCase());
  }
);
var hyphenateRE = /\B([A-Z])/g;
var hyphenate = cacheStringFunction(
  (str) => str.replace(hyphenateRE, "-$1").toLowerCase()
);
var capitalize = cacheStringFunction((str) => {
  return str.charAt(0).toUpperCase() + str.slice(1);
});
var toHandlerKey = cacheStringFunction(
  (str) => {
    const s = str ? `on${capitalize(str)}` : ``;
    return s;
  }
);
var hasChanged = (value, oldValue) => !Object.is(value, oldValue);
var specialBooleanAttrs = `itemscope,allowfullscreen,formnovalidate,ismap,nomodule,novalidate,readonly`;
var isBooleanAttr2 = /* @__PURE__ */ makeMap(
  specialBooleanAttrs + `,async,autofocus,autoplay,controls,default,defer,disabled,inert,loop,open,required,reversed,scoped,seamless,checked,muted,multiple,selected`
);
function warn2(msg, ...args) {
  console.warn(`[Vue warn] ${msg}`, ...args);
}
var activeEffectScope;
var activeSub;
var pausedQueueEffects = /* @__PURE__ */ new WeakSet();
var ReactiveEffect = class {
  constructor(fn) {
    this.fn = fn;
    this.deps = void 0;
    this.depsTail = void 0;
    this.flags = 1 | 4;
    this.next = void 0;
    this.cleanup = void 0;
    this.scheduler = void 0;
    if (activeEffectScope) {
      if (activeEffectScope.active) {
        activeEffectScope.effects.push(this);
      } else {
        this.flags &= -2;
      }
    }
  }
  pause() {
    this.flags |= 64;
  }
  resume() {
    if (this.flags & 64) {
      this.flags &= -65;
      if (pausedQueueEffects.has(this)) {
        pausedQueueEffects.delete(this);
        this.trigger();
      }
    }
  }
  /**
   * @internal
   */
  notify() {
    if (this.flags & 2 && !(this.flags & 32)) {
      return;
    }
    if (!(this.flags & 8)) {
      batch(this);
    }
  }
  run() {
    if (!(this.flags & 1)) {
      return this.fn();
    }
    this.flags |= 2;
    cleanupEffect(this);
    prepareDeps(this);
    const prevEffect = activeSub;
    const prevShouldTrack = shouldTrack;
    activeSub = this;
    shouldTrack = true;
    try {
      return this.fn();
    } finally {
      if (activeSub !== this) {
        warn2(
          "Active effect was not restored correctly - this is likely a Vue internal bug."
        );
      }
      cleanupDeps(this);
      activeSub = prevEffect;
      shouldTrack = prevShouldTrack;
      this.flags &= -3;
    }
  }
  stop() {
    if (this.flags & 1) {
      for (let link = this.deps; link; link = link.nextDep) {
        removeSub(link);
      }
      this.deps = this.depsTail = void 0;
      cleanupEffect(this);
      this.onStop && this.onStop();
      this.flags &= -2;
    }
  }
  trigger() {
    if (this.flags & 64) {
      pausedQueueEffects.add(this);
    } else if (this.scheduler) {
      this.scheduler();
    } else {
      this.runIfDirty();
    }
  }
  /**
   * @internal
   */
  runIfDirty() {
    if (isDirty(this)) {
      this.run();
    }
  }
  get dirty() {
    return isDirty(this);
  }
};
var batchDepth = 0;
var batchedSub;
var batchedComputed;
function batch(sub, isComputed = false) {
  sub.flags |= 8;
  if (isComputed) {
    sub.next = batchedComputed;
    batchedComputed = sub;
    return;
  }
  sub.next = batchedSub;
  batchedSub = sub;
}
function startBatch() {
  batchDepth++;
}
function endBatch() {
  if (--batchDepth > 0) {
    return;
  }
  if (batchedComputed) {
    let e = batchedComputed;
    batchedComputed = void 0;
    while (e) {
      const next = e.next;
      e.next = void 0;
      e.flags &= -9;
      e = next;
    }
  }
  let error2;
  while (batchedSub) {
    let e = batchedSub;
    batchedSub = void 0;
    while (e) {
      const next = e.next;
      e.next = void 0;
      e.flags &= -9;
      if (e.flags & 1) {
        try {
          ;
          e.trigger();
        } catch (err) {
          if (!error2)
            error2 = err;
        }
      }
      e = next;
    }
  }
  if (error2)
    throw error2;
}
function prepareDeps(sub) {
  for (let link = sub.deps; link; link = link.nextDep) {
    link.version = -1;
    link.prevActiveLink = link.dep.activeLink;
    link.dep.activeLink = link;
  }
}
function cleanupDeps(sub) {
  let head;
  let tail = sub.depsTail;
  let link = tail;
  while (link) {
    const prev = link.prevDep;
    if (link.version === -1) {
      if (link === tail)
        tail = prev;
      removeSub(link);
      removeDep(link);
    } else {
      head = link;
    }
    link.dep.activeLink = link.prevActiveLink;
    link.prevActiveLink = void 0;
    link = prev;
  }
  sub.deps = head;
  sub.depsTail = tail;
}
function isDirty(sub) {
  for (let link = sub.deps; link; link = link.nextDep) {
    if (link.dep.version !== link.version || link.dep.computed && (refreshComputed(link.dep.computed) || link.dep.version !== link.version)) {
      return true;
    }
  }
  if (sub._dirty) {
    return true;
  }
  return false;
}
function refreshComputed(computed) {
  if (computed.flags & 4 && !(computed.flags & 16)) {
    return;
  }
  computed.flags &= -17;
  if (computed.globalVersion === globalVersion) {
    return;
  }
  computed.globalVersion = globalVersion;
  if (!computed.isSSR && computed.flags & 128 && (!computed.deps && !computed._dirty || !isDirty(computed))) {
    return;
  }
  computed.flags |= 2;
  const dep = computed.dep;
  const prevSub = activeSub;
  const prevShouldTrack = shouldTrack;
  activeSub = computed;
  shouldTrack = true;
  try {
    prepareDeps(computed);
    const value = computed.fn(computed._value);
    if (dep.version === 0 || hasChanged(value, computed._value)) {
      computed.flags |= 128;
      computed._value = value;
      dep.version++;
    }
  } catch (err) {
    dep.version++;
    throw err;
  } finally {
    activeSub = prevSub;
    shouldTrack = prevShouldTrack;
    cleanupDeps(computed);
    computed.flags &= -3;
  }
}
function removeSub(link, soft = false) {
  const { dep, prevSub, nextSub } = link;
  if (prevSub) {
    prevSub.nextSub = nextSub;
    link.prevSub = void 0;
  }
  if (nextSub) {
    nextSub.prevSub = prevSub;
    link.nextSub = void 0;
  }
  if (dep.subsHead === link) {
    dep.subsHead = nextSub;
  }
  if (dep.subs === link) {
    dep.subs = prevSub;
    if (!prevSub && dep.computed) {
      dep.computed.flags &= -5;
      for (let l = dep.computed.deps; l; l = l.nextDep) {
        removeSub(l, true);
      }
    }
  }
  if (!soft && !--dep.sc && dep.map) {
    dep.map.delete(dep.key);
  }
}
function removeDep(link) {
  const { prevDep, nextDep } = link;
  if (prevDep) {
    prevDep.nextDep = nextDep;
    link.prevDep = void 0;
  }
  if (nextDep) {
    nextDep.prevDep = prevDep;
    link.nextDep = void 0;
  }
}
function effect2(fn, options) {
  if (fn.effect instanceof ReactiveEffect) {
    fn = fn.effect.fn;
  }
  const e = new ReactiveEffect(fn);
  if (options) {
    extend(e, options);
  }
  try {
    e.run();
  } catch (err) {
    e.stop();
    throw err;
  }
  const runner = e.run.bind(e);
  runner.effect = e;
  return runner;
}
function stop(runner) {
  runner.effect.stop();
}
var shouldTrack = true;
var trackStack = [];
function pauseTracking() {
  trackStack.push(shouldTrack);
  shouldTrack = false;
}
function resetTracking() {
  const last = trackStack.pop();
  shouldTrack = last === void 0 ? true : last;
}
function cleanupEffect(e) {
  const { cleanup } = e;
  e.cleanup = void 0;
  if (cleanup) {
    const prevSub = activeSub;
    activeSub = void 0;
    try {
      cleanup();
    } finally {
      activeSub = prevSub;
    }
  }
}
var globalVersion = 0;
var Link = class {
  constructor(sub, dep) {
    this.sub = sub;
    this.dep = dep;
    this.version = dep.version;
    this.nextDep = this.prevDep = this.nextSub = this.prevSub = this.prevActiveLink = void 0;
  }
};
var Dep = class {
  // TODO isolatedDeclarations "__v_skip"
  constructor(computed) {
    this.computed = computed;
    this.version = 0;
    this.activeLink = void 0;
    this.subs = void 0;
    this.map = void 0;
    this.key = void 0;
    this.sc = 0;
    this.__v_skip = true;
    if (true) {
      this.subsHead = void 0;
    }
  }
  track(debugInfo) {
    if (!activeSub || !shouldTrack || activeSub === this.computed) {
      return;
    }
    let link = this.activeLink;
    if (link === void 0 || link.sub !== activeSub) {
      link = this.activeLink = new Link(activeSub, this);
      if (!activeSub.deps) {
        activeSub.deps = activeSub.depsTail = link;
      } else {
        link.prevDep = activeSub.depsTail;
        activeSub.depsTail.nextDep = link;
        activeSub.depsTail = link;
      }
      addSub(link);
    } else if (link.version === -1) {
      link.version = this.version;
      if (link.nextDep) {
        const next = link.nextDep;
        next.prevDep = link.prevDep;
        if (link.prevDep) {
          link.prevDep.nextDep = next;
        }
        link.prevDep = activeSub.depsTail;
        link.nextDep = void 0;
        activeSub.depsTail.nextDep = link;
        activeSub.depsTail = link;
        if (activeSub.deps === link) {
          activeSub.deps = next;
        }
      }
    }
    if (activeSub.onTrack) {
      activeSub.onTrack(
        extend(
          {
            effect: activeSub
          },
          debugInfo
        )
      );
    }
    return link;
  }
  trigger(debugInfo) {
    this.version++;
    globalVersion++;
    this.notify(debugInfo);
  }
  notify(debugInfo) {
    startBatch();
    try {
      if (true) {
        for (let head = this.subsHead; head; head = head.nextSub) {
          if (head.sub.onTrigger && !(head.sub.flags & 8)) {
            head.sub.onTrigger(
              extend(
                {
                  effect: head.sub
                },
                debugInfo
              )
            );
          }
        }
      }
      for (let link = this.subs; link; link = link.prevSub) {
        if (link.sub.notify()) {
          ;
          link.sub.dep.notify();
        }
      }
    } finally {
      endBatch();
    }
  }
};
function addSub(link) {
  link.dep.sc++;
  if (link.sub.flags & 4) {
    const computed = link.dep.computed;
    if (computed && !link.dep.subs) {
      computed.flags |= 4 | 16;
      for (let l = computed.deps; l; l = l.nextDep) {
        addSub(l);
      }
    }
    const currentTail = link.dep.subs;
    if (currentTail !== link) {
      link.prevSub = currentTail;
      if (currentTail)
        currentTail.nextSub = link;
    }
    if (link.dep.subsHead === void 0) {
      link.dep.subsHead = link;
    }
    link.dep.subs = link;
  }
}
var targetMap = /* @__PURE__ */ new WeakMap();
var ITERATE_KEY = /* @__PURE__ */ Symbol(
  true ? "Object iterate" : ""
);
var MAP_KEY_ITERATE_KEY = /* @__PURE__ */ Symbol(
  true ? "Map keys iterate" : ""
);
var ARRAY_ITERATE_KEY = /* @__PURE__ */ Symbol(
  true ? "Array iterate" : ""
);
function track(target, type, key) {
  if (shouldTrack && activeSub) {
    let depsMap = targetMap.get(target);
    if (!depsMap) {
      targetMap.set(target, depsMap = /* @__PURE__ */ new Map());
    }
    let dep = depsMap.get(key);
    if (!dep) {
      depsMap.set(key, dep = new Dep());
      dep.map = depsMap;
      dep.key = key;
    }
    if (true) {
      dep.track({
        target,
        type,
        key
      });
    } else {
      dep.track();
    }
  }
}
function trigger(target, type, key, newValue, oldValue, oldTarget) {
  const depsMap = targetMap.get(target);
  if (!depsMap) {
    globalVersion++;
    return;
  }
  const run = (dep) => {
    if (dep) {
      if (true) {
        dep.trigger({
          target,
          type,
          key,
          newValue,
          oldValue,
          oldTarget
        });
      } else {
        dep.trigger();
      }
    }
  };
  startBatch();
  if (type === "clear") {
    depsMap.forEach(run);
  } else {
    const targetIsArray = isArray(target);
    const isArrayIndex = targetIsArray && isIntegerKey(key);
    if (targetIsArray && key === "length") {
      const newLength = Number(newValue);
      depsMap.forEach((dep, key2) => {
        if (key2 === "length" || key2 === ARRAY_ITERATE_KEY || !isSymbol(key2) && key2 >= newLength) {
          run(dep);
        }
      });
    } else {
      if (key !== void 0 || depsMap.has(void 0)) {
        run(depsMap.get(key));
      }
      if (isArrayIndex) {
        run(depsMap.get(ARRAY_ITERATE_KEY));
      }
      switch (type) {
        case "add":
          if (!targetIsArray) {
            run(depsMap.get(ITERATE_KEY));
            if (isMap(target)) {
              run(depsMap.get(MAP_KEY_ITERATE_KEY));
            }
          } else if (isArrayIndex) {
            run(depsMap.get("length"));
          }
          break;
        case "delete":
          if (!targetIsArray) {
            run(depsMap.get(ITERATE_KEY));
            if (isMap(target)) {
              run(depsMap.get(MAP_KEY_ITERATE_KEY));
            }
          }
          break;
        case "set":
          if (isMap(target)) {
            run(depsMap.get(ITERATE_KEY));
          }
          break;
      }
    }
  }
  endBatch();
}
function reactiveReadArray(array) {
  const raw2 = toRaw(array);
  if (raw2 === array)
    return raw2;
  track(raw2, "iterate", ARRAY_ITERATE_KEY);
  return isShallow(array) ? raw2 : raw2.map(toReactive);
}
function shallowReadArray(arr) {
  track(arr = toRaw(arr), "iterate", ARRAY_ITERATE_KEY);
  return arr;
}
function toWrapped(target, item) {
  if (isReadonly(target)) {
    return isReactive2(target) ? toReadonly(toReactive(item)) : toReadonly(item);
  }
  return toReactive(item);
}
var arrayInstrumentations = {
  __proto__: null,
  [Symbol.iterator]() {
    return iterator(this, Symbol.iterator, (item) => toWrapped(this, item));
  },
  concat(...args) {
    return reactiveReadArray(this).concat(
      ...args.map((x) => isArray(x) ? reactiveReadArray(x) : x)
    );
  },
  entries() {
    return iterator(this, "entries", (value) => {
      value[1] = toWrapped(this, value[1]);
      return value;
    });
  },
  every(fn, thisArg) {
    return apply(this, "every", fn, thisArg, void 0, arguments);
  },
  filter(fn, thisArg) {
    return apply(
      this,
      "filter",
      fn,
      thisArg,
      (v) => v.map((item) => toWrapped(this, item)),
      arguments
    );
  },
  find(fn, thisArg) {
    return apply(
      this,
      "find",
      fn,
      thisArg,
      (item) => toWrapped(this, item),
      arguments
    );
  },
  findIndex(fn, thisArg) {
    return apply(this, "findIndex", fn, thisArg, void 0, arguments);
  },
  findLast(fn, thisArg) {
    return apply(
      this,
      "findLast",
      fn,
      thisArg,
      (item) => toWrapped(this, item),
      arguments
    );
  },
  findLastIndex(fn, thisArg) {
    return apply(this, "findLastIndex", fn, thisArg, void 0, arguments);
  },
  // flat, flatMap could benefit from ARRAY_ITERATE but are not straight-forward to implement
  forEach(fn, thisArg) {
    return apply(this, "forEach", fn, thisArg, void 0, arguments);
  },
  includes(...args) {
    return searchProxy(this, "includes", args);
  },
  indexOf(...args) {
    return searchProxy(this, "indexOf", args);
  },
  join(separator) {
    return reactiveReadArray(this).join(separator);
  },
  // keys() iterator only reads `length`, no optimization required
  lastIndexOf(...args) {
    return searchProxy(this, "lastIndexOf", args);
  },
  map(fn, thisArg) {
    return apply(this, "map", fn, thisArg, void 0, arguments);
  },
  pop() {
    return noTracking(this, "pop");
  },
  push(...args) {
    return noTracking(this, "push", args);
  },
  reduce(fn, ...args) {
    return reduce(this, "reduce", fn, args);
  },
  reduceRight(fn, ...args) {
    return reduce(this, "reduceRight", fn, args);
  },
  shift() {
    return noTracking(this, "shift");
  },
  // slice could use ARRAY_ITERATE but also seems to beg for range tracking
  some(fn, thisArg) {
    return apply(this, "some", fn, thisArg, void 0, arguments);
  },
  splice(...args) {
    return noTracking(this, "splice", args);
  },
  toReversed() {
    return reactiveReadArray(this).toReversed();
  },
  toSorted(comparer) {
    return reactiveReadArray(this).toSorted(comparer);
  },
  toSpliced(...args) {
    return reactiveReadArray(this).toSpliced(...args);
  },
  unshift(...args) {
    return noTracking(this, "unshift", args);
  },
  values() {
    return iterator(this, "values", (item) => toWrapped(this, item));
  }
};
function iterator(self2, method, wrapValue) {
  const arr = shallowReadArray(self2);
  const iter = arr[method]();
  if (arr !== self2 && !isShallow(self2)) {
    iter._next = iter.next;
    iter.next = () => {
      const result = iter._next();
      if (!result.done) {
        result.value = wrapValue(result.value);
      }
      return result;
    };
  }
  return iter;
}
var arrayProto = Array.prototype;
function apply(self2, method, fn, thisArg, wrappedRetFn, args) {
  const arr = shallowReadArray(self2);
  const needsWrap = arr !== self2 && !isShallow(self2);
  const methodFn = arr[method];
  if (methodFn !== arrayProto[method]) {
    const result2 = methodFn.apply(self2, args);
    return needsWrap ? toReactive(result2) : result2;
  }
  let wrappedFn = fn;
  if (arr !== self2) {
    if (needsWrap) {
      wrappedFn = function(item, index2) {
        return fn.call(this, toWrapped(self2, item), index2, self2);
      };
    } else if (fn.length > 2) {
      wrappedFn = function(item, index2) {
        return fn.call(this, item, index2, self2);
      };
    }
  }
  const result = methodFn.call(arr, wrappedFn, thisArg);
  return needsWrap && wrappedRetFn ? wrappedRetFn(result) : result;
}
function reduce(self2, method, fn, args) {
  const arr = shallowReadArray(self2);
  const needsWrap = arr !== self2 && !isShallow(self2);
  let wrappedFn = fn;
  let wrapInitialAccumulator = false;
  if (arr !== self2) {
    if (needsWrap) {
      wrapInitialAccumulator = args.length === 0;
      wrappedFn = function(acc, item, index2) {
        if (wrapInitialAccumulator) {
          wrapInitialAccumulator = false;
          acc = toWrapped(self2, acc);
        }
        return fn.call(this, acc, toWrapped(self2, item), index2, self2);
      };
    } else if (fn.length > 3) {
      wrappedFn = function(acc, item, index2) {
        return fn.call(this, acc, item, index2, self2);
      };
    }
  }
  const result = arr[method](wrappedFn, ...args);
  return wrapInitialAccumulator ? toWrapped(self2, result) : result;
}
function searchProxy(self2, method, args) {
  const arr = toRaw(self2);
  track(arr, "iterate", ARRAY_ITERATE_KEY);
  const res = arr[method](...args);
  if ((res === -1 || res === false) && isProxy(args[0])) {
    args[0] = toRaw(args[0]);
    return arr[method](...args);
  }
  return res;
}
function noTracking(self2, method, args = []) {
  pauseTracking();
  startBatch();
  const res = toRaw(self2)[method].apply(self2, args);
  endBatch();
  resetTracking();
  return res;
}
var isNonTrackableKeys = /* @__PURE__ */ makeMap(`__proto__,__v_isRef,__isVue`);
var builtInSymbols = new Set(
  /* @__PURE__ */ Object.getOwnPropertyNames(Symbol).filter((key) => key !== "arguments" && key !== "caller").map((key) => Symbol[key]).filter(isSymbol)
);
function hasOwnProperty2(key) {
  if (!isSymbol(key))
    key = String(key);
  const obj = toRaw(this);
  track(obj, "has", key);
  return obj.hasOwnProperty(key);
}
var BaseReactiveHandler = class {
  constructor(_isReadonly = false, _isShallow = false) {
    this._isReadonly = _isReadonly;
    this._isShallow = _isShallow;
  }
  get(target, key, receiver) {
    if (key === "__v_skip")
      return target["__v_skip"];
    const isReadonly2 = this._isReadonly, isShallow2 = this._isShallow;
    if (key === "__v_isReactive") {
      return !isReadonly2;
    } else if (key === "__v_isReadonly") {
      return isReadonly2;
    } else if (key === "__v_isShallow") {
      return isShallow2;
    } else if (key === "__v_raw") {
      if (receiver === (isReadonly2 ? isShallow2 ? shallowReadonlyMap : readonlyMap : isShallow2 ? shallowReactiveMap : reactiveMap).get(target) || // receiver is not the reactive proxy, but has the same prototype
      // this means the receiver is a user proxy of the reactive proxy
      Object.getPrototypeOf(target) === Object.getPrototypeOf(receiver)) {
        return target;
      }
      return;
    }
    const targetIsArray = isArray(target);
    if (!isReadonly2) {
      let fn;
      if (targetIsArray && (fn = arrayInstrumentations[key])) {
        return fn;
      }
      if (key === "hasOwnProperty") {
        return hasOwnProperty2;
      }
    }
    const res = Reflect.get(
      target,
      key,
      // if this is a proxy wrapping a ref, return methods using the raw ref
      // as receiver so that we don't have to call `toRaw` on the ref in all
      // its class methods
      isRef(target) ? target : receiver
    );
    if (isSymbol(key) ? builtInSymbols.has(key) : isNonTrackableKeys(key)) {
      return res;
    }
    if (!isReadonly2) {
      track(target, "get", key);
    }
    if (isShallow2) {
      return res;
    }
    if (isRef(res)) {
      const value = targetIsArray && isIntegerKey(key) ? res : res.value;
      return isReadonly2 && isObject(value) ? readonly(value) : value;
    }
    if (isObject(res)) {
      return isReadonly2 ? readonly(res) : reactive2(res);
    }
    return res;
  }
};
var MutableReactiveHandler = class extends BaseReactiveHandler {
  constructor(isShallow2 = false) {
    super(false, isShallow2);
  }
  set(target, key, value, receiver) {
    let oldValue = target[key];
    const isArrayWithIntegerKey = isArray(target) && isIntegerKey(key);
    if (!this._isShallow) {
      const isOldValueReadonly = isReadonly(oldValue);
      if (!isShallow(value) && !isReadonly(value)) {
        oldValue = toRaw(oldValue);
        value = toRaw(value);
      }
      if (!isArrayWithIntegerKey && isRef(oldValue) && !isRef(value)) {
        if (isOldValueReadonly) {
          if (true) {
            warn2(
              `Set operation on key "${String(key)}" failed: target is readonly.`,
              target[key]
            );
          }
          return true;
        } else {
          oldValue.value = value;
          return true;
        }
      }
    }
    const hadKey = isArrayWithIntegerKey ? Number(key) < target.length : hasOwn(target, key);
    const result = Reflect.set(
      target,
      key,
      value,
      isRef(target) ? target : receiver
    );
    if (target === toRaw(receiver) && result) {
      if (!hadKey) {
        trigger(target, "add", key, value);
      } else if (hasChanged(value, oldValue)) {
        trigger(target, "set", key, value, oldValue);
      }
    }
    return result;
  }
  deleteProperty(target, key) {
    const hadKey = hasOwn(target, key);
    const oldValue = target[key];
    const result = Reflect.deleteProperty(target, key);
    if (result && hadKey) {
      trigger(target, "delete", key, void 0, oldValue);
    }
    return result;
  }
  has(target, key) {
    const result = Reflect.has(target, key);
    if (!isSymbol(key) || !builtInSymbols.has(key)) {
      track(target, "has", key);
    }
    return result;
  }
  ownKeys(target) {
    track(
      target,
      "iterate",
      isArray(target) ? "length" : ITERATE_KEY
    );
    return Reflect.ownKeys(target);
  }
};
var ReadonlyReactiveHandler = class extends BaseReactiveHandler {
  constructor(isShallow2 = false) {
    super(true, isShallow2);
  }
  set(target, key) {
    if (true) {
      warn2(
        `Set operation on key "${String(key)}" failed: target is readonly.`,
        target
      );
    }
    return true;
  }
  deleteProperty(target, key) {
    if (true) {
      warn2(
        `Delete operation on key "${String(key)}" failed: target is readonly.`,
        target
      );
    }
    return true;
  }
};
var mutableHandlers = /* @__PURE__ */ new MutableReactiveHandler();
var readonlyHandlers = /* @__PURE__ */ new ReadonlyReactiveHandler();
var toShallow = (value) => value;
var getProto = (v) => Reflect.getPrototypeOf(v);
function createIterableMethod(method, isReadonly2, isShallow2) {
  return function(...args) {
    const target = this["__v_raw"];
    const rawTarget = toRaw(target);
    const targetIsMap = isMap(rawTarget);
    const isPair = method === "entries" || method === Symbol.iterator && targetIsMap;
    const isKeyOnly = method === "keys" && targetIsMap;
    const innerIterator = target[method](...args);
    const wrap = isShallow2 ? toShallow : isReadonly2 ? toReadonly : toReactive;
    !isReadonly2 && track(
      rawTarget,
      "iterate",
      isKeyOnly ? MAP_KEY_ITERATE_KEY : ITERATE_KEY
    );
    return extend(
      // inheriting all iterator properties
      Object.create(innerIterator),
      {
        // iterator protocol
        next() {
          const { value, done } = innerIterator.next();
          return done ? { value, done } : {
            value: isPair ? [wrap(value[0]), wrap(value[1])] : wrap(value),
            done
          };
        }
      }
    );
  };
}
function createReadonlyMethod(type) {
  return function(...args) {
    if (true) {
      const key = args[0] ? `on key "${args[0]}" ` : ``;
      warn2(
        `${capitalize(type)} operation ${key}failed: target is readonly.`,
        toRaw(this)
      );
    }
    return type === "delete" ? false : type === "clear" ? void 0 : this;
  };
}
function createInstrumentations(readonly2, shallow) {
  const instrumentations = {
    get(key) {
      const target = this["__v_raw"];
      const rawTarget = toRaw(target);
      const rawKey = toRaw(key);
      if (!readonly2) {
        if (hasChanged(key, rawKey)) {
          track(rawTarget, "get", key);
        }
        track(rawTarget, "get", rawKey);
      }
      const { has } = getProto(rawTarget);
      const wrap = shallow ? toShallow : readonly2 ? toReadonly : toReactive;
      if (has.call(rawTarget, key)) {
        return wrap(target.get(key));
      } else if (has.call(rawTarget, rawKey)) {
        return wrap(target.get(rawKey));
      } else if (target !== rawTarget) {
        target.get(key);
      }
    },
    get size() {
      const target = this["__v_raw"];
      !readonly2 && track(toRaw(target), "iterate", ITERATE_KEY);
      return target.size;
    },
    has(key) {
      const target = this["__v_raw"];
      const rawTarget = toRaw(target);
      const rawKey = toRaw(key);
      if (!readonly2) {
        if (hasChanged(key, rawKey)) {
          track(rawTarget, "has", key);
        }
        track(rawTarget, "has", rawKey);
      }
      return key === rawKey ? target.has(key) : target.has(key) || target.has(rawKey);
    },
    forEach(callback, thisArg) {
      const observed = this;
      const target = observed["__v_raw"];
      const rawTarget = toRaw(target);
      const wrap = shallow ? toShallow : readonly2 ? toReadonly : toReactive;
      !readonly2 && track(rawTarget, "iterate", ITERATE_KEY);
      return target.forEach((value, key) => {
        return callback.call(thisArg, wrap(value), wrap(key), observed);
      });
    }
  };
  extend(
    instrumentations,
    readonly2 ? {
      add: createReadonlyMethod("add"),
      set: createReadonlyMethod("set"),
      delete: createReadonlyMethod("delete"),
      clear: createReadonlyMethod("clear")
    } : {
      add(value) {
        const target = toRaw(this);
        const proto = getProto(target);
        const rawValue = toRaw(value);
        const valueToAdd = !shallow && !isShallow(value) && !isReadonly(value) ? rawValue : value;
        const hadKey = proto.has.call(target, valueToAdd) || hasChanged(value, valueToAdd) && proto.has.call(target, value) || hasChanged(rawValue, valueToAdd) && proto.has.call(target, rawValue);
        if (!hadKey) {
          target.add(valueToAdd);
          trigger(target, "add", valueToAdd, valueToAdd);
        }
        return this;
      },
      set(key, value) {
        if (!shallow && !isShallow(value) && !isReadonly(value)) {
          value = toRaw(value);
        }
        const target = toRaw(this);
        const { has, get: get2 } = getProto(target);
        let hadKey = has.call(target, key);
        if (!hadKey) {
          key = toRaw(key);
          hadKey = has.call(target, key);
        } else if (true) {
          checkIdentityKeys(target, has, key);
        }
        const oldValue = get2.call(target, key);
        target.set(key, value);
        if (!hadKey) {
          trigger(target, "add", key, value);
        } else if (hasChanged(value, oldValue)) {
          trigger(target, "set", key, value, oldValue);
        }
        return this;
      },
      delete(key) {
        const target = toRaw(this);
        const { has, get: get2 } = getProto(target);
        let hadKey = has.call(target, key);
        if (!hadKey) {
          key = toRaw(key);
          hadKey = has.call(target, key);
        } else if (true) {
          checkIdentityKeys(target, has, key);
        }
        const oldValue = get2 ? get2.call(target, key) : void 0;
        const result = target.delete(key);
        if (hadKey) {
          trigger(target, "delete", key, void 0, oldValue);
        }
        return result;
      },
      clear() {
        const target = toRaw(this);
        const hadItems = target.size !== 0;
        const oldTarget = true ? isMap(target) ? new Map(target) : new Set(target) : void 0;
        const result = target.clear();
        if (hadItems) {
          trigger(
            target,
            "clear",
            void 0,
            void 0,
            oldTarget
          );
        }
        return result;
      }
    }
  );
  const iteratorMethods = [
    "keys",
    "values",
    "entries",
    Symbol.iterator
  ];
  iteratorMethods.forEach((method) => {
    instrumentations[method] = createIterableMethod(method, readonly2, shallow);
  });
  return instrumentations;
}
function createInstrumentationGetter(isReadonly2, shallow) {
  const instrumentations = createInstrumentations(isReadonly2, shallow);
  return (target, key, receiver) => {
    if (key === "__v_isReactive") {
      return !isReadonly2;
    } else if (key === "__v_isReadonly") {
      return isReadonly2;
    } else if (key === "__v_raw") {
      return target;
    }
    return Reflect.get(
      hasOwn(instrumentations, key) && key in target ? instrumentations : target,
      key,
      receiver
    );
  };
}
var mutableCollectionHandlers = {
  get: /* @__PURE__ */ createInstrumentationGetter(false, false)
};
var readonlyCollectionHandlers = {
  get: /* @__PURE__ */ createInstrumentationGetter(true, false)
};
function checkIdentityKeys(target, has, key) {
  const rawKey = toRaw(key);
  if (rawKey !== key && has.call(target, rawKey)) {
    const type = toRawType(target);
    warn2(
      `Reactive ${type} contains both the raw and reactive versions of the same object${type === `Map` ? ` as keys` : ``}, which can lead to inconsistencies. Avoid differentiating between the raw and reactive versions of an object and only use the reactive version if possible.`
    );
  }
}
var reactiveMap = /* @__PURE__ */ new WeakMap();
var shallowReactiveMap = /* @__PURE__ */ new WeakMap();
var readonlyMap = /* @__PURE__ */ new WeakMap();
var shallowReadonlyMap = /* @__PURE__ */ new WeakMap();
function targetTypeMap(rawType) {
  switch (rawType) {
    case "Object":
    case "Array":
      return 1;
    case "Map":
    case "Set":
    case "WeakMap":
    case "WeakSet":
      return 2;
    default:
      return 0;
  }
}
function reactive2(target) {
  if (/* @__PURE__ */ isReadonly(target)) {
    return target;
  }
  return createReactiveObject(
    target,
    false,
    mutableHandlers,
    mutableCollectionHandlers,
    reactiveMap
  );
}
function readonly(target) {
  return createReactiveObject(
    target,
    true,
    readonlyHandlers,
    readonlyCollectionHandlers,
    readonlyMap
  );
}
function createReactiveObject(target, isReadonly2, baseHandlers, collectionHandlers, proxyMap) {
  if (!isObject(target)) {
    if (true) {
      warn2(
        `value cannot be made ${isReadonly2 ? "readonly" : "reactive"}: ${String(
          target
        )}`
      );
    }
    return target;
  }
  if (target["__v_raw"] && !(isReadonly2 && target["__v_isReactive"])) {
    return target;
  }
  if (target["__v_skip"] || !Object.isExtensible(target)) {
    return target;
  }
  const existingProxy = proxyMap.get(target);
  if (existingProxy) {
    return existingProxy;
  }
  const targetType = targetTypeMap(toRawType(target));
  if (targetType === 0) {
    return target;
  }
  const proxy = new Proxy(
    target,
    targetType === 2 ? collectionHandlers : baseHandlers
  );
  proxyMap.set(target, proxy);
  return proxy;
}
function isReactive2(value) {
  if (/* @__PURE__ */ isReadonly(value)) {
    return /* @__PURE__ */ isReactive2(value["__v_raw"]);
  }
  return !!(value && value["__v_isReactive"]);
}
function isReadonly(value) {
  return !!(value && value["__v_isReadonly"]);
}
function isShallow(value) {
  return !!(value && value["__v_isShallow"]);
}
function isProxy(value) {
  return value ? !!value["__v_raw"] : false;
}
function toRaw(observed) {
  const raw2 = observed && observed["__v_raw"];
  return raw2 ? /* @__PURE__ */ toRaw(raw2) : observed;
}
var toReactive = (value) => isObject(value) ? /* @__PURE__ */ reactive2(value) : value;
var toReadonly = (value) => isObject(value) ? /* @__PURE__ */ readonly(value) : value;
function isRef(r) {
  return r ? r["__v_isRef"] === true : false;
}
magic("nextTick", () => nextTick);
magic("dispatch", (el) => dispatch.bind(dispatch, el));
magic("watch", (el, { evaluateLater: evaluateLater2, cleanup }) => (key, callback) => {
  let evaluate22 = evaluateLater2(key);
  let getter = () => {
    let value;
    evaluate22((i) => value = i);
    return value;
  };
  let unwatch = watch(getter, callback);
  cleanup(unwatch);
});
magic("store", getStores);
magic("data", (el) => scope(el));
magic("root", (el) => closestRoot(el));
magic("refs", (el) => {
  if (el._x_refs_proxy)
    return el._x_refs_proxy;
  el._x_refs_proxy = mergeProxies(getArrayOfRefObject(el));
  return el._x_refs_proxy;
});
function getArrayOfRefObject(el) {
  let refObjects = [];
  findClosest(el, (i) => {
    if (i._x_refs)
      refObjects.push(i._x_refs);
  });
  return refObjects;
}
var globalIdMemo = {};
function findAndIncrementId(name) {
  if (!globalIdMemo[name])
    globalIdMemo[name] = 0;
  return ++globalIdMemo[name];
}
function closestIdRoot(el, name) {
  return findClosest(el, (element) => {
    if (element._x_ids && element._x_ids[name])
      return true;
  });
}
function setIdRoot(el, name) {
  if (!el._x_ids)
    el._x_ids = {};
  if (!el._x_ids[name])
    el._x_ids[name] = findAndIncrementId(name);
}
magic("id", (el, { cleanup }) => (name, key = null) => {
  let cacheKey = `${name}${key ? `-${key}` : ""}`;
  return cacheIdByNameOnElement(el, cacheKey, cleanup, () => {
    let root = closestIdRoot(el, name);
    let id = root ? root._x_ids[name] : findAndIncrementId(name);
    return key ? `${name}-${id}-${key}` : `${name}-${id}`;
  });
});
interceptClone((from, to) => {
  if (from._x_id) {
    to._x_id = from._x_id;
  }
});
function cacheIdByNameOnElement(el, cacheKey, cleanup, callback) {
  if (!el._x_id)
    el._x_id = {};
  if (el._x_id[cacheKey])
    return el._x_id[cacheKey];
  let output = callback();
  el._x_id[cacheKey] = output;
  cleanup(() => {
    delete el._x_id[cacheKey];
  });
  return output;
}
magic("el", (el) => el);
warnMissingPluginMagic("Focus", "focus", "focus");
warnMissingPluginMagic("Persist", "persist", "persist");
function warnMissingPluginMagic(name, magicName, slug) {
  magic(magicName, (el) => warn(`You can't use [$${magicName}] without first installing the "${name}" plugin here: https://alpinejs.dev/plugins/${slug}`, el));
}
directive("modelable", (el, { expression }, { effect: effect3, evaluateLater: evaluateLater2, cleanup }) => {
  let func = evaluateLater2(expression);
  let innerGet = () => {
    let result;
    func((i) => result = i);
    return result;
  };
  let evaluateInnerSet = evaluateLater2(`${expression} = __placeholder`);
  let innerSet = (val) => evaluateInnerSet(() => {
  }, { scope: { "__placeholder": val } });
  let initialValue = innerGet();
  innerSet(initialValue);
  queueMicrotask(() => {
    if (!el._x_model)
      return;
    el._x_removeModelListeners["default"]();
    let outerGet = el._x_model.get;
    let outerSet = el._x_model.setWithModifiers;
    let releaseEntanglement = entangle(
      {
        get() {
          return outerGet();
        },
        set(value) {
          outerSet(value);
        }
      },
      {
        get() {
          return innerGet();
        },
        set(value) {
          innerSet(value);
        }
      }
    );
    cleanup(releaseEntanglement);
  });
});
directive("teleport", (el, { modifiers, expression }, { cleanup }) => {
  if (el.tagName.toLowerCase() !== "template")
    warn("x-teleport can only be used on a <template> tag", el);
  let target = getTarget(expression);
  let clone22 = el.content.cloneNode(true).firstElementChild;
  el._x_teleport = clone22;
  clone22._x_teleportBack = el;
  el.setAttribute("data-teleport-template", true);
  clone22.setAttribute("data-teleport-target", true);
  if (el._x_forwardEvents) {
    el._x_forwardEvents.forEach((eventName) => {
      clone22.addEventListener(eventName, (e) => {
        e.stopPropagation();
        el.dispatchEvent(new e.constructor(e.type, e));
      });
    });
  }
  addScopeToNode(clone22, {}, el);
  let placeInDom = (clone3, target2, modifiers2) => {
    if (modifiers2.includes("prepend")) {
      target2.parentNode.insertBefore(clone3, target2);
    } else if (modifiers2.includes("append")) {
      target2.parentNode.insertBefore(clone3, target2.nextSibling);
    } else {
      target2.appendChild(clone3);
    }
  };
  mutateDom(() => {
    skipDuringClone(() => {
      placeInDom(clone22, target, modifiers);
      initTree(clone22);
    })();
  });
  el._x_teleportPutBack = () => {
    let target2 = getTarget(expression);
    mutateDom(() => {
      placeInDom(el._x_teleport, target2, modifiers);
    });
  };
  cleanup(
    () => mutateDom(() => {
      clone22.remove();
      destroyTree(clone22);
    })
  );
});
var teleportContainerDuringClone = document.createElement("div");
function getTarget(expression) {
  let target = skipDuringClone(() => {
    return document.querySelector(expression);
  }, () => {
    return teleportContainerDuringClone;
  })();
  if (!target)
    warn(`Cannot find x-teleport element for selector: "${expression}"`);
  return target;
}
var handler = () => {
};
handler.inline = (el, { modifiers }, { cleanup }) => {
  modifiers.includes("self") ? el._x_ignoreSelf = true : el._x_ignore = true;
  cleanup(() => {
    modifiers.includes("self") ? delete el._x_ignoreSelf : delete el._x_ignore;
  });
};
directive("ignore", handler);
directive("effect", skipDuringClone((el, { expression }, { effect: effect3 }) => {
  effect3(evaluateLater(el, expression));
}));
function on(el, event, modifiers, callback) {
  let listenerTarget = el;
  let handler4 = (e) => callback(e);
  let options = {};
  let wrapHandler = (callback2, wrapper) => (e) => wrapper(callback2, e);
  if (modifiers.includes("dot"))
    event = dotSyntax(event);
  if (modifiers.includes("camel"))
    event = camelCase2(event);
  if (modifiers.includes("capture"))
    options.capture = true;
  if (modifiers.includes("window"))
    listenerTarget = window;
  if (modifiers.includes("document"))
    listenerTarget = document;
  if (modifiers.includes("passive")) {
    options.passive = modifiers[modifiers.indexOf("passive") + 1] !== "false";
  }
  handler4 = addDebounceOrThrottle(modifiers, handler4);
  if (modifiers.includes("prevent"))
    handler4 = wrapHandler(handler4, (next, e) => {
      e.preventDefault();
      next(e);
    });
  if (modifiers.includes("stop"))
    handler4 = wrapHandler(handler4, (next, e) => {
      e.stopPropagation();
      next(e);
    });
  if (modifiers.includes("once")) {
    handler4 = wrapHandler(handler4, (next, e) => {
      next(e);
      listenerTarget.removeEventListener(event, handler4, options);
    });
  }
  if (modifiers.includes("away") || modifiers.includes("outside")) {
    listenerTarget = document;
    handler4 = wrapHandler(handler4, (next, e) => {
      if (el.contains(e.target))
        return;
      if (e.target.isConnected === false)
        return;
      if (el.offsetWidth < 1 && el.offsetHeight < 1)
        return;
      if (el._x_isShown === false)
        return;
      next(e);
    });
  }
  if (modifiers.includes("self"))
    handler4 = wrapHandler(handler4, (next, e) => {
      e.target === el && next(e);
    });
  if (event === "submit") {
    handler4 = wrapHandler(handler4, (next, e) => {
      if (e.target._x_pendingModelUpdates) {
        e.target._x_pendingModelUpdates.forEach((fn) => fn());
      }
      next(e);
    });
  }
  if (isKeyEvent(event) || isClickEvent(event)) {
    handler4 = wrapHandler(handler4, (next, e) => {
      if (isListeningForASpecificKeyThatHasntBeenPressed(e, modifiers)) {
        return;
      }
      next(e);
    });
  }
  listenerTarget.addEventListener(event, handler4, options);
  return () => {
    listenerTarget.removeEventListener(event, handler4, options);
  };
}
function addDebounceOrThrottle(modifiers, handler4) {
  if (modifiers.includes("debounce")) {
    let nextModifier = modifiers[modifiers.indexOf("debounce") + 1] || "invalid-wait";
    let wait = isNumeric(nextModifier.split("ms")[0]) ? Number(nextModifier.split("ms")[0]) : 250;
    handler4 = debounce(handler4, wait);
  }
  if (modifiers.includes("throttle")) {
    let nextModifier = modifiers[modifiers.indexOf("throttle") + 1] || "invalid-wait";
    let wait = isNumeric(nextModifier.split("ms")[0]) ? Number(nextModifier.split("ms")[0]) : 250;
    handler4 = throttle(handler4, wait);
  }
  return handler4;
}
function dotSyntax(subject) {
  return subject.replace(/-/g, ".");
}
function camelCase2(subject) {
  return subject.toLowerCase().replace(/-(\w)/g, (match, char) => char.toUpperCase());
}
function isNumeric(subject) {
  return !Array.isArray(subject) && !isNaN(subject);
}
function kebabCase2(subject) {
  if ([" ", "_"].includes(
    subject
  ))
    return subject;
  return subject.replace(/([a-z])([A-Z])/g, "$1-$2").replace(/[_\s]/, "-").toLowerCase();
}
function isKeyEvent(event) {
  return ["keydown", "keyup"].includes(event);
}
function isClickEvent(event) {
  return ["contextmenu", "click", "mouse"].some((i) => event.includes(i));
}
var nonKeyModifiers = [
  // x-on's own modifiers:
  "window",
  "document",
  "prevent",
  "stop",
  "once",
  "capture",
  "self",
  "away",
  "outside",
  "passive",
  "dot",
  "camel",
  "preserve-scroll",
  // x-model's own modifiers:
  "blur",
  "change",
  "lazy",
  "number",
  "boolean",
  "trim",
  "fill",
  "unintrusive",
  "parent"
];
function isListeningForASpecificKeyThatHasntBeenPressed(e, modifiers) {
  let keyModifiers = modifiers.filter((modifier, index2) => {
    if (modifier === "false" && modifiers[index2 - 1] === "passive")
      return false;
    return !nonKeyModifiers.includes(modifier);
  });
  if (keyModifiers.includes("debounce")) {
    let debounceIndex = keyModifiers.indexOf("debounce");
    keyModifiers.splice(debounceIndex, isNumeric((keyModifiers[debounceIndex + 1] || "invalid-wait").split("ms")[0]) ? 2 : 1);
  }
  if (keyModifiers.includes("throttle")) {
    let debounceIndex = keyModifiers.indexOf("throttle");
    keyModifiers.splice(debounceIndex, isNumeric((keyModifiers[debounceIndex + 1] || "invalid-wait").split("ms")[0]) ? 2 : 1);
  }
  if (keyModifiers.length === 0)
    return false;
  if (keyModifiers.length === 1 && keyToModifiers(e.key).includes(keyModifiers[0]))
    return false;
  const systemKeyModifiers = ["ctrl", "shift", "alt", "meta", "cmd", "super"];
  const selectedSystemKeyModifiers = systemKeyModifiers.filter((modifier) => keyModifiers.includes(modifier));
  keyModifiers = keyModifiers.filter((i) => !selectedSystemKeyModifiers.includes(i));
  if (selectedSystemKeyModifiers.length > 0) {
    const activelyPressedKeyModifiers = selectedSystemKeyModifiers.filter((modifier) => {
      if (modifier === "cmd" || modifier === "super")
        modifier = "meta";
      return e[`${modifier}Key`];
    });
    if (activelyPressedKeyModifiers.length === selectedSystemKeyModifiers.length) {
      if (isClickEvent(e.type))
        return false;
      if (keyToModifiers(e.key).includes(keyModifiers[0]))
        return false;
    }
  }
  return true;
}
function keyToModifiers(key) {
  if (!key)
    return [];
  key = kebabCase2(key);
  let modifierToKeyMap = {
    "ctrl": "control",
    "slash": "/",
    "space": " ",
    "spacebar": " ",
    "cmd": "meta",
    "esc": "escape",
    "up": "arrow-up",
    "down": "arrow-down",
    "left": "arrow-left",
    "right": "arrow-right",
    "period": ".",
    "comma": ",",
    "equal": "=",
    "minus": "-",
    "underscore": "_"
  };
  modifierToKeyMap[key] = key;
  return Object.keys(modifierToKeyMap).map((modifier) => {
    if (modifierToKeyMap[modifier] === key)
      return modifier;
  }).filter((modifier) => modifier);
}
directive("model", (el, { modifiers, expression }, { effect: effect3, cleanup }) => {
  let scopeTarget = el;
  if (modifiers.includes("parent")) {
    scopeTarget = findClosest(el, (element) => element !== el);
  }
  let evaluateGet = evaluateLater(scopeTarget, expression);
  let evaluateSet;
  if (typeof expression === "string") {
    evaluateSet = evaluateLater(scopeTarget, `${expression} = __placeholder`);
  } else if (typeof expression === "function" && typeof expression() === "string") {
    evaluateSet = evaluateLater(scopeTarget, `${expression()} = __placeholder`);
  } else {
    evaluateSet = () => {
    };
  }
  let getValue = () => {
    let result;
    evaluateGet((value) => result = value);
    return isGetterSetter(result) ? result.get() : result;
  };
  let setValue = (value) => {
    let result;
    evaluateGet((value2) => result = value2);
    if (isGetterSetter(result)) {
      result.set(value);
    } else {
      evaluateSet(() => {
      }, {
        scope: { "__placeholder": value }
      });
    }
  };
  if (typeof expression === "string" && el.type === "radio") {
    mutateDom(() => {
      if (!el.hasAttribute("name"))
        el.setAttribute("name", expression);
    });
  }
  let hasChangeModifier = modifiers.includes("change") || modifiers.includes("lazy");
  let hasBlurModifier = modifiers.includes("blur");
  let hasEnterModifier = modifiers.includes("enter");
  let hasExplicitEventModifiers = hasChangeModifier || hasBlurModifier || hasEnterModifier;
  let removeListener;
  if (isCloning) {
    removeListener = () => {
    };
  } else if (hasExplicitEventModifiers) {
    let listeners2 = [];
    let syncValue = (e) => setValue(getInputValue(el, modifiers, e, getValue()));
    if (hasChangeModifier) {
      listeners2.push(on(el, "change", modifiers, syncValue));
    }
    if (hasBlurModifier) {
      listeners2.push(on(el, "blur", modifiers, syncValue));
      if (el.form) {
        let form = el.form;
        let syncCallback = () => syncValue({ target: el });
        if (!form._x_pendingModelUpdates)
          form._x_pendingModelUpdates = [];
        form._x_pendingModelUpdates.push(syncCallback);
        cleanup(() => {
          if (form._x_pendingModelUpdates) {
            form._x_pendingModelUpdates.splice(form._x_pendingModelUpdates.indexOf(syncCallback), 1);
          }
        });
      }
    }
    if (hasEnterModifier) {
      listeners2.push(on(el, "keydown", modifiers, (e) => {
        if (e.key === "Enter")
          syncValue(e);
      }));
    }
    removeListener = () => listeners2.forEach((remove2) => remove2());
  } else {
    let event = el.tagName.toLowerCase() === "select" || ["checkbox", "radio"].includes(el.type) ? "change" : "input";
    removeListener = on(el, event, modifiers, (e) => {
      setValue(getInputValue(el, modifiers, e, getValue()));
    });
  }
  if (modifiers.includes("fill")) {
    if ([void 0, null, ""].includes(getValue()) || isCheckbox(el) && Array.isArray(getValue()) || el.tagName.toLowerCase() === "select" && el.multiple) {
      setValue(
        getInputValue(el, modifiers, { target: el }, getValue())
      );
    }
  }
  if (!el._x_removeModelListeners)
    el._x_removeModelListeners = {};
  el._x_removeModelListeners["default"] = removeListener;
  cleanup(() => el._x_removeModelListeners["default"]());
  if (el.form) {
    let removeResetListener = on(el.form, "reset", [], (e) => {
      nextTick(() => el._x_model && el._x_model.set(getInputValue(el, modifiers, { target: el }, getValue())));
    });
    cleanup(() => removeResetListener());
  }
  el._x_model = {
    get() {
      return getValue();
    },
    set(value) {
      setValue(value);
    },
    setWithModifiers: addDebounceOrThrottle(modifiers, setValue)
  };
  el._x_forceModelUpdate = (value) => {
    if (value === void 0 && typeof expression === "string" && expression.match(/\./))
      value = "";
    mutateDom(() => {
      if (isCheckbox(el)) {
        if (Array.isArray(value)) {
          el.checked = value.some((val) => val == el.value);
        } else {
          el.checked = !!value;
        }
      } else if (isRadio(el)) {
        if (typeof value === "boolean") {
          el.checked = safeParseBoolean(el.value) === value;
        } else {
          el.checked = el.value == value;
        }
      } else {
        bind(el, "value", value);
      }
    });
  };
  if (el.tagName === "SELECT") {
    let observer2 = new MutationObserver(() => {
      el._x_forceModelUpdate(getValue());
    });
    observer2.observe(el, { childList: true });
    cleanup(() => observer2.disconnect());
  }
  effect3(() => {
    let value = getValue();
    if (modifiers.includes("unintrusive") && document.activeElement.isSameNode(el))
      return;
    el._x_forceModelUpdate(value);
  });
});
function getInputValue(el, modifiers, event, currentValue) {
  return mutateDom(() => {
    if (event instanceof CustomEvent && event.detail !== void 0)
      return event.detail !== null && event.detail !== void 0 ? event.detail : event.target.value;
    else if (isCheckbox(el)) {
      if (Array.isArray(currentValue)) {
        let newValue = null;
        if (modifiers.includes("number")) {
          newValue = safeParseNumber(event.target.value);
        } else if (modifiers.includes("boolean")) {
          newValue = safeParseBoolean(event.target.value);
        } else {
          newValue = event.target.value;
        }
        return event.target.checked ? currentValue.includes(newValue) ? currentValue : currentValue.concat([newValue]) : currentValue.filter((el2) => !checkedAttrLooseCompare2(el2, newValue));
      } else {
        return event.target.checked;
      }
    } else if (el.tagName.toLowerCase() === "select" && el.multiple) {
      if (modifiers.includes("number")) {
        return Array.from(event.target.selectedOptions).map((option2) => {
          let rawValue = option2.value || option2.text;
          return safeParseNumber(rawValue);
        });
      } else if (modifiers.includes("boolean")) {
        return Array.from(event.target.selectedOptions).map((option2) => {
          let rawValue = option2.value || option2.text;
          return safeParseBoolean(rawValue);
        });
      }
      return Array.from(event.target.selectedOptions).map((option2) => {
        return option2.value || option2.text;
      });
    } else {
      let newValue;
      if (isRadio(el)) {
        if (event.target.checked) {
          newValue = event.target.value;
        } else {
          newValue = currentValue;
        }
      } else {
        newValue = event.target.value;
      }
      if (modifiers.includes("number")) {
        return safeParseNumber(newValue);
      } else if (modifiers.includes("boolean")) {
        return safeParseBoolean(newValue);
      } else if (modifiers.includes("trim")) {
        return newValue.trim();
      } else {
        return newValue;
      }
    }
  });
}
function safeParseNumber(rawValue) {
  let number = rawValue ? parseFloat(rawValue) : null;
  return isNumeric2(number) ? number : rawValue;
}
function checkedAttrLooseCompare2(valueA, valueB) {
  return valueA == valueB;
}
function isNumeric2(subject) {
  return !Array.isArray(subject) && !isNaN(subject);
}
function isGetterSetter(value) {
  return value !== null && typeof value === "object" && typeof value.get === "function" && typeof value.set === "function";
}
directive("cloak", (el) => queueMicrotask(() => mutateDom(() => el.removeAttribute(prefix("cloak")))));
addInitSelector(() => `[${prefix("init")}]`);
directive("init", skipDuringClone((el, { expression }, { evaluate: evaluate22 }) => {
  if (typeof expression === "string") {
    return !!expression.trim() && evaluate22(expression, {}, false);
  }
  return evaluate22(expression, {}, false);
}));
directive("text", (el, { expression }, { effect: effect3, evaluateLater: evaluateLater2 }) => {
  let evaluate22 = evaluateLater2(expression);
  effect3(() => {
    evaluate22((value) => {
      mutateDom(() => {
        el.textContent = value;
      });
    });
  });
});
directive("html", (el, { expression }, { effect: effect3, evaluateLater: evaluateLater2 }) => {
  let evaluate22 = evaluateLater2(expression);
  effect3(() => {
    evaluate22((value) => {
      mutateDom(() => {
        Array.from(el.children).forEach((child) => destroyTree(child));
        el.innerHTML = value ?? "";
        el._x_ignoreSelf = true;
        initTree(el);
        delete el._x_ignoreSelf;
      });
    });
  }, { priority: "structural" });
});
mapAttributes(startingWith(":", into(prefix("bind:"))));
var handler2 = (el, { value, modifiers, expression, original }, { effect: effect3, cleanup }) => {
  if (!value) {
    let bindingProviders = {};
    injectBindingProviders(bindingProviders);
    let getBindings = evaluateLater(el, expression);
    getBindings((bindings) => {
      applyBindingsObject(el, bindings, original);
    }, { scope: bindingProviders });
    return;
  }
  if (value === "key")
    return storeKeyForXFor(el, expression);
  if (el._x_inlineBindings && el._x_inlineBindings[value] && el._x_inlineBindings[value].extract) {
    return;
  }
  let evaluate22 = evaluateLater(el, expression);
  effect3(() => evaluate22((result) => {
    if (result === void 0 && typeof expression === "string" && expression.match(/\./)) {
      result = "";
    }
    mutateDom(() => bind(el, value, result, modifiers));
  }));
  cleanup(() => {
    el._x_undoAddedClasses && el._x_undoAddedClasses();
    el._x_undoAddedStyles && el._x_undoAddedStyles();
  });
};
handler2.inline = (el, { value, modifiers, expression }) => {
  if (!value)
    return;
  if (!el._x_inlineBindings)
    el._x_inlineBindings = {};
  el._x_inlineBindings[value] = { expression, extract: false };
};
directive("bind", handler2);
function storeKeyForXFor(el, expression) {
  el._x_keyExpression = expression;
}
addRootSelector(() => `[${prefix("data")}]`);
var dataForReconciliation = Symbol();
directive("data", (el, { expression }, { cleanup }) => {
  if (shouldSkipRegisteringDataDuringClone(el))
    return;
  let dataToReconcile = el[dataForReconciliation];
  if (dataToReconcile?.expression === expression)
    return;
  expression = expression === "" ? "{}" : expression;
  let magicContext = {};
  injectMagics(magicContext, el);
  let dataProviderContext = {};
  injectDataProviders(dataProviderContext, magicContext);
  let data2 = evaluate(el, expression, { scope: dataProviderContext });
  if (data2 === void 0 || data2 === true)
    data2 = {};
  injectMagics(data2, el);
  let reactiveData;
  if (dataToReconcile?.reactiveData) {
    reactiveData = dataToReconcile.reactiveData;
    reconcileData(reactiveData, data2);
    let initialized = { expression };
    el[dataForReconciliation] = initialized;
    queueMicrotask(() => {
      if (el[dataForReconciliation] === initialized) {
        delete el[dataForReconciliation];
      }
    });
  } else {
    reactiveData = reactive(data2);
  }
  initInterceptors(reactiveData, cleanup);
  let undo = addScopeToNode(el, reactiveData);
  skipDuringClone(() => {
    reactiveData["init"] && evaluate(el, reactiveData["init"]);
  })();
  cleanup(() => {
    reactiveData["destroy"] && evaluate(el, reactiveData["destroy"]);
    undo();
    let removed = { reactiveData };
    el[dataForReconciliation] = removed;
    queueMicrotask(() => {
      if (el[dataForReconciliation] === removed) {
        delete el[dataForReconciliation];
      }
    });
  });
});
function reconcileData(target, source) {
  Object.keys(source).forEach((key) => {
    let descriptor = Object.getOwnPropertyDescriptor(source, key);
    let existingDescriptor = Object.getOwnPropertyDescriptor(target, key);
    if (descriptor.get || descriptor.set || existingDescriptor?.get || existingDescriptor?.set) {
      if (existingDescriptor)
        delete target[key];
      if (!existingDescriptor)
        target[key] = void 0;
      descriptor.get || descriptor.set ? Object.defineProperty(target, key, descriptor) : target[key] = source[key];
    } else {
      target[key] = source[key];
    }
  });
  Object.keys(target).filter((key) => !Object.prototype.hasOwnProperty.call(source, key)).forEach((key) => delete target[key]);
}
interceptClone((from, to) => {
  if (from._x_dataStack) {
    to._x_dataStack = from._x_dataStack;
    to.setAttribute("data-has-alpine-state", true);
  }
});
function shouldSkipRegisteringDataDuringClone(el) {
  if (!isCloning)
    return false;
  if (isCloningLegacy)
    return true;
  return el.hasAttribute("data-has-alpine-state");
}
directive("show", (el, { modifiers, expression }, { effect: effect3 }) => {
  let evaluate22 = evaluateLater(el, expression);
  if (!el._x_doHide)
    el._x_doHide = () => {
      mutateDom(() => {
        el.style.setProperty("display", "none", modifiers.includes("important") ? "important" : void 0);
      });
    };
  if (!el._x_doShow)
    el._x_doShow = () => {
      mutateDom(() => {
        if (el.style.length === 1 && el.style.display === "none") {
          el.removeAttribute("style");
        } else {
          el.style.removeProperty("display");
        }
      });
    };
  let hide = () => {
    el._x_doHide();
    el._x_isShown = false;
  };
  let show = () => {
    el._x_doShow();
    el._x_isShown = true;
  };
  let clickAwayCompatibleShow = () => setTimeout(show);
  let toggle = once(
    (value) => value ? show() : hide(),
    (value) => {
      if (typeof el._x_toggleAndCascadeWithTransitions === "function") {
        el._x_toggleAndCascadeWithTransitions(el, value, show, hide);
      } else {
        value ? clickAwayCompatibleShow() : hide();
      }
    }
  );
  let oldValue;
  let firstTime = true;
  effect3(() => evaluate22((value) => {
    if (!firstTime && value === oldValue)
      return;
    if (modifiers.includes("immediate"))
      value ? clickAwayCompatibleShow() : hide();
    toggle(value);
    oldValue = value;
    firstTime = false;
  }));
});
directive("for", skipDuringClone((el, { expression }, { effect: effect3, cleanup }) => {
  let iteratorNames = parseForExpression(expression);
  let evaluateItems = evaluateLater(el, iteratorNames.items);
  let evaluateKey = evaluateLater(
    el,
    // the x-bind:key expression is stored for our use instead of evaluated.
    el._x_keyExpression || "index"
  );
  el._x_lookup = /* @__PURE__ */ new Map();
  effect3(() => loop(el, iteratorNames, evaluateItems, evaluateKey), { priority: "structural" });
  cleanup(() => {
    el._x_lookup.forEach(
      (el2) => mutateDom(() => {
        destroyTree(el2);
        el2.remove();
      })
    );
    delete el._x_lookup;
    delete el._x_lastRenderedEl;
  });
}));
function refreshScope(scope2) {
  return (newScope) => {
    Object.entries(newScope).forEach(([key, value]) => {
      scope2[key] = value;
    });
  };
}
function loop(templateEl, iteratorNames, evaluateItems, evaluateKey) {
  evaluateItems((items) => {
    if (isNumeric3(items))
      items = Array.from({ length: items }, (_, i) => i + 1);
    if (items === void 0 || items === null)
      items = [];
    if (items instanceof Set)
      items = Array.from(items);
    if (items instanceof Map)
      items = Array.from(items);
    let oldLookup = templateEl._x_lookup;
    let lookup = /* @__PURE__ */ new Map();
    templateEl._x_lookup = lookup;
    let hasStringKeys = isObject2(items);
    let scopeEntries = Object.entries(items).map(([index2, item]) => {
      if (!hasStringKeys)
        index2 = parseInt(index2);
      let scope2 = getIterationScopeVariables(iteratorNames, item, index2, items);
      let key;
      evaluateKey((innerKey) => {
        if (typeof innerKey === "object")
          warn("x-for key cannot be an object, it must be a string or an integer", templateEl);
        if (oldLookup.has(innerKey)) {
          lookup.set(innerKey, oldLookup.get(innerKey));
          oldLookup.delete(innerKey);
        }
        key = innerKey;
      }, { scope: { index: index2, ...scope2 } });
      return [key, scope2];
    });
    mutateDom(() => {
      oldLookup.forEach((el) => {
        destroyTree(el);
        el.remove();
      });
      let added = /* @__PURE__ */ new Set();
      let prev = templateEl;
      scopeEntries.forEach(([key, scope2]) => {
        if (lookup.has(key)) {
          let el = lookup.get(key);
          el._x_refreshXForScope(scope2);
          if (prev.nextElementSibling !== el) {
            if (prev.nextElementSibling)
              el.replaceWith(prev.nextElementSibling);
            prev.after(el);
          }
          prev = el;
          if (el._x_currentIfEl) {
            if (el.nextElementSibling !== el._x_currentIfEl)
              prev.after(el._x_currentIfEl);
            prev = el._x_currentIfEl;
          }
          return;
        }
        if (templateEl.content.children.length > 1)
          warn("x-for templates require a single root element, additional elements will be ignored.", templateEl);
        let clone22 = document.importNode(templateEl.content, true).firstElementChild;
        let reactiveScope = reactive(scope2);
        addScopeToNode(clone22, reactiveScope, templateEl);
        clone22._x_refreshXForScope = refreshScope(reactiveScope);
        lookup.set(key, clone22);
        added.add(clone22);
        prev.after(clone22);
        prev = clone22;
      });
      added.forEach((clone22) => initTree(clone22));
      if (prev !== templateEl) {
        templateEl._x_lastRenderedEl = prev;
      } else {
        delete templateEl._x_lastRenderedEl;
      }
    });
  });
}
function parseForExpression(expression) {
  let forIteratorRE = /,([^,\}\]]*)(?:,([^,\}\]]*))?$/;
  let stripParensRE = /^\s*\(|\)\s*$/g;
  let forAliasRE = /([\s\S]*?)\b(?:in|of)\b([\s\S]*)/;
  let inMatch = expression.match(forAliasRE);
  if (!inMatch)
    return;
  let res = {};
  res.items = inMatch[2].trim();
  let item = inMatch[1].replace(stripParensRE, "").trim();
  let iteratorMatch = item.match(forIteratorRE);
  if (iteratorMatch) {
    res.item = item.replace(forIteratorRE, "").trim();
    res.index = iteratorMatch[1].trim();
    if (iteratorMatch[2]) {
      res.collection = iteratorMatch[2].trim();
    }
  } else {
    res.item = item;
  }
  return res;
}
function getIterationScopeVariables(iteratorNames, item, index2, items) {
  let scopeVariables = {};
  if (/^\[.*\]$/.test(iteratorNames.item) && Array.isArray(item)) {
    let names = iteratorNames.item.replace("[", "").replace("]", "").split(",").map((i) => i.trim());
    names.forEach((name, i) => {
      scopeVariables[name] = item[i];
    });
  } else if (/^\{.*\}$/.test(iteratorNames.item) && !Array.isArray(item) && typeof item === "object") {
    let names = iteratorNames.item.replace("{", "").replace("}", "").split(",").map((i) => i.trim());
    names.forEach((name) => {
      scopeVariables[name] = item[name];
    });
  } else {
    scopeVariables[iteratorNames.item] = item;
  }
  if (iteratorNames.index)
    scopeVariables[iteratorNames.index] = index2;
  if (iteratorNames.collection)
    scopeVariables[iteratorNames.collection] = items;
  return scopeVariables;
}
function isNumeric3(subject) {
  return typeof subject !== "object" && !isNaN(subject);
}
function isObject2(subject) {
  return typeof subject === "object" && !Array.isArray(subject);
}
function handler3() {
}
handler3.inline = (el, { expression }, { cleanup }) => {
  let root = closestRoot(el);
  if (!root)
    return;
  if (!root._x_refs)
    root._x_refs = {};
  root._x_refs[expression] = el;
  cleanup(() => delete root._x_refs[expression]);
};
directive("ref", handler3);
directive("if", skipDuringClone((el, { expression }, { effect: effect3, cleanup }) => {
  if (el.tagName.toLowerCase() !== "template")
    warn("x-if can only be used on a <template> tag", el);
  let evaluate22 = evaluateLater(el, expression);
  let show = () => {
    if (el._x_currentIfEl)
      return el._x_currentIfEl;
    let clone22 = el.content.cloneNode(true).firstElementChild;
    addScopeToNode(clone22, {}, el);
    mutateDom(() => {
      el.after(clone22);
      initTree(clone22);
    });
    el._x_currentIfEl = clone22;
    el._x_lastRenderedEl = clone22;
    el._x_undoIf = () => {
      mutateDom(() => {
        destroyTree(clone22);
        clone22.remove();
      });
      delete el._x_currentIfEl;
      delete el._x_lastRenderedEl;
    };
    return clone22;
  };
  let hide = () => {
    if (!el._x_undoIf)
      return;
    el._x_undoIf();
    delete el._x_undoIf;
  };
  effect3(() => evaluate22((value) => {
    value ? show() : hide();
  }), { priority: "structural" });
  cleanup(() => el._x_undoIf && el._x_undoIf());
}));
directive("id", (el, { expression }, { evaluate: evaluate22 }) => {
  let names = evaluate22(expression);
  names.forEach((name) => setIdRoot(el, name));
});
interceptClone((from, to) => {
  if (from._x_ids) {
    to._x_ids = from._x_ids;
  }
});
mapAttributes(startingWith("@", into(prefix("on:"))));
directive("on", skipDuringClone((el, { value, modifiers, expression }, { cleanup }) => {
  let evaluate22 = expression ? evaluateLater(el, expression) : () => {
  };
  if (el.tagName.toLowerCase() === "template") {
    if (!el._x_forwardEvents)
      el._x_forwardEvents = [];
    if (!el._x_forwardEvents.includes(value))
      el._x_forwardEvents.push(value);
  }
  let removeListener = on(el, value, modifiers, (e) => {
    evaluate22(() => {
    }, { scope: { "$event": e }, params: [e] });
  });
  cleanup(() => removeListener());
}));
warnMissingPluginDirective("Collapse", "collapse", "collapse");
warnMissingPluginDirective("Intersect", "intersect", "intersect");
warnMissingPluginDirective("Focus", "trap", "focus");
warnMissingPluginDirective("Mask", "mask", "mask");
function warnMissingPluginDirective(name, directiveName, slug) {
  directive(directiveName, (el) => warn(`You can't use [x-${directiveName}] without first installing the "${name}" plugin here: https://alpinejs.dev/plugins/${slug}`, el));
}
alpine_default.setEvaluator(normalEvaluator);
alpine_default.setRawEvaluator(normalRawEvaluator);
alpine_default.setReactivityEngine({
  reactive: reactive2,
  // Since Vue 3.2, the scheduler is called with no arguments, so we wrap
  // the effect to hand Alpine's scheduler the runner it expects to queue.
  effect: (callback, options = {}) => {
    let runner;
    runner = effect2(callback, {
      scheduler: () => {
        if (!runner)
          return;
        options.scheduler ? options.scheduler(runner) : runner();
      }
    });
    return runner;
  },
  release: stop,
  raw: toRaw
});
var src_default = alpine_default;
var module_default = src_default;

// src/utils.js
var WeakBag = class {
  constructor() {
    this.map = /* @__PURE__ */ new WeakMap();
  }
  add(key, value) {
    if (!this.map.has(key)) this.map.set(key, []);
    this.map.get(key).push(value);
  }
  get(key) {
    return this.map.get(key) || [];
  }
  each(key, callback) {
    this.get(key).forEach(callback);
  }
  remove(key) {
    this.map.delete(key);
  }
};
function dispatch2(target, name, detail = {}, bubbles = true) {
  target.dispatchEvent(new CustomEvent(name, { detail, bubbles, composed: true, cancelable: true }));
}
function isObjecty(subject) {
  return typeof subject === "object" && subject !== null;
}
function isObject3(subject) {
  return isObjecty(subject) && !isArray2(subject);
}
function isArray2(subject) {
  return Array.isArray(subject);
}
function isFunction(subject) {
  return typeof subject === "function";
}
function isPrimitive(subject) {
  return typeof subject !== "object" || subject === null;
}
function deepClone(value) {
  return JSON.parse(JSON.stringify(value));
}
function deeplyEqual(a, b) {
  return JSON.stringify(a) === JSON.stringify(b);
}
function parsePathSegments(path) {
  if (path === "") return [];
  return path.replace(/\[(['"]?)(.+?)\1\]/g, ".$2").replace(/^\./, "").split(".");
}
function dataGet(object, key) {
  if (key === "") return object;
  return parsePathSegments(key).reduce((carry, segment) => carry?.[segment], object);
}
function dataSet(object, key, value) {
  let segments = parsePathSegments(key);
  if (segments.length === 1) {
    object[segments[0]] = value;
    return;
  }
  let first2 = segments.shift();
  let next = segments[0];
  if (object[first2] === void 0) object[first2] = {};
  if (isArray2(object[first2]) && !isNaN(parseInt(next)) && parseInt(next) > object[first2].length) {
    object[first2] = { ...object[first2] };
  }
  dataSet(object[first2], segments.join("."), value);
}
function diffAndConsolidate(left, right) {
  let diffs = {};
  diffNode(left, right, "", diffs, right);
  return diffs;
}
function diffNode(left, right, path, diffs, root) {
  if (left === right) return { changed: false, consolidated: false };
  let converted = false;
  if (typeof left !== typeof right || isObject3(left) && isArray2(right) || isArray2(left) && isObject3(right)) {
    if (isObject3(right) && (left === void 0 || isArray2(left) && left.length === 0)) {
      left = {};
      converted = true;
    } else {
      diffs[path] = right;
      return { changed: true, consolidated: false };
    }
  }
  if (isPrimitive(left) || isPrimitive(right)) {
    diffs[path] = right;
    return { changed: true, consolidated: false };
  }
  let leftKeys = Object.keys(left);
  let rightKeys = Object.keys(right);
  let whole = () => {
    diffs[path] = dataGet(root, path);
    return { changed: true, consolidated: true };
  };
  if (!converted) {
    let reshaped = leftKeys.length !== rightKeys.length || !leftKeys.every((key) => rightKeys.includes(key)) || isObject3(left) && leftKeys.some((key, i) => key !== rightKeys[i]);
    if (reshaped && path !== "") return whole();
    if (reshaped && leftKeys.length !== rightKeys.length) {
      rightKeys.forEach((key) => {
        if (!deeplyEqual(left[key], right[key])) diffs[key] = right[key];
      });
      return { changed: true, consolidated: true };
    }
  }
  let childDiffs = {};
  let changed = 0;
  let consolidated = 0;
  rightKeys.forEach((key) => {
    let result = diffNode(left[key], right[key], path === "" ? key : `${path}.${key}`, childDiffs, root);
    if (result.changed) changed++;
    if (result.consolidated) consolidated++;
  });
  if (path !== "" && rightKeys.length > 1 && changed === rightKeys.length && consolidated === 0 && !converted) {
    return whole();
  }
  Object.assign(diffs, childDiffs);
  return { changed: changed > 0, consolidated: consolidated > 0 || converted };
}
function diffAndPatchRecursive(left, right, target) {
  let leftKeys = new Set(Object.keys(left));
  let rightKeys = Object.keys(right);
  if (!isArray2(target) && [...leftKeys].some((key, i) => key !== rightKeys[i])) {
    Object.keys(target).forEach((key) => delete target[key]);
    rightKeys.forEach((key) => target[key] = right[key]);
    return;
  }
  rightKeys.forEach((key) => {
    leftKeys.delete(key);
    if (deeplyEqual(left[key], right[key])) return;
    if (isObjecty(left[key]) && isObjecty(right[key]) && isObjecty(target[key]) && isArray2(right[key]) === isArray2(target[key])) {
      diffAndPatchRecursive(left[key], right[key], target[key]);
    } else {
      target[key] = right[key];
    }
  });
  if (isArray2(target)) {
    [...leftKeys].map(Number).sort((a, b) => b - a).forEach((index2) => target.splice(index2, 1));
  }
}
function extractData(payload) {
  let value = isSynthetic(payload) ? payload[0] : payload;
  if (isObjecty(value)) {
    Object.entries(value).forEach(([key, item]) => {
      value[key] = extractData(item);
    });
  }
  return value;
}
function isSynthetic(subject) {
  return isArray2(subject) && subject.length === 2 && isObject3(subject[1]) && Object.keys(subject[1]).includes("s");
}
function scriptConfig() {
  return window.wireScriptConfig || window.brewScriptConfig || {};
}
function getCsrfToken() {
  let meta = document.querySelector('meta[name="csrf-token"]');
  if (meta) return meta.getAttribute("content");
  let tagged = document.querySelector("[data-csrf]");
  if (tagged) return tagged.getAttribute("data-csrf");
  if (scriptConfig().csrf) return scriptConfig().csrf;
  throw "Brew: No CSRF token detected";
}
function getUpdateUri() {
  return document.querySelector("[data-update-uri]")?.getAttribute("data-update-uri") ?? scriptConfig().uri ?? "/wire/update";
}
function walkUpwards(el, callback) {
  let current = el;
  while (current) {
    let found = callback(current);
    if (found !== void 0) return found;
    current = current._x_teleportBack || current.parentElement;
  }
}

// src/hooks.js
var listeners = {};
function on2(name, callback) {
  if (!listeners[name]) listeners[name] = [];
  listeners[name].push(callback);
  return () => {
    listeners[name] = listeners[name].filter((i) => i !== callback);
  };
}
function trigger2(name, ...params) {
  let finishers = (listeners[name] || []).map((callback) => callback(...params)).filter(isFunction);
  return (result) => runFinishers(finishers, result);
}
async function triggerAsync(name, ...params) {
  let finishers = [];
  for (let callback of listeners[name] || []) {
    let finisher = await callback(...params);
    if (isFunction(finisher)) finishers.push(finisher);
  }
  return (result) => runFinishers(finishers, result);
}
function runFinishers(finishers, result) {
  return finishers.reduce((latest, finisher) => {
    let next = finisher(latest);
    return next === void 0 ? latest : next;
  }, result);
}

// src/modal.js
function showHtmlModal(html) {
  let page = document.createElement("html");
  page.innerHTML = html;
  page.querySelectorAll("a").forEach((a) => a.setAttribute("target", "_top"));
  let modal = document.getElementById("brew-error");
  if (modal) {
    modal.innerHTML = "";
  } else {
    modal = document.createElement("dialog");
    modal.id = "brew-error";
    Object.assign(modal.style, { margin: "50px", width: "calc(100% - 100px)", height: "calc(100% - 100px)", borderRadius: "5px", padding: "0px" });
  }
  let iframe = document.createElement("iframe");
  Object.assign(iframe.style, { backgroundColor: "#17161A", borderRadius: "5px", width: "100%", height: "100%" });
  modal.appendChild(iframe);
  document.body.prepend(modal);
  document.body.style.overflow = "hidden";
  iframe.contentWindow.document.open();
  iframe.contentWindow.document.write(page.outerHTML);
  iframe.contentWindow.document.close();
  modal.addEventListener("click", () => hideHtmlModal(modal));
  modal.addEventListener("close", () => {
    modal.outerHTML = "";
    document.body.style.overflow = "visible";
  });
  modal.showModal();
  modal.focus();
  modal.blur();
}
function hideHtmlModal(modal) {
  modal.close();
}

// src/browser.js
var browser = {
  reload: () => window.location.reload(),
  visit: (url) => {
    window.location.href = url;
  }
};

// src/request/interceptor.js
var noop = () => {
};
var MessageInterceptor = class {
  constructor(message, callback) {
    __publicField(this, "onSend", noop);
    __publicField(this, "onCancel", noop);
    __publicField(this, "onFailure", noop);
    __publicField(this, "onError", noop);
    __publicField(this, "onSuccess", noop);
    __publicField(this, "onSkipped", noop);
    __publicField(this, "onFinish", noop);
    __publicField(this, "onSync", noop);
    __publicField(this, "onEffect", noop);
    __publicField(this, "onMorph", async () => {
    });
    __publicField(this, "onMorphed", noop);
    __publicField(this, "onRender", noop);
    callback({
      message,
      cancel: () => {
        if (!message.interceptors.includes(this)) this.onCancel();
        message.cancel();
      },
      onSend: (cb) => this.onSend = cb,
      onCancel: (cb) => this.onCancel = cb,
      onFailure: (cb) => this.onFailure = cb,
      onError: (cb) => this.onError = cb,
      onSuccess: (cb) => this.onSuccess = cb,
      onSkipped: (cb) => this.onSkipped = cb,
      onFinish: (cb) => this.onFinish = cb
    });
  }
};
var RequestInterceptor = class {
  constructor(request, callback) {
    __publicField(this, "onSend", noop);
    __publicField(this, "onCancel", noop);
    __publicField(this, "onFailure", noop);
    __publicField(this, "onResponse", noop);
    __publicField(this, "onParsed", noop);
    __publicField(this, "onError", noop);
    __publicField(this, "onRedirect", noop);
    __publicField(this, "onSuccess", noop);
    __publicField(this, "onFinish", noop);
    callback({
      request,
      onSend: (cb) => this.onSend = cb,
      onCancel: (cb) => this.onCancel = cb,
      onFailure: (cb) => this.onFailure = cb,
      onResponse: (cb) => this.onResponse = cb,
      onParsed: (cb) => this.onParsed = cb,
      onError: (cb) => this.onError = cb,
      onRedirect: (cb) => this.onRedirect = cb,
      onSuccess: (cb) => this.onSuccess = cb,
      onFinish: (cb) => this.onFinish = cb
    });
  }
};
function remover(list, callback) {
  return () => {
    let index2 = list.indexOf(callback);
    if (index2 !== -1) list.splice(index2, 1);
  };
}
var InterceptorRegistry = class {
  constructor() {
    __publicField(this, "messageCallbacks", []);
    __publicField(this, "componentMessageCallbacks", new WeakBag());
    __publicField(this, "requestCallbacks", []);
  }
  addComponentMessageInterceptor(component, callback) {
    this.componentMessageCallbacks.add(component, callback);
    return remover(this.componentMessageCallbacks.get(component), callback);
  }
  addMessageInterceptor(callback) {
    this.messageCallbacks.push(callback);
    return remover(this.messageCallbacks, callback);
  }
  addRequestInterceptor(callback) {
    this.requestCallbacks.push(callback);
    return remover(this.requestCallbacks, callback);
  }
  messageInterceptorsFor(message) {
    return [...this.componentMessageCallbacks.get(message.component), ...this.messageCallbacks].map((callback) => new MessageInterceptor(message, callback));
  }
  requestInterceptorsFor(request) {
    return [...this.requestCallbacks].map((callback) => new RequestInterceptor(request, callback));
  }
};

// src/request/bus.js
var componentScopes = /* @__PURE__ */ new WeakMap();
function scopeOf(component) {
  if (!componentScopes.has(component)) componentScopes.set(component, Symbol());
  return componentScopes.get(component);
}
var MessageBus = class {
  constructor() {
    __publicField(this, "pendingMessages", /* @__PURE__ */ new Set());
    __publicField(this, "activeMessages", /* @__PURE__ */ new Set());
    __publicField(this, "bufferingMessages", /* @__PURE__ */ new Set());
  }
  /**
   * Hold a message open for 5ms so actions fired in the same tick (and
   * other components' messages) are bundled into a single request.
   */
  buffer(message, callback) {
    if (this.bufferingMessages.has(message)) return;
    this.bufferingMessages.add(message);
    setTimeout(() => {
      callback();
      this.bufferingMessages.delete(message);
    }, 5);
  }
  findPendingMessageFor(action) {
    if (action.isAsync()) return null;
    return [...this.pendingMessages].find((message) => message.component === action.component);
  }
  activeMessagesFor(action) {
    let scope2 = scopeOf(action.component);
    return [...this.activeMessages].filter((message) => message.scope === scope2);
  }
  takePendingMessages() {
    let messages = [...this.pendingMessages];
    this.pendingMessages.clear();
    return messages;
  }
};

// src/request/request.js
var MessageRequest = class {
  constructor() {
    __publicField(this, "messages", /* @__PURE__ */ new Set());
    __publicField(this, "controller", new AbortController());
    __publicField(this, "interceptors", []);
    __publicField(this, "cancelled", false);
    __publicField(this, "isolated", false);
    __publicField(this, "uri", null);
  }
  addMessage(message) {
    message.request = this;
    this.messages.add(message);
  }
  initInterceptors(registry) {
    this.interceptors = registry.requestInterceptorsFor(this);
    this.messages.forEach((message) => {
      message.interceptors = [...registry.messageInterceptorsFor(message), ...message.interceptors];
    });
  }
  cancel() {
    if (this.cancelled) return;
    this.cancelled = true;
    this.controller.abort();
    this.each("onCancel");
    this.messages.forEach((message) => message.cancel());
  }
  hasAllCancelledMessages() {
    return [...this.messages].every((message) => message.isCancelled());
  }
  isCancelled() {
    return this.cancelled;
  }
  each(name, ...args) {
    this.interceptors.forEach((interceptor2) => interceptor2[name](...args));
  }
  invokeOnSend(args) {
    this.each("onSend", args);
    this.messages.forEach((message) => message.invokeOnSend());
  }
  invokeOnFailure({ error: error2 }) {
    this.each("onFailure", { error: error2 });
    this.messages.forEach((message) => message.invokeOnFailure(error2));
  }
  invokeOnError(args) {
    this.each("onError", args);
    this.messages.forEach((message) => message.invokeOnError(args));
  }
};

// src/request/message.js
var ABORTED = { status: null, body: null, json: null, errors: null };
var Message = class {
  constructor(component) {
    __publicField(this, "actions", /* @__PURE__ */ new Set());
    __publicField(this, "snapshot", null);
    __publicField(this, "updates", null);
    __publicField(this, "calls", null);
    __publicField(this, "payload", null);
    __publicField(this, "responsePayload", null);
    __publicField(this, "pendingReturns", []);
    __publicField(this, "pendingReturnsMeta", {});
    __publicField(this, "interceptors", []);
    __publicField(this, "cancelled", false);
    __publicField(this, "skipped", false);
    __publicField(this, "superseded", false);
    __publicField(this, "request", null);
    __publicField(this, "scope", null);
    this.component = component;
  }
  addAction(action) {
    let twin = [...this.actions].find((existing) => existing.fingerprint === action.fingerprint);
    if (twin) {
      twin.addSquashedAction(action);
      return;
    }
    action.message = this;
    this.actions.add(action);
  }
  removeAction(action) {
    this.actions.delete(action);
    action.message = null;
    if (this.actions.size === 0) this.cancel();
  }
  addInterceptor(callback) {
    this.interceptors.push(new MessageInterceptor(this, callback));
  }
  cancel() {
    if (this.cancelled) return;
    this.cancelled = true;
    this.invokeOnCancel();
    if (this.request?.hasAllCancelledMessages()) this.request.cancel();
  }
  isCancelled() {
    return this.cancelled;
  }
  isAsync() {
    return [...this.actions].every((action) => action.isAsync());
  }
  each(name, ...args) {
    this.interceptors.forEach((interceptor2) => interceptor2[name](...args));
  }
  eachAction(name, ...args) {
    this.actions.forEach((action) => action.invoke(name, ...args));
  }
  settleRejected(error2) {
    this.actions.forEach((action) => action.reject(error2));
    this.eachAction("Finish");
    this.each("onFinish");
  }
  invokeOnSend() {
    this.each("onSend", { payload: this.payload });
    [...this.actions].forEach((action, index2) => action.invoke("Send", { call: this.calls[index2] }));
  }
  invokeOnCancel() {
    this.each("onCancel");
    this.settleRejected(ABORTED);
  }
  invokeOnFailure(error2) {
    this.each("onFailure", { error: error2 });
    this.eachAction("Failure", { error: error2 });
    this.settleRejected(ABORTED);
  }
  invokeOnError({ response, body, preventDefault }) {
    this.each("onError", { response, body, preventDefault });
    this.eachAction("Error", { response, body, preventDefault });
    let json = null;
    try {
      json = JSON.parse(body);
    } catch (e) {
    }
    this.settleRejected({ status: response.status, body, json, errors: null });
  }
  invokeOnSuccess() {
    this.interceptors.forEach((interceptor2) => {
      interceptor2.onSuccess({
        payload: this.responsePayload,
        onSync: (cb) => interceptor2.onSync = cb,
        onEffect: (cb) => interceptor2.onEffect = cb,
        onMorph: (cb) => interceptor2.onMorph = cb,
        onMorphed: (cb) => interceptor2.onMorphed = cb,
        onRender: (cb) => interceptor2.onRender = cb
      });
    });
    let effects = this.responsePayload.effects;
    this.pendingReturns = effects.returns || [];
    this.pendingReturnsMeta = effects.returnsMeta || {};
  }
  invokeOnSkipped() {
    this.each("onSkipped");
  }
  async invokeOnMorph() {
    for (let interceptor2 of this.interceptors) {
      await interceptor2.onMorph();
    }
  }
  resolveActionPromises(returns, returnsMeta) {
    ;
    [...this.actions].forEach((action, index2) => {
      let meta = returnsMeta[index2];
      if (meta?.errors || meta?.status) {
        action.reject({ status: meta.errors ? 422 : meta.status, body: null, json: null, errors: meta.errors || null });
      } else {
        action.invoke("Success", returns[index2]);
        action.resolve(returns[index2]);
      }
      action.invoke("Finish");
    });
  }
};

// src/request/action.js
var LIFECYCLE = ["Send", "Cancel", "Success", "Error", "Failure", "Finish"];
var Action = class {
  constructor(component, name, params = [], metadata = {}, origin = null) {
    __publicField(this, "squashedActions", /* @__PURE__ */ new Set());
    __publicField(this, "supersedes", /* @__PURE__ */ new Set());
    __publicField(this, "message", null);
    __publicField(this, "cancelled", false);
    __publicField(this, "deferred", false);
    __publicField(this, "callbacks", Object.fromEntries(LIFECYCLE.map((name) => [name, []])));
    this.component = component;
    this.name = name;
    this.params = params;
    this.metadata = metadata;
    this.origin = origin;
    this.promise = new Promise((resolve, reject) => {
      this.resolution = { resolve, reject };
    });
    this.promise.catch(() => {
    });
  }
  hooks() {
    return {
      action: this,
      onSend: (cb) => this.callbacks.Send.push(cb),
      onCancel: (cb) => this.callbacks.Cancel.push(cb),
      onSuccess: (cb) => this.callbacks.Success.push(cb),
      onError: (cb) => this.callbacks.Error.push(cb),
      onFailure: (cb) => this.callbacks.Failure.push(cb),
      onFinish: (cb) => this.callbacks.Finish.push(cb)
    };
  }
  addInterceptor(callback) {
    callback(this.hooks());
  }
  invoke(name, ...args) {
    this.callbacks[name].forEach((cb) => cb(...args));
    this.squashedActions.forEach((action) => action.invoke(name, ...args));
  }
  cancel() {
    if (this.cancelled) return;
    this.cancelled = true;
    this.invoke("Cancel");
    this.invoke("Finish");
    this.reject({ status: null, body: null, json: null, errors: null });
    this.message?.removeAction(this);
  }
  isCancelled() {
    return this.cancelled;
  }
  defer() {
    this.deferred = true;
  }
  isDeferred() {
    return this.deferred;
  }
  fire() {
    this.fireUsing(this);
  }
  get fingerprint() {
    return JSON.stringify([this.component.id, this.name, this.params, this.metadata]);
  }
  isAsync() {
    let marked = this.component.snapshot.memo.async || [];
    return marked.includes(this.name) || !!this.origin?.directive?.modifiers.includes("async") || !!this.metadata.async;
  }
  addSquashedAction(action) {
    this.squashedActions.add(action);
  }
  resolve(value) {
    this.squashedActions.forEach((action) => action.resolution.resolve(value));
    this.resolution.resolve(value);
  }
  reject(error2) {
    this.squashedActions.forEach((action) => action.resolution.reject(error2));
    this.resolution.reject(error2);
  }
};

// src/request/index.js
var nextOrigin = null;
var nextMetadata = {};
var nextInterceptors = [];
var actionInterceptors = [];
var partitionInterceptors = [];
var interceptors2 = new InterceptorRegistry();
var bus = new MessageBus();
var sessionExpired = false;
function sessionIsExpired() {
  return sessionExpired;
}
function messageBus() {
  return bus;
}
function setNextActionOrigin(origin) {
  nextOrigin = origin;
}
function setNextActionMetadata(metadata) {
  nextMetadata = metadata;
}
function setNextActionInterceptor(callback) {
  nextInterceptors.push(callback);
}
function removable(list, callback) {
  list.push(callback);
  return () => {
    let index2 = list.indexOf(callback);
    if (index2 !== -1) list.splice(index2, 1);
  };
}
function interceptAction(callback) {
  return removable(actionInterceptors, callback);
}
function interceptMessage(callback) {
  return interceptors2.addMessageInterceptor(callback);
}
function interceptRequest(callback) {
  return interceptors2.addRequestInterceptor(callback);
}
function interceptPartition(callback) {
  return removable(partitionInterceptors, callback);
}
function scoped(nameOrCallback, maybeCallback) {
  return typeof nameOrCallback === "string" ? [nameOrCallback, maybeCallback] : [null, nameOrCallback];
}
function hasAction(message, name) {
  return !name || [...message.actions].some((action) => action.name === name);
}
function interceptComponentAction(component, nameOrCallback, maybeCallback) {
  let [name, callback] = scoped(nameOrCallback, maybeCallback);
  return interceptAction((params) => {
    if (params.action.component !== component) return;
    if (name && params.action.name !== name) return;
    callback(params);
  });
}
function interceptComponentMessage(component, nameOrCallback, maybeCallback) {
  let [name, callback] = scoped(nameOrCallback, maybeCallback);
  return interceptors2.addComponentMessageInterceptor(component, (params) => {
    if (hasAction(params.message, name)) callback(params);
  });
}
function interceptComponentRequest(component, nameOrCallback, maybeCallback) {
  let [name, callback] = scoped(nameOrCallback, maybeCallback);
  return interceptRequest((params) => {
    let matches3 = [...params.request.messages].some((message) => message.component === component && hasAction(message, name));
    if (matches3) callback(params);
  });
}
interceptMessage(({ message, onFinish }) => {
  bus.activeMessages.add(message);
  onFinish(() => bus.activeMessages.delete(message));
});
function fireAction(component, method, params = [], metadata = {}) {
  let action = constructAction(component, method, params, metadata);
  [...actionInterceptors].forEach((callback) => callback(action.hooks()));
  if (action.isCancelled() || action.isDeferred()) return action.promise;
  return fireActionInstance(action);
}
function constructAction(component, method, params = [], metadata = {}) {
  let action = new Action(component, method, params, { ...metadata, ...nextMetadata }, nextOrigin);
  action.fireUsing = fireActionInstance;
  nextInterceptors.forEach((callback) => action.addInterceptor(callback));
  nextOrigin = null;
  nextMetadata = {};
  nextInterceptors = [];
  return action;
}
function fireActionInstance(action) {
  let message = createOrAddToPendingMessage(action);
  bus.buffer(message, sendMessages);
  return action.promise;
}
function createOrAddToPendingMessage(action) {
  let message = bus.findPendingMessageFor(action) || new Message(action.component);
  message.addAction(action);
  bus.pendingMessages.add(message);
  return message;
}
function compileRequest(requests, messages) {
  let existing = [...requests].find((request2) => messages.some((message) => request2.messages.has(message)));
  if (existing) {
    messages.forEach((message) => existing.addMessage(message));
    return existing;
  }
  let request = new MessageRequest();
  messages.forEach((message) => request.addMessage(message));
  requests.add(request);
  return request;
}
function sendMessages() {
  let requests = /* @__PURE__ */ new Set();
  [...bus.pendingMessages].forEach((message) => {
    ;
    [...partitionInterceptors].forEach((callback) => callback({
      message,
      compileRequest: (messages) => compileRequest(requests, messages)
    }));
  });
  bus.takePendingMessages().forEach((message) => {
    if ([...requests].some((request2) => request2.messages.has(message))) return;
    let request = [...requests].find((candidate) => !candidate.isolated && ![...candidate.messages].some((m) => m.component === message.component));
    if (request) {
      request.addMessage(message);
    } else {
      compileRequest(requests, [message]);
    }
  });
  requests.forEach((request) => {
    request.messages.forEach((message) => {
      message.snapshot = message.component.getEncodedSnapshotWithLatestChildrenMergedIn();
      message.updates = message.component.getUpdates();
      message.calls = [...message.actions].map((action) => ({
        method: action.name,
        params: action.params,
        metadata: action.metadata
      }));
      message.payload = { snapshot: message.snapshot, updates: message.updates, calls: message.calls };
      message.scope = scopeOf(message.component);
    });
    request.uri = getUpdateUri();
    Object.defineProperty(request, "payload", {
      get: () => ({ _token: getCsrfToken(), components: [...request.messages].map((message) => message.payload) })
    });
    let options;
    Object.defineProperty(request, "options", {
      get: () => options ?? (options = {
        method: "POST",
        body: JSON.stringify(request.payload),
        headers: { "Content-type": "application/json", "X-Brew": "1" },
        signal: request.controller.signal
      })
    });
    request.initInterceptors(interceptors2);
    if (request.hasAllCancelledMessages()) request.cancel();
    sendRequest(request);
  });
}
function preventable() {
  let state = { prevented: false };
  state.preventDefault = () => state.prevented = true;
  return state;
}
function handleError2(request, response, body) {
  let error2 = preventable();
  request.invokeOnError({ response, body, preventDefault: error2.preventDefault });
  if (error2.prevented) return;
  if (response.status === 419) {
    if (sessionExpired) return;
    sessionExpired = true;
    if (confirm("This page has expired.\nWould you like to refresh the page?")) browser.reload();
    return;
  }
  if (response.ok && body.trim() === "") {
    if (confirm("The server returned an empty response.\nWould you like to refresh the page?")) browser.reload();
    return;
  }
  showHtmlModal(body);
}
async function sendRequest(request) {
  let response;
  try {
    if (request.isCancelled()) return;
    let responsePromise = fetch(request.uri, request.options);
    request.invokeOnSend({ responsePromise });
    response = await responsePromise;
  } catch (error2) {
    if (request.isCancelled()) return;
    request.invokeOnFailure({ error: error2 });
    request.each("onFinish");
    return;
  }
  request.each("onResponse", { response });
  let body = await response.text();
  if (request.isCancelled()) return;
  request.each("onParsed", { response, body });
  let json = null;
  if (response.ok && !response.redirected && body.trim() !== "") {
    try {
      json = JSON.parse(body);
    } catch (e) {
      console.error(e);
    }
  }
  if (response.redirected && response.ok) {
    let redirect = preventable();
    request.each("onRedirect", { url: response.url, preventDefault: redirect.preventDefault });
    if (!redirect.prevented) browser.visit(response.url);
    request.messages.forEach((message) => {
      message.resolveActionPromises([], {});
      message.each("onFinish");
    });
  } else if (json === null) {
    handleError2(request, response, body);
  } else {
    request.each("onSuccess", { response, body, json });
    await triggerAsync("payload.intercept", json);
    applyResponse(request, json);
  }
  request.each("onFinish");
}
function applyResponse(request, json) {
  request.messages.forEach((message) => {
    json.components.forEach((payload) => {
      if (message.isCancelled()) return;
      if (payload.skip) {
        if (payload.id !== message.component.id || discardIfSuperseded(message)) return;
        supersedeOlderMessages(message);
        message.responsePayload = payload;
        message.skipped = true;
        message.invokeOnSkipped();
        message.resolveActionPromises([], []);
        message.each("onFinish");
        return;
      }
      let { snapshot: encoded, effects } = payload;
      let snapshot = JSON.parse(encoded);
      if (snapshot.memo.id !== message.component.id || discardIfSuperseded(message)) return;
      supersedeOlderMessages(message);
      message.responsePayload = { snapshot, effects };
      message.invokeOnSuccess();
      if (message.isCancelled()) return;
      let morphed = false;
      module_default.transaction(async () => {
        message.component.mergeNewSnapshot(encoded, effects, message.updates);
        message.each("onSync");
        if (message.isCancelled()) return;
        message.component.processEffects(effects, request);
        message.each("onEffect");
        if (message.isCancelled()) return;
        await message.invokeOnMorph();
        morphed = true;
        message.each("onMorphed");
      }).finally(() => {
        if (!message.isCancelled()) {
          message.resolveActionPromises(message.pendingReturns, message.pendingReturnsMeta);
          message.each("onFinish");
        }
        if (!morphed) return;
        requestAnimationFrame(() => {
          if (!message.isCancelled()) message.each("onRender");
        });
      });
    });
  });
}
function discardIfSuperseded(message) {
  if (!message.superseded) return false;
  message.resolveActionPromises([], []);
  message.each("onFinish");
  return true;
}
function supersedeOlderMessages(message) {
  message.actions.forEach((action) => action.supersedes.forEach((older) => older.superseded = true));
}

// src/features/errors.js
function getErrorsObject(component) {
  let state = component.__errorsState ?? (component.__errorsState = module_default.reactive({ clientErrors: null }));
  component.__lastErrorsSnapshot ?? (component.__lastErrorsSnapshot = component.snapshot);
  return {
    messages() {
      if (component.__lastErrorsSnapshot !== component.snapshot) {
        state.clientErrors = null;
        component.__lastErrorsSnapshot = component.snapshot;
      }
      return state.clientErrors ?? (state.clientErrors = component.snapshot.memo.errors || {});
    },
    keys() {
      return Object.keys(this.messages());
    },
    has(...keys) {
      if (this.isEmpty()) return false;
      if (keys.length === 0 || keys.length === 1 && keys[0] == null) return this.any();
      if (keys.length === 1 && Array.isArray(keys[0])) keys = keys[0];
      for (let key of keys) {
        if (this.first(key) === "") return false;
      }
      return true;
    },
    hasAny(keys) {
      if (this.isEmpty()) return false;
      if (keys.length === 1 && Array.isArray(keys[0])) keys = keys[0];
      for (let key of keys) {
        if (this.has(key)) return true;
      }
      return false;
    },
    missing(...keys) {
      if (keys.length === 1 && Array.isArray(keys[0])) keys = keys[0];
      return !this.hasAny(keys);
    },
    first(key = null) {
      let messages = key === null ? this.all() : this.get(key);
      let firstMessage = messages.length > 0 ? messages[0] : "";
      return Array.isArray(firstMessage) ? firstMessage[0] : firstMessage;
    },
    get(key) {
      return this.messages()[key] || [];
    },
    all() {
      return Object.values(this.messages()).flat();
    },
    isEmpty() {
      return !this.any();
    },
    isNotEmpty() {
      return this.any();
    },
    any() {
      return Object.keys(this.messages()).length > 0;
    },
    count() {
      return Object.values(this.messages()).reduce((total, array) => total + array.length, 0);
    },
    clear(field = null) {
      if (field === null) {
        state.clientErrors = {};
      } else {
        let errors = { ...state.clientErrors ?? (component.snapshot.memo.errors || {}) };
        delete errors[field];
        state.clientErrors = errors;
      }
    }
  };
}

// src/features/refs.js
function findRefEl(component, name) {
  let refEl = component.el.querySelector(`[wire\\:ref="${name}"]`);
  if (!refEl) {
    console.error(`Ref "${name}" not found in component "${component.id}"`);
    return;
  }
  return refEl;
}

// src/directives.js
var customDirectiveNames = /* @__PURE__ */ new Set();
function matchesForBrewDirective(attributeName) {
  return attributeName.startsWith("wire:");
}
function extractDirective(el, name) {
  let [value, ...modifiers] = name.slice("wire:".length).split(".");
  return new Directive(value, modifiers, name, el);
}
function directive2(name, callback) {
  if (customDirectiveNames.has(name)) return;
  customDirectiveNames.add(name);
  on2("directive.init", ({ el, component, directive: directive3, cleanup }) => {
    if (directive3.value === name) callback({ el, directive: directive3, component, $wire: component.$wire, cleanup });
  });
}
function customDirectiveHasBeenRegistered(name) {
  return customDirectiveNames.has(name);
}
function getDirectives(el) {
  return new DirectiveManager(el);
}
var DirectiveManager = class {
  constructor(el) {
    this.el = el;
    this.directives = this.extractTypeModifiersAndValue();
  }
  all() {
    return this.directives;
  }
  has(value) {
    return this.directives.map((directive3) => directive3.value).includes(value);
  }
  missing(value) {
    return !this.has(value);
  }
  get(value) {
    return this.directives.find((directive3) => directive3.value === value);
  }
  extractTypeModifiersAndValue() {
    return Array.from(this.el.getAttributeNames().filter((name) => matchesForBrewDirective(name)).map((name) => extractDirective(this.el, name)));
  }
};
var Directive = class {
  constructor(value, modifiers, rawName, el) {
    this.rawName = this.raw = rawName;
    this.el = el;
    this.value = value;
    this.modifiers = modifiers;
    this.expression = el.getAttribute(rawName);
  }
  get method() {
    return this.methods[0].method;
  }
  get params() {
    return this.methods[0].params;
  }
  get methods() {
    return splitMethods(this.expression).map(({ method, paramString }) => ({ method, params: evaluateParams(paramString) }));
  }
};
function evaluateParams(paramString) {
  if (paramString === "") return [];
  try {
    return module_default.evaluateRaw(document, `__brewArgs(${paramString})`, { scope: { __brewArgs: (...args) => args } });
  } catch (error2) {
    console.warn("Failed to parse parameters:", paramString, error2);
    return [];
  }
}
function splitMethods(expression) {
  let methods = [];
  let current = "";
  let depth = 0;
  let quote = null;
  let source = expression.trim();
  for (let i = 0; i < source.length; i++) {
    let char = source[i];
    if (quote) {
      if (char === quote && source[i - 1] !== "\\") quote = null;
    } else if (char === '"' || char === "'") {
      quote = char;
    } else if (char === "(") {
      depth++;
    } else if (char === ")") {
      depth--;
    } else if (char === "," && depth === 0) {
      methods.push(parseCall(current.trim()));
      current = "";
      continue;
    }
    current += char;
  }
  if (current.trim() !== "") methods.push(parseCall(current.trim()));
  return methods;
}
function parseCall(call) {
  let match = call.match(/^([^(]+)\(/);
  if (!match) return { method: call, paramString: "" };
  return { method: match[1].trim(), paramString: call.slice(match[0].length, call.lastIndexOf(")")).trim() };
}

// src/directives/shared.js
function toggleBooleanStateDirective(el, directive3, isTruthy, cachedDisplay = null) {
  isTruthy = directive3.modifiers.includes("remove") ? !isTruthy : isTruthy;
  if (directive3.modifiers.includes("class")) {
    let classes = directive3.expression.split(" ").filter(String);
    if (isTruthy) el.classList.add(...classes);
    else el.classList.remove(...classes);
  } else if (directive3.modifiers.includes("attr")) {
    if (isTruthy) el.setAttribute(directive3.expression, true);
    else el.removeAttribute(directive3.expression);
  } else {
    let cache = cachedDisplay ?? window.getComputedStyle(el, null).getPropertyValue("display");
    let display = ["inline", "list-item", "block", "table", "flex", "grid", "inline-flex"].filter((i) => directive3.modifiers.includes(i))[0] || "inline-block";
    display = directive3.modifiers.includes("remove") && !isTruthy ? cache : display;
    el.style.display = isTruthy ? display : "none";
  }
}

// src/directives/dirty.js
var refreshDirtyStatesByComponent = new WeakBag();
function checkDirty(component, targets) {
  if (targets === void 0) return !deeplyEqual(component.canonical, component.reactive);
  if (Array.isArray(targets)) {
    return targets.some((target) => !deeplyEqual(dataGet(component.canonical, target), dataGet(component.reactive, target)));
  }
  return !deeplyEqual(dataGet(component.canonical, targets), dataGet(component.reactive, targets));
}
function registerDirtyDirective() {
  on2("commit", ({ component, succeed }) => {
    succeed(() => {
      setTimeout(() => refreshDirtyStatesByComponent.each(component, (i) => i(false)));
    });
  });
  directive2("dirty", ({ el, directive: directive3, component }) => {
    let targets = dirtyTargets(el);
    let oldIsDirty = false;
    let initialDisplay = el.style.display;
    let refreshDirtyState = (isDirty2) => {
      toggleBooleanStateDirective(el, directive3, isDirty2, initialDisplay);
      oldIsDirty = isDirty2;
    };
    refreshDirtyStatesByComponent.add(component, refreshDirtyState);
    module_default.effect(() => {
      let isDirty2 = checkDirty(component, targets.length === 0 ? void 0 : targets);
      if (oldIsDirty !== isDirty2) refreshDirtyState(isDirty2);
      oldIsDirty = isDirty2;
    });
  });
}
function dirtyTargets(el) {
  let directives2 = getDirectives(el);
  let targets = [];
  if (directives2.has("model")) targets.push(directives2.get("model").expression);
  if (directives2.has("target")) {
    targets = targets.concat(
      directives2.get("target").expression.split(",").map((s) => s.trim()).filter(Boolean)
    );
  }
  return targets;
}

// src/evaluator.js
var SKIP = ["JSON", "true", "false", "null", "undefined", "this", "$wire", "$event"];
function alpineScopeKeys(el) {
  let keys = [];
  for (let current = el; current; current = current.parentElement) {
    let own = current._x_dataStack?.[0] || {};
    Object.keys(own).forEach((key) => {
      if (!key.startsWith("$") && !keys.includes(key)) keys.push(key);
    });
    if (current.hasAttribute("wire:id")) break;
  }
  return keys;
}
function reportError(error2, expression, el) {
  console.warn(`Brew Expression Error: ${error2.message}

Expression: "${expression}"

`, el);
  console.error(error2);
}
var reactiveExpressionEvaluationDepth = 0;
function isEvaluatingReactiveExpression() {
  return reactiveExpressionEvaluationDepth > 0;
}
function evaluateReactiveExpression(el, expression, options = {}) {
  reactiveExpressionEvaluationDepth++;
  try {
    return evaluateActionExpression(el, expression, options);
  } finally {
    reactiveExpressionEvaluationDepth--;
  }
}
function evaluateActionExpression(el, expression, options = {}) {
  if (!expression || expression.trim() === "") return;
  try {
    return module_default.evaluateRaw(el, contextualizeExpression(expression, el), options);
  } catch (error2) {
    reportError(error2, expression, el);
  }
}
function contextualizeExpression(expression, el) {
  let scopeKeys = alpineScopeKeys(el);
  let skip = [...SKIP, ...scopeKeys];
  let protectedParts = [];
  let target = actionTargetOffset(expression);
  let result = expression.replace(/(["'`])(?:(?!\1)[^\\]|\\.)*\1|\/\*[\s\S]*?\*\/|\/\/[^\n]*/g, (match) => {
    protectedParts.push(match);
    return `___${protectedParts.length - 1}___`;
  });
  result = result.replace(/(^|[^.\w$])(\$?[a-zA-Z_]\w*)/g, (match, pre, ident, offset2) => {
    let isActionTarget = scopeKeys.includes(ident) && offset2 + pre.length === target;
    if (skip.includes(ident) && !isActionTarget || /^___\d+___$/.test(ident)) return pre + ident;
    if (result[offset2 + match.length] === ":") return pre + ident;
    return pre + "$wire." + ident;
  });
  return result.replace(/___(\d+)___/g, (match, i) => protectedParts[i]);
}
function actionTargetOffset(expression) {
  let match = expression.match(/^(\s*)([a-zA-Z_]\w*)/);
  if (!match) return null;
  let rest = expression.slice(match[0].length).replace(/^(?:\s+|\/\*[\s\S]*?\*\/|\/\/[^\n]*(?:\n|$))*/, "");
  if (rest !== "" && !rest.startsWith("(") && !rest.startsWith(";")) return null;
  return match[1].length;
}

// src/events.js
function dispatch3(component, name, params) {
  dispatchEvent(component.el, name, params);
}
function dispatchGlobal(name, params) {
  dispatchEvent(window, name, params);
}
function dispatchSelf(component, name, params) {
  dispatchEvent(component.el, name, params, false);
}
function dispatchEl(component, selector, name, params) {
  component.el.querySelectorAll(selector).forEach((target) => {
    dispatchEvent(target, name, params, false);
  });
}
function dispatchTo(componentName, name, params) {
  componentsByName(componentName).forEach((target) => {
    dispatchEvent(target.el, name, params, false);
  });
}
function dispatchRef(component, ref, name, params) {
  let el = findRefEl(component, ref);
  if (el) dispatchEvent(el, name, params, false);
}
function listen(component, name, callback) {
  let handler4 = (e) => callback(e.detail);
  component.el.addEventListener(name, handler4);
  return () => component.el.removeEventListener(name, handler4);
}
function on3(eventName, callback) {
  let handler4 = (e) => {
    if (!e.__brew) return;
    callback(e.detail);
  };
  window.addEventListener(eventName, handler4);
  return () => window.removeEventListener(eventName, handler4);
}
function dispatchEvent(target, name, params, bubbles = true) {
  if (typeof params === "string") params = [params];
  let event = new CustomEvent(name, { bubbles, detail: params, composed: true });
  event.__brew = { name, params, receivedBy: [] };
  target.dispatchEvent(event);
}

// src/features/entangle.js
function entangle2(component, name, live = false) {
  let options = { live };
  let api = {
    get() {
      return dataGet(component.reactive, name);
    },
    set(value) {
      dataSet(component.reactive, name, value);
      if (options.live) component.$wire.$set(name, value);
    }
  };
  Object.defineProperty(api, "live", {
    get() {
      options.live = true;
      return api;
    }
  });
  return api;
}

// src/wire.js
var properties = {};
var aliases = {
  el: "$el",
  id: "$id",
  get: "$get",
  set: "$set",
  call: "$call",
  hook: "$hook",
  watch: "$watch",
  dirty: "$dirty",
  commit: "$commit",
  errors: "$errors",
  refs: "$refs",
  on: "$on",
  dispatch: "$dispatch",
  dispatchTo: "$dispatchTo",
  dispatchSelf: "$dispatchSelf",
  dispatchEl: "$dispatchEl",
  dispatchRef: "$dispatchRef",
  intercept: "$intercept",
  interceptAction: "$interceptAction",
  interceptMessage: "$interceptMessage",
  interceptRequest: "$interceptRequest",
  js: "$js",
  entangle: "$entangle"
};
function wireProperty(name, callback) {
  properties[name] = callback;
}
function callServer(component, method, params) {
  if (params.length === 1 && params[0] instanceof Event) params = [];
  if (isEvaluatingReactiveExpression()) {
    console.warn(`Brew: Cannot call server method "${method}" from a reactive binding like wire:text, wire:show, or wire:bind.`, component.el);
    return Promise.resolve();
  }
  return fireAction(component, method, params);
}
function generateWireObject(component, state) {
  return new Proxy({}, {
    get(target, property) {
      if (property === "__instance") return component;
      let name = Object.hasOwn(aliases, property) ? aliases[property] : property;
      if (Object.hasOwn(properties, name)) return properties[name](component);
      if (property in state) return state[property];
      if (property === "toJSON") return () => component.toJSON();
      if (typeof property === "symbol" || property === "then") return void 0;
      return (...params) => callServer(component, property, params);
    },
    set(target, property, value) {
      if (property in state) state[property] = value;
      return true;
    }
  });
}
function watcher(component, cleanup) {
  return (path, callback) => {
    let unwatch = module_default.watch(() => dataGet(component.reactive, path), callback);
    if (cleanup) {
      cleanup(unwatch);
      return unwatch;
    }
    let removeCleanup = component.addCleanup(unwatch);
    return () => {
      removeCleanup();
      unwatch();
    };
  };
}
function safely(callback) {
  try {
    return callback();
  } catch (error2) {
    return Promise.reject(error2);
  }
}
wireProperty("$get", (component) => (path, reactive3 = true) => dataGet(reactive3 ? component.reactive : component.ephemeral, path));
wireProperty("$el", (component) => component.el);
wireProperty("$id", (component) => component.id);
wireProperty("$set", (component) => (path, value, live = true) => {
  dataSet(component.reactive, path, value);
  if (!live) return Promise.resolve();
  component.queueUpdate(path, value);
  return fireAction(component, "$set");
});
wireProperty("$toggle", (component) => (path, live = true) => component.$wire.$set(path, !component.$wire.$get(path), live));
wireProperty("$call", (component) => (method, ...params) => component.$wire[method](...params));
wireProperty("$refresh", (component) => () => safely(() => fireAction(component, "$refresh")));
wireProperty("$commit", (component) => () => safely(() => fireAction(component, "$commit")));
wireProperty("$watch", (component) => watcher(component));
wireProperty("$hook", (component) => (name, callback) => {
  let unhook = on2(name, ({ component: hooked, ...params }) => {
    if (hooked === void 0) return callback(params);
    if (hooked.id === component.id) return callback({ component: hooked, ...params });
  });
  component.addCleanup(unhook);
  return unhook;
});
wireProperty("$intercept", (component) => (name, callback) => interceptComponentAction(component, name, callback));
wireProperty("$interceptAction", (component) => (name, callback) => interceptComponentAction(component, name, callback));
wireProperty("$interceptMessage", (component) => (name, callback) => interceptComponentMessage(component, name, callback));
wireProperty("$interceptRequest", (component) => (name, callback) => interceptComponentRequest(component, name, callback));
wireProperty("$errors", (component) => getErrorsObject(component));
wireProperty("$refs", (component) => {
  let fn = (name) => findRefEl(component, name);
  return new Proxy(fn, {
    get(target, property) {
      if (property in target) return target[property];
      return fn(property);
    }
  });
});
wireProperty("$dirty", (component) => (property) => {
  let reactive3 = module_default.reactive({ dirty: false });
  interceptComponentMessage(component, ({ onFinish }) => {
    onFinish(() => {
      queueMicrotask(() => {
        reactive3.dirty = checkDirty(component, property);
      });
    });
  });
  module_default.effect(() => {
    reactive3.dirty = checkDirty(component, property);
  });
  return reactive3.dirty;
});
wireProperty("$on", (component) => (name, callback) => {
  let off2 = listen(component, name, callback);
  component.addCleanup(off2);
  return off2;
});
wireProperty("$dispatch", (component) => (name, params) => dispatch3(component, name, params));
wireProperty("$dispatchSelf", (component) => (name, params) => dispatchSelf(component, name, params));
wireProperty("$dispatchTo", () => (name, event, params) => dispatchTo(name, event, params));
wireProperty("$dispatchEl", (component) => (selector, name, params) => dispatchEl(component, selector, name, params));
wireProperty("$dispatchRef", (component) => (ref, name, params) => dispatchRef(component, ref, name, params));
wireProperty("$js", (component) => component.$js ?? (component.$js = {}));
wireProperty("$entangle", (component) => (name, live = false) => entangle2(component, name, live));
wireProperty("$parent", (component) => {
  let parent = component.parent;
  return parent ? parent.$wire : new Proxy({}, { get: () => () => {
  } });
});
function registerWireMagic() {
  module_default.magic("wire", (el, { cleanup }) => {
    let component;
    let resolve = () => component ?? (component = findComponentByEl(el, false));
    return new Proxy({}, {
      get(target, property) {
        if (!resolve()) return () => {
        };
        if (property === "$watch" || property === "watch") return watcher(component, cleanup);
        return component.$wire[property];
      },
      set(target, property, value) {
        if (resolve()) component.$wire[property] = value;
        return true;
      }
    });
  });
}

// src/component.js
var Component = class {
  constructor(el) {
    if (el.__brew) throw "Component already initialized";
    el.__brew = this;
    this.el = el;
    this.id = el.getAttribute("wire:id");
    this.key = el.getAttribute("wire:key");
    this.snapshotEncoded = el.getAttribute("wire:snapshot");
    this.snapshot = JSON.parse(this.snapshotEncoded);
    if (!this.snapshot) throw "Snapshot missing on Brew component with id: " + this.id;
    this.name = this.snapshot.memo.name;
    this.effects = JSON.parse(el.getAttribute("wire:effects") || "{}");
    this.canonical = extractData(deepClone(this.snapshot.data));
    this.ephemeral = extractData(deepClone(this.snapshot.data));
    this.reactive = module_default.reactive(this.ephemeral);
    this.queuedUpdates = {};
    this.cleanups = [];
    this.$wire = generateWireObject(this, this.reactive);
    el.$wire = this.$wire;
  }
  mergeNewSnapshot(snapshotEncoded, effects, updates = {}) {
    let snapshot = JSON.parse(snapshotEncoded);
    let previous = deepClone(this.canonical);
    for (let key in updates) dataSet(previous, key, updates[key]);
    this.snapshotEncoded = snapshotEncoded;
    this.snapshot = snapshot;
    this.effects = effects;
    this.canonical = extractData(deepClone(snapshot.data));
    diffAndPatchRecursive(previous, extractData(deepClone(snapshot.data)), this.reactive);
  }
  queueUpdate(path, value) {
    this.queuedUpdates[path] = value;
  }
  getUpdates() {
    let diff = diffAndConsolidate(this.canonical, this.ephemeral);
    Object.entries(this.queuedUpdates).forEach(([path, value]) => {
      Object.keys(diff).forEach((key) => {
        if (key === path || key.startsWith(path + ".")) delete diff[key];
      });
      diff[path] = value;
    });
    this.queuedUpdates = {};
    return diff;
  }
  processEffects(effects, request) {
    trigger2("effects", this, effects);
    trigger2("effect", { component: this, effects, cleanup: (i) => this.addCleanup(i), request });
  }
  get children() {
    return [...this.el.querySelectorAll("[wire\\:id]")].filter((el) => el.parentElement.closest("[wire\\:id]") === this.el && el.__brew).map((el) => el.__brew);
  }
  get parent() {
    return findComponentByEl(this.el.parentElement, false);
  }
  get isIsolated() {
    return !!this.snapshot.memo.isolate;
  }
  getDeepChildren(callback) {
    this.children.forEach((child) => {
      callback(child);
      child.getDeepChildren(callback);
    });
  }
  getDeepChildrenWithBindings(callback) {
    this.getDeepChildren((child) => {
      if (child.snapshot.memo.props || child.snapshot.memo.bindings) callback(child);
    });
  }
  getEncodedSnapshotWithLatestChildrenMergedIn() {
    let children = {};
    this.children.forEach((child) => {
      children[child.key] = [child.el.tagName.toLowerCase(), child.id];
    });
    return this.snapshotEncoded.replace(/"children":\{[^}]*\}/, `"children":${JSON.stringify(children)}`);
  }
  toJSON() {
    return { id: this.id, name: this.name, key: this.key, data: { ...this.ephemeral } };
  }
  addCleanup(cleanup) {
    this.cleanups.push(cleanup);
    return () => {
      this.cleanups = this.cleanups.filter((i) => i !== cleanup);
    };
  }
  cleanup() {
    delete this.el.__brew;
    while (this.cleanups.length > 0) this.cleanups.pop()();
  }
};

// src/store.js
var components = {};
function initComponent(el) {
  let component = new Component(el);
  if (components[component.id]) throw "Component already registered";
  trigger2("component.init", { component, cleanup: (i) => component.addCleanup(i) });
  components[component.id] = component;
  component.processEffects(component.effects);
  trigger2("component.initialized", { component });
  return component;
}
function destroyComponent(id) {
  let component = components[id];
  if (!component) return;
  component.cleanup();
  delete components[id];
}
function hasComponent(id) {
  return !!components[id];
}
function findComponentByEl(el, strict = true) {
  let id = walkUpwards(el, (node) => node.__brew?.id);
  let component = id && components[id];
  if (!component && strict) throw "Could not find Brew component in DOM tree";
  return component || void 0;
}
function componentsByName(name) {
  return Object.values(components).filter((component) => component.name === name);
}
function getByName(name) {
  return componentsByName(name).map((component) => component.$wire);
}
function find(id) {
  return components[id]?.$wire;
}
function first() {
  return Object.values(components)[0]?.$wire;
}
function all() {
  return Object.values(components);
}

// node_modules/@alpinejs/morph/dist/module.esm.js
function morph(from, toHtml, options) {
  monkeyPatchDomSetAttributeToAllowAtSymbols();
  let context = createMorphContext(options);
  let toEl = typeof toHtml === "string" ? createElement(toHtml) : toHtml;
  if (window.Alpine && window.Alpine.closestDataStack && !from._x_dataStack) {
    toEl._x_dataStack = window.Alpine.closestDataStack(from);
    toEl._x_dataStack && window.Alpine.cloneNode(from, toEl);
  }
  context.patch(from, toEl);
  return from;
}
function morphBetween(startMarker, endMarker, toHtml, options = {}) {
  monkeyPatchDomSetAttributeToAllowAtSymbols();
  let context = createMorphContext(options);
  let fromContainer = startMarker.parentNode;
  let fromBlock = new Block(startMarker, endMarker);
  let toContainer = typeof toHtml === "string" ? (() => {
    let container = document.createElement("div");
    container.insertAdjacentHTML("beforeend", toHtml);
    return container;
  })() : toHtml;
  let toStartMarker = document.createComment("[morph-start]");
  let toEndMarker = document.createComment("[morph-end]");
  toContainer.insertBefore(toStartMarker, toContainer.firstChild);
  toContainer.appendChild(toEndMarker);
  let toBlock = new Block(toStartMarker, toEndMarker);
  if (window.Alpine && window.Alpine.closestDataStack) {
    toContainer._x_dataStack = window.Alpine.closestDataStack(fromContainer);
    toContainer._x_dataStack && window.Alpine.cloneNode(fromContainer, toContainer);
  }
  context.patchChildren(fromBlock, toBlock);
}
function createMorphContext(options = {}) {
  let defaultGetKey = (el) => el.getAttribute("key");
  let noop2 = () => {
  };
  let context = {
    key: options.key || defaultGetKey,
    lookahead: options.lookahead || false,
    updating: options.updating || noop2,
    updated: options.updated || noop2,
    removing: options.removing || noop2,
    removed: options.removed || noop2,
    adding: options.adding || noop2,
    added: options.added || noop2
  };
  context.patch = function(from, to) {
    if (context.differentElementNamesTypesOrKeys(from, to)) {
      return context.swapElements(from, to);
    }
    let updateChildrenOnly = false;
    let skipChildren = false;
    let skipUntil = (predicate) => context.skipUntilCondition = predicate;
    if (shouldSkipChildren(context.updating, () => skipChildren = true, skipUntil, from, to, () => updateChildrenOnly = true))
      return;
    if (from.nodeType === 1 && window.Alpine) {
      window.Alpine.cloneNode(from, to);
      if (from._x_teleport && to._x_teleport) {
        context.patch(from._x_teleport, to._x_teleport);
      }
    }
    if (textOrComment(to)) {
      context.patchNodeValue(from, to);
      context.updated(from, to);
      return;
    }
    if (!updateChildrenOnly) {
      context.patchAttributes(from, to);
    }
    context.updated(from, to);
    if (!skipChildren) {
      context.patchChildren(from, to);
    }
  };
  context.differentElementNamesTypesOrKeys = function(from, to) {
    return from.nodeType != to.nodeType || from.nodeName != to.nodeName || context.getKey(from) != context.getKey(to);
  };
  context.swapElements = function(from, to) {
    if (shouldSkip(context.removing, from))
      return;
    let toCloned = to.cloneNode(true);
    if (shouldSkip(context.adding, toCloned))
      return;
    from.replaceWith(toCloned);
    context.removed(from);
    context.added(toCloned);
  };
  context.patchNodeValue = function(from, to) {
    let value = to.nodeValue;
    if (from.nodeValue !== value) {
      from.nodeValue = value;
    }
  };
  context.patchAttributes = function(from, to) {
    if (from._x_transitioning)
      return;
    if (from._x_isShown && !to._x_isShown) {
      return;
    }
    if (!from._x_isShown && to._x_isShown) {
      return;
    }
    let domAttributes = Array.from(from.attributes);
    let toAttributes = Array.from(to.attributes);
    for (let i = domAttributes.length - 1; i >= 0; i--) {
      let name = domAttributes[i].name;
      if (!to.hasAttribute(name)) {
        if (name === "open" && from.nodeName === "DIALOG" && from.open) {
          from.close();
        } else {
          from.removeAttribute(name);
        }
      }
    }
    for (let i = toAttributes.length - 1; i >= 0; i--) {
      let name = toAttributes[i].name;
      let value = toAttributes[i].value;
      if (from.getAttribute(name) !== value) {
        from.setAttribute(name, value);
      }
    }
  };
  context.patchChildren = function(from, to) {
    let fromKeys = context.keyToMap(from.children);
    let fromKeyHoldovers = {};
    let currentTo = getFirstNode(to);
    let currentFrom = getFirstNode(from);
    while (currentTo) {
      seedingMatchingId(currentTo, currentFrom);
      let toKey = context.getKey(currentTo);
      let fromKey = context.getKey(currentFrom);
      if (context.skipUntilCondition) {
        let fromDone = !currentFrom || context.skipUntilCondition(currentFrom);
        let toDone = !currentTo || context.skipUntilCondition(currentTo);
        if (fromDone && toDone) {
          context.skipUntilCondition = null;
        } else {
          if (!fromDone)
            currentFrom = currentFrom && getNextSibling(from, currentFrom);
          if (!toDone)
            currentTo = currentTo && getNextSibling(to, currentTo);
          continue;
        }
      }
      if (!currentFrom) {
        if (toKey && fromKeyHoldovers[toKey]) {
          let holdover = fromKeyHoldovers[toKey];
          from.appendChild(holdover);
          currentFrom = holdover;
          fromKey = context.getKey(currentFrom);
        } else {
          if (!shouldSkip(context.adding, currentTo)) {
            let clone3 = currentTo.cloneNode(true);
            from.appendChild(clone3);
            context.added(clone3);
          }
          currentTo = getNextSibling(to, currentTo);
          continue;
        }
      }
      let isIf = (node) => node && node.nodeType === 8 && node.textContent === "[if BLOCK]><![endif]";
      let isEnd = (node) => node && node.nodeType === 8 && node.textContent === "[if ENDBLOCK]><![endif]";
      if (isIf(currentTo) && isIf(currentFrom)) {
        let nestedIfCount = 0;
        let fromBlockStart = currentFrom;
        while (currentFrom) {
          let next = getNextSibling(from, currentFrom);
          if (isIf(next)) {
            nestedIfCount++;
          } else if (isEnd(next) && nestedIfCount > 0) {
            nestedIfCount--;
          } else if (isEnd(next) && nestedIfCount === 0) {
            currentFrom = next;
            break;
          }
          currentFrom = next;
        }
        let fromBlockEnd = currentFrom;
        nestedIfCount = 0;
        let toBlockStart = currentTo;
        while (currentTo) {
          let next = getNextSibling(to, currentTo);
          if (isIf(next)) {
            nestedIfCount++;
          } else if (isEnd(next) && nestedIfCount > 0) {
            nestedIfCount--;
          } else if (isEnd(next) && nestedIfCount === 0) {
            currentTo = next;
            break;
          }
          currentTo = next;
        }
        let toBlockEnd = currentTo;
        let fromBlock = new Block(fromBlockStart, fromBlockEnd);
        let toBlock = new Block(toBlockStart, toBlockEnd);
        context.patchChildren(fromBlock, toBlock);
        continue;
      }
      if (currentFrom.nodeType === 1 && context.lookahead && !currentFrom.isEqualNode(currentTo)) {
        let nextToElementSibling = getNextSibling(to, currentTo);
        let found = false;
        while (!found && nextToElementSibling) {
          if (nextToElementSibling.nodeType === 1 && currentFrom.isEqualNode(nextToElementSibling)) {
            found = true;
            currentFrom = context.addNodeBefore(from, currentTo, currentFrom);
            fromKey = context.getKey(currentFrom);
          }
          nextToElementSibling = getNextSibling(to, nextToElementSibling);
        }
      }
      if (toKey !== fromKey) {
        if (!toKey && fromKey) {
          fromKeyHoldovers[fromKey] = currentFrom;
          currentFrom = context.addNodeBefore(from, currentTo, currentFrom);
          fromKeyHoldovers[fromKey].remove();
          currentFrom = getNextSibling(from, currentFrom);
          currentTo = getNextSibling(to, currentTo);
          continue;
        }
        if (toKey && !fromKey) {
          if (fromKeys[toKey]) {
            currentFrom.replaceWith(fromKeys[toKey]);
            currentFrom = fromKeys[toKey];
            fromKey = context.getKey(currentFrom);
          }
        }
        if (toKey && fromKey) {
          let fromKeyNode = fromKeys[toKey];
          if (fromKeyNode) {
            fromKeyHoldovers[fromKey] = currentFrom;
            currentFrom.replaceWith(fromKeyNode);
            currentFrom = fromKeyNode;
            fromKey = context.getKey(currentFrom);
          } else {
            fromKeyHoldovers[fromKey] = currentFrom;
            currentFrom = context.addNodeBefore(from, currentTo, currentFrom);
            fromKeyHoldovers[fromKey].remove();
            currentFrom = getNextSibling(from, currentFrom);
            currentTo = getNextSibling(to, currentTo);
            continue;
          }
        }
      }
      let currentFromNext = currentFrom && getNextSibling(from, currentFrom);
      context.patch(currentFrom, currentTo);
      if (currentFrom._x_lastRenderedEl) {
        currentFromNext = getNextSibling(from, currentFrom._x_lastRenderedEl);
      }
      currentTo = currentTo && getNextSibling(to, currentTo);
      currentFrom = currentFromNext;
    }
    let removals = [];
    while (currentFrom) {
      if (!shouldSkip(context.removing, currentFrom))
        removals.push(currentFrom);
      currentFrom = getNextSibling(from, currentFrom);
    }
    while (removals.length) {
      let domForRemoval = removals.shift();
      domForRemoval.remove();
      context.removed(domForRemoval);
    }
  };
  context.getKey = function(el) {
    return el && el.nodeType === 1 && context.key(el);
  };
  context.keyToMap = function(els) {
    let map = {};
    for (let el of els) {
      let theKey = context.getKey(el);
      if (theKey) {
        map[theKey] = el;
      }
    }
    return map;
  };
  context.addNodeBefore = function(parent, node, beforeMe) {
    if (!shouldSkip(context.adding, node)) {
      let clone3 = node.cloneNode(true);
      parent.insertBefore(clone3, beforeMe);
      context.added(clone3);
      return clone3;
    }
    return node;
  };
  return context;
}
morph.step = () => {
};
morph.log = () => {
};
function shouldSkip(hook, ...args) {
  let skip = false;
  hook(...args, () => skip = true);
  return skip;
}
function shouldSkipChildren(hook, skipChildren, skipUntil, ...args) {
  let skip = false;
  hook(...args, () => skip = true, skipChildren, skipUntil);
  return skip;
}
var patched = false;
function createElement(html) {
  const template = document.createElement("template");
  template.innerHTML = html;
  return template.content.firstElementChild;
}
function textOrComment(el) {
  return el.nodeType === 3 || el.nodeType === 8;
}
var Block = class {
  constructor(start3, end) {
    this.startComment = start3;
    this.endComment = end;
  }
  get children() {
    let children = [];
    let currentNode = this.startComment.nextSibling;
    while (currentNode && currentNode !== this.endComment) {
      children.push(currentNode);
      currentNode = currentNode.nextSibling;
    }
    return children;
  }
  appendChild(child) {
    this.endComment.before(child);
  }
  get firstChild() {
    let first2 = this.startComment.nextSibling;
    if (first2 === this.endComment)
      return;
    return first2;
  }
  nextNode(reference) {
    let next = reference.nextSibling;
    if (next === this.endComment)
      return;
    return next;
  }
  insertBefore(newNode, reference) {
    reference.before(newNode);
    return newNode;
  }
};
function getFirstNode(parent) {
  return parent.firstChild;
}
function getNextSibling(parent, reference) {
  let next;
  if (parent instanceof Block) {
    next = parent.nextNode(reference);
  } else {
    next = reference.nextSibling;
  }
  return next;
}
function monkeyPatchDomSetAttributeToAllowAtSymbols() {
  if (patched)
    return;
  patched = true;
  let original = Element.prototype.setAttribute;
  let hostDiv = document.createElement("div");
  Element.prototype.setAttribute = function newSetAttribute(name, value) {
    if (!name.includes("@")) {
      return original.call(this, name, value);
    }
    let escapedValue = value.replace(/&/g, "&amp;").replace(/"/g, "&quot;");
    hostDiv.innerHTML = `<span ${name}="${escapedValue}"></span>`;
    let attr = hostDiv.firstElementChild.getAttributeNode(name);
    hostDiv.firstElementChild.removeAttributeNode(attr);
    this.setAttributeNode(attr);
  };
}
function seedingMatchingId(to, from) {
  let fromId = from && from._x_bindings && from._x_bindings.id;
  if (!fromId)
    return;
  if (!to.setAttribute)
    return;
  to.setAttribute("id", fromId);
  to.id = fromId;
}
function src_default2(Alpine2) {
  Alpine2.morph = morph;
  Alpine2.morphBetween = morphBetween;
}
var module_default2 = src_default2;

// node_modules/@alpinejs/intersect/dist/module.esm.js
function src_default3(Alpine2) {
  Alpine2.directive("intersect", Alpine2.skipDuringClone((el, { value, expression, modifiers }, { evaluateLater: evaluateLater2, cleanup }) => {
    let evaluate3 = evaluateLater2(expression);
    let threshold = getThreshold(modifiers);
    let options = {
      rootMargin: getRootMargin(modifiers),
      threshold,
      root: modifiers.includes("parent") ? el.parentElement : null
    };
    if (modifiers.includes("dwell")) {
      observeForDwell(el, value, modifiers, evaluate3, cleanup, options, threshold);
      return;
    }
    let observer2 = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting === (value === "leave"))
          return;
        evaluate3();
        modifiers.includes("once") && observer2.disconnect();
      });
    }, options);
    observer2.observe(el);
    cleanup(() => {
      observer2.disconnect();
    });
  }));
}
function observeForDwell(el, value, modifiers, evaluate3, cleanup, options, threshold) {
  let dwellTimeout;
  let lastEntry;
  let hasEvaluated = false;
  let isConfirmingDwell = false;
  let duration2 = getDwellDuration(modifiers);
  let observer2;
  let clearDwellTimeout = () => {
    clearTimeout(dwellTimeout);
    dwellTimeout = void 0;
  };
  let completeDwell = () => {
    isConfirmingDwell = false;
    if (!lastEntry || document.hidden || !meetsDwellThreshold(lastEntry, value, threshold))
      return;
    hasEvaluated = true;
    evaluate3();
    if (modifiers.includes("once")) {
      observer2.disconnect();
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    }
  };
  let observe = () => {
    observer2 && observer2.disconnect();
    observer2 = new IntersectionObserver(handleEntries, options);
    observer2.observe(el);
  };
  let confirmDwell = () => {
    dwellTimeout = void 0;
    if (document.hidden)
      return;
    let pendingEntries = observer2.takeRecords();
    if (pendingEntries.some((entry) => !meetsDwellThreshold(entry, value, threshold))) {
      handleEntries(pendingEntries);
      return;
    }
    isConfirmingDwell = true;
    lastEntry = void 0;
    observe();
  };
  let beginDwell = () => {
    if (dwellTimeout !== void 0 || hasEvaluated || document.hidden || !lastEntry)
      return;
    if (!meetsDwellThreshold(lastEntry, value, threshold))
      return;
    dwellTimeout = setTimeout(confirmDwell, duration2);
  };
  let handleEntries = (entries) => {
    entries.forEach((entry) => {
      lastEntry = entry;
      if (!meetsDwellThreshold(entry, value, threshold)) {
        clearDwellTimeout();
        hasEvaluated = false;
        isConfirmingDwell = false;
        return;
      }
      if (isConfirmingDwell) {
        completeDwell();
        return;
      }
      beginDwell();
    });
  };
  let handleVisibilityChange = () => {
    if (document.hidden) {
      clearDwellTimeout();
      lastEntry = void 0;
      hasEvaluated = false;
      isConfirmingDwell = false;
      observer2.disconnect();
      return;
    }
    observe();
  };
  observe();
  document.addEventListener("visibilitychange", handleVisibilityChange);
  cleanup(() => {
    clearDwellTimeout();
    observer2.disconnect();
    document.removeEventListener("visibilitychange", handleVisibilityChange);
  });
}
function meetsDwellThreshold(entry, value, threshold) {
  let meetsThreshold = threshold === 0 ? entry.isIntersecting : entry.isIntersecting && entry.intersectionRatio >= threshold;
  return value === "leave" ? !meetsThreshold : meetsThreshold;
}
function getDwellDuration(modifiers) {
  let rawDuration = modifiers[modifiers.indexOf("dwell") + 1] || "";
  let match = rawDuration.match(/^([0-9]+)(ms)?$/);
  return match ? Number(match[1]) : 250;
}
function getThreshold(modifiers) {
  if (modifiers.includes("full"))
    return 0.99;
  if (modifiers.includes("half"))
    return 0.5;
  if (!modifiers.includes("threshold"))
    return 0;
  let threshold = modifiers[modifiers.indexOf("threshold") + 1];
  if (threshold === "100")
    return 1;
  if (threshold === "0")
    return 0;
  return Number(`.${threshold}`);
}
function getLengthValue(rawValue) {
  let match = rawValue.match(/^(-?[0-9]+)(px|%)?$/);
  return match ? match[1] + (match[2] || "px") : void 0;
}
function getRootMargin(modifiers) {
  const key = "margin";
  const fallback = "0px 0px 0px 0px";
  const index2 = modifiers.indexOf(key);
  if (index2 === -1)
    return fallback;
  let values = [];
  for (let i = 1; i < 5; i++) {
    values.push(getLengthValue(modifiers[index2 + i] || ""));
  }
  values = values.filter((v) => v !== void 0);
  return values.length ? values.join(" ").trim() : fallback;
}
var module_default3 = src_default3;

// node_modules/@alpinejs/collapse/dist/module.esm.js
function src_default4(Alpine2) {
  Alpine2.directive("collapse", collapse);
  collapse.inline = (el, { modifiers }) => {
    if (!modifiers.includes("min"))
      return;
    el._x_doShow = () => {
    };
    el._x_doHide = () => {
    };
  };
  function collapse(el, { modifiers }) {
    let duration2 = modifierValue2(modifiers, "duration", 250) / 1e3;
    let floor2 = modifierValue2(modifiers, "min", 0);
    let fullyHide = !modifiers.includes("min");
    if (!el._x_isShown)
      el.style.height = `${floor2}px`;
    if (!el._x_isShown && fullyHide)
      el.hidden = true;
    if (!el._x_isShown)
      el.style.overflow = "hidden";
    let setFunction = (el2, styles) => {
      let revertFunction = Alpine2.setStyles(el2, styles);
      return styles.height ? () => {
      } : revertFunction;
    };
    let transitionStyles = {
      transitionProperty: "height",
      transitionDuration: `${duration2}s`,
      transitionTimingFunction: "cubic-bezier(0.4, 0.0, 0.2, 1)"
    };
    el._x_transition = {
      in(before = () => {
      }, after = () => {
      }) {
        if (fullyHide)
          el.hidden = false;
        if (fullyHide)
          el.style.display = null;
        let current = el.getBoundingClientRect().height;
        el.style.height = "auto";
        let full = el.getBoundingClientRect().height;
        if (current === full) {
          current = floor2;
        }
        Alpine2.transition(el, Alpine2.setStyles, {
          during: transitionStyles,
          start: { height: current + "px" },
          end: { height: full + "px" }
        }, () => el._x_isShown = true, () => {
          if (Math.abs(el.getBoundingClientRect().height - full) < 1) {
            el.style.overflow = null;
          }
        });
      },
      out(before = () => {
      }, after = () => {
      }) {
        let full = el.getBoundingClientRect().height;
        Alpine2.transition(el, setFunction, {
          during: transitionStyles,
          start: { height: full + "px" },
          end: { height: floor2 + "px" }
        }, () => el.style.overflow = "hidden", () => {
          el._x_isShown = false;
          if (el.style.height == `${floor2}px` && fullyHide) {
            el.style.display = "none";
            el.hidden = true;
          }
        });
      }
    };
  }
}
function modifierValue2(modifiers, key, fallback) {
  if (modifiers.indexOf(key) === -1)
    return fallback;
  const rawValue = modifiers[modifiers.indexOf(key) + 1];
  if (!rawValue)
    return fallback;
  if (key === "duration") {
    let match = rawValue.match(/([0-9]+)ms/);
    if (match)
      return match[1];
  }
  if (key === "min") {
    let match = rawValue.match(/([0-9]+)px/);
    if (match)
      return match[1];
  }
  return rawValue;
}
var module_default4 = src_default4;

// node_modules/@alpinejs/focus/dist/module.esm.js
var candidateSelectors = ["input:not([inert]):not([inert] *)", "select:not([inert]):not([inert] *)", "textarea:not([inert]):not([inert] *)", "a[href]:not([inert]):not([inert] *)", "button:not([inert]):not([inert] *)", "[tabindex]:not(slot):not([inert]):not([inert] *)", "audio[controls]:not([inert]):not([inert] *)", "video[controls]:not([inert]):not([inert] *)", '[contenteditable]:not([contenteditable="false"]):not([inert]):not([inert] *)', "details>summary:first-of-type:not([inert]):not([inert] *)", "details:not([inert]):not([inert] *)"];
var candidateSelector = /* @__PURE__ */ candidateSelectors.join(",");
var NoElement = typeof Element === "undefined";
var matches = NoElement ? function() {
} : Element.prototype.matches || Element.prototype.msMatchesSelector || Element.prototype.webkitMatchesSelector;
var getRootNode = !NoElement && Element.prototype.getRootNode ? function(element) {
  var _element$getRootNode;
  return element === null || element === void 0 ? void 0 : (_element$getRootNode = element.getRootNode) === null || _element$getRootNode === void 0 ? void 0 : _element$getRootNode.call(element);
} : function(element) {
  return element === null || element === void 0 ? void 0 : element.ownerDocument;
};
var _isInert = function isInert(node, lookUp) {
  var _node$getAttribute;
  if (lookUp === void 0) {
    lookUp = true;
  }
  var inertAtt = node === null || node === void 0 ? void 0 : (_node$getAttribute = node.getAttribute) === null || _node$getAttribute === void 0 ? void 0 : _node$getAttribute.call(node, "inert");
  var inert = inertAtt === "" || inertAtt === "true";
  var result = inert || lookUp && node && // closest does not exist on shadow roots, so we fall back to a manual
  // lookup upward, in case it is not defined.
  (typeof node.closest === "function" ? node.closest("[inert]") : _isInert(node.parentNode));
  return result;
};
var isContentEditable = function isContentEditable2(node) {
  var _node$getAttribute2;
  var attValue = node === null || node === void 0 ? void 0 : (_node$getAttribute2 = node.getAttribute) === null || _node$getAttribute2 === void 0 ? void 0 : _node$getAttribute2.call(node, "contenteditable");
  return attValue === "" || attValue === "true";
};
var getCandidates = function getCandidates2(el, includeContainer, filter) {
  if (_isInert(el)) {
    return [];
  }
  var candidates = Array.prototype.slice.apply(el.querySelectorAll(candidateSelector));
  if (includeContainer && matches.call(el, candidateSelector)) {
    candidates.unshift(el);
  }
  candidates = candidates.filter(filter);
  return candidates;
};
var _getCandidatesIteratively = function getCandidatesIteratively(elements, includeContainer, options) {
  var candidates = [];
  var elementsToCheck = Array.from(elements);
  while (elementsToCheck.length) {
    var element = elementsToCheck.shift();
    if (_isInert(element, false)) {
      continue;
    }
    if (element.tagName === "SLOT") {
      var assigned = element.assignedElements();
      var content = assigned.length ? assigned : element.children;
      var nestedCandidates = _getCandidatesIteratively(content, true, options);
      if (options.flatten) {
        candidates.push.apply(candidates, nestedCandidates);
      } else {
        candidates.push({
          scopeParent: element,
          candidates: nestedCandidates
        });
      }
    } else {
      var validCandidate = matches.call(element, candidateSelector);
      if (validCandidate && options.filter(element) && (includeContainer || !elements.includes(element))) {
        candidates.push(element);
      }
      var shadowRoot = element.shadowRoot || // check for an undisclosed shadow
      typeof options.getShadowRoot === "function" && options.getShadowRoot(element);
      var validShadowRoot = !_isInert(shadowRoot, false) && (!options.shadowRootFilter || options.shadowRootFilter(element));
      if (shadowRoot && validShadowRoot) {
        var _nestedCandidates = _getCandidatesIteratively(shadowRoot === true ? element.children : shadowRoot.children, true, options);
        if (options.flatten) {
          candidates.push.apply(candidates, _nestedCandidates);
        } else {
          candidates.push({
            scopeParent: element,
            candidates: _nestedCandidates
          });
        }
      } else {
        elementsToCheck.unshift.apply(elementsToCheck, element.children);
      }
    }
  }
  return candidates;
};
var hasTabIndex = function hasTabIndex2(node) {
  return !isNaN(parseInt(node.getAttribute("tabindex"), 10));
};
var getTabIndex = function getTabIndex2(node) {
  if (!node) {
    throw new Error("No node provided");
  }
  if (node.tabIndex < 0) {
    if ((/^(AUDIO|VIDEO|DETAILS)$/.test(node.tagName) || isContentEditable(node)) && !hasTabIndex(node)) {
      return 0;
    }
  }
  return node.tabIndex;
};
var getSortOrderTabIndex = function getSortOrderTabIndex2(node, isScope) {
  var tabIndex = getTabIndex(node);
  if (tabIndex < 0 && isScope && !hasTabIndex(node)) {
    return 0;
  }
  return tabIndex;
};
var sortOrderedTabbables = function sortOrderedTabbables2(a, b) {
  return a.tabIndex === b.tabIndex ? a.documentOrder - b.documentOrder : a.tabIndex - b.tabIndex;
};
var isInput = function isInput2(node) {
  return node.tagName === "INPUT";
};
var isHiddenInput = function isHiddenInput2(node) {
  return isInput(node) && node.type === "hidden";
};
var isDetailsWithSummary = function isDetailsWithSummary2(node) {
  var r = node.tagName === "DETAILS" && Array.prototype.slice.apply(node.children).some(function(child) {
    return child.tagName === "SUMMARY";
  });
  return r;
};
var getCheckedRadio = function getCheckedRadio2(nodes, form) {
  for (var i = 0; i < nodes.length; i++) {
    if (nodes[i].checked && nodes[i].form === form) {
      return nodes[i];
    }
  }
};
var isTabbableRadio = function isTabbableRadio2(node) {
  if (!node.name) {
    return true;
  }
  var radioScope = node.form || getRootNode(node);
  var queryRadios = function queryRadios2(name) {
    return radioScope.querySelectorAll('input[type="radio"][name="' + name + '"]');
  };
  var radioSet;
  if (typeof window !== "undefined" && typeof window.CSS !== "undefined" && typeof window.CSS.escape === "function") {
    radioSet = queryRadios(window.CSS.escape(node.name));
  } else {
    try {
      radioSet = queryRadios(node.name);
    } catch (err) {
      console.error("Looks like you have a radio button with a name attribute containing invalid CSS selector characters and need the CSS.escape polyfill: %s", err.message);
      return false;
    }
  }
  var checked = getCheckedRadio(radioSet, node.form);
  return !checked || checked === node;
};
var isRadio2 = function isRadio22(node) {
  return isInput(node) && node.type === "radio";
};
var isNonTabbableRadio = function isNonTabbableRadio2(node) {
  return isRadio2(node) && !isTabbableRadio(node);
};
var isNodeAttached = function isNodeAttached2(node) {
  var _nodeRoot;
  var nodeRoot = node && getRootNode(node);
  var nodeRootHost = (_nodeRoot = nodeRoot) === null || _nodeRoot === void 0 ? void 0 : _nodeRoot.host;
  var attached = false;
  if (nodeRoot && nodeRoot !== node) {
    var _nodeRootHost, _nodeRootHost$ownerDo, _node$ownerDocument;
    attached = !!((_nodeRootHost = nodeRootHost) !== null && _nodeRootHost !== void 0 && (_nodeRootHost$ownerDo = _nodeRootHost.ownerDocument) !== null && _nodeRootHost$ownerDo !== void 0 && _nodeRootHost$ownerDo.contains(nodeRootHost) || node !== null && node !== void 0 && (_node$ownerDocument = node.ownerDocument) !== null && _node$ownerDocument !== void 0 && _node$ownerDocument.contains(node));
    while (!attached && nodeRootHost) {
      var _nodeRoot2, _nodeRootHost2, _nodeRootHost2$ownerD;
      nodeRoot = getRootNode(nodeRootHost);
      nodeRootHost = (_nodeRoot2 = nodeRoot) === null || _nodeRoot2 === void 0 ? void 0 : _nodeRoot2.host;
      attached = !!((_nodeRootHost2 = nodeRootHost) !== null && _nodeRootHost2 !== void 0 && (_nodeRootHost2$ownerD = _nodeRootHost2.ownerDocument) !== null && _nodeRootHost2$ownerD !== void 0 && _nodeRootHost2$ownerD.contains(nodeRootHost));
    }
  }
  return attached;
};
var isZeroArea = function isZeroArea2(node) {
  var _node$getBoundingClie = node.getBoundingClientRect(), width = _node$getBoundingClie.width, height = _node$getBoundingClie.height;
  return width === 0 && height === 0;
};
var isHidden = function isHidden2(node, _ref) {
  var displayCheck = _ref.displayCheck, getShadowRoot = _ref.getShadowRoot;
  if (displayCheck === "full-native") {
    if ("checkVisibility" in node) {
      var visible = node.checkVisibility({
        // Checking opacity might be desirable for some use cases, but natively,
        // opacity zero elements _are_ focusable and tabbable.
        checkOpacity: false,
        opacityProperty: false,
        contentVisibilityAuto: true,
        visibilityProperty: true,
        // This is an alias for `visibilityProperty`. Contemporary browsers
        // support both. However, this alias has wider browser support (Chrome
        // >= 105 and Firefox >= 106, vs. Chrome >= 121 and Firefox >= 122), so
        // we include it anyway.
        checkVisibilityCSS: true
      });
      return !visible;
    }
  }
  if (getComputedStyle(node).visibility === "hidden") {
    return true;
  }
  var isDirectSummary = matches.call(node, "details>summary:first-of-type");
  var nodeUnderDetails = isDirectSummary ? node.parentElement : node;
  if (matches.call(nodeUnderDetails, "details:not([open]) *")) {
    return true;
  }
  if (!displayCheck || displayCheck === "full" || // full-native can run this branch when it falls through in case
  // Element#checkVisibility is unsupported
  displayCheck === "full-native" || displayCheck === "legacy-full") {
    if (typeof getShadowRoot === "function") {
      var originalNode = node;
      while (node) {
        var parentElement = node.parentElement;
        var rootNode = getRootNode(node);
        if (parentElement && !parentElement.shadowRoot && getShadowRoot(parentElement) === true) {
          return isZeroArea(node);
        } else if (node.assignedSlot) {
          node = node.assignedSlot;
        } else if (!parentElement && rootNode !== node.ownerDocument) {
          node = rootNode.host;
        } else {
          node = parentElement;
        }
      }
      node = originalNode;
    }
    if (isNodeAttached(node)) {
      return !node.getClientRects().length;
    }
    if (displayCheck !== "legacy-full") {
      return true;
    }
  } else if (displayCheck === "non-zero-area") {
    return isZeroArea(node);
  }
  return false;
};
var isDisabledFromFieldset = function isDisabledFromFieldset2(node) {
  if (/^(INPUT|BUTTON|SELECT|TEXTAREA)$/.test(node.tagName)) {
    var parentNode = node.parentElement;
    while (parentNode) {
      if (parentNode.tagName === "FIELDSET" && parentNode.disabled) {
        for (var i = 0; i < parentNode.children.length; i++) {
          var child = parentNode.children.item(i);
          if (child.tagName === "LEGEND") {
            return matches.call(parentNode, "fieldset[disabled] *") ? true : !child.contains(node);
          }
        }
        return true;
      }
      parentNode = parentNode.parentElement;
    }
  }
  return false;
};
var isNodeMatchingSelectorFocusable = function isNodeMatchingSelectorFocusable2(options, node) {
  if (node.disabled || isHiddenInput(node) || isHidden(node, options) || // For a details element with a summary, the summary element gets the focus
  isDetailsWithSummary(node) || isDisabledFromFieldset(node)) {
    return false;
  }
  return true;
};
var isNodeMatchingSelectorTabbable = function isNodeMatchingSelectorTabbable2(options, node) {
  if (isNonTabbableRadio(node) || getTabIndex(node) < 0 || !isNodeMatchingSelectorFocusable(options, node)) {
    return false;
  }
  return true;
};
var isShadowRootTabbable = function isShadowRootTabbable2(shadowHostNode) {
  var tabIndex = parseInt(shadowHostNode.getAttribute("tabindex"), 10);
  if (isNaN(tabIndex) || tabIndex >= 0) {
    return true;
  }
  return false;
};
var _sortByOrder = function sortByOrder(candidates) {
  var regularTabbables = [];
  var orderedTabbables = [];
  candidates.forEach(function(item, i) {
    var isScope = !!item.scopeParent;
    var element = isScope ? item.scopeParent : item;
    var candidateTabindex = getSortOrderTabIndex(element, isScope);
    var elements = isScope ? _sortByOrder(item.candidates) : element;
    if (candidateTabindex === 0) {
      isScope ? regularTabbables.push.apply(regularTabbables, elements) : regularTabbables.push(element);
    } else {
      orderedTabbables.push({
        documentOrder: i,
        tabIndex: candidateTabindex,
        item,
        isScope,
        content: elements
      });
    }
  });
  return orderedTabbables.sort(sortOrderedTabbables).reduce(function(acc, sortable) {
    sortable.isScope ? acc.push.apply(acc, sortable.content) : acc.push(sortable.content);
    return acc;
  }, []).concat(regularTabbables);
};
var tabbable = function tabbable2(container, options) {
  options = options || {};
  var candidates;
  if (options.getShadowRoot) {
    candidates = _getCandidatesIteratively([container], options.includeContainer, {
      filter: isNodeMatchingSelectorTabbable.bind(null, options),
      flatten: false,
      getShadowRoot: options.getShadowRoot,
      shadowRootFilter: isShadowRootTabbable
    });
  } else {
    candidates = getCandidates(container, options.includeContainer, isNodeMatchingSelectorTabbable.bind(null, options));
  }
  return _sortByOrder(candidates);
};
var focusable = function focusable2(container, options) {
  options = options || {};
  var candidates;
  if (options.getShadowRoot) {
    candidates = _getCandidatesIteratively([container], options.includeContainer, {
      filter: isNodeMatchingSelectorFocusable.bind(null, options),
      flatten: true,
      getShadowRoot: options.getShadowRoot
    });
  } else {
    candidates = getCandidates(container, options.includeContainer, isNodeMatchingSelectorFocusable.bind(null, options));
  }
  return candidates;
};
var isTabbable = function isTabbable2(node, options) {
  options = options || {};
  if (!node) {
    throw new Error("No node provided");
  }
  if (matches.call(node, candidateSelector) === false) {
    return false;
  }
  return isNodeMatchingSelectorTabbable(options, node);
};
var focusableCandidateSelector = /* @__PURE__ */ candidateSelectors.concat("iframe:not([inert]):not([inert] *)").join(",");
var isFocusable = function isFocusable2(node, options) {
  options = options || {};
  if (!node) {
    throw new Error("No node provided");
  }
  if (matches.call(node, focusableCandidateSelector) === false) {
    return false;
  }
  return isNodeMatchingSelectorFocusable(options, node);
};
function _arrayLikeToArray(r, a) {
  (null == a || a > r.length) && (a = r.length);
  for (var e = 0, n = Array(a); e < a; e++)
    n[e] = r[e];
  return n;
}
function _arrayWithoutHoles(r) {
  if (Array.isArray(r))
    return _arrayLikeToArray(r);
}
function asyncGeneratorStep(n, t, e, r, o, a, c) {
  try {
    var i = n[a](c), u = i.value;
  } catch (n2) {
    return void e(n2);
  }
  i.done ? t(u) : Promise.resolve(u).then(r, o);
}
function _asyncToGenerator(n) {
  return function() {
    var t = this, e = arguments;
    return new Promise(function(r, o) {
      var a = n.apply(t, e);
      function _next(n2) {
        asyncGeneratorStep(a, r, o, _next, _throw, "next", n2);
      }
      function _throw(n2) {
        asyncGeneratorStep(a, r, o, _next, _throw, "throw", n2);
      }
      _next(void 0);
    });
  };
}
function _createForOfIteratorHelper(r, e) {
  var t = "undefined" != typeof Symbol && r[Symbol.iterator] || r["@@iterator"];
  if (!t) {
    if (Array.isArray(r) || (t = _unsupportedIterableToArray(r)) || e) {
      t && (r = t);
      var n = 0, F = function() {
      };
      return {
        s: F,
        n: function() {
          return n >= r.length ? {
            done: true
          } : {
            done: false,
            value: r[n++]
          };
        },
        e: function(r2) {
          throw r2;
        },
        f: F
      };
    }
    throw new TypeError("Invalid attempt to iterate non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.");
  }
  var o, a = true, u = false;
  return {
    s: function() {
      t = t.call(r);
    },
    n: function() {
      var r2 = t.next();
      return a = r2.done, r2;
    },
    e: function(r2) {
      u = true, o = r2;
    },
    f: function() {
      try {
        a || null == t.return || t.return();
      } finally {
        if (u)
          throw o;
      }
    }
  };
}
function _defineProperty(e, r, t) {
  return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, {
    value: t,
    enumerable: true,
    configurable: true,
    writable: true
  }) : e[r] = t, e;
}
function _iterableToArray(r) {
  if ("undefined" != typeof Symbol && null != r[Symbol.iterator] || null != r["@@iterator"])
    return Array.from(r);
}
function _nonIterableSpread() {
  throw new TypeError("Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.");
}
function ownKeys(e, r) {
  var t = Object.keys(e);
  if (Object.getOwnPropertySymbols) {
    var o = Object.getOwnPropertySymbols(e);
    r && (o = o.filter(function(r2) {
      return Object.getOwnPropertyDescriptor(e, r2).enumerable;
    })), t.push.apply(t, o);
  }
  return t;
}
function _objectSpread2(e) {
  for (var r = 1; r < arguments.length; r++) {
    var t = null != arguments[r] ? arguments[r] : {};
    r % 2 ? ownKeys(Object(t), true).forEach(function(r2) {
      _defineProperty(e, r2, t[r2]);
    }) : Object.getOwnPropertyDescriptors ? Object.defineProperties(e, Object.getOwnPropertyDescriptors(t)) : ownKeys(Object(t)).forEach(function(r2) {
      Object.defineProperty(e, r2, Object.getOwnPropertyDescriptor(t, r2));
    });
  }
  return e;
}
function _regenerator() {
  var e, t, r = "function" == typeof Symbol ? Symbol : {}, n = r.iterator || "@@iterator", o = r.toStringTag || "@@toStringTag";
  function i(r2, n2, o2, i2) {
    var c2 = n2 && n2.prototype instanceof Generator ? n2 : Generator, u2 = Object.create(c2.prototype);
    return _regeneratorDefine(u2, "_invoke", (function(r3, n3, o3) {
      var i3, c3, u3, f2 = 0, p = o3 || [], y = false, G = {
        p: 0,
        n: 0,
        v: e,
        a: d,
        f: d.bind(e, 4),
        d: function(t2, r4) {
          return i3 = t2, c3 = 0, u3 = e, G.n = r4, a;
        }
      };
      function d(r4, n4) {
        for (c3 = r4, u3 = n4, t = 0; !y && f2 && !o4 && t < p.length; t++) {
          var o4, i4 = p[t], d2 = G.p, l = i4[2];
          r4 > 3 ? (o4 = l === n4) && (u3 = i4[(c3 = i4[4]) ? 5 : (c3 = 3, 3)], i4[4] = i4[5] = e) : i4[0] <= d2 && ((o4 = r4 < 2 && d2 < i4[1]) ? (c3 = 0, G.v = n4, G.n = i4[1]) : d2 < l && (o4 = r4 < 3 || i4[0] > n4 || n4 > l) && (i4[4] = r4, i4[5] = n4, G.n = l, c3 = 0));
        }
        if (o4 || r4 > 1)
          return a;
        throw y = true, n4;
      }
      return function(o4, p2, l) {
        if (f2 > 1)
          throw TypeError("Generator is already running");
        for (y && 1 === p2 && d(p2, l), c3 = p2, u3 = l; (t = c3 < 2 ? e : u3) || !y; ) {
          i3 || (c3 ? c3 < 3 ? (c3 > 1 && (G.n = -1), d(c3, u3)) : G.n = u3 : G.v = u3);
          try {
            if (f2 = 2, i3) {
              if (c3 || (o4 = "next"), t = i3[o4]) {
                if (!(t = t.call(i3, u3)))
                  throw TypeError("iterator result is not an object");
                if (!t.done)
                  return t;
                u3 = t.value, c3 < 2 && (c3 = 0);
              } else
                1 === c3 && (t = i3.return) && t.call(i3), c3 < 2 && (u3 = TypeError("The iterator does not provide a '" + o4 + "' method"), c3 = 1);
              i3 = e;
            } else if ((t = (y = G.n < 0) ? u3 : r3.call(n3, G)) !== a)
              break;
          } catch (t2) {
            i3 = e, c3 = 1, u3 = t2;
          } finally {
            f2 = 1;
          }
        }
        return {
          value: t,
          done: y
        };
      };
    })(r2, o2, i2), true), u2;
  }
  var a = {};
  function Generator() {
  }
  function GeneratorFunction() {
  }
  function GeneratorFunctionPrototype() {
  }
  t = Object.getPrototypeOf;
  var c = [][n] ? t(t([][n]())) : (_regeneratorDefine(t = {}, n, function() {
    return this;
  }), t), u = GeneratorFunctionPrototype.prototype = Generator.prototype = Object.create(c);
  function f(e2) {
    return Object.setPrototypeOf ? Object.setPrototypeOf(e2, GeneratorFunctionPrototype) : (e2.__proto__ = GeneratorFunctionPrototype, _regeneratorDefine(e2, o, "GeneratorFunction")), e2.prototype = Object.create(u), e2;
  }
  return GeneratorFunction.prototype = GeneratorFunctionPrototype, _regeneratorDefine(u, "constructor", GeneratorFunctionPrototype), _regeneratorDefine(GeneratorFunctionPrototype, "constructor", GeneratorFunction), GeneratorFunction.displayName = "GeneratorFunction", _regeneratorDefine(GeneratorFunctionPrototype, o, "GeneratorFunction"), _regeneratorDefine(u), _regeneratorDefine(u, o, "Generator"), _regeneratorDefine(u, n, function() {
    return this;
  }), _regeneratorDefine(u, "toString", function() {
    return "[object Generator]";
  }), (_regenerator = function() {
    return {
      w: i,
      m: f
    };
  })();
}
function _regeneratorDefine(e, r, n, t) {
  var i = Object.defineProperty;
  try {
    i({}, "", {});
  } catch (e2) {
    i = 0;
  }
  _regeneratorDefine = function(e2, r2, n2, t2) {
    function o(r3, n3) {
      _regeneratorDefine(e2, r3, function(e3) {
        return this._invoke(r3, n3, e3);
      });
    }
    r2 ? i ? i(e2, r2, {
      value: n2,
      enumerable: !t2,
      configurable: !t2,
      writable: !t2
    }) : e2[r2] = n2 : (o("next", 0), o("throw", 1), o("return", 2));
  }, _regeneratorDefine(e, r, n, t);
}
function _toConsumableArray(r) {
  return _arrayWithoutHoles(r) || _iterableToArray(r) || _unsupportedIterableToArray(r) || _nonIterableSpread();
}
function _toPrimitive(t, r) {
  if ("object" != typeof t || !t)
    return t;
  var e = t[Symbol.toPrimitive];
  if (void 0 !== e) {
    var i = e.call(t, r);
    if ("object" != typeof i)
      return i;
    throw new TypeError("@@toPrimitive must return a primitive value.");
  }
  return ("string" === r ? String : Number)(t);
}
function _toPropertyKey(t) {
  var i = _toPrimitive(t, "string");
  return "symbol" == typeof i ? i : i + "";
}
function _unsupportedIterableToArray(r, a) {
  if (r) {
    if ("string" == typeof r)
      return _arrayLikeToArray(r, a);
    var t = {}.toString.call(r).slice(8, -1);
    return "Object" === t && r.constructor && (t = r.constructor.name), "Map" === t || "Set" === t ? Array.from(r) : "Arguments" === t || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(t) ? _arrayLikeToArray(r, a) : void 0;
  }
}
var activeFocusTraps = {
  // Returns the trap from the top of the stack.
  getActiveTrap: function getActiveTrap(trapStack) {
    if ((trapStack === null || trapStack === void 0 ? void 0 : trapStack.length) > 0) {
      return trapStack[trapStack.length - 1];
    }
    return null;
  },
  // Pauses the currently active trap, then adds a new trap to the stack.
  activateTrap: function activateTrap(trapStack, trap) {
    var activeTrap = activeFocusTraps.getActiveTrap(trapStack);
    if (trap !== activeTrap) {
      activeFocusTraps.pauseTrap(trapStack);
    }
    var trapIndex = trapStack.indexOf(trap);
    if (trapIndex === -1) {
      trapStack.push(trap);
    } else {
      trapStack.splice(trapIndex, 1);
      trapStack.push(trap);
    }
  },
  // Removes the trap from the top of the stack, then unpauses the next trap down.
  deactivateTrap: function deactivateTrap(trapStack, trap) {
    var trapIndex = trapStack.indexOf(trap);
    if (trapIndex !== -1) {
      trapStack.splice(trapIndex, 1);
    }
    activeFocusTraps.unpauseTrap(trapStack);
  },
  // Pauses the trap at the top of the stack.
  pauseTrap: function pauseTrap(trapStack) {
    var activeTrap = activeFocusTraps.getActiveTrap(trapStack);
    activeTrap === null || activeTrap === void 0 || activeTrap._setPausedState(true);
  },
  // Unpauses the trap at the top of the stack.
  unpauseTrap: function unpauseTrap(trapStack) {
    var activeTrap = activeFocusTraps.getActiveTrap(trapStack);
    if (activeTrap && !activeTrap._isManuallyPaused()) {
      activeTrap._setPausedState(false);
    }
  }
};
var isSelectableInput = function isSelectableInput2(node) {
  return node.tagName && node.tagName.toLowerCase() === "input" && typeof node.select === "function";
};
var isEscapeEvent = function isEscapeEvent2(e) {
  return (e === null || e === void 0 ? void 0 : e.key) === "Escape" || (e === null || e === void 0 ? void 0 : e.key) === "Esc" || (e === null || e === void 0 ? void 0 : e.keyCode) === 27;
};
var isTabEvent = function isTabEvent2(e) {
  return (e === null || e === void 0 ? void 0 : e.key) === "Tab" || (e === null || e === void 0 ? void 0 : e.keyCode) === 9;
};
var isKeyForward = function isKeyForward2(e) {
  return isTabEvent(e) && !e.shiftKey;
};
var isKeyBackward = function isKeyBackward2(e) {
  return isTabEvent(e) && e.shiftKey;
};
var delay = function delay2(fn) {
  return setTimeout(fn, 0);
};
var valueOrHandler = function valueOrHandler2(value) {
  for (var _len = arguments.length, params = new Array(_len > 1 ? _len - 1 : 0), _key = 1; _key < _len; _key++) {
    params[_key - 1] = arguments[_key];
  }
  return typeof value === "function" ? value.apply(void 0, params) : value;
};
var getActualTarget = function getActualTarget2(event) {
  return event.target.shadowRoot && typeof event.composedPath === "function" ? event.composedPath()[0] : event.target;
};
var internalTrapStack = [];
var createFocusTrap = function createFocusTrap2(elements, userOptions) {
  var doc = (userOptions === null || userOptions === void 0 ? void 0 : userOptions.document) || document;
  var trapStack = (userOptions === null || userOptions === void 0 ? void 0 : userOptions.trapStack) || internalTrapStack;
  var config2 = _objectSpread2({
    returnFocusOnDeactivate: true,
    escapeDeactivates: true,
    delayInitialFocus: true,
    isolateSubtrees: false,
    isKeyForward,
    isKeyBackward
  }, userOptions);
  var state = {
    // containers given to createFocusTrap()
    /** @type {Array<HTMLElement>} */
    containers: [],
    // list of objects identifying tabbable nodes in `containers` in the trap
    // NOTE: it's possible that a group has no tabbable nodes if nodes get removed while the trap
    //  is active, but the trap should never get to a state where there isn't at least one group
    //  with at least one tabbable node in it (that would lead to an error condition that would
    //  result in an error being thrown)
    /** @type {Array<{
     *    container: HTMLElement,
     *    tabbableNodes: Array<HTMLElement>, // empty if none
     *    focusableNodes: Array<HTMLElement>, // empty if none
     *    posTabIndexesFound: boolean,
     *    firstTabbableNode: HTMLElement|undefined,
     *    lastTabbableNode: HTMLElement|undefined,
     *    firstDomTabbableNode: HTMLElement|undefined,
     *    lastDomTabbableNode: HTMLElement|undefined,
     *    nextTabbableNode: (node: HTMLElement, forward: boolean) => HTMLElement|undefined
     *  }>}
     */
    containerGroups: [],
    // same order/length as `containers` list
    // references to objects in `containerGroups`, but only those that actually have
    //  tabbable nodes in them
    // NOTE: same order as `containers` and `containerGroups`, but __not necessarily__
    //  the same length
    tabbableGroups: [],
    // references to nodes that are siblings to the ancestors of this trap's containers.
    /** @type {Set<HTMLElement>} */
    adjacentElements: /* @__PURE__ */ new Set(),
    // references to nodes that were inert or aria-hidden before the trap was activated.
    /** @type {Set<HTMLElement>} */
    alreadySilent: /* @__PURE__ */ new Set(),
    nodeFocusedBeforeActivation: null,
    mostRecentlyFocusedNode: null,
    active: false,
    paused: false,
    manuallyPaused: false,
    // timer ID for when delayInitialFocus is true and initial focus in this trap
    //  has been delayed during activation
    delayInitialFocusTimer: void 0,
    // the most recent KeyboardEvent for the configured nav key (typically [SHIFT+]TAB), if any
    recentNavEvent: void 0
  };
  var trap;
  var getOption = function getOption2(configOverrideOptions, optionName, configOptionName) {
    return configOverrideOptions && configOverrideOptions[optionName] !== void 0 ? configOverrideOptions[optionName] : config2[configOptionName || optionName];
  };
  var findContainerIndex = function findContainerIndex2(element, event) {
    var composedPath = typeof (event === null || event === void 0 ? void 0 : event.composedPath) === "function" ? event.composedPath() : void 0;
    return state.containerGroups.findIndex(function(_ref) {
      var container = _ref.container, tabbableNodes = _ref.tabbableNodes;
      return container.contains(element) || // fall back to explicit tabbable search which will take into consideration any
      //  web components if the `tabbableOptions.getShadowRoot` option was used for
      //  the trap, enabling shadow DOM support in tabbable (`Node.contains()` doesn't
      //  look inside web components even if open)
      (composedPath === null || composedPath === void 0 ? void 0 : composedPath.includes(container)) || tabbableNodes.find(function(node) {
        return node === element;
      });
    });
  };
  var getNodeForOption = function getNodeForOption2(optionName) {
    var _ref2 = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : {}, _ref2$hasFallback = _ref2.hasFallback, hasFallback = _ref2$hasFallback === void 0 ? false : _ref2$hasFallback, _ref2$params = _ref2.params, params = _ref2$params === void 0 ? [] : _ref2$params;
    var optionValue = config2[optionName];
    if (typeof optionValue === "function") {
      optionValue = optionValue.apply(void 0, _toConsumableArray(params));
    }
    if (optionValue === true) {
      optionValue = void 0;
    }
    if (!optionValue) {
      if (optionValue === void 0 || optionValue === false) {
        return optionValue;
      }
      throw new Error("`".concat(optionName, "` was specified but was not a node, or did not return a node"));
    }
    var node = optionValue;
    if (typeof optionValue === "string") {
      try {
        node = doc.querySelector(optionValue);
      } catch (err) {
        throw new Error("`".concat(optionName, '` appears to be an invalid selector; error="').concat(err.message, '"'));
      }
      if (!node) {
        if (!hasFallback) {
          throw new Error("`".concat(optionName, "` as selector refers to no known node"));
        }
      }
    }
    return node;
  };
  var getInitialFocusNode = function getInitialFocusNode2() {
    var node = getNodeForOption("initialFocus", {
      hasFallback: true
    });
    if (node === false) {
      return false;
    }
    if (node === void 0 || node && !isFocusable(node, config2.tabbableOptions)) {
      if (findContainerIndex(doc.activeElement) >= 0) {
        node = doc.activeElement;
      } else {
        var firstTabbableGroup = state.tabbableGroups[0];
        var firstTabbableNode = firstTabbableGroup && firstTabbableGroup.firstTabbableNode;
        node = firstTabbableNode || getNodeForOption("fallbackFocus");
      }
    } else if (node === null) {
      node = getNodeForOption("fallbackFocus");
    }
    if (!node) {
      throw new Error("Your focus-trap needs to have at least one focusable element");
    }
    return node;
  };
  var updateTabbableNodes = function updateTabbableNodes2() {
    state.containerGroups = state.containers.map(function(container) {
      var tabbableNodes = tabbable(container, config2.tabbableOptions);
      var focusableNodes = focusable(container, config2.tabbableOptions);
      var firstTabbableNode = tabbableNodes.length > 0 ? tabbableNodes[0] : void 0;
      var lastTabbableNode = tabbableNodes.length > 0 ? tabbableNodes[tabbableNodes.length - 1] : void 0;
      var firstDomTabbableNode = focusableNodes.find(function(node) {
        return isTabbable(node);
      });
      var lastDomTabbableNode = focusableNodes.slice().reverse().find(function(node) {
        return isTabbable(node);
      });
      var posTabIndexesFound = !!tabbableNodes.find(function(node) {
        return getTabIndex(node) > 0;
      });
      return {
        container,
        tabbableNodes,
        focusableNodes,
        /** True if at least one node with positive `tabindex` was found in this container. */
        posTabIndexesFound,
        /** First tabbable node in container, __tabindex__ order; `undefined` if none. */
        firstTabbableNode,
        /** Last tabbable node in container, __tabindex__ order; `undefined` if none. */
        lastTabbableNode,
        // NOTE: DOM order is NOT NECESSARILY "document position" order, but figuring that out
        //  would require more than just https://developer.mozilla.org/en-US/docs/Web/API/Node/compareDocumentPosition
        //  because that API doesn't work with Shadow DOM as well as it should (@see
        //  https://github.com/whatwg/dom/issues/320) and since this first/last is only needed, so far,
        //  to address an edge case related to positive tabindex support, this seems like a much easier,
        //  "close enough most of the time" alternative for positive tabindexes which should generally
        //  be avoided anyway...
        /** First tabbable node in container, __DOM__ order; `undefined` if none. */
        firstDomTabbableNode,
        /** Last tabbable node in container, __DOM__ order; `undefined` if none. */
        lastDomTabbableNode,
        /**
         * Finds the __tabbable__ node that follows the given node in the specified direction,
         *  in this container, if any.
         * @param {HTMLElement} node
         * @param {boolean} [forward] True if going in forward tab order; false if going
         *  in reverse.
         * @returns {HTMLElement|undefined} The next tabbable node, if any.
         */
        nextTabbableNode: function nextTabbableNode(node) {
          var forward = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : true;
          var nodeIdx = tabbableNodes.indexOf(node);
          if (nodeIdx < 0) {
            if (forward) {
              return focusableNodes.slice(focusableNodes.indexOf(node) + 1).find(function(el) {
                return isTabbable(el);
              });
            }
            return focusableNodes.slice(0, focusableNodes.indexOf(node)).reverse().find(function(el) {
              return isTabbable(el);
            });
          }
          return tabbableNodes[nodeIdx + (forward ? 1 : -1)];
        }
      };
    });
    state.tabbableGroups = state.containerGroups.filter(function(group) {
      return group.tabbableNodes.length > 0;
    });
    if (state.tabbableGroups.length <= 0 && !getNodeForOption("fallbackFocus")) {
      throw new Error("Your focus-trap must have at least one container with at least one tabbable node in it at all times");
    }
    if (state.containerGroups.find(function(g) {
      return g.posTabIndexesFound;
    }) && state.containerGroups.length > 1) {
      throw new Error("At least one node with a positive tabindex was found in one of your focus-trap's multiple containers. Positive tabindexes are only supported in single-container focus-traps.");
    }
  };
  var _getActiveElement = function getActiveElement(el) {
    var activeElement = el.activeElement;
    if (!activeElement) {
      return;
    }
    if (activeElement.shadowRoot && activeElement.shadowRoot.activeElement !== null) {
      return _getActiveElement(activeElement.shadowRoot);
    }
    return activeElement;
  };
  var _tryFocus = function tryFocus(node) {
    if (node === false) {
      return;
    }
    if (node === _getActiveElement(document)) {
      return;
    }
    if (!node || !node.focus) {
      _tryFocus(getInitialFocusNode());
      return;
    }
    node.focus({
      preventScroll: !!config2.preventScroll
    });
    state.mostRecentlyFocusedNode = node;
    if (isSelectableInput(node)) {
      node.select();
    }
  };
  var getReturnFocusNode = function getReturnFocusNode2(previousActiveElement) {
    var node = getNodeForOption("setReturnFocus", {
      params: [previousActiveElement]
    });
    return node ? node : node === false ? false : previousActiveElement;
  };
  var findNextNavNode = function findNextNavNode2(_ref3) {
    var target = _ref3.target, event = _ref3.event, _ref3$isBackward = _ref3.isBackward, isBackward = _ref3$isBackward === void 0 ? false : _ref3$isBackward;
    target = target || getActualTarget(event);
    updateTabbableNodes();
    var destinationNode = null;
    if (state.tabbableGroups.length > 0) {
      var containerIndex = findContainerIndex(target, event);
      var containerGroup = containerIndex >= 0 ? state.containerGroups[containerIndex] : void 0;
      if (containerIndex < 0) {
        if (isBackward) {
          destinationNode = state.tabbableGroups[state.tabbableGroups.length - 1].lastTabbableNode;
        } else {
          destinationNode = state.tabbableGroups[0].firstTabbableNode;
        }
      } else if (isBackward) {
        var startOfGroupIndex = state.tabbableGroups.findIndex(function(_ref4) {
          var firstTabbableNode = _ref4.firstTabbableNode;
          return target === firstTabbableNode;
        });
        if (startOfGroupIndex < 0 && (containerGroup.container === target || isFocusable(target, config2.tabbableOptions) && !isTabbable(target, config2.tabbableOptions) && !containerGroup.nextTabbableNode(target, false))) {
          startOfGroupIndex = containerIndex;
        }
        if (startOfGroupIndex >= 0) {
          var destinationGroupIndex = startOfGroupIndex === 0 ? state.tabbableGroups.length - 1 : startOfGroupIndex - 1;
          var destinationGroup = state.tabbableGroups[destinationGroupIndex];
          destinationNode = getTabIndex(target) >= 0 ? destinationGroup.lastTabbableNode : destinationGroup.lastDomTabbableNode;
        } else if (!isTabEvent(event)) {
          destinationNode = containerGroup.nextTabbableNode(target, false);
        }
      } else {
        var lastOfGroupIndex = state.tabbableGroups.findIndex(function(_ref5) {
          var lastTabbableNode = _ref5.lastTabbableNode;
          return target === lastTabbableNode;
        });
        if (lastOfGroupIndex < 0 && (containerGroup.container === target || isFocusable(target, config2.tabbableOptions) && !isTabbable(target, config2.tabbableOptions) && !containerGroup.nextTabbableNode(target))) {
          lastOfGroupIndex = containerIndex;
        }
        if (lastOfGroupIndex >= 0) {
          var _destinationGroupIndex = lastOfGroupIndex === state.tabbableGroups.length - 1 ? 0 : lastOfGroupIndex + 1;
          var _destinationGroup = state.tabbableGroups[_destinationGroupIndex];
          destinationNode = getTabIndex(target) >= 0 ? _destinationGroup.firstTabbableNode : _destinationGroup.firstDomTabbableNode;
        } else if (!isTabEvent(event)) {
          destinationNode = containerGroup.nextTabbableNode(target);
        }
      }
    } else {
      destinationNode = getNodeForOption("fallbackFocus");
    }
    return destinationNode;
  };
  var checkPointerDown = function checkPointerDown2(e) {
    var target = getActualTarget(e);
    if (findContainerIndex(target, e) >= 0) {
      return;
    }
    if (valueOrHandler(config2.clickOutsideDeactivates, e)) {
      trap.deactivate({
        // NOTE: by setting `returnFocus: false`, deactivate() will do nothing,
        //  which will result in the outside click setting focus to the node
        //  that was clicked (and if not focusable, to "nothing"); by setting
        //  `returnFocus: true`, we'll attempt to re-focus the node originally-focused
        //  on activation (or the configured `setReturnFocus` node), whether the
        //  outside click was on a focusable node or not
        returnFocus: config2.returnFocusOnDeactivate
      });
      return;
    }
    if (valueOrHandler(config2.allowOutsideClick, e)) {
      return;
    }
    e.preventDefault();
  };
  var checkFocusIn = function checkFocusIn2(event) {
    var target = getActualTarget(event);
    var targetContained = findContainerIndex(target, event) >= 0;
    if (targetContained || target instanceof Document) {
      if (targetContained) {
        state.mostRecentlyFocusedNode = target;
      }
    } else {
      event.stopImmediatePropagation();
      var nextNode;
      var navAcrossContainers = true;
      if (state.mostRecentlyFocusedNode) {
        if (getTabIndex(state.mostRecentlyFocusedNode) > 0) {
          var mruContainerIdx = findContainerIndex(state.mostRecentlyFocusedNode);
          var tabbableNodes = state.containerGroups[mruContainerIdx].tabbableNodes;
          if (tabbableNodes.length > 0) {
            var mruTabIdx = tabbableNodes.findIndex(function(node) {
              return node === state.mostRecentlyFocusedNode;
            });
            if (mruTabIdx >= 0) {
              if (config2.isKeyForward(state.recentNavEvent)) {
                if (mruTabIdx + 1 < tabbableNodes.length) {
                  nextNode = tabbableNodes[mruTabIdx + 1];
                  navAcrossContainers = false;
                }
              } else {
                if (mruTabIdx - 1 >= 0) {
                  nextNode = tabbableNodes[mruTabIdx - 1];
                  navAcrossContainers = false;
                }
              }
            }
          }
        } else {
          if (!state.containerGroups.some(function(g) {
            return g.tabbableNodes.some(function(n) {
              return getTabIndex(n) > 0;
            });
          })) {
            navAcrossContainers = false;
          }
        }
      } else {
        navAcrossContainers = false;
      }
      if (navAcrossContainers) {
        nextNode = findNextNavNode({
          // move FROM the MRU node, not event-related node (which will be the node that is
          //  outside the trap causing the focus escape we're trying to fix)
          target: state.mostRecentlyFocusedNode,
          isBackward: config2.isKeyBackward(state.recentNavEvent)
        });
      }
      if (nextNode) {
        _tryFocus(nextNode);
      } else {
        _tryFocus(state.mostRecentlyFocusedNode || getInitialFocusNode());
      }
    }
    state.recentNavEvent = void 0;
  };
  var checkKeyNav = function checkKeyNav2(event) {
    var isBackward = arguments.length > 1 && arguments[1] !== void 0 ? arguments[1] : false;
    state.recentNavEvent = event;
    var destinationNode = findNextNavNode({
      event,
      isBackward
    });
    if (destinationNode) {
      if (isTabEvent(event)) {
        event.preventDefault();
      }
      _tryFocus(destinationNode);
    }
  };
  var checkTabKey = function checkTabKey2(event) {
    if (config2.isKeyForward(event) || config2.isKeyBackward(event)) {
      checkKeyNav(event, config2.isKeyBackward(event));
    }
  };
  var checkEscapeKey = function checkEscapeKey2(event) {
    if (isEscapeEvent(event) && valueOrHandler(config2.escapeDeactivates, event) !== false) {
      event.preventDefault();
      trap.deactivate();
    }
  };
  var checkClick = function checkClick2(e) {
    var target = getActualTarget(e);
    if (findContainerIndex(target, e) >= 0) {
      return;
    }
    if (valueOrHandler(config2.clickOutsideDeactivates, e)) {
      return;
    }
    if (valueOrHandler(config2.allowOutsideClick, e)) {
      return;
    }
    e.preventDefault();
    e.stopImmediatePropagation();
  };
  var addListeners = function addListeners2() {
    if (!state.active) {
      return Promise.resolve();
    }
    activeFocusTraps.activateTrap(trapStack, trap);
    var promise;
    if (config2.delayInitialFocus) {
      promise = new Promise(function(resolve) {
        state.delayInitialFocusTimer = delay(function() {
          _tryFocus(getInitialFocusNode());
          resolve();
        });
      });
    } else {
      promise = Promise.resolve();
      _tryFocus(getInitialFocusNode());
    }
    doc.addEventListener("focusin", checkFocusIn, true);
    doc.addEventListener("mousedown", checkPointerDown, {
      capture: true,
      passive: false
    });
    doc.addEventListener("touchstart", checkPointerDown, {
      capture: true,
      passive: false
    });
    doc.addEventListener("click", checkClick, {
      capture: true,
      passive: false
    });
    doc.addEventListener("keydown", checkTabKey, {
      capture: true,
      passive: false
    });
    doc.addEventListener("keydown", checkEscapeKey);
    return promise;
  };
  var collectAdjacentElements = function collectAdjacentElements2(containers) {
    if (state.active && !state.paused) {
      trap._setSubtreeIsolation(false);
    }
    state.adjacentElements.clear();
    state.alreadySilent.clear();
    var containerAncestors = /* @__PURE__ */ new Set();
    var adjacentElements = /* @__PURE__ */ new Set();
    var _iterator = _createForOfIteratorHelper(containers), _step;
    try {
      for (_iterator.s(); !(_step = _iterator.n()).done; ) {
        var container = _step.value;
        containerAncestors.add(container);
        var insideShadowRoot = typeof ShadowRoot !== "undefined" && container.getRootNode() instanceof ShadowRoot;
        var current = container;
        while (current) {
          containerAncestors.add(current);
          var parent = current.parentElement;
          var siblings = [];
          if (parent) {
            siblings = parent.children;
          } else if (!parent && insideShadowRoot) {
            siblings = current.getRootNode().children;
            parent = current.getRootNode().host;
            insideShadowRoot = typeof ShadowRoot !== "undefined" && parent.getRootNode() instanceof ShadowRoot;
          }
          var _iterator2 = _createForOfIteratorHelper(siblings), _step2;
          try {
            for (_iterator2.s(); !(_step2 = _iterator2.n()).done; ) {
              var child = _step2.value;
              adjacentElements.add(child);
            }
          } catch (err) {
            _iterator2.e(err);
          } finally {
            _iterator2.f();
          }
          current = parent;
        }
      }
    } catch (err) {
      _iterator.e(err);
    } finally {
      _iterator.f();
    }
    containerAncestors.forEach(function(el) {
      adjacentElements["delete"](el);
    });
    state.adjacentElements = adjacentElements;
  };
  var removeListeners = function removeListeners2() {
    if (!state.active) {
      return;
    }
    doc.removeEventListener("focusin", checkFocusIn, true);
    doc.removeEventListener("mousedown", checkPointerDown, true);
    doc.removeEventListener("touchstart", checkPointerDown, true);
    doc.removeEventListener("click", checkClick, true);
    doc.removeEventListener("keydown", checkTabKey, true);
    doc.removeEventListener("keydown", checkEscapeKey);
    return trap;
  };
  var checkDomRemoval = function checkDomRemoval2(mutations) {
    var isFocusedNodeRemoved = mutations.some(function(mutation) {
      var removedNodes = Array.from(mutation.removedNodes);
      return removedNodes.some(function(node) {
        return node === state.mostRecentlyFocusedNode;
      });
    });
    if (isFocusedNodeRemoved) {
      _tryFocus(getInitialFocusNode());
    }
  };
  var mutationObserver = typeof window !== "undefined" && "MutationObserver" in window ? new MutationObserver(checkDomRemoval) : void 0;
  var updateObservedNodes = function updateObservedNodes2() {
    if (!mutationObserver) {
      return;
    }
    mutationObserver.disconnect();
    if (state.active && !state.paused) {
      state.containers.map(function(container) {
        mutationObserver.observe(container, {
          subtree: true,
          childList: true
        });
      });
    }
  };
  trap = {
    get active() {
      return state.active;
    },
    get paused() {
      return state.paused;
    },
    activate: function activate(activateOptions) {
      if (state.active) {
        return this;
      }
      var onActivate = getOption(activateOptions, "onActivate");
      var onPostActivate = getOption(activateOptions, "onPostActivate");
      var checkCanFocusTrap = getOption(activateOptions, "checkCanFocusTrap");
      var preexistingTrap = activeFocusTraps.getActiveTrap(trapStack);
      var revertState = false;
      if (preexistingTrap && !preexistingTrap.paused) {
        var _preexistingTrap$_set;
        (_preexistingTrap$_set = preexistingTrap._setSubtreeIsolation) === null || _preexistingTrap$_set === void 0 || _preexistingTrap$_set.call(preexistingTrap, false);
        revertState = true;
      }
      try {
        if (!checkCanFocusTrap) {
          updateTabbableNodes();
        }
        state.active = true;
        state.paused = false;
        state.nodeFocusedBeforeActivation = _getActiveElement(doc);
        onActivate === null || onActivate === void 0 || onActivate();
        var finishActivation = /* @__PURE__ */ (function() {
          var _ref6 = _asyncToGenerator(/* @__PURE__ */ _regenerator().m(function _callee() {
            return _regenerator().w(function(_context) {
              while (1)
                switch (_context.n) {
                  case 0:
                    if (checkCanFocusTrap) {
                      updateTabbableNodes();
                    }
                    _context.n = 1;
                    return addListeners();
                  case 1:
                    trap._setSubtreeIsolation(true);
                    updateObservedNodes();
                    onPostActivate === null || onPostActivate === void 0 || onPostActivate();
                  case 2:
                    return _context.a(2);
                }
            }, _callee);
          }));
          return function finishActivation2() {
            return _ref6.apply(this, arguments);
          };
        })();
        if (checkCanFocusTrap) {
          checkCanFocusTrap(state.containers.concat()).then(finishActivation, finishActivation);
          return this;
        }
        finishActivation();
      } catch (error2) {
        if (preexistingTrap === activeFocusTraps.getActiveTrap(trapStack) && revertState) {
          var _preexistingTrap$_set2;
          (_preexistingTrap$_set2 = preexistingTrap._setSubtreeIsolation) === null || _preexistingTrap$_set2 === void 0 || _preexistingTrap$_set2.call(preexistingTrap, true);
        }
        throw error2;
      }
      return this;
    },
    deactivate: function deactivate(deactivateOptions) {
      if (!state.active) {
        return this;
      }
      var options = _objectSpread2({
        onDeactivate: config2.onDeactivate,
        onPostDeactivate: config2.onPostDeactivate,
        checkCanReturnFocus: config2.checkCanReturnFocus
      }, deactivateOptions);
      clearTimeout(state.delayInitialFocusTimer);
      state.delayInitialFocusTimer = void 0;
      if (!state.paused) {
        trap._setSubtreeIsolation(false);
      }
      state.alreadySilent.clear();
      removeListeners();
      state.active = false;
      state.paused = false;
      updateObservedNodes();
      activeFocusTraps.deactivateTrap(trapStack, trap);
      var onDeactivate = getOption(options, "onDeactivate");
      var onPostDeactivate = getOption(options, "onPostDeactivate");
      var checkCanReturnFocus = getOption(options, "checkCanReturnFocus");
      var returnFocus = getOption(options, "returnFocus", "returnFocusOnDeactivate");
      onDeactivate === null || onDeactivate === void 0 || onDeactivate();
      var finishDeactivation = function finishDeactivation2() {
        delay(function() {
          if (returnFocus) {
            _tryFocus(getReturnFocusNode(state.nodeFocusedBeforeActivation));
          }
          onPostDeactivate === null || onPostDeactivate === void 0 || onPostDeactivate();
        });
      };
      if (returnFocus && checkCanReturnFocus) {
        checkCanReturnFocus(getReturnFocusNode(state.nodeFocusedBeforeActivation)).then(finishDeactivation, finishDeactivation);
        return this;
      }
      finishDeactivation();
      return this;
    },
    pause: function pause(pauseOptions) {
      if (!state.active) {
        return this;
      }
      state.manuallyPaused = true;
      return this._setPausedState(true, pauseOptions);
    },
    unpause: function unpause(unpauseOptions) {
      if (!state.active) {
        return this;
      }
      state.manuallyPaused = false;
      if (trapStack[trapStack.length - 1] !== this) {
        return this;
      }
      return this._setPausedState(false, unpauseOptions);
    },
    updateContainerElements: function updateContainerElements(containerElements) {
      var elementsAsArray = [].concat(containerElements).filter(Boolean);
      state.containers = elementsAsArray.map(function(element) {
        return typeof element === "string" ? doc.querySelector(element) : element;
      });
      if (config2.isolateSubtrees) {
        collectAdjacentElements(state.containers);
      }
      if (state.active) {
        updateTabbableNodes();
        if (!state.paused) {
          trap._setSubtreeIsolation(true);
        }
      }
      updateObservedNodes();
      return this;
    }
  };
  Object.defineProperties(trap, {
    _isManuallyPaused: {
      value: function value() {
        return state.manuallyPaused;
      }
    },
    _setPausedState: {
      value: function value(paused, options) {
        if (state.paused === paused) {
          return this;
        }
        state.paused = paused;
        if (paused) {
          var onPause = getOption(options, "onPause");
          var onPostPause = getOption(options, "onPostPause");
          onPause === null || onPause === void 0 || onPause();
          removeListeners();
          trap._setSubtreeIsolation(false);
          updateObservedNodes();
          onPostPause === null || onPostPause === void 0 || onPostPause();
        } else {
          var onUnpause = getOption(options, "onUnpause");
          var onPostUnpause = getOption(options, "onPostUnpause");
          onUnpause === null || onUnpause === void 0 || onUnpause();
          var finishUnpause = /* @__PURE__ */ (function() {
            var _ref7 = _asyncToGenerator(/* @__PURE__ */ _regenerator().m(function _callee2() {
              return _regenerator().w(function(_context2) {
                while (1)
                  switch (_context2.n) {
                    case 0:
                      updateTabbableNodes();
                      _context2.n = 1;
                      return addListeners();
                    case 1:
                      trap._setSubtreeIsolation(true);
                      updateObservedNodes();
                      onPostUnpause === null || onPostUnpause === void 0 || onPostUnpause();
                    case 2:
                      return _context2.a(2);
                  }
              }, _callee2);
            }));
            return function finishUnpause2() {
              return _ref7.apply(this, arguments);
            };
          })();
          finishUnpause();
        }
        return this;
      }
    },
    _setSubtreeIsolation: {
      value: function value(isEnabled) {
        if (config2.isolateSubtrees) {
          state.adjacentElements.forEach(function(el) {
            var _el$getAttribute;
            if (isEnabled) {
              switch (config2.isolateSubtrees) {
                case "aria-hidden":
                  if (el.ariaHidden === "true" || ((_el$getAttribute = el.getAttribute("aria-hidden")) === null || _el$getAttribute === void 0 ? void 0 : _el$getAttribute.toLowerCase()) === "true") {
                    state.alreadySilent.add(el);
                  }
                  el.setAttribute("aria-hidden", "true");
                  break;
                default:
                  if (el.inert || el.hasAttribute("inert")) {
                    state.alreadySilent.add(el);
                  }
                  el.setAttribute("inert", true);
                  break;
              }
            } else {
              if (state.alreadySilent.has(el))
                ;
              else {
                switch (config2.isolateSubtrees) {
                  case "aria-hidden":
                    el.removeAttribute("aria-hidden");
                    break;
                  default:
                    el.removeAttribute("inert");
                    break;
                }
              }
            }
          });
        }
      }
    }
  });
  trap.updateContainerElements(elements);
  return trap;
};
function src_default5(Alpine2) {
  let lastFocused;
  let currentFocused;
  window.addEventListener("focusin", () => {
    lastFocused = currentFocused;
    currentFocused = document.activeElement;
  });
  Alpine2.magic("focus", (el) => {
    let within = el;
    return {
      __noscroll: false,
      __wrapAround: false,
      within(el2) {
        within = el2;
        return this;
      },
      withoutScrolling() {
        this.__noscroll = true;
        return this;
      },
      noscroll() {
        this.__noscroll = true;
        return this;
      },
      withWrapAround() {
        this.__wrapAround = true;
        return this;
      },
      wrap() {
        return this.withWrapAround();
      },
      focusable(el2) {
        return isFocusable(el2);
      },
      previouslyFocused() {
        return lastFocused;
      },
      lastFocused() {
        return lastFocused;
      },
      focused() {
        return currentFocused;
      },
      focusables() {
        if (Array.isArray(within))
          return within;
        return focusable(within, { displayCheck: "none" });
      },
      all() {
        return this.focusables();
      },
      isFirst(el2) {
        let els = this.all();
        return els[0] && els[0].isSameNode(el2);
      },
      isLast(el2) {
        let els = this.all();
        return els.length && els.slice(-1)[0].isSameNode(el2);
      },
      getFirst() {
        return this.all()[0];
      },
      getLast() {
        return this.all().slice(-1)[0];
      },
      getNext() {
        let list = this.all();
        let current = document.activeElement;
        if (list.indexOf(current) === -1)
          return;
        if (this.__wrapAround && list.indexOf(current) === list.length - 1) {
          return list[0];
        }
        return list[list.indexOf(current) + 1];
      },
      getPrevious() {
        let list = this.all();
        let current = document.activeElement;
        if (list.indexOf(current) === -1)
          return;
        if (this.__wrapAround && list.indexOf(current) === 0) {
          return list.slice(-1)[0];
        }
        return list[list.indexOf(current) - 1];
      },
      first() {
        this.focus(this.getFirst());
      },
      last() {
        this.focus(this.getLast());
      },
      next() {
        this.focus(this.getNext());
      },
      previous() {
        this.focus(this.getPrevious());
      },
      prev() {
        return this.previous();
      },
      focus(el2) {
        if (!el2)
          return;
        setTimeout(() => {
          if (!el2.hasAttribute("tabindex"))
            el2.setAttribute("tabindex", "0");
          el2.focus({ preventScroll: this.__noscroll });
        });
      }
    };
  });
  Alpine2.directive("trap", Alpine2.skipDuringClone(
    (el, { expression, modifiers }, { effect: effect3, evaluateLater: evaluateLater2, cleanup }) => {
      let evaluator = evaluateLater2(expression);
      let oldValue = false;
      let options = {
        escapeDeactivates: false,
        allowOutsideClick: true,
        fallbackFocus: () => el
      };
      let undoInert = () => {
      };
      if (modifiers.includes("noautofocus")) {
        options.initialFocus = false;
      } else {
        let autofocusEl = el.querySelector("[autofocus]");
        if (autofocusEl)
          options.initialFocus = autofocusEl;
      }
      if (modifiers.includes("inert")) {
        options.onPostActivate = () => {
          Alpine2.nextTick(() => {
            undoInert = setInert(el);
          });
        };
      }
      let trap = createFocusTrap(el, options);
      let undoDisableScrolling = () => {
      };
      const releaseFocus = () => {
        undoInert();
        undoInert = () => {
        };
        undoDisableScrolling();
        undoDisableScrolling = () => {
        };
        trap.deactivate({
          returnFocus: !modifiers.includes("noreturn")
        });
      };
      effect3(() => evaluator((value) => {
        if (oldValue === value)
          return;
        if (value && !oldValue) {
          if (modifiers.includes("noscroll"))
            undoDisableScrolling = disableScrolling();
          setTimeout(() => {
            trap.activate();
          }, 15);
        }
        if (!value && oldValue) {
          releaseFocus();
        }
        oldValue = !!value;
      }));
      cleanup(releaseFocus);
    },
    // When cloning, we only want to add aria-hidden attributes to the
    // DOM and not try to actually trap, as trapping can mess with the
    // live DOM and isn't just isolated to the cloned DOM.
    (el, { expression, modifiers }, { evaluate: evaluate3 }) => {
      if (modifiers.includes("inert") && evaluate3(expression))
        setInert(el);
    }
  ));
}
function setInert(el) {
  let undos = [];
  crawlSiblingsUp(el, (sibling) => {
    let cache = sibling.hasAttribute("aria-hidden");
    sibling.setAttribute("aria-hidden", "true");
    undos.push(() => cache || sibling.removeAttribute("aria-hidden"));
  });
  return () => {
    while (undos.length)
      undos.pop()();
  };
}
function crawlSiblingsUp(el, callback) {
  if (el.isSameNode(document.body) || !el.parentNode)
    return;
  Array.from(el.parentNode.children).forEach((sibling) => {
    if (sibling.isSameNode(el)) {
      crawlSiblingsUp(el.parentNode, callback);
    } else {
      callback(sibling);
    }
  });
}
function disableScrolling() {
  let overflow = document.documentElement.style.overflow;
  let paddingRight = document.documentElement.style.paddingRight;
  let scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;
  let scrollbarGutter = window.getComputedStyle(document.documentElement).scrollbarGutter;
  document.documentElement.style.overflow = "hidden";
  if (scrollbarGutter && scrollbarGutter !== "auto") {
    return () => {
      document.documentElement.style.overflow = overflow;
    };
  }
  document.documentElement.style.paddingRight = `${scrollbarWidth}px`;
  return () => {
    document.documentElement.style.overflow = overflow;
    document.documentElement.style.paddingRight = paddingRight;
  };
}
var module_default5 = src_default5;

// node_modules/@alpinejs/persist/dist/module.esm.js
function src_default6(Alpine2) {
  let persist = () => {
    let alias;
    let storage;
    try {
      storage = localStorage;
    } catch (e) {
      console.error(e);
      console.warn("Alpine: $persist is using temporary storage since localStorage is unavailable.");
      let dummy = /* @__PURE__ */ new Map();
      storage = {
        getItem: (key) => dummy.has(key) ? dummy.get(key) : null,
        setItem: dummy.set.bind(dummy)
      };
    }
    return Alpine2.interceptor((initialValue, getter, setter, path, key, cleanup = () => {
    }) => {
      let lookup = alias || `_x_${path}`;
      let initial = storageHas(lookup, storage) ? storageGet(lookup, storage) : initialValue;
      setter(initial);
      let effect3 = Alpine2.effect(() => {
        let value = getter();
        storageSet(lookup, value, storage);
        setter(value);
      });
      cleanup(() => Alpine2.release(effect3));
      return initial;
    }, (func) => {
      func.as = (key) => {
        alias = key;
        return func;
      }, func.using = (target) => {
        storage = target;
        return func;
      };
    });
  };
  Object.defineProperty(Alpine2, "$persist", { get: () => persist() });
  Alpine2.magic("persist", persist);
  Alpine2.persist = (key, { get: get2, set: set2 }, storage = localStorage) => {
    let initial = storageHas(key, storage) ? storageGet(key, storage) : get2();
    set2(initial);
    Alpine2.effect(() => {
      let value = get2();
      storageSet(key, value, storage);
      set2(value);
    });
  };
}
function storageHas(key, storage) {
  let value = storage.getItem(key);
  return value !== null && value !== void 0;
}
function storageGet(key, storage) {
  let value = storage.getItem(key);
  if (value === void 0)
    return;
  return JSON.parse(value);
}
function storageSet(key, value, storage) {
  if (value === void 0) {
    storage.removeItem?.(key);
    return;
  }
  storage.setItem(key, JSON.stringify(value));
}
var module_default6 = src_default6;

// node_modules/@alpinejs/anchor/dist/module.esm.js
var min = Math.min;
var max = Math.max;
var round = Math.round;
var floor = Math.floor;
var createCoords = (v) => ({
  x: v,
  y: v
});
var oppositeSideMap = {
  left: "right",
  right: "left",
  bottom: "top",
  top: "bottom"
};
var oppositeAlignmentMap = {
  start: "end",
  end: "start"
};
function clamp(start3, value, end) {
  return max(start3, min(value, end));
}
function evaluate2(value, param) {
  return typeof value === "function" ? value(param) : value;
}
function getSide(placement) {
  return placement.split("-")[0];
}
function getAlignment(placement) {
  return placement.split("-")[1];
}
function getOppositeAxis(axis) {
  return axis === "x" ? "y" : "x";
}
function getAxisLength(axis) {
  return axis === "y" ? "height" : "width";
}
function getSideAxis(placement) {
  return ["top", "bottom"].includes(getSide(placement)) ? "y" : "x";
}
function getAlignmentAxis(placement) {
  return getOppositeAxis(getSideAxis(placement));
}
function getAlignmentSides(placement, rects, rtl) {
  if (rtl === void 0) {
    rtl = false;
  }
  const alignment = getAlignment(placement);
  const alignmentAxis = getAlignmentAxis(placement);
  const length = getAxisLength(alignmentAxis);
  let mainAlignmentSide = alignmentAxis === "x" ? alignment === (rtl ? "end" : "start") ? "right" : "left" : alignment === "start" ? "bottom" : "top";
  if (rects.reference[length] > rects.floating[length]) {
    mainAlignmentSide = getOppositePlacement(mainAlignmentSide);
  }
  return [mainAlignmentSide, getOppositePlacement(mainAlignmentSide)];
}
function getExpandedPlacements(placement) {
  const oppositePlacement = getOppositePlacement(placement);
  return [getOppositeAlignmentPlacement(placement), oppositePlacement, getOppositeAlignmentPlacement(oppositePlacement)];
}
function getOppositeAlignmentPlacement(placement) {
  return placement.replace(/start|end/g, (alignment) => oppositeAlignmentMap[alignment]);
}
function getSideList(side, isStart, rtl) {
  const lr = ["left", "right"];
  const rl = ["right", "left"];
  const tb = ["top", "bottom"];
  const bt = ["bottom", "top"];
  switch (side) {
    case "top":
    case "bottom":
      if (rtl)
        return isStart ? rl : lr;
      return isStart ? lr : rl;
    case "left":
    case "right":
      return isStart ? tb : bt;
    default:
      return [];
  }
}
function getOppositeAxisPlacements(placement, flipAlignment, direction, rtl) {
  const alignment = getAlignment(placement);
  let list = getSideList(getSide(placement), direction === "start", rtl);
  if (alignment) {
    list = list.map((side) => side + "-" + alignment);
    if (flipAlignment) {
      list = list.concat(list.map(getOppositeAlignmentPlacement));
    }
  }
  return list;
}
function getOppositePlacement(placement) {
  return placement.replace(/left|right|bottom|top/g, (side) => oppositeSideMap[side]);
}
function expandPaddingObject(padding) {
  return {
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    ...padding
  };
}
function getPaddingObject(padding) {
  return typeof padding !== "number" ? expandPaddingObject(padding) : {
    top: padding,
    right: padding,
    bottom: padding,
    left: padding
  };
}
function rectToClientRect(rect) {
  return {
    ...rect,
    top: rect.y,
    left: rect.x,
    right: rect.x + rect.width,
    bottom: rect.y + rect.height
  };
}
function computeCoordsFromPlacement(_ref, placement, rtl) {
  let {
    reference,
    floating
  } = _ref;
  const sideAxis = getSideAxis(placement);
  const alignmentAxis = getAlignmentAxis(placement);
  const alignLength = getAxisLength(alignmentAxis);
  const side = getSide(placement);
  const isVertical = sideAxis === "y";
  const commonX = reference.x + reference.width / 2 - floating.width / 2;
  const commonY = reference.y + reference.height / 2 - floating.height / 2;
  const commonAlign = reference[alignLength] / 2 - floating[alignLength] / 2;
  let coords;
  switch (side) {
    case "top":
      coords = {
        x: commonX,
        y: reference.y - floating.height
      };
      break;
    case "bottom":
      coords = {
        x: commonX,
        y: reference.y + reference.height
      };
      break;
    case "right":
      coords = {
        x: reference.x + reference.width,
        y: commonY
      };
      break;
    case "left":
      coords = {
        x: reference.x - floating.width,
        y: commonY
      };
      break;
    default:
      coords = {
        x: reference.x,
        y: reference.y
      };
  }
  switch (getAlignment(placement)) {
    case "start":
      coords[alignmentAxis] -= commonAlign * (rtl && isVertical ? -1 : 1);
      break;
    case "end":
      coords[alignmentAxis] += commonAlign * (rtl && isVertical ? -1 : 1);
      break;
  }
  return coords;
}
var computePosition = async (reference, floating, config2) => {
  const {
    placement = "bottom",
    strategy = "absolute",
    middleware = [],
    platform: platform2
  } = config2;
  const validMiddleware = middleware.filter(Boolean);
  const rtl = await (platform2.isRTL == null ? void 0 : platform2.isRTL(floating));
  let rects = await platform2.getElementRects({
    reference,
    floating,
    strategy
  });
  let {
    x,
    y
  } = computeCoordsFromPlacement(rects, placement, rtl);
  let statefulPlacement = placement;
  let middlewareData = {};
  let resetCount = 0;
  for (let i = 0; i < validMiddleware.length; i++) {
    const {
      name,
      fn
    } = validMiddleware[i];
    const {
      x: nextX,
      y: nextY,
      data: data2,
      reset
    } = await fn({
      x,
      y,
      initialPlacement: placement,
      placement: statefulPlacement,
      strategy,
      middlewareData,
      rects,
      platform: platform2,
      elements: {
        reference,
        floating
      }
    });
    x = nextX != null ? nextX : x;
    y = nextY != null ? nextY : y;
    middlewareData = {
      ...middlewareData,
      [name]: {
        ...middlewareData[name],
        ...data2
      }
    };
    if (reset && resetCount <= 50) {
      resetCount++;
      if (typeof reset === "object") {
        if (reset.placement) {
          statefulPlacement = reset.placement;
        }
        if (reset.rects) {
          rects = reset.rects === true ? await platform2.getElementRects({
            reference,
            floating,
            strategy
          }) : reset.rects;
        }
        ({
          x,
          y
        } = computeCoordsFromPlacement(rects, statefulPlacement, rtl));
      }
      i = -1;
      continue;
    }
  }
  return {
    x,
    y,
    placement: statefulPlacement,
    strategy,
    middlewareData
  };
};
async function detectOverflow(state, options) {
  var _await$platform$isEle;
  if (options === void 0) {
    options = {};
  }
  const {
    x,
    y,
    platform: platform2,
    rects,
    elements,
    strategy
  } = state;
  const {
    boundary = "clippingAncestors",
    rootBoundary = "viewport",
    elementContext = "floating",
    altBoundary = false,
    padding = 0
  } = evaluate2(options, state);
  const paddingObject = getPaddingObject(padding);
  const altContext = elementContext === "floating" ? "reference" : "floating";
  const element = elements[altBoundary ? altContext : elementContext];
  const clippingClientRect = rectToClientRect(await platform2.getClippingRect({
    element: ((_await$platform$isEle = await (platform2.isElement == null ? void 0 : platform2.isElement(element))) != null ? _await$platform$isEle : true) ? element : element.contextElement || await (platform2.getDocumentElement == null ? void 0 : platform2.getDocumentElement(elements.floating)),
    boundary,
    rootBoundary,
    strategy
  }));
  const rect = elementContext === "floating" ? {
    ...rects.floating,
    x,
    y
  } : rects.reference;
  const offsetParent = await (platform2.getOffsetParent == null ? void 0 : platform2.getOffsetParent(elements.floating));
  const offsetScale = await (platform2.isElement == null ? void 0 : platform2.isElement(offsetParent)) ? await (platform2.getScale == null ? void 0 : platform2.getScale(offsetParent)) || {
    x: 1,
    y: 1
  } : {
    x: 1,
    y: 1
  };
  const elementClientRect = rectToClientRect(platform2.convertOffsetParentRelativeRectToViewportRelativeRect ? await platform2.convertOffsetParentRelativeRectToViewportRelativeRect({
    rect,
    offsetParent,
    strategy
  }) : rect);
  return {
    top: (clippingClientRect.top - elementClientRect.top + paddingObject.top) / offsetScale.y,
    bottom: (elementClientRect.bottom - clippingClientRect.bottom + paddingObject.bottom) / offsetScale.y,
    left: (clippingClientRect.left - elementClientRect.left + paddingObject.left) / offsetScale.x,
    right: (elementClientRect.right - clippingClientRect.right + paddingObject.right) / offsetScale.x
  };
}
var flip = function(options) {
  if (options === void 0) {
    options = {};
  }
  return {
    name: "flip",
    options,
    async fn(state) {
      var _middlewareData$arrow, _middlewareData$flip;
      const {
        placement,
        middlewareData,
        rects,
        initialPlacement,
        platform: platform2,
        elements
      } = state;
      const {
        mainAxis: checkMainAxis = true,
        crossAxis: checkCrossAxis = true,
        fallbackPlacements: specifiedFallbackPlacements,
        fallbackStrategy = "bestFit",
        fallbackAxisSideDirection = "none",
        flipAlignment = true,
        ...detectOverflowOptions
      } = evaluate2(options, state);
      if ((_middlewareData$arrow = middlewareData.arrow) != null && _middlewareData$arrow.alignmentOffset) {
        return {};
      }
      const side = getSide(placement);
      const isBasePlacement = getSide(initialPlacement) === initialPlacement;
      const rtl = await (platform2.isRTL == null ? void 0 : platform2.isRTL(elements.floating));
      const fallbackPlacements = specifiedFallbackPlacements || (isBasePlacement || !flipAlignment ? [getOppositePlacement(initialPlacement)] : getExpandedPlacements(initialPlacement));
      if (!specifiedFallbackPlacements && fallbackAxisSideDirection !== "none") {
        fallbackPlacements.push(...getOppositeAxisPlacements(initialPlacement, flipAlignment, fallbackAxisSideDirection, rtl));
      }
      const placements2 = [initialPlacement, ...fallbackPlacements];
      const overflow = await detectOverflow(state, detectOverflowOptions);
      const overflows = [];
      let overflowsData = ((_middlewareData$flip = middlewareData.flip) == null ? void 0 : _middlewareData$flip.overflows) || [];
      if (checkMainAxis) {
        overflows.push(overflow[side]);
      }
      if (checkCrossAxis) {
        const sides2 = getAlignmentSides(placement, rects, rtl);
        overflows.push(overflow[sides2[0]], overflow[sides2[1]]);
      }
      overflowsData = [...overflowsData, {
        placement,
        overflows
      }];
      if (!overflows.every((side2) => side2 <= 0)) {
        var _middlewareData$flip2, _overflowsData$filter;
        const nextIndex = (((_middlewareData$flip2 = middlewareData.flip) == null ? void 0 : _middlewareData$flip2.index) || 0) + 1;
        const nextPlacement = placements2[nextIndex];
        if (nextPlacement) {
          return {
            data: {
              index: nextIndex,
              overflows: overflowsData
            },
            reset: {
              placement: nextPlacement
            }
          };
        }
        let resetPlacement = (_overflowsData$filter = overflowsData.filter((d) => d.overflows[0] <= 0).sort((a, b) => a.overflows[1] - b.overflows[1])[0]) == null ? void 0 : _overflowsData$filter.placement;
        if (!resetPlacement) {
          switch (fallbackStrategy) {
            case "bestFit": {
              var _overflowsData$map$so;
              const placement2 = (_overflowsData$map$so = overflowsData.map((d) => [d.placement, d.overflows.filter((overflow2) => overflow2 > 0).reduce((acc, overflow2) => acc + overflow2, 0)]).sort((a, b) => a[1] - b[1])[0]) == null ? void 0 : _overflowsData$map$so[0];
              if (placement2) {
                resetPlacement = placement2;
              }
              break;
            }
            case "initialPlacement":
              resetPlacement = initialPlacement;
              break;
          }
        }
        if (placement !== resetPlacement) {
          return {
            reset: {
              placement: resetPlacement
            }
          };
        }
      }
      return {};
    }
  };
};
async function convertValueToCoords(state, options) {
  const {
    placement,
    platform: platform2,
    elements
  } = state;
  const rtl = await (platform2.isRTL == null ? void 0 : platform2.isRTL(elements.floating));
  const side = getSide(placement);
  const alignment = getAlignment(placement);
  const isVertical = getSideAxis(placement) === "y";
  const mainAxisMulti = ["left", "top"].includes(side) ? -1 : 1;
  const crossAxisMulti = rtl && isVertical ? -1 : 1;
  const rawValue = evaluate2(options, state);
  let {
    mainAxis,
    crossAxis,
    alignmentAxis
  } = typeof rawValue === "number" ? {
    mainAxis: rawValue,
    crossAxis: 0,
    alignmentAxis: null
  } : {
    mainAxis: 0,
    crossAxis: 0,
    alignmentAxis: null,
    ...rawValue
  };
  if (alignment && typeof alignmentAxis === "number") {
    crossAxis = alignment === "end" ? alignmentAxis * -1 : alignmentAxis;
  }
  return isVertical ? {
    x: crossAxis * crossAxisMulti,
    y: mainAxis * mainAxisMulti
  } : {
    x: mainAxis * mainAxisMulti,
    y: crossAxis * crossAxisMulti
  };
}
var offset = function(options) {
  if (options === void 0) {
    options = 0;
  }
  return {
    name: "offset",
    options,
    async fn(state) {
      const {
        x,
        y
      } = state;
      const diffCoords = await convertValueToCoords(state, options);
      return {
        x: x + diffCoords.x,
        y: y + diffCoords.y,
        data: diffCoords
      };
    }
  };
};
var shift = function(options) {
  if (options === void 0) {
    options = {};
  }
  return {
    name: "shift",
    options,
    async fn(state) {
      const {
        x,
        y,
        placement
      } = state;
      const {
        mainAxis: checkMainAxis = true,
        crossAxis: checkCrossAxis = false,
        limiter = {
          fn: (_ref) => {
            let {
              x: x2,
              y: y2
            } = _ref;
            return {
              x: x2,
              y: y2
            };
          }
        },
        ...detectOverflowOptions
      } = evaluate2(options, state);
      const coords = {
        x,
        y
      };
      const overflow = await detectOverflow(state, detectOverflowOptions);
      const crossAxis = getSideAxis(getSide(placement));
      const mainAxis = getOppositeAxis(crossAxis);
      let mainAxisCoord = coords[mainAxis];
      let crossAxisCoord = coords[crossAxis];
      if (checkMainAxis) {
        const minSide = mainAxis === "y" ? "top" : "left";
        const maxSide = mainAxis === "y" ? "bottom" : "right";
        const min2 = mainAxisCoord + overflow[minSide];
        const max2 = mainAxisCoord - overflow[maxSide];
        mainAxisCoord = clamp(min2, mainAxisCoord, max2);
      }
      if (checkCrossAxis) {
        const minSide = crossAxis === "y" ? "top" : "left";
        const maxSide = crossAxis === "y" ? "bottom" : "right";
        const min2 = crossAxisCoord + overflow[minSide];
        const max2 = crossAxisCoord - overflow[maxSide];
        crossAxisCoord = clamp(min2, crossAxisCoord, max2);
      }
      const limitedCoords = limiter.fn({
        ...state,
        [mainAxis]: mainAxisCoord,
        [crossAxis]: crossAxisCoord
      });
      return {
        ...limitedCoords,
        data: {
          x: limitedCoords.x - x,
          y: limitedCoords.y - y
        }
      };
    }
  };
};
function getNodeName(node) {
  if (isNode(node)) {
    return (node.nodeName || "").toLowerCase();
  }
  return "#document";
}
function getWindow(node) {
  var _node$ownerDocument;
  return (node == null ? void 0 : (_node$ownerDocument = node.ownerDocument) == null ? void 0 : _node$ownerDocument.defaultView) || window;
}
function getDocumentElement(node) {
  var _ref;
  return (_ref = (isNode(node) ? node.ownerDocument : node.document) || window.document) == null ? void 0 : _ref.documentElement;
}
function isNode(value) {
  return value instanceof Node || value instanceof getWindow(value).Node;
}
function isElement(value) {
  return value instanceof Element || value instanceof getWindow(value).Element;
}
function isHTMLElement(value) {
  return value instanceof HTMLElement || value instanceof getWindow(value).HTMLElement;
}
function isShadowRoot(value) {
  if (typeof ShadowRoot === "undefined") {
    return false;
  }
  return value instanceof ShadowRoot || value instanceof getWindow(value).ShadowRoot;
}
function isOverflowElement(element) {
  const {
    overflow,
    overflowX,
    overflowY,
    display
  } = getComputedStyle2(element);
  return /auto|scroll|overlay|hidden|clip/.test(overflow + overflowY + overflowX) && !["inline", "contents"].includes(display);
}
function isTableElement(element) {
  return ["table", "td", "th"].includes(getNodeName(element));
}
function isContainingBlock(element) {
  const webkit = isWebKit();
  const css2 = getComputedStyle2(element);
  return css2.transform !== "none" || css2.perspective !== "none" || (css2.containerType ? css2.containerType !== "normal" : false) || !webkit && (css2.backdropFilter ? css2.backdropFilter !== "none" : false) || !webkit && (css2.filter ? css2.filter !== "none" : false) || ["transform", "perspective", "filter"].some((value) => (css2.willChange || "").includes(value)) || ["paint", "layout", "strict", "content"].some((value) => (css2.contain || "").includes(value));
}
function getContainingBlock(element) {
  let currentNode = getParentNode(element);
  while (isHTMLElement(currentNode) && !isLastTraversableNode(currentNode)) {
    if (isContainingBlock(currentNode)) {
      return currentNode;
    } else {
      currentNode = getParentNode(currentNode);
    }
  }
  return null;
}
function isWebKit() {
  if (typeof CSS === "undefined" || !CSS.supports)
    return false;
  return CSS.supports("-webkit-backdrop-filter", "none");
}
function isLastTraversableNode(node) {
  return ["html", "body", "#document"].includes(getNodeName(node));
}
function getComputedStyle2(element) {
  return getWindow(element).getComputedStyle(element);
}
function getNodeScroll(element) {
  if (isElement(element)) {
    return {
      scrollLeft: element.scrollLeft,
      scrollTop: element.scrollTop
    };
  }
  return {
    scrollLeft: element.pageXOffset,
    scrollTop: element.pageYOffset
  };
}
function getParentNode(node) {
  if (getNodeName(node) === "html") {
    return node;
  }
  const result = (
    // Step into the shadow DOM of the parent of a slotted node.
    node.assignedSlot || // DOM Element detected.
    node.parentNode || // ShadowRoot detected.
    isShadowRoot(node) && node.host || // Fallback.
    getDocumentElement(node)
  );
  return isShadowRoot(result) ? result.host : result;
}
function getNearestOverflowAncestor(node) {
  const parentNode = getParentNode(node);
  if (isLastTraversableNode(parentNode)) {
    return node.ownerDocument ? node.ownerDocument.body : node.body;
  }
  if (isHTMLElement(parentNode) && isOverflowElement(parentNode)) {
    return parentNode;
  }
  return getNearestOverflowAncestor(parentNode);
}
function getOverflowAncestors(node, list, traverseIframes) {
  var _node$ownerDocument2;
  if (list === void 0) {
    list = [];
  }
  if (traverseIframes === void 0) {
    traverseIframes = true;
  }
  const scrollableAncestor = getNearestOverflowAncestor(node);
  const isBody = scrollableAncestor === ((_node$ownerDocument2 = node.ownerDocument) == null ? void 0 : _node$ownerDocument2.body);
  const win = getWindow(scrollableAncestor);
  if (isBody) {
    return list.concat(win, win.visualViewport || [], isOverflowElement(scrollableAncestor) ? scrollableAncestor : [], win.frameElement && traverseIframes ? getOverflowAncestors(win.frameElement) : []);
  }
  return list.concat(scrollableAncestor, getOverflowAncestors(scrollableAncestor, [], traverseIframes));
}
function getCssDimensions(element) {
  const css2 = getComputedStyle2(element);
  let width = parseFloat(css2.width) || 0;
  let height = parseFloat(css2.height) || 0;
  const hasOffset = isHTMLElement(element);
  const offsetWidth = hasOffset ? element.offsetWidth : width;
  const offsetHeight = hasOffset ? element.offsetHeight : height;
  const shouldFallback = round(width) !== offsetWidth || round(height) !== offsetHeight;
  if (shouldFallback) {
    width = offsetWidth;
    height = offsetHeight;
  }
  return {
    width,
    height,
    $: shouldFallback
  };
}
function unwrapElement(element) {
  return !isElement(element) ? element.contextElement : element;
}
function getScale(element) {
  const domElement = unwrapElement(element);
  if (!isHTMLElement(domElement)) {
    return createCoords(1);
  }
  const rect = domElement.getBoundingClientRect();
  const {
    width,
    height,
    $
  } = getCssDimensions(domElement);
  let x = ($ ? round(rect.width) : rect.width) / width;
  let y = ($ ? round(rect.height) : rect.height) / height;
  if (!x || !Number.isFinite(x)) {
    x = 1;
  }
  if (!y || !Number.isFinite(y)) {
    y = 1;
  }
  return {
    x,
    y
  };
}
var noOffsets = /* @__PURE__ */ createCoords(0);
function getVisualOffsets(element) {
  const win = getWindow(element);
  if (!isWebKit() || !win.visualViewport) {
    return noOffsets;
  }
  return {
    x: win.visualViewport.offsetLeft,
    y: win.visualViewport.offsetTop
  };
}
function shouldAddVisualOffsets(element, isFixed, floatingOffsetParent) {
  if (isFixed === void 0) {
    isFixed = false;
  }
  if (!floatingOffsetParent || isFixed && floatingOffsetParent !== getWindow(element)) {
    return false;
  }
  return isFixed;
}
function getBoundingClientRect(element, includeScale, isFixedStrategy, offsetParent) {
  if (includeScale === void 0) {
    includeScale = false;
  }
  if (isFixedStrategy === void 0) {
    isFixedStrategy = false;
  }
  const clientRect = element.getBoundingClientRect();
  const domElement = unwrapElement(element);
  let scale = createCoords(1);
  if (includeScale) {
    if (offsetParent) {
      if (isElement(offsetParent)) {
        scale = getScale(offsetParent);
      }
    } else {
      scale = getScale(element);
    }
  }
  const visualOffsets = shouldAddVisualOffsets(domElement, isFixedStrategy, offsetParent) ? getVisualOffsets(domElement) : createCoords(0);
  let x = (clientRect.left + visualOffsets.x) / scale.x;
  let y = (clientRect.top + visualOffsets.y) / scale.y;
  let width = clientRect.width / scale.x;
  let height = clientRect.height / scale.y;
  if (domElement) {
    const win = getWindow(domElement);
    const offsetWin = offsetParent && isElement(offsetParent) ? getWindow(offsetParent) : offsetParent;
    let currentIFrame = win.frameElement;
    while (currentIFrame && offsetParent && offsetWin !== win) {
      const iframeScale = getScale(currentIFrame);
      const iframeRect = currentIFrame.getBoundingClientRect();
      const css2 = getComputedStyle2(currentIFrame);
      const left = iframeRect.left + (currentIFrame.clientLeft + parseFloat(css2.paddingLeft)) * iframeScale.x;
      const top = iframeRect.top + (currentIFrame.clientTop + parseFloat(css2.paddingTop)) * iframeScale.y;
      x *= iframeScale.x;
      y *= iframeScale.y;
      width *= iframeScale.x;
      height *= iframeScale.y;
      x += left;
      y += top;
      currentIFrame = getWindow(currentIFrame).frameElement;
    }
  }
  return rectToClientRect({
    width,
    height,
    x,
    y
  });
}
function convertOffsetParentRelativeRectToViewportRelativeRect(_ref) {
  let {
    rect,
    offsetParent,
    strategy
  } = _ref;
  const isOffsetParentAnElement = isHTMLElement(offsetParent);
  const documentElement = getDocumentElement(offsetParent);
  if (offsetParent === documentElement) {
    return rect;
  }
  let scroll = {
    scrollLeft: 0,
    scrollTop: 0
  };
  let scale = createCoords(1);
  const offsets = createCoords(0);
  if (isOffsetParentAnElement || !isOffsetParentAnElement && strategy !== "fixed") {
    if (getNodeName(offsetParent) !== "body" || isOverflowElement(documentElement)) {
      scroll = getNodeScroll(offsetParent);
    }
    if (isHTMLElement(offsetParent)) {
      const offsetRect = getBoundingClientRect(offsetParent);
      scale = getScale(offsetParent);
      offsets.x = offsetRect.x + offsetParent.clientLeft;
      offsets.y = offsetRect.y + offsetParent.clientTop;
    }
  }
  return {
    width: rect.width * scale.x,
    height: rect.height * scale.y,
    x: rect.x * scale.x - scroll.scrollLeft * scale.x + offsets.x,
    y: rect.y * scale.y - scroll.scrollTop * scale.y + offsets.y
  };
}
function getClientRects(element) {
  return Array.from(element.getClientRects());
}
function getWindowScrollBarX(element) {
  return getBoundingClientRect(getDocumentElement(element)).left + getNodeScroll(element).scrollLeft;
}
function getDocumentRect(element) {
  const html = getDocumentElement(element);
  const scroll = getNodeScroll(element);
  const body = element.ownerDocument.body;
  const width = max(html.scrollWidth, html.clientWidth, body.scrollWidth, body.clientWidth);
  const height = max(html.scrollHeight, html.clientHeight, body.scrollHeight, body.clientHeight);
  let x = -scroll.scrollLeft + getWindowScrollBarX(element);
  const y = -scroll.scrollTop;
  if (getComputedStyle2(body).direction === "rtl") {
    x += max(html.clientWidth, body.clientWidth) - width;
  }
  return {
    width,
    height,
    x,
    y
  };
}
function getViewportRect(element, strategy) {
  const win = getWindow(element);
  const html = getDocumentElement(element);
  const visualViewport = win.visualViewport;
  let width = html.clientWidth;
  let height = html.clientHeight;
  let x = 0;
  let y = 0;
  if (visualViewport) {
    width = visualViewport.width;
    height = visualViewport.height;
    const visualViewportBased = isWebKit();
    if (!visualViewportBased || visualViewportBased && strategy === "fixed") {
      x = visualViewport.offsetLeft;
      y = visualViewport.offsetTop;
    }
  }
  return {
    width,
    height,
    x,
    y
  };
}
function getInnerBoundingClientRect(element, strategy) {
  const clientRect = getBoundingClientRect(element, true, strategy === "fixed");
  const top = clientRect.top + element.clientTop;
  const left = clientRect.left + element.clientLeft;
  const scale = isHTMLElement(element) ? getScale(element) : createCoords(1);
  const width = element.clientWidth * scale.x;
  const height = element.clientHeight * scale.y;
  const x = left * scale.x;
  const y = top * scale.y;
  return {
    width,
    height,
    x,
    y
  };
}
function getClientRectFromClippingAncestor(element, clippingAncestor, strategy) {
  let rect;
  if (clippingAncestor === "viewport") {
    rect = getViewportRect(element, strategy);
  } else if (clippingAncestor === "document") {
    rect = getDocumentRect(getDocumentElement(element));
  } else if (isElement(clippingAncestor)) {
    rect = getInnerBoundingClientRect(clippingAncestor, strategy);
  } else {
    const visualOffsets = getVisualOffsets(element);
    rect = {
      ...clippingAncestor,
      x: clippingAncestor.x - visualOffsets.x,
      y: clippingAncestor.y - visualOffsets.y
    };
  }
  return rectToClientRect(rect);
}
function hasFixedPositionAncestor(element, stopNode) {
  const parentNode = getParentNode(element);
  if (parentNode === stopNode || !isElement(parentNode) || isLastTraversableNode(parentNode)) {
    return false;
  }
  return getComputedStyle2(parentNode).position === "fixed" || hasFixedPositionAncestor(parentNode, stopNode);
}
function getClippingElementAncestors(element, cache) {
  const cachedResult = cache.get(element);
  if (cachedResult) {
    return cachedResult;
  }
  let result = getOverflowAncestors(element, [], false).filter((el) => isElement(el) && getNodeName(el) !== "body");
  let currentContainingBlockComputedStyle = null;
  const elementIsFixed = getComputedStyle2(element).position === "fixed";
  let currentNode = elementIsFixed ? getParentNode(element) : element;
  while (isElement(currentNode) && !isLastTraversableNode(currentNode)) {
    const computedStyle = getComputedStyle2(currentNode);
    const currentNodeIsContaining = isContainingBlock(currentNode);
    if (!currentNodeIsContaining && computedStyle.position === "fixed") {
      currentContainingBlockComputedStyle = null;
    }
    const shouldDropCurrentNode = elementIsFixed ? !currentNodeIsContaining && !currentContainingBlockComputedStyle : !currentNodeIsContaining && computedStyle.position === "static" && !!currentContainingBlockComputedStyle && ["absolute", "fixed"].includes(currentContainingBlockComputedStyle.position) || isOverflowElement(currentNode) && !currentNodeIsContaining && hasFixedPositionAncestor(element, currentNode);
    if (shouldDropCurrentNode) {
      result = result.filter((ancestor) => ancestor !== currentNode);
    } else {
      currentContainingBlockComputedStyle = computedStyle;
    }
    currentNode = getParentNode(currentNode);
  }
  cache.set(element, result);
  return result;
}
function getClippingRect(_ref) {
  let {
    element,
    boundary,
    rootBoundary,
    strategy
  } = _ref;
  const elementClippingAncestors = boundary === "clippingAncestors" ? getClippingElementAncestors(element, this._c) : [].concat(boundary);
  const clippingAncestors = [...elementClippingAncestors, rootBoundary];
  const firstClippingAncestor = clippingAncestors[0];
  const clippingRect = clippingAncestors.reduce((accRect, clippingAncestor) => {
    const rect = getClientRectFromClippingAncestor(element, clippingAncestor, strategy);
    accRect.top = max(rect.top, accRect.top);
    accRect.right = min(rect.right, accRect.right);
    accRect.bottom = min(rect.bottom, accRect.bottom);
    accRect.left = max(rect.left, accRect.left);
    return accRect;
  }, getClientRectFromClippingAncestor(element, firstClippingAncestor, strategy));
  return {
    width: clippingRect.right - clippingRect.left,
    height: clippingRect.bottom - clippingRect.top,
    x: clippingRect.left,
    y: clippingRect.top
  };
}
function getDimensions(element) {
  return getCssDimensions(element);
}
function getRectRelativeToOffsetParent(element, offsetParent, strategy) {
  const isOffsetParentAnElement = isHTMLElement(offsetParent);
  const documentElement = getDocumentElement(offsetParent);
  const isFixed = strategy === "fixed";
  const rect = getBoundingClientRect(element, true, isFixed, offsetParent);
  let scroll = {
    scrollLeft: 0,
    scrollTop: 0
  };
  const offsets = createCoords(0);
  if (isOffsetParentAnElement || !isOffsetParentAnElement && !isFixed) {
    if (getNodeName(offsetParent) !== "body" || isOverflowElement(documentElement)) {
      scroll = getNodeScroll(offsetParent);
    }
    if (isOffsetParentAnElement) {
      const offsetRect = getBoundingClientRect(offsetParent, true, isFixed, offsetParent);
      offsets.x = offsetRect.x + offsetParent.clientLeft;
      offsets.y = offsetRect.y + offsetParent.clientTop;
    } else if (documentElement) {
      offsets.x = getWindowScrollBarX(documentElement);
    }
  }
  return {
    x: rect.left + scroll.scrollLeft - offsets.x,
    y: rect.top + scroll.scrollTop - offsets.y,
    width: rect.width,
    height: rect.height
  };
}
function getTrueOffsetParent(element, polyfill) {
  if (!isHTMLElement(element) || getComputedStyle2(element).position === "fixed") {
    return null;
  }
  if (polyfill) {
    return polyfill(element);
  }
  return element.offsetParent;
}
function getOffsetParent(element, polyfill) {
  const window2 = getWindow(element);
  if (!isHTMLElement(element)) {
    return window2;
  }
  let offsetParent = getTrueOffsetParent(element, polyfill);
  while (offsetParent && isTableElement(offsetParent) && getComputedStyle2(offsetParent).position === "static") {
    offsetParent = getTrueOffsetParent(offsetParent, polyfill);
  }
  if (offsetParent && (getNodeName(offsetParent) === "html" || getNodeName(offsetParent) === "body" && getComputedStyle2(offsetParent).position === "static" && !isContainingBlock(offsetParent))) {
    return window2;
  }
  return offsetParent || getContainingBlock(element) || window2;
}
var getElementRects = async function(_ref) {
  let {
    reference,
    floating,
    strategy
  } = _ref;
  const getOffsetParentFn = this.getOffsetParent || getOffsetParent;
  const getDimensionsFn = this.getDimensions;
  return {
    reference: getRectRelativeToOffsetParent(reference, await getOffsetParentFn(floating), strategy),
    floating: {
      x: 0,
      y: 0,
      ...await getDimensionsFn(floating)
    }
  };
};
function isRTL(element) {
  return getComputedStyle2(element).direction === "rtl";
}
var platform = {
  convertOffsetParentRelativeRectToViewportRelativeRect,
  getDocumentElement,
  getClippingRect,
  getOffsetParent,
  getElementRects,
  getClientRects,
  getDimensions,
  getScale,
  isElement,
  isRTL
};
function observeMove(element, onMove) {
  let io = null;
  let timeoutId;
  const root = getDocumentElement(element);
  function cleanup() {
    clearTimeout(timeoutId);
    io && io.disconnect();
    io = null;
  }
  function refresh(skip, threshold) {
    if (skip === void 0) {
      skip = false;
    }
    if (threshold === void 0) {
      threshold = 1;
    }
    cleanup();
    const {
      left,
      top,
      width,
      height
    } = element.getBoundingClientRect();
    if (!skip) {
      onMove();
    }
    if (!width || !height) {
      return;
    }
    const insetTop = floor(top);
    const insetRight = floor(root.clientWidth - (left + width));
    const insetBottom = floor(root.clientHeight - (top + height));
    const insetLeft = floor(left);
    const rootMargin = -insetTop + "px " + -insetRight + "px " + -insetBottom + "px " + -insetLeft + "px";
    const options = {
      rootMargin,
      threshold: max(0, min(1, threshold)) || 1
    };
    let isFirstUpdate = true;
    function handleObserve(entries) {
      const ratio = entries[0].intersectionRatio;
      if (ratio !== threshold) {
        if (!isFirstUpdate) {
          return refresh();
        }
        if (!ratio) {
          timeoutId = setTimeout(() => {
            refresh(false, 1e-7);
          }, 100);
        } else {
          refresh(false, ratio);
        }
      }
      isFirstUpdate = false;
    }
    try {
      io = new IntersectionObserver(handleObserve, {
        ...options,
        // Handle <iframe>s
        root: root.ownerDocument
      });
    } catch (e) {
      io = new IntersectionObserver(handleObserve, options);
    }
    io.observe(element);
  }
  refresh(true);
  return cleanup;
}
function autoUpdate(reference, floating, update, options) {
  if (options === void 0) {
    options = {};
  }
  const {
    ancestorScroll = true,
    ancestorResize = true,
    elementResize = typeof ResizeObserver === "function",
    layoutShift = typeof IntersectionObserver === "function",
    animationFrame = false
  } = options;
  const referenceEl = unwrapElement(reference);
  const ancestors = ancestorScroll || ancestorResize ? [...referenceEl ? getOverflowAncestors(referenceEl) : [], ...getOverflowAncestors(floating)] : [];
  ancestors.forEach((ancestor) => {
    ancestorScroll && ancestor.addEventListener("scroll", update, {
      passive: true
    });
    ancestorResize && ancestor.addEventListener("resize", update);
  });
  const cleanupIo = referenceEl && layoutShift ? observeMove(referenceEl, update) : null;
  let reobserveFrame = -1;
  let resizeObserver = null;
  if (elementResize) {
    resizeObserver = new ResizeObserver((_ref) => {
      let [firstEntry] = _ref;
      if (firstEntry && firstEntry.target === referenceEl && resizeObserver) {
        resizeObserver.unobserve(floating);
        cancelAnimationFrame(reobserveFrame);
        reobserveFrame = requestAnimationFrame(() => {
          resizeObserver && resizeObserver.observe(floating);
        });
      }
      update();
    });
    if (referenceEl && !animationFrame) {
      resizeObserver.observe(referenceEl);
    }
    resizeObserver.observe(floating);
  }
  let frameId;
  let prevRefRect = animationFrame ? getBoundingClientRect(reference) : null;
  if (animationFrame) {
    frameLoop();
  }
  function frameLoop() {
    const nextRefRect = getBoundingClientRect(reference);
    if (prevRefRect && (nextRefRect.x !== prevRefRect.x || nextRefRect.y !== prevRefRect.y || nextRefRect.width !== prevRefRect.width || nextRefRect.height !== prevRefRect.height)) {
      update();
    }
    prevRefRect = nextRefRect;
    frameId = requestAnimationFrame(frameLoop);
  }
  update();
  return () => {
    ancestors.forEach((ancestor) => {
      ancestorScroll && ancestor.removeEventListener("scroll", update);
      ancestorResize && ancestor.removeEventListener("resize", update);
    });
    cleanupIo && cleanupIo();
    resizeObserver && resizeObserver.disconnect();
    resizeObserver = null;
    if (animationFrame) {
      cancelAnimationFrame(frameId);
    }
  };
}
var computePosition2 = (reference, floating, options) => {
  const cache = /* @__PURE__ */ new Map();
  const mergedOptions = {
    platform,
    ...options
  };
  const platformWithCache = {
    ...mergedOptions.platform,
    _c: cache
  };
  return computePosition(reference, floating, {
    ...mergedOptions,
    platform: platformWithCache
  });
};
function src_default7(Alpine2) {
  Alpine2.magic("anchor", (el) => {
    if (!el._x_anchor)
      throw "Alpine: No x-anchor directive found on element using $anchor...";
    return el._x_anchor;
  });
  Alpine2.interceptClone((from, to) => {
    if (from && from._x_anchor && !to._x_anchor) {
      to._x_anchor = from._x_anchor;
    }
  });
  Alpine2.directive("anchor", Alpine2.skipDuringClone(
    (el, { expression, modifiers, value }, { evaluate: evaluate22, effect: effect3, cleanup }) => {
      let { placement, offsetValue, unstyled, strategy, allowFlip } = getOptions(modifiers);
      el._x_anchor = Alpine2.reactive({ x: 0, y: 0 });
      let previousReference = null;
      let release2 = null;
      effect3(() => {
        let reference = evaluate22(expression);
        if (!reference)
          return;
        if (previousReference !== reference) {
          if (release2)
            release2();
          previousReference = reference;
          let compute = () => {
            let previousValue;
            computePosition2(reference, el, {
              placement,
              strategy,
              middleware: [allowFlip && flip(), shift({ padding: 5 }), offset(offsetValue)]
            }).then(({ x, y }) => {
              unstyled || setStyles2(el, x, y, strategy);
              if (JSON.stringify({ x, y }) !== previousValue) {
                el._x_anchor.x = x;
                el._x_anchor.y = y;
              }
              previousValue = JSON.stringify({ x, y });
            });
          };
          release2 = autoUpdate(reference, el, () => compute());
        }
      });
      cleanup(() => {
        if (release2)
          release2();
      });
    },
    // When cloning (or "morphing"), we will graft the style and position data from the live tree...
    (el, { expression, modifiers, value }, { cleanup, evaluate: evaluate22 }) => {
      let { placement, offsetValue, unstyled, strategy } = getOptions(modifiers);
      if (el._x_anchor) {
        unstyled || setStyles2(el, el._x_anchor.x, el._x_anchor.y, strategy);
      }
    }
  ));
}
function setStyles2(el, x, y, strategy = "absolute") {
  Object.assign(el.style, {
    left: x + "px",
    top: y + "px",
    position: strategy
  });
}
function getOptions(modifiers) {
  let positions = ["top", "top-start", "top-end", "right", "right-start", "right-end", "bottom", "bottom-start", "bottom-end", "left", "left-start", "left-end"];
  let placement = positions.find((i) => modifiers.includes(i));
  let offsetValue = 0;
  if (modifiers.includes("offset")) {
    let idx = modifiers.findIndex((i) => i === "offset");
    offsetValue = modifiers[idx + 1] !== void 0 ? Number(modifiers[idx + 1]) : offsetValue;
  }
  let unstyled = modifiers.includes("no-style");
  let allowFlip = !modifiers.includes("noflip");
  let strategy = modifiers.includes("fixed") ? "fixed" : "absolute";
  return { placement, offsetValue, unstyled, strategy, allowFlip };
}
var module_default7 = src_default7;

// node_modules/@alpinejs/mask/dist/module.esm.js
function src_default8(Alpine2) {
  Alpine2.directive("mask", (el, { value, expression }, { effect: effect3, evaluateLater: evaluateLater2, cleanup }) => {
    let templateFn = () => expression;
    let lastInputValue = "";
    let undoModelUpdate = () => {
    };
    queueMicrotask(() => {
      if (["function", "dynamic"].includes(value)) {
        let evaluator = evaluateLater2(expression);
        effect3(() => {
          templateFn = (input) => {
            let result;
            Alpine2.dontAutoEvaluateFunctions(() => {
              evaluator((value2) => {
                result = typeof value2 === "function" ? value2(input) : value2;
              }, { scope: {
                // These are "magics" we'll make available to the x-mask:function:
                "$input": input,
                "$money": formatMoney.bind({ el })
              } });
            });
            return result;
          };
          processInputValue(el, false);
        });
      } else {
        processInputValue(el, false);
      }
      if (el._x_model) {
        if (el._x_model.get() !== el.value) {
          if (!(el._x_model.get() === null && el.value === "")) {
            el._x_model.set(el.value);
          }
        }
        let updater = el._x_forceModelUpdate;
        let update = (value2) => {
          if (value2 === void 0) {
            lastInputValue = "";
            return updater(value2);
          }
          value2 = String(value2);
          let template = templateFn(value2);
          if (template && template !== "false") {
            value2 = formatInput(template, value2);
          }
          lastInputValue = value2;
          updater(value2);
          el._x_model.set(value2);
        };
        el._x_forceModelUpdate = update;
        undoModelUpdate = () => {
          if (el._x_forceModelUpdate === update) {
            el._x_forceModelUpdate = updater;
          }
        };
      }
    });
    const controller = new AbortController();
    cleanup(() => {
      controller.abort();
      undoModelUpdate();
    });
    el.addEventListener("input", () => processInputValue(el), {
      signal: controller.signal,
      // Setting this as a capture phase listener to ensure it runs
      // before wire:model or x-model added as a latent binding...
      capture: true
    });
    el.addEventListener("blur", () => processInputValue(el, false), { signal: controller.signal });
    function processInputValue(el2, shouldRestoreCursor = true) {
      let input = el2.value;
      let template = templateFn(input);
      if (!template || template === "false")
        return false;
      if (lastInputValue.length - el2.value.length === 1) {
        return lastInputValue = el2.value;
      }
      let setInput = () => {
        lastInputValue = el2.value = formatInput(template, input);
      };
      if (shouldRestoreCursor) {
        restoreCursorPosition(el2, template, () => {
          setInput();
        });
      } else {
        setInput();
      }
    }
  }).before("model");
}
function restoreCursorPosition(el, template, callback) {
  let cursorPosition = el.selectionStart;
  let unformattedValue = el.value;
  callback();
  let beforeLeftOfCursorBeforeFormatting = unformattedValue.slice(0, cursorPosition);
  let newPosition = formatInput(
    template,
    beforeLeftOfCursorBeforeFormatting
  ).length;
  el.setSelectionRange(newPosition, newPosition);
}
var regexes = {
  "9": /[0-9]/,
  "a": /[a-zA-Z]/,
  "*": /[a-zA-Z0-9]/
};
function formatInput(template, input) {
  let templateMark = 0;
  let inputMark = 0;
  let output = "";
  while (templateMark < template.length && inputMark < input.length) {
    let templateChar = template[templateMark];
    let inputChar = input[inputMark];
    if (templateChar in regexes) {
      if (regexes[templateChar].test(inputChar)) {
        output += inputChar;
        templateMark++;
      }
      inputMark++;
    } else {
      output += templateChar;
      templateMark++;
      if (templateChar === input[inputMark])
        inputMark++;
    }
  }
  return output;
}
function formatMoney(input, delimiter = ".", thousands, precision = 2) {
  if (input === "-")
    return "-";
  if (/^\D+$/.test(input))
    return "9";
  if (thousands === null || thousands === void 0) {
    thousands = delimiter === "," ? "." : ",";
  }
  let addThousands = (input2, thousands2) => {
    let output = "";
    let counter = 0;
    for (let i = input2.length - 1; i >= 0; i--) {
      if (input2[i] === thousands2)
        continue;
      if (counter === 3) {
        output = input2[i] + thousands2 + output;
        counter = 0;
      } else {
        output = input2[i] + output;
      }
      counter++;
    }
    return output;
  };
  let minus = input.startsWith("-") ? "-" : "";
  let strippedInput = input.replaceAll(new RegExp(`[^0-9\\${delimiter}]`, "g"), "");
  let template = Array.from({ length: strippedInput.split(delimiter)[0].length }).fill("9").join("");
  template = `${minus}${addThousands(template, thousands)}`;
  if (precision > 0 && input.includes(delimiter))
    template += `${delimiter}` + "9".repeat(precision);
  queueMicrotask(() => {
    if (this.el.value.endsWith(delimiter))
      return;
    if (this.el.value[this.el.selectionStart - 1] === delimiter) {
      this.el.setSelectionRange(this.el.selectionStart - 1, this.el.selectionStart - 1);
    }
  });
  return template;
}
var module_default8 = src_default8;

// node_modules/@alpinejs/sort/dist/module.esm.js
function ownKeys2(object, enumerableOnly) {
  var keys = Object.keys(object);
  if (Object.getOwnPropertySymbols) {
    var symbols = Object.getOwnPropertySymbols(object);
    if (enumerableOnly) {
      symbols = symbols.filter(function(sym) {
        return Object.getOwnPropertyDescriptor(object, sym).enumerable;
      });
    }
    keys.push.apply(keys, symbols);
  }
  return keys;
}
function _objectSpread22(target) {
  for (var i = 1; i < arguments.length; i++) {
    var source = arguments[i] != null ? arguments[i] : {};
    if (i % 2) {
      ownKeys2(Object(source), true).forEach(function(key) {
        _defineProperty2(target, key, source[key]);
      });
    } else if (Object.getOwnPropertyDescriptors) {
      Object.defineProperties(target, Object.getOwnPropertyDescriptors(source));
    } else {
      ownKeys2(Object(source)).forEach(function(key) {
        Object.defineProperty(target, key, Object.getOwnPropertyDescriptor(source, key));
      });
    }
  }
  return target;
}
function _typeof(obj) {
  "@babel/helpers - typeof";
  if (typeof Symbol === "function" && typeof Symbol.iterator === "symbol") {
    _typeof = function(obj2) {
      return typeof obj2;
    };
  } else {
    _typeof = function(obj2) {
      return obj2 && typeof Symbol === "function" && obj2.constructor === Symbol && obj2 !== Symbol.prototype ? "symbol" : typeof obj2;
    };
  }
  return _typeof(obj);
}
function _defineProperty2(obj, key, value) {
  if (key in obj) {
    Object.defineProperty(obj, key, {
      value,
      enumerable: true,
      configurable: true,
      writable: true
    });
  } else {
    obj[key] = value;
  }
  return obj;
}
function _extends() {
  _extends = Object.assign || function(target) {
    for (var i = 1; i < arguments.length; i++) {
      var source = arguments[i];
      for (var key in source) {
        if (Object.prototype.hasOwnProperty.call(source, key)) {
          target[key] = source[key];
        }
      }
    }
    return target;
  };
  return _extends.apply(this, arguments);
}
function _objectWithoutPropertiesLoose(source, excluded) {
  if (source == null)
    return {};
  var target = {};
  var sourceKeys = Object.keys(source);
  var key, i;
  for (i = 0; i < sourceKeys.length; i++) {
    key = sourceKeys[i];
    if (excluded.indexOf(key) >= 0)
      continue;
    target[key] = source[key];
  }
  return target;
}
function _objectWithoutProperties(source, excluded) {
  if (source == null)
    return {};
  var target = _objectWithoutPropertiesLoose(source, excluded);
  var key, i;
  if (Object.getOwnPropertySymbols) {
    var sourceSymbolKeys = Object.getOwnPropertySymbols(source);
    for (i = 0; i < sourceSymbolKeys.length; i++) {
      key = sourceSymbolKeys[i];
      if (excluded.indexOf(key) >= 0)
        continue;
      if (!Object.prototype.propertyIsEnumerable.call(source, key))
        continue;
      target[key] = source[key];
    }
  }
  return target;
}
var version = "1.15.2";
function userAgent(pattern) {
  if (typeof window !== "undefined" && window.navigator) {
    return !!/* @__PURE__ */ navigator.userAgent.match(pattern);
  }
}
var IE11OrLess = userAgent(/(?:Trident.*rv[ :]?11\.|msie|iemobile|Windows Phone)/i);
var Edge = userAgent(/Edge/i);
var FireFox = userAgent(/firefox/i);
var Safari = userAgent(/safari/i) && !userAgent(/chrome/i) && !userAgent(/android/i);
var IOS = userAgent(/iP(ad|od|hone)/i);
var ChromeForAndroid = userAgent(/chrome/i) && userAgent(/android/i);
var captureMode = {
  capture: false,
  passive: false
};
function on4(el, event, fn) {
  el.addEventListener(event, fn, !IE11OrLess && captureMode);
}
function off(el, event, fn) {
  el.removeEventListener(event, fn, !IE11OrLess && captureMode);
}
function matches2(el, selector) {
  if (!selector)
    return;
  selector[0] === ">" && (selector = selector.substring(1));
  if (el) {
    try {
      if (el.matches) {
        return el.matches(selector);
      } else if (el.msMatchesSelector) {
        return el.msMatchesSelector(selector);
      } else if (el.webkitMatchesSelector) {
        return el.webkitMatchesSelector(selector);
      }
    } catch (_) {
      return false;
    }
  }
  return false;
}
function getParentOrHost(el) {
  return el.host && el !== document && el.host.nodeType ? el.host : el.parentNode;
}
function closest(el, selector, ctx, includeCTX) {
  if (el) {
    ctx = ctx || document;
    do {
      if (selector != null && (selector[0] === ">" ? el.parentNode === ctx && matches2(el, selector) : matches2(el, selector)) || includeCTX && el === ctx) {
        return el;
      }
      if (el === ctx)
        break;
    } while (el = getParentOrHost(el));
  }
  return null;
}
var R_SPACE = /\s+/g;
function toggleClass(el, name, state) {
  if (el && name) {
    if (el.classList) {
      el.classList[state ? "add" : "remove"](name);
    } else {
      var className = (" " + el.className + " ").replace(R_SPACE, " ").replace(" " + name + " ", " ");
      el.className = (className + (state ? " " + name : "")).replace(R_SPACE, " ");
    }
  }
}
function css(el, prop, val) {
  var style = el && el.style;
  if (style) {
    if (val === void 0) {
      if (document.defaultView && document.defaultView.getComputedStyle) {
        val = document.defaultView.getComputedStyle(el, "");
      } else if (el.currentStyle) {
        val = el.currentStyle;
      }
      return prop === void 0 ? val : val[prop];
    } else {
      if (!(prop in style) && prop.indexOf("webkit") === -1) {
        prop = "-webkit-" + prop;
      }
      style[prop] = val + (typeof val === "string" ? "" : "px");
    }
  }
}
function matrix(el, selfOnly) {
  var appliedTransforms = "";
  if (typeof el === "string") {
    appliedTransforms = el;
  } else {
    do {
      var transform = css(el, "transform");
      if (transform && transform !== "none") {
        appliedTransforms = transform + " " + appliedTransforms;
      }
    } while (!selfOnly && (el = el.parentNode));
  }
  var matrixFn = window.DOMMatrix || window.WebKitCSSMatrix || window.CSSMatrix || window.MSCSSMatrix;
  return matrixFn && new matrixFn(appliedTransforms);
}
function find2(ctx, tagName, iterator2) {
  if (ctx) {
    var list = ctx.getElementsByTagName(tagName), i = 0, n = list.length;
    if (iterator2) {
      for (; i < n; i++) {
        iterator2(list[i], i);
      }
    }
    return list;
  }
  return [];
}
function getWindowScrollingElement() {
  var scrollingElement = document.scrollingElement;
  if (scrollingElement) {
    return scrollingElement;
  } else {
    return document.documentElement;
  }
}
function getRect(el, relativeToContainingBlock, relativeToNonStaticParent, undoScale, container) {
  if (!el.getBoundingClientRect && el !== window)
    return;
  var elRect, top, left, bottom, right, height, width;
  if (el !== window && el.parentNode && el !== getWindowScrollingElement()) {
    elRect = el.getBoundingClientRect();
    top = elRect.top;
    left = elRect.left;
    bottom = elRect.bottom;
    right = elRect.right;
    height = elRect.height;
    width = elRect.width;
  } else {
    top = 0;
    left = 0;
    bottom = window.innerHeight;
    right = window.innerWidth;
    height = window.innerHeight;
    width = window.innerWidth;
  }
  if ((relativeToContainingBlock || relativeToNonStaticParent) && el !== window) {
    container = container || el.parentNode;
    if (!IE11OrLess) {
      do {
        if (container && container.getBoundingClientRect && (css(container, "transform") !== "none" || relativeToNonStaticParent && css(container, "position") !== "static")) {
          var containerRect = container.getBoundingClientRect();
          top -= containerRect.top + parseInt(css(container, "border-top-width"));
          left -= containerRect.left + parseInt(css(container, "border-left-width"));
          bottom = top + elRect.height;
          right = left + elRect.width;
          break;
        }
      } while (container = container.parentNode);
    }
  }
  if (undoScale && el !== window) {
    var elMatrix = matrix(container || el), scaleX = elMatrix && elMatrix.a, scaleY = elMatrix && elMatrix.d;
    if (elMatrix) {
      top /= scaleY;
      left /= scaleX;
      width /= scaleX;
      height /= scaleY;
      bottom = top + height;
      right = left + width;
    }
  }
  return {
    top,
    left,
    bottom,
    right,
    width,
    height
  };
}
function isScrolledPast(el, elSide, parentSide) {
  var parent = getParentAutoScrollElement(el, true), elSideVal = getRect(el)[elSide];
  while (parent) {
    var parentSideVal = getRect(parent)[parentSide], visible = void 0;
    if (parentSide === "top" || parentSide === "left") {
      visible = elSideVal >= parentSideVal;
    } else {
      visible = elSideVal <= parentSideVal;
    }
    if (!visible)
      return parent;
    if (parent === getWindowScrollingElement())
      break;
    parent = getParentAutoScrollElement(parent, false);
  }
  return false;
}
function getChild(el, childNum, options, includeDragEl) {
  var currentChild = 0, i = 0, children = el.children;
  while (i < children.length) {
    if (children[i].style.display !== "none" && children[i] !== Sortable.ghost && (includeDragEl || children[i] !== Sortable.dragged) && closest(children[i], options.draggable, el, false)) {
      if (currentChild === childNum) {
        return children[i];
      }
      currentChild++;
    }
    i++;
  }
  return null;
}
function lastChild(el, selector) {
  var last = el.lastElementChild;
  while (last && (last === Sortable.ghost || css(last, "display") === "none" || selector && !matches2(last, selector))) {
    last = last.previousElementSibling;
  }
  return last || null;
}
function index(el, selector) {
  var index2 = 0;
  if (!el || !el.parentNode) {
    return -1;
  }
  while (el = el.previousElementSibling) {
    if (el.nodeName.toUpperCase() !== "TEMPLATE" && el !== Sortable.clone && (!selector || matches2(el, selector))) {
      index2++;
    }
  }
  return index2;
}
function getRelativeScrollOffset(el) {
  var offsetLeft = 0, offsetTop = 0, winScroller = getWindowScrollingElement();
  if (el) {
    do {
      var elMatrix = matrix(el), scaleX = elMatrix.a, scaleY = elMatrix.d;
      offsetLeft += el.scrollLeft * scaleX;
      offsetTop += el.scrollTop * scaleY;
    } while (el !== winScroller && (el = el.parentNode));
  }
  return [offsetLeft, offsetTop];
}
function indexOfObject(arr, obj) {
  for (var i in arr) {
    if (!arr.hasOwnProperty(i))
      continue;
    for (var key in obj) {
      if (obj.hasOwnProperty(key) && obj[key] === arr[i][key])
        return Number(i);
    }
  }
  return -1;
}
function getParentAutoScrollElement(el, includeSelf) {
  if (!el || !el.getBoundingClientRect)
    return getWindowScrollingElement();
  var elem = el;
  var gotSelf = false;
  do {
    if (elem.clientWidth < elem.scrollWidth || elem.clientHeight < elem.scrollHeight) {
      var elemCSS = css(elem);
      if (elem.clientWidth < elem.scrollWidth && (elemCSS.overflowX == "auto" || elemCSS.overflowX == "scroll") || elem.clientHeight < elem.scrollHeight && (elemCSS.overflowY == "auto" || elemCSS.overflowY == "scroll")) {
        if (!elem.getBoundingClientRect || elem === document.body)
          return getWindowScrollingElement();
        if (gotSelf || includeSelf)
          return elem;
        gotSelf = true;
      }
    }
  } while (elem = elem.parentNode);
  return getWindowScrollingElement();
}
function extend2(dst, src) {
  if (dst && src) {
    for (var key in src) {
      if (src.hasOwnProperty(key)) {
        dst[key] = src[key];
      }
    }
  }
  return dst;
}
function isRectEqual(rect1, rect2) {
  return Math.round(rect1.top) === Math.round(rect2.top) && Math.round(rect1.left) === Math.round(rect2.left) && Math.round(rect1.height) === Math.round(rect2.height) && Math.round(rect1.width) === Math.round(rect2.width);
}
var _throttleTimeout;
function throttle2(callback, ms) {
  return function() {
    if (!_throttleTimeout) {
      var args = arguments, _this = this;
      if (args.length === 1) {
        callback.call(_this, args[0]);
      } else {
        callback.apply(_this, args);
      }
      _throttleTimeout = setTimeout(function() {
        _throttleTimeout = void 0;
      }, ms);
    }
  };
}
function cancelThrottle() {
  clearTimeout(_throttleTimeout);
  _throttleTimeout = void 0;
}
function scrollBy(el, x, y) {
  el.scrollLeft += x;
  el.scrollTop += y;
}
function clone2(el) {
  var Polymer = window.Polymer;
  var $ = window.jQuery || window.Zepto;
  if (Polymer && Polymer.dom) {
    return Polymer.dom(el).cloneNode(true);
  } else if ($) {
    return $(el).clone(true)[0];
  } else {
    return el.cloneNode(true);
  }
}
function getChildContainingRectFromElement(container, options, ghostEl2) {
  var rect = {};
  Array.from(container.children).forEach(function(child) {
    var _rect$left, _rect$top, _rect$right, _rect$bottom;
    if (!closest(child, options.draggable, container, false) || child.animated || child === ghostEl2)
      return;
    var childRect = getRect(child);
    rect.left = Math.min((_rect$left = rect.left) !== null && _rect$left !== void 0 ? _rect$left : Infinity, childRect.left);
    rect.top = Math.min((_rect$top = rect.top) !== null && _rect$top !== void 0 ? _rect$top : Infinity, childRect.top);
    rect.right = Math.max((_rect$right = rect.right) !== null && _rect$right !== void 0 ? _rect$right : -Infinity, childRect.right);
    rect.bottom = Math.max((_rect$bottom = rect.bottom) !== null && _rect$bottom !== void 0 ? _rect$bottom : -Infinity, childRect.bottom);
  });
  rect.width = rect.right - rect.left;
  rect.height = rect.bottom - rect.top;
  rect.x = rect.left;
  rect.y = rect.top;
  return rect;
}
var expando = "Sortable" + (/* @__PURE__ */ new Date()).getTime();
function AnimationStateManager() {
  var animationStates = [], animationCallbackId;
  return {
    captureAnimationState: function captureAnimationState() {
      animationStates = [];
      if (!this.options.animation)
        return;
      var children = [].slice.call(this.el.children);
      children.forEach(function(child) {
        if (css(child, "display") === "none" || child === Sortable.ghost)
          return;
        animationStates.push({
          target: child,
          rect: getRect(child)
        });
        var fromRect = _objectSpread22({}, animationStates[animationStates.length - 1].rect);
        if (child.thisAnimationDuration) {
          var childMatrix = matrix(child, true);
          if (childMatrix) {
            fromRect.top -= childMatrix.f;
            fromRect.left -= childMatrix.e;
          }
        }
        child.fromRect = fromRect;
      });
    },
    addAnimationState: function addAnimationState(state) {
      animationStates.push(state);
    },
    removeAnimationState: function removeAnimationState(target) {
      animationStates.splice(indexOfObject(animationStates, {
        target
      }), 1);
    },
    animateAll: function animateAll(callback) {
      var _this = this;
      if (!this.options.animation) {
        clearTimeout(animationCallbackId);
        if (typeof callback === "function")
          callback();
        return;
      }
      var animating = false, animationTime = 0;
      animationStates.forEach(function(state) {
        var time = 0, target = state.target, fromRect = target.fromRect, toRect = getRect(target), prevFromRect = target.prevFromRect, prevToRect = target.prevToRect, animatingRect = state.rect, targetMatrix = matrix(target, true);
        if (targetMatrix) {
          toRect.top -= targetMatrix.f;
          toRect.left -= targetMatrix.e;
        }
        target.toRect = toRect;
        if (target.thisAnimationDuration) {
          if (isRectEqual(prevFromRect, toRect) && !isRectEqual(fromRect, toRect) && // Make sure animatingRect is on line between toRect & fromRect
          (animatingRect.top - toRect.top) / (animatingRect.left - toRect.left) === (fromRect.top - toRect.top) / (fromRect.left - toRect.left)) {
            time = calculateRealTime(animatingRect, prevFromRect, prevToRect, _this.options);
          }
        }
        if (!isRectEqual(toRect, fromRect)) {
          target.prevFromRect = fromRect;
          target.prevToRect = toRect;
          if (!time) {
            time = _this.options.animation;
          }
          _this.animate(target, animatingRect, toRect, time);
        }
        if (time) {
          animating = true;
          animationTime = Math.max(animationTime, time);
          clearTimeout(target.animationResetTimer);
          target.animationResetTimer = setTimeout(function() {
            target.animationTime = 0;
            target.prevFromRect = null;
            target.fromRect = null;
            target.prevToRect = null;
            target.thisAnimationDuration = null;
          }, time);
          target.thisAnimationDuration = time;
        }
      });
      clearTimeout(animationCallbackId);
      if (!animating) {
        if (typeof callback === "function")
          callback();
      } else {
        animationCallbackId = setTimeout(function() {
          if (typeof callback === "function")
            callback();
        }, animationTime);
      }
      animationStates = [];
    },
    animate: function animate(target, currentRect, toRect, duration2) {
      if (duration2) {
        css(target, "transition", "");
        css(target, "transform", "");
        var elMatrix = matrix(this.el), scaleX = elMatrix && elMatrix.a, scaleY = elMatrix && elMatrix.d, translateX = (currentRect.left - toRect.left) / (scaleX || 1), translateY = (currentRect.top - toRect.top) / (scaleY || 1);
        target.animatingX = !!translateX;
        target.animatingY = !!translateY;
        css(target, "transform", "translate3d(" + translateX + "px," + translateY + "px,0)");
        this.forRepaintDummy = repaint(target);
        css(target, "transition", "transform " + duration2 + "ms" + (this.options.easing ? " " + this.options.easing : ""));
        css(target, "transform", "translate3d(0,0,0)");
        typeof target.animated === "number" && clearTimeout(target.animated);
        target.animated = setTimeout(function() {
          css(target, "transition", "");
          css(target, "transform", "");
          target.animated = false;
          target.animatingX = false;
          target.animatingY = false;
        }, duration2);
      }
    }
  };
}
function repaint(target) {
  return target.offsetWidth;
}
function calculateRealTime(animatingRect, fromRect, toRect, options) {
  return Math.sqrt(Math.pow(fromRect.top - animatingRect.top, 2) + Math.pow(fromRect.left - animatingRect.left, 2)) / Math.sqrt(Math.pow(fromRect.top - toRect.top, 2) + Math.pow(fromRect.left - toRect.left, 2)) * options.animation;
}
var plugins = [];
var defaults = {
  initializeByDefault: true
};
var PluginManager = {
  mount: function mount(plugin2) {
    for (var option2 in defaults) {
      if (defaults.hasOwnProperty(option2) && !(option2 in plugin2)) {
        plugin2[option2] = defaults[option2];
      }
    }
    plugins.forEach(function(p) {
      if (p.pluginName === plugin2.pluginName) {
        throw "Sortable: Cannot mount plugin ".concat(plugin2.pluginName, " more than once");
      }
    });
    plugins.push(plugin2);
  },
  pluginEvent: function pluginEvent(eventName, sortable, evt) {
    var _this = this;
    this.eventCanceled = false;
    evt.cancel = function() {
      _this.eventCanceled = true;
    };
    var eventNameGlobal = eventName + "Global";
    plugins.forEach(function(plugin2) {
      if (!sortable[plugin2.pluginName])
        return;
      if (sortable[plugin2.pluginName][eventNameGlobal]) {
        sortable[plugin2.pluginName][eventNameGlobal](_objectSpread22({
          sortable
        }, evt));
      }
      if (sortable.options[plugin2.pluginName] && sortable[plugin2.pluginName][eventName]) {
        sortable[plugin2.pluginName][eventName](_objectSpread22({
          sortable
        }, evt));
      }
    });
  },
  initializePlugins: function initializePlugins(sortable, el, defaults2, options) {
    plugins.forEach(function(plugin2) {
      var pluginName = plugin2.pluginName;
      if (!sortable.options[pluginName] && !plugin2.initializeByDefault)
        return;
      var initialized = new plugin2(sortable, el, sortable.options);
      initialized.sortable = sortable;
      initialized.options = sortable.options;
      sortable[pluginName] = initialized;
      _extends(defaults2, initialized.defaults);
    });
    for (var option2 in sortable.options) {
      if (!sortable.options.hasOwnProperty(option2))
        continue;
      var modified = this.modifyOption(sortable, option2, sortable.options[option2]);
      if (typeof modified !== "undefined") {
        sortable.options[option2] = modified;
      }
    }
  },
  getEventProperties: function getEventProperties(name, sortable) {
    var eventProperties = {};
    plugins.forEach(function(plugin2) {
      if (typeof plugin2.eventProperties !== "function")
        return;
      _extends(eventProperties, plugin2.eventProperties.call(sortable[plugin2.pluginName], name));
    });
    return eventProperties;
  },
  modifyOption: function modifyOption(sortable, name, value) {
    var modifiedValue;
    plugins.forEach(function(plugin2) {
      if (!sortable[plugin2.pluginName])
        return;
      if (plugin2.optionListeners && typeof plugin2.optionListeners[name] === "function") {
        modifiedValue = plugin2.optionListeners[name].call(sortable[plugin2.pluginName], value);
      }
    });
    return modifiedValue;
  }
};
function dispatchEvent2(_ref) {
  var sortable = _ref.sortable, rootEl2 = _ref.rootEl, name = _ref.name, targetEl = _ref.targetEl, cloneEl2 = _ref.cloneEl, toEl = _ref.toEl, fromEl = _ref.fromEl, oldIndex2 = _ref.oldIndex, newIndex2 = _ref.newIndex, oldDraggableIndex2 = _ref.oldDraggableIndex, newDraggableIndex2 = _ref.newDraggableIndex, originalEvent = _ref.originalEvent, putSortable2 = _ref.putSortable, extraEventProperties = _ref.extraEventProperties;
  sortable = sortable || rootEl2 && rootEl2[expando];
  if (!sortable)
    return;
  var evt, options = sortable.options, onName = "on" + name.charAt(0).toUpperCase() + name.substr(1);
  if (window.CustomEvent && !IE11OrLess && !Edge) {
    evt = new CustomEvent(name, {
      bubbles: true,
      cancelable: true
    });
  } else {
    evt = document.createEvent("Event");
    evt.initEvent(name, true, true);
  }
  evt.to = toEl || rootEl2;
  evt.from = fromEl || rootEl2;
  evt.item = targetEl || rootEl2;
  evt.clone = cloneEl2;
  evt.oldIndex = oldIndex2;
  evt.newIndex = newIndex2;
  evt.oldDraggableIndex = oldDraggableIndex2;
  evt.newDraggableIndex = newDraggableIndex2;
  evt.originalEvent = originalEvent;
  evt.pullMode = putSortable2 ? putSortable2.lastPutMode : void 0;
  var allEventProperties = _objectSpread22(_objectSpread22({}, extraEventProperties), PluginManager.getEventProperties(name, sortable));
  for (var option2 in allEventProperties) {
    evt[option2] = allEventProperties[option2];
  }
  if (rootEl2) {
    rootEl2.dispatchEvent(evt);
  }
  if (options[onName]) {
    options[onName].call(sortable, evt);
  }
}
var _excluded = ["evt"];
var pluginEvent2 = function pluginEvent3(eventName, sortable) {
  var _ref = arguments.length > 2 && arguments[2] !== void 0 ? arguments[2] : {}, originalEvent = _ref.evt, data2 = _objectWithoutProperties(_ref, _excluded);
  PluginManager.pluginEvent.bind(Sortable)(eventName, sortable, _objectSpread22({
    dragEl,
    parentEl,
    ghostEl,
    rootEl,
    nextEl,
    lastDownEl,
    cloneEl,
    cloneHidden,
    dragStarted: moved,
    putSortable,
    activeSortable: Sortable.active,
    originalEvent,
    oldIndex,
    oldDraggableIndex,
    newIndex,
    newDraggableIndex,
    hideGhostForTarget: _hideGhostForTarget,
    unhideGhostForTarget: _unhideGhostForTarget,
    cloneNowHidden: function cloneNowHidden() {
      cloneHidden = true;
    },
    cloneNowShown: function cloneNowShown() {
      cloneHidden = false;
    },
    dispatchSortableEvent: function dispatchSortableEvent(name) {
      _dispatchEvent({
        sortable,
        name,
        originalEvent
      });
    }
  }, data2));
};
function _dispatchEvent(info) {
  dispatchEvent2(_objectSpread22({
    putSortable,
    cloneEl,
    targetEl: dragEl,
    rootEl,
    oldIndex,
    oldDraggableIndex,
    newIndex,
    newDraggableIndex
  }, info));
}
var dragEl;
var parentEl;
var ghostEl;
var rootEl;
var nextEl;
var lastDownEl;
var cloneEl;
var cloneHidden;
var oldIndex;
var newIndex;
var oldDraggableIndex;
var newDraggableIndex;
var activeGroup;
var putSortable;
var awaitingDragStarted = false;
var ignoreNextClick = false;
var sortables = [];
var tapEvt;
var touchEvt;
var lastDx;
var lastDy;
var tapDistanceLeft;
var tapDistanceTop;
var moved;
var lastTarget;
var lastDirection;
var pastFirstInvertThresh = false;
var isCircumstantialInvert = false;
var targetMoveDistance;
var ghostRelativeParent;
var ghostRelativeParentInitialScroll = [];
var _silent = false;
var savedInputChecked = [];
var documentExists = typeof document !== "undefined";
var PositionGhostAbsolutely = IOS;
var CSSFloatProperty = Edge || IE11OrLess ? "cssFloat" : "float";
var supportDraggable = documentExists && !ChromeForAndroid && !IOS && "draggable" in document.createElement("div");
var supportCssPointerEvents = (function() {
  if (!documentExists)
    return;
  if (IE11OrLess) {
    return false;
  }
  var el = document.createElement("x");
  el.style.cssText = "pointer-events:auto";
  return el.style.pointerEvents === "auto";
})();
var _detectDirection = function _detectDirection2(el, options) {
  var elCSS = css(el), elWidth = parseInt(elCSS.width) - parseInt(elCSS.paddingLeft) - parseInt(elCSS.paddingRight) - parseInt(elCSS.borderLeftWidth) - parseInt(elCSS.borderRightWidth), child1 = getChild(el, 0, options), child2 = getChild(el, 1, options), firstChildCSS = child1 && css(child1), secondChildCSS = child2 && css(child2), firstChildWidth = firstChildCSS && parseInt(firstChildCSS.marginLeft) + parseInt(firstChildCSS.marginRight) + getRect(child1).width, secondChildWidth = secondChildCSS && parseInt(secondChildCSS.marginLeft) + parseInt(secondChildCSS.marginRight) + getRect(child2).width;
  if (elCSS.display === "flex") {
    return elCSS.flexDirection === "column" || elCSS.flexDirection === "column-reverse" ? "vertical" : "horizontal";
  }
  if (elCSS.display === "grid") {
    return elCSS.gridTemplateColumns.split(" ").length <= 1 ? "vertical" : "horizontal";
  }
  if (child1 && firstChildCSS["float"] && firstChildCSS["float"] !== "none") {
    var touchingSideChild2 = firstChildCSS["float"] === "left" ? "left" : "right";
    return child2 && (secondChildCSS.clear === "both" || secondChildCSS.clear === touchingSideChild2) ? "vertical" : "horizontal";
  }
  return child1 && (firstChildCSS.display === "block" || firstChildCSS.display === "flex" || firstChildCSS.display === "table" || firstChildCSS.display === "grid" || firstChildWidth >= elWidth && elCSS[CSSFloatProperty] === "none" || child2 && elCSS[CSSFloatProperty] === "none" && firstChildWidth + secondChildWidth > elWidth) ? "vertical" : "horizontal";
};
var _dragElInRowColumn = function _dragElInRowColumn2(dragRect, targetRect, vertical) {
  var dragElS1Opp = vertical ? dragRect.left : dragRect.top, dragElS2Opp = vertical ? dragRect.right : dragRect.bottom, dragElOppLength = vertical ? dragRect.width : dragRect.height, targetS1Opp = vertical ? targetRect.left : targetRect.top, targetS2Opp = vertical ? targetRect.right : targetRect.bottom, targetOppLength = vertical ? targetRect.width : targetRect.height;
  return dragElS1Opp === targetS1Opp || dragElS2Opp === targetS2Opp || dragElS1Opp + dragElOppLength / 2 === targetS1Opp + targetOppLength / 2;
};
var _detectNearestEmptySortable = function _detectNearestEmptySortable2(x, y) {
  var ret;
  sortables.some(function(sortable) {
    var threshold = sortable[expando].options.emptyInsertThreshold;
    if (!threshold || lastChild(sortable))
      return;
    var rect = getRect(sortable), insideHorizontally = x >= rect.left - threshold && x <= rect.right + threshold, insideVertically = y >= rect.top - threshold && y <= rect.bottom + threshold;
    if (insideHorizontally && insideVertically) {
      return ret = sortable;
    }
  });
  return ret;
};
var _prepareGroup = function _prepareGroup2(options) {
  function toFn(value, pull) {
    return function(to, from, dragEl2, evt) {
      var sameGroup = to.options.group.name && from.options.group.name && to.options.group.name === from.options.group.name;
      if (value == null && (pull || sameGroup)) {
        return true;
      } else if (value == null || value === false) {
        return false;
      } else if (pull && value === "clone") {
        return value;
      } else if (typeof value === "function") {
        return toFn(value(to, from, dragEl2, evt), pull)(to, from, dragEl2, evt);
      } else {
        var otherGroup = (pull ? to : from).options.group.name;
        return value === true || typeof value === "string" && value === otherGroup || value.join && value.indexOf(otherGroup) > -1;
      }
    };
  }
  var group = {};
  var originalGroup = options.group;
  if (!originalGroup || _typeof(originalGroup) != "object") {
    originalGroup = {
      name: originalGroup
    };
  }
  group.name = originalGroup.name;
  group.checkPull = toFn(originalGroup.pull, true);
  group.checkPut = toFn(originalGroup.put);
  group.revertClone = originalGroup.revertClone;
  options.group = group;
};
var _hideGhostForTarget = function _hideGhostForTarget2() {
  if (!supportCssPointerEvents && ghostEl) {
    css(ghostEl, "display", "none");
  }
};
var _unhideGhostForTarget = function _unhideGhostForTarget2() {
  if (!supportCssPointerEvents && ghostEl) {
    css(ghostEl, "display", "");
  }
};
if (documentExists && !ChromeForAndroid) {
  document.addEventListener("click", function(evt) {
    if (ignoreNextClick) {
      evt.preventDefault();
      evt.stopPropagation && evt.stopPropagation();
      evt.stopImmediatePropagation && evt.stopImmediatePropagation();
      ignoreNextClick = false;
      return false;
    }
  }, true);
}
var nearestEmptyInsertDetectEvent = function nearestEmptyInsertDetectEvent2(evt) {
  if (dragEl) {
    evt = evt.touches ? evt.touches[0] : evt;
    var nearest = _detectNearestEmptySortable(evt.clientX, evt.clientY);
    if (nearest) {
      var event = {};
      for (var i in evt) {
        if (evt.hasOwnProperty(i)) {
          event[i] = evt[i];
        }
      }
      event.target = event.rootEl = nearest;
      event.preventDefault = void 0;
      event.stopPropagation = void 0;
      nearest[expando]._onDragOver(event);
    }
  }
};
var _checkOutsideTargetEl = function _checkOutsideTargetEl2(evt) {
  if (dragEl) {
    dragEl.parentNode[expando]._isOutsideThisEl(evt.target);
  }
};
function Sortable(el, options) {
  if (!(el && el.nodeType && el.nodeType === 1)) {
    throw "Sortable: `el` must be an HTMLElement, not ".concat({}.toString.call(el));
  }
  this.el = el;
  this.options = options = _extends({}, options);
  el[expando] = this;
  var defaults2 = {
    group: null,
    sort: true,
    disabled: false,
    store: null,
    handle: null,
    draggable: /^[uo]l$/i.test(el.nodeName) ? ">li" : ">*",
    swapThreshold: 1,
    // percentage; 0 <= x <= 1
    invertSwap: false,
    // invert always
    invertedSwapThreshold: null,
    // will be set to same as swapThreshold if default
    removeCloneOnHide: true,
    direction: function direction() {
      return _detectDirection(el, this.options);
    },
    ghostClass: "sortable-ghost",
    chosenClass: "sortable-chosen",
    dragClass: "sortable-drag",
    ignore: "a, img",
    filter: null,
    preventOnFilter: true,
    animation: 0,
    easing: null,
    setData: function setData(dataTransfer, dragEl2) {
      dataTransfer.setData("Text", dragEl2.textContent);
    },
    dropBubble: false,
    dragoverBubble: false,
    dataIdAttr: "data-id",
    delay: 0,
    delayOnTouchOnly: false,
    touchStartThreshold: (Number.parseInt ? Number : window).parseInt(window.devicePixelRatio, 10) || 1,
    forceFallback: false,
    fallbackClass: "sortable-fallback",
    fallbackOnBody: false,
    fallbackTolerance: 0,
    fallbackOffset: {
      x: 0,
      y: 0
    },
    supportPointer: Sortable.supportPointer !== false && "PointerEvent" in window && !Safari,
    emptyInsertThreshold: 5
  };
  PluginManager.initializePlugins(this, el, defaults2);
  for (var name in defaults2) {
    !(name in options) && (options[name] = defaults2[name]);
  }
  _prepareGroup(options);
  for (var fn in this) {
    if (fn.charAt(0) === "_" && typeof this[fn] === "function") {
      this[fn] = this[fn].bind(this);
    }
  }
  this.nativeDraggable = options.forceFallback ? false : supportDraggable;
  if (this.nativeDraggable) {
    this.options.touchStartThreshold = 1;
  }
  if (options.supportPointer) {
    on4(el, "pointerdown", this._onTapStart);
  } else {
    on4(el, "mousedown", this._onTapStart);
    on4(el, "touchstart", this._onTapStart);
  }
  if (this.nativeDraggable) {
    on4(el, "dragover", this);
    on4(el, "dragenter", this);
  }
  sortables.push(this.el);
  options.store && options.store.get && this.sort(options.store.get(this) || []);
  _extends(this, AnimationStateManager());
}
Sortable.prototype = /** @lends Sortable.prototype */
{
  constructor: Sortable,
  _isOutsideThisEl: function _isOutsideThisEl(target) {
    if (!this.el.contains(target) && target !== this.el) {
      lastTarget = null;
    }
  },
  _getDirection: function _getDirection(evt, target) {
    return typeof this.options.direction === "function" ? this.options.direction.call(this, evt, target, dragEl) : this.options.direction;
  },
  _onTapStart: function _onTapStart(evt) {
    if (!evt.cancelable)
      return;
    var _this = this, el = this.el, options = this.options, preventOnFilter = options.preventOnFilter, type = evt.type, touch = evt.touches && evt.touches[0] || evt.pointerType && evt.pointerType === "touch" && evt, target = (touch || evt).target, originalTarget = evt.target.shadowRoot && (evt.path && evt.path[0] || evt.composedPath && evt.composedPath()[0]) || target, filter = options.filter;
    _saveInputCheckedState(el);
    if (dragEl) {
      return;
    }
    if (/mousedown|pointerdown/.test(type) && evt.button !== 0 || options.disabled) {
      return;
    }
    if (originalTarget.isContentEditable) {
      return;
    }
    if (!this.nativeDraggable && Safari && target && target.tagName.toUpperCase() === "SELECT") {
      return;
    }
    target = closest(target, options.draggable, el, false);
    if (target && target.animated) {
      return;
    }
    if (lastDownEl === target) {
      return;
    }
    oldIndex = index(target);
    oldDraggableIndex = index(target, options.draggable);
    if (typeof filter === "function") {
      if (filter.call(this, evt, target, this)) {
        _dispatchEvent({
          sortable: _this,
          rootEl: originalTarget,
          name: "filter",
          targetEl: target,
          toEl: el,
          fromEl: el
        });
        pluginEvent2("filter", _this, {
          evt
        });
        preventOnFilter && evt.cancelable && evt.preventDefault();
        return;
      }
    } else if (filter) {
      filter = filter.split(",").some(function(criteria) {
        criteria = closest(originalTarget, criteria.trim(), el, false);
        if (criteria) {
          _dispatchEvent({
            sortable: _this,
            rootEl: criteria,
            name: "filter",
            targetEl: target,
            fromEl: el,
            toEl: el
          });
          pluginEvent2("filter", _this, {
            evt
          });
          return true;
        }
      });
      if (filter) {
        preventOnFilter && evt.cancelable && evt.preventDefault();
        return;
      }
    }
    if (options.handle && !closest(originalTarget, options.handle, el, false)) {
      return;
    }
    this._prepareDragStart(evt, touch, target);
  },
  _prepareDragStart: function _prepareDragStart(evt, touch, target) {
    var _this = this, el = _this.el, options = _this.options, ownerDocument = el.ownerDocument, dragStartFn;
    if (target && !dragEl && target.parentNode === el) {
      var dragRect = getRect(target);
      rootEl = el;
      dragEl = target;
      parentEl = dragEl.parentNode;
      nextEl = dragEl.nextSibling;
      lastDownEl = target;
      activeGroup = options.group;
      Sortable.dragged = dragEl;
      tapEvt = {
        target: dragEl,
        clientX: (touch || evt).clientX,
        clientY: (touch || evt).clientY
      };
      tapDistanceLeft = tapEvt.clientX - dragRect.left;
      tapDistanceTop = tapEvt.clientY - dragRect.top;
      this._lastX = (touch || evt).clientX;
      this._lastY = (touch || evt).clientY;
      dragEl.style["will-change"] = "all";
      dragStartFn = function dragStartFn2() {
        pluginEvent2("delayEnded", _this, {
          evt
        });
        if (Sortable.eventCanceled) {
          _this._onDrop();
          return;
        }
        _this._disableDelayedDragEvents();
        if (!FireFox && _this.nativeDraggable) {
          dragEl.draggable = true;
        }
        _this._triggerDragStart(evt, touch);
        _dispatchEvent({
          sortable: _this,
          name: "choose",
          originalEvent: evt
        });
        toggleClass(dragEl, options.chosenClass, true);
      };
      options.ignore.split(",").forEach(function(criteria) {
        find2(dragEl, criteria.trim(), _disableDraggable);
      });
      on4(ownerDocument, "dragover", nearestEmptyInsertDetectEvent);
      on4(ownerDocument, "mousemove", nearestEmptyInsertDetectEvent);
      on4(ownerDocument, "touchmove", nearestEmptyInsertDetectEvent);
      on4(ownerDocument, "mouseup", _this._onDrop);
      on4(ownerDocument, "touchend", _this._onDrop);
      on4(ownerDocument, "touchcancel", _this._onDrop);
      if (FireFox && this.nativeDraggable) {
        this.options.touchStartThreshold = 4;
        dragEl.draggable = true;
      }
      pluginEvent2("delayStart", this, {
        evt
      });
      if (options.delay && (!options.delayOnTouchOnly || touch) && (!this.nativeDraggable || !(Edge || IE11OrLess))) {
        if (Sortable.eventCanceled) {
          this._onDrop();
          return;
        }
        on4(ownerDocument, "mouseup", _this._disableDelayedDrag);
        on4(ownerDocument, "touchend", _this._disableDelayedDrag);
        on4(ownerDocument, "touchcancel", _this._disableDelayedDrag);
        on4(ownerDocument, "mousemove", _this._delayedDragTouchMoveHandler);
        on4(ownerDocument, "touchmove", _this._delayedDragTouchMoveHandler);
        options.supportPointer && on4(ownerDocument, "pointermove", _this._delayedDragTouchMoveHandler);
        _this._dragStartTimer = setTimeout(dragStartFn, options.delay);
      } else {
        dragStartFn();
      }
    }
  },
  _delayedDragTouchMoveHandler: function _delayedDragTouchMoveHandler(e) {
    var touch = e.touches ? e.touches[0] : e;
    if (Math.max(Math.abs(touch.clientX - this._lastX), Math.abs(touch.clientY - this._lastY)) >= Math.floor(this.options.touchStartThreshold / (this.nativeDraggable && window.devicePixelRatio || 1))) {
      this._disableDelayedDrag();
    }
  },
  _disableDelayedDrag: function _disableDelayedDrag() {
    dragEl && _disableDraggable(dragEl);
    clearTimeout(this._dragStartTimer);
    this._disableDelayedDragEvents();
  },
  _disableDelayedDragEvents: function _disableDelayedDragEvents() {
    var ownerDocument = this.el.ownerDocument;
    off(ownerDocument, "mouseup", this._disableDelayedDrag);
    off(ownerDocument, "touchend", this._disableDelayedDrag);
    off(ownerDocument, "touchcancel", this._disableDelayedDrag);
    off(ownerDocument, "mousemove", this._delayedDragTouchMoveHandler);
    off(ownerDocument, "touchmove", this._delayedDragTouchMoveHandler);
    off(ownerDocument, "pointermove", this._delayedDragTouchMoveHandler);
  },
  _triggerDragStart: function _triggerDragStart(evt, touch) {
    touch = touch || evt.pointerType == "touch" && evt;
    if (!this.nativeDraggable || touch) {
      if (this.options.supportPointer) {
        on4(document, "pointermove", this._onTouchMove);
      } else if (touch) {
        on4(document, "touchmove", this._onTouchMove);
      } else {
        on4(document, "mousemove", this._onTouchMove);
      }
    } else {
      on4(dragEl, "dragend", this);
      on4(rootEl, "dragstart", this._onDragStart);
    }
    try {
      if (document.selection) {
        _nextTick(function() {
          document.selection.empty();
        });
      } else {
        window.getSelection().removeAllRanges();
      }
    } catch (err) {
    }
  },
  _dragStarted: function _dragStarted(fallback, evt) {
    awaitingDragStarted = false;
    if (rootEl && dragEl) {
      pluginEvent2("dragStarted", this, {
        evt
      });
      if (this.nativeDraggable) {
        on4(document, "dragover", _checkOutsideTargetEl);
      }
      var options = this.options;
      !fallback && toggleClass(dragEl, options.dragClass, false);
      toggleClass(dragEl, options.ghostClass, true);
      Sortable.active = this;
      fallback && this._appendGhost();
      _dispatchEvent({
        sortable: this,
        name: "start",
        originalEvent: evt
      });
    } else {
      this._nulling();
    }
  },
  _emulateDragOver: function _emulateDragOver() {
    if (touchEvt) {
      this._lastX = touchEvt.clientX;
      this._lastY = touchEvt.clientY;
      _hideGhostForTarget();
      var target = document.elementFromPoint(touchEvt.clientX, touchEvt.clientY);
      var parent = target;
      while (target && target.shadowRoot) {
        target = target.shadowRoot.elementFromPoint(touchEvt.clientX, touchEvt.clientY);
        if (target === parent)
          break;
        parent = target;
      }
      dragEl.parentNode[expando]._isOutsideThisEl(target);
      if (parent) {
        do {
          if (parent[expando]) {
            var inserted = void 0;
            inserted = parent[expando]._onDragOver({
              clientX: touchEvt.clientX,
              clientY: touchEvt.clientY,
              target,
              rootEl: parent
            });
            if (inserted && !this.options.dragoverBubble) {
              break;
            }
          }
          target = parent;
        } while (parent = parent.parentNode);
      }
      _unhideGhostForTarget();
    }
  },
  _onTouchMove: function _onTouchMove(evt) {
    if (tapEvt) {
      var options = this.options, fallbackTolerance = options.fallbackTolerance, fallbackOffset = options.fallbackOffset, touch = evt.touches ? evt.touches[0] : evt, ghostMatrix = ghostEl && matrix(ghostEl, true), scaleX = ghostEl && ghostMatrix && ghostMatrix.a, scaleY = ghostEl && ghostMatrix && ghostMatrix.d, relativeScrollOffset = PositionGhostAbsolutely && ghostRelativeParent && getRelativeScrollOffset(ghostRelativeParent), dx = (touch.clientX - tapEvt.clientX + fallbackOffset.x) / (scaleX || 1) + (relativeScrollOffset ? relativeScrollOffset[0] - ghostRelativeParentInitialScroll[0] : 0) / (scaleX || 1), dy = (touch.clientY - tapEvt.clientY + fallbackOffset.y) / (scaleY || 1) + (relativeScrollOffset ? relativeScrollOffset[1] - ghostRelativeParentInitialScroll[1] : 0) / (scaleY || 1);
      if (!Sortable.active && !awaitingDragStarted) {
        if (fallbackTolerance && Math.max(Math.abs(touch.clientX - this._lastX), Math.abs(touch.clientY - this._lastY)) < fallbackTolerance) {
          return;
        }
        this._onDragStart(evt, true);
      }
      if (ghostEl) {
        if (ghostMatrix) {
          ghostMatrix.e += dx - (lastDx || 0);
          ghostMatrix.f += dy - (lastDy || 0);
        } else {
          ghostMatrix = {
            a: 1,
            b: 0,
            c: 0,
            d: 1,
            e: dx,
            f: dy
          };
        }
        var cssMatrix = "matrix(".concat(ghostMatrix.a, ",").concat(ghostMatrix.b, ",").concat(ghostMatrix.c, ",").concat(ghostMatrix.d, ",").concat(ghostMatrix.e, ",").concat(ghostMatrix.f, ")");
        css(ghostEl, "webkitTransform", cssMatrix);
        css(ghostEl, "mozTransform", cssMatrix);
        css(ghostEl, "msTransform", cssMatrix);
        css(ghostEl, "transform", cssMatrix);
        lastDx = dx;
        lastDy = dy;
        touchEvt = touch;
      }
      evt.cancelable && evt.preventDefault();
    }
  },
  _appendGhost: function _appendGhost() {
    if (!ghostEl) {
      var container = this.options.fallbackOnBody ? document.body : rootEl, rect = getRect(dragEl, true, PositionGhostAbsolutely, true, container), options = this.options;
      if (PositionGhostAbsolutely) {
        ghostRelativeParent = container;
        while (css(ghostRelativeParent, "position") === "static" && css(ghostRelativeParent, "transform") === "none" && ghostRelativeParent !== document) {
          ghostRelativeParent = ghostRelativeParent.parentNode;
        }
        if (ghostRelativeParent !== document.body && ghostRelativeParent !== document.documentElement) {
          if (ghostRelativeParent === document)
            ghostRelativeParent = getWindowScrollingElement();
          rect.top += ghostRelativeParent.scrollTop;
          rect.left += ghostRelativeParent.scrollLeft;
        } else {
          ghostRelativeParent = getWindowScrollingElement();
        }
        ghostRelativeParentInitialScroll = getRelativeScrollOffset(ghostRelativeParent);
      }
      ghostEl = dragEl.cloneNode(true);
      toggleClass(ghostEl, options.ghostClass, false);
      toggleClass(ghostEl, options.fallbackClass, true);
      toggleClass(ghostEl, options.dragClass, true);
      css(ghostEl, "transition", "");
      css(ghostEl, "transform", "");
      css(ghostEl, "box-sizing", "border-box");
      css(ghostEl, "margin", 0);
      css(ghostEl, "top", rect.top);
      css(ghostEl, "left", rect.left);
      css(ghostEl, "width", rect.width);
      css(ghostEl, "height", rect.height);
      css(ghostEl, "opacity", "0.8");
      css(ghostEl, "position", PositionGhostAbsolutely ? "absolute" : "fixed");
      css(ghostEl, "zIndex", "100000");
      css(ghostEl, "pointerEvents", "none");
      Sortable.ghost = ghostEl;
      container.appendChild(ghostEl);
      css(ghostEl, "transform-origin", tapDistanceLeft / parseInt(ghostEl.style.width) * 100 + "% " + tapDistanceTop / parseInt(ghostEl.style.height) * 100 + "%");
    }
  },
  _onDragStart: function _onDragStart(evt, fallback) {
    var _this = this;
    var dataTransfer = evt.dataTransfer;
    var options = _this.options;
    pluginEvent2("dragStart", this, {
      evt
    });
    if (Sortable.eventCanceled) {
      this._onDrop();
      return;
    }
    pluginEvent2("setupClone", this);
    if (!Sortable.eventCanceled) {
      cloneEl = clone2(dragEl);
      cloneEl.removeAttribute("id");
      cloneEl.draggable = false;
      cloneEl.style["will-change"] = "";
      this._hideClone();
      toggleClass(cloneEl, this.options.chosenClass, false);
      Sortable.clone = cloneEl;
    }
    _this.cloneId = _nextTick(function() {
      pluginEvent2("clone", _this);
      if (Sortable.eventCanceled)
        return;
      if (!_this.options.removeCloneOnHide) {
        rootEl.insertBefore(cloneEl, dragEl);
      }
      _this._hideClone();
      _dispatchEvent({
        sortable: _this,
        name: "clone"
      });
    });
    !fallback && toggleClass(dragEl, options.dragClass, true);
    if (fallback) {
      ignoreNextClick = true;
      _this._loopId = setInterval(_this._emulateDragOver, 50);
    } else {
      off(document, "mouseup", _this._onDrop);
      off(document, "touchend", _this._onDrop);
      off(document, "touchcancel", _this._onDrop);
      if (dataTransfer) {
        dataTransfer.effectAllowed = "move";
        options.setData && options.setData.call(_this, dataTransfer, dragEl);
      }
      on4(document, "drop", _this);
      css(dragEl, "transform", "translateZ(0)");
    }
    awaitingDragStarted = true;
    _this._dragStartId = _nextTick(_this._dragStarted.bind(_this, fallback, evt));
    on4(document, "selectstart", _this);
    moved = true;
    if (Safari) {
      css(document.body, "user-select", "none");
    }
  },
  // Returns true - if no further action is needed (either inserted or another condition)
  _onDragOver: function _onDragOver(evt) {
    var el = this.el, target = evt.target, dragRect, targetRect, revert, options = this.options, group = options.group, activeSortable = Sortable.active, isOwner = activeGroup === group, canSort = options.sort, fromSortable = putSortable || activeSortable, vertical, _this = this, completedFired = false;
    if (_silent)
      return;
    function dragOverEvent(name, extra) {
      pluginEvent2(name, _this, _objectSpread22({
        evt,
        isOwner,
        axis: vertical ? "vertical" : "horizontal",
        revert,
        dragRect,
        targetRect,
        canSort,
        fromSortable,
        target,
        completed,
        onMove: function onMove(target2, after2) {
          return _onMove(rootEl, el, dragEl, dragRect, target2, getRect(target2), evt, after2);
        },
        changed
      }, extra));
    }
    function capture() {
      dragOverEvent("dragOverAnimationCapture");
      _this.captureAnimationState();
      if (_this !== fromSortable) {
        fromSortable.captureAnimationState();
      }
    }
    function completed(insertion) {
      dragOverEvent("dragOverCompleted", {
        insertion
      });
      if (insertion) {
        if (isOwner) {
          activeSortable._hideClone();
        } else {
          activeSortable._showClone(_this);
        }
        if (_this !== fromSortable) {
          toggleClass(dragEl, putSortable ? putSortable.options.ghostClass : activeSortable.options.ghostClass, false);
          toggleClass(dragEl, options.ghostClass, true);
        }
        if (putSortable !== _this && _this !== Sortable.active) {
          putSortable = _this;
        } else if (_this === Sortable.active && putSortable) {
          putSortable = null;
        }
        if (fromSortable === _this) {
          _this._ignoreWhileAnimating = target;
        }
        _this.animateAll(function() {
          dragOverEvent("dragOverAnimationComplete");
          _this._ignoreWhileAnimating = null;
        });
        if (_this !== fromSortable) {
          fromSortable.animateAll();
          fromSortable._ignoreWhileAnimating = null;
        }
      }
      if (target === dragEl && !dragEl.animated || target === el && !target.animated) {
        lastTarget = null;
      }
      if (!options.dragoverBubble && !evt.rootEl && target !== document) {
        dragEl.parentNode[expando]._isOutsideThisEl(evt.target);
        !insertion && nearestEmptyInsertDetectEvent(evt);
      }
      !options.dragoverBubble && evt.stopPropagation && evt.stopPropagation();
      return completedFired = true;
    }
    function changed() {
      newIndex = index(dragEl);
      newDraggableIndex = index(dragEl, options.draggable);
      _dispatchEvent({
        sortable: _this,
        name: "change",
        toEl: el,
        newIndex,
        newDraggableIndex,
        originalEvent: evt
      });
    }
    if (evt.preventDefault !== void 0) {
      evt.cancelable && evt.preventDefault();
    }
    target = closest(target, options.draggable, el, true);
    dragOverEvent("dragOver");
    if (Sortable.eventCanceled)
      return completedFired;
    if (dragEl.contains(evt.target) || target.animated && target.animatingX && target.animatingY || _this._ignoreWhileAnimating === target) {
      return completed(false);
    }
    ignoreNextClick = false;
    if (activeSortable && !options.disabled && (isOwner ? canSort || (revert = parentEl !== rootEl) : putSortable === this || (this.lastPutMode = activeGroup.checkPull(this, activeSortable, dragEl, evt)) && group.checkPut(this, activeSortable, dragEl, evt))) {
      vertical = this._getDirection(evt, target) === "vertical";
      dragRect = getRect(dragEl);
      dragOverEvent("dragOverValid");
      if (Sortable.eventCanceled)
        return completedFired;
      if (revert) {
        parentEl = rootEl;
        capture();
        this._hideClone();
        dragOverEvent("revert");
        if (!Sortable.eventCanceled) {
          if (nextEl) {
            rootEl.insertBefore(dragEl, nextEl);
          } else {
            rootEl.appendChild(dragEl);
          }
        }
        return completed(true);
      }
      var elLastChild = lastChild(el, options.draggable);
      if (!elLastChild || _ghostIsLast(evt, vertical, this) && !elLastChild.animated) {
        if (elLastChild === dragEl) {
          return completed(false);
        }
        if (elLastChild && el === evt.target) {
          target = elLastChild;
        }
        if (target) {
          targetRect = getRect(target);
        }
        if (_onMove(rootEl, el, dragEl, dragRect, target, targetRect, evt, !!target) !== false) {
          capture();
          if (elLastChild && elLastChild.nextSibling) {
            el.insertBefore(dragEl, elLastChild.nextSibling);
          } else {
            el.appendChild(dragEl);
          }
          parentEl = el;
          changed();
          return completed(true);
        }
      } else if (elLastChild && _ghostIsFirst(evt, vertical, this)) {
        var firstChild = getChild(el, 0, options, true);
        if (firstChild === dragEl) {
          return completed(false);
        }
        target = firstChild;
        targetRect = getRect(target);
        if (_onMove(rootEl, el, dragEl, dragRect, target, targetRect, evt, false) !== false) {
          capture();
          el.insertBefore(dragEl, firstChild);
          parentEl = el;
          changed();
          return completed(true);
        }
      } else if (target.parentNode === el) {
        targetRect = getRect(target);
        var direction = 0, targetBeforeFirstSwap, differentLevel = dragEl.parentNode !== el, differentRowCol = !_dragElInRowColumn(dragEl.animated && dragEl.toRect || dragRect, target.animated && target.toRect || targetRect, vertical), side1 = vertical ? "top" : "left", scrolledPastTop = isScrolledPast(target, "top", "top") || isScrolledPast(dragEl, "top", "top"), scrollBefore = scrolledPastTop ? scrolledPastTop.scrollTop : void 0;
        if (lastTarget !== target) {
          targetBeforeFirstSwap = targetRect[side1];
          pastFirstInvertThresh = false;
          isCircumstantialInvert = !differentRowCol && options.invertSwap || differentLevel;
        }
        direction = _getSwapDirection(evt, target, targetRect, vertical, differentRowCol ? 1 : options.swapThreshold, options.invertedSwapThreshold == null ? options.swapThreshold : options.invertedSwapThreshold, isCircumstantialInvert, lastTarget === target);
        var sibling;
        if (direction !== 0) {
          var dragIndex = index(dragEl);
          do {
            dragIndex -= direction;
            sibling = parentEl.children[dragIndex];
          } while (sibling && (css(sibling, "display") === "none" || sibling === ghostEl));
        }
        if (direction === 0 || sibling === target) {
          return completed(false);
        }
        lastTarget = target;
        lastDirection = direction;
        var nextSibling = target.nextElementSibling, after = false;
        after = direction === 1;
        var moveVector = _onMove(rootEl, el, dragEl, dragRect, target, targetRect, evt, after);
        if (moveVector !== false) {
          if (moveVector === 1 || moveVector === -1) {
            after = moveVector === 1;
          }
          _silent = true;
          setTimeout(_unsilent, 30);
          capture();
          if (after && !nextSibling) {
            el.appendChild(dragEl);
          } else {
            target.parentNode.insertBefore(dragEl, after ? nextSibling : target);
          }
          if (scrolledPastTop) {
            scrollBy(scrolledPastTop, 0, scrollBefore - scrolledPastTop.scrollTop);
          }
          parentEl = dragEl.parentNode;
          if (targetBeforeFirstSwap !== void 0 && !isCircumstantialInvert) {
            targetMoveDistance = Math.abs(targetBeforeFirstSwap - getRect(target)[side1]);
          }
          changed();
          return completed(true);
        }
      }
      if (el.contains(dragEl)) {
        return completed(false);
      }
    }
    return false;
  },
  _ignoreWhileAnimating: null,
  _offMoveEvents: function _offMoveEvents() {
    off(document, "mousemove", this._onTouchMove);
    off(document, "touchmove", this._onTouchMove);
    off(document, "pointermove", this._onTouchMove);
    off(document, "dragover", nearestEmptyInsertDetectEvent);
    off(document, "mousemove", nearestEmptyInsertDetectEvent);
    off(document, "touchmove", nearestEmptyInsertDetectEvent);
  },
  _offUpEvents: function _offUpEvents() {
    var ownerDocument = this.el.ownerDocument;
    off(ownerDocument, "mouseup", this._onDrop);
    off(ownerDocument, "touchend", this._onDrop);
    off(ownerDocument, "pointerup", this._onDrop);
    off(ownerDocument, "touchcancel", this._onDrop);
    off(document, "selectstart", this);
  },
  _onDrop: function _onDrop(evt) {
    var el = this.el, options = this.options;
    newIndex = index(dragEl);
    newDraggableIndex = index(dragEl, options.draggable);
    pluginEvent2("drop", this, {
      evt
    });
    parentEl = dragEl && dragEl.parentNode;
    newIndex = index(dragEl);
    newDraggableIndex = index(dragEl, options.draggable);
    if (Sortable.eventCanceled) {
      this._nulling();
      return;
    }
    awaitingDragStarted = false;
    isCircumstantialInvert = false;
    pastFirstInvertThresh = false;
    clearInterval(this._loopId);
    clearTimeout(this._dragStartTimer);
    _cancelNextTick(this.cloneId);
    _cancelNextTick(this._dragStartId);
    if (this.nativeDraggable) {
      off(document, "drop", this);
      off(el, "dragstart", this._onDragStart);
    }
    this._offMoveEvents();
    this._offUpEvents();
    if (Safari) {
      css(document.body, "user-select", "");
    }
    css(dragEl, "transform", "");
    if (evt) {
      if (moved) {
        evt.cancelable && evt.preventDefault();
        !options.dropBubble && evt.stopPropagation();
      }
      ghostEl && ghostEl.parentNode && ghostEl.parentNode.removeChild(ghostEl);
      if (rootEl === parentEl || putSortable && putSortable.lastPutMode !== "clone") {
        cloneEl && cloneEl.parentNode && cloneEl.parentNode.removeChild(cloneEl);
      }
      if (dragEl) {
        if (this.nativeDraggable) {
          off(dragEl, "dragend", this);
        }
        _disableDraggable(dragEl);
        dragEl.style["will-change"] = "";
        if (moved && !awaitingDragStarted) {
          toggleClass(dragEl, putSortable ? putSortable.options.ghostClass : this.options.ghostClass, false);
        }
        toggleClass(dragEl, this.options.chosenClass, false);
        _dispatchEvent({
          sortable: this,
          name: "unchoose",
          toEl: parentEl,
          newIndex: null,
          newDraggableIndex: null,
          originalEvent: evt
        });
        if (rootEl !== parentEl) {
          if (newIndex >= 0) {
            _dispatchEvent({
              rootEl: parentEl,
              name: "add",
              toEl: parentEl,
              fromEl: rootEl,
              originalEvent: evt
            });
            _dispatchEvent({
              sortable: this,
              name: "remove",
              toEl: parentEl,
              originalEvent: evt
            });
            _dispatchEvent({
              rootEl: parentEl,
              name: "sort",
              toEl: parentEl,
              fromEl: rootEl,
              originalEvent: evt
            });
            _dispatchEvent({
              sortable: this,
              name: "sort",
              toEl: parentEl,
              originalEvent: evt
            });
          }
          putSortable && putSortable.save();
        } else {
          if (newIndex !== oldIndex) {
            if (newIndex >= 0) {
              _dispatchEvent({
                sortable: this,
                name: "update",
                toEl: parentEl,
                originalEvent: evt
              });
              _dispatchEvent({
                sortable: this,
                name: "sort",
                toEl: parentEl,
                originalEvent: evt
              });
            }
          }
        }
        if (Sortable.active) {
          if (newIndex == null || newIndex === -1) {
            newIndex = oldIndex;
            newDraggableIndex = oldDraggableIndex;
          }
          _dispatchEvent({
            sortable: this,
            name: "end",
            toEl: parentEl,
            originalEvent: evt
          });
          this.save();
        }
      }
    }
    this._nulling();
  },
  _nulling: function _nulling() {
    pluginEvent2("nulling", this);
    rootEl = dragEl = parentEl = ghostEl = nextEl = cloneEl = lastDownEl = cloneHidden = tapEvt = touchEvt = moved = newIndex = newDraggableIndex = oldIndex = oldDraggableIndex = lastTarget = lastDirection = putSortable = activeGroup = Sortable.dragged = Sortable.ghost = Sortable.clone = Sortable.active = null;
    savedInputChecked.forEach(function(el) {
      el.checked = true;
    });
    savedInputChecked.length = lastDx = lastDy = 0;
  },
  handleEvent: function handleEvent(evt) {
    switch (evt.type) {
      case "drop":
      case "dragend":
        this._onDrop(evt);
        break;
      case "dragenter":
      case "dragover":
        if (dragEl) {
          this._onDragOver(evt);
          _globalDragOver(evt);
        }
        break;
      case "selectstart":
        evt.preventDefault();
        break;
    }
  },
  /**
   * Serializes the item into an array of string.
   * @returns {String[]}
   */
  toArray: function toArray() {
    var order = [], el, children = this.el.children, i = 0, n = children.length, options = this.options;
    for (; i < n; i++) {
      el = children[i];
      if (closest(el, options.draggable, this.el, false)) {
        order.push(el.getAttribute(options.dataIdAttr) || _generateId(el));
      }
    }
    return order;
  },
  /**
   * Sorts the elements according to the array.
   * @param  {String[]}  order  order of the items
   */
  sort: function sort(order, useAnimation) {
    var items = {}, rootEl2 = this.el;
    this.toArray().forEach(function(id, i) {
      var el = rootEl2.children[i];
      if (closest(el, this.options.draggable, rootEl2, false)) {
        items[id] = el;
      }
    }, this);
    useAnimation && this.captureAnimationState();
    order.forEach(function(id) {
      if (items[id]) {
        rootEl2.removeChild(items[id]);
        rootEl2.appendChild(items[id]);
      }
    });
    useAnimation && this.animateAll();
  },
  /**
   * Save the current sorting
   */
  save: function save() {
    var store2 = this.options.store;
    store2 && store2.set && store2.set(this);
  },
  /**
   * For each element in the set, get the first element that matches the selector by testing the element itself and traversing up through its ancestors in the DOM tree.
   * @param   {HTMLElement}  el
   * @param   {String}       [selector]  default: `options.draggable`
   * @returns {HTMLElement|null}
   */
  closest: function closest$1(el, selector) {
    return closest(el, selector || this.options.draggable, this.el, false);
  },
  /**
   * Set/get option
   * @param   {string} name
   * @param   {*}      [value]
   * @returns {*}
   */
  option: function option(name, value) {
    var options = this.options;
    if (value === void 0) {
      return options[name];
    } else {
      var modifiedValue = PluginManager.modifyOption(this, name, value);
      if (typeof modifiedValue !== "undefined") {
        options[name] = modifiedValue;
      } else {
        options[name] = value;
      }
      if (name === "group") {
        _prepareGroup(options);
      }
    }
  },
  /**
   * Destroy
   */
  destroy: function destroy() {
    pluginEvent2("destroy", this);
    var el = this.el;
    el[expando] = null;
    off(el, "mousedown", this._onTapStart);
    off(el, "touchstart", this._onTapStart);
    off(el, "pointerdown", this._onTapStart);
    if (this.nativeDraggable) {
      off(el, "dragover", this);
      off(el, "dragenter", this);
    }
    Array.prototype.forEach.call(el.querySelectorAll("[draggable]"), function(el2) {
      el2.removeAttribute("draggable");
    });
    this._onDrop();
    this._disableDelayedDragEvents();
    sortables.splice(sortables.indexOf(this.el), 1);
    this.el = el = null;
  },
  _hideClone: function _hideClone() {
    if (!cloneHidden) {
      pluginEvent2("hideClone", this);
      if (Sortable.eventCanceled)
        return;
      css(cloneEl, "display", "none");
      if (this.options.removeCloneOnHide && cloneEl.parentNode) {
        cloneEl.parentNode.removeChild(cloneEl);
      }
      cloneHidden = true;
    }
  },
  _showClone: function _showClone(putSortable2) {
    if (putSortable2.lastPutMode !== "clone") {
      this._hideClone();
      return;
    }
    if (cloneHidden) {
      pluginEvent2("showClone", this);
      if (Sortable.eventCanceled)
        return;
      if (dragEl.parentNode == rootEl && !this.options.group.revertClone) {
        rootEl.insertBefore(cloneEl, dragEl);
      } else if (nextEl) {
        rootEl.insertBefore(cloneEl, nextEl);
      } else {
        rootEl.appendChild(cloneEl);
      }
      if (this.options.group.revertClone) {
        this.animate(dragEl, cloneEl);
      }
      css(cloneEl, "display", "");
      cloneHidden = false;
    }
  }
};
function _globalDragOver(evt) {
  if (evt.dataTransfer) {
    evt.dataTransfer.dropEffect = "move";
  }
  evt.cancelable && evt.preventDefault();
}
function _onMove(fromEl, toEl, dragEl2, dragRect, targetEl, targetRect, originalEvent, willInsertAfter) {
  var evt, sortable = fromEl[expando], onMoveFn = sortable.options.onMove, retVal;
  if (window.CustomEvent && !IE11OrLess && !Edge) {
    evt = new CustomEvent("move", {
      bubbles: true,
      cancelable: true
    });
  } else {
    evt = document.createEvent("Event");
    evt.initEvent("move", true, true);
  }
  evt.to = toEl;
  evt.from = fromEl;
  evt.dragged = dragEl2;
  evt.draggedRect = dragRect;
  evt.related = targetEl || toEl;
  evt.relatedRect = targetRect || getRect(toEl);
  evt.willInsertAfter = willInsertAfter;
  evt.originalEvent = originalEvent;
  fromEl.dispatchEvent(evt);
  if (onMoveFn) {
    retVal = onMoveFn.call(sortable, evt, originalEvent);
  }
  return retVal;
}
function _disableDraggable(el) {
  el.draggable = false;
}
function _unsilent() {
  _silent = false;
}
function _ghostIsFirst(evt, vertical, sortable) {
  var firstElRect = getRect(getChild(sortable.el, 0, sortable.options, true));
  var childContainingRect = getChildContainingRectFromElement(sortable.el, sortable.options, ghostEl);
  var spacer = 10;
  return vertical ? evt.clientX < childContainingRect.left - spacer || evt.clientY < firstElRect.top && evt.clientX < firstElRect.right : evt.clientY < childContainingRect.top - spacer || evt.clientY < firstElRect.bottom && evt.clientX < firstElRect.left;
}
function _ghostIsLast(evt, vertical, sortable) {
  var lastElRect = getRect(lastChild(sortable.el, sortable.options.draggable));
  var childContainingRect = getChildContainingRectFromElement(sortable.el, sortable.options, ghostEl);
  var spacer = 10;
  return vertical ? evt.clientX > childContainingRect.right + spacer || evt.clientY > lastElRect.bottom && evt.clientX > lastElRect.left : evt.clientY > childContainingRect.bottom + spacer || evt.clientX > lastElRect.right && evt.clientY > lastElRect.top;
}
function _getSwapDirection(evt, target, targetRect, vertical, swapThreshold, invertedSwapThreshold, invertSwap, isLastTarget) {
  var mouseOnAxis = vertical ? evt.clientY : evt.clientX, targetLength = vertical ? targetRect.height : targetRect.width, targetS1 = vertical ? targetRect.top : targetRect.left, targetS2 = vertical ? targetRect.bottom : targetRect.right, invert = false;
  if (!invertSwap) {
    if (isLastTarget && targetMoveDistance < targetLength * swapThreshold) {
      if (!pastFirstInvertThresh && (lastDirection === 1 ? mouseOnAxis > targetS1 + targetLength * invertedSwapThreshold / 2 : mouseOnAxis < targetS2 - targetLength * invertedSwapThreshold / 2)) {
        pastFirstInvertThresh = true;
      }
      if (!pastFirstInvertThresh) {
        if (lastDirection === 1 ? mouseOnAxis < targetS1 + targetMoveDistance : mouseOnAxis > targetS2 - targetMoveDistance) {
          return -lastDirection;
        }
      } else {
        invert = true;
      }
    } else {
      if (mouseOnAxis > targetS1 + targetLength * (1 - swapThreshold) / 2 && mouseOnAxis < targetS2 - targetLength * (1 - swapThreshold) / 2) {
        return _getInsertDirection(target);
      }
    }
  }
  invert = invert || invertSwap;
  if (invert) {
    if (mouseOnAxis < targetS1 + targetLength * invertedSwapThreshold / 2 || mouseOnAxis > targetS2 - targetLength * invertedSwapThreshold / 2) {
      return mouseOnAxis > targetS1 + targetLength / 2 ? 1 : -1;
    }
  }
  return 0;
}
function _getInsertDirection(target) {
  if (index(dragEl) < index(target)) {
    return 1;
  } else {
    return -1;
  }
}
function _generateId(el) {
  var str = el.tagName + el.className + el.src + el.href + el.textContent, i = str.length, sum = 0;
  while (i--) {
    sum += str.charCodeAt(i);
  }
  return sum.toString(36);
}
function _saveInputCheckedState(root) {
  savedInputChecked.length = 0;
  var inputs = root.getElementsByTagName("input");
  var idx = inputs.length;
  while (idx--) {
    var el = inputs[idx];
    el.checked && savedInputChecked.push(el);
  }
}
function _nextTick(fn) {
  return setTimeout(fn, 0);
}
function _cancelNextTick(id) {
  return clearTimeout(id);
}
if (documentExists) {
  on4(document, "touchmove", function(evt) {
    if ((Sortable.active || awaitingDragStarted) && evt.cancelable) {
      evt.preventDefault();
    }
  });
}
Sortable.utils = {
  on: on4,
  off,
  css,
  find: find2,
  is: function is(el, selector) {
    return !!closest(el, selector, el, false);
  },
  extend: extend2,
  throttle: throttle2,
  closest,
  toggleClass,
  clone: clone2,
  index,
  nextTick: _nextTick,
  cancelNextTick: _cancelNextTick,
  detectDirection: _detectDirection,
  getChild
};
Sortable.get = function(element) {
  return element[expando];
};
Sortable.mount = function() {
  for (var _len = arguments.length, plugins2 = new Array(_len), _key = 0; _key < _len; _key++) {
    plugins2[_key] = arguments[_key];
  }
  if (plugins2[0].constructor === Array)
    plugins2 = plugins2[0];
  plugins2.forEach(function(plugin2) {
    if (!plugin2.prototype || !plugin2.prototype.constructor) {
      throw "Sortable: Mounted plugin must be a constructor function, not ".concat({}.toString.call(plugin2));
    }
    if (plugin2.utils)
      Sortable.utils = _objectSpread22(_objectSpread22({}, Sortable.utils), plugin2.utils);
    PluginManager.mount(plugin2);
  });
};
Sortable.create = function(el, options) {
  return new Sortable(el, options);
};
Sortable.version = version;
var autoScrolls = [];
var scrollEl;
var scrollRootEl;
var scrolling = false;
var lastAutoScrollX;
var lastAutoScrollY;
var touchEvt$1;
var pointerElemChangedInterval;
function AutoScrollPlugin() {
  function AutoScroll() {
    this.defaults = {
      scroll: true,
      forceAutoScrollFallback: false,
      scrollSensitivity: 30,
      scrollSpeed: 10,
      bubbleScroll: true
    };
    for (var fn in this) {
      if (fn.charAt(0) === "_" && typeof this[fn] === "function") {
        this[fn] = this[fn].bind(this);
      }
    }
  }
  AutoScroll.prototype = {
    dragStarted: function dragStarted(_ref) {
      var originalEvent = _ref.originalEvent;
      if (this.sortable.nativeDraggable) {
        on4(document, "dragover", this._handleAutoScroll);
      } else {
        if (this.options.supportPointer) {
          on4(document, "pointermove", this._handleFallbackAutoScroll);
        } else if (originalEvent.touches) {
          on4(document, "touchmove", this._handleFallbackAutoScroll);
        } else {
          on4(document, "mousemove", this._handleFallbackAutoScroll);
        }
      }
    },
    dragOverCompleted: function dragOverCompleted(_ref2) {
      var originalEvent = _ref2.originalEvent;
      if (!this.options.dragOverBubble && !originalEvent.rootEl) {
        this._handleAutoScroll(originalEvent);
      }
    },
    drop: function drop3() {
      if (this.sortable.nativeDraggable) {
        off(document, "dragover", this._handleAutoScroll);
      } else {
        off(document, "pointermove", this._handleFallbackAutoScroll);
        off(document, "touchmove", this._handleFallbackAutoScroll);
        off(document, "mousemove", this._handleFallbackAutoScroll);
      }
      clearPointerElemChangedInterval();
      clearAutoScrolls();
      cancelThrottle();
    },
    nulling: function nulling() {
      touchEvt$1 = scrollRootEl = scrollEl = scrolling = pointerElemChangedInterval = lastAutoScrollX = lastAutoScrollY = null;
      autoScrolls.length = 0;
    },
    _handleFallbackAutoScroll: function _handleFallbackAutoScroll(evt) {
      this._handleAutoScroll(evt, true);
    },
    _handleAutoScroll: function _handleAutoScroll(evt, fallback) {
      var _this = this;
      var x = (evt.touches ? evt.touches[0] : evt).clientX, y = (evt.touches ? evt.touches[0] : evt).clientY, elem = document.elementFromPoint(x, y);
      touchEvt$1 = evt;
      if (fallback || this.options.forceAutoScrollFallback || Edge || IE11OrLess || Safari) {
        autoScroll(evt, this.options, elem, fallback);
        var ogElemScroller = getParentAutoScrollElement(elem, true);
        if (scrolling && (!pointerElemChangedInterval || x !== lastAutoScrollX || y !== lastAutoScrollY)) {
          pointerElemChangedInterval && clearPointerElemChangedInterval();
          pointerElemChangedInterval = setInterval(function() {
            var newElem = getParentAutoScrollElement(document.elementFromPoint(x, y), true);
            if (newElem !== ogElemScroller) {
              ogElemScroller = newElem;
              clearAutoScrolls();
            }
            autoScroll(evt, _this.options, newElem, fallback);
          }, 10);
          lastAutoScrollX = x;
          lastAutoScrollY = y;
        }
      } else {
        if (!this.options.bubbleScroll || getParentAutoScrollElement(elem, true) === getWindowScrollingElement()) {
          clearAutoScrolls();
          return;
        }
        autoScroll(evt, this.options, getParentAutoScrollElement(elem, false), false);
      }
    }
  };
  return _extends(AutoScroll, {
    pluginName: "scroll",
    initializeByDefault: true
  });
}
function clearAutoScrolls() {
  autoScrolls.forEach(function(autoScroll2) {
    clearInterval(autoScroll2.pid);
  });
  autoScrolls = [];
}
function clearPointerElemChangedInterval() {
  clearInterval(pointerElemChangedInterval);
}
var autoScroll = throttle2(function(evt, options, rootEl2, isFallback) {
  if (!options.scroll)
    return;
  var x = (evt.touches ? evt.touches[0] : evt).clientX, y = (evt.touches ? evt.touches[0] : evt).clientY, sens = options.scrollSensitivity, speed = options.scrollSpeed, winScroller = getWindowScrollingElement();
  var scrollThisInstance = false, scrollCustomFn;
  if (scrollRootEl !== rootEl2) {
    scrollRootEl = rootEl2;
    clearAutoScrolls();
    scrollEl = options.scroll;
    scrollCustomFn = options.scrollFn;
    if (scrollEl === true) {
      scrollEl = getParentAutoScrollElement(rootEl2, true);
    }
  }
  var layersOut = 0;
  var currentParent = scrollEl;
  do {
    var el = currentParent, rect = getRect(el), top = rect.top, bottom = rect.bottom, left = rect.left, right = rect.right, width = rect.width, height = rect.height, canScrollX = void 0, canScrollY = void 0, scrollWidth = el.scrollWidth, scrollHeight = el.scrollHeight, elCSS = css(el), scrollPosX = el.scrollLeft, scrollPosY = el.scrollTop;
    if (el === winScroller) {
      canScrollX = width < scrollWidth && (elCSS.overflowX === "auto" || elCSS.overflowX === "scroll" || elCSS.overflowX === "visible");
      canScrollY = height < scrollHeight && (elCSS.overflowY === "auto" || elCSS.overflowY === "scroll" || elCSS.overflowY === "visible");
    } else {
      canScrollX = width < scrollWidth && (elCSS.overflowX === "auto" || elCSS.overflowX === "scroll");
      canScrollY = height < scrollHeight && (elCSS.overflowY === "auto" || elCSS.overflowY === "scroll");
    }
    var vx = canScrollX && (Math.abs(right - x) <= sens && scrollPosX + width < scrollWidth) - (Math.abs(left - x) <= sens && !!scrollPosX);
    var vy = canScrollY && (Math.abs(bottom - y) <= sens && scrollPosY + height < scrollHeight) - (Math.abs(top - y) <= sens && !!scrollPosY);
    if (!autoScrolls[layersOut]) {
      for (var i = 0; i <= layersOut; i++) {
        if (!autoScrolls[i]) {
          autoScrolls[i] = {};
        }
      }
    }
    if (autoScrolls[layersOut].vx != vx || autoScrolls[layersOut].vy != vy || autoScrolls[layersOut].el !== el) {
      autoScrolls[layersOut].el = el;
      autoScrolls[layersOut].vx = vx;
      autoScrolls[layersOut].vy = vy;
      clearInterval(autoScrolls[layersOut].pid);
      if (vx != 0 || vy != 0) {
        scrollThisInstance = true;
        autoScrolls[layersOut].pid = setInterval(function() {
          if (isFallback && this.layer === 0) {
            Sortable.active._onTouchMove(touchEvt$1);
          }
          var scrollOffsetY = autoScrolls[this.layer].vy ? autoScrolls[this.layer].vy * speed : 0;
          var scrollOffsetX = autoScrolls[this.layer].vx ? autoScrolls[this.layer].vx * speed : 0;
          if (typeof scrollCustomFn === "function") {
            if (scrollCustomFn.call(Sortable.dragged.parentNode[expando], scrollOffsetX, scrollOffsetY, evt, touchEvt$1, autoScrolls[this.layer].el) !== "continue") {
              return;
            }
          }
          scrollBy(autoScrolls[this.layer].el, scrollOffsetX, scrollOffsetY);
        }.bind({
          layer: layersOut
        }), 24);
      }
    }
    layersOut++;
  } while (options.bubbleScroll && currentParent !== winScroller && (currentParent = getParentAutoScrollElement(currentParent, false)));
  scrolling = scrollThisInstance;
}, 30);
var drop = function drop2(_ref) {
  var originalEvent = _ref.originalEvent, putSortable2 = _ref.putSortable, dragEl2 = _ref.dragEl, activeSortable = _ref.activeSortable, dispatchSortableEvent = _ref.dispatchSortableEvent, hideGhostForTarget = _ref.hideGhostForTarget, unhideGhostForTarget = _ref.unhideGhostForTarget;
  if (!originalEvent)
    return;
  var toSortable = putSortable2 || activeSortable;
  hideGhostForTarget();
  var touch = originalEvent.changedTouches && originalEvent.changedTouches.length ? originalEvent.changedTouches[0] : originalEvent;
  var target = document.elementFromPoint(touch.clientX, touch.clientY);
  unhideGhostForTarget();
  if (toSortable && !toSortable.el.contains(target)) {
    dispatchSortableEvent("spill");
    this.onSpill({
      dragEl: dragEl2,
      putSortable: putSortable2
    });
  }
};
function Revert() {
}
Revert.prototype = {
  startIndex: null,
  dragStart: function dragStart(_ref2) {
    var oldDraggableIndex2 = _ref2.oldDraggableIndex;
    this.startIndex = oldDraggableIndex2;
  },
  onSpill: function onSpill(_ref3) {
    var dragEl2 = _ref3.dragEl, putSortable2 = _ref3.putSortable;
    this.sortable.captureAnimationState();
    if (putSortable2) {
      putSortable2.captureAnimationState();
    }
    var nextSibling = getChild(this.sortable.el, this.startIndex, this.options);
    if (nextSibling) {
      this.sortable.el.insertBefore(dragEl2, nextSibling);
    } else {
      this.sortable.el.appendChild(dragEl2);
    }
    this.sortable.animateAll();
    if (putSortable2) {
      putSortable2.animateAll();
    }
  },
  drop
};
_extends(Revert, {
  pluginName: "revertOnSpill"
});
function Remove() {
}
Remove.prototype = {
  onSpill: function onSpill2(_ref4) {
    var dragEl2 = _ref4.dragEl, putSortable2 = _ref4.putSortable;
    var parentSortable = putSortable2 || this.sortable;
    parentSortable.captureAnimationState();
    dragEl2.parentNode && dragEl2.parentNode.removeChild(dragEl2);
    parentSortable.animateAll();
  },
  drop
};
_extends(Remove, {
  pluginName: "removeOnSpill"
});
Sortable.mount(new AutoScrollPlugin());
Sortable.mount(Remove, Revert);
var sortable_esm_default = Sortable;
function walk2(el, callback) {
  if (typeof ShadowRoot === "function" && el instanceof ShadowRoot) {
    Array.from(el.children).forEach((el2) => walk2(el2, callback));
    return;
  }
  let skip = false;
  callback(el, () => skip = true);
  if (skip)
    return;
  let node = el.firstElementChild;
  while (node) {
    walk2(node, callback, false);
    node = node.nextElementSibling;
  }
}
function src_default9(Alpine2) {
  Alpine2.directive("sort", (el, { value, modifiers, expression }, { effect: effect3, evaluate: evaluate3, cleanup }) => {
    if (value === "config") {
      return;
    }
    if (value === "handle") {
      return;
    }
    if (value === "group") {
      return;
    }
    if (value === "key" || value === "item") {
      if ([void 0, null, ""].includes(expression))
        return;
      el._x_sort_key = evaluate3(expression);
      return;
    }
    let handleSelector = "[x-sort\\:handle],[wire\\:sort\\:handle]";
    let preferences = {
      hideGhost: !modifiers.includes("ghost"),
      useHandles: !!el.querySelector(handleSelector) || Array.from(el.querySelectorAll("template:not(svg template)")).some((tmpl) => tmpl.content?.querySelector(handleSelector)),
      group: getGroupName(el, modifiers)
    };
    let handleSort = generateSortHandler(expression, evaluate3);
    let config2 = getConfigurationOverrides(el, modifiers, evaluate3);
    let sortable = initSortable(el, config2, preferences, (key, position) => {
      handleSort(key, position);
    });
    cleanup(() => sortable.destroy());
  });
}
function generateSortHandler(expression, evaluate3) {
  if ([void 0, null, ""].includes(expression))
    return () => {
    };
  return (key, position) => {
    evaluate3(expression, { scope: {
      // Supporting both `$item` AND `$key` ($key for BC)...
      $key: key,
      $item: key,
      $position: position
    }, params: [
      key,
      position
    ] });
  };
}
function getConfigurationOverrides(el, modifiers, evaluate3) {
  if (el.hasAttribute("x-sort:config")) {
    return evaluate3(el.getAttribute("x-sort:config"));
  }
  if (el.hasAttribute("wire:sort:config")) {
    return evaluate3(el.getAttribute("wire:sort:config"));
  }
  return {};
}
function initSortable(el, config2, preferences, handle) {
  let ghostRef;
  let options = {
    animation: 150,
    handle: preferences.useHandles ? "[x-sort\\:handle],[wire\\:sort\\:handle]" : null,
    group: preferences.group,
    scroll: true,
    forceAutoScrollFallback: true,
    scrollSensitivity: 50,
    // This is here so that if a div containing inputs or buttons has x-sort:ignore, it will not prevent interaction...
    preventOnFilter: false,
    filter(e) {
      if (e.target.hasAttribute("x-sort:ignore") || e.target.hasAttribute("wire:sort:ignore"))
        return true;
      if (e.target.closest("[x-sort\\:ignore]") || e.target.closest("[wire\\:sort\\:ignore]"))
        return true;
      if (!el.querySelector("[x-sort\\:item],[wire\\:sort\\:item]"))
        return false;
      let itemHasAttribute = e.target.closest("[x-sort\\:item],[wire\\:sort\\:item]");
      return itemHasAttribute ? false : true;
    },
    onSort(e) {
      if (e.from !== e.to) {
        if (e.to !== e.target) {
          return;
        }
      }
      let key = void 0;
      walk2(e.item, (el2, skip) => {
        if (key !== void 0)
          return;
        if (el2._x_sort_key) {
          key = el2._x_sort_key;
          skip();
        }
      });
      let position = e.newIndex;
      if (key !== void 0 || key !== null) {
        handle(key, position);
      }
    },
    onStart() {
      document.body.classList.add("sorting");
      ghostRef = document.querySelector(".sortable-ghost");
      if (preferences.hideGhost && ghostRef)
        ghostRef.style.opacity = "0";
    },
    onEnd() {
      document.body.classList.remove("sorting");
      if (preferences.hideGhost && ghostRef)
        ghostRef.style.opacity = "1";
      ghostRef = void 0;
      keepElementsWithinMorphMarkers(el);
    }
  };
  return new sortable_esm_default(el, { ...options, ...config2 });
}
function keepElementsWithinMorphMarkers(el) {
  let cursor = el.firstChild;
  while (cursor.nextSibling) {
    if (cursor.textContent.trim() === "[if ENDBLOCK]><![endif]") {
      el.append(cursor);
      break;
    }
    cursor = cursor.nextSibling;
  }
}
function getGroupName(el, modifiers) {
  if (el.hasAttribute("x-sort:group")) {
    return el.getAttribute("x-sort:group");
  }
  if (el.hasAttribute("wire:sort:group")) {
    return el.getAttribute("wire:sort:group");
  }
  return modifiers.indexOf("group") !== -1 ? modifiers[modifiers.indexOf("group") + 1] : null;
}
var module_default9 = src_default9;

// node_modules/@alpinejs/resize/dist/module.esm.js
function src_default10(Alpine2) {
  Alpine2.directive("resize", Alpine2.skipDuringClone((el, { value, expression, modifiers }, { evaluateLater: evaluateLater2, cleanup }) => {
    let evaluator = evaluateLater2(expression);
    let evaluate3 = (width, height) => {
      evaluator(() => {
      }, { scope: { "$width": width, "$height": height } });
    };
    if (modifiers.includes("viewport")) {
      onViewportResize(evaluate3, cleanup);
      return;
    }
    let off2 = modifiers.includes("document") ? onDocumentResize(evaluate3) : onElResize(el, evaluate3);
    cleanup(() => off2());
  }));
}
function onElResize(el, callback) {
  let observer2 = new ResizeObserver((entries) => {
    let [width, height] = dimensions(entries);
    callback(width, height);
  });
  observer2.observe(el);
  return () => observer2.disconnect();
}
function onViewportResize(callback, cleanup) {
  let viewport = window.visualViewport;
  if (!viewport) {
    cleanup(onElResize(document.documentElement, callback));
    return;
  }
  let evaluate3 = () => callback(viewport.width, viewport.height);
  viewport.addEventListener("resize", evaluate3);
  cleanup(() => viewport.removeEventListener("resize", evaluate3));
  evaluate3();
}
var documentResizeObserver;
var documentResizeObserverCallbacks = /* @__PURE__ */ new Set();
function onDocumentResize(callback) {
  documentResizeObserverCallbacks.add(callback);
  if (!documentResizeObserver) {
    documentResizeObserver = new ResizeObserver((entries) => {
      let [width, height] = dimensions(entries);
      documentResizeObserverCallbacks.forEach((i) => i(width, height));
    });
    documentResizeObserver.observe(document.documentElement);
  }
  return () => {
    documentResizeObserverCallbacks.delete(callback);
  };
}
function dimensions(entries) {
  let width, height;
  for (let entry of entries) {
    width = entry.borderBoxSize[0].inlineSize;
    height = entry.borderBoxSize[0].blockSize;
  }
  return [width, height];
}
var module_default10 = src_default10;

// src/lifecycle.js
function initDirectives(el, component, names) {
  names.map((name) => extractDirective(el, name)).forEach((directive3) => {
    trigger2("directive.init", {
      el,
      component,
      directive: directive3,
      cleanup: (callback) => module_default.onAttributeRemoved(el, directive3.raw, callback)
    });
  });
}
function start2() {
  setTimeout(warnIfScriptIsMisplaced);
  dispatch2(document, "wire:init");
  dispatch2(document, "brew:init");
  dispatch2(document, "wire:initializing");
  dispatch2(document, "brew:initializing");
  [module_default2, module_default3, module_default9, module_default10, module_default4, module_default7, module_default5, module_default6, module_default8].forEach((plugin2) => module_default.plugin(plugin2));
  module_default.addRootSelector(() => "[wire\\:id]");
  module_default.onAttributesAdded((el, attributes) => {
    let names = [...attributes].map((attribute) => attribute.name).filter(matchesForBrewDirective);
    if (names.length === 0 || !el._x_marker) return;
    let component = findComponentByEl(el, false);
    if (component) initDirectives(el, component, names);
  });
  module_default.interceptInit(module_default.skipDuringClone((el) => {
    let names = el.getAttributeNames().filter(matchesForBrewDirective);
    if (names.length === 0) return;
    if (el.hasAttribute("wire:id") && !el.__brew && !hasComponent(el.getAttribute("wire:id"))) {
      let component2 = initComponent(el);
      module_default.onAttributeRemoved(el, "wire:id", () => destroyComponent(component2.id));
    }
    let component = findComponentByEl(el, false);
    if (!component) return;
    trigger2("element.init", { el, component });
    initDirectives(el, component, names);
  }));
  module_default.start();
  setTimeout(() => window.Wire.initialRenderIsFinished = true);
  dispatch2(document, "wire:initialized");
  dispatch2(document, "brew:initialized");
}
function warnIfScriptIsMisplaced() {
  let script = document.querySelector("script[data-update-uri][data-csrf]");
  if (script?.closest("[wire\\:id]")) {
    console.warn("Wire: missing closing tags found. Ensure your template elements contain matching closing tags.", script.closest("[wire\\:id]"));
  }
}

// src/request/interactions.js
function allActions(message, predicate) {
  return [...message.actions].every(predicate);
}
function hasBoundChildren(component) {
  let found = false;
  component.getDeepChildrenWithBindings(() => found = true);
  return found;
}
function coordinateNetworkInteractions() {
  interceptPartition(({ message, compileRequest: compileRequest2 }) => {
    if (message.component.isIsolated) compileRequest2([message]).isolated = true;
  });
  interceptPartition(({ message, compileRequest: compileRequest2 }) => {
    let bundled = [];
    message.component.getDeepChildrenWithBindings((child) => {
      bundled.push(createOrAddToPendingMessage(constructAction(child, "$commit")));
    });
    if (bundled.length > 0) compileRequest2([message, ...bundled]);
  });
  interceptAction(({ action }) => {
    let modifiers = action.origin?.directive?.modifiers || [];
    if (modifiers.includes("renderless")) action.metadata.renderless = true;
    if (modifiers.includes("async")) action.metadata.async = true;
    if (modifiers.includes("preserve-scroll")) action.metadata.preserveScroll = true;
    let active = messageBus().activeMessagesFor(action);
    let message = active[0];
    if (!message) return;
    if (message.isAsync() || action.isAsync()) return;
    if (action.metadata.type === "poll") return action.cancel();
    if (allActions(message, (i) => i.metadata.type === "poll")) return message.cancel();
    let bothModelLive = action.metadata.type === "model.live" && allActions(message, (i) => i.metadata.type === "model.live");
    if (bothModelLive && !hasBoundChildren(action.component) && ![...message.actions].some((i) => hasBoundChildren(i.component))) {
      active.filter((older) => allActions(older, (i) => i.metadata.type === "model.live")).forEach((older) => action.supersedes.add(older));
      return;
    }
    action.defer();
    message.addInterceptor(({ onFinish }) => onFinish(() => action.fire()));
  });
}

// src/request/legacy.js
function registerLegacyHooks() {
  interceptRequest(({ request, onFailure, onResponse, onError, onSuccess }) => {
    let respond = [];
    let succeed = [];
    let fail = [];
    trigger2("request", {
      url: request.uri,
      options: request.options,
      payload: request.options.body,
      respond: (cb) => respond.push(cb),
      succeed: (cb) => succeed.push(cb),
      fail: (cb) => fail.push(cb)
    });
    onResponse(({ response }) => respond.forEach((cb) => cb({ status: response.status, response })));
    onSuccess(({ response, json }) => succeed.forEach((cb) => cb({ status: response.status, json })));
    onFailure(() => fail.forEach((cb) => cb({ status: 503, content: null, preventDefault: () => {
    } })));
    onError(({ response, body, preventDefault }) => fail.forEach((cb) => cb({ status: response.status, content: body, preventDefault })));
  });
  interceptMessage(({ message, onCancel, onError, onSuccess, onFinish }) => {
    let respond = [];
    let succeed = [];
    let fail = [];
    trigger2("commit", {
      component: message.component,
      commit: message.payload,
      respond: (cb) => respond.push(cb),
      succeed: (cb) => succeed.push(cb),
      fail: (cb) => fail.push(cb)
    });
    onFinish(() => respond.forEach((cb) => cb()));
    onSuccess(({ payload, onRender }) => {
      onRender(() => succeed.forEach((cb) => cb({ snapshot: payload.snapshot, effects: payload.effects })));
    });
    onError(() => fail.forEach((cb) => cb()));
    onCancel(() => fail.forEach((cb) => cb()));
  });
}

// src/directives/transition.js
var defaultName = "match-element";
var assignedTransitionNames = /* @__PURE__ */ new WeakSet();
function registerTransitionDirective() {
  directive2("transition", () => {
  });
}
function setTransitionNames(root, options = {}) {
  let attribute = options.attribute ?? "wire:transition";
  let hasUsedDefaultName = false;
  elementsForAttribute(root, attribute, options.includeRoot).forEach((el) => {
    if (el.style.viewTransitionName) return;
    let name = el.getAttribute(attribute);
    if (!name && options.defaultName) {
      if (hasUsedDefaultName) {
        console.warn(`Brew: Only one unnamed [${attribute}] element can be transitioned per page. Give additional elements unique names.`);
        return;
      }
      name = options.defaultName;
      hasUsedDefaultName = true;
    }
    if (!name && options.type) return;
    el.style.viewTransitionName = name || defaultName;
    assignedTransitionNames.add(el);
  });
}
function clearTransitionNames(root, options = {}) {
  let attribute = options.attribute ?? "wire:transition";
  elementsForAttribute(root, attribute, options.includeRoot).forEach((el) => {
    if (!assignedTransitionNames.has(el)) return;
    el.style.viewTransitionName = "";
    assignedTransitionNames.delete(el);
  });
}
function selectorForAttribute(attribute) {
  return `[${attribute.replace(/[:.]/g, "\\$&")}]`;
}
function elementsForAttribute(root, attribute, includeRoot = false) {
  let selector = selectorForAttribute(attribute);
  let elements = Array.from(root.querySelectorAll(selector));
  if (includeRoot && root.matches?.(selector)) elements.unshift(root);
  return elements;
}
function startViewTransition(update, options = {}) {
  let transitionConfig = { update };
  if (options.type) transitionConfig.types = [options.type];
  try {
    return document.startViewTransition(transitionConfig);
  } catch (e) {
    return document.startViewTransition(update);
  }
}
function skipTransitionWhenTopLayerOpens(transition2) {
  transition2.ready.catch(() => {
  });
  let onBeforeToggle = (event) => {
    if (event.newState === "open" && event.target.matches?.("dialog, [popover]")) {
      transition2.skipTransition();
    }
  };
  document.addEventListener("beforetoggle", onBeforeToggle, true);
  let observer2 = new MutationObserver(() => {
    if (document.querySelector("dialog:modal")) {
      transition2.skipTransition();
      observer2.disconnect();
    }
  });
  observer2.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["open"],
    subtree: true
  });
  transition2.finished.finally(() => {
    observer2.disconnect();
    document.removeEventListener("beforetoggle", onBeforeToggle, true);
  }).catch(() => {
  });
}
function topLayerBlocksTransitions() {
  if (document.querySelector("dialog:modal")) return true;
  for (let popover of document.querySelectorAll(":popover-open")) {
    for (let el of popover.querySelectorAll("*")) {
      if (typeof el.checkVisibility === "function" ? el.checkVisibility() : false) return true;
    }
  }
  return false;
}
async function transitionDomMutation(fromEl, toEl, callback, options = {}) {
  if (options.skip) return callback();
  if (!fromEl.querySelector?.("[wire\\:transition]") && !toEl.querySelector?.("[wire\\:transition]")) {
    return callback();
  }
  if (typeof document.startViewTransition !== "function") return callback();
  if (topLayerBlocksTransitions()) return callback();
  setTransitionNames(fromEl, options);
  let style = document.createElement("style");
  style.textContent = `
        @media (prefers-reduced-motion: reduce) {
            ::view-transition-group(*), ::view-transition-old(*), ::view-transition-new(*) {
                animation: none !important;
            }
        }
        ::view-transition-old(root) { animation: none !important; opacity: 0 !important; }
        ::view-transition-new(root) { animation: none !important; opacity: 1 !important; }
    `;
  document.head.appendChild(style);
  let update = () => {
    callback();
    setTransitionNames(fromEl, options);
  };
  let cleanup = () => {
    style.remove();
    clearTransitionNames(fromEl);
  };
  let transition2 = startViewTransition(update, { type: options.type });
  skipTransitionWhenTopLayerOpens(transition2);
  transition2.finished.finally(cleanup).catch(() => {
  });
  await transition2.updateCallbackDone;
}

// src/morph.js
async function morph2(component, el, html) {
  let wrapper = document.createElement(tagNameOf(el.parentElement));
  wrapper.innerHTML = html;
  let parent = findComponentByEl(el.parentElement, false);
  if (parent) wrapper.__brew = parent;
  let to = wrapper.firstElementChild;
  to.setAttribute("wire:snapshot", component.snapshotEncoded);
  let { html: _html, ...effects } = component.effects;
  to.setAttribute("wire:effects", JSON.stringify(effects));
  to.__brew = component;
  trigger2("morph", { el, toEl: to, component });
  let existing = {};
  el.querySelectorAll("[wire\\:id]").forEach((child) => existing[child.getAttribute("wire:id")] = child);
  to.querySelectorAll("[wire\\:id]").forEach((child) => {
    if (child.hasAttribute("wire:snapshot")) return;
    let live = existing[child.getAttribute("wire:id")];
    if (live) child.replaceWith(live.cloneNode(true));
  });
  let transitionOptions = {};
  if (Object.prototype.hasOwnProperty.call(component.effects, "transition") && component.effects.transition) {
    transitionOptions = component.effects.transition;
  }
  await transitionDomMutation(el, to, () => {
    module_default.morph(el, to, morphConfig(component));
  }, transitionOptions);
  trigger2("morphed", { el, component });
}
function isElement2(el) {
  return typeof el.hasAttribute === "function";
}
function morphConfig(component) {
  return {
    updating: (el, toEl, childrenOnly, skip, skipChildren) => {
      if (!isElement2(el)) return;
      trigger2("morph.updating", { el, toEl, component, skip, childrenOnly, skipChildren });
      if (el.__brew_replace === true) el.innerHTML = toEl.innerHTML;
      if (el.__brew_replace_self === true) {
        el.outerHTML = toEl.outerHTML;
        return skip();
      }
      if (el.__brew_ignore === true) return skip();
      if (el.__brew_ignore_self === true) childrenOnly();
      if (el.__brew_ignore_children === true) return skipChildren();
      if (el.hasAttribute("wire:id")) {
        if (el.getAttribute("wire:id") !== component.id) return skip();
        toEl.__brew = component;
      }
    },
    updated: (el) => {
      if (isElement2(el)) trigger2("morph.updated", { el, component });
    },
    removing: (el, skip) => {
      if (isElement2(el)) trigger2("morph.removing", { el, component, skip });
    },
    removed: (el) => {
      if (isElement2(el)) trigger2("morph.removed", { el, component });
    },
    adding: (el) => {
      trigger2("morph.adding", { el, component });
    },
    added: (el) => {
      if (isElement2(el)) trigger2("morph.added", { el });
    },
    key: (el) => {
      if (el.hasAttribute("wire:id")) return el.getAttribute("wire:id");
      if (el.hasAttribute("wire:key")) return el.getAttribute("wire:key");
      return el.id;
    },
    lookahead: false
  };
}
function tagNameOf(el) {
  let tag = el.tagName.toLowerCase();
  return customElements.get(tag) ? "div" : tag;
}
function registerMorphEffect() {
  interceptMessage(({ message, onSuccess }) => {
    onSuccess(({ payload, onMorph }) => {
      onMorph(async () => {
        let html = payload.effects.html;
        if (html) await morph2(message.component, message.component.el, html);
      });
    });
  });
}

// src/directives/wildcard.js
var RESERVED = [
  "snapshot",
  "effects",
  "model",
  "init",
  "loading",
  "poll",
  "ignore",
  "id",
  "data",
  "key",
  "target",
  "dirty",
  "sort",
  "name",
  "cloak",
  "show",
  "text",
  "bind",
  "ref",
  "transition",
  "offline",
  "confirm",
  "navigate",
  "current",
  "persist",
  "scroll"
];
var BREW_ONLY_MODIFIERS = ["async", "renderless", "preserve-scroll", "prepend", "append", "island"];
function registerWildcardDirective() {
  on2("directive.init", ({ el, directive: directive3, cleanup, component }) => {
    if (RESERVED.includes(directive3.value) || customDirectiveHasBeenRegistered(directive3.value)) return;
    let modifiers = [];
    for (let i = 0; i < directive3.modifiers.length; i++) {
      let modifier = directive3.modifiers[i];
      if (BREW_ONLY_MODIFIERS.includes(modifier)) {
        if (modifier === "island") {
          let next = directive3.modifiers[i + 1];
          if (next && !BREW_ONLY_MODIFIERS.includes(next) && !["prevent", "stop", "window", "document", "outside", "once", "debounce", "throttle", "self", "camel", "dot", "passive", "capture"].includes(next)) {
            i++;
          }
        }
        continue;
      }
      modifiers.push(modifier);
    }
    if (directive3.value === "submit" && !modifiers.includes("prevent")) modifiers.push("prevent");
    let attribute = ["x-on:" + directive3.value, ...modifiers].join(".");
    let unbind = module_default.bind(el, {
      [attribute](event) {
        directive3.eventContext = event;
        directive3.wire = component.$wire;
        let execute = () => {
          let targetEl = directive3.value === "submit" ? event.submitter || el.querySelector('button[type="submit"], input[type="submit"]') : void 0;
          setNextActionOrigin(targetEl === void 0 ? { el, directive: directive3 } : { el, directive: directive3, targetEl });
          let options = event.detail?.brew;
          if (options?.interceptAction) setNextActionInterceptor(options.interceptAction);
          let expression = directive3.expression;
          if (options?.defaultParams !== void 0 && !expression.includes("(")) {
            let params = Array.isArray(options.defaultParams) ? options.defaultParams : [options.defaultParams];
            expression += "(" + params.map((param) => JSON.stringify(param)).join(", ") + ")";
          }
          evaluateActionExpression(el, expression, { scope: { $event: event } });
        };
        if (el.__brew_confirm) {
          el.__brew_confirm(execute, () => event.stopImmediatePropagation());
        } else {
          execute();
        }
      }
    });
    cleanup(unbind);
  });
}

// src/directives/model.js
function registerModelDirective() {
  directive2("model", ({ el, directive: directive3, component }) => {
    let { expression } = directive3;
    let modifiers = directive3.modifiers.filter((modifier) => modifier !== "renderless");
    if (!expression) return console.warn("Wire: [wire:model] is missing a value.", el);
    if (el.type === "file") {
      el.addEventListener("change", () => {
        let files = el.files;
        if (!files || files.length === 0) return;
        if (el.multiple) component.$wire.$uploadMultiple(expression, files);
        else component.$wire.$upload(expression, files[0]);
      });
      return;
    }
    if (!Object.hasOwn(component.canonical, baseProperty(expression))) {
      return console.warn(`Brew: [wire:model="${expression}"] property does not exist on component: [${component.name}]`, el);
    }
    let liveIndex = modifiers.indexOf("live");
    let isLive = liveIndex !== -1;
    let lazyOnly = modifiers.includes("lazy") && !isLive;
    let sendsNetwork = isLive || lazyOnly;
    let local = isLive ? modifiers.slice(0, liveIndex) : modifiers.filter((modifier) => modifier !== "lazy");
    let network = isLive ? modifiers.slice(liveIndex + 1) : lazyOnly ? ["change"] : [];
    if (!local.includes("deep") && !network.includes("deep") && !local.includes("self")) local.push("self");
    let onBlur = network.includes("blur");
    let onChange = network.includes("change");
    let onEnter = network.includes("enter");
    let hasTriggers = onBlur || onChange || onEnter;
    let debounced = network.includes("debounce");
    let throttled = network.includes("throttle");
    let update = () => {
      setNextActionOrigin({ el, directive: directive3 });
      if (isLive) setNextActionMetadata({ type: "model.live" });
      component.$wire.$commit();
    };
    let send = update;
    if (debounced || sendsNetwork && !hasTriggers && !throttled && isRealtimeInput(el)) {
      send = debounce2(update, duration(network, "debounce") ?? 150);
    }
    if (throttled) send = throttle3(update, duration(network, "throttle") ?? 150);
    let bindings = {};
    if (onBlur) bindings["@blur"] = () => queueMicrotask(() => {
      if (!deeplyEqual(component.canonical, component.ephemeral)) update();
    });
    if (onChange) bindings["@change"] = () => update();
    if (onEnter) bindings["@keydown.enter"] = () => update();
    bindings["x-model" + modifierTail(local)] = () => ({
      get: () => dataGet(component.$wire, expression),
      set: (value) => {
        dataSet(component.$wire, expression, value);
        if (sendsNetwork && !hasTriggers) send();
      }
    });
    module_default.bind(el, bindings);
  });
}
function baseProperty(expression) {
  return (expression.match(/^\[['"]?([^\]'"]+)['"]?\]/) || expression.match(/^([^.[]+)/))[1];
}
function isRealtimeInput(el) {
  return ["INPUT", "TEXTAREA"].includes(el.tagName) && !["checkbox", "radio"].includes(el.type);
}
function modifierTail(modifiers) {
  let kept = [];
  for (let i = 0; i < modifiers.length; i++) {
    let modifier = modifiers[i];
    if (modifier === "debounce" || modifier === "throttle") {
      if (duration(modifiers, modifier) !== void 0) i++;
      continue;
    }
    if (modifier !== "defer") kept.push(modifier);
  }
  return "." + kept.join(".");
}
function duration(modifiers, key) {
  let next = modifiers[modifiers.indexOf(key) + 1] || "";
  let value = parseInt(next);
  return /^\d+(ms)?$/.test(next) ? value : void 0;
}
function debounce2(callback, wait) {
  let timeout;
  return () => {
    clearTimeout(timeout);
    timeout = setTimeout(callback, wait);
  };
}
function throttle3(callback, limit) {
  let waiting = false;
  return () => {
    if (waiting) return;
    callback();
    waiting = true;
    setTimeout(() => waiting = false, limit);
  };
}

// src/directives/simple.js
function registerSimpleDirectives() {
  directive2("init", ({ el, directive: directive3 }) => {
    setNextActionOrigin({ el, directive: directive3 });
    evaluateActionExpression(el, directive3.expression || "$refresh");
  });
  directive2("ignore", ({ el, directive: directive3 }) => {
    if (directive3.modifiers.includes("self")) {
      el.__brew_ignore_self = true;
    } else if (directive3.modifiers.includes("children")) {
      el.__brew_ignore_children = true;
    } else {
      el.__brew_ignore = true;
    }
  });
  directive2("replace", ({ el, directive: directive3 }) => {
    if (directive3.modifiers.includes("self")) {
      el.__brew_replace_self = true;
    } else {
      el.__brew_replace = true;
    }
  });
}

// src/directives/loading.js
function registerLoadingDirective() {
  directive2("loading", ({ el, directive: directive3, component, cleanup }) => {
    let { targets, inverted } = getTargets(el);
    let [delay3, abortDelay] = applyDelay(directive3);
    let restoreLoadingState = () => toggleBooleanStateDirective(el, directive3, false);
    let activeLoadingCount = 0;
    let startLoading = () => {
      if (activeLoadingCount === 0) {
        if (directive3.modifiers.includes("class")) {
          let classes = directive3.expression.split(" ").filter(String);
          let classStates = classes.map((className) => [className, el.classList.contains(className)]);
          restoreLoadingState = () => classStates.forEach(([className, wasPresent]) => {
            el.classList.toggle(className, wasPresent);
          });
        } else if (directive3.modifiers.includes("attr")) {
          let attribute = directive3.expression;
          let value = el.getAttribute(attribute);
          restoreLoadingState = value === null ? () => el.removeAttribute(attribute) : () => el.setAttribute(attribute, value);
        }
        delay3(() => toggleBooleanStateDirective(el, directive3, true));
      }
      activeLoadingCount++;
    };
    let endLoading = () => {
      activeLoadingCount--;
      if (activeLoadingCount === 0) abortDelay(restoreLoadingState);
    };
    cleanup(whenTargetsArePartOfRequest(component, targets, inverted, [startLoading, endLoading]));
  });
}
function applyDelay(directive3) {
  if (!directive3.modifiers.includes("delay") || directive3.modifiers.includes("none")) {
    return [(i) => i(), (i) => i()];
  }
  let duration2 = 200;
  let delayModifiers = {
    shortest: 50,
    shorter: 100,
    short: 150,
    default: 200,
    long: 300,
    longer: 500,
    longest: 1e3
  };
  Object.keys(delayModifiers).some((key) => {
    if (directive3.modifiers.includes(key)) {
      duration2 = delayModifiers[key];
      return true;
    }
  });
  let timeout;
  let started2 = false;
  return [
    (callback) => {
      timeout = setTimeout(() => {
        callback();
        started2 = true;
      }, duration2);
    },
    async (callback) => {
      if (started2) {
        await callback();
        started2 = false;
      } else {
        clearTimeout(timeout);
      }
    }
  ];
}
function whenTargetsArePartOfRequest(component, targets, inverted, [startLoading, endLoading]) {
  return interceptMessage(({ message, onSend, onSuccess, onFinish }) => {
    if (component !== message.component) return;
    if (targets.length === 0 && ![...message.actions].some((action) => action.metadata.island === void 0)) return;
    let matches3 = true;
    let cleared = false;
    let finishLoading = () => {
      if (!matches3 || cleared) return;
      endLoading();
      cleared = true;
    };
    onSend(({ payload }) => {
      if (targets.length > 0 && containsTargets(payload, targets) === inverted) {
        matches3 = false;
      }
      if (!matches3) return;
      startLoading();
    });
    onSuccess(({ onEffect }) => onEffect(() => finishLoading()));
    onFinish(() => finishLoading());
  });
}
function containsTargets(payload, targets) {
  let { updates, calls } = payload;
  return targets.some(({ target, params }) => {
    if (params) {
      return calls.some(({ method, params: methodParams }) => target === method && params === quickHash(JSON.stringify(methodParams)));
    }
    let hasMatchingUpdate = Object.keys(updates).some((property) => {
      if (property.includes(".")) {
        if (property.split(".")[0] === target) return true;
      }
      return property === target;
    });
    if (hasMatchingUpdate) return true;
    return calls.map((i) => i.method).includes(target);
  });
}
function getTargets(el) {
  let directives2 = getDirectives(el);
  let targets = [];
  let inverted = false;
  if (directives2.has("target")) {
    let directive3 = directives2.get("target");
    if (directive3.modifiers.includes("except")) inverted = true;
    directive3.methods.forEach(({ method, params }) => {
      targets.push({
        target: method,
        params: params && params.length > 0 ? quickHash(JSON.stringify(params)) : void 0
      });
    });
  } else {
    let nonAction = ["init", "dirty", "offline", "navigate", "target", "loading", "poll", "ignore", "key", "id", "cloak", "show", "text", "bind", "ref", "transition", "confirm"];
    directives2.all().filter((i) => !nonAction.includes(i.value)).map((i) => i.expression.split("(")[0]).forEach((target) => targets.push({ target }));
  }
  return { targets, inverted };
}
function quickHash(subject) {
  return btoa(encodeURIComponent(subject));
}

// src/directives/offline.js
var offlineHandlers = /* @__PURE__ */ new Set();
var onlineHandlers = /* @__PURE__ */ new Set();
var isOffline = !navigator.onLine;
window.addEventListener("offline", () => {
  isOffline = true;
  offlineHandlers.forEach((i) => i());
});
window.addEventListener("online", () => {
  isOffline = false;
  onlineHandlers.forEach((i) => i());
});
function brewIsOffline() {
  return isOffline;
}
function registerOfflineDirective() {
  directive2("offline", ({ el, directive: directive3, cleanup }) => {
    let setOffline = () => toggleBooleanStateDirective(el, directive3, true);
    let setOnline = () => toggleBooleanStateDirective(el, directive3, false);
    offlineHandlers.add(setOffline);
    onlineHandlers.add(setOnline);
    if (isOffline) setOffline();
    cleanup(() => {
      offlineHandlers.delete(setOffline);
      onlineHandlers.delete(setOnline);
    });
  });
}

// src/directives/poll.js
function registerPollDirective() {
  directive2("poll", ({ el, directive: directive3 }) => {
    let interval = extractDurationFrom(directive3.modifiers, 2e3);
    let { start: start3, pauseWhile, throttleWhile, stopWhen } = poll(() => {
      triggerComponentRequest(el, directive3);
    }, interval);
    start3();
    throttleWhile(() => theTabIsInTheBackground() && theDirectiveIsMissingKeepAlive(directive3));
    pauseWhile(() => theDirectiveHasVisible(directive3) && theElementIsNotInTheViewport(el));
    pauseWhile(() => theDirectiveIsOffTheElement(el));
    pauseWhile(() => brewIsOffline());
    pauseWhile(() => sessionIsExpired());
    stopWhen(() => theElementIsDisconnected(el));
  });
}
function triggerComponentRequest(el, directive3) {
  setNextActionOrigin({ el, directive: directive3, targetEl: null });
  setNextActionMetadata({ type: "poll" });
  evaluateActionExpression(el, directive3.expression || "$refresh");
}
function poll(callback, interval = 2e3) {
  let pauseConditions = [];
  let throttleConditions = [];
  let stopConditions = [];
  return {
    start() {
      let clear = synchronizedInterval(interval, () => {
        if (stopConditions.some((i) => i())) return clear();
        if (pauseConditions.some((i) => i())) return;
        if (throttleConditions.some((i) => i()) && shouldThrottlePoll()) return;
        callback();
      });
    },
    pauseWhile(condition) {
      pauseConditions.push(condition);
    },
    throttleWhile(condition) {
      throttleConditions.push(condition);
    },
    stopWhen(condition) {
      stopConditions.push(condition);
    }
  };
}
var clocks = {};
function synchronizedInterval(ms, callback) {
  if (!clocks[ms]) {
    clocks[ms] = {
      timer: setInterval(() => clocks[ms].callbacks.forEach((i) => i()), ms),
      callbacks: /* @__PURE__ */ new Set()
    };
  }
  clocks[ms].callbacks.add(callback);
  return () => {
    clocks[ms].callbacks.delete(callback);
    if (clocks[ms].callbacks.size === 0) {
      clearInterval(clocks[ms].timer);
      delete clocks[ms];
    }
  };
}
var inBackground = false;
document.addEventListener("visibilitychange", () => {
  inBackground = document.hidden;
}, false);
function theTabIsInTheBackground() {
  return inBackground;
}
function theDirectiveIsOffTheElement(el) {
  return !getDirectives(el).has("poll");
}
function theDirectiveIsMissingKeepAlive(directive3) {
  return !directive3.modifiers.includes("keep-alive");
}
function theDirectiveHasVisible(directive3) {
  return directive3.modifiers.includes("visible");
}
function theElementIsNotInTheViewport(el) {
  let bounding = el.getBoundingClientRect();
  return !(bounding.top < (window.innerHeight || document.documentElement.clientHeight) && bounding.left < (window.innerWidth || document.documentElement.clientWidth) && bounding.bottom > 0 && bounding.right > 0);
}
function theElementIsDisconnected(el) {
  return el.isConnected === false;
}
function shouldThrottlePoll(random = Math.random) {
  return random() < 0.95;
}
function extractDurationFrom(modifiers, defaultDuration) {
  let ms = modifiers.find((mod) => mod.match(/([0-9]+)ms/));
  let s = modifiers.find((mod) => mod.match(/([0-9]+)s/));
  let m = modifiers.find((mod) => mod.match(/([0-9]+)m/));
  if (ms) return Number(ms.replace("ms", ""));
  if (s) return Number(s.replace("s", "")) * 1e3;
  if (m) return Number(m.replace("m", "")) * 60 * 1e3;
  return defaultDuration;
}

// src/directives/confirm.js
function registerConfirmDirective() {
  directive2("confirm", ({ el, directive: directive3 }) => {
    let message = (directive3.expression || "").replaceAll("\\n", "\n");
    let shouldPrompt = directive3.modifiers.includes("prompt");
    if (message === "") message = "Are you sure?";
    el.__brew_confirm = (action, instead) => {
      if (shouldPrompt) {
        let [question, expected] = message.split("|");
        if (!expected) {
          console.warn("Wire: Must provide expectation with wire:confirm.prompt");
        } else {
          let input = prompt(question);
          if (input === expected) action();
          else instead();
        }
      } else if (confirm(message)) {
        action();
      } else {
        instead();
      }
    };
  });
}

// src/directives/cloak.js
function registerCloakDirective() {
  module_default.interceptInit((el) => {
    if (el.hasAttribute("wire:cloak")) {
      module_default.mutateDom(() => el.removeAttribute("wire:cloak"));
    }
  });
}

// src/directives/showTextBind.js
function registerShowDirective() {
  module_default.interceptInit((el) => {
    for (let i = 0; i < el.attributes.length; i++) {
      if (!el.attributes[i].name.startsWith("wire:show")) continue;
      let { name, value } = el.attributes[i];
      let modifierString = name.split("wire:show")[1];
      let expression = value.trim();
      module_default.bind(el, {
        ["x-show" + modifierString]() {
          return evaluateReactiveExpression(el, expression);
        }
      });
    }
  });
}
function registerTextDirective() {
  module_default.interceptInit((el) => {
    for (let i = 0; i < el.attributes.length; i++) {
      if (!el.attributes[i].name.startsWith("wire:text")) continue;
      let { name, value } = el.attributes[i];
      let modifierString = name.split("wire:text")[1];
      let expression = value.trim();
      module_default.bind(el, {
        ["x-text" + modifierString]() {
          return evaluateReactiveExpression(el, expression);
        }
      });
    }
  });
}
function registerBindDirective() {
  module_default.interceptInit((el) => {
    for (let i = 0; i < el.attributes.length; i++) {
      if (!el.attributes[i].name.startsWith("wire:bind:")) continue;
      let { name, value } = el.attributes[i];
      let remainder = name.split("wire:bind")[1];
      let expression = value.trim();
      module_default.bind(el, {
        ["x-bind" + remainder]() {
          return evaluateReactiveExpression(el, expression);
        }
      });
    }
  });
}

// src/features/dataLoading.js
function registerDataLoading() {
  interceptMessage(({ message, onSend, onFinish }) => {
    let undos = [];
    onSend(() => {
      message.actions.forEach((action) => {
        let origin = action.origin;
        if (!origin) return;
        let el = Object.prototype.hasOwnProperty.call(origin, "targetEl") ? origin.targetEl : origin.el;
        if (!el) return;
        el.setAttribute("data-loading", "true");
        undos.push(() => el.removeAttribute("data-loading"));
      });
    });
    onFinish(() => undos.forEach((undo) => undo()));
  });
}

// src/features/disableForms.js
var cleanups = new WeakBag();
function registerDisableFormsDuringRequest() {
  on2("directive.init", ({ el, directive: directive3, component }) => setTimeout(() => {
    if (directive3.value !== "submit") return;
    el.addEventListener("submit", () => {
      let cleanup = disableForm(el);
      cleanups.add(component, cleanup);
    });
  }));
  interceptMessage(({ message, onFinish }) => {
    onFinish(() => {
      cleanups.each(message.component, (cleanup) => cleanup());
      cleanups.remove(message.component);
    });
  });
}
function disableForm(formEl) {
  let undos = [];
  module_default.walk(formEl, (el, skip) => {
    if (el.hasAttribute("wire:ignore")) return skip();
    if (shouldMarkDisabled(el)) undos.push(markDisabled(el));
    else if (shouldMarkReadOnly(el)) undos.push(markReadOnly(el));
  });
  return () => {
    while (undos.length > 0) undos.shift()();
  };
}
function shouldMarkDisabled(el) {
  let tag = el.tagName.toLowerCase();
  let inputTypesThatDontSupportReadonly = [
    "hidden",
    "range",
    "color",
    "checkbox",
    "radio",
    "file",
    "submit",
    "image",
    "reset",
    "button"
  ];
  if (tag === "select") return true;
  if (tag === "button" && el.type === "submit") return true;
  if (tag === "input" && inputTypesThatDontSupportReadonly.includes(el.type)) return true;
  return false;
}
function shouldMarkReadOnly(el) {
  return ["input", "textarea"].includes(el.tagName.toLowerCase());
}
function markDisabled(el) {
  let undo = el.disabled ? () => {
  } : () => {
    el.disabled = false;
  };
  el.disabled = true;
  return undo;
}
function markReadOnly(el) {
  let undo = el.readOnly ? () => {
  } : () => {
    el.readOnly = false;
  };
  el.readOnly = true;
  return undo;
}

// src/features/dispatches.js
function registerDispatches() {
  interceptMessage(({ message, onSuccess }) => {
    onSuccess(({ payload, onMorphed }) => {
      onMorphed(() => {
        dispatchEvents(message.component, payload.effects?.dispatches || []);
      });
    });
  });
  on2("component.init", ({ component }) => {
    let dispatches = component.effects?.dispatches || [];
    if (dispatches.length === 0) return;
    queueMicrotask(() => {
      queueMicrotask(() => {
        queueMicrotask(() => dispatchEvents(component, dispatches));
      });
    });
  });
}
function dispatchEvents(component, dispatches) {
  dispatches.forEach(({ name, params = {}, self = false, component: componentName, ref, el }) => {
    if (self) dispatchSelf(component, name, params);
    else if (componentName) dispatchTo(componentName, name, params);
    else if (ref) dispatchRef(component, ref, name, params);
    else if (el) dispatchEl(component, el, name, params);
    else dispatch3(component, name, params);
  });
}

// src/features/listeners.js
function registerListeners() {
  on2("effect", ({ component, effects, cleanup }) => {
    let names = effects.listeners || [];
    names.forEach((name) => {
      let handler4 = (event) => {
        if (!event.__brew) return;
        fireAction(component, "__dispatch", [name, event.detail || {}]);
      };
      window.addEventListener(name, handler4);
      cleanup(() => window.removeEventListener(name, handler4));
    });
  });
}

// src/features/jsEvaluation.js
function registerJsEvaluation() {
  interceptMessage(({ message, onSuccess }) => {
    onSuccess(({ payload, onMorphed }) => {
      onMorphed(() => {
        let expressions = payload.effects?.xjs || [];
        expressions.forEach((expression) => {
          module_default.evaluate(message.component.el, expression);
        });
      });
    });
  });
}

// src/features/redirects.js
function registerRedirects() {
  interceptMessage(({ message, onSuccess }) => {
    onSuccess(({ payload }) => {
      let redirect = payload.effects?.redirect;
      if (!redirect) return;
      browser.visit(redirect.url || redirect.default || "/");
    });
  });
}

// src/features/streaming.js
function registerStreaming() {
  interceptMessage(({ message, onSuccess }) => {
    onSuccess(({ payload, onMorphed }) => {
      onMorphed(() => {
        let streams = payload.effects?.streams || [];
        streams.forEach(({ content, to, replace }) => {
          let targets = to ? message.component.el.querySelectorAll(`[wire\\:stream="${to}"], [wire\\:stream\\.replace="${to}"]`) : message.component.el.querySelectorAll("[wire\\:stream], [wire\\:stream\\.replace]");
          targets.forEach((el) => {
            let shouldReplace = replace || el.hasAttribute("wire:stream.replace");
            if (shouldReplace) el.innerHTML = content;
            else el.insertAdjacentHTML("beforeend", content);
          });
        });
      });
    });
  });
}

// src/features/downloads.js
function downloadField(download, key, fallback) {
  return Object.prototype.hasOwnProperty.call(download, key) ? download[key] : fallback;
}
function registerDownloads() {
  interceptMessage(({ onSuccess }) => {
    onSuccess(({ payload }) => {
      let download = payload.effects && payload.effects.download;
      if (!download) return;
      let content = downloadField(download, "content", "");
      let name = downloadField(download, "name", "download");
      let contentType = downloadField(download, "contentType", "application/octet-stream");
      let binary = atob(content);
      let bytes = new Uint8Array(binary.length);
      for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
      let blob = new Blob([bytes], { type: contentType });
      let url = URL.createObjectURL(blob);
      let anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = name;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    });
  });
}

// src/features/preserveScroll.js
function registerPreserveScroll() {
  interceptAction(({ action }) => {
    if (action.origin?.directive?.modifiers.includes("preserve-scroll")) {
      action.metadata.preserveScroll = true;
    }
  });
  interceptMessage(({ message, onSuccess }) => {
    let preserve = [...message.actions].some((action) => action.metadata.preserveScroll);
    if (!preserve) return;
    let x = window.scrollX;
    let y = window.scrollY;
    onSuccess(({ onMorphed }) => {
      onMorphed(() => window.scrollTo(x, y));
    });
  });
}

// src/features/scripts.js
function registerScripts() {
  on2("effect", ({ component, effects }) => {
    let js = effects.js || {};
    Object.entries(js).forEach(([name, body]) => {
      if (!component.$js) component.$js = {};
      component.$js[name] = (...params) => module_default.evaluate(component.el, body, {
        scope: { $wire: component.$wire, params }
      });
    });
    let scripts = effects.scripts || [];
    if (scripts.length === 0) return;
    if (component.__wireScriptsRan) return;
    component.__wireScriptsRan = true;
    scripts.forEach((source) => {
      module_default.evaluate(component.el, source, { scope: { $wire: component.$wire, $js: component.$js } });
    });
  });
}

// src/features/assets.js
var injected = /* @__PURE__ */ new Set();
function assetKey(html) {
  return html.trim().slice(0, 40).replace(/[^a-zA-Z0-9_-]/g, "");
}
function registerAssets() {
  on2("effect", ({ effects }) => {
    let assets = effects.assets || [];
    assets.forEach((html) => {
      if (typeof html !== "string" || !html.trim()) return;
      let key = assetKey(html);
      if (injected.has(key)) return;
      injected.add(key);
      let wrapper = document.createElement("div");
      wrapper.innerHTML = html;
      [...wrapper.childNodes].forEach((node) => {
        if (node.nodeType === 1) document.head.appendChild(node);
      });
    });
  });
}

// src/features/echo.js
function registerEcho() {
  on2("effect", ({ component, effects, cleanup }) => {
    let echo = effects.echo || {};
    let Echo = window.Echo;
    if (!Echo || Object.keys(echo).length === 0) return;
    Object.entries(echo).forEach(([spec, method]) => {
      let [prefix2, rest] = spec.split(/:(.+)/);
      let [channel, event] = (rest || "").split(",");
      if (!channel || !event) return;
      let channelRef;
      if (prefix2 === "echo-private") channelRef = Echo.private(channel);
      else if (prefix2 === "echo-presence") channelRef = Echo.join(channel);
      else channelRef = Echo.channel(channel);
      let handler4 = (payload) => fireAction(component, method, [payload]);
      channelRef.listen(event, handler4);
      cleanup(() => {
        if (typeof channelRef.stopListening !== "function") return;
        channelRef.stopListening(event, handler4);
      });
    });
  });
}

// src/features/teleport.js
function applyTeleports(root) {
  root.querySelectorAll("template[wire\\:teleport]").forEach((template) => {
    let selector = template.getAttribute("wire:teleport");
    if (!selector) return;
    let target = document.querySelector(selector);
    if (!target) return;
    target.append(...template.content.childNodes);
    template.remove();
  });
}
function registerTeleport() {
  on2("effect", ({ component }) => applyTeleports(component.el));
}

// src/features/islands.js
function registerIslands() {
  interceptAction(({ action }) => {
    let modifiers = action.origin?.directive?.modifiers || [];
    let islandIdx = modifiers.indexOf("island");
    if (islandIdx === -1) return;
    let name = modifiers[islandIdx + 1] || "default";
    let mode = modifiers.includes("append") ? "append" : modifiers.includes("prepend") ? "prepend" : "morph";
    action.metadata.island = { name, mode };
  });
  wireProperty("$island", (component) => (name = "default", mode = "morph") => {
    component.__brewIsland = { name, mode };
    return fireAction(component, "$refresh");
  });
  on2("effect", ({ component, effects }) => {
    let islands = effects.islands || {};
    Object.entries(islands).forEach(([name, payload]) => {
      let el = component.el.querySelector(`[wire\\:island="${name}"]`);
      if (!el || !payload?.html) return;
      let mode = component.__brewIsland?.mode || "morph";
      if (mode === "append") el.insertAdjacentHTML("beforeend", payload.html);
      else if (mode === "prepend") el.insertAdjacentHTML("afterbegin", payload.html);
      else el.innerHTML = payload.html;
    });
  });
}

// src/features/lazy.js
function scheduleLazyLoad(component, defer) {
  let load = () => fireAction(component, "$refresh");
  if (defer) {
    queueMicrotask(load);
    return;
  }
  if (typeof IntersectionObserver === "undefined") {
    load();
    return;
  }
  let observer2 = new IntersectionObserver((entries) => {
    if (!entries.some((entry) => entry.isIntersecting)) return;
    observer2.disconnect();
    load();
  });
  observer2.observe(component.el);
  component.addCleanup(() => observer2.disconnect());
}
function registerLazy() {
  on2("component.init", ({ component }) => {
    let lazy = component.effects?.lazy || component.snapshot.memo?.lazyLoaded === false;
    let defer = component.effects?.defer || component.snapshot.memo?.defer;
    if (!lazy && !defer) return;
    scheduleLazyLoad(component, !!defer);
  });
}

// src/features/navigate.js
var progressEl = null;
var prefetchCache = /* @__PURE__ */ new Map();
function config() {
  return window.wireScriptConfig && window.wireScriptConfig.navigate || window.brewScriptConfig && window.brewScriptConfig.navigate || {};
}
function showProgress() {
  if (config().show_progress_bar === false) return;
  if (!progressEl) {
    progressEl = document.createElement("div");
    progressEl.setAttribute("data-wire-progress", "");
    progressEl.setAttribute("data-brew-progress", "");
    progressEl.style.cssText = `position:fixed;top:0;left:0;height:3px;width:0;background:${config().progress_bar_color || "#FF9A00"};z-index:9999;transition:width .2s`;
    document.body.appendChild(progressEl);
  }
  requestAnimationFrame(() => {
    if (progressEl) progressEl.style.width = "70%";
  });
}
function hideProgress() {
  if (!progressEl) return;
  progressEl.style.width = "100%";
  setTimeout(() => {
    if (progressEl) progressEl.remove();
    progressEl = null;
  }, 200);
}
function fire(name, detail = {}) {
  document.dispatchEvent(new CustomEvent("wire:" + name, { detail, bubbles: true }));
  document.dispatchEvent(new CustomEvent("brew:" + name, { detail, bubbles: true }));
  document.dispatchEvent(new CustomEvent("livewire:" + name, { detail, bubbles: true }));
}
function persistSnapshot() {
  document.querySelectorAll("[wire\\:persist]").forEach((el) => {
    el._brewPersistKey = el.getAttribute("wire:persist") || "default";
  });
}
function restorePersist(doc) {
  document.querySelectorAll("[wire\\:persist]").forEach((el) => {
    let next = doc.querySelector(`[wire\\:persist="${el._brewPersistKey}"]`);
    if (next) next.replaceWith(el);
  });
}
function applyScripts(doc) {
  doc.querySelectorAll("script").forEach((script) => {
    let skipOnce = script.hasAttribute("data-navigate-once") && script.src !== "" && document.querySelector(`script[src="${script.src}"][data-navigate-once]`);
    let skipTrack = script.hasAttribute("data-navigate-track") && document.querySelector(`script[data-navigate-track="${script.getAttribute("data-navigate-track")}"]`);
    if (skipOnce || skipTrack) return;
    let clone3 = document.createElement("script");
    [...script.attributes].forEach((attr) => clone3.setAttribute(attr.name, attr.value));
    clone3.textContent = script.textContent;
    document.body.appendChild(clone3);
  });
}
async function fetchPage(url, { prefetch = false } = {}) {
  if (prefetch && prefetchCache.has(url)) return prefetchCache.get(url);
  let promise = Promise.resolve().then(() => fetch(url, { headers: { "X-Brew-Navigate": "1", Accept: "text/html" } })).then((r) => r.text()).catch(() => "<html><head></head><body></body></html>");
  if (prefetch) prefetchCache.set(url, promise);
  return promise;
}
async function navigateTo(url, { replace = false } = {}) {
  fire("navigate", { url });
  fire("navigating", { url });
  showProgress();
  persistSnapshot();
  let html = await fetchPage(url);
  let doc = new DOMParser().parseFromString(html, "text/html");
  let swap = () => {
    restorePersist(doc);
    document.title = doc.title;
    document.body.replaceWith(doc.body);
    applyScripts(doc);
    history[replace ? "replaceState" : "pushState"]({}, "", url);
    hideProgress();
    fire("navigated", { url });
    try {
      window.Wire.start();
    } catch (_) {
    }
  };
  if (config().view_transitions && document.startViewTransition) document.startViewTransition(swap);
  else swap();
}
function registerNavigate() {
  document.addEventListener("click", (e) => {
    let link = e.target.closest("[wire\\:navigate]");
    if (!link || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button) return;
    e.preventDefault();
    let href = link.getAttribute("href");
    if (!href || href.startsWith("#") || href.startsWith("mailto:")) return;
    navigateTo(href);
  }, true);
  document.addEventListener("mouseover", (e) => {
    let link = e.target.closest("[wire\\:navigate\\.hover]");
    let href = link && link.getAttribute("href");
    if (href) fetchPage(href, { prefetch: true });
  }, { capture: true });
  window.addEventListener("popstate", () => navigateTo(location.href, { replace: true }));
  on2("effect", ({ effects }) => {
    if (effects.disableBackButtonCache) {
      window.addEventListener("unload", () => {
      });
      history.scrollRestoration = "manual";
    }
    if (effects.url) {
      let params = new URLSearchParams(location.search);
      Object.entries(effects.url).forEach(([key, meta]) => {
        let value = meta && Object.prototype.hasOwnProperty.call(meta, "value") ? meta.value : meta;
        if (value == null || value === "") params.delete(key);
        else params.set(key, String(value));
      });
      let qs = params.toString();
      let next = location.pathname + (qs === "" ? "" : "?" + qs);
      if (Object.values(effects.url).some((m) => m && m.history === true)) history.pushState({}, "", next);
      else history.replaceState({}, "", next);
    }
    if (effects.title) document.title = effects.title;
  });
  window.Wire = window.Wire || {};
  window.Wire.navigate = navigateTo;
}

// src/features/current.js
function resolveHref(el, dir) {
  let href = el.getAttribute("href");
  if (href) return href;
  if (dir.expression) return dir.expression;
  return "";
}
function pathOf(href) {
  if (!href.includes("://")) return href;
  let afterHost = href.indexOf("/", href.indexOf("://") + 3);
  return afterHost < 0 ? "/" : href.slice(afterHost);
}
function registerCurrentDirective() {
  directive2("current", ({ el, directive: dir }) => {
    let href = resolveHref(el, dir);
    let path = location.pathname;
    let target = pathOf(href);
    let exact = dir.modifiers.includes("exact") || dir.modifiers.includes("strict");
    let matches3 = path === target;
    if (!exact && !matches3 && target.length > 1) matches3 = path.startsWith(target);
    el.toggleAttribute("data-current", matches3);
  });
}

// src/directives/navigate.js
function registerNavigateDirective() {
  directive2("navigate", () => {
  });
}

// src/features/uploads.js
var uploadDeps = {
  fireAction
};
function uploadUri() {
  let base = window.wireScriptConfig && window.wireScriptConfig.uri || window.brewScriptConfig && window.brewScriptConfig.uri || "/wire/update";
  return base.replace(/\/update$/, "/upload");
}
function fireUpload(name, detail) {
  document.dispatchEvent(new CustomEvent("wire-upload-" + name, { detail, bubbles: true }));
  document.dispatchEvent(new CustomEvent("brew-upload-" + name, { detail, bubbles: true }));
  document.dispatchEvent(new CustomEvent("livewire-upload-" + name, { detail, bubbles: true }));
}
var controllers = /* @__PURE__ */ new Map();
async function uploadFiles(component, property, files, { multiple = false } = {}) {
  let list = Array.from(files || []);
  if (list.length === 0) return;
  let key = component.id + ":" + property;
  let ctrl = new AbortController();
  controllers.set(key, ctrl);
  fireUpload("start", { property, component });
  try {
    let info = { name: list[0].name, size: list[0].size, type: list[0].type };
    let signed = await uploadDeps.fireAction(component, "_startUpload", [property, info, multiple]);
    let uploadUrl = signed && signed.uploadUrl || uploadUri();
    let isS3 = !!(signed && signed.isS3);
    let paths = [];
    for (let file of list) {
      let body = file.arrayBuffer ? await file.arrayBuffer() : await new Response(file).arrayBuffer();
      let doFetch = uploadDeps.fetch || fetch;
      let response;
      if (isS3) {
        let headers = Object.assign(
          { "Content-Type": file.type || "application/octet-stream" },
          signed && signed.headers || {}
        );
        response = await doFetch(uploadUrl, { method: "PUT", headers, body, signal: ctrl.signal });
        if (!response.ok) throw new Error("upload failed");
        paths.push(signed && signed.path || file.name);
      } else {
        let sep = uploadUrl.includes("?") ? "&" : "?";
        let url = uploadUrl + sep + `name=${encodeURIComponent(property)}&filename=${encodeURIComponent(file.name)}&size=${file.size}&multiple=${multiple}`;
        response = await doFetch(url, {
          method: "POST",
          headers: { "Content-Type": file.type || "application/octet-stream", "X-Brew": "1" },
          body,
          signal: ctrl.signal
        });
        if (!response.ok) throw new Error("upload failed");
        let json = await response.json();
        paths.push(...json.paths || []);
      }
      fireUpload("progress", { property, progress: 100, component });
    }
    await uploadDeps.fireAction(component, "_finishUpload", [property, paths, multiple]);
    fireUpload("finish", { property, component });
  } catch (error2) {
    await uploadDeps.fireAction(component, "_uploadErrored", [property, String(error2), multiple]).catch(() => {
    });
    fireUpload("error", { property, error: error2, component });
  } finally {
    controllers.delete(key);
  }
}
function cancelUpload(component, property) {
  let ctrl = controllers.get(component.id + ":" + property);
  if (ctrl) ctrl.abort();
  fireUpload("cancel", { property, component });
}
function registerUploads() {
  wireProperty("$upload", (component) => (property, file, options) => uploadFiles(component, property, file ? [file] : [], { multiple: false, ...options || {} }));
  wireProperty("$uploadMultiple", (component) => (property, files, options) => uploadFiles(component, property, files || [], { multiple: true, ...options || {} }));
  wireProperty("$cancelUpload", (component) => (property) => cancelUpload(component, property));
  wireProperty("$removeUpload", (component) => (property, filename) => uploadDeps.fireAction(component, "_removeUpload", [property, filename]));
}

// src/directives/intersect.js
function registerIntersectDirective() {
  let bind3 = (isLeave) => ({ el, directive: dir, component, cleanup }) => {
    let once2 = dir.modifiers.includes("once");
    let threshold = dir.modifiers.includes("full") ? 1 : dir.modifiers.includes("half") ? 0.5 : 0;
    let marginMod = dir.modifiers.find((m) => m.startsWith("margin"));
    let rootMargin = marginMod ? marginMod.replace("margin-", "").replace(/-/g, " ") : "0px";
    let expression = dir.expression || "$refresh";
    let leave = isLeave || dir.rawName.includes(":leave");
    let fired = false;
    let observer2 = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        let hit = leave ? !entry.isIntersecting : entry.isIntersecting;
        if (!hit) return;
        if (once2 && fired) return;
        fired = true;
        fireAction(component, expression);
        if (once2) observer2.disconnect();
      });
    }, { threshold, rootMargin });
    observer2.observe(el);
    cleanup(() => observer2.disconnect());
  };
  directive2("intersect", bind3(false));
  directive2("intersect:enter", bind3(false));
  directive2("intersect:leave", bind3(true));
}

// src/directives/sort.js
function itemOf(el, target) {
  let node = target;
  while (node && node !== el) {
    if (node.hasAttribute && node.hasAttribute("wire:sort:item")) return node;
    node = node.parentElement;
  }
  return null;
}
function registerSortDirective() {
  directive2("sort", ({ el, directive: dir, component, cleanup }) => {
    let method = dir.expression || "sort";
    let group = el.getAttribute("wire:sort:group") || "default";
    let dragging = null;
    let onDragStart = (e) => {
      let item = itemOf(el, e.target);
      if (!item) return;
      let ignored = false;
      let node = e.target;
      while (node && node !== item) {
        if (node.hasAttribute && node.hasAttribute("wire:sort:ignore")) ignored = true;
        node = node.parentElement;
      }
      if (ignored) return;
      let handle = null;
      for (let child of item.children) {
        if (child.hasAttribute("wire:sort:handle")) handle = child;
      }
      if (handle && e.target !== handle && !handle.contains(e.target)) return;
      dragging = item;
      if (e.dataTransfer) {
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", item.getAttribute("wire:sort:item") || "");
      }
      item.setAttribute("data-sorting", "");
    };
    let onDragOver = (e) => {
      if (!dragging) return;
      e.preventDefault();
      let over = itemOf(el, e.target);
      if (!over || over === dragging) return;
      el.insertBefore(dragging, over);
    };
    let onDrop = (e) => {
      e.preventDefault();
      if (!dragging) return;
      dragging.removeAttribute("data-sorting");
      let items = [...el.children].filter((node) => node.hasAttribute("wire:sort:item")).map((node) => node.getAttribute("wire:sort:item"));
      fireAction(component, method, [items, group]);
      dragging = null;
    };
    let onDragEnd = () => {
      if (dragging) dragging.removeAttribute("data-sorting");
      dragging = null;
    };
    for (let item of el.children) {
      if (item.hasAttribute("wire:sort:item")) item.setAttribute("draggable", "true");
    }
    el.addEventListener("dragstart", onDragStart);
    el.addEventListener("dragover", onDragOver);
    el.addEventListener("drop", onDrop);
    el.addEventListener("dragend", onDragEnd);
    cleanup(() => {
      el.removeEventListener("dragstart", onDragStart);
      el.removeEventListener("dragover", onDragOver);
      el.removeEventListener("drop", onDrop);
      el.removeEventListener("dragend", onDragEnd);
    });
  });
}

// src/index.js
var Brew = {
  directive: directive2,
  interceptAction: (callback) => interceptAction(callback),
  interceptMessage: (callback) => interceptMessage(callback),
  interceptRequest: (callback) => interceptRequest(callback),
  fireAction: (component, method, params = [], metadata = {}) => fireAction(component.__instance || component, method, params, metadata),
  start: start2,
  first,
  find,
  getByName,
  all,
  hook: on2,
  trigger: trigger2,
  triggerAsync,
  dispatch: dispatchGlobal,
  dispatchTo,
  on: on3,
  navigate: navigateTo
};
var warnAboutMultipleInstancesOf = (entity) => console.warn(`Detected multiple instances of ${entity} running`);
if (window.Wire || window.Brew) warnAboutMultipleInstancesOf("Wire");
if (window.Alpine) warnAboutMultipleInstancesOf("Alpine");
registerWireMagic();
registerLegacyHooks();
registerMorphEffect();
registerModelDirective();
registerSimpleDirectives();
registerLoadingDirective();
registerDirtyDirective();
registerOfflineDirective();
registerPollDirective();
registerConfirmDirective();
registerCloakDirective();
registerShowDirective();
registerTextDirective();
registerBindDirective();
registerTransitionDirective();
registerDataLoading();
registerDisableFormsDuringRequest();
registerDispatches();
registerListeners();
registerJsEvaluation();
registerRedirects();
registerStreaming();
registerDownloads();
registerPreserveScroll();
registerScripts();
registerAssets();
registerEcho();
registerTeleport();
registerIslands();
registerLazy();
registerNavigate();
registerNavigateDirective();
registerCurrentDirective();
registerUploads();
registerIntersectDirective();
registerSortDirective();
registerWildcardDirective();
queueMicrotask(coordinateNetworkInteractions);
window.Wire = Brew;
window.Brew = Brew;
window.Alpine = module_default;
if (window.wireScriptConfig === void 0 && window.brewScriptConfig === void 0) {
  module_default.__fromWire = true;
  document.addEventListener("DOMContentLoaded", () => {
    if (window.Alpine.__fromWire === void 0) warnAboutMultipleInstancesOf("Alpine");
    Brew.start();
  });
}
export {
  module_default as Alpine,
  Brew
};
