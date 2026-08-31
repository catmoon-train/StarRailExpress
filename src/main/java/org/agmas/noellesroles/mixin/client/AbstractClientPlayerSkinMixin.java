/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.mixin.client;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {

    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin applySkinSwap(PlayerSkin originalSkin) {
        if (SRE.isLobby)
            return originalSkin;
        if (SREClient.isInLobby)
            return originalSkin;
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null)
            return originalSkin;
        /** 火眼金睛效果：穿透皮肤伪装 */
        if (client.player.hasEffect(ModEffects.TRUE_SKIN_OBSERVER)) {
            return originalSkin;
        }
        PlayerSkinResult result = OnGettingPlayerSkin.EVENT.invoker().onGetSkin(self, originalSkin);
        if (result == null || result.type == 0 || result.type == -1) {
            return originalSkin;
        }
        if (result.type >= 1 && result.playerSkin != null) {
            return result.playerSkin;
        }
        return originalSkin;
        /**
         * 此处为了某些兼容性所以删了 (result.type == 1 时)。但是材质还是会变，在 PlayerEntityRendererMixin 中。
         */
        // PlayerSkin.Model model = result.isSlim ? PlayerSkin.Model.SLIM :
        // PlayerSkin.Model.WIDE;
        // PlayerSkin ret = new PlayerSkin(result.texture, null, null, null, model,
        // true);
        // cir.setReturnValue(ret);
    }
}
