'use strict';

const mysql = require('mysql2/promise');
const { loadConfig } = require('./config');

let pool = null;

function getPool() {
  if (pool) return pool;
  const { db } = loadConfig();
  pool = mysql.createPool({
    host: db.host,
    port: db.port || 3306,
    user: db.user || db.username,
    password: db.password,
    database: db.database,
    charset: db.charset || 'utf8mb4',
    waitForConnections: true,
    connectionLimit: db.connectionLimit || 4,
    connectTimeout: db.connectTimeout || 5000,
    namedPlaceholders: true,
    // 让 BIGINT 以字符串返回，避免精度丢失；数值字段按需 Number()
    supportBigNumbers: true,
    bigNumberStrings: false,
  });
  return pool;
}

// 命名占位符查询: query(sql, {a:1}) -> rows
async function query(sql, params = {}) {
  const [rows] = await getPool().execute(sql, params);
  return rows;
}

// 取单行
async function queryOne(sql, params = {}) {
  const rows = await query(sql, params);
  return Array.isArray(rows) && rows.length ? rows[0] : null;
}

// 取单标量
async function queryScalar(sql, params = {}) {
  const row = await queryOne(sql, params);
  if (!row) return null;
  const keys = Object.keys(row);
  return keys.length ? row[keys[0]] : null;
}

// 直接执行（无占位符 DDL 用）
async function exec(sql) {
  const [res] = await getPool().query(sql);
  return res;
}

// 把数值安全转成可内联进 SQL 的非负整数字面量（用于 LIMIT/OFFSET，
// 因为预处理语句的 LIMIT 绑定在部分 MySQL 版本上会报错）。
function lim(n, def = 0) {
  const v = Math.trunc(Number(n));
  return String(Number.isFinite(v) && v >= 0 ? v : def);
}

// 事务: withTx(async (conn) => {...})；conn.execute 支持命名占位符
async function withTx(fn) {
  const conn = await getPool().getConnection();
  try {
    await conn.beginTransaction();
    const result = await fn(conn);
    await conn.commit();
    return result;
  } catch (err) {
    try { await conn.rollback(); } catch (_) { /* ignore */ }
    throw err;
  } finally {
    conn.release();
  }
}

module.exports = { getPool, query, queryOne, queryScalar, exec, withTx, lim };
