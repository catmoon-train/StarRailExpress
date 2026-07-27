package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 咒术师·领域展开技能包。
 * 客户端在背包 {@code LimitedInventoryScreen} 点选一名已被诅咒且存活的目标，请求对其展开领域。
 */
public record WarlockDomainC2SPacket(UUID target) implements CustomPacketPayload {
    public static final ResourceLocation WARLOCK_DOMAIN_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "warlock_domain");
    public static final CustomPacketPayload.Type<WarlockDomainC2SPacket> ID = new CustomPacketPayload.Type<>(WARLOCK_DOMAIN_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, WarlockDomainC2SPacket> CODEC;

    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(this.target);
    }

    public static WarlockDomainC2SPacket read(FriendlyByteBuf buf) {
        return new WarlockDomainC2SPacket(buf.readUUID());
    }

    static {
        CODEC = StreamCodec.ofMember(WarlockDomainC2SPacket::write, WarlockDomainC2SPacket::read);
    }
}
