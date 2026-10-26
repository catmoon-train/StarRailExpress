package io.wifi.starrailexpress.mixin.client.settlement;

import io.wifi.starrailexpress.settlement.client.SettlementClient;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 黑色舞台无方块实体；禁止远处现场方块实体通过透视效果进入镜头。 */
@Mixin(value = BlockEntityRenderDispatcher.class, priority = 2500)
public abstract class SettlementBlockEntityMixin {
    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void settlement$blockEntities(CallbackInfo ci) { if (SettlementClient.isActive()) ci.cancel(); }
}
