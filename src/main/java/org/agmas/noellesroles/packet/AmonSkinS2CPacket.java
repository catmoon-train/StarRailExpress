package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 阿蒙夺舍后的皮肤/名字顶替广播。
 *
 * <ul>
 *   <li>{@code amonId == null}：清除所有阿蒙伪装映射。</li>
 *   <li>{@code amonId != null, hostId != null}：阿蒙顶替 host 的皮肤与名字。</li>
 *   <li>{@code amonId != null, hostId == null}：清除该阿蒙的伪装。</li>
 * </ul>
 */
public record AmonSkinS2CPacket(UUID amonId, UUID hostId) implements CustomPacketPayload {
    public static final Type<AmonSkinS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "amon_skin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AmonSkinS2CPacket> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.amonId != null);
                if (p.amonId != null) buf.writeUUID(p.amonId);
                buf.writeBoolean(p.hostId != null);
                if (p.hostId != null) buf.writeUUID(p.hostId);
            },
            buf -> {
                UUID a = buf.readBoolean() ? buf.readUUID() : null;
                UUID h = buf.readBoolean() ? buf.readUUID() : null;
                return new AmonSkinS2CPacket(a, h);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
