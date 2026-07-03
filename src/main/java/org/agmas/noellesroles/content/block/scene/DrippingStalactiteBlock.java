package org.agmas.noellesroles.content.block.scene;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.scene.SceneParticles;

import java.util.List;

/**
 * 滴水石椎：随机间隔先滴水预警（dripping），随后坠落一颗石锥实体，砸死正下方一整列内的玩家。
 * 使用原版滴水石块/尖锥贴图。
 */
public class DrippingStalactiteBlock extends Block {

    public static final BooleanProperty DRIPPING = BooleanProperty.create("dripping");

    /** 预警到坠落的延迟。 */
    public static final int WARN_DELAY = 15;
    /** 向下扫描的最大深度。 */
    private static final int MAX_DEPTH = 32;
    /** 坠落时恢复方块的瞬时标记，避免 onPlace 重复排程导致循环加倍。 */
    private static boolean restoring = false;

    public DrippingStalactiteBlock(Properties settings) {
        super(settings);
        this.registerDefaultState(this.stateDefinition.any().setValue(DRIPPING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DRIPPING);
    }

    private static int nextInterval(RandomSource random) {
        return 100 + random.nextInt(200); // 5~15 秒
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // 放置后开启自动坠落循环（恢复方块或同方块状态变化时不重复排程）
        if (!level.isClientSide && !restoring && !oldState.is(this)
                && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 40);
        }
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 安全网：若循环因故中断（如重载丢失计划tick），重新启动
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, nextInterval(random));
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(DRIPPING)) {
            // 预警阶段：滴水提示
            level.setBlock(pos, state.setValue(DRIPPING, true), Block.UPDATE_CLIENTS);
            SceneParticles.column(level, pos.below(), ParticleTypes.DRIPPING_DRIPSTONE_WATER, 1.0, 0.0);
            level.playSound(null, pos, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.BLOCKS, 0.9F, 0.6F);
            level.scheduleTick(pos, this, WARN_DELAY);
        } else {
            // 坠落阶段
            level.setBlock(pos, state.setValue(DRIPPING, false), Block.UPDATE_CLIENTS);
            drop(level, pos);
            level.scheduleTick(pos, this, nextInterval(random));
        }
    }

    /** 立即坠落一颗石锥（供指令调用）。 */
    public static void drop(ServerLevel level, BlockPos pos) {
        BlockState self = level.getBlockState(pos);

        // 生成下落的石锥实体（视觉）：尖端朝下，使其呈倒挂石锥形态
        BlockState spike = Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP);
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, spike);
        falling.setHurtsEntities(8.0F, 40);
        // 落地后既不放置成方块，也不掉落物品（砸到其它石锥同理），仅造成伤害与碎裂效果
        falling.dropItem = false;
        // fall() 会把方块设为空气，立即恢复触发器方块（restoring 标记避免 onPlace 重复排程）
        restoring = true;
        level.setBlock(pos, self, Block.UPDATE_CLIENTS);
        restoring = false;

        level.playSound(null, pos, SoundEvents.STONE_FALL, SoundSource.BLOCKS, 1.0F, 0.9F);

        // 向下找到落点，砸死该列玩家
        int floorY = pos.getY() - MAX_DEPTH;
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int dy = 1; dy <= MAX_DEPTH; dy++) {
            cursor.set(pos.getX(), pos.getY() - dy, pos.getZ());
            BlockState below = level.getBlockState(cursor);
            if (!below.getCollisionShape(level, cursor).isEmpty()) {
                floorY = pos.getY() - dy + 1;
                break;
            }
        }

        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK,
                Blocks.DRIPSTONE_BLOCK.defaultBlockState());
        SceneParticles.columnDown(level, Vec3.atCenterOf(pos), dust, pos.getY() - floorY, 0.0);
        level.sendParticles(dust, pos.getX() + 0.5, floorY + 0.2, pos.getZ() + 0.5, 40, 0.4, 0.2, 0.4, 0.15);
        level.playSound(null, new BlockPos(pos.getX(), floorY, pos.getZ()),
                SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.BLOCKS, 1.2F, 1.0F);

        AABB column = new AABB(pos.getX(), floorY, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        List<Player> victims = level.getEntitiesOfClass(Player.class, column,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player victim : victims) {
            GameUtils.forceKillPlayer(victim, true, null, GameConstants.DeathReasons.STALACTITE_IMPALE);
        }
    }
}
