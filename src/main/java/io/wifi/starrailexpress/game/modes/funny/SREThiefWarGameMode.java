package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.List;
import java.util.OptionalInt;

/**
 * 小偷大战
 * <p>
 * 模式特性：
 *  - 全员小偷
 *  - 胜利条件：活到最后或者攒够资金美美撤离
 *  - 模式附带随机事件提升游戏可玩性
 * </p>
 * <p>
 * 随机事件列表：
 *  - 物品雨：游玩区域内随机降下随机物品
 *  - 随机恋人事件：有人可能会被组为恋人
 *  - 转职事件：花费资金转职成其他中立
 *  - 佛说：你说我做，当没有佛说则是反向你说我做
 *  - 盗窃违法事件：当在摄像头前方一定范围内一定时间触发
 *  - 保安返厂：死亡的部分玩家复活为保安（根据在场人数），当玩家在60s内进行过偷窃行为则可被击杀，否则会导致小脑
 * </p>
 */
public class SREThiefWarGameMode extends SREBaseCustomizationGameMode {
    /**
     * @param identifier       游戏的id
     */
    public SREThiefWarGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
    }

    @Override
    protected void initItemList() {
        sharedItems.add(TMMItems.CROWBAR::getDefaultInstance);
    }

    @Override
    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            gameWorldComponent.addRole(player, ModRoles.THIEF);
        }
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
                               List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        assignModdedRole(players, gameWorldComponent);

        // 初始化启动资金：获得目标的一半
        for (Player player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            if (role == ModRoles.THIEF) {
                SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                ThiefPlayerComponent thief = ThiefPlayerComponent.KEY.get(player);
                shop.setBalance(thief.honorCost / 2);
            }
        }
    }
    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;
        // 统计小偷数量
        int thiefCount = 0;
        for (Player player : serverWorld.players()) {
            if (gameWorldComponent.isRole(player, ModRoles.THIEF) && !GameUtils.isPlayerEliminated(player)) {
                ++thiefCount;
            }
        }

        // 没有小偷存活则无人获胜
        if (thiefCount == 0) {
            winStatus = GameUtils.WinStatus.NO_PLAYER;
        }
        // 小偷数量为1则小偷获胜
        else if (thiefCount == 1) {
            RoleUtils.customWinnerWin(serverWorld, GameUtils.WinStatus.CUSTOM, "thief",
                    OptionalInt.of(new java.awt.Color(255, 215, 0).getRGB()));
        }
        else {
            Player winner = null;
            for (Player player : serverWorld.players()) {
                if (!GameUtils.isPlayerEliminated(player) && gameWorldComponent.isRole(player, ModRoles.THIEF)) {
                    ItemStack heldItem = player.getMainHandItem();
                    // 检查小偷是否手持小偷的荣誉（金锭）睡觉
                    if (heldItem.is(Items.GOLD_INGOT) && player.isSleeping()) {
                        winner = player;
                        break;
                    }
                }
            }
            if (winner != null) {
                for (Player player : serverWorld.players()) {
                    if (player != winner)
                        GameUtils.killPlayer(player, true, winner,  Noellesroles.id("heart_attack"), true);
                }
            }
        }

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;
        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }
}
