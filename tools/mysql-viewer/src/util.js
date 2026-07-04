'use strict';

const crypto = require('crypto');

function nowMs() {
  return Date.now();
}

function isUuid(value) {
  return typeof value === 'string'
    && /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/.test(value);
}

function uuidV4() {
  return crypto.randomUUID();
}

// 解析 payload_json -> 对象/数组/原文
function decodePayload(payload) {
  if (payload === null || payload === undefined || payload === '') return null;
  try {
    return JSON.parse(payload);
  } catch (_) {
    return payload;
  }
}

// 把字符串安全转 JSON 对象（失败返回 null）
function tryParseObject(str) {
  if (typeof str !== 'string' || str.trim() === '') return null;
  try {
    const v = JSON.parse(str);
    return (v && typeof v === 'object') ? v : null;
  } catch (_) {
    return null;
  }
}

// 把逗号/空格/分号/竖线/中文标点分隔的标识符拆分并去重（保留原始大小写，按小写去重）
function splitIdentifiers(raw) {
  if (typeof raw !== 'string') return [];
  const trimmed = raw.trim();
  if (trimmed === '') return [];
  const parts = trimmed.split(/[\s,，;；|]+/u);
  const seen = new Map();
  for (const part of parts) {
    const id = String(part || '').trim();
    if (id === '') continue;
    const key = id.toLowerCase();
    if (!seen.has(key)) seen.set(key, id);
  }
  return Array.from(seen.values());
}

function toInt(value, def = 0) {
  if (value === null || value === undefined || value === '') return def;
  const n = Number(value);
  return Number.isFinite(n) ? Math.trunc(n) : def;
}

function isNumeric(value) {
  if (typeof value === 'number') return Number.isFinite(value);
  if (typeof value === 'string' && value.trim() !== '') return Number.isFinite(Number(value));
  return false;
}

module.exports = {
  nowMs, isUuid, uuidV4, decodePayload, tryParseObject, splitIdentifiers, toInt, isNumeric,
};
