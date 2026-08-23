/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role_data.killer;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.role.ModRoles;

public class BanditRoleData extends SimpleRoleData {

    /**
     * 构造函数
     */
    public BanditRoleData(RoleDataContext context) {
        super(context);
    }


    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 处理强盗击杀目标时的金钱盗取
     * 
     * @param victim 被杀的受害者
     */
    public void handleKilledVictim(Player victim) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        if (!(victim instanceof ServerPlayer victimPlayer))
            return;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.BANDIT))
            return;
        ConfigWorldComponent.onPlayerUsedSkill( serverPlayer);

        // 获取受害者的金钱
        SREPlayerShopComponent victimShop = SREPlayerShopComponent.KEY.get(victim);
        int victimBalance = victimShop.balance;

        if (victimBalance > 0) {
            // 盗取受害者一半的钱
            int stolenAmount = victimBalance / 2;
            
            // 减少受害者一半的钱
            victimShop.balance = victimBalance / 2;
            victimShop.sync();
            
            // 增加强盗的金钱
            SREPlayerShopComponent killerShop = SREPlayerShopComponent.KEY.get(player);
            killerShop.balance += stolenAmount;
            killerShop.sync();

            // 通知强盗
            serverPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.bandit.stole_money",
                            victim.getName().getString(),
                            stolenAmount)
                            .withStyle(ChatFormatting.GOLD),
                    true);

            // 通知受害者
            victimPlayer.displayClientMessage(
                    Component.translatable("message.noellesroles.bandit.lost_money",
                            stolenAmount)
                            .withStyle(ChatFormatting.RED),
                    true);
        }
    }

    /**
     * 同步到客户端
     */
    @Override
    public void serverTick() {
        // 强盗组件不需要每tick处理
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
    }
}
