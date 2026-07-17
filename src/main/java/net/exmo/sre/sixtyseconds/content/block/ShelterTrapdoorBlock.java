package net.exmo.sre.sixtyseconds.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 避难所<b>活板门</b>（避难所门的变体）：3×3×2=18 方块的多方块结构，本块是<b>主控块</b>（3×3 顶层的正中）。
 * <p>
 * 继承 {@link ShelterDoorBlock} —— 全代码库所有 {@code instanceof ShelterDoorBlock} 的判定（门菜单、区域/锚点
 * 绑定工具、门锁/门陷阱、夜袭门缓存、门高亮）都会自动认它，因此活板门「和普通门一样的逻辑、也可以被绑定」。
 * 与普通门一样是实心块、靠右键菜单传送进出（不是物理穿过），18 块只是更大的地表舱盖视觉。
 * <p>
 * 摆放主控块时自动把周围 17 格填成 {@link ShelterTrapdoorPartBlock}；破坏任一格连带清掉整座结构。
 * 避难所模板里放一块活板门 → 建图时整座避难所智能下沉、让活板门齐地表（见 {@code SixtySecondsArena}）。
 */
public class ShelterTrapdoorBlock extends ShelterDoorBlock {

    public ShelterTrapdoorBlock(Properties properties) {
        super(properties);
    }

    /** 结构相对主控块的 18 个格子（含主控块自身）：3(x)×3(z) 顶层 + 其正下方一层，共 2 层。 */
    public static List<BlockPos> structurePositions(BlockPos controller) {
        List<BlockPos> list = new ArrayList<>(18);
        for (int dy = 0; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    list.add(controller.offset(dx, dy, dz));
                }
            }
        }
        return list;
    }

    /** 从结构里任一格（部件或主控）反查主控块坐标；找不到返回 null。 */
    public static BlockPos findController(Level level, BlockPos any) {
        if (level.getBlockState(any).getBlock() instanceof ShelterTrapdoorBlock) {
            return any;
        }
        // 部件在主控块的同层或下一层、水平 ±1 内 → 主控块在同层或上一层
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = any.offset(dx, dy, dz);
                    if (level.getBlockState(p).getBlock() instanceof ShelterTrapdoorBlock) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    /** 把整座结构清成空气（破坏任一格时调用；用 setBlock(AIR) 不会再触发部件的 playerWillDestroy，无需防重入）。 */
    public static void removeStructure(Level level, BlockPos controller) {
        for (BlockPos p : structurePositions(controller)) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof ShelterTrapdoorBlock || s.getBlock() instanceof ShelterTrapdoorPartBlock) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        // 主控块摆下后，把其余 17 格填成部件块（含朝向随主控块，纯视觉）
        BlockState part = ModPartState(state);
        for (BlockPos p : structurePositions(pos)) {
            if (p.equals(pos)) {
                continue;
            }
            level.setBlock(p, part, Block.UPDATE_CLIENTS);
        }
    }

    private static BlockState ModPartState(BlockState controllerState) {
        BlockState part = org.agmas.noellesroles.init.ModBlocks.SIXTY_SECONDS_SHELTER_TRAPDOOR_PART.defaultBlockState();
        if (part.hasProperty(FACING) && controllerState.hasProperty(FACING)) {
            part = part.setValue(FACING, controllerState.getValue(FACING));
        }
        return part;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            removeStructure(level, pos); // pos 即主控块
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
