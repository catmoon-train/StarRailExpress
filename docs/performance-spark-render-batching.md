# spark 定向优化：复用 Iris 告示牌文字合批

## 输入与采样口径

用户提供 `7cl0gwO3GE_1_.sparkprofile`，SHA-256：
`7863ca32ff97fd065d80a37149472602ae3090bf11071371bd581cb8d38edc7c`。
使用当前整合包 spark 1.10.109 的 protobuf 类在本地解析，未上传原文件、服务器配置或玩家资料。

元数据：EXECUTION / JAVA，间隔 4000 µs，起止时间跨度 58022 ms，仅 Render thread；
调用树权重合计 59212 ms。采样权重与墙钟时长不完全相同，以下比例统一使用 59212 作分母。
这是线程栈采样，不是 GPU 计时；inclusive 节点有包含关系，不能相加当作可消除的耗时。

| 调用路径（inclusive） | 权重 ms | 占比 |
| --- | ---: | ---: |
| Iris FullyBufferedMultiBufferSource.endBatch | 8996 | 15.19% |
| GLFW.glfwSwapBuffers | 5408 | 9.13% |
| Iris FullyBufferedMultiBufferSource.readyUp | 1964 | 3.32% |
| SignRenderer 的 sre$flushSignText | 1832 | 3.09% |
| SREPlushBlockEntityRenderer.renderBakedPlush | 1356 | 2.29% |
| PlateBlockEntityRenderer.renderItems | 1104 | 1.86% |
| ClientPlayerDataCache.economy | 564 | 0.95% |

明显开销还包括 GL draw/upload/状态切换、ModernUI 的文本布局和世界文字。
不能直接把 OpenGL 驱动调用删除，也不能把 SwapBuffers 等待等同于 JVM CPU 运算。
本轮不按采样百分比承诺 FPS 收益，不以此声称已复现所有核显 4 FPS 场景。

## 已实现

### 告示牌：不绕过 Iris 的世界合批

旧 `SignRendererMixin` 将普通文字、发光描边统一写入独立 `SIGN_TEXT` 缓冲，
每次 `renderSignText`（每一面告示牌）结束立即 build/upload/draw。
即使 Iris 已提供完整世界批处理源，也被这条路径绕过，产生逐面提交与状态切换。

新增可选 `IrisWorldTextBatchingSourceMixin`，只给以下类添加 `WorldTextBatchingSource` 标记接口：

- `FullyBufferedMultiBufferSource`
- 它实际交给渲染器的 `FullyBufferedMultiBufferSource$UnflushableWrapper`

`TextBatchingBuffer.signTextSource` 只在输入对象带此标记时原样使用世界缓冲。
普通和描边文字都保留原 Font 调用、矩阵、颜色、光照、DisplayMode 与参数；
由 Iris 自己接收其 RenderType、包装着色器状态、处理排序和世界阶段提交，不在 GUI 阶段延迟绘制。
原每面刷新钩子只会刷新回退缓冲，Iris 路径不再向其中提交顶点。

没有 Iris 时由插件跳过兼容 Mixin；`@Pseudo` / 字符串目标避免链接可选类。
普通来源仍逐面写入并刷新原 `SIGN_TEXT`，不强制接管其他模组缓冲。
没有扩张告示牌可见距离，没有改文字内容、发光描边规则、聊天层级、粒子或实体可见性。

### 皮肤：沿用上轮已完成的 JSON 热路径优化

本次输入也证实 `ClientPlayerDataCache.economy` 仍在渲染调用链中。
将上一轮尚未提交的同步时解码、只读皮肤查询、连接清理和 14 项测试一同提交，
细节及独立查询基准见 `performance-client-skin-cache.md`。

## 未采用的方向

- 不改变 Iris 半透明拓扑排序、跨帧 builder/LRU 归属或内存压力回收时间；不能把整个 endBatch 当成冗余工作。
- 不缓存可变 Component 的 hashCode 或直接禁用 ModernUI，避免文字动态更新失效。
- 不通过删减玩偶、餐盘内容、必要实体/粒子或暗度效果换取帧率。

## 验证与限制

- `test remapJar runClient --offline --console=plain`（隔离测试 init script）成功，730 项 JUnit，0 失败/错误/跳过。
- 新增 4 项 `SignTextBatchingTest`：普通来源回退、标记来源复用、不保留上一世界来源、Mixin 不添加行为方法或字段。
- MC 1.21.1 / Sodium 0.8.13 / Iris 1.8.14-beta.1 的独立 Fabric 启动，在 preLaunch 阶段加载真实目标类，
  验证本体与私有包装类都已实现标记，告示牌的现有注入正常转换：`SRE_IRIS_TEXT_PRELAUNCH_PASS checks=3`。
- preLaunch 测试在游戏初始化前正常退出，不连接服务器；它证明 Mixin 实际生效，**不是完整光影画面或 GPU 像素验收**。
- 尚无当前整合包同场景、同前台路线的优化后 spark/FPS 对照；需要实际告示牌密集场景复测提交次数与帧时间，
  并检查普通/发光/双面文字、遮挡、第三人称及启停光影。没有将采样的 3.09% 写成实际提升幅度。

正式产物仍为 `build/libs/starrailexpress.jar`，不含临时验证入口/Agent。
构建会包含当前工作树原有源码改动，但提交只包含本轮性能相关文件，不包含用户其他未提交工作。
本轮未替换运行中的实例、未改更新器或模组版本；发布到整合包正常更新源后再启动核对实际 JAR 哈希。
回退只需正常退出后恢复旧 JAR，无配置、存档或数据库迁移。
