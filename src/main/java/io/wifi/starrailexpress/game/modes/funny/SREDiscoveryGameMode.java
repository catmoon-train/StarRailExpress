package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.init.ModItems;

import java.util.List;

/**
 * 游客模式 (sre:discovery)
 * <p>
 * 模式规则：
 * - 所有玩家的职业均为"游客"
 * - 不会刷新修饰符
 * - 所有人拥有一把万能钥匙
 * - 游戏时间为 20 分钟
 * - 只能通过倒计时归零来结束游戏
 * </p>
 */
public class SREDiscoveryGameMode extends SREMurderGameMode {
    public SREDiscoveryGameMode(ResourceLocation identifier) {
        super(identifier, 20, 1);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 重置角色配置
        Harpymodloader.refreshRoles();

        // 清空角色映射
        gameWorldComponent.clearRoleMap();

        // 将所有玩家添加到队伍中
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");

        // 执行游戏开始时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");

        int killerCount = 0;

        // 所有玩家分配为游客（DISCOVERY_CIVILIAN），并发放万能钥匙
        for (ServerPlayer player : players) {
            gameWorldComponent.addRole(player, TMMRoles.DISCOVERY_CIVILIAN, false);
            // 给予万能钥匙
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.MASTER_KEY));
        }

        // 同步角色信息
        gameWorldComponent.syncRoles();

        // 发送职业公告并触发事件
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                ServerPlayNetworking.send(player,
                        new AnnounceWelcomePayload(role.getIdentifier().toString(), killerCount,
                                players.size() - killerCount));
                ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
            }
        }

        // 清理强制角色/修饰符（游客模式不使用这些）
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 只检查倒计时，无视击杀/存活的常规胜负条件
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }
}
