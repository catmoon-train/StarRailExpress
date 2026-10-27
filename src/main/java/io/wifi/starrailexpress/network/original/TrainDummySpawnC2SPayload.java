/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package io.wifi.starrailexpress.network.original;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.index.DevItems;
import io.wifi.starrailexpress.traindummy.TrainDummyEntity;
import io.wifi.starrailexpress.traindummy.TrainDummySettings;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 列车假人工具的一次性生成请求。配置不落盘。
 */
public record TrainDummySpawnC2SPayload(
        String skinName,
        boolean corpseOnDeath,
        int virtualHp,
        int virtualShield,
        int shieldLayers,
        int timedLayers,
        int timedTicks,
        int weakLayers,
        int weakTicks,
        String weakReason,
        boolean moving,
        int direction,
        boolean loop,
        int distance,
        boolean randomMove,
        boolean blink,
        int breakpoints,
        int blinkInterval,
        float scale,
        ItemStack held) implements CustomPacketPayload {

    public static final Type<TrainDummySpawnC2SPayload> ID = new Type<>(SRE.id("train_dummy_spawn"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrainDummySpawnC2SPayload> CODEC = StreamCodec.of(
            TrainDummySpawnC2SPayload::encode, TrainDummySpawnC2SPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, TrainDummySpawnC2SPayload payload) {
        buf.writeUtf(payload.skinName == null ? "" : payload.skinName, 32);
        buf.writeBoolean(payload.corpseOnDeath);
        buf.writeVarInt(payload.virtualHp);
        buf.writeVarInt(payload.virtualShield);
        buf.writeVarInt(payload.shieldLayers);
        buf.writeVarInt(payload.timedLayers);
        buf.writeVarInt(payload.timedTicks);
        buf.writeVarInt(payload.weakLayers);
        buf.writeVarInt(payload.weakTicks);
        buf.writeUtf(payload.weakReason == null ? "" : payload.weakReason, 256);
        buf.writeBoolean(payload.moving);
        buf.writeVarInt(payload.direction);
        buf.writeBoolean(payload.loop);
        buf.writeVarInt(payload.distance);
        buf.writeBoolean(payload.randomMove);
        buf.writeBoolean(payload.blink);
        buf.writeVarInt(payload.breakpoints);
        buf.writeVarInt(payload.blinkInterval);
        buf.writeFloat(payload.scale);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.held == null ? ItemStack.EMPTY : payload.held);
    }

    private static TrainDummySpawnC2SPayload decode(RegistryFriendlyByteBuf buf) {
        return new TrainDummySpawnC2SPayload(
                buf.readUtf(32),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(256),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static class Receiver implements ServerPlayNetworking.PlayPayloadHandler<TrainDummySpawnC2SPayload> {
        @Override
        public void receive(@NotNull TrainDummySpawnC2SPayload payload, ServerPlayNetworking.@NotNull Context context) {
            ServerPlayer player = context.player();
            if (!player.getMainHandItem().is(DevItems.TRAIN_DUMMY_TOOL)
                    && !player.getOffhandItem().is(DevItems.TRAIN_DUMMY_TOOL)) {
                return;
            }
            context.server().execute(() -> spawn(player, payload));
        }
    }

    private static void spawn(ServerPlayer player, TrainDummySpawnC2SPayload payload) {
        TrainDummySettings settings = new TrainDummySettings();
        settings.skinName = payload.skinName();
        settings.corpseOnDeath = payload.corpseOnDeath();
        settings.virtualHp = payload.virtualHp();
        settings.virtualShield = payload.virtualShield();
        settings.shieldLayers = payload.shieldLayers();
        settings.timedLayers = payload.timedLayers();
        settings.timedTicks = payload.timedTicks();
        settings.weakLayers = payload.weakLayers();
        settings.weakTicks = payload.weakTicks();
        settings.weakReason = payload.weakReason();
        settings.moving = payload.moving();
        settings.direction = payload.direction();
        settings.loop = payload.loop();
        settings.distance = payload.distance();
        settings.randomMove = payload.randomMove();
        settings.blink = payload.blink();
        settings.breakpoints = payload.breakpoints();
        settings.blinkInterval = payload.blinkInterval();
        settings.scale = payload.scale();
        settings.held = payload.held();
        settings.clamp();

        TrainDummyEntity dummy = TrainDummyEntity.TYPE.create(player.serverLevel());
        if (dummy == null) {
            return;
        }
        dummy.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        dummy.applySettings(settings, settings.held());
        player.serverLevel().addFreshEntity(dummy);
    }
}
