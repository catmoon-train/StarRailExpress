# StarRail Express 开发者 API 文档
# StarRail Express Developer API Documentation

> 本文档面向希望为 StarRail Express 编写扩展的开发者。  
> This document is intended for developers who want to write extensions for StarRail Express.

---

## 目录 / Table of Contents

1. [重要提醒 / Important Notes](#重要提醒--important-notes)
2. [角色系统 / Role System](#角色系统--role-system)
   - [SRERole — 角色基类](#srerole--角色基类)
   - [NormalRole — 标准角色](#normalrole--标准角色)
   - [ExtraEffectRole — 药水效果角色](#extraeffectrole--药水效果角色)
   - [TMMRoles — 角色注册表](#tmmroles--角色注册表)
3. [CCA 组件 / CCA Components](#cca-组件--cca-components)
   - [RoleComponent — 角色组件接口](#rolecomponent--角色组件接口)
   - [SREAbilityPlayerComponent — 通用技能组件](#sreabilityplayercomponent--通用技能组件)
4. [技能系统 / Skill System](#技能系统--skill-system)
   - [RoleSkill — 技能注册](#roleskill--技能注册)
5. [商店系统 / Shop System](#商店系统--shop-system)
   - [ShopEntry — 商店条目](#shopentry--商店条目)
   - [ShopContent — 商店内容管理](#shopcontent--商店内容管理)
6. [蓄力物品系统 / Chargeable Item System](#蓄力物品系统--chargeable-item-system)
   - [ChargeableItem — 蓄力物品接口](#chargeableitem--蓄力物品接口)
   - [ChargeableItemRegistry — 蓄力物品注册表](#chargeableitemregistry--蓄力物品注册表)
7. [事件系统 / Event System](#事件系统--event-system)
   - [游戏生命周期事件](#游戏生命周期事件)
   - [玩家死亡事件](#玩家死亡事件)
   - [技能与交互事件](#技能与交互事件)
   - [渲染与客户端事件](#渲染与客户端事件)
   - [其他事件](#其他事件)
8. [游戏模式系统 / Game Mode System](#游戏模式系统--game-mode-system)
   - [GameMode — 游戏模式基类](#gamemode--游戏模式基类)
   - [SREGameModes — 游戏模式注册表](#sregamemodes--游戏模式注册表)
9. [HUD 渲染 / HUD Rendering](#hud-渲染--hud-rendering)
10. [Replay 系统 / Replay System](#replay-系统--replay-system)
    - [IGameReplayRecorder — 回放记录接口](#igamereplayrecorder--回放记录接口)
    - [IGameReplayReader — 回放读取接口](#igamereplayreader--回放读取接口)
    - [ReplayEventTypes — 事件类型枚举](#replayeventtypes--事件类型枚举)

---

## 重要提醒 / Important Notes

- **不要引用 Wathe 的库**，它会导致崩溃（未初始化）。  
  **Do NOT import Wathe libraries** — they will cause crashes (uninitialized state).
- 网络同步压力在人少时几乎不可见，但在 16 人以上的服务器上会非常明显，请遵循"尽量不同步"原则。  
  Network sync overhead is negligible with few players but significant on servers with 16+ players. Minimize unnecessary sync.
- 尽量使用 `RoleComponent` 将存储和同步逻辑分离，避免污染玩家 NBT。  
  Prefer `RoleComponent` to separate storage/sync logic and avoid polluting player NBT.

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
               boolean hideScoreboard)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `identifier` | `ResourceLocation` | 角色唯一 ID |
| `color` | `int` | 通告颜色（ARGB 整数） |
| `isInnocent` | `boolean` | 是否属于乘客（平民）阵营 |
| `canUseKiller` | `boolean` | 是否具有杀手能力 |
| `moodType` | `MoodType` | 心情类型：`NONE` / `REAL` / `FAKE` |
| `maxSprintTime` | `int` | 最大冲刺时间（tick），`-1` 为无限制 |
| `hideScoreboard` | `boolean` | 是否隐藏计分板 |

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
SRERole setCanSeeTeammateKiller(boolean canSeeKiller)    // 是否可以看到队友杀手身份
SRERole setOccupiedRoleCount(int count)                  // 占用角色池数量（默认 1）
SRERole setMax(int count)                                // 设置最大同时存在数量
SRERole setAutoReset(boolean autoReset)                  // 游戏结束是否自动重置
SRERole setComponentKey(ComponentKey<? extends RoleComponent> key) // 关联 CCA 组件
SRERole setCanAutoAddMoney(boolean bl)                   // 是否启用自动加钱（被动收入）
SRERole setCanHavePassiveIncome(boolean bl)              // 是否启用被动收入
SRERole addChild(Consumer<LimitedInventoryScreen> addChild) // 添加 HUD 子元素
SRERole setServerGameTickEvent(BiConsumer<ServerPlayer, SREGameWorldComponent> event) // 服务端 Tick 回调
SRERole setClientGameTickEvent(BiConsumer<Player, SREGameWorldComponent> event)       // 客户端 Tick 回调
```

#### 可重写的回调方法 / Overridable Callbacks

```java
// 玩家死亡时调用（可返回 false 阻止尸体生成）
boolean onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason)

// 杀手杀死玩家时调用
boolean onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason)

// 完成任务时调用
void onFinishQuest(Player player, String quest)

// 角色初始化（游戏开始分配角色后）
void onInit(MinecraftServer server, ServerPlayer serverPlayer)

// 服务端每 Tick 调用（游戏进行中）
void serverTick(ServerPlayer player)

// 客户端每 Tick 调用（游戏进行中）
void clientTick(Player player)

// 右键实体
void rightClickEntity(Player player, Entity victim)

// 左键实体
void leftClickEntity(Player player, Entity victim)

// 使用物品（G 键）
void onAbilityUse(Player player)

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

// 限定哪些物品不能被该职业拾取
Predicate<Item> cantPickupItem(Player player)

// 获取角色初始物品列表
List<ItemStack> getDefaultItems()

// 获取角色商店条目列表
List<ShopEntry> getShopEntries()
```

#### 枚举 MoodType

| 值 | 说明 |
|----|------|
| `NONE` | 无心情 |
| `REAL` | 真实心情 |
| `FAKE` | 假心情 |

#### 获取技能冷却组件

```java
// 静态辅助方法，从玩家获取通用技能组件
SREAbilityPlayerComponent component = SRERole.getCooldownComponent(player);
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
new ExtraEffectRole(ResourceLocation id, int color, boolean isInnocent,
                    boolean canUseKiller, MoodType moodType, int maxSprintTime,
                    boolean hideScoreboard, MobEffectInstance... effects)
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
| `CIVILIAN` | `sre:civilian` | 乘客 |
| `VIGILANTE` | `sre:vigilante` | 自警阵营 |
| `KILLER` | `sre:killer` | 杀手 |
| `LOOSE_END` | `sre:loose_end` | 散局（中立） |

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
    .setComponentKey(ModComponents.MY_ROLE)  // 关联组件（可空）
    .setCanSeeCoin(true)
    .setOccupiedRoleCount(2)
);
```

#### 注册角色组件键 / Register Role Component Key

```java
TMMRoles.addRoleComponents(ModComponents.MY_COMPONENT);
```

---

## CCA 组件 / CCA Components

### RoleComponent — 角色组件接口

**包 / Package:** `io.wifi.starrailexpress.api`

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
SREAbilityPlayerComponent comp = SRERole.getCooldownComponent(player);
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

**包 / Package:** `org.agmas.noellesroles`

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
RoleSkill.beginUseWithTarget(player, targetUUID);
```

#### 其他方法 / Other Methods

```java
boolean isRegistered(ResourceLocation role)   // 是否已注册
boolean isRegistered(SRERole role)
boolean unregister(ResourceLocation role)     // 注销技能
boolean tryRegister(ResourceLocation, Consumer<RoleSkillContext>)  // 失败时返回 false 而不抛异常

// 手动触发技能（服务端，含 BEFORE/AFTER 事件）
boolean beginUse(ServerPlayer player)
boolean beginUseWithTarget(ServerPlayer player, UUID target)
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
// 注册蓄力物品
ChargeableItemRegistry.register(MyItems.MY_ITEM, new ChargeableItem() {
    @Override
    public int getMaxChargeTime(ItemStack stack, Player player) { return 20; }

    @Override
    public float getChargePercentage(ItemStack stack, Player player, int ticks) {
        return Math.min(1.0f, ticks / 20.0f);
    }
});

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
AllowGameEnd.EVENT.register((serverLevel, currentWinStatus, isLooseEndsMode) -> {
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
| `CUSTOM` | 自定义胜利（需设置 `RoundEndComponent.CustomWinnerID` 和 `CustomWinnersPredicates`） |

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

#### `OnRoundStartWelcomeTimer` — 开场欢迎计时器

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
ShouldDropOnDeath.EVENT.register((player) -> true);
```

#### `OnShieldBroken` — 护盾破碎

**类型:** 通知型。

```java
OnShieldBroken.EVENT.register((player) -> { /* ... */ });
```

#### `OnTeammateKilledTeammate` — 队友击杀队友

**类型:** 通知型。

```java
OnTeammateKilledTeammate.EVENT.register((victim, killer) -> { /* ... */ });
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
OnRevolverUsed.EVENT.register((player) -> { /* ... */ });
```

#### `IsShootBackFire` — 是否触发后坐力

**类型:** 可返回 `true` 触发后坐力。

#### `AllowShootRevolverDrop` — 是否允许左轮射击时掉落子弹

**类型:** 可拦截。

#### `IsPlayerPunchable` — 玩家是否可被击打

**类型:** 返回 `true` 表示可被击打。

```java
IsPlayerPunchable.EVENT.register((attacker, target) -> true);
```

#### `AllowPlayerPunching` — 是否允许玩家出拳

**类型:** 可拦截。

```java
AllowPlayerPunching.EVENT.register((attacker, target) -> true);
```

#### `AllowPlayerOpenLockedDoor` — 是否允许玩家开锁

**类型:** 可拦截。

```java
AllowPlayerOpenLockedDoor.EVENT.register((player) -> true);
```

#### `OnGetInstinctHighlight` — 获取本能高亮实体

**类型:** 首个非 `null` 列表返回值生效。可自定义本能技能高亮的实体范围。

```java
OnGetInstinctHighlight.EVENT.register((player) -> {
    // 返回需要高亮的实体列表，或 null 跳过
    return null;
});
```

#### `OnGiveKillerBalance` — 给予杀手金币

**类型:** 可拦截。

#### `OnKillerCohortDisplay` — 杀手同伙显示

**类型:** 通知型。控制杀手同伙的显示方式。

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
AllowItemShowInHand.EVENT.register((player, stack) -> true);
```

#### `AllowOtherCameraType` — 是否允许使用非第一人称视角

**类型:** 可拦截（客户端）。

```java
AllowOtherCameraType.EVENT.register((player) -> true);
```

#### `ClientHeldItemSwitchEvent` — 客户端切换手持物品

**类型:** 通知型（客户端）。

#### `OnOpenInventory` — 是否需要打开限制背包

**类型:** 任意监听器返回 `true` 则打开限制背包界面。

```java
OnOpenInventory.EVENT.register((localPlayer, screen) -> false);
```

---

### 其他事件

#### `CanSeePoison` — 是否可以看到毒药相关内容

**类型:** 可拦截。

#### `AFKEventHandler` — AFK 事件处理

**类型:** 通知型。玩家进入/离开 AFK 状态时触发。

#### `PlayerInteractionHandler` — 玩家交互处理

通用玩家交互处理入口，与 `EntityInteractionHandler` 类似。

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
    public void tickCommonGameLoop() {}
    // 客户端每 Tick
    public void tickClientGameLoop() {}
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

`DISCOVERY_ID = sre:discovery` — Discovery 模式 ID（仅注册，无对应 `GameMode` 常量）

#### 注册自定义游戏模式

```java
public static final ResourceLocation MY_MODE_ID = SRE.id("my_mode");
public static final GameMode MY_MODE = SREGameModes.registerGameMode(MY_MODE_ID, new MyGameMode(MY_MODE_ID));
```

---

## HUD 渲染 / HUD Rendering

如果 HUD 是针对特定职业的，使用 `RoleHudRenderCallback`。  
For role-specific HUD elements, use `RoleHudRenderCallback`.

**包 / Package:** `org.agmas.noellesroles` (通过 `RicesRoleRhapsody` 注册)

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
recorder.recordEvent(EventType.PLAYER_KILL, new PlayerKillDetails(killerUUID, victimUUID));

// 记录自定义事件
recorder.recordCustomEvent(MY_EVENT_ID, playerUUID, "custom message");
```

### IGameReplayReader — 回放读取接口

**包 / Package:** `io.wifi.starrailexpress.api.replay`

```java
List<ReplayEvent> all = reader.getEvents();
List<ReplayEvent> inRange = reader.getEventsInTimeRange(startMs, endMs);
List<ReplayEvent> byPlayer = reader.getEventsByPlayer(uuid);
List<ReplayEvent> byType = reader.getEventsByType(EventType.PLAYER_KILL);
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
| `ITEM_USED` / `ITEM_USE` | `ItemUsedDetails` | 物品使用 |
| `MOOD_CHANGE` | `MoodChangeDetails` | 心情变化 |
| `NOTE_EDIT` | `NoteEditDetails` | 便条编辑 |
| `GAME_START` / `GAME_END` | — | 游戏开始/结束 |
| `ROLE_ASSIGNMENT` | — | 角色分配 |
| `BLACKOUT_START` / `BLACKOUT_END` | `BlackoutEventDetails` | 停电事件 |
| `ROUND_END` | `RoundEndDetails` | 回合结束 |
| `KEY_USED` | `KeyUsedDetails` | 钥匙使用 |
| `SKILL_USED` | — | 技能使用 |
| `PSYCHO_STATE_CHANGE` | `PsychoStateChangeDetails` | 精神状态变化 |
| `GUN_FIRED` | `GunFiredDetails` | 枪械射击 |
| `GRENADE_THROWN` | `GrenadeThrownDetails` | 手雷投掷 |
| `CUSTOM_MESSAGE` | `CustomEventDetails` | 自定义事件 |

#### 注册自定义事件序列化器

```java
ReplayEventRegistry.registerCustomEvent(
    MY_CUSTOM_EVENT_ID,    // ResourceLocation
    MyEventDetails.class,
    (details, json) -> { /* 序列化 */ },
    (json) -> { /* 反序列化 */ return new MyEventDetails(...); }
);
```

---

## 参考 / References

- 角色系统源码：`src/main/java/io/wifi/starrailexpress/api/`
- 事件列表：`src/main/java/io/wifi/starrailexpress/event/`
- 技能系统：`src/main/java/org/agmas/noellesroles/RoleSkill.java`
- Noellesroles 事件：`src/main/java/org/agmas/noellesroles/events/`
- 创建扩展指南：[`CreateExtention.md`](../CreateExtention.md)
- 中文 README：[`README.zh.md`](../README.zh.md)
