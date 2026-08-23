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
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class MonitorRoleData extends SimpleRoleData {

    public UUID markedTarget = null;

    public int cooldown = 0;

    /** 冷却时间 */
    public static final int COOLDOWN_TICKS = 60 * 20;

    public MonitorRoleData(RoleDataContext context) {
        super(context);
    }

    /**
     * 标记目标玩家
     * 
     * @param target 目标玩家 UUID
     */
    public void markTarget(UUID target) {
        if (!(player instanceof ServerPlayer))return;
        ConfigWorldComponent.onPlayerUsedSkill( (ServerPlayer) player);
        this.markedTarget = target;
        this.cooldown = COOLDOWN_TICKS;
        this.sync();
    }

    public boolean canUseAbility() {
        return cooldown <= 0;
    }

    public UUID getMarkedTarget() {
        return markedTarget;
    }

    /**
     * 重置组件状态
     */
    @Override
    public void init() {
        this.markedTarget = null;
        this.cooldown = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    @Override
    public void serverTick() {
        if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 20 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }
        if (this.player.level() == null) {
            return;
        }
        // 检查目标是否存活，如果死亡则清除标记
        if (this.markedTarget != null) {
            if (!this.player.level().isClientSide()) {
                Player targetPlayer = this.player.getServer().getPlayerList().getPlayer(this.markedTarget);
                if (targetPlayer == null || !GameUtils.isPlayerAliveAndSurvival(targetPlayer)) {
                    // 目标不存在或已死亡，清除标记
                    this.markedTarget = null;
                    Component targetPlayerName = Component.translatable("gui.noellesroles.monitor.unknown");
                    if (targetPlayer != null) {
                        targetPlayerName = targetPlayer.getName();
                    }
                    this.player.displayClientMessage(
                            Component
                                    .translatable("gui.noellesroles.monitor.target_died",
                                            Component.literal("").append(targetPlayerName)
                                                    .withStyle(ChatFormatting.GOLD))
                                    .withStyle(ChatFormatting.RED),
                            true);
                    this.sync();
                }
            }
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        if (this.markedTarget != null) {
            tag.putUUID("markedTarget", this.markedTarget);
        }
        tag.putInt("cooldown", this.cooldown);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.markedTarget = tag.contains("markedTarget") ? tag.getUUID("markedTarget") : null;
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
    }
}
