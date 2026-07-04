'use strict';

const db = require('./db');
const { tableName, adminTableNames } = require('./config');

let syncReady = false;
let adminReady = false;

async function ensureColumn(table, column, definition) {
  const row = await db.queryOne(
    'SELECT 1 AS ok FROM information_schema.COLUMNS '
    + 'WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t AND COLUMN_NAME = :c LIMIT 1',
    { t: table, c: column },
  );
  if (row) return;
  await db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
}

async function ensureIndex(table, index, columns) {
  const row = await db.queryOne(
    'SELECT 1 AS ok FROM information_schema.STATISTICS '
    + 'WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :t AND INDEX_NAME = :i LIMIT 1',
    { t: table, i: index },
  );
  if (row) return;
  await db.exec(`ALTER TABLE ${table} ADD INDEX ${index} (${columns})`);
}

async function ensureSyncSchema() {
  if (syncReady) return;
  const table = tableName();
  await db.exec(
    `CREATE TABLE IF NOT EXISTS ${table} (`
    + 'player_uuid CHAR(36) NOT NULL,'
    + 'data_key VARCHAR(64) NOT NULL,'
    + 'payload_json LONGTEXT NOT NULL,'
    + 'updated_at BIGINT NOT NULL,'
    + 'record_version BIGINT NOT NULL DEFAULT 1,'
    + 'created_at BIGINT NOT NULL DEFAULT 0,'
    + "updated_by VARCHAR(64) NOT NULL DEFAULT 'admin_viewer',"
    + 'PRIMARY KEY (player_uuid, data_key),'
    + 'KEY idx_updated_at (updated_at),'
    + 'KEY idx_data_key_updated_at (data_key, updated_at),'
    + 'KEY idx_player_uuid_updated_at (player_uuid, updated_at)'
    + ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  );

  await ensureColumn(table, 'record_version', 'BIGINT NOT NULL DEFAULT 0');
  await ensureColumn(table, 'created_at', 'BIGINT NOT NULL DEFAULT 0');
  await ensureColumn(table, 'updated_by', "VARCHAR(64) NOT NULL DEFAULT 'legacy'");
  await ensureIndex(table, 'idx_data_key_updated_at', 'data_key, updated_at');
  await ensureIndex(table, 'idx_player_uuid_updated_at', 'player_uuid, updated_at');
  await db.exec(`UPDATE ${table} SET record_version = 1 WHERE record_version = 0`);
  await db.exec(`UPDATE ${table} SET created_at = updated_at WHERE created_at = 0`);

  syncReady = true;
}

async function ensureAdminSchema() {
  if (adminReady) return;
  await ensureSyncSchema();
  const t = adminTableNames();

  await db.exec(
    `CREATE TABLE IF NOT EXISTS ${t.pendingOps} (`
    + 'id BIGINT NOT NULL AUTO_INCREMENT,'
    + 'player_name VARCHAR(64) NOT NULL,'
    + 'player_uuid CHAR(36) NULL,'
    + 'data_key VARCHAR(64) NOT NULL,'
    + 'operation_type VARCHAR(32) NOT NULL,'
    + 'payload_json LONGTEXT NOT NULL,'
    + "status VARCHAR(16) NOT NULL DEFAULT 'pending',"
    + 'note VARCHAR(255) NULL,'
    + 'created_at BIGINT NOT NULL,'
    + 'updated_at BIGINT NOT NULL,'
    + 'applied_at BIGINT NULL,'
    + 'PRIMARY KEY (id),'
    + 'KEY idx_status (status),'
    + 'KEY idx_player_name (player_name),'
    + 'KEY idx_player_uuid (player_uuid),'
    + 'KEY idx_data_key (data_key)'
    + ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  );

  await db.exec(
    `CREATE TABLE IF NOT EXISTS ${t.globalMail} (`
    + 'id CHAR(36) NOT NULL,'
    + 'sender VARCHAR(64) NOT NULL,'
    + 'title VARCHAR(255) NOT NULL,'
    + 'content LONGTEXT NOT NULL,'
    + 'commands_json LONGTEXT NOT NULL,'
    + 'sent_at BIGINT NOT NULL,'
    + 'expires_at BIGINT NOT NULL,'
    + 'active TINYINT(1) NOT NULL DEFAULT 1,'
    + 'created_at BIGINT NOT NULL,'
    + 'updated_at BIGINT NOT NULL,'
    + 'PRIMARY KEY (id),'
    + 'KEY idx_active (active),'
    + 'KEY idx_sent_at (sent_at)'
    + ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  );

  await db.exec(
    `CREATE TABLE IF NOT EXISTS ${t.globalMailDelivery} (`
    + 'mail_id CHAR(36) NOT NULL,'
    + 'player_uuid CHAR(36) NOT NULL,'
    + 'delivered_at BIGINT NOT NULL,'
    + 'PRIMARY KEY (mail_id, player_uuid),'
    + 'KEY idx_player_uuid (player_uuid),'
    + 'KEY idx_delivered_at (delivered_at)'
    + ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  );

  await db.exec(
    `CREATE TABLE IF NOT EXISTS ${t.playerIndex} (`
    + 'player_uuid CHAR(36) NOT NULL,'
    + 'player_name VARCHAR(64) NOT NULL,'
    + 'name_key VARCHAR(64) NOT NULL,'
    + 'source VARCHAR(32) NOT NULL,'
    + 'last_seen_at BIGINT NOT NULL,'
    + 'updated_at BIGINT NOT NULL,'
    + 'PRIMARY KEY (player_uuid),'
    + 'UNIQUE KEY uniq_name_key (name_key),'
    + 'KEY idx_last_seen_at (last_seen_at)'
    + ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci',
  );

  adminReady = true;
}

module.exports = { ensureSyncSchema, ensureAdminSchema };
