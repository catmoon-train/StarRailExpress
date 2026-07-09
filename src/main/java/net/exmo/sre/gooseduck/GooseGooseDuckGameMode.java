package net.exmo.sre.gooseduck;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.MurderTimeEventComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.exmo.sre.gooseduck.role.GooseDuckRoles;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;

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
 *   <li><b>会议 + 投票</b>：右键尸体上报召开会议（运行时打开 {@code meetingEnabled}），
 *       会议现场施加 2D 俯视视角 + 冻结技能等效果并发起放逐投票。</li>
 *   <li><b>破坏</b>：鸭拥有主动关灯破坏技能（{@link GooseDuckSabotage}），并复用谋杀模式的随机时间事件。</li>
 * </ul>
 */
public class GooseGooseDuckGameMode extends SREMurderGameMode {

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
    }

    /** 鸭数量：约每 4 名玩家 1 只鸭，范围 1~3。 */
    private static int duckCount(int playerCount) {
        return Math.max(1, Math.min(3, playerCount / 4));
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 先驱动会议 / 投票 / 会议期效果，再走谋杀模式的胜负与被动收益判定。
        GooseDuckMeetingDirector.tick(serverWorld);
        super.tickServerGameLoop(serverWorld, gameWorldComponent);
    }

    @Override
    public void stopGame(ServerLevel world) {
        GooseDuckMeetingDirector.onGameStop(world);
        super.stopGame(world);
    }
}
