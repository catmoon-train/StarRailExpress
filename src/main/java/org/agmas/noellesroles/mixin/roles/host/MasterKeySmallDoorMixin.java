package org.agmas.noellesroles.mixin.roles.host;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.block.SmallDoorBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SmallDoorBlock.class)
public abstract class MasterKeySmallDoorMixin {

    @WrapOperation(method = "useWithoutItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z", ordinal = 0))
    private boolean conductor(ItemStack instance, Item item, Operation<Boolean> original) {
        return original.call(instance, item) || instance.is(ModItems.MASTER_KEY) || instance.is(FunnyItems.BOWEN_BADGE);
    }

}
