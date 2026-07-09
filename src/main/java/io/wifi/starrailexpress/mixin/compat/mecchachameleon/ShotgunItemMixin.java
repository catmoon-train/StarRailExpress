package io.wifi.starrailexpress.mixin.compat.mecchachameleon;

import com.mecchachameleon.game.Room;
import com.mecchachameleon.item.ShotgunItem;
import io.wifi.starrailexpress.compat.chameleon.ChameleonCompat;
import io.wifi.starrailexpress.game.modes.funny.SREChameleonGameMode;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * 霰弹枪靠 {@code Room.isHider} 挑选目标。SRE 托管的房间刻意不填 {@code hiders}
 * （否则猎人的近战会被变色龙改判成 tag 击杀，顶掉 SRE 自己的刀和枪），
 * 所以只在开枪这一次调用上，把目标判定重定向到 SRE 的职业。
 */
@Mixin(value = ShotgunItem.class, remap = false)
public abstract class ShotgunItemMixin {

    @Redirect(method = "fire", at = @At(value = "INVOKE", target = "Lcom/mecchachameleon/game/Room;isHider(Ljava/util/UUID;)Z"))
    private static boolean sre$shotgunTargetsChameleons(Room room, UUID candidate, ServerPlayer shooter) {
        if (!ChameleonCompat.isRoomActive()) {
            return room.isHider(candidate);
        }
        MinecraftServer server = shooter.getServer();
        if (server == null) {
            return false;
        }
        return SREChameleonGameMode.isHiderTarget(server.getPlayerList().getPlayer(candidate));
    }
}
