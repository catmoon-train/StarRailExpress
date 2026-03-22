package org.agmas.noellesroles.packet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 邮差传递网络包
 * 
 * 从客户端发送到服务端，包含：
 * - 操作类型（打开界面、放入物品、确认交换、取消）
 * - 目标玩家 UUID
 * - 物品数据（如果有）
 */
public record PostmanC2SPacket(
    Action action,
    UUID targetPlayer,
    ItemStack item
) implements CustomPacketPayload {
    
    public static final ResourceLocation POSTMAN_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "postman_delivery");
    public static final Type<PostmanC2SPacket> ID = new Type<>(POSTMAN_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, PostmanC2SPacket> CODEC;
    
    public enum Action {
        OPEN_DELIVERY,      // 打开传递界面
        SET_ITEM,           // 放入物品
        CONFIRM,            // 确认交换
        CANCEL              // 取消传递
    }
    
    public PostmanC2SPacket(Action action, UUID targetPlayer, ItemStack item) {
        this.action = action;
        this.targetPlayer = targetPlayer;
        this.item = item != null ? item : ItemStack.EMPTY;
    }
    
    // 简化构造函数（无物品）
    public PostmanC2SPacket(Action action, UUID targetPlayer) {
        this(action, targetPlayer, ItemStack.EMPTY);
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
    
    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeUUID(this.targetPlayer);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, this.item);
    }
    
    public static PostmanC2SPacket read(RegistryFriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        UUID targetPlayer = buf.readUUID();
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        return new PostmanC2SPacket(action, targetPlayer, item);
    }
    
    public Action action() {
        return this.action;
    }
    
    public UUID targetPlayer() {
        return this.targetPlayer;
    }
    
    public ItemStack item() {
        return this.item;
    }
    
    static {
        CODEC = StreamCodec.ofMember(PostmanC2SPacket::write, PostmanC2SPacket::read);
    }
}