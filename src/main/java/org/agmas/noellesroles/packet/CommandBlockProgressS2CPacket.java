package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 命令方块破坏进度。
 * <p>{@code goal} 为 0 时客户端把整块 HUD 藏起来；{@code eventTotal} 为 0 时不画下个事件的倒计时条。
 */
public record CommandBlockProgressS2CPacket(int progress, int goal, int eventRemaining, int eventTotal)
        implements CustomPacketPayload {
    public static final Type<CommandBlockProgressS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "command_block_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommandBlockProgressS2CPacket> CODEC = StreamCodec
            .ofMember(CommandBlockProgressS2CPacket::write, CommandBlockProgressS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, progress));
        buf.writeVarInt(Math.max(0, goal));
        buf.writeVarInt(Math.max(0, eventRemaining));
        buf.writeVarInt(Math.max(0, eventTotal));
    }

    private static CommandBlockProgressS2CPacket read(FriendlyByteBuf buf) {
        return new CommandBlockProgressS2CPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
