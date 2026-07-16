package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.island.SixtySecondsIsland;
import net.exmo.sre.sixtyseconds.island.SixtySecondsIslands;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 服务端→客户端：海图数据（岛屿元数据 + 本队解锁迷雾）。客户端 SeaChartScreen 用岛屿 seed
 * 与服务端同一套形状函数重画轮廓，无需同步方块。{@code openScreen=true} 时客户端收到即打开海图。
 * 解锁状态<b>按接收者所在队</b>打包（创造模式全解锁），故必须逐人发送。
 */
public record SixtySecondsSeaChartS2CPacket(boolean enabled, boolean openScreen, int seaY,
        List<Entry> islands) implements CustomPacketPayload {

    /** 单岛条目：locked（未解锁）条目在客户端画成迷雾「未知海域」。 */
    public record Entry(int id, int level, int namePrefix, int nameSuffix, int centerX, int centerZ,
            int radius, long seed, boolean unlocked, boolean visited) {
    }

    public static final Type<SixtySecondsSeaChartS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_sea_chart"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsSeaChartS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeBoolean(packet.enabled());
                buf.writeBoolean(packet.openScreen());
                buf.writeVarInt(packet.seaY());
                buf.writeVarInt(packet.islands().size());
                for (Entry entry : packet.islands()) {
                    buf.writeVarInt(entry.id());
                    buf.writeVarInt(entry.level());
                    buf.writeVarInt(entry.namePrefix());
                    buf.writeVarInt(entry.nameSuffix());
                    buf.writeVarInt(entry.centerX());
                    buf.writeVarInt(entry.centerZ());
                    buf.writeVarInt(entry.radius());
                    buf.writeLong(entry.seed());
                    buf.writeBoolean(entry.unlocked());
                    buf.writeBoolean(entry.visited());
                }
            }, buf -> {
                boolean enabled = buf.readBoolean();
                boolean openScreen = buf.readBoolean();
                int seaY = buf.readVarInt();
                int count = buf.readVarInt();
                List<Entry> islands = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    islands.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                            buf.readLong(), buf.readBoolean(), buf.readBoolean()));
                }
                return new SixtySecondsSeaChartS2CPacket(enabled, openScreen, seaY, islands);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 按接收者视角（本队解锁集；创造=全解锁）打包并发送。 */
    public static void send(ServerPlayer player, boolean openScreen) {
        ServerLevel level = player.serverLevel();
        SixtySecondsIslands.Data data = SixtySecondsIslands.get(level);
        int teamId = SixtySecondsStatsComponent.KEY.get(player).teamId;
        Set<Integer> unlocked = data.teamUnlocked.get(teamId);
        Set<Integer> visited = data.teamVisited.get(teamId);
        boolean seeAll = player.isCreative() || player.isSpectator();
        List<Entry> entries = new ArrayList<>();
        int seaY = 0;
        for (SixtySecondsIsland island : data.save.islands) {
            seaY = island.seaY;
            boolean isUnlocked = seeAll || island.level <= 1
                    || (unlocked != null && unlocked.contains(island.id));
            entries.add(new Entry(island.id, island.level, island.namePrefix, island.nameSuffix,
                    island.centerX, island.centerZ, island.radius, island.seed, isUnlocked,
                    visited != null && visited.contains(island.id)));
        }
        ServerPlayNetworking.send(player,
                new SixtySecondsSeaChartS2CPacket(data.save.enabled, openScreen, seaY, entries));
    }
}
