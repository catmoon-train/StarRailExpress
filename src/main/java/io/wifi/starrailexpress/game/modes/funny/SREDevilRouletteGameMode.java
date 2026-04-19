package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 轮盘赌锦标赛
 * <p>
 *     - 模式特性：玩家两两分组先后进行多轮赛
 *          每轮后可购买道具（根据本轮剩余生命值获得金币）：包括局内道具（便宜，仅对局内使用增加获胜可能性）和场外道具（较贵，如一次性手枪直接打死对手相当于掀桌子）
 *     - 局内死亡条件：生命值不足将死亡，或被对手使用场外道具击杀
 *     - 局外死亡条件：死亡次数累计到一定值死亡旁观
 * </p>
 */
public class SREDevilRouletteGameMode extends GameMode {
    /**
     * @param identifier       the game mode identifier
     * @param defaultStartTime the default time at which the timer will be set at
     *                         the start of the game mode, in minutes
     * @param minPlayerCount   the minimum amount of players required to start the
     *                         game mode
     */
    public SREDevilRouletteGameMode(ResourceLocation identifier, int defaultStartTime, int minPlayerCount) {
        super(identifier, defaultStartTime, minPlayerCount);
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {

    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {

    }
}
