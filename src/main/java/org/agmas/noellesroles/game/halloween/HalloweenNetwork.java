package org.agmas.noellesroles.game.halloween;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 仅下行的活动快照。共享栏内容由原版容器协议同步。 */
public final class HalloweenNetwork {
    private static final Map<UUID, State> CLIENT = new HashMap<>();
    private HalloweenNetwork() {}

    public record State(UUID player, UUID partner, boolean suppressed) {}

    public record Snapshot(ResourceLocation dimension, List<State> states) implements CustomPacketPayload {
        public static final Type<Snapshot> ID = new Type<>(Noellesroles.id("halloween_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.ofMember(
                (packet, buf) -> {
                    buf.writeResourceLocation(packet.dimension);
                    buf.writeVarInt(packet.states.size());
                    for (State state : packet.states) {
                        buf.writeUUID(state.player);
                        buf.writeBoolean(state.partner != null);
                        if (state.partner != null) buf.writeUUID(state.partner);
                        buf.writeBoolean(state.suppressed);
                    }
                }, buf -> {
                    var dimension = buf.readResourceLocation();
                    int size = buf.readVarInt();
                    if (size < 0 || size > 1024) throw new IllegalArgumentException("Invalid Halloween state size");
                    List<State> states = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        UUID player = buf.readUUID();
                        UUID partner = buf.readBoolean() ? buf.readUUID() : null;
                        states.add(new State(player, partner, buf.readBoolean()));
                    }
                    return new Snapshot(dimension, states);
                });
        @Override public Type<? extends CustomPacketPayload> type() { return ID; }
    }

    public static void register() { PayloadTypeRegistry.playS2C().register(Snapshot.ID, Snapshot.CODEC); }

    public static void sync(ServerLevel level) {
        List<State> states = level.players().stream().limit(1024)
                .map(p -> new State(p.getUUID(), HalloweenSoulLinks.partnerId(p),
                        HalloweenCombat.isShieldSuppressed(p))).toList();
        send(level, states);
    }

    public static void syncEmpty(ServerLevel level) { send(level, List.of()); }

    private static void send(ServerLevel level, List<State> states) {
        Snapshot snapshot = new Snapshot(level.dimension().location(), states);
        for (var player : level.players()) {
            if (ServerPlayNetworking.canSend(player, Snapshot.ID)) ServerPlayNetworking.send(player, snapshot);
        }
    }

    public static void apply(Snapshot snapshot) {
        CLIENT.clear();
        for (State state : snapshot.states) CLIENT.put(state.player, state);
    }

    public static void clearClient() { CLIENT.clear(); }
    public static UUID clientPartner(UUID player) {
        State state = CLIENT.get(player);
        return state == null ? null : state.partner;
    }
    public static boolean isSuppressed(UUID player) {
        State state = CLIENT.get(player);
        return state != null && state.suppressed;
    }
    public static List<State> clientStates() { return List.copyOf(CLIENT.values()); }
}
