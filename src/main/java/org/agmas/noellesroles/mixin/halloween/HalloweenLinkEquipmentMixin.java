package org.agmas.noellesroles.mixin.halloween;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.halloween.HalloweenLinkItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 手中物品和装备替换走相同的借用槽位规则。 */
@Mixin(Player.class)
public abstract class HalloweenLinkEquipmentMixin {
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void halloween$linkEquipment(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && HalloweenLinkItems.replaceEquipment(player, slot, stack))
            ci.cancel();
    }
}
