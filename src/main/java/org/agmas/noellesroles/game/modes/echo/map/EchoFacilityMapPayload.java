package org.agmas.noellesroles.game.modes.echo.map;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.state.EchoMachineView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 所有阵营与观察者共用的固定设施快照；不包含运行状态、出生点或私人信息。 */
public record EchoFacilityMapPayload(UUID match, ResourceLocation dimension, String mapId,
                                     List<Entry> entries) implements CustomPacketPayload {
    public static final UUID NONE = new UUID(0, 0);
    public static final int MAX_ENTRIES = 2048;
    public static final Type<EchoFacilityMapPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("noellesroles", "echo_facility_map"));
    private static final EchoMachineView.Type[] TYPES = EchoMachineView.Type.values();

    public record Entry(EchoMachineView.Type type, double x, double y, double z, int number) {
        public Entry {
            Objects.requireNonNull(type);
            // 与近距机器投影采用相同世界范围，且拒绝 NaN/Infinity。
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || y < -2048 || y > 2048
                    || number < 1 || number > MAX_ENTRIES)
                throw new IllegalArgumentException("无效设施地图点位");
        }
    }

    public EchoFacilityMapPayload {
        Objects.requireNonNull(match);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(mapId);
        Objects.requireNonNull(entries);
        if (mapId.length() > 128 || (!NONE.equals(match) && mapId.isBlank()))
            throw new IllegalArgumentException("无效设施地图ID");
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("设施地图点位超过2048项");
        entries = List.copyOf(entries);
        if (NONE.equals(match) && (!mapId.isEmpty() || !entries.isEmpty()))
            throw new IllegalArgumentException("空设施快照不能携带地图或点位");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoFacilityMapPayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeUUID(p.match());
        buf.writeResourceLocation(p.dimension());
        buf.writeUtf(p.mapId(), 128);
        buf.writeVarInt(p.entries().size());
        for (var entry : p.entries()) {
            buf.writeVarInt(entry.type().ordinal());
            buf.writeDouble(entry.x()); buf.writeDouble(entry.y()); buf.writeDouble(entry.z());
            buf.writeVarInt(entry.number());
        }
    }, buf -> {
        var match = buf.readUUID();
        var dimension = buf.readResourceLocation();
        var mapId = buf.readUtf(128);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_ENTRIES) throw new DecoderException("设施地图点位数量越界: " + size);
        var entries = new ArrayList<Entry>(size);
        for (int i = 0; i < size; i++) {
            int ordinal = buf.readVarInt();
            if (ordinal < 0 || ordinal >= TYPES.length)
                throw new DecoderException("未知设施地图机器类型: " + ordinal);
            entries.add(new Entry(TYPES[ordinal], buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt()));
        }
        return new EchoFacilityMapPayload(match, dimension, mapId, entries);
    });

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
