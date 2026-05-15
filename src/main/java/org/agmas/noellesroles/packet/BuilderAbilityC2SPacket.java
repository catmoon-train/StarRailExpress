package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

import java.util.UUID;

/**
 * 建筑师技能网络包（客户端 -> 服务端）
 * 当玩家按下技能键时发送
 */
public record BuilderAbilityC2SPacket(boolean shiftDown) implements CustomPacketPayload {
    
    public static final Type<BuilderAbilityC2SPacket> ID = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "builder_ability")
    );
    
    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderAbilityC2SPacket> CODEC = StreamCodec.ofMember(
        BuilderAbilityC2SPacket::write, BuilderAbilityC2SPacket::read
    );
    
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(shiftDown);
    }
    
    public static BuilderAbilityC2SPacket read(FriendlyByteBuf buf) {
        return new BuilderAbilityC2SPacket(buf.readBoolean());
    }
    
    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
