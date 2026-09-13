# AI指导
尽量使用API和各种Event而不是直接写进代码甚至是mixin。

如果实在没办法请告知用户让其自行鉴定后修改。

# 关于AI
本项目使用AI进行vibe coding建议你至少使用 DeepSeek 模型（比DeepSeek更先进的当然更好）
否则可能出现一大堆神秘报错。

# 有关Component（CCA）
！！！请尽量不要使用CCA！！！
如果你打算写冷却等需要ticking的cca：
- 请服务端尽量在重大更改时同步，而不是每秒同步！
- 如果是类似于 cooldown-- 的需要同步的逻辑，每10s再同步。
- 或者使用 `GameUtils.getTicksFromGameStart() + time` 设定触发时间来代替（只需要在触发和结束的时候同步更改）（推荐）（注意，如果是同步间隔，还是建议使用level.getGameTime()，此处API在时停和会议期间会暂停，一般情况的cd都建议使用此API避免与会议冲突。）
  - 实际签名是 `GameUtils.getTicksFromGameStart(Level world)`（需要一个 `Level` 参数）。


# 有关玩家职业数据

你可以使用SRERole中的 
```java
.setRoleData(RoleData实例类::new)
```
RoleData实例类：可以extends SimpleRoleData，或是 implements RoleData
因为每次实例都是创建新的，理论上你不需要init和clear。

获取此实例类方法是 `RoleData.getNullable(类.class, 玩家)`
或者 `RoleData.getOptional(类.class, 玩家);`

如果你打算写冷却等需要ticking的事件：
- 请服务端尽量在重大更改时同步，而不是每秒同步！
- 如果是类似于 cooldown-- 的需要同步的逻辑，每10s再同步。
- 或者使用 `GameUtils.getTicksFromGameStart() + time` 设定触发时间来代替（只需要在触发和结束的时候同步更改）（推荐）（注意，如果是同步间隔，还是建议使用level.getGameTime()，此处API在时停和会议期间会暂停，一般情况的cd都建议使用此API避免与会议冲突。）
  - 实际签名是 `GameUtils.getTicksFromGameStart(Level world)`（需要一个 `Level` 参数）。
- 
！！！尽量使用此API，不要使用CCA！！！

# 语言文件
遵循使用翻译键，优先补全 `zh_cn.json`
# 有关背包界面（LimitedInventoryScreen）API

- 不要用 mixin 直接改 `LimitedInventoryScreen`！请使用事件或 SRERole 钩子。
- 事件（纯客户端）：`io.wifi.starrailexpress.event.client.LimitedInventoryScreenEvents`
  （INIT / INIT_TAIL / RENDER / RENDER_TAIL），非职业扩展（如 modifier）用。
- 职业扩展：SRERole 上的 `.setInventoryScreenExtensionFactory(扩展工厂 Supplier)`
  在客户端注册（如 NoellesrolesClient.onInitializeClient 调用的 RoleScreenRegister）。
  每次打开背包会创建**新的扩展实例**（实例字段随打开重置，避免状态固定；需要跨次保留的状态用 static）。
  扩展类实现 `io.wifi.starrailexpress.client.gui.screen.ingame.RoleInventoryScreenExtension`
  接口并覆写钩子（onInventoryScreenInit / onInventoryScreenInitTail / onInventoryScreenRender）。
  创建扩展时内部会先判断运行环境
  （`FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)`），非客户端直接返回 null。
- 服务端类严禁直接 import 客户端类！需要客户端执行客户端方法时：判别环境后经
  SREClient（客户端入口，允许客户端 only 方法）执行。
- 轮椅方法（都是 `LimitedInventoryScreen` 的**实例方法**，不是静态）：`addRoleWidget(控件)` / `removeRoleWidget(GuiEventListener)` / `clearRoleWidgets()` / `reinit()`（添加组件/移除组件/清空组件/重建界面）；
  选人列表的分页、玩家名搜索（输入框）、按名排序见
  `io.wifi.starrailexpress.client.gui.screen.ingame.PlayerPaginationHelper` 与 `RoleScreenHelper`
  （翻页 nextPage/prevPage/jumpToPage、搜索 attachSearchBox、排序 setNameExtractor/setSort）。
- 搜索框提示文字使用翻译键（`gui.starrailexpress.role_screen.search`），优先补全 zh_cn.json。

# 有关粒子与特效（网络）
！！！服务端不要在循环里 `sendParticles` 凑数量！！！

`ServerLevel.sendParticles(...)` 一次调用 = 一个 `ClientboundLevelParticlesPacket`（发给 32 格内每个玩家）；`count` 参数才是粒子数，不额外占包大小。需要 N 颗就传 `count = N`，不要写 `for` 循环发 N 次。

- 服务端统一用 `io.wifi.starrailexpress.util.ParticleFx`：`burst` / `sphere` / `segment` / `region` / `regionCapped`，每个方法保证只发一个包。
- 场景方块用 `org.agmas.noellesroles.scene.SceneParticles`（`burst` / `blockBurst` / `ring` / `column` / `columnDown` / `regionScatter`），同样是单包实现。
- 环形/螺旋/柱状这类"按形状摆点"的效果合并后会退化成中心高斯团，这是网络与性能优先的取舍；**不要为了形状再写回循环**。

需要保留自定义形状（环形、螺旋、沿轨迹、跟随实体、多边形范围提示等）时，用通用自定义粒子包：**服务端只发一个包（id + 原点 + 时长 + 参数），形状由客户端按 id 生成**（客户端逐点 `addParticle` 不进网络）。

```java
// 服务端：一个包（sendCustomBatch 可把多条合并成同一个包）
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mymod", "shock_wave");
ParticleFx.sendCustom(level, id, pos, 20, 6.0F, 0.5F);   // 参数含义由客户端处理器定义

// 客户端：注册一次（放在客户端初始化处），按 id 自己摆形状
CustomParticleHandlers.register(id, (level, origin, durationTicks, params) -> {
    double radius = params.length > 0 ? params[0] : 1.0D;
    for (int i = 0; i < 64; i++) {
        double a = Math.PI * 2.0 * i / 64;
        level.addParticle(ParticleTypes.END_ROD,
                origin.x + Math.cos(a) * radius, origin.y, origin.z + Math.sin(a) * radius, 0, 0, 0);
    }
});
```

约束：单包最多 64 条、每条最多 8 个 float 参数（超限服务端截断并告警、客户端断开连接）；广播半径 64 格；未知 id 客户端静默忽略（服务端可先行、客户端后补）；同 id 重复注册会抛错。完整签名见 `docs/api.md` 的「粒子与特效」章节。

