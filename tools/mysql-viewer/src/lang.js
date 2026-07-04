'use strict';

const fs = require('fs');
const path = require('path');

let bundle = null;

function loadBundle() {
  if (bundle) return bundle;
  bundle = {};
  const root = path.join(__dirname, '..', '..', '..'); // tools/mysql-viewer -> 项目根
  const files = [
    path.join(root, 'src/main/resources/assets/starrailexpress/lang/zh_cn.json'),
    path.join(root, 'src/main/resources/assets/stupid_express/lang/zh_cn.json'),
    path.join(root, 'src/main/resources/assets/noellesroles/lang/zh_cn.json'),
  ];
  for (const file of files) {
    try {
      if (!fs.existsSync(file)) continue;
      const raw = fs.readFileSync(file, 'utf8');
      if (!raw || raw.trim() === '') continue;
      const decoded = JSON.parse(raw);
      if (decoded && typeof decoded === 'object') Object.assign(bundle, decoded);
    } catch (_) { /* ignore */ }
  }
  return bundle;
}

function t(key, fallback = '') {
  const b = loadBundle();
  if (typeof b[key] === 'string' && b[key].trim() !== '') return b[key];
  return fallback !== '' ? fallback : key;
}

module.exports = { t };
