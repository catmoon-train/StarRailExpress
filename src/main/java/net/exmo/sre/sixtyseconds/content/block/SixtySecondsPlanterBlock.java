package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
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
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.init.ModItems;

/**
 * 培育箱（耕地系统）：种子 → 蔬菜的家用农业闭环。
 * <ul>
 *   <li><b>播种</b>：手持种子包右键空箱（age=0）→ 消耗 1 包，进入发芽（age=1）。</li>
 *   <li><b>生长</b>：每 {@link SixtySecondsBalance#PLANTER_GROW_STAGE_TICKS} 自动长一阶
 *       （发芽→生长→成熟，scheduleTick 驱动 + randomTick 兜底），共约 4 分钟成熟。</li>
 *   <li><b>培育</b>：手持肥料右键生长中的箱子 → 消耗 1 份，立即跳一阶。</li>
 *   <li><b>收获</b>：成熟（age=3）右键 → {@link SixtySecondsBalance#PLANTER_HARVEST_MIN}~
 *       {@link SixtySecondsBalance#PLANTER_HARVEST_MAX} 份新鲜蔬菜，
 *       {@link SixtySecondsBalance#PLANTER_SEED_RETURN_CHANCE} 概率返还 1 包种子，箱子清空可复种。</li>
 * </ul>
 */
public class SixtySecondsPlanterBlock extends Block {
    /** 0=空耕土，1=发芽，2=生长，3=成熟。 */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    private static final String LANG = "message.noellesroles.sixty_seconds.planter.";

    public SixtySecondsPlanterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        interact(state, level, pos, player, stack);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        interact(state, level, pos, player, ItemStack.EMPTY);
        return InteractionResult.SUCCESS;
    }

    private void interact(BlockState state, Level level, BlockPos pos, Player player, ItemStack held) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        int age = state.getValue(AGE);
        if (age == 0) {
            // 播种：需要手持种子包
            if (!held.is(ModItems.SIXTY_SECONDS_SEEDS_PACK)) {
                serverPlayer.displayClientMessage(Component.translatable(LANG + "need_seeds")
                        .withStyle(ChatFormatting.GRAY), true);
                return;
            }
            if (!serverPlayer.isCreative()) {
                held.shrink(1);
            }
            grow(serverLevel, pos, state, 1);
            serverLevel.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.0F);
            serverPlayer.displayClientMessage(Component.translatable(LANG + "planted")
                    .withStyle(ChatFormatting.GREEN), true);
            return;
        }
        if (age < 3) {
            // 培育：肥料立即跳一阶；空手提示生长进度
            if (held.is(ModItems.SIXTY_SECONDS_FERTILIZER)) {
                if (!serverPlayer.isCreative()) {
                    held.shrink(1);
                }
                grow(serverLevel, pos, state, age + 1);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
                serverLevel.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
                serverPlayer.displayClientMessage(Component.translatable(LANG + "fertilized")
                        .withStyle(ChatFormatting.GREEN), true);
            } else {
                serverPlayer.displayClientMessage(Component.translatable(LANG + "growing", age, 3)
                        .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        // 成熟：收获
        RandomSource random = serverLevel.getRandom();
        int count = SixtySecondsBalance.PLANTER_HARVEST_MIN + random.nextInt(
                SixtySecondsBalance.PLANTER_HARVEST_MAX - SixtySecondsBalance.PLANTER_HARVEST_MIN + 1);
        serverPlayer.getInventory().placeItemBackInInventory(
                new ItemStack(ModItems.SIXTY_SECONDS_FRESH_VEGETABLES, count));
        boolean seedBack = random.nextDouble() < SixtySecondsBalance.PLANTER_SEED_RETURN_CHANCE;
        if (seedBack) {
            serverPlayer.getInventory().placeItemBackInInventory(
                    new ItemStack(ModItems.SIXTY_SECONDS_SEEDS_PACK));
        }
        serverLevel.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_ALL);
        serverLevel.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.9F, 1.0F);
        serverPlayer.displayClientMessage(Component.translatable(
                seedBack ? LANG + "harvest_seed" : LANG + "harvest", count)
                .withStyle(ChatFormatting.GOLD), true);
    }

    /** 设置生长阶段并（未成熟时）排下一阶的定时生长。 */
    private void grow(ServerLevel level, BlockPos pos, BlockState state, int newAge) {
        level.setBlock(pos, state.setValue(AGE, Math.min(3, newAge)), Block.UPDATE_ALL);
        if (newAge < 3) {
            level.scheduleTick(pos, this, SixtySecondsBalance.PLANTER_GROW_STAGE_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= 1 && age < 3) {
            grow(level, pos, state, age + 1);
        }
    }

    /** randomTick 兜底：scheduleTick 因区块卸载丢失时仍能缓慢续长（Properties 需 randomTicks()）。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age >= 1 && age < 3 && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            grow(level, pos, state, age + 1);
        }
    }
}
