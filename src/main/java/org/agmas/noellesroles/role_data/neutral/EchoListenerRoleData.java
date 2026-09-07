/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.agmas.noellesroles.role_data.neutral;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.event.OnGiveKillerBalance;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;

import java.util.List;

public class EchoListenerRoleData extends SimpleRoleData {
    private static final EntityDataAccessor<Byte> SHARED_FLAGS = new EntityDataAccessor<>(0,
            EntityDataSerializers.BYTE);
    private static final byte GLOWING_FLAG = 0x40;
    private static final int INCOME_INTERVAL = 5 * 20;
    private static final int RESPAWN_DELAY = 60 * 20;
    private int incomeTicks;
    private int respawnTicks = -1;

    static {
        OnGiveKillerBalance.EVENT.register((victim, killer,
                reason) -> SREGameWorldComponent.KEY.get(victim.level()).isRole(victim, ModRoles.ECHO_LISTENER)
                        ? -(GameConstants.getMoneyPerKill() / 2)
                        : 0);
    }

    public EchoListenerRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer listener))
            return;
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        if (!player.hasEffect(ModEffects.BLIND_VISION) || player.level().getGameTime() % 40 == 0) {
            listener.addEffect(new MobEffectInstance(ModEffects.BLIND_VISION, 60, 0, false, false, false));
        }
        // The effect drives the existing sound-reactive blind-vision shader.

        if (!GameUtils.isPlayerAliveAndSurvival(listener)) {
            if (respawnTicks < 0)
                respawnTicks = RESPAWN_DELAY;
            if (--respawnTicks <= 0) {
                GameUtils.revivePlayerToItsRoom(listener);
                respawnTicks = -1;
                listener.displayClientMessage(Component.translatable("message.noellesroles.echo_listener.respawned"),
                        true);
            }
            return;
        }
        respawnTicks = -1;

        if (++incomeTicks >= INCOME_INTERVAL) {
            incomeTicks = 0;
            SREPlayerShopComponent.KEY.get(listener).addToBalance(5);
        }

        if (listener.tickCount % 5 == 0) {
            revealNoisyPlayers(listener);
            spreadFear(listener);
        }
    }

    private void revealNoisyPlayers(ServerPlayer listener) {
        for (ServerPlayer target : listener.serverLevel().players()) {
            if (target == listener || !GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            boolean noisy = target.getDeltaMovement().horizontalDistanceSqr() > 0.0025
                    || target.fallDistance > 0.1f || target.swinging;
            byte flags = target.getEntityData().get(SHARED_FLAGS);
            byte visibleFlags = noisy ? (byte) (flags | GLOWING_FLAG) : flags;
            listener.connection.send(new ClientboundSetEntityDataPacket(target.getId(),
                    List.of(new SynchedEntityData.DataValue<>(SHARED_FLAGS.id(), SHARED_FLAGS.serializer(),
                            visibleFlags))));
        }
    }

    private void spreadFear(ServerPlayer listener) {
        for (ServerPlayer target : listener.serverLevel().players()) {
            if (target == listener || !GameUtils.isPlayerAliveAndSurvival(target))
                continue;
            double distance = target.distanceTo(listener);
            if (distance > 16.0)
                continue;
            int amplifier = distance <= 5.0 ? 1 : 0;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, amplifier, false, false, false));
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0, false, false, false));
            SREPlayerMoodComponent.KEY.get(target).addMood(distance <= 5.0 ? -0.008f : -0.003f);
        }
    }

    public boolean useSonicWave() {
        if (!(player instanceof ServerPlayer listener) || !GameUtils.isPlayerAliveAndSurvival(listener))
            return false;
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(listener);
        if (shop.balance < 50) {
            listener.displayClientMessage(Component.translatable("message.noellesroles.echo_listener.no_money", 50),
                    true);
            return false;
        }
        Vec3 start = listener.getEyePosition();
        Vec3 look = listener.getLookAngle();
        ServerPlayer best = null;
        double bestDistance = 24.0;
        AABB search = listener.getBoundingBox().expandTowards(look.scale(24.0)).inflate(1.5);
        for (ServerPlayer target : listener.serverLevel().getEntitiesOfClass(ServerPlayer.class, search,
                p -> p != listener && GameUtils.isPlayerAliveAndSurvival(p))) {
            Vec3 offset = target.getEyePosition().subtract(start);
            double distance = offset.length();
            if (distance < bestDistance && offset.normalize().dot(look) >= 0.985) {
                best = target;
                bestDistance = distance;
            }
        }
        if (best == null) {
            listener.displayClientMessage(Component.translatable("message.noellesroles.echo_listener.no_target"), true);
            return false;
        }
        shop.addToBalance(-50);
        int duration = 12 * 20;
        best.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, false, true));
        best.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, duration, 0, false, false, true));
        best.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 1, false, false, true));
        ServerLevel level = listener.serverLevel();
        level.playSound(null, listener.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 1.0f);
        return true;
    }

    /** 不传输内容 */
    @Override
    public void writeToSyncNbt(CompoundTag tag, Provider registryLookup) {
    }

    /** 不传输内容 */
    @Override
    public void readFromSyncNbt(CompoundTag tag, Provider registryLookup) {
    }
}
