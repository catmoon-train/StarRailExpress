# 区域地图取色与增量渲染

## 参考与范围

检查用户提供的 Xaero Minimap 26.6.0 和 World Map 1.47.0 的 MC 1.21.1 Fabric JAR 字节码：

- MinimapWriter.loadBlockColourFromTexture / addBlockColorMultipliers：烘焙模型面与备用材质、染色索引、位置相关颜色乘子。
- MinimapWriter 的 writeChunk / writeTile、detectedChunkChanges 和 getSectionBasedHeight：分区更新、变化记录、区段高度查询。
- World Map 的 MapWriter 与 LeafRegionTexture：地图数据生成与纹理上传分离，按加载状态、纹理版本管理上传。

本实现仅参考上述思路，没有复制第三方代码、资源或引入 Xaero 依赖。仍使用客户端已有区块和玩家高度切片，保留室内地图、2D/3D、平移缩放、任务点与回声设施筛选；不请求服务端未发送的区块。

## 改动

`AreaMapPalette` 从当前资源包的烘焙顶面读取图集已有的 CPU 像素，不读取显存，也不重新解码 PNG。材质均色按透明度加权，每张材质至多采样 16×16 个点，方块状态与材质分别缓存。草叶等使用方块颜色提供器，流体使用 Fabric 流体渲染提供器。透明表面与下面的材质合成；连续同类流体只叠色一次。动画材质固定取一个有效帧，不逐帧重算整张地图。

`AreaMapManager` 保留最多 512×512 个采样格，按 16×16 格瓦片排队，玩家附近优先。直接读取已加载区块并跳过全空气区段。打开地图时每 tick 上限 4096 列、扫描时间预算 3ms；小地图为 1024 列、1.5ms。每 16 列检查一次时钟，因此这些是协作式预算，不是最坏情况耗时保证。首次模型或材质缓存建立可能超出单次预算。

完成后不再循环全图重扫。客户端收到区块时使相应瓦片失效，LevelChunk.setBlockState 真正改变方块后使附近瓦片失效。队列去重。切换楼层会清空旧切片，换世界、区域、地图或回声对局时重建；资源包重载和断开连接时释放纹理、清除缓存。

地形使用相邻高度的柔和方向明暗，墙体只适度压暗，不再使用原来的 45% 亮度。每张纹理独立累计变化矩形，仅在绘制时上传该子区域；2D 不上传墙层变化。地图画布和小地图内部使用中性深色底，外围与筛选面板仍保持原有风格。

进度只统计真正读到区块的采样格，不把未加载区块算作完成。未收到的部分保持空白，已探索区块卸载后保留当前切片的内存数据，重新加载时刷新。没有新增磁盘地图缓存，重进世界仍需重新生成。

## 验证

执行：

```bash
./gradlew :test --tests 'org.agmas.noellesroles.client.map.*' \
  --tests 'org.agmas.noellesroles.game.modes.echo.client.EchoAreaMapIntegrationTest' \
  :remapJar --offline --console=plain
```

主模组编译、针对性测试与重映射打包通过。测试覆盖 ABGR/RGB 通道、群系染色乘法、透明像素权重、透明层合成、亮度钳制、玩家附近优先、非整瓦片边界、局部失效去重与唤醒、焦点重排、跨瓦片变化和上传矩形。保留并更新回声地图接线回归检查。`git diff --check` 通过。

首次未限定任务路径的命令还启动了其他子项目：`sre-performance` 当时缺少 RuntimeCounters，`sre-pack-compat:test` 无匹配的地图测试；未修改这些无关模块，改用上述主模组专属任务验证。

未进行游戏内截图对比、实际区块扫描耗时测量或运行时 Mixin 启动验证；不能把配置预算或列数上限当作实际加速倍数。仍需在目标地图检查资源包配色、楼层切换、开关门、重新加载区块和 3D 视图。本次未更改模组版本号，也未替换用户的游戏实例 JAR。
