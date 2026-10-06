package org.agmas.noellesroles.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 厨师「客户端」食物盘 / 饮料盘的同步包（服务端 -> 客户端）。
 *
 * <p>服务端并不真的放置方块，而是让所有客户端在自己的世界里 setBlock 画出这个盘子，
 * 因此需要显式通知客户端：新建（PLACE）、更新内容物（UPDATE）、移除（REMOVE）。
 *
 * <p>
 * 这里传的是<b>盘子当前摆放着的那一份物品</b>（{@link ItemStack}）而不是「空/满」布尔值：
 * 客户端要用它把食物/饮料的<b>真实物品模型</b>渲染在盘子里，而不是拿一个贴了物品贴图的方块糊弄。
 */
public record ChefTrayS2CPacket(int action, UUID trayId, BlockPos pos, boolean drink, ItemStack content)
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

    public static ChefTrayS2CPacket place(UUID trayId, BlockPos pos, boolean drink, ItemStack content) {
        return new ChefTrayS2CPacket(ACTION_PLACE, trayId, pos, drink, content);
    }

    public static ChefTrayS2CPacket update(UUID trayId, BlockPos pos, ItemStack content) {
        return new ChefTrayS2CPacket(ACTION_UPDATE, trayId, pos, false, content);
    }

    public static ChefTrayS2CPacket remove(UUID trayId, BlockPos pos) {
        return new ChefTrayS2CPacket(ACTION_REMOVE, trayId, pos, false, ItemStack.EMPTY);
    }

    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUUID(trayId);
        buf.writeBlockPos(pos);
        buf.writeBoolean(drink);
        // 直接写物品栈：客户端要靠它渲染真正的食物/饮料模型
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, content);
    }

    public static ChefTrayS2CPacket read(RegistryFriendlyByteBuf buf) {
        int action = buf.readVarInt();
        UUID trayId = buf.readUUID();
        BlockPos pos = buf.readBlockPos();
        boolean drink = buf.readBoolean();
        ItemStack content = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        if (content == null) {
            content = ItemStack.EMPTY;
        }
        return new ChefTrayS2CPacket(action, trayId, pos, drink, content);
    }

    static {
        CODEC = StreamCodec.ofMember(ChefTrayS2CPacket::write, ChefTrayS2CPacket::read);
    }
}
