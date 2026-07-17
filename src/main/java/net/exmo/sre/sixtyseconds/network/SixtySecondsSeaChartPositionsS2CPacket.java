package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务端→客户端：海图上的动态点位——本队<b>庇护所</b>坐标 + 在线<b>队友</b>坐标。
 * <p>
 * 只在玩家<b>开着海图</b>时按秒推送（{@link SixtySecondsSeaChartWatchC2SPacket} 登记观看者，
 * {@code SixtySecondsIslands.tickChartWatchers} 驱动）——队友位置天然每 tick 都在变，
 * 不可能塞进低频的海图元数据包；但也不该对全服常推。开屏订阅 / 关屏退订是这里唯一的流量纪律。
 * </p>
 *
 * @param hasShelter 本队庇护所坐标是否有效（未开局/无队伍时为 false）
 * @param shelterX   庇护所 X（{@code hasShelter=false} 时无意义）
 * @param shelterZ   庇护所 Z
 * @param mates      在线队友（不含自己）的名字与坐标
 */
public record SixtySecondsSeaChartPositionsS2CPacket(boolean hasShelter, int shelterX, int shelterZ,
        List<Mate> mates) implements CustomPacketPayload {

    /** 一名队友的海图点位。 */
    public record Mate(String name, int x, int z, boolean downed) {
    }

    public static final Type<SixtySecondsSeaChartPositionsS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart_positions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartPositionsS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeBoolean(packet.hasShelter());
                buf.writeVarInt(packet.shelterX());
                buf.writeVarInt(packet.shelterZ());
                buf.writeVarInt(packet.mates().size());
                for (Mate mate : packet.mates()) {
                    buf.writeUtf(mate.name(), 32);
                    buf.writeVarInt(mate.x());
                    buf.writeVarInt(mate.z());
                    buf.writeBoolean(mate.downed());
                }
            }, buf -> {
                boolean hasShelter = buf.readBoolean();
                int shelterX = buf.readVarInt();
                int shelterZ = buf.readVarInt();
                int count = buf.readVarInt();
                List<Mate> mates = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    mates.add(new Mate(buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
                }
                return new SixtySecondsSeaChartPositionsS2CPacket(hasShelter, shelterX, shelterZ, mates);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 按接收者所在队打包当前点位并发送（无队伍时只发一个空包，客户端照常渲染自己）。 */
    public static void send(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        SixtySecondsState.TeamData team = teamId < 0 ? null : SixtySecondsState.get(level).teams.get(teamId);
        BlockPos shelter = team == null ? null : team.shelterSpawn;
        List<Mate> mates = new ArrayList<>();
        if (team != null) {
            for (UUID uuid : team.members) {
                if (uuid.equals(player.getUUID())) {
                    continue;
                }
                ServerPlayer mate = level.getServer().getPlayerList().getPlayer(uuid);
                // 旁观者（已淘汰/观战）不在海图上暴露位置——否则死人能给活人当侦察兵
                if (mate == null || mate.isSpectator()) {
                    continue;
                }
                mates.add(new Mate(mate.getGameProfile().getName(), (int) mate.getX(), (int) mate.getZ(),
                        SixtySecondsStatsComponent.KEY.get(mate).downed));
            }
        }
        ServerPlayNetworking.send(player, new SixtySecondsSeaChartPositionsS2CPacket(shelter != null,
                shelter == null ? 0 : shelter.getX(), shelter == null ? 0 : shelter.getZ(), mates));
    }
}
