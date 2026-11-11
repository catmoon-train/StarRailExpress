package org.agmas.noellesroles.game.roles.killer.ma_chen_xu;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;
import java.util.UUID;

/** 公开领域开关与计时；不包含私有诡术、怨念或背包信息。 */
public final class SackGhostPackets {
    private SackGhostPackets() {}

    public record RealmS2C(UUID owner, int remaining, int duration, int stage) implements CustomPacketPayload {
        public static final Type<RealmS2C> ID = new Type<>(Noellesroles.id("sack_ghost_realm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RealmS2C> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeUUID(packet.owner()); buf.writeVarInt(packet.remaining()); buf.writeVarInt(packet.duration()); buf.writeVarInt(packet.stage()); },
                buf -> new RealmS2C(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void register() { PayloadTypeRegistry.playS2C().register(RealmS2C.ID, RealmS2C.CODEC); }

    public static void send(ServerPlayer recipient, ServerPlayer owner, MaChenXuRoleData data) {
        ServerPlayNetworking.send(recipient, new RealmS2C(owner.getUUID(), data.otherworldActive ? data.otherworldDuration : 0,
                data.otherworldTotalTicks, data.stage));
    }

    public static void publish(ServerPlayer owner, MaChenXuRoleData data) {
        publish(owner.serverLevel(), owner, data);
    }

    public static void publish(ServerLevel level, ServerPlayer owner, MaChenXuRoleData data) {
        for (ServerPlayer recipient : level.players()) send(recipient, owner, data);
    }
}
