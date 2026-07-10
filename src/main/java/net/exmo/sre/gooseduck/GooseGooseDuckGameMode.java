package net.exmo.sre.gooseduck;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.exmo.sre.gooseduck.role.GooseDuckRoles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.init.ModEffects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 鹅鸭杀游戏模式（Goose Goose Duck / Among Us 式）。
 * <p>
 * 继承 {@link SREMurderGameMode} 以复用其胜负判定（好人全灭 → 鸭胜；鸭全灭 → 鹅胜；超时 → 平局结算）
 * 与被动收益逻辑，仅重写职业分配为「鹅 vs 鸭」，并在服务端 tick 中驱动
 * {@link GooseDuckMeetingDirector 会议 / 投票编排器}。
 * <p>
 * 关键特性：
 * <ul>
 *   <li><b>只刷新小游戏任务</b>：鹅 / 鸭职业（{@link net.exmo.sre.gooseduck.role.GooseDuckRole}）屏蔽全部普通任务，
 *       且本模式关闭 Mood（{@link #hasMood()} 返回 false）；任务完全来自小游戏任务点
 *       （开局运行时打开 {@code minigameQuestEnabled}）。</li>
 *   <li><b>全程俯视视角</b>：开局起给所有存活玩家施加 2D 俯视（{@link ModEffects#TWO_DIMENSIONAL_CAMERA}
 *       等级 4 + 视距）、鼠标指针（{@link ModEffects#POINTER}）与箱庭视野
 *       （{@link ModEffects#HAKONIWA_VISION}），复刻原作的俯视 + 指针操作手感。</li>
 *   <li><b>会议 + 投票</b>：右键尸体上报召开会议（运行时打开 {@code meetingEnabled}），
 *       会议现场施加冻结技能等效果并发起放逐投票。</li>
 *   <li><b>破坏</b>：鸭拥有主动关灯破坏技能（{@link GooseDuckSabotage}），并复用谋杀模式的随机时间事件。</li>
 * </ul>
 */
public class GooseGooseDuckGameMode extends SREMurderGameMode {

    /** 视角效果的续期时长（tick）。每秒补发一次，玩家重连 / 死亡复活也能自愈。 */
    private static final int PERSPECTIVE_DURATION_TICKS = 60;
    /** 2D 视角：4 = 正上方俯视，对应原作的俯视棋盘视野。 */
    private static final int TWO_D_OVERHEAD_AMPLIFIER = 4;
    /** 2D 视距：等级越高相机拉得越远，看清全场。 */
    private static final int TWO_D_DISTANCE_AMPLIFIER = 2;

    public GooseGooseDuckGameMode(ResourceLocation identifier) {
        super(identifier, 10, 4);
    }

    /** 无 Mood 系统：任务完全来自小游戏任务点，SAN 不参与胜负。 */
    @Override
    public boolean hasMood() {
        return false;
    }

    /** 尸体可被查看，配合会议上报机制。 */
    @Override
    public boolean canSeeBodyContent() {
        return true;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        GooseDuckRoles.init();
        GooseDuckMeetingDirector.reset();

        // 本模式依赖「会议」与「小游戏任务」：运行时（内存内、当局有效）为当前地图开启这两项，
        // 使模式自成体系，不强制要求地图预先手动配置。
        AreasWorldComponent.KEY.get(serverWorld).areasSettings.meetingEnabled = true;
        AreasWorldComponent.KEY.get(serverWorld).areasSettings.minigameQuestEnabled = true;

        gameWorldComponent.clearRoleMap();
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");
        // 复用谋杀模式的随机时间事件（关灯 / 卡门锁）作为额外的破坏氛围。
        MurderTimeEventComponent.KEY.get(serverWorld).initializeDefaults();

        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        int duckCount = duckCount(shuffled.size());
        int assignedDucks = 0;
        for (ServerPlayer player : shuffled) {
            SRERole role = assignedDucks < duckCount ? GooseDuckRoles.DUCK : GooseDuckRoles.GOOSE;
            if (role == GooseDuckRoles.DUCK) {
                assignedDucks++;
            }
            gameWorldComponent.addRole(player, role, false);
            if (role.canUseKiller()) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                if (shop.balance < GameConstants.getMoneyStart()) {
                    shop.setBalance(GameConstants.getMoneyStart());
                }
            }
            ServerPlayNetworking.send(player, new AnnounceWelcomePayload(role.getIdentifier().toString(),
                    duckCount, players.size() - duckCount));
            ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }
        gameWorldComponent.syncRoles();
        // 开局立刻切到俯视 + 指针，别等第一次 tick。
        applyPerspectiveEffects(serverWorld);
    }

    /**
     * 全程俯视视角：2D 俯视相机 + 视距 + 鼠标指针 + 箱庭视野。
     * <p>
     * 箱庭视野负责在区块网格级剔除玩家所在房间的屋顶 / 近墙，让俯视直接看到室内——
     * 它是 2D 视角遮挡剔除的正解，永不产生「一片虚空」（未封闭房间会被判为 outside 而完整渲染）。
     * 效果只给存活的游戏内玩家；旁观者保持自由视角。
     */
    private static void applyPerspectiveEffects(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                addPerspectiveEffect(player, ModEffects.TWO_DIMENSIONAL_CAMERA, TWO_D_OVERHEAD_AMPLIFIER);
                addPerspectiveEffect(player, ModEffects.TWO_DIMENSIONAL_CAMERA_DISTANCE, TWO_D_DISTANCE_AMPLIFIER);
                addPerspectiveEffect(player, ModEffects.HAKONIWA_VISION, 0);
                addPerspectiveEffect(player, ModEffects.POINTER, 0);
            } else {
                // 刚死亡的玩家立刻交还自由视角，不必等效果自然过期。
                removePerspectiveEffects(player);
            }
        }
    }

    private static void addPerspectiveEffect(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, PERSPECTIVE_DURATION_TICKS, amplifier, false, false, false));
    }

    private static void clearPerspectiveEffects(ServerLevel serverWorld) {
        for (ServerPlayer player : serverWorld.players()) {
            removePerspectiveEffects(player);
        }
    }

    private static void removePerspectiveEffects(ServerPlayer player) {
        player.removeEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
        player.removeEffect(ModEffects.TWO_DIMENSIONAL_CAMERA_DISTANCE);
        player.removeEffect(ModEffects.HAKONIWA_VISION);
        player.removeEffect(ModEffects.POINTER);
    }

    /** 鸭数量：约每 4 名玩家 1 只鸭，范围 1~3。 */
    private static int duckCount(int playerCount) {
        return Math.max(1, Math.min(3, playerCount / 4));
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 每秒续期一次视角效果（时长 60 tick，留足冗余）。
        if (serverWorld.getGameTime() % 20L == 0L) {
            applyPerspectiveEffects(serverWorld);
        }
        // 先驱动会议 / 投票 / 会议期效果，再走谋杀模式的胜负与被动收益判定。
        GooseDuckMeetingDirector.tick(serverWorld);
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    @Override
    public void stopGame(ServerLevel world) {
        clearPerspectiveEffects(world);
        GooseDuckMeetingDirector.onGameStop(world);
        super.stopGame(world);
    }
}
