# 注意事项
在你写代码之前，请阅读 `ai_doc.md` 以及 `docs/*.md`

`docs/*.md` 中的 API 可能已过时。以实际为准。

如果写职业，请先看 `docs/AI创建新职业攻略.md` (./AI创建新职业攻略.md)：里面是可直接抄的模板、真实文件路径、翻译键位置对照表与踩坑清单（原则：职业逻辑集中在 SRERole 子类与 RoleData 里，便于维护且更节省token）。

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

# 粒子发包（服务端）

`ServerLevel.sendParticles(...)` 每次调用都会构造**一个** `ClientboundLevelParticlesPacket`，发给 32 格内**每个**玩家；包里的 `count` 参数才是"这次生成几颗粒子"，不额外增加包体积。

**禁止在循环里反复调用 `sendParticles` 来凑粒子数**，需要 N 颗就传 `count = N`：

```java
// 错误：N 个包 × 玩家数
for (int i = 0; i < 20; i++) level.sendParticles(P, x, y, z, 1, 0.3, 0.3, 0.3, 0.0);

// 正确：1 个包
level.sendParticles(P, x, y, z, 20, 0.3, 0.3, 0.3, 0.0);
```

服务端写粒子优先用 `io.wifi.starrailexpress.util.ParticleFx`（`burst` / `sphere` / `segment` / `region`），它保证一次调用只发一个包，语义见其类注释。场景方块用 `org.agmas.noellesroles.scene.SceneParticles`。

代价是环形/螺旋/柱状这类"按形状摆点"的效果会退化成中心高斯团——网络与性能优先时接受这一取舍；反之也不要为了形状再写回循环。

面向单个玩家的私有包 `sendParticles(ServerPlayer, ...)`、以及位置随玩家变化的"每玩家一包"不适用本条。

## 自定义形状粒子：服务端发 id，客户端渲染

环形、螺旋、沿轨迹、跟随实体、多边形范围提示等形状，**用原版包表达不了**（原版只能"在一个点按高斯散布"），而为了摆形状在服务端循环里逐点发包又会把包数放大成百上千倍。

约定：**服务端只发一个包（id + 原点 + 时长 + 参数），形状由客户端按 id 生成**（客户端逐点 `addParticle` 不进网络，形状可以任意复杂）。

服务端（一个包，可携带多条）：

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mymod", "shock_wave");
ParticleFx.sendCustom(level, id, pos, 20, 6.0F, 0.5F);        // 一条，参数含义由客户端处理器定义
ParticleFx.sendCustomBatch(level, center, entries);           // 同一时刻多条：合并成一个包
```

客户端（注册一次，按 id 生成形状）：

```java
CustomParticleHandlers.register(id, (level, origin, durationTicks, params) -> {
    double radius = params.length > 0 ? params[0] : 1.0D;
    for (int i = 0; i < 64; i++) {
        double a = Math.PI * 2.0 * i / 64;
        level.addParticle(ParticleTypes.END_ROD,
                origin.x + Math.cos(a) * radius, origin.y, origin.z + Math.sin(a) * radius, 0, 0, 0);
    }
});
```

约束与行为：单包最多 64 条、每条最多 8 个 float 参数（服务端超限会截断并告警，客户端超限直接断开连接）；广播半径 64 格；**未知 id 客户端静默忽略**，所以服务端可以先上新特效、客户端后续版本再补处理器；同一个 id 客户端重复注册会立刻抛错。

文档：[`docs/api.md`](docs/api.md) 的「粒子与特效」章节。

# 测试/调试
必须完成一个功能块再一次性进行test

# 编码
请使用 `UTF8 without BOM` 编码