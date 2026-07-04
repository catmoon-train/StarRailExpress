'use strict';

const path = require('path');
const express = require('express');
const session = require('express-session');
const { loadConfig } = require('./src/config');
const { ensureSyncSchema, ensureAdminSchema } = require('./src/schema');
const apiRouter = require('./src/routes');

const cfg = loadConfig();
const app = express();

app.disable('x-powered-by');
app.use(express.json({ limit: '4mb' }));
app.use(express.urlencoded({ extended: false }));

app.use(session({
  name: 'sre_viewer_session',
  secret: (cfg.auth && cfg.auth.sessionSecret) || 'change-this-session-secret',
  resave: false,
  saveUninitialized: false,
  cookie: { httpOnly: true, sameSite: 'lax', secure: false, maxAge: null },
}));

// API
app.use('/api', apiRouter);

// 静态 SPA
const pub = path.join(__dirname, 'public');
app.use(express.static(pub));
// 兜底回首页（非 /api 路径）
app.get(/^(?!\/api).*/, (req, res) => {
  res.sendFile(path.join(pub, 'index.html'));
});

const host = cfg.host || '127.0.0.1';
const port = cfg.port || 8788;

(async () => {
  try {
    await ensureSyncSchema();
    await ensureAdminSchema();
    // eslint-disable-next-line no-console
    console.log('[db] schema ready');
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error('[db] 初始化失败（请检查 config.local.js 的数据库连接）:', err.message);
  }
  app.listen(port, host, () => {
    // eslint-disable-next-line no-console
    console.log(`StarRailExpress 数据管理台已启动: http://${host === '0.0.0.0' ? '127.0.0.1' : host}:${port}`);
  });
})();
