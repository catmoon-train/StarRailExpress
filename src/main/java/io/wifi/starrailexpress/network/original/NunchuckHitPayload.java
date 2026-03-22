package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerNunchuckComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.item.NunchuckItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
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

    public static void onHurt(ServerPlayer attacker, Player target, int direction_) {
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

        // 检查游戏状态
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(attacker.level());
        final var role = game.getRole(attacker);
        if (role != null && !role.onGunHit(attacker, target)) {
            return;
        }

        // 检查目标是否存活
        if (!GameUtils.isPlayerAliveAndSurvival(target)) {
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
        int cooldownTicks = 15; // 默认0.75秒冷却

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

        // 检查目标是否在方块侧面
        boolean targetNearBlock = NunchuckItem.isPlayerNearBlock(target);

        // 获取目标的击打记录组件
        SREPlayerNunchuckComponent targetComponent = SREPlayerNunchuckComponent.KEY.get(target);
        SREPlayerNunchuckComponent.HitRecord record = targetComponent.getHitRecord(attacker.getUUID());

        boolean shouldKill = false;

        if (record != null) {
            // 检查是否连续被击打3次
            if (record.hitCount >= 3) {
                shouldKill = true;
            } else {
                // 检查: 如果之前在方块侧面被击打，现在再次被击打则直接死亡
                if (record.nearBlock) {
                    shouldKill = true;
                } else {
                    // 增加击打次数
                    targetComponent.incrementHitCount(attacker.getUUID());
                    // 更新是否在方块侧面的状态
                    record.nearBlock = targetNearBlock;
                }
            }
        } else {
            // 第一次被击打
            targetComponent.recordHit(attacker.getUUID(), direction_, targetNearBlock);
        }

        // 如果应该击杀，执行击杀
        if (shouldKill) {
            GameUtils.killPlayer(target, true, attacker, GameConstants.DeathReasons.NUNCHUCK);
            targetComponent.clearHitRecord(attacker.getUUID());

            // 设置物品冷却
            if (!attacker.isCreative()) {
                attacker.getCooldowns().addCooldown(TMMItems.NUNCHUCK, 7 * 20); // 7秒
            }
        } else {
            // 不击杀的情况下，设置较短的冷却
            if (!attacker.isCreative()) {
                if (shouldApplyCooldown) {
                    attacker.getCooldowns().addCooldown(TMMItems.NUNCHUCK, cooldownTicks);
                } else {
                    attacker.getCooldowns().addCooldown(TMMItems.NUNCHUCK, 15); 
                }
            }
        }
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<NunchuckHitPayload> {
        @Override
        public void receive(@NotNull NunchuckHitPayload payload, ServerPlayNetworking.@NotNull Context context) {
            var target = context.player().serverLevel().getEntity(payload.targetId());
            if (target instanceof Player targetP) {
                onHurt(context.player(), targetP, payload.direction);
            }
        }
    }
}
