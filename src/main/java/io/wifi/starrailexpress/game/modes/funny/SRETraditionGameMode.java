package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 传统模式 (starrailexpress:tradition)
 * <p>
 * 模式规则：
 * - 所有杀手均为"杀手"职业
 * - 所有警长均为"义警"职业
 * - 所有平民均为"平民"职业
 * - 不会生成任何中立职业
 * - 义警没有商店
 * - 没有修饰符
 * </p>
 */
public class SRETraditionGameMode extends SREMurderGameMode {

    /** 保存被移除的义警商店条目，用于游戏结束时恢复 */
    private List<ShopEntry> savedVigilanteShop = null;

    public SRETraditionGameMode(ResourceLocation identifier) {
        super(identifier);
    }

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        // 移除义警的商店条目（传统模式下义警不拥有商店）
        savedVigilanteShop = ShopContent.customEntries.remove(TMMRoles.VIGILANTE.getIdentifier());

        // 重置角色配置
        Harpymodloader.refreshRoles();

        // 清空角色映射
        gameWorldComponent.clearRoleMap();

        // 将所有玩家添加到队伍中
        addPlayersToTeam(serverWorld.getServer().createCommandSourceStack(), players, "harpymodloader_game");

        // 执行游戏开始时的函数
        executeFunction(serverWorld.getServer().createCommandSourceStack(), "harpymodloader:start_game");

        // 确保义警数量上限足够
        Harpymodloader.setRoleMaximum(TMMRoles.VIGILANTE.getIdentifier(), 100);

        // 计算各阵营人数
        int killerCount = RoleCountManager.getKillerCount(players.size());
        int vigilanteCount = RoleCountManager.getVigilanteCount(players.size());
        // 中立数量设为 0

        // 打乱玩家顺序，确保公平分配
        List<ServerPlayer> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);

        int index = 0;

        // 分配杀手（TMMRoles.KILLER）
        for (int i = 0; i < killerCount && index < shuffled.size(); i++, index++) {
            ServerPlayer player = shuffled.get(index);
            gameWorldComponent.addRole(player, TMMRoles.KILLER, false);
            // 给杀手发放起始金钱
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
            if (playerShopComponent.balance < GameConstants.getMoneyStart())
                playerShopComponent.setBalance(GameConstants.getMoneyStart());
        }

        // 分配警长（TMMRoles.VIGILANTE）
        for (int i = 0; i < vigilanteCount && index < shuffled.size(); i++, index++) {
            ServerPlayer player = shuffled.get(index);
            gameWorldComponent.addRole(player, TMMRoles.VIGILANTE, false);
        }

        // 剩余玩家分配为平民（TMMRoles.CIVILIAN）
        while (index < shuffled.size()) {
            ServerPlayer player = shuffled.get(index);
            gameWorldComponent.addRole(player, TMMRoles.CIVILIAN, false);
            index++;
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

        // 清理强制角色/修饰符（传统模式不使用这些）
        Harpymodloader.FORCED_MODDED_ROLE.clear();
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
        Harpymodloader.FORCED_MODDED_MODIFIER.clear();
        PlayerRoleWeightManager.ForcePlayerTeam.clear();
    }

    @Override
    public void finalizeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        // 恢复义警的商店条目
        if (savedVigilanteShop != null) {
            ShopContent.customEntries.put(TMMRoles.VIGILANTE.getIdentifier(), savedVigilanteShop);
            savedVigilanteShop = null;
        }
        super.finalizeGame(serverWorld, gameWorldComponent);
    }
}
