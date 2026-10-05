package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 厨师「客户端」食物盘 / 饮料盘的同步包（服务端 -> 客户端）。
 *
 * <p>服务端并不真的放置方块，而是让所有客户端在自己的世界里 setBlock 画出这个盘子，
 * 因此需要显式通知客户端：新建（PLACE）、更新内容物（UPDATE）、移除（REMOVE）。
 */
public record ChefTrayS2CPacket(int action, UUID trayId, BlockPos pos, boolean drink, boolean filled)
        implements CustomPacketPayload {

    /** 新建一个盘子。 */
    public static final int ACTION_PLACE = 0;
    /** 盘子内容物变化（空 <-> 有东西）。 */
    public static final int ACTION_UPDATE = 1;
    /** 移除一个盘子。 */
    public static final int ACTION_REMOVE = 2;

    public static final ResourceLocation PACKET_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
            "chef_tray");
    public static final Type<ChefTrayS2CPacket> ID = new Type<>(PACKET_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ChefTrayS2CPacket> CODEC;

    public static ChefTrayS2CPacket place(UUID trayId, BlockPos pos, boolean drink, boolean filled) {
        return new ChefTrayS2CPacket(ACTION_PLACE, trayId, pos, drink, filled);
    }

    public static ChefTrayS2CPacket update(UUID trayId, BlockPos pos, boolean filled) {
        return new ChefTrayS2CPacket(ACTION_UPDATE, trayId, pos, false, filled);
    }

    public static ChefTrayS2CPacket remove(UUID trayId, BlockPos pos) {
        return new ChefTrayS2CPacket(ACTION_REMOVE, trayId, pos, false, false);
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUUID(trayId);
        buf.writeBlockPos(pos);
        buf.writeBoolean(drink);
        buf.writeBoolean(filled);
    }

    public static ChefTrayS2CPacket read(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        UUID trayId = buf.readUUID();
        BlockPos pos = buf.readBlockPos();
        boolean drink = buf.readBoolean();
        boolean filled = buf.readBoolean();
        return new ChefTrayS2CPacket(action, trayId, pos, drink, filled);
    }

    static {
        CODEC = StreamCodec.ofMember(ChefTrayS2CPacket::write, ChefTrayS2CPacket::read);
    }
}
