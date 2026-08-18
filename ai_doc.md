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
- 或者使用 `SRE.getTicksFromGameStart() + time` 设定触发时间来代替（只需要在触发和结束的时候同步更改）（推荐）


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
- 或者使用 `SRE.getTicksFromGameStart() + time` 设定触发时间来代替（只需要在触发和结束的时候同步更改）（推荐）
- 
！！！尽量使用此API，不要使用CCA！！！

# 语言文件
遵循使用翻译键，优先补全 `zh_cn.json`