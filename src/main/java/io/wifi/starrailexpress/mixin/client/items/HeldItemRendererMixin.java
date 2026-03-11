package io.wifi.starrailexpress.mixin.client.items;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import io.wifi.starrailexpress.item.RevolverItem;
import io.wifi.starrailexpress.util.MatrixParticleManager;
import io.wifi.starrailexpress.util.MatrixUtils;
import io.wifi.starrailexpress.SRE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V", shift = At.Shift.AFTER))
    private void tmm$itemVFX(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, boolean leftHanded,
            PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (SRE.isLobby)
            return;
        if (renderMode.firstPerson()) {
            SREClient.handParticleManager.render(matrices, vertexConsumers, light);
        }

        if (entity instanceof Player playerEntity
                && (stack.is(TMMItemTags.GUNS) || (this.mainHandItem.getItem() instanceof RevolverItem))) {
            if (playerEntity.getUUID() != Minecraft.getInstance().player.getUUID()) {
                MatrixParticleManager.setMuzzlePosForPlayer(playerEntity, MatrixUtils.matrixToVec(matrices));
            } else if (!renderMode.firstPerson()) {
                MatrixParticleManager.setMuzzlePosForPlayer(playerEntity, MatrixUtils.matrixToVec(matrices));
            }
        }
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean tmm$ignoreNbtUpdateForRevolver(boolean original, @Local(ordinal = 0) ItemStack newItemStack) {
        if (SRE.isLobby)
            return original;
        if (!original) {
            if (this.mainHandItem.is(TMMItemTags.GUNS) && newItemStack.is(TMMItemTags.GUNS)
                    || (this.mainHandItem.getItem() instanceof RevolverItem)) {
                return true;
            }
        }
        return original;
    }
}