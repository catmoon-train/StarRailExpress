package pro.fazeclan.river.stupid_express.role.arsonist;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import pro.fazeclan.river.stupid_express.StupidExpress;

/** 客户端只能请求换装；命中与伤害不接受客户端提交。公开包不包含私有浇油名单。 */
public final class ArsonistPackets {
    private ArsonistPackets() {}

    public record ReloadC2S() implements CustomPacketPayload {
        public static final Type<ReloadC2S> ID = new Type<>(StupidExpress.id("arsonist_reload"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReloadC2S> CODEC =
                StreamCodec.of((buf, packet) -> {}, buf -> new ReloadC2S());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record HeatS2C(int heat) implements CustomPacketPayload {
        public static final Type<HeatS2C> ID = new Type<>(StupidExpress.id("arsonist_heat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HeatS2C> CODEC =
                StreamCodec.of((buf, packet) -> buf.writeVarInt(packet.heat()), buf -> new HeatS2C(buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record MomentS2C(java.util.UUID owner, boolean active, int remainingTicks) implements CustomPacketPayload {
        public MomentS2C(java.util.UUID owner, boolean active) {
            this(owner, active, active ? ArsonistRules.MOMENT_TICKS : 0);
        }
        public static final Type<MomentS2C> ID = new Type<>(StupidExpress.id("arsonist_moment"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MomentS2C> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeUUID(packet.owner()); buf.writeBoolean(packet.active()); buf.writeVarInt(packet.remainingTicks()); },
                buf -> new MomentS2C(buf.readUUID(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record IntroS2C(java.util.UUID owner, net.minecraft.world.phys.Vec3 position, float yaw,
            net.minecraft.world.phys.AABB mapBounds, int elapsedTicks, boolean active) implements CustomPacketPayload {
        public static final Type<IntroS2C> ID = new Type<>(StupidExpress.id("arsonist_intro"));
        public static final StreamCodec<RegistryFriendlyByteBuf, IntroS2C> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeUUID(p.owner());
            buf.writeDouble(p.position().x); buf.writeDouble(p.position().y); buf.writeDouble(p.position().z);
            buf.writeFloat(p.yaw());
            buf.writeDouble(p.mapBounds().minX); buf.writeDouble(p.mapBounds().minY); buf.writeDouble(p.mapBounds().minZ);
            buf.writeDouble(p.mapBounds().maxX); buf.writeDouble(p.mapBounds().maxY); buf.writeDouble(p.mapBounds().maxZ);
            buf.writeVarInt(p.elapsedTicks()); buf.writeBoolean(p.active());
        }, buf -> new IntroS2C(buf.readUUID(), new net.minecraft.world.phys.Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat(), new net.minecraft.world.phys.AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public record DeathFxS2C() implements CustomPacketPayload {
        public static final Type<DeathFxS2C> ID = new Type<>(StupidExpress.id("arsonist_death_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeathFxS2C> CODEC = StreamCodec.of(
                (buf, packet) -> {}, buf -> new DeathFxS2C());
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    /** 大油罐遥控状态：相机不再绑到油罐，改由该包告知客户端当前受控实体 id。 */
    public record OilCanControlS2C(int entityId, boolean active) implements CustomPacketPayload {
        public static final Type<OilCanControlS2C> ID = new Type<>(StupidExpress.id("arsonist_oil_can_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OilCanControlS2C> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeVarInt(packet.entityId()); buf.writeBoolean(packet.active()); },
                buf -> new OilCanControlS2C(buf.readVarInt(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ReloadC2S.ID, ReloadC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(HeatS2C.ID, HeatS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(MomentS2C.ID, MomentS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(OilCanControlS2C.ID, OilCanControlS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(IntroS2C.ID, IntroS2C.CODEC);
        PayloadTypeRegistry.playS2C().register(DeathFxS2C.ID, DeathFxS2C.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReloadC2S.ID, (packet, context) ->
                context.server().execute(() -> {
                    ArsonistRoleData data = ArsonistRoleData.of(context.player());
                    if (data != null) data.startReload();
                }));
    }
}
