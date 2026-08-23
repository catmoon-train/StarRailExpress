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

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class LocksmithInspirationRoleData extends SimpleRoleData {



    public static final int MAX_POINTS = 18;
    // 10s
    public static final int OBSERVE_TICKS_REQUIRED = 20 * 10;

    private int inspirationPoints = 0;
    private int observingDoorTicks = 0;

    public LocksmithInspirationRoleData(RoleDataContext context) {
        super(context);
    }


    @Override
    public void init() {
        this.inspirationPoints = 0;
        this.observingDoorTicks = 0;
        this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(this.player.level());
        if (!gameWorldComponent.isRunning()) {
            return;
        }
        if (player instanceof ServerPlayer sp) {
            tickLocksmithInspiration(sp, gameWorldComponent);
        }

    }

    private static void tickLocksmithInspiration(ServerPlayer player, SREGameWorldComponent gameWorldComponent) {
        if (!gameWorldComponent.isRole(player, ModRoles.LOCKSMITH))
            return;
        LocksmithInspirationRoleData component = RoleData.getNullable(LocksmithInspirationRoleData.class, player);
        if (!RoleData.isAttached(component))
            return;
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            if (component.getObservingDoorTicks() > 0) {
                component.setObservingDoorTicks(0);
            }
            return;
        }

        if (component.getInspirationPoints() >= LocksmithInspirationRoleData.MAX_POINTS) {
            if (component.getObservingDoorTicks() > 0) {
                component.setObservingDoorTicks(0);
            }
            return;
        }

        if (!isLookingAtDoor(player)) {
            if (component.getObservingDoorTicks() > 0) {
                component.setObservingDoorTicks(0);
            }
            return;
        }

        int ticks = component.incrementObservingDoorTicks();
        if (ticks >= LocksmithInspirationRoleData.OBSERVE_TICKS_REQUIRED) {
            component.setObservingDoorTicks(0);
            component.addInspiration(1);
        }
    }

    private static final double LOCKSMITH_OBSERVE_DISTANCE = 4.0D;

    private static boolean isLookingAtDoor(ServerPlayer player) {
        HitResult hitResult = player.pick(LOCKSMITH_OBSERVE_DISTANCE, 0.0F, false);
        if (hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult blockHitResult)) {
            return false;
        }
        return isDoorBlock(player.level(), blockHitResult.getBlockPos());
    }

    private static boolean isDoorBlock(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof DoorBlock) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof io.wifi.starrailexpress.content.block_entity.DoorBlockEntity) {
            return true;
        }
        if (level.getBlockEntity(pos.below()) instanceof io.wifi.starrailexpress.content.block_entity.DoorBlockEntity) {
            return true;
        }
        return level.getBlockEntity(pos.above()) instanceof io.wifi.starrailexpress.content.block_entity.DoorBlockEntity;
    }

    public int getInspirationPoints() {
        return inspirationPoints;
    }

    public int getObservingDoorTicks() {
        return observingDoorTicks;
    }

    public void setObservingDoorTicks(int ticks) {
        int clamped = Math.max(0, ticks);
        if (this.observingDoorTicks != clamped) {
            this.observingDoorTicks = clamped;
            this.sync();
        }
    }

    public int incrementObservingDoorTicks() {
        this.observingDoorTicks++;
        return this.observingDoorTicks;
    }

    public boolean addInspiration(int amount) {
        int next = Math.min(MAX_POINTS, Math.max(0, this.inspirationPoints + amount));
        if (next == this.inspirationPoints) {
            return false;
        }
        this.inspirationPoints = next;
        this.sync();
        return true;
    }

    public boolean consumeInspiration(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.inspirationPoints < amount) {
            return false;
        }
        this.inspirationPoints -= amount;
        this.sync();
        return true;
    }


    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("inspirationPoints", this.inspirationPoints);
        tag.putInt("observingDoorTicks", this.observingDoorTicks);
    }



    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inspirationPoints = Math.max(0, Math.min(MAX_POINTS, tag.getInt("inspirationPoints")));
        this.observingDoorTicks = Math.max(0, tag.getInt("observingDoorTicks"));
    }
}
