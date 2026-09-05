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

package org.agmas.noellesroles.role_data.innocence;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.block.DoorPartBlock;
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.packet.ConductorDoorListS2CPacket;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 船长技能「舱门调度」：对准房间门使用，花费金币并前摇后选择其他房间门，传送到门旁安全位置。
 */
public class ConductorRoleData extends SimpleRoleData {

    public static final ResourceLocation SKILL_ID = Noellesroles.id("conductor_door_warp");
    public static final int SKILL_COST = 125;
    public static final int WINDUP_SECONDS = 5;
    public static final int COOLDOWN_SECONDS = 90;
    private static final double DOOR_REACH = 6.0;
    private static final int ROOM_DOOR_TASK_TYPE = 7;

    /** 前摇结束的游戏时刻（level.getGameTime）；0 表示未在前摇。 */
    public long windupEndGameTime;
    public boolean awaitingPick;
    private int chargedCost;
    @Nullable
    private BlockPos sourceDoorPos;

    public ConductorRoleData(RoleDataContext context) {
        super(context);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer target) {
        return this.player == target;
    }

    public boolean useSkill(ServerPlayer sp) {
        if (sp.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(sp)) {
            return false;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isSkillAvailable || !gameWorld.isRole(sp, ModRoles.CONDUCTOR)) {
            return false;
        }
        if (isWindingUp(sp) || awaitingPick) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.already_active")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        BlockPos source = findLookedDoor(sp);
        if (source == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.need_door")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        List<ConductorDoorListS2CPacket.DoorEntry> destinations = listOtherRoomDoors(sp.serverLevel(), source);
        if (destinations.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.no_other_doors")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        int cost = skillCost();
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(sp);
        if (shop.balance < cost) {
            sp.displayClientMessage(
                    Component.translatable("message.noellesroles.insufficient_funds_money", cost)
                            .withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        shop.addToBalance(-cost);
        chargedCost = cost;
        sourceDoorPos = source.immutable();

        int windup = windupTicks();
        if (windup <= 0) {
            openDoorPicker(sp, destinations);
            return true;
        }

        windupEndGameTime = sp.level().getGameTime() + windup;
        awaitingPick = false;
        sp.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, windup + 10, 0, false, false, false));
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.45f,
                1.35f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.windup")
                .withStyle(ChatFormatting.GOLD), true);
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.CONDUCTOR)) {
            resetState(false);
            return;
        }
        if (windupEndGameTime <= 0 && !awaitingPick) {
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp) || sp.isSpectator() || !gameWorld.isSkillAvailable) {
            cancelPending(sp, "message.noellesroles.conductor.cancelled");
            return;
        }
        if (windupEndGameTime > 0 && sp.level().getGameTime() >= windupEndGameTime && !awaitingPick) {
            List<ConductorDoorListS2CPacket.DoorEntry> destinations = listOtherRoomDoors(sp.serverLevel(),
                    sourceDoorPos);
            if (destinations.isEmpty()) {
                cancelPending(sp, "message.noellesroles.conductor.no_other_doors");
                return;
            }
            openDoorPicker(sp, destinations);
        }
    }

    public void selectDestination(@Nullable BlockPos targetPos) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        if (!awaitingPick) {
            return;
        }
        if (targetPos == null) {
            cancelPending(sp, "message.noellesroles.conductor.cancelled_pick");
            return;
        }
        if (!GameUtils.isPlayerAliveAndSurvival(sp) || sp.isSpectator()) {
            cancelPending(sp, "message.noellesroles.conductor.cancelled");
            return;
        }
        SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
        if (!gameWorld.isRunning() || !gameWorld.isRole(sp, ModRoles.CONDUCTOR) || !gameWorld.isSkillAvailable) {
            cancelPending(sp, "message.noellesroles.conductor.cancelled");
            return;
        }
        BlockPos door = normalizeDoor(sp.serverLevel(), targetPos);
        if (door == null || door.equals(sourceDoorPos) || normalizeRoomDoor(sp.serverLevel(), door) == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.invalid_door")
                    .withStyle(ChatFormatting.RED), true);
            reopenPicker(sp);
            return;
        }
        Vec3 dest = findSafeBesideDoor(sp.serverLevel(), door);
        if (dest == null) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.unsafe_door")
                    .withStyle(ChatFormatting.RED), true);
            reopenPicker(sp);
            return;
        }
        awaitingPick = false;
        chargedCost = 0;
        sourceDoorPos = null;
        windupEndGameTime = 0;
        sp.removeEffect(ModEffects.MOVE_BANED);
        float yaw = destYawTowardDoor(sp.serverLevel(), door, dest);
        sp.teleportTo(sp.serverLevel(), dest.x, dest.y, dest.z, yaw, 0f);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.9f,
                1.1f);
        sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.warped")
                .withStyle(ChatFormatting.GOLD), true);
        sync();
    }

    public boolean isWindingUp(ServerPlayer sp) {
        return windupEndGameTime > 0 && sp.level().getGameTime() < windupEndGameTime;
    }

    private void openDoorPicker(ServerPlayer sp, List<ConductorDoorListS2CPacket.DoorEntry> destinations) {
        windupEndGameTime = 0;
        awaitingPick = true;
        sp.removeEffect(ModEffects.MOVE_BANED);
        ServerPlayNetworking.send(sp, new ConductorDoorListS2CPacket(destinations));
        sp.displayClientMessage(Component.translatable("message.noellesroles.conductor.pick_door")
                .withStyle(ChatFormatting.GOLD), true);
        sync();
    }

    private void reopenPicker(ServerPlayer sp) {
        List<ConductorDoorListS2CPacket.DoorEntry> destinations = listOtherRoomDoors(sp.serverLevel(), sourceDoorPos);
        if (destinations.isEmpty()) {
            cancelPending(sp, "message.noellesroles.conductor.no_other_doors");
            return;
        }
        ServerPlayNetworking.send(sp, new ConductorDoorListS2CPacket(destinations));
    }

    private void cancelPending(ServerPlayer sp, String msgKey) {
        if (chargedCost > 0) {
            SREPlayerShopComponent.KEY.get(sp).addToBalance(chargedCost);
        }
        SREAbilityPlayerComponent.KEY.get(sp).setSkillCooldown(SKILL_ID, 0);
        sp.removeEffect(ModEffects.MOVE_BANED);
        resetState(true);
        sp.displayClientMessage(Component.translatable(msgKey).withStyle(ChatFormatting.RED), true);
    }

    private void resetState(boolean syncNow) {
        windupEndGameTime = 0;
        awaitingPick = false;
        chargedCost = 0;
        sourceDoorPos = null;
        if (syncNow) {
            sync();
        }
    }

    @Nullable
    private static BlockPos findLookedDoor(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 end = eye.add(sp.getLookAngle().scale(DOOR_REACH));
        BlockHitResult hit = sp.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, sp));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return normalizeDoor(sp.serverLevel(), hit.getBlockPos());
    }

    @Nullable
    private static BlockPos normalizeDoor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SmallDoorBlock)) {
            return null;
        }
        BlockPos lower = state.getValue(SmallDoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (!(level.getBlockEntity(lower) instanceof SmallDoorBlockEntity)) {
            return null;
        }
        return lower.immutable();
    }

    @Nullable
    private static BlockPos normalizeRoomDoor(ServerLevel level, BlockPos pos) {
        BlockPos lower = normalizeDoor(level, pos);
        if (lower == null) {
            return null;
        }
        if (!(level.getBlockEntity(lower) instanceof SmallDoorBlockEntity door)) {
            return null;
        }
        String key = door.getKeyName();
        if (key == null || key.isEmpty()) {
            return null;
        }
        return lower;
    }

    private static List<ConductorDoorListS2CPacket.DoorEntry> listOtherRoomDoors(ServerLevel level,
            @Nullable BlockPos source) {
        List<ConductorDoorListS2CPacket.DoorEntry> result = new ArrayList<>();
        if (GameUtils.taskBlocks == null || GameUtils.taskBlocks.isEmpty()) {
            return result;
        }
        String sourceKey = source == null ? null : roomKeyAt(level, source);
        for (Map.Entry<BlockPos, Integer> entry : GameUtils.taskBlocks.entrySet()) {
            if (entry.getValue() == null || entry.getValue() != ROOM_DOOR_TASK_TYPE) {
                continue;
            }
            BlockPos pos = normalizeRoomDoor(level, entry.getKey());
            if (pos == null || pos.equals(source)) {
                continue;
            }
            String key = roomKeyAt(level, pos);
            if (key == null || key.equals(sourceKey)) {
                continue;
            }
            result.add(new ConductorDoorListS2CPacket.DoorEntry(pos, displayName(key)));
        }
        result.sort(Comparator.comparing(ConductorDoorListS2CPacket.DoorEntry::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Nullable
    private static String roomKeyAt(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof DoorBlockEntity door)) {
            return null;
        }
        String key = door.getKeyName();
        if (key == null || key.isEmpty()) {
            return null;
        }
        return key;
    }

    private static String displayName(String keyName) {
        String name = keyName;
        if (name.startsWith("locked:")) {
            name = name.substring("locked:".length());
        }
        return name.isEmpty() ? keyName : name;
    }

    @Nullable
    private static Vec3 findSafeBesideDoor(ServerLevel level, BlockPos doorPos) {
        BlockState state = level.getBlockState(doorPos);
        Direction facing = state.hasProperty(DoorPartBlock.FACING)
                ? state.getValue(DoorPartBlock.FACING)
                : Direction.NORTH;
        List<BlockPos> candidates = new ArrayList<>();
        candidates.add(doorPos.relative(facing));
        candidates.add(doorPos.relative(facing.getOpposite()));
        candidates.add(doorPos.relative(facing, 2));
        candidates.add(doorPos.relative(facing.getOpposite(), 2));
        candidates.add(doorPos.relative(facing.getClockWise()));
        candidates.add(doorPos.relative(facing.getCounterClockWise()));
        for (BlockPos base : candidates) {
            for (int dy : new int[] { 0, 1, -1 }) {
                BlockPos feet = base.offset(0, dy, 0);
                if (isStandable(level, feet)) {
                    return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos feet) {
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());
        BlockState ground = level.getBlockState(feet.below());
        if (feetState.getBlock() instanceof SmallDoorBlock || headState.getBlock() instanceof SmallDoorBlock) {
            return false;
        }
        if (!feetState.getCollisionShape(level, feet).isEmpty()) {
            return false;
        }
        if (!headState.getCollisionShape(level, feet.above()).isEmpty()) {
            return false;
        }
        return !ground.getCollisionShape(level, feet.below()).isEmpty();
    }

    private static float destYawTowardDoor(ServerLevel level, BlockPos doorPos, Vec3 dest) {
        double dx = (doorPos.getX() + 0.5) - dest.x;
        double dz = (doorPos.getZ() + 0.5) - dest.z;
        return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
    }

    public static int skillCost() {
        int cost = NoellesRolesConfig.instance().conductorDoorWarpCost;
        return cost < 0 ? SKILL_COST : cost;
    }

    public static int windupTicks() {
        int seconds = NoellesRolesConfig.instance().conductorDoorWarpWindupSeconds;
        if (seconds < 0) {
            seconds = WINDUP_SECONDS;
        }
        return GameConstants.getInTicks(0, seconds);
    }

    public static int cooldownSeconds() {
        int seconds = NoellesRolesConfig.instance().conductorDoorWarpCooldownSeconds;
        return seconds <= 0 ? COOLDOWN_SECONDS : seconds;
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("WindupEnd", windupEndGameTime);
        tag.putBoolean("AwaitingPick", awaitingPick);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider provider) {
        windupEndGameTime = tag.getLong("WindupEnd");
        awaitingPick = tag.getBoolean("AwaitingPick");
    }
}
