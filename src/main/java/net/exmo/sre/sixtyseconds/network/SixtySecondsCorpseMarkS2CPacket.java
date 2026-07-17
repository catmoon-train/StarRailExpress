package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 服务端→客户端：自己的<b>尸体标记</b>（区域地图上的一个红点）。
 * <p>
 * 区域地图的自定义标注（{@code SixtySecondsClientMapZone.MARKERS}）是<b>纯客户端</b>的一张静态表，
 * 服务端碰不到，所以死亡时得显式推一包过去；{@code add=false} 则是复活后把它清掉。
 * 一次死亡/一次复活各一包，不参与任何轮询。
 * </p>
 *
 * @param add  true=在 (x,z) 打标记；false=清除该标记
 * @param x    尸体世界坐标 X
 * @param y    尸体世界坐标 Y（聊天栏提示坐标用；地图只用 X/Z）
 * @param z    尸体世界坐标 Z
 */
public record SixtySecondsCorpseMarkS2CPacket(boolean add, int x, int y, int z)
        implements CustomPacketPayload {

    public static final Type<SixtySecondsCorpseMarkS2CPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_corpse_mark"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsCorpseMarkS2CPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeBoolean(packet.add());
                buf.writeVarInt(packet.x());
                buf.writeVarInt(packet.y());
                buf.writeVarInt(packet.z());
            }, buf -> new SixtySecondsCorpseMarkS2CPacket(buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 在尸体处打标记。 */
    public static void mark(ServerPlayer player, BlockPos pos) {
        ServerPlayNetworking.send(player,
                new SixtySecondsCorpseMarkS2CPacket(true, pos.getX(), pos.getY(), pos.getZ()));
    }

    /** 清除尸体标记（复活时）。 */
    public static void clear(ServerPlayer player, BlockPos pos) {
        ServerPlayNetworking.send(player,
                new SixtySecondsCorpseMarkS2CPacket(false, pos.getX(), pos.getY(), pos.getZ()));
    }
}
