package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.PlayerSkin;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.role.bouns.BounsRoles;

/**
 * 大肥鱼的「DeepSeek 娘化」皮肤。
 *
 * <p>贴图放在 {@code assets/noellesroles/textures/entity/player/deepseek.png}（64x64）。
 * 走 {@link OnGettingPlayerSkin} 的好处是**连模型一起指定**：这张贴图是细手绘制的，
 * 所以这里固定用 {@link PlayerSkin.Model#SLIM}（而 {@code SRERole#getNormalSkin}
 * 只换贴图，模型仍跟着玩家自己账号的宽/细手走）。
 */
@Environment(EnvType.CLIENT)
public final class FatFishSkinHandler {

    private static final PlayerSkin FAT_FISH_SKIN = new PlayerSkin(
            Noellesroles.id("textures/entity/player/deepseek.png"),
            null, null, null, PlayerSkin.Model.SLIM, false);

    private FatFishSkinHandler() {
    }

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            if (player == null || player.level() == null) {
                return PlayerSkinResult.SKIP;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (game == null || !game.isRole(player, BounsRoles.FAT_FISH)) {
                return PlayerSkinResult.SKIP;
            }
            return PlayerSkinResult.playerSkin(FAT_FISH_SKIN);
        });
    }
}
