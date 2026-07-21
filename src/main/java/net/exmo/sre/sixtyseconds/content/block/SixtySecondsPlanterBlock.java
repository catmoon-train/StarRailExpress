package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsBalance;
import net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent;
import net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsPlanterBlockEntity;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops.Crop;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsCrops.Tier;
import net.exmo.sre.sixtyseconds.logic.SixtySecondsTechTree;
import net.exmo.sre.sixtyseconds.state.SixtySecondsState;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.init.ModItems;

/**
 * 培育箱（耕地系统，三档）：多作物家用农业闭环，作物表见 {@link SixtySecondsCrops}。
 * <ul>
 *   <li><b>播种</b>：手持种子右键空箱（age=0）→ 校验培育箱等级 + 队伍科技门控，消耗 1 份进入发芽。</li>
 *   <li><b>生长</b>：每 {@link SixtySecondsBalance#PLANTER_GROW_STAGE_TICKS} 自动长一阶；
 *       <b>高级培育箱</b>对常规作物只需 2 阶段（发芽后直接成熟），
 *       工业麻/火把花/瓶子草仍走满 3 阶段。</li>
 *   <li><b>培育</b>：肥料 → 跳一阶；<b>营养肥</b> → 直接催熟。</li>
 *   <li><b>收获</b>：成熟右键 → 按作物表产出；马铃薯有 5% 概率其中 1 个变毒马铃薯；
 *       队伍收益（按科技）：收蔬菜 30% 野谷种子袋 / 5% 野茶籽（农业-II）、
 *       收蔬菜/野米 20% 工业麻种子（农业-III）、收任意作物 1% 烟草种子（烟草）。</li>
 * </ul>
 */
public class SixtySecondsPlanterBlock extends Block implements EntityBlock {
    /** 0=空耕土，1=发芽，2=生长，3=成熟。 */
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 4);
    private static final String LANG = "message.noellesroles.sixty_seconds.planter.";

    private final Tier tier;

    public SixtySecondsPlanterBlock(Properties properties) {
        this(properties, Tier.BASIC);
    }

    public SixtySecondsPlanterBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    public Tier tier() {
        return tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SixtySecondsPlanterBlockEntity(pos, state);
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
        SixtySecondsState.TeamData team = teamOf(serverLevel, serverPlayer);
        int age = state.getValue(AGE);
        if (age == 0) {
            plant(state, serverLevel, pos, serverPlayer, held, team);
            return;
        }
        Crop crop = cropAt(serverLevel, pos);
        if (age < 3) {
            // 营养肥：直接催熟；肥料：跳一阶；空手：提示进度
            if (held.is(ModItems.SIXTY_SECONDS_NUTRIENT_FERTILIZER)) {
                if (!serverPlayer.isCreative()) {
                    held.shrink(1);
                }
                grow(serverLevel, pos, state, 3, crop);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.0);
                serverLevel.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8F, 1.3F);
                serverPlayer.displayClientMessage(Component.translatable(LANG + "ripened")
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (held.is(ModItems.SIXTY_SECONDS_FERTILIZER)) {
                if (!serverPlayer.isCreative()) {
                    held.shrink(1);
                }
                grow(serverLevel, pos, state, age + 1, crop);
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
        harvest(state, serverLevel, pos, serverPlayer, team, crop);
    }

    /** 播种：等级 + 科技门控。 */
    private void plant(BlockState state, ServerLevel level, BlockPos pos, ServerPlayer player,
            ItemStack held, SixtySecondsState.TeamData team) {
        Crop crop = SixtySecondsCrops.bySeed(held.getItem());
        if (crop == null) {
            player.displayClientMessage(Component.translatable(LANG + "need_seeds")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!SixtySecondsCrops.allowedIn(crop, tier)) {
            player.displayClientMessage(Component.translatable(LANG + "wrong_planter")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (crop.techId() != null && !SixtySecondsTechTree.isUnlocked(team, crop.techId())) {
            player.displayClientMessage(Component.translatable(LANG + "tech_locked",
                    Component.translatable("tech.noellesroles.sixty_seconds." + crop.techId()))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        if (level.getBlockEntity(pos) instanceof SixtySecondsPlanterBlockEntity be) {
            be.cropId = crop.id();
            be.setChanged();
        }
        grow(level, pos, state, 1, crop);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.0F);
        player.displayClientMessage(Component.translatable(LANG + "planted")
                .withStyle(ChatFormatting.GREEN), true);
    }

    /** 收获：按作物表产出 + 毒马铃薯 + 队伍科技收益。 */
    protected void harvest(BlockState state, ServerLevel level, BlockPos pos, ServerPlayer player,
            SixtySecondsState.TeamData team, Crop crop) {
        RandomSource random = level.getRandom();
        int count = crop.minCount() + (crop.maxCount() > crop.minCount()
                ? random.nextInt(crop.maxCount() - crop.minCount() + 1) : 0);
        // 马铃薯：5% 其中 1 个变毒马铃薯
        if ("potato".equals(crop.id()) && count > 0 && random.nextDouble() < 0.05) {
            count--;
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.POISONOUS_POTATO));
        }
        if (count > 0) {
            player.getInventory().placeItemBackInInventory(new ItemStack(crop.product(), count));
        }
        // 紫颂花额外产出紫颂果
        if ("chorus_flower".equals(crop.id())) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Items.CHORUS_FRUIT, 1));
        }
        // 队伍收益（科技门控）
        boolean isVeg = "vegetables".equals(crop.id());
        boolean isRice = "wild_rice".equals(crop.id());
        if (isVeg && SixtySecondsTechTree.isUnlocked(team, "agri_2")) {
            if (random.nextDouble() < 0.30) {
                player.getInventory().placeItemBackInInventory(
                        new ItemStack(ModItems.SIXTY_SECONDS_WILD_RICE_SEEDS));
            }
            if (random.nextDouble() < 0.05) {
                player.getInventory().placeItemBackInInventory(
                        new ItemStack(ModItems.SIXTY_SECONDS_WILD_TEA_SEED));
            }
        }
        if ((isVeg || isRice) && SixtySecondsTechTree.isUnlocked(team, "agri_3")
                && random.nextDouble() < 0.20) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(ModItems.SIXTY_SECONDS_HEMP_SEEDS));
        }
        if (SixtySecondsTechTree.isUnlocked(team, "tobacco") && random.nextDouble() < 0.01) {
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(ModItems.SIXTY_SECONDS_TOBACCO_SEEDS));
        }
        // 蔬菜保留旧的返种概率闭环
        boolean seedBack = isVeg && random.nextDouble() < SixtySecondsBalance.PLANTER_SEED_RETURN_CHANCE;
        if (seedBack) {
            player.getInventory().placeItemBackInInventory(new ItemStack(ModItems.SIXTY_SECONDS_SEEDS_PACK));
        }
        if (level.getBlockEntity(pos) instanceof SixtySecondsPlanterBlockEntity be) {
            be.cropId = "";
            be.setChanged();
        }
        level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.9F, 1.0F);
        player.displayClientMessage(Component.translatable(
                seedBack ? LANG + "harvest_seed" : LANG + "harvest", count)
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static SixtySecondsState.TeamData teamOf(ServerLevel level, ServerPlayer player) {
        return SixtySecondsState.get(level).teams
                .get(SixtySecondsStatsComponent.KEY.get(player).teamId);
    }

    private Crop cropAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SixtySecondsPlanterBlockEntity be) {
            Crop crop = SixtySecondsCrops.byId(be.cropId);
            if (crop != null) {
                return crop;
            }
        }
        return SixtySecondsCrops.byId("vegetables");
    }

    /**
     * 设置生长阶段并（未成熟时）排下一阶的定时生长。
     * 高级培育箱对常规作物只需 2 阶段：发芽（1）后下一步直接成熟（跳过 2）。
     */
    /** 最大生长阶段数（子类可覆写为 4 实现 5 阶段）。 */
    public int maxAge() {
        return 3;
    }

    protected void grow(ServerLevel level, BlockPos pos, BlockState state, int newAge, Crop crop) {
        int max = maxAge();
        int target = Math.min(max, newAge);
        if (tier == Tier.ADVANCED && crop != null && !crop.fullStagesInAdvanced() && target == 2) {
            target = 3;
        }
        level.setBlock(pos, state.setValue(AGE, target), Block.UPDATE_ALL);
        if (target < max) {
            float mul = crop != null ? crop.growthTimeMultiplier() : 1.0F;
            level.scheduleTick(pos, this, (int) (SixtySecondsBalance.PLANTER_GROW_STAGE_TICKS * mul));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        int max = maxAge();
        if (age >= 1 && age < max) {
            grow(level, pos, state, age + 1, cropAt(level, pos));
        }
    }

    /** randomTick 兜底：scheduleTick 因区块卸载丢失时仍能缓慢续长（Properties 需 randomTicks()）。 */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        int max = maxAge();
        if (age >= 1 && age < max && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            grow(level, pos, state, age + 1, cropAt(level, pos));
        }
    }
}
