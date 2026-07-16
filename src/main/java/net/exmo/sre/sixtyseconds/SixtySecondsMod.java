package net.exmo.sre.sixtyseconds;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 末日60秒模式的引导装配：通过公开入口 {@link SREGameModes#registerGameMode} 注册模式，
 * <b>不改动</b> {@code io.wifi} 内任何文件；仅需在 {@code Noellesroles.onInitialize} 里调用一次 {@link #init()}
 * （与 {@code GooseDuckMod.init()} 一致）。
 */
public final class SixtySecondsMod {
    /** 模式 ID：{@code sre:sixty_seconds}，可用 {@code /tmm:start sre:sixty_seconds} 启动。 */
    public static final ResourceLocation MODE_ID = StarRailExpressID.shortId("sixty_seconds");

    /** 注册后的模式实例（init 后非空）。 */
    public static GameMode MODE;

    /** 本模式是否正在进行（供无世界上下文的 mixin 判断，如食物不可堆叠）。开局置 true，结束置 false。 */
    public static volatile boolean RUNNING = false;

    private SixtySecondsMod() {
    }

    public static void init() {
        SixtySecondsCreativeTab.register(); // 统一创造标签页（须在物品入页前注册）
        MODE = SREGameModes.registerGameMode(new SixtySecondsGameMode(MODE_ID));
        net.exmo.sre.sixtyseconds.arena.SixtySecondsArena.registerEntityClearWindow(); // 开局清卸载区块里的残留尸体/掉落物
        net.exmo.sre.sixtyseconds.logic.SixtySecondsHealthSystem.register();
        net.exmo.sre.sixtyseconds.logic.SixtySecondsMonsterSystem.registerEvents();
        net.exmo.sre.sixtyseconds.logic.SixtySecondsStations.register(); // 合成台绑定（书桌/灶台/浴缸）
        net.exmo.sre.sixtyseconds.logic.SixtySecondsCorpseLoot.register(); // 死亡物品装入尸体箱可搜刮
        net.exmo.sre.sixtyseconds.logic.SixtySecondsLootSearch.register(); // 物资箱搜刮全局推进（游戏外也生效）
        net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.register(); // 夜袭者死亡掉废料
        net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem.register(); // PVE 游荡怪死亡掉废料
        net.exmo.sre.sixtyseconds.logic.SixtySecondsReconnect.register(); // 掉线备份/重连恢复（背包+状态）
        net.exmo.sre.sixtyseconds.logic.SixtySecondsRockets.register(); // RPG 火箭投射物全局推进
        net.exmo.sre.sixtyseconds.logic.SixtySecondsAirdrop.register(); // 指令空投下落动画全局推进
        net.exmo.sre.sixtyseconds.content.item.SixtySecondsRopeItem.register(); // 临时绳索到期清除
        net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrapplingHookItem.register(); // 钩锁荡索摔落保护
        net.exmo.sre.sixtyseconds.logic.SixtySecondsProximityChat.register(); // 邻近聊天（只有附近玩家能看到）
        net.exmo.sre.sixtyseconds.content.block.SixtySecondsBaseUtilityBlock.register(); // 基地报警器/玩偶/次声波音响
        net.exmo.sre.sixtyseconds.logic.SixtySecondsMystic.register(); // 神秘技术：复活图腾右键尸体
        net.exmo.sre.sixtyseconds.island.SixtySecondsIslands.register(); // 海岛远征：收音机侦听岛屿情报
        registerDropRule(); // 本模式放行丢弃物品（全局默认禁丢，见 KeyBindingMixin/DropRules）
        registerChatHudRule(); // 本模式放行聊天栏渲染（存活玩家默认被 ChatHudMixin 隐藏）
    }

    /**
     * 允许本模式存活玩家看到聊天栏：{@code ChatHudMixin} 默认对局内存活玩家隐藏聊天渲染
     * （仅 {@code ChatHudRules} 放行的职业/玩家可见），而 60s 有邻近聊天玩法
     * （{@code SixtySecondsProximityChat}），聊天栏被隐藏时消息根本看不到。
     */
    private static void registerChatHudRule() {
        io.wifi.starrailexpress.rules.ChatHudRules.canUseChatHudPlayer.add(
                player -> player != null && isActive(player.level()));
    }

    /**
     * 允许玩家在本模式丢弃物品：全局默认禁止丢弃（{@code KeyBindingMixin} 抑制丢弃键），
     * 这里往 {@link io.wifi.starrailexpress.rules.DropRules#canDrop} 加一条本模式放行规则。
     * 但<b>不放行屏障占位</b>——{@code SixtySecondsInventoryLimit} 用 BARRIER 填充受限槽（准备阶段可能落到快捷栏），
     * 否则玩家会把占位符扔进世界。
     */
    private static void registerDropRule() {
        io.wifi.starrailexpress.rules.DropRules.canDrop.add(player -> {
            if (player == null || !isActive(player.level())) {
                return false;
            }
            return !player.getMainHandItem().is(net.minecraft.world.item.Items.BARRIER);
        });
    }

    /** 当前世界是否正在运行本模式。 */
    public static boolean isActive(Level level) {
        return MODE != null && SREGameWorldComponent.KEY.get(level).getGameMode() == MODE;
    }
}
