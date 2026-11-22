# se36tYRgdx.sparkprofile 低帧分析

## 结论

这份记录来自**安卓手机，而不是 D 盘的 Windows 实例**。应优先排查手机的 GL4ES 图形转译/数据上传、动态材质动画、环境/声音负担与 2 GB Java 堆压力。其他模组确实出现在值得检查的执行路径上，但目前没有逐项停用的对照，**不能认定某一款模组就是唯一原因，也不能给出删除后的 FPS 提升幅度**。

此外，原文件存在父子节点时间权重不守恒的问题。本报告不把这些异常权重换算成模组耗时百分比；下面的顺序是排查优先级，不是可靠的耗时排行榜。

D 盘游戏目录只用于核对版本和查看本地实现；电脑的 RTX 2080 SUPER、视距 4、260 FPS 上限及光影配置，均不能作为手机采样的配置或硬件证据。若目标是诊断电脑自身低帧，需要另采电脑端记录。

## 1. 输入与环境

- 原文件：`G:/download/se36tYRgdx.sparkprofile`，2,286,286 字节。
- SHA-256：`741f14ab042266c75526199cc483ab616e01d02e8e65ad39cd2091df3f976e5a`。
- 对照目录：`D:/StarRailHarpyExpress/versions/SRE4.3`。仅只读访问，未修改游戏文件、配置或模组。
- 使用实例内 Spark 1.10.109 的 `SparkSamplerProtos.SamplerData` 本地解析，并用独立 Python protobuf wire 解码复核关键节点；未上传原始记录。

| 项目 | 记录内的事实 |
| --- | --- |
| 系统 / 架构 | Linux / Android-16，aarch64 |
| 启动器 | Zalith Launcher v2 路径，启动器版本标记 `2.4.8-sre4.5.0-lite-fix2` |
| 游戏 / Loader | MC 1.21.1 / Fabric 0.20.1 |
| Java | 21.0.1-internal，堆初始值和上限均为 2048 MiB |
| 图形后端 | `-Dorg.lwjgl.opengl.libname=libng_gl4es.so` |
| 窗口参数 | `2800×1260`；这是启动参数，不是已验证的实际逐帧渲染分辨率 |
| 采样 | EXECUTION / JAVA，4000 μs 间隔，仅 Render thread |
| 起止时间 | 2026-11-13 18:36:05.918—18:38:01.057（UTC+8），跨度 115.139 秒 |
| 可见线程权重 | 90,560 ms；不能与墙钟跨度直接混用 |
| 来源清单 | 190 项，包含 Fabric 子模块和库，并不等于 190 个独立模组 JAR |
| FPS | 没有可直接使用的逐帧 FPS / 帧时间序列 |

`numberOfTicks=2296` 和接近 20 的 TPS 是客户端逻辑 Tick 指标，**不是 20 FPS**，也不是远端服务器性能证明。Android 的 CPU 使用率字段为 0，不能解释为 CPU 空闲；记录未提供足以判断的手机 SoC / GPU 型号与 GPU 计时。

## 2. 时间权重异常：为什么不列“模组占比”

调用树有 18,974 个节点。逐节点检查发现 1,268 个节点的直接子节点权重之和超过自身；920 条直接父子边的子节点甚至比父节点更大。例子：

| 节点 | 文件原始权重之和 |
| --- | ---: |
| Render thread | 90,560 ms |
| `Main.main` | 90,560 ms |
| 它的直接子节点 `Minecraft.run`（`class_310.method_1514`） | 181,116 ms |
| 一处 `GL15C.nglBufferSubData` 叶节点 | 119,360 ms |
| 一处 `GL11C.nglTexSubImage2D` 叶节点 | 137,248 ms |

上述数据由 Spark 官方消息类解析，并直接从原文件的 packed double 字段独立复核，结果一致。检查了 `ProtoTimeEncoder`：它按时间窗口存储转换后的数值，并非需要对父子关系做差分解压。不能将异常当作普通 inclusive 重叠：同一方法在不同路径下聚合可能重叠，但单条直接父子边不应违反包含关系。

未确定异常产生于哪个采集/导出环节，未将其认定为 Android 或某个模组的已知 Bug。不能擅自按深度、倍数或归一化“修复”后给出精确占比。调用关系仍能提供候选方向，但连相对权重排名也需要新样本验证。

## 3. 优先检查的执行路径

### A. 动态材质动画 → 纹理上传 → GL4ES

记录两个时间窗口都包含：

```text
Minecraft.tick
  → TextureManager / TextureAtlas.cycleAnimationFrames
  → SpriteContents$Ticker
  → SpriteContents$InterpolationData.uploadInterpolatedFrame
  → NativeImage.upload
  → glTexSubImage2D
```

这不是区块生成或角色服务端逻辑，而是材质动画和插值帧上传。GL4ES 将桌面 OpenGL 调用转译到手机图形接口；频繁纹理更新值得优先对照。不能据此确定是哪一个资源包或哪一种方块动画，因为记录没有可用的逐纹理统计，也没有手机资源包清单。

建议先在手机端临时关闭装饰性动态材质/插值或用较轻的材质做同场景对照；不要把关闭所有玩法提示粒子、隐藏必要实体当作修复。

### B. Iris 世界批处理 → ImmediatelyFast 顶点上传

记录包含：

```text
LevelRenderer
  → Iris FullyBufferedMultiBufferSource.endBatch
  → RenderType / BufferUploader / VertexBuffer.uploadVertexBuffer
  → ImmediatelyFast optimizeVertexDataUploading
  → glBufferSubData
```

这是图形缓冲提交链路。Iris 和 ImmediatelyFast 位于路径中，说明可作为安卓兼容性对照候选，**不等于它们制造了全部上传工作，也不证明二者冲突**。

记录出现的是 `VanillaRenderingPipeline`，没有据此证明正在运行外部光影包；Iris 即使不启用光影也可能提供实体合批。电脑后来开启 Complementary 的日志不能解释手机采样。

建议先测试较低实际分辨率与启动器支持的其他后端；随后在手机测试副本中一次只停用 Iris 或 ImmediatelyFast 之一，保留其他条件，检查画面正确性及依赖关系。不建议直接永久删除 Sodium、Iris、ImmediatelyFast 全套。

### C. Dynamic Surroundings：区块扫描与地雾计算

实际路径包含：

- `Handlers.tickHandler → AreaBlockEffects → SystemsScanner → RandomBlockEffectSystem.sample`。
- `GroundFogField.update / prepare / boxBlur / relight`。

这不是只有一个空的事件入口，而是实际扫描和环境效果计算。**动态环境 `dsurround` 是优先做单独停用对照的可选模组之一**。手机端先测试关闭地雾/环境方块效果，或在测试副本中单独停用它，观察同一路线帧时间。

### D. 声音链路与 Presence Footsteps

记录有：

```text
SoundManager.tick → SoundEngine.tickNonPaused / play
  → CompletableFuture.join → LockSupport.park

PresenceFootsteps → ImmediateSoundPlayer.playSound
  → SoundEngine.play → CompletableFuture.join
```

说明主线程存在等待声音任务完成的路径，Presence Footsteps 的脚步声也接入其中。但声音执行线程未被采样，不能进一步断言是 OpenAL、物理声效射线、驱动还是其他声音工作阻塞。

建议在动态环境对照之后单独对照 Presence Footsteps。`sound_physics_remastered` 在主线程中可见的是少量缓存维护；其重工作可能位于其他线程，因此既不能直接定罪，也不能凭本文件排除它。若音效对照明显有效，再补充包含声音线程的采样。

### E. ModernUI 与 SRE 自身也有负担

记录包含 ModernUI 的文字布局/描画、SRE 聊天文字缓冲提交、餐盘/手持物品渲染，以及：

```text
玩家模型 / 手持物品判定
  → AllowItemShowInHand
  → MorphApiClient.getDisplayedPlushStack
  → PlushEquipmentManager.findIdentityPlush / collectInventories
```

因此不能把问题全部归咎于第三方模组。SRE 自身的渲染期查询和文字提交也值得后续定向优化；仅凭当前异常权重，尚不能确定优化收益大小。

ModernUI 可以作为后续文字链路对照候选，但须先确认包内依赖和中文字体回退，避免停用后出现文字缺失。当前没有证据支持为了帧数盲目删除角色、方块等玩法内容。

## 4. 内存压力与限帧的区别

| 指标 | 值 | 能说明什么 |
| --- | ---: | --- |
| 结束时 Java 堆已用 | 1795.97 / 2048 MiB（87.69%） | 堆余量较小；这只是一次快照，不是持续占用曲线 |
| G1 Old Gen 已用 | 1492.19 MiB | 大量堆占用位于老年代 |
| 累计 Young GC | 61 次，平均 47.13 ms | 本次进程累计，不能全算到这 115 秒里 |
| 累计 Old GC | 1 次，556 ms | 存在长暂停风险，但不能证明发生在采样区间内 |

手机可用物理内存和 swap 字段属于整个系统；不能把系统 swap 用量全部归因于 Minecraft。先关闭手机后台应用，再谨慎对照 2.5–3 GB Java 堆；若系统回收或换页变重，应恢复原值，不建议直接拉到 6–8 GB。

另有：

```text
RenderSystem.limitDisplayFPS → sre_pack_compat HybridFramePacer
  → LockSupport.parkNanos
```

这属于目标帧率等待，不能算成纯 CPU 计算浪费。已核对本地 `HybridFramePacer.pace`：根据调用方传入的 FPS 休眠，并在最后约 1 ms 短自旋；这不证明存在某个固定低帧上限。手机的帧率上限、垂直同步和失焦节流需要在手机上核对，不能套用 D 盘电脑的 260 FPS 配置。

## 5. 与电脑目录的版本核对

下列关键版本在手机记录与当前电脑实例中一致：

| 来源 | 版本 |
| --- | --- |
| Iris | `1.8.14-beta.1+mc1.21.1` |
| ImmediatelyFast | `1.6.15+1.21.1` |
| Sodium | `0.8.13+mc1.21.1` |
| ModernUI | `3.13.0.1` |
| Dynamic Surroundings | 元数据 `0.4.9`，电脑 JAR 文件名仍含 `0.4.8`，以元数据为准 |
| SRE | 元数据 `4.5.0`，电脑 JAR 文件名仍为 `star_rail_express-4.3.0.jar` |

当前电脑日志有 Chloride、FirstPerson、IMBlocker、SmoothMenu、Tekst 等额外条目，而手机来源清单没有它们。因此这些“仅在电脑目录中看到”的模组不能直接当作本份手机低帧的嫌疑。模组版本一致也不意味着文件哈希、配置、资源包或运行场景一致。

ReplayMod、LambDynamicLights、NotEnoughAnimations 等确实加载；在本份单线程且权重异常的记录里，不足以将它们判为首要原因，也不足以保证它们没有影响。

## 6. 推荐验证顺序

每项均在**手机端测试副本**中进行，保持相同地图、位置、视角和前台状态；每次只改一个因素，测试后恢复或保留经过验证的项目。预热后记录 60 秒真实平均 FPS 与 P95/P99 帧时间，而非只看某一瞬间的帧数。

1. **先取可靠基线**：重启客户端，低帧场景前台采一次新的 Render thread Spark，使用一次完整的开始—停止—导出流程。检查父子时间守恒；若仍异常，换用启动器支持的其他分析方式，并同时记录 FPS。现阶段不将反复导出、特定后端等猜测写成异常根因。
2. **实际分辨率降低**：启动参数为 2800×1260；若实际渲染确实如此，宽高各减半到约 1400×630，像素量为原来的四分之一。观察收益可帮助判断图形侧压力，但不能承诺四倍 FPS，也不能靠 GUI 缩放代替渲染分辨率调整。
3. **材质动画/插值单项对照**：针对纹理上传链路；手机资源包情况未知，先确认再调整。
4. **Dynamic Surroundings 单项对照**：优先地雾、环境方块效果，或测试副本单独停用。
5. **Presence Footsteps 单项对照**：若改善明显，再检查其他声音模组，并采声音工作线程。
6. **渲染后端及 Iris / ImmediatelyFast 对照**：仅使用启动器和设备支持的后端，分别测试，记录兼容性。未知手机 GPU，不能承诺 Zink / ANGLE 一定可用或更快。
7. **内存对照**：关后台后再谨慎增加至 2.5–3 GB，观察长帧与 GC，而非仅看平均 FPS。

## 7. 已完成的核验与未完成项

已完成：同版本 Spark 本地解析、18,974 节点结构检查、原始 wire 独立复核线程及四个关键节点、调用名映射、关键模组版本核对、限帧实现只读检查。

本次只新增本报告，未改代码、未删改模组、未修改游戏配置、未执行项目构建，也未进行手机现场复现或停用模组 A/B 测试。

仍需数据：可靠的新采样、手机实际 FPS/帧时间、SoC/GPU 与实际渲染分辨率、手机端画质/资源包设置。当前能够提供有依据的排查方向，不能提供经过实测确认的唯一根因或模组损失百分比。
