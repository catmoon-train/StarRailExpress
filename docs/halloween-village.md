# 万圣村：接线、行为与独立验收

## 启用与接线

这是**地图活动，不是新游戏模式**。地图 `settings.halloweenEventEnabled=true` 且当前正在运行普通谋杀或 `SREMurderGameMode` 子类时才启用；门、商店、魂契还会检查存活状态和安全期。

```json
{
  "settings": { "halloweenEventEnabled": true },
  "resetPasteArea": { "minX": 0, "minY": 40, "minZ": 0, "maxX": 128, "maxY": 120, "maxZ": 128 }
}
```

在目标 `resetPasteArea` 内放置两个原版南瓜灯或 `SREBlocks.JACK_O_LANTERN`。不要求中心点。范围采用 AABB 的 `[min,max)` 坐标；请给灯上方留出玩家模型空间。

总入口需要做的接线（本模块不修改共享入口、语言文件或 mixin JSON）：

1. 公共初始化调用一次 `HalloweenVillage.register()`，**必须在注册表冻结前**；NPC 注册表和默认属性由本模块自行注册，不改 `ModEntities`。
2. 客户端初始化调用一次 `HalloweenVillageClient.register()`。
3. 活动运行器每世界 tick 调用 `HalloweenVillage.tick(ServerLevel)`，包括关闭活动时的 tick；本类有同 tick 去重。地图切换前可主动调用 `clear`，本类也会比较 `mapName/resetPasteArea`，并监听停局、卸载和最后一位玩家断线。
4. `noellesroles.mixins.json` 的 `mixins` 增加 `halloween.village.VillageSoulTrackingMixin`；`client` 增加 `halloween.village.VillageLightTextureMixin`。客户端 mixin **不能**放公共列表。
5. 合并交付的 `halloween-village.zh_cn.json` / `halloween-village.en_us.json` 翻译。
6. 如需文件配置糖果数量，在总配置增加 `candyNpcRewardCount=1`，加载/重载后调用 `HalloweenVillage.configureCandyReward(count)`（有效范围 1..64）。现有约定的配置字段不包含此字段，所以本模块提供接线 API，而不越权修改配置类。
7. 近残月击杀奖励和死者亡灵由 `HalloweenCombat` 负责；它已经调用 `isNearReaper(victim)` 并奖励一枚南瓜。本模块**不再注册第二次击杀奖励**，避免双倍发放。

语音已直接接入现有两个插件，不需要添加 voicechat 插件入口：

- `NoellesrolesVoiceChatPlugin.paranoidEvent` 在沉默、准备/结束阶段检查之后调用 `VillageGhostVoice.forward`。
- `VoiceExtraEffectsPlugin.onClientSound` 在查找说话者实体之前调用 `VillageGhostAudio.transform`。

## 已实现的规则

### 残月与交易

- 分帧扫描只读取已加载区块的 `getChunkNow`，**没有 `getChunk/loadChunk` 或强制区块票据**。每 tick 最多读取 `min(scanBlocksPerTick,65536)` 个方块、探测 64 个区块候选；玩家附近 3×3 区块优先，每区块保存扫描进度。未加载区块在后续有限遍历中重试，找到两个灯即停止扫描。
- 两处各生成一个不可伤害、不存档、不推挤的玩家模型 NPC。渲染器直接绑定本地 `starrailexpress:textures/block/plush/canyuesama.png`，标准 64×64 玩家 UV；不使用 skull、在线 GameProfile 或 Mojang 下载。
- 右键打开服务端商品快照。界面为棕黑渐变、金色描边、滚动裁剪和 hover 高亮；ESC 返回游戏，无新增按键绑定。
- 万圣残月不直接出售献血锯子（`noellesroles:halloween_blood_saw`）或魂契；旧 `trades` 配置会过滤禁售项，商品展示和实际交易均重新校验，拒绝时不扣费。刀具商店与其他获取途径不变。
- 交易请求只含 NPC UUID、服务端随机 nonce、商品索引；不接受客户端价格、数量或物品。服务端重新验证所属 NPC、同世界、距离 ≤4 格、视线、存活、安全期、商店禁用、金币权限、20 秒会话有效期，并在执行前作废 nonce。
- 南瓜只从主物品栏/快捷栏扣除；金币是 `SREPlayerShopComponent.balance`，不是活动金币道具。先在副本完成南瓜扣费和所有商品装包，成功后在服务端主线程一次提交双币与物品。空间不够、金币不足或职业无金币系统时，完全不扣费，也不在地上丢弃购买商品。扣费腾出的格子可以装商品；合并堆叠保留组件。
- 持 `SOUL_CONTRACT` 右键残月（主副手均支持），只有 `HalloweenCombat.acceptPact` 返回 true 后才消耗一枚。任务与复活时序由战斗模块处理。
- `isNearReaper` 根据实际 NPC 坐标和 `npcKillRadius`（默认 8）计算三维距离，不要求死者仍存活。

### 敲门与糖果使者

- **潜行、主副手都空手、主手右键小门**敲门。仅支持模组 `SmallDoorBlock`，原版门不触发活动敲门，上下半统一；服务端重做距离、地图范围和视线校验。回调始终 PASS，不拦截正常开门。
- **所有玩家、所有维度共用一次奖励冷却**，默认 300 tick / 15 秒，不是每人、每门各自冷却。
- 有效敲门完成后向敲门者发送屏幕顶部的 `SubtitleHUD` 提示“咚咚……你敲响了门。”，持续 3 秒，无背景、无打字机效果；冷却未结束时也用同样的无背景 HUD 显示剩余秒数。
- 一次有效敲门：60% 给活动南瓜 1–2 枚；30% 生成 200 tick / 10 秒亡灵；10% 给糖果袋。背包放不下的免费敲门物品会在玩家脚下掉落，不会吞掉。
- 另作独立抽样，默认 20% 出现糖果使者，600 tick / 30 秒后移除。**一个使者全服只可成功领取一次**，成功后立即消失；满背包不会占用领取机会，可腾出格子重试。奖励缺省一枚糖果，可通过上述配置 API 调整数量。

### 亡魂夜与真实语音

- 默认第 2400 tick / 120 秒开始，持续 1200 tick / 60 秒，每 3600 tick / 180 秒开始一次（因此第二次为开局 300 秒）。全部读取服务端配置。
- 客户端 lightmap 像素暗闪，偶尔连续停灯约两秒；不改 LIT/ACTIVE，不写永久或临时方块状态，所以不存在停局、断线或崩溃后灯没有恢复的问题。失效后下一次 lightmap 更新恢复原像素。
- 服务端同步真正旁观者实体；客户端仅在自己和旁观者所在位置的原始环境亮度均 ≤`nightDarkThreshold`（默认 5）、距离 ≤64 格时画半透明鬼魂脸。没有伪造随机玩家魂魄；视觉暗闪不改变逻辑亮度门槛。
- SVC 默认旁观者组不会把语音发给活人，因此服务端在亡魂夜额外转发**实际麦克风 Opus 包**给附近活动活人，原旁观者组仍保留。距离使用 `min(SVC语音范围,64)`，服从现有 `shouldBanVoice` 的耳聋、隔离、时停、鹈鹕等规则及会议禁言。
- 主线程发布不可变 UUID/坐标路由，语音线程不读取活动世界；快照 500ms 无更新即失效。额外转发使用带标记的独立频道，听者即使尚未收到新死者实体/夜晚名单，也会处理第一帧语音，不泄漏清晰原声。
- 听者侧 PCM 两级低通、31Hz 环形调制与短回声将人声变成模糊鬼叫；不拿随机音效冒充语音，也不把 OpenAL 滤波器挂到世界音效上。普通局不产生额外路由，未标记/未授权的音频逐位原样返回。

## 必做的联机人工验收（本次未启动客户端）

1. 相同地图关闭开关，以及非谋杀模式：无 NPC、无敲门奖励、无夜晚/活人收听旁观者的变化。
2. 两种南瓜灯各一个：两名残月分别出现；核对头、衣服、双臂双腿和外层皮肤的 64×64 UV，断网运行依然正确。
3. 多人同时敲不同门和同一扇门上下半，奖励总计最多一份；15 秒后才有下一份。普通右键仍开门；安全期、旁观者、超距和隔墙包无奖励。
4. 兑换分别测金币不足、南瓜不足、主背包满、消耗南瓜刚好腾一格、可合并堆叠、无金币职业；失败时两币种和所有物品保持不变。
5. 连发同一 nonce、修改索引为负/超界、替换 NPC UUID、伪造随机 nonce、离 NPC 超过 4 格或切图后重放，均不能取得商品。
6. 双手各持魂契连续交付：资格只建立一次，只消耗一枚；战斗模块拒绝时不消耗。
7. 糖果使者同时被两人右键只能发一份糖；满背包时不消失，成功领取后消失；不领取 30 秒后消失。
8. 夜晚亮度 5/6 边界测试，亮处不显示，暗处显示实际旁观者的脸；把旁观者移动，鬼魂随真实位置移动；切图/停局/重连灯光恢复且无 NPC 残留。
9. 两个真实 SVC 客户端：死者入旁观组并说一段可辨认的话，活人夜晚附近听到对应节奏但模糊的鬼声；停止说话后不应凭空产生鬼叫。夜晚结束或距离超出范围后不再转发；旁观者间原组语音、普通局活人语音保持原样；会议禁言和耳聋仍有效。

## 不启动 Minecraft 的独立逻辑测试

覆盖夜晚边界、60/30/10 精确分桶、共享冷却边界、完整扣费腾格、失败不改原背包、不可堆叠物品、10,000 组随机交易守恒、鬼声不改原 PCM 和普通频道旁路。JDK 21 即可运行，无 Gradle、无新依赖；不会编译整个项目。

把下方 `java self-test` 块提取到临时目录，和 `VillageRules.java`、`VillageInventory.java`、`VillageGhostAudio.java` 一起 `javac -encoding UTF-8 -d <临时classes>`，再 `java -cp <临时classes> VillageLogicTest`。

```java self-test
import java.util.*;
import org.agmas.noellesroles.game.halloween.village.*;
public class VillageLogicTest {
    static int assertions;
    static void check(boolean b) { assertions++; if (!b) throw new AssertionError("check " + assertions); }
    record Stack(String id, int count, int max) {}
    static final VillageInventory.Slots<Stack> SLOTS = new VillageInventory.Slots<>() {
        public int count(Stack s) { return s.count; }
        public int limit(Stack s) { return s.max; }
        public boolean currency(Stack s) { return s.count > 0 && s.id.equals("pumpkin"); }
        public boolean matches(Stack a, Stack b) { return a.id.equals(b.id); }
        public Stack withCount(Stack s, int n) { return new Stack(s.id,n,s.max); }
    };
    static int total(List<Stack> a,String id) { return a.stream().filter(s->s.id.equals(id)).mapToInt(Stack::count).sum(); }
    public static void main(String[] args) {
        for (long t : new long[]{2399,3600,5999,7200}) check(!VillageRules.isNight(t,2400,1200,3600));
        for (long t : new long[]{2400,3599,6000,7199}) check(VillageRules.isNight(t,2400,1200,3600));
        check(!VillageRules.isNight(100,0,0,1));
        int[] rolls=new int[3]; for(int i=0;i<100;i++) rolls[VillageRules.knockReward(i)]++;
        check(Arrays.equals(rolls,new int[]{60,30,10}));
        check(!VillageRules.cooldownReady(299,300)); check(VillageRules.cooldownReady(300,300));
        var reward=new Stack("candy",1,64); var pay=List.of(new Stack("pumpkin",3,64));
        var exact=VillageInventory.plan(pay,3,reward,1,SLOTS);
        check(exact!=null && total(exact,"pumpkin")==0 && total(exact,"candy")==1);
        check(total(pay,"pumpkin")==3);
        check(VillageInventory.plan(pay,2,reward,1,SLOTS)==null);
        check(VillageInventory.plan(pay,4,reward,1,SLOTS)==null);
        check(VillageInventory.plan(pay,-1,reward,1,SLOTS)==null);
        check(VillageInventory.plan(pay,1,reward,0,SLOTS)==null);
        var merge=VillageInventory.plan(List.of(new Stack("candy",63,64)),0,reward,1,SLOTS);
        check(merge!=null && total(merge,"candy")==64);
        check(VillageInventory.plan(List.of(new Stack("candy",63,64)),0,reward,2,SLOTS)==null);
        var tools=VillageInventory.plan(List.of(new Stack("",0,64),new Stack("",0,64)),0,new Stack("tool",1,1),2,SLOTS);
        check(tools!=null && tools.get(0).count==1 && tools.get(1).count==1);
        Random random=new Random(5472);
        for(int trial=0;trial<10000;trial++) {
            List<Stack> input=new ArrayList<>();
            for(int i=0;i<36;i++) { String id=List.of("pumpkin","candy","stone","").get(random.nextInt(4)); input.add(new Stack(id,id.isEmpty()?0:1+random.nextInt(64),64)); }
            List<Stack> before=List.copyOf(input); int price=random.nextInt(130),amount=1+random.nextInt(130);
            var plan=VillageInventory.plan(input,price,reward,amount,SLOTS); check(input.equals(before));
            if(plan!=null) {
                check(total(plan,"pumpkin")==total(input,"pumpkin")-price);
                check(total(plan,"candy")==total(input,"candy")+amount);
                check(total(plan,"stone")==total(input,"stone"));
                check(plan.stream().allMatch(s->s.count>=0 && s.count<=s.max));
            }
        }
        UUID speaker=UUID.randomUUID(); short[] pcm=new short[960];
        for(int i=0;i<pcm.length;i++) pcm[i]=(short)(14000*Math.sin(i*.2));
        short[] original=pcm.clone(); VillageGhostAudio.clear();
        check(VillageGhostAudio.transform(speaker,pcm)==pcm);
        UUID channel=VillageGhostAudio.channel(speaker); check(VillageGhostAudio.isGhostChannel(channel));
        short[] ghost=VillageGhostAudio.transform(channel,pcm);
        check(ghost.length==pcm.length && !Arrays.equals(ghost,pcm)); check(Arrays.equals(pcm,original));
        boolean nonzero=false; for(short n:ghost) nonzero|=n!=0; check(nonzero);
        VillageGhostAudio.setAllowed(Set.of(speaker)); check(VillageGhostAudio.transform(speaker,pcm)!=pcm);
        VillageGhostAudio.clear(); check(VillageGhostAudio.transform(speaker,pcm)==pcm);
        System.out.println("Village pure logic: PASS ("+assertions+" assertions, 10,000 transaction cases)");
    }
}
```

本次还以本地已缓存的 Minecraft/Fabric/SVC 依赖、现有项目 class 文件及四个约定活动 API 的临时桩，对本模块和改动的两个语音文件进行了**限定源文件 javac 检查**。此检查验证 Java/API 签名，不代替父代理的整合构建、mixin 应用和上述联机验收。
