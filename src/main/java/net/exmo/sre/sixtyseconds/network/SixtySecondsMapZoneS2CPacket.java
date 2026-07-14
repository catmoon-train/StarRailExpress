package net.exmo.sre.sixtyseconds.network;

import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端→客户端：设置区域地图当前应扫描的 60s 区域（住宅/避难所/探索区）与「家」点位。
 * {@code active=false} 表示清除（回退到全图 playArea）。传送/进出探索区时低频发送。
 * 创造模式玩家额外接收所有队伍的避难所门位置（{@code shelterDoors}），用于在地图上显示。
 */
public record SixtySecondsMapZoneS2CPacket(boolean active, int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ, boolean hasHome, BlockPos home,
        boolean safeZone, List<BlockPos> shelterDoors) implements CustomPacketPayload {

    public static final Type<SixtySecondsMapZoneS2CPacket> ID = new Type<>(Noellesroles.id("sixty_seconds_map_zone"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsMapZoneS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeBoolean(packet.active());
                buf.writeVarInt(packet.minX());
                buf.writeVarInt(packet.minY());
                buf.writeVarInt(packet.minZ());
                buf.writeVarInt(packet.maxX());
                buf.writeVarInt(packet.maxY());
                buf.writeVarInt(packet.maxZ());
                buf.writeBoolean(packet.hasHome());
                if (packet.hasHome()) {
                    buf.writeBlockPos(packet.home());
                }
                buf.writeBoolean(packet.safeZone());
                // shelterDoors: 创造模式额外数据，普通玩家为空列表
                List<BlockPos> doors = packet.shelterDoors();
                buf.writeVarInt(doors.size());
                for (BlockPos door : doors) {
                    buf.writeBlockPos(door);
                }
            }, buf -> {
                boolean active = buf.readBoolean();
                int minX = buf.readVarInt();
                int minY = buf.readVarInt();
                int minZ = buf.readVarInt();
                int maxX = buf.readVarInt();
                int maxY = buf.readVarInt();
                int maxZ = buf.readVarInt();
                boolean hasHome = buf.readBoolean();
                BlockPos home = hasHome ? buf.readBlockPos() : BlockPos.ZERO;
                boolean safeZone = buf.readBoolean();
                int doorCount = buf.readVarInt();
                List<BlockPos> doors = new ArrayList<>(doorCount);
                for (int i = 0; i < doorCount; i++) {
                    doors.add(buf.readBlockPos());
                }
                return new SixtySecondsMapZoneS2CPacket(active, minX, minY, minZ, maxX, maxY, maxZ, hasHome, home,
                        safeZone, doors);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 一个玩家最近一次收到的区域（用于其切到创造模式后由 {@link #refreshCreativeDoors} 补发门列表）。 */
    private record LastZone(AABB zone, @Nullable BlockPos home, boolean safe) {
    }

    /** 玩家 UUID → 最近区域。开局/结束时由 {@link #clearAll()} 清空。 */
    private static final Map<UUID, LastZone> LAST_ZONES = new HashMap<>();

    /** 便捷发送：设置玩家的地图区域与家点位（zone 为 null 则忽略）。创造模式玩家额外接收所有队伍的避难所门位置。 */
    public static void send(ServerPlayer player, @Nullable AABB zone, @Nullable BlockPos home,
            boolean safeZone) {
        if (zone == null) {
            return;
        }
        // 记住该玩家当前所处区域：其可能在开局后（survival 时收过区域包）才切到创造模式，
        // 届时由 refreshCreativeDoors 用这份记忆重发一遍带门列表的包（「创造看不到别人的避难所门」根因）。
        LAST_ZONES.put(player.getUUID(), new LastZone(zone, home, safeZone));
        // 创造模式玩家额外接收所有队伍的避难所门；普通玩家为空列表，避免泄露他队位置。
        List<BlockPos> doors = player.isCreative()
                ? collectShelterDoors(player.serverLevel())
                : Collections.emptyList();
        ServerPlayNetworking.send(player, new SixtySecondsMapZoneS2CPacket(true,
                (int) Math.floor(zone.minX), (int) Math.floor(zone.minY), (int) Math.floor(zone.minZ),
                (int) Math.ceil(zone.maxX), (int) Math.ceil(zone.maxY), (int) Math.ceil(zone.maxZ),
                home != null, home == null ? BlockPos.ZERO : home, safeZone, doors));
    }

    /**
     * 收集所有队伍的避难所门位置供创造旁观者在地图上查看：
     * <ul>
     *   <li>避难所<b>室内主门</b>（{@code doorPos} 缓存扫描）优先，未找到退回避难所出生点；</li>
     *   <li>探险区一侧的<b>出口门</b>（{@code returnDoorPos}，即「返回住所」门）——旁观者也要看得到探险区的避难所门。</li>
     * </ul>
     */
    private static List<BlockPos> collectShelterDoors(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        List<BlockPos> doors = new ArrayList<>();
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            BlockPos door = net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.doorPos(level, team);
            if (door == null) {
                door = team.shelterSpawn;
            }
            if (door != null) {
                doors.add(door);
            }
            if (team.returnDoorPos != null) {
                doors.add(team.returnDoorPos);
            }
        }
        return doors;
    }

    /**
     * 周期性把避难所门列表补发给在线的<b>创造模式</b>玩家——他们常在开局后才切到创造观察，
     * 而区域包只在传送/进出探索区等时机低频下发，切模式当下不会重发，门列表便一直是空的。
     * 用 {@link #LAST_ZONES} 记的当前区域重发即可（普通玩家跳过，门列表照旧为空不泄露）。
     */
    public static void refreshCreativeDoors(ServerLevel level) {
        SixtySecondsState.Data data = SixtySecondsState.get(level);
        AABB overview = null;
        boolean overviewComputed = false;
        for (ServerPlayer player : level.players()) {
            if (!player.isCreative()) {
                continue;
            }
            LastZone last = LAST_ZONES.get(player.getUUID());
            if (last != null) {
                // 有过区域记忆（队员，或此前已发过总览的旁观者）：原样重发一遍，带上门列表。
                send(player, last.zone(), last.home(), last.safe());
                continue;
            }
            // 没有区域记忆 = 未参与游戏的创造旁观者：从没被传送过、拿不到任何区域，区域地图整个不激活
            //（连别人的家都看不到——正是本次要修的问题）。给他发一份覆盖所有队伍住宅+避难所的总览区域
            //（无个人家点），据此激活地图并显示所有队伍的避难所门（=各家）。首次发出后即写入 LAST_ZONES，
            // 之后每次走上面的 last!=null 分支持续重发。
            if (!overviewComputed) {
                overview = overviewZone(data);
                overviewComputed = true;
            }
            if (overview != null) {
                send(player, overview, null, false);
            }
        }
    }

    /**
     * 覆盖所有队伍住宅+避难所盒 + 共享探险区盒的总览区域（未参与的创造旁观者据此纵览各家与探险区）。
     * 无任何盒时返回 null。
     */
    private static AABB overviewZone(SixtySecondsState.Data data) {
        AABB union = null;
        for (SixtySecondsState.TeamData team : data.teams.values()) {
            union = expandBox(union, team.residentialBox);
            union = expandBox(union, team.shelterBox);
            union = expandBox(union, team.searchZoneBox);
        }
        return union;
    }

    private static AABB expandBox(@Nullable AABB acc, @Nullable AABB box) {
        if (box == null) {
            return acc;
        }
        return acc == null ? box : acc.minmax(box);
    }

    /**
     * 幂等补发：仅当玩家<b>当前记忆的区域</b>（{@link #LAST_ZONES}）不是 {@code zone} 时才发一次，避免重复发包。
     * 供「玩家已在家但地图区域可能未同步/发漏」的兜底对账用（拜访离开、门外事件、探险归来等路径若漏发区域包，
     * 会导致「回来地图不显示坐标」——区域仍指向别人家，客户端扫的是远处未加载区块，出不了图）。
     */
    public static void ensureZone(ServerPlayer player, @Nullable AABB zone, @Nullable BlockPos home, boolean safeZone) {
        if (zone == null) {
            return;
        }
        LastZone last = LAST_ZONES.get(player.getUUID());
        if (last != null && sameBox(last.zone(), zone)) {
            return; // 已是该区域，不重复发
        }
        send(player, zone, home, safeZone);
    }

    /** 两个区域盒的水平范围是否一致（Y 无关；与打包时的 floor/ceil 取整口径一致）。 */
    private static boolean sameBox(@Nullable AABB a, @Nullable AABB b) {
        return a != null && b != null
                && (int) Math.floor(a.minX) == (int) Math.floor(b.minX)
                && (int) Math.floor(a.minZ) == (int) Math.floor(b.minZ)
                && (int) Math.ceil(a.maxX) == (int) Math.ceil(b.maxX)
                && (int) Math.ceil(a.maxZ) == (int) Math.ceil(b.maxZ);
    }

    /** 便捷发送：清除玩家的地图区域（回退到全图）。 */
    public static void sendClear(ServerPlayer player) {
        LAST_ZONES.remove(player.getUUID());
        ServerPlayNetworking.send(player,
                new SixtySecondsMapZoneS2CPacket(false, 0, 0, 0, 0, 0, 0, false, BlockPos.ZERO, false,
                        Collections.emptyList()));
    }

    /** 开局/结束时清空区域记忆（避免跨局残留 UUID 项）。 */
    public static void clearAll() {
        LAST_ZONES.clear();
    }

    /** 客户端应用（在客户端接收器里调用）。 */
    public AABB toAabb() {
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
