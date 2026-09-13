package pro.fazeclan.river.stupid_express.modifier.twin_children;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwinChildrenHitboxTest {

    private static final float HALF_SCALE = 0.5F;
    private static final float STANDING = TwinChildrenHitbox.VISUAL_STANDING_HEIGHT;
    private static final float CROUCHING = 1.5F;
    private static final float CRAWLING = 0.6F;

    @Test
    void stackedCollisionIsTallerThanOneBlockAfterHalfScale() {
        float standing = STANDING * TwinChildrenHitbox.stackedHeightScale(STANDING) * HALF_SCALE;
        float crouching = CROUCHING * TwinChildrenHitbox.stackedHeightScale(CROUCHING) * HALF_SCALE;
        float crawling = CRAWLING * TwinChildrenHitbox.stackedHeightScale(CRAWLING) * HALF_SCALE;

        assertEquals(TwinChildrenHitbox.STACKED_COLLISION_HEIGHT, standing, 1.0e-4F);
        assertEquals(TwinChildrenHitbox.STACKED_COLLISION_HEIGHT, crouching, 1.0e-4F);
        assertEquals(TwinChildrenHitbox.STACKED_COLLISION_HEIGHT, crawling, 1.0e-4F);
        assertTrue(standing > 1.0F);
        assertTrue(crouching > 1.0F);
        assertTrue(crawling > 1.0F);
        assertTrue(standing < STANDING * HALF_SCALE + 0.81F);
    }

    @Test
    void upperCollisionIsPointSevenAfterHalfScale() {
        float standing = STANDING * TwinChildrenHitbox.upperHeightScale(STANDING) * HALF_SCALE;
        float crouching = CROUCHING * TwinChildrenHitbox.upperHeightScale(CROUCHING) * HALF_SCALE;
        float crawling = CRAWLING * TwinChildrenHitbox.upperHeightScale(CRAWLING) * HALF_SCALE;

        assertEquals(TwinChildrenHitbox.UPPER_COLLISION_HEIGHT, standing, 1.0e-4F);
        assertEquals(TwinChildrenHitbox.UPPER_COLLISION_HEIGHT, crouching, 1.0e-4F);
        assertEquals(TwinChildrenHitbox.UPPER_COLLISION_HEIGHT, crawling, 1.0e-4F);
    }

    @Test
    void halfScaleStandingWithoutStackFitsInOneBlock() {
        assertTrue(STANDING * HALF_SCALE < 1.0F);
    }

    @Test
    void upperTwinFeetSitOnVisualHead() {
        double vehicleAttachY = 0.6 * HALF_SCALE;
        double attachmentY = TwinChildrenHitbox.headPassengerAttachmentY(HALF_SCALE, vehicleAttachY);
        double feetY = attachmentY - vehicleAttachY;

        assertEquals(STANDING * HALF_SCALE, feetY, 1.0e-6);
    }

    @Test
    void invisibleSeatSitsOnHalfScaleHead() {
        assertEquals(STANDING * HALF_SCALE, TwinChildrenHitbox.headSeatY(0.0D, HALF_SCALE), 1.0e-6);
    }
}
