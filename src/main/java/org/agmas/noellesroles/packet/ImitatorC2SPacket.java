package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ImitatorC2SPacket(Action action, @Nullable UUID targetUuid) implements CustomPacketPayload {
    public static final ResourceLocation IMITATOR_PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "imitator_ability");
    public static final Type<ImitatorC2SPacket> ID = new Type<>(IMITATOR_PAYLOAD_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ImitatorC2SPacket> CODEC;

    public enum Action {
        COPY,           // 复制活人能力
        EAT_START,      // 开始吃尸体
        EAT_CANCEL,     // 取消吃尸体
        SWITCH_SLOT,    // 切换槽位
        USE_ABILITY     // 使用能力
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeEnum(this.action());
        buf.writeBoolean(this.targetUuid() != null);
        if (this.targetUuid() != null) {
            buf.writeUUID(this.targetUuid());
        }
    }

    public static ImitatorC2SPacket read(FriendlyByteBuf buf) {
        Action action = buf.readEnum(Action.class);
        boolean hasTarget = buf.readBoolean();
        UUID target = hasTarget ? buf.readUUID() : null;
        return new ImitatorC2SPacket(action, target);
    }

    static {
        CODEC = StreamCodec.ofMember(ImitatorC2SPacket::write, ImitatorC2SPacket::read);
    }
}
