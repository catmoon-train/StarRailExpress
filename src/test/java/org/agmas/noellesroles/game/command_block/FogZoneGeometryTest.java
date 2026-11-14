package org.agmas.noellesroles.game.command_block;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** 不启动游戏，验证雾区边界，不把包围盒角落误判为雾内。 */
class FogZoneGeometryTest {
    @Test
    void cylinderIncludesItsRadialAndVerticalBoundary() {
        assertTrue(FogZoneGeometry.contains(0, 0, 0));
        assertTrue(FogZoneGeometry.contains(15, 5, 0));
        assertTrue(FogZoneGeometry.contains(-15, 0, 0));
        assertTrue(FogZoneGeometry.contains(9, 2, 12));
    }

    @Test
    void outsideAndBoundingBoxCornersDoNotCountAsFog() {
        assertFalse(FogZoneGeometry.contains(15.001, 2, 0));
        assertFalse(FogZoneGeometry.contains(15, 2, 15));
        assertFalse(FogZoneGeometry.contains(0, -0.001, 0));
        assertFalse(FogZoneGeometry.contains(0, 5.001, 0));
    }
}
