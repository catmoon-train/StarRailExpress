# 注意事项
在你写代码之前，请阅读 `AGENT.md` / `docs/*.md`

`docs/*.md` 中的 API 可能已过时。以实际为准。

文件中的AI提示词与注释十分重要，你不能忽视。

按键绑定原则：优先不使用新的按键绑定，复用旧的（功能类似且不冲突时）。

按键绑定注册是常量，不应该动态注册。

避免与当前的和MC的按键冲突。

冷门按键绑定应该使用冷门的按键，比如小键盘上的按键。

UI打开不一定必须要按键绑定，也可以写客户端命令打开。客户端命令注册位置：`SREClientCommand.java`

功能类似或相同的尽量使用同一按键。

# 有关依赖

尽量不引入新依赖。引入的新依赖请不要直接丢文件。而是使用在线资源下载。

# 版本号

不要随便改模组版本号。版本号应当由人工手动更改。

# 有关玩家职业数据

你可以使用SRERole中的 
```java
.setRoleData(RoleData实例类::new)
```

RoleData实例类：可以extends SimpleRoleData，或是 implements RoleData
因为每次实例都是创建新的，理论上你不需要init和clear。
获取此实例类方法是 `RoleData.getNullable(类.class, 玩家)`
或者 `RoleData.getOptional(类.class, 玩家);`

！！！尽量使用此API，不要使用CCA！！！

# 语言文件
遵循使用翻译键，优先补全 `zh_cn.json`

# 粒子与特效（服务端发包）

`ServerLevel.sendParticles(...)` 每次调用都会构造**一个** `ClientboundLevelParticlesPacket`，发给 32 格内**每个**玩家；包里的 `count` 才是"这次生成几颗粒子"，不额外增加包体积。**禁止在循环里反复调用 `sendParticles` 凑粒子数**，需要 N 颗就传 `count = N`：

```java
for (int i = 0; i < 20; i++) level.sendParticles(P, x, y, z, 1, 0.3, 0.3, 0.3, 0.0); // 错误：20 个包 × 玩家数
level.sendParticles(P, x, y, z, 20, 0.3, 0.3, 0.3, 0.0);                            // 正确：1 个包
```

服务端优先用 `io.wifi.starrailexpress.util.ParticleFx`（`burst` / `sphere` / `segment` / `region`，一次调用只发一个包）；场景方块用 `org.agmas.noellesroles.scene.SceneParticles`。代价是环形/螺旋/柱状会退化成中心高斯团——网络与性能优先时接受这一取舍。

**需要自定义形状（环形/螺旋/轨迹/跟随实体）时，不要在服务端为形状循环发包**：服务端只发一个通用包 `CustomParticleS2CPayload`（`ParticleFx.sendCustom` / `sendCustomBatch`，传入 id + 原点 + 参数），形状由客户端按 id 生成：

```java
// 服务端：一个包
ParticleFx.sendCustom(level, ResourceLocation.fromNamespaceAndPath("mymod", "shock_wave"), pos, 20, 6.0F);

// 客户端：注册一次，自己摆形状（本地 addParticle 不进网络）
CustomParticleHandlers.register(id, (level, origin, durationTicks, params) -> { /* 逐点摆形状 */ });
```

约束：单包最多 64 条、每条最多 8 个 float 参数，广播半径 64 格，未知 id 客户端静默忽略，同 id 重复注册会抛错。详见 `AGENT.md` 与 `docs/api.md` 的「粒子与特效」。

# 测试/调试
必须完成一个功能块再一次性进行test