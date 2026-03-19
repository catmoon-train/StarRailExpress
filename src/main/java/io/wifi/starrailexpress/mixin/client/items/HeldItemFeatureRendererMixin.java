package io.wifi.starrailexpress.mixin.client.items;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.AllowItemShowInHand;
import io.wifi.starrailexpress.index.TMMItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemInHandLayer.class)
public class HeldItemFeatureRendererMixin {
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    public ItemStack nrs$changeOffHandItemStack(LivingEntity instance, Operation<ItemStack> original) {
        ItemStack ret = original.call(instance);
        if (instance.isInvisible())
            return ItemStack.EMPTY;
        for (var i : TMMItems.INVISIBLE_ITEMS) {
            if (ret.is(i)) {
                return ItemStack.EMPTY;
            }
        }
        
        if (instance instanceof Player player) {
            var eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, ret, false);
            if (eventRes != null) {
                return eventRes;
            }
        }
        return ret;
    }
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    public ItemStack nrs$changeMainHandItemStack(LivingEntity instance, Operation<ItemStack> original) {
        ItemStack ret = original.call(instance);
        if (instance.isInvisible())
            return ItemStack.EMPTY;
        for (var i : TMMItems.INVISIBLE_ITEMS) {
            if (ret.is(i)) {
                return ItemStack.EMPTY;
            }
        }
        
        if (instance instanceof Player player) {
            var eventRes = AllowItemShowInHand.EVENT.invoker().allowShowInHand(player, ret, true);
            if (eventRes != null) {
                return eventRes;
            }
        }

        if (SREClient.moodComponent != null && SREClient.moodComponent.isLowerThanMid()) {
            HashMap<UUID, ItemStack> psychosisItems = SREClient.moodComponent.getPsychosisItems();
            UUID uuid = instance.getUUID();
            if (psychosisItems.containsKey(uuid)) {
                ret = psychosisItems.get(uuid);
            }
        }

        return ret;
    }
}
