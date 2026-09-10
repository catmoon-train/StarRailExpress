package org.agmas.noellesroles.game.modifier.fatskinny;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FatSkinnyPushLogicTest {

    @Test
    void fatScaleIsWiderAndSkinnyIsNarrower() {
        assertTrue(FatSkinnyPushLogic.FAT_HORIZONTAL_SCALE > 1.0F);
        assertTrue(FatSkinnyPushLogic.SKINNY_HORIZONTAL_SCALE < 1.0F);
        assertEquals(1.0F, FatSkinnyPushLogic.horizontalScale(false, false));
        assertEquals(FatSkinnyPushLogic.FAT_HORIZONTAL_SCALE, FatSkinnyPushLogic.horizontalScale(true, false));
        assertEquals(FatSkinnyPushLogic.SKINNY_HORIZONTAL_SCALE, FatSkinnyPushLogic.horizontalScale(false, true));
    }

    @Test
    void fatDoesNotPushWhenTargetIsOutsideRadius() {
        FatSkinnyPushLogic.Vec2 push = FatSkinnyPushLogic.fatPush(0, 0, 8, 0, false, false);
        assertTrue(push.isZero());
    }

    @Test
    void fatPushesTargetAwayAlongX() {
        FatSkinnyPushLogic.Vec2 push = FatSkinnyPushLogic.fatPush(0, 0, 0.4, 0, false, false);
        assertTrue(push.x() > 0.0D);
        assertEquals(0.0D, push.z(), 1.0e-9);
    }

    @Test
    void fatPushesSkinnyHarderThanNormal() {
        FatSkinnyPushLogic.Vec2 normal = FatSkinnyPushLogic.fatPush(0, 0, 0.4, 0, false, false);
        FatSkinnyPushLogic.Vec2 skinny = FatSkinnyPushLogic.fatPush(0, 0, 0.4, 0, true, false);
        assertTrue(skinny.x() > normal.x());
        assertEquals(normal.x() * FatSkinnyPushLogic.SKINNY_FROM_FAT_MULTIPLIER, skinny.x(), 1.0e-9);
    }

    @Test
    void twoFatsPushEachOtherLessHard() {
        FatSkinnyPushLogic.Vec2 normal = FatSkinnyPushLogic.fatPush(0, 0, 0.4, 0, false, false);
        FatSkinnyPushLogic.Vec2 fat = FatSkinnyPushLogic.fatPush(0, 0, 0.4, 0, false, true);
        assertTrue(fat.x() < normal.x());
        assertEquals(normal.x() * FatSkinnyPushLogic.FAT_VS_FAT_MULTIPLIER, fat.x(), 1.0e-9);
    }

    @Test
    void overlappingFatStillSeparatesAlongFallbackAxis() {
        FatSkinnyPushLogic.Vec2 push = FatSkinnyPushLogic.fatPush(1, 2, 1, 2, false, false);
        assertTrue(push.x() > 0.0D);
        assertEquals(0.0D, push.z(), 1.0e-9);
    }

    @Test
    void skinnyIsSqueezedAwayFromNeighbor() {
        FatSkinnyPushLogic.Vec2 squeeze = FatSkinnyPushLogic.skinnySqueeze(0.3, 0, 0, 0);
        assertTrue(squeeze.x() > 0.0D);
        assertEquals(0.0D, squeeze.z(), 1.0e-9);
    }

    @Test
    void skinnyIsNotSqueezedWhenNeighborIsFar() {
        FatSkinnyPushLogic.Vec2 squeeze = FatSkinnyPushLogic.skinnySqueeze(6, 0, 0, 0);
        assertTrue(squeeze.isZero());
    }

    @Test
    void closerTargetsArePushedHarder() {
        FatSkinnyPushLogic.Vec2 near = FatSkinnyPushLogic.fatPush(0, 0, 0.3, 0, false, false);
        FatSkinnyPushLogic.Vec2 far = FatSkinnyPushLogic.fatPush(0, 0, 1.0, 0, false, false);
        assertTrue(near.x() > far.x());
        assertTrue(far.x() > 0.0D);
    }
}
