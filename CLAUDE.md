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
遵循使用翻译键，优先补全 `zh_cn.json`。

## 职业 / 物品介绍文案规则
面向玩家的介绍文案（如 `src/main/resources/assets/role_modifier_intro/lang/*.json` 的 `info.screen.roleid.*`，以及 `noellesroles` 的语言键）遵循以下原则：

- **不写过场与技能演出**：不要写镜头拉近、画面演出、粒子特效演出等玩家不可见、不关心的“过场”描述。
- **面向玩家，避免专业 / 技术术语**：不用 HUD、粒子、附身、spectate 等开发侧或技术词汇，改用玩家能直接理解的说法。
- **不要一组一组地罗列**：避免“X、Y、Z 均计入”“A、B、C 均不可”这种罗列式，改写成通顺连贯的句子。
- **概括实现细节**：部分规则可以概括，不必逐条描述具体实现（如诡物的计数方式、绑定范围、各种中断条件的逐条罗列，可概括成一句话表达核心约束）。
- **给定概括示例必须照做**：当用户给出具体的概括示例时，严格按示例改写，不要自作主张把被要求概括掉的内容（如“可转交”“真实好人（含警长）”等）又加回去。
- **不要漏字漏词**：润色时一个词语都不要少字漏字，不得因润色而丢失关键功能信息——所有机制、数值、约束都要保留。
- **语言通顺连贯**：句子读起来通顺，不要把一句话硬拆成两半（半句 + 半句），也不要有生硬拗口的表述。
- **多语言同步**：`zh_cn.json` 修改后，对应的 `en_us.json` 等语言文件要同步更新，保持内容一致。

- **阵营首句格式**：介绍正文第一句话必须是且只能是“xx阵营”，换行后写明阵营关系：
  - 杀手方中立：`中立阵营` 换行 `与杀手一同获胜`
  - 偏好中立：`中立阵营` 换行 `与平民一同获胜`
  - 独立胜利中立：`中立阵营` 换行 `拥有独立胜利条件`
  - 命令方块事件职业：`xx阵营` 换行 `仅由命令方块事件产生。`
  - 文案中的“乘客阵营”统一写作“平民阵营”。



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