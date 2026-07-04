'use strict';

const express = require('express');
const db = require('./db');
const { loadConfig, tableName, adminTableNames } = require('./config');
const { ensureSyncSchema, ensureAdminSchema } = require('./schema');
const auth = require('./auth');
const { resolvePlayer, refreshPlayerIndex, recentPlayers, loadUsercache } = require('./resolve');
const records = require('./records');
const merge = require('./merge');
const pending = require('./pending');
const lang = require('./lang');
const { nowMs, splitIdentifiers, toInt, isNumeric } = require('./util');

const router = express.Router();

// ---------- 请求体辅助 ----------
function bodyString(req, key, def = '') {
  const v = req.body ? req.body[key] : undefined;
  return typeof v === 'string' ? v.trim() : def;
}
function bodyInt(req, key, def = 0) {
  const v = req.body ? req.body[key] : undefined;
  return isNumeric(v) ? toInt(v, def) : def;
}
function bodyArray(req, key) {
  const v = req.body ? req.body[key] : undefined;
  return Array.isArray(v) ? v : [];
}
function bodyObject(req, key) {
  const v = req.body ? req.body[key] : undefined;
  return (v && typeof v === 'object' && !Array.isArray(v)) ? v : {};
}

function wrap(fn) {
  return (req, res) => Promise.resolve(fn(req, res)).catch((err) => {
    // eslint-disable-next-line no-console
    console.error('[api]', err);
    res.status(500).json({ ok: false, message: String(err && err.message ? err.message : err) });
  });
}

// 两段式保存：命中 UUID 立即 merge+upsert，否则进 pending 队列
async function twoTierMerge(resolved, dataKey, opType, patch, mergeFn, note) {
  if (resolved.player_uuid) {
    const existing = await records.fetchSyncRecord(resolved.player_uuid, dataKey);
    const merged = mergeFn(existing ? existing.payload_json : null, patch);
    await records.upsertSyncRecord(resolved.player_uuid, dataKey, merged);
    return { ok: true, mode: 'applied', player: resolved };
  }
  const pendingId = await pending.queuePendingOp(resolved.player_name, null, dataKey, opType, patch, note);
  return { ok: true, mode: 'queued', pending_id: pendingId, player: resolved };
}

// ============ 公共端点（无需登录） ============

router.get('/me', wrap(async (req, res) => {
  const cfg = loadConfig();
  res.json({
    ok: true,
    authenticated: auth.isLoggedIn(req),
    authEnabled: auth.authConfig().enabled,
    username: req.session && req.session.authUser ? req.session.authUser : null,
    siteTitle: cfg.siteTitle || 'StarRailExpress 数据管理台',
  });
}));

router.post('/login', wrap(async (req, res) => {
  const username = bodyString(req, 'username');
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  if (!auth.verifyLogin(username, password)) {
    return res.status(401).json({ ok: false, message: '用户名或密码错误。' });
  }
  await new Promise((resolve, reject) => req.session.regenerate((e) => (e ? reject(e) : resolve())));
  req.session.authOk = true;
  req.session.authUser = username;
  req.session.authAt = nowMs();
  return res.json({ ok: true, username });
}));

router.post('/logout', wrap(async (req, res) => {
  await new Promise((resolve) => req.session.destroy(() => resolve()));
  res.json({ ok: true });
}));

// 只读概要（对照旧 player_dashboard）
router.get('/dashboard', wrap(async (req, res) => {
  const identifier = String(req.query.identifier || '').trim();
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });

  const resolved = await resolvePlayer(identifier);
  if (!resolved.player_uuid) return res.status(404).json({ ok: false, message: '未找到该玩家，请检查用户名是否正确。' });

  const recs = await records.fetchPlayerRecords(resolved.player_uuid);
  const stats = (recs.stats && recs.stats.payload && typeof recs.stats.payload === 'object') ? recs.stats.payload : {};
  const progression = (recs.progression && recs.progression.payload && typeof recs.progression.payload === 'object') ? recs.progression.payload : {};
  const skins = (recs.skins && recs.skins.payload && typeof recs.skins.payload === 'object') ? recs.skins.payload : {};
  const mailbox = (recs.mailbox && Array.isArray(recs.mailbox.payload)) ? recs.mailbox.payload : [];

  const num = (src, keys, def = 0) => {
    for (const k of keys) if (isNumeric(src[k])) return toInt(src[k]);
    return def;
  };

  const totalGames = num(stats, ['totalGamesPlayed']);
  const totalWins = num(stats, ['totalWins']);
  const totalKills = num(stats, ['totalKills']);
  const totalDeaths = num(stats, ['totalDeaths']);
  const winRate = totalGames > 0 ? Math.round((totalWins / totalGames) * 1000) / 10 : 0;
  const kd = totalDeaths > 0 ? Math.round((totalKills / totalDeaths) * 100) / 100 : (totalKills > 0 ? totalKills : 0);

  const statsRows = [
    { label: '总游玩局数', value: totalGames },
    { label: '总胜场', value: totalWins },
    { label: '总败场', value: num(stats, ['totalLosses']) },
    { label: '总击杀', value: totalKills },
    { label: '总死亡', value: totalDeaths },
    { label: '误伤次数', value: num(stats, ['totalTeamKills']) },
    { label: '恋人模式胜场', value: num(stats, ['totalLoversWins']) },
    { label: '累计在线时长(秒)', value: num(stats, ['totalPlayTime']) },
    { label: '胜率', value: winRate + '%' },
    { label: 'K/D', value: kd },
  ];

  const factionRows = [
    { faction: lang.t('sre.pass.faction.civilian', '平民阵营卡'), games: num(stats, ['totalCivilianGames']), wins: num(stats, ['totalCivilianWins']), kills: num(stats, ['totalCivilianKills']), deaths: num(stats, ['totalCivilianDeaths']) },
    { faction: lang.t('sre.pass.faction.killer', '杀手阵营卡'), games: num(stats, ['totalKillerGames']), wins: num(stats, ['totalKillerWins']), kills: num(stats, ['totalKillerKills']), deaths: num(stats, ['totalKillerDeaths']) },
    { faction: lang.t('sre.pass.faction.neutral', '中立阵营卡'), games: num(stats, ['totalNeutralGames']), wins: num(stats, ['totalNeutralWins']), kills: num(stats, ['totalNeutralKills']), deaths: num(stats, ['totalNeutralDeaths']) },
    { faction: lang.t('announcement.star.role.vigilante', '义警'), games: num(stats, ['totalSheriffGames']), wins: num(stats, ['totalSheriffWins']), kills: num(stats, ['totalSheriffKills']), deaths: num(stats, ['totalSheriffDeaths']) },
  ];

  const progressRows = [
    { label: '当前等级', value: num(progression, ['level', 'lv'], 1) },
    { label: '当前经验', value: num(progression, ['experience', 'xp']) },
    { label: '累计经验', value: num(progression, ['totalExperience', 'txp']) },
    { label: '已领取金币奖励', value: num(progression, ['claimedCoinRewards', 'ccr']) },
    { label: '已领取抽卡奖励', value: num(progression, ['claimedLootRewards', 'clr']) },
  ];

  const factionCards = [];
  const cardRaw = progression.factionCards || progression.fc || {};
  if (cardRaw && typeof cardRaw === 'object') {
    const cardNameMap = {
      killer: lang.t('sre.pass.faction.killer', '杀手阵营卡'),
      civilian: lang.t('sre.pass.faction.civilian', '平民阵营卡'),
      vigilante: lang.t('announcement.star.role.vigilante', '义警'),
      neutral: lang.t('sre.pass.faction.neutral', '中立阵营卡'),
      neutral_for_killer: lang.t('sre.pass.faction.neutral_for_killer', '杀手中立阵营卡'),
    };
    for (const [key, count] of Object.entries(cardRaw)) {
      if (!isNumeric(count)) continue;
      const keyStr = String(key).toLowerCase();
      factionCards.push({ name: cardNameMap[keyStr] || keyStr, count: toInt(count) });
    }
  }

  const equipped = (skins.equipped && typeof skins.equipped === 'object') ? skins.equipped : {};
  const unlocked = (skins.unlocked && typeof skins.unlocked === 'object') ? skins.unlocked : {};
  let unlockedCount = 0;
  for (const group of Object.values(unlocked)) {
    if (group && typeof group === 'object') {
      for (const flag of Object.values(group)) if (flag === true) unlockedCount += 1;
    }
  }
  const skinRows = [
    { label: '金币', value: num(skins, ['coinNum']) },
    { label: '抽奖机会', value: num(skins, ['lootChance']) },
    { label: '已装备皮肤数量', value: Object.keys(equipped).length },
    { label: '已解锁皮肤数量', value: unlockedCount },
  ];

  let mailTotal = 0; let mailUnread = 0; let mailClaimable = 0; let mailExpired = 0;
  for (const mail of mailbox) {
    if (!mail || typeof mail !== 'object') continue;
    mailTotal += 1;
    const read = mail.read === true;
    const claimed = mail.claimed === true;
    const expiresAt = isNumeric(mail.expiresAt) ? toInt(mail.expiresAt) : 0;
    const expired = expiresAt > 0 && expiresAt < nowMs();
    if (!read) mailUnread += 1;
    if (!claimed && !expired) mailClaimable += 1;
    if (expired) mailExpired += 1;
  }
  const mailRows = [
    { label: '总邮件数', value: mailTotal },
    { label: '未读邮件', value: mailUnread },
    { label: '可领取邮件', value: mailClaimable },
    { label: '已过期邮件', value: mailExpired },
  ];

  const roleRows = [];
  const roleStats = (stats.roleStats && typeof stats.roleStats === 'object') ? stats.roleStats : {};
  for (const [roleId, row] of Object.entries(roleStats)) {
    if (!row || typeof row !== 'object') continue;
    roleRows.push({
      role: typeof roleId === 'string' ? roleId : 'unknown',
      timesPlayed: isNumeric(row.timesPlayed) ? toInt(row.timesPlayed) : 0,
      winsAsRole: isNumeric(row.winsAsRole) ? toInt(row.winsAsRole) : 0,
      killsAsRole: isNumeric(row.killsAsRole) ? toInt(row.killsAsRole) : 0,
      deathsAsRole: isNumeric(row.deathsAsRole) ? toInt(row.deathsAsRole) : 0,
    });
  }
  roleRows.sort((a, b) => b.timesPlayed - a.timesPlayed);

  res.json({
    ok: true,
    player: { identifier: resolved.identifier || identifier, username: resolved.player_name || identifier, uuid: resolved.player_uuid || null, source: resolved.source || 'unknown' },
    dashboard: { statsRows, factionRows, progressRows, factionCards, skinRows, mailRows, roleRows: roleRows.slice(0, 30) },
  });
}));

// ============ 以下需要登录 ============
router.use(auth.requireAuth);

// 完整档案（可编辑视图）
router.get('/player', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = String(req.query.identifier || '').trim();
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });
  const resolved = await resolvePlayer(identifier);
  let recs = {};
  if (resolved.player_uuid) recs = await records.fetchPlayerRecords(resolved.player_uuid);

  const t = adminTableNames();
  let pendingRows;
  if (resolved.player_uuid) {
    pendingRows = await db.query(
      `SELECT * FROM ${t.pendingOps} WHERE player_name = :name OR player_uuid = :uuid ORDER BY created_at DESC LIMIT 50`,
      { name: resolved.player_name, uuid: resolved.player_uuid },
    );
  } else {
    pendingRows = await db.query(
      `SELECT * FROM ${t.pendingOps} WHERE player_name = :name ORDER BY created_at DESC LIMIT 50`,
      { name: resolved.player_name },
    );
  }

  return res.json({ ok: true, profile: { player: resolved, records: recs, pending: pendingRows } });
}));

// 系统概况
router.get('/status', wrap(async (req, res) => {
  await ensureAdminSchema();
  const table = tableName();
  const t = adminTableNames();
  const total = toInt(await db.queryScalar(`SELECT COUNT(*) AS c FROM ${table}`));
  const latest = await db.queryScalar(`SELECT MAX(updated_at) AS m FROM ${table}`);
  const groups = await db.query(`SELECT data_key, COUNT(*) AS record_count FROM ${table} GROUP BY data_key ORDER BY record_count DESC, data_key ASC`);
  const usercache = loadUsercache();
  const pendingCount = toInt(await db.queryScalar(`SELECT COUNT(*) AS c FROM ${t.pendingOps} WHERE status = 'pending'`));
  const globalMailCount = toInt(await db.queryScalar(`SELECT COUNT(*) AS c FROM ${t.globalMail} WHERE active = 1`));
  res.json({
    ok: true,
    summary: {
      total_records: total,
      latest_updated_at: latest != null ? toInt(latest) : null,
      groups,
      usercache_count: usercache.entries.length,
      pending_count: pendingCount,
      global_mail_count: globalMailCount,
    },
  });
}));

router.get('/recent-players', wrap(async (req, res) => {
  const limit = Math.max(1, Math.min(toInt(loadConfig().recentPlayerLimit, 30), 100));
  res.json({ ok: true, players: await recentPlayers(limit) });
}));

router.get('/pending-ops', wrap(async (req, res) => {
  res.json({ ok: true, pending: await pending.listPendingOps(100) });
}));

router.get('/global-mails', wrap(async (req, res) => {
  res.json({ ok: true, global_mails: await pending.listGlobalMails(50) });
}));

// 聚合最近玩家的皮肤目录: item -> [skins]
router.get('/skin-catalog', wrap(async (req, res) => {
  await ensureSyncSchema();
  const rows = await db.query(
    `SELECT payload_json FROM ${tableName()} WHERE data_key = 'skins' ORDER BY updated_at DESC LIMIT 300`,
  );
  const catalog = {};
  const add = (item, skin) => {
    if (!item || !skin) return;
    if (!catalog[item]) catalog[item] = new Set();
    catalog[item].add(skin);
  };
  for (const row of rows) {
    let p;
    try { p = JSON.parse(row.payload_json); } catch (_) { continue; }
    if (!p || typeof p !== 'object') continue;
    if (p.equipped && typeof p.equipped === 'object') {
      for (const [item, skin] of Object.entries(p.equipped)) if (typeof skin === 'string') add(item, skin);
    }
    if (p.unlocked && typeof p.unlocked === 'object') {
      for (const [item, group] of Object.entries(p.unlocked)) {
        if (group && typeof group === 'object') for (const skin of Object.keys(group)) add(item, skin);
      }
    }
  }
  const out = {};
  for (const [item, set] of Object.entries(catalog)) out[item] = Array.from(set).sort();
  res.json({ ok: true, catalog: out });
}));

// ---------- 保存类端点 ----------

router.post('/save-stats', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });
  const patch = {};
  for (const field of merge.STATS_NUMERIC_FIELDS) patch[field] = bodyInt(req, field, 0);
  patch.roleStats = bodyObject(req, 'roleStats');
  const resolved = await resolvePlayer(identifier);
  return res.json(await twoTierMerge(resolved, 'stats', 'stats_merge', patch, merge.mergeStatsPayload, 'Queued stats update'));
}));

router.post('/save-progression', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });
  const patch = {
    level: bodyInt(req, 'level', 1),
    experience: bodyInt(req, 'experience', 0),
    totalExperience: bodyInt(req, 'totalExperience', 0),
    claimedCoinRewards: bodyInt(req, 'claimedCoinRewards', 0),
    claimedLootRewards: bodyInt(req, 'claimedLootRewards', 0),
    lastQuestRefreshTime: bodyInt(req, 'lastQuestRefreshTime', 0),
    lastWeeklyRefreshTime: bodyInt(req, 'lastWeeklyRefreshTime', 0),
  };
  // 仅当显式传入非空 factionCards 时才替换，避免空对象清空已有阵营卡
  const fc = bodyObject(req, 'factionCards');
  if (Object.keys(fc).length > 0) patch.factionCards = fc;
  const resolved = await resolvePlayer(identifier);
  return res.json(await twoTierMerge(resolved, 'progression', 'progression_merge', patch, merge.mergeProgressionPayload, 'Queued progression update'));
}));

router.post('/save-skins', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });
  const patch = {
    coinNum: bodyInt(req, 'coinNum', 0),
    lootChance: bodyInt(req, 'lootChance', 0),
  };
  // 仅当显式传入非空映射时才替换，避免空对象清空已装备/已解锁皮肤
  const eq = bodyObject(req, 'equipped');
  const un = bodyObject(req, 'unlocked');
  if (Object.keys(eq).length > 0) patch.equipped = eq;
  if (Object.keys(un).length > 0) patch.unlocked = un;
  const resolved = await resolvePlayer(identifier);
  return res.json(await twoTierMerge(resolved, 'skins', 'skins_merge', patch, merge.mergeSkinsPayload, 'Queued skin update'));
}));

router.post('/save-nametags', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });
  const patch = { nameTags: bodyArray(req, 'nameTags'), currentNametag: bodyString(req, 'currentNametag') };
  const resolved = await resolvePlayer(identifier);
  return res.json(await twoTierMerge(resolved, 'nametags', 'nametags_merge', patch, merge.mergeNametagsPayload, 'Queued nametag update'));
}));

router.post('/save-mailbox', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });
  const mails = bodyArray(req, 'mails');
  const resolved = await resolvePlayer(identifier);
  if (resolved.player_uuid) {
    const merged = merge.replaceMailboxPayload(mails);
    await records.upsertSyncRecord(resolved.player_uuid, 'mailbox', merged);
    return res.json({ ok: true, mode: 'applied', player: resolved });
  }
  const pendingId = await pending.queuePendingOp(resolved.player_name, null, 'mailbox', 'mailbox_replace', { mails }, 'Queued mailbox replacement');
  return res.json({ ok: true, mode: 'queued', pending_id: pendingId, player: resolved });
}));

router.post('/save-raw', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  const dataKey = bodyString(req, 'data_key');
  const rawPayload = bodyString(req, 'raw_payload');
  if (identifier === '' || dataKey === '' || rawPayload === '') {
    return res.status(422).json({ ok: false, message: 'identifier, data_key and raw_payload are required.' });
  }
  try { JSON.parse(rawPayload); } catch (_) { return res.status(422).json({ ok: false, message: 'raw_payload must be valid JSON.' }); }
  const resolved = await resolvePlayer(identifier);
  if (resolved.player_uuid) {
    await records.upsertSyncRecord(resolved.player_uuid, dataKey, rawPayload);
    return res.json({ ok: true, mode: 'applied', player: resolved });
  }
  const pendingId = await pending.queuePendingOp(resolved.player_name, null, dataKey, 'raw_replace', { raw_payload: rawPayload }, 'Queued by raw editor');
  return res.json({ ok: true, mode: 'queued', pending_id: pendingId, player: resolved });
}));

router.post('/grant-faction-card', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  const faction = bodyString(req, 'faction').toLowerCase();
  const amount = Math.max(1, bodyInt(req, 'amount', 1));
  if (identifier === '' || faction === '') return res.status(422).json({ ok: false, message: 'identifier and faction are required.' });
  const allowed = ['killer', 'civilian', 'vigilante', 'neutral', 'neutral_for_killer'];
  if (!allowed.includes(faction)) return res.status(422).json({ ok: false, message: 'invalid faction.' });
  const resolved = await resolvePlayer(identifier);
  const patch = { factionCardsDelta: { [faction]: amount } };
  return res.json(await twoTierMerge(resolved, 'progression', 'progression_merge', patch, merge.mergeProgressionPayload, 'Queued faction card grant'));
}));

// 快捷阵营卡增减（支持负数；只动 factionCards，不影响等级/经验）
router.post('/quick/faction-card', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  const faction = bodyString(req, 'faction').toLowerCase();
  const delta = bodyInt(req, 'delta', 0);
  if (identifier === '' || faction === '') return res.status(422).json({ ok: false, message: 'identifier and faction are required.' });
  const allowed = ['killer', 'civilian', 'vigilante', 'neutral', 'neutral_for_killer'];
  if (!allowed.includes(faction)) return res.status(422).json({ ok: false, message: 'invalid faction.' });
  if (delta === 0) return res.status(422).json({ ok: false, message: 'delta must be non-zero.' });
  const resolved = await resolvePlayer(identifier);
  const patch = { factionCardsDelta: { [faction]: delta } };
  return res.json(await twoTierMerge(resolved, 'progression', 'progression_merge', patch, merge.mergeProgressionPayload, 'Queued faction card delta'));
}));

router.post('/send-mail', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifierRaw = bodyString(req, 'recipient', bodyString(req, 'identifier'));
  const title = bodyString(req, 'title');
  if (identifierRaw === '' || title === '') return res.status(422).json({ ok: false, message: 'identifier and title are required.' });
  const recipients = splitIdentifiers(identifierRaw);
  if (recipients.length === 0) return res.status(422).json({ ok: false, message: 'No valid recipients found.' });

  const results = [];
  let applied = 0; let queued = 0;
  for (const recipient of recipients) {
    const mail = merge.buildMailPayload(
      bodyString(req, 'sender', 'System'), title, bodyString(req, 'content'), bodyArray(req, 'commands'), bodyInt(req, 'expiresAt', 0),
    );
    // eslint-disable-next-line no-await-in-loop
    const resolved = await resolvePlayer(recipient);
    if (resolved.player_uuid) {
      // eslint-disable-next-line no-await-in-loop
      const existing = await records.fetchSyncRecord(resolved.player_uuid, 'mailbox');
      const merged = merge.mergeMailboxPayload(existing ? existing.payload_json : null, [mail]);
      // eslint-disable-next-line no-await-in-loop
      await records.upsertSyncRecord(resolved.player_uuid, 'mailbox', merged);
      results.push({ recipient, mode: 'applied', player: resolved, mail });
      applied += 1;
    } else {
      // eslint-disable-next-line no-await-in-loop
      const pendingId = await pending.queuePendingOp(resolved.player_name, null, 'mailbox', 'mailbox_append', { mails: [mail] }, 'Queued mailbox delivery');
      results.push({ recipient, mode: 'queued', pending_id: pendingId, player: resolved, mail });
      queued += 1;
    }
  }
  const single = recipients.length === 1;
  res.json({
    ok: true,
    mode: single ? results[0].mode : 'batch',
    player: single ? (results[0].player || null) : null,
    mail: single ? (results[0].mail || null) : null,
    pending_id: single ? (results[0].pending_id || null) : null,
    results,
    summary: { total: recipients.length, applied, queued },
  });
}));

router.post('/create-global-mail', wrap(async (req, res) => {
  await ensureAdminSchema();
  const title = bodyString(req, 'title');
  if (title === '') return res.status(422).json({ ok: false, message: 'title is required.' });
  const mail = merge.buildMailPayload(
    bodyString(req, 'sender', 'System'), title, bodyString(req, 'content'), bodyArray(req, 'commands'), bodyInt(req, 'expiresAt', 0),
  );
  await pending.insertGlobalMail(mail);
  res.json({ ok: true, mail });
}));

router.post('/reconcile', wrap(async (req, res) => {
  await ensureAdminSchema();
  res.json({ ok: true, reconcile: await pending.runReconcile() });
}));

router.post('/refresh-index', wrap(async (req, res) => {
  res.json({ ok: true, refresh: await refreshPlayerIndex() });
}));

// ---------- 快捷操作端点 ----------

// 金币/抽奖：支持增量(delta)或直接设值(set)
router.post('/quick/adjust-coins', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  if (identifier === '') return res.status(422).json({ ok: false, message: 'identifier is required.' });
  const resolved = await resolvePlayer(identifier);

  // 读取现值（仅命中 UUID 时；未命中按 0 基准）
  let current = { coinNum: 0, lootChance: 0 };
  if (resolved.player_uuid) {
    const existing = await records.fetchSyncRecord(resolved.player_uuid, 'skins');
    const p = existing ? merge_safeParse(existing.payload_json) : null;
    if (p) {
      if (isNumeric(p.coinNum)) current.coinNum = toInt(p.coinNum);
      if (isNumeric(p.lootChance)) current.lootChance = toInt(p.lootChance);
    }
  }

  const patch = {};
  if (req.body && req.body.setCoins !== undefined && req.body.setCoins !== null && req.body.setCoins !== '') {
    patch.coinNum = Math.max(0, bodyInt(req, 'setCoins', current.coinNum));
  } else if (req.body && isNumeric(req.body.deltaCoins)) {
    patch.coinNum = Math.max(0, current.coinNum + bodyInt(req, 'deltaCoins', 0));
  }
  if (req.body && req.body.setLoot !== undefined && req.body.setLoot !== null && req.body.setLoot !== '') {
    patch.lootChance = Math.max(0, bodyInt(req, 'setLoot', current.lootChance));
  } else if (req.body && isNumeric(req.body.deltaLoot)) {
    patch.lootChance = Math.max(0, current.lootChance + bodyInt(req, 'deltaLoot', 0));
  }
  if (Object.keys(patch).length === 0) return res.status(422).json({ ok: false, message: '需要 deltaCoins/setCoins/deltaLoot/setLoot 之一。' });

  const out = await twoTierMerge(resolved, 'skins', 'skins_merge', patch, merge.mergeSkinsPayload, 'Queued coin adjust');
  out.patch = patch;
  return res.json(out);
}));

// 称号一键增删/设置/清空
router.post('/quick/nametag', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  const action = bodyString(req, 'action');
  const value = typeof req.body?.value === 'string' ? req.body.value : '';
  if (identifier === '' || action === '') return res.status(422).json({ ok: false, message: 'identifier and action are required.' });
  const resolved = await resolvePlayer(identifier);

  // 读取现有称号
  let nameTags = [];
  let currentNametag = '';
  if (resolved.player_uuid) {
    const existing = await records.fetchSyncRecord(resolved.player_uuid, 'nametags');
    const p = existing ? merge_safeParse(existing.payload_json) : null;
    if (p) {
      if (Array.isArray(p.nameTags)) nameTags = p.nameTags.filter((x) => typeof x === 'string');
      currentNametag = typeof p.currentNametag === 'string' ? p.currentNametag
        : (typeof p.CurrentNameTag === 'string' ? p.CurrentNameTag : '');
    }
  }

  const tag = value.trim();
  switch (action) {
    case 'add':
      if (tag === '') return res.status(422).json({ ok: false, message: 'value is required.' });
      if (!nameTags.includes(tag)) nameTags.push(tag);
      break;
    case 'addSet':
      if (tag === '') return res.status(422).json({ ok: false, message: 'value is required.' });
      if (!nameTags.includes(tag)) nameTags.push(tag);
      currentNametag = tag;
      break;
    case 'remove':
      nameTags = nameTags.filter((x) => x !== tag);
      if (currentNametag === tag) currentNametag = '';
      break;
    case 'set':
      if (tag !== '' && !nameTags.includes(tag)) nameTags.push(tag);
      currentNametag = tag;
      break;
    case 'clear':
      nameTags = [];
      currentNametag = '';
      break;
    default:
      return res.status(422).json({ ok: false, message: 'invalid action.' });
  }

  const patch = { nameTags, currentNametag };
  const out = await twoTierMerge(resolved, 'nametags', 'nametags_merge', patch, merge.mergeNametagsPayload, 'Queued nametag quick op');
  out.nameTags = nameTags;
  out.currentNametag = currentNametag;
  return res.json(out);
}));

// 皮肤解锁/装备/上锁/全解锁
router.post('/quick/skin', wrap(async (req, res) => {
  await ensureAdminSchema();
  const identifier = bodyString(req, 'identifier');
  const action = bodyString(req, 'action');
  const item = bodyString(req, 'item');
  const skin = bodyString(req, 'skin');
  if (identifier === '' || action === '') return res.status(422).json({ ok: false, message: 'identifier and action are required.' });
  const resolved = await resolvePlayer(identifier);

  let equipped = {};
  let unlocked = {};
  if (resolved.player_uuid) {
    const existing = await records.fetchSyncRecord(resolved.player_uuid, 'skins');
    const p = existing ? merge_safeParse(existing.payload_json) : null;
    if (p) {
      if (p.equipped && typeof p.equipped === 'object') equipped = p.equipped;
      if (p.unlocked && typeof p.unlocked === 'object') unlocked = p.unlocked;
    }
  }
  const ensureItem = (it) => { if (!unlocked[it] || typeof unlocked[it] !== 'object') unlocked[it] = {}; };

  switch (action) {
    case 'unlock':
      if (item === '' || skin === '') return res.status(422).json({ ok: false, message: 'item and skin are required.' });
      ensureItem(item); unlocked[item][skin] = true;
      break;
    case 'lock':
      if (item === '' || skin === '') return res.status(422).json({ ok: false, message: 'item and skin are required.' });
      if (unlocked[item] && typeof unlocked[item] === 'object') delete unlocked[item][skin];
      if (equipped[item] === skin) delete equipped[item];
      break;
    case 'equip':
      if (item === '' || skin === '') return res.status(422).json({ ok: false, message: 'item and skin are required.' });
      ensureItem(item); unlocked[item][skin] = true; equipped[item] = skin;
      break;
    case 'unlockAll': {
      // 用聚合目录解锁全部已知皮肤；item 为空则对所有物品类型
      const rows = await db.query(`SELECT payload_json FROM ${tableName()} WHERE data_key = 'skins' ORDER BY updated_at DESC LIMIT 300`);
      const catalog = {};
      const add = (it, sk) => { if (!it || !sk) return; if (!catalog[it]) catalog[it] = new Set(); catalog[it].add(sk); };
      for (const row of rows) {
        const pp = merge_safeParse(row.payload_json);
        if (!pp) continue;
        if (pp.equipped && typeof pp.equipped === 'object') for (const [it, sk] of Object.entries(pp.equipped)) if (typeof sk === 'string') add(it, sk);
        if (pp.unlocked && typeof pp.unlocked === 'object') for (const [it, g] of Object.entries(pp.unlocked)) if (g && typeof g === 'object') for (const sk of Object.keys(g)) add(it, sk);
      }
      // 也并入该玩家已有的键
      for (const [it, g] of Object.entries(unlocked)) if (g && typeof g === 'object') for (const sk of Object.keys(g)) add(it, sk);
      const items = item !== '' ? [item] : Object.keys(catalog);
      for (const it of items) {
        ensureItem(it);
        for (const sk of (catalog[it] || [])) unlocked[it][sk] = true;
      }
      break;
    }
    default:
      return res.status(422).json({ ok: false, message: 'invalid action.' });
  }

  const patch = { equipped, unlocked };
  const out = await twoTierMerge(resolved, 'skins', 'skins_merge', patch, merge.mergeSkinsPayload, 'Queued skin quick op');
  out.equipped = equipped;
  out.unlocked = unlocked;
  return res.json(out);
}));

// 局部 JSON 安全解析
function merge_safeParse(str) {
  try { const v = JSON.parse(str); return (v && typeof v === 'object') ? v : null; } catch (_) { return null; }
}

module.exports = router;
