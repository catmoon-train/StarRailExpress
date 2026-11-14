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
    public static final int FORM_REALM = 1;
    public static final int FORM_ESCAPE = 2;
    public static final int FORM_SHROUD = 4;
    private SackGhostPackets() {}

    public record RealmS2C(UUID owner, int remaining, int duration, int stage) implements CustomPacketPayload {
        public static final Type<RealmS2C> ID = new Type<>(Noellesroles.id("sack_ghost_realm"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RealmS2C> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeUUID(packet.owner()); buf.writeVarInt(packet.remaining()); buf.writeVarInt(packet.duration()); buf.writeVarInt(packet.stage()); },
                buf -> new RealmS2C(buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** 只公开外观状态，不公开背包、威胁来源或替死冷却。 */
    public record FormS2C(UUID owner, int flags, int remaining) implements CustomPacketPayload {
        public static final Type<FormS2C> ID = new Type<>(Noellesroles.id("sack_ghost_form"));
        public static final StreamCodec<RegistryFriendlyByteBuf, FormS2C> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeUUID(packet.owner()); buf.writeVarInt(packet.flags()); buf.writeVarInt(packet.remaining()); },
                buf -> new FormS2C(buf.readUUID(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** 蒙眼只发受害者；计时由有效对局时钟维护。 */
    public record VeilS2C(int remaining) implements CustomPacketPayload {
        public static final Type<VeilS2C> ID = new Type<>(Noellesroles.id("sack_ghost_veil"));
        public static final StreamCodec<RegistryFriendlyByteBuf, VeilS2C> CODEC = StreamCodec.of(
                (buf, packet) -> buf.writeVarInt(packet.remaining()), buf -> new VeilS2C(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(RealmS2C.ID, RealmS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(FormS2C.ID, FormS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(VeilS2C.ID, VeilS2C.CODEC);
    }

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

    public static void publishForm(ServerLevel level, ServerPlayer owner, int flags, int remaining) {
        FormS2C packet = new FormS2C(owner.getUUID(), flags, Math.max(0, remaining));
        for (ServerPlayer recipient : level.players()) ServerPlayNetworking.send(recipient, packet);
    }
}
