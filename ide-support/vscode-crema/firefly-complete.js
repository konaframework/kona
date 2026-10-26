/**
 * Completions from the generated Firefly catalog.
 * The caret completes a type name, or a method after that type name and a dot.
 */
function completions(text, offset, catalog) {
  const types = catalog && Array.isArray(catalog.types) ? catalog.types : [];
  const head = text.slice(0, Math.max(0, Math.min(offset, text.length)));
  const prefix = trailingIdent(head);
  const before = head.slice(0, head.length - prefix.length);
  const receiver = receiverName(before, types);
  if (receiver) {
    const type = types.find((item) => item.name === receiver);
    if (!type || !Array.isArray(type.methods)) return [];
    const needle = prefix.toLowerCase();
    return type.methods
      .filter((method) => needle.length === 0 || method.toLowerCase().startsWith(needle))
      .map((method) => ({ label: method, detail: type.name + '.' + method + '()', kind: 'method' }));
  }
  if (endsWithDot(before) || prefix.length === 0) return [];
  const needle = prefix.toLowerCase();
  return types
    .filter((type) => String(type.name || '').toLowerCase().startsWith(needle))
    .map((type) => ({
      label: type.name,
      detail: (type.package || '') + ' · ' + (type.kind || ''),
      kind: 'class',
    }));
}

function receiverName(before, types) {
  const trimmed = trimTail(before);
  if (!trimmed.endsWith('.')) return '';
  let left = trimTail(trimmed.slice(0, -1));
  if (left.endsWith(')')) {
    const cut = skipCall(left);
    if (cut < 0) return '';
    left = left.slice(0, cut);
  }
  const name = trailingIdent(left);
  if (!types.some((type) => type.name === name)) return '';
  return name;
}

function skipCall(value) {
  let depth = 0;
  for (let index = value.length - 1; index >= 0; index -= 1) {
    const ch = value[index];
    if (ch === ')') depth += 1;
    else if (ch === '(') {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

function endsWithDot(before) {
  return trimTail(before).endsWith('.');
}

function trailingIdent(value) {
  let index = value.length;
  while (index > 0 && ident(value[index - 1])) index -= 1;
  return value.slice(index);
}

function trimTail(value) {
  let end = value.length;
  while (end > 0 && (value[end - 1] === ' ' || value[end - 1] === '\n' || value[end - 1] === '\t')) {
    end -= 1;
  }
  return value.slice(0, end);
}

function ident(ch) {
  return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch === '_';
}

module.exports = { completions };
