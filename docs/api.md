# StarRail Express 开发者 API 文档
# StarRail Express Developer API Documentation

> 本文档面向希望为 StarRail Express 编写扩展的开发者。  
> This document is intended for developers who want to write extensions for StarRail Express.

---

> ⚠️ **本文件的 API 清单存在大量过时内容（包名/方法名/事件签名），写代码请以源码为准。**
> 下面这张表是本文件已核实的「旧写法 → 实际写法」对照；正文里的代码块也已按此表做过一轮修正，但**仍可能有遗漏**，遇到不确定的符号请直接 grep 源码：
> `grep -rn "方法名\|类名" src/main/java`；事件签名看 `src/main/java/io/wifi/starrailexpress/event/*.java`。
>
> | 旧写法（本文档可能残留） | 实际写法 / 位置 |
> | --- | --- |
> | `io.wifi.starrailexpress.contents.item.*` | `io.wifi.starrailexpress.content.item.*`（无 `s`） |
> | `ItemSkinManager` | `io.wifi.starrailexpress.util.ItemSkinManager`；注册皮肤 `registerACustomSkin(skinType, skinID, color)` |
> | `TMMItemUtils` | `io.wifi.starrailexpress.util.SREItemUtils` |
> | `org.agmas.noellesroles.RoleSkill` | `io.wifi.starrailexpress.api.RoleSkill` |
> | `RoleSkill.beginUseWithTarget(...)` | `beginUseShiftedWithTarget(ServerPlayer, UUID)` / `beginUse(ServerPlayer, @Nullable UUID, int, Phase)` |
> | `SRERole.getCooldownComponent(player)` | `SREAbilityPlayerComponent.KEY.get(player)` |
> | `SRERole.setMax(n)`、`SREModifier.setMax(n)` | `setDefaultMax(n)` |
> | `setCanSeeTeammateKiller(b)` | `setCanSeeTeammateKillerRole(b)` |
> | 构造器末参 `hideScoreboard` | 实际是 `canSeeTime`（含义相反） |
> | `onDeath(...)` 4 参、`onKill(...)` 返回 `boolean` | `void onDeath(..., boolean forceDeath)` / `void onKill(...)`；需要尸体用 `onDeathWithBody(...)` |
> | `rightClickEntity/leftClickEntity` 返回 `void` | 返回 `InteractionResult` |
> | `onAbilityUse(Player)` | `boolean onAbilityUse(ServerPlayer)` |
> | `MorphApi.resolveDisplayedOwnerUuid/getDisplayedName/getDisplayedPlushStack` | 在客户端类 `MorphApiClient` 上 |
> | `RoundEndComponent.CustomWinnerID` / `GameUtils.CustomWinnersPredicates` | `SREGameRoundEndComponent.CustomWinnerID`；**没有 `CustomWinnersPredicates`**，独立胜利用 `RoleUtils.customWinnerWin(...)`（见 `docs/AI创建新职业攻略.md` §12） |
> | `GameConstants.DeathReasons.KNIFE_STAB` | `DeathReasons.KNIFE`（值仍是 `sre:knife_stab`） |
> | `GameUtils.addItemCooldowns(level)` | `addItemCooldowns(ServerLevel, int time)` |
> | `GameUtils.isPlayerSplitPersonalityAndSurvive` | `GameUtils.isPlayerReallyAliveOrDead`（`SPAliveResult`：`ALIVE`/`DEAD`/`NOT`） |
> | `RoleUtils.RemoveAllPlayerAttributes` / `RemoveAllEffects` | `removeAllPlayerAttributes` / `removeAllEffects` |
> | `RoleUtils.getRoleFromName("killer")` | `RoleUtils.getRole(String)` / `getRole(ResourceLocation)` |
> | `Harpymodloader.setOccupationRole/getOccupationRole/removeOccupationRole` | `addOccupationRole(main, companion)` / `getOccupationRoles(role)` / `clearOccupationRole(role)` |
> | `DISCOVERY_ID` | `SREGameModes.DISCOVERY_MODE_ID` |
> | `SREGameModes.registerGameMode(id, mode)` | `registerGameMode(GameMode)` |
> | `GameMode.tickCommonGameLoop()` / `tickClientGameLoop()` | 都带参数：`(Level level)` |
> | `List<ReplayEvent>` | `List<TimelineReplayEvent>` |
> | Replay `EventType`：`ITEM_USE`/`ROUND_END`/`ROLE_ASSIGNMENT`/`SKILL_USED`/`CUSTOM_MESSAGE`/`NOTE_EDIT`/`KEY_USED`/`GUN_FIRED` | 实际为 `ITEM_USED`/`GAME_END`/`CHANGE_ROLE`/`SKILL_RELEASE`/`CUSTOM_EVENT`；`NOTE_EDIT`/`KEY_USED`/`GUN_FIRED` **不存在**（完整枚举见 `api/replay/ReplayEventTypes.java`） |
> | 本能在 `OnGetInstinctHighlight` 上 | 实际是 `event.client.CommonInstinctEvents` 的 `ALIVE_COMMON_BEFORE/MIDDLE/AFTER_EVENT` / `SPECTATOR_COMMON_EVENT`，返回 `TrueFalseAndCustomResult<Integer>` |
> | `OnRoundStartWelcomeTimer` | `OnRoundStartWelcomeTimmer`（拼写如此） |
> | `AllowGameEnd.EVENT` | `AllowGameEnd.EVENT_START` / `EVENT_END` |
> | `AreasSettings.meetingX/Y/Z`、`meetingChairScanRadius` | `meetingPosition: {x,y,z}`、`meetingChairScanBox: {minX..maxZ}` |
>
> 注：**RoleData / ShopEntry / 商店 / 背包扩展 / TimeRewind / ui_style 等章节经核对基本正确**，可以放心参考；职业相关的最新最全流程请优先看 [`角色开发指南.md`](角色开发指南.md) 与 [`AI创建新职业攻略.md`](AI创建新职业攻略.md)。

## 目录 / Table of Contents

1. [重要提醒 / Important Notes](#重要提醒--important-notes)
2. [角色系统 / Role System](#角色系统--role-system)
   - [SRERole — 角色基类](#srerole--角色基类)
   - [NormalRole — 标准角色](#normalrole--标准角色)
   - [ExtraEffectRole — 药水效果角色](#extraeffectrole--药水效果角色)
   - [TMMRoles — 角色注册表](#tmmroles--角色注册表)
   - [职业随机事件 / Role Round Event](#职业随机事件--role-round-event)
3. [修饰符系统 / Modifier System](#修饰符系统--modifier-system)
   - [SREModifier — 修饰符基类](#sremodifier--修饰符基类)
   - [HMLModifiers — 修饰符注册表](#hmlmodifiers--修饰符注册表)
   - [WorldModifierComponent — 修饰符 CCA](#worldmodifiercomponent--修饰符-cca)
4. [CCA 组件 / CCA Components](#cca-组件--cca-components)
   - [RoleComponent — 角色组件接口](#rolecomponent--角色组件接口)
   - [SREAbilityPlayerComponent — 通用技能组件](#sreabilityplayercomponent--通用技能组件)
5. [技能系统 / Skill System](#技能系统--skill-system)
   - [RoleSkill — 技能注册](#roleskill--技能注册)
6. [商店系统 / Shop System](#商店系统--shop-system)
   - [ShopEntry — 商店条目](#shopentry--商店条目)
   - [ShopContent — 商店内容管理](#shopcontent--商店内容管理)
   - [DynamicShopComponent — 动态价格组件](#dynamicshopcomponent--动态价格组件)
   - [/dynamicshop 指令](#dynamicshop-指令)
   - [示例：杀手刀有限耐久 + 首购折扣](#示例杀手刀有限耐久--首购折扣)
7. [蓄力物品系统 / Chargeable Item System](#蓄力物品系统--chargeable-item-system)
   - [ChargeableItem — 蓄力物品接口](#chargeableitem--蓄力物品接口)
   - [ChargeableItemRegistry — 蓄力物品注册表](#chargeableitemregistry--蓄力物品注册表)
8. [物品类型 / Item Types](#物品类型--item-types)
   - [可继承物品基类](#可继承物品基类)
   - [SkinableItem — 可换皮肤物品](#skinableitem--可换皮肤物品)
9. [皮肤系统 / Skin System](#皮肤系统--skin-system)
   - [ItemSkinManager — 皮肤工具类](#itemskinmanager--皮肤工具类)
   - [注册自定义皮肤](#注册自定义皮肤)
   - [变形 API / Morph API](#变形-api--morph-api)
   - [实体伪装 API / Entity Disguise API](#实体伪装-api--entity-disguise-api)
10. [事件系统 / Event System](#事件系统--event-system)
    - [游戏生命周期事件](#游戏生命周期事件)
    - [玩家死亡事件](#玩家死亡事件)
    - [技能与交互事件](#技能与交互事件)
    - [渲染与客户端事件](#渲染与客户端事件)
    - [变形与伪装事件](#变形与伪装事件)
    - [其他事件](#其他事件)
11. [Harpymodloader API](#harpymodloader-api)
    - [Harpymodloader — 主入口](#harpymodloader--主入口)
    - [HML 事件](#hml-事件)
12. [游戏模式系统 / Game Mode System](#游戏模式系统--game-mode-system)
    - [GameMode — 游戏模式基类](#gamemode--游戏模式基类)
    - [SREGameModes — 游戏模式注册表](#sregamemodes--游戏模式注册表)
13. [HUD 渲染 / HUD Rendering](#hud-渲染--hud-rendering)
14. [工具类 / Utilities](#工具类--utilities)
    - [GameUtils — 游戏工具](#gameutils--游戏工具)
    - [SREItemUtils — 物品工具](#tmmitemutils--物品工具)
    - [RoleUtils — 角色工具](#roleutils--角色工具)
15. [Replay 系统 / Replay System](#replay-系统--replay-system)
    - [IGameReplayRecorder — 回放记录接口](#igamereplayrecorder--回放记录接口)
    - [IGameReplayReader — 回放读取接口](#igamereplayreader--回放读取接口)
    - [ReplayEventTypes — 事件类型枚举](#replayeventtypes--事件类型枚举)
16. [背包界面 API / Inventory Screen API](#背包界面-api--inventory-screen-api)
    - [LimitedInventoryScreenEvents — 事件](#limitedinventoryscreenevents--事件)
    - [SRERole 屏幕钩子 / Screen Hooks](#srerole-屏幕钩子--screen-hooks)
    - [屏幕公开"轮椅"方法](#屏幕公开轮椅方法)
    - [PlayerPaginationHelper — 翻页/搜索/排序](#playerpaginationhelper--翻页搜索排序)
    - [RoleScreenHelper — 角色选人辅助](#rolescreenhelper--角色选人辅助)
17. [粒子与特效 / Particle & FX](#粒子与特效--particle--fx)
    - [ParticleFx — 服务端粒子助手](#particlefx--服务端粒子助手)
    - [SceneParticles — 场景方块粒子](#sceneparticles--场景方块粒子)
    - [自定义形状粒子 / Custom Shape Particles](#自定义形状粒子--custom-shape-particles)

---

## 重要提醒 / Important Notes

- **不要引用 Wathe 的库**，它会导致崩溃（未初始化）。  
  **Do NOT import Wathe libraries** — they will cause crashes (uninitialized state). 比如 `GameFunctions`，不要用他！请使用 `GameUtils` 代替！
- 网络同步压力在人少时几乎不可见，但在 16 人以上的服务器上会非常明显，请遵循"尽量不同步"原则。  
  Network sync overhead is negligible with few players but significant on servers with 16+ players. Minimize unnecessary sync.
- 尽量使用 `RoleData`（`.setRoleData`）保存职业状态；CCA 只留给世界/全局或必须挂在当前职业以外玩家身上的状态。  
  Prefer `RoleData` for role state. Keep CCA for world/global data, or status that must live on a player who is not currently that role.

---

## 角色系统 / Role System

### SRERole — 角色基类

**包 / Package:** `io.wifi.starrailexpress.api`

所有职业的抽象基类。通过链式调用配置角色属性，并重写回调方法实现自定义行为。  
Abstract base class for all roles. Configure role properties via fluent setters and override callbacks for custom behavior.

#### 构造函数 / Constructor

```java
public SRERole(ResourceLocation identifier, int color, boolean isInnocent,
               boolean canUseKiller, MoodType moodType, int maxSprintTime,
               boolean canSeeTime)   // 最后一个参数是「是否看得见游戏计时」，不是隐藏计分板
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `identifier` | `ResourceLocation` | 角色唯一 ID |
| `color` | `int` | 通告颜色（ARGB 整数） |
| `isInnocent` | `boolean` | 是否属于乘客（平民）阵营 |
| `canUseKiller` | `boolean` | 是否具有杀手能力 |
| `moodType` | `MoodType` | 心情类型：`NONE` / `REAL` / `FAKE` |
| `maxSprintTime` | `int` | 最大冲刺时间（tick），`-1` 为无限制 |
| `canSeeTime` | `boolean` | 是否看得见游戏计时（旧文档写作 `hideScoreboard`，含义相反） |

#### 属性配置方法 / Property Setters（链式调用 / Fluent）

```java
SRERole setColor(int color)                              // 设置颜色
SRERole setInnocent(boolean innocent)                    // 设置是否为乘客阵营
SRERole setCanUseKiller(boolean canUseKiller)            // 设置是否有杀手能力
SRERole setMoodType(MoodType moodType)                   // 设置心情类型
SRERole setMaxSprintTime(int maxSprintTime)              // 设置最大冲刺时间（固定值）
SRERole setMaxSprintTime(ToIntFunction<Player> func)     // 设置最大冲刺时间（动态函数）
SRERole setCanSeeTime(boolean canSeeTime)                // 是否可以看到计时器
SRERole setCanSeeCoin(boolean canSeeCoin)                // 是否可以看到金币
SRERole setCanUseInstinct(boolean canUseInstinct)        // 是否可以使用本能技能
SRERole setAbleToPickUpRevolver(boolean able)            // 是否可以拾取左轮手枪
SRERole setNeutrals(boolean neutrals)                    // 是否为中立阵营
SRERole setNeutralForKiller(boolean forKiller)           // 是否对杀手中立（同时设置 isNeutrals=true）
SRERole setVigilanteTeam(boolean vigilanteTeam)          // 是否为自警阵营
SRERole setCanSeeTeammateKillerRole(boolean canSeeKiller)    // 是否可以看到队友杀手身份
SRERole setOccupiedRoleCount(int count)                  // 占用角色池数量（默认 1）
SRERole setDefaultMax(int count)                         // 设置最大同时存在数量
SRERole setAutoReset(boolean autoReset)                  // 游戏结束是否自动重置
SRERole setRoleData(Function<RoleDataContext, RoleData> func) // 绑定职业数据（默认，优先于 CCA）
SRERole setComponentKey(ComponentKey<? extends RoleComponent> key) // 关联 CCA（仅跨玩家/全局状态）
SRERole setCanAutoAddMoney(boolean bl)                   // 是否启用自动加钱（被动收入）
SRERole setCanHavePassiveIncome(boolean bl)              // 是否启用被动收入
SRERole addChild(Consumer<LimitedInventoryScreen> addChild) // 添加 HUD 子元素
SRERole setServerGameTickEvent(BiConsumer<ServerPlayer, SREGameWorldComponent> event) // 服务端 Tick 回调
SRERole setClientGameTickEvent(BiConsumer<Player, SREGameWorldComponent> event)       // 客户端 Tick 回调
SRERole setInventoryScreenExtensionFactory(Supplier<RoleInventoryScreenExtension> factory) // 背包界面扩展工厂（客户端注册；每次打开创建新实例）
SRERole setEventEnableChance(...)                        // 职业专属随机事件：每局开局掷一次启用骰（见「职业随机事件」）
```

#### 可重写的回调方法 / Overridable Callbacks

```java
// 玩家死亡时调用（可返回 false 阻止尸体生成）
void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason, boolean forceDeath)
void onDeathWithBody(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason, PlayerBodyEntity body)

// 杀手杀死玩家时调用
void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason)

// 完成任务时调用
void onFinishQuest(Player player, String quest)

// 角色初始化（游戏开始分配角色后）
void onInit(MinecraftServer server, ServerPlayer serverPlayer)

// 服务端每 Tick 调用（游戏进行中）
void serverTick(ServerPlayer player)

// 客户端每 Tick 调用（游戏进行中）
void clientTick(Player player)

// 右键实体
InteractionResult rightClickEntity(Player player, Entity victim)

// 左键实体
InteractionResult leftClickEntity(Player player, Entity victim)

// 使用物品（G 键）
boolean onAbilityUse(ServerPlayer player)

// 使用左轮手枪
boolean onUseGun(Player player)

// 使用暗器（小手枪）
boolean onUseDerringer(Player player)

// 左轮命中玩家
boolean onGunHit(Player killer, Player victim)

// 使用刀
boolean onUseKnife(Player player)

// 刀命中玩家
boolean onUseKnifeHit(Player player, Player target)

// 右键使用物品
InteractionResultHolder<ItemStack> onItemUse(Player player, Level world, InteractionHand hand)

// 右键使用方块
InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult)

// 获取角色初始物品列表
List<ItemStack> getDefaultItems()

// 获取角色商店条目列表
List<ShopEntry> getShopEntries()

// Psycho 模式可使用的武器（按优先级排序）
List<Item> getPsychoSupportedWeapons(Player player)

// 自定义 Psycho 无兼容武器时的发放逻辑
boolean onPsychoGiveItem(Player player, SREPlayerPsychoComponent component)

// Psycho 结束时是否回收本次新发放的兼容武器（默认 true；职业武器覆写为 false）
boolean shouldClearGrantedPsychoWeapon(Player player, Item weapon)

// 背包界面 init() 开头（仅客户端调用；由 LimitedInventoryScreen 触发）
void onInventoryScreenInit(LimitedInventoryScreen screen)

// 背包界面 init() 末尾（仅客户端调用）
void onInventoryScreenInitTail(LimitedInventoryScreen screen)

// 背包界面 render() 开头，每帧（仅客户端调用）
void onInventoryScreenRender(LimitedInventoryScreen screen, GuiGraphics graphics, int mouseX, int mouseY, float delta)
```

Psycho 开始时会先搜索 `getPsychoSupportedWeapons` 返回的所有武器：主手已持有则保持，
快捷栏中已存在时直接切槽，副手或背包中已存在时会换入当前主手槽；只有全部不存在时才调用 `onPsychoGiveItem`。
默认实现仍兼容旧的 `getPsychoItem()` 单武器覆写。职业武器可覆写 `shouldClearGrantedPsychoWeapon` 以免结束时被回收。

#### 枚举 MoodType

| 值 | 说明 |
|----|------|
| `NONE` | 无心情 |
| `REAL` | 真实心情 |
| `FAKE` | 假心情 |

#### 任务刷新控制 / Task Refresh Control

控制该职业玩家的 SAN 任务随机池：黑名单排除指定任务，白名单（非空时）只允许指定任务。
任务生成（`SREPlayerTaskComponent.generateTaskInternal`）时对每个候选任务调用 `canRefreshTask`。
Controls which SAN tasks this role's players can roll: a blacklist excludes tasks, and a non-empty
whitelist restricts rolls to the listed tasks only. `canRefreshTask` is consulted for every candidate
during task generation.

```java
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;

// 黑名单：该职业不可刷出的任务（链式，可叠加）
SRERole addUnrefreshableTasks(Task... tasks)
SRERole removeUnrefreshableTasks(Task... tasks)

// 白名单：该职业仅可刷出的任务（为空表示不限制）
SRERole addOnlyRefreshableTasks(Task... tasks)
SRERole removeOnlyRefreshableTasks(Task... tasks)

// 只读查询
Set<Task> getUnrefreshableTasks()
Set<Task> getOnlyRefreshableTasks()

// 可重写：动态判断（默认先查白名单再查黑名单）
boolean canRefreshTask(Player player, Task taskType)
```

```java
// 示例：某职业永远不会刷出睡觉/马桶任务
TMMRoles.registerRole(new NormalRole(...)
        .addUnrefreshableTasks(Task.SLEEP, Task.TOILET));

// 示例：某职业只会刷出进食和喝水任务
TMMRoles.registerRole(new NormalRole(...)
        .addOnlyRefreshableTasks(Task.EAT, Task.DRINK));
```

#### 获取技能冷却组件

```java
// 静态辅助方法，从玩家获取通用技能组件
SREAbilityPlayerComponent component = SREAbilityPlayerComponent.KEY.get(player);
```

---

### NormalRole — 标准角色

**包 / Package:** `io.wifi.starrailexpress.api`

继承自 `SRERole` 的标准实现，适合无特殊 Tick 行为的角色。  
Standard `SRERole` implementation suitable for roles without special tick behavior.

```java
new NormalRole(ResourceLocation id, int color, boolean isInnocent,
               boolean canUseKiller, MoodType moodType, int maxSprintTime,
               boolean hideScoreboard)
```

---

### ExtraEffectRole — 药水效果角色

**包 / Package:** `io.wifi.starrailexpress.api`

在 `NormalRole` 基础上，每 20 tick 自动给玩家施加药水效果。  
Extends `NormalRole` and automatically applies potion effects to the player every 20 ticks.

```java
// 注意：没有 varargs 构造器，效果通过构造器传入 ArrayList<MobEffectInstance>（或单个 MobEffectInstance），
// 或构造后用 addEffect(...) 链式添加
new ExtraEffectRole(ResourceLocation id, int color, boolean isInnocent,
                    boolean canUseKiller, MoodType moodType, int maxSprintTime,
                    boolean canSeeTime, ArrayList<MobEffectInstance> effects)
```

| 方法 | 说明 |
|------|------|
| `List<MobEffectInstance> getEffects()` | 获取效果列表 |
| `ExtraEffectRole addEffect(MobEffectInstance)` | 添加效果（链式） |
| `ExtraEffectRole removeEffect(MobEffectInstance)` | 移除效果 |

---

### TMMRoles — 角色注册表

**包 / Package:** `io.wifi.starrailexpress.api`

#### 内置角色 / Built-in Roles

| 常量 | ID | 阵营 |
|------|-----|------|
| `DISCOVERY_CIVILIAN` | `sre:discovery_civilian` | 乘客（发现模式） |
| `CIVILIAN` | `sre:civilian` | 平民 |
| `VIGILANTE` | `sre:vigilante` | 义警 |
| `KILLER` | `sre:killer` | 杀手 |
| `LOOSE_END` | `sre:loose_end` | 亡命徒（中立） |

#### 注册新角色 / Registering a New Role

```java
// 1. 创建角色 ID
public static final ResourceLocation MY_ROLE_ID = SRE.id("my_role");

// 2. 注册角色
public static final SRERole MY_ROLE = TMMRoles.registerRole(
    new NormalRole(
        MY_ROLE_ID,
        new Color(75, 0, 130).getRGB(), // 颜色
        false,      // isInnocent（非乘客阵营）
        true,       // canUseKiller（有杀手能力）
        SRERole.MoodType.FAKE,
        Integer.MAX_VALUE,  // 无限冲刺
        true        // 隐藏计分板
    )
    .setRoleData(MyRoleData::new)
    .setCanSeeCoin(true)
    .setOccupiedRoleCount(2)
);
```

#### 注册角色组件键 / Register Role Component Key

职业状态请用 `.setRoleData`，不要再 `TMMRoles.addRoleComponents` 给每个职业挂一份 CCA。  
`addRoleComponents` 只用于必须在所有玩家上 tick/同步的全局玩家组件（心情、商店、中毒等）。

```java
TMMRoles.addRoleComponents(ModComponents.MY_GLOBAL_COMPONENT); // 仅全局玩家组件
```

---

### 职业随机事件 / Role Round Event

**包 / Package:** `io.wifi.starrailexpress.api`（`RoleRoundEvent` 实现，入口在 `SRERole`）

「职业随机事件」= 某个职业专属的、**每局开局只掷一次**的启用骰：开局时按概率决定这一局这个事件发不发生。
掷骰前会先套用职业自己的地图限制与禁用状态，所以「只在实验室地图刷的紫怪」「概率读配置的假史蒂夫」
都能直接用它表达。仓库里的实例：假史蒂夫（`noellesroles:fake_steve`，概率读配置）与紫怪
（`noellesroles:purple_monster`，固定 60% + 仅 LAB 地图）。

状态是**职业级、维度通用**的：同一职业在所有维度共用一份「本局是否启用」，所以查询与「强制下一局」
都不需要传世界；世界只影响掷骰时的地图限制判定和回调入参，**默认取主世界**。

> **不要**为这种需求自己注册 `OnGameTrueStarted` / `OnGameEnd` 监听器。事件只在
> `SREEventRegister#registerEventHandlers()` 里静态注册一次，运行时遍历 `TMMRoles` 维护的
> 「声明过事件的职业」列表派发：职业被注销会自动移出列表，监听器数量也不随职业数量增长。

#### 声明事件 / Declaring an event

一次给出两个互不干扰的回调，都接在 `TMMRoles.registerRole(...)` 返回的职业上
（`chance` 是**万分比**，`6000` = 60%，超出 0–10000 会被裁剪）：

| 重载 | 掷骰回调 | 局末回调 |
| --- | --- | --- |
| `setEventEnableChance(BiConsumer<ServerLevel,Boolean>, Consumer<ServerLevel>, int)` | **每次**掷骰都调用；未掷中收到 `(level, false)` | 仅本局掷中过时调用 |
| `setEventEnableChance(Consumer<ServerLevel>, Consumer<ServerLevel>, int)` | 仅本局掷中时调用 | 仅本局掷中过时调用 |
| `setEventEnableChance(BiConsumer<ServerLevel,Boolean>, int)` | **每次**掷骰都调用 | 不注册 |
| `setEventEnableChance(Consumer<ServerLevel>, int)` | 仅本局掷中时调用 | 不注册 |
| `setEventEnableChance(int)` / `setEventEnableChance(IntSupplier)` | 不注册（触发点自己查 `isEventEnabled()`） | 不注册 |

每个 `chance` 为 `int` 的重载都有对应的 `IntSupplier` 版本（概率每次掷骰重新读，适合绑配置）。

局末回调还可以**单独挂**：`setRoundEventEndHandler(Consumer<ServerLevel>)` 不改变已声明的掷骰回调与概率，
可以链在任意声明之后（包括上面的纯查询式），先挂后挂都行，也不会被后续的 `setEventEnableChance` 覆盖。

```java
// 结果回调 + 局末收尾（假史蒂夫的写法）
.setEventEnableChance(MyEvent::onRollResult, MyEvent::onRoundEnd, 6000)

// 只关心掷中 + 局末收尾
.setEventEnableChance(level -> startMyEvent(level), MyEvent::onRoundEnd, 6000)

// 不要局末回调：第二个参数传 null，或直接用两参重载
.setEventEnableChance(MyEvent::onRollResult, null, 6000)

// 概率读配置
.setEventEnableChance(MyEvent::onRollResult, MyEvent::onRoundEnd,
        () -> MyConfig.instance().myEventChance)

// 只要概率，触发点自己查询
.setEventEnableChance(6000)

// 查询式声明 + 单独挂收尾（不需要转型、也不用写空掷骰回调）
.setEventEnableChance(() -> MyConfig.instance().myEventChance)
.setRoundEventEndHandler(MyEvent::onRoundEnd)
```

#### 判定顺序 / Roll order

每局**正式开局**（`OnGameTrueStarted`，安全时间结束后）掷一次（维度通用，判定用的世界默认主世界），依次检查：

1. **地图限制** —— `setSpecialMapRole` / `setSpecialMapRolesCondition` / `setCanSpawnInMap`；
   地图信息缺失时只有完全不限制地图的职业算通过；
2. **职业禁用状态** —— `SREDisableManager#isRoleDisabled`（含地图 `disabledRoles`、配置禁用、轮选）；
3. **概率** —— 已被 `forceEventEnableNextRound()` 强制的职业跳过这一步。

三项都通过才算本局启用。注意是**每局一次**，不是每次查询都重掷。

#### 两个回调 / Two callbacks

- **掷骰回调**：每局开局掷骰后调用一次，能看到刚写入的本局状态。
  带 `boolean` 的版本**没掷中也调用**（`(level, false)`），用于处理「启用失败」（例如复位、公告）；
  `Consumer` 版本只在本局掷中时调用，开场与收尾天然配对。
- **局末回调**：局末（`OnGameEnd`）清理时调用，**只在本局掷中过时才调用**，且**先于状态清空**执行——
  回调里 `isEventEnabled()` 仍反映本局结果，方便判断「本局事件跑过了」再做收尾。
  只注册掷骰回调（不注册局末回调）时，局末不会收到任何通知。
- 状态不按维度分开：同一职业一份，多维度服务器共用。

#### 查询 / Querying

```java
// 只声明概率、触发点自己判断（写法 ④ 的配套用法）
if (BounsRoles.PURPLE_MONSTER.isEventEnabled() && canTrigger(level)) {
    startEvent(level);
}

boolean declared = role.hasRoundEvent();   // 本职业是否声明过事件（即是否参与每局掷骰）
```

`isEventEnabled()` 在「未声明事件 / 本局未掷中 / 局末已清理 / 职业当前被禁用」时都返回 `false`
（禁用状态是查询时复查的，不只看掷骰那一刻）。

#### 强制下一局 / Force next round

```java
// 管理员命令 /sre:fake_steve next 就是这么实现的
if (!FakeSteveDirector.canGenerate(level)) {              // 自己的前置检查
    failure(...);
    return 0;
}
if (!ModRoles.FAKE_STEVE.forceEventEnableNextRound()) {   // false = 已经排过队（无需世界参数）
    success("已经排过下一局了");
    return 1;
}

// 回调里区分「命令强开」与「自然掷中」（例如日志文案 / ActivationSource）
boolean forced = ModRoles.FAKE_STEVE.wasEventForceEnabled();
boolean pending = ModRoles.FAKE_STEVE.isEventForcePending();
```

请求**跨局保留**（局末清理不会清掉它），在下一次开局掷骰时被消费。它只保证跳过概率判定：
地图限制与禁用状态仍然生效，**若那一局被这两项拦下，请求即被消费而不会顺延到再下一局**。

#### 最小完整例子 / Minimal example

```java
public static final SRERole MY_ROLE = TMMRoles.registerRole(new NormalRole(...))
        // 只在实验室地图
        .setSpecialMapRolesCondition(features -> features.contains(MapSpecialFeatures.LAB))
        // 每局 25%；第一个是掷骰回调，第二个是局末收尾
        .setEventEnableChance(MyEventHandler::onRollResult, MyEventHandler::onRoundEnd, 2500);

// 事件本体放在自己的类里，静态方法即可
public static void onRollResult(ServerLevel level, boolean enabled) {
    if (!enabled) {          // 没掷中也会走到这里，可用来处理「启用失败」
        return;
    }
    scheduleMyEvent(level);  // 本局事件开始
}

/** 只在本局掷中过时才会走到这里；此刻 isEventEnabled() 仍为 true。 */
public static void onRoundEnd(ServerLevel level) {
    cleanupMyEvent(level);
}
```

#### 相关 API / API summary

| 成员 | 说明 |
|------|------|
| `SRERole#setEventEnableChance(...)` | 声明事件：掷骰回调（带结果 / 仅启用）+ 局末回调 + 概率，见上表 |
| `SRERole#setRoundEventEndHandler(Consumer<ServerLevel>)` | 单独挂/追加局末回调，不改变已声明的掷骰回调与概率 |
| `SRERole#hasRoundEvent()` | 是否声明过事件（决定是否参与每局掷骰） |
| `SRERole#isEventEnabled()` | 本局是否启用（维度通用，查询时复查禁用状态） |
| `SRERole#forceEventEnableNextRound()` | 强制下一局必定掷中；返回 false 表示已排队 |
| `SRERole#wasEventForceEnabled()` | 本局是否由命令强开 |
| `SRERole#isEventForcePending()` | 是否有等待生效的强制请求 |
| `RoleRoundEvent` | 上述机制的实现：每个职业一个，一份维度通用的本局状态 + 掷骰/局末回调 + 跨局强制请求 |
| `TMMRoles` | 维护「声明过事件的职业」列表，注销职业时自动移出 |

---

## 修饰符系统 / Modifier System

修饰符（Modifier）是叠加在职业上的附加属性/能力，一名玩家可同时拥有多个修饰符。  
Modifiers are additive traits/abilities stacked on top of a role; a player can hold multiple simultaneously.

### SREModifier — 修饰符基类

**包 / Package:** `org.agmas.harpymodloader.modifiers`

#### 构造函数 / Constructor

```java
new SREModifier(
    ResourceLocation identifier,         // 修饰符唯一 ID
    int color,                           // 通告颜色（ARGB 整数）
    @Nullable ArrayList<SRERole> cannotBeAppliedTo,  // 不能应用到的职业列表（null = 无限制）
    @Nullable ArrayList<SRERole> canOnlyBeAppliedTo, // 只能应用到的职业列表（null = 无限制）
    boolean killerOnly,                  // 仅限杀手
    boolean civilianOnly                 // 仅限平民阵营
)
```

#### 链式配置方法 / Fluent Setters

```java
SREModifier setMax(int count)                                    // 同场最大数量（-1 无限制）
SREModifier setServerGameTickEvent(Consumer<ServerPlayer> event) // 服务端每 Tick 回调
SREModifier setClientGameTickEvent(Consumer<Player> event)       // 客户端每 Tick 回调
void setCannotBeAppliedTo(ArrayList<SRERole> list)               // 设置排除职业列表
void setCanOnlyBeAppliedTo(ArrayList<SRERole> list)              // 设置白名单职业列表
```

#### 查询方法 / Getters

```java
ResourceLocation identifier()                // 获取 ID
int color()                                  // 获取颜色
ArrayList<SRERole> cannotBeAppliedTo()       // 获取排除职业列表
ArrayList<SRERole> canOnlyBeAppliedTo()      // 获取白名单职业列表
MutableComponent getName()                   // 获取翻译名称（无颜色）
MutableComponent getName(boolean withColor)  // 获取翻译名称（可带颜色）
```

#### 翻译键 / Translation Key

```
announcement.star.modifier.<namespace>.<path>
// 或（兼容 starrailexpress 命名空间简写）：
announcement.star.modifier.<path>
```

---

### HMLModifiers — 修饰符注册表

**包 / Package:** `org.agmas.harpymodloader.modifiers`

```java
// 全部已注册修饰符列表
ArrayList<SREModifier> HMLModifiers.MODIFIERS

// path -> 修饰符（忽略命名空间），重复注册校验用
Map<String, SREModifier> HMLModifiers.MODIFIERS_BY_PATH

// 注册修饰符（返回修饰符本身，支持链式）
// 同一 path 重复注册会抛 IllegalArgumentException
SREModifier HMLModifiers.registerModifier(SREModifier modifier)

// 注册配置驱动的自定义修饰符：重复注册不抛异常，记 error 日志并返回 null
SREModifier HMLModifiers.registerCustomModifier(SREModifier modifier)

// 注销修饰符（同时清理 path 索引）
boolean HMLModifiers.unregisterModifier(SREModifier modifier)

// 按 path 查修饰符
SREModifier HMLModifiers.getModifierByPath(String path)
```

#### 重复注册

修饰符按 **identifier 的 path 全局去重**（忽略命名空间，与职业的 `TMMRoles.registerRole` 一致）：

- 内置/静态注册（`registerModifier`）：同 path 已存在 → 抛 `IllegalArgumentException`，启动期就直接暴露问题；
- 自定义修饰符（`registerCustomModifier`）：同 path 已存在 → 打 error 日志并返回 `null`，调用方跳过该条配置；
- 因此自定义修饰符**不能与内置修饰符同 path**（内置已有 `frail`，自定义就不能再叫 `frail`）。

注销请用 `unregisterModifier`，不要直接操作 `MODIFIERS`：索引与列表不同步的话，之后同名修饰符会被误判成重复而注册失败（自定义修饰符热重载依赖这一点）。

服务端 `/sre:reload custom_modifiers` 重载时若有 id 冲突，会跳过该条配置并**向全体玩家播报**（`sre.custom_modifier.error.duplicated`），与自定义职业的重复提示一致。

#### 完整注册示例

```java
// 1. 声明 ID
public static final ResourceLocation MY_MODIFIER_ID = MyMod.id("my_modifier");

// 2. 注册修饰符
public static final SREModifier MY_MODIFIER = HMLModifiers.registerModifier(
    new SREModifier(
        MY_MODIFIER_ID,
        0xFF5500,  // 橙色
        null,      // 不排除任何职业
        null,      // 不限制职业
        false,     // 不仅限杀手
        true       // 仅限平民阵营
    )
    .setDefaultMax(2)     // 同场最多 2 人拥有
    .setServerGameTickEvent(player -> {
        // 每 Tick 执行的服务端逻辑
    })
);
```

#### 添加配置（可选）

修饰符每局分配数量受 `HarpyModLoaderConfig` 中两个参数控制：  
- `modifierMaximum`：每名玩家最多修饰符数量（默认 4）
- `modifierMultiplier`：按玩家总数乘以该系数分配修饰符（默认 0.5）

可通过 `/setEnabledModifier` 指令在游戏内禁用/启用修饰符。

---

### WorldModifierComponent — 修饰符 CCA

**包 / Package:** `org.agmas.harpymodloader.component`  
**组件键 / Component Key:** `WorldModifierComponent.KEY`（Level 级 CCA）

```java
// 获取组件
WorldModifierComponent wmc = WorldModifierComponent.KEY.get(player.level());
```

| 方法 | 说明 |
|------|------|
| `boolean isModifier(Player player, SREModifier modifier)` | 判断玩家是否拥有该修饰符 |
| `boolean isModifier(UUID uuid, SREModifier modifier)` | 同上（UUID 版） |
| `Set<SREModifier> getModifiers(Player player)` | 获取玩家所有修饰符 |
| `Set<SREModifier> getModifiers(UUID uuid)` | 同上（UUID 版） |
| `Map<UUID, Set<SREModifier>> getModifiers()` | 获取全局修饰符映射 |
| `List<UUID> getAllWithModifier(SREModifier modifier)` | 获取拥有该修饰符的所有玩家 |
| `void addModifier(UUID player, SREModifier modifier)` | 为玩家添加修饰符（并同步） |
| `void removeModifier(UUID player, SREModifier modifier)` | 移除玩家修饰符（并同步） |
| `Set<SREModifier> getDisplayableModifiers(Player player)` | 获取可展示给该玩家的修饰符列表 |

> **注意：** 修饰符添加/移除会分别触发 `ModifierAssigned.EVENT` / `ModifierRemoved.EVENT`，见[HML 事件](#hml-事件)。

---
## 职业数据 / RoleData

### 注册
你可以在注册职业的时候（`SRERole`）声明RoleData：
```java
.setRoleData(RoleData实例类::new)
```

如：
注册部分：
```java
// 会计角色 - 乘客阵营
public static SRERole ACCOUNTANT = TMMRoles.registerRole(new NormalRole(
  /* 省略... */
)).setRoleData(AccountantRoleData::new);
```
`RoleData` 部分：
```java
public class AccountantRoleData extends SimpleRoleData {
    /* 具体逻辑... */
}
```
### RoleData 实例类 / RoleData Instance
`RoleData` 实例类：可以 `extends SimpleRoleData`，或是 `implements RoleData`

因为每次实例都是创建新的，理论上你不需要去单独写 `init` 和 `clear` 然后同步，这会导致更多的网络流量（在玩家分配到职业时会自动要求客户端创建类）。

获取此实例类方法：
```java
RoleData.getNullable(类.class, 玩家)
```
或者 
```java
RoleData.getOptional(类.class, 玩家);
```

#### 原理
我们将大量职业使用同一个CCA：`SRERoleDataPlayerComponent` 进行管理。仅同步当前职业需要的数据，能够有效避免一些无关职业的数据的同步（比如清除状态等）。

当玩家切换到此职业时，触发 `init` 事件，服务端调用 `serverInit` 创建 `RoleData` 实例后发送同步包要求客户端创建实例。

客户端接受到请求后会调用 `clientInit` 创建实例。

若客户端没接收到创建实例申请，但受到正常同步包，客户端会先尝试初始化再处理同步包。

而当玩家变成新职业时，会调用旧的 `RoleData` 的 `clear` 事件，然后直接抛弃旧的 `RoleData` 数据。

这也是为什么可以不用写 `init` 和 `clear` 来初始化的原因。

**不要**把会在中途换职业、却还要保留的状态放进 RoleData（例如傀儡师操控假人时临时变成杀手）。那种状态继续用玩家 CCA。

跨玩家状态（任何人身上的感染、被操纵、被浇油若记在受害者身上）也不适合 RoleData；能改成「记在技能持有者的 RoleData 里再按 UUID 查找」的，优先那么做（纵火犯浇油即如此）。

---

## CCA 组件 / CCA Components

CCA 用于世界/对局、以及必须挂在「当前职业以外」的玩家状态。职业私有状态用上一节的 `RoleData`。

### RoleComponent — 角色组件接口

**包 / Package:** `io.wifi.starrailexpress.api`

对于职业，我们更建议您使用我们的新接口：`RoleData`

详情请查看 `SimpleRoleData` 和 `RoleData` 类。

（相关介绍在前文）

所有角色 CCA 组件需实现的接口，已继承 `AutoSyncedComponent`。  
Interface all role CCA components must implement; extends `AutoSyncedComponent`.

```java
public interface RoleComponent extends AutoSyncedComponent {
    Player getPlayer();     // 获取关联玩家
    void init();            // 角色分配时初始化（清空状态）
    void clear();           // 游戏结束时清除

    // 同步数据写入（服务端 → 客户端）
    void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);
    // 同步数据读取（客户端接收）
    void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup);
}
```

> **提示 / Tip:** 默认 `shouldSyncWith` 只同步给玩家自己。如需广播给其他玩家，请覆盖该方法。  
> By default, `shouldSyncWith` only syncs to the player themselves. Override it to broadcast to others.

#### 推荐实践 / Best Practices

- 尽量能不同步不要同步，减少网络压力。  
  Avoid unnecessary sync to reduce network load.
- 存储时间相关数据时，使用"结束时间戳"而非每 tick 递减的倒计时。  
  Store end-timestamps instead of decrementing countdowns each tick.
- 如需倒计时，在客户端本地模拟计算，服务端约 10 秒同步一次。  
  For countdowns, simulate locally on client; sync from server roughly every 10 seconds.

---

### SREAbilityPlayerComponent — 通用技能组件

**包 / Package:** `io.wifi.starrailexpress.cca`  
**组件键 / Component Key:** `SREAbilityPlayerComponent.KEY`

管理角色技能冷却与使用次数，并自动在客户端/服务端之间同步（用于 HUD 显示）。  
Manages skill cooldowns and charge counts with automatic client/server sync for HUD display.

```java
// 从玩家获取组件
SREAbilityPlayerComponent comp = SREAbilityPlayerComponent.KEY.get(player);
// 或通过 SRERole 辅助方法
SREAbilityPlayerComponent comp = SREAbilityPlayerComponent.KEY.get(player);
```

| 字段 / Field | 类型 | 说明 |
|---|---|---|
| `cooldown` | `int` | 当前冷却（tick） |
| `charges` | `int` | 剩余使用次数（`-1` 无限） |
| `maxCharges` | `int` | 最大使用次数（HUD 显示用） |
| `status` | `int` | 自定义状态值（`-1` 表示无） |

| 方法 / Method | 说明 |
|---|---|
| `void setCooldown(int ticks)` | 设置冷却并自动同步 |
| `void setCharges(int charges)` | 设置次数并自动同步 |
| `void init()` | 重置所有字段 |
| `void clear()` | 等同于 `init()` |

---

## 技能系统 / Skill System

### RoleSkill — 技能注册

**包 / Package:** `io.wifi.starrailexpress.api`（不是 `org.agmas.noellesroles`）

服务端 G 键技能的注册与触发中心。玩家按下技能键时，客户端自动发送 `AbilityC2SPacket`，服务端通过 `RoleSkill` 分发处理。  
Central registry and dispatcher for server-side G-key role skills. The client auto-sends `AbilityC2SPacket` on G-key press; the server dispatches via `RoleSkill`.

#### 数据类 / Data Class

```java
public record RoleSkillContext(ServerPlayer player, @Nullable UUID target) {}
```

#### 注册技能 / Registering Skills

```java
// 注册技能处理器
RoleSkill.register(MY_ROLE_ID, (context) -> {
    ServerPlayer player = context.player();
    // 实现技能逻辑...
});

// 或通过 SRERole 对象注册
RoleSkill.register(ModRoles.MY_ROLE, (context) -> {
    // ...
});

// 带目标的技能（需客户端发送 AbilityWithTargetC2SPacket）
RoleSkill.beginUseShiftedWithTarget(player, targetUUID);   // 潜行副技能；普通技能用 beginUse(player)
```

#### 其他方法 / Other Methods

```java
boolean isRegistered(ResourceLocation role)   // 是否已注册
boolean isRegistered(SRERole role)
boolean unregister(ResourceLocation role)     // 注销技能
boolean tryRegister(ResourceLocation, Consumer<RoleSkillContext>)  // 失败时返回 false 而不抛异常

// 手动触发技能（服务端，含 BEFORE/AFTER 事件）
boolean beginUse(ServerPlayer player)
boolean beginUseWithTarget(...)  // ← 不存在，实际是下面两个
boolean beginUseShiftedWithTarget(ServerPlayer player, UUID target)
boolean beginUse(ServerPlayer player, @Nullable UUID target, int requestedSlot, Phase phase)
```

#### 技能前后钩子 / Before/After Hooks

技能触发前后会分别调用 `OnRoleSkillUse.BEFORE` 和 `OnRoleSkillUse.AFTER` 事件（见[事件系统](#事件系统--event-system)）。  
Before/after skill use, the `OnRoleSkillUse.BEFORE` and `.AFTER` events are fired (see [Event System](#事件系统--event-system)).

---

## 商店系统 / Shop System

### ShopEntry — 商店条目

**包 / Package:** `io.wifi.starrailexpress.util`

```java
// 基础条目
new ShopEntry(ItemStack itemStack, int price, ShopEntry.Type type)

// 自定义购买逻辑
new ShopEntry(itemStack, price, type) {
    @Override
    public boolean onBuy(@NotNull Player player) {
        // 自定义购买逻辑
        // 返回 true → 自动扣除金币并给予物品
        // 返回 false → 购买失败
        return true;
    }
}
```

#### Type 枚举

| 值 | 说明 |
|---|---|
| `WEAPON` | 武器类 |
| `TOOL` | 工具类 |
| `POISON` | 毒药类 |

### ShopContent — 商店内容管理

**包 / Package:** `io.wifi.starrailexpress.game`

```java
// 注册自定义角色商店
ArrayList<ShopEntry> shop = new ArrayList<>();
shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), 75, ShopEntry.Type.TOOL));
ShopContent.customEntries.put(ModRoles.MY_ROLE.getIdentifier(), shop);
```

> **提示 / Tip:** 也可在 `SRERole.getShopEntries()` 中直接返回商店列表，优先级高于 `customEntries`。  
> Alternatively, override `SRERole.getShopEntries()` directly; this takes priority over `customEntries`.

商店购买前会触发 `OnVendingMachinesBuyItems.EVENT` 事件（见[事件系统](#事件系统--event-system)）。  
Before purchase, `OnVendingMachinesBuyItems.EVENT` fires (see [Event System](#事件系统--event-system)).

### DynamicShopComponent — 动态价格组件

**包 / Package:** `io.wifi.starrailexpress.cca`

按玩家维度存储「商品价格修正」与「购买次数」，让**局内商店**（杀手 / 职业商店）的商品拥有动态价格：
百分比折扣、固定减价、价格乘数（`<1` 打折、`>1` 溢价）。修正以**物品 ID**（`ResourceLocation`）为键，
因此同一件商品在不同玩家身上可以有不同的实时价格。组件会自动同步给所有者客户端，因此商店 UI 显示的价格
与服务端实际扣费一致。  
Stores per-player price modifiers and purchase counts (keyed by item id) so **in-game shop** items can have
dynamic prices: percentage discounts, flat reductions, or multipliers. Auto-syncs to the owner so the shop UI
shows exactly what the server will charge.

价格公式 / Price formula：`effective = max(0, round(basePrice * multiplier) - flatReduction)`。

```java
DynamicShopComponent dyn = DynamicShopComponent.KEY.get(player);

// 价格修正 / price modifiers（以物品 ID 为键）
dyn.setPercentDiscount(itemId, 50);      // 降价 50% / -50%
dyn.setFlatReduction(itemId, 100);       // 固定减 100 / -100 flat
dyn.setMultiplier(itemId, 1.5);          // 溢价 1.5 倍 / surge x1.5
dyn.setModifier(itemId, 0.5, 20);        // 先 x0.5 再 -20 / multiply then subtract
dyn.clearModifier(itemId);               // 移除单件修正
dyn.clearAllModifiers();                 // 清空全部修正（保留购买次数）

boolean has = dyn.hasModifier(itemId);
int price  = dyn.effectivePrice(itemId, basePrice);  // 计算实际价格
int price2 = dyn.effectivePrice(shopEntry);           // 便捷重载：用条目物品+基础价

// 购买次数 / purchase counts（可用于「首购触发」等逻辑）
int count = dyn.getPurchaseCount(itemId);
dyn.recordPurchase(itemId);              // 记录一次购买并返回新次数
```

> **接入点 / Integration:** `SREPlayerShopComponent.tryBuy` 在校验余额与扣费时统一改用
> `DynamicShopComponent.effectivePrice(entry)`；无任何修正时实际价格 == 基础价格，因此对未使用本系统的
> 商品零影响。组件在游戏开始（`GameUtils` 初始化）与 `resetPlayer` 时随其它玩家组件一并 `init()` / `clear()`，
> 仅同步、不写入磁盘（局内状态）。  
> `tryBuy` resolves the charged price through `effectivePrice`; with no modifier the effective price equals the
> base price, so unmodified items are unaffected. The component is reset alongside the other player components on
> game start and `resetPlayer`; it is sync-only (not persisted to disk).

### /dynamicshop 指令

运行时为玩家设置局内商店商品的动态价格（权限等级 2）。  
Configure dynamic shop prices per player at runtime (permission level 2).

```
/dynamicshop discount   <players> <item> <percent 0-100>   # 设置百分比折扣
/dynamicshop flat       <players> <item> <amount>          # 设置固定减价
/dynamicshop multiplier <players> <item> <multiplier>      # 设置价格乘数
/dynamicshop clear      <players> <item>                   # 清除单件修正
/dynamicshop clearall   <players>                          # 清除全部修正
/dynamicshop query      <players> <item>                   # 查询修正/购买次数
```

示例 / Example：`/dynamicshop discount @a sre:knife 50` 让所有玩家的刀降价 50%。

### 示例：杀手刀有限耐久 + 首购折扣

`DynamicShopComponent` 的一个完整落地示例，**仅在 `murder` 或继承 `SREMurderGameMode` 的模式下生效**，
并且耐久部分可由配置项 `SREConfig.knifeDurabilityMode`（商店分类，默认开启，已同步到客户端）一键开关：  
A full example built on `DynamicShopComponent`, active **only in `murder` (or modes extending
`SREMurderGameMode`)**, with the durability part toggled by `SREConfig.knifeDurabilityMode`
(shop category, on by default, synced to clients):

- 杀手通过商店购买的刀只有 **3 点耐久**（`KillerKnifeDurability.MAX_DURABILITY`），每次成功捅人消耗 1 点；
- 耐久耗尽后刀**不会消失**，但无法继续使用；
- 再次购买刀时：若背包内已有「耗尽」的刀则**原地刷新为满耐久**（替换），否则按原逻辑发放一把新刀；
- **首次购买刀后**，后续购买价格 **-50%**（首购全价，从第二把起半价；该折扣只受 murder 模式约束，不随耐久开关关闭）；
- 当配置 `knifeDurabilityMode` 开启时，刀的物品说明（item desc）会追加耐久提醒，并显示「剩余耐久 X/3」；关闭后恢复普通无耐久刀。

涉及类 / Classes:

| 类 | 包 | 职责 |
|---|---|---|
| `KillerKnifeShopEntry` | `io.wifi.starrailexpress.game` | 自定义 `ShopEntry`：替换耗尽刀 / 发放带耐久新刀 / 首购挂折扣 |
| `KillerKnifeDurability` | `io.wifi.starrailexpress.game` | 耐久工具：标记、消耗、查找耗尽刀、模式判定 |

```java
// KillerKnifeShopEntry.onBuy 核心逻辑（murder 模式）
ItemStack depleted = KillerKnifeDurability.findDepletedKnife(player);
if (depleted != null) {
    KillerKnifeDurability.applyFreshDurability(depleted);   // 替换：原地刷满耐久
} else {
    ItemStack fresh = this.stack().copy();
    KillerKnifeDurability.applyFreshDurability(fresh);       // 新刀：带 3 点耐久
    RoleUtils.insertStackInFreeSlot(player, fresh);
}
// 首购后挂 -50% 折扣
DynamicShopComponent dyn = DynamicShopComponent.KEY.get(player);
if (dyn.getPurchaseCount(knifeId) == 0) dyn.setPercentDiscount(knifeId, 50);
dyn.recordPurchase(knifeId);
```

耐久通过逐栈的 `MAX_DAMAGE` / `DAMAGE` 数据组件实现（**不修改物品注册**），因此只影响被标记过的刀；
消耗与「耗尽不可用」的判定在 `KnifeStabPayload` 服务端接收处理，且以模式 + 标记双重门控，确保其它来源的刀
（初始物品、其它模式、亡命徒等）不受影响。  
Durability uses per-stack `MAX_DAMAGE`/`DAMAGE` data components (no item-registration change); consumption and the
"depleted = unusable" check live in the server-side `KnifeStabPayload` handler, gated by both game mode and the
stamp so knives from other sources/modes are untouched.

---

## 蓄力物品系统 / Chargeable Item System

### ChargeableItem — 蓄力物品接口

**包 / Package:** `io.wifi.starrailexpress.api`

允许第三方 mod 为物品添加自定义蓄力行为。  
Allows third-party mods to add custom charging behavior to items.

```java
public interface ChargeableItem {
    // 最大蓄力时间（tick）
    int getMaxChargeTime(ItemStack stack, Player player);

    // 当前蓄力百分比（0.0 ~ 1.0）
    float getChargePercentage(ItemStack stack, Player player, int ticksUsingItem);

    // 蓄力完成回调（默认空实现）
    default void onFullyCharged(ItemStack stack, Player player) {}

    // 最大体力值（默认 8.0）
    default float getMaxStamina(ItemStack stack, Player player) { return 8.0f; }

    // 是否启用特殊视觉效果（如屏幕边缘闪烁，默认 false）
    default boolean hasSpecialVisualEffects(ItemStack stack, Player player) { return false; }
}
```

### ChargeableItemRegistry — 蓄力物品注册表

**包 / Package:** `io.wifi.starrailexpress.api`

```java
// 不需要注册了，只需要给Item类implements ChargeableItem 即可！

// 查询
boolean chargeable = ChargeableItemRegistry.isChargeable(item);
ChargeableItem impl = ChargeableItemRegistry.getChargeable(item);

// 获取蓄力信息（用于 HUD 显示）
ChargeableItemRegistry.ChargeInfo info = ChargeableItemRegistry.getChargeInfo(stack, player);
// info.maxChargeTime, info.currentTicksUsing, info.chargePercentage, info.maxStamina, info.hasSpecialVisualEffects

// 触发蓄力完成回调
ChargeableItemRegistry.onFullyCharged(stack, player);
```

---

## 物品类型 / Item Types

### 可继承物品基类

以下是游戏中可被继承扩展的物品基类，每个都提供了特定的游戏机制钩子。

| 类 | 包 | 说明 |
|---|---|---|
| `SkinableItem` | `io.wifi.starrailexpress.content.item` | 抽象基类，支持皮肤系统的物品 |
| `KnifeItem` | `io.wifi.starrailexpress.content.item` | 近战刀（继承 `SkinableItem`），蓄力刺杀；子类可覆写蓄力钩子 |
| `RevolverItem` | `io.wifi.starrailexpress.content.item` | 左轮手枪（继承 `SkinableItem`），有耐久度 |
| `BatItem` | `io.wifi.starrailexpress.content.item` | 球棒（继承 `SkinableItem`） |
| `GrenadeItem` | `io.wifi.starrailexpress.content.item` | 手雷（继承 `SkinableItem`），蓄力投掷 |
| `DefenseItem` | `io.wifi.starrailexpress.content.item` | 防具/防御物品（继承 `Item`），限制使用职业 |
| `NoteItem` | `io.wifi.starrailexpress.content.item` | 便签（继承 `Item` + `AdventureUsable`） |

#### KnifeItem — 蓄力钩子

`KnifeItem` 右键会 `startUsingItem`，松开走 `releaseUsing`。子类可覆写：

```java
boolean canStartKnifeCharge(Level world, Player user, InteractionHand hand, ItemStack stack)
void onKnifeChargeStarted(Level world, Player user, InteractionHand hand, ItemStack stack)
boolean onKnifeChargeReleased(ItemStack stack, Level world, Player attacker, int usedTicks) // true=已处理，不再默认刺杀
int getMinKnifeChargeTicks(ItemStack stack, LivingEntity user)
```

蓄满 `getUseDuration` 时会走 `finishUsingItem` → `releaseUsing`。

#### DefenseItem — 防御物品

`DefenseItem` 是防具类物品的基类，使用动画为 `DRINK`。可通过 `canUseByRightClickRolePaths` 白名单限制使用该物品的职业路径（path 字符串）：

```java
// 允许特定职业路径使用（path = identifier().getPath()）
DefenseItem.canUseByRightClickRolePaths.add("my_role");
```

---

### SkinableItem — 可换皮肤物品

**包 / Package:** `io.wifi.starrailexpress.content.item`

继承此抽象类以创建支持皮肤系统的物品。  
Extend this abstract class to create an item that supports the skin system.

```java
public class MyWeapon extends SkinableItem {
    public MyWeapon(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemSkinType() {
        // 返回皮肤类型名称（需与 ItemSkinManager.registerType 中注册的名称一致）
        return "my_weapon";
    }

    @Override
    public String getDefaultSkin() {
        return "default";
    }

    @Override
    public String[] getAvailableSkins() {
        // 返回该物品支持的皮肤名称数组
        return new String[]{ "default", "gold", "iron" };
    }
}
```

| 方法 | 说明 |
|---|---|
| `abstract String getItemSkinType()` | **必须实现**，返回皮肤类型字符串 |
| `String getDefaultSkin()` | 默认皮肤名（默认 `"default"`） |
| `String[] getAvailableSkins()` | 支持的皮肤列表（用于 UI 展示） |

---

## 皮肤系统 / Skin System

### ItemSkinManager — 皮肤工具类

**包 / Package:** `io.wifi.starrailexpress.util`

皮肤系统的核心管理类，负责皮肤注册、查询、锁定/解锁，以及玩家皮肤状态持久化。  
Core skin system manager: handles registration, querying, lock/unlock, and player skin persistence.

#### 注册自定义皮肤

```java
// 1. 注册皮肤类型（须在 ItemSkinManager 静态初始化顺序之后，建议在 mod onInitialize 中调用）
ItemSkinManager.registerType("my_weapon");

// 2. 注册具体皮肤（type, skinID, color）
ItemSkinManager.registerACustomSkin("my_weapon", "default", Colors.LIGHT_GRAY);
ItemSkinManager.registerACustomSkin("my_weapon", "gold",    0xFFD700);
ItemSkinManager.registerACustomSkin("my_weapon", "iron",    0xAAAAAA);
```

#### 皮肤数据操作

```java
// 检查玩家是否解锁了某皮肤
boolean unlocked = ItemSkinManager.isSkinUnlocked(player, itemStack, "gold");

// 解锁皮肤给玩家
ItemSkinManager.unlockSkin(player, itemStack, "gold");

// 按物品类型解锁皮肤（无 ItemStack 版本）
ItemSkinManager.unlockSkinForItemType(player, "my_weapon", "gold");

// 锁定皮肤（移除解锁状态）
ItemSkinManager.lockSkin(player, itemStack, "gold");

// 获取玩家当前装备的皮肤
String skinName = ItemSkinManager.getEquippedSkin(player, itemStack);

// 设置玩家当前装备皮肤
ItemSkinManager.setEquippedSkin(player, itemStack, "gold");
ItemSkinManager.setEquippedSkinForItemType(player, "my_weapon", "gold");

// 同步皮肤数据给客户端
ItemSkinManager.sync(player);
```

#### 皮肤彩券货币

皮肤系统内置两种货币用于彩券（开箱）系统：

```java
// 获取/增加彩券抽取次数
int chances = ItemSkinManager.getLootChance(player);
ItemSkinManager.addLootChance(player, 1);

// 获取/增加皮肤货币数量
int coins = ItemSkinManager.getCoinNum(player);
ItemSkinManager.addCoinNum(player, 100);
```

#### ItemSkinManager.Skin — 皮肤数据类

```java
ItemSkinManager.Skin skin = ItemSkinManager.Skin.fromString("my_weapon", "gold");
int color = skin.getColor();   // 颜色值
String name = skin.getName();  // 皮肤小写名称
String tooltip = skin.tooltipName; // Tooltip 显示名
```

#### 内置皮肤类型 / Built-in Skin Types

| 常量 | 字符串值 |
|---|---|
| `ItemSkinManager.SkinTypes.KNIFE` | `"knife"` |
| `ItemSkinManager.SkinTypes.REVOLVER` | `"revolver"` |
| `ItemSkinManager.SkinTypes.BAT` | `"bat"` |
| `ItemSkinManager.SkinTypes.GRENADE` | `"grenade"` |
| `ItemSkinManager.SkinTypes.HAT` | `"hat"` |

#### 皮肤品质颜色 / QualityColor

```java
ItemSkinManager.QualityColor.COMMON       // 0xFFEEEEEE 白灰
ItemSkinManager.QualityColor.UNCOMMON     // 0xFF33FF55 绿色
ItemSkinManager.QualityColor.RARE         // 0xFFAAAAFF 蓝色
ItemSkinManager.QualityColor.EPIC         // 0xFFAA55FF 紫色
ItemSkinManager.QualityColor.LEGENDARY    // 0xFFFFAA55 金色
ItemSkinManager.QualityColor.UNBELIEVABLE // 0xFFFF3F3F 红色
```

---

## 变形 API / Morph API

**包 / Package:** `io.wifi.starrailexpress.morph`

统一玩家外观变形：指定玩家、随机玩家、指定贴图。变形为玩家时，皮肤 / 帽子 / 名牌 / 身份玩偶一律跟随显示对象（与帽子绑定相同），避免用赞助玩偶识人。

```java
import io.wifi.starrailexpress.morph.MorphApi;

// 变形成指定玩家（帽子、名牌、赞助玩偶一并绑定）
MorphApi.morphToPlayer(serverPlayer, target.getUUID());
MorphApi.morphToPlayer(serverPlayer, target.getUUID(), 20 * 15); // 15 秒后自动解除

// 随机变形成一名存活玩家
MorphApi.morphToRandomPlayer(serverPlayer);
MorphApi.morphToRandomPlayer(serverPlayer, candidate -> GameUtils.isPlayerAliveAndSurvival(candidate), 0);

// 使用其他贴图变形（无真实玩家可复制，帽子/名牌前缀/玩偶隐藏）
MorphApi.morphToTexture(serverPlayer, SRE.id("textures/entity/disguise/disguise_skin_1.png"), false);

// 解除变形
MorphApi.clearMorph(serverPlayer);

MorphAppearance appearance = MorphApi.getAppearance(player);
boolean morphed = MorphApi.isMorphed(player);

// 剩余时间（服务端可读，不动包体）：-1 = 无限期，0 = 未变形 / 已到期
int remaining = MorphApi.getRemainingTicks(player);
boolean permanent = MorphApi.isPermanent(player);
Set<UUID> morphedPlayers = MorphApi.getMorphedPlayers();   // clearall 之类批量操作用
```

服务端也可以直接用指令操作：`/sre:morph start infinite|<秒数> player|random|texture ...`、`/sre:morph clear|clearall|query`（见 `docs/commands.md`）。

客户端查询当前应显示的拥有者 / 名称 / 玩偶：

```java
UUID owner = MorphApiClient.resolveDisplayedOwnerUuid(clientPlayer);   // 这三个是客户端专用，在 MorphApiClient 上
Component name = MorphApiClient.getDisplayedName(clientPlayer);
ItemStack plush = MorphApiClient.getDisplayedPlushStack(clientPlayer);
```

---

## 实体伪装 API / Entity Disguise API

**包 / Package:** `io.wifi.starrailexpress.disguise`

把玩家整体伪装成**任意已注册实体**：客户端用目标实体的渲染器绘制玩家（玩家本体、名牌、帽子、手持物等附属渲染一起被替换），同时把眼高压到该实体眼高。

**碰撞箱尺寸保持不变**。地图是按人的尺寸做的，只改眼睛高度，于是相机、准星射线、枪械命中判定这些读 `getEyeY()` 的地方一起下移，画面与命中点不会错开。

实现上不枚举任何具体实体：状态只存「实体类型 + 外观 NBT」，眼高在设置伪装时用一个临时实体算一次，之后查询都是 O(1) 查表，渲染与命中路径上不创建实体。因此其他模组注册的实体同样可用。

```java
import io.wifi.starrailexpress.disguise.EntityDisguise;

// 无限期伪装成牛：直到 clear，或开局 / 结束重置
EntityDisguise.disguise(serverPlayer, EntityType.COW);

// 限时伪装：20*30 tick（30 秒）后自动解除
EntityDisguise.disguise(serverPlayer, EntityType.COW, 20 * 30);

// 带外观 NBT 的伪装（羊的颜色、狼的项圈、史莱姆尺寸、村民职业……都在 NBT 里）
CompoundTag nbt = new CompoundTag();
nbt.putByte("Color", (byte) 3);
EntityDisguise.disguise(serverPlayer, EntityType.SHEEP, nbt, 20 * 60);

// 直接传入一个实体：复制它的类型与外观 NBT
EntityDisguise.disguise(serverPlayer, someEntity, 20 * 30);

// 自定义结束条件：test 返回 true 即解除（这里：下水就现原形）
EntityDisguise.disguise(serverPlayer, EntityType.COW, null, 0, Player::isInWater);

// 解除 / 清空
EntityDisguise.clear(serverPlayer);
EntityDisguise.clearAll(server.getServer());

// 查询（服务端读管理表，客户端读同步缓存，两边都能用）
boolean disguised = EntityDisguise.isDisguised(player);
EntityDisguiseState state = EntityDisguise.get(player);      // NONE 表示未伪装
ResourceLocation typeId = EntityType.getKey(state.type());
float eyeHeight = state.eyeHeight();
```

### 三种结束方式 / End conditions

| 方式 | 用法 | 说明 |
| --- | --- | --- |
| 时长 | `durationTicks > 0` | 到游戏刻自动解除。走 `SRE.getTicksFromGameStart()`，游戏时间暂停时不推进 |
| 自定义条件 | 传 `Predicate<ServerPlayer>` | 每 tick 求值，`test` 返回 `true` 即解除；条件抛异常时按「已结束」处理并打日志 |
| 无限期 | `durationTicks <= 0` 且不传条件 | 直到代码 `clear`、指令 `clear`，或开局 / 结束重置（指令里写 `infinite`） |

### 眼高规则 / Eye height

取 `min(玩家当前姿态眼高, 实体眼高)`：

- 游泳、睡觉等本就低于实体的姿态不会被抬高，第三人称相机不会掉进地里；
- 末影人这类高个子实体也不会把玩家眼高抬起来；
- 碰撞箱 `width` / `height` 原样保留。

眼高由 `Player#getDefaultDimensions` 的 mixin 提供，而 `Entity` 会把结果缓存进 `eyeHeight` 字段，所以设置 / 解除伪装时服务端会自动 `refreshDimensions()` 并下发 `RefreshDimensionsS2CPacket`；客户端收到同步后也会对受影响的玩家 `refreshDimensions()`。**这一步不可省**，否则眼高不会生效。

### 指令 / Command

```
/sre:disguise start infinite <player> <entity_type> [nbt] 无限期伪装，直到手动解除
/sre:disguise start <seconds> <player> <entity_type> [nbt] 限时伪装，<seconds> 秒后自动解除
/sre:disguise clear <player>                               解除伪装
/sre:disguise query <player>                               查询是否处于伪装状态
```

时长是必填的：`infinite` 字面量或秒数；两个分支都支持 `[nbt]`。`entity_type` 用原版实体注册表参数（就是 `/summon` 那个），候选由原版可召唤实体列表给出，本模组不注册任何参数类型。
`entity_type` 的候选来自实体注册表（含其他模组的实体）；`minecraft:player` 被排除。

### 判定「是否在伪装」/ Querying disguise state

命令侧：

| 方式 | 判定 | 覆盖范围 |
| --- | --- | --- |
| `/execute if sre:disguised <player>` | 是否处于任何形式的伪装 | 全部来源 |
| `/execute if sre:disguised_type <player> <entity_type>` | 是否伪装成该实体 | 仅实体伪装 |
| `/execute if sre:morphed <player>` | 是否处于变形状态（任意形态） | 仅变形 |
| `/execute if sre:morphed_player <player> <target>` | 是否正变形为该玩家 | 仅变形 |
| `/execute if sre:morphed_texture <player> <texture>` | 是否正变形成该贴图 | 仅变形 |
| `/execute if data sre:disguise <player> <path>` | 外观 NBT 的**路径是否存在**（原版 `if data` 语义） | 仅实体伪装 |
| `/data get\|merge\|modify\|remove sre:disguise <player> ...` | 读写伪装外观 NBT（还能作为 NBT 来源 / `execute store` 目标） | 仅实体伪装 |

NBT 那一支不是另开条件，而是把伪装 NBT 注册成了原版 `/data` 的数据源（`DisguiseDataProvider`，注入点见 `DataCommandsMixin`），所以路径、缩放、`from` 取值这些全部沿用原版实现；`if data` 与原版一致只判路径存在与否。详见 `docs/commands.md` 的 `data sre:disguise` 一节。

代码侧分两条路，按你在哪一侧选：

```java
// common / 服务端：任何来源是否在伪装（RoleData / CCA / 管理器全部是 common 侧）
boolean any = io.wifi.starrailexpress.disguise.DisguiseQuery.isDisguised(player);

// 收窄到实体伪装
boolean asCow = DisguiseQuery.isDisguisedAs(player, EntityType.COW);   // 是否伪装成该实体类型
boolean blue  = DisguiseQuery.isDisguisedWithNbt(player, nbt);         // 外观 NBT 是否“包含”给定 NBT（代码侧子集匹配）

// 只看变形（MorphApi）：任意形态 / 变形为某玩家 / 变形成某贴图
boolean morphed   = DisguiseQuery.isMorphed(player);
boolean asSteve   = DisguiseQuery.isMorphedAsPlayer(player, steve);
boolean asSkinOne = DisguiseQuery.isMorphedAsTexture(player, SRE.id("textures/entity/disguise/disguise_skin_1.png"));

// 读写实体伪装本身
EntityDisguiseState state = EntityDisguise.get(player);
boolean entity = EntityDisguise.isDisguised(player);
EntityDisguise.setNbt(player, nbt);   // 覆写外观 NBT，等价于 /data merge|modify sre:disguise；保留时长/predicate

// 客户端渲染路径：按 tick 打戳的判定（同一 tick 内只查表）
RoleDisguiseResolver.Flags flags = RoleDisguiseResolver.resolve(clientPlayer);
```

注意两者的判定方式不同，按需选：命令侧 `if data sre:disguise <player> <path>` 是**路径存在性**（原版语义，不比较数值）；代码侧 `isDisguisedWithNbt` 是**子集匹配**（给定标签是否是外观 NBT 的子集，可一次比多个键）。

`DisguiseQuery` 汇总的来源与客户端的 `DisguiseStatusResolver` 一一对应，两边要一起改。

### 生命周期 / Lifecycle

开局（`OnGameInitialized`）、结束（`OnGameEnd`）、玩家重置（`ResetPlayerEvent`）时自动清空，**不写入存档**——伪装是局内状态。

### 网络发包 / Sync policy

伪装是纯服务端权威状态，客户端只读，因此**没有 C2S 包**，服务端也不会每 tick 发包：

| 场景 | 发包 |
| --- | --- |
| 一次 `disguise` / `clear` | 变更先攒着，tick 结束时**合并成一个增量包**广播；同一玩家同一 tick 内多次变更只发最终状态 |
| 一局里 N 人同时伪装 | 1 个包 × 收件人数，而不是 N 个包 × 收件人数 |
| 包体：状态调色板 | 相同的「实体类型 + 外观 NBT」只写一次，每条记录只花 `UUID(16) + varint(1)`（「解除」也是一个调色板项） |
| 包体：外观 NBT | **只发偏离「同类型裸实体」默认值的键**。正常生成的生物，其 `Health`/`Attributes`/`Air`/`Motion`/`Pos`/`Brain`… 全等于默认值，于是全部不发；通常只剩几个字节到几十字节（不带 NBT 时**完全不发**，即 1 字节 END 标记）。真正会留下的就是变体信息：`Color`/`Size`/`variant`/`Sheared`/`CollarColor`/`Saddle`/`ChestedHorse`/`Owner`/装备/幼年/`CustomName`/`Tags` 等 |
| 位置 / 朝向 / 行走动画 | **0 字节**。客户端逐帧从**客户端自己的那个玩家实体**上抄（`EntityDisguiseRenderer#copyPlayerState`），移动本来就有原版的实体追踪包，伪装不额外发任何移动数据 |
| 眼高生效 | 每个受影响玩家补一个 `RefreshDimensionsS2CPacket`（极小）；同一条连接上先到状态包、后到刷新包，客户端才是「先知道伪装成什么，再按新眼高 refreshDimensions」 |
| 玩家进服 | 补一次全量快照；服务端没有任何伪装时一个包都不发 |
| 玩家离线 | 若其处于伪装状态，在同一批变更里广播一次「解除」，清掉其他客户端的本地缓存（否则他重进服时别人会继续按旧伪装渲染他） |
| 开局 / 结束 | 只发**一个**全量空快照 |
| 每 tick（无人伪装） | **0 包**，tick 里只有一次 `isEmpty()` |
| 到期 / 自定义条件 / 查询 | **0 包**（纯服务端判定，客户端本地查缓存） |

临时实体只存在于客户端内存里，**不会真的生成实体**，所以没有实体生成 / 追踪包。

想知道某次伪装实际多大，用 `/sre:disguise query <player>` —— 它会把投影后的外观 NBT 字节数一起打出来。

### 客户端开销 / Client cost

伪装是逐帧渲染的东西，所以这条路径按「常见情况最便宜」来设计：

| 情况 | 每帧开销 |
| --- | --- |
| 服务端没有任何伪装（绝大多数时间） | **一次 volatile 读**就返回，不查表、不分配 |
| 正在渲染的这名玩家没被伪装 | 一次哈希查找（UUID key） |
| 正在画某个伪装玩家 | 一次哈希查找拿到「临时实体 + 状态 + 渲染器」，然后逐帧拷贝约 15 个字段 |

几个刻意的取舍：

- **状态按引用比较，不做 `equals`**。`EntityDisguiseState.equals` 会递归比较 NBT，若每帧调用就是白烧 CPU；同步包给的就是状态对象本身，所以只有真的收到新状态 / 换维度才重建临时实体。
- **渲染器只在重建时解析一次**（`getRenderer` 的结果跟着临时实体一起缓存），不在每帧的渲染路径上查表。
- **不做 200ms 时间缓存**。仓库里既有的猪伪装渲染用 `System.currentTimeMillis()` 做节流，代价是状态变更最多延迟 200ms 生效；这里的逐帧成本已经降到 1~2 次查表，直接读实时状态更快也更准。
- **安静站着不动时跳过 `setPos`**：`Entity#setPos` 每次都重算一遍 AABB，位置没变就是纯浪费；`xo/yo/zo` 仍逐帧抄，渲染插值不受影响。
- **不调用临时实体的 `tick()`**：客户端跑实体 AI 没意义，还会和逐帧位置拷贝抢位置导致抖动；只推进 `tickCount` 与 `walkAnimation`。代价是少数依赖「客户端 tick 里算出来的辅助状态」的模型动画会静止（如狼甩尾），纯外观问题。
- **每 tick 开销为 0**：没有注册任何客户端 tick 处理器，只在渲染钩子、同步包、开局 / 结束事件上做事。
- 临时实体只存于客户端内存且每名玩家至多一个，状态解除 / 换维度 / 换局时立即释放；模型与贴图由原版渲染器共享，不额外占显存。

同样的做法也回填到了**原有的职业形态伪装**（猪 / 兔 / 番茄头 / 悦灵 / 熊猫）与**皮肤覆盖**：

| 位置 | 原来 | 现在 |
| --- | --- | --- |
| `RoleDisguiseResolver`（`org.agmas.noellesroles.client`） | 两个 mixin 各自持一份 `System.currentTimeMillis()` 节流缓存，窗口 200ms | 一份缓存、按 tick 打戳：同一 tick 内只查表，跨 tick 才重新探测 → 形态变化最迟 1 tick 生效 |
| `AbstractClientPlayerSkinMixin` | 读墙钟，窗口 100ms / 超性能模式 200ms | 按 tick 打戳，窗口砍半（1 tick / 2 tick），节流思路保留 |

两者都不再每帧读系统时间，形态切换的滞后从「最多一个节流窗口」降到「最多 1 个 tick」。

### HUD 与第一人称手臂 / HUD & first-person arm

**伪装状态 HUD**（`EntityDisguiseHud`）

| 位置 | 谁看得到 | 内容 |
| --- | --- | --- |
| 左上角信息行（心情任务 + 小游戏任务）**下方**，x 与任务行对齐 | 只有被伪装的玩家自己 | `当前伪装：牛`，一行一个来源 |
| 战斗名牌区域（瞄准某名玩家时），新起一行 | 火眼金睛持有者 / 创造 / 旁观 | `当前伪装成：牛` |

- **报哪些来源**：实体伪装（本 API）、职业形态（猪 / 兔 / 番茄头 / 悦灵 / 熊猫）、`MorphApi` 皮肤变形（变形为玩家时显示目标玩家名，贴图变形显示固定文案）。三者互不排斥，同时生效就各占一行。
- 名字一律是组件（原版实体翻译键 / 目标玩家名），每个客户端显示自己语言的名字；模组实体没提供翻译时回退到 `namespace:path`。
- 左上角那一行会跟随 `SREClientConfig.moodLeftOffset/moodTopOffset`，与心情 / 小游戏 HUD 一起搬动，不会因为你挪动它们而重叠；行位置按心情任务行数 + 小游戏任务行数推算。
- 开关：`SREClientConfig.showDisguiseHud`（默认开）。
- 瞄准提示挂在 `OnRenderRoleName.RENDER_PLAYER_EXTRA` 事件上（`RoleNameRenderer` 的作者明确要求不要 mixin 那个类），按事件约定先偏移 12 新起一行、结束时把光标推到下一行起点，避免与肉汁提示 / Dream 血条等其它监听器重叠。
- 实体伪装**不**被火眼金睛穿透：那个效果只针对皮肤（见 `AbstractClientPlayerSkinMixin`），模型替换照旧，所以这里照报不误。

**第一人称手臂**（`EntityDisguiseRenderer#renderFirstPersonHand`）

伪装时把自己的手臂换成目标实体的手臂。原版 `ItemInHandRenderer.renderPlayerArm` 其实把手臂绘制委托给了 `PlayerRenderer#renderRightHand/renderLeftHand`（都是 public），并且**调用前已经把第一人称手臂的位姿推进了 poseStack**——所以只需要换掉「画谁的手臂 + 用谁的贴图」，位置由原版保证，不需要自己写位姿数学。

- 目标实体的模型是 `HumanoidModel`（僵尸、骷髅、村民、灾厄、盔甲架，以及绝大多数模组人形）→ 画它的手臂 + 它的贴图。
- 非人形实体（牛、羊、物品实体……）→ 不画手臂：伪装成牛却看到一只人手更怪。
- 只渲染那一个 `ModelPart`，不动模型的 `visible` 标志，因此不会影响世界里真实实体的渲染。
- 手持物品仍然照常渲染（第一人称还要用来瞄准）。

### 注意事项 / Notes

- **第一人称看不到自己的伪装体**：原版不渲染相机所在的实体。现有「皮革噶的」角色伪装成猪靠谎报 `isDetached` + 模型后移实现自见，泛化版对任意实体存在相机陷在模型里的风险，故不做。
- **不带 NBT 时用实体默认外观**：伪装不调用 `finalizeSpawn`，所以不会像 `/summon` 那样随机变体（羊不会随机颜色、马不会随机花纹）。要变体就显式给 NBT，或直接把一个实体交给 `disguise(player, entity)` 让它连 NBT 一起复制。
- **外观 NBT 会被自动瘦身**：只发与「同类型裸实体默认值」不同的键，因此你完全可以放心把 `entity.saveWithoutId(...)` 的完整结果整个丢进来，多余的部分不会上路。
- **伪装期间不显示玩家名**：整个玩家渲染被取消，不会出现「一头牛顶着玩家名牌」。外观 NBT 里的 `Pos` / `Motion` / `Rotation` / `Health` / `Air` / `Brain` 等会被自动剥掉；`CustomNameVisible` 也会被剥掉（避免名牌），但 **`CustomName` 本身保留** —— `jeb_` 绵羊（彩虹毛）、`Toast` 兔子、`Dinnerbone` / `Grumm`（倒过来）这些原版外观效果就是靠名字触发的。保留名字**不会**导致名牌出现：临时实体不在世界实体表里，永远不可能是准星拾取目标（原版显示自定义名的两个条件之一），另一个条件又已被按掉。
- **`Tags` 保留**：方便用指令区分「这一类伪装」，例如 `/sre:disguise start infinite @p minecraft:cow {Tags:["boss_cow"]}` 之后用 `/execute if data sre:disguise @p Tags` 就能筛人。空标签列表等于默认值会被差集丢掉，不占包体。
- **不支持 `minecraft:player`**：玩家模型需要 `AbstractClientPlayer`，包装成人形会直接 `ClassCastException`；「看起来是别的玩家」请用上面的 [变形 API / Morph API](#变形-api--morph-api)。
- 头部俯仰跟随玩家真实视角（与原版一致）。四足模型的头部枢轴在脖子处，极端俯仰时头部可能显得扎进身体——纯外观问题。
- 与 `MorphApi` 可以叠加：`MorphApi` 管皮肤 / 名牌归属，实体伪装会整体替换玩家渲染，此时看不到 `MorphApi` 的效果。

---

## 事件系统 / Event System

所有事件位于 `io.wifi.starrailexpress.event` 包（以及 `org.agmas.noellesroles.events`）。  
All events are in package `io.wifi.starrailexpress.event` (and `org.agmas.noellesroles.events`).

注册方式 / Registration pattern:
```java
SomeEvent.EVENT.register((param1, param2) -> { /* ... */ });
```

---

### 游戏生命周期事件

#### `AllowGameEnd` — 是否允许游戏结束

**类型:** 可拦截，首个非 `NOT_MODIFY` 返回值生效。

```java
AllowGameEnd.EVENT_END.register((serverLevel, currentWinStatus, isLooseEndsMode) -> {   // 另有 EVENT_START
    // 返回 WinStatus.NOT_MODIFY 不修改，其他值将结束游戏
    return WinStatus.NOT_MODIFY;
});
```

`WinStatus` 枚举：

| 值 | 说明 |
|---|---|
| `NONE` | 不结束游戏 |
| `NOT_MODIFY` | 不修改（默认，传递给下一个监听器） |
| `KILLERS` | 杀手获胜 |
| `PASSENGERS` | 乘客获胜 |
| `TIME` | 超时 |
| `LOOSE_END` | 散局玩家获胜 |
| `GAMBLER` | 赌徒获胜 |
| `RECORDER` | 记录者获胜 |
| `CUSTOM` | 自定义胜利（设置 `SREGameRoundEndComponent.CustomWinnerID`，用 `RoleUtils.customWinnerWin(...)` 结算；**不存在 `CustomWinnersPredicates`**） |

#### `OnGameEnd` — 游戏结束时

**类型:** 通知型，所有监听器都会调用。

```java
OnGameEnd.EVENT.register((serverLevel, gameWorldComponent) -> {
    // 游戏结束后的清理逻辑
});
```

#### `OnGameTrueStarted` — 游戏真正开始时

**类型:** 通知型。游戏真正开始（非准备阶段）时触发。

```java
OnGameTrueStarted.EVENT.register((serverLevel) -> {
    // 游戏开始逻辑
});
```

#### `OnTrainAreaHaveReseted` — 列车区域已重置

**类型:** 通知型。地图重置完成后触发。

```java
OnTrainAreaHaveReseted.EVENT.register((serverLevel) -> { /* ... */ });
```

#### `OnRoundStartWelcomeTimmer` — 开场欢迎计时器（**注意类名拼写是 Timmer**）

**类型:** 通知型。每轮开始欢迎计时阶段触发。

---

### 玩家死亡事件

#### `AllowPlayerDeath` — 是否允许玩家死亡（无击杀者）

**类型:** 可拦截，任意监听器返回 `false` 则取消死亡。

```java
AllowPlayerDeath.EVENT.register((player, deathReason) -> {
    // 返回 false 阻止玩家死亡
    return true;
});
```

**内置死亡原因 / Built-in death reasons** (`GameConstants.DeathReasons`)：  
`fell_out_of_train` · `poison` · `grenade` · `bat_hit` · `gun_shot` · `knife_stab` · `generic`

#### `AllowPlayerDeathWithKiller` — 是否允许玩家死亡（有击杀者）

**类型:** 可拦截。

```java
AllowPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> true);
```

#### `AfterShieldAllowPlayerDeath` — 护盾后是否允许死亡（无击杀者）

**类型:** 可拦截。在护盾逻辑处理后调用。

#### `AfterShieldAllowPlayerDeathWithKiller` — 护盾后是否允许死亡（有击杀者）

**类型:** 可拦截。在护盾逻辑处理后调用。

#### `OnPlayerDeath` — 玩家死亡通知（无击杀者）

**类型:** 通知型，所有监听器都会调用。

```java
OnPlayerDeath.EVENT.register((player, deathReason) -> {
    // 玩家死亡后的逻辑
});
```

#### `OnPlayerDeathWithKiller` — 玩家死亡通知（有击杀者）

**类型:** 通知型。

```java
OnPlayerDeathWithKiller.EVENT.register((victim, killer, deathReason) -> { /* ... */ });
```

#### `OnPlayerKilledPlayer` — 玩家击杀玩家

**类型:** 通知型，所有监听器都会调用。

```java
OnPlayerKilledPlayer.EVENT.register((victim, killer, reason) -> {
    // reason: OnPlayerKilledPlayer.DeathReason
});
```

`DeathReason` 枚举：`GUN_SHOOT` · `KNIFE` · `GRENADE` · `BAT` · `POISON` · `ARROW` · `TRIDENT` · `UNKNOWN` · `OTHER`

#### `OnPlayerKilledPlayerIdentifier` — 玩家击杀玩家（ResourceLocation 版）

与 `OnPlayerKilledPlayer` 类似，但死亡原因为 `ResourceLocation`。

```java
OnPlayerKilledPlayerIdentifier.EVENT.register((victim, killer, deathReasonId) -> { /* ... */ });
```

#### `EarlyKillPlayer` — 提前确定真实击杀者

**类型:** 首个非 `null` 返回值生效。

```java
EarlyKillPlayer.FIND_KILLER_EVENT.register((victim, killer, reason) -> {
    // 返回真实击杀者，或 null 跳过
    return null;
});
```

#### `ShouldDropOnDeath` — 死亡时是否掉落物品

**类型:** 可拦截，任意监听器返回 `false` 则不掉落。

```java
ShouldDropOnDeath.EVENT.register((stack) -> true);   // 回调参数是 ItemStack，不是玩家
```

#### `OnShieldBroken` — 护盾破碎

**类型:** 通知型。

```java
OnShieldBroken.EVENT.register((victim, killer) -> { /* ... */ });
```

#### `OnTeammateKilledTeammate` — 队友击杀队友

**类型:** 通知型。

```java
OnTeammateKilledTeammate.EVENT.register((victim, killer, isInnocent, deathReason) -> { /* ... */ });
```

---

### 技能与交互事件

#### `OnRoleSkillUse` — 角色技能使用

**类型:** 可拦截（BEFORE 和 AFTER 均可）。

```java
// 技能使用前（返回 false 可取消技能）
OnRoleSkillUse.BEFORE.register((player, role) -> true);

// 技能使用后
OnRoleSkillUse.AFTER.register((player, role) -> true);
```

#### `OnPlayerUsedSkill` — 玩家使用技能（更通用）

**类型:** 通知型。

```java
OnPlayerUsedSkill.EVENT.register((player) -> { /* ... */ });
```

#### `OnVendingMachinesBuyItems` — 自动售货机购买物品

**包 / Package:** `org.agmas.noellesroles.events`  
**类型:** 可拦截，任意监听器返回 `false` 则取消购买。

```java
OnVendingMachinesBuyItems.EVENT.register((player, shopEntry) -> {
    // 返回 false 阻止购买
    return true;
});
```

#### `OnRevolverUsed` — 左轮手枪使用

**类型:** 通知型。

```java
OnRevolverUsed.EVENT.register((player, target) -> { /* ... */ });   // target 可能为 null
```

#### `IsShootBackFire` — 是否触发后坐力

**类型:** 可返回 `true` 触发后坐力。

#### `AllowShootRevolverDrop` — 是否允许左轮射击时掉落子弹

**类型:** 可拦截。

#### `IsPlayerPunchable` — 玩家是否可被击打

**类型:** 返回 `true` 表示可被击打。

```java
IsPlayerPunchable.EVENT.register((player) -> true);   // 只有 1 个参数（被攻击者）
```

#### `AllowPlayerPunching` — 是否允许玩家出拳

**类型:** 可拦截。

```java
AllowPlayerPunching.EVENT.register((player) -> true);   // 只有 1 个参数（攻击者）
```

#### `AllowPlayerOpenLockedDoor` — 是否允许玩家开锁

**类型:** 可拦截。

```java
AllowPlayerOpenLockedDoor.EVENT.register((player) -> true);
```

#### `AllowPlayerControlled` — 是否允许玩家被操控/附身

**类型:** 可拦截（任意监听器返回 `false` 即阻止）。在操纵师等附身职业发动操控前触发，可用于让某些职业/效果免疫被操控。  
**Type:** Vetoable (any listener returning `false` blocks it). Fired before a possession-style role (e.g. Manipulator) takes control; lets roles/effects make a target immune.

```java
// controller 发起者，target 目标；返回 false 阻止操控
AllowPlayerControlled.EVENT.register((controller, target) -> {
    // 例：被标记为"不可操控"的玩家免疫
    return !ImmunityComponent.KEY.get(target).immune;
});
```

#### `CommonInstinctEvents` — 本能高亮（**不存在 `OnGetInstinctHighlight`**）

**包 / Package:** `io.wifi.starrailexpress.event.client.CommonInstinctEvents`
**类型:** 返回 `TrueFalseAndCustomResult<Integer>`（`pass()` 跳过 / `custom(颜色)` 覆盖）。

```java
// 四个阶段事件：ALIVE_COMMON_BEFORE_EVENT / ALIVE_COMMON_MIDDLE_EVENT /
//             ALIVE_COMMON_AFTER_EVENT / SPECTATOR_COMMON_EVENT
CommonInstinctEvents.ALIVE_COMMON_AFTER_EVENT.register((self, target, isInstinctEnabled) -> {
    return TrueFalseAndCustomResult.pass();   // 或 TrueFalseAndCustomResult.custom(0xFF00FF)
});
```

#### `OnGiveKillerBalance` — 给予杀手金币

**类型:** **通知型且会累加返回值**（`int` 金币数，把所有监听器的返回值相加），不是拦截型。

#### `EntityInteractionHandler` — 实体交互处理

提供与地图命令方块类似的占位符替换功能：  
Provides placeholder replacement similar to command blocks:

| 占位符 | 含义 |
|------|------|
| `%target` | 目标实体名 |
| `%player` | 交互玩家名 |
| `%name_player` | 交互玩家显示名 |
| `%x` / `%y` / `%z` | 目标坐标 |
| `%player_x` / `%player_y` / `%player_z` | 玩家坐标 |
| `%world` | 世界维度 ID |
| `%distance` | 玩家与目标距离 |

---

### 渲染与客户端事件

#### `RenderClientLightLevel` — 客户端光照等级渲染

**类型:** 通知型（客户端）。可自定义光照等级显示。

#### `AllowNameRender` — 是否允许渲染玩家名称

**类型:** 可拦截（客户端）。

```java
AllowNameRender.EVENT.register((player) -> true);
```

#### `AllowItemShowInHand` — 是否允许在手中显示物品

**类型:** 可拦截（客户端）。

```java
AllowItemShowInHand.EVENT.register((player, stack, mainHand) -> stack);   // 返回 ItemStack
```

#### `AllowOtherCameraType` — 是否允许使用非第一人称视角

**类型:** 可拦截（客户端）。

```java
AllowOtherCameraType.EVENT.register((original, localPlayer) -> ReturnCameraType.PASS);   // 返回 ReturnCameraType
```

#### `ClientHeldItemSwitchEvent` — 客户端切换手持物品

**类型:** 通知型（客户端）。

#### `OnOpenInventory` — 是否需要打开限制背包

**类型:** 任意监听器返回 `true` 则打开限制背包界面。

```java
OnOpenInventory.EVENT.register((localPlayer, screen) -> false);
```

---

### 变形与伪装事件

#### `AllowPlayerMorph` — 是否允许变形

**包 / Package:** `io.wifi.starrailexpress.event`  
**类型:** 可拦截，任意监听器返回 `false` 则取消。

```java
AllowPlayerMorph.EVENT.register((player, appearance) -> {
    // appearance 为 MorphAppearance.NONE 时表示解除变形
    return true;
});
```

#### `OnPlayerMorph` — 变形完成

**类型:** 通知型。外观已写入并开始同步。`next` 为 `NONE` 时表示解除变形。

```java
OnPlayerMorph.EVENT.register((player, previous, next) -> { /* ... */ });
```

#### `AllowPlayerDisguise` — 是否允许实体伪装

**包 / Package:** `io.wifi.starrailexpress.event`  
**类型:** 可拦截，任意监听器返回 `false` 则取消本次伪装 / 解除。

```java
AllowPlayerDisguise.EVENT.register((player, state) -> {
    // state 为 EntityDisguiseState.NONE 时表示解除伪装
    return true;
});
```

注意：开局 / 结束时的生命周期清理**不走此事件**——清理不应被监听器否决。

#### `OnPlayerDisguise` — 实体伪装变更

**类型:** 通知型。状态已写入、尺寸已刷新、开始同步。`next` 为 `NONE` 时表示解除伪装。

```java
OnPlayerDisguise.EVENT.register((player, previous, next) -> { /* ... */ });
```

#### `OnResolveDisplayedSkinOwner` — 解析显示皮肤拥有者（客户端）

帽子、名牌、身份玩偶都通过此事件解析「看起来是谁」。返回非本人 UUID 即生效。

---

### 其他事件

#### `CanSeePoison` — 是否可以看到毒药相关内容

**类型:** 可拦截。

#### `AFKEventHandler` — AFK 事件处理

**类型:** 通知型。玩家进入/离开 AFK 状态时触发。

#### `PlayerInteractionHandler` — 玩家交互处理

通用玩家交互处理入口，与 `EntityInteractionHandler` 类似。

---

## Harpymodloader API

**包 / Package:** `org.agmas.harpymodloader`

`Harpymodloader` 是修饰符系统和职业权重系统的核心，提供以下 API。

### Harpymodloader — 主入口

#### 强制职业 / Force Role

```java
// 为玩家设置强制分配的职业（下局生效）
Harpymodloader.addToForcedRoles(ModRoles.MY_ROLE, player);
```

#### 强制修饰符 / Force Modifier

```java
// 为玩家设置强制分配的修饰符（下局生效）
Harpymodloader.addToForcedModifiers(NRModifiers.EXPEDITION, player);
```

#### 职业最大数量 / Role Maximum Count

```java
// 设置职业同场最大数量（也可通过 SRERole.setMax(n) 链式设置）
Harpymodloader.setRoleMaximum(ModRoles.MY_ROLE, 2);
Harpymodloader.setRoleMaximum(MY_ROLE_ID, 2);  // ResourceLocation 版
```

#### 伴侣职业 / Companion Role（同时分配两个职业）
现已迁移到 `SRERole` 中存储。当然，您也可以使用旧版本API：
```java
// 设置：分配 DOCTOR 的同时也分配 POISONER
Harpymodloader.addOccupationRole(ModRoles.DOCTOR, ModRoles.POISONER);

// 查询
SRERole companion = Harpymodloader.getOccupationRoles(ModRoles.DOCTOR); // POISONER
boolean has = Harpymodloader.hasOccupationRole(ModRoles.DOCTOR);

// 移除
Harpymodloader.clearOccupationRole(ModRoles.DOCTOR, ModRoles.POISONER);
Harpymodloader.clearOccupationRole(ModRoles.DOCTOR);
```

#### 隐藏修饰符 / Hide Modifiers

设置修饰符的 `setHidden(true)` 可使其不在 UI 中展示（但仍可被分配）。

```java
// 假设存在：SREModifier modifier;
modifier.setHidden(true);
```

#### 特殊职业列表 / Special Roles

`SPECIAL_ROLES`：不参与普通分配池的职业（如 CIVILIAN、LOOSE_END）。  
`OVERWRITE_ROLES`：分配后会覆盖先前职业的角色列表。

---

### HML 事件

所有事件位于 `org.agmas.harpymodloader.events`。

#### `ModdedRoleAssigned` — 职业分配时

**类型:** 通知型。职业被分配给玩家时触发（同时自动调用 `RoleMethodDispatcher.onInit`）。

```java
ModdedRoleAssigned.EVENT.register((player, role) -> {
    // 初始化职业相关逻辑
});
```

#### `ModdedRoleRemoved` — 职业移除时

**类型:** 通知型。职业从玩家移除时触发。

```java
ModdedRoleRemoved.EVENT.register((player, role) -> {
    // 清理职业相关逻辑
});
```

#### `ModifierAssigned` — 修饰符分配时

**类型:** 通知型。修饰符被分配给玩家时触发。

```java
ModifierAssigned.EVENT.register((player, modifier) -> {
    if (modifier.equals(NRModifiers.MY_MODIFIER)) {
        // 初始化修饰符组件或状态
    }
});
```

#### `ModifierRemoved` — 修饰符移除时

**类型:** 通知型。修饰符从玩家移除时触发。

```java
ModifierRemoved.EVENT.register((player, modifier) -> {
    if (modifier.equals(NRModifiers.MY_MODIFIER)) {
        // 清理修饰符状态
    }
});
```

#### `GameInitializeEvent` — 游戏初始化时

**类型:** 通知型。游戏开始初始化后触发（职业已分配完毕）。

```java
GameInitializeEvent.EVENT.register((serverLevel, gameWorldComponent, players) -> {
    // 所有玩家的职业已分配，可在此进行进一步初始化
});
```

#### `OnGamePlayerRolesConfirm` — 职业分配确认前

**类型:** 通知型。职业分配方案确定后、实际分配前触发，可修改分配映射。

```java
OnGamePlayerRolesConfirm.EVENT.register((serverLevel, roleAssignments) -> {
    // roleAssignments: Map<Player, SRERole>
    // 可以在这里调整/覆盖分配方案
});
```

#### `ResetPlayerEvent` — 玩家重置时

**类型:** 通知型。玩家状态重置时触发（游戏开始前或结束后）。

```java
ResetPlayerEvent.EVENT.register(player -> {
    // 清理该玩家的自定义状态
});
```

---

## 游戏模式系统 / Game Mode System

### GameMode — 游戏模式基类

**包 / Package:** `io.wifi.starrailexpress.api`

```java
public abstract class GameMode {
    public final ResourceLocation identifier;
    public final int defaultStartTime;  // 分钟
    public final int minPlayerCount;

    // 从 NBT 恢复状态
    public void readFromNbt(CompoundTag nbt, HolderLookup.Provider lookup) {}
    // 保存状态到 NBT
    public void writeToNbt(CompoundTag nbt, HolderLookup.Provider lookup) {}

    // 通用（客户端+服务端）每 Tick
    public void tickCommonGameLoop(Level level) {}
    // 客户端每 Tick
    public void tickClientGameLoop(Level level) {}
    // 服务端每 Tick（必须实现）
    public abstract void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent);

    // 游戏初始化（必须实现）
    public abstract void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
                                         List<ServerPlayer> players);
    // 游戏结束清理（可选）
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {}
}
```

### SREGameModes — 游戏模式注册表

**包 / Package:** `io.wifi.starrailexpress.api`

#### 内置游戏模式 / Built-in Game Modes

| 常量 | ID | 说明 |
|------|-----|------|
| `MURDER` | `sre:murder` | 标准谋杀模式 |
| `LOOSE_ENDS` | `wathe:loose_ends` | 散局模式 |

`DISCOVERY_MODE_ID = sre:discovery` — Discovery 模式 ID（仅注册，无对应 `GameMode` 常量）

#### 注册自定义游戏模式

```java
public static final ResourceLocation MY_MODE_ID = SRE.id("my_mode");
public static final GameMode MY_MODE = SREGameModes.registerGameMode(new MyGameMode(MY_MODE_ID));
```

---

## HUD 渲染 / HUD Rendering

如果 HUD 是针对特定职业的，使用 `RoleHudRenderCallback`。  
如果不是针对特定职业的，可以使用 `CommonHudRenderCallback`。
与官方 HudRenderCallback 相比，它有着更好的性能，能够在一定程度上提高fps。

**包 / Package:** `org.agmas.noellesroles.client.event`

```java
RoleHudRenderCallback.EVENT.register(
    ModRoles.MY_ROLE_ID,       // 职业 ID（ResourceLocation）
    (context, tickCounter) -> {
        Minecraft client = Minecraft.getInstance();
        Component text = Component.translatable("gui.mymod.my_role.status");
        int color = 0x55FF55;  // 绿色
        int screenWidth = context.guiWidth();
        int screenHeight = context.guiHeight();
        int textWidth = client.font.width(text);

        // 右下角显示
        int x = screenWidth - textWidth - 10;
        int y = screenHeight - 20;
        context.drawString(client.font, text, x, y, color);
    }
);
```

该事件**只会在玩家是对应职业时**被调用，无需手动判断当前职业。  
This event is **only called when the player has the specified role** — no manual role check needed.

---

## Replay 系统 / Replay System

### IGameReplayRecorder — 回放记录接口

**包 / Package:** `io.wifi.starrailexpress.api.replay`

```java
// 记录事件
recorder.recordEvent(EventType.PLAYER_KILL, new PlayerKillDetails(killerUUID, victimUUID, deathReason));

// 记录自定义事件
recorder.recordCustomEvent(MY_EVENT_ID, playerUUID, "custom message");
```

### IGameReplayReader — 回放读取接口

**包 / Package:** `io.wifi.starrailexpress.api.replay`

```java
List<TimelineReplayEvent> all = reader.getEvents();   // 元素类型是 TimelineReplayEvent
List<TimelineReplayEvent> inRange = reader.getEventsInTimeRange(startMs, endMs);
List<TimelineReplayEvent> byPlayer = reader.getEventsByPlayer(uuid);
List<TimelineReplayEvent> byType = reader.getEventsByType(EventType.PLAYER_KILL);
List<UUID> players = reader.getAllPlayerUuids();
Optional<String> name = reader.getPlayerName(uuid);
```

### ReplayEventTypes — 事件类型枚举

**包 / Package:** `io.wifi.starrailexpress.api.replay`

| EventType | 详情记录类 | 说明 |
|---|---|---|
| `PLAYER_JOIN` / `PLAYER_LEAVE` | `PlayerJoinLeaveDetails` | 玩家加入/离开 |
| `PLAYER_KILL` | `PlayerKillDetails` | 玩家击杀 |
| `PLAYER_POISONED` | `PlayerPoisonedDetails` | 玩家中毒 |
| `TASK_COMPLETE` | `TaskCompleteDetails` | 任务完成 |
| `STORE_BUY` | `StoreBuyDetails` | 商店购买 |
| `DOOR_OPEN` / `DOOR_CLOSE` / `DOOR_LOCK` / `DOOR_UNLOCK` | `DoorActionDetails` | 门操作 |
| `LOCKPICK_ATTEMPT` | `LockpickAttemptDetails` | 撬锁尝试 |
| `ITEM_USED` | `ItemUsedDetails` | 物品使用（没有 `ITEM_USE`） |
| `MOOD_CHANGE` | `MoodChangeDetails` | 心情变化 |
| ~~`NOTE_EDIT`~~ | — | **枚举里不存在**该类型 |
| `GAME_START` / `GAME_END` | — | 游戏开始/结束 |
| `CHANGE_ROLE` | — | 角色变更 |
| `BLACKOUT_START` / `BLACKOUT_END` | `BlackoutEventDetails` | 停电事件 |
| `GAME_END` | `RoundEndDetails` | 游戏/回合结束 |
| ~~`KEY_USED`~~ | — | **枚举里不存在**该类型 |
| `SKILL_RELEASE` | — | 技能释放 |
| `PSYCHO_STATE_CHANGE` | `PsychoStateChangeDetails` | 精神状态变化 |
| ~~`GUN_FIRED`~~ | — | **枚举里不存在**该类型 |
| `GRENADE_THROWN` | `GrenadeThrownDetails` | 手雷投掷 |
| `CUSTOM_EVENT` | `CustomEventDetails` | 自定义事件 |

#### 注册自定义事件序列化器

```java
TimelineReplayEventRegistry.registerCustomEvent(
    MY_CUSTOM_EVENT_ID,    // ResourceLocation
    MyEventDetails.class,
    (details, json) -> { /* 序列化 */ },
    (json) -> { /* 反序列化 */ return new MyEventDetails(...); }
);
```

---

## 工具类 / Utilities

### GameUtils — 游戏工具

**包 / Package:** `io.wifi.starrailexpress.game`

游戏流程控制、玩家状态判断、杀戮逻辑等核心工具方法。  
Core utilities for game flow, player state checks, and kill logic.

#### 游戏流程 / Game Flow

```java
// 启动游戏（isLobby=false 时才生效）
GameUtils.startGame(serverLevel, gameMode, timeInMinutes);

// 强制真正开始游戏（跳过准备阶段）
GameUtils.trueStartGame(serverLevel, gameMode, timeInMinutes);

// 停止游戏
GameUtils.stopGame(serverLevel);

// 初始化游戏（内部调用）
GameUtils.initializeGame(serverLevel);

// 游戏结束后清理
GameUtils.finalizeGame(serverLevel);

// 添加游戏开始的物品冷却（安全时间）
GameUtils.addItemCooldowns(serverLevel, ticks);
```

#### 执行命令

```java
GameUtils.executeCommand(commandSourceStack, "/say Hello");
```

#### 玩家重置 / Player Reset

```java
// 游戏中重置玩家（清背包、状态等）
GameUtils.resetPlayer(serverPlayer);

// 游戏结束后重置玩家（含发送结束包）
GameUtils.resetPlayerAfterGame(serverPlayer);
```

#### 玩家状态判断 / Player State

```java
// 是否已被淘汰（死亡/旁观/创造）
boolean eliminated = GameUtils.isPlayerEliminated(player);
boolean eliminatedIgnoreSplit = GameUtils.isPlayerEliminatedIgnoreShitSplit(player);

// 是否存活（非旁观/创造）
boolean alive = GameUtils.isPlayerAliveAndSurvival(player);
boolean alive2 = GameUtils.isPlayerAliveAndSurvival(player, worldModifierComponent);

// 是否旁观
boolean spectator = GameUtils.isPlayerSpectator(player);

// 是否创造
boolean creative = GameUtils.isPlayerCreative(player);

// 是否旁观或创造
boolean specOrCreative = GameUtils.isPlayerSpectatingOrCreative(player);

// 分裂人格存活结果
GameUtils.SPAliveResult result = GameUtils.isPlayerReallyAliveOrDead(player);
// result: ALIVE | DEAD | NOT
```

#### 玩家击杀 / Kill Player

```java
// 击杀玩家（可指定死亡原因，触发 AllowPlayerDeath/OnPlayerDeath 等事件）
GameUtils.killPlayer(victim, spawnBody, killer);
GameUtils.killPlayer(victim, spawnBody, killer, GameConstants.DeathReasons.KNIFE);

// 强制击杀（跳过 AllowPlayerDeath 拦截）
GameUtils.forceKillPlayer(victim, spawnBody, killer, deathReason);
```

#### 阵营判断

```java
// 判断两职业是否属于不同阵营
boolean diff = GameUtils.differentTeam(role1, role2);
```

#### 死亡掉落

```java
// 判断物品死亡时是否应掉落
boolean drop = GameUtils.shouldDropOnDeath(itemStack);
```

#### 玩家位置限制

```java
// 将玩家限制在 AABB 范围内（超出则传送回边界）
GameUtils.limitPlayerToBox(serverPlayer, new AABB(minX,minY,minZ, maxX,maxY,maxZ));
```

#### 自定义胜利条件

```java
// ⚠️ GameUtils.CustomWinnersPredicates 并不存在！自定义/独立胜利请这样写：
// 1) 在职业类里覆写 CustomWinnerRoleInterface#checkWin 返回 WinStatus.CUSTOM（继承 CustomWinnerRole 最省事）
// 2) 由 RoleUtils.customWinnerWin 完成结算：
RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM, ModRoles.MY_ROLE_ID.getPath(),
        OptionalInt.of(ModRoles.MY_ROLE.color()));
// 3) 必要时在 CustomWinnerClass.registerCustomWinners() 里加分支（顺序 = 优先级）
// 详见 docs/角色开发指南.md §5 / §14 与 docs/AI创建新职业攻略.md §12
```

---

### SREItemUtils — 物品工具

**包 / Package:** `io.wifi.starrailexpress.util`

提供玩家背包物品清除与统计的简便方法，自动同步背包 UI。  
Provides convenient player inventory clear/count methods that auto-sync the inventory UI.

```java
// 清除玩家背包中指定物品（全部），返回清除数量
int count = SREItemUtils.clearItem(player, TMMItems.KNIFE);
int count = SREItemUtils.clearItem(player, TMMItemTags.GUNS);       // 按标签
int count = SREItemUtils.clearItem(player, stack -> stack.isDamaged()); // 按谓词

// 清除指定数量
int count = SREItemUtils.clearItem(player, TMMItems.KNIFE, 1);
int count = SREItemUtils.clearItem(player, predicate, 3);

// 统计玩家背包中物品数量（不清除）
int has = SREItemUtils.hasItem(player, TMMItems.KNIFE);
int has = SREItemUtils.hasItem(player, TMMItemTags.GUNS);
int has = SREItemUtils.hasItem(player, predicate);
```

---

### RoleUtils — 角色工具

**包 / Package:** `org.agmas.noellesroles.utils`  
继承自 `MCItemsUtils`（提供物品基础工具）

#### 胜利控制

```java
// 触发自定义胜利（需设置胜利者 ID 和颜色）
RoleUtils.customWinnerWin(serverLevel, "my_winner_id", 0xFF5500);

// 完整版（可指定 WinStatus 类型）
RoleUtils.customWinnerWin(serverLevel, WinStatus.CUSTOM,
    "my_winner_id", OptionalInt.of(0xFF5500));
```

#### 音效播放

```java
// 给指定玩家播放音效（仅该玩家可听到，服务端发包）
RoleUtils.playSound(serverPlayer, TMMSounds.KNIFE_HIT, SoundSource.PLAYERS, 1.0f, 1.0f);
RoleUtils.playSound(serverPlayer, soundEvent, source, x, y, z, volume, pitch);
```

#### 属性操作

```java
// 移除玩家所有属性修饰符
RoleUtils.removeAllPlayerAttributes(serverPlayer);

// 清除所有药水效果
boolean removed = RoleUtils.removeAllEffects(player);
```

#### 背包操作

```java
// 玩家是否有空格子（0-8 快捷栏）
boolean hasFree = RoleUtils.isPlayerHasFreeSlot(player);

// 移除指定槽位的物品
RoleUtils.removeStackItem(serverPlayer, slotIndex);

// 掉落并清除满足条件的物品，返回清除数量
int count = RoleUtils.dropAndClearAllSatisfiedItems(serverPlayer, TMMItems.KNIFE);
int count = RoleUtils.dropAndClearAllSatisfiedItems(serverPlayer, TMMItemTags.GUNS);

// 仅清除（不掉落）
int count = RoleUtils.clearAllSatisfiedItems(serverPlayer, item);
int count = RoleUtils.clearAllSatisfiedItems(serverPlayer, tagKey);
int count = RoleUtils.clearAllKnives(serverPlayer);    // 快捷方法：清除所有刀
int count = RoleUtils.clearAllRevolver(serverPlayer);  // 快捷方法：清除所有枪
```

#### 角色变更

```java
// 变更玩家的职业（触发 ModdedRoleRemoved/ModdedRoleAssigned 事件）
RoleUtils.changeRole(player, ModRoles.KILLER);
RoleUtils.changeRole(player, ModRoles.KILLER, /* record= */ true);

// 发送欢迎公告（告知职业）
RoleUtils.sendWelcomeAnnouncement(serverPlayer);
```

#### 名称与颜色工具

```java
// 获取职业翻译名（Component）
MutableComponent name = RoleUtils.getRoleName(role);
MutableComponent name = RoleUtils.getRoleName(roleId);

// 获取职业描述
MutableComponent desc = RoleUtils.getRoleDescription(role);

// 获取修饰符翻译名
MutableComponent modName = RoleUtils.getModifierName(modifier);
MutableComponent modNameColored = RoleUtils.getModifierNameWithColor(modifier);
MutableComponent modDesc = RoleUtils.getModifierDescription(modifier);

// 统一处理职业/修饰符/物品的名称（用于 UI 展示）
Component display = RoleUtils.getRoleOrModifierName(roleOrModifier);
MutableComponent colored = RoleUtils.getRoleOrModifierNameWithColor(roleOrModifier);
MutableComponent desc2 = RoleUtils.getRoleOrModifierDescription(roleOrModifier);
int color = RoleUtils.getRoleOrModifierColor(roleOrModifier);
ResourceLocation id = RoleUtils.getRoleOrModifierIdentifier(roleOrModifier);
MutableComponent typeName = RoleUtils.getRoleOrModifierTypeName(roleOrModifier); // "职业" / "修饰符"

// 同上，额外支持 Item 类型
Component name2 = RoleUtils.getRoleOrModifierOrItemName(roleOrModifierOrItem);
ResourceLocation id2 = RoleUtils.getRoleOrModifierOrItemIdentifier(roleOrModifierOrItem);
```

#### 职业查询

```java
// 通过名称（path）获取职业
SRERole role = RoleUtils.getRole("killer");  // Noellesroles 命名空间（getRole(String) / getRole(ResourceLocation)）
SRERole role = RoleUtils.getRole(roleId);             // 任意 ResourceLocation

// 判断两职业是否相同（null 安全）
boolean eq = RoleUtils.compareRole(role1, role2);
```

---

## 紧急会议系统 / Emergency Meeting System

**包 / Package:** `net.exmo.sre.meeting`

Among Us / 鹅鸭杀式会议：由**地图配置**启用（`AreasSettings.meetingEnabled` 等字段，
可在地图配置 GUI 的「会议」标签页可视化编辑，或 `/sre:area_manager set meetingEnabled true`）。
存活玩家**右键尸体**即召开会议：全体存活玩家被传送至会议地点，系统自动搜寻周围的椅子
（`MountableBlock`）就座；开场环绕运镜 + 标题动画后进入狼人杀式讨论 —— 按发言键（默认 B）、
在聊天栏发言、或使用 svc 语音说话都会被标记为「发言中」，镜头自动对准发言者（支持多人同时发言）。
讨论期间禁止移动 / 攻击 / 技能且死亡一律否决；时间到后全员原路返回。

### MeetingApi

```java
// 以「发现尸体」的名义召开会议（右键尸体的默认交互已内置）
boolean ok = MeetingApi.reportBody(reporter, playerBodyEntity);

// 紧急按钮式会议（无尸体）
boolean ok2 = MeetingApi.startMeeting(serverLevel, reporter, null);

// 立即结束当前会议
MeetingApi.endMeeting();

// 查询
boolean active = MeetingApi.isMeetingActive();
boolean joined = MeetingApi.isParticipant(playerUuid);
```

### 地图配置字段（AreasSettings，category = "meeting"）

| 字段 | 默认 | 说明 |
|---|---|---|
| `meetingEnabled` | `false` | 是否启用会议系统 |
| `meetingPosition` | `0,0,0` | 会议地点坐标（`StoreableVec3`；**没有 `meetingX/Y/Z`**） |
| `meetingChairScanBox` | `-12,-3,-12 → 12,3,12` | 自动搜索椅子的 AABB（`StoreableAABB`，旧文档写作 `meetingChairScanRadius`） |
| `meetingChairScanRadius` | `12` | 自动搜寻椅子的半径（上限 32） |
| `meetingDiscussSeconds` | `60` | 讨论阶段时长（秒） |
| `meetingCooldownSeconds` | `90` | 两次会议的最小间隔（秒） |

> 网络包：`MeetingStateS2CPayload`（状态全量同步）、`MeetingSpeakC2SPayload`（发言开关）。
> 客户端渲染 / 运镜见 `net.exmo.sre.meeting.client.MeetingClientHandler` 与 `MeetingHud`。

---

## 客户端 JAR 密钥认证 / Client JAR Key Authentication

**包 / Package:** `net.exmo.sre.mod_whitelist`（默认**关闭**）

发布者用外部工具 `tools/sign_sre_jar.py`（无 Python 的主机用等效的 `tools/sign_sre_jar.ps1`）
把随机密钥嵌入最终 jar（`sre_auth_key.txt`），
服务端与所有客户端运行**同一份签名 jar**。玩家入服时服务端下发一次性 nonce，双方各自计算
`HMAC-SHA256(密钥, 自身jar摘要|nonce|版本)` 比对 —— jar 被修改 / 未签名 / 版本不符即被断开。
在 `config/starrailexpress-config.json` 中设置 `"ENABLE_JAR_KEY_AUTH": true` 启用；
可与既有的 `VERIFY_STARRAILEXPRESS_HASHES` 哈希白名单叠加。

---

## 背包界面 API / Inventory Screen API

限位背包界面（`LimitedInventoryScreen`）的扩展 API。**不要再用 mixin 改背包界面**；
通过事件（非职业扩展，如 modifier）或 SRERole 钩子（职业扩展）实现。全部为纯客户端机制，
服务端类严禁直接 import 客户端类。

### LimitedInventoryScreenEvents — 事件

**包 / Package:** `io.wifi.starrailexpress.event.client`

4 个 fabric 事件，与旧版 `@Mixin(LimitedInventoryScreen.class)` 的注入点一一对应：

| 事件 | 触发时机 | 回调签名 |
|------|---------|---------|
| `INIT` | `init()` 开头 | `Init.onInit(LimitedInventoryScreen)` |
| `INIT_TAIL` | `init()` 末尾 | 同上 |
| `RENDER` | `render()` 开头（每帧） | `Render.onRender(LimitedInventoryScreen, GuiGraphics, int, int, float)` |
| `RENDER_TAIL` | `render()` 末尾（每帧） | 同上 |

```java
// 非职业扩展（如 modifier）示例
LimitedInventoryScreenEvents.INIT.register(screen -> {
    screen.addRoleWidget(Button.builder(Component.literal("x"), b -> {}).bounds(10, 10, 20, 20).build());
});
LimitedInventoryScreenEvents.RENDER_TAIL.register((screen, g, mx, my, d) -> {
    g.drawCenteredString(Minecraft.getInstance().font, Component.literal("hi"), screen.width / 2, 10, 0xFFFFFF);
});
```

### SRERole 屏幕钩子 / Screen Hooks

**职业扩展**用 SRERole 上的扩展工厂注册，再由 `LimitedInventoryScreen` 每次打开背包时
创建**新的扩展实例**并调用接口钩子（避免状态固定；需要跨次保留的状态用 `static`）。
创建时内部会先判断运行环境（`FabricLoader` 环境 != CLIENT 返回 null）。
注册应在**客户端**进行（如 `NoellesrolesClient.onInitializeClient()` 调用的 `RoleScreenRegister`）。

```java
// 客户端注册（示例）
ModRoles.AMON.setInventoryScreenExtensionFactory(AmonRoleScreenExtension::new);
```

扩展类实现 `RoleInventoryScreenExtension` 接口并覆写钩子：

```java
public final class AmonRoleScreenExtension extends PlayerListRoleScreenExtension<PlayerInfo> {
    // 每次打开背包创建新实例：实例字段自动重置；需要跨次保留的状态用 static

    @Override
    public void onInventoryScreenInit(LimitedInventoryScreen screen) { ... }  // init() 开头（HEAD）
    @Override
    public void onInventoryScreenInitTail(LimitedInventoryScreen screen) { ... } // init() 末尾（TAIL）
    @Override
    public void onInventoryScreenRender(LimitedInventoryScreen screen, GuiGraphics g, int mx, int my, float d) { ... } // render() 开头，每帧
}
```

- `onInventoryScreenInit` —— `init()` 开头（HEAD）
- `onInventoryScreenInitTail` —— `init()` 末尾（TAIL，需要盖在最上层时用）
- `onInventoryScreenRender` —— `render()` 开头，每帧

> 客户端需要执行客户端方法时：判别环境（`player.level().isClientSide` 或 `FabricLoader` 环境）
> 后经 `SREClient`（客户端入口，允许客户端 only 方法）执行；服务端类不要 import 客户端类。

### 屏幕公开"轮椅"方法

`LimitedInventoryScreen` 提供的便捷方法（供事件监听器 / 钩子使用）：

| 方法 | 说明 |
|------|------|
| `addRoleWidget(T widget)` | 添加控件（等价原版 `addRenderableWidget`，公开） |
| `removeRoleWidget(GuiEventListener widget)` | 移除控件 |
| `clearRoleWidgets()` | 清空全部控件（慎用） |
| `reinit()` | 清空控件并重新 `init()`（两阶段界面用，如葬仪选人→选死因） |

### PlayerPaginationHelper — 翻页/搜索/排序

**包 / Package:** `io.wifi.starrailexpress.client.gui.screen.ingame`

"选人列表"分页辅助（原 noellesroles 的 `PlayerPaginationHelper` 迁入核心）。每页 8 人。

```java
PlayerPaginationHelper<PlayerInfo> helper = new PlayerPaginationHelper<>(creator, textProvider);
helper.setNameExtractor(info -> info.getProfile().getName()); // 启用按名搜索 + 默认按名排序
helper.setPlayerEntries(list);

helper.attachSearchBox(screen);   // 挂载玩家名搜索框（输入实时过滤；翻页不会清除）
helper.nextPage(screen);          // 下一页
helper.prevPage(screen);          // 上一页
helper.jumpToPage(screen, page);  // 跳到指定页
helper.getCurrentPage();          // 当前页（0 起）
helper.getTotalPages();           // 总页数
helper.getVisibleEntries();       // 过滤 + 排序后的可见条目
helper.setSort(comparator);       // 自定义排序（覆盖默认按名排序）
helper.setSearchQuery("abc");     // 直接设置过滤词
```

### RoleScreenHelper — 角色选人辅助

**包 / Package:** `io.wifi.starrailexpress.client.gui.screen.ingame`

在 `PlayerPaginationHelper` 之上封装"角色激活判断 + 分页 + 搜索 + 排序"：

```java
RoleScreenHelper<PlayerInfo> helper = new RoleScreenHelper<>(
        player, ModRoles.AMON,
        (screen, x, y, entry, index) -> {
            Button widget = new AmonPlayerWidget(screen, x, y, entry);
            screen.addRoleWidget(widget); // 记得把控件挂到屏幕上
            return widget;
        },
        textProvider, extraDrawer, entriesSupplier);
helper.setNameExtractor(info -> info.getProfile().getName());

helper.onInit(screen);            // 清旧控件 + 填充条目 + 加当前页
helper.attachSearchBox(screen);   // 挂载搜索框
helper.onRender(graphics, screen);// 画提示文字 + 页码
```

完整可抄的职业示例见 `docs/角色开发指南.md`。

---

## 粒子与特效 / Particle & FX

### 前置事实：粒子是怎么过网的

`ServerLevel.sendParticles(type, x, y, z, count, dx, dy, dz, speed)` 每次调用都会构造**一个** `ClientboundLevelParticlesPacket`，发送给 32 格内的**每个**玩家。包里的 `count` 就是"这次生成几颗粒子"，**不额外增加包体积**；客户端把每颗粒子放在 `pos + nextGaussian() * spread`、速度取 `nextGaussian() * speed`。

因此两条硬规则：

1. **禁止在循环里反复调用 `sendParticles` 凑粒子数** —— 需要 N 颗就传 `count = N`。
2. **禁止为了"摆形状"在服务端逐点发包** —— 需要自定义形状时走下面的自定义粒子包，形状交给客户端算。

```java
// 错误：20 个包 × 附近玩家数
for (int i = 0; i < 20; i++) level.sendParticles(P, x, y, z, 1, 0.3, 0.3, 0.3, 0.0);

// 正确：1 个包，20 颗粒子
level.sendParticles(P, x, y, z, 20, 0.3, 0.3, 0.3, 0.0);
```

不适用上述规则的情况：面向单个玩家的私有包 `sendParticles(ServerPlayer, ...)`，以及位置随玩家变化的"每玩家一包"（如每个玩家脚下各来一簇）。纯客户端的 `level.addParticle` / `addAlwaysVisibleParticle` 不进网络，随便用。

### ParticleFx — 服务端粒子助手

`io.wifi.starrailexpress.util.ParticleFx`，每个方法都只发一个包。

| 方法 | 用途 |
| --- | --- |
| `burst(level, particle, x, y, z, count, spreadX, spreadY, spreadZ, speed)` | 一次发包的通用入口 |
| `sphere(level, particle, Vec3 center, count, radius, speed)` | 球形体积（原"绕圈摆点"的效果退化用） |
| `segment(level, particle, Vec3 from, Vec3 to, count, thickness, speed)` | 两点之间的柱体（原"沿轨迹逐点摆"退化用） |
| `region(level, particle, AABB box, count, speed)` | AABB 区域内散布 |
| `regionCapped(level, particle, AABB box, count, speed, maxSpread)` | 同上，但各轴散布有上限（长轨迹不会被摊成一整片） |
| `sendCustom(...)` / `sendCustomBatch(...)` | 自定义形状粒子，见下节 |

### SceneParticles — 场景方块粒子

`org.agmas.noellesroles.scene.SceneParticles`（服务端），同样每个方法只发一个包：`burst` / `blockBurst` / `ring` / `column` / `columnDown` / `regionScatter`。

### 自定义形状粒子 / Custom Shape Particles

环形、螺旋、沿轨迹、跟随实体、多边形范围提示等形状，原版包**表达不了**（原版只有"一个点 + 高斯散布"）。约定：**服务端只发一个包，形状由客户端按 id 生成**——客户端逐点 `addParticle` 不进网络，形状可以任意复杂，也不会有额外包数。

服务端只提供 id、原点、时长和自定义参数：

```java
public static final ResourceLocation SHOCK_WAVE =
        ResourceLocation.fromNamespaceAndPath("mymod", "shock_wave");

// 一条；最后是自定义参数（最多 8 个 float，含义完全由客户端处理器定义）
ParticleFx.sendCustom(level, SHOCK_WAVE, pos, 20, 6.0F, 0.5F);

// 同一时刻多条：合并成一个包（不要循环调用 sendCustom）
ParticleFx.sendCustomBatch(level, center, List.of(
        CustomParticleS2CPayload.Entry.at(SHOCK_WAVE, pos.x, pos.y, pos.z, 20, 6.0F),
        CustomParticleS2CPayload.Entry.at(SHOCK_WAVE, pos2.x, pos2.y, pos2.z, 20, 3.0F)));
```

客户端注册处理器（客户端初始化处注册一次，例如 `SREClient` 的初始化流程或该功能自己的客户端初始化方法）：

```java
CustomParticleHandlers.register(SHOCK_WAVE, (level, origin, durationTicks, params) -> {
    double radius = params.length > 0 ? params[0] : 1.0D;
    double speed = params.length > 1 ? params[1] : 0.0D;
    for (int i = 0; i < 64; i++) {
        double a = Math.PI * 2.0 * i / 64;
        level.addParticle(ParticleTypes.END_ROD,
                origin.x + Math.cos(a) * radius, origin.y, origin.z + Math.sin(a) * radius,
                0, 0, 0);
    }
});
```

`Handler.play(ClientLevel level, Vec3 origin, int durationTicks, float[] params)` 在客户端主线程调用，未做距离判断（服务端已按半径筛过接收者）。需要持续多帧的特效可利用 `durationTicks`，在客户端自行按 tick 推进（例如存到客户端的特效列表里逐帧生成）。

约束与行为：

| 项 | 值 / 行为 |
| --- | --- |
| 单个包最多携带 | `CustomParticleS2CPayload.MAX_ENTRIES` = 64 条（服务端超出会截断并打告警；客户端超出直接断开连接） |
| 每条最多参数 | `CustomParticleS2CPayload.MAX_PARAMS` = 8 个 float |
| 广播半径 | `ParticleFx.CUSTOM_FX_RANGE` = 64 格（按原点筛选接收者，不会发给其他维度） |
| 未知 id | 客户端**静默忽略**，不报错——服务端可以先上新特效，客户端后续版本再补处理器 |
| 重复注册 id | 客户端立刻抛 `IllegalStateException`，便于早发现冲突 |
| 调试 | `CustomParticleHandlers.isRegistered(id)` / `registeredIds()` |

包 id 为 `starrailexpress:custom_particle_s2c`（`CustomParticleS2CPayload.ID`），类型注册在 `SREPayloadRegister`，客户端接收在 `SREClient`。

---

## 参考 / References

- 角色系统源码：`src/main/java/io/wifi/starrailexpress/api/`
- 事件列表：`src/main/java/io/wifi/starrailexpress/event/`
- 技能系统：`src/main/java/io/wifi/starrailexpress/api/RoleSkill.java`
- 修饰符系统：`src/main/java/org/agmas/harpymodloader/modifiers/`
- Harpymodloader 事件：`src/main/java/org/agmas/harpymodloader/events/`
- Noellesroles 事件：`src/main/java/org/agmas/noellesroles/events/`
- 皮肤管理：`src/main/java/io/wifi/starrailexpress/util/ItemSkinManager.java`
- 粒子/特效：`src/main/java/io/wifi/starrailexpress/util/ParticleFx.java` · `src/main/java/io/wifi/starrailexpress/client/particle/CustomParticleHandlers.java` · `src/main/java/io/wifi/starrailexpress/network/packet/CustomParticleS2CPayload.java` · `src/main/java/org/agmas/noellesroles/scene/SceneParticles.java`
- 工具类：`src/main/java/io/wifi/starrailexpress/util/SREItemUtils.java` · `src/main/java/org/agmas/noellesroles/utils/RoleUtils.java`
- 创建扩展指南：[`CreateExtention.md`](../CreateExtention.md)
- 中文 README：[`README.zh.md`](../README.zh.md)
