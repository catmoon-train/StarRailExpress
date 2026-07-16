package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsPveSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 哨戒炮：放置登记归属队伍，<b>通电时</b>（{@code SixtySecondsPowerSystem} 发电机供电）每 1.5s
 * 自动射击 {@code TURRET_RANGE} 内最近的 60s 怪（游荡怪/夜袭者/Boss/低语怪）或<b>敌队</b>玩家
 * （本队免疫）；结算在 {@link SixtySecondsPveSystem#tick}。放在白色混凝土标记上方，扳手可拆除返还。
 */
public class SixtySecondsTurretBlock extends Block
        implements net.minecraft.world.level.block.EntityBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public SixtySecondsTurretBlock(Properties properties) {
        super(properties);
    }

    /** BE 只服务于客户端渲染（炮头旋转），无服务端 tick。 */
    @Override
    public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(
            BlockPos pos, BlockState state) {
        return new net.exmo.sre.sixtyseconds.content.block_entity.SixtySecondsTurretBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel && placer instanceof ServerPlayer player) {
            int teamId = net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(player).teamId;
            SixtySecondsPveSystem.registerTurret(serverLevel, pos, teamId);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SixtySecondsPveSystem.unregisterTurret(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
