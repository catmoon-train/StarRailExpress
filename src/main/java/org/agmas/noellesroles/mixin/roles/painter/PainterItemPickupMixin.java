package org.agmas.noellesroles.mixin.roles.painter;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 画家物品捡起检测Mixin
 * 监听玩家捡起物品的事件，触发画家的绘画灵感技能
 */
@Mixin(ItemEntity.class)
public abstract class PainterItemPickupMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void onPainterItemPickup(Player player, CallbackInfo ci) {
        try {
            // 检查是否为画家
            SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY.get(player.level());
            if (!gameWorldComponent.isRole(player, ModRoles.PAINTER)) {
                return;
            }

            ItemEntity itemEntity = (ItemEntity) (Object) this;
            ItemStack itemStack = itemEntity.getItem();

            // 获取物品的 ResourceLocation
            String itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();

            // 获取画家组件并触发绘画灵感
            var painterComponent = ModComponents.PAINTER.maybeGet(player).orElse(null);
            if (painterComponent != null) {
                painterComponent.onItemPickup(itemId);
            }
        } catch (Exception ignored) {
            // 静默处理异常
        }
    }
}
