'use strict';

/* ============ 基础工具 ============ */
const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

function toast(msg, type = '') {
  const el = document.createElement('div');
  el.className = 'toast' + (type ? ' ' + type : '');
  el.textContent = msg;
  $('#toasts').appendChild(el);
  setTimeout(() => { el.style.opacity = '0'; setTimeout(() => el.remove(), 250); }, 2600);
}

async function api(path, method = 'GET', body = null) {
  const opts = { method, headers: {}, credentials: 'same-origin' };
  if (body != null) { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
  const res = await fetch('/api' + path, opts);
  let data = {};
  try { data = await res.json(); } catch (_) { /* ignore */ }
  if (!res.ok || data.ok === false) {
    const msg = (data && data.message) ? data.message : ('请求失败 (' + res.status + ')');
    const err = new Error(msg); err.status = res.status; err.data = data;
    throw err;
  }
  return data;
}

function fmtTime(ms) {
  if (!ms) return '—';
  const d = new Date(Number(ms));
  if (Number.isNaN(d.getTime())) return '—';
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function modeToast(resp, appliedMsg) {
  if (resp.mode === 'queued') toast('已排队，等待该玩家上线后生效', 'warn');
  else if (resp.mode === 'batch') toast(`批量完成：应用 ${resp.summary.applied} / 排队 ${resp.summary.queued}`, 'good');
  else toast(appliedMsg || '已应用', 'good');
}

/* ============ 全局状态 ============ */
const state = { me: null, identifier: '', profile: null, catalog: {} };

/* ============ 初始化 ============ */
async function init() {
  try {
    const me = await api('/me');
    state.me = me;
    if (me.siteTitle) { $('#brand').textContent = me.siteTitle; $('#login-title').textContent = me.siteTitle; document.title = me.siteTitle; }
    if (me.authenticated) showApp(); else showLogin();
  } catch (_) { showLogin(); }
}

function showLogin() {
  $('#login-view').classList.remove('hidden');
  $('#app-view').classList.add('hidden');
}

function showApp() {
  $('#login-view').classList.add('hidden');
  $('#app-view').classList.remove('hidden');
  $('#who').textContent = state.me && state.me.username ? state.me.username : '';
  loadRecent();
}

/* ============ 登录 ============ */
$('#login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    const r = await api('/login', 'POST', { username: $('#login-user').value, password: $('#login-pass').value });
    state.me.authenticated = true; state.me.username = r.username;
    toast('登录成功', 'good');
    showApp();
  } catch (err) { toast(err.message, 'bad'); }
});

$('#logout-btn').addEventListener('click', async () => {
  try { await api('/logout', 'POST'); } catch (_) { /* ignore */ }
  state.me.authenticated = false;
  showLogin();
});

/* ============ Tab 切换 ============ */
$$('.tabs button').forEach((btn) => btn.addEventListener('click', () => {
  $$('.tabs button').forEach((b) => b.classList.remove('active'));
  btn.classList.add('active');
  const tab = btn.dataset.tab;
  $('#tab-player').classList.toggle('hidden', tab !== 'player');
  $('#tab-admin').classList.toggle('hidden', tab !== 'admin');
  if (tab === 'admin') loadAdmin();
}));

/* ============ 玩家工作台 ============ */
$('#search-btn').addEventListener('click', () => loadPlayer($('#search-input').value.trim()));
$('#search-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadPlayer($('#search-input').value.trim()); });

async function loadRecent() {
  try {
    const r = await api('/recent-players');
    const box = $('#recent-chips');
    box.innerHTML = '';
    (r.players || []).forEach((p) => {
      const c = document.createElement('button');
      c.className = 'chip';
      c.textContent = p.name;
      c.title = p.uuid + ' · ' + (p.source || '');
      c.addEventListener('click', () => { $('#search-input').value = p.name; loadPlayer(p.name); });
      box.appendChild(c);
    });
  } catch (_) { /* ignore */ }
}

async function loadPlayer(identifier) {
  if (!identifier) { toast('请输入玩家名或 UUID', 'warn'); return; }
  state.identifier = identifier;
  const content = $('#player-content');
  content.innerHTML = '<p class="muted"><span class="spinner"></span> 加载中…</p>';
  try {
    const [profile, cat] = await Promise.all([
      api('/player?identifier=' + encodeURIComponent(identifier)),
      api('/skin-catalog').catch(() => ({ catalog: {} })),
    ]);
    state.profile = profile.profile;
    state.catalog = cat.catalog || {};
    renderPlayer();
  } catch (err) {
    content.innerHTML = `<div class="card"><div class="card-body"><p class="muted">${esc(err.message)}</p></div></div>`;
  }
}

function reload() { return loadPlayer(state.identifier); }

function rec(key) {
  const r = state.profile.records || {};
  return r[key] || null;
}
function payload(key) {
  const r = rec(key);
  return (r && r.payload && typeof r.payload === 'object') ? r.payload : {};
}

/* ---- 渲染玩家页 ---- */
function renderPlayer() {
  const p = state.profile.player;
  const content = $('#player-content');
  const grid = document.createElement('div');
  grid.className = 'grid';

  grid.appendChild(cardOverview(p));
  grid.appendChild(cardCoins());
  grid.appendChild(cardNametag());
  grid.appendChild(cardSkins());
  grid.appendChild(cardProgression());
  grid.appendChild(cardStats());
  grid.appendChild(cardMailbox());
  grid.appendChild(cardRaw());

  content.innerHTML = '';
  if (!p.player_uuid) {
    const warn = document.createElement('div');
    warn.className = 'card span-2';
    warn.innerHTML = `<div class="card-body"><span class="badge" style="background:var(--warn-bg);color:var(--warn)">未解析</span>
      该玩家暂未解析到 UUID（不在 usercache / 索引 / sync 表中）。所有修改将<b>排队</b>，等其上线后由“管理面板 → 立即对账”应用。</div>`;
    content.appendChild(warn);
  }
  content.appendChild(grid);
}

function cardShell(title, meta) {
  const card = document.createElement('div');
  card.className = 'card';
  card.innerHTML = `<div class="card-head">${esc(title)}${meta ? `<span class="meta">${esc(meta)}</span>` : ''}</div><div class="card-body"></div>`;
  return card;
}
function metaFor(key) {
  const r = rec(key);
  if (!r) return '无记录';
  return `v${r.record_version} · ${fmtTime(r.updated_at)}`;
}

/* ---- 概览 ---- */
function cardOverview(p) {
  const card = cardShell('概览', p.source || '');
  const body = $('.card-body', card);
  const keys = Object.keys(state.profile.records || {});
  body.innerHTML = `
    <div class="kv">
      <div class="k">玩家名</div><div>${esc(p.player_name)}</div>
      <div class="k">UUID</div><div style="word-break:break-all">${esc(p.player_uuid || '未解析')}</div>
      <div class="k">解析来源</div><div>${esc(p.source)}</div>
      <div class="k">数据键</div><div>${keys.length ? keys.map((k) => esc(k)).join(', ') : '无'}</div>
    </div>`;
  return card;
}

/* ---- 金币 / 抽奖（快捷） ---- */
function cardCoins() {
  const card = cardShell('金币 / 抽奖', metaFor('skins'));
  const body = $('.card-body', card);
  const s = payload('skins');
  const coin = Number(s.coinNum || 0);
  const loot = Number(s.lootChance || 0);
  body.innerHTML = `
    <div class="bignum">
      <div class="stat"><div class="k">金币</div><div class="v" id="coin-v">${coin}</div></div>
      <div class="stat"><div class="k">抽奖次数</div><div class="v" id="loot-v">${loot}</div></div>
    </div>
    <div class="section-title">金币</div>
    <div class="btn-row" data-grp="coin">
      <button class="sm" data-d="-100">-100</button>
      <button class="sm" data-d="-10">-10</button>
      <button class="sm good" data-d="10">+10</button>
      <button class="sm good" data-d="100">+100</button>
      <button class="sm good" data-d="1000">+1000</button>
    </div>
    <div class="inline"><input type="number" id="coin-set" placeholder="设为指定值" /><button class="sm primary" id="coin-set-btn">设值</button></div>
    <hr class="sep" />
    <div class="section-title">抽奖次数</div>
    <div class="btn-row" data-grp="loot">
      <button class="sm" data-d="-1">-1</button>
      <button class="sm good" data-d="1">+1</button>
      <button class="sm good" data-d="10">+10</button>
    </div>
    <div class="inline"><input type="number" id="loot-set" placeholder="设为指定值" /><button class="sm primary" id="loot-set-btn">设值</button></div>`;

  const adjust = async (b) => {
    try { const r = await api('/quick/adjust-coins', 'POST', b); modeToast(r, '已调整'); await reload(); }
    catch (e) { toast(e.message, 'bad'); }
  };
  $$('[data-grp="coin"] button', body).forEach((btn) => btn.addEventListener('click', () => adjust({ identifier: state.identifier, deltaCoins: Number(btn.dataset.d) })));
  $$('[data-grp="loot"] button', body).forEach((btn) => btn.addEventListener('click', () => adjust({ identifier: state.identifier, deltaLoot: Number(btn.dataset.d) })));
  $('#coin-set-btn', body).addEventListener('click', () => { const v = $('#coin-set', body).value; if (v === '') return; adjust({ identifier: state.identifier, setCoins: Number(v) }); });
  $('#loot-set-btn', body).addEventListener('click', () => { const v = $('#loot-set', body).value; if (v === '') return; adjust({ identifier: state.identifier, setLoot: Number(v) }); });
  return card;
}

/* ---- 称号（快捷） ---- */
const COLOR_CODES = [
  ['0', '#000'], ['1', '#00a'], ['2', '#0a0'], ['3', '#0aa'], ['4', '#a00'], ['5', '#a0a'],
  ['6', '#fa0'], ['7', '#aaa'], ['8', '#555'], ['9', '#55f'], ['a', '#5f5'], ['b', '#5ff'],
  ['c', '#f55'], ['d', '#f5f'], ['e', '#ff5'], ['f', '#fff'],
];
function cardNametag() {
  const card = cardShell('称号', metaFor('nametags'));
  const body = $('.card-body', card);
  const p = payload('nametags');
  const tags = Array.isArray(p.nameTags) ? p.nameTags : [];
  const current = p.currentNametag || p.CurrentNameTag || '';

  const list = tags.length ? tags.map((t) => `
    <div class="row-item ${t === current ? 'active' : ''}">
      <span class="label">${esc(t)}</span>
      ${t === current ? '<span class="badge">当前</span>' : `<button class="sm" data-act="set" data-v="${esc(t)}">设为当前</button>`}
      <button class="sm bad" data-act="remove" data-v="${esc(t)}">移除</button>
    </div>`).join('') : '<p class="muted">暂无称号</p>';

  const codes = COLOR_CODES.map(([c, col]) => `<button data-code="§${c}" style="color:${col};background:var(--bg-input)" title="§${c}">${c}</button>`).join('');

  body.innerHTML = `
    <div class="list" id="tag-list">${list}</div>
    <hr class="sep" />
    <label>新称号文本（支持 § 颜色码）</label>
    <input id="tag-input" placeholder="例如：§6传奇车长" />
    <div class="color-codes">${codes}<button data-code="§l" title="粗体" style="font-weight:bold">L</button><button data-code="§r" title="重置">R</button></div>
    <div class="btn-row">
      <button class="sm primary" data-act="addSet">添加并设为当前</button>
      <button class="sm" data-act="add">仅添加</button>
      <button class="sm bad ghost" data-act="clear">清空全部</button>
    </div>`;

  const call = async (action, value) => {
    try { const r = await api('/quick/nametag', 'POST', { identifier: state.identifier, action, value }); modeToast(r, '称号已更新'); await reload(); }
    catch (e) { toast(e.message, 'bad'); }
  };
  $$('#tag-list [data-act]', body).forEach((btn) => btn.addEventListener('click', () => call(btn.dataset.act, btn.dataset.v)));
  $$('.color-codes button', body).forEach((btn) => btn.addEventListener('click', () => { const inp = $('#tag-input', body); inp.value += btn.dataset.code; inp.focus(); }));
  $('[data-act="add"]', body).addEventListener('click', () => { const v = $('#tag-input', body).value.trim(); if (!v) return toast('请输入称号', 'warn'); call('add', v); });
  $('[data-act="addSet"]', body).addEventListener('click', () => { const v = $('#tag-input', body).value.trim(); if (!v) return toast('请输入称号', 'warn'); call('addSet', v); });
  $('[data-act="clear"]', body).addEventListener('click', () => { if (confirm('确认清空该玩家全部称号？')) call('clear', ''); });
  return card;
}

/* ---- 皮肤（快捷） ---- */
function cardSkins() {
  const card = cardShell('皮肤', metaFor('skins'));
  const body = $('.card-body', card);
  const s = payload('skins');
  const equipped = (s.equipped && typeof s.equipped === 'object') ? s.equipped : {};
  const unlocked = (s.unlocked && typeof s.unlocked === 'object') ? s.unlocked : {};

  // 合并物品类型：已解锁 + 已装备 + 目录
  const items = new Set([...Object.keys(unlocked), ...Object.keys(equipped), ...Object.keys(state.catalog)]);
  let html = '';
  if (items.size === 0) {
    html = '<p class="muted">暂无皮肤数据（数据库中没有任何已知皮肤）。</p>';
  } else {
    for (const item of Array.from(items).sort()) {
      const known = new Set([
        ...Object.keys(unlocked[item] || {}),
        ...(state.catalog[item] || []),
        ...(equipped[item] ? [equipped[item]] : []),
      ]);
      const opts = Array.from(known).sort().map((sk) => {
        const on = unlocked[item] && unlocked[item][sk] === true;
        const eq = equipped[item] === sk;
        return `<div class="row-item ${eq ? 'active' : ''}">
          <span class="label">${esc(sk)} ${on ? '<span class="badge">已解锁</span>' : '<span class="muted" style="font-size:11px">未解锁</span>'}</span>
          ${eq ? '<span class="badge">已装备</span>' : `<button class="sm" data-act="equip" data-item="${esc(item)}" data-skin="${esc(sk)}">装备</button>`}
          ${on ? `<button class="sm bad ghost" data-act="lock" data-item="${esc(item)}" data-skin="${esc(sk)}">上锁</button>`
              : `<button class="sm good" data-act="unlock" data-item="${esc(item)}" data-skin="${esc(sk)}">解锁</button>`}
        </div>`;
      }).join('');
      html += `<div class="section-title">${esc(item)} <button class="sm good ghost" data-act="unlockAll" data-item="${esc(item)}" style="float:right">全解锁</button></div>
        <div class="list" style="margin-bottom:10px">${opts || '<p class="muted">无</p>'}</div>`;
    }
    html += '<hr class="sep" /><button class="sm good" data-act="unlockAll" data-item="">一键全解锁（所有物品）</button>';
  }
  body.innerHTML = html;

  const call = async (action, item, skin) => {
    try { const r = await api('/quick/skin', 'POST', { identifier: state.identifier, action, item, skin }); modeToast(r, '皮肤已更新'); await reload(); }
    catch (e) { toast(e.message, 'bad'); }
  };
  $$('[data-act]', body).forEach((btn) => btn.addEventListener('click', () => call(btn.dataset.act, btn.dataset.item || '', btn.dataset.skin || '')));
  return card;
}

/* ---- 等级 / 经验 / 阵营卡 ---- */
const FACTIONS = [['killer', '杀手'], ['civilian', '平民'], ['vigilante', '义警'], ['neutral', '中立'], ['neutral_for_killer', '杀手中立']];
function cardProgression() {
  const card = cardShell('等级 / 经验 / 阵营卡', metaFor('progression'));
  const body = $('.card-body', card);
  const p = payload('progression');
  const numField = (key, label) => `<div class="field-num"><label>${label}</label><input type="number" data-pf="${key}" value="${Number(p[key] || 0)}" /></div>`;
  const fc = (p.factionCards && typeof p.factionCards === 'object') ? p.factionCards : {};

  const cardsHtml = FACTIONS.map(([k, name]) => {
    // 显示时兼容大写键
    const val = Number(fc[k] != null ? fc[k] : (fc[k.toUpperCase()] != null ? fc[k.toUpperCase()] : 0));
    return `<div class="row-item"><span class="label">${name} <span class="muted">(${k})</span></span>
      <button class="sm" data-fc="${k}" data-amt="-1">-1</button>
      <span style="min-width:28px;text-align:center">${val}</span>
      <button class="sm good" data-fc="${k}" data-amt="1">+1</button>
      <button class="sm good" data-fc="${k}" data-amt="5">+5</button></div>`;
  }).join('');

  body.innerHTML = `
    <div class="fields">
      ${numField('level', '等级')}
      ${numField('experience', '当前经验')}
      ${numField('totalExperience', '累计经验')}
      ${numField('claimedCoinRewards', '已领金币奖励')}
      ${numField('claimedLootRewards', '已领抽卡奖励')}
    </div>
    <div class="btn-row" style="margin-top:10px"><button class="sm primary" id="prog-save">保存等级/经验</button></div>
    <hr class="sep" />
    <div class="section-title">阵营卡（增减立即生效）</div>
    <div class="list">${cardsHtml}</div>`;

  $('#prog-save', body).addEventListener('click', async () => {
    const patch = { identifier: state.identifier };
    $$('[data-pf]', body).forEach((inp) => { patch[inp.dataset.pf] = Number(inp.value || 0); });
    try { const r = await api('/save-progression', 'POST', patch); modeToast(r, '已保存'); await reload(); }
    catch (e) { toast(e.message, 'bad'); }
  });
  $$('[data-fc]', body).forEach((btn) => btn.addEventListener('click', async () => {
    const delta = Number(btn.dataset.amt);
    try {
      const r = await api('/quick/faction-card', 'POST', { identifier: state.identifier, faction: btn.dataset.fc, delta });
      modeToast(r, '阵营卡已更新'); await reload();
    } catch (e) { toast(e.message, 'bad'); }
  }));
  return card;
}

/* ---- 战绩 ---- */
const STATS_FIELDS = [
  ['totalGamesPlayed', '总局数'], ['totalWins', '总胜'], ['totalLosses', '总败'], ['totalKills', '总杀'], ['totalDeaths', '总死'],
  ['totalTeamKills', '误伤'], ['totalLoversWins', '恋人胜'], ['totalPlayTime', '在线(秒)'],
  ['totalCivilianGames', '平民局'], ['totalCivilianWins', '平民胜'], ['totalCivilianKills', '平民杀'], ['totalCivilianDeaths', '平民死'],
  ['totalKillerGames', '杀手局'], ['totalKillerWins', '杀手胜'], ['totalKillerKills', '杀手杀'], ['totalKillerDeaths', '杀手死'],
  ['totalNeutralGames', '中立局'], ['totalNeutralWins', '中立胜'], ['totalNeutralKills', '中立杀'], ['totalNeutralDeaths', '中立死'],
  ['totalSheriffGames', '义警局'], ['totalSheriffWins', '义警胜'], ['totalSheriffKills', '义警杀'], ['totalSheriffDeaths', '义警死'],
];
function cardStats() {
  const card = cardShell('战绩', metaFor('stats'));
  card.classList.add('span-2');
  const body = $('.card-body', card);
  const s = payload('stats');
  const fields = STATS_FIELDS.map(([k, l]) => `<div class="field-num"><label title="${k}">${l}</label><input type="number" data-sf="${k}" value="${Number(s[k] || 0)}" /></div>`).join('');
  const roleStats = (s.roleStats && typeof s.roleStats === 'object') ? s.roleStats : {};
  const rows = Object.entries(roleStats).map(([rid, r]) => `<tr><td>${esc(rid)}</td><td>${Number(r.timesPlayed || 0)}</td><td>${Number(r.winsAsRole || 0)}</td><td>${Number(r.killsAsRole || 0)}</td><td>${Number(r.deathsAsRole || 0)}</td></tr>`).join('');
  body.innerHTML = `
    <div class="fields">${fields}</div>
    <div class="btn-row" style="margin-top:10px"><button class="sm primary" id="stats-save">保存战绩</button></div>
    ${rows ? `<hr class="sep" /><div class="section-title">角色战绩（只读）</div><div class="table-wrap"><table><thead><tr><th>角色</th><th>场次</th><th>胜</th><th>杀</th><th>死</th></tr></thead><tbody>${rows}</tbody></table></div>` : ''}`;

  $('#stats-save', body).addEventListener('click', async () => {
    const patch = { identifier: state.identifier, roleStats };
    $$('[data-sf]', body).forEach((inp) => { patch[inp.dataset.sf] = Number(inp.value || 0); });
    try { const r = await api('/save-stats', 'POST', patch); modeToast(r, '已保存'); await reload(); }
    catch (e) { toast(e.message, 'bad'); }
  });
  return card;
}

/* ---- 邮箱 ---- */
function cardMailbox() {
  const card = cardShell('邮箱', metaFor('mailbox'));
  card.classList.add('span-2');
  const body = $('.card-body', card);
  const mails = Array.isArray(rec('mailbox') && rec('mailbox').payload) ? rec('mailbox').payload : [];

  const list = mails.length ? mails.map((m, i) => `
    <div class="row-item" data-mi="${i}">
      <span class="label"><b>${esc(m.title || '(无标题)')}</b> <span class="muted">— ${esc(m.sender || 'System')}</span>
        ${m.read ? '' : '<span class="badge" style="background:var(--warn-bg);color:var(--warn)">未读</span>'}
        ${m.claimed ? '<span class="badge">已领</span>' : ''}</span>
      <button class="sm bad" data-del="${i}">删除</button>
    </div>`).join('') : '<p class="muted">暂无邮件</p>';

  body.innerHTML = `
    <div class="section-title">现有邮件（删除后点“保存邮箱”生效）</div>
    <div class="list" id="mail-list">${list}</div>
    <div class="btn-row" style="margin-top:8px"><button class="sm" id="mail-save">保存邮箱</button></div>
    <hr class="sep" />
    <div class="section-title">快捷发件</div>
    <div class="fields">
      <div style="grid-column:1/-1"><label>收件人（可多个，逗号/空格分隔；留空=当前玩家）</label><input id="m-to" placeholder="${esc(state.identifier)}" /></div>
      <div><label>发件人</label><input id="m-sender" value="System" /></div>
      <div><label>标题</label><input id="m-title" /></div>
      <div style="grid-column:1/-1"><label>正文</label><textarea id="m-content"></textarea></div>
      <div style="grid-column:1/-1"><label>附带命令（每行一条，玩家领取时执行）</label><textarea id="m-cmds" placeholder="give @s minecraft:diamond 5"></textarea></div>
      <div><label>有效期（毫秒时间戳，0=永久）</label><input id="m-exp" type="number" value="0" /></div>
    </div>
    <div class="btn-row" style="margin-top:10px"><button class="sm primary" id="mail-send">发送邮件</button></div>`;

  // 本地维护可删除列表
  let working = mails.slice();
  const renderList = () => {
    $('#mail-list', body).innerHTML = working.length ? working.map((m, i) => `
      <div class="row-item"><span class="label"><b>${esc(m.title || '(无标题)')}</b> <span class="muted">— ${esc(m.sender || 'System')}</span></span>
      <button class="sm bad" data-del="${i}">删除</button></div>`).join('') : '<p class="muted">（空）</p>';
    $$('[data-del]', $('#mail-list', body)).forEach((btn) => btn.addEventListener('click', () => { working.splice(Number(btn.dataset.del), 1); renderList(); }));
  };
  $$('[data-del]', body).forEach((btn) => btn.addEventListener('click', () => { working.splice(Number(btn.dataset.del), 1); renderList(); }));

  $('#mail-save', body).addEventListener('click', async () => {
    try { const r = await api('/save-mailbox', 'POST', { identifier: state.identifier, mails: working }); modeToast(r, '邮箱已保存'); await reload(); }
    catch (e) { toast(e.message, 'bad'); }
  });
  $('#mail-send', body).addEventListener('click', async () => {
    const title = $('#m-title', body).value.trim();
    if (!title) return toast('请填写标题', 'warn');
    const commands = $('#m-cmds', body).value.split('\n').map((x) => x.trim()).filter(Boolean);
    const recipient = $('#m-to', body).value.trim() || state.identifier;
    try {
      const r = await api('/send-mail', 'POST', {
        recipient, sender: $('#m-sender', body).value.trim() || 'System', title,
        content: $('#m-content', body).value, commands, expiresAt: Number($('#m-exp', body).value || 0),
      });
      modeToast(r, '邮件已发送'); await reload();
    } catch (e) { toast(e.message, 'bad'); }
  });
  return card;
}

/* ---- 原始 JSON ---- */
function cardRaw() {
  const card = cardShell('原始 JSON 编辑', '高级');
  card.classList.add('span-2');
  const body = $('.card-body', card);
  const keys = Object.keys(state.profile.records || {});
  const known = ['economy', 'skins', 'progression', 'stats', 'nametags', 'mailbox', 'player_identity'];
  const allKeys = Array.from(new Set([...keys, ...known]));
  body.innerHTML = `
    <div class="inline" style="margin-bottom:8px">
      <select id="raw-key">${allKeys.map((k) => `<option value="${esc(k)}">${esc(k)}</option>`).join('')}</select>
      <button class="sm" id="raw-load">载入</button>
    </div>
    <textarea id="raw-text" style="min-height:200px;font-family:monospace"></textarea>
    <div class="btn-row" style="margin-top:8px"><button class="sm primary" id="raw-save">保存该键</button></div>`;

  const loadKey = () => {
    const k = $('#raw-key', body).value;
    const r = rec(k);
    $('#raw-text', body).value = r ? (r.raw_payload || JSON.stringify(r.payload, null, 2)) : '{}';
  };
  $('#raw-load', body).addEventListener('click', loadKey);
  $('#raw-key', body).addEventListener('change', loadKey);
  loadKey();
  $('#raw-save', body).addEventListener('click', async () => {
    const k = $('#raw-key', body).value; const raw = $('#raw-text', body).value;
    try { JSON.parse(raw); } catch (_) { return toast('JSON 格式错误', 'bad'); }
    try { const r = await api('/save-raw', 'POST', { identifier: state.identifier, data_key: k, raw_payload: raw }); modeToast(r, '已保存'); await reload(); }
    catch (e) { toast(e.message, 'bad'); }
  });
  return card;
}

/* ============ 管理面板 ============ */
async function loadAdmin() {
  const content = $('#admin-content');
  content.innerHTML = '<p class="muted"><span class="spinner"></span> 加载中…</p>';
  try {
    const [status, pend, mails] = await Promise.all([api('/status'), api('/pending-ops'), api('/global-mails')]);
    renderAdmin(status.summary, pend.pending || [], mails.global_mails || []);
  } catch (err) {
    content.innerHTML = `<div class="card"><div class="card-body"><p class="muted">${esc(err.message)}</p></div></div>`;
  }
}

function renderAdmin(summary, pending, mails) {
  const content = $('#admin-content');
  content.innerHTML = '';
  const grid = document.createElement('div');
  grid.className = 'grid';

  // 系统状态
  const st = cardShell('系统状态', '');
  $('.card-body', st).innerHTML = `
    <div class="kv">
      <div class="k">总记录数</div><div>${summary.total_records}</div>
      <div class="k">待处理操作</div><div>${summary.pending_count}</div>
      <div class="k">活跃全局邮件</div><div>${summary.global_mail_count}</div>
      <div class="k">usercache 条目</div><div>${summary.usercache_count}</div>
      <div class="k">最后更新</div><div>${fmtTime(summary.latest_updated_at)}</div>
    </div>
    <div class="section-title" style="margin-top:10px">各数据键</div>
    <div class="table-wrap"><table><thead><tr><th>data_key</th><th>记录数</th></tr></thead><tbody>
      ${(summary.groups || []).map((g) => `<tr><td>${esc(g.data_key)}</td><td>${g.record_count}</td></tr>`).join('')}
    </tbody></table></div>
    <div class="btn-row" style="margin-top:10px">
      <button class="sm primary" id="btn-reconcile">立即对账（应用待处理 + 投递全局邮件）</button>
      <button class="sm" id="btn-refresh-index">刷新玩家索引</button>
    </div>`;
  grid.appendChild(st);

  // 待处理队列
  const pd = cardShell('待处理操作', String(pending.length));
  pd.classList.add('span-2');
  $('.card-body', pd).innerHTML = pending.length ? `<div class="table-wrap"><table>
    <thead><tr><th>ID</th><th>玩家</th><th>UUID</th><th>data_key</th><th>操作</th><th>状态</th><th>创建</th><th>备注</th></tr></thead>
    <tbody>${pending.map((r) => `<tr><td>${r.id}</td><td>${esc(r.player_name)}</td><td style="word-break:break-all">${esc(r.player_uuid || '')}</td><td>${esc(r.data_key)}</td><td>${esc(r.operation_type)}</td><td>${esc(r.status)}</td><td>${fmtTime(r.created_at)}</td><td>${esc(r.note || '')}</td></tr>`).join('')}</tbody>
    </table></div>` : '<p class="muted">无待处理操作</p>';
  grid.appendChild(pd);

  // 全局邮件
  const gm = cardShell('全局邮件', String(mails.length));
  gm.classList.add('span-2');
  $('.card-body', gm).innerHTML = `
    <div class="fields">
      <div><label>发件人</label><input id="gm-sender" value="System" /></div>
      <div><label>标题</label><input id="gm-title" /></div>
      <div style="grid-column:1/-1"><label>正文</label><textarea id="gm-content"></textarea></div>
      <div style="grid-column:1/-1"><label>附带命令（每行一条）</label><textarea id="gm-cmds"></textarea></div>
      <div><label>有效期（毫秒时间戳，0=永久）</label><input id="gm-exp" type="number" value="0" /></div>
    </div>
    <div class="btn-row" style="margin-top:10px"><button class="sm primary" id="gm-create">创建全局邮件</button>
      <span class="muted">创建后需点“立即对账”投递给已知玩家</span></div>
    <hr class="sep" />
    <div class="table-wrap"><table><thead><tr><th>标题</th><th>发件人</th><th>发送</th><th>有效期</th><th>活跃</th></tr></thead>
      <tbody>${mails.map((m) => `<tr><td>${esc(m.title)}</td><td>${esc(m.sender)}</td><td>${fmtTime(m.sent_at)}</td><td>${m.expires_at ? fmtTime(m.expires_at) : '永久'}</td><td>${m.active ? '是' : '否'}</td></tr>`).join('')}</tbody>
    </table></div>`;
  grid.appendChild(gm);

  content.appendChild(grid);

  $('#btn-reconcile').addEventListener('click', async () => {
    try { const r = await api('/reconcile', 'POST'); const rc = r.reconcile;
      toast(`对账完成：应用 ${rc.pending_applied} / 等待 ${rc.pending_waiting} / 邮件投递 ${rc.global_mail_applied}`, 'good'); await loadAdmin(); }
    catch (e) { toast(e.message, 'bad'); }
  });
  $('#btn-refresh-index').addEventListener('click', async () => {
    try { const r = await api('/refresh-index', 'POST'); toast(`索引刷新：usercache ${r.refresh.usercache.indexed} / sync ${r.refresh.sync_table.indexed}`, 'good'); loadRecent(); }
    catch (e) { toast(e.message, 'bad'); }
  });
  $('#gm-create').addEventListener('click', async () => {
    const title = $('#gm-title').value.trim();
    if (!title) return toast('请填写标题', 'warn');
    const commands = $('#gm-cmds').value.split('\n').map((x) => x.trim()).filter(Boolean);
    try {
      await api('/create-global-mail', 'POST', { sender: $('#gm-sender').value.trim() || 'System', title, content: $('#gm-content').value, commands, expiresAt: Number($('#gm-exp').value || 0) });
      toast('全局邮件已创建', 'good'); await loadAdmin();
    } catch (e) { toast(e.message, 'bad'); }
  });
}

init();
