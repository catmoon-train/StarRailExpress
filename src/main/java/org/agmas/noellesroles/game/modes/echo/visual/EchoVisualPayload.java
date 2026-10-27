package org.agmas.noellesroles.game.modes.echo.visual;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 公共狂暴外观快照 + 仅接收者本人的无方向恐惧强度；不含手牌、契约、目标位置或锁定值。 */
public record EchoVisualPayload(UUID match, ResourceLocation dimension, List<UUID> interceptors, int pressure)
        implements CustomPacketPayload {
    public static final UUID NONE = new UUID(0L, 0L);
    private static final int MAX_PLAYERS = 256;
    public static final Type<EchoVisualPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/visual"));
    public EchoVisualPayload {
        interceptors = List.copyOf(interceptors);
        if (interceptors.size() > MAX_PLAYERS || pressure < 0 || pressure > 100)
            throw new IllegalArgumentException("无效回声视觉快照");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoVisualPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.match()); b.writeResourceLocation(p.dimension()); b.writeVarInt(p.interceptors().size());
        p.interceptors().forEach(b::writeUUID); b.writeVarInt(p.pressure());
    }, b -> {
        UUID match = b.readUUID(); var dimension = b.readResourceLocation(); int count = b.readVarInt();
        if (count < 0 || count > MAX_PLAYERS) throw new IllegalArgumentException("过多回声视觉玩家");
        List<UUID> players = new ArrayList<>(count);
        for (int i = 0; i < count; i++) players.add(b.readUUID());
        return new EchoVisualPayload(match, dimension, players, b.readVarInt());
    });
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
