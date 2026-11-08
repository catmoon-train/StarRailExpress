package org.agmas.noellesroles.spear;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.client.renderer.BambooSpearVisualPose;
import org.agmas.noellesroles.content.item.spear.SpearAnim;
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

    @Test
    void wholePoleMovesWithoutChangingLengthOrHitPosition() {
        Vec3 base = new Vec3(2.0, 65.0, 3.0);
        Vec3 view = new Vec3(0.0, 0.0, 1.0);
        for (int step = 0; step <= 200; step++) {
            float reach = (step <= 100 ? step : 200 - step) / 10.0F;
            BambooSpearVisualPose pose = BambooSpearVisualPose.at(base, view, reach, 0.0F, true);
            assertEquals(BambooSpearVisualPose.POLE_LENGTH, pose.tail().distanceTo(pose.tip()), 0.000001);
            assertEquals(0.0, pose.tip().distanceTo(base.add(view.scale(reach))), 0.000001);
            assertEquals(0.0, pose.tail().add(pose.direction().scale(BambooSpearVisualPose.POLE_LENGTH))
                    .distanceTo(pose.tip()), 0.000001);
        }
        var start = BambooSpearVisualPose.at(base, view, 0.0F, 0.0F, true);
        var end = BambooSpearVisualPose.at(base, view, 10.0F, 0.0F, true);
        assertEquals(10.0, start.tail().distanceTo(end.tail()), 0.000001);
    }

    @Test
    void mirrorsGripButNotTipForLeftHand() {
        Vec3 view = new Vec3(0.0, 0.0, 1.0);
        var right = BambooSpearVisualPose.at(Vec3.ZERO, view, 1.0F, 0.0F, true);
        var left = BambooSpearVisualPose.at(Vec3.ZERO, view, 1.0F, 0.0F, false);
        assertEquals(right.tip(), left.tip());
        assertEquals(-right.tail().x, left.tail().x, 0.000001);
        assertEquals(right.tail().y, left.tail().y, 0.000001);
        assertEquals(right.tail().z, left.tail().z, 0.000001);
        assertNotEquals(0.0, right.tail().x);
    }

    @Test
    void aimsAtHitPositionEvenWhenLookingStraightUpOrDown() {
        for (float yaw : new float[]{0.0F, 90.0F, 180.0F, -90.0F}) {
            for (float pitch : new float[]{-90.0F, -45.0F, 0.0F, 45.0F, 90.0F}) {
                Vec3 view = Vec3.directionFromRotation(pitch, yaw);
                for (boolean rightHand : new boolean[]{true, false}) {
                    var pose = BambooSpearVisualPose.at(Vec3.ZERO, view, 10.0F, yaw, rightHand);
                    assertEquals(BambooSpearVisualPose.POLE_LENGTH, pose.tail().distanceTo(pose.tip()), 0.000001);
                    assertEquals(0.0, pose.tip().distanceTo(view.normalize().scale(10.0)), 0.000001);
                    assertEquals(1.0, pose.direction().length(), 0.000001);
                }
            }
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
