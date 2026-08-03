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

package org.agmas.noellesroles.role_data.vigilante;

import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

public class HoanMeirinRoleData extends SimpleRoleData {

    public HoanMeirinRoleData(RoleDataContext context) {
        super(context);
    }

    // 技能冷却时间（tick）
    public int cooldown = 0;
    public int loneyTime = 0;

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        SREArmorPlayerComponent.KEY.get(player).setArmor(1);
    }

    @Override
    public void clear() {
        SREArmorPlayerComponent.KEY.get(player).setArmor(0);
    }

    /**
     * 设置冷却时间
     * 
     * @param ticks 冷却时间（tick），20 tick = 1 秒
     */
    public void setCooldown(int ticks) {
        this.cooldown = ticks;
        this.sync();
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }
    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 服务端每 tick 减少冷却时间
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        boolean shouldSync = false;
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每20秒同步一次（而不是每 tick），减少网络压力
            if (this.cooldown % 400 == 0 || this.cooldown == 0) {
                shouldSync = true;
            }
        }
        if (GameUtils.isPlayerAliveAndSurvival(player)) {
            int nearByPlayerCount = 0;
            for (var p : this.player.level().players()) {
                if (!GameUtils.isPlayerAliveAndSurvival(p))
                    continue;
                if (p.getUUID().equals(player.getUUID()))
                    continue;
                if (p.distanceTo(player) <= 5) {
                    nearByPlayerCount++;
                    break;
                }
            }
            if (nearByPlayerCount <= 0) {
                this.loneyTime++;
                // 10s
                if (this.loneyTime % 200 == 0) {
                    shouldSync = true;
                }
            } else {
                this.loneyTime = 0;
            }
        }

        if (shouldSync)
            this.sync();
        if (this.loneyTime == 45 * 20) {
            this.player.displayClientMessage(
                    Component.translatable("message.hoan_meirin.tip_for_loney").withStyle(ChatFormatting.YELLOW), true);
        }
        if (this.loneyTime >= 60 * 20) {
            killPlayerBecauseLonely();
        }
    }

    public void killPlayerBecauseLonely() {
        this.loneyTime = 0;
        GameUtils.killPlayer(player, true, null, Noellesroles.id("hoan_meirin_lonely"));
        this.sync();
    }

    @Override
    public void clientTick() {
        if (!GameUtils.isPlayerAliveAndSurvival(player))
            return;
        // 客户端也进行冷却计算（用于预测显示）
        if (this.cooldown > 0) {
            this.cooldown--;
        }
        int nearByPlayerCount = 0;
        for (var p : this.player.level().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p))
                continue;
            if (player.getUUID().equals(p.getUUID()))
                continue;
            if (p.distanceTo(player) <= 5) {
                nearByPlayerCount++;
                break;
            }
        }
        if (nearByPlayerCount <= 0) {
            this.loneyTime++;
        } else {
            this.loneyTime = 0;
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("loneyTime", this.loneyTime);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.loneyTime = tag.contains("loneyTime") ? tag.getInt("loneyTime") : 0;
    }
}
