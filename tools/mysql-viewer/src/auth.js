'use strict';

const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const { loadConfig } = require('./config');

function authConfig() {
  const a = loadConfig().auth || {};
  return {
    enabled: a.enabled !== false,
    username: String(a.username || 'admin'),
    passwordHash: String(a.passwordHash || ''),
    passwordPlain: String(a.passwordPlain || ''),
  };
}

// 定长比较，避免时序泄漏
function safeEqual(a, b) {
  const ba = Buffer.from(String(a));
  const bb = Buffer.from(String(b));
  if (ba.length !== bb.length) return false;
  return crypto.timingSafeEqual(ba, bb);
}

function verifyLogin(username, password) {
  const auth = authConfig();
  if (!auth.enabled) return true;
  if (auth.username === '' || !safeEqual(auth.username, username)) return false;

  const hash = auth.passwordHash.trim();
  if (hash !== '') {
    try { return bcrypt.compareSync(String(password), hash); } catch (_) { return false; }
  }
  if (auth.passwordPlain === '') return false;
  return safeEqual(auth.passwordPlain, password);
}

function isLoggedIn(req) {
  if (!authConfig().enabled) return true;
  return !!(req.session && req.session.authOk === true);
}

// API 中间件：未登录返回 401
function requireAuth(req, res, next) {
  if (isLoggedIn(req)) return next();
  return res.status(401).json({ ok: false, message: 'Unauthorized. Please login first.' });
}

module.exports = {
  authConfig, verifyLogin, isLoggedIn, requireAuth,
};
