package org.agmas.noellesroles.mixin.halloween;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.item.KnifeItem;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.game.halloween.HalloweenItems;
import org.agmas.noellesroles.game.halloween.HalloweenRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 按实际刀商品而非阵营标签扩展商店，兼容特殊职业自定义商店。 */
@Mixin(ShopContent.class)
public abstract class HalloweenShopMixin {
    @Inject(method = "getShopEntries(Lio/wifi/starrailexpress/api/SRERole;Lnet/minecraft/world/entity/player/Player;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void halloween$saw(SRERole role, Player player, CallbackInfoReturnable<List<ShopEntry>> cir) {
        if (player == null || !HalloweenRuntime.isEnabled(player.level())) return;
        List<ShopEntry> entries = cir.getReturnValue();
        if (entries.stream().noneMatch(e -> e.stack().getItem() instanceof KnifeItem)
                || entries.stream().anyMatch(e -> e.stack().is(HalloweenItems.BLOOD_SAW))) return;
        List<ShopEntry> result = new ArrayList<>(entries);
        result.add(new ShopEntry(HalloweenItems.BLOOD_SAW.getDefaultInstance(), 200, ShopEntry.Type.WEAPON) {
            @Override public boolean canBuy(Player buyer) {
                var actualRole = SREGameWorldComponent.KEY.get(buyer.level()).getRole(buyer);
                return HalloweenRuntime.isEnabled(buyer.level()) && actualRole != null
                        && ShopContent.getShopEntries(actualRole, buyer).stream()
                        .anyMatch(e -> e.stack().getItem() instanceof KnifeItem)
                        && super.canBuy(buyer);
            }
        });
        cir.setReturnValue(result);
    }
}
