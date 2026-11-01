# Sodium / Iris 场景区段重复收集越界修复

## 崩溃与根因

对应 `crash-2026-11-01_20.39.54-client.txt`：Iris 阴影渲染调用 SRE 的 `sre$addSceneSections` 后，`ChunkRenderList.add` 抛出 `Index 256 out of bounds for length 256`。

Sodium 0.8.13 的一个 region 包含 8×4×8 = 256 个区段，因此几何、精灵、方块实体的区段索引数组固定为 256 项。SRE 原先用 `RenderSection.lastVisibleFrame == frame` 判断场景区段是否已经加入。但 Iris 阴影使用的 `TreeSectionCollector.visit(x, y, z)` 直接访问区段，不更新该可见帧。场景区段可能已经填满列表，却被 SRE 再次追加，最终写入第 257 项。

## 修复

- `SceneSectionCollectorMixin` 在 `SectionCollector.visit(RenderSection, int)` 入口记录本收集器实际访问的区段，使用引用身份集合。普通 `visit` 和 `visitWithFlags` 均经过此入口，零标志区段也会登记。
- 场景追加查询当前收集器的访问记录，不再用跨主画面/阴影共享的区段可见帧去重。不同收集器各自独立；重建后的新区段实例不会被旧实例误判。
- 只阻止 SRE 重复追加，不取消 Sodium 自己的访问，保留网格构建完成后的增量标志更新、场景区段恢复及初始构建队列扩容。
- 新 Mixin 仅在 client 注册，并沿用现有 Sodium 可选模组检查。不扩大数组、不吞异常、不禁用阴影，不新增依赖或修改版本号。

## 验证

`./gradlew test remapJar --offline --console=plain` 成功：702 项 JUnit 测试，0 失败、错误或跳过。其中新增 5 项测试覆盖树形访问、256 个槽位与重复追加、主画面/阴影/下一轮独立集合、零标志/增量访问及区段实例身份。

另在真实 MC 1.21.1 / Fabric Loader 0.19.3 / Sodium 0.8.13 隔离客户端运行临时测试模组，通过反射调用实际注入后的场景追加钩子，使用真实 `RenderRegion`、`RenderSection`、`TreeSectionCollector` 和 `OcclusionSectionCollector`：

1. 先用原可见帧判断逻辑复现满 256 区段后的数组越界，输出 `SRE_SCENE_OLD_OVERFLOW_REPRODUCED`。
2. 同一满区段收集器连续追加 100 次，几何、精灵、方块实体数量始终为 256；半满列表只补入缺失区段。
3. 主画面收集器、同帧新收集器及下一帧收集器均正常；零标志后追加几何/实体/精灵标志仍生效。
4. 256 个未构建场景区段均进入初始构建队列，再次追加不重复排队。

输出 `SRE_SCENE_COLLECTOR_PASS checks=1194`，客户端正常关闭，Gradle 退出码 0。测试模组及游戏目录仅在会话 scratch，不包含在正式 JAR，也没有替换用户实例文件。

### 验证边界

尝试装入 Iris 1.8.14-beta.1 的开发环境客户端，但其既有 `MixinLevelRenderer` 的 `iris$writeRainAndSnowToDepthBuffer` 在本次测试执行前因开发映射注入解析失败（`desc is null`）而终止。因此上述成功客户端测试未装载 Iris；它直接覆盖 Iris 使用的 Sodium 树形收集路径，但不等于完整 Iris 光影集成验收。没有进入用户地图、连接多人服务器或完成真实光影场景的人工视觉验收。

## 交付

正式产物：`build/libs/starrailexpress.jar`。停游戏、备份旧 JAR 后替换客户端 SRE 模组；不要同时保留两份 SRE。本轮不自动替换用户实例 JAR。
