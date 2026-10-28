package io.wifi.starrailexpress.pet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 随从运动数学的单元测试。
 * <p>
 * {@link PetMotion} 刻意不引用任何 Minecraft 类型，就是为了能在这里直接验证
 * 「5 格钳制」「地面吸附」「平滑收敛」「角度环绕」这四条最容易出错的规则。
 */
class PetMotionTest {

    /** 距离比较的容差：位置是 double 计算，允许极小的浮点误差。 */
    private static final double EPS = 1.0E-6D;

    // ─── 5 格硬钳制 ────────────────────────────────────────────────────────

    @Test
    void clampLeavesNearbyPositionUntouched() {
        double[] pos = {1.0D, 2.0D, 3.0D};
        boolean clamped = PetMotion.clampToOwner(pos, 0.0D, 0.0D, 0.0D);
        assertFalse(clamped, "3 格内不应触发钳制");
        assertEquals(1.0D, pos[0], EPS);
        assertEquals(2.0D, pos[1], EPS);
        assertEquals(3.0D, pos[2], EPS);
    }

    @Test
    void clampPullsFarPositionBackToMaxDistance() {
        // 主人瞬移到原点，随从留在 100 格外
        double[] pos = {100.0D, 0.0D, 0.0D};
        boolean clamped = PetMotion.clampToOwner(pos, 0.0D, 0.0D, 0.0D);
        assertTrue(clamped, "超出 5 格必须被钳制");

        double distance = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        assertEquals(PetMotionProfile.MAX_DISTANCE, distance, 1.0E-4D,
                "钳制后应恰好落在 5 格边界上");
        assertEquals(0.0D, pos[1], EPS, "方向必须保持（y 分量原来是 0）");
    }

    @Test
    void clampPreservesDirection() {
        double[] pos = {3.0D, 4.0D, 0.0D}; // 距离 5，方向 (0.6, 0.8, 0)
        PetMotion.clampToOwner(pos, 10.0D, 4.0D, 0.0D);
        // 相对主人的偏移为 (-7, 0, 0)，方向变成纯 -X
        assertEquals(10.0D - PetMotionProfile.MAX_DISTANCE, pos[0], 1.0E-4D);
        assertEquals(4.0D, pos[1], 1.0E-4D);
        assertEquals(0.0D, pos[2], EPS);
    }

    // ─── 指数平滑 ──────────────────────────────────────────────────────────

    @Test
    void approachConvergesWithoutOvershoot() {
        double current = 0.0D;
        double target = 10.0D;
        for (int i = 0; i < 200; i++) {
            current = PetMotion.approach(current, target, 10.0D, 0.05D);
            assertTrue(current <= target + EPS, "指数平滑不允许过冲");
        }
        assertEquals(target, current, 1.0E-3D, "足够多步之后必须收敛到目标");
    }

    @Test
    void approachWithZeroDtKeepsCurrent() {
        assertEquals(3.0D, PetMotion.approach(3.0D, 100.0D, 10.0D, 0.0D), EPS,
                "dt=0 时不应移动（防止暂停/掉帧时抖动）");
        assertEquals(3.0D, PetMotion.approach(3.0D, 100.0D, 10.0D, -1.0D), EPS,
                "负数 dt 同样不移动");
    }

    @Test
    void approachIsFrameRateIndependent() {
        // 一大步 vs 两小步，结果应几乎一致（这是「与帧率无关」的定义）
        double oneStep = PetMotion.approach(0.0D, 1.0D, 6.0D, 0.1D);
        double twoSteps = PetMotion.approach(PetMotion.approach(0.0D, 1.0D, 6.0D, 0.05D), 1.0D, 6.0D, 0.05D);
        assertEquals(oneStep, twoSteps, 1.0E-9D);
    }

    // ─── 角度 ──────────────────────────────────────────────────────────────

    @Test
    void wrapDegreesFoldsIntoRange() {
        assertEquals(0.0F, PetMotion.wrapDegrees(360.0F), 1.0E-4F);
        assertEquals(90.0F, PetMotion.wrapDegrees(450.0F), 1.0E-4F);
        assertEquals(-90.0F, PetMotion.wrapDegrees(-450.0F), 1.0E-4F);
        assertEquals(-180.0F, PetMotion.wrapDegrees(180.0F), 1.0E-4F,
                "180° 折成 -180°，与 Mth.wrapDegrees 一致");
    }

    @Test
    void lerpAngleTakesShortestArc() {
        // 170° → -170°：最短弧是跨过 ±180（走 +20°），不是反向绕 -340°
        float step = PetMotion.lerpAngle(170.0F, -170.0F, 0.5F);
        // 走 +20° 的一半 => 180°，折回后是 -180
        assertEquals(-180.0F, PetMotion.wrapDegrees(step), 1.0E-3F,
                "应当朝 +方向跨过 ±180，而不是反向绕一大圈");
        // 若错误地走长弧（-340°），一半会得到 0°
        assertFalse(Math.abs(PetMotion.wrapDegrees(step)) < 1.0F,
                "不应走长弧（长弧的中点会落在 0° 附近）");
    }

    @Test
    void yawFromDeltaMatchesMinecraftConvention() {
        // 0 朝 +Z（南）
        assertEquals(0.0F, PetMotion.yawFromDelta(0.0D, 1.0D), 1.0E-4F);
        // 90 朝 -X（西）
        assertEquals(90.0F, PetMotion.yawFromDelta(-1.0D, 0.0D), 1.0E-4F);
        // -90 朝 +X（东）
        assertEquals(-90.0F, PetMotion.yawFromDelta(1.0D, 0.0D), 1.0E-4F);
    }

    // ─── 地面吸附 ──────────────────────────────────────────────────────────

    @Test
    void snapToGroundFindsSurfaceBelow() {
        // 只在 y=64 有一层地面
        PetMotion.GroundSampler sampler = (x, y, z) -> Math.abs(y - 64.0D) < 1.0E-6D ? 64.0D : null;
        double result = PetMotion.snapToGround(sampler, 0.0D, 66.0D, 0.0D, 66.0D);
        assertEquals(64.0D, result, EPS);
    }

    @Test
    void snapToGroundFallsBackWhenNothingFound() {
        PetMotion.GroundSampler sampler = (x, y, z) -> null;
        double result = PetMotion.snapToGround(sampler, 0.0D, 100.0D, 0.0D, 99.5D);
        assertEquals(99.5D, result, EPS, "找不到地面时用回退值，不能无限向下找");
    }

    @Test
    void snapToGroundRespectsScanDepth() {
        // 地面放在扫描深度之外（100 格下方），应该扫不到
        PetMotion.GroundSampler sampler = (x, y, z) -> Math.abs(y - 0.0D) < 1.0E-6D ? 0.0D : null;
        double result = PetMotion.snapToGround(sampler, 0.0D, 100.0D, 0.0D, 77.0D);
        assertEquals(77.0D, result, EPS,
                "超过 " + PetMotion.GROUND_SCAN_DEPTH + " 格就放弃，避免卡在悬崖边");
    }

    @Test
    void snapToGroundWithNullSamplerReturnsFallback() {
        assertEquals(42.0D, PetMotion.snapToGround(null, 0.0D, 0.0D, 0.0D, 42.0D), EPS);
    }

    // ─── 期望位置 ──────────────────────────────────────────────────────────

    @Test
    void behindOffsetStaysOnRadius() {
        double[] offset = new double[2];
        for (float bodyYaw = -360.0F; bodyYaw <= 360.0F; bodyYaw += 37.0F) {
            PetMotion.offsetFromAngle(PetMotion.behindAngle(bodyYaw), 2.0F, offset);
            double radius = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
            assertEquals(2.0D, radius, 1.0E-6D, "背后偏移半径必须恒定");
        }
    }

    @Test
    void behindAngleIsOppositeToBodyYaw() {
        // yaw=0 时玩家正面朝 +Z，背后应为 -Z
        double[] offset = new double[2];
        PetMotion.offsetFromAngle(PetMotion.behindAngle(0.0F), 2.0F, offset);
        assertEquals(0.0D, offset[0], 1.0E-6D, "yaw=0 时背后偏移不应有 X 分量");
        assertEquals(-2.0D, offset[1], 1.0E-6D, "yaw=0 时背后应在 -Z 方向");
    }

    @Test
    void offsetFollowsMinecraftYawConvention() {
        double[] offset = new double[2];
        // yaw=90 时实体正面朝 -X，因此「前方」偏移应在 -X
        PetMotion.offsetFromAngle(90.0F, 1.0F, offset);
        assertEquals(-1.0D, offset[0], 1.0E-6D, "yaw=90 的前方是 -X");
        assertEquals(0.0D, offset[1], 1.0E-6D);
        // 背后与前方严格相反
        PetMotion.offsetFromAngle(PetMotion.behindAngle(90.0F), 1.0F, offset);
        assertEquals(1.0D, offset[0], 1.0E-6D, "yaw=90 的背后是 +X");
        assertEquals(0.0D, offset[1], 1.0E-6D);
    }

    @Test
    void behindOffsetStaysWithinMaxDistance() {
        double[] offset = new double[2];
        PetMotion.offsetFromAngle(PetMotion.behindAngle(37.0F), PetMotionProfile.MAX_DISTANCE, offset);
        double distance = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]);
        assertTrue(distance <= PetMotionProfile.MAX_DISTANCE + 1.0E-6D, "背后偏移不得超过 5 格");
    }
    @Test
    void flyerHoversAboveOwnerHead() {
        PetMotionProfile flyer = PetMotionProfile.defaults(PetMotionProfile.Kind.FLYER);
        double y = PetMotion.desiredY(flyer, 64.0D, 1.8F, 0L);
        assertTrue(y > 64.0D + 1.8D, "飞行类必须浮在主人头部之上");
    }

    @Test
    void walkerDesiredYStartsAtOwnerFeet() {
        PetMotionProfile walker = PetMotionProfile.defaults(PetMotionProfile.Kind.WALKER);
        assertEquals(64.0D, PetMotion.desiredY(walker, 64.0D, 1.8F, 12345L), EPS,
                "陆行类的 Y 由地面吸附决定，起点应在主人脚部");
    }

    // ─── 配置约束 ──────────────────────────────────────────────────────────

    @Test
    void profilesRespectMaxDistance() {
        for (PetMotionProfile.Kind kind : PetMotionProfile.Kind.values()) {
            PetMotionProfile profile = PetMotionProfile.defaults(kind);
            assertNotNull(profile);
            assertTrue(profile.orbitRadius() <= PetMotionProfile.MAX_DISTANCE,
                    kind + " 的环绕半径必须小于 5 格");
            assertTrue(profile.stiffness() > 0.0F, kind + " 的刚度必须为正");
        }
    }

    @Test
    void profileRejectsOversizedOrbitRadius() {
        boolean threw = false;
        try {
            PetMotionProfile.defaults(PetMotionProfile.Kind.WALKER)
                    .withOrbitRadius(PetMotionProfile.MAX_DISTANCE + 1.0F);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw, "超过 5 格的环绕半径必须在构造时就被拒绝");
    }

    @Test
    void horizontalDistanceSqrIsSquared() {
        assertEquals(25.0D, PetMotion.horizontalDistanceSqr(0.0D, 0.0D, 3.0D, 4.0D), EPS);
        assertEquals(0.0D, PetMotion.horizontalDistanceSqr(7.0D, 7.0D, 7.0D, 7.0D), EPS);
    }

    @Test
    void groundSamplerIsNullForEmptyColumn() {
        // 顺手验证函数式接口可用 lambda 实现且能返回 null（表示"没有地面"）
        PetMotion.GroundSampler empty = (x, y, z) -> null;
        assertNull(empty.groundAt(0.0D, 0.0D, 0.0D));
    }
}
