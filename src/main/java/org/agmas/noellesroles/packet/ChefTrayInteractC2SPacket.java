package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/**
 * 玩家右键「客户端」食物盘 / 饮料盘时由客户端发来的交互包（客户端 -> 服务端）。
 *
 * <p>盘子只存在于客户端世界里，原版的 useItemOn 包服务端认不出这个方块，
 * 所以由客户端 mixin 拦截右键后改发本包，服务端在这里校验并记账。
 */
public record ChefTrayInteractC2SPacket(BlockPos pos) implements CustomPacketPayload {

    public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "chef_tray_interact");
    public static final Type<ChefTrayInteractC2SPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ChefTrayInteractC2SPacket> CODEC;

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public static ChefTrayInteractC2SPacket read(FriendlyByteBuf buf) {
        return new ChefTrayInteractC2SPacket(buf.readBlockPos());
    }

    static {
        CODEC = StreamCodec.ofMember(ChefTrayInteractC2SPacket::write, ChefTrayInteractC2SPacket::read);
    }
}
