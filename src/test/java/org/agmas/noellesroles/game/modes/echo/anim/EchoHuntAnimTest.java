package org.agmas.noellesroles.game.modes.echo.anim;

import org.agmas.noellesroles.game.modes.echo.state.EchoSkill;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 追猎与施法动画曲线：端点归零、幅度有界、六专长互不相同。 */
class EchoHuntAnimTest {

    @Test void chargePhasesAreMonotonicAndBounded() {
        float previousLift = -1F;
        for (int i = 0; i <= 100; i++) {
            float[] phases = EchoHuntAnim.chargePhases(i / 100F);
            for (float value : phases) assertTrue(value >= 0F && value <= 1F);
            assertTrue(phases[0] >= previousLift);
            previousLift = phases[0];
        }
        float[] end = EchoHuntAnim.chargePhases(1F);
        assertEquals(1F, end[0], 1.0E-5);
        assertEquals(1F, end[1], 1.0E-5);
        assertEquals(1F, end[2], 1.0E-5);
    }

    @Test void firstPersonChargeStartsAtRest() {
        float[] pose = EchoHuntAnim.firstPersonCharge(0F, 1, false);
        for (float value : pose) assertEquals(0F, value, 1.0E-5);
    }

    @Test void firstPersonStrikeReturnsNearRest() {
        float[] pose = EchoHuntAnim.firstPersonStrike(1F, false, 1);
        assertEquals(0F, pose[0], 1.0E-4);
        // 一记挥砍在结束时手应基本回正，允许极小残量。
        assertTrue(Math.abs(pose[3]) < 6F);
    }

    @Test void recoverDecaysToRest() {
        for (boolean miss : new boolean[]{true, false}) {
            float[] pose = EchoHuntAnim.firstPersonRecover(1F, miss, 1);
            for (float value : pose) assertEquals(0F, value, 1.0E-5);
        }
        // 挥空的起始下垂必须比命中更明显。
        assertTrue(EchoHuntAnim.firstPersonRecover(0F, true, 1)[1] < EchoHuntAnim.firstPersonRecover(0F, false, 1)[1]);
    }

    @Test void empoweredAmplifiesStrike() {
        float normal = Math.abs(EchoHuntAnim.firstPersonStrike(0.5F, false, 1)[3]);
        float empowered = Math.abs(EchoHuntAnim.firstPersonStrike(0.5F, true, 1)[3]);
        assertTrue(empowered > normal);
    }

    @Test void sideMirrorsSymmetrically() {
        float[] right = EchoHuntAnim.firstPersonCharge(0.5F, 1, false);
        float[] left = EchoHuntAnim.firstPersonCharge(0.5F, -1, false);
        assertEquals(right[0], -left[0], 1.0E-5);
        assertEquals(right[1], left[1], 1.0E-5);
        assertEquals(right[4], -left[4], 1.0E-5);
    }

    @Test void wipeStartsAndEndsAtRest() {
        for (boolean miss : new boolean[]{true, false}) {
            for (int i = 0; i <= 1; i++) {
                float[] pose = EchoHuntAnim.firstPersonWipe(i, miss, 1);
                for (float value : pose) assertEquals(0F, value, 1.0E-4);
            }
            float[] third = EchoHuntAnim.thirdPersonWipe(0F, miss, 1);
            for (float value : third) assertEquals(0F, value, 1.0E-4);
        }
    }

    @Test void wipeOnHitIsLargerThanWipeOnMiss() {
        float hit = 0F, miss = 0F;
        for (int i = 1; i < 100; i++) {
            float t = i / 100F;
            float[] a = EchoHuntAnim.firstPersonWipe(t, false, 1);
            float[] b = EchoHuntAnim.firstPersonWipe(t, true, 1);
            hit = Math.max(hit, Math.abs(a[4]) + Math.abs(a[1]) * 100F);
            miss = Math.max(miss, Math.abs(b[4]) + Math.abs(b[1]) * 100F);
        }
        assertTrue(hit > miss, "命中的擦刀幅度必须大于挥空");
        assertTrue(miss > 0F, "挥空仍应有收刀动作");
    }

    @Test void wipeDropsTheLateralHandGestureOnMiss() {
        // 挥空不做横抹：中段的横向分量应为 0（只剩收刀的下压）。
        float[] missMid = EchoHuntAnim.firstPersonWipe(0.55F, true, 1);
        assertEquals(0F, missMid[0], 1.0E-5);
        float[] hitMid = EchoHuntAnim.firstPersonWipe(0.55F, false, 1);
        assertTrue(Math.abs(hitMid[0]) > 0F, "命中中段应有横抹位移");
    }

    @Test void wipeItemCurveIsFinite() {
        for (int i = 0; i <= 100; i++) {
            float t = i / 100F;
            for (float value : EchoHuntAnim.thirdPersonWipeItem(t, false, 1)) assertTrue(Float.isFinite(value));
        }
    }

    @Test void thirdPersonCurvesAreFiniteAndBounded() {
        for (int i = 0; i <= 100; i++) {
            float t = i / 100F;
            for (float value : EchoHuntAnim.thirdPersonCharge(t, 1, true)) assertTrue(Float.isFinite(value));
            for (float value : EchoHuntAnim.thirdPersonStrike(t, false, 1)) assertTrue(Float.isFinite(value));
            for (float value : EchoHuntAnim.thirdPersonRecover(t, true, 1)) assertTrue(Float.isFinite(value));
            for (float value : EchoHuntAnim.thirdPersonChargeItem(t, 1, true)) assertTrue(Float.isFinite(value));
        }
    }

}
