# Cobblemon 1.8.4 资源外置

## 结果与安装

已处理用户指定的 `G:/download/Cobblemon-fabric-1.8.4+1.21.1.jar`，原路径现为精简版。未替换任何 Minecraft 实例中的模组、配置或存档。

项目交付目录 `build/cobblemon-resource-split/` 与下载目录 `G:/download/Cobblemon-1.8.4-resource-split/` 均有以下文件：

| 文件 | 用法 | 大小 |
| --- | --- | ---: |
| `Cobblemon-fabric-1.8.4+1.21.1-slim.jar` | 替换客户端/服务端的原 Cobblemon JAR，mods 内只放一份 | 66.14 MiB |
| `Cobblemon-1.8.4-resources.zip` | 放入客户端 resourcepacks，启用后恢复完整基础资源 | 60.72 MiB |
| `Cobblemon-1.8.4-optional-adorncompatibility.zip` | 原内置 Adorn 可选资源，按需启用 | 0.04 MiB |
| `Cobblemon-1.8.4-optional-gyaradosjump.zip` | 原内置暴鲤龙外观可选资源 | 0.73 MiB |
| `Cobblemon-1.8.4-optional-regionbiasforms.zip` | 原内置地区外观可选资源 | 0.14 MiB |
| `Cobblemon-1.8.4-optional-uniqueshinyforms.zip` | 原内置闪光外观可选资源 | 0.83 MiB |

可选包需要时放在主资源包之上；不将它们混入主包以强制改变外观。四个原内置包在精简 JAR 里只保留 pack.mcmeta 和存在的语言文件，实际资源由对应外置 ZIP 提供。

无需启用完整资源包也能启动，但外观会缺图或使用 substitute 占位模型，声音及大部分特效不可用，日志出现缺模型/缺声音警告属于预期。需要完整宝可梦体验时启用主包。资源外置本身不会在主包已启用时自动减少同一批资源的运行内存。

## 用户确认的最小兜底方案

用户允许保留极少量启动/占位资源，而不是严格删除所有非语言 assets。原因：原版强制注册粒子着色器；VaryingModelRepository 的缺模型回退又依赖 substitute 资源，直接全部删除会在后续模型查询处失败。树果 patchModels 也对缺失几何做非空断言。

精简模组现只保留 `zh_cn`、`zh_tw`、`en_us` 三份主语言文件，及 **101 份非语言兜底资源，共 102,823 字节（约 100.4 KiB，未压缩）**：

- Cobblemon 粒子着色器及原版实体着色器覆盖。
- 图集定义，以及树果和杂项的小型几何数据。
- substitute 的几何、姿态、动画、解析定义与普通/闪光/眼睛层贴图。
- 模组元数据使用的原图标。

精确保留路径列于 `split-manifest.json` 的 `retainedFallbacks`，不是把全部普通宝可梦模型伪装成兜底保留。

主材质包包含原 `assets/` 所有非语言文件，包括兜底文件的相同副本，共 18,953 份、110,388,230 字节（未压缩）。完整资源启用后覆盖/补齐精简模组中的资源。主包不含语言；原内置可选包各自按原目录内容独立输出，其语言仍同时保留在模组原位置。

`data/`、`datapacks/`、全部 `.class`、Mixin、access widener、内嵌依赖及模组元数据逐文件保持原始字节不变；未改版本号、注册、玩法或存档逻辑。原许可证随 ZIP 保留。

## 备份与回滚

原始完整 JAR 备份：

`G:/download/Cobblemon-1.8.4-resource-split/Cobblemon-fabric-1.8.4+1.21.1.jar.original.bak`

原件 SHA-256：`77c4866b2e4c38afb488ed09b62ffd2729ce291dc793fca9c055c087f0b03978`

当前三语精简版 SHA-256：`a37e948eaf381fcba6c6a62ab8543058973d237292454e889c9100b7593eab11`

回滚时退出游戏，用该备份复制覆盖原 `.jar` 路径即可。不要把完整和精简两个同 ID 模组同时放进 mods。备份的 `.bak` 后缀防止被误当作模组扫描。

仅回滚本次语言筛选，可使用 `G:/download/Cobblemon-1.8.4-resource-split/Cobblemon-fabric-1.8.4+1.21.1.jar.before-language-prune.bak`；其哈希为 `66d1c8dd03f80880614b9b58879a5995408914ad9123497b949c59206150d85f`，对应上一轮含全部语言的 68.99 MiB 精简版。

## 验证

本次语言后处理删除了其他 26 份主语言文件，保留简中、繁中、美式英语及内置 Adorn 兼容包的美式英语。ZIP CRC 校验通过，所有保留条目逐字节一致；下载原路径、下载交付副本及项目交付副本已同步，完整资源包不变。具体记录见 `language-pruning.json`。本次未重新启动游戏；下列单测及两种客户端启动结果均为语言精简前已经完成的验证。

- `python -X utf8 tools/test_split_cobblemon_resources.py -v`：5 项测试全部通过，覆盖保留内容、命名空间、可选包目录、备份原样、二次替换拒绝、哈希漂移/篡改保护、缺兜底和签名包拒绝。
- 生成阶段逐文件比对所有保留文件和每个外置资源，并校验 ZIP CRC 与产物 SHA-256。替换前再检查输入/产物哈希，先备份并核验，最后同目录原子替换。
- 使用 Java 21.0.10、Fabric Loader 0.19.3、Fabric API 0.116.13、MC 1.21.1 的生产 intermediary 命名空间，分别启动两个全新隔离客户端；两者均完成实际资源重载、运行模型/贴图查询探针并以 0 退出。
- 不启用完整包：`COBBLE_SPLIT_PROBE_PASS full=false variations=1 berries=72`。上游加载 1 个几何模型；未知模型和皮卡丘模型均可回退，贴图查询返回 substitute.png。
- 启用完整包：`COBBLE_SPLIT_PROBE_PASS full=true variations=985 berries=72`。加载 1,198 个几何模型、10,287 个动画和 1,111 个粒子效果；皮卡丘解析项恢复，未知模型仍可回退。

证据为交付目录的 `verification.json`、`cobble-prod-no-pack.log` 和 `cobble-prod-with-pack.log`。临时探针不进入正式模组/材质包。

先期开发命名空间测试受 Loom 内嵌运行库处理及 Kotlin 反射映射问题阻断，不计为验收；已切换生产命名空间完成上述检查，未为了测试改动 Cobblemon 代码。

验证范围是独立客户端初始化、资源重载及关键模型/贴图 API，不等于完整整包联机、独立服务器、所有界面/战斗/骑乘长时间验收；未访问用户存档。

## 重做

工具只接受已核对的 Cobblemon Fabric 1.8.4+1.21.1，输入必须仍是原始完整 JAR，不要把已精简的原路径当完整源重复处理。原始哈希不符、输出目录已存在或资源签名异常都会拒绝。

```bash
python -X utf8 tools/split_cobblemon_resources.py prepare "完整原件.jar" "新的输出目录" --expected-sha256 77c4866b2e4c38afb488ed09b62ffd2729ce291dc793fca9c055c087f0b03978
python -X utf8 tools/split_cobblemon_resources.py apply "新的输出目录/split-manifest.json"
```

`prepare` 不改输入；`apply` 才执行备份与替换。更新 Cobblemon 版本时先重新核对强制资源入口和兜底清单。

上述拆分工具默认保留全部语言；当前三语版额外做了语言后处理。重做时还需按 `zh_cn`、`zh_tw`、`en_us` 筛选，不能把默认拆分产物误当三语版。
