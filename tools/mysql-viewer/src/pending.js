'use strict';

const db = require('./db');
const { tableName, adminTableNames, configInt } = require('./config');
const { ensureAdminSchema } = require('./schema');
const { nowMs, isUuid } = require('./util');
const merge = require('./merge');
const records = require('./records');
const { loadUsercache, recentPlayers, resolvePlayer } = require('./resolve');

async function queuePendingOp(playerName, playerUuid, dataKey, operationType, payload, note = null) {
  await ensureAdminSchema();
  const t = adminTableNames();
  const now = nowMs();

  const existing = await db.queryOne(
    `SELECT id, payload_json FROM ${t.pendingOps} `
    + 'WHERE player_name = :name AND data_key = :key AND operation_type = :op AND status = :status '
    + 'ORDER BY updated_at DESC LIMIT 1',
    { name: playerName, key: dataKey, op: operationType, status: 'pending' },
  );

  if (existing) {
    const mergedPayload = merge.mergePendingPayload(operationType, String(existing.payload_json), payload);
    await db.query(
      `UPDATE ${t.pendingOps} SET player_uuid = COALESCE(:uuid, player_uuid), payload_json = :payload, note = :note, updated_at = :updated WHERE id = :id`,
      {
        uuid: playerUuid == null ? null : playerUuid,
        payload: mergedPayload,
        note: note == null ? null : note,
        updated: now,
        id: existing.id,
      },
    );
    return Number(existing.id);
  }

  const res = await db.query(
    `INSERT INTO ${t.pendingOps} (player_name, player_uuid, data_key, operation_type, payload_json, status, note, created_at, updated_at, applied_at) `
    + 'VALUES (:name, :uuid, :key, :op, :payload, :status, :note, :created, :updated, NULL)',
    {
      name: playerName,
      uuid: playerUuid == null ? null : playerUuid,
      key: dataKey,
      op: operationType,
      payload: JSON.stringify(payload),
      status: 'pending',
      note: note == null ? null : note,
      created: now,
      updated: now,
    },
  );
  return Number(res.insertId);
}

async function listPendingOps(limit = 100) {
  await ensureAdminSchema();
  const t = adminTableNames();
  return db.query(`SELECT * FROM ${t.pendingOps} ORDER BY created_at DESC LIMIT ${db.lim(limit)}`);
}

async function listGlobalMails(limit = 50) {
  await ensureAdminSchema();
  const t = adminTableNames();
  return db.query(`SELECT * FROM ${t.globalMail} ORDER BY created_at DESC LIMIT ${db.lim(limit)}`);
}

async function insertGlobalMail(mail) {
  await ensureAdminSchema();
  const t = adminTableNames();
  const now = nowMs();
  await db.query(
    `INSERT INTO ${t.globalMail} (id, sender, title, content, commands_json, sent_at, expires_at, active, created_at, updated_at) `
    + 'VALUES (:id, :sender, :title, :content, :commands, :sentAt, :expiresAt, 1, :created, :updated)',
    {
      id: mail.id,
      sender: mail.sender,
      title: mail.title,
      content: mail.content,
      commands: JSON.stringify(mail.commands),
      sentAt: mail.sentAt,
      expiresAt: mail.expiresAt,
      created: now,
      updated: now,
    },
  );
}

async function collectKnownPlayerUuids(limit = null) {
  await ensureAdminSchema();
  const cap = limit == null ? configInt('reconcileBatchSize', 500, 1, 5000) : limit;
  const t = adminTableNames();
  const uuids = new Set();

  for (const entry of loadUsercache().entries) {
    if (uuids.size >= cap) break;
    if (typeof entry.uuid === 'string' && isUuid(entry.uuid)) uuids.add(entry.uuid);
  }

  if (uuids.size < cap) {
    const rows = await db.query(
      `SELECT player_uuid FROM ${t.playerIndex} ORDER BY last_seen_at DESC LIMIT ${db.lim(cap)}`,
    );
    for (const row of rows) {
      if (uuids.size >= cap) break;
      if (typeof row.player_uuid === 'string' && isUuid(row.player_uuid)) uuids.add(row.player_uuid);
    }
  }

  if (uuids.size < cap) {
    const rows = await db.query(
      `SELECT player_uuid FROM ${tableName()} GROUP BY player_uuid ORDER BY MAX(updated_at) DESC LIMIT ${db.lim(cap)}`,
    );
    for (const row of rows) {
      if (uuids.size >= cap) break;
      if (typeof row.player_uuid === 'string' && isUuid(row.player_uuid)) uuids.add(row.player_uuid);
    }
  }

  for (const entry of await recentPlayers(Math.min(200, cap))) {
    if (uuids.size >= cap) break;
    if (typeof entry.uuid === 'string' && isUuid(entry.uuid)) uuids.add(entry.uuid);
  }

  return Array.from(uuids);
}

async function runReconcile() {
  await ensureAdminSchema();
  const t = adminTableNames();
  const now = nowMs();
  const batchSize = configInt('reconcileBatchSize', 500, 1, 5000);
  const report = {
    processed: 0,
    pending_applied: 0,
    pending_waiting: 0,
    pending_conflicts: 0,
    global_mail_applied: 0,
    global_mail_waiting: 0,
    batch_size: batchSize,
  };

  const pendingRows = await db.query(
    `SELECT * FROM ${t.pendingOps} WHERE status = 'pending' ORDER BY id ASC LIMIT ${db.lim(batchSize)}`,
  );

  for (const row of pendingRows) {
    report.processed += 1;
    let resolvedUuid = null;
    if (typeof row.player_uuid === 'string' && row.player_uuid !== '') {
      resolvedUuid = row.player_uuid;
    } else {
      // eslint-disable-next-line no-await-in-loop
      const r = await resolvePlayer(String(row.player_name));
      resolvedUuid = r.player_uuid || null;
    }
    if (!resolvedUuid) { report.pending_waiting += 1; continue; }

    let payload;
    try { payload = JSON.parse(String(row.payload_json)); } catch (_) { payload = {}; }
    if (!payload || typeof payload !== 'object') payload = {};

    const dataKey = String(row.data_key);
    // eslint-disable-next-line no-await-in-loop
    const existing = await records.fetchSyncRecord(resolvedUuid, dataKey);
    const existingPayload = existing ? existing.payload_json : null;

    try {
      let newPayload;
      switch (String(row.operation_type)) {
        case 'skins_merge': newPayload = merge.mergeSkinsPayload(existingPayload, payload); break;
        case 'progression_merge': newPayload = merge.mergeProgressionPayload(existingPayload, payload); break;
        case 'stats_merge': newPayload = merge.mergeStatsPayload(existingPayload, payload); break;
        case 'mailbox_append': newPayload = merge.mergeMailboxPayload(existingPayload, Array.isArray(payload.mails) ? payload.mails : []); break;
        case 'mailbox_replace': newPayload = merge.replaceMailboxPayload(Array.isArray(payload.mails) ? payload.mails : []); break;
        case 'nametags_merge': newPayload = merge.mergeNametagsPayload(existingPayload, payload); break;
        case 'raw_replace':
        default:
          newPayload = typeof payload.raw_payload === 'string' ? payload.raw_payload : '{}';
          break;
      }

      // eslint-disable-next-line no-await-in-loop
      await records.upsertSyncRecord(resolvedUuid, dataKey, newPayload, now);
      // eslint-disable-next-line no-await-in-loop
      await db.query(
        `UPDATE ${t.pendingOps} SET status = 'applied', player_uuid = :uuid, applied_at = :applied, updated_at = :updated WHERE id = :id`,
        { uuid: resolvedUuid, applied: now, updated: now, id: row.id },
      );
      report.pending_applied += 1;
    } catch (err) {
      report.pending_conflicts += 1;
      // eslint-disable-next-line no-await-in-loop
      await db.query(
        `UPDATE ${t.pendingOps} SET note = :note, updated_at = :updated WHERE id = :id`,
        { note: String(err.message || err).slice(0, 240), updated: now, id: row.id },
      );
    }
  }

  // 全局邮件投递
  const mailRows = await db.query(`SELECT * FROM ${t.globalMail} WHERE active = 1 ORDER BY created_at ASC`);
  const globalMails = mailRows.map((row) => {
    let commands;
    try { commands = JSON.parse(String(row.commands_json)); } catch (_) { commands = []; }
    return {
      id: row.id,
      sender: row.sender,
      title: row.title,
      content: row.content,
      claimed: false,
      read: false,
      sentAt: Number(row.sent_at),
      expiresAt: Number(row.expires_at),
      commands: Array.isArray(commands) ? commands : [],
    };
  });

  if (globalMails.length > 0) {
    const knownUuids = await collectKnownPlayerUuids(batchSize);
    for (const uuid of knownUuids) {
      const newMails = [];
      for (const mail of globalMails) {
        // eslint-disable-next-line no-await-in-loop
        const delivered = await db.queryScalar(
          `SELECT 1 FROM ${t.globalMailDelivery} WHERE mail_id = :mid AND player_uuid = :uuid LIMIT 1`,
          { mid: mail.id, uuid },
        );
        if (delivered) continue;
        newMails.push(mail);
      }
      if (newMails.length === 0) continue;

      // eslint-disable-next-line no-await-in-loop
      const existing = await records.fetchSyncRecord(uuid, 'mailbox');
      const existingPayload = existing ? existing.payload_json : null;
      const newPayload = merge.mergeMailboxPayload(existingPayload, newMails);
      if (existingPayload !== newPayload) {
        // eslint-disable-next-line no-await-in-loop
        await records.upsertSyncRecord(uuid, 'mailbox', newPayload, now);
      }
      for (const mail of newMails) {
        // eslint-disable-next-line no-await-in-loop
        await db.query(
          `INSERT IGNORE INTO ${t.globalMailDelivery} (mail_id, player_uuid, delivered_at) VALUES (:mid, :uuid, :at)`,
          { mid: mail.id, uuid, at: now },
        );
      }
      report.global_mail_applied += newMails.length;
    }
    report.global_mail_waiting = 0;
  }

  return report;
}

module.exports = {
  queuePendingOp,
  listPendingOps,
  listGlobalMails,
  insertGlobalMail,
  collectKnownPlayerUuids,
  runReconcile,
};
