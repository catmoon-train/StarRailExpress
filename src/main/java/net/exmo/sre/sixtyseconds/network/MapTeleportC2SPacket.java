package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：创造模式在区域地图（{@code AreaMapScreen}）上点击 家点位/避难所门/自定义标注 请求传送。
 * {@code exact}=true 表示坐标含精确 Y（家点位/门，直接传）；false 表示标注只有 XZ
 * （客户端填的是玩家当前 Y），服务端在该 XZ 上按给定 Y 上下扫可站立点，扫不到退回地表高度。
 * 服务端复核 创造模式或 OP（参照 {@code LootTableSaveC2SPacket} 门控）。
 */
public record MapTeleportC2SPacket(int x, int y, int z, boolean exact) implements CustomPacketPayload {

    public static final Type<MapTeleportC2SPacket> ID = new Type<>(Noellesroles.id("sixty_seconds_map_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapTeleportC2SPacket> CODEC =
            StreamCodec.ofMember(MapTeleportC2SPacket::encode, MapTeleportC2SPacket::decode);

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
        buf.writeBoolean(exact);
    }

    public static MapTeleportC2SPacket decode(RegistryFriendlyByteBuf buf) {
        return new MapTeleportC2SPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static void handle(MapTeleportC2SPacket payload, ServerPlayNetworking.Context context) {
        ServerPlayer player = context.player();
        if (!player.hasPermissions(2) && !player.isCreative()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos target = new BlockPos(payload.x(), payload.y(), payload.z());
        if (!payload.exact()) {
            target = resolveStandable(level, target);
        }
        player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.map_teleported",
                target.getX(), target.getY(), target.getZ()), true);
    }

    /** 在给定 XZ 上从参考 Y 上下扫（±24）可站立点；扫不到退回地表高度（创造能飞，悬空也无碍）。 */
    private static BlockPos resolveStandable(ServerLevel level, BlockPos pos) {
        for (int dy = 0; dy <= 24; dy++) {
            for (int sign : new int[] { 1, -1 }) {
                BlockPos candidate = pos.offset(0, dy * sign, 0);
                if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.above()).isAir()
                        && level.getBlockState(candidate.below()).isSolidRender(level, candidate.below())) {
                    return candidate;
                }
                if (dy == 0) {
                    break; // dy=0 正负相同，只测一次
                }
            }
        }
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos);
    }
}
