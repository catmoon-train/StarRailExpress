package io.wifi.events.day_night_fight.cca;

import io.wifi.events.day_night_fight.DNF;
import io.wifi.events.day_night_fight.block.DNFBlocks;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * DNF underworld flow:
 * waiting room -> particle white door -> true underworld countdown.
 */
public class DNFUnderworldComponent implements RoleComponent, ServerTickingComponent {
    public static final ComponentKey<DNFUnderworldComponent> KEY = ComponentRegistry.getOrCreate(
            SRE.id("dnf_underworld"), DNFUnderworldComponent.class);

    private static final int MAX_REVIVE_TICKS = 180 * 20;
    private static final int ATTACK_TIME_REDUCTION_TICKS = 30 * 20;
    private static final int ROOM_HALF_SIZE = 5;
    private static final int ROOM_HEIGHT = 5;
    private static final int ROOM_SPACING = 32;

    private final Player player;
    private boolean waitingRoom;
    private boolean inUnderworld;
    private int reviveCountdownTicks;
    private BlockPos waitingRoomOrigin = BlockPos.ZERO;
    private BlockPos doorPos = BlockPos.ZERO;

    public DNFUnderworldComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        clear();
    }

    @Override
    public void clear() {
        waitingRoom = false;
        inUnderworld = false;
        reviveCountdownTicks = 0;
        waitingRoomOrigin = BlockPos.ZERO;
        doorPos = BlockPos.ZERO;
        if (player instanceof ServerPlayer serverPlayer) {
            removeUnderworldEffects(serverPlayer);
        }
        sync();
    }

    public void sync() {
        if (player instanceof ServerPlayer sp) {
            KEY.sync(sp);
        }
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        writeState(tag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        readState(tag);
    }

    @Override
    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        writeState(tag);
    }

    @Override
    public void readFromNbt(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider provider) {
        readState(tag);
    }

    private void writeState(CompoundTag tag) {
        tag.putBoolean("WaitingRoom", waitingRoom);
        tag.putBoolean("InUnderworld", inUnderworld);
        tag.putInt("ReviveCountdownTicks", reviveCountdownTicks);
        tag.putLong("WaitingRoomOrigin", waitingRoomOrigin.asLong());
        tag.putLong("DoorPos", doorPos.asLong());
    }

    private void readState(CompoundTag tag) {
        waitingRoom = tag.getBoolean("WaitingRoom");
        inUnderworld = tag.getBoolean("InUnderworld");
        reviveCountdownTicks = tag.contains("ReviveCountdownTicks")
                ? tag.getInt("ReviveCountdownTicks")
                : tag.getInt("revive_countdown");
        waitingRoomOrigin = tag.contains("WaitingRoomOrigin") ? BlockPos.of(tag.getLong("WaitingRoomOrigin")) : BlockPos.ZERO;
        doorPos = tag.contains("DoorPos") ? BlockPos.of(tag.getLong("DoorPos")) : BlockPos.ZERO;
    }

    /**
     * Called when the player is pulled away from the normal world. Countdown does not start yet.
     */
    public void enterUnderworld() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ServerLevel level = serverPlayer.serverLevel();
        waitingRoom = true;
        inUnderworld = false;
        reviveCountdownTicks = MAX_REVIVE_TICKS;
        waitingRoomOrigin = computeWaitingRoomOrigin(serverPlayer);
        doorPos = waitingRoomOrigin.offset(0, 1, ROOM_HALF_SIZE);

        buildWaitingRoom(level, waitingRoomOrigin);
        applyWaitingRoomEffects(serverPlayer);
        serverPlayer.teleportTo(level, waitingRoomOrigin.getX() + 0.5, waitingRoomOrigin.getY() + 1.0,
                waitingRoomOrigin.getZ() - ROOM_HALF_SIZE + 2.5, serverPlayer.getYRot(), serverPlayer.getXRot());
        level.playSound(null, serverPlayer.blockPosition(), SoundEvents.WARDEN_DEATH, SoundSource.PLAYERS, 1.0f, 0.8f);
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.underworld.waiting_room")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        sync();
    }

    public void enterTrueUnderworld(ServerPlayer serverPlayer) {
        if (!waitingRoom || inUnderworld) {
            return;
        }
        ServerLevel level = serverPlayer.serverLevel();
        BlockPos revivePos = DNFWorldComponent.KEY.get(level).findRandomUnderworldSpawn(level);
        waitingRoom = false;
        inUnderworld = true;
        reviveCountdownTicks = MAX_REVIVE_TICKS;
        applyTrueUnderworldEffects(serverPlayer);
        serverPlayer.teleportTo(level, revivePos.getX() + 0.5, revivePos.getY() + 1.0, revivePos.getZ() + 0.5,
                serverPlayer.getYRot(), serverPlayer.getXRot());
        level.playSound(null, revivePos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.75f);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, revivePos.getX() + 0.5,
                revivePos.getY() + 1.0, revivePos.getZ() + 0.5, 30, 0.6, 0.8, 0.6, 0.05);
        DNF.spawnUnderworldMonster(level, serverPlayer);
        serverPlayer.displayClientMessage(Component.translatable("message.dnf.underworld.enter")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
        sync();
    }

    public void tick() {
        if (waitingRoom && player instanceof ServerPlayer serverPlayer) {
            ensureUnderworldEffects(serverPlayer);
            if (serverPlayer.blockPosition().distSqr(doorPos) <= 2.25) {
                enterTrueUnderworld(serverPlayer);
            }
            return;
        }
        if (!inUnderworld) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            ensureUnderworldEffects(serverPlayer);
        }
        if (reviveCountdownTicks > 0) {
            reviveCountdownTicks--;
            if (reviveCountdownTicks % 20 == 0 || reviveCountdownTicks <= 100) {
                sync();
            }
        }
    }

    public void reduceTime() {
        if (!inUnderworld) {
            return;
        }
        reviveCountdownTicks = Math.max(0, reviveCountdownTicks - ATTACK_TIME_REDUCTION_TICKS);
        sync();
    }

    public void revivePlayer() {
        this.waitingRoom = false;
        this.inUnderworld = false;
        this.reviveCountdownTicks = 0;
        if (player instanceof ServerPlayer serverPlayer) {
            removeUnderworldEffects(serverPlayer);
        }
        sync();
    }

    public boolean isInUnderworld() {
        return inUnderworld;
    }

    public boolean isWaitingRoom() {
        return waitingRoom;
    }

    public boolean isUnderworldSequenceActive() {
        return waitingRoom || inUnderworld;
    }

    public int getReviveCountdown() {
        return reviveCountdownTicks;
    }

    public int getRemainingMinutes() {
        return Math.max(0, reviveCountdownTicks / 20) / 60;
    }

    public int getRemainingSeconds() {
        return Math.max(0, reviveCountdownTicks / 20) % 60;
    }

    public float getDarkness() {
        if (!inUnderworld) {
            return 0.0f;
        }
        float elapsed = 1.0f - Math.clamp((float) reviveCountdownTicks / (float) MAX_REVIVE_TICKS, 0.0f, 1.0f);
        return elapsed * 0.5f;
    }

    @Override
    public void serverTick() {
        if (!isUnderworldSequenceActive()) {
            return;
        }
        tick();
        if (inUnderworld && reviveCountdownTicks <= 0) {
            GameUtils.killPlayer(player, true, null);
        }
    }

    private static void applyWaitingRoomEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.GHOST_STATE, Integer.MAX_VALUE, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.PLAYER_ISOLATION, Integer.MAX_VALUE, 0, false, false, false));
        player.removeEffect(ModEffects.AFTERLIFE_FILTER);
    }

    private static void applyTrueUnderworldEffects(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModEffects.GHOST_STATE, Integer.MAX_VALUE, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.PLAYER_ISOLATION, Integer.MAX_VALUE, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.AFTERLIFE_FILTER, Integer.MAX_VALUE, 0, false, false, false));
    }

    private void ensureUnderworldEffects(ServerPlayer player) {
        if (inUnderworld) {
            applyTrueUnderworldEffects(player);
        } else if (waitingRoom) {
            applyWaitingRoomEffects(player);
        }
    }

    private static void removeUnderworldEffects(ServerPlayer player) {
        player.removeEffect(ModEffects.GHOST_STATE);
        player.removeEffect(ModEffects.PLAYER_ISOLATION);
        player.removeEffect(ModEffects.AFTERLIFE_FILTER);
    }

    private static BlockPos computeWaitingRoomOrigin(ServerPlayer player) {
        DNFWorldComponent world = DNFWorldComponent.KEY.get(player.serverLevel());
        int hash = Math.abs(player.getUUID().hashCode());
        int xOffset = 96 + (hash % 8) * ROOM_SPACING;
        int zOffset = 96 + ((hash / 8) % 8) * ROOM_SPACING;
        return world.getUnderworldCenter().offset(xOffset, 24, zOffset);
    }

    private static void buildWaitingRoom(ServerLevel level, BlockPos origin) {
        BlockState white = DNFBlocks.WHITE_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState door = DNFBlocks.UNDERWORLD_DOOR.defaultBlockState();

        for (int x = -ROOM_HALF_SIZE; x <= ROOM_HALF_SIZE; x++) {
            for (int y = 0; y <= ROOM_HEIGHT; y++) {
                for (int z = -ROOM_HALF_SIZE; z <= ROOM_HALF_SIZE; z++) {
                    boolean shell = x == -ROOM_HALF_SIZE || x == ROOM_HALF_SIZE
                            || z == -ROOM_HALF_SIZE || z == ROOM_HALF_SIZE || y == 0 || y == ROOM_HEIGHT;
                    level.setBlock(origin.offset(x, y, z), shell ? white : air, 3);
                }
            }
        }

        level.setBlock(origin.offset(0, 1, ROOM_HALF_SIZE), door, 3);
        level.setBlock(origin.offset(0, 2, ROOM_HALF_SIZE), door, 3);
        level.setBlock(origin.offset(0, 1, ROOM_HALF_SIZE + 1), white, 3);
        level.setBlock(origin.offset(0, 2, ROOM_HALF_SIZE + 1), white, 3);

        level.setBlock(origin.offset(-3, 1, -1), Blocks.QUARTZ_STAIRS.defaultBlockState(), 3);
        level.setBlock(origin.offset(-2, 1, -1), Blocks.QUARTZ_SLAB.defaultBlockState(), 3);
        level.setBlock(origin.offset(3, 1, 1), Blocks.WHITE_BED.defaultBlockState(), 3);
        level.setBlock(origin.offset(2, 1, 3), Blocks.END_ROD.defaultBlockState(), 3);
        level.setBlock(origin.offset(-4, 1, 3), Blocks.WHITE_CARPET.defaultBlockState(), 3);
        level.setBlock(origin.offset(4, 1, -3), Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState(), 3);
        level.setBlock(origin.offset(1, 1, -4), Blocks.WHITE_GLAZED_TERRACOTTA.defaultBlockState(), 3);
    }
}
