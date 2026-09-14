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
import io.wifi.starrailexpress.SREClientConfig;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;

import org.agmas.noellesroles.init.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * 皮肤覆盖（伪装 / 换肤 / 火眼金睛）。
 * <p>
 * {@code getSkin()} 每帧会被问好几次（渲染、标签页、皮肤层……），而解析要遍历
 * {@link OnGettingPlayerSkin} 的全部监听器，所以这里保留缓存；但把节流从**读墙钟**
 * （原来 100ms，超性能模式 200ms）换成**按 tick 打戳**，顺带把窗口砍半：
 * 普通模式每 tick 一次、超性能模式每 2 tick 一次。于是换肤 / 伪装生效最迟 1 个 tick（50ms），
 * 而不是原来的 100~200ms，同时不再每帧读一次系统时间。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerSkinMixin {

    @Unique
    private int lastResolveTick = -1;
    @Unique
    private PlayerSkin cacheResult = null;
    /** 普通模式每 tick 解析一次。 */
    private static final int CACHE_TICK_GAP = 1;
    /** 超性能模式每 2 tick 一次：节流思路保留，但只有原来 200ms 的一半。 */
    private static final int CACHE_TICK_GAP_ULTRA = 2;

    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin applySkinSwap(PlayerSkin originalSkin) {
        if (SRE.isLobby)
            return originalSkin;
        if (SREClient.isInLobby)
            return originalSkin;
        final AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        final Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null)
            return originalSkin;
        final int tick = self.tickCount;
        final int gap = SREClientConfig.instance().ultraPerfMode ? CACHE_TICK_GAP_ULTRA : CACHE_TICK_GAP;
        // delta < 0 是 tickCount 被重置（重生 / 换维度）时的兜底，避免缓存一直不刷新。
        final int delta = tick - lastResolveTick;
        if (cacheResult == null || delta >= gap || delta < 0) {
            cacheResult = getResult(client, self, originalSkin);
            lastResolveTick = tick;
        }
        if (cacheResult != null) {
            return cacheResult;
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

    private PlayerSkin getResult(Minecraft client, AbstractClientPlayer self, PlayerSkin originalSkin) {
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
    }
}
