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
7. [蓄力物品系统 / Chargeable Item System](#蓄力物品系统--chargeable-item-system)
   - [ChargeableItem — 蓄力物品接口](#chargeableitem--蓄力物品接口)
   - [ChargeableItemRegistry — 蓄力物品注册表](#chargeableitemregistry--蓄力物品注册表)
8. [物品类型 / Item Types](#物品类型--item-types)
   - [可继承物品基类](#可继承物品基类)
   - [SkinableItem — 可换皮肤物品](#skinableitem--可换皮肤物品)
9. [皮肤系统 / Skin System](#皮肤系统--skin-system)
   - [SkinManager — 皮肤工具类](#skinmanager--皮肤工具类)
   - [注册自定义皮肤](#注册自定义皮肤)
10. [事件系统 / Event System](#事件系统--event-system)
    - [游戏生命周期事件](#游戏生命周期事件)
    - [玩家死亡事件](#玩家死亡事件)
    - [技能与交互事件](#技能与交互事件)
    - [渲染与客户端事件](#渲染与客户端事件)
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
    - [TMMItemUtils — 物品工具](#tmmitemutils--物品工具)
    - [RoleUtils — 角色工具](#roleutils--角色工具)
15. [Replay 系统 / Replay System](#replay-系统--replay-system)
    - [IGameReplayRecorder — 回放记录接口](#igamereplayrecorder--回放记录接口)
    - [IGameReplayReader — 回放读取接口](#igamereplayreader--回放读取接口)
    - [ReplayEventTypes — 事件类型枚举](#replayeventtypes--事件类型枚举)

---

## 重要提醒 / Important Notes

- **不要引用 Wathe 的库**，它会导致崩溃（未初始化）。  
  **Do NOT import Wathe libraries** — they will cause crashes (uninitialized state). 比如 `GameFunctions`，不要用他！请使用 `GameUtils` 代替！
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

// 注册修饰符（返回修饰符本身，支持链式）
SREModifier HMLModifiers.registerModifier(SREModifier modifier)
```

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
    .setMax(2)     // 同场最多 2 人拥有
    .setServerGameTickEvent(player -> {
        // 每 Tick 执行的服务端逻辑
    })
);
```

#### 添加配置（可选）

修饰符每局分配数量受 `HarpyModLoaderConfig` 中两个参数控制：  
- `modifierMaximum`：每名玩家最多修饰符数量（默认 1）
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
| `ArrayList<SREModifier> getModifiers(Player player)` | 获取玩家所有修饰符 |
| `ArrayList<SREModifier> getModifiers(UUID uuid)` | 同上（UUID 版） |
| `HashMap<UUID, ArrayList<SREModifier>> getModifiers()` | 获取全局修饰符映射 |
| `List<UUID> getAllWithModifier(SREModifier modifier)` | 获取拥有该修饰符的所有玩家 |
| `void addModifier(UUID player, SREModifier modifier)` | 为玩家添加修饰符（并同步） |
| `void removeModifier(UUID player, SREModifier modifier)` | 移除玩家修饰符（并同步） |
| `ArrayList<SREModifier> getDisplayableModifiers(Player player)` | 获取可展示给该玩家的修饰符列表 |

> **注意：** 修饰符添加/移除会分别触发 `ModifierAssigned.EVENT` / `ModifierRemoved.EVENT`，见[HML 事件](#hml-事件)。

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

## 物品类型 / Item Types

### 可继承物品基类

以下是游戏中可被继承扩展的物品基类，每个都提供了特定的游戏机制钩子。

| 类 | 包 | 说明 |
|---|---|---|
| `SkinableItem` | `io.wifi.starrailexpress.contents.item` | 抽象基类，支持皮肤系统的物品 |
| `KnifeItem` | `io.wifi.starrailexpress.contents.item` | 近战刀（继承 `SkinableItem`），蓄力刺杀 |
| `RevolverItem` | `io.wifi.starrailexpress.contents.item` | 左轮手枪（继承 `SkinableItem`），有耐久度 |
| `BatItem` | `io.wifi.starrailexpress.contents.item` | 球棒（继承 `SkinableItem`） |
| `GrenadeItem` | `io.wifi.starrailexpress.contents.item` | 手雷（继承 `SkinableItem`），蓄力投掷 |
| `DefenseItem` | `io.wifi.starrailexpress.contents.item` | 防具/防御物品（继承 `Item`），限制使用职业 |
| `NoteItem` | `io.wifi.starrailexpress.contents.item` | 便签（继承 `Item` + `AdventureUsable`） |

#### DefenseItem — 防御物品

`DefenseItem` 是防具类物品的基类，使用动画为 `DRINK`。可通过 `canUseByRightClickRolePaths` 白名单限制使用该物品的职业路径（path 字符串）：

```java
// 允许特定职业路径使用（path = identifier().getPath()）
DefenseItem.canUseByRightClickRolePaths.add("my_role");
```

---

### SkinableItem — 可换皮肤物品

**包 / Package:** `io.wifi.starrailexpress.contents.item`

继承此抽象类以创建支持皮肤系统的物品。  
Extend this abstract class to create an item that supports the skin system.

```java
public class MyWeapon extends SkinableItem {
    public MyWeapon(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemSkinType() {
        // 返回皮肤类型名称（需与 SkinManager.registerType 中注册的名称一致）
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

### SkinManager — 皮肤工具类

**包 / Package:** `io.wifi.starrailexpress.util`

皮肤系统的核心管理类，负责皮肤注册、查询、锁定/解锁，以及玩家皮肤状态持久化。  
Core skin system manager: handles registration, querying, lock/unlock, and player skin persistence.

#### 注册自定义皮肤

```java
// 1. 注册皮肤类型（须在 SkinManager 静态初始化顺序之后，建议在 mod onInitialize 中调用）
SkinManager.registerType("my_weapon");

// 2. 注册具体皮肤（type, skinID, color）
SkinManager.registerSkin("my_weapon", "default", Colors.LIGHT_GRAY);
SkinManager.registerSkin("my_weapon", "gold",    0xFFD700);
SkinManager.registerSkin("my_weapon", "iron",    0xAAAAAA);
```

#### 皮肤数据操作

```java
// 检查玩家是否解锁了某皮肤
boolean unlocked = SkinManager.isSkinUnlocked(player, itemStack, "gold");

// 解锁皮肤给玩家
SkinManager.unlockSkin(player, itemStack, "gold");

// 按物品类型解锁皮肤（无 ItemStack 版本）
SkinManager.unlockSkinForItemType(player, "my_weapon", "gold");

// 锁定皮肤（移除解锁状态）
SkinManager.lockSkin(player, itemStack, "gold");

// 获取玩家当前装备的皮肤
String skinName = SkinManager.getEquippedSkin(player, itemStack);

// 设置玩家当前装备皮肤
SkinManager.setEquippedSkin(player, itemStack, "gold");
SkinManager.setEquippedSkinForItemType(player, "my_weapon", "gold");

// 同步皮肤数据给客户端
SkinManager.sync(player);
```

#### 皮肤彩券货币

皮肤系统内置两种货币用于彩券（开箱）系统：

```java
// 获取/增加彩券抽取次数
int chances = SkinManager.getLootChance(player);
SkinManager.addLootChance(player, 1);

// 获取/增加皮肤货币数量
int coins = SkinManager.getCoinNum(player);
SkinManager.addCoinNum(player, 100);
```

#### SkinManager.Skin — 皮肤数据类

```java
SkinManager.Skin skin = SkinManager.Skin.fromString("my_weapon", "gold");
int color = skin.getColor();   // 颜色值
String name = skin.getName();  // 皮肤小写名称
String tooltip = skin.tooltipName; // Tooltip 显示名
```

#### 内置皮肤类型 / Built-in Skin Types

| 常量 | 字符串值 |
|---|---|
| `SkinManager.SkinTypes.KNIFE` | `"knife"` |
| `SkinManager.SkinTypes.REVOLVER` | `"revolver"` |
| `SkinManager.SkinTypes.BAT` | `"bat"` |
| `SkinManager.SkinTypes.GRENADE` | `"grenade"` |
| `SkinManager.SkinTypes.HAT` | `"hat"` |

#### 皮肤品质颜色 / QualityColor

```java
SkinManager.QualityColor.COMMON       // 0xFFEEEEEE 白灰
SkinManager.QualityColor.UNCOMMON     // 0xFF33FF55 绿色
SkinManager.QualityColor.RARE         // 0xFFAAAAFF 蓝色
SkinManager.QualityColor.EPIC         // 0xFFAA55FF 紫色
SkinManager.QualityColor.LEGENDARY    // 0xFFFFAA55 金色
SkinManager.QualityColor.UNBELIEVABLE // 0xFFFF3F3F 红色
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

```java
// 设置：分配 DOCTOR 的同时也分配 POISONER
Harpymodloader.setOccupationRole(ModRoles.DOCTOR, ModRoles.POISONER);

// 查询
SRERole companion = Harpymodloader.getOccupationRole(ModRoles.DOCTOR); // POISONER
boolean has = Harpymodloader.hasOccupationRole(ModRoles.DOCTOR);

// 移除
Harpymodloader.removeOccupationRole(ModRoles.DOCTOR);
Harpymodloader.clearOccupationRoles();
```

#### 隐藏修饰符 / Hide Modifiers

将修饰符 path 加入 `HIDDEN_MODIFIERS` 可使其不在 UI 中展示（但仍可被分配）。

```java
Harpymodloader.HIDDEN_MODIFIERS.add(NRModifiers.INTROVERTED.identifier().getPath());
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
| `NOTE_EDIT` | `NoteEditDetails` | 便签编辑 |
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
GameUtils.addItemCooldowns(serverLevel);
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
GameUtils.SPAliveResult result = GameUtils.isPlayerSplitPersonalityAndSurvive(player);
// result: ALIVE | DEAD | NOT_APPLICABLE
```

#### 玩家击杀 / Kill Player

```java
// 击杀玩家（可指定死亡原因，触发 AllowPlayerDeath/OnPlayerDeath 等事件）
GameUtils.killPlayer(victim, spawnBody, killer);
GameUtils.killPlayer(victim, spawnBody, killer, GameConstants.DeathReasons.KNIFE_STAB);

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
// 注册自定义胜利判断谓词（配合 WinStatus.CUSTOM 使用）
GameUtils.CustomWinnersPredicates.add(entry -> {
    Player player = entry.getKey();
    String roleId = entry.getValue();
    return roleId.equals("my_role"); // 满足条件的玩家为胜利者
});
```

---

### TMMItemUtils — 物品工具

**包 / Package:** `io.wifi.starrailexpress.util`

提供玩家背包物品清除与统计的简便方法，自动同步背包 UI。  
Provides convenient player inventory clear/count methods that auto-sync the inventory UI.

```java
// 清除玩家背包中指定物品（全部），返回清除数量
int count = TMMItemUtils.clearItem(player, TMMItems.KNIFE);
int count = TMMItemUtils.clearItem(player, TMMItemTags.GUNS);       // 按标签
int count = TMMItemUtils.clearItem(player, stack -> stack.isDamaged()); // 按谓词

// 清除指定数量
int count = TMMItemUtils.clearItem(player, TMMItems.KNIFE, 1);
int count = TMMItemUtils.clearItem(player, predicate, 3);

// 统计玩家背包中物品数量（不清除）
int has = TMMItemUtils.hasItem(player, TMMItems.KNIFE);
int has = TMMItemUtils.hasItem(player, TMMItemTags.GUNS);
int has = TMMItemUtils.hasItem(player, predicate);
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
RoleUtils.RemoveAllPlayerAttributes(serverPlayer);

// 清除所有药水效果
boolean removed = RoleUtils.RemoveAllEffects(player);
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
SRERole role = RoleUtils.getRoleFromName("killer");  // Noellesroles 命名空间
SRERole role = RoleUtils.getRole(roleId);             // 任意 ResourceLocation

// 判断两职业是否相同（null 安全）
boolean eq = RoleUtils.compareRole(role1, role2);
```

---

## 参考 / References

- 角色系统源码：`src/main/java/io/wifi/starrailexpress/api/`
- 事件列表：`src/main/java/io/wifi/starrailexpress/event/`
- 技能系统：`src/main/java/org/agmas/noellesroles/RoleSkill.java`
- 修饰符系统：`src/main/java/org/agmas/harpymodloader/modifiers/`
- Harpymodloader 事件：`src/main/java/org/agmas/harpymodloader/events/`
- Noellesroles 事件：`src/main/java/org/agmas/noellesroles/events/`
- 皮肤管理：`src/main/java/io/wifi/starrailexpress/util/SkinManager.java`
- 工具类：`src/main/java/io/wifi/starrailexpress/util/TMMItemUtils.java` · `src/main/java/org/agmas/noellesroles/utils/RoleUtils.java`
- 创建扩展指南：[`CreateExtention.md`](../CreateExtention.md)
- 中文 README：[`README.zh.md`](../README.zh.md)
