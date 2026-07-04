# StarRailExpress 数据管理台 (Node.js)

读写 StarRailExpress 模组 MySQL 玩家数据的 Web 管理工具。**零构建**：纯 Node + 原生前端，单进程同时提供 API 与页面，移动端可用，并为金币、称号、皮肤、阵营卡、邮件等高频操作提供一键快捷入口。

> 本工具是旧版 PHP + Nginx 实现的替代品，行为等价（同样的表结构、合并规则、两段式保存与对账机制），不再需要 PHP-FPM / Nginx。

## 功能

- **玩家工作台**（卡片式、就地编辑）
  - 金币 / 抽奖次数：`-100/-10/+10/+100/+1000` 快捷增减 + 直接设值
  - 称号：已有称号点选「设为当前 / 移除」，文本框 + § 颜色码助手「添加 / 添加并设为当前 / 清空」
  - 皮肤：按物品类型解锁 / 上锁 / 装备，单物品或全部「全解锁」
  - 等级 / 经验 / 阵营卡：数值编辑 + 阵营卡 `+/-` 即时增减
  - 战绩：全量数值字段 + 角色战绩表
  - 邮箱：现有邮件删除、快捷发件（支持批量收件人、附带命令、有效期）
  - 原始 JSON：任意 `data_key` 的兜底编辑
- **管理面板**：系统状态、待处理队列 + 立即对账、全局邮件创建与投递、刷新玩家索引
- **两段式保存**：解析到 UUID 立即落库；否则进 `*_admin_pending_ops` 队列，玩家上线后由「立即对账」补发

## 环境要求

- Node.js 18+（含 npm）
- 可访问的 MySQL（建议使用具备读写权限的专用账号）

## 配置

复制模板并填写：

```bash
copy config.sample.js config.local.js   # Windows
# cp config.sample.js config.local.js   # macOS / Linux
```

`config.local.js`（已被 `.gitignore` 忽略）关键字段：

| 字段 | 说明 |
| --- | --- |
| `host` / `port` | HTTP 监听地址。仅本机用 `127.0.0.1`；手机/局域网访问改 `host: '0.0.0.0'` |
| `auth.username` | 登录用户名 |
| `auth.passwordHash` | bcrypt 哈希（优先）。生成：`node -e "console.log(require('bcryptjs').hashSync('你的密码',10))"` |
| `auth.passwordPlain` | 明文口令（过渡用，配置 hash 后清空） |
| `auth.sessionSecret` | 会话签名密钥，改成随机串 |
| `usercachePath` | Minecraft 服务端 `run/usercache.json` 路径（用于玩家名↔UUID）。文件不存在不会报错 |
| `db.*` | 数据库连接（与游戏侧 `run/config/starrailexpress.json` 中同一套库） |
| `tableName` | 主同步表名，默认 `sre_player_sync_data` |

辅助表（`sre_admin_pending_ops`、`sre_admin_global_mail`、`sre_admin_global_mail_delivery`、`sre_admin_player_index`）会在首次启动时自动创建。

## 运行

Windows 双击或命令行：

```bat
start.bat
```

或手动：

```bash
npm install
npm start
```

浏览器打开 `http://127.0.0.1:8788`（端口以配置为准），登录后即可使用。

## 安全建议

- 仅监听 `127.0.0.1`，或置于内网 / 反向代理之后，不要直接暴露公网。
- 使用具备读写权限的**专用** DB 账号，限制来源 IP。
- 首次部署后立即修改默认口令，优先用 `passwordHash`。

## 数据键速查

所有玩家数据存于 `sre_player_sync_data`，按 `data_key` 区分，`payload_json` 为 JSON：

- `skins`：`coinNum` / `lootChance` / `equipped{item:skin}` / `unlocked{item:{skin:true}}`
- `progression`：`level` / `experience` / `totalExperience` / `factionCards{...}` 等
- `stats`：各类 `total*` 计数 + `roleStats`
- `nametags`：`nameTags[]` / `currentNametag`
- `mailbox`：邮件数组（按 `id` 去重）

## 目录结构

```
server.js              # Express 入口
config.sample.js       # 配置模板（复制为 config.local.js）
src/
  config.js  db.js  schema.js  auth.js
  resolve.js merge.js records.js pending.js
  lang.js    routes.js
public/                # 零构建前端
  index.html  app.js  styles.css
```
