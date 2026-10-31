package org.agmas.noellesroles.spear;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import org.agmas.noellesroles.mixin.client.roles.nature_spirit.HumanoidModelBambooSpearMixin;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BambooSpearAnimationTest {
    @Test
    void inheritedAttackTimeIsNotShadowedOnHumanoidModel() throws NoSuchFieldException {
        assertEquals(EntityModel.class, HumanoidModel.class.getField("attackTime").getDeclaringClass());
        assertThrows(NoSuchFieldException.class,
                () -> HumanoidModelBambooSpearMixin.class.getDeclaredField("attackTime"));
    }

    @Test
    void extendsAndRecoversThirtyPercentLonger() {
        assertEquals(30 * 1.3, BambooSpearEntity.EXTEND_TICKS, 0.00001);
        assertEquals(10 * 1.3, BambooSpearEntity.RETRACT_TICKS, 0.00001);
        assertEquals(52, BambooSpearEntity.EXTEND_TICKS + BambooSpearEntity.RETRACT_TICKS);
        assertEquals(10.0F, BambooSpearEntity.MAX_LENGTH);
    }

    @Test
    void mapsSyncedLengthToSpearThrustAndRecovery() {
        assertEquals(0.0F, SpearAnim.bambooStabProgress(0.0F, false));
        assertEquals(0.1F, SpearAnim.bambooStabProgress(0.5F, false));
        assertEquals(0.2F, SpearAnim.bambooStabProgress(1.0F, false));
        assertEquals(0.4F, SpearAnim.bambooStabProgress(1.0F, true));
        assertEquals(0.7F, SpearAnim.bambooStabProgress(0.5F, true), 0.00001F);
        assertEquals(1.0F, SpearAnim.bambooStabProgress(0.0F, true));
    }

    @Test
    void fullExtensionHasNoPoseJumpWhenRecoveryBegins() {
        float thrust = SpearAnim.bambooStabProgress(1.0F, false);
        float recovery = SpearAnim.bambooStabProgress(1.0F, true);
        assertEquals(SpearAnim.thirdPersonArmPitch(thrust), SpearAnim.thirdPersonArmPitch(recovery));
        assertArrayEquals(firstPersonPose(thrust), firstPersonPose(recovery), 0.00001F);
        assertArrayEquals(thirdPersonPose(thrust), thirdPersonPose(recovery), 0.00001F);
    }

    @Test
    void bothViewsReturnToRestAfterRetraction() {
        float end = SpearAnim.bambooStabProgress(0.0F, true);
        float[] rest = new PoseStack().last().pose().get(new float[16]);
        assertEquals(0.0F, SpearAnim.thirdPersonArmPitch(end), 0.00001F);
        assertArrayEquals(rest, firstPersonPose(end), 0.00001F);
        assertArrayEquals(rest, thirdPersonPose(end), 0.00001F);
        assertNotEquals(SpearAnim.thirdPersonArmPitch(0.0F), SpearAnim.thirdPersonArmPitch(0.2F));
    }

    @Test
    void clampsLengthOvershootAndSupportsEarlyRetraction() {
        assertEquals(0.0F, SpearAnim.bambooStabProgress(-0.01F, false));
        assertEquals(0.2F, SpearAnim.bambooStabProgress(1.01F, false));
        assertEquals(1.0F, SpearAnim.bambooStabProgress(-0.01F, true));
        assertEquals(0.4F, SpearAnim.bambooStabProgress(1.01F, true));
        float previous = 0.0F;
        for (int i = 0; i <= 100; i++) {
            float progress = SpearAnim.bambooStabProgress(i / 100.0F, false);
            assertTrue(progress >= previous);
            previous = progress;
        }
        previous = 0.4F;
        for (int i = 35; i >= 0; i--) {
            float progress = SpearAnim.bambooStabProgress(i / 100.0F, true);
            assertTrue(progress >= previous && progress <= 1.0F);
            assertTrue(Float.isFinite(SpearAnim.thirdPersonArmPitch(progress)));
            previous = progress;
        }
    }

    private static float[] firstPersonPose(float progress) {
        PoseStack pose = new PoseStack();
        SpearAnim.applyFirstPersonStab(pose, progress);
        return pose.last().pose().get(new float[16]);
    }

    private static float[] thirdPersonPose(float progress) {
        PoseStack pose = new PoseStack();
        SpearAnim.applyThirdPersonStab(pose, progress, 0.38F);
        return pose.last().pose().get(new float[16]);
    }
}
