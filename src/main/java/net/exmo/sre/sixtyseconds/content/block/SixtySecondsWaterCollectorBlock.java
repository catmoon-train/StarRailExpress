package net.exmo.sre.sixtyseconds.content.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.agmas.noellesroles.init.ModItems;

/**
 * 集水器（三档）：放置后被动积攒<b>污染水</b>，右键一次性收取。
 * 档位差异 = 产水间隔 + 容量（注册时给定，常量见 {@link net.exmo.sre.sixtyseconds.SixtySecondsBalance}）：
 * <ul>
 *   <li><b>雨水桶</b>：慢、容量小（木桶接屋檐水）。</li>
 *   <li><b>雨棚集水器</b>：中速中容量（塑料布大接水面）。</li>
 *   <li><b>冷凝集水器</b>：快、容量大（从空气里凝水，合成需供电）。</li>
 * </ul>
 * 满仓即停，收取后恢复积水。产出是<b>污染水</b>——仍需净化链（浴缸/净化台）处理成饮用水，
 * 与「高级净化」科技形成上下游。生长驱动与 {@link SixtySecondsPlanterBlock} 同套路：
 * scheduleTick 主驱动 + randomTick 兜底（区块卸载丢定时后仍能续产）。
 */
public class SixtySecondsWaterCollectorBlock extends Block {
    /** 当前存水（0..容量；属性上限取三档最大容量）。 */
    public static final IntegerProperty WATER = IntegerProperty.create("water", 0, 6);
    private static final String LANG = "message.noellesroles.sixty_seconds.collector.";

    private final int capacity;
    private final int intervalTicks;

    public SixtySecondsWaterCollectorBlock(Properties properties, int capacity, int intervalTicks) {
        super(properties);
        this.capacity = Math.min(capacity, 6);
        this.intervalTicks = intervalTicks;
        registerDefaultState(stateDefinition.any().setValue(WATER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATER);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            serverLevel.scheduleTick(pos, this, intervalTicks);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult) {
        collect(state, level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.phys.BlockHitResult hitResult) {
        collect(state, level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private void collect(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int water = state.getValue(WATER);
        if (water <= 0) {
            serverPlayer.displayClientMessage(Component.translatable(LANG + "empty")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        serverPlayer.getInventory().placeItemBackInInventory(
                new ItemStack(ModItems.SIXTY_SECONDS_DIRTY_WATER, water));
        serverLevel.setBlock(pos, state.setValue(WATER, 0), Block.UPDATE_ALL);
        // 满仓时产水已停：收空后重新排产
        serverLevel.scheduleTick(pos, this, intervalTicks);
        serverLevel.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.7F, 1.1F);
        serverLevel.sendParticles(ParticleTypes.SPLASH,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.25, 0.15, 0.25, 0.0);
        serverPlayer.displayClientMessage(Component.translatable(LANG + "collected", water)
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** 定时产水：+1 后未满仓继续排产（满仓停摆，等收取重启）。 */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int water = state.getValue(WATER);
        if (water >= capacity) {
            return;
        }
        level.setBlock(pos, state.setValue(WATER, water + 1), Block.UPDATE_ALL);
        if (water + 1 < capacity) {
            level.scheduleTick(pos, this, intervalTicks);
        }
    }

    /** randomTick 兜底：定时因区块卸载丢失时仍能缓慢续产（Properties 需 randomTicks()）。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATER) < capacity && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, intervalTicks);
        }
    }
}
