package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsNpcMenu;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：在 NPC 对话菜单里点了某个动作。
 * 服务端 {@link SixtySecondsNpcMenu#handleAction} 对每个动作全量重校验（类型/存活/距离/相位/资金），
 * 不信客户端下发的 enabled 与 param。
 */
public record NpcDialogueActionC2SPacket(int entityId, int action, int param) implements CustomPacketPayload {
    public static final Type<NpcDialogueActionC2SPacket> ID = new Type<>(Noellesroles.id("npc_dialogue_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NpcDialogueActionC2SPacket> CODEC =
            StreamCodec.ofMember(NpcDialogueActionC2SPacket::encode, NpcDialogueActionC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(action);
        buf.writeVarInt(param);
    }

    public static NpcDialogueActionC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new NpcDialogueActionC2SPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(NpcDialogueActionC2SPacket payload, ServerPlayNetworking.Context context) {
        SixtySecondsNpcMenu.handleAction(context.player(), payload.entityId(), payload.action(),
                payload.param());
    }
}
