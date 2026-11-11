package pro.fazeclan.river.stupid_express.role.arsonist;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.agmas.noellesroles.packet.MechanicalBirdControlC2SPacket.*;
import static org.junit.jupiter.api.Assertions.*;

class OilCanFlightControlTest {
    private static final double EPSILON = 1.0E-9D;

    @Test void forwardUsesTheCurrentYawInEveryCardinalDirection() {
        assertVector(0, 0, 1, OilCanFlightControl.input(0, 0, BIT_FORWARD));
        assertVector(-1, 0, 0, OilCanFlightControl.input(90, 0, BIT_FORWARD));
        assertVector(0, 0, -1, OilCanFlightControl.input(180, 0, BIT_FORWARD));
        assertVector(1, 0, 0, OilCanFlightControl.input(-90, 0, BIT_FORWARD));
        assertVector(0, 0, -1, OilCanFlightControl.input(0, 0, BIT_BACK));
    }

    @Test void leftAndRightMatchTheCameraInsteadOfBeingMirrored() {
        assertVector(1, 0, 0, OilCanFlightControl.input(0, 0, BIT_LEFT));
        assertVector(-1, 0, 0, OilCanFlightControl.input(0, 0, BIT_RIGHT));
        assertVector(0, 0, 1, OilCanFlightControl.input(90, 0, BIT_LEFT));
        assertVector(0, 0, -1, OilCanFlightControl.input(90, 0, BIT_RIGHT));
    }

    @Test void pitchTiltsForwardButKeepsStrafingHorizontal() {
        assertVector(0, Math.sqrt(3) / 2, 0.5, OilCanFlightControl.input(0, -60, BIT_FORWARD));
        assertVector(0, -Math.sqrt(3) / 2, 0.5, OilCanFlightControl.input(0, 60, BIT_FORWARD));
        assertVector(-1, 0, 0, OilCanFlightControl.input(0, -90, BIT_RIGHT));
        assertVector(0, 0, -1, OilCanFlightControl.input(90, 90, BIT_RIGHT));
    }

    @Test void jumpAndSneakUseWorldVerticalRegardlessOfCameraAngles() {
        assertVector(0, 1, 0, OilCanFlightControl.input(137, 85, BIT_JUMP));
        assertVector(0, -1, 0, OilCanFlightControl.input(-43, -85, BIT_SNEAK));
    }

    @Test void OppositeKeysCancelAndUnknownBitsDoNotAddMovement() {
        assertVector(0, 0, 0, OilCanFlightControl.input(137, -85, OilCanFlightControl.INPUT_MASK));
        assertVector(0, 0, 0, OilCanFlightControl.input(0, 0, BIT_FORWARD | BIT_BACK));
        assertVector(0, 0, 0, OilCanFlightControl.input(0, 0, BIT_LEFT | BIT_RIGHT));
        assertVector(0, 0, 0, OilCanFlightControl.input(0, 0, 1 << 20));
    }

    @Test void everyKeyCombinationRespectsTheUnchangedSpeedLimit() {
        assertEquals(0.4D, OilCanFlightControl.MAX_SPEED);
        for (float yaw : new float[] {-179, 0, 90, 359}) {
            for (float pitch : new float[] {-85, -45, 0, 45, 85}) {
                for (int bits = 0; bits <= OilCanFlightControl.INPUT_MASK; bits++) {
                    Vec3 input = OilCanFlightControl.input(yaw, pitch, bits);
                    assertTrue(input.lengthSqr() <= 1.0D + EPSILON);
                    Vec3 motion = new Vec3(1, -1, 1);
                    for (int tick = 0; tick < 8; tick++) {
                        motion = OilCanFlightControl.nextVelocity(motion, input);
                        assertTrue(motion.length() <= OilCanFlightControl.MAX_SPEED + EPSILON);
                    }
                }
            }
        }
    }

    @Test void turningTheCameraRedirectsExistingMomentumWithinThreeTicks() {
        Vec3 motion = new Vec3(0, 0, OilCanFlightControl.MAX_SPEED);
        Vec3 turned = OilCanFlightControl.input(90, 0, BIT_FORWARD);
        for (int tick = 0; tick < 3; tick++) {
            motion = OilCanFlightControl.nextVelocity(motion.scale(0.99D), turned);
        }
        assertTrue(motion.x < -0.35D);
        assertTrue(Math.abs(motion.z) < 0.04D);
    }

    @Test void reverseInputReversesTheOldMomentumOnTheFirstTick() {
        Vec3 motion = new Vec3(0, 0, OilCanFlightControl.MAX_SPEED);
        Vec3 reverse = OilCanFlightControl.input(0, 0, BIT_BACK);
        assertTrue(OilCanFlightControl.nextVelocity(motion.scale(0.99D), reverse).z < 0);
    }

    @Test void releaseBrakesInsteadOfContinuingToDriftAndStillAllowsFalling() {
        Vec3 motion = new Vec3(OilCanFlightControl.MAX_SPEED, 0, 0);
        for (int tick = 0; tick < 8; tick++) {
            Vec3 step = OilCanFlightControl.nextVelocity(motion, Vec3.ZERO);
            // 无输入时实体恢复基类重力；模拟同样的空气阻力与重力顺序。
            motion = step.scale(0.99D).add(0, -0.03D, 0);
        }
        assertTrue(Math.abs(motion.x) < 0.001D);
        assertTrue(motion.y < -0.04D);
    }

    @Test void verticalInputRecoversImmediatelyFromAFall() {
        Vec3 motion = new Vec3(0, -OilCanFlightControl.MAX_SPEED, 0);
        Vec3 up = OilCanFlightControl.input(0, 0, BIT_JUMP);
        assertTrue(OilCanFlightControl.nextVelocity(motion, up).y > 0);
    }

    @Test void invalidAnglesNeverProduceNaNAndWrappedYawKeepsItsHeading() {
        assertVector(0, 0, 0, OilCanFlightControl.input(Float.NaN, 0, BIT_FORWARD));
        assertVector(0, 0, 0, OilCanFlightControl.input(0, Float.POSITIVE_INFINITY, BIT_FORWARD));
        assertVector(-1, 0, 0, OilCanFlightControl.input(450, 0, BIT_FORWARD));
        Vec3 clamped = OilCanFlightControl.input(0, 900, BIT_FORWARD);
        assertEquals(-Math.sin(Math.toRadians(85)), clamped.y, EPSILON);
    }

    private static void assertVector(double x, double y, double z, Vec3 actual) {
        assertEquals(x, actual.x, EPSILON);
        assertEquals(y, actual.y, EPSILON);
        assertEquals(z, actual.z, EPSILON);
    }
}
