/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.fake_steve;

/**
 * 寻路与移动的纯规则：不读世界、不持有状态，因此每一条都能被单元测试直接断言。
 *
 * <p>
 * 与寻路相关的常数（{@link #STEP_HEIGHT}、{@link #VERTICAL_ORDER}）集中在这里，
 * 让 {@link FakeSteveNavigator} 的搜索内核不掺杂魔法数字。
 */
final class FakeStevePathPolicy {
    /** 原版玩家不需要跳跃输入就能迈上去的最大高度（0.6 格）。 */
    static final double STEP_HEIGHT = 0.6D;
    /**
     * 同一水平坐标上尝试落脚的高度顺序：同层 → 上一层 → 下一层。
     *
     * <p>
     * 顺序固定，因此「上层能站就跳上去」这种副作用不会发生；
     * 也保证 A* 给出一条路时，运行时按同一条规则就能走上去。
     */
    static final int[] VERTICAL_ORDER = { 0, 1, -1 };
    private static final int[] LEVEL_OR_DESCEND = { 0, -1 };
    private static final int[] LEVEL_ASCEND_OR_DESCEND = { 0, 1, -1 };

    private FakeStevePathPolicy() {
    }


    static boolean shouldJump(boolean onGround, boolean ascends, long now, long nextJumpTick) {
        return onGround && ascends && now >= nextJumpTick;
    }

    static boolean shouldJump(boolean jumpsAllowed, boolean onGround, boolean ascends,
                              long now, long nextJumpTick) {
        return jumpsAllowed && shouldJump(onGround, ascends, now, nextJumpTick);
    }

    static boolean hasStalled(double previousDistanceSqr, double currentDistanceSqr,
                              long lastProgressTick, long now) {
        return currentDistanceSqr >= previousDistanceSqr - 0.15D
                && now - lastProgressTick >= 80L;
    }

    /** The body wants to move but its position barely changes between AI samples. */
    static boolean isStuck(double movedDistanceSqr, long sampleGap) {
        return isStuck(movedDistanceSqr, sampleGap, false);
    }

    static boolean isStuck(double movedDistanceSqr, long sampleGap, boolean climbing) {
        return !climbing && sampleGap >= 8L && movedDistanceSqr < 0.0025D;
    }

    static boolean feetCanOccupy(boolean collisionEmpty, double collisionMaxY,
            boolean stair, boolean slab, boolean door) {
        return collisionEmpty || collisionMaxY < 1.0D || stair || slab || door;
    }

    /** The next stair occupies the head cell but leaves a walkable gap. */
    static boolean headCanOccupy(boolean collisionEmpty, boolean stair, boolean slab,
            boolean door) {
        return collisionEmpty || stair || slab || door;
    }

    /** 一格高度的台阶/楼梯相当于半格抬升，可以直接走上去；其他抬升按 0.6 格上限。 */
    static boolean canAscendWithoutJump(double verticalRise, boolean destinationIsStep) {
        if (destinationIsStep && verticalRise <= 1.0D) {
            return true;
        }
        return verticalRise <= STEP_HEIGHT;
    }

    static boolean shouldFaceNextNode(float lookYaw, float nodeYaw) {
        float delta = lookYaw - nodeYaw;
        while (delta > 180.0F) {
            delta -= 360.0F;
        }
        while (delta < -180.0F) {
            delta += 360.0F;
        }
        return Math.abs(delta) > 40.0F;
    }

    static boolean shouldAbandonIdleGoal(int pathFailures) {
        return pathFailures >= 4;
    }

    static boolean needsRecalculation(int stuckTicks) {
        return stuckTicks >= 6;
    }

    static boolean shouldAutoOpenSmallDoor(boolean open, boolean hardLocked) {
        return !open && !hardLocked;
    }

    static boolean shouldSwimUp(boolean inWater, double bodyY, double targetY) {
        return inWater && targetY > bodyY + 0.2D;
    }

    /**
     * Hold jump in water so the body does not sink. Shallow water that still
     * has ground underfoot is walked; only a climb-out needs a hop there.
     */
    static boolean shouldHoldSwim(boolean inWater, boolean onGround, double bodyY,
            double targetY) {
        if (!inWater || targetY < bodyY - 0.35D) {
            return false;
        }
        return !onGround || targetY > bodyY + 0.2D;
    }

    /**
     * 游泳相关的额外代价。节点类型本身已经给出水面代价（{@link FakeStevePathType#WATER}），
     * 这里只补「完全没有支撑的开放水面」那一档，避免身体在深水里被 A* 当作捷径。
     */
    static int swimPenalty(boolean openWater) {
        return openWater ? 3 : 0;
    }

    static boolean shouldSprintForPursuit(boolean pursuingHuman, boolean psychoActive,
                                          boolean crowdBlocked) {
        return !crowdBlocked && (pursuingHuman || psychoActive);
    }

    static boolean countsAsClimbing(boolean verticalProgress, boolean inWater) {
        return verticalProgress || inWater;
    }

    static boolean isWalkThroughFootLayer(boolean collisionEmpty, double collisionMaxY) {
        // Grass paths are 15/16 high. They are not walls: vanilla can step from
        // their top onto a neighbouring full block without jumping.
        return collisionEmpty || collisionMaxY < 1.0D;
    }

    /** A vanilla player can step up 0.6 blocks without a jump input. */
    static boolean canStepUpWithoutJump(double verticalRise) {
        return verticalRise <= STEP_HEIGHT;
    }

    static int[] verticalOffsets(boolean jumpsAllowed, boolean swimming) {
        return jumpsAllowed || swimming ? LEVEL_ASCEND_OR_DESCEND : LEVEL_OR_DESCEND;
    }

    static boolean shouldPreferDirectRoute(boolean explicitTarget, boolean corridorClear) {
        return explicitTarget && corridorClear;
    }

    static boolean canTrackPlayer(boolean alive, boolean spectator, boolean creative,
                                  boolean survivalParticipant) {
        return alive && !spectator && !creative && survivalParticipant;
    }
}
