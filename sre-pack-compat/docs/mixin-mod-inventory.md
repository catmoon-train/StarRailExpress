# SRE4.3 逐模组 Mixin 声明清单（2026-11-07）

> 本表为模型候选索引加入前的快照。后续仅兼容包新增 1 个客户端声明（42 → 43），其余 79 个模组未变；见 [模型索引实测](startup-model-index.md)。

## 统计口径

来自实际完整实例的 80 个顶层 JAR，保留全部模组。读取各 JAR 的 `fabric.mod.json`、引用的 Mixin JSON 和内嵌 JAR；不根据文件名推断版本。

- 本体列仅计算当前顶层模组自己的声明，不把内嵌 Fabric API、MixinSquared 等算进本体。common 表示 JSON 的 `mixins` 列表。
- 内嵌元数据是物理副本数量，不是 Fabric 最终选中数量，不能跨行相加当作运行时模组数。整包物理共有 289 份元数据，含重复依赖；实际日志仍为 **198 个运行时 Mod ID**。
- 按实际 ID/版本去重，可匹配 194 份模组元数据，共 188 个配置声明、981 个 common 和 1486 个 client 声明；其余四项为 Fabric Loader、Java、Minecraft 和加载器提供的 MixinExtras。这仍不是实际应用数量。
- 数字包括环境/插件可能拒绝的可选声明；不调用插件 `getMixins()` 来猜测动态添加项，不计 server 列表。相同插件在多个配置出现时分别计数。
- **声明数不是 CPU 时间，也不是删除该模组可节约的时间。** `0/0` 不表示零成本：例如 Fabric API、Kotlin、动态光源的重要代码可能来自内嵌项或入口。IMBlocker 等还注册了插件，插件动态项不在本表。

## 当前优化判断

SRE 本体 7 个配置、469 个 common/client 声明，包括 Noelle's Roles 的 275 项、主配置的 145 项及其他集成功能。现有 SRE/NR 插件主要是轻量可选模组条件判断，不能把其包含式 Mixin 调用栈当作插件自身的独占 CPU。

本轮确认可处理的是 **SRE 的晚期取消路径**：Wathe 54、Ratatouille 14（不含白名单 accessor）、Sodium Extra 11，共 79 个声明原来在准备后才由 MixinSquared 取消。从中只将已核对的普通、未引用候选提前处理；运行时结构检查后实际为 **41 个**，保留其余声明及原取消器。见 [实现及完整实例实测](startup-mixin-pruning.md)。

**本轮 A/B 未证明整体启动收益，提前过滤最终默认关闭，仅供显式开启实验。** 不能把 41 个声明的减少描述为已确认的秒级提速。

其他模组本轮没有仅因声明数多就关闭、删除或并行执行补丁：

- Iris、Sodium、ModernFix 等保留已有插件和条件选择，避免破坏光影、渲染及状态构建优化。
- Handcrafted、Moonlight、Supplementaries 的历史热点含注册/状态/资源工作；此前状态组合索引与邻接布局优化继续保留，不能把这些成本误标为其 Mixin 插件耗时。
- DashLoader 保留资源缓存正确性、失效规则和资源包顺序，不靠省略资源准备得到空菜单。
- 其他条目没有足以支持独立修改的新证据，故保持原样；本轮不是“80 个模组都已提速”的结论。

历史入口计时及其包含式归因边界见根项目 `docs/performance-mod-load-times-20261106.md`，不是本轮对照成绩。

## 80 个顶层模组

| Mod ID | 本体 common/client | 本体配置 | 本体插件 | 内嵌元数据份数 |
| --- | ---: | ---: | ---: | ---: |
| watheextraitems | 0/0 | 1 | 0 | 2 |
| reeses-sodium-options | 0/3 | 1 | 0 | 0 |
| stfu | 0/41 | 1 | 1 | 0 |
| gpumemleakfix | 0/2 | 1 | 0 | 0 |
| dsurround | 0/28 | 2 | 1 | 0 |
| replaymod | 0/9 | 2 | 1 | 6 |
| exposure | 15/23 | 2 | 1 | 0 |
| notenoughanimations | 0/13 | 1 | 0 | 2 |
| krypton | 9/0 | 1 | 1 | 1 |
| spark | 3/3 | 1 | 1 | 1 |
| sound_physics_remastered | 2/9 | 1 | 0 | 0 |
| modernui | 0/37 | 2 | 2 | 2 |
| modernfix | 51/54 | 2 | 2 | 0 |
| dashloader | 48/7 | 1 | 1 | 3 |
| nochatreports | 7/22 | 2 | 0 | 0 |
| handcrafted | 1/1 | 2 | 0 | 0 |
| presencefootsteps | 0/9 | 1 | 0 | 1 |
| imblocker | 0/0 | 1 | 1 | 0 |
| eatinganimationid | 0/1 | 1 | 0 | 0 |
| sodium-extra | 0/43 | 1 | 1 | 0 |
| sodium | 0/90 | 2 | 1 | 6 |
| supplementaries | 72/42 | 2 | 2 | 2 |
| amendments | 50/15 | 2 | 2 | 0 |
| architectury | 45/17 | 2 | 1 | 0 |
| auditory | 19/0 | 1 | 0 | 2 |
| charta | 12/3 | 1 | 0 | 1 |
| chloride | 0/39 | 1 | 1 | 1 |
| cloth-config | 0/0 | 0 | 0 | 1 |
| coroutil | 0/0 | 1 | 0 | 52 |
| cullify | 6/0 | 1 | 1 | 0 |
| exkeys | 0/7 | 1 | 0 | 0 |
| exposure_polaroid | 0/1 | 2 | 1 | 0 |
| fabric-api | 0/0 | 0 | 0 | 48 |
| fabric-language-kotlin | 0/0 | 0 | 0 | 13 |
| fastpaintings | 3/1 | 2 | 0 | 0 |
| firstperson | 0/19 | 1 | 0 | 2 |
| forgeconfigapiport | 1/0 | 2 | 0 | 2 |
| framepacer | 0/1 | 1 | 0 | 0 |
| fzzy_config | 0/0 | 0 | 0 | 3 |
| geckolib | 3/4 | 1 | 0 | 0 |
| gnetum | 0/30 | 2 | 0 | 0 |
| hats | 1/3 | 1 | 0 | 0 |
| immediatelyfast | 0/37 | 2 | 2 | 1 |
| invmove | 0/7 | 2 | 1 | 0 |
| iris | 0/152 | 9 | 6 | 5 |
| ixeris | 0/11 | 2 | 2 | 0 |
| keeptheresourcepack | 0/4 | 1 | 0 | 0 |
| keyoverlay | 0/1 | 1 | 0 | 0 |
| lambdynlights | 0/0 | 0 | 0 | 11 |
| languagereload | 0/20 | 1 | 0 | 0 |
| lightspeed | 11/10 | 1 | 0 | 0 |
| skin-unlocker | 3/0 | 1 | 0 | 0 |
| minopp | 0/3 | 2 | 0 | 0 |
| modmenu | 0/3 | 1 | 0 | 6 |
| moonlight | 50/35 | 2 | 1 | 1 |
| multi-key-bindings | 0/18 | 2 | 1 | 0 |
| betterblockentities | 34/0 | 2 | 0 | 0 |
| packetfixer | 20/0 | 1 | 1 | 0 |
| particle_core | 0/22 | 1 | 1 | 1 |
| placeholder-api | 0/0 | 0 | 0 | 0 |
| quick-pack | 4/5 | 1 | 0 | 0 |
| ratatouille | 5/10 | 1 | 0 | 1 |
| resourcefullib | 3/3 | 1 | 0 | 2 |
| smoothmenu | 0/1 | 1 | 0 | 0 |
| sre_resource | 1/2 | 1 | 0 | 0 |
| sre_pack_compat | 0/42 | 6 | 5 | 1 |
| sre-watheextended | 0/0 | 0 | 0 | 0 |
| sre-wathextras | 0/1 | 1 | 0 | 0 |
| starrailexpress | 205/264 | 7 | 3 | 4 |
| starrail-express-item-skin-mod | 13/8 | 1 | 0 | 0 |
| talkbubbles | 0/4 | 1 | 0 | 0 |
| talkingheads | 0/8 | 1 | 0 | 0 |
| tekst | 0/3 | 1 | 0 | 0 |
| virtual_proxy_manger | 1/8 | 2 | 1 | 1 |
| voicechat | 1/10 | 1 | 0 | 7 |
| wathe | 14/40 | 1 | 1 | 6 |
| watut | 4/13 | 1 | 0 | 3 |
| weatherrefind | 1/4 | 1 | 0 | 0 |
| wifi-card-games | 0/0 | 0 | 0 | 0 |
| yet_another_config_lib_v3 | 0/7 | 2 | 0 | 8 |

原始逐配置清单、类摘要、未引用候选检查和运行时 ID 去重记录保存在本会话 scratch 的 `startup30/mixin-*.json`；不含 BAT 凭证。
