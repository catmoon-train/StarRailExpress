package net.exmo.sre.sixtyseconds.content.block;

import net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 尖刺陷阱类方块（尖刺陷阱/铁丝网）：压板外观、无碰撞体；夜袭怪物踩入每秒受
 * {@link #damage} 伤害并减速（{@link SixtySecondsDefenseSystem}，按放置时注册的伤害结算）。
 * 放在白色混凝土标记上方，扳手可拆除返还。
 */
public class SixtySecondsSpikeTrapBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 1.5, 15);
    /** 踩入每秒伤害（尖刺={@code SPIKE_TRAP_DAMAGE}，铁丝网={@code BARBED_WIRE_DAMAGE}）。 */
    private final float damage;

    public SixtySecondsSpikeTrapBlock(Properties properties) {
        this(properties, net.exmo.sre.sixtyseconds.SixtySecondsBalance.SPIKE_TRAP_DAMAGE);
    }

    public SixtySecondsSpikeTrapBlock(Properties properties, float damage) {
        super(properties);
        this.damage = damage;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return Shapes.empty(); // 可踩入
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            // 记录放置者队伍：对玩家结算（SixtySecondsPveSystem.tickTraps）时本队免疫、敌队受伤
            int ownerTeam = placer instanceof net.minecraft.server.level.ServerPlayer player
                    ? net.exmo.sre.sixtyseconds.component.SixtySecondsStatsComponent.KEY.get(player).teamId
                    : -1;
            SixtySecondsDefenseSystem.registerTrap(serverLevel, pos, damage, ownerTeam);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SixtySecondsDefenseSystem.unregister(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
