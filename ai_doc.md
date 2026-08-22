# AI指导
请不要修改现有内容！

请不要修改别人的代码！

尽量使用API和各种Event而不是直接写进代码甚至是mixin。

如果实在没办法请告知用户让其自行鉴定后修改。

# 有关Component（CCA）
！！！请尽量不要使用CCA！！！
如果你打算写冷却等需要ticking的cca：
- 请服务端尽量在重大更改时同步，而不是每秒同步！
- 如果是类似于 cooldown-- 的需要同步的逻辑，每10s再同步。
- 或者使用 `GameUtils.getTicksFromGameStart() + time` 设定触发时间来代替（只需要在触发和结束的时候同步更改）（推荐）（注意，如果是同步间隔，还是建议使用level.getGameTime()，此处API在时停和会议期间会暂停，一般情况的cd都建议使用此API避免与会议冲突。）


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
- 
！！！尽量使用此API，不要使用CCA！！！

# 语言文件
遵循使用翻译键，优先补全 `zh_cn.json`
# 有关背包界面（LimitedInventoryScreen）API

- 不要用 mixin 直接改 `LimitedInventoryScreen`！请使用事件或 SRERole 钩子。
- 事件（纯客户端）：`io.wifi.starrailexpress.event.client.LimitedInventoryScreenEvents`
  （INIT / INIT_TAIL / RENDER / RENDER_TAIL），非职业扩展（如 modifier）用。
- 职业扩展：SRERole 上的
  `.setInventoryScreenInitHandler(客户端函数)` /
  `.setInventoryScreenInitTailHandler(客户端函数)` /
  `.setInventoryScreenRenderHandler(客户端函数)`
  在客户端注册（如 NoellesrolesClient.onInitializeClient），钩子调用时内部会先判断运行环境
  （`FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT)`），非客户端直接返回。
- 服务端类严禁直接 import 客户端类！需要客户端执行客户端方法时：判别环境后经
  SREClient（客户端入口，允许客户端 only 方法）执行。
- 轮椅方法：`LimitedInventoryScreen.addRoleWidget/removeRoleWidget/clearRoleWidgets/reinit`（添加组件/重建界面）；
  选人列表的分页、玩家名搜索（输入框）、按名排序见
  `io.wifi.starrailexpress.client.gui.screen.ingame.PlayerPaginationHelper` 与 `RoleScreenHelper`
  （翻页 nextPage/prevPage/jumpToPage、搜索 attachSearchBox、排序 setNameExtractor/setSort）。
- 搜索框提示文字使用翻译键（`gui.starrailexpress.role_screen.search`），优先补全 zh_cn.json。
