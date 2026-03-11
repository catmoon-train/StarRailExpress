package org.agmas.noellesroles.mixin.client.roles.conductor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemInHandLayer.class)
public class MasterKeyInvisibilityMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack view(LivingEntity instance, Operation<ItemStack> original) {

        ItemStack ret = original.call(instance);
        // if (ret.is(ModItems.MASTER_KEY) && !(ConfigWorldComponent.KEY.get(instance.level()).masterKeyIsVisible || !ConfigWorldComponent.KEY.get(instance.level()).masterKeyIsVisible)) {
        //     ret = TMMItems.LOCKPICK.getDefaultInstance();
        // }

        return ret;
    }
}
