package org.agmas.noellesroles.content.block_entity.scene;

import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.content.block.scene.FlamethrowerBlock;
import org.agmas.noellesroles.init.ModSceneBlocks;
import org.agmas.noellesroles.scene.SceneEventManager;

import java.util.List;

/**
 * 喷火装置方块实体：周期性喷火；破坏任务激活时持续喷火。火焰范围内玩家死亡。
 */
public class FlamethrowerBlockEntity extends BlockEntity {

    /** 喷火周期。 */
    public static final int CYCLE = 100;
    /** 每周期喷火时长。 */
    public static final int BURST = 30;
    /** 火焰射程（方块）。 */
    public static final int RANGE = 5;

    public FlamethrowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModSceneBlocks.FLAMETHROWER_ENTITY, pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FlamethrowerBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Direction dir = state.getValue(FlamethrowerBlock.FACING);
        boolean sabotage = SceneEventManager.isSabotageActive(serverLevel);
        boolean hasRedstone = state.getValue(FlamethrowerBlock.POWERED);
        long phase = serverLevel.getGameTime() % CYCLE;

        // 红石信号控制：收到红石信号时由红石电平控制喷火开关，忽略周期和破坏任务
        boolean firing;
        if (hasRedstone) {
            firing = level.hasNeighborSignal(pos); // 红石信号直接控制
        } else {
            firing = sabotage || phase < BURST; // 无红石信号时正常逻辑
        }

        // 点火嘴常亮小火苗
        serverLevel.sendParticles(ParticleTypes.SMALL_FLAME,
                pos.getX() + 0.5 + dir.getStepX() * 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5 + dir.getStepZ() * 0.5,
                1, 0.05, 0.05, 0.05, 0.0);

        if (!firing) {
            return;
        }

        // 火焰柱 + 灼烧范围
        AABB flame = flameBox(pos, dir);
        for (int i = 1; i <= RANGE; i++) {
            double fx = pos.getX() + 0.5 + dir.getStepX() * i;
            double fy = pos.getY() + 0.5;
            double fz = pos.getZ() + 0.5 + dir.getStepZ() * i;
            serverLevel.sendParticles(ParticleTypes.FLAME, fx, fy, fz, 3, 0.25, 0.25, 0.25, 0.02);
            serverLevel.sendParticles(ParticleTypes.LAVA, fx, fy, fz, 1, 0.2, 0.2, 0.2, 0.0);
        }
        if (serverLevel.getGameTime() % 8 == 0) {
            serverLevel.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.8F);
        }

        List<Player> victims = serverLevel.getEntitiesOfClass(Player.class, flame,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        for (Player victim : victims) {
            victim.setRemainingFireTicks(80);
            GameUtils.forceKillPlayer(victim, true, null, GameConstants.DeathReasons.FLAMETHROWER_BURNED);
        }
    }

    private static AABB flameBox(BlockPos pos, Direction dir) {
        BlockPos end = pos.relative(dir, RANGE);
        return new AABB(pos).expandTowards(dir.getStepX() * RANGE, 0, dir.getStepZ() * RANGE)
                .minmax(new AABB(end));
    }
}
