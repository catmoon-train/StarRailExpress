package org.agmas.noellesroles.game.roles.Innocent.locksmith_inspiration;

import io.wifi.starrailexpress.api.RoleComponent;
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
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class LocksmithInspirationComponent implements RoleComponent, ServerTickingComponent {

    public static final ComponentKey<LocksmithInspirationComponent> KEY = ModComponents.LOCKSMITH_INSPIRATION;

    public static final int MAX_POINTS = 18;
    public static final int OBSERVE_TICKS_REQUIRED = 20 * 15;

    private final Player player;
    private int inspirationPoints = 0;
    private int observingDoorTicks = 0;

    public LocksmithInspirationComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
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
        LocksmithInspirationComponent component = ModComponents.LOCKSMITH_INSPIRATION.get(player);
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            if (component.getObservingDoorTicks() > 0) {
                component.setObservingDoorTicks(0);
            }
            return;
        }

        if (component.getInspirationPoints() >= LocksmithInspirationComponent.MAX_POINTS) {
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
        if (ticks >= LocksmithInspirationComponent.OBSERVE_TICKS_REQUIRED) {
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
        if (level.getBlockEntity(pos) instanceof io.wifi.starrailexpress.contents.block_entity.DoorBlockEntity) {
            return true;
        }
        if (level.getBlockEntity(pos.below()) instanceof io.wifi.starrailexpress.contents.block_entity.DoorBlockEntity) {
            return true;
        }
        return level.getBlockEntity(pos.above()) instanceof io.wifi.starrailexpress.contents.block_entity.DoorBlockEntity;
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

    public void sync() {
        ModComponents.LOCKSMITH_INSPIRATION.sync(this.player);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("inspirationPoints", this.inspirationPoints);
        tag.putInt("observingDoorTicks", this.observingDoorTicks);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("inspirationPoints", this.inspirationPoints);
        tag.putInt("observingDoorTicks", this.observingDoorTicks);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inspirationPoints = Math.max(0, Math.min(MAX_POINTS, tag.getInt("inspirationPoints")));
        this.observingDoorTicks = Math.max(0, tag.getInt("observingDoorTicks"));
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.inspirationPoints = Math.max(0, Math.min(MAX_POINTS, tag.getInt("inspirationPoints")));
        this.observingDoorTicks = Math.max(0, tag.getInt("observingDoorTicks"));
    }
}