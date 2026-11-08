# SRE Pack Compat

独立客户端 Mod，MC 1.21.1 / Java 21，Mod ID `sre_pack_compat`，源码位于当前项目的 `sre-pack-compat/`。

## 范围

- 从 SRE 核心物理迁入全部 19 个 DashLoader Mixin、配套缓存／指纹／JSON 回退工具和测试，包含 Supplementaries 模型缓存排除及 WATUT 辅助粒子图集修复。
- 迁入 HttpTexture 皮肤上传调度修复、方块状态警告汇总。
- 新增 Particle Core 0.3.3 + Dynamic Surroundings 0.4.8 的 WaterFoam 同步 tick 兼容。
- 移植 Su's Instant Swap 2.0.0 Fabric 1.21.1（探囊取物）的长按交换与配置功能。
- 内置整合包配置，在 preLaunch 覆盖同名实例配置；Chloride 1.8.1 设置按当前整包值锁定。
- Sodium/Extra 本体选项限制、Iris、Lightspeed、下载缓存、活塞和核心玩法仍在原模块；新增的 Chloride 设置锁定独立位于兼容包。
- 内置自定义/实验性世界提示跳过：客户端加载时仅将该警告检查读取到的生命周期视为 stable，创建时跳过对应确认。存档数据不改写，旧自定义世界的备份提示、版本兼容和数据包错误等其它检查保持原样。

迁移代码保留原 Java 包名以维持既有映射和工具接口，但不再编译进核心 JAR。兼容包不依赖 SRE 核心进行编译：列车加载层为可选字符串目标，自定义模型 ID 为可选反射接入。独立 ZIP URI 工具使用新类名，避免与核心仍用于 Lightspeed 的类重复。

## 构建

在仓库根目录运行：

```bash
./gradlew :test :remapJar :sre-pack-compat:test :sre-pack-compat:remapJar --offline --console=plain
```

产物：

- 新核心：`build/libs/starrailexpress.jar`
- 独立补丁：`sre-pack-compat/build/libs/sre-pack-compat-1.0.0.jar`

开发运行时由根项目的 `runtimeOnly` 项目依赖加载兼容包，不将它嵌入核心。子项目复用已有 Loom、映射、Fabric 和 MixinSquared 版本，无新增外部功能依赖。也可只构建 `:sre-pack-compat:remapJar`；根 `settings.gradle` 提供统一构建入口。

开发启动可在根目录使用 `./gradlew runClient`（也可明确使用 `:runClient`）。兼容包移除了 Loom 独立客户端配置，对应 `:sre-pack-compat:runClient` 禁用且不再准备独立启动依赖；裸任务选择时该任务可能显示为 `SKIPPED`，不会启动第二个游戏进程。主客户端仍通过上述 runtimeOnly 依赖加载兼容包。

## 安装／升级

**这次拆分必须同时替换核心并新增兼容 JAR。** 客户端 `mods/` 中只保留一份新核心和一份 `sre-pack-compat-1.0.0.jar`。不安装到专用服务器。

旧核心仍内置同名补丁，不能只把本包加到旧核心旁边。启动检查会检测旧 `starrailexpress.mixins.json` 的重复注册并明确报错，避免双注入。主模组版本号没有擅自增加，不能仅凭版本名判断是否已换成拆分版。

DashLoader、Moonlight、Particle Core 和 Dynamic Surroundings 均是可选目标：缺失时跳过对应补丁；WaterFoam 修复仅在 Particle Core 与 Dynamic Surroundings 同时存在时启用。未安装 SRE 时仍可独立启动，列车加载层接入自动跳过。

正常更新源需同时发布这两个产物和对应哈希，否则自动更新可能重新装回旧核心。不要通过关闭鉴权、安全检查或永久关闭更新来掩盖发布不一致。

## Particle Core 修复

两份报告均为 `WaterFoam` 的 `Ticking Particle` 空指针；报告没有原始异常的内部栈，因此无法只凭它们确定 NPE 的具体语句。实际代码可确认 WaterFoam 的静态水面 HashMap、可变探针坐标和世界读取不具备并行 tick 安全性，而 Particle Core 原本只在部分已知线程异常后才回退。

本补丁在 ParticleEngine 构造完成、首次 tick 前，将 WaterFoam 的类身份登记到 Particle Core 自带 `unsafeParticles` 集合。工作线程会直接返回同步回退结果，主线程由 Particle Core 原流程执行；其他类不改动。不吞 NPE、不丢弃水面泡沫，不每帧扫描／反射，也不更改全局异步或粒子显示配置。用户已禁用该异步优化时，缺少对应字段可安全跳过。

## 已完成验证

- 统一测试及正式打包成功：核心 689 项、兼容包 61 项测试，0 失败／错误／跳过。
- 无 SRE 核心、无 DashLoader、无 Particle Core 的隔离客户端正常启动、退出。
- 装有实际 Particle Core / Dynamic Surroundings 的独立隔离客户端：对真实 `tickParticleSafe` 分派方法执行 4096 次测试，2048 次 WaterFoam 均返回同步回退、没有工作线程 tick；2048 次普通粒子仍执行，异步总开关保持 true。该测试验证真实注入／线程分派，不声称重现了原地图两小时后的具体 NPE。
- 新核心＋独立兼容包＋DashLoader／Supplementaries／Moonlight 的隔离客户端完成冷→热缓存→强制刷新；黑板 8 个状态的几何、绘图、GPU 像素共 10,769 项检查通过。热缓存读取前确认 Supplementaries 缓存模型 0、其他缓存模型 61,905、回退状态 18,557。
- 正式产物顶层重复类 0，迁移补丁仅在独立 JAR 中；临时测试器不在正式产物。

本轮没有重现原报告的多人地图长期负载，修复针对已确认的非线程安全执行路径。后续若仍出现同类 NPE，应保留新的完整堆栈再定位，不添加“忽略所有粒子异常”的兜底。

## 探囊取物（Instant Swap）

- 沿用原版背包键（默认 E）：短按正常打开背包；按住至少 200ms，鼠标指向槽位后松开，将其与当前快捷栏槽位交换并关闭。阈值可调 50–1000ms。
- 支持普通容器、原版生存/冒险背包、创造物品栏（列表取一个，原手持物先移到空槽）。满背包拒绝覆盖原手持物；创造背包、装备与副手交给原版槽位处理。
- 配置含总开关、长按阈值、音效、鼠标重定位、GUI 交换、空位交换、调试日志，默认值与提供的 JAR 相同。
- Mod Menu → SRE Pack Compat → 配置，或客户端命令 `/sreinstantswap` 打开设置。沿用 `config/susinstantswap.json`，已有配置无需迁移。
- 「界面中更换」默认未绑定。开启 GUI 交换后，不单独绑定就复用背包键；也可在控制设置中绑定独立按键。输入框有焦点时不抢占文字输入。
- 支持 SRE 的 `LimitedInventoryScreen` / `LimitedHandledScreen`：长按背包键松开交换，或开启 GUI 交换后直接按交换键；仅命中实际显示的快捷栏槽位。复用原界面的缩放、偏移和点击处理，同时检查交换双方的禁止取物谓词。不移动隐藏背包、装备或商店物品，回声面板隐藏原槽位时不参与交换，搜索框输入保持原行为。不主动打开原版背包，不绕过核心按键和服务端槽位限制；核心未安装时此桥接自动跳过。
- 当前元数据不再声明与 `susinstantswap` 的硬冲突，但本包仍内置交换功能；同时安装原 `Sus_InstantSwap` 可能重复处理输入，整合包应只保留一套交换实现。版本号未修改。

来源为用户提供的 `Sus_InstantSwap-2.0.0-Fabric1.21.1.jar`；原作者 Su_Crispy，项目 https://github.com/SuCrispy/Sus-InstantSwap ，原许可证 LGPL-3.0。输入和槽位处理已适配重写；上游许可文本随 JAR 的 `licenses/` 发布。翻译键继续使用 `susinstantswap` 命名空间以兼容已有按键配置。Mod Menu 仅复用根项目已有版本作为可选编译依赖，不新增运行时硬依赖。

## preLaunch 启动优化

新增普通 `preLaunch` 入口和本地 JAR 原始类字节有界缓存，默认 32 MiB；不使用 agent，不改启动参数，不缓存转换后类，不跳过或并行应用 Mixin。原入口补丁优先，来源选择仍由原加载器决定，类路径改变时失效，初始加载交接后释放并恢复入口表。

配置：`config/sre-pack-compat-startup.properties`，`enabled=false` 可完全关闭此项。仅对已核对的 Fabric Loader 0.19.3 / 0.20.1 生产客户端启用，未知实现自动回到原流程。更新 JAR 不覆盖已有配置。

早期兼容包 92 项测试、两版 Loader 的生产态隔离客户端及关闭回退验证通过；隔离开关对照平均差约 0.15 秒，未证明原始类字节缓存有明显净收益。后续在完整 80 模组实例加入 ResourcefulLib 状态查询索引，原样运行用户 BAT：热启动 35.430 秒（JFR）／34.659 秒（正常），仍未达到 30 秒。完整测量、归因边界及本轮 137 项测试见 [完整实例启动实测](docs/startup-full-pack.md)；原始缓存原理与历史记录见 [启动缓存说明](docs/startup-prelaunch.md)。

后续 10 秒目标尝试：增加状态邻接表构建期整数索引及 ArrayTable 不可变布局复用，最终兼容包 153 项测试通过。用户选择保留另一款游戏并行运行，系统 CPU 约 83% 时，新包热启动 77.355 秒、对照 82.898 秒；仍未达到 10 秒，也不能与凌晨低负载结果混比。当轮部署、回退与测量边界见 [10 秒启动尝试](docs/startup-10s-attempt.md)。

逐模组/Mixin 准备轮次：完成 [80 模组声明盘点](docs/mixin-mod-inventory.md)，实现仅提前处理 SRE 已取消的 41 个已核对声明。173 项测试通过；同产物四轮热启动的关闭/启用均值为 67.248/67.345 秒，没有稳定整体收益，因此该实验最终默认关闭，原有优化保留。当轮部署、开关、完整数据和边界见 [Mixin 准备实验](docs/startup-mixin-pruning.md)。

最新模型候选索引：把 SRE 每个模型全遍历基准类型改为按命名空间/路径选候选，原皮肤包装和烘焙继续执行，仅对已核对核心启用。191 项测试及完整实例冷建、四轮热启动通过；启用两轮为 40.132/39.522 秒，但对照负载不同，不能宣称稳定节省数秒。索引保留默认启用，临时开关已恢复，微基准、完整数据与回退见 [模型索引实测](docs/startup-model-index.md)。

## F3 FPS 调试字符串按需格式化

用户提供的 STFU 2.9.1 没有此优化；BadOptimizations-1.21 源码的 Config.java 明确记录 config v3 已删除 FPS string optimization，因此本包针对实际 MC 1.21.1 调用点独立实现，不是复制该 ZIP 中不存在的补丁。

`DebugFpsStringMixin` 仅包装 `Minecraft.runTick` 中生成 FPS 调试行的 `String.format(Locale, String, Object[])`：F3 关闭时复用旧字符串，显示时执行原调用，最迟在下次原版每秒更新时刷新。FPS 数值、帧计数、计时器和分析器逻辑均保持原样；不停止所有 F3 相关计算，参数准备工作也仍保留。

读取 `Minecraft.getFps()` 的 FPS HUD 不受影响；直接读取 `Minecraft.fpsString` 的模组在 F3 关闭时会得到旧调试行，不能保证所有显示 FPS 的模组无差别兼容。无新增依赖、按键或版本号变更；未实测 FPS 提升。

## 内置配置与 Chloride 锁定

配置值来源：`src/main/resources/config/chloride-client.toml`，使用用户确认的 SRE4.3 最新设置（床/箱子静态模型开启，雨滴粒子关闭）。每次 preLaunch 递归将资源 `config/` 下文件覆盖到实例同名路径，内容相同不重写，不删除其他配置。

Chloride 的 68 项设置同时受运行策略、Sodium 控件/binding/默认值及独立粒子列表限制。修改内置 TOML、重新打包并重启即可更新；无额外硬依赖或版本号修改。详见 [配置覆盖与锁定说明](docs/bundled-config-chloride.md)。

## Sodium 设置入口隐藏

隐藏 BBE、Chloride、Gnetum、LambDynamicLights、Reese's 及 Cullify 自身设置页，Sodium Extra 只隐藏动画、细节、显示，保留“粒子效果”和“其他”；保留 Sodium 自身及 Iris 光影入口。仅过滤 `Config.getModOptions()` 的展示副本，不删内部选项注册、依赖或覆盖，不关闭模组及其优化。粒子选项保持开放，Chloride 锁定、内置配置及 preLaunch 覆盖保持不变。

配套核心同时开放天气、树叶、粒子、平滑光照、暗角及全部声音分类，实体渲染距离固定 100%。这部分在核心而非兼容包，更新时需要同时替换两个 JAR。

动态光源同时匹配 `lambdynlights` 与当前整合 JAR 中的 `lambdynlights_runtime` 配置组，隐藏残留入口；运行功能不变。配套核心将“禁止着色器”（`sodium-extra:prevent_shaders`）固定关闭并隐藏，不允许通过配置改开。

## FramePacer / Skin Unlocker / GPU 资源回收

内置混合等待帧节奏、第三方皮肤纹理来源/签名解锁，以及遗弃 RenderTarget 的纹理/FBO 回收。GPU 方案使用引用队列取代 finalizer，正常销毁与 resize 会撤销旧编号，防止重复删除；每 tick 最多清理 20 组，GL 操作仅在渲染线程进行。皮肤仅放宽 `textures` 校验，不改登录认证或其他属性。

若仍安装 `framepacer`、`skin-unlocker`、`gpumemleakfix`，本包自动关闭对应内置实现，由原模组负责，避免重复处理；未安装时使用内置实现。无需为本次更新删除原模组。来源、许可、行为及边界见 [运行兼容功能说明](docs/runtime-features.md)。

## 自动清理过时 Mod

编辑 `src/main/resources/obsolete-mods.txt`，每行一个精确的 `.jar` 文件名（支持空行及 `#` 注释），重新打包发布。默认空名单。preLaunch 检测当前实例 `mods/` 下的命中文件；没有命中就正常启动。

命中时先启动独立 Java 清理窗口，显示“正在删除过时的 Mod，请重启游戏”，确认窗口就绪后退出游戏。清理器等游戏进程退出再删除名单中实际命中的文件，每文件最多重试 15 秒；完成或失败会显示结果。拒绝路径、通配符、符号链接、目录和兼容包自身，不递归删除、不匹配相似名称。临时工作目录保留 `worker.log` / `result.txt` 便于排查。

此功能在 Loader 的 preLaunch 阶段执行，**不能处理在此之前就导致 Loader 硬依赖/`breaks` 检查失败的旧 Mod**；这类仍需手动或启动器预先移除。没有绕过 Loader、鉴权或自动更新校验。名单之外的文件不删除；启动器若重新下载旧 Mod，应同步修正正常整合包发布清单。

## Simple Voice Chat 自动重连

按用户提供的 Fabric 1.21.1 / Simple Voice Chat 2.6.27 核对连接接口。仅在安装 `voicechat` 时启用客户端 Mixin，不内嵌语音模组、不改变核心的语音依赖版本。

本次服务器会话的语音握手曾成功后，检测到断线按 **5 → 10 → 20 → 30 → 30… 秒的相邻尝试间隔**自动重试；持续失败时约为断线后第 5、15、35、65、95… 秒。计时从客户端检测到断线开始，不替换上游心跳超时检测。每次先清理残留连接，再请求新密钥并由上游完成认证。成功恢复后重置到 5 秒档；从未连通不触发，退出/换服清空状态，禁用语音时暂停重试。不会取消用户的静音/禁用设置，不新增按键或后台定时线程。

针对性回归测试：`./gradlew :sre-pack-compat:test --tests '*VoicechatReconnectBackoffTest' :sre-pack-compat:remapJar --console=plain`。真实服务器 UDP 断线恢复需另行联机验证。

## 死亡语音群组 HUD

仅替换服务端同步的隐藏群组 `Train Spectators`：左侧竖排显示正在说话的成员头像，停止说话后随 Voice Chat 自带说话状态缓存消失。按住左/右 Alt 时在头像右侧显示发言者名字，松开隐藏名字；不会展开未发言成员。沿用 Voice Chat 的 HUD 总开关、头像缩放及显示自己头像设置，普通群组不变。无需新增按键绑定，不改变语音路由或群组成员。
