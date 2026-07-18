package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端→客户端：广播某玩家的 60s 血量（health / healthMax）。
 * <p>
 * 由 {@code SixtySecondsStatsComponent.sync()} 在血量<b>实际变化时</b>触发（非每 tick、非每次 sync），
 * 发给同维度所有其他玩家。客户端收到后存入 {@link #CLIENT_HEALTH} 静态表，
 * 供 {@code SixtySecondsCombatHud} 在准星对准他人时显示血量条与伤害数字。
 * <p>
 * 与 CCA 精简变体分离：精简变体仍只带队伍/身份/状态位（~5 字节），
 * 血量走独立包（~12 字节：UUID 16B + 2 varint），只在血量变化时发。
 *
 * @param playerId  目标玩家 UUID
 * @param health    当前 60s 血量
 * @param healthMax 血量上限
 */
public record PlayerHealthS2CPacket(UUID playerId, int health, int healthMax)
        implements CustomPacketPayload {

    public static final Type<PlayerHealthS2CPacket> ID =
            new Type<>(Noellesroles.id("player_health_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerHealthS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeUUID(packet.playerId());
                buf.writeVarInt(packet.health());
                buf.writeVarInt(packet.healthMax());
            }, buf -> new PlayerHealthS2CPacket(buf.readUUID(), buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 客户端血量缓存：playerId → {health, healthMax}。CombatHud 读这里获取他人血量。 */
    public static final Map<UUID, int[]> CLIENT_HEALTH = new ConcurrentHashMap<>();

    /**
     * 服务端广播：把源玩家的血量发给同维度所有其他玩家。
     * 只在血量变化时调用（由 {@code SixtySecondsStatsComponent.sync()} 触发）。
     */
    public static void broadcast(ServerPlayer source, int health, int healthMax) {
        var packet = new PlayerHealthS2CPacket(source.getUUID(), health, healthMax);
        for (ServerPlayer recipient : source.serverLevel().players()) {
            if (recipient != source) {
                ServerPlayNetworking.send(recipient, packet);
            }
        }
    }
}
