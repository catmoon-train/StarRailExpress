package net.exmo.sre.repair.content.block_entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.ModComponents;
import net.exmo.sre.repair.state.RepairModeState;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HunterCageBlockEntity extends BlockEntity {
    public static final int MAX_PRISONERS = 2;
    private final List<TrialEntry> prisoners = new ArrayList<>();
    private int rescueProgress;
    // 多方块结构子方块位置列表
    private final List<BlockPos> structureBlocks = new ArrayList<>();
    private boolean structureBuilt = false;
    // 跟踪上次救援金币奖励的时间
    private long lastRescueRewardTick = 0L;

    public long getLastRescueRewardTick() {
        return lastRescueRewardTick;
    }

    public void setLastRescueRewardTick(long tick) {
        this.lastRescueRewardTick = tick;
    }

    public HunterCageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HUNTER_CAGE_BLOCK_ENTITY, pos, state);
    }

    // === 多方块结构建造 ===
    public void buildCageStructure() {
        if (structureBuilt || level == null || level.isClientSide()) return;
        structureBuilt = true;
        // 2x3x2 笼子结构：底座+铁栅栏形成笼子
        BlockPos base = worldPosition;
        // 底座 (2x2)
        for (int dx = -1; dx <= 0; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                BlockPos p = base.offset(dx, -1, dz);
                if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(Blocks.AIR)) {
                    level.setBlock(p, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
                    structureBlocks.add(p.immutable());
                }
            }
        }
        // 铁栅栏笼体 (2x3x2)
        for (int y = 0; y <= 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // 跳过中心（囚犯空间）和四个角柱（用实心方块）
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                    boolean center = dx == 0 && dz == 0;
                    if (center) continue;
                    BlockPos p = base.offset(dx, y, dz);
                    if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(Blocks.AIR)) {
                        if (corner && y <= 1) {
                            level.setBlock(p, Blocks.DARK_OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
                        } else {
                            level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
                        }
                        structureBlocks.add(p.immutable());
                    }
                }
            }
        }
        // 顶部横梁
        for (int dx = -1; dx <= 1; dx++) {
            BlockPos p = base.offset(dx, 3, -1);
            if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(Blocks.AIR)) {
                level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
                structureBlocks.add(p.immutable());
            }
            p = base.offset(dx, 3, 1);
            if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(Blocks.AIR)) {
                level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
                structureBlocks.add(p.immutable());
            }
        }
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, base, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.7F);
        }
    }

    public void destroyCageStructure() {
        if (level == null || level.isClientSide()) return;
        for (BlockPos p : structureBlocks) {
            if (level.getBlockState(p).is(Blocks.IRON_BARS) || level.getBlockState(p).is(Blocks.DARK_OAK_FENCE)
                    || level.getBlockState(p).is(Blocks.POLISHED_BLACKSTONE)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        structureBlocks.clear();
        structureBuilt = false;
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 0.9F);
        }
    }

    public Optional<UUID> getPrisoner() {
        return prisoners.isEmpty() ? Optional.empty() : Optional.of(prisoners.get(0).prisoner);
    }

    public void setPrisoner(UUID prisoner) {
        addPrisoner(prisoner, null);
    }

    public Optional<UUID> getCaptor() {
        return prisoners.isEmpty() || prisoners.get(0).captor == null
                ? Optional.empty()
                : Optional.of(prisoners.get(0).captor);
    }

    public void setCaptor(UUID captor) {
        if (!prisoners.isEmpty()) {
            prisoners.get(0).captor = captor;
            setChangedAndSync();
        }
    }

    public List<TrialEntry> getTrialEntries() {
        return List.copyOf(prisoners);
    }

    public int getProgress(UUID prisoner) {
        return prisoners.stream()
                .filter(entry -> entry.prisoner.equals(prisoner))
                .findFirst()
                .map(entry -> entry.progress)
                .orElse(0);
    }

    public int getRescueProgress() {
        return rescueProgress;
    }

    public boolean addPrisoner(UUID prisoner, UUID captor) {
        if (prisoners.size() >= MAX_PRISONERS
                || prisoners.stream().anyMatch(entry -> entry.prisoner.equals(prisoner))) {
            return false;
        }
        if (prisoners.isEmpty()) {
            buildCageStructure();
        }
        prisoners.add(new TrialEntry(prisoner, captor, 0));
        rescueProgress = 0;
        setChangedAndSync();
        return true;
    }

    public boolean addRescueProgress(int amount) {
        if (prisoners.isEmpty()) {
            return false;
        }
        rescueProgress = Math.min(100, rescueProgress + amount);
        setChangedAndSync();
        if (rescueProgress >= 100) {
            releasePrisoner();
            return true;
        }
        return false;
    }

    public void releasePrisoner() {
        if (level instanceof ServerLevel serverLevel && !prisoners.isEmpty()) {
            TrialEntry entry = prisoners.remove(0);
            if (serverLevel.getPlayerByUUID(entry.prisoner) instanceof ServerPlayer target) {
                RepairModeState.clearRestraints(target);
                target.teleportTo(worldPosition.getX() + 0.5D, worldPosition.getY(), worldPosition.getZ() + 0.5D);
                target.setHealth(Math.min(target.getMaxHealth(), RepairModeState.REVIVE_HEALTH));
            }
        }
        rescueProgress = 0;
        if (prisoners.isEmpty()) {
            destroyCageStructure();
        }
        setChangedAndSync();
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                            HunterCageBlockEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean changed = entity.prisoners.removeIf(entry ->
                !(serverLevel.getPlayerByUUID(entry.prisoner) instanceof ServerPlayer target)
                        || target.isSpectator()
                        || GameUtils.isPlayerEliminated(target)
                        || !ModComponents.REPAIR_ROLES.get(target).trialStand.present()
                        || !ModComponents.REPAIR_ROLES.get(target).trialStand.toBlockPos().equals(pos));

        if (entity.prisoners.isEmpty() && entity.structureBuilt) {
            entity.destroyCageStructure();
        }

        // === 动画效果 ===
        if (!entity.prisoners.isEmpty()) {
            long t = level.getGameTime();
            // 笼子抖动粒子
            if (t % 6 == 0) {
                for (TrialEntry entry : entity.prisoners) {
                    float progress = Math.min(1.0F, entry.progress / (float) RepairModeState.TRIAL_EXECUTION_TICKS);
                    int particleCount = 1 + (int) (progress * 4);
                    serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.getX() + 0.5D, pos.getY() + 1.8D, pos.getZ() + 0.5D,
                            particleCount, 0.4D, 0.2D, 0.4D, 0.01D);
                    if (progress > 0.5F) {
                        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                                pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                                2, 0.35D, 0.3D, 0.35D, 0.02D);
                    }
                }
            }
            // 笼子震颤音效
            if (t % 40 == 0) {
                serverLevel.playSound(null, pos, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.5F, 0.6F);
            }
            if (t % 80 == 0 && entity.prisoners.stream().anyMatch(e -> e.progress > RepairModeState.TRIAL_EXECUTION_TICKS / 2)) {
                serverLevel.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.5F);
            }
        }

        for (TrialEntry entry : List.copyOf(entity.prisoners)) {
            if (!(serverLevel.getPlayerByUUID(entry.prisoner) instanceof ServerPlayer target)) {
                continue;
            }
            entry.progress++;

            // 每5 tick 调整一次位置，避免每tick传送导致卡顿和持续扣血
            if (level.getGameTime() % 5 == 0) {
                target.teleportTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                // 设置玩家为站立姿势（而非趴下）
                target.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
            // 保持较高血量避免持续扣血动画
            target.setHealth(Math.max(6.0F, target.getHealth()));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 10, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 2, false, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false, true));
            target.addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 40, 0, false, false, true));

            // === 审判进度全图播报 ===
            int totalTicks = RepairModeState.TRIAL_EXECUTION_TICKS;
            int[] milestones = {totalTicks / 4, totalTicks / 2, totalTicks * 3 / 4, totalTicks * 9 / 10};
            String[] milestoneKeys = {"25", "50", "75", "90"};
            for (int i = 0; i < milestones.length; i++) {
                if (entry.progress == milestones[i]) {
                    net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.translatable(
                            "message.noellesroles.repair.trial_broadcast_" + milestoneKeys[i],
                            target.getName(),
                            (totalTicks - entry.progress) / 20);
                    for (ServerPlayer p : serverLevel.players()) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                                new org.agmas.noellesroles.packet.BroadcastMessageS2CPacket(msg, true));
                    }
                }
            }

            if (entry.progress >= totalTicks) {
                var component = ModComponents.REPAIR_ROLES.get(target);
                component.downed = false;
                component.carriedBy = null;
                component.trialStand = net.exmo.sre.repair.component.RepairRolePlayerComponent.BlockPosTag.NONE;
                component.sync();
                target.removeEffect(ModEffects.NO_COLLIDE);
                GameUtils.forceKillPlayer(target, true,
                        entry.captor != null ? serverLevel.getPlayerByUUID(entry.captor) : null,
                        ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "repair_trial_execution"));
                onTrialExecuted(serverLevel, pos, entry, target);
                entity.prisoners.remove(entry);
                changed = true;
            }
        }

        if (changed || level.getGameTime() % 10 == 0) {
            entity.setChangedAndSync();
        }
    }

    /**
     * 处决落地后的 murder 融合逻辑：
     * 处决者获得赏金（murder 击杀奖励特色），处决地点掉落一把遗恨德林加，
     * 尸体本身由 forceKillPlayer(spawnBody=true) 生成、可被幸存者搜刮。
     */
    private static void onTrialExecuted(ServerLevel serverLevel, BlockPos pos, TrialEntry entry, ServerPlayer victim) {
        if (entry.captor != null
                && serverLevel.getPlayerByUUID(entry.captor) instanceof ServerPlayer captor
                && RepairModeState.isHunter(captor)) {
            RepairModeState.awardCoins(captor, 60, "repair_coin_source.trial_execution");
        }
        ItemEntity drop = new ItemEntity(serverLevel,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                new ItemStack(ModItems.VENGEANCE_DERRINGER));
        drop.setDefaultPickUpDelay();
        serverLevel.addFreshEntity(drop);
        serverLevel.sendParticles(ParticleTypes.SOUL,
                pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, 20, 0.4D, 0.5D, 0.4D, 0.02D);
        for (ServerPlayer player : serverLevel.players()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.noellesroles.repair.vengeance_dropped", victim.getDisplayName())
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ListTag list = new ListTag();
        for (TrialEntry entry : prisoners) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Prisoner", entry.prisoner);
            if (entry.captor != null) {
                entryTag.putUUID("Captor", entry.captor);
            }
            entryTag.putInt("Progress", entry.progress);
            list.add(entryTag);
        }
        tag.put("Prisoners", list);
        tag.putInt("RescueProgress", rescueProgress);
        tag.putBoolean("StructureBuilt", structureBuilt);
        if (!structureBlocks.isEmpty()) {
            ListTag structList = new ListTag();
            for (BlockPos p : structureBlocks) {
                CompoundTag pt = new CompoundTag();
                pt.putInt("X", p.getX());
                pt.putInt("Y", p.getY());
                pt.putInt("Z", p.getZ());
                structList.add(pt);
            }
            tag.put("StructureBlocks", structList);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        prisoners.clear();
        if (tag.contains("Prisoners", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Prisoners", Tag.TAG_COMPOUND);
            for (Tag raw : list) {
                CompoundTag entry = (CompoundTag) raw;
                if (entry.hasUUID("Prisoner")) {
                    prisoners.add(new TrialEntry(entry.getUUID("Prisoner"),
                            entry.hasUUID("Captor") ? entry.getUUID("Captor") : null,
                            entry.getInt("Progress")));
                }
            }
        } else if (tag.hasUUID("Prisoner")) {
            prisoners.add(new TrialEntry(tag.getUUID("Prisoner"),
                    tag.hasUUID("Captor") ? tag.getUUID("Captor") : null, 0));
        }
        rescueProgress = tag.getInt("RescueProgress");
        structureBuilt = tag.getBoolean("StructureBuilt");
        structureBlocks.clear();
        if (tag.contains("StructureBlocks", Tag.TAG_LIST)) {
            ListTag structList = tag.getList("StructureBlocks", Tag.TAG_COMPOUND);
            for (Tag raw : structList) {
                CompoundTag pt = (CompoundTag) raw;
                structureBlocks.add(new BlockPos(pt.getInt("X"), pt.getInt("Y"), pt.getInt("Z")));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static class TrialEntry {
        public final UUID prisoner;
        public UUID captor;
        public int progress;

        private TrialEntry(UUID prisoner, UUID captor, int progress) {
            this.prisoner = prisoner;
            this.captor = captor;
            this.progress = progress;
        }
    }
}