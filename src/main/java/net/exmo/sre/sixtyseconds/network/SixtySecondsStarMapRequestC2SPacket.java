package net.exmo.sre.sixtyseconds.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.Noellesroles;

/**
 * 客户端→服务端：请求刷新星图星级区域数据。
 * <p>
 * 星级区域是静态配置，不需要逐秒推送。客户端在以下时机会发送本包，服务端收到后
 * 回一份 {@link SixtySecondsStarMapS2CPacket}：
 * <ul>
 *   <li>玩家右键打开全屏星图时（保证看到最新配置）；</li>
 *   <li>玩家首次手持星图物品时（让 HUD 小地图也能显示星级指示）。</li>
 * </ul>
 * 包体为空——只是个触发器，服务端按当前地图配置打包回送。
 */
public record SixtySecondsStarMapRequestC2SPacket() implements CustomPacketPayload {

    public static final Type<SixtySecondsStarMapRequestC2SPacket> ID =
            new Type<>(Noellesroles.id("sixty_seconds_star_map_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SixtySecondsStarMapRequestC2SPacket> CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                // 空包体
            }, buf -> new SixtySecondsStarMapRequestC2SPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /** 服务端接收处理：回送当前星级区域配置。 */
    public static void handle(ServerPlayer player) {
        SixtySecondsStarMapS2CPacket.send(player);
    }

    /** 便捷注册方法。 */
    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (payload, context) ->
                context.server().execute(() -> handle(context.player())));
    }
}
