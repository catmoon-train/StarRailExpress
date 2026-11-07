# SRE Mixin 准备优化实验（2026-11-07）

## 结论

**没有证明整体启动提速，最终提前过滤默认关闭，仅保留显式开启的实验入口。** 既有原始类字节缓存、状态组合索引、邻接布局复用、资源及运行兼容功能保持不变。仍未达到此前 10 秒目标。

本轮完整盘点 80 个顶层模组，见 [逐模组 Mixin 清单](mixin-mod-inventory.md)。不根据声明数删除有效补丁，不把包含式代理栈归为 SRE 插件自身的独占 CPU。

## 完整实例同产物对照

使用用户原 BAT 原样启动 `D:/StarRailHarpyExpress/versions/SRE4.3`，保留 Minecraft 1.21.1 / Fabric Loader 0.20.1 / Java 21、80 个模组、原 JVM 参数及认证、安全、更新流程。没有操作其他游戏，也没有进入存档或服务器。

A/B 全程使用同一个兼容 JAR，SHA-256 为 `93b20d1cef4ffaa29d09709e1a08139534f38880137e6947cb61ac688d711147`。仅临时修改新功能专属开关，DashLoader 缓存键始终为 `sre-a15e3a3be7cf07d0f86a7e64ec7dbee8b086a00acea55c59c7efaa45dda25349`。

单位秒；CPU 为 watcher 期间每秒采样的系统平均值，不是独占 CPU。

| 轮次 | 缓存/诊断 | mod-construction | mod-loading-complete | 资源完成 | Game took | CPU |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 启用，首次 | SAVE / JFR | 33.050 | 54.736 | 84.174 | 85.524 | 74.48% |
| 关闭，热启 1 | LOAD / 无 JFR | 29.808 | 54.326 | 66.285 | 67.412 | 80.17% |
| 启用，热启 1 | LOAD / 无 JFR | 32.296 | 56.384 | 68.539 | 69.961 | 79.39% |
| 关闭，热启 2 | LOAD / 无 JFR | 30.134 | 55.276 | 65.688 | 67.083 | 78.53% |
| 启用，热启 2 | LOAD / 无 JFR | 30.391 | 52.775 | 63.030 | 64.729 | 77.95% |

关闭均值 **67.248 秒**，启用均值 **67.345 秒**，相差约 0.098 秒；一组慢、一组快，没有稳定收益。样本较少且机器有并发负载，不能根据最好的 64.729 秒宣称提速，也不把这点均值差解释成固定性能回退。首次 SAVE/JFR 不参加热启动均值，不能与此前低负载成绩直接混比。

## 实现与安全边界

原核心的 `WatheMixinBlocker`、`SodiumExtraLightOptionMixinBlocker` 经 MixinSquared 在 `preApply` 才取消；此时目标元数据和 ASM 准备已经完成。本轮实验仅尝试前移其中已核对的一部分无效准备，不取消原本生效的 Mixin。

新增 `EarlyMixinPruner`、`PendingMixinPruner`、`SreMixinCancellationPolicy`，从兼容插件 `onLoad` 调用，不引入 agent、启动参数或新依赖，不修改核心或第三方 JAR：

- 同时覆盖已选择但未准备的 `pendingConfigs` 和剩余全局队列；规范化包名末尾的点，只处理 common/client 声明。
- 原取消范围共 79 个声明：Wathe 54、Ratatouille 14、Sodium Extra 11。离线结构/引用检查后清单收窄为 42 个候选；运行时额外排除实现接口的一项，实际提前移除 **41 个**（31 / 5 / 5）。安装校验及过滤观测为 45–77ms，不是节约的时间。
- accessor、invoker、接口、嵌套/nest 类、非 Object 继承或未核对类保留原准备。特别保留 Ratatouille `BlockRenderManagerAccessor` 和被 `LivingEntityMixin` 引用的 Wathe `EntityMixin`。
- 校验核心取消器的注册及类摘要、MixinConfig/Processor/Transformer 实现摘要、MixinSquared 0.3.7-beta.1、目标版本/配置/插件及候选类摘要；Sodium Extra 公共插件父类也校验摘要。未知实现安全回退。
- 原 MixinSquared 取消器、配置插件、服务端列表及插件动态添加项保持原流程。已准备配置不动。
- 先完成全部计划，再替换列表，保留顺序和原列表对象内容。写入失败按逆序恢复；无法恢复时明确失败，不以“安全回退”掩盖半提交。

开启时运行日志确认移除 41 个声明，晚期取消日志从 59 条变为 32 条。两者不应相减直接对应：部分 common 声明的目标在本次客户端启动中并未实际加载。日志减少不作为性能证据。

本实验仅核对当前完整实例及指定实现，未声称对任意新增模组组合或全部游戏行为做了等价性证明。

## 最终开关

最终版本 **默认关闭**，不自动创建配置。实验时可显式创建：

```properties
# config/sre-pack-compat-mixin.properties
enabled=true
```

`enabled=false`、不存在文件或缺少 enabled 项均不启用。格式/读取错误不启用，保留原取消流程。A/B 临时配置已删除，恢复了实例原先不存在该文件的状态。

## 构建与验证

最终执行：

```bash
./gradlew :sre-pack-compat:test :sre-pack-compat:remapJar --offline --console=plain
```

构建成功，**173 项测试通过，0 失败、错误、跳过**。新增 20 项覆盖名称边界/独立规则、白名单、摘要不匹配、接口/accessor/nest/继承保留、顺序/null/重复项、未知配置/声明、已准备跳过、重复执行、提交前变更、读写失败回滚及默认关闭的配置行为。

实际 A/B 五轮均到完整资源加载日志和首个就绪 tick，无新增过滤失败或致命 Mixin 应用错误；首次新缓存成功后台保存，后续四轮均 LOAD。只正常关闭本任务创建的客户端，不以窗口出现冒充启动完成。没有测试存档、联机或长时间游玩。

最终默认关闭包另用原 BAT 完成一次无 JFR 的正式加载：26.502 / 44.515 / 80.782 秒三个里程碑，`Game took 82.608` 秒，缓存为更换产物后的 SAVE，并成功后台保存（15.6 秒）。日志确认新过滤项未启用、首个就绪 tick 已到达；临时开关文件不存在。这不是热启动成绩，也不参加上面的同产物 A/B。

构建已有 `addExtraModel` 映射提示及 Gradle 弃用提示仍保留，退出码 0；未为此扩大改动范围。

## 部署与回退

- 最终默认关闭产物：`sre-pack-compat/build/libs/sre-pack-compat-1.0.0.jar`。
- 最终 SHA-256：`fe92be757319df78d673af6417b5cf4b31d80714495d4f67eab368d717df8eaf`。
- 相对 A/B 包只改变 `EarlyMixinPruner.class` 的默认开关；相对本轮前包只增加实验实现/清单及插件调用，没有删除旧内容或覆盖其他功能。
- 本轮前有效备份：`D:/StarRailHarpyExpress/versions/SRE4.3/PCL/startup-mixin-20261107-144516/sre-pack-compat-1.0.0.jar`，SHA-256 `771299be6ee95b14d6d075aaed8470550d513a494377d22287378f37f38f1a08`。
- 每轮核对其他 79 个模组哈希不变；核心未重新打包或部署，原 BAT 未修改。

回退时先正常退出目标 Minecraft，再用本轮前备份替换实例兼容 JAR，不在 mods 并存两份。

## 后续定位依据

本轮冷建 JFR 的 1888 个执行采样中，Mixin 准备包含式调用链 79 个、应用调用链 36 个、SRE 业务调用链 244 个；有重叠，不是独占毫秒数，不能用来分摊每个模组的耗时。

SRE `GeneralModelLoadingPlugin.modifyModelOnLoad` 出现 11 个直接叶采样，源码确认其对每个顶层模型遍历 `BASE_MODELS`。这是模型回调业务，不是插件布尔判断或无效 Mixin 准备；后续若处理应保留命名空间、前缀重叠的首匹配顺序、弓弩阶段和资源重载语义。本轮仅定位，**没有改动该回调**，也不能用冷建采样证明热缓存收益。

原始清单、JFR、逐轮日志/CPU/哈希及部署记录位于本会话 scratch 的 `startup30/mixin-*`。不复制或打印含凭证的 BAT 内容。
