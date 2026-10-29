# 亡灵之主：灵魂与号令重做

## 规则（全地图生效）

- 保留 `noellesroles:undead_lord`、杀手阵营及原有获胜判定，不引入独立胜利。
- 赋予职业/开局创建 RoleData 时重置为 **2 魂**，上限 **6**；离开职业、局末清空魂、感染、亡灵和冷却。仅同步本人。
- 既有 `SRERole.onKill` 成功击杀钩子奖励 **1 魂**，要求真实且仍存活的玩家受害者。该钩子位于死亡保护判定之后、玩家转旁观之前。尸体、失败攻击、单纯注入感染均不产魂；同一受害者同一 tick 的重复回调去重，复活后再次成功击杀仍可奖励。
- 普通模式统一最多 **4 只**自己的亡灵（同时尊重更低的旧配置上限），覆盖尸体复苏、感染转化、旧商店召唤和新主动技能。亡灵死亡/消散释放名额，失败加入世界不登记数量。

| 操作 | 效果 | 成功后的代价 |
| --- | --- | --- |
| 右键尸体【亡者复苏】 | 服务器重做眼位/朝向射线，3 格、无遮挡；真实尸体转化为自己的亡灵，复用 `UndeadEntity.DEFAULT_LIFETIME`（90 秒） | 1 魂，独立 20 秒冷却，销毁尸体 |
| R【亡魂号令】 | 服务器瞄准 24 格内最近的存活玩家，拒绝杀手阵营/杀手方中立及记分板队友，给自己的全部有效亡灵设置 8 秒追击目标 | 独立 20 秒冷却，不耗魂 |
| Shift+R【冥火招魂】 | 身边寻找有空间的位置，召唤 1 只自己的亡灵，寿命 60 秒；无需尸体，初始 2 魂即可起步 | 1 魂，独立 30 秒冷却 |

技能键沿用既有 RoleSkill 输入，支持改键；HUD 显示实际绑定键。两个主动技能在职业构造器中**一次固定注册**，不增加按键、不按玩家注册事件。它们使用独立的 RoleSkill 冷却，通过成功后的 `RoleSkillContext.setSkillCooldown` 读取当前配置；定义保留零固定冷却，避免 `markSkillUsed` 覆盖动态值。`noCastCCA(true)` 避免污染旧的全局技能冷却。复苏冷却保存在 RoleData，以局内游戏时钟结束时刻计时。

无亡灵、无合法目标、魂不足、数量达上限、身边无空间或生成失败，均不扣除本次魂/技能冷却。尸体要求玩家 UUID 非空、未被移除、非伪造、非待复活、主人未复活；主线程内先占用 `nonInteractable`，生成失败回滚占用，成功后立即销毁并提交消耗，双手/多人不能重复使用同一尸体。

## 配置与兼容

`src/main/java/org/agmas/noellesroles/config/NoellesRolesConfig.java`：

| 字段 | 默认值 |
| --- | --- |
| `undeadLordInitialSouls` / `undeadLordMaxSouls` | 2 / 6 |
| `undeadLordCommandCooldownSeconds` / `undeadLordCommandDurationSeconds` | 20 / 8 |
| `undeadLordCommandRange` | 24 |
| `undeadLordSummonCooldownSeconds` / `undeadLordSummonLifetimeSeconds` | 30 / 60 |
| **复用旧字段** `undeadLordReviveCooldownSeconds` | 20（与拟议的 `undeadLordRaiseCooldownSeconds` 同义，不新增重复配置） |
| **复用旧字段** `undeadLordMaxActive` | 4（普通模式再钳制至 4，且受旧 `undeadLordHardCap` 保护） |

旧配置文件保存的自定义数值继续生效，不自动覆盖为新默认值。旧商店四件物品 **骨杖、瘟疫之雾、亡者召唤符、感染增幅器** 全部保留，不删除物品注册或存量物品。感染仍是可选副机制，骨杖五次耐久/充能、感染金币、毒雾、满感染转化及杀手免疫感染致死沿用；无需修改 BoneStaffItem/Handler。

旧商店【亡者召唤符】不消耗灵魂，沿用独立旧配置（默认 200 金币、最多 2 只、45 秒寿命、90 秒冷却），与 Shift+R 区分；没有生成任何亡灵时不扣金币或商店冷却。感染转化继续使用 90 秒默认寿命。

`UndeadLordRoleData` 的既有 public API/字段保持签名：`init/clear`、`getActiveUndeadCount/maxActiveUndead/canRaiseFromCorpse/spawnUndeadAt/onUndeadRemoved`、`addInfection/canDieFromInfection`、`trySummonCharm/summonTemporaryUndead/startInfectionAmp/releasePlagueFog`、同步字段和增幅/召唤符字段均保留。`ignoreUndeadCap` 仍绕过普通/硬上限，并保留邪恶战争的无冷却尸体复苏；`boostUndeadSpeed` 仍使生成的亡灵获得原先 +200% 速度。新灵魂消耗仍适用。未修改邪恶战争调用方。

追击仅调用另一个战斗模块提供的 `UndeadEntity.setHalloweenTarget(UUID, int)`；本模块不改 UndeadEntity 或万圣节战斗代码。服务器在施放号令前验证阵营，未知客户端 UUID 不参与选目标。

## 入口与父代理合并

- 现有 `ModRolesInitialEventRegister` → `UndeadLordHandler.init()` 链已足够：Handler 现在幂等注册尸体交互及 `OnGameEnd` 清理；职业构造器注册两项 RoleSkill。
- 现有 `ModRoles.UNDEAD_LORD.setRoleData(UndeadLordRoleData::new)` 和 `UndeadLordHud.register()` 保持即可，不需要新增 CCA、包、按键或 mixin。
- 删除原 Handler 中的二次 `ModdedRoleAssigned → data.init()`，避免覆盖职业数据生命周期的初始化和其他模式设置。
- 万圣节必出一个亡灵之主由父代理负责，本模块无地图条件限制、无任何强制分配逻辑。
- 中文合并补丁（以目标资源文件路径分组）：`H:/pi/data/scratch/ac859a6b-9622-4888-acbb-88b6a14272b9/undead-lord-translations.zh_cn.json`。包含新技能/HUD/错误提示及更新后的 goal、详细/简略职业介绍、骨杖与旧商店物品说明；父代理合并语言文件，本模块不写共享 JSON。

## 模块验证（不跑 Gradle）

JDK 21 独立检查器：`src/test/java/org/agmas/noellesroles/game/roles/killer/undead_lord/UndeadLordReworkCheck.java`。

在仓库根目录运行，所有中间产物写入会话 scratch：

```bash
javac -encoding UTF-8 -d "$PI_SCRATCH_DIR/undead-lord-tests" \
  src/test/java/org/agmas/noellesroles/game/roles/killer/undead_lord/UndeadLordReworkCheck.java
java -cp "$PI_SCRATCH_DIR/undead-lord-tests" \
  org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordReworkCheck "$PI_SCRATCH_DIR"
```

检查器只解析本模块五个生产 Java 文件的语法，不分析 Minecraft 依赖；静态验证配置、固定技能注册、服务器目标校验、消费顺序、原子尸体事务、局末清理、HUD 和旧 API。它提取并实际编译/执行生产 `SoulPool` 纯逻辑，覆盖初始两魂启动、上下限、真实击杀奖励去重、复活重杀、满魂奖励去重、局末/新局清理及整数边界。不是完整项目类型检查或游戏集成测试；全 Gradle 构建和实机验证由父代理统一。
