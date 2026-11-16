# Cobblemon 运行库原生替换可行性分析

## 范围与证据边界
- 本文整理主 agent 已核对的第一手源码、发行包扫描与标准 JDK 探测结果，不是已批准的改造方案。
- “原生”严格指 Java / JDK / Minecraft API；换成另一第三方引擎、独立进程或外部服务不算原生替换。
- 源码根为 `G:/download/cobblemon-main (1)/cobblemon-main`，下文代码路径均相对此根。
- `gradle.properties` 标识 **1.8.5 snapshot、Kotlin 2.2.20、Minecraft 1.21.1**；不是此前处理的 1.8.4 发行源码。
- 为便于阅读，下文 `K/` 代表 `common/src/main/kotlin/com/cobblemon/mod/common/`；路径后的数字为源码行号。
- 本轮仅静态分析及标准 JDK 探测，未修改外部源码或二进制，未运行构建，未制作或验证瘦运行库版本，未承诺全部兼容。

## 结论速览
| 对象 | 原生替换或裁剪可行性 | 建议 |
| --- | --- | --- |
| GraalJS / Showdown | 无标准 JDK 即插即用替代；涉及规则引擎及注册数据 | 保留，或单独立项重写完整 Showdown 兼容层 |
| MongoDB 驱动 | 非 Mongo 存档可隔离依赖；不能原生等价保留全部 Mongo 能力 | 优先评估可选后端 / 精简 profile |
| Kotlin 反射 | 部分真实反射可迁移；KClass 引用不等于依赖完整反射库 | 小范围逐步替换，不直接删 JAR |
| Kotlin 其他模块 | 未见部分模块直接引用，但依赖闭包尚未证明 | 建立最小模块 profile 后验证 |
| ICU | Graal 间接引用已确认；仅客户端存在复用候选 | 验证 MC 版本兼容与服务端打包，不能直接删除 |

## GraalJS：不是关掉战斗就能移除
- `gradle/libs.versions.toml` 声明 Graal 22.3.0、ICU 71.1、Mongo 4.10.2。
- `fabric/build.gradle.kts:66-77` 内嵌 Graal、Mongo 与 Fabric Language Kotlin（FLK）；`:86` 将 ICU 加入 `minecraftServerLibraries`。
- `K/battles/runner/ShowdownService.kt:40` 的 lazy 实现固定选择 `GraalShowdownService`，并非可配置的通用引擎发现入口。
- `K/battles/runner/graal/GraalShowdownService.kt:71` 使用 `Context.newBuilder("js")`；`:99-104` 配置 CommonJS 及 buffer / crypto / path 替换。
- 同文件 `:124` 执行 `showdown/index.js`，`:142` 提供 `getRegistryData`；依赖不止于战斗线程本身。
- `K/api/moves/Moves.kt:57-69`、`K/api/abilities/Abilities.kt:46-58`、`K/api/item/HeldItems.kt:35-47` 初始化时读写 JS 注册数据。
- 因此仅禁用战斗线程不能切断 Graal 初始化路径；原生重写还要覆盖招式、特性、携带物注册与 Showdown 规则语义。
- 主 agent 在本机 JDK 21 用 `ScriptEngineManager` 实测脚本引擎工厂数为 **0**；该 JDK 没有内置可平替的 JS 引擎。
- Java 的脚本 API 是接口，不是 JS 实现；不能以“JDK 有脚本接口”为依据直接移除 Graal。

## Socket 路径：搬走运行时，不等于原生替换
- `K/battles/runner/socket/SocketShowdownService.kt:30-32` 明确定位为调试用途，且没有故障处理。
- 同文件 `:49-52` 使用 socket 与 ASCII 编码；`:78-82` 的读取未处理短读和 EOF，不能视为可靠的完整报文读取。
- 它仍需要外部 Showdown JS 服务，只是将运行时移出当前 JVM；不能称为零依赖或可直接上线的替代品。
- 若另行立项，还需处理连接生命周期、报文边界、异常恢复、编码和服务部署，并校验注册数据与战斗协议一致性。

## MongoDB：优先隔离可选后端
- `K/config/CobblemonConfig.kt:77` 的 `storageFormat` 默认是 `nbt`，并不代表所有用户都在使用 Mongo。
- `K/Cobblemon.kt:375-441` 对 nbt / json 使用既有 NBT / JSON 适配器，仅 mongodb 分支使用驱动；但核心函数仍直接引用 Mongo 类型。
- `common/src/main/java/com/cobblemon/mod/common/duck/CameraDuck.java` 还误用 `com.mongodb.lang.Nullable`，需解除这种非存储耦合。
- 不使用 Mongo 的部署可考虑把后端隔离为可选模块或精简 profile；应先清除核心签名、导入与类加载路径中的直接耦合。
- 不能只删除驱动后声称 nbt 分支必然能正常启动；必须验证缺少 Mongo 类时客户端、独立服务端及相关入口的类加载。
- 不得自动把已有 Mongo 存档切换为 nbt / json；迁移需独立设计、备份及一致性校验。
- Java 标准库不是 MongoDB 协议与驱动功能的等价实现；继续支持 Mongo 时仍须保留可靠实现。

## Kotlin：区分轻量类型引用与真实反射
- 主 agent 限定 `common/fabric/neoforge` 的非 test `src` 静态扫描，共 **2870 个 .kt、143 个 .java**；不能扩大解释为所有依赖的扫描。
- `kotlin.reflect` 引用出现在 28 个文件，其中含仅使用 `KClass` 的轻用法，不能全算成完整 kotlin-reflect 的必要调用。
- `K/pokemon/SpeciesAdditions.kt:79-104` 使用 `declaredMemberProperties`、`isAccessible`、`returnType.javaType`，属于真实反射迁移点。
- `K/config/CobblemonConfig.kt:419-421` 使用 `memberProperties` 及可变属性；配置 GUI 也使用 `KMutableProperty`，不能遗漏界面路径。
- 可逐步使用 Java `Class` / `Field` / `Method` 或显式适配器替换真实反射，但 Kotlin 属性不总与 Java 字段一一对应。
- 必须保留属性、注解、默认参数、泛型解释，以及数据包和附属 mod 的 ABI 兼容；删掉 reflect JAR 本身不算完成迁移。
- 源码扫描未发现 `kotlinx.coroutines` / `kotlinx.serialization` 直接引用，不代表 FLK、其他运行库或附属 mod 没有依赖。
- 1.8.4 外层 `.class` 扫描也未见 coroutines / serialization / datetime / io / atomicfu 及 ICU 前缀；这不是内嵌库或传递依赖不存在的证明。
- 可建立最小 Kotlin 模块 profile，但应保留 stdlib 与必要 reflect，并检查模组元数据、内嵌依赖闭包和附属 mod 的实际需求。
- MoLang 由 `fabric/build.gradle.kts:79-83` 的 `bundle(libs.molang)` 直接打包，不在内嵌 JAR 列表中；源码静态扫描有 277 个文件引用其包。它并非 Java/JDK/MC 的同名现成功能，本轮未评估语言解释器替换，不能据外层 JAR 清单漏算或认定可删。

## ICU：间接依赖已确认，客户端去重仍需验证
- 当前 MC 1.21.1 的 `mojang_minecraft_info.json` 客户端库包含 `com.ibm.icu:icu4j:73.2`；但官方服务器 JAR 的 `META-INF/libraries.list` 没有 ICU，且 `fabric/build.gradle.kts:86` 显式添加服务端 ICU 71.1。
- 对当前 1.8.4 JAR 内嵌运行库作只读 `.class` 字节扫描：`js-22.3.0.jar` 有 44 个类包含 ICU 类引用，例子包括 `JSRealm`、`JSDate`、`JSNumberFormat`；`regex-22.3.0.jar` 的 `PythonRegexParser` 也包含引用。外层 Cobblemon 没有直接 import 不能证明 ICU 没有作用。
- 上述结果证明存在静态传递引用，不等于每条路径都会执行，也没有证明不同 ICU 版本二进制兼容。
- 客户端专用构建可尝试复用 MC 自带 ICU，但须验证类加载可见性、内部 API、Unicode，以及 JS 的 Intl / regex / Date 行为；独立服务端必须提供有效实现。
- 这不是用 `java.text` 等少量 JDK 接口即可无损替换的工具库；本轮没有进行 ICU 替换运行测试，不能判定可安全删除。

## 体积证据：只代表旧发行包内嵌 payload
以下为 **1.8.4 发行 JAR 内嵌 JAR 的原始 payload**，单位 MiB；不是 1.8.5 snapshot 构建产物测量。
| 内嵌项目 | 原始 payload |
| --- | ---: |
| Graal js / truffle / regex / graalSDK | 22.44 / 14.43 / 2.97 / 0.69 |
| ICU | 13.40 |
| FLK 整包 | 7.13 |
| Mongo 两个内嵌 JAR 合计 | 1.67 |
| FLK 内部 stdlib / reflect | 1.70 / 3.23 |
| FLK 内部协程 / datetime | 1.42 / 0.67 |
- FLK 内还含 serialization / json / cbor 等模块；内部子项已包含在 FLK 整包中，不能再次相加。
- payload 不等于实际堆保留量，也不等于删库后的成品差值；压缩、打包与共享依赖都会影响最终结果。
- 本轮未测启动耗时、GC、运行内存或战斗吞吐，不宣称任何性能收益已经得到验证。

## 建议顺序与后续验收边界
1. 优先评估 Mongo 可选化、小范围真实反射迁移，以及 Kotlin 未用模块的依赖闭包验证；每项均需单独确认后实施。
2. Graal 默认保留；若要求纯 Java / JDK / MC API，则单独立项实现完整 Showdown 兼容，不能以 socket 或禁用线程代替。
3. 将来获批后，分别验证客户端与独立服务端的启动、注册、存档、战斗、配置 GUI、数据包及附属 mod 兼容，再测实际收益。
4. 1.8.4 资源外置的历史验证不能代替运行库裁剪验证；本轮只交付分析文档，不修改源码、JAR 或构建文件，也不提交 Git。
