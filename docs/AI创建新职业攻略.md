# AI 创建新职业攻略（StarRailExpress / noellesroles）

> 面向「下次再来新建职业的 AI / 人类」的实操攻略。目标是**照着抄就能跑通**，并且**改动都集中在少数几个文件里**，方便后续调试与修改。
> 配套阅读：`AGENT.md`、`ai_doc.md`、`docs/角色开发指南.md`（概念）、`docs/api.md`（API 清单，可能过时，以源码为准）。
>
> 本文所有路径与写法都来自本仓库真实代码，示例取自最近的「程序员（programmer）」职业实现，可直接搜同名文件对照。

---

## 0. 三条铁律（先看这里）

1. **职业相关的方法尽量写在 SRERole 子类（和 RoleData）里，集中！**
   - 商店、技能、物品交互、自己的指令解析、准入校验……全部放在一个 `XxxRole extends NormalRole/EggRole` 里。
   - 物品类 / 数据包接收器 / 客户端界面 **只做薄转发**：`XxxRole.doSomething(player, ...)`。
   - 好处：改数值、改白名单、改文案逻辑只动一个文件；出 bug 时一个类就能读完；不会被「逻辑散落在事件、商店注册表、接收器」坑到。

2. **优先用 API 与事件，不要写 mixin；不要改动 `io/wifi/starrailexpress/` 里的代码**（那是主模组作者的权益），只能调用它的 API。

3. **不要手写 GPL 文件头**（`/* This program is free software ... */`），本仓库在最后提交时统一补。新文件直接写 `package` 开头即可。

✅ 正例 / ❌ 反例：

```text
❌ 角色的商店写进 RoleShopHandler、指令解析写进 ModPacketsReciever、状态用 CCA 记、还顺手 mixin 了 Screen
✅ 一个 ProgrammerRole 类：getShopEntries() + canUseTerminal() + parseTerminalCommand() + executeTerminalCommand()
   RoleShopHandler / ModPacketsReciever / TerminalScreen 各自只有 1~3 行转发
```

---

## 1. 最短路径：新建一个职业要动哪些文件

| 步骤 | 文件 | 必做 |
| --- | --- | --- |
| ① 职业类（逻辑集中地） | `src/main/java/org/agmas/noellesroles/role/bouns/roles/XxxRole.java`（彩蛋角色）或直接写在 `role/ModRoles.java`（普通职业） | ✅ |
| ② 注册职业 | `role/bouns/BounsRoles.java` 或 `role/ModRoles.java`：`TMMRoles.registerRole(new XxxRole(...))` | ✅ |
| ③ 职业数据（有每人状态/冷却才要） | `org/agmas/noellesroles/role_data/**/XxxRoleData.java` + `.setRoleData(XxxRoleData::new)` | 按需 |
| ④ 专属物品 | `init/ModItems.java` / `init/FunnyItems.java` + `content/item/XxxItem.java` + 模型 json | 按需 |
| ⑤ 商店 | 职业类里覆写 `getShopEntries()`（推荐）或 `init/RoleShopHandler.java` 里 `ShopContent.customEntries.put(...)` | 按需 |
| ⑥ 技能 | `init/ModRolesInitialEventRegister` 的 `static {}` 里 `RoleSkill.register(...)`，或是在 xxxHandlers（彩蛋/东方/番剧职业）里引用 XXXRole 的 static 方法注册（**两种写法见 §4**） | 按需 |
| ⑦ 网络包 | `packet/XxxC2SPacket.java` + 注册 `init/ModPackets.java` + 接收 `init/ModPacketsReciever.java` | 按需 |
| ⑧ 客户端界面 | `client/screen/XxxScreen.java`；打开方式二选一：**纯客户端回调**（阴谋之书页那种，零网络包）或 `utils/OpenScreenManager.java`（统一的打开简单UI payload，避免注册新的包）+ `client/ClientOpenScreenManager.java`（**见 §7**） | 按需 |
| ⑨ 翻译键 | 三处语言文件（见 §8） | ✅ |
| ⑩ 编译 | `./gradlew compileJava --offline` 或 `./gradlew build --offline` | ✅ |

> 特殊刷新池 / 自定义胜利：继承 `EggRole` / `TouhouRole` / `CustomWinnerRole`，或直接 `implements EggRoleInterface` / `TouhouRoleInterface` / `CustomWinnerRoleInterface`（**接口说明见 §12**）。

> 职业注册**不需要**改任何中央清单：`TMMRoles` 全局 map 会自动收录（`registerRole` 里按 `identifier` 注册）。但职业名/描述等翻译键是**必须**补的，否则游戏里显示错误文本。

---

## 2. 注册职业：构造器参数逐项说明

注册表：`io/wifi/starrailexpress/api/TMMRoles.java`（`registerRole(SRERole)` → 返回该角色，可继续链式设置）。
基类：`io/wifi/starrailexpress/api/SRERole.java`、`NormalRole.java`、`EggRole.java`、`OriginalRole.java`。

### 2.1 选哪个基类

| 基类 | 用途 | 备注 |
| --- | --- | --- |
| `NormalRole` | 普通职业（最常见的默认选择） | 构造器会自动 `setPassiveIncome(canUseKiller)`、`setNeutrals(!isInnocent && !canUseKiller)` |
| `EggRole` | **彩蛋职业** | 同上 + `addFlag("bouns")`；`canBeRandomed()` 受彩蛋总开关 `InitModRolesMax.isEggEnabled` 控制，刷新概率用 `setDefaultEnableChance(200)` ≈ 2% |
| `OriginalRole` | 原版基础职业（如 `TMMRoles.KILLER`） | 一般不用自己 `new` |

> 另外还有三个「角色接口」会改变刷新池与胜利判定：`EggRoleInterface` / `TouhouRoleInterface`（彩蛋池、东方池的开关）与 `CustomWinnerRoleInterface`（自定义/独立胜利）。**详见 §12**。

### 2.2 两个构造器形态

```java
// 形态 A：直接给阵营布尔值（推荐，语义最清楚）
new NormalRole(
    ResourceLocation identifier, // 例：BounsRoles.id("programmer") / Noellesroles.id("guard")
    int color,                   // 0xRRGGBB，常用 new Color(r, g, b).getRGB()
    boolean isInnocent,          // true = 乘客/好人阵营；false = 杀手阵营
    boolean canUseKiller,        // 是否有杀手能力（杀手商店、被动收入、直觉等）
    SRERole.MoodType moodType,   // NONE / REAL / FAKE
    int maxSprintTime,           // 冲刺上限 tick；-1 或 Integer.MAX_VALUE = 无限
    boolean canSeeTime           // 是否显示计时（注意 SRERole 里这个参数历史上叫 hideScoreboard 的位置，以签名 javadoc 为准）
)

// 形态 B：用 RoleType 自动推导阵营（见 NormalRole#NormalRole(ResourceLocation,int,RoleType,...)）
// RoleType.CIVILIAN / VIGILANTE → isInnocent=true；RoleType.KILLER → canUseKiller=true，还会顺带设置警长阵营等
new WitchMaidenRole(id, color, RoleType.NEUTRALS_FOR_KILLERS, MoodType.FAKE, Integer.MAX_VALUE, true)
```

SRERole 构造器本身还会顺带设：`ableToPickUpRevolver = isInnocent`、`canUseInstinct = canUseKiller`。
**注意**：`ableToPickUpRevolver=false` 只影响「捡起地上的掉落物枪械」（`ItemEntityMixin` / `onPickUpItem`），不影响 `/give` 或直接塞进背包。

### 2.3 一个完整的注册例子（照抄改）

`role/bouns/BounsRoles.java`：

```java
public static final ResourceLocation PROGRAMMER_ID = id("programmer");

/**
 * 程序员角色
 * - 彩蛋职业（受彩蛋刷新概率影响）
 * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
 * - 假心情系统、无限冲刺时间、在计分板上显示
 * - 商店：杀手默认刀具 + 终端（150金币）
 * - 职业相关规则全部集中在 ProgrammerRole 里
 */
public static SRERole PROGRAMMER = TMMRoles.registerRole(new ProgrammerRole(
        PROGRAMMER_ID,
        new Color(0, 170, 120).getRGB(),
        false,                                  // isInnocent = 杀手阵营
        true,                                   // canUseKiller = 有杀手能力
        SRERole.MoodType.FAKE,
        -1,                                     // 无限冲刺
        true
)).setCanBeRandomedByOtherRoles(false)
        .setDefaultMax(1)
        .setDefaultEnableChance(200)            // 彩蛋概率 2%
        .setAddedVersion("4.4");                // versiontag
```

> ⚠️ **字段类型坑**：所有角色字段都声明成 `SRERole`，所以 `BounsRoles.PROGRAMMER.myOwnMethod()` **编译不过**。
> 解法有两条，本项目采用第一条：
> 1. 把职业自己的 API 写成 `public static` 方法（本仓库「程序员」就是这么做的：`ProgrammerRole.executeTerminalCommand(...)`）；
> 2. 或者给字段加类型：`public static ProgrammerRole PROGRAMMER = (ProgrammerRole) TMMRoles.registerRole(new ProgrammerRole(...))...;`（末尾强制转换，链式 setter 返回 `SRERole`）。
> 还有一点：职业类的 `static {}` 里**不要**引用 `BounsRoles.XXX`（会形成类初始化循环），在**方法体**里引用是安全的。

### 2.4 常用链式配置（`SRERole` 上都有，返回自身可继续链）

| 方法 | 作用 |
| --- | --- |
| `setRoleData(Function<RoleDataContext, RoleData>)` | 绑定职业数据（见 §3） |
| `setColor(int)` / `setMoodColor(...)` / `setMoodType(...)` | 颜色 / 心情 |
| `setAddedVersion(String)` | 版本标签（配置界面里的「新增版本」；也可在文件底部 `static {}` 里统一设） |
| `setDefaultMax(int)` / `setDefaultEnableChance(int)` | 单局最大人数 / 刷新权重（100 = 1%，200 = 2%，1000 = 10%） |
| `setDefaultEnableNeededPlayerCount(int)` / `setDefaultEnableMaxPlayerCount(int)` | 人数下限 / 上限 |
| `setCanBeRandomedByOtherRoles(boolean)` / `setCanSetSpawnInfoInConfig(boolean)` | 是否允许被「随机职业」类能力抽到 / 是否允许在配置界面调刷新 |
| `setCanSeeCoin(boolean)` | 是否看得见金币（**默认就是 true**，杀手/买东西的职业一般不用写；非杀手要发钱才写） |
| `setCanUseInstinctAndNightVision(boolean)` / `setInstinctType(...)` / `setBeSeenInstinctType(...)` | 直觉/夜视 |
| `setVigilanteTeam(true)` / `setSpecialVigilante(true)` / `setSpecialPolice(true)` | 警长阵营相关 |
| `setNeutrals(true)` / `setNeutralForKiller(...)` / `setNeutralForInnocent(...)` | 中立阵营 |
| `setCanPickUpRevolver(boolean)` | 能否捡起地上的枪 |
| `setTaskReward(int, int, ItemStack...)` / `setKillExtraCoinAwards(int)` | 任务奖励 / 击杀额外金币 |
| `setPassiveIncome(int)` / `setCanAutoAddMoney(boolean)` / `setInitialCoinCount(int)` | 收入相关 |
| `setSpecialMapRolesCondition(Predicate<Set<MapSpecialFeatures>>)` | 只在特定地图刷新（如 UNDERWATER / LAB） |
| `setEventEnableChance(掷骰回调, 局末回调, 概率)` / `setEventEnableChance(概率)` / `setEventEnableChance(IntSupplier)` / `setRoundEventEndHandler(局末回调)` | **每局开局掷一次**的职业专属事件：概率 + 地图限制 + 禁用状态，还带管理员「强制下一局」。掷骰回调每次开局都调用（未掷中收到 `false`），局末回调只在本局掷中过时调用、也可单独挂。**不要**为它自己注册 `OnGameTrueStarted` / `OnGameEnd`，详见 `docs/api.md` §职业随机事件 |
| `setOtherModeRole(true)` / `setHiddenForRoleRotation(true)` | 特殊模式专用 / **轮抽界面隐藏真名**（显示为「随机」并抑制技能播报）。注意它**不会**把职业排除出轮抽候选池，职业仍可能被抽到 |
| `addRelatedRole(...)` / `addOpposingRole(...)` / `addBothRelatedRole(...)` | 关系（供侦探/任务等系统用） |

> 变体很多，**以 `SRERole.java` 的 setter 列表为准**；名字最稳的找法是 `grep -n "public SRERole set" src/main/java/io/wifi/starrailexpress/api/SRERole.java`。

### 2.5 覆写职业行为（也是集中在职业类里）

`SRERole` 上可直接 `@Override` 的钩子（按需挑）：

```java
@Override public List<ItemStack> getDefaultItems() { ... }                    // 开局自带物品
@Override public List<ShopEntry> getShopEntries() { ... }                    // 专属商店（见 §5）
@Override public InteractionResult onDropItem(Player player, ItemStack item) // 丢东西（可拦截）
@Override public InteractionResultHolder<ItemStack> onItemUse(Player, Level, InteractionHand)
@Override public TrueFalseResult onPickUpItem(Player player, ItemStack item) // 捡东西（FALSE = 禁止）
@Override public void onDeath(Player victim, boolean spawnBody, Player killer, ResourceLocation deathReason, boolean forceDeath)
@Override public void onDeathWithBody(...)                                   // 需要尸体实体时
@Override public ResourceLocation getPsychoSkin(Player player, boolean isSlim) // 疯狂状态皮肤
@Override public void onPsychoOver(Player player, SREPlayerPsychoComponent c) // 疯狂状态结束
```

---

## 3. 职业数据 RoleData（有每人状态/冷却/次数时才建）

**优先用 RoleData，不要用 CCA**（`ai_doc.md`/`AGENT.md` 反复强调）。没有每人状态的职业（例如「程序员」：终端用没用过由物品自己承载）**不需要**建 RoleData，别为了对称硬加一个空类。

```java
package org.agmas.noellesroles.role_data.innocence;

public class XxxRoleData extends SimpleRoleData {
    /** 冷却结束时刻（用「结束时刻」而不是每 tick 自减） */
    public long skillReadyAt;
    public int usesLeft = 3;

    public XxxRoleData(RoleDataContext context) {
        super(context);
    }

    /** 只同步给本人 */
    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    @Override
    public void serverTick() {
        // 需要每 tick 做的事（能不做就不做）
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("readyAt", skillReadyAt);
        tag.putInt("usesLeft", usesLeft);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider provider) {
        skillReadyAt = tag.getLong("readyAt");
        usesLeft = tag.getInt("usesLeft");
    }
}
```

绑定与读写：

```java
// 注册时绑定（每次实例都是新建的，理论上不需要 init/clear）
.setRoleData(XxxRoleData::new)

// 读写
var data = RoleData.getNullable(XxxRoleData.class, player);   // 可能为 null
var opt  = RoleData.getOptional(XxxRoleData.class, player);
if (data != null) { data.usesLeft--; data.sync(); }           // 重大变化时才 sync()
```

冷却怎么写（重要）：
- 用**结束时刻**代替 `cd--`：`GameUtils.getTicksFromGameStart() + 时长`（时停/会议期间会暂停，正是大多数冷却想要的）。
- 只在「触发」和「结束」时同步，别每 tick 同步。
- 秒 → tick：`GameConstants.getInTicks(0, 秒)` 或 `秒 * 20`。
- 可配置数值放 `org/agmas/noellesroles/config/NoellesRolesConfig.java`，用 `NoellesRolesConfig.HANDLER.instance().字段` 读。

---

## 4. 技能（主动技能请统一走 RoleSkill）

**技能逻辑写在职业类里（推荐 `public static` 方法），注册位置二选一**：

| 放哪 | 适合 | 例子 |
| --- | --- | --- |
| ① 中央：`init/ModRolesInitialEventRegister.java` 的 `static {}` | 普通职业（`role/ModRoles.java` 里的） | `ModRoles.NET_COP` / `DOOMED_SINNER` / `INFECTED`…都在这里注册 |
| ② 系列 Handler：`handler/BounsHandlers.java`、`handler/TouhouHandlers.java`、`handler/AnimeHandlers.java`（由 `handler/AAAHandlerFather.register()` 统一调用），或职业自己的 `XxxRoleHandler`（`game/roles/neutral/leader/LeaderEventHandler`、`game/roles/neutral/lender/LenderRoleHandler`、`game/roles/vigilante/guard/GuardPlayerHandler`、`handler/utils/BeeFamilyManager`） | 彩蛋 / 东方 / 番剧等按系列分组的职业，或职业自带一套事件+技能时 | `BounsHandlers` 里的 `BounsRoles.HENG_XING_TI` / `LAO_DA` |

启动链（了解即可）：`register/NREventRegister.register()` → `AAAHandlerFather.register()` → `TouhouHandlers/BounsHandlers/AnimeHandlers.register()` → 角色类里的 static；`NREventRegister` 里还会直接调 `LeaderEventHandler.register()` / `LenderRoleHandler.register()`，`init/events/NRCombatEvents` 调 `GuardPlayerHandler.register()`。

**推荐写法：handler 只负责「注册」，技能本体是角色类的 static 方法**（和「职业逻辑集中在职业类」一致，改数值只动角色类）：

```java
// ── handler（例如 handler/BounsHandlers.java 的 register() 里）──
RoleSkill.register(BounsRoles.HENG_XING_TI,                       // 引用职业字段
        RoleSkill.skill(SRE.id("heng_xing_ti"), "skill.noellesroles.heng_xing_ti", ctx -> {
            return HengXingTiRole.triggerSkill(ctx);              // ← 引用角色类的 static 方法
        }).showOnHud(true).recordReplay().cooldownSeconds(240).announceToSelf().build());

// ── 角色类（role/bouns/roles/HengXingTiRole.java）──
public static boolean triggerSkill(RoleSkillContext ctx) {
    ServerPlayer player = ctx.player();
    if (player.isSpectator()) return false;      // 返回 true 才消耗冷却/充能
    ...
    return true;
}
```

角色类也可以自己暴露一个 `public static void registerEvents()`，由系列 handler 调用（真实例子：`BounsHandlers.register()` 里调 `RabbitWansuiRole.registerEvents()`），把「这个职业的事件监听」也收在角色类里。

如果是走**方式 ①（中央 `ModRolesInitialEventRegister`）**，只是注册的地方不同，写法一样：

```java
RoleSkill.register(ModRoles.MY_ROLE,
    RoleSkill.skill(SRE.id("my_primary"), "skill.noellesroles.my.primary", ctx -> {
        ServerPlayer p = ctx.player();
        if (p.isSpectator()) return false;          // 返回 true 才消耗冷却
        return MyRole.doPrimary(p, ctx.target());
    }).cooldownSeconds(40).showOnHud(true).build());
```

要点：`context.player()/target()/phase()/skillReady()`；`.cooldownSeconds/.cooldownTicks`、`.charges(n)`、`.shifted(true)`（潜行+技能键副技能）、`.toggleable(true)`、`.showOnHud(true)`（自动进 `UnifiedSkillHud`，**不需要**自己画冷却 HUD）、`.recordReplay()`（进回放）、`.announceToSelf()`（释放时给自己一条提示）；技能名翻译键 `skill.noellesroles.<id>.<name>`。旁观者豁免与 `SKILL_BANED` 由统一管线处理。

⚠️ **一个职业的技能必须一次注册完**：`RoleSkill.register(role, def1, def2, ...)` 内部是 `UNIFIED_SKILLS.put(role, ...)`，同一角色调用第二次会打一条 `The skills of the role ... has already been registered!` 的 error 并把前一次的技能**丢掉**（`CustomRoleLoader` 里就是为这个才「收集齐所有技能模块后一次性注册」）。多技能职业别写成两次 `register`。


---

## 5. 商店（两种写法，选一种并保持集中）

`io/wifi/starrailexpress/util/ShopEntry.java`：`new ShopEntry(ItemStack, int price, ShopEntry.Type type[, Currency currency, int weight])`；
可覆写 `canBuy(Player)` / `canDisplay(Player)` / `isSafeTime(Player)` / `onBuy(Player)`（返回 `false` = 购买失败不扣钱），`stack()` 取条目物品，`setFailedMessage(Component)` 设失败提示。
`Type` 常用 `TOOL` / `WEAPON` / `POISON`。货币默认 `Currency.MONEY`（金币 = `SREPlayerShopComponent.balance`，发钱/查钱用 `MoneyUtils`，见 §10）；另一档 `Currency.MINIGAME_TOKEN`（小游戏代币 = `SREPlayerMinigameTaskComponent`）。

### 推荐：职业类里覆写 `getShopEntries()`（逻辑跟着职业走）

```java
@Override
public List<ShopEntry> getShopEntries() {
    List<ShopEntry> shop = ShopContent.getDefaultKnifeEntries();  // 杀手默认刀具（动态刀条目），返回可变副本
    shop.add(new ShopEntry(FunnyItems.TERMINAL.getDefaultInstance(), 150, ShopEntry.Type.TOOL));
    return shop;    // 双端都会被调用（客户端画商店/图标），所以别在这里碰服务端专用东西
}
```

带条件/带效果的条目：

```java
shop.add(new ShopEntry(ModItems.BOMB.getDefaultInstance(), 120, ShopEntry.Type.TOOL) {
    @Override public boolean canBuy(@NotNull Player player) {          // 已有就不给买
        return super.canBuy(player) && MCItemsUtils.countItem(player, ModItems.BOMB) <= 0;
    }
    @Override public boolean onBuy(@NotNull Player player) {           // 自定义发货逻辑
        return RoleUtils.insertOrDropItem(player, ModItems.BOMB.getDefaultInstance());
    }
});
```

### 备选：集中在 `init/RoleShopHandler.java` 里批量注册

```java
// shopRegister() 内；注意该方法是可重入的（配置重载/命令会再调一次），里面先 clear 了 customEntries
ShopContent.customEntries.put(ModRoles.MY_ROLE_ID, myShopEntries);
```

`ShopContent.customEntries` 的生效条件：职业**没有**覆写 `getShopEntries()`（`ShopContent.getShopEntries(role, player)` 的判定顺序是 覆写 → customEntries → 杀手默认刀具）。
配套：`ShopContent.getDefaultKnifeEntries()`（杀手默认）、`ShopContent.register()`（默认条目表）。

> 价格若要可配置：优先在 `NoellesRolesConfig` 加字段；`SREConfig`（`io.wifi`）里的价格字段只读不改。

### 购买流程与校验（了解即可）

购买 UI 在 `client/gui/screen/ingame/LimitedInventoryScreen.java` → 发 `StoreBuyPayload` → 服务端 `SREPlayerShopComponent.tryBuy(index)`：检查安全时间、`SHOP_BANNED`、条目冷却、动态价格、余额、`canDisplay/canBuy/isSafeTime`，最后 `onBuy` 并扣钱 + 记录回放。

---

## 6. 物品

### 6.1 注册

`org/agmas/noellesroles/init/FunnyItems.java`（彩蛋/趣味物品）或 `init/ModItems.java`（常规物品）：

```java
public static final Item TERMINAL = register(
        new TerminalItem(new Item.Properties().stacksTo(1)),
        "terminal");                 // → noellesroles:terminal
```

`register(...)` 会顺带把物品加进创造模式标签页与 `TMMDescItems.introItems`（物品介绍面板），并在 `init()`（`org/agmas/noellesroles/RicesRoleRhapsody.java` 的 `onInitialize1()` 里调用 `FunnyItems.init()`）里统一 `registrar.registerEntries()`。

### 6.2 模型 / 贴图（不必新增二进制资源）

- 模型：`src/main/resources/assets/noellesroles/models/item/<id>.json`
  ```json
  { "parent": "item/generated", "textures": { "layer0": "noellesroles:item/<贴图名>" } }
  ```
- 想省事可以**复用已有贴图**（本项目「终端」就复用了监控终端贴图）：
  ```json
  { "parent": "item/generated", "textures": { "layer0": "noellesroles:item/monitoring_terminal" } }
  ```
- 也可以直接复用原版模型（连贴图都不用）：`{ "parent": "minecraft:item/poisonous_potato" }`

### 6.3 物品类：右键行为 + 打开界面

```java
public class TerminalItem extends Item {
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);   // 客户端只摆手臂
        if (!ProgrammerRole.canUseTerminal(player)) {          // ← 规则问职业类要，物品不自己判断
            player.displayClientMessage(Component.translatable("message....not_programmer")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            OpenScreenManager.openScreen(serverPlayer, OpenScreenManager.MY_SCREEN);          // 见 §7
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
```

坑：
- **只要界面不依赖服务端校验/数据，可以纯客户端打开**（零网络包）：物品类留 `public static Runnable openScreenCallback`，客户端在 `RicesRoleRhapsodyClient.setupItemCallbacks()` 里赋值即可 —— 见 §7.1（阴谋之书页的写法）。本仓库「终端」因为要校验「只有程序员能开」，所以走的是服务端下发 §7.2。
- **只覆写 `use()`（右键空气/方块通用）时不需要 `AdventureUsable`**：原版在 `useItemOn` 失败后会回退到 `useItem`，冒险模式也能用。
- **覆写 `useOn()`（右键方块）时必须 `implements AdventureUsable`**，否则 `ItemStack#useOn` 里的 `mayBuild` 检查会让它直接 PASS 掉（见 `ItemStackMixin` 与 `net/exmo/sre/repair/content/item/RepairBoostItem` 的注释）。
- 玩家在局内的游戏模式是 **ADVENTURE**，判断「活着且能交互」用 `GameUtils.isPlayerAliveAndSurvival(player)`。
- 稀有物品记得 `stacksTo(1)`。

---

## 7. GUI：打开方式二选一 + 客户端自绘

打开界面的两种做法，**按「要不要服务端参与」来选**：

| 方式 | 什么时候用 | 代价 |
| --- | --- | --- |
| **A. 纯客户端回调**（阴谋之书页那种） | 纯展示/纯客户端交互：图鉴、地图、笔记、需要客户端 `hitResult` 的建造工具 | 服务端**不知道**你开了界面，不能拿它当权限校验；界面里要改状态仍必须走 C2S 包 |
| **B. 服务端下发**（`OpenScreenManager`） | 需要服务端校验身份（例如「只有某职业能开」），或界面内容要由服务端下发 | 多一次 S2C（本仓库已有通用 payload，不用新增包） |

两者最终都是 `Minecraft.getInstance().setScreen(new XxxScreen(...))`，Screen 自绘写法完全一样（§7.3）。

### 7.1 方式 A：纯客户端打开（零网络包，如阴谋之书页）

物品类在 `content/item/` 下（**双端共用的类，不能 import 任何客户端类**），只留一个静态回调字段：

```java
public class ConspiracyPageItem extends Item {
    /** 静态回调：由客户端在 onInitializeClient 里赋值；服务端永远为 null */
    public static Runnable openScreenCallback = null;   // 需要玩家信息时用 Predicate<Player>

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() && openScreenCallback != null) {   // ← 双端都会跑 use()，必须判 isClientSide
            openScreenCallback.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
```

客户端在 `client/NoellesrolesClient.onInitializeClient`（约 1544 行）里调用的 `client/RicesRoleRhapsodyClient.setupItemCallbacks()` 中赋值：

```java
ConspiracyPageItem.openScreenCallback = () -> {
    Minecraft client = Minecraft.getInstance();
    if (client.player == null) return;
    client.setScreen(new ConspiratorScreen());
};
```

仓库里的现成例子：`ConspiracyPageItem`→`ConspiratorScreen`、`AreaMapItem`→`AreaMapScreen`、`DeductionBookItem`→`DeductionBookScreen`、`WrittenNoteItem`→`RecorderScreen`、`ProblemSetItem`→`MathSolverScreen`、`PanItem`→`ChefStartGameScreen`；`MapBuildHelperItem` / `CustomRoleToolItem` 用的是 `Predicate<Player>` 版本（还借 `client.hitResult` 取右键的方块坐标）。

注意：
- 回调字段是 `static`，**服务端为 null**，所以服务端不会因此报错（`use()` 里已有 `isClientSide()` 判断）。
- 纯客户端 = 打开界面这件事**没有任何服务端校验**，客户端可以自己伪造打开（只影响他自己看到的界面，无害）；但**别把「谁能拿到效果」的逻辑放在这里** —— 那必须在服务端（见 §7.2）。
- 界面里要发起的任何操作（给物品、改状态）依然走 §8 的 C2S 包 + 服务端校验。

### 7.2 方式 B：服务端下发（按 ID 开界面，零新增 S2C 包；终端界面用的就是这条）

1. 服务端注册一个屏幕 ID：`org/agmas/noellesroles/utils/OpenScreenManager.java`
   ```java
   ResourceLocation MY_SCREEN = register(Noellesroles.id("my_screen"), Component.translatable("screen.noellesroles.my.title"));
   ```
2. 服务端任意位置：`OpenScreenManager.openScreen(serverPlayer, OpenScreenManager.MY_SCREEN);`（内部发 `OpenScreenPayload`）
3. 客户端映射：`org/agmas/noellesroles/client/ClientOpenScreenManager.java`
   ```java
   if (id.equals(OpenScreenManager.MY_SCREEN)) screen = new MyScreen();
   ```
   （接收器已在 `client/NoellesrolesClient.onInitializeClient` 里注册好了，不用你写。）

### 7.3 Screen 自绘要点（参考 `client/screen/BroadcasterScreen.java`、`TerminalScreen.java`）

```java
public class MyScreen extends Screen {
    private EditBox input;                       // 需要输入就用 EditBox
    public MyScreen() { super(Component.translatable("screen.noellesroles.my.title")); }

    @Override protected void init() {
        super.init();                            // resize 时会重新调用：日志等状态要放字段，不要放局部变量
        this.input = new EditBox(this.font, x, y, w, h, Component.translatable("...input_hint"));
        this.input.setMaxLength(120);
        this.input.setHint(Component.translatable("...input_hint").withStyle(ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.input);     // 只有真正要交互的控件才 addRenderableWidget
        this.setInitialFocus(this.input);         // 输入框要抢焦点
        // 其余「按钮」可以自己在 mouseClicked 里做命中判定（本项目大量屏幕都这么做）
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);  // 先背景
        g.fill(...);                                            // 再自绘面板
        super.render(g, mouseX, mouseY, partialTick);            // 再让 super 画控件（EditBox）
        g.drawString(this.font, Component.literal("..."), x, y, 0xFF3AF07A, false);  // 最后画文字
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {   // 257 / 335
            submit();                    // 自己先拦回车，否则会被 EditBox 抢走
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);   // ESC 交给父类 → onClose()
    }

    @Override public void resize(Minecraft mc, int width, int height) {   // 重排后保留输入内容
        String text = this.input == null ? "" : this.input.getValue();
        super.resize(mc, width, height);
        if (this.input != null) this.input.setValue(text);
    }

    @Override public boolean isPauseScreen() { return false; }   // 别让单人游戏暂停
    @Override public void onClose() { if (this.minecraft != null) this.minecraft.setScreen(null); }
}
```

坑：
- **界面开着的时候看不到聊天栏/HUD**！所以「失败提示」要么在界面内用本地校验直接画出来，要么让服务端用 `displayClientMessage(component, true)`（actionbar，界面开着也看得见）。
- `super.render(...)` 一定要放在「自绘底板之后、需要显示的控件之前」，否则面板会盖住输入框。
- 屏幕若还画了别的自绘内容，注意顺序：`renderBackground` → 自绘 → `super.render` → 文字。

### 7.4 三条网络铁律（`docs/角色开发指南.md` §9）

- 服务端类**不要 import 客户端类**（`Screen` / `Minecraft` / `ClientPlayNetworking` 都算）；要用就在客户端入口 `client/NoellesrolesClient` / `client/ClientOpenScreenManager` 里做。
- 客户端 receiver 不要写在 payload 类里（payload 只放 `record` + `ID` + `CODEC`）。
- 非客户端类不要引用客户端类。

---

## 8. 网络包（C2S 模板；接收器只做薄转发）

1) `org/agmas/noellesroles/packet/XxxC2SPacket.java`

```java
public record XxxC2SPacket(String text) implements CustomPacketPayload {
    public static final ResourceLocation XXX_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "xxx_action");
    public static final Type<XxxC2SPacket> ID = new Type<>(XXX_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, XxxC2SPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeUtf(packet.text()),
            buf -> new XxxC2SPacket(buf.readUtf()));

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
```

2) 注册：`init/ModPackets.java#registerPackets()` →
`PayloadTypeRegistry.playC2S().register(XxxC2SPacket.ID, XxxC2SPacket.CODEC);`（S2C 用 `playS2C()`）

3) 服务端接收：`init/ModPacketsReciever.java#registerPackets()` —— **薄转发**

```java
ServerPlayNetworking.registerGlobalReceiver(XxxC2SPacket.ID,
    (payload, context) -> context.server().execute(() ->
        ProgrammerRole.executeTerminalCommand(context.player(), payload.command())));
```

4) 客户端发送：`ClientPlayNetworking.send(new XxxC2SPacket(text));`（客户端接收用 `ClientPlayNetworking.registerGlobalReceiver(...)` + `context.client().execute(() -> ...setScreen(...))`，位置在 `client/NoellesrolesClient#onInitializeClient`）。

5) **提示文案怎么写**：服务端发 `Component.translatable("...")` 即可 —— 可翻译组件由**客户端**按自己的语言解析，服务端不需要知道语言，也不要把翻译好的字符串发过去。

---

## 9. 翻译键位置对照表（每个职业必补）

| 键 | 放哪个文件 | 说明 |
| --- | --- | --- |
| `announcement.star.role.<路径>` | `assets/noellesroles/lang/zh_cn.json`（+ `zh_tw` / `en_us`） | 职业名。`<路径>` = `identifier().getPath()`，**不是**完整 id（`bouns:programmer` → `programmer`） |
| `announcement.star.goals.<路径>` | 同上 | 职业目标一句话 |
| `announcement.star.win.<路径>` | 同上 | 胜利提示（仅自定义获胜需要）（按需） |
| `info.screen.roleid.<路径>` | `assets/role_modifier_intro/lang/zh_cn.json` | 职业详细介绍（`\n` 多行；可选 `.simple` 简短版） |
| `item.<命名空间>.<id>` | `assets/noellesroles/lang/zh_cn.json` | 物品名 |
| `item.<命名空间>.<id>.tooltip` | 同上 | 物品悬浮说明（`getDescriptionId() + ".tooltip"`）需要在 TMMItemTooltips 注册或者在Item类里手动append提示。 |
| `item.<命名空间>.<id>.desc` | `assets/item_intro/lang/zh_cn.json` | **强制**：物品介绍面板文本 |
| `screen.<命名空间>.<屏幕>.*` | `assets/noellesroles/lang/zh_cn.json` | 界面标题/提示（本项目界面统一放 noellesroles 语言文件） |
| `message.noellesroles.<职业>.*` | 同上 | 运行时提示 |
| `skill.noellesroles.<职业>.<技能>` | 同上 | 技能名 |

细节坑：
- 文案里出现**字面百分号**要写 `%%`（例：`"只有2%%的概率刷新"`）；`%s` 占位符照常写。请不要使用%d，而是使用%s + 代码端`String.format("%d", 数值)`。
- 全部面向玩家的文本都用 `Component.translatable(key, args...)`，**禁止硬编码字符串**。
- 三语齐全（`zh_cn` / `zh_tw` / `en_us`）是本项目规范；若某次任务只要求中文，请显式确认，别默认只写一半。请一定写中文翻译！

---

## 10. 出生自带物品、任务、经济、胜利（快速索引）

| 需求 | 位置 |
| --- | --- |
| 开局自带物品 | 覆写 `getDefaultItems()`（`init/RoleInitialItems.java` 走的就是它） |
| 发钱（局内金币） | `org.agmas.noellesroles.utils.MoneyUtils`：`addToBalance(player, n)` / `setBalance(player, n)` / `getBalance(player)` / `hasBalance(player, n)` / `cost(player, n)`（够就扣并返回 true）/ `sendNotEnoughtMoneyMessage(player, n)`。底层就是 `SREPlayerShopComponent.KEY.get(player).balance`（`addToBalance/setBalance/sync`）—— **局内金币由 `SREPlayerShopComponent` 管理** |
| 小游戏代币 | `MoneyUtils.addToMinigamesTokens / getMinigamesTokens`（= `SREPlayerMinigameTaskComponent`，对应 `ShopEntry.Currency.MINIGAME_TOKEN`） |
| 调试发钱 | 游戏内 `/tmm:money add <玩家> <数量>`（`content/command/MoneyCommand.java`） |
| ⚠️ 别用错 | `io.wifi.starrailexpress.data.PlayerSkinEconomyManager`（旧名 `PlayerEconomyManager`）**不是游戏内金币**——源码注释原话：「请注意这个不是游戏内的金币，游戏内的金币是SREPlayerShopComponent在管理！」。这个类管的是跨局持久化的经济档案（掉落概率 `lootChance`、累计 `coinNum`、物品皮肤解锁/装备），改它不会让玩家在商店里多出可花的钱。用户提到的金币均不是它，而是 `MoneyUtils` 的金币。 |
| 杀人 / 强制杀 | `GameUtils.killPlayer(victim, spawnBody, killer, deathReason)` / `GameUtils.forceKillPlayer(...)`（绕否决） |
| 复活 | `GameUtils.revivePlayer(serverPlayer, x, y, z)` |
| 回自己房间 | `GameUtils.teleportBackToRoom(player)`（映射表 `GameUtils.roomToPlayer`，**先判断 `containsKey`**，没分配房间时该方法会静默失败/把人变旁观） |
| 独立胜利 | `RoleUtils.customWinnerWin(serverLevel, GameUtils.WinStatus.CUSTOM, roleId.getPath(), OptionalInt.of(role.color()))` + `CustomWinnerClass.registerCustomWinners()` 加分支 |
| 死亡事件 | `io.wifi.starrailexpress.event.*`：`AllowPlayerDeath(WithKiller)`（可否决）、`OnPlayerDeath(WithKiller)`（已定局） |
| 每局开局随机的职业事件 | `.setEventEnableChance(掷骰回调, 局末回调, 概率)` 声明一次即可（例：假史蒂夫、紫怪）；开局掷骰 / 局末收尾由核心统一派发，掷骰回调未掷中时也会收到 `false`，局末回调只在本局掷中过时调用；状态**维度通用**，想按需查询用 `.isEventEnabled()`。详见 `docs/api.md` §职业随机事件 —— **不要**自己注册 `OnGameTrueStarted` 来掷骰 |
| 背包界面选人列表 | `client/rolescreen/*` + `SRERole.setInventoryScreenExtensionFactory(...)`（**不要 mixin** `LimitedInventoryScreen`） |
| HUD 自绘（进度条类） | `RoleHudRenderCallback.EVENT.register(roleId, ...)`（冷却显示用 `showOnHud(true)` 就够） |

---

## 11. 调试 / 自查清单

编译（本机实测可离线跑通；注意 `GRADLE_USER_HOME=E:\gradle_home` 里的 loom 插件缓存可能不全，用默认 gradle home 反而更稳）：

```bash
./gradlew compileJava --offline     # 快速语法/API 校验
./gradlew build --offline           # 完整打包校验（会 remapJar）
# 语言文件是手改的 JSON，务必校验：
python -c "import json;json.load(open('src/main/resources/assets/noellesroles/lang/zh_cn.json',encoding='utf-8'))"
```

功能自查：
- [ ] 职业能刷新出来（彩蛋职业确认 `InitModRolesMax.isEggEnabled` 打开），配置界面能看到并调整刷新权重
- [ ] 职业名/目标/详细介绍三处翻译键都不缺（游戏里不会显示 `info.screen.role.desc.error`）
- [ ] 商店条目买得起、买完扣钱正确、余额不足有提示；道具能进背包（满了用 `RoleUtils.insertOrDropItem` 掉地上，必要时再加 `canBuy` 限制重复购买）
- [ ] 物品右键在**冒险模式**可用（只覆写 `use()` 就够；`useOn()` 记得 `AdventureUsable`）
- [ ] 界面：`super.render` 顺序对、回车能提交、`resize` 不丢输入、`isPauseScreen()=false`
- [ ] 服务端二次校验：客户端本地校验只是体验优化，**服务端必须再校验角色/存活/持有物**（防作弊 + 状态不同步）
- [ ] 消耗型物品：只在真正执行成功时消耗（失败别扣），并且要兼容「背包满」「地图没房间」这类边界
- [ ] 没有 mixin、没有改动 `io/wifi/starrailexpress/`、没有新增按键绑定、没有手写 GPL 头
- [ ] 每个功能块做完再统一测，不要边写边跑

排查经验：
- 类加载循环：职业 `static {}` 里出现 `BounsRoles.XXX` 会初始化死锁，挪进方法体。
- 字段类型：`BounsRoles.XXX` 是 `SRERole`，子类方法要通过 `静态方法` 或 `(XxxRole)` 强转访问。
- `displayClientMessage(..., true)` = 动作栏（界面开着也看得到），`false` = 聊天栏（界面开着看不到）。
- 商店改动不生效：`RoleShopHandler.shopRegister()` 会在配置重载时重新注册，`customEntries` 每次都被 `clear()`；如果你覆写的是 `getShopEntries()` 则实时生效、无需重载。

---

## 12. 角色接口：`EggRoleInterface` / `TouhouRoleInterface` / `CustomWinnerRoleInterface`

三个接口都在 `io/wifi/starrailexpress/api/` 下。前两个是**空接口（纯标记）**，用来把职业划进「特殊刷新池」；第三个带两个 `default` 方法，用来接管**胜利判定**。

### 12.1 `EggRoleInterface` / `TouhouRoleInterface`：刷新池标记接口

```java
// io/wifi/starrailexpress/api/EggRoleInterface.java
public interface EggRoleInterface { }        // 没有任何方法，只是标记

// io/wifi/starrailexpress/api/TouhouRoleInterface.java
public interface TouhouRoleInterface { }
```

**它们到底做了什么**（消费点：`org/agmas/noellesroles/init/InitModRolesMax.java` 的 `autoRoleMaxCount(...)` 与开池逻辑，约 200~330 行 / 395~405 行）：

1. **跳过常规数量计算**：`autoRoleMaxCount(...)` 遍历 `TMMRoles.ROLES` 时，遇到实现了这两个接口之一的角色就 `continue` —— 它们**不参与**「按人数/地图/`setDefaultEnableChance` 算本轮上限」的常规流程。
2. **改由整池开关决定**：每局开局先掷一次总开关：
   - 彩蛋池：`玩家数 >= NoellesRolesConfig.minPlayerForEggRoles`（默认 12）**且** `random(0..100) <= EGGS_CHANCE`（= `config.chanceOfEggRoles`，默认 5）→ `InitModRolesMax.isEggEnabled = true`；否则 `false`。
   - 东方池：`玩家数 >= config.minPlayerForTouhouRoles`（默认 12）**且** `random(0..100) < TOUHOU_CHANCE`（= `config.chanceOfTouhouRoles`，默认 5）→ `InitModRolesMax.isTouhouEnabled = true`；否则 `false`。
3. **开池**：对该池每个角色调用 `role.getRoundMaxCount(...)` 得到本轮上限，`>= 0` 时写入 `Harpymodloader.setRoleMaximum(role, max)`（返回 `-1` 表示「不覆盖」，保持原有配置）。
4. **关池**：整池 `setRoleMaximum(role, 0)` —— **本轮绝对不会刷新**。

也就是：**实现接口 ≈ 声明「我只在该池被抽中时才可能出现」**。彩蛋职业的「2% 刷新率」不是直接生效的，而是「先 5% 抽中彩蛋池，池内再按 `setDefaultEnableChance` / `getRoundMaxCount` 竞争」。

怎么用：

```java
// 方式 A（推荐）：直接继承现成基类
public class MyEggRole extends EggRole { ... }      // 自动 addFlag("bouns")；
                                                   // 并覆写 canBeRandomed() → 只有 isEggEnabled 时才可能被随机到
public class MyTouhouRole extends TouhouRole { ... } // 自动 addFlag("touhou")；同上但看 isTouhouEnabled

// 方式 B：继承位被别的基类占了，就直接 implements 标记接口
public class LeaderRole extends CustomWinnerRole implements EggRoleInterface { ... }   // 仓库真实例子
public class HengXingTiRole extends ExtraEffectRole implements EggRoleInterface { ... }
```

坑：
- **只 `implements` 接口不会得到 `canBeRandomed()` 的池开关覆写**（那段逻辑写在 `EggRole` / `TouhouRole` 这两个**类**里，不在接口里）。自己 `implements` 时，要么手动覆写：
  ```java
  @Override public boolean canBeRandomed() {
      return InitModRolesMax.isEggEnabled && super.canBeRandomed();
  }
  ```
  要么接受「池关着时仍可能被其他能力（赌徒等）随机到」的行为。
- 同一职业**可以同时**是彩蛋池 + 自定义胜利（`LeaderRole`、`RabbitWansuiRole` 就是 `CustomWinnerRole implements EggRoleInterface`）。
- 修饰符有对应物：`org/agmas/harpymodloader/modifiers/EggModifier.java`、`TouhouModifier.java`（都 `extends SREModifier`），由上面**同一对开关**统一开/关（`HMLModifiers.MODIFIER_MAX`）。写彩蛋修饰符就继承它们。
- 相关配置项（`org/agmas/noellesroles/config/NoellesRolesConfig.java`）：`chanceOfEggRoles`、`minPlayerForEggRoles`、`chanceOfTouhouRoles`、`minPlayerForTouhouRoles`；`InitModRolesMax` 启动时把 chance 抄进静态字段 `EGGS_CHANCE` / `TOUHOU_CHANCE`。

**顺带：flag 就是角色介绍界面的分类标签。** `EggRole` 会 `addFlag("bouns")`、`TouhouRole` 会 `addFlag("touhou")`、`AnimeRole` 会 `addFlag("anime")`、`OriginalRole` 会 `addFlag("inner.original")`；`FlagUtils` → `RoleIntroduceScreen` 把它们做成筛选分类，显示名取翻译键 `screen.roleintroduce.flag.<flag>`（缺失时回退成大写 flag 名），例如已有：

```json
"screen.roleintroduce.flag.bouns": "彩蛋角色",
"screen.roleintroduce.flag.touhou": "东方 Project N创同人",
"screen.roleintroduce.flag.anime": "动漫相关",
"screen.roleintroduce.flag.creator_team": "制作组成员相关"
```

所以要给一批职业打自定义分类，加 flag + 补 `screen.roleintroduce.flag.<flag>` 文案即可；`TMMRoles.registerRole(role, "creator_team")` 这个两参重载就是「注册时顺便加 flag」。

### 12.2 `CustomWinnerRoleInterface`：自定义 / 独立胜利

```java
// io/wifi/starrailexpress/api/CustomWinnerRoleInterface.java
public interface CustomWinnerRoleInterface {
    /** 胜负结算时调用；返回非 NOT_MODIFY 即为改判 */
    default WinStatus checkWin(ServerPlayer player, WinStatus winStatus) { return WinStatus.NOT_MODIFY; }

    /** 统计「这个玩家算不算赢」时调用，可覆盖默认阵营判定 */
    default boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) { return original; }
}
```

两个方法的调用时机完全不同，别混：

| 方法 | 调用点 | 语义 | 返回值 |
| --- | --- | --- | --- |
| `checkWin(player, 当前状态)` | `org/agmas/noellesroles/CustomWinnerClass`（约 70 行），对每个**存活**玩家调用 | **决定本局是什么胜利**（改判）。遍历顺序 = 优先级。返回非 `NOT_MODIFY` 就立即 `return` 这个新状态；若返回 `CUSTOM` 且该角色是 `CustomWinnerRole`，会自动调用 `win(player)` | `WinStatus`（`NOT_MODIFY` = 不干预） |
| `didPlayerWin(player, 原判定, 最终状态)` | `io/wifi/starrailexpress/game/modes/SREMurderGameMode`（约 1008 行），对每个玩家调用 | **决定某个玩家算不算赢**（覆盖阵营默认判定，例如中立满足条件也算赢） | `boolean` |

推荐写法（继承 `CustomWinnerRole`，它已经 `extends NormalRole implements CustomWinnerRoleInterface` 并给好了 `win(player)` 默认实现）：

```java
public class MyNeutralRole extends CustomWinnerRole {
    public MyNeutralRole(ResourceLocation id, int color, RoleType type, MoodType mood, int sprint, boolean seeTime) {
        super(id, color, type, mood, sprint, seeTime);
    }

    @Override
    public WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        if (MyComponent.checkMyVictory(player.serverLevel())) {
            return WinStatus.CUSTOM;      // 触发胜利；CustomWinnerRole.win() 会自动调 RoleUtils.customWinnerWin(...)
        }
        return WinStatus.NOT_MODIFY;      // 不干预
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        // 需要让「我的队友/同阵营」也一起算赢时在这里返回 true
        return original;
    }
}
```

配套规则（`docs/角色开发指南.md` §5 / §14，务必遵守）：
- `winnerId` 用**该职业自己的** `identifier().getPath()`；一律通过 `RoleUtils.customWinnerWin(serverLevel, WinStatus.CUSTOM, path, OptionalInt.of(color))` 结算。
- **不要**给 `WinStatus` 加枚举项，也**不要**直接调 `GameUtils.stopGame(...)`（会绕过统计）。
- 若条件可能在常规结算前达成，还要在 `CustomWinnerClass.registerCustomWinners()` 里加/复核分支（那里的 if 顺序 = 优先级）。
- 参考实现：秉烛人、渡鸦、宿命的罪人；`LeaderRole` 同时演示了「自定义胜利 + 彩蛋池」的组合。

> 一句话记忆：想让职业**只在特定池里出现** → 实现 `EggRoleInterface` / `TouhouRoleInterface`（优先继承 `EggRole` / `TouhouRole`）；想让它**用自己的条件获胜** → 实现 `CustomWinnerRoleInterface`（优先继承 `CustomWinnerRole`）。

