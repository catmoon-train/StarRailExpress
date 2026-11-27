package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 命令方块掉血 / 被摧毁的客户端反馈：玻璃破碎声 + 一次屏幕震动。
 *
 * <p>不带坐标：命令方块在场地中心的高空，而这条反馈是给全场的，所以音效按
 * {@code playNotifySound} 播在本地玩家身上，与 {@code CommandBlockAmbientSound} 同口径。</p>
 *
 * @param strength 震动强度：1 = 破坏进度推进，2 = 命令方块被彻底破坏
 */
public record CommandBlockImpactS2CPacket(int strength) implements CustomPacketPayload {
    public static final Type<CommandBlockImpactS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "command_block_impact"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommandBlockImpactS2CPacket> CODEC = StreamCodec
            .ofMember(CommandBlockImpactS2CPacket::write, CommandBlockImpactS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(1, strength));
    }

    private static CommandBlockImpactS2CPacket read(FriendlyByteBuf buf) {
        return new CommandBlockImpactS2CPacket(buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
