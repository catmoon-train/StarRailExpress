package net.exmo.sre.sixtyseconds.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：打开 NPC 对话菜单（{@code NpcDialogueScreen}）。
 * 服务端按玩家上下文 + NPC 变体算好可用选项，客户端只负责展示：每个 {@link Option} 带动作 id
 * （{@code SixtySecondsNpcMenu.ACTION_*}）、可用状态与一个数字参数（交易=商品数、雇佣=价格、
 * 偷窃=成功率%，其余 0）。
 * <p>{@code entityId} 用 {@code entity.getId()}（4 字节）而非 UUID：{@code level.getEntity(id)}
 * 一步取回；服务端在 {@code handleAction} 里重校验类型/存活/距离，不信客户端。
 */
public record OpenNpcDialogueS2CPacket(int entityId, int variantId, String npcName, int tokens,
        List<Option> options) implements CustomPacketPayload {

    public record Option(int action, boolean enabled, int param) {
    }

    public static final Type<OpenNpcDialogueS2CPacket> ID = new Type<>(Noellesroles.id("open_npc_dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNpcDialogueS2CPacket> CODEC =
            StreamCodec.ofMember(OpenNpcDialogueS2CPacket::encode, OpenNpcDialogueS2CPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(variantId);
        buf.writeUtf(npcName);
        buf.writeVarInt(tokens);
        buf.writeVarInt(options.size());
        for (Option option : options) {
            buf.writeVarInt(option.action);
            buf.writeBoolean(option.enabled);
            buf.writeVarInt(option.param);
        }
    }

    public static OpenNpcDialogueS2CPacket decode(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int variantId = buf.readVarInt();
        String npcName = buf.readUtf();
        int tokens = buf.readVarInt();
        int count = buf.readVarInt();
        List<Option> options = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            options.add(new Option(buf.readVarInt(), buf.readBoolean(), buf.readVarInt()));
        }
        return new OpenNpcDialogueS2CPacket(entityId, variantId, npcName, tokens, options);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
