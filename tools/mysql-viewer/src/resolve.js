'use strict';

const fs = require('fs');
const db = require('./db');
const { loadConfig, tableName, adminTableNames, configInt } = require('./config');
const { ensureAdminSchema } = require('./schema');
const { nowMs, isUuid } = require('./util');

let usercacheCache = null;

function loadUsercache() {
  if (usercacheCache) return usercacheCache;
  const result = { entries: [], byName: {}, byUuid: {} };
  const path = String(loadConfig().usercachePath || '');
  if (path === '' || !fs.existsSync(path)) {
    usercacheCache = result;
    return result;
  }
  let raw;
  try {
    raw = fs.readFileSync(path, 'utf8');
  } catch (_) {
    usercacheCache = result;
    return result;
  }
  let decoded;
  try {
    decoded = JSON.parse(raw);
  } catch (_) {
    usercacheCache = result;
    return result;
  }
  if (!Array.isArray(decoded)) {
    usercacheCache = result;
    return result;
  }
  for (const entry of decoded) {
    if (!entry || typeof entry !== 'object') continue;
    const name = typeof entry.name === 'string' ? entry.name.trim() : '';
    const uuid = typeof entry.uuid === 'string' ? entry.uuid.trim() : '';
    if (name === '' || uuid === '') continue;
    const record = { name, uuid, expiresOn: typeof entry.expiresOn === 'string' ? entry.expiresOn : '' };
    result.entries.push(record);
    result.byName[name.toLowerCase()] = record;
    result.byUuid[uuid.toLowerCase()] = record;
  }
  usercacheCache = result;
  return result;
}

async function upsertPlayerIndex(playerName, playerUuid, source = 'usercache', seenAt = null) {
  const name = String(playerName || '').trim();
  const uuid = String(playerUuid || '').trim();
  const nameKey = name.toLowerCase();
  if (name === '' || uuid === '' || !isUuid(uuid)) return;

  await ensureAdminSchema();
  const t = adminTableNames();
  const now = seenAt == null ? nowMs() : seenAt;

  await db.withTx(async (conn) => {
    const [rows] = await conn.execute(
      `SELECT last_seen_at FROM ${t.playerIndex} WHERE player_uuid = :uuid OR name_key = :nameKey`,
      { uuid, nameKey },
    );
    let existingLastSeen = 0;
    for (const row of rows) {
      if (row.last_seen_at != null && Number.isFinite(Number(row.last_seen_at))) {
        existingLastSeen = Math.max(existingLastSeen, Number(row.last_seen_at));
      }
    }
    await conn.execute(
      `DELETE FROM ${t.playerIndex} WHERE player_uuid = :uuid OR name_key = :nameKey`,
      { uuid, nameKey },
    );
    await conn.execute(
      `INSERT INTO ${t.playerIndex} (player_uuid, player_name, name_key, source, last_seen_at, updated_at) `
      + 'VALUES (:uuid, :name, :nameKey, :source, :lastSeen, :updated)',
      { uuid, name, nameKey, source, lastSeen: Math.max(existingLastSeen, now), updated: now },
    );
  });
}

function extractPlayerIdentity(payloadJson, fallbackUuid = null) {
  if (!payloadJson || String(payloadJson).trim() === '') return null;
  let decoded;
  try {
    decoded = JSON.parse(payloadJson);
  } catch (_) {
    return null;
  }
  if (!decoded || typeof decoded !== 'object') return null;

  let playerUuid = '';
  for (const key of ['uuid', 'player_uuid', 'playerUuid']) {
    if (typeof decoded[key] === 'string' && decoded[key].trim() !== '') {
      playerUuid = decoded[key].trim();
      break;
    }
  }
  if (playerUuid === '' && fallbackUuid) playerUuid = String(fallbackUuid).trim();

  let playerName = '';
  for (const key of ['username', 'name', 'player_name', 'playerName']) {
    if (typeof decoded[key] === 'string' && decoded[key].trim() !== '') {
      playerName = decoded[key].trim();
      break;
    }
  }
  if (playerName === '' || !isUuid(playerUuid)) return null;
  return { player_name: playerName, player_uuid: playerUuid };
}

async function syncPlayerIndexFromUsercache() {
  const report = { source: 'usercache', processed: 0, indexed: 0, skipped: 0 };
  for (const entry of loadUsercache().entries) {
    report.processed += 1;
    if (!entry.name || !entry.uuid) { report.skipped += 1; continue; }
    // eslint-disable-next-line no-await-in-loop
    await upsertPlayerIndex(entry.name, entry.uuid, 'usercache');
    report.indexed += 1;
  }
  return report;
}

async function syncPlayerIndexFromSyncTable(limit = null, offset = 0) {
  const lim = limit == null ? configInt('identitySyncBatchSize', 500, 1, 5000) : limit;
  const off = Math.max(0, offset);
  const report = { source: 'sync_table', processed: 0, indexed: 0, skipped: 0, limit: lim, offset: off };

  const rows = await db.query(
    `SELECT player_uuid, payload_json, updated_at FROM ${tableName()} `
    + `WHERE data_key = :key ORDER BY updated_at DESC LIMIT ${db.lim(lim)} OFFSET ${db.lim(off)}`,
    { key: 'player_identity' },
  );
  rows.reverse();
  for (const row of rows) {
    report.processed += 1;
    const identity = extractPlayerIdentity(
      typeof row.payload_json === 'string' ? row.payload_json : null,
      typeof row.player_uuid === 'string' ? row.player_uuid : null,
    );
    if (!identity) { report.skipped += 1; continue; }
    // eslint-disable-next-line no-await-in-loop
    await upsertPlayerIndex(identity.player_name, identity.player_uuid, 'sync_table',
      row.updated_at != null ? Number(row.updated_at) : null);
    report.indexed += 1;
  }
  return report;
}

async function refreshPlayerIndex() {
  await ensureAdminSchema();
  return {
    usercache: await syncPlayerIndexFromUsercache(),
    sync_table: await syncPlayerIndexFromSyncTable(),
  };
}

async function lookupPlayerIndexByName(playerName) {
  await ensureAdminSchema();
  const t = adminTableNames();
  return db.queryOne(
    `SELECT player_uuid, player_name, source, last_seen_at FROM ${t.playerIndex} WHERE name_key = :nameKey LIMIT 1`,
    { nameKey: String(playerName || '').trim().toLowerCase() },
  );
}

async function lookupPlayerIndexByUuid(playerUuid) {
  await ensureAdminSchema();
  const t = adminTableNames();
  return db.queryOne(
    `SELECT player_uuid, player_name, source, last_seen_at FROM ${t.playerIndex} WHERE player_uuid = :uuid LIMIT 1`,
    { uuid: String(playerUuid || '').trim() },
  );
}

async function findPlayerIdentityInSyncTable(identifier) {
  const id = String(identifier || '').trim();
  if (id === '') return null;
  let rows;
  if (isUuid(id)) {
    rows = await db.query(
      `SELECT player_uuid, payload_json, updated_at FROM ${tableName()} `
      + 'WHERE data_key = :key AND player_uuid = :uuid ORDER BY updated_at DESC LIMIT 1',
      { key: 'player_identity', uuid: id },
    );
  } else {
    const like = '%"username":"' + id.replace(/[\\%_]/g, (m) => '\\' + m) + '"%';
    rows = await db.query(
      `SELECT player_uuid, payload_json, updated_at FROM ${tableName()} `
      + 'WHERE data_key = :key AND payload_json LIKE :like ORDER BY updated_at DESC LIMIT 5',
      { key: 'player_identity', like },
    );
  }
  for (const row of rows) {
    const identity = extractPlayerIdentity(
      typeof row.payload_json === 'string' ? row.payload_json : null,
      typeof row.player_uuid === 'string' ? row.player_uuid : null,
    );
    if (!identity || !isUuid(identity.player_uuid)) continue;
    if (!isUuid(id) && identity.player_name.toLowerCase() !== id.toLowerCase()) continue;
    // eslint-disable-next-line no-await-in-loop
    await upsertPlayerIndex(identity.player_name, identity.player_uuid, 'sync_table',
      row.updated_at != null ? Number(row.updated_at) : null);
    return { player_name: identity.player_name, player_uuid: identity.player_uuid, source: 'sync_table' };
  }
  return null;
}

async function findPendingPlayerUuid(playerName) {
  await ensureAdminSchema();
  const t = adminTableNames();
  const uuid = await db.queryScalar(
    `SELECT player_uuid FROM ${t.pendingOps} `
    + "WHERE player_name = :name AND player_uuid IS NOT NULL AND player_uuid <> '' ORDER BY updated_at DESC LIMIT 1",
    { name: String(playerName || '').trim() },
  );
  return (typeof uuid === 'string' && uuid !== '') ? uuid : null;
}

async function promotePendingOpsForPlayer(playerName, playerUuid) {
  const name = String(playerName || '').trim();
  const uuid = String(playerUuid || '').trim();
  if (name === '' || uuid === '' || !isUuid(uuid)) return;
  await ensureAdminSchema();
  const t = adminTableNames();
  await db.query(
    `UPDATE ${t.pendingOps} SET player_uuid = :uuid, updated_at = :updated `
    + "WHERE player_name = :name AND (player_uuid IS NULL OR player_uuid = '') AND status = 'pending'",
    { uuid, updated: nowMs(), name },
  );
}

async function recentPlayers(limit = 12) {
  await ensureAdminSchema();
  const t = adminTableNames();
  return db.query(
    `SELECT player_name AS name, player_uuid AS uuid, source FROM ${t.playerIndex} ORDER BY last_seen_at DESC LIMIT ${db.lim(limit)}`,
  );
}

// 四级解析: usercache -> player_index -> sync 表 player_identity -> pending_ops
async function resolvePlayer(identifier) {
  const id = String(identifier || '').trim();
  const usercache = loadUsercache();

  if (id !== '' && isUuid(id)) {
    const entry = usercache.byUuid[id.toLowerCase()] || null;
    const indexed = entry === null ? await lookupPlayerIndexByUuid(id) : null;
    const identity = (entry === null && indexed === null) ? await findPlayerIdentityInSyncTable(id) : null;
    if (entry !== null) {
      await upsertPlayerIndex(entry.name, id, 'usercache');
      await promotePendingOpsForPlayer(entry.name, id);
    } else if (indexed !== null) {
      await promotePendingOpsForPlayer(indexed.player_name, id);
    } else if (identity !== null) {
      await promotePendingOpsForPlayer(identity.player_name, id);
    }
    return {
      identifier: id,
      player_name: (entry && entry.name) || (indexed && indexed.player_name) || (identity && identity.player_name) || id,
      player_uuid: id,
      resolved: true,
      source: entry !== null ? 'usercache' : ((indexed && indexed.source) || (identity && identity.source) || 'direct_uuid'),
    };
  }

  const entry = usercache.byName[id.toLowerCase()] || null;
  if (entry !== null) {
    await upsertPlayerIndex(entry.name, entry.uuid, 'usercache');
    await promotePendingOpsForPlayer(entry.name, entry.uuid);
    return { identifier: id, player_name: entry.name, player_uuid: entry.uuid, resolved: true, source: 'usercache' };
  }

  const indexed = await lookupPlayerIndexByName(id);
  if (indexed !== null) {
    await promotePendingOpsForPlayer(indexed.player_name, indexed.player_uuid);
    return {
      identifier: id, player_name: indexed.player_name, player_uuid: indexed.player_uuid, resolved: true, source: indexed.source || 'player_index',
    };
  }

  const identity = await findPlayerIdentityInSyncTable(id);
  if (identity !== null) {
    await promotePendingOpsForPlayer(identity.player_name, identity.player_uuid);
    return {
      identifier: id, player_name: identity.player_name, player_uuid: identity.player_uuid, resolved: true, source: identity.source,
    };
  }

  const pendingUuid = await findPendingPlayerUuid(id);
  if (pendingUuid !== null) {
    await upsertPlayerIndex(id, pendingUuid, 'pending');
    await promotePendingOpsForPlayer(id, pendingUuid);
    return { identifier: id, player_name: id, player_uuid: pendingUuid, resolved: true, source: 'pending' };
  }

  return { identifier: id, player_name: id, player_uuid: null, resolved: false, source: 'unknown' };
}

module.exports = {
  loadUsercache,
  upsertPlayerIndex,
  refreshPlayerIndex,
  recentPlayers,
  resolvePlayer,
};
