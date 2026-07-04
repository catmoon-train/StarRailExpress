'use strict';

const db = require('./db');
const { tableName } = require('./config');
const { ensureSyncSchema } = require('./schema');
const { nowMs, decodePayload } = require('./util');

async function fetchSyncRecord(playerUuid, dataKey) {
  await ensureSyncSchema();
  return db.queryOne(
    `SELECT player_uuid, data_key, payload_json, updated_at, record_version, updated_by FROM ${tableName()} `
    + 'WHERE player_uuid = :uuid AND data_key = :key LIMIT 1',
    { uuid: playerUuid, key: dataKey },
  );
}

async function fetchPlayerRecords(playerUuid) {
  await ensureSyncSchema();
  const rows = await db.query(
    `SELECT player_uuid, data_key, payload_json, updated_at, record_version, updated_by FROM ${tableName()} `
    + 'WHERE player_uuid = :uuid ORDER BY data_key ASC',
    { uuid: playerUuid },
  );
  const records = {};
  for (const row of rows) {
    records[row.data_key] = {
      player_uuid: row.player_uuid,
      data_key: row.data_key,
      updated_at: Number(row.updated_at),
      record_version: Number(row.record_version),
      updated_by: String(row.updated_by),
      payload: decodePayload(row.payload_json),
      raw_payload: row.payload_json,
    };
  }
  return records;
}

async function upsertSyncRecord(playerUuid, dataKey, payloadJson, updatedAt = null) {
  await ensureSyncSchema();
  const ts = updatedAt == null ? nowMs() : updatedAt;
  const sql = `INSERT INTO ${tableName()} `
    + '(player_uuid, data_key, payload_json, updated_at, record_version, created_at, updated_by) '
    + 'VALUES (:uuid, :key, :payload, :updated, 1, :created, :by) '
    + 'ON DUPLICATE KEY UPDATE payload_json = VALUES(payload_json), '
    + 'updated_at = GREATEST(updated_at, VALUES(updated_at)), '
    + 'updated_by = VALUES(updated_by), '
    + 'record_version = record_version + 1';
  await db.query(sql, {
    uuid: playerUuid,
    key: dataKey,
    payload: payloadJson,
    updated: ts,
    created: ts,
    by: 'admin_viewer',
  });
}

module.exports = { fetchSyncRecord, fetchPlayerRecords, upsertSyncRecord };
