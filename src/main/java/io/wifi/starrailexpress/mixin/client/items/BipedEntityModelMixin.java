package io.wifi.starrailexpress.mixin.client.items;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class BipedEntityModelMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart head;

    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void tmm$holdRevolverRightArm(T entity, CallbackInfo ci) {
        if (isHoldingGun(entity) && entity.getMainArm() == HumanoidArm.RIGHT) {
            holdGun(this.rightArm, this.leftArm, this.head, true);
        }
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void tmm$tmm$holdRevolverLeftArm(T entity, CallbackInfo ci) {
        if (isHoldingGun(entity) && entity.getMainArm() != HumanoidArm.RIGHT) {
            holdGun(this.rightArm, this.leftArm, this.head, false);
        }
    }

    @Unique
    private boolean isHoldingGun(T entity) {
        ItemStack psychosisItemStack = SREPlayerMoodComponent.KEY.get(Minecraft.getInstance().player).getPsychosisItems().get(entity.getUUID());
        if (psychosisItemStack != null) {
            return psychosisItemStack.is(TMMItemTags.GUNS);
        } else return entity.getMainHandItem().is(TMMItemTags.GUNS);
    }

    @Unique
    private static void holdGun(ModelPart holdingArm, ModelPart otherArm, ModelPart head, boolean rightArmed) {
        ModelPart modelPart = rightArmed ? holdingArm : otherArm;
        modelPart.yRot = (rightArmed ? -0.3F : 0.3F) + head.yRot;
        modelPart.xRot = (float) (-Math.PI / 2) + head.xRot + 0.1F;
    }
}
