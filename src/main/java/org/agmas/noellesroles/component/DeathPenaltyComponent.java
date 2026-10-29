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

package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENBTUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import pro.fazeclan.river.stupid_express.modifier.refugee.cca.RefugeeComponent;

import org.agmas.noellesroles.api.time.TimeRewindPlayback;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.UUID;

public class DeathPenaltyComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {
    private final Player player;

    // 客户端+服务端
    public long penaltyExpiry = 0;

    // 仅服务端
    public UUID limitCameraUUID = null;
    public boolean morePenalty = true;
    /**
     * 整局死亡惩罚（仅服务端）：本局未参与游戏的玩家会被强制施加死亡惩罚，
     * 且不因复活、阴谋家/无我死亡、计时到期等任何情况解除，只在游戏结束或重新参与后清除。
     * <p>
     * 不写入同步 NBT；客户端通过同步下来的 {@code penaltyExpiry = -1} 判定为「无限期惩罚」。
     */
    public boolean stickyPenalty = false;

    // 客户端+服务端
    public Vec3 limitPos = null;

    // 客户端
    public boolean chatEnabled = true;

    public static ComponentKey<DeathPenaltyComponent> KEY = ModComponents.DEATH_PENALTY;

    public void clearAll() {
        this.init();
    }

    public void check() {
        // 整局死亡惩罚：不接受任何自动解除条件
        if (this.stickyPenalty) {
            return;
        }
        if (!this.hasPenalty()) {
            return;
        } else {
            if (GameUtils.isPlayerAliveAndSurvival(this.player)) {
                this.init();
                return;
            }
            if (this.penaltyExpiry < 0) {
                SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
                if (this.penaltyExpiry != -2) {
                    if (limitPos != null) {
                        return;
                    }
                    if (limitCameraUUID != null) {
                        Level level = this.player.level();
                        if (level instanceof ServerLevel serverLevel) {
                            Entity cameraEntity = serverLevel.getEntity(limitCameraUUID);
                            if (cameraEntity != null && cameraEntity.isAlive()
                                    && !(cameraEntity instanceof ServerPlayer)) {
                                return;
                            }
                            if (cameraEntity instanceof ServerPlayer cameraPlayer
                                    && GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(cameraPlayer)) {
                                return;
                            }
                        }
                    }
                }

                if (RefugeeComponent.KEY.get(player.level()).isAnyRevivals) {
                    // 亡命徒时刻卡住不处理death penalty。
                    return;
                }
                
                // boolean INSANE_alive = false;
                boolean CONSPIRATOR_alive = false;
                for (Player p : player.level().players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(p)
                            && (gameWorldComponent.isRole(p, ModRoles.CONSPIRATOR)
                                    // 无我或无妄存活时，与阴谋家一样维持死亡惩罚
                                    || gameWorldComponent.isRole(p, ModRoles.ANATMAN)
                                    || gameWorldComponent.isRole(p, ModRoles.ASATYA))) {
                        CONSPIRATOR_alive = true;
                    }
                    if (CONSPIRATOR_alive) {
                        if (this.penaltyExpiry == -2) {
                            this.penaltyExpiry = -1;
                            this.limitCameraUUID = null;
                            this.limitPos = null;
                            // 无我 / 无妄 存活时不能说「由于阴谋家存活」，否则玩家会以为惩罚没生效
                            String limitMessageKey = GameUtils.spectatorsDeathPenaltyMessageKey(player);
                            player.sendSystemMessage(
                                    Component.translatable(limitMessageKey)
                                            .withStyle(ChatFormatting.RED));
                            player.displayClientMessage(
                                    Component.translatable(limitMessageKey)
                                            .withStyle(ChatFormatting.RED),
                                    true);
                            if (player.hasPermissions(2)) {
                                player.sendSystemMessage(
                                        Component.translatable("message.noellesroles.admin.free_cam_hint")
                                                .withStyle(ChatFormatting.YELLOW));
                            }
                            sync();
                        }
                        return;
                    }
                }
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.penalty.unlimit").withStyle(ChatFormatting.GREEN),
                        true);
                player.sendSystemMessage(
                        Component.translatable("message.noellesroles.penalty.unlimit").withStyle(ChatFormatting.GREEN));
                this.init();
                return;
            } else {
                if (SRE.getTicksFromGameStart() >= this.penaltyExpiry) {
                    player.displayClientMessage(Component.translatable("message.noellesroles.penalty.unlimit")
                            .withStyle(ChatFormatting.GREEN), true);
                    player.sendSystemMessage(Component.translatable("message.noellesroles.penalty.unlimit")
                            .withStyle(ChatFormatting.GREEN));
                    this.init();
                    return;
                }
            }
        }
    }

    public DeathPenaltyComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 开始死亡惩罚限制，并且有玩家视角限制。设置durationticks为负数可以让玩家一直受限（需要手动取消）。
     * </p>
     * 仅服务端有效。
     */
    public void setPenaltyWithPositionLimit(long durationTicks, Vec3 pos, boolean morePenalty) {
        // 整局死亡惩罚期间不接受其它惩罚覆盖
        if (this.stickyPenalty) {
            return;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (durationTicks < 0) {
            this.penaltyExpiry = -1;
        } else {
            this.penaltyExpiry = SRE.getTicksFromGameStart() + durationTicks;
        }
        if (pos != null) {
            this.limitPos = pos;
            sp.teleportTo(pos.x, pos.y, pos.z);
        }
        this.limitCameraUUID = null;
        this.morePenalty = morePenalty;
        sync();
    }

    public void sync() {
        ModComponents.DEATH_PENALTY.sync(player);
    }

    /**
     * 开始死亡惩罚限制，并且有玩家视角限制。设置durationticks为负数可以让玩家被限制到被观察对象死亡。
     * </p>
     * 仅服务端有效。
     */
    public void setPenaltyWithCameraLimit(long durationTicks, Entity cameraEntity, boolean morePenalty) {
        // 整局死亡惩罚期间不接受其它惩罚覆盖
        if (this.stickyPenalty) {
            return;
        }
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (durationTicks < 0) {
            this.penaltyExpiry = -1;
        } else {
            this.penaltyExpiry = SRE.getTicksFromGameStart() + durationTicks;
        }
        if (cameraEntity != null) {
            this.limitCameraUUID = cameraEntity.getUUID();
            sp.setCamera(cameraEntity);
        }
        this.limitPos = null;
        this.morePenalty = morePenalty;
        sync();
    }

    public void setPenalty(long durationTicks, boolean morePenalty) {
        // 整局死亡惩罚期间不接受其它惩罚覆盖
        if (this.stickyPenalty) {
            return;
        }
        this.morePenalty = morePenalty;
        if (durationTicks < 0) {
            this.penaltyExpiry = -1;
            sync();
            return;
        }
        this.penaltyExpiry = SRE.getTicksFromGameStart() + durationTicks;
        sync();
    }

    /**
     * 设置 / 取消「整局死亡惩罚」。
     * <p>
     * 开启后惩罚不限时（客户端收到 {@code penaltyExpiry = -1}），且不会被复活、计时到期、
     * 阴谋家/无我死亡等任何常规条件解除；只有本方法显式传入 {@code false} 才恢复常规逻辑。
     * 仅服务端有效。
     *
     * @param sticky true = 施加整局惩罚，false = 解除并回到正常状态
     */
    public void setStickyPenalty(boolean sticky) {
        if (player.level().isClientSide) {
            return;
        }
        if (sticky) {
            if (this.stickyPenalty) {
                return;
            }
            this.stickyPenalty = true;
            this.penaltyExpiry = -1;
            this.morePenalty = true;
            this.limitPos = null;
            this.limitCameraUUID = null;
            this.chatEnabled = true;
            sync();
        } else {
            if (!this.stickyPenalty) {
                return;
            }
            this.stickyPenalty = false;
            this.init();
        }
    }

    public boolean hasStrictPenalty() {
        if (isRewinding())
            return true;
        return this.hasPenalty() && (!chatEnabled || (this.limitCameraUUID != null || this.limitPos != null));
    }

    public boolean isRewinding() {

        if (player.hasEffect(ModEffects.TIME_REWIND_DAZE)) {
            return true;
        }
        if (player.hasEffect(ModEffects.TIME_REWIND_MARK)) {
            return true;
        }
        if (!player.level().isClientSide)
            if (TimeRewindPlayback.isActive(player.getUUID())) {
                return true;
            }
        return false;
    }

    public boolean hasPenalty() {
        // 整局死亡惩罚：始终视为有惩罚，且不参与计时到期转换
        if (this.stickyPenalty)
            return true;
        if (isRewinding())
            return true;
        if (this.penaltyExpiry == 0)
            return false;
        if (this.penaltyExpiry < 0) {
            return true;
        }
        if (SRE.getTicksFromGameStart() >= this.penaltyExpiry && morePenalty) {
            this.penaltyExpiry = -2;
        }
        return true;
    }

    @Override
    public void init() {
        // 整局死亡惩罚不能被任何常规清除路径解除（复活回房、游戏重置等）
        if (this.stickyPenalty) {
            return;
        }
        this.penaltyExpiry = 0;
        if (!player.level().isClientSide) {
            if (limitCameraUUID != null) {
                if (player instanceof ServerPlayer sp) {
                    sp.setCamera(sp);
                }
            }
        }
        this.limitPos = null;
        this.limitCameraUUID = null;
        if (this.player.hasEffect(ModEffects.MOVE_BANED)) {
            int duration = this.player.getEffect(ModEffects.MOVE_BANED).getDuration();
            if (duration >= 60 * 20 || duration == -1) {
                this.player.removeEffect(ModEffects.MOVE_BANED);
            }
        }
        if (this.player.hasEffect(ModEffects.USED_BANED)) {
            int duration = this.player.getEffect(ModEffects.USED_BANED).getDuration();
            if (duration >= 60 * 20 || duration == -1) {
                this.player.removeEffect(ModEffects.USED_BANED);
            }
        }
        sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.penaltyExpiry = tag.getLong("penaltyExpiry");
        if (tag.contains("chatEnabled")) {
            this.chatEnabled = tag.getBoolean("chatEnabled");
        } else {
            this.chatEnabled = true;
        }
        if (tag.contains("pos", CompoundTag.TAG_COMPOUND)) {
            this.limitPos = SRENBTUtils.tagToVec3(tag.getCompound("pos"));
        } else {
            this.limitPos = null;
        }
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putLong("penaltyExpiry", this.penaltyExpiry);
        if (this.limitCameraUUID != null || this.limitPos != null) {
            tag.putBoolean("chatEnabled", false);
        }
        if (limitPos != null) {
            tag.put("pos", SRENBTUtils.vec3ToTag(limitPos));
        }
    }

    @Override
    public void serverTick() {
        if (isRewinding())
            return;

        if (!SREGameWorldComponent.KEY.get(this.player.level()).isRunning()) {
            // 游戏结束：整局死亡惩罚也随之结束（本局限定，不跨局残留）
            if (this.stickyPenalty) {
                this.stickyPenalty = false;
                this.init();
                return;
            }
            if (this.hasPenalty()) {
                this.clear();
            }
            return;
        }
        this.check();
        if (player != null) {
            if (player instanceof ServerPlayer sp) {
                if (limitCameraUUID != null) {
                    Entity currentCamera = sp.getCamera();
                    if (currentCamera == null || !currentCamera.getUUID().equals(limitCameraUUID)) {
                        var target = sp.serverLevel().getEntity(limitCameraUUID);
                        boolean flag = target != null && target.isAlive();
                        // ()
                        if (target instanceof ServerPlayer cp && !GameUtils.isPlayerAliveAndSurvival(cp)) {
                            flag = false;
                        }
                        if (flag) {
                            sp.setCamera(target);
                        } else {
                            sp.setCamera(sp);
                        }
                    }
                }
                if (limitPos != null) {
                    if (player.level().getGameTime() % 20 == 0) {
                        if (player.distanceToSqr(limitPos) >= 2) {
                            player.teleportTo(limitPos.x, limitPos.y, limitPos.z);
                        }
                    }
                }
                if (limitPos != null || limitCameraUUID != null) {
                    if (!player.hasEffect(ModEffects.MOVE_BANED) || player.level().getGameTime() % 30 == 0) {
                        player.addEffect(new MobEffectInstance(
                                ModEffects.MOVE_BANED,
                                (int) (40), // 持续时间（tick）
                                0, // 等级（0 = 速度 I）
                                false, // ambient（环境效果，如信标）
                                true, // showParticles（显示粒子）
                                false // showIcon（显示图标）
                        ));
                        player.addEffect(new MobEffectInstance(
                                ModEffects.USED_BANED,
                                (int) (40), // 持续时间（tick）
                                0, // 等级（0 = 速度 I）
                                false, // ambient（环境效果，如信标）
                                true, // showParticles（显示粒子）
                                false // showIcon（显示图标）
                        ));
                    }

                }
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void clientTick() {
        if (isRewinding())
            return;
        if (limitPos != null && limitCameraUUID == null) {
            if (player.distanceToSqr(limitPos) >= 1) {
                player.setPos(limitPos);
            }
        }
    }

    public static boolean hasStrictPenalty(Player p) {
        return KEY.get(p).hasStrictPenalty();
    }

    public static boolean hasPenalty(Player p) {
        return KEY.get(p).hasPenalty();
    }
}
