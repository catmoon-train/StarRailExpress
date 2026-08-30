package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded, player-sized A* used by possessed bodies. */
final class FakeSteveNavigator {
    private static final int MAX_VISITED = 1024;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private FakeSteveNavigator() {
    }

    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal) {
        return find(level, mover, goal, false);
    }

    static ArrayDeque<BlockPos> find(ServerLevel level, ServerPlayer mover, BlockPos goal,
                                     boolean explicitTarget) {
        BlockPos start = mover.blockPosition();
        Set<BlockPos> occupied = occupiedByPlayers(level, mover);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> cost = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        BlockPos normalizedStart = normalize(level, start);
        BlockPos normalizedGoal = normalize(level, goal);
        if (FakeStevePathPolicy.shouldPreferDirectRoute(explicitTarget,
                hasDirectWalkCorridor(level, normalizedStart, normalizedGoal, occupied))) {
            ArrayDeque<BlockPos> direct = new ArrayDeque<>();
            direct.add(normalizedGoal.immutable());
            return direct;
        }
        boolean jumpsAllowed = SREGameWorldComponent.KEY.get(level).isJumpAvailable();
        BlockPos best = normalizedStart;
        int bestDistance = distance(normalizedStart, goal);
        cost.put(normalizedStart, 0);
        open.add(new Node(normalizedStart, bestDistance));

        while (!open.isEmpty() && closed.size() < MAX_VISITED) {
            BlockPos current = open.remove().pos();
            if (!closed.add(current)) {
                continue;
            }
            int currentDistance = distance(current, goal);
            if (currentDistance < bestDistance) {
                best = current;
                bestDistance = currentDistance;
            }
            if (currentDistance <= 1) {
                best = current;
                break;
            }
            for (BlockPos next : neighbours(level, current, occupied, jumpsAllowed)) {
                if (closed.contains(next)) {
                    continue;
                }
                int tentative = cost.get(current) + 1 + Math.abs(next.getY() - current.getY())
                        + FakeStevePathPolicy.edgePenalty(dropBeside(level, next));
                if (tentative >= cost.getOrDefault(next, Integer.MAX_VALUE)) {
                    continue;
                }
                cameFrom.put(next, current);
                cost.put(next, tentative);
                open.add(new Node(next, tentative + distance(next, goal)));
            }
        }

        ArrayDeque<BlockPos> path = new ArrayDeque<>();
        BlockPos cursor = best;
        while (!cursor.equals(normalizedStart) && cameFrom.containsKey(cursor)) {
            path.addFirst(cursor);
            cursor = cameFrom.get(cursor);
        }
        return path;
    }

    static boolean reaches(ArrayDeque<BlockPos> path, BlockPos goal) {
        BlockPos last = path.peekLast();
        return last != null && distance(last, goal) <= 1;
    }

    /**
     * True when the column can carry a body. A carpet or trapdoor is not support
     * on its own, so an unbacked one can never be mistaken for a floor.
     */
    static boolean safeStand(ServerLevel level, BlockPos pos) {
        for (int dy : new int[] { 0, 1, -1 }) {
            if (standable(level, pos.offset(0, dy, 0))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Guard against walking into empty space. Only a real drop (no floor within
     * three blocks below the next tile) is refused; a one-block step down is
     * normal walking and must never freeze the body.
     */
    static boolean stepSafe(ServerLevel level, Vec3 from, Vec3 horizontal) {
        Vec3 ahead = from.add(horizontal);
        BlockPos feet = BlockPos.containing(ahead);
        if (!level.getBlockState(feet).getFluidState().isEmpty()) {
            return true;
        }
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
            return true;
        }
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos below = feet.below(dy);
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Nodes hugging an open drop are discouraged so routes keep to the deck. */
    private static boolean dropBeside(ServerLevel level, BlockPos pos) {
        for (Direction direction : HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (!level.getBlockState(side).getFluidState().is(FluidTags.WATER)
                    && !hasFloor(level, side) && !hasFloor(level, side.below())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFloor(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static List<BlockPos> neighbours(ServerLevel level, BlockPos current,
                                             Set<BlockPos> occupied, boolean jumpsAllowed) {
        List<BlockPos> result = new ArrayList<>(12);
        boolean swimming = level.getBlockState(current).getFluidState().is(FluidTags.WATER)
                || level.getBlockState(current.above()).getFluidState().is(FluidTags.WATER);
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.relative(direction);
            // Include the upper node even on no-jump maps. A grass path to a
            // grass block is a 1/16 step, not a real jump; filter larger rises
            // below using the actual collision surfaces.
            for (int dy : new int[] { 0, 1, -1 }) {
                BlockPos candidate = horizontal.offset(0, dy, 0);
                if (dy > 0 && !jumpsAllowed && !swimming
                        && !FakeStevePathPolicy.canStepUpWithoutJump(
                                standingSurfaceY(level, candidate) - standingSurfaceY(level, current))) {
                    continue;
                }
                if (!occupied.contains(candidate) && standable(level, candidate)) {
                    result.add(candidate.immutable());
                    break;
                }
            }
        }
        return result;
    }

    /** World-space support height for deciding whether an upward node needs jump input. */
    private static double standingSurfaceY(ServerLevel level, BlockPos feet) {
        var feetShape = level.getBlockState(feet).getCollisionShape(level, feet);
        if (!feetShape.isEmpty()) {
            double height = feetShape.max(Direction.Axis.Y);
            if (FakeStevePathPolicy.isWalkThroughFootLayer(false, height)) {
                return feet.getY() + height;
            }
        }
        BlockPos below = feet.below();
        var belowShape = level.getBlockState(below).getCollisionShape(level, below);
        return belowShape.isEmpty() ? feet.getY() : below.getY() + belowShape.max(Direction.Axis.Y);
    }

    private static Set<BlockPos> occupiedByPlayers(ServerLevel level, ServerPlayer mover) {
        Set<BlockPos> occupied = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player == mover || !player.isAlive() || player.isSpectator()) {
                continue;
            }
            occupied.add(player.blockPosition().immutable());
            Vec3 movement = player.getDeltaMovement();
            double horizontalLength = Math.sqrt(movement.x * movement.x + movement.z * movement.z);
            if (horizontalLength > 0.1D) {
                occupied.add(BlockPos.containing(player.position().add(
                        movement.x / horizontalLength, 0.0D,
                        movement.z / horizontalLength)).immutable());
            }
        }
        return occupied;
    }

    private static BlockPos normalize(ServerLevel level, BlockPos pos) {
        for (int dy : new int[] { 0, 1, -1 }) {
            BlockPos candidate = pos.offset(0, dy, 0);
            if (standable(level, candidate)) {
                return candidate.immutable();
            }
        }
        return pos.immutable();
    }

    private static boolean standable(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        var feetShape = feetState.getCollisionShape(level, feet);
        boolean feetShapeEmpty = feetShape.isEmpty();
        double feetCollisionHeight = feetShapeEmpty ? 0.0D : feetShape.max(Direction.Axis.Y);
        boolean feetFree = FakeStevePathPolicy.isWalkThroughFootLayer(
                        feetShapeEmpty, feetCollisionHeight)
                || FakeSteveDoorAccess.isOpenablePassage(feetState);
        boolean headFree = headState.getCollisionShape(level, feet.above()).isEmpty()
                || FakeSteveDoorAccess.isOpenablePassage(headState);
        boolean swimming = feetState.getFluidState().is(FluidTags.WATER)
                || headState.getFluidState().is(FluidTags.WATER);
        boolean groundBelow = !level.getBlockState(feet.below())
                .getCollisionShape(level, feet.below()).isEmpty();
        // A slab or stair is a floor by itself; a carpet never is.
        boolean selfSupporting = !feetShapeEmpty && feetCollisionHeight >= 0.4D;
        return feetFree && headFree && (swimming || groundBelow || selfSupporting);
    }

    private static boolean hasDirectWalkCorridor(ServerLevel level, BlockPos start,
                                                  BlockPos goal, Set<BlockPos> occupied) {
        if (start.getY() != goal.getY()) {
            return false;
        }
        double dx = goal.getX() - start.getX();
        double dz = goal.getZ() - start.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) {
            return true;
        }
        double perpendicularX = -dz / length;
        double perpendicularZ = dx / length;
        int samples = Math.max(1, (int) Math.ceil(length * 2.0D));
        for (int sample = 1; sample <= samples; sample++) {
            double progress = (double) sample / samples;
            double centerX = start.getX() + 0.5D + dx * progress;
            double centerZ = start.getZ() + 0.5D + dz * progress;
            for (double side : new double[] { -0.28D, 0.0D, 0.28D }) {
                BlockPos position = BlockPos.containing(
                        centerX + perpendicularX * side, start.getY(),
                        centerZ + perpendicularZ * side);
                if (!standable(level, position)
                        || occupied.contains(position) && distance(position, goal) > 1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int distance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private record Node(BlockPos pos, double score) {
    }
}
