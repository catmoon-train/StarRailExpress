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

package org.agmas.noellesroles.mixin.modifier;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.events.OnShopPurchase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 吝啬效果：在商店购买成功后触发金币返还
 */
@Mixin(SREPlayerShopComponent.class)
public class StingyPurchaseMixin {

    @Inject(method = "tryBuy", at = @At(value = "INVOKE", target = "Lio/wifi/starrailexpress/api/replay/GameReplayManager;recordStoreBuy(Ljava/util/UUID;Lnet/minecraft/resources/ResourceLocation;II)V"))
    private void onPurchaseSuccess(int index, CallbackInfo ci) {
        SREPlayerShopComponent self = (SREPlayerShopComponent) (Object) this;
        Player player = self.getPlayer();

        if (!(player instanceof ServerPlayer sp))
            return;
        if (sp.serverLevel().isClientSide)
            return;

        // 获取商品信息和价格
        try {
            // 获取角色和商店条目
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            if (gameWorld == null)
                return;

            var role = gameWorld.getRole(sp);
            if (role == null || !GameUtils.isPlayerAliveAndSurvival(sp))
                return;

            List<ShopEntry> entries = ShopContent.getShopEntries(role.getIdentifier(), player);

            if (index >= 0 && index < entries.size()) {
                ShopEntry entry = entries.get(index);
                // 调用购买事件
                OnShopPurchase.EVENT.invoker().onPurchase(sp, entry, entry.price());
            }
        } catch (Exception e) {
            // 静默处理错误
        }
    }
}
