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
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.replay.GameReplayUtils;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.jetbrains.annotations.NotNull;

public class GhostRoleData extends SimpleRoleData {

    public boolean isActive = true;
    public int cooldown = 0;
    public int invisibilityTicks = 0;
    public boolean abilityUnlocked = false;
    public boolean unlockNotified = false;
    public boolean lastStandNotified = false;
    /** 解锁所需的游戏剩余时间（3分钟 = 180秒 = 3600 tick） */
    public static final int UNLOCK_REMAINING_TICKS = 180 * 20;
    /** 最后的幸存者模式时间（2分钟 = 120秒 = 2400 tick） */
    public static final int LAST_STAND_TIME = 120 * 20;
    public static final int FURAN_LAST_STAND_TIME = 90 * 20;

    @Override
    public void init() {
        this.isActive = true;
        this.cooldown = 0;
        this.invisibilityTicks = 0;
        this.abilityUnlocked = false;
        this.unlockNotified = false;
        this.lastStandNotified = false;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    public GhostRoleData(RoleDataContext context) {
        super(context);
    }

    public void clientTick() {
        if (invisibilityTicks > 1) {
            invisibilityTicks--;
        }
        if (cooldown > 1) {
            cooldown--;
        }
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return player == this.player;
    }

    public void serverTick() {
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(player.level());

        if (!gameWorld.isRunning()) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        if (gameWorld.isRole(player, ModRoles.GHOST)) {

            // 检查技能解锁（当游戏剩余3分钟时解锁）
            if (!abilityUnlocked) {
                // 获取游戏剩余时间
                SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(player.level());
                if (gameTime != null) {
                    long remainingTicks = gameTime.getTime();
                    // 当剩余时间 <= 3分钟时解锁
                    if (remainingTicks <= UNLOCK_REMAINING_TICKS) {
                        abilityUnlocked = true;
                        sync();
                    }
                }
            }

            // 发送解锁提示
            if (abilityUnlocked && !unlockNotified) {
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.noellesroles.ghost.ability_unlocked")
                                    .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                            true);
                    unlockNotified = true;
                }
            }
            checkLastStand(gameWorld);

            // 检查最后幸存者状态

            if (cooldown > 0) {
                cooldown--;
            }
            if (invisibilityTicks > 0) {
                invisibilityTicks--;
            }
            if (player.level().getGameTime() % 400 == 0) {
                sync();
            }
        } else if (gameWorld.isRole(player, THRedHouseRoles.FURANDORU)) {
            checkFuranLastStand(gameWorld);
        }

    }

    public boolean useAbility() {
        // 冷却由 RoleSkill 统一管理
        if (!abilityUnlocked) {
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.ghost.not_unlocked")
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }

        SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
        if (shopComponent.balance < 150) {
            player.displayClientMessage(Component.translatable("message.noellesroles.insufficient_funds"), true);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.level().playSound(null, serverPlayer.blockPosition(), TMMSounds.UI_SHOP_BUY_FAIL,
                        SoundSource.PLAYERS, 1.0F, 1.0F);
            }
            return false;
        }

        shopComponent.balance -= 150;
        shopComponent.sync();

        invisibilityTicks = 160;
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 160, 0, false, false, true));

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.level().playSound(null, serverPlayer.blockPosition(), TMMSounds.UI_SHOP_BUY,
                    SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        sync();
        return true;
    }

    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("isActive", this.isActive);
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("invisibilityTicks", this.invisibilityTicks);
        tag.putBoolean("abilityUnlocked", this.abilityUnlocked);
        tag.putBoolean("unlockNotified", this.unlockNotified);
    }

    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.isActive = !tag.contains("isActive") || tag.getBoolean("isActive");
        this.cooldown = tag.getInt("cooldown");
        this.invisibilityTicks = tag.getInt("invisibilityTicks");
        this.abilityUnlocked = tag.getBoolean("abilityUnlocked");
        this.unlockNotified = tag.getBoolean("unlockNotified");
    }

    /**
     * 检查最后幸存者状态
     * 当场上平民阵营只剩小透明时，将游戏时间设为2分钟（如果当前时间更长）
     * 并广播提示
     */
    public void checkLastStand(SREGameWorldComponent gameWorld) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (player.isSpectator())
            return;
        if (player.isCreative())
            return;
        if (player.hasEffect(ModEffects.SAFE_TIME))
            return;
        if (player.hasEffect(ModEffects.SKILL_BANED))
            return;
        if (lastStandNotified) {
            return; // 已经通知过了，不再重复
        }

        // 统计存活的平民阵营玩家
        int aliveCivilianCount = 0;
        boolean hasGhost = true;

        for (var p : player.level().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            var role = gameWorld.getRole(p.getUUID());
            if (role == null) {
                continue;
            }
            // 检查是否是平民阵营（isInnocent = true 且 canUseKiller = false）
            if (role.isInnocent() && !role.canUseKiller() && !role.isNeutrals()) {
                aliveCivilianCount++;
            }
        }

        // 如果存活平民阵营只有1人，且是小透明
        if (aliveCivilianCount == 1 && hasGhost) {
            // 获取游戏时间
            SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(player.level());
            if (gameTime != null) {
                long currentTicks = gameTime.getTime();
                // 如果当前时间超过2分钟，则设置为2分钟
                if (currentTicks > LAST_STAND_TIME) {
                    gameTime.setTime(LAST_STAND_TIME);
                }
            }

            // 发送全局广播
            var broadcastMessage = Component
                    .translatable("message.noellesroles.ghost.last_stand")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            serverPlayer.server.getPlayerList().getPlayers().forEach((p) -> {
                BroadcastCommand.BroadcastMessage(p, broadcastMessage);
            });

            lastStandNotified = true;
            sync();
            // 回放记录：小透明加速时间
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.ghost.accelerate_time",
                            GameReplayUtils.getReplayPlayerDisplayText(player, true)));
        }
    }

    /**
     * 检查最后幸存者状态
     * 当场上只剩furan+单阵营时，将游戏时间设为1分钟（如果当前时间更长）
     * 并广播提示
     */
    public void checkFuranLastStand(SREGameWorldComponent gameWorld) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!gameWorld.isSkillAvailable)
            return;
        if (player.isSpectator())
            return;
        if (player.isCreative())
            return;
        if (player.hasEffect(ModEffects.SAFE_TIME))
            return;
        if (player.hasEffect(ModEffects.SKILL_BANED))
            return;
        if (lastStandNotified) {
            return; // 已经通知过了，不再重复
        }

        // 统计存活的平民阵营玩家
        int aliveCivilianCount = 0;
        int aliveKillerCount = 0;
        SREGameTimeComponent gameTime = SREGameTimeComponent.KEY.get(player.level());

        for (var p : player.level().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                continue;
            }
            var role = gameWorld.getRole(p.getUUID());
            if (role == null) {
                continue;
            }
            // 检查是否是平民阵营（isInnocent = true 且 canUseKiller = false）
            if (role.isInnocent() && !role.canUseKiller() && !role.isNeutrals()) {
                aliveCivilianCount++;
            }
            if (role.canUseKiller()) {
                aliveKillerCount++;
            }
        }

        // 场上只剩下单一阵营+fulan，或者时间还剩1分半。
        if (gameTime.getTime() <= FURAN_LAST_STAND_TIME
                || (aliveCivilianCount <= 0 || aliveKillerCount <= 0) && aliveCivilianCount + aliveKillerCount > 0) {
            // 获取游戏时间
            if (gameTime != null) {
                long currentTicks = gameTime.getTime();
                // 如果当前时间超过2分钟，则设置为2分钟
                if (currentTicks > FURAN_LAST_STAND_TIME) {
                    gameTime.setTime(FURAN_LAST_STAND_TIME);
                }
            }

            // 发送全局广播
            var broadcastMessage = Component
                    .translatable("message.noellesroles.furandoru.last_stand")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
            serverPlayer.server.getPlayerList().getPlayers().forEach((p) -> {
                BroadcastCommand.BroadcastMessage(p, broadcastMessage);
                MCItemsUtils.insertStackInFreeSlot(p, Items.WIND_CHARGE.getDefaultInstance());
            });

            lastStandNotified = true;
            this.player.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING,
                    (int) 90 * 20, // 持续时间 60s（tick）
                    0, // 等级（0 = 速度 I）
                    true, // ambient（环境效果，如信标）
                    true, // showParticles（显示粒子）
                    false // showIcon（显示图标）
            ));
            sync();
            // 回放记录：芙兰朵露加速时间
            SRE.REPLAY_MANAGER.recordCustomEvent(
                    Component.translatable("replay.event.ghost.accelerate_time",
                            GameReplayUtils.getReplayPlayerDisplayText(player, true)));
        }
    }

}
