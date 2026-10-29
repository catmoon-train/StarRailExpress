package org.agmas.noellesroles.game.modes.echo.specialization;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization;

import java.util.*;

/** 本人专长候选；只含本阵营两项与窗口截止，不含他人信息。 */
public record EchoSpecializationPayload(UUID match, EchoFaction faction, String contract, long deadline,
                                        List<Option> options, String selected, boolean locked) implements CustomPacketPayload {
    public record Option(String id, String nameKey, String descKey) {
        public Option {
            Objects.requireNonNull(id); Objects.requireNonNull(nameKey); Objects.requireNonNull(descKey);
            if (id.length() > 32 || nameKey.length() > 64 || descKey.length() > 64) throw new IllegalArgumentException("无效专长选项");
        }
    }

    public static final Type<EchoSpecializationPayload> ID = new Type<>(ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/specialization"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EchoSpecializationPayload> CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.match()); b.writeEnum(p.faction()); b.writeUtf(p.contract(), 32); b.writeVarLong(p.deadline());
        b.writeVarInt(p.options().size());
        for (var option : p.options()) { b.writeUtf(option.id(), 32); b.writeUtf(option.nameKey(), 64); b.writeUtf(option.descKey(), 64); }
        b.writeUtf(p.selected(), 32); b.writeBoolean(p.locked());
    }, b -> {
        var match = b.readUUID(); var faction = b.readEnum(EchoFaction.class); var contract = b.readUtf(32);
        long deadline = b.readVarLong(); int size = b.readVarInt();
        if (size < 0 || size > 2) throw new IllegalArgumentException("专长候选数量超限");
        var options = new ArrayList<Option>(size);
        for (int n = 0; n < size; n++) options.add(new Option(b.readUtf(32), b.readUtf(64), b.readUtf(64)));
        return new EchoSpecializationPayload(match, faction, contract, deadline, options, b.readUtf(32), b.readBoolean());
    });

    public static EchoSpecializationPayload of(UUID match, EchoFaction faction, String contract, long deadline,
                                               String selected, boolean locked) {
        var options = EchoSpecialization.of(faction).stream()
                .map(spec -> new Option(spec.id(), spec.nameKey(), spec.descriptionKey())).toList();
        return new EchoSpecializationPayload(match, faction, contract == null ? "" : contract, deadline, options, selected, locked);
    }

    public EchoSpecializationPayload {
        Objects.requireNonNull(match); Objects.requireNonNull(faction); Objects.requireNonNull(options); Objects.requireNonNull(selected);
        contract = contract == null ? "" : contract;
        options = List.copyOf(options);
        if (deadline < 0 || options.size() > 2) throw new IllegalArgumentException("无效专长快照");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
