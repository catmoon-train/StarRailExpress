'use strict';

const { nowMs, uuidV4, tryParseObject, isNumeric } = require('./util');

// 对照 viewer_merge_stats_payload
const STATS_NUMERIC_FIELDS = [
  'totalPlayTime', 'totalGamesPlayed', 'totalKills', 'totalDeaths', 'totalWins', 'totalLosses',
  'totalTeamKills', 'totalLoversWins',
  'totalCivilianGames', 'totalCivilianWins', 'totalCivilianKills', 'totalCivilianDeaths',
  'totalKillerGames', 'totalKillerWins', 'totalKillerKills', 'totalKillerDeaths',
  'totalNeutralGames', 'totalNeutralWins', 'totalNeutralKills', 'totalNeutralDeaths',
  'totalSheriffGames', 'totalSheriffWins', 'totalSheriffKills', 'totalSheriffDeaths',
];

function mergeStatsPayload(existingPayload, patch) {
  let payload = tryParseObject(existingPayload) || {};
  for (const field of STATS_NUMERIC_FIELDS) {
    if (isNumeric(patch[field])) payload[field] = Math.trunc(Number(patch[field]));
  }
  if (patch.roleStats && typeof patch.roleStats === 'object') {
    payload.roleStats = patch.roleStats;
  }
  payload.version = nowMs();
  return JSON.stringify(payload);
}

function mergeSkinsPayload(existingPayload, patch) {
  let payload = { equipped: {}, unlocked: {}, lootChance: 0, coinNum: 0 };
  const decoded = tryParseObject(existingPayload);
  if (decoded) payload = Object.assign(payload, decoded);
  for (const field of ['coinNum', 'lootChance']) {
    if (isNumeric(patch[field])) payload[field] = Math.trunc(Number(patch[field]));
  }
  for (const field of ['equipped', 'unlocked']) {
    if (patch[field] && typeof patch[field] === 'object') payload[field] = patch[field];
  }
  const ts = nowMs();
  payload.version = ts;
  payload.timestamp = ts;
  return JSON.stringify(payload);
}

function mergeProgressionPayload(existingPayload, patch) {
  let payload = { factionCards: {} };
  const decoded = tryParseObject(existingPayload);
  if (decoded) payload = Object.assign(payload, decoded);
  if (!payload.factionCards || typeof payload.factionCards !== 'object') payload.factionCards = {};

  for (const field of [
    'level', 'experience', 'totalExperience', 'claimedCoinRewards', 'claimedLootRewards',
    'lastQuestRefreshTime', 'lastWeeklyRefreshTime',
  ]) {
    if (isNumeric(patch[field])) payload[field] = Math.trunc(Number(patch[field]));
  }
  if (patch.factionCards && typeof patch.factionCards === 'object' && !Array.isArray(patch.factionCards)) {
    payload.factionCards = patch.factionCards;
  }
  if (patch.factionCardsDelta && typeof patch.factionCardsDelta === 'object') {
    for (const [cardKey, delta] of Object.entries(patch.factionCardsDelta)) {
      if (!isNumeric(delta)) continue;
      const current = isNumeric(payload.factionCards[cardKey]) ? Math.trunc(Number(payload.factionCards[cardKey])) : 0;
      payload.factionCards[cardKey] = Math.max(0, current + Math.trunc(Number(delta)));
    }
  }
  payload.version = nowMs();
  return JSON.stringify(payload);
}

function mergeNametagsPayload(existingPayload, patch) {
  let payload = { nameTags: [], currentNametag: '' };
  const decoded = tryParseObject(existingPayload);
  if (decoded) payload = Object.assign(payload, decoded);

  if (Array.isArray(patch.nameTags)) {
    payload.nameTags = patch.nameTags
      .map((v) => (typeof v === 'string' ? v.trim() : ''))
      .filter((v) => v !== '');
  }
  if (typeof patch.currentNametag === 'string') {
    payload.currentNametag = patch.currentNametag.trim();
  }
  const ts = nowMs();
  payload.version = ts;
  payload.timestamp = ts;
  return JSON.stringify(payload);
}

// 按 id 去重追加（保留已有，补入新邮件）
function mergeMailboxPayload(existingPayload, mailObjects) {
  let existing = tryParseObject(existingPayload);
  if (!Array.isArray(existing)) existing = [];
  const byId = new Map();
  for (const item of existing) {
    if (item && typeof item === 'object' && item.id !== undefined) byId.set(String(item.id), item);
  }
  for (const mail of mailObjects || []) {
    if (mail && typeof mail === 'object' && mail.id !== undefined && !byId.has(String(mail.id))) {
      byId.set(String(mail.id), mail);
    }
  }
  return JSON.stringify(Array.from(byId.values()));
}

// 规范化整表替换
function replaceMailboxPayload(mails) {
  const normalized = [];
  for (const mail of mails || []) {
    if (!mail || typeof mail !== 'object') continue;
    const id = (typeof mail.id === 'string' && mail.id.trim() !== '') ? mail.id.trim() : uuidV4();
    let commands = Array.isArray(mail.commands) ? mail.commands : [];
    commands = commands
      .map((c) => (typeof c === 'string' ? c.trim() : ''))
      .filter((c) => c !== '');
    normalized.push({
      id,
      sender: (typeof mail.sender === 'string' && mail.sender.trim() !== '') ? mail.sender.trim() : 'System',
      title: typeof mail.title === 'string' ? mail.title.trim() : '',
      content: typeof mail.content === 'string' ? mail.content : '',
      claimed: !!mail.claimed,
      read: !!mail.read,
      sentAt: isNumeric(mail.sentAt) ? Math.trunc(Number(mail.sentAt)) : nowMs(),
      expiresAt: isNumeric(mail.expiresAt) ? Math.max(0, Math.trunc(Number(mail.expiresAt))) : 0,
      commands,
    });
  }
  return JSON.stringify(normalized);
}

// pending 队列里同型操作的合并（对照 viewer_merge_pending_payload）
function mergePendingPayload(operationType, existingPayloadJson, payload) {
  const existing = tryParseObject(existingPayloadJson) || {};
  switch (operationType) {
    case 'mailbox_append': {
      const mergedMails = mergeMailboxPayload(
        JSON.stringify(Array.isArray(existing.mails) ? existing.mails : []),
        Array.isArray(payload.mails) ? payload.mails : [],
      );
      return JSON.stringify({ mails: JSON.parse(mergedMails) });
    }
    case 'skins_merge':
    case 'progression_merge':
    case 'stats_merge':
    case 'mailbox_replace':
    case 'nametags_merge':
      return JSON.stringify(Object.assign({}, existing, payload));
    default:
      return JSON.stringify(payload);
  }
}

// 构造一封邮件对象（对照 viewer_build_mail_payload）
function buildMailPayload(sender, title, content, commands, expiresAt = 0) {
  return {
    id: uuidV4(),
    sender: sender && sender !== '' ? sender : 'System',
    title,
    content,
    claimed: false,
    read: false,
    sentAt: nowMs(),
    expiresAt: Math.max(0, expiresAt),
    commands: (Array.isArray(commands) ? commands : [])
      .map((c) => (typeof c === 'string' ? c.trim() : ''))
      .filter((c) => c !== ''),
  };
}

module.exports = {
  mergeStatsPayload,
  mergeSkinsPayload,
  mergeProgressionPayload,
  mergeNametagsPayload,
  mergeMailboxPayload,
  replaceMailboxPayload,
  mergePendingPayload,
  buildMailPayload,
  STATS_NUMERIC_FIELDS,
};
