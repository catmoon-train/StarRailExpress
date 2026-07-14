package io.wifi.starrailexpress.content.block;

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * 小游戏任务点方块
 * 透明、可含水、无碰撞体积，类似实体交互方块
 * 创造模式玩家右键可打开小游戏选择GUI
 * 冒险模式玩家右键直接打开配置的小游戏
 */
public class MinigameQuestBlock extends BaseEntityBlock
        implements TaskInstinctShowableInterface, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int TASK_INSTINCT_ID = 14;

    public MinigameQuestBlock(Properties settings) {
        super(settings.noOcclusion().noCollission());
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide)
            return InteractionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MinigameQuestBlockEntity questBe) {
            if (player instanceof ServerPlayer sp && sp.isCreative()) {
                // 创造模式：打开配置界面
                questBe.openConfigUI(sp);
            } else if (player instanceof ServerPlayer sp) {
                // 破坏任务触发点：杀手 + canUseSabotage 角色可右键
                if (questBe.isSabotageTrigger()) {
                    var role = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(sp.level())
                            .getRole(sp);
                    if (role == null || (!role.isKiller() && !role.canUseSabotage())) {
                        return InteractionResult.SUCCESS;
                    }
                    if (questBe.isSabotageOnCooldown(sp.level().getGameTime())) {
                        sp.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable("message.sre.sabotage_cooldown"),
                                true);
                        return InteractionResult.SUCCESS;
                    }
                    String sabotageMinigameId = questBe.getMinigameId();
                    if (sabotageMinigameId != null && !sabotageMinigameId.isEmpty()) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                                new io.wifi.starrailexpress.network.MinigameQuestPayload.OpenGame(pos,
                                        sabotageMinigameId));
                    }
                    return InteractionResult.SUCCESS;
                }
                // 冒险/生存模式：打开小游戏
                String minigameId = questBe.getMinigameId();
                if (minigameId != null && !minigameId.isEmpty()) {
                    // 游戏进行中：必须有对应的待办小游戏任务，且该点位不在本玩家冷却中；
                    // 未开始游戏时：可随意打开（无任务 / 冷却限制）。
                    if (io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(sp.level()).isRunning()) {
                        // 60s 模式：发电机断电 = 镶板停机（服务端硬门控；非 60s 恒放行）
                        if (net.exmo.sre.sixtyseconds.logic.SixtySecondsPowerSystem.minigameBlockedByPower(sp)) {
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable(
                                            "message.noellesroles.sixty_seconds.minigame_no_power"),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                        var mgComp = io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent.KEY.get(sp);
                        if (!mgComp.hasPendingTask()) {
                            // 当前没有对应的小游戏任务：拒绝使用并提示
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.sre.minigame_no_task"),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                        if (mgComp.isBlockUsed(pos)) {
                            // 该任务点对本玩家仍在复用冷却中：拒绝使用并提示（各玩家独立）
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.sre.minigame_cooldown"),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                        // 校验小游戏类型匹配：若有指定目标类型，必须匹配
                        if (mgComp.targetMinigameId != null && !mgComp.targetMinigameId.isEmpty()
                                && !mgComp.targetMinigameId.equals(minigameId)) {
                            sp.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.sre.minigame_wrong_type",
                                            net.minecraft.network.chat.Component.translatable(
                                                    "minigame.starrailexpress." + mgComp.targetMinigameId)),
                                    true);
                            return InteractionResult.SUCCESS;
                        }
                        // 使用任务点即进入复用冷却（透视也随之隐藏）
                        mgComp.startBlockCooldown(pos);
                    }
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new io.wifi.starrailexpress.network.MinigameQuestPayload.OpenGame(pos, minigameId));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MinigameQuestBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, TMMBlockEntities.MINIGAME_QUEST,
                (lvl, pos, s, be) -> {
                    /* 无需tick */ });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ══════════════════════════════════════════
    // 任务路标接口（支持scan同步）
    // ══════════════════════════════════════════

    @Override
    public int taskInstinctId() {
        return TASK_INSTINCT_ID;
    }

    @Override
    public boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player) {

        // 小游戏任务点(14/15)：仅在玩家有待办小游戏任务、该点本局未被使用、
        // 且该点的 minigameId 与玩家指派的目标类型匹配（或无指定目标）时才金色透视
        boolean isMinigamePoint = level.getBlockEntity(pos) instanceof MinigameQuestBlockEntity questBe
                && !questBe.isSabotageTrigger();
        if (isMinigamePoint) {
            var mgComp = SREPlayerMinigameTaskComponent.KEY.get(player);
            if (mgComp != null && mgComp.hasPendingTask() && !mgComp.isBlockUsed(pos)) {
                // 读取该方块的小游戏类型
                boolean typeMatches = true;
                if (level
                        .getBlockEntity(pos) instanceof MinigameQuestBlockEntity questBe2) {
                    String blockMgId = questBe2.getMinigameId();
                    if (mgComp.targetMinigameId != null && !mgComp.targetMinigameId.isEmpty()
                            && !mgComp.targetMinigameId.equals(blockMgId)) {
                        typeMatches = false;
                    }
                }
                if (typeMatches) {
                    return true;
                }
            }
            return false;
        }
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinigameQuestBlockEntity questBe) {
                // 破坏任务触发点：杀手 + canUseSabotage 角色可见
                if (questBe.isSabotageTrigger()) {
                    if (questBe.isSabotageOnCooldown(level.getGameTime())) {
                        return false;
                    }
                    var role = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level)
                            .getRole(player);
                    return role != null && (role.isKiller() || role.canUseSabotage());
                }
                return questBe.isTaskMarker();
            }
        }
        return false;
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        Level level = player.level();
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinigameQuestBlockEntity questBe) {
                int c = questBe.getMarkerColor();
                // 破坏任务触发点：若颜色为默认金色则改为红色
                if (questBe.isSabotageTrigger() && c == 0xFFD700) {
                    return Color.RED;
                }
                return new Color(c);
            }
        }
        return new Color(255, 215, 0); // 金色
    }
}
