package org.agmas.noellesroles.content.block;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.ServerTaskInfoClasses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.modes.repair.RepairModeState;
import org.agmas.noellesroles.game.modes.repair.RepairRoleDefinition;

import java.util.ArrayList;
import java.util.List;

public class RepairExitGateBlock extends Block {
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    // 大型拱门结构：5宽 × 5高 × 2深
    // 立柱用石砖 (2x2)，顶部横梁用深色橡木，铁栅栏填充拱门
    private static final int[][] PILLAR_OFFSETS = {
        // 左侧立柱 (2x2 石砖，高5)
        {-2,1,0},{-2,1,1},{-1,1,0},{-1,1,1},
        {-2,2,0},{-2,2,1},{-1,2,0},{-1,2,1},
        {-2,3,0},{-2,3,1},{-1,3,0},{-1,3,1},
        {-2,4,0},{-2,4,1},{-1,4,0},{-1,4,1},
        {-2,5,0},{-2,5,1},{-1,5,0},{-1,5,1},
        // 右侧立柱 (2x2 石砖，高5)
        {2,1,0},{2,1,1},{3,1,0},{3,1,1},
        {2,2,0},{2,2,1},{3,2,0},{3,2,1},
        {2,3,0},{2,3,1},{3,3,0},{3,3,1},
        {2,4,0},{2,4,1},{3,4,0},{3,4,1},
        {2,5,0},{2,5,1},{3,5,0},{3,5,1},
    };
    private static final int[][] ARCH_OFFSETS = {
        // 拱门铁栅栏 (3宽 × 4高，1深)
        {0,1,0},{1,1,0},
        {0,2,0},{1,2,0},
        {0,3,0},{1,3,0},
        {0,4,0},{1,4,0},
        // 顶部横梁 (深色橡木)
        {-2,5,0},{-1,5,0},{0,5,0},{1,5,0},{2,5,0},{3,5,0},
        {-2,5,1},{-1,5,1},{0,5,1},{1,5,1},{2,5,1},{3,5,1},
        // 装饰层：石砖楼梯(拱形顶)
        {-1,4,0},{-1,4,1},{2,4,0},{2,4,1},
    };
    private static final int[][] DECOR_OFFSETS = {
        // 灯笼 (立柱内侧)
        {-1,3,0},{2,3,0},
    };

    public RepairExitGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide() && !state.is(oldState.getBlock())) {
            buildGateStructure(level, pos);
        }
        super.onPlace(state, level, pos, oldState, moved);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            destroyGateStructure(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private void buildGateStructure(Level level, BlockPos pos) {
        // 石砖立柱
        for (int[] off : PILLAR_OFFSETS) {
            BlockPos p = pos.offset(off[0], off[1], off[2]);
            if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(Blocks.AIR)) {
                level.setBlock(p, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        // 铁栅栏拱门
        for (int[] off : ARCH_OFFSETS) {
            BlockPos p = pos.offset(off[0], off[1], off[2]);
            if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(Blocks.AIR)) {
                level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        // 装饰灯笼
        for (int[] off : DECOR_OFFSETS) {
            BlockPos p = pos.offset(off[0], off[1], off[2]);
            if (level.getBlockState(p).canBeReplaced() || level.getBlockState(p).is(Blocks.AIR)) {
                level.setBlock(p, Blocks.LANTERN.defaultBlockState()
                        .setValue(LanternBlock.HANGING, off[1] >= 3), Block.UPDATE_ALL);
            }
        }
        if (level instanceof ServerLevel sl) {
            sl.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.6F, 0.5F);
        }
    }

    private static void destroyGateStructure(Level level, BlockPos pos) {
        for (int[][] offsets : new int[][][] { PILLAR_OFFSETS, ARCH_OFFSETS, DECOR_OFFSETS }) {
            for (int[] off : offsets) {
                BlockPos p = pos.offset(off[0], off[1], off[2]);
                BlockState state = level.getBlockState(p);
                if (state.is(Blocks.STONE_BRICKS) || state.is(Blocks.IRON_BARS)
                        || state.is(Blocks.DARK_OAK_FENCE) || state.is(Blocks.LANTERN)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        return useGate(state, level, pos, player) ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        useGate(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private boolean useGate(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        boolean survivor = RepairRoleDefinition.byId(ModComponents.REPAIR_ROLES.get(serverPlayer).activeRole)
                .map(role -> role.faction == RepairRoleDefinition.Faction.SURVIVOR)
                .orElse(false);
        if (!survivor || !RepairModeState.canUseSurvivorUtility(serverPlayer)) {
            return true;
        }
        if (!RepairModeState.areExitGatesPowered(serverLevel)) {
            serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.gate_locked",
                    RepairModeState.getCompletedStationCount(serverLevel), RepairModeState.REQUIRED_REPAIRED_STATIONS),
                    true);
            return true;
        }

        // === 多阶段开门动画 ===
        playGateOpenAnimation(serverLevel, pos);
        // 立即让玩家逃脱（动画异步进行）
        RepairModeState.clearRestraints(serverPlayer);
        serverPlayer.addTag(RepairModeState.ESCAPED_TAG);
        RepairModeState.awardCoins(serverPlayer, 125, "repair_coin_source.station");
        serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.repair.escaped"), false);
        serverPlayer.setGameMode(GameType.SPECTATOR);
        level.setBlockAndUpdate(pos, state.setValue(OPEN, true));
        return true;
    }

    private void playGateOpenAnimation(ServerLevel level, BlockPos pos) {
        playGateOpenAnimationStatic(level, pos);
    }

    public static void playGateOpenAnimationStatic(ServerLevel level, BlockPos pos) {
        // 阶段1 (t=0)：大门震颤，铁栅栏抖动粒子
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.3F);
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 3.0D, pos.getZ() + 0.5D,
                20, 1.5D, 1.5D, 0.5D, 0.15D);

        // 阶段2 (t=5 ticks)：石砖立柱碎裂
        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(5, () -> {
            for (int[] off : PILLAR_OFFSETS) {
                BlockPos p = pos.offset(off[0], off[1], off[2]);
                if (off[1] <= 2 && (level.getBlockState(p).is(Blocks.STONE_BRICKS))) {
                    // 底部立柱先碎裂
                    level.setBlock(p, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D,
                    30, 1.8D, 1.2D, 0.5D, 0.08D);
            level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 2.5D, pos.getZ() + 0.5D,
                    16, 1.2D, 1.0D, 0.5D, 0.12D);
        }));

        // 阶段3 (t=10 ticks)：铁栅栏崩解，立柱继续碎裂
        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(10, () -> {
            for (int[] off : ARCH_OFFSETS) {
                BlockPos p = pos.offset(off[0], off[1], off[2]);
                if (level.getBlockState(p).is(Blocks.IRON_BARS)) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                            5, 0.2D, 0.2D, 0.2D, 0.02D);
                }
            }
            for (int[] off : PILLAR_OFFSETS) {
                BlockPos p = pos.offset(off[0], off[1], off[2]);
                if (level.getBlockState(p).is(Blocks.COBBLESTONE) || level.getBlockState(p).is(Blocks.STONE_BRICKS)) {
                    level.setBlock(p, Blocks.GRAVEL.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            level.playSound(null, pos, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.BLOCKS, 0.8F, 0.4F);
            level.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5D, pos.getY() + 3.0D, pos.getZ() + 0.5D,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5D, pos.getY() + 2.0D, pos.getZ() + 0.5D,
                    25, 2.0D, 1.5D, 0.5D, 0.05D);
        }));

        // 阶段4 (t=18 ticks)：完全崩塌，清除所有结构，胜利粒子
        GameUtils.serverTaskQueue.add(new ServerTaskInfoClasses.SchedulerTask(18, () -> {
            destroyGateStructure(level, pos);
            // 胜利粒子雨
            level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5D, pos.getY() + 2.5D, pos.getZ() + 0.5D,
                    20, 2.5D, 2.0D, 0.5D, 0.12D);
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5D, pos.getY() + 3.5D, pos.getZ() + 0.5D,
                    40, 2.0D, 2.5D, 0.5D, 0.08D);
            level.sendParticles(ParticleTypes.SCRAPE, pos.getX() + 0.5D, pos.getY() + 1.5D, pos.getZ() + 0.5D,
                    35, 2.5D, 1.0D, 0.5D, 0.06D);
            level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2F, 0.5F);
        }));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }
}
