package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.SixtySecondsMod;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 基地门（1..3 级，搭图用）：封住基地的额外房间。手持<b>对应等级的扩容钥匙</b>右键
 * → 消耗钥匙并打开（连通的同种基地门方块整面一起消失，泛洪上限 {@link #MAX_FLOOD}）。
 * 等级不匹配/空手右键给提示。方块本身近乎不可破坏（只能用钥匙开）。
 * <p>
 * 外观复用避难所门（{@code sixty_seconds_shelter_door}）的 2 格高薄门模型与贴图，
 * 带水平朝向（碰撞仍是整块，与避难所门一致）。
 */
public class SixtySecondsBaseDoorBlock extends Block {

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    private static final int MAX_FLOOD = 64;

    /** 门等级 1..3。 */
    private final int level;

    public SixtySecondsBaseDoorBlock(Properties properties, int level) {
        super(properties);
        this.level = level;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(
            net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    private Item keyItem() {
        return switch (level) {
            case 2 -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_EXPANSION_KEY_2;
            case 3 -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_EXPANSION_KEY_3;
            default -> org.agmas.noellesroles.init.ModItems.SIXTY_SECONDS_EXPANSION_KEY_1;
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(keyItem())) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.noellesroles.sixty_seconds.base_door_need_key", level), true);
            world.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 0.9F);
            return ItemInteractionResult.SUCCESS;
        }
        if (!serverPlayer.isCreative()) {
            stack.shrink(1);
        }
        // 泛洪移除连通的同种基地门（整扇门/整面墙一次打开）
        Block doorBlock = state.getBlock();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        visited.add(pos);
        while (!queue.isEmpty() && visited.size() <= MAX_FLOOD) {
            BlockPos current = queue.poll();
            world.setBlock(current, Blocks.AIR.defaultBlockState(), 3);
            for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
                BlockPos next = current.relative(dir);
                if (visited.add(next) && world.getBlockState(next).is(doorBlock)) {
                    queue.add(next);
                }
            }
        }
        world.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
        serverPlayer.displayClientMessage(Component.translatable(
                "message.noellesroles.sixty_seconds.base_door_opened")
                .withStyle(ChatFormatting.GREEN), false);
        if (SixtySecondsMod.isActive(world)) {
            serverPlayer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        }
        return ItemInteractionResult.SUCCESS;
    }
}
