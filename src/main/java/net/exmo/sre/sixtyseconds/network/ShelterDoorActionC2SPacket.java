package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDoorMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：统一门菜单里选中的操作（action = {@code SixtySecondsDoorMenu.ACTION_*}）。
 * {@code rvEntityId >= 0} 表示这是房车控制台的动作，走房车路径（按实体+队伍判定），否则走门坐标路径。
 */
public record ShelterDoorActionC2SPacket(BlockPos pos, int action, int rvEntityId)
        implements CustomPacketPayload {
    public static final Type<ShelterDoorActionC2SPacket> ID = new Type<>(Noellesroles.id("shelter_door_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShelterDoorActionC2SPacket> CODEC =
            StreamCodec.ofMember(ShelterDoorActionC2SPacket::encode, ShelterDoorActionC2SPacket::decode);

    /** 门（非房车）用。 */
    public ShelterDoorActionC2SPacket(BlockPos pos, int action) {
        this(pos, action, -1);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(action);
        buf.writeInt(rvEntityId);
    }

    public static ShelterDoorActionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new ShelterDoorActionC2SPacket(buf.readBlockPos(), buf.readVarInt(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(ShelterDoorActionC2SPacket payload, ServerPlayNetworking.Context context) {
        if (payload.rvEntityId() >= 0) {
            net.minecraft.world.entity.Entity entity =
                    context.player().serverLevel().getEntity(payload.rvEntityId());
            if (entity instanceof net.exmo.sre.sixtyseconds.content.entity.SixtySecondsRvEntity rv) {
                SixtySecondsDoorMenu.handleRvAction(context.player(), rv, payload.action());
            }
            return;
        }
        SixtySecondsDoorMenu.handleAction(context.player(), payload.pos(), payload.action());
    }
}
