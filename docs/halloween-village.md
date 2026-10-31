# 万圣村：接线、行为与独立验收

## 启用与接线

这是**地图活动，不是新游戏模式**。地图 `settings.halloweenEventEnabled=true` 且当前正在运行普通谋杀或 `SREMurderGameMode` 子类时才启用；门、商店、魂契还会检查存活状态和安全期。

```json
{
  "settings": { "halloweenEventEnabled": true },
  "resetPasteArea": { "minX": 0, "minY": 40, "minZ": 0, "maxX": 128, "maxY": 120, "maxZ": 128 }
}
```

固定兑换残月由地图设置的 `halloweenNpcSpawnPos`（地图事件分类，方块坐标）决定，**只生成一个**，不再扫描南瓜灯。

```json
"halloweenNpcSpawnPos": { "x": 35073, "y": 20, "z": 20000 }
```

坐标是残月**脚底所在的方块**，NPC 出现在该方块下沿中心；`(0,0,0)` 视为未设置，此时不生成固定残月（敲门出现的临时残月不受影响）。坐标不要求落在 `resetPasteArea` 内，但该区块必须已加载——区块未加载时不会主动加载，玩家靠近后下一 tick 生成。请在坐标上方留出约 3 格高、3 格宽的玩家魔女和扫帚展示空间（实际交互箱不变）。

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

- 固定残月只按地图坐标生成，**没有分帧方块扫描、`getChunk/loadChunk` 或强制区块票据**：每 tick 只检查坐标所在区块是否已加载，已加载且实例丢失时重建一个。
- 生成一个不可伤害、不存档、不推挤的万圣残月，使用原版 Alex 玩家模型和用户提供的 64×64 本地皮肤，配独立贴图的低帽檐紫黑魔女帽、扫帚与手持南瓜。原“糖果使者”也统一成这个名称和形象，但固定兑换与临时发糖仍是两种服务端交互状态；不改奖励和寿命规则。不使用在线 GameProfile 或下载。见 [模型说明](halloween-canyue-model.md)。
- 右键打开服务端商品快照。界面为棕黑渐变、金色描边、滚动裁剪和 hover 高亮；ESC 返回游戏，无新增按键绑定。
- 万圣残月不直接出售献血锯子（`noellesroles:halloween_blood_saw`）或魂契；旧 `trades` 配置会过滤禁售项，商品展示和实际交易均重新校验，拒绝时不扣费。刀具商店与其他获取途径不变。
- 交易请求只含 NPC UUID、服务端随机 nonce、商品索引；不接受客户端价格、数量或物品。服务端重新验证所属 NPC、同世界、距离 ≤4 格、视线、存活、安全期、商店禁用、金币权限、20 秒会话有效期，并在执行前作废 nonce。
- 南瓜只从主物品栏/快捷栏扣除；金币是 `SREPlayerShopComponent.balance`，不是活动金币道具。先在副本完成南瓜扣费和所有商品装包，成功后在服务端主线程一次提交双币与物品。空间不够、金币不足或职业无金币系统时，完全不扣费，也不在地上丢弃购买商品。扣费腾出的格子可以装商品；合并堆叠保留组件。
- 持 `SOUL_CONTRACT` 右键残月（主副手均支持），只有 `HalloweenCombat.acceptPact` 返回 true 后才消耗一枚。任务与复活时序由战斗模块处理。
- `isNearReaper` 根据实际 NPC 坐标和 `npcKillRadius`（默认 8）计算三维距离，不要求死者仍存活。

### 敲门与临时发糖残月（原糖果使者）

- **潜行、主副手都空手、主手右键小门**敲门。仅支持模组 `SmallDoorBlock`，原版门不触发活动敲门，上下半统一；服务端重做距离、地图范围和视线校验。回调始终 PASS，不拦截正常开门。
- **每位玩家独立奖励冷却**，默认及最低 700 tick / 35 秒；旧配置不足 35 秒时恢复为 35 秒，更长配置保留。同一玩家换门、换维度或重连不重置冷却，不影响其他玩家敲门。
- 有效敲门完成后向敲门者发送原版 title“咚咚……你敲响了门。”，停留 2 秒并播放木门敲击音效；冷却未结束时也用 title 显示剩余秒数，但不播放成功音效。不再使用 actionbar 或 SubtitleHUD，因此不会被锁门的 actionbar 提示覆盖。
- 一次有效敲门：60% 给活动南瓜 1–2 枚；30% 生成 200 tick / 10 秒亡灵；10% 给糖果袋。背包放不下的免费敲门物品会在玩家脚下掉落，不会吞掉。
- 另作独立抽样，默认 20% 出现临时发糖残月，600 tick / 30 秒后移除。**每名临时 NPC 全服只可成功领取一次**，成功后立即消失；满背包不会占用领取机会，可腾出格子重试。奖励缺省一枚糖果，可通过上述配置 API 调整数量。只统一名字和外观，不将其变成商店，也不使固定残月在领糖后消失。

### 亡魂夜（已移除）

- 不再定时触发亡魂夜，不再暗闪或停灯，也不显示旁观者鬼脸。
- 不再将旁观者语音额外转发给活人；原有旁观者语音组规则保持不变。
- 旧的 `nightStartTicks` / `nightDurationTicks` / `nightIntervalTicks` / `nightDarkThreshold` 字段和网络包仅为兼容保留，修改旧配置不会重新启用事件。普通万圣节天空、NPC、敲门、商店和魂契不受影响。

## 必做的联机人工验收（本次未启动客户端）

1. 相同地图关闭开关，以及非谋杀模式：无 NPC、无敲门奖励、无夜晚/活人收听旁观者的变化。
2. 把 `halloweenNpcSpawnPos` 设到指定方块：固定残月出现在该坐标；改成 (0,0,0) 后不再出现；改坐标后旧实例消失、新坐标出现；核对玩家皮肤 UV、帽檐覆盖少量头发且不挡眼、南瓜托在右手。转头时帽子跟头、南瓜跟手、扫帚不跟头。敲门出现的临时残月使用完全相同的名称和模型，领取糖果后消失；固定残月仍正常兑换和签魂契。断网和 F3+T 后贴图正确。
3. 多人同时敲门，各自都能得到一份奖励，互不占用冷却；同一人连敲不同门或门的上下半，35 秒内只奖励一次，跨维度/重连不重置个人冷却。核对成功与剩余冷却提示使用原版 title，成功时有木门敲击声，锁门 actionbar 提示不覆盖 title。普通右键仍开门；安全期、旁观者、超距和隔墙包无奖励。
4. 兑换分别测金币不足、南瓜不足、主背包满、消耗南瓜刚好腾一格、可合并堆叠、无金币职业；失败时两币种和所有物品保持不变。
5. 连发同一 nonce、修改索引为负/超界、替换 NPC UUID、伪造随机 nonce、离 NPC 超过 4 格或切图后重放，均不能取得商品。
6. 双手各持魂契连续交付：资格只建立一次，只消耗一枚；战斗模块拒绝时不消耗。
7. 糖果使者同时被两人右键只能发一份糖；满背包时不消失，成功领取后消失；不领取 30 秒后消失。
8. 持续运行超过原亡魂夜起始及重复时间，确认没有事件提示、暗闪、停灯和鬼脸。
9. 两个真实 SVC 客户端验证旁观者语音不额外传给活人；旁观者原有组语音及普通玩家语音保持原样。

## 不启动 Minecraft 的独立逻辑测试

覆盖夜晚边界、60/30/10 精确分桶、冷却边界、完整扣费腾格、失败不改原背包、不可堆叠物品、10,000 组随机交易守恒、鬼声不改原 PCM 和普通频道旁路。JDK 21 即可运行，无 Gradle、无新依赖；不会编译整个项目。

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
