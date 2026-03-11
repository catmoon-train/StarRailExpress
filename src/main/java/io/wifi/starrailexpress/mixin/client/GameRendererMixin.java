package io.wifi.starrailexpress.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.wifi.starrailexpress.client.gui.ScopeOverlayRenderer;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow
    @Final
    Minecraft minecraft;

    @WrapOperation(method = "shouldRenderBlockOutline", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayBuild:Z"))
    public boolean useOnBlock(Abilities instance, Operation<Boolean> original) {
        if (this.minecraft.getCameraEntity() instanceof LivingEntity entity && entity.getMainHandItem().getItem() instanceof AdventureUsable)
            return true;
        return original.call(instance);
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    public void modifyFov(net.minecraft.client.Camera camera, float partialTick, boolean bobbing, CallbackInfoReturnable<Double> cir) {
        if (ScopeOverlayRenderer.isInScopeView()) {
            double original = cir.getReturnValue();
            cir.setReturnValue(original / 3d); // 开镜时将FOV缩小到原来的1/3，实现拉近视角效果
        }
    }
}