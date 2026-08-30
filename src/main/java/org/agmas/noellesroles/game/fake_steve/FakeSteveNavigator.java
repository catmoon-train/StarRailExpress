package org.agmas.noellesroles.game.fake_steve;

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
        BlockPos start = mover.blockPosition();
        Set<BlockPos> occupied = occupiedByPlayers(level, mover);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::score));
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Map<BlockPos, Integer> cost = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        BlockPos normalizedStart = normalize(level, start);
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
            for (BlockPos next : neighbours(level, current, occupied)) {
                if (closed.contains(next)) {
                    continue;
                }
                int tentative = cost.get(current) + 1 + Math.abs(next.getY() - current.getY());
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

    private static List<BlockPos> neighbours(ServerLevel level, BlockPos current,
                                             Set<BlockPos> occupied) {
        List<BlockPos> result = new ArrayList<>(12);
        for (Direction direction : HORIZONTAL) {
            BlockPos horizontal = current.relative(direction);
            for (int dy : new int[] { 0, 1, -1 }) {
                BlockPos candidate = horizontal.offset(0, dy, 0);
                if (!occupied.contains(candidate) && standable(level, candidate)) {
                    result.add(candidate.immutable());
                    break;
                }
            }
        }
        return result;
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
        boolean feetFree = feetState.getCollisionShape(level, feet).isEmpty()
                || FakeSteveDoorAccess.isOpenablePassage(feetState);
        boolean headFree = headState.getCollisionShape(level, feet.above()).isEmpty()
                || FakeSteveDoorAccess.isOpenablePassage(headState);
        boolean swimming = feetState.getFluidState().is(FluidTags.WATER)
                || headState.getFluidState().is(FluidTags.WATER);
        return feetFree && headFree && (swimming
                || !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty());
    }

    private static int distance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }

    private record Node(BlockPos pos, double score) {
    }
}
