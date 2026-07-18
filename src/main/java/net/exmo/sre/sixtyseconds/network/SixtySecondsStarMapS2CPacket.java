package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.config.SixtySecondsConfig;
import net.exmo.sre.sixtyseconds.config.SixtySecondsConfigStore;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端→客户端：星图星级区域同步。
 * <p>
 * 把服务端 {@link SixtySecondsConfig#areaLevelOverrides}（管理员用魔杖/命令划定的星级区域）
 * 推送给客户端，客户端据此调用 {@code StarMapManager.setStarRegions(...)}，让全屏星图与
 * HUD 小地图能绘制星级边框、标签与所在区域指示。
 * <p>
 * 与海图不同：星级区域是静态配置，不需要逐秒推送。客户端打开星图或首次手持星图时
 * 发 {@link SixtySecondsStarMapRequestC2SPacket} 请求，服务端收到后回本包；服务端也会在
 * 玩家加入时主动推一次，保证 HUD 在不打开全屏图的情况下也有数据。
 * <p>
 * 家居位置暂不同步（{@code StarMapManager.homePos} 保持 null），未来如需可在此包扩展。
 */
public record SixtySecondsStarMapS2CPacket(List<RegionEntry> regions) implements CustomPacketPayload {

    /** 单条星级区域：世界坐标盒（两角含端点，写入时已取正序）+ 等级 1..5 + 可选名字。 */
    public record RegionEntry(int minX, int minZ, int maxX, int maxZ, int level, String name) {
    }

    public static final Type<SixtySecondsStarMapS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_star_map"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsStarMapS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeVarInt(packet.regions().size());
                for (RegionEntry r : packet.regions()) {
                    buf.writeVarInt(r.minX());
                    buf.writeVarInt(r.minZ());
                    buf.writeVarInt(r.maxX());
                    buf.writeVarInt(r.maxZ());
                    buf.writeVarInt(r.level());
                    buf.writeUtf(r.name() == null ? "" : r.name());
                }
            }, buf -> {
                int count = buf.readVarInt();
                List<RegionEntry> regions = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int minX = buf.readVarInt();
                    int minZ = buf.readVarInt();
                    int maxX = buf.readVarInt();
                    int maxZ = buf.readVarInt();
                    int level = buf.readVarInt();
                    String name = buf.readUtf();
                    regions.add(new RegionEntry(minX, minZ, maxX, maxZ, level,
                            name == null || name.isEmpty() ? null : name));
                }
                return new SixtySecondsStarMapS2CPacket(regions);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 从当前地图配置打包并发送给指定玩家。 */
    public static void send(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<RegionEntry> entries = new ArrayList<>();
        SixtySecondsConfigStore.current(level).ifPresent(config -> {
            if (config.areaLevelOverrides != null) {
                for (SixtySecondsConfig.LevelRegion lr : config.areaLevelOverrides) {
                    if (lr == null || lr.min == null || lr.max == null) {
                        continue;
                    }
                    // 两角取正序，保证 minX<=maxX
                    int minX = Math.min(lr.min.x, lr.max.x);
                    int maxX = Math.max(lr.min.x, lr.max.x);
                    int minZ = Math.min(lr.min.z, lr.max.z);
                    int maxZ = Math.max(lr.min.z, lr.max.z);
                    int lvl = Math.max(1, Math.min(5, lr.level));
                    entries.add(new RegionEntry(minX, minZ, maxX, maxZ, lvl, lr.name));
                }
            }
        });
        ServerPlayNetworking.send(player, new SixtySecondsStarMapS2CPacket(entries));
    }
}
