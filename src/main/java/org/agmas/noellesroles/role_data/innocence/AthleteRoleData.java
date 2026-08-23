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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class AthleteRoleData extends SimpleRoleData {

    // ==================== 常量定义 ====================

    /** 使用后冷却时间（120秒 = 2400 tick） */
    public static final int ABILITY_COOLDOWN = 2400;

    /** 速度效果持续时间（20秒 = 400 tick） */
    public static final int SPEED_DURATION = 400;

    /** 速度效果等级（1级，索引为0，对应 Speed I） */
    public static final int SPEED_AMPLIFIER = 4;

    // ==================== 状态变量 ====================

    /** 技能冷却时间（tick） */
    public int cooldown = 0;

    /** 速度效果剩余时间（tick），用于HUD显示 */
    public int speedTicks = 0;

    /** 是否正在疾跑中 */
    public boolean isSprinting = false;

    /**
     * 构造函数
     */
    public AthleteRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        this.cooldown = 1200; // 开局60秒冷却
        this.speedTicks = 0;
        this.isSprinting = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 检查技能是否可用
     */
    public boolean canUseAbility() {
        return cooldown <= 0 && !isSprinting;
    }

    /**
     * 使用疾跑技能
     * 
     * @return 是否成功使用
     */
    public boolean useAbility() {
        if (!canUseAbility()) {
            return false;
        }

        // 验证是运动员
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());
        if (!gameWorld.isRole(player, ModRoles.ATHLETE)) {
            return false;
        }

        // 应用速度效果（无粒子，不显示图标）
        // ambient=true 使粒子不可见, showParticles=false 确保无粒子
        // showIcon=false 确保不显示效果图标
        player.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                SPEED_DURATION,
                SPEED_AMPLIFIER,
                true, // ambient - 环境效果（粒子更少更透明）
                false, // showParticles - 不显示粒子
                false // showIcon - 不显示图标
        ));

        // 设置状态
        this.isSprinting = true;
        this.speedTicks = SPEED_DURATION;

        // 设置冷却
        this.cooldown = ABILITY_COOLDOWN;

        // 发送消息
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.athlete.ability_activated"),
                    true);
        }

        this.sync();
        return true;
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    /**
     * 获取速度效果剩余时间（秒）
     */
    public float getSpeedSeconds() {
        return speedTicks / 20.0f;
    }

    /**
     * 同步到客户端
     */

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        // 减少冷却时间
        if (this.cooldown > 0) {
            this.cooldown--;
            // 每秒同步一次，减少网络压力
            if (this.cooldown % 200 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }

        // 减少速度效果时间
        if (this.speedTicks > 0) {
            this.speedTicks--;

            // 速度效果结束
            if (this.speedTicks <= 0) {
                this.isSprinting = false;
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.athlete.ability_ended"), true);
                }
                this.sync();
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("speedTicks", this.speedTicks);
        tag.putBoolean("isSprinting", this.isSprinting);
    }



    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.speedTicks = tag.contains("speedTicks") ? tag.getInt("speedTicks") : 0;
        this.isSprinting = tag.contains("isSprinting") && tag.getBoolean("isSprinting");
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 1) {
            this.cooldown--;
        }
        if (this.speedTicks > 0) {
            this.speedTicks--;
        }
    }
}
