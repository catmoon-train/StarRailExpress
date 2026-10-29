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

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerNunchuckComponent;
import io.wifi.starrailexpress.content.item.NunchuckItem;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public record NunchuckHitPayload(int targetId, int direction) implements CustomPacketPayload {
    public static final Type<NunchuckHitPayload> ID = new Type<>(SRE.id("nunchuck_hit"));
    public static final StreamCodec<FriendlyByteBuf, NunchuckHitPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            NunchuckHitPayload::targetId,
            ByteBufCodecs.INT,
            NunchuckHitPayload::direction,
            NunchuckHitPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void onHurt(ServerPlayer attacker, LivingEntity target, int direction_) {
        // 检查冷却
        if (attacker.getCooldowns().isOnCooldown(TMMItems.NUNCHUCK)) {
            return;
        }

        // 获取主手物品
        if (!attacker.getMainHandItem().is(TMMItems.NUNCHUCK)) {
            return;
        }

        // 获取目标
        if (target == null || target == attacker) {
            return;
        }

        // 检查距离
        double distance = attacker.distanceTo(target);
        if (distance > 5.0) {
            return;
        }

        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(attacker.level());
        final var role = game.getRole(attacker);
        if (target instanceof ServerPlayer playerTarget) {
            if (role != null && !role.onGunHit(attacker, playerTarget)) {
                return;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(playerTarget)) {
                return;
            }
        } else if (!(target instanceof io.wifi.starrailexpress.traindummy.TrainDummyEntity dummy)
                || !dummy.canReceiveHits()) {
            return;
        }

        // 获取攻击者的双节棍组件
        SREPlayerNunchuckComponent attackerComponent = SREPlayerNunchuckComponent.KEY.get(attacker);
        SREPlayerNunchuckComponent.AttackRecord attackRecord = attackerComponent.getAttackRecord();

        // 检查该招式是否在7秒冷却中
        if (attackerComponent.isMoveOnCooldown(direction_, 140)) {
            return; // 招式在冷却中，不允许使用
        }

        boolean shouldApplyCooldown = false;
        int cooldownTicks = 0; // 默认无冷却

        // 检查攻击者是否在7秒内连续使用3次
        if (attackRecord != null) {
            if (attackRecord.attackCount >= 3) {
                shouldApplyCooldown = true;
                cooldownTicks = 5 * 20; // 5秒冷却
                attackerComponent.resetAttackRecord();
            } else {
                attackerComponent.incrementAttackCount();
            }
        } else {
            attackerComponent.recordAttack();
        }

        // 记录该招式的使用时间
        attackerComponent.recordMoveUse(direction_);

        // 播放声音
        attacker.level().playSound(null, attacker.getX(), attacker.getEyeY(), attacker.getZ(),
                TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.PLAYERS, 1.0f, 1.0f);

        // 执行击退
        Vec3 knockbackDir = NunchuckItem.getKnockbackDirection(attacker, direction_);
        double knockbackStrength = (direction_ == 2) ? 1.5 : 0.5; // 蹲下左键(direction=2)击退距离为1.5，其他情况为0.5
        target.setDeltaMovement(knockbackDir.scale(knockbackStrength));
        target.hurtMarked = true;

        // 造成1点伤害
        target.hurt(attacker.level().damageSources().playerAttack(attacker), 1.0f);

        if (target instanceof ServerPlayer playerTarget) {
            playerTarget.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            boolean targetNearBlock = NunchuckItem.isPlayerNearBlock(playerTarget);
            SREPlayerNunchuckComponent targetComponent = SREPlayerNunchuckComponent.KEY.get(playerTarget);
            SREPlayerNunchuckComponent.HitRecord record = targetComponent.getHitRecord(attacker.getUUID());
            boolean shouldKill = false;
            if (record != null) {
                if (record.hitCount >= 3) {
                    shouldKill = true;
                } else if (record.nearBlock) {
                    shouldKill = true;
                } else {
                    targetComponent.incrementHitCount(attacker.getUUID());
                    record.nearBlock = targetNearBlock;
                }
            } else {
                targetComponent.recordHit(attacker.getUUID(), direction_, targetNearBlock);
            }
            if (shouldKill) {
                GameUtils.killPlayer(playerTarget, true, attacker, GameConstants.DeathReasons.NUNCHUCK);
                targetComponent.clearHitRecord(attacker.getUUID());
                if (!attacker.isCreative()) {
                    attacker.getCooldowns().addCooldown(TMMItems.NUNCHUCK, 5 * 20);
                }
            } else if (!attacker.isCreative()) {
                attacker.getCooldowns().addCooldown(TMMItems.NUNCHUCK, shouldApplyCooldown ? cooldownTicks : 0);
            }
        } else if (target instanceof io.wifi.starrailexpress.traindummy.TrainDummyEntity dummy) {
            io.wifi.starrailexpress.traindummy.TrainDummyCombat.applyEffect(dummy,
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
            if (dummy.noteNunchuckHit(attacker.getUUID(), nearSolid(dummy))) {
                io.wifi.starrailexpress.traindummy.TrainDummyCombat.applyLethal(dummy, attacker,
                        GameConstants.DeathReasons.NUNCHUCK);
                if (!attacker.isCreative()) {
                    attacker.getCooldowns().addCooldown(TMMItems.NUNCHUCK, 5 * 20);
                }
            } else if (!attacker.isCreative()) {
                attacker.getCooldowns().addCooldown(TMMItems.NUNCHUCK, shouldApplyCooldown ? cooldownTicks : 0);
            }
        }


    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<NunchuckHitPayload> {
        @Override
        public void receive(@NotNull NunchuckHitPayload payload, ServerPlayNetworking.@NotNull Context context) {
            var target = context.player().serverLevel().getEntity(payload.targetId());
            if (org.agmas.noellesroles.game.command_block.CommandBlockCracks.isCrack(target)) {
                ServerPlayer player = context.player();
                if (payload.direction() < 0 || payload.direction() > 3
                        || !player.getMainHandItem().is(TMMItems.NUNCHUCK)
                        || player.getCooldowns().isOnCooldown(TMMItems.NUNCHUCK)
                        || player.distanceTo(target) > 4.0D) {
                    return;
                }
                var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
                var component = SREPlayerNunchuckComponent.KEY.get(player);
                if ((role == null || role.onUseGun(player))
                        && !component.isMoveOnCooldown(payload.direction(), 140)
                        && org.agmas.noellesroles.game.command_block.CommandBlockCracks.tryShatterWithWeapon(player, target)) {
                    component.recordMoveUse(payload.direction());
                }
                return;
            }
            if (target instanceof LivingEntity living && (living instanceof Player || living instanceof io.wifi.starrailexpress.traindummy.TrainDummyEntity)) {
                onHurt(context.player(), living, payload.direction);
            }
        }
    }

    private static boolean nearSolid(LivingEntity entity) {
        var pos = entity.blockPosition();
        int[][] offsets = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
        for (int[] offset : offsets) {
            if (!entity.level().getBlockState(pos.offset(offset[0], offset[1], offset[2])).isAir()) {
                return true;
            }
        }
        return false;
    }
}
