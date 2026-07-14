package io.wifi.starrailexpress.mixin.client.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家隐身时隐藏身上的盔甲渲染。
 * <p>
 * 原版隐身效果只隐藏玩家本体模型，盔甲依然可见（浮空盔甲）。
 * 此 Mixin 拦截 {@code HumanoidArmorLayer#render}，在实体处于隐身状态时
 * 跳过盔甲渲染，使隐身效果更完整。
 */
@Mixin(HumanoidArmorLayer.class)
public class HideArmorOnInvisible {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void sre$hideArmorWhenInvisible(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight,
            LivingEntity livingEntity, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (livingEntity.hasEffect(MobEffects.INVISIBILITY) || livingEntity.isInvisible()) {
            ci.cancel();
        }
    }
}
