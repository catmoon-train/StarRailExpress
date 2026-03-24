package io.wifi.starrailexpress.mixin.item;

import io.wifi.starrailexpress.cca.SREPlayerProgressionComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class UseItemProgressionMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void starrailexpress$trackUseItem(Level level, net.minecraft.world.entity.player.Player player,
            InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        SREPlayerProgressionComponent.KEY.get(serverPlayer).onItemUsed(itemId);
    }
}
