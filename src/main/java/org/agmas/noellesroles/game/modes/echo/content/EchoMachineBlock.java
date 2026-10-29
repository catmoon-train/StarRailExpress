package org.agmas.noellesroles.game.modes.echo.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;

/** 回声机器的真实多格外壳；不接管右键，交互仍由模式坐标与网络处理。 */
public final class EchoMachineBlock extends Block implements TaskInstinctShowableInterface {
    public static final MapCodec<EchoMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.comapFlatMap(name -> {
                try {
                    return DataResult.success(EchoMachines.Kind.valueOf(name));
                } catch (IllegalArgumentException exception) {
                    return DataResult.<EchoMachines.Kind>error(() -> "Unknown Echo machine kind: " + name);
                }
            }, EchoMachines.Kind::name).fieldOf("kind").forGetter(EchoMachineBlock::kind),
            propertiesCodec()).apply(instance, EchoMachineBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 17);
    private final EchoMachines.Kind kind;

    public EchoMachineBlock(EchoMachines.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    public EchoMachines.Kind kind() { return kind; }

    @Override public int taskInstinctId() { return EchoMachineTasks.TYPE; }
    @Override public boolean isTaskInstinctAnchor(BlockState state) { return EchoMachineTasks.anchorPart(state.getValue(PART)); }
    @Override
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public boolean shouldRenderTaskInstinct(Level level,BlockState state,BlockPos pos,Player player) {
        return isTaskInstinctAnchor(state);
    }
    @Override
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public java.awt.Color taskInstinctRenderColor(BlockState state,BlockPos pos,Player player) {
        return new java.awt.Color(EchoMachineTasks.color(kind));
    }
    @Override
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public net.minecraft.world.phys.AABB taskInstinctBounds(Level level,BlockState state,BlockPos pos) {
        return EchoMachineInteractions.bounds(level,pos).move(-pos.getX(),-pos.getY(),-pos.getZ());
    }

    @Override
    protected MapCodec<? extends Block> codec() { return CODEC; }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
                .setValue(PART, EchoMachineStructure.mirroredPart(state.getValue(PART), mirror, kind));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(asItem());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 同种方块的 PART/FACING 变化也必须断开旧关联；绝不能删掉 pos 上的新状态。
        if (!level.isClientSide && state != newState && !EchoMachineStructure.mutating()
                && EchoMachineStructure.validPart(kind, state.getValue(PART))) {
            EchoMachineStructure.remove(level, pos, state, true);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // 玩家掉落发生在 onRemove 之后；子格先借用仍存在的根生成唯一掉落。
        // 随后的 playerDestroy 再查询时根已消失，因此不会重复；创造不走此分支。
        if (!level.isClientSide && !player.isCreative() && state.getValue(PART) != 0) {
            Block.dropResources(state, level, pos, null, player, player.getMainHandItem());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (!EchoMachineStructure.validPart(kind, state.getValue(PART))) return List.of();
        if (state.getValue(PART) == 0) return List.of(new ItemStack(asItem()));
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return List.of();
        BlockPos root = EchoMachineStructure.anchor(BlockPos.containing(origin), state);
        // 爆炸与 destroyBlock 在移除前查询掉落；根只会被一份子格消费。
        return EchoMachineStructure.loadedMatch(params.getLevel(), root, state, 0)
                ? List.of(new ItemStack(asItem())) : List.of();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 20 + state.getValue(PART));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        // 不在 updateShape 中立即拆除：模板可用 UPDATE_KNOWN_SHAPE 同步写齐全部格子。
        if (!level.isClientSide) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos root = EchoMachineStructure.anchor(pos, state);
        if (!EchoMachineStructure.validPart(kind, state.getValue(PART))
                || (EchoMachineStructure.loadedFootprint(level, root, state, kind)
                && !EchoMachineStructure.complete(level, root))) {
            EchoMachineStructure.remove(level, pos, state, false);
            return;
        }
        // 跨卸载区块的残格保留到全体积重新加载后清理；不加载区块、不补掉落。
        // 定期自检让卸载格在重载后也可收敛，无需每实例事件或方块实体。
        level.scheduleTick(pos, this, 40 + state.getValue(PART));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(net.minecraft.network.chat.Component.translatable("echo.machine.tooltip").withStyle(net.minecraft.ChatFormatting.GOLD));
        tooltip.add(net.minecraft.network.chat.Component.translatable("echo.machine.tooltip.config").withStyle(net.minecraft.ChatFormatting.GRAY));
        EchoMachineStructure.Size size = EchoMachineStructure.size(kind);
        tooltip.add(net.minecraft.network.chat.Component.translatable("echo_multiblock.tooltip.size",
                size.width(), size.height(), size.depth()).withStyle(net.minecraft.ChatFormatting.AQUA));
        tooltip.add(net.minecraft.network.chat.Component.translatable("echo_multiblock.tooltip.anchor").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
