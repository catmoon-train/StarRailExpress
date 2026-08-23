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

package org.agmas.noellesroles.role_data.special;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class BetterVigilanteRoleData extends SimpleRoleData {

    /** 组件键 - 用于从玩家获取此组件 */


    // ==================== 状态变量 ====================


    /** 是否已激活"最后防线"能力 */
    public boolean lastStandActivated = false;

    /** 是否是活跃的红海军 */

    /** 检查间隔计时器（每20tick检查一次，减少性能消耗） */
    public static int checkTimer = 0;


    /**
     * 构造函数
     */
    public BetterVigilanteRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.lastStandActivated = false;

        this.sync();
    }

    @Override
    public void clear() {
        clearAll();
    }

    /**
     * 清除所有状态
     */
    public void clearAll() {
        this.lastStandActivated = false;

        this.sync();
    }

    /**
     * 检查是否是活跃的红海军
     */
    public boolean isActiveBetterVigilante() {
        if (player == null)
            return false;
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        return gameWorld.isRole(player, ModRoles.BETTER_VIGILANTE);
    }

    /**
     * 检查场上存活的平民和义警数量
     * 
     * @return 平民+义警的存活数量
     */
    public int countAliveCiviliansAndVigilantes() {
        if (player.level().isClientSide())
            return 0;

        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        ServerLevel serverWorld = (ServerLevel) player.level();

        int count = 0;
        for (Player p : serverWorld.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;

            SRERole role = gameWorld.getRole(p);
            if (role == null)
                continue;

            // 检查是否是平民或义警（isInnocent = true 且 canUseKiller = false 的角色）
            // 这包括：平民、义警、以及其他乘客阵营角色
            if (role.isInnocent()) {
                count++;
            }
        }

        return count;
    }

    /**
     * 激活"最后防线"能力
     * 给予武器
     */
    public void activateLastStand() {
        if (lastStandActivated)
            return;
        if (!(player instanceof ServerPlayer serverPlayer))
            return;

        lastStandActivated = true;
        ConfigWorldComponent.onPlayerUsedSkill( serverPlayer);
        // 给予武器
        // 一把德林加手枪
        player.getInventory().add(new ItemStack(TMMItems.DERRINGER));
        // 一个手雷
        player.getInventory().add(new ItemStack(TMMItems.GRENADE));

        // 播放激活音效
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.TRIDENT_THUNDER.value(),
                SoundSource.MASTER, 2.0F, 0.8F);

        // 发送激活消息
        serverPlayer.displayClientMessage(
                Component.translatable("message.noellesroles.better_vigilante.last_stand_activated")
                        .withStyle(style -> style.withColor(0xFF0000).withBold(true)),
                true);

        this.sync();
    }

    /**
     * 同步到客户端
     */

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 只有活跃的红海军才需要检测
        if (!isActiveBetterVigilante())
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;

        // 如果已经激活了最后防线，不需要再检测
        if (lastStandActivated)
            return;

        // 每秒检查一次（20 tick）
        ++checkTimer;
        if (checkTimer % 20 == 0) {

            int aliveCount = countAliveCiviliansAndVigilantes();
            if (aliveCount <= 2) {
                activateLastStand();
            }
        }
        // 检查场上平民+义警数量

        // 当存活的平民+义警数量 <= 2 时激活

    }

    // ==================== NBT 序列化 ====================



    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("lastStandActivated", this.lastStandActivated);

    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.lastStandActivated = tag.contains("lastStandActivated") && tag.getBoolean("lastStandActivated");

    }
}
