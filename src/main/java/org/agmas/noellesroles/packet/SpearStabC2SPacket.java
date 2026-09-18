package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/**
 * 矛的直刺网络包：客户端左键时发出，服务端据此执行一次穿刺结算。
 */
public record SpearStabC2SPacket(int entityId) implements CustomPacketPayload {
    public static final ResourceLocation SPEAR_STAB_PAYLOAD_ID = ResourceLocation
            .fromNamespaceAndPath(Noellesroles.MOD_ID, "spear_stab");
    public static final CustomPacketPayload.Type<SpearStabC2SPacket> ID = new CustomPacketPayload.Type<>(
            SPEAR_STAB_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SpearStabC2SPacket> CODEC;

    public SpearStabC2SPacket(int entityId) {
        this.entityId = entityId;
    }

    @Nullable
    public Entity getEntity(Level level) {
        return level.getEntity(this.entityId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entityId);
    }

    public static SpearStabC2SPacket read(FriendlyByteBuf buf) {
        return new SpearStabC2SPacket(buf.readVarInt());
    }

    public int entityId() {
        return this.entityId;
    }

    static {
        CODEC = StreamCodec.ofMember(SpearStabC2SPacket::write, SpearStabC2SPacket::read);
    }
}
