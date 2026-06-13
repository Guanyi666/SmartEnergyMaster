const fs = require('fs');
const path = require('path');
const SRC = path.resolve('src');

function walk(dir) {
  const out = [];
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name);
    if (f.isDirectory()) out.push(...walk(p));
    else if (/\.(vue|js)$/.test(f.name)) out.push(p);
  }
  return out;
}

const moduleExports = new Map();
for (const file of walk(SRC)) {
  const rel = path.relative(SRC, file).replace(/\\/g, '/').replace(/\.(vue|js)$/, '');
  const txt = fs.readFileSync(file, 'utf8');
  const names = new Set();
  for (const m of txt.matchAll(/^\s*export\s+(?:const|let|var|function|class|async\s+function)\s+([A-Za-z_$][\w$]*)/gm)) names.add(m[1]);
  for (const m of txt.matchAll(/^\s*export\s*\{([^}]+)\}/gm)) for (const x of m[1].split(',')) {
    const n = x.trim().split(/\s+as\s+/).pop().trim();
    if (n) names.add(n);
  }
  if (names.size) moduleExports.set(rel, names);
}

for (const file of walk(SRC)) {
  const txt = fs.readFileSync(file, 'utf8');
  const m = txt.match(/<script\s+setup[^>]*>([\s\S]*?)<\/script>/);
  const body = m ? m[1] : txt;

  for (const im of body.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]([^'"]+)['"]/g)) {
    const names = im[1].split(',').map(s => s.trim().split(/\s+as\s+/).pop().trim()).filter(Boolean);
    let target = im[2];
    if (!target.startsWith('.')) continue;
    const fromDir = path.dirname(file);
    let abs = path.resolve(fromDir, target);
    const candidates = [
      abs + '.vue',
      abs + '.js',
      path.join(abs, 'index.js'),
      path.join(abs, 'index.vue')
    ];
    let foundKey = null;
    for (const c of candidates) {
      try { fs.accessSync(c); foundKey = path.relative(SRC, c).replace(/\\/g, '/').replace(/\.(vue|js)$/, ''); break; } catch {}
    }
    if (!foundKey) {
      console.log(`${path.relative(SRC, file)}: CANNOT RESOLVE '${target}' (used: ${names.join(', ')})`);
      continue;
    }
    const exports = moduleExports.get(foundKey) || new Set();
    for (const n of names) {
      if (!exports.has(n)) {
        console.log(`${path.relative(SRC, file)}: '${n}' NOT EXPORTED from '${target}' (resolved: ${foundKey})`);
      }
    }
  }
}