package org.agmas.noellesroles.game.fake_steve;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent.Task;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Adapts assigned train tasks to normal world movement and interaction. */
public final class FakeSteveTaskPlanner {
    private static final long RETRY_TICKS = 10 * 20L;
    private static final double INTERACT_DISTANCE_SQR = 10.0D;

    public enum Strategy {
        BLOCK_INTERACT,
        CONSUME,
        HOLD_POSITION,
        CROUCH,
        JUMP
    }

    private FakeSteveTaskPlanner() {
    }

    public static Optional<Strategy> strategy(Task task) {
        if (task == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (task) {
            case SLEEP, RAED_BOOK, CHAIR, NOTE_BLOCK, TOILET,
                    LIGHT_STOVE, CLEAN_DUST, TRANSPORT, PRUNE_BUSH -> Strategy.BLOCK_INTERACT;
            case EAT, DRINK -> Strategy.CONSUME;
            case EXERCISE, BATHE, BE_ALONE, BREATHE, OUTSIDE, PRAY -> Strategy.HOLD_POSITION;
            case MEDITATE -> Strategy.CROUCH;
            case HARVEST_CROP -> Strategy.JUMP;
            default -> null;
        });
    }

    static boolean hasCompletableTask(ServerPlayer body, FakeSteveAgentState state) {
        SREPlayerTaskComponent component = SREPlayerTaskComponent.KEY.get(body);
        if (component == null) {
            return false;
        }
        long now = body.serverLevel().getGameTime();
        if (state.taskType != null && component.tasks.containsKey(state.taskType)
                && strategy(state.taskType).isPresent()) {
            return true;
        }
        return component.tasks.keySet().stream().anyMatch(task -> strategy(task).isPresent()
                && state.taskBackoffUntil.getOrDefault(task, 0L) <= now);
    }

    static boolean tick(ServerLevel level, ServerPlayer body, FakeSteveAgentState state) {
        SREPlayerTaskComponent component = SREPlayerTaskComponent.KEY.get(body);
        if (component == null) {
            return false;
        }
        long now = level.getGameTime();
        if (now < state.taskRetryTick) {
            return false;
        }
        if (state.taskType != null && !component.tasks.containsKey(state.taskType)) {
            releaseTaskPosture(body, state.taskType);
            clear(state);
        }
        Task selected = chooseTask(component, body, state, now);
        if (selected != state.taskType) {
            releaseTaskPosture(body, state.taskType);
            if (state.taskType != null
                    && now - state.taskStartedTick > FakeSteveTaskSelection.STALL_TICKS) {
                state.taskBackoffUntil.put(state.taskType, now + RETRY_TICKS);
            }
            clearDestination(state);
            state.taskType = selected;
            state.taskStartedTick = now;
        }
        if (state.taskType == null) {
            return false;
        }

        if (state.taskGoal == null) {
            TaskDestination destination = findDestination(level, body, state.taskType);
            if (destination == null) {
                state.taskBackoffUntil.put(state.taskType, now + RETRY_TICKS);
                state.taskType = null;
                clearDestination(state);
                return false;
            }
            state.taskGoal = destination.standingPos();
            state.taskInteractTarget = destination.interactionPos();
        }

        if (body.distanceToSqr(Vec3.atBottomCenterOf(state.taskGoal)) > 1.15D) {
            FakeSteveAi.follow(level, body, state.taskGoal, state, 0.16D);
            return true;
        }

        Strategy strategy = strategy(state.taskType).orElse(null);
        if (strategy == null) {
            clear(state);
            return false;
        }
        BlockPos interactionPos = state.taskInteractTarget == null ? state.taskGoal : state.taskInteractTarget;
        float[] rotation = rotation(body, Vec3.atCenterOf(interactionPos));
        switch (strategy) {
            case HOLD_POSITION -> FakeSteveMotionController.hold(body, state, rotation[0], rotation[1]);
            case CROUCH -> FakeSteveMotionController.drive(body, state, 0.0F, 0.0F,
                    false, false, true, rotation[0], rotation[1], body.blockPosition());
            case JUMP -> FakeSteveMotionController.drive(body, state, 0.0F, 0.0F,
                    SREGameWorldComponent.KEY.get(level).isJumpAvailable()
                            && body.onGround() && now % 12L == 0L, false, false,
                    rotation[0], rotation[1], body.blockPosition());
            case CONSUME -> consumeOrCollect(level, body, state, rotation, now);
            case BLOCK_INTERACT -> interact(level, body, state, rotation, now);
        }
        return true;
    }

    /** Drops the active disguise task, releasing any posture it forced on the body. */
    public static void abandon(ServerPlayer body, FakeSteveAgentState state) {
        releaseTaskPosture(body, state.taskType);
        clear(state);
    }

    private static Task chooseTask(SREPlayerTaskComponent component, ServerPlayer body,
            FakeSteveAgentState state, long now) {
        List<FakeSteveTaskSelection.Candidate> candidates = component.tasks.keySet().stream()
                .filter(task -> strategy(task).isPresent())
                .map(task -> new FakeSteveTaskSelection.Candidate(task, estimatedDistance(body, task),
                        state.taskBackoffUntil.getOrDefault(task, 0L) > now))
                .toList();
        return FakeSteveTaskSelection.choose(candidates, state.taskType, state.taskStartedTick, now);
    }

    private static double estimatedDistance(ServerPlayer body, Task task) {
        int[] types = pointTypes(task, body);
        if (types.length == 0) {
            return 0.0D;
        }
        return GameUtils.taskBlocks.entrySet().stream()
                .filter(entry -> contains(types, entry.getValue()))
                .mapToDouble(entry -> body.distanceToSqr(Vec3.atCenterOf(entry.getKey())))
                .min().orElse(Double.MAX_VALUE);
    }

    private static TaskDestination findDestination(ServerLevel level, ServerPlayer body, Task task) {
        if (task == Task.MEDITATE) {
            return new TaskDestination(body.blockPosition(), body.blockPosition());
        }
        if (task == Task.BREATHE || task == Task.OUTSIDE) {
            BlockPos pos = findOpenSky(level, body);
            return pos == null ? null : new TaskDestination(pos, pos);
        }
        if (task == Task.BE_ALONE) {
            BlockPos pos = findAloneSpot(level, body);
            return new TaskDestination(pos, pos);
        }
        BlockPos ownedBed = task == Task.SLEEP ? findOwnedRoomBed(level, body) : null;
        int[] types = pointTypes(task, body);
        List<BlockPos> candidates = new ArrayList<>();
        if (ownedBed != null) {
            candidates.add(ownedBed);
        } else {
            GameUtils.taskBlocks.forEach((pos, type) -> {
                if (contains(types, type)) {
                    candidates.add(pos.immutable());
                }
            });
        }
        candidates.sort(Comparator.comparingDouble(pos -> body.distanceToSqr(Vec3.atCenterOf(pos))));
        for (BlockPos candidate : candidates) {
            BlockPos goal = standingGoal(level, body, task, candidate);
            if (goal == null) {
                continue;
            }
            var path = FakeSteveNavigator.find(level, body, goal);
            if (body.distanceToSqr(Vec3.atCenterOf(goal)) <= INTERACT_DISTANCE_SQR
                    || FakeSteveNavigator.reaches(path, goal)) {
                return new TaskDestination(goal, candidate);
            }
        }
        return null;
    }

    private static BlockPos findOpenSky(ServerLevel level, ServerPlayer body) {
        BlockPos origin = body.blockPosition();
        for (int radius = 0; radius <= 24; radius += 4) {
            for (int x = -radius; x <= radius; x += 4) {
                for (int z = -radius; z <= radius; z += 4) {
                    BlockPos pos = origin.offset(x, 0, z);
                    if (canStand(level, pos) && level.canSeeSky(pos.above())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos findAloneSpot(ServerLevel level, ServerPlayer body) {
        BlockPos best = body.blockPosition();
        double bestNearest = nearestOtherPlayerSqr(level, body, best);
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos pos = body.blockPosition().offset(level.getRandom().nextInt(33) - 16,
                    0, level.getRandom().nextInt(33) - 16);
            if (!canStand(level, pos)) {
                continue;
            }
            double nearest = nearestOtherPlayerSqr(level, body, pos);
            if (nearest > bestNearest) {
                best = pos;
                bestNearest = nearest;
            }
        }
        return best;
    }

    private static double nearestOtherPlayerSqr(ServerLevel level, ServerPlayer body, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        return level.players().stream().filter(player -> player != body && player.isAlive())
                .mapToDouble(player -> player.distanceToSqr(center)).min().orElse(Double.MAX_VALUE);
    }

    private static boolean canStand(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }

    private static void consumeOrCollect(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, float[] rotation, long now) {
        int slot = findConsumable(body, state.taskType == Task.DRINK);
        if (slot >= 0) {
            FakeSteveAi.select(body, slot);
            FakeSteveMotionController.hold(body, state, rotation[0], rotation[1]);
            if (!body.isUsingItem()) {
                body.gameMode.useItem(body, level, body.getMainHandItem(), InteractionHand.MAIN_HAND);
                if (!body.isUsingItem()) {
                    body.startUsingItem(InteractionHand.MAIN_HAND);
                }
            }
            return;
        }
        int empty = firstEmptyHotbar(body);
        if (empty >= 0) {
            FakeSteveAi.select(body, empty);
        }
        interact(level, body, state, rotation, now);
    }

    private static int findConsumable(ServerPlayer body, boolean drink) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = body.getInventory().getItem(slot);
            UseAnim animation = stack.getUseAnimation();
            boolean isDrink = animation == UseAnim.DRINK;
            boolean isFood = stack.has(DataComponents.FOOD) || animation == UseAnim.EAT;
            if (drink ? isDrink : isFood && !isDrink) {
                return slot;
            }
        }
        return -1;
    }

    private static void interact(ServerLevel level, ServerPlayer body,
            FakeSteveAgentState state, float[] rotation, long now) {
        FakeSteveMotionController.hold(body, state, rotation[0], rotation[1]);
        if ((state.taskType == Task.CHAIR || state.taskType == Task.TOILET) && body.isPassenger()) {
            return;
        }
        if (state.taskType == Task.SLEEP && body.isSleeping()) {
            return;
        }
        if (now < state.nextTaskInteractionTick) {
            return;
        }
        state.nextTaskInteractionTick = now + humanInteractionDelay(level);
        if (state.taskType == Task.CLEAN_DUST && !selectItem(body, Items.BRUSH)) {
            state.taskRetryTick = now + RETRY_TICKS;
            return;
        }
        if (state.taskType == Task.PRUNE_BUSH && !selectItem(body, Items.SHEARS)) {
            state.taskRetryTick = now + RETRY_TICKS;
            return;
        }
        if (state.taskType == Task.TRANSPORT) {
            int packageSlot = findItem(body, ModItems.TRANSPORT_PACKAGE);
            if (packageSlot >= 0) {
                FakeSteveAi.select(body, packageSlot);
            } else {
                int empty = firstEmptyHotbar(body);
                if (empty >= 0) {
                    FakeSteveAi.select(body, empty);
                }
            }
        }
        BlockPos target = state.taskInteractTarget == null ? state.taskGoal : state.taskInteractTarget;
        if ((state.taskType == Task.EAT || state.taskType == Task.DRINK)
                && isPlateEmpty(level, target)) {
            if (state.taskType != null) {
                state.taskBackoffUntil.put(state.taskType, now + RETRY_TICKS);
            }
            releaseTaskPosture(body, state.taskType);
            clear(state);
            return;
        }
        double maxDistance = FakeSteveInteractionPolicy.maxInteractionDistance(state.taskType);
        if (body.position().distanceTo(Vec3.atCenterOf(target)) > maxDistance) {
            state.taskGoal = null;
            state.taskInteractTarget = null;
            state.path.clear();
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target),
                Direction.UP, target, false);
        body.gameMode.useItemOn(body, level, body.getMainHandItem(),
                InteractionHand.MAIN_HAND, hit);
        if (FakeSteveInteractionPolicy.swingsHand(state.taskType)) {
            body.swing(InteractionHand.MAIN_HAND, true);
        }
        if (state.taskType == Task.TRANSPORT
                && findItem(body, ModItems.TRANSPORT_PACKAGE) >= 0
                && GameUtils.taskBlocks.getOrDefault(target, -1) != 19) {
            state.taskGoal = null;
            state.taskInteractTarget = null;
            state.path.clear();
        }
    }

    private static int humanInteractionDelay(ServerLevel level) {
        return 6 + level.getRandom().nextInt(11);
    }

    /** A serving tray with nothing on it cannot feed anyone. */
    private static boolean isPlateEmpty(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PlateTrayBlockEntity plate
                && plate.getStoredItems().isEmpty();
    }

    private static boolean selectItem(ServerPlayer body, net.minecraft.world.item.Item item) {
        int slot = findItem(body, item);
        if (slot < 0) {
            return false;
        }
        FakeSteveAi.select(body, slot);
        return true;
    }

    private static int findItem(ServerPlayer body, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 9; slot++) {
            if (body.getInventory().getItem(slot).is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static int firstEmptyHotbar(ServerPlayer body) {
        for (int slot = 0; slot < 9; slot++) {
            if (body.getInventory().getItem(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int[] pointTypes(Task task, ServerPlayer body) {
        return switch (task) {
            case EAT -> new int[] { 1 };
            case DRINK -> new int[] { 2 };
            case BATHE -> new int[] { 3 };
            case SLEEP -> new int[] { 4 };
            case EXERCISE -> new int[] { 5 };
            case RAED_BOOK -> new int[] { 6 };
            case TOILET -> new int[] { 8 };
            case CHAIR -> new int[] { 9 };
            case NOTE_BLOCK -> new int[] { 10 };
            case LIGHT_STOVE -> new int[] { 16 };
            case CLEAN_DUST -> new int[] { 17 };
            case TRANSPORT -> findItem(body, ModItems.TRANSPORT_PACKAGE) >= 0
                    ? new int[] { 19 } : new int[] { 18 };
            case PRAY -> new int[] { 20 };
            case PRUNE_BUSH -> new int[] { 21 };
            case HARVEST_CROP -> new int[] { 22 };
            default -> new int[0];
        };
    }

    private static BlockPos standingGoal(ServerLevel level, ServerPlayer body, Task task, BlockPos taskPoint) {
        BlockPos special = switch (task) {
            case EXERCISE, HARVEST_CROP -> taskPoint.above();
            case BATHE -> taskPoint.below();
            default -> null;
        };
        if (special != null && canStand(level, special)) {
            return special;
        }
        if (canStand(level, taskPoint)) {
            return taskPoint;
        }
        return Direction.Plane.HORIZONTAL.stream()
                .map(taskPoint::relative)
                .filter(pos -> canStand(level, pos))
                .min(Comparator.comparingDouble(pos -> body.distanceToSqr(Vec3.atBottomCenterOf(pos))))
                .orElse(null);
    }

    private static BlockPos findOwnedRoomBed(ServerLevel level, ServerPlayer body) {
        List<String> keyNames = new ArrayList<>();
        for (int slot = 0; slot < body.getInventory().getContainerSize(); slot++) {
            ItemStack stack = body.getInventory().getItem(slot);
            if (!stack.is(TMMItems.KEY)) {
                continue;
            }
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
                keyNames.add(lore.lines().getFirst().getString());
            }
        }
        if (keyNames.isEmpty()) {
            return null;
        }
        BlockPos roomDoor = GameUtils.taskBlocks.entrySet().stream()
                .filter(entry -> entry.getValue() == 7)
                .map(java.util.Map.Entry::getKey)
                .filter(pos -> level.getBlockEntity(pos) instanceof SmallDoorBlockEntity door
                        && keyNames.contains(door.getKeyName()))
                .min(Comparator.comparingDouble(pos -> body.distanceToSqr(Vec3.atCenterOf(pos))))
                .orElse(null);
        if (roomDoor == null) {
            return null;
        }
        return GameUtils.taskBlocks.entrySet().stream()
                .filter(entry -> entry.getValue() == 4)
                .map(java.util.Map.Entry::getKey)
                .filter(pos -> pos.distSqr(roomDoor) <= 18.0D * 18.0D)
                .min(Comparator.comparingDouble(pos -> pos.distSqr(roomDoor)))
                .orElse(null);
    }

    private static boolean contains(int[] values, int value) {
        for (int candidate : values) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }

    private static float[] rotation(ServerPlayer body, Vec3 target) {
        Vec3 delta = target.subtract(body.getEyePosition());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        return new float[] {
                (float) (Mth.atan2(-delta.x, delta.z) * Mth.RAD_TO_DEG),
                (float) (-Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG)
        };
    }

    private static void clear(FakeSteveAgentState state) {
        state.taskType = null;
        state.taskStartedTick = 0L;
        clearDestination(state);
    }

    private static void releaseTaskPosture(ServerPlayer body, Task task) {
        if (!FakeSteveInteractionPolicy.releasesPostureAfterCompletion(task)) {
            return;
        }
        if (body.isPassenger()) {
            body.stopRiding();
        }
        if (body.isSleeping()) {
            body.stopSleeping();
        }
        if (task == Task.RAED_BOOK) {
            body.closeContainer();
        }
    }

    private static void clearDestination(FakeSteveAgentState state) {
        state.taskGoal = null;
        state.taskInteractTarget = null;
        state.pathGoal = null;
        state.nextTaskInteractionTick = 0L;
        state.path.clear();
    }

    private record TaskDestination(BlockPos standingPos, BlockPos interactionPos) {
    }
}
