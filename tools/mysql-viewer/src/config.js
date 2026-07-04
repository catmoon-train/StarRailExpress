'use strict';

const fs = require('fs');
const path = require('path');

let cached = null;

function loadConfig() {
  if (cached) return cached;
  const root = path.join(__dirname, '..');
  const local = path.join(root, 'config.local.js');
  const sample = path.join(root, 'config.sample.js');
  const file = fs.existsSync(local) ? local : sample;
  // eslint-disable-next-line import/no-dynamic-require, global-require
  const cfg = require(file);
  if (!cfg || typeof cfg !== 'object') {
    throw new Error('配置文件无效: ' + file);
  }
  cached = cfg;
  if (file === sample) {
    // eslint-disable-next-line no-console
    console.warn('[config] 未找到 config.local.js，已回退到 config.sample.js，请复制并填写真实配置。');
  }
  return cached;
}

function tableName() {
  const t = String(loadConfig().tableName || 'sre_player_sync_data');
  if (!/^[A-Za-z0-9_]+$/.test(t)) {
    throw new Error('非法的表名: ' + t);
  }
  return t;
}

// 管理辅助表名：主表去掉 player_sync_data 后缀作为前缀，默认 sre_
function adminTableNames() {
  const sync = tableName();
  const suffix = 'player_sync_data';
  const prefix = sync.endsWith(suffix) ? sync.slice(0, sync.length - suffix.length) : 'sre_';
  return {
    pendingOps: prefix + 'admin_pending_ops',
    globalMail: prefix + 'admin_global_mail',
    globalMailDelivery: prefix + 'admin_global_mail_delivery',
    playerIndex: prefix + 'admin_player_index',
  };
}

function configInt(key, def, min = 1, max = Number.MAX_SAFE_INTEGER) {
  const v = loadConfig()[key];
  const n = Number(v);
  if (!Number.isFinite(n)) return def;
  return Math.max(min, Math.min(Math.trunc(n), max));
}

module.exports = { loadConfig, tableName, adminTableNames, configInt };
