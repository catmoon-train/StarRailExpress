package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class SkinGetterMixin {
    // @Inject(method = "handlePlayerInfoUpdate", at = @At(value = "INVOKE", target
    // =
    // "Ljava/util/Map;putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
    // ordinal = 0, // 指定第一个
    // // putIfAbsent
    // // 调用（位于新玩家处理循环中）
    // shift = At.Shift.BEFORE))
    // private void beforePutIfAbsent(ClientboundPlayerInfoUpdatePacket packet,
    // CallbackInfo ci,
    // @Local(ordinal = 0) ClientboundPlayerInfoUpdatePacket.Entry entry,
    // @Local(ordinal = 1) PlayerInfo playerInfo) {
    // TMMClient.PLAYER_ENTRIES_CACHE.putIfAbsent(playerInfo.getProfile().getId(),
    // playerInfo);
    // // 在这里你就可以使用 entry 和 playerInfo 了
    // System.out.println("新玩家信息：" + playerInfo.getProfile().getName());
    // // 你可以添加自己的逻辑，例如记录日志、修改数据等
    // }
    @Inject(method = "handlePlayerInfoUpdate(Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket;)V", // 使用完整描述符
            at = @At(value = "INVOKE", target = "Ljava/util/Map;putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0, shift = At.Shift.BEFORE))
    private void beforePutIfAbsent(ClientboundPlayerInfoUpdatePacket packet,
            CallbackInfo ci, @Local LocalRef<ClientboundPlayerInfoUpdatePacket.Entry> entry,@Local LocalRef<PlayerInfo> playerInfo) { // 先不加 @Local 参数测试
                ClientboundPlayerInfoUpdatePacket.Entry entry2 = entry.get();
                PlayerInfo playerInfo2 = playerInfo.get();
                SREClient.PLAYER_ENTRIES_CACHE.putIfAbsent(entry2.profileId(), playerInfo2);
        // 空实现，只测试注入点是否能找到
    }
}
