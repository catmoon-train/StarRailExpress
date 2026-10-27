package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.PlayerSkin;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.ModRoles;

/**
 * him 的细手皮肤。
 *
 * <p>贴图放在 {@code assets/noellesroles/textures/entity/player/him.png}（64x64）。
 * 走 {@link OnGettingPlayerSkin} 是为了连模型一起指定：这张贴图按细手绘制，
 * 固定用 {@link PlayerSkin.Model#SLIM}。
 */
@Environment(EnvType.CLIENT)
public final class HimSkinHandler {

    private static final PlayerSkin HIM_SKIN = new PlayerSkin(
            Noellesroles.id("textures/entity/player/him.png"),
            null, null, null, PlayerSkin.Model.SLIM, false);

    private HimSkinHandler() {
    }

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            if (player == null || player.level() == null) {
                return PlayerSkinResult.SKIP;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (game == null || !game.isRole(player, ModRoles.HIM)) {
                return PlayerSkinResult.SKIP;
            }
            return PlayerSkinResult.playerSkin(HIM_SKIN);
        });
    }
}
