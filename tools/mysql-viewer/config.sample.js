'use strict';

/**
 * 配置模板。复制本文件为 config.local.js 并填写真实值。
 * config.local.js 已被 .gitignore 忽略，不会进入版本库。
 */
module.exports = {
  // 站点标题（显示在页面顶部）
  siteTitle: 'StarRailExpress 数据管理台',

  // HTTP 监听
  host: '127.0.0.1', // 仅本机访问；如需局域网手机访问改为 '0.0.0.0'
  port: 8788,

  // 登录鉴权
  auth: {
    enabled: true,
    username: 'admin',
    // 生产环境填 bcrypt 哈希（用 `node -e "console.log(require('bcryptjs').hashSync('你的密码',10))"` 生成）。
    passwordHash: '',
    // 仅用于过渡；配置 passwordHash 后可清空。
    passwordPlain: 'change_me_now',
    // express-session 的签名密钥，请改成随机字符串。
    sessionSecret: 'change-this-session-secret',
  },

  // 玩家名 -> UUID 的 usercache.json 路径（Minecraft 服务端 run 目录下）。
  // 找不到该文件时不会报错，仅退化到 player_index / sync 表解析。
  usercachePath: '/www/server/minecraft/run/usercache.json',

  // 数据库连接（建议使用具备读写权限的专用账号）
  db: {
    host: '127.0.0.1',
    port: 3306,
    database: 'starrailexpress',
    user: 'viewer_admin',
    password: 'replace_me',
    charset: 'utf8mb4',
    connectionLimit: 4,
    connectTimeout: 5000,
  },

  // 主同步表名（默认与游戏侧一致）
  tableName: 'sre_player_sync_data',

  // 批处理 / 列表上限
  identitySyncBatchSize: 500,
  reconcileBatchSize: 500,
  recentPlayerLimit: 30,
};
