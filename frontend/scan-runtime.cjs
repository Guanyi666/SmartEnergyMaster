const fs = require('fs');
const path = require('path');
const SRC = path.resolve('src');
const VUE_APIS = ['ref','reactive','computed','watch','watchEffect','onMounted','onBeforeUnmount','onUnmounted','onUpdated','onBeforeMount','onBeforeUpdate','nextTick','shallowRef','shallowReactive','toRefs','toRef','provide','inject','useTemplateRef','customRef','triggerRef','markRaw','toRaw','readonly'];

function walk(dir) {
  const out = [];
  for (const f of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, f.name);
    if (f.isDirectory()) out.push(...walk(p));
    else if (/\.(vue|js)$/.test(f.name)) out.push(p);
  }
  return out;
}

for (const file of walk(SRC)) {
  const txt = fs.readFileSync(file, 'utf8');
  const m = txt.match(/<script\s+setup[^>]*>([\s\S]*?)<\/script>/);
  const body = m ? m[1] : txt;

  const imported = new Set();
  for (const im of body.matchAll(/import\s*\{([^}]+)\}\s*from\s*['"]vue['"]/g)) {
    for (const x of im[1].split(',')) {
      const n = x.trim().split(/\s+as\s+/).pop().trim();
      if (n) imported.add(n);
    }
  }

  const issues = [];
  for (const api of VUE_APIS) {
    if (imported.has(api)) continue;
    // 词边界匹配: 不是字母/数字/_/$ 紧挨在前面
    const re = new RegExp('(^|[^A-Za-z0-9_$])' + api + '\\s*\\(', 'g');
    if (re.test(body)) issues.push(api);
  }
  if (issues.length) {
    console.log(path.relative(SRC, file) + ': missing ' + issues.join(', '));
  }
}