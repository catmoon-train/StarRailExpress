package org.agmas.noellesroles.packet;

import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.Noellesroles;

/** 按接收者过滤的可见性及资格；不发送 SAN 原值、他人的种子或轮盘暗弹。 */
public record RouletteDevilStateS2CPacket(List<UUID> demons, List<UUID> visible, List<UUID> lowSan,
        List<UUID> seeded, List<UUID> mature, List<Retry> retries, int wins, int required, int lives,
        long manifestUntil, UUID duelOpponent, long transitionUntil) implements CustomPacketPayload {
    /** 同一目标的下一次可挑战时刻；由服务端结算写入，客户端只用来显示倒计时。 */
    public record Retry(UUID target, long readyAt) {
        public static Retry of(UUID target, long readyAt) { return new Retry(target, readyAt); }
    }
    public static final Type<RouletteDevilStateS2CPacket> ID = new Type<>(Noellesroles.id("roulette_devil_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RouletteDevilStateS2CPacket> CODEC = StreamCodec.ofMember(
            (p, b) -> {
                writeIds(b, p.demons); writeIds(b, p.visible); writeIds(b, p.lowSan);
                writeIds(b, p.seeded); writeIds(b, p.mature);
                b.writeVarInt(p.retries.size());
                for (Retry retry : p.retries) { b.writeUUID(retry.target()); b.writeLong(retry.readyAt()); }
                b.writeVarInt(p.wins); b.writeVarInt(p.required); b.writeVarInt(p.lives);
                b.writeLong(p.manifestUntil); b.writeBoolean(p.duelOpponent != null);
                if (p.duelOpponent != null) b.writeUUID(p.duelOpponent);
                b.writeLong(p.transitionUntil);
            }, b -> new RouletteDevilStateS2CPacket(readIds(b), readIds(b), readIds(b), readIds(b), readIds(b),
                    readRetries(b), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong(),
                    b.readBoolean() ? b.readUUID() : null, b.readLong()));
    private static void writeIds(RegistryFriendlyByteBuf b, List<UUID> ids) {
        b.writeVarInt(ids.size()); for (UUID id : ids) b.writeUUID(id);
    }
    private static List<UUID> readIds(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 1024) throw new IllegalArgumentException("Too many roulette devil entries");
        var ids = new java.util.ArrayList<UUID>(count);
        for (int i = 0; i < count; i++) ids.add(b.readUUID());
        return List.copyOf(ids);
    }
    private static List<Retry> readRetries(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > 1024) throw new IllegalArgumentException("Too many roulette devil cooldowns");
        var retries = new java.util.ArrayList<Retry>(count);
        for (int i = 0; i < count; i++) retries.add(new Retry(b.readUUID(), b.readLong()));
        return List.copyOf(retries);
    }
    public long retryAt(UUID target) {
        for (Retry retry : retries) if (retry.target().equals(target)) return retry.readyAt();
        return 0L;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
