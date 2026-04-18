package org.agmas.noellesroles.game.modes.fourthroom.network;

import io.wifi.starrailexpress.SRE;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.fourthroom.block.FourthRoomTableBlockEntity;
import org.agmas.noellesroles.game.modes.fourthroom.effect.EffectEvent;
import org.agmas.noellesroles.game.modes.fourthroom.effect.TableEffectEvents;

import java.util.ArrayList;
import java.util.List;

public record FourthRoomTableEffectsPayload(BlockPos origin, List<EffectEvent> effects) implements CustomPacketPayload {
    public static final Type<FourthRoomTableEffectsPayload> ID = new Type<>(SRE.id("fourth_room_table_effects"));
    public static final StreamCodec<FriendlyByteBuf, FourthRoomTableEffectsPayload> CODEC = StreamCodec.ofMember(
            FourthRoomTableEffectsPayload::encode,
            FourthRoomTableEffectsPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(origin);
        buf.writeVarInt(effects.size());
        for (EffectEvent effect : effects) {
            TableEffectEvents.encode(buf, effect);
        }
    }

    public static FourthRoomTableEffectsPayload decode(FriendlyByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        int size = buf.readVarInt();
        List<EffectEvent> effects = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            effects.add(TableEffectEvents.decode(buf));
        }
        return new FourthRoomTableEffectsPayload(origin, effects);
    }

    public static void sendNearby(ServerLevel level, BlockPos origin, List<EffectEvent> effects) {
        if (effects.isEmpty()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(origin);
        FourthRoomTableEffectsPayload payload = new FourthRoomTableEffectsPayload(origin, List.copyOf(effects));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) <= 32.0D * 32.0D) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (payload, context) -> context.client().execute(() -> {
            if (context.client().level == null) {
                return;
            }
            if (!(context.client().level.getBlockEntity(payload.origin()) instanceof FourthRoomTableBlockEntity table)) {
                return;
            }
            table.clientEffectQueue().setOrigin(payload.origin());
            table.clientEffectQueue().enqueue(payload.effects());
        }));
    }
}