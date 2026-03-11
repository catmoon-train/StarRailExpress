package io.wifi.mixins.client;

import com.bawnorton.mixinsquared.TargetHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.doctor4t.ratatouille.client.render.feature.RendersArmInFirstPerson;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

@Mixin(value = PlayerRenderer.class, priority = 2000)
public abstract class RatatouilleArmorFixMixin {

    @Shadow
    protected abstract void setModelProperties(AbstractClientPlayer player);

    // ─── 缓存反射字段 ──────────────────────────────────────────────────────────

    private static volatile Field cachedField = null;
    private static volatile boolean fieldSearchDone = false;

    @SuppressWarnings("unchecked")
    private List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> getFirstPersonArmFeatures() {
        if (!fieldSearchDone) {
            synchronized (RatatouilleArmorFixMixin.class) {
                if (!fieldSearchDone) {
                    Class<?> clazz = this.getClass();
                    outer: while (clazz != null) {
                        for (Field f : clazz.getDeclaredFields()) {
                            // @Unique 字段在字节码中实际名称: ratatouille$firstPersonArmFeatures
                            if (f.getName().contains("firstPersonArmFeatures")) {
                                f.setAccessible(true);
                                cachedField = f;
                                break outer;
                            }
                        }
                        clazz = clazz.getSuperclass();
                    }
                    fieldSearchDone = true;
                }
            }
        }

        if (cachedField != null) {
            try {
                return (List<RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>>) cachedField
                        .get(this);
            } catch (IllegalAccessException ignored) {
            }
        }
        return Collections.emptyList();
    }

    // ─── 完整复现 renderArmorArm 逻辑 ─────────────────────────────────────────

    private void renderArmorArm(PoseStack matrices, MultiBufferSource vertexConsumers, int light,
            AbstractClientPlayer player, boolean isRightArm) {
        for (RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> featureRenderer : getFirstPersonArmFeatures()) {
            if (featureRenderer instanceof RendersArmInFirstPerson<?> rendersArmInFirstPerson) {
                if (!rendersArmInFirstPerson.isFeatureEnabled(player)
                        || rendersArmInFirstPerson.getModel(player) == null)
                    continue;

                @SuppressWarnings("unchecked")
                HumanoidModel<AbstractClientPlayer> model = (HumanoidModel<AbstractClientPlayer>) (rendersArmInFirstPerson
                        .getModel(player));
                matrices.pushPose();
                matrices.scale(0.0F, 0.0F, 0.0F);
                featureRenderer.render(matrices, vertexConsumers, light, player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
                matrices.popPose();

                ((PlayerModel<?>) ((PlayerRenderer) (Object) this).getModel()).setAllVisible(true);
                this.setModelProperties(player);

                model.attackTime = 0.0F;
                model.crouching = false;
                model.swimAmount = 0.0F;
                model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

                ModelPart arm = isRightArm ? model.rightArm : model.leftArm;
                if (arm != null) {
                    arm.xRot = 0.0F;
                    arm.render(
                            matrices,
                            vertexConsumers.getBuffer(
                                    RenderType.entityTranslucent(rendersArmInFirstPerson.getTexture(player))),
                            light,
                            OverlayTexture.NO_OVERLAY);
                }
            }
        }
    }

    // ─── Right Hand ────────────────────────────────────────────────────────────

    @TargetHandler(mixin = "dev.doctor4t.ratatouille.mixin.client.armor.PlayerEntityRendererMixin", name = "hadopelagic$renderArmorRightArm")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false)
    private void fix$renderArmorRightArm(
            PoseStack matrices, MultiBufferSource vertexConsumers, int light,
            AbstractClientPlayer player,
            CallbackInfo ci, // ← 原始 handler 的 CallbackInfo
            CallbackInfo ci2 // ← MixinSquared 注入追加的 CallbackInfo
    ) {
        ci2.cancel(); // ← cancel 第二个（MixinSquared 的那个）
        renderArmorArm(matrices, vertexConsumers, light, player, true);
    }

    // ─── Left Hand ─────────────────────────────────────────────────────────────
    @TargetHandler(mixin = "dev.doctor4t.ratatouille.mixin.client.armor.PlayerEntityRendererMixin", name = "hadopelagic$renderArmorLeftArm")
    @Inject(method = "@MixinSquared:Handler", at = @At("HEAD"), cancellable = true, remap = false)
    private void fix$renderArmorLeftArm(
            PoseStack matrices, MultiBufferSource vertexConsumers, int light,
            AbstractClientPlayer player,
            CallbackInfo ci,
            CallbackInfo ci2) {
        ci2.cancel();
        renderArmorArm(matrices, vertexConsumers, light, player, false);
    }
}