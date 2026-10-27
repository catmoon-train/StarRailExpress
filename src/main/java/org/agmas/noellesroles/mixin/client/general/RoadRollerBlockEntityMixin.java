package org.agmas.noellesroles.mixin.client.general;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.agmas.noellesroles.client.RoadRollerClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 告示牌、箱子等不能在方块隐藏后仍悬空显示，也不能破坏其数据。 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class RoadRollerBlockEntityMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void noellesroles$hideCrushedBlockEntity(BlockEntity entity, float partialTick,
            PoseStack poses, MultiBufferSource buffers, CallbackInfo ci) {
        var pos = entity.getBlockPos();
        if (RoadRollerClient.hides(pos.getX(), pos.getY(), pos.getZ())) ci.cancel();
    }
}
