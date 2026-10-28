# 回声托运站 · 分阶段实现进度

> **现行变更（后于下方历史记录）**：污染机制已整体下线，截流者改为「迟滞充能」（不扣进度）；
> 原污染加速/强污染/过载脉冲/净化器/稳定剂五张卡与净化稳定剂已替换为迟滞脉冲/强迟滞/截货/稳流阀/信标助推/信标超载。
> 详见 [22 污染机制（future）](22-pollution-future.md)。下方历史段落保留原始记录，不代表当前实现。

## 当前交付：阶段0–8规则、资源与HUD已接入；完整信号表现与实机验收继续推进

当前仍是**开发模式**，不是已经验收的首发玩法。普通服务器默认拒绝回声开局。37卡真实效果/独立图标、断线托管、重置后路线准入与逐局冻结规则已接入；规则包括76项基础数字及逐卡白名单/权重。公共目标HUD、本人引导/拒绝提示/KEY周期、独立工具贴图及宽细臂假影已接入。尚需补齐其余世界信号，并完成真实地图、多人、语音、GUI和性能验收；构建与纯测试不代替这些验收。

## 已实现

### 阶段0–1

- 固定6–12人人数档、无放回身份/契约分配、纯主胜负及个人契约结果判定。
- 唯一资源归属台账、期望位置校验、批量先验计划、终态防复活。台账支持同库存格两个实例以容纳包堆，类型/品质叠放校验由后续六格库存服务负责。
- 37个不可变卡牌定义、16个同系配方、品质桶加权无放回三选一；任一候选含KEY即声明一份配额预留计划。
- 单包绑定、引导、中断CD、候选超时默认选择和幂等结果纯状态机，已由阶段4接入真实槽位、KEY配额和选卡面板。
- 严格侧文件读取：真实路径边界、大小、字段/类型、重复字段、schema与整数溢出检查；配置不可变且只允许一次模板→实战平移。
- 独立GameMode、三身份、本人RoleData、一次性bootstrap、按世界实例隔离会话、SRE计时器START/END桥接；模式不重复扣阶段时间。
- 停局前结果冻结、全体UUID标准结果投影、开发报告原子保存、外部停止/卸载中止；结果不依赖已清理的职业数据。
- 公共AllowGameInitialize预检闸门、固定分钟参数拒绝。用户已允许最小公共接入；GameMode/SRERole基类不改。

### 阶段2

- 每人一个引导、目标独占、10tick租约、动作ID精确取消；固定点位移动/离距/LOS失效会取消，完成时重新校验容量和目标状态。
- 托运者普通晶体4秒采集、20秒晶簇恢复、两枚独立货槽、搬货-10%速度、每20秒仅向附近截流者发送方向提示。
- 一次提交一枚、默认1秒提交、三枚满料、自动充能、附近最多三名托运者加速。阶段7改为百万分之tick定点工作量，支持配置充能时长，避免逐tick整数截断。
- 截流者4秒污染、回退20个百分点、封锁20秒、每信标45秒共享冷却；稳定层/净化/暂停的状态接口已实现并单测，阶段5已接入实际卡牌。
- 三祭坛随机两把初始钥匙、每60秒确定性补给、每人一KEY、总量N+3上限；持钥不阻止新补给，拾取/掉落/消费均走台账。
- 钥匙同时开核心组入口15秒、内部免费单向出口、门关闭前安全移出占用者；撤离时核心入口保持开放。
- 全信标启动自动切入完整撤离窗、托运者停留撤离区2秒离场、已撤离UUID只计一次、达Q团队获胜；撤离先提交事实再判断超时。
- 截流者复用G键发动全队一次30秒过载，需12格及LOS；只关闭撤离门，主倒计时继续，不回滚已撤离者。
- 世界掉落复用原版可右键展示实体，不注册新依赖。实体头部物品仅作显示，不是合法模式资源；拾取必须通过台账，孤儿展示重载时注销。
- 晶体/钥匙地面寿命按模式tick回收；非权威展示意外消失后补建同一地面记录，不提前回收或刷新寿命。普通拾取和开门瞬时提交，祭坛取钥仍需1秒引导。
- 奖励按事实去重；阶段4已经替换临时PENDING引用和KEY固定槽0：使用真实六槽入包、满包掉落、固定实例待交付重试，品质不重掷。
- 最小反馈使用原版BossBar、actionbar、公开声音/粒子与三语文字。私人声纹当前是八向文字，不广播货主坐标；最终波纹、持续光柱、完整背包/HUD仍属后续表现层。
- 门体写前持久化恢复日志，记录每块原值及写入前/后允许状态；中止/结束恢复，重启尝试恢复，不覆盖外部编辑冲突。未解决记录会阻止对应地图开局。
- 预检扩展到交互点唯一/在游戏区域/非空气、出口安全、闭合门体为限定普通实心方块且无方块实体。真实步行连通性仍需后续路线验收。

### 阶段3：两次机会与战斗

- 专用截流器固定快捷栏0，不占六槽；服务器12tick蓄力、2.75格射线/墙体遮挡、一次一目标，充满挥空也冷却50tick；倒地者不补刀，普通攻击/其它武器不能绕过机会数。
- HEALTHY首次命中DOWNED，900模式tick流血；空手2.5格/LOS独占救援100tick，救起WOUNDED并保护120tick，主动资源动作提前解除保护；再次命中或强制死亡最终淘汰。
- 倒地保持冒险、仅允许观察；客户端禁键，服务器锚定安全位置并使用独立速度修饰；先处理流血截止，再处理本tick救援。
- 托运者G推挡：2.5格、25tick控制，成功才进入500tick自身冷却；控制结束后160tick抗性。狼普通环境致命送回出生点、控制60tick、同类复位保护200tick；管理员强制淘汰。
- 首次倒地只掉货物/KEY、保留包卡；最终淘汰才走标准死亡和语音隔离。统一纯胜负策略消费撤离/淘汰事实，不把受控或倒地误判为换职。
- 三语截流器名称/介绍、倒地/救援提示和技能键已补齐。

### 阶段4：六槽与真实开包

- EchoInventory六格不可变快照，同品质包每格最多两独立实例，卡/KEY一格一份，所有KEY个人最多一份；预留槽、实例锁和revision校验，台账批量提交也包含新产物。
- SAFE发开局包一次；奖励入真实六槽，满包落地；地面记录先于实体展示，达到展示上限或生成失败只等待补建，不重复生成实例或重掷品质。
- 真实30tick拆包：额外空格预留、首次绑定/缓存三候选、10秒取消CD、绑定包不可移动或丢弃、一人仅一未决绑定包；OFFERED消耗包、20秒超时默认0，选卡产物入原保留格。
- 任一候选含KEY即持有个人与全局一份配额，取消保留配额；选非KEY释放、选KEY同事务转为实物；暂停冻结引导/期限，倒地可解决已有offer。
- 37定义和16配方实际可抽/合，两个同名不同实例可以合成；满包无需第三格，较小输入槽产出，失败零移动。物流被动真实影响容量和搬货惩罚，离格处理超额货物。
- 卡/包地面展示、明确右键拾取、普通资源120秒回收；KEY迁入实际六槽，拾取/掉落/开门按真实槽号走同一事务。
- 新库存C2S有protocol/matchId/epoch/requestId/revision，服务器主线程意图队列、128回复去重、旧序号拒绝、写请求10次/秒/突发20、快照独立2次/秒。私有投影只同步本人，不包含未OFFERED候选。
- 新EchoInventoryScreen复用SreButton、原背包职业扩展和客户端命令/sre:echo_bag；固定六槽、两输入合成预览/确认、丢弃确认、绑定/保留状态、同屏三选一、等待与版本冲突反馈；未执行游戏内视觉验收。
- 补充三语资源放在assets/echo_freight/lang，键仍为echo.*，由Minecraft统一合并；37张卡名/描述、品质、系列、库存错误键完整。独立图标和卡牌代理属阶段5。
- EchoInventory.exchange提供原子最终布局（含满包一换一）；阶段6已在此之上接入真实提案/双确认和契约事实。

### 阶段5：效果与授权场景

- 新EchoEffectState按比赛tick维护静默/污染标记、机动/猎杀减速、冷却；动作取消不扣标记，成功动作才扣次数，60秒到期；同级机动拒绝，高级替换不叠乘，模式速度与货物惩罚合成后clamp。
- 静默采集器/套件接入普通采集和提交；公共数字不隐藏。污染加速/强污染接入2秒引导和40个百分点回退，成功后才清标记，稳定层/目标冷却仍由原信标状态管理。
- 稳定剂、净化器、净化稳定剂、过载脉冲、门撑、声纹钥匙、万能钥匙接入真实信标/门；万能钥匙20秒并只给本人扫描门内12格真狼2秒，空手旧开门入口也使用同一效果接口。
- 团队雷达/扫描、晶体嗅探/全图脉冲、猎杀标记/压迫接入授权服务；扫描使用时冻结目标集合，每tick撤销下线/倒地/离场目标，货物追踪失货撤权。扇区投影不带身份/坐标；轮廓仅本人RoleData同步，不给全局GLOWING。
- 库存信封新增EQUIP_CARD/USE_CARD及固定大小两点字段，复用epoch/revision/去重/限流；服务器验证合法实例、阵营、锁、阶段/状态和目标，效果失败不扣卡。
- 通用代理固定快捷栏1、不能丢弃，不保存卡ID；背包新增使用/装备，私有选卡同步、客户端目标射线、两点预选（未提交不扣卡）、授权线框/方向HUD；模型为原版地图开发占位，尚无实际MC视觉验证或37个独立图标。
- 暂停时仍执行服务器倒地/控制锚定；普通致命在暂停时拒绝；拆包跳跃在服务器取消引导，不仅依赖客户端禁键。
- EchoSceneState有界纯状态管理假人/静默假人/残影与假提交/污染/调度；每人两个诱饵、全场24个，所有场景最多64条；双点完整校验后整体创建，固定位置延迟40tick发第二声，暂停不重复发声。场景没有实体、碰撞或任务身份。
- 服务器校验地面支撑/容身空间/地图边界/范围/LOS；狼空手2.75格准星使用可拆除。假人每60tick仅向24格活人发采集声；假提交只向同局活人发声。静默假人仅前100tick/20格内干扰嗅探扇区，不改真实携货台账。
- 两种残影先校验场景容量与机动替换再整体提交；客户端直接画玩家皮肤模型，不往世界添加假玩家。假光柱/粒子在客户端按展示投影生成，不在服务端为形状循环发包。
- 阵营伪装/双面面具按比赛tick到期，高级替换不叠加；有效攻击/受击破除；双面面具只抑制一次普通钥匙脉冲。正常客户端名字按观察者投影友方色，隐藏真实角色/同伙/修饰符标签；狼真队友名字有私有标记，面具和队友标记都不画轮廓。既有全局职业ID同步边界未改变。
- 偷包钩爪只抽未绑定/未锁定且本人能容纳的包，以原子单向交换转移，按施术者-目标维持600tick冷却；通行破坏器在LOS/阵营/持钥验证后先取消目标交易、释放锁，再掉落真实KEY。
- 黑晶嗅探/拾荒磁场支持最近可拾黑晶点/本人黑晶掉落/黑市包方向租约，目标采走/捡走/失效立即撤销；磁场可收入LOS4格最近可容纳物资包，满包不销毁物资，无目标不扣卡。
- 交易提案/双向商约接入真实提案：60秒内1/2次，范围10/16格，提案有效60秒；成功发提案才扣标记次数，拒绝/取消不退款，既有提案冻结范围/期限。独立卡图标/完整HUD及实际游戏视觉验收仍待完成。

### 阶段6：交易、契约与黑晶

- 两阶段交易：站立同局、3格/双向LOS、400tick期限、提案即300tick发起CD，每人一笔；对方接受后各报0–2独立实例，允许赠送、拒绝双方空报价。
- 报价锁留在原库存，整批替换先验；报价或任一库存revision变化清双方确认，双方确认同一版本才原子交换最终布局。满包等量交换可成功，失败零移动；按tradeId精确取消，旧请求不解新锁。
- 每tick验证在线/站立/范围/LOS/期限；攻击、有效受击、控制、离场、钥匙干扰和结束取消交易。本人RoleData只投影双方报价，不泄对手整包/阵营。
- 背包交易面板支持附近目标、接受/拒绝、双实例包堆选择、报价/清空、版本/容量/KEY提示和双确认；投机客复用G准星提案，没有新按键。未做Minecraft界面验证。
- 四契约权威在会话服务：走私两枚独立黑晶、情报真实双向非空交易各一真阵营（事件/实例往返去重）、拾荒者三不同PackResolved后ARMED且正常终局在线健康/受创、钥匙贩子最后有效交易受让链的真实控制器消费。
- KEY转手/掉地/合成后来源立即失效；控制器成功且消费后才锁成功，同实例仅一次。48格内真狼收到40tick增强八向脉冲，不受面具或假人影响，不附目标坐标/UUID；拾荒者第三包起每300tick给32格真狼40tick微光。
- 黑晶点每点初始一枚、80tick采集、1200tick恢复；货槽最多两枚且共用容量/搬运减速，指定投放点40tick每枚；黑晶不用于信标、普通货主嗅探或六槽交易。原主独占拾取，600tick落地回源。
- 即时成功不可逆且不抢停主局；指定brokerExit站定60tick并显式确认后永久RETIRED，掉资源、旁观/语音隔离、不计Q；拾荒者拒绝提前退场。私人契约面板/HUD和三语说明已接入，终局快照使用契约真实结果。
- 断线600tick一次宽限、离线offer/流血推进及新epoch恢复已完成，详见下方断线托管补记；不再因普通断线开发中止。

## 当前开发入口

隔离测试服启动参数加 `-Dsre.echo.development=true`。仍需6–12名准备玩家、已搭建的Areas地图，以及 `<world>/echo_freight/maps/<mapId>.json`；示例fixture仅是坐标配置，不会自动生成建筑。沿用已有权限、参与状态和身份验证。

没有现成建筑时，`/sre:echo map generate <mapId>` 会用代码把一张 200×200 的多分区测试地图（回声枢纽：外围假山带、北院货场、西侧工业区、东侧仓储市场、中央核心枢纽、南侧托运者营地、撤离门楼，外加四角台地与 15 处假山，道路全部为折线印章铺设）写进当前 Areas 地图的模板区：分 tick 切片覆盖模板 → 复制到实战区 → 写侧文件并绑定 → 补 Areas 设置（关会议/初始物品/强制职业，声明 `echo_freight`）→ 跑 6–32 人预检与四阶段路线准入。`generate status|cancel` 配套；模板旧方块超过 64 格需再执行一次带 `confirm` 的同名命令。布局/整数画布/场景与侧文件生成分别在 `map/manor/EchoManorLayout|EchoManorCanvas|EchoManorScene|EchoManorPlan`，运行时推进在 `EchoManorGenerator`，结构/步行验收在 `EchoManorLayoutTest`。

- `/sre:echo map new|edit <mapId>`、`mark`、`corner a|b`、`volume`、`remove`、`list`、`cancel`、`save <mapId> [confirm <nonce>]`：OP2、非对局内存编辑，当前Areas地图同ID；模板坐标原子保存、30秒一次性摘要确认。`validate [mapId]`检查6–12人结构与世界点位，`preview [mapId]`私有线框标签，`routes [mapId]`异步有界步行诊断；省略ID时优先本人草稿，显式ID读取当前地图磁盘文件。`reload`仅说明下一局生效。
- `/tmm:start noellesroles:echo_freight`：开发开局，不传分钟参数。
- 空手按住右键操作配置的晶簇、信标、祭坛、物资箱；钥匙自动用于所点核心控制器，内部出口只能从核心内使用。
- 右键地面展示拾取；祭坛上的钥匙按住1秒。
- `/sre:echo_drop cargo` 或 `/sre:echo_drop key`：普通参与者丢自己的一份货物/钥匙，无需管理员权限。未来背包按钮复用同一事务。
- 截流者G：撤离阶段过载关门；托运者G：准星推挡；投机客G：准星交易；截流器右键蓄力松手攻击，空手按住倒地玩家5秒救援。
- `/sre:echo status`、`abort`：OP2状态/中止。
- `/sre:echo debug evacuation`、`carriers_win`、`interceptors_win`：开发阶段/结果测试，不代替真实信标完成。

所有局均标记debug，不计普通谋杀胜率。两次机会和最终淘汰已经接入；未离场成员首次合法断线进入600模式tick托管，第二次断线/主动旁观/换职业/跨维度直接永久退出。真实登录与语音仍需多人实测。

## 输入与同步的阶段性实现

阶段2复用原版持续右键交互包（通常每4tick）开始/续约，服务器按配置方块/掉落实体解析目标，执行距离、LOS、阵营、容量和限流校验，不接收客户端完成百分比。

新增C2S只用于松键取消：`EchoCancelActionPayload(matchId, actionId)`。操作者从连接上下文取；旧match/action取消幂等且不影响新动作，按与交互相同的队列顺序执行，暂停时仍可取消。取消不产生资源，后续库存写请求再接07的完整信封。客户端打开界面也取消引导。

目标公共状态走原版BossBar、消息和方块更新，私有货物/KEY/待交付引用/动作ID走本人RoleData。所有事件固定注册，退出活跃模式后输入拦截恢复默认行为；不改普通模式原有交互。

## 验证记录

- 阶段0–1：回声23项JUnit通过；完整构建全项目36项测试通过。
- 阶段2：先执行回声测试及编译通过，再修正已定位的取消排序、瞬时拾取、模式隔离和门写入验证，补最后tick及恢复边界测试。
- 最终执行 `./gradlew build --offline --console=plain` 成功。JUnit XML：**全项目49项、回声36项，失败/错误/跳过均为0**。门日志4项、目标/租约9项测试均通过。
- 产物：`build/libs/starrailexpress.jar`。三语运行时静态键/八向提示/技能键齐全；新Java为UTF-8无BOM、无行尾空白，`git diff --check`通过。
- 版本号、依赖、GameMode/SRERole基类未改。构建仍有既有mixin重映射/Gradle弃用警告，未导致失败。
- **未执行**：游戏内多人开局、真实地图路线、网络延迟下松键与目标切换、门关闭碰撞/语音隔离、视觉与性能实测。纯状态机和模拟方块日志测试不能代替这些验收。
- 子agent传输失败且无代码可整合，本阶段由主agent完成；没有继续重试或让其它代理覆盖相同文件。

## 下一功能块

继续补齐卡牌参数覆盖、本人动作/KEY周期等HUD、工具贴图、细臂假影和超大地图音效，再做实际Minecraft/多人回归。地图编辑、覆写确认、私有预览、诊断、重置后强制路线准入、六域基础数值配置及37卡图标/公共目标HUD已接通并构建通过，但尚无Minecraft实机验收。无需再次向用户请求阶段批准。

当前完整首发仍未完成，开发开关在游戏内验收和后续功能完成前保持默认关闭。

### 阶段3–4验证补记

- 阶段3完整build成功，全项目61项/回声48项JUnit无失败、错误或跳过；新增12项机会/救援/蓄力/控制/强制死亡/暂停边界测试。
- 阶段4最终执行./gradlew build --offline --console=plain成功；新增六槽/锁/预留/KEY配额/满包合成/原子交换/失败零移动、请求去重与限流测试。统计由JUnit XML核实，不等于MC场景测试。
- 已修复实际发现的属性API差异、丢弃失败反馈、重复保留格、无KEY预留产钥匙、私有同步旧tick和界面提示重叠。一次可选只读代理复核传输失败且无报告，未反复开启评审。
- 待游戏内验证：蓄力射线/实体遮挡、倒地锚定与姿态、GUI缩放/窄屏三语、弱网关屏取消/候选重开、多人语音、真实路线和性能。开发开关保持默认关闭。

### 阶段5首块验证

- `./gradlew build --offline --console=plain`成功；JUnit XML全项目82项/回声69项，失败、错误、跳过均0；新增6项标记/到期/暂停tick/替换/速度合成/清理测试。
- 三语各156键且键集合一致，JSON解析成功；git diff --check通过。构建日志位于会话scratch的echo-stage5a-final-build.log。
- 未执行Minecraft多人场景、真实卡牌命中、雷达接收者实测、GUI缩放/视觉/性能验收；保留开发开关。

### 阶段5第二块验证

- `./gradlew build --offline --console=plain`成功；JUnit XML全项目93项/回声80项，失败、错误、跳过均0；新增7项场景上限/原子创建/延迟声/暂停/误导/清理测试、2项面具测试、2项单向库存转移测试。
- 三语各159键且键集合一致，JSON解析成功；构建日志为会话scratch的echo-stage5b-final-build.log。未执行Minecraft模型/多观察者权限/GUI/多人性能验收，开发开关保持默认关闭。

### 阶段6交易与契约验证

- 交易块完整build成功，全项目102项/回声89项；契约/黑晶/退场块完整build成功，全项目111项/回声98项JUnit，失败、错误、跳过均0。新增9项交易状态/锁/满包/赠送/版本/KEY容量测试及9项契约/来源链/黑晶恢复测试。
- 三语各204键，JSON解析及键集合一致；Java文件package开头、UTF-8无BOM检查通过，git diff --check通过。构建日志：会话scratch的echo-stage6-contract-final-build.log；产物build/libs/starrailexpress.jar。
- 修复构造器局部契约分配表遮蔽服务字段的实际编译错误后重建通过。未执行真实Minecraft多人交易/黑晶路线/契约权限/退场语音/GUI/性能验收；开发开关保留。

### 阶段6：断线托管与关联修复

- Member持有唯一EchoCargoState；RoleData仅挂载在线库存/货槽引用，清理投影不删除会话资源。离线库存、绑定包、契约与生命截止仍由原会话推进。
- 固定JOIN/DISCONNECT监听；首次断线取消动作/交易/蓄力和退场停留，普通/黑晶及KEY在最后安全点掉落，旧请求窗口失效并清除旧RoleData引用；只提供一次600模式tick宽限，重复断线事件不续期，第二次断线直接退出。
- 重连核对名单/局/宽限，生成新epoch和请求序列，挂回同一个六槽库存；恢复原生命/控制/保护截止及尚有效私人投影，不补初始包、不添加新保护；记录点失效回本人出生点。陌生UUID只能旁观。
- 离线OFFERED按原期限默认选择，CHANNELING取消但保留绑定与缓存；离线流血截止继续推进；宽限过期/主动旁观/换职业/跨维度按UUID完成一次永久退出与资源掉落，不依赖已经清空的RoleData。宽限中的成员进入主胜负available计算，但不能行动、守点或获得离线存活契约资格。
- 地面记录与展示分离，展示最多256份、每秒最多补建16份，KEY/黑晶/普通货优先替换低优先展示；只撤销展示不删资源。真实生成失败保留原实例与寿命，按原规则回收。
- 开包50%系数合并进模式速度clamp，避免携货/猎杀与独立乘区跌破0.5；有效非零环境伤害取消交易并解除面具。
- `./gradlew build --offline --console=plain`成功；JUnit XML全项目120项/回声107项，失败、错误、跳过均0；新增8项连接/离线组合回归及1项开包速度下限测试。三语205键一致，UTF-8无BOM与git diff --check通过。日志echo-stage6-reconnect-build.log。
- **未执行**真实Minecraft重登/跨维度/并发角色修改/实体生成失败注入/语音恢复；这些仍是实机验收，不用纯状态测试冒充。

### 阶段7：地图底层首块

- EchoMapDraft提供模板坐标的内存编辑、精确点位替换、出生朝向、人数阈值、门体/核心/撤离框、删除与重命名导出；先修改副本再严格解析，失败不改变现有草稿。新建草稿未补齐必需点位时不能保存。
- EchoMapRepository新增受限mapId、无符号链接保存路径、1MiB限制、全部6–12人结构校验、禁止实战坐标导出、现存文件SHA-256确认及原子保存。摘要已变化/未确认覆盖时保留原文件，临时文件清理；命令侧确认流程已在下述工具块接入。
- 地图底层完整build成功；累计JUnit XML**全项目125项/回声112项**，失败、错误、跳过均0。新增5项编辑失败零修改/门框与坐标/不完整草稿/保存覆盖摘要/非法路径测试。三语205键一致、git diff --check通过。日志echo-stage7-map-core-build.log；产物build/libs/starrailexpress.jar。

### 阶段7：地图命令、私有预览与步行诊断

- 新Fabric注册完整空白token参数，地图ID的斜杠/资源ID冒号不截断；纯Brigadier回归覆盖后续参数、引号和输入上限。
- OP2固定命令树：new/edit/cancel/list、mark/minPlayers、corner、volume、remove、save/confirm、validate、preview、routes/cancel、reload。编辑仅INACTIVE阶段、每人一份、全服16份、闲置30分钟；绑定世界实例/维度/Areas地图/原平移，断线、卸载、切图、开局或权限撤销清理。
- 出生/安全点取服务器玩家脚下，交互点取16格服务器方块射线；世界→模板只减一次resetPaste.min-resetTemplate.min，核对实际方块复制偏移。两个角点转包含端点的整数半开框，不写建筑。
- 保存先检查所有6–12人结构和真实世界安全，再同目录原子保存；覆盖确认绑定随机nonce、mapId、草稿revision、文件SHA-256及600服务器tick，失败/过期/重放拒绝。保存后不改变任何活动对局。
- EchoPreflight可检查给定草稿，补齐黑晶、黑晶投放、brokerExit、撤离控制器、世界边界、危险地面、掉货锚点、核心/撤离区边界；结构失败先返回，避免对无效巨大门体进行世界遍历，门体检查不强制加载区块。
- 私有预览单S2C包最多1536条框；客户端30秒、最近128格内256框、48格内32标签，带类型/ID/人数阈值/出生朝向；换维度或开局清除，无假实体、无全服逐点粒子。
- EchoWalkGraph纯有界四邻接图、最多1格高差、canJump/起跳顶空/下落顶空/反向回程；EchoRouteCheck采样真实已加载地面/流体/危险/边界/LOS，分别检查闭核心开放循环、逐个入口虚拟开门的核心信标/黑市/内部出口及回程，检测关门仍可步入核心。虚拟门仅改变采样，不写方块。
- 路线诊断全服至多2任务、轮转16节点小步、每tick约2ms预算；单图6万节点/36万探测，累计24万/144万、CPU2秒、总时长60秒限制。未加载、预算不足或模型外特殊碰撞明确MANUAL而非通过；失败列阶段/点位，最多12条。下述增补已接入开局闸门，自动采样不等于真人路线验收。
- 最终`./gradlew build --offline --console=plain`成功；JUnit XML全项目**140项/回声127项**，失败、错误、跳过均0，新增6编辑/确认、3token、6步行图测试。三语221键集合一致；UTF-8无BOM及git diff --check通过。日志echo-stage7-map-tools-final-build.log，产物build/libs/starrailexpress.jar。
- 未执行Minecraft命令权限/实际坐标命中、预览渲染、真实地图路线或多人/性能测试；纯测试和成功构建不能替代这些验收。

### 阶段7：重置后强制准入与基础数值配置

- 公共DeferGameInitialize异步准备闸门位于地图重置之后、身份分配之前，传递原流程计算的只读名单；其它模式默认不等待。Echo在准备态分帧扫描，失败由既有AllowGameInitialize否决路径统一复位，不进入半局，不在同步预检中运行多秒BFS。
- 扫描覆盖所有已配置出生、开放资源/祭坛/入口控制、各入口开后的核心资源/内部出口及回程；新增虚拟撤离门开启后的撤离区进入/回程与门外掉货锚点。已检测到闭核心泄漏优先FAILED，不被预算不足降级成人工可确认。
- 每次开局单独许可，绑定世界实例、地图/规则文件SHA-256、平移、playArea、canJump、世界边界、参赛UUID集合和地形版本。LevelChunk写块RETURN监听真实修改（覆盖命令/活塞/流体），区块加载/卸载也失效；普通诊断发生地形/草稿变更则撤销。无跨重启、跨局或重置前复用。
- 只有MANUAL生成600服务器tick的一次性令牌：OP2可在STARTING执行`map routes confirm <nonce>`；错误、过期、改图改规则或地形变更不能放行，FAILED无令牌。确认记录操作者/维度/mapId/配置摘要/版本；最终许可100tick内一次消费。60秒扫描硬超时或异常取消开局，修复后重新开始。
- EchoRuleRepository读取世界echo_freight/rules.json，缺省才默认；64KiB/无符号链接/严格UTF-8、嵌套对象、重复/未知字段/类型/溢出/概率与时长关系校验；所有外部Seconds精确×20转整数tick。EchoRuleConfig六域73项数字不可变，预检载入、会话持有，reload不改活动局。
- 实际接入物流容量/减速/采集/补给/箱与地面寿命/奖励概率，信标供料/定点充能/护送/污染/暂停，核心/门撑/高级门/撤离/过载，战斗蓄力/冷却/倒地/救援/控制与环境保护，开包引导/取消/选择/速度/五类品质分布，货物/KEY声纹及拆包声范围。本人同步容量/减速基线与最短蓄力，信标分母显示实际配置，终局快照保存有效规则hash。
- 仍未覆盖全部05卡牌参数与交易参数自定义，不能宣称任意卡数值已可配置。37独立图标子代理因`Stream ended without finish_reason`失败且未产生文件，将由主agent继续，无重复委派。
- `./gradlew build --offline --console=plain`成功；JUnit XML全项目**159项/回声146项**，失败、错误、跳过均0（新增8许可、11规则加载/冻结/运行效果测试）。三语**229键**一致、精确静态Echo翻译无缺项、Java UTF-8无BOM及git diff --check通过。日志echo-stage7-rules-final-build.log；产物build/libs/starrailexpress.jar。
- 未执行Minecraft真实开局/地图重置等待/确认命令、Mixin运行/活塞流体变更、界面缩放、多人/语音/性能测试；开发闸门保留。

### 阶段8：37卡图标、可滚动卡牌详情与公开目标HUD

- 新增37张64×64 RGBA语义像素图标，棕黑/米金统一底板、阵营辅助色；高级版本主体结构有区别，不仅换色。tools/echo-freight/generate_icons.py可复现生成，不增加游戏依赖。三选一和选中卡详情实际读取card.icon()，独立滚动描述/配方/同系持有数，窄屏保留三个选择按钮。
- 新EchoPublicState/EchoPublicPayload只包含阶段/时钟、公开供料/充能/封锁/暂停/稳定层、撤离Q、核心和撤离门、共享过载。最多3信标、ID64字符、解码先检查数量；同维度公开广播与私人RoleData分开，旁观者也不获取他人手牌/契约/阵营。
- EchoHud统一展示公开目标和本人货物/生命/推挡冷却/装备/私有方向，移除重复BossBar与旧卡牌HUD回调。每10服务器tick发布（SAFE和暂停也发布），本地时间最多插值10tick，暂停冻结，完成/开门事实不由插值推断。结束通知、维度切换、断线、模式切换及快照过期清理。
- 完整build成功，JUnit XML核实**全项目164项/回声151项**，失败/错误/跳过0；新增图标覆盖/唯一摘要/尺寸测试、3项公开快照边界/暂停/插值测试，以及仓库rules.example.json真实解析回归。三语各**252键**一致，git diff --check通过。日志echo-stage8-icons-hud-final-build.log。
- 本次修复了实际编译发现的escapedCount返回long与CommonHudRenderCallback使用FakeGuiGraphics的API差异。抽查了工作区万能钥匙/偷包钩爪PNG；临时联络表被图片工具路径边界拒绝，未声称完整视觉验收。
- 未执行真实Minecraft的公开包/旁观权限、GUI缩放、长文三语、HUD与既有界面叠放或性能验收；动作引导仍沿用服务器actionbar，KEY下次脉冲/完整交互拒绝提示仍待补齐。开发门控不撤销。

### 阶段8增补：冻结卡牌/交易参数与一致显示

- EchoCardTuning严格逐卡字段/边界/关系校验，外部Seconds精确转tick，嵌套不可变；未知、固定机制或gate/beacon已有别名拒绝，默认值覆盖归一化。有效hash纳入全部37卡实际参数及权重，不仅记录差异。
- 实际接入卡牌权重抽包、效果持续/次数/范围/刷新、物流被动、黑晶搜索/吸包、假人声音/诱导、门内扫描、双点延迟/高亮、基础交易范围/期限/提案冷却。原gate/beacon仍为钥匙时长、门撑、污染与脉冲暂停唯一来源。
- 首次绑定/重连独立有界S2C同步冻结规则，不夹带高频RoleData；按match/维度应用，规则未到禁写。详情、候选和交易提示使用37项三语机制说明+本局实际数字，不继续显示写死默认秒数；代理目标距离与物流读取相同定义。
- 完整build成功，JUnit全项目171项/回声158项，失败/错误/跳过0；新增7项白名单/边界/冻结/语义hash/别名/真实权重/效果与语言测试。日志echo-card-tuning-final-build.log。首次测试编译的泛型推断错误已修复。

### 阶段8增补：本人引导、KEY周期、工具与定向提交声

- EchoActionView只读私有投影包含动作类型/开始tick/时长，不含其它玩家坐标；HUD进度上限99.9%，不推断成功。本人交互拒绝提示限频/短期截止；KEY下次脉冲使用资源原期限，暂停不推进。移除重复持续actionbar。
- 共用HUD仅对Echo门控普通身份、时间、金币、心情/小游戏及附属提示，保留公共回调与广播；Echo技能不再额外显示普通零冷却就绪栏。尚未游戏内叠放验收。
- 假影缓存宽臂/细臂两种PlayerModel，按实际PlayerSkin模型选用；截流器与卡牌代理新增独立32px透明贴图和模型引用，generate_tools.py可复现。两张PNG均已通过图片工具实际查看，尚未第一人称验收。
- 真实与伪造提交共用位置音效包，固定白名单音源/音量、有限坐标、match/维度/活人权限；客户端使用真实声源坐标及Attenuation.NONE，不把声源移到听者，也不靠增大音量假装全图。没有真实/伪造布尔事实，声音不能推进目标。
- 完整build成功，JUnit XML全项目**174项/回声161项**，失败/错误/跳过0；新增2项动作边界/防提前成功测试、1项工具资源检查。三语各**341键**一致；JAR的37图标、工具模型/贴图、Java package/无BOM与git diff --check通过。日志echo-presentation-build.log。
- 尚未执行真实Minecraft声音方向/大地图听感、重连规则包顺序、HUD缩放/三语长文/普通模式回归、多人/语音/性能验收；开发门控仍保留。

### 阶段8增补：真实服务端启动与开局闸门对照验证

- 用项目既有`runServer`（离线Gradle、`nogui`）实际启动开发服务端。首次未隔离时Fabric加载全部mod后进入`Done (0.326s)`，`noellesroles`、语音插件与回声模式/角色/会话/内容注册均无异常；但该次落在了项目`run/`，仅更新既有开发世界的`level.dat`/`session.lock`，未开局、未生成`echo_freight`。
- 后续改为隔离运行目录：用`-I`初始化脚本把Loom的`server.runDir`指向scratch，`mods`用Windows目录联接复用项目`run/mods`（18个jar，零复制），`config`复制副本，`eula.txt`沿用既有`eula=true`。服务端在隔离目录进入`Done (6.462s)`，验证结束后主动`rmdir`联接，源`run/mods`完好、无残留服务端Java进程。
- 控制台实机响应：`sre:echo status` → `No Echo session is available for this operation.`；`sre:echo map list` → `Map tool: 此编辑操作只能由玩家执行`。命令树与无对局/非玩家拒绝路径均真实生效。
- 开局闸门对照（同一隔离世界、0在线玩家、`tmm:start noellesroles:echo_freight`）：默认实例被`回声开局预检未通过（地图=null，人数=0）: [Echo Freight is not released. Start the server with -Dsre.echo.development=true for development testing.]`拒绝；以`-Dsre.echo.development=true`启动后，同一命令改为`Echo Freight requires 6–12 ready players; currently 0.`。两次都未创建会话、未创建`echo_freight`目录，证明开发门控是首个分叉点，放行后仍受人数档约束。
- 注意`Game started successfully!`是`GameUtils.startGame`的早期消息，**不代表回声已开局**；上述两次均随后`GameStatus`复位为INACTIVE（`GameUtils.initializeGame`拒绝分支），不能据此误判。
- 本次实机验证暴露并修复了诊断缺口：预检/准入失败原先只发给在线玩家，无人在线时服务端无痕迹；现增加含地图名、人数与前8条原因的WARN日志，随后重新构建并复验通过。
- 日志中的MySQL玩家数据同步失败（环境无数据库）、`No data fixer registered`与`harpymodloader:start_game_2`函数解析错误均为该开发环境既有告警，与Echo代码路径无关。
- **仍未验证**：6–12名真人参与的完整开局、地图重置后的路线准入、门体写入/恢复、卡牌/交易/契约/撤离结算、HUD与GUI缩放、语音与多人重连、性能。开发门控继续保留，普通服务器仍拒绝回声开局。

### 阶段8增补：人数档与地图闸门、地图工具提示复验

- 用Carpet假玩家在隔离服务端实测人数档：`player EchoT1..EchoT6 spawn`后`list`确认`There are 6 of a max of 20 players online`，再执行`tmm:start noellesroles:echo_freight force_all_players`。预检从`requires 6–12 ready players; currently 0`推进为`回声开局预检未通过（地图=null，人数=6）: [Load an explicit map with Echo Freight configuration first.]`，证明人数档通过后确实进入地图配置检查，且仍不创建会话或`echo_freight`目录。
- 假玩家（`op`后）执行`sre:echo map new test/echo_smoke`时，原先误报`非法mapId`。根因是`EchoMapTools.context`直接对空的`AreasWorldComponent.mapName`调用ID校验。已改为区分“未选择地图”和“地图名不合法”，复验输出为`请先用现有Areas工具加载实战地图（当前未选择地图）`；未改变任何放行条件。
- 新增[11 手工验收清单](11-manual-acceptance.md)，把真实地图制作、开局闸门、物流/战斗/卡牌/交易/契约/重连、GUI、音频与性能的剩余验收写成可执行步骤、预期与失败判定；前置条件与记录模板一并固定，避免用单元测试冒充实机通过。
- 尝试用`sre:area_manager set mapName ...`在隔离世界建立Areas地图以继续验证草稿生命周期，但该命令的反馈只发给假玩家、地图名未实际变更，`map new`仍被同一前置检查拒绝。该工具属第三方Areas链路，未在本次范围内修改。
- 因此**草稿创建/标点/保存/预览/路线诊断的真实玩家路径仍未执行**；本轮只验证了非玩家拒绝、地图前置检查与错误提示。相关构建日志`echo-maptool-msg-build.log`。

### 阶段9：阵营专长与开局揭晓

- 新增六专长（CARRIER porter/scout、INTERCEPTOR hunter/saboteur、BROKER smuggler/fixer），每阵营一个 starter。只调数值，不新增身份、按键、胜利条件；不移植 repair 的随机职业/金币/商店/临时选角建筑。
- 规则新增 `specialization` 域（11 项数值），严格边界：容量加值 −1..1、减速增量 ±0.05、倍率 0.5..1.5、交易范围加值 0..4；有效 hash 纳入专长数值，rules.example.json 同步。
- 选择窗口绑定 SAFE 与 deadline（SAFE 结束前 40 tick）；C2S 只带选项下标与递增序号，服务端按当前阵营候选解释；重复选择覆盖、越界/过期/重放拒绝；进入 OPERATIONS 时统一锁定。
- 数值接入：货槽与搬货减速、采集/黑晶采集/污染引导倍率、截流器蓄力与冷却（成员冻结 Combat 副本）、基础交易范围与提案冷却。断线保留已选，重连重发快照；旁观不发送。
- 开局揭晓为不接管相机的 3 秒阵营卡（移动可跳过），随后打开专长界面；入口为背包按钮与 `/sre:echo_spec`，无新按键绑定。
- 结果快照新增 `specialization` 字段，旧 8 参构造保留为 null 兼容。
- 完整build成功，JUnit XML**全项目182项/回声169项**，失败/错误/跳过0；新增5项专长默认/倍率/边界/协议/示例回归。三语各**365键**一致。
- 未执行真实多人选择/断线重连/GUI 缩放/揭晓表现验收；这些已并入[11 手工验收清单](11-manual-acceptance.md)第13节。

### 阶段10：界面演出与物品描述

- 新增`EchoAnim`（缓动/淡入/呼吸/紧迫/脉冲/混合），全部基于系统毫秒，不参与权威判定。
- 背包、交易、契约、专长四个界面加入180ms入场淡入与打开音效；开包三选一按90ms错开逐张淡入；专长卡片hover描边；专长与交易倒计时最后数秒由蓝转红闪烁。
- HUD货物/KEY数值变化900ms金色脉冲、生命状态变化1200ms提亮；假影最后1秒按剩余时间淡出；充能信标光柱1600ms呼吸；揭晓卡前后淡入淡出。
- 补齐`echo_card_proxy`三语名称/简短描述与`item_intro`长描述（操作步骤、代理不携带卡牌身份、失败不扣卡）；`echo_cutter`长描述已在。
- 语言文件按仓库CRLF与既有BOM写回，避免整文件行尾变化。
- 完整build成功，JUnit XML全项目182项/回声169项，失败/错误/跳过0；三语echo_freight各365键一致、item_intro各271键一致。
- 未做镜头轨道/全屏shader/粒子烟花；未执行GUI缩放、长文案与多人下的动画观感验收。

### 阶段11：三格携带、专长主动技能、技能 HUD、屏幕演出、呼吸音效与听觉被动

- **删除开局钥匙与信件**：根因是共享的`GameUtils.baseInitialize`给每名玩家发放`TMMItems.KEY`、`ModItems.LETTER_ITEM`（以及赞助玩偶）。现以`echoMode`判定（`getGameMode()==EchoBootstrap.ID`）跳过这三处发放，其它模式行为不变；服务器`enforceTool`每5 tick清空3–8槽并把`selected`钳到2，因此即使沿用旧背包也不会残留钥匙。
- **三格快捷栏**：新增客户端mixin`EchoHotbarMixin`（`@Inject`到私有`Gui.renderItemHotbar`的HEAD并`ci.cancel()`），自绘3格背景/选中/物品/数量；`EchoHotbarScrollMixin`在`Inventory.swapPaint`HEAD把滚轮限制在0..2且不回绕；数字键在客户端tick内clamp。两个mixin注册在`noellesroles.mixins.json`的`client`段，`EchoHotbar.active()`确保只在回声对局接管。
- **专长主动技能**：`EchoSkill`（6个：`steady_haul`/`swift_pick`/`lock_on`/`surge`/`dark_cache`/`broker_deal`，各带冷却与窗口）+`EchoSkillState`（一次性`pending`与持续`buff`分离，消耗不退冷却、窗口到期自动失效）。右键第3格专长信物→`EchoSkillItem.use`→`EchoMatch.useSkill`，逐项校验阶段/站立/canAct/冷却/撮合目标，失败不进入冷却。六项效果全部真实接线：稳载（容量+1且减速归零）、速取（晶体采集瞬发）、锁定（截流器蓄力1 tick）、涌动（污染40 tick并按强污染结算）、暗格（黑市交货瞬发且减速归零）、撮合（交易范围+4并跳过提案冷却）。
- **五边形技能 HUD**：右侧居中46×46（窗口高<240时38×38），扫描行填充正五边形+Bresenham描边，24×24图标，冷却自下而上覆盖并在中央显示剩余秒数（>60秒整秒、否则一位小数），就绪时描边按1.6秒呼吸；倒地/淘汰降到40%透明度并显示`×`。全部只读本人投影。
- **屏幕状态演出**：不引入后处理链，用上下`fillGradient`加左右8段alpha条带近似暗角；受伤0.10呼吸、倒地0.22+整体压暗+心跳节奏（流血<10秒加快）、受控冷蓝0.08、撤离0.35白闪600ms淡出。
- **呼吸/咳嗽/心跳**：复用`WARDEN_HEARTBEAT`/`PLAYER_BREATH`/`GENERIC_EXTINGUISH_FIRE`（原版无咳嗽音效，用 pitch 1.45 的熄灭气声近似干咳），倒地40（紧张时20）tick心跳+60 tick呼吸、受创160 tick咳嗽+120 tick呼吸；生命状态变化重置计数器，暂停不播放，全部本地`SimpleSoundInstance.forUI`，不发包。
- **截流者听觉被动**：`EchoSignalService.hearing`每tick对站立截流者取24格内噪声最高者（冲刺+3、水平速度>0.12+2、正在交互+2、带货+1、黑货+1，总分为0静默），发20 tick方向扇区`hearing`信号；`EchoSignalView.directionOnly`纳入`hearing`并继续禁止携带目标UUID；同一目标40 tick内不重复触发。
- **资源与文本**：新增`tools/echo-freight/generate_skills.py`生成`echo_skill_token`物品贴图与6个技能图标（32px RGBA），配套物品模型与三语名称/短说明（`noellesroles`）和长说明（`item_intro`）。
- **补齐语言缺口**：审计发现119个`echo.*`键缺失（战斗播报、`sre:echo`回执、方向扇区0–7、地图编辑器HUD/按钮/提示/回执、开局校验、目标播报、开局运镜6段解说+专长标签、5个阶段名、私有信号`cargo`/`key`/`hearing`、6个技能名/描述等），此前界面会直接显示原始键名。现已三语补齐：`echo_freight`各**489键**且三语键集一致，`item_intro`各**272键**，写回保持CRLF与既有BOM。
- **顺带修复**：`EchoRoleData`缺少`actionId()`读取器导致契约退出与客户端取消链路编译失败；此前未编译通过的`EchoIntroService`（`dolly`参数顺序）、`EchoGameMode`（缺`EchoIntroService`导入）、`EchoIntroClient`（`context.tickDelta()`应为`context.tickCounter().getGameTimeDeltaPartialTick(false)`）一并修正。
- 完整build成功，JUnit XML**全项目188项/回声175项**，失败/错误/跳过0；新增`EchoSkillStateTest`6项（冷却、一次性消耗、窗口到期不退冷却、增益不可消耗、清空、技能与专长一一对应与三语键覆盖），`EchoSignalViewTest`扩展`hearing`方向类。
- **与既有系统共存（核对结果）**：`InGameHudMixin` 已`@WrapMethod` `renderHotbarAndDecorations`，高级运镜期间整块跳过（含我方三格栏），运镜结束自动恢复；`PlayerInventoryMixin` 已`@WrapMethod` `Inventory.swapPaint` 处理疯魔回滚，我方`@Inject` HEAD 作用在原始方法体内且疯魔与回声不共存；三个HUD/演出都挂在`CommonHudRenderCallback`，与既有职业HUD同通道，受`OptimizedTextRenderer.isTickDirty()`节流为**每tick一次（20Hz）**，因此呼吸/闪烁按20Hz步进而非每帧；项目已有`PostProcessor`+`ImmersiveFilterShaderMixin`后处理管线，但它在世界阶段、UI之前生效，压不住HUD，故屏幕演出走GUI叠层。
- **仍未验证**：快捷栏mixin的真实客户端注入（F1/创造/旁观/联机）、五边形HUD在各分辨率与GUI缩放下的位置、暗角在光影下的叠加、心跳与呼吸音量、听觉被动多人下的刷屏与延迟、技能与卡牌/契约并发边界；开发门控`-Dsre.echo.development=true`继续保留。清单见[11 手工验收清单](11-manual-acceptance.md)与[13 §10](13-skills-hud-atmosphere.md)。

### 阶段12：追猎循环、完整施法动作与第三人称动画同步

- **四相追猎循环**：新增纯状态机`EchoHuntLoop`（`STALK`/`CHARGE`/`LUNGE`/`RECOVER`），只在`STALK`可起手，蓄力减速0.55×、后摇0.85×，挥空24 tick、命中8 tick；相位到期在读取时惰性推进，不依赖外部 tick。
- **锁定值与扑击**：24格内按距离（1.0/0.75/0.5）与目标移动（疾跑1.4/移动1.0/静止0.6）累积，无目标每tick衰减3；满值下一击升级为扑击（判定+2格、倒地+200 tick、命中免后摇、锁定清零）。
- **狩猎本能改造**：`EchoSkill.LOCK_ON`由一次性pending改为60 tick持续增益（`HUNTER,1500,60,false`）：立即满锁定、1.2×移速、命中免后摇、蓄力压缩到8 tick；`cutterChargeTicks()`优先取本能值。
- **受害者心跳**：`huntTick`在锁定≥50且有目标时，仅向目标本人播放`WARDEN_HEARTBEAT`，间隔40→12 tick随锁定加速，不泄露追捕者坐标。
- **动作广播**：新增`EchoHuntPayload`（`CHARGE/STRIKE/MISS/INSTINCT/CAST` + `serial` + `matchTick` + `empowered` + 施法母题）与`EchoHuntFx`（96格内广播、按玩家递增序号）；客户端`EchoHuntClient`用`System.nanoTime()`推进并按序号去重，重连换局自动清空。
- **动画曲线**：新增`EchoHuntAnim`（蓄力三段：抬刀/后收/满值颤抖，出击单峰弧线，命中与挥空两档后摇，扑击1.2×幅度，第三人称手臂与刀身分开）与`EchoSkillCastAnim`（六专长各一套关键帧母题`PRESS/SNATCH/SCAN/SLAM/CONCEAL/OFFER`，16 tick，端点严格归零）。
- **渲染层**：三个mixin——`EchoHuntFirstPersonMixin`（`renderArmWithItem` HEAD 叠加增量，本地蓄力进度无延迟）、`EchoHuntThirdPersonMixin`（`poseRightArm/poseLeftArm` TAIL 覆写手臂欧拉角）、`EchoHuntItemPoseMixin`（`renderItem` HEAD 旋转第三人称刀/信物），已注册进`noellesroles.mixins.json`的`client`段。
- **追猎 HUD**：`EchoHuntHud`在技能徽章左侧画锁定环（0–100，三档配色）、相位内点（潜伏呼吸/蓄力实心/突进亮橙/后摇空心）、相位名、扑击就绪与本能倒计时，仅本人为追捕者且可行动时显示。
- **服务端接线**：`beginCutter`→`charge`、`releaseCutter`→`strike`/`miss`、`useSkill`→`cast`；`huntTick`负责目标选取、`EchoHuntLoop.tick`、`AttributeModifier(ADD_MULTIPLIED_TOTAL)`移速修正、心跳与投影；离开追猎态立即移除速度修正。
- **语言**：新增`echo.hunt.*`7键（扑击命中提示、扑击就绪、本能剩余、四相位名），三语`echo_freight`各**496键**且键集一致，写回保持CRLF。
- **测试**：`EchoHuntLoopTest`15项（锁定累积/衰减、蓄力消耗与不可重入、扑击升级与清零、命中/挥空后摇、本能免后摇与压缩蓄力、移速倍率、心跳边界、增益分段、提示只播一次、清空、快照）、`EchoHuntAnimTest`8项、`EchoSkillCastAnimTest`8项。
- **修正**：`EchoCombatService`类尾多一个`}`导致「未命名类」编译错误；`lethal`返回类型改为`EchoCombatState.Hit`后所有调用点按`IGNORED`区分真实命中；`firstPersonStrike`前摇分量随进度回落，保证终点归零。
- 完整build成功，JUnit全项目**220项**（回声207项），失败/错误/跳过0。
- **仍未验证**：第一人称叠加原版`SPEAR`姿势的抬刀高度与准心遮挡、第三人称覆写手臂在行走/疾跑时的过渡跳变、96格广播在12人局的带宽、扑击`+2`格在墙角/斜坡的射线一致性、多人同时锁定的心跳叠音、HUD在720p/4K/大GUI缩放下的遮挡、施法动作与服务端生效时刻的主观延迟。清单见[14 §10](14-hunt-loop-animations.md)。

### 阶段12增补：攻击特效、擦刀、狂暴形态外观

- **攻击特效**：新增`EchoHuntFxIds`（4 个自定义粒子 id）与客户端`EchoHuntFxClient`——服务端每次出击发一段弧形刀光、命中发锥形血花（秒杀扑击额外叠地面冲击环）、擦刀发血线；每类只发一包，形状由客户端逐 tick 摆点，客户端粒子不进网络，同时最多 24 条。
- **擦刀**：新增`EchoHuntPayload.Kind.WIPE`（`empowered` 位复用为「是否命中」）与`EchoHuntAnim.firstPersonWipe/thirdPersonWipe/thirdPersonWipeItem`三段曲线（甩血 0–35%、横抹 35–75%、归位 75–100%），时长 0.9 秒；命中才有横抹手势，挥空只做小幅收刀。第一/第三人称手臂与刀身均已接线。
- **擦刀音**：本人`ARMOR_EQUIP_LEATHER.value()`（pitch 1.35）+ 12 格内其他人`PLAYER_ATTACK_SWEEP`（pitch 1.25），复用原版声音。
- **追捕者狂暴形态**：新增`EchoInterceptorSkin`，追捕者固定使用`wathe:textures/entity/psycho(_thin).png`；`PlayerEntityRendererMixin` 在`getTextureLocation` HEAD 短路（优先于职业皮肤），`renderHand` 的`ModifyVariable` 同步第一人称手臂。
- **1.5 倍体型**：新增`EchoInterceptorScaleMixin`注入`PlayerRenderer.scale`的 RETURN 乘 1.5（不能用`LivingEntityRenderer.scale`，1.21.1 的`PlayerRenderer`重写了它且不调`super`）。**碰撞箱完全不变**：不碰`getDimensions`/`Pose`/`getBoundingBox`，判定、射线、挤压与服务器位置保持原值。
- **修正**：`ARMOR_EQUIP_LEATHER`是`Holder<SoundEvent>`，需`.value()`；`firstPersonWipe`的 settle 项初值非零导致 t=0 有残量，改为「末段余量 × 起手柔和」后端点严格归零。
- 新增`EchoHuntAnimTest` 4 项（擦刀端点归零、命中幅度大于挥空、挥空无横抹、物品曲线有限），动画测试共 12 项。

### 阶段13：追猎者职业（裂爪 / 织网）

- **职业与专长分层**：新增 `EchoHunterClass`（`REND` 裂爪 / `WEAVER` 织网），与 `EchoSpecialization` 刻意分开——专长只调数值，职业改变**攻击方式、被动判定、主动技能**。职业只在专长为 `HUNTER` 时生效，`SABOTEUR` 与其它阵营完全保留加入职业之前的手感。
- **裂爪（近战三段连击）**：`EchoRendCombo` 纯状态机，三段判定距离 `+0 / +0.3 / +1.0` 格、出血 `0 / 1 / 3` 层（每层 +40 tick）、命中后摇 `6 / 8 / 14` tick，衔接窗口 18 tick；`swing(now)` 先按窗口到期回退，因此不会读到过期状态；终结技打完后回到第一段。
- **织网（远程蛛丝弹）**：`EchoWebBolt` 纯模拟抛物线（初速 1.4、重力 0.05、命中球 0.45、最长 40 tick、每 tick 2 子步），**不注册新实体类型**；服务端逐子步先判人再判墙，命中走与近战相同的 `lethal(...)`；贴脸 2.5 格内改为 120° 横甩；出手后循环立即回 `STALK` 且免后摇（结果延后结算，不提前惩罚）。
- **黏网场地**：`EchoWebField` 一份结构承载 `SPLAT`（2.2 格 / 5 秒 / 0.55× 移速 / 命中 +60 tick）与 `NEST`（4.5 格 / 12 秒 / 0.45× / 主人锁定不衰减）；每人最多 4 块，只困猎物不困追猎者，减速用 `noellesroles:echo_web_slow` 与控制的 `echo_immobilized` 叠加。
- **被动**：裂爪「血腥味」24 格内有流血猎物时锁定增益 ×1.5；织网「丝线感知」20 格内静止 ≥60 tick 的目标锁定增益 ×1.5，且命中站在黏网上的目标额外 +40 tick 倒地。
- **主动技能**：`EchoSkill.REND_DASH`（600 tick，视线突进 4 格 + 连击直推终结技，40 tick 内不出手作废）与 `EchoSkill.WEB_NEST`（700 tick，脚下布阵）。两者 `specialization()` 为 `null`，由 `EchoSkill.of(spec, hunterClass)` 解析。
- **接线**：`EchoRoleData` 增加 `hunterClass` 投影与 NBT 键 `echoHunterClass`；`EchoMatch` 的 `Member` 增加职业与 `EchoHunterState`，开局按追猎者出场序号轮流默认分配，新增 `selectHunterClass`；`EchoCombatService.releaseCutter` 分派 `meleeRelease` / `weaverRelease`，新增 `tickHunterProjectiles`/`resolveBolt`/`webHit`/`splat`/`applyWebSlow`/`rendDash`/`placeWebNest`/`bleedingNearby`/`trackStillness`；`EchoHuntLoop` 新增 `tick(now,gain,holdFocus)` 与 `onHit(now,empowered,recoverTicks)` 重载（旧签名行为不变）。**没有新增网络包**：职业是本人私有信息，走已有 `EchoRoleData` 单向投影。
- **命令**：`/sre:echo_hunter` 无参回显当前职业，`/sre:echo_hunter rend|weaver` 切换，仅 SAFE 且专长为 `HUNTER` 时接受（按 `EchoHunterClass.values()` 生成子命令，不手写 id）。
- **表现层**：4 个新特效 id（`echo_rend_claw` / `echo_web_cast` / `echo_web_splat` / `echo_web_field`）与对应客户端画法；`EchoSkillCastAnim.Style` 新增 `CLAW` / `WEAVE` 两套施法母题（端点严格归零）；两张技能图标 `rend_dash.png` / `web_nest.png`；`EchoHuntHud` 在环下显示职业名。
- **语言**：新增 20 键（两个职业的名称/说明/被动/攻击方式、技能名与说明、`echo.hunter.locked|selected|none`），三语 `echo_freight` 各**515 键**且键集一致，写回保持 CRLF、无 BOM。
- **测试**：新增 `EchoHunterClassTest` 19 项（身份与默认分配、职业边界、连段推进/窗口边界/挥空/后摇/技能、弹道前进与重力/归一化/退化/到期/命中球、场地尺寸与时长与减速/包含与过期、两条被动阈值、容器封顶与 prune/clear、`holdFocus` 不衰减但蓄力仍消耗、三语键与图标存在）；`EchoSkillStateTest` 的「专长↔技能一一对应」改为「专长各一 + 职业各一，总数 = 专长数 + 职业数」。
- **修正**：`EchoWebBolt` 原先只有 `advanceTick()` 记账，服务端逐子步推进时 `ticks` 永不增长会让弹永不消失——拆出 `countTick()` 并在 `resolveBolt` 显式调用。
- 完整 build 成功，JUnit 全项目**245 项**（回声 232 项），失败/错误/跳过 0；隔离 `runServer` 到达 `Done (1.286s)`，无新增 mixin/注册错误（日志中其余警告均为既有）。
- **仍未验证**：18 tick 连段窗口的真实手感、终结技 +1.0 格在墙角/斜坡/楼梯的射线一致性、0.45 格命中球对高速横移目标的命中率、黏网与冲撞控制叠加后的移速观感、`CLAW`/`WEAVE` 母题下的手臂穿模、12 人局 4 块场地的客户端粒子开销、SAFE 末期切换职业的投影时序、重连后 `echoHunterClass` 的 NBT 恢复。清单见 [15 §7](15-hunter-classes.md)。

### 阶段14：机器模型重做 + 第二批功能性卡

- **机器模型重做**：`tools/echo-freight/generate_multiblock_models.py` 删除共用的 `beacon()/extractor()/console()/crate()/market()/dropoff()/altar()` 模板，改为 12 个独立构建函数（`build_echo_*`），并新增 `quad()`（按中心镜像成组件）、`taper()`（阶梯收缩晶体）、`disc()`（无 rotation 的三层方盒圆盘）。12 台元素数 40–56（原 ~34），全部 ≥40；剪影刻意拉开（开放信标细高 32.0 / 核心信标宽扁 25.4 / 黑晶低矮 23.2 等）。契约未变：`part = x + 3*z + 9*y`、四朝向各 72 variant、整体模型 x/z ∈ [-16,32]、y ∈ [0,32]、无 `rotation`、UV 全部 0..16；生成器幂等（连跑两次 `git status` 无变化）。
- **卡池 37 → 54**：新增 17 张（10 基础 + 7 高级），引入 5 个新系列 `RELAY / STEALTH / LOCK / SNARE / KEY_BAIT`，并保持「每配方组恰有一个高级产物」闭合（23 组）。新卡全部落到既有服务，**没有新增网络包**：`signal_relay/relay_network`（`EchoSignalService.relay`）、`beacon_anchor/beacon_lock`（`EchoBeaconState.stall` + `EchoObjectiveService.cardStalled`）、`shadow_step/phantom_stride`（场景假影 + 单一机动源）、`courier_instinct`（`courierInstinct` 方向扇区）、`silent_route/hunter_blind`（`Marker.SILENCE`）、`snare_trap/hard_snare`（`EchoSceneState.Trap` + `EchoCardEffects.tickTraps`）、`cargo_bait/decoy_swap/mirror_double`（场景诱饵）、`key_bait/fake_key_cache`（`Kind.KEY_FAKE` 只诱导钥匙脉冲）、`armor_patch`（`EchoCombatService.applyDamage` 前置吸收）。
- **新机制边界**：定身是独立于倒地/控制的第三态——只锁水平位移与移速基线，**不进** `EchoCombatState.controlled`、不解除蓄力、不禁用卡牌与交互，并带免疫窗口不刷新旧截止；护甲一次性、不叠层、强制淘汰与环境伤害照常；中继按 `(接收者,目标)` 覆盖不叠时长且只投方向扇区；`deception` 按 `Kind` 过滤，假钥匙不会改写货物嗅探（`EchoSceneService.keyDeception`）。
- **资源**：`generate_icons.py` 改为按卡母题（`CARD_MOTIFS`）优先，新增 17 套独立像素母题并加 `RELAY` 系列画法；54 张 64×64 RGBA 图标 SHA-256 全不相同。三语 `echo_freight` 各 743 键且键集一致（新增 51 卡牌键 + 4 参数键 + 3 提示键），写回保持 CRLF、无 BOM。
- **调参**：`EchoCardTuning` 补 `triggerRange` 边界（0.5–128），其余新参数沿用 `*Ticks` / `*range` 既有规则，未放宽任何校验。
- **测试**：新增 `EchoFunctionalCardTest` 10 项（配方组唯一产物、三阵营可抽到新卡、定身免疫与不刷新、护甲单次且不叠层、陷阱数量与比赛 tick 过期、假钥匙不污染货物嗅探、双诱饵原子拒绝、三语键齐备、跨系列不可合成、通道标记单次消费）；`EchoCardRegistryTest` 与 `EchoCardAssetsTest` 计数改为从注册表推导。
- 完整 build 成功，JUnit 全项目 **417 项**，失败/错误/跳过 0。
- **仍未验证（需游戏内）**：12 台新模型在四朝向下的接缝与遮挡、GUI 物品栏缩放下的辨识度；陷阱定身在斜坡/楼梯/载具上的水平锁位移手感；护甲抵消时的客户端受击反馈缺失是否引起误判；中继/直觉方向扇区在高密度对局的信息过载；54 张卡池的抽包分布与高级卡出现率。
